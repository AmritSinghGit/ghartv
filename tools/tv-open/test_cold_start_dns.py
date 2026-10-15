"""Controlled resolver/transport tests: no live DNS, Android, signing or provider calls."""
from pathlib import Path
import ast
import importlib.util
import json
import subprocess
import tempfile
import unittest
from contextlib import ExitStack
from unittest.mock import Mock, patch

TOOLS=Path(__file__).parents[1]
def load(name,path):
    spec=importlib.util.spec_from_file_location(name,path)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    return module
m=load('cold_start_transport',TOOLS/'tv_local.py')
n=load('cold_start_network',TOOLS/'emulator_network_repair.py')
RESOLVERS=['192.0.2.53','192.0.2.54']
def result(text=''):
    return subprocess.CompletedProcess([],0,text,'')
class SpawnObserved(Exception):pass

class ColdStartTests(unittest.TestCase):
    def fake_call(self,args,timeout=8):
        args=list(map(str,args))
        if args[-1]=='devices':return result('List of devices attached\n')
        if args[-1]=='-list-avds':return result(m.AVD+'\n')
        if 'getprop' in args:return result('1\n')
        return result()
    def setup_transport(self):
        stack=ExitStack();self.addCleanup(stack.close)
        stack.enter_context(patch.object(m,'call',side_effect=self.fake_call))
        stack.enter_context(patch.object(m,'avd_processes',return_value=[]))
        stack.enter_context(patch.object(m,'_listen_owners',return_value=[]))
        stack.enter_context(patch.object(m,'_reusable_pair',return_value=[{'state':'REUSABLE'}]))
        stack.enter_context(patch.object(m,'verify_target'))
        stack.enter_context(patch.object(m.time,'sleep'))
        spawn=stack.enter_context(patch.object(m.subprocess,'Popen'))
        spawn.return_value.poll.return_value=None
        return stack,spawn
    def test_system_resolvers_are_checked_between_two_memory_observations(self):
        sequence=[]
        def memory():sequence.append('memory');return True
        def dns():sequence.append('system_dns');return list(RESOLVERS)
        with patch.object(n,'system_dns',side_effect=dns):
            self.assertEqual(n.cold_start_resolvers(memory),RESOLVERS)
        self.assertEqual(sequence,['memory','system_dns','memory'])
    def test_initial_memory_hold_never_calls_resolvers(self):
        with patch.object(n,'system_dns') as dns:
            with self.assertRaisesRegex(RuntimeError,'COLD_START_MEMORY_HELD'):
                n.cold_start_resolvers(lambda:False)
            dns.assert_not_called()
    def test_memory_change_during_lookup_holds(self):
        with patch.object(n,'system_dns',return_value=RESOLVERS):
            with self.assertRaisesRegex(RuntimeError,'COLD_START_MEMORY_HELD'):
                n.cold_start_resolvers(Mock(side_effect=[True,False]))
    def test_no_working_resolver_holds_without_public_fallback(self):
        with patch.object(n,'system_dns',return_value=[]):
            with self.assertRaisesRegex(RuntimeError,'COLD_START_NO_WORKING_SYSTEM_DNS'):
                n.cold_start_resolvers(lambda:True)
    def test_stopped_transport_passes_verified_resolvers_to_one_spawn(self):
        _,spawn=self.setup_transport();prepare=Mock(return_value=RESOLVERS)
        with tempfile.TemporaryDirectory(dir=Path(tempfile.gettempdir()).resolve()) as t:
            _,started=m.attach_or_start(Path(t),Path(t),prepare_cold_start=prepare)
            self.assertTrue(started);prepare.assert_called_once_with();spawn.assert_called_once()
            argv=spawn.call_args.args[0]
            self.assertEqual(argv[argv.index('-dns-server')+1],','.join(RESOLVERS))
            self.assertEqual(argv[argv.index('-avd')+1],m.AVD)
            self.assertEqual(argv[argv.index('-port')+1],'5580')
            self.assertNotIn('-wipe-data',argv)
            evidence=json.loads((Path(t)/'PORT_CHECK.json').read_text())
            self.assertIn('COLD_START_SYSTEM_DNS_PREPARED',[row['stage'] for row in evidence['observations']])
            for address in RESOLVERS:self.assertNotIn(address,json.dumps(evidence))
    def test_online_reuse_does_not_prepare_dns_or_spawn(self):
        _,spawn=self.setup_transport();prepare=Mock(side_effect=AssertionError('unexpected DNS'))
        base=self.fake_call
        def call(args,timeout=8):
            return result('emulator-5580 device\n') if str(args[-1])=='devices' else base(args,timeout)
        with tempfile.TemporaryDirectory(dir=Path(tempfile.gettempdir()).resolve()) as t,patch.object(m,'call',side_effect=call):
            _,started=m.attach_or_start(Path(t),Path(t),prepare_cold_start=prepare)
            self.assertFalse(started)
        prepare.assert_not_called();spawn.assert_not_called()
    def test_unknown_listener_prevents_resolver_call_and_spawn(self):
        _,spawn=self.setup_transport();prepare=Mock()
        with tempfile.TemporaryDirectory(dir=Path(tempfile.gettempdir()).resolve()) as t,patch.object(m,'_listen_owners',return_value=[{'pid':777,'uid':-1,'port':5580,'executable':'other'}]):
            with self.assertRaises(m.Hold):m.attach_or_start(Path(t),Path(t),prepare_cold_start=prepare)
        prepare.assert_not_called();spawn.assert_not_called()
    def test_invalid_resolver_values_cannot_enter_spawn_arguments(self):
        _,spawn=self.setup_transport()
        bad=[None,[],['127.0.0.1'],['0.0.0.0'],['169.254.1.1'],['224.0.0.1'],['::1'],['1.2.3.999'],[53],['-wipe-data'],RESOLVERS*2,[RESOLVERS[0]]*2,'8.8.8.8']
        for value in bad:
            with self.subTest(value=value),tempfile.TemporaryDirectory(dir=Path(tempfile.gettempdir()).resolve()) as t:
                with self.assertRaisesRegex(m.Hold,'COLD_START_RESOLVER_SET_INVALID'):
                    m.attach_or_start(Path(t),Path(t),prepare_cold_start=lambda:value)
        spawn.assert_not_called()
    def test_resource_hold_from_real_resolver_helper_prevents_spawn(self):
        _,spawn=self.setup_transport()
        with tempfile.TemporaryDirectory(dir=Path(tempfile.gettempdir()).resolve()) as t,patch.object(n,'system_dns') as dns:
            with self.assertRaisesRegex(m.Hold,'COLD_START_MEMORY_HELD'):
                m.attach_or_start(Path(t),Path(t),prepare_cold_start=lambda:n.cold_start_resolvers(lambda:False))
            dns.assert_not_called()
        spawn.assert_not_called()
    def test_arbitrary_callback_errors_are_not_leaked(self):
        _,spawn=self.setup_transport()
        with tempfile.TemporaryDirectory(dir=Path(tempfile.gettempdir()).resolve()) as t:
            with self.assertRaisesRegex(m.Hold,'^COLD_START_DNS_PREFLIGHT_FAILED$'):
                m.attach_or_start(Path(t),Path(t),prepare_cold_start=Mock(side_effect=RuntimeError('private value')))
        spawn.assert_not_called()
    def test_instance_appearing_during_resolver_check_is_reused(self):
        _,spawn=self.setup_transport();appeared=[False];base=self.fake_call
        def call(args,timeout=8):
            if str(args[-1])=='devices' and appeared[0]:return result('emulator-5580 device\n')
            return base(args,timeout)
        def prepare():appeared[0]=True;return RESOLVERS
        with tempfile.TemporaryDirectory(dir=Path(tempfile.gettempdir()).resolve()) as t,patch.object(m,'call',side_effect=call):
            _,started=m.attach_or_start(Path(t),Path(t),prepare_cold_start=prepare)
        self.assertFalse(started);spawn.assert_not_called()
    def test_real_packaging_binding_fixes_both_original_attach_sites(self):
        entry=(TOOLS.parent/'GHARTV_OPEN_REVIEW.command').read_text()
        recipe=ast.parse((TOOLS/'tv-first/package_review53.py').read_text())
        injection=next(ast.literal_eval(node.value) for node in recipe.body if isinstance(node,ast.Assign) and any(isinstance(target,ast.Name) and target.id=='injection' for target in node.targets))
        nodes=ast.parse(injection).body
        i=next(i for i,node in enumerate(nodes) if isinstance(node,ast.Assign) and any(isinstance(target,ast.Name) and target.id=='cold_start_anchor' for target in node.targets))
        ns={'entry':entry}
        exec(compile(ast.Module(body=nodes[i:i+4],type_ignores=[]),'actual_packaging_dns_binding','exec'),ns)
        fixed=ns['entry']
        self.assertEqual(entry.count('transport.attach_or_start(sdk,RUN)'),2)
        self.assertEqual(fixed.count('prepare_cold_start=lambda:'),2)
        # Execute the actual original and corrected android_review to the first
        # mocked VM spawn. No signing, installation, provider or device call runs.
        captured=[]
        for source in (entry,fixed):
            with tempfile.TemporaryDirectory(dir=Path(tempfile.gettempdir()).resolve()) as t:
                _,spawn=self.setup_transport();spawn.side_effect=SpawnObserved
                raw=source.split("<<'PY'\n",1)[1].rsplit('\nPY\n',1)[0]
                function=next(node for node in ast.parse(raw).body if isinstance(node,ast.FunctionDef) and node.name=='android_review')
                ns={'SERIAL':m.SERIAL,'RUN':Path(t),'R':{},'call':self.fake_call,'memory_normal':lambda:True,'source_module':lambda z,path:n,'Hold':m.Hold}
                exec(compile(ast.Module(body=[function],type_ignores=[]),'real_android_review','exec'),ns)
                with patch.object(n,'system_dns',return_value=RESOLVERS):
                    with self.assertRaises(SpawnObserved):ns['android_review'](None,m,Path(t),None,None,{})
                captured.append(spawn.call_args.args[0])
        self.assertNotIn('-dns-server',captured[0])
        self.assertEqual(captured[1][captured[1].index('-dns-server')+1],','.join(RESOLVERS))

if __name__=='__main__':unittest.main()
