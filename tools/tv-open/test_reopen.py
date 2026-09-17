"""Bounded transport fixtures and real TCP close/rebind regression checks."""
from pathlib import Path
import importlib.util, unittest, socket, tempfile, subprocess, os
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('tv_local',Path(__file__).parents[1]/'tv_local.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
def result(text='',code=0,err=''):
    return subprocess.CompletedProcess([],code,text,err)
class ReopenTests(unittest.TestCase):
    def call(self,args,timeout=8):
        args=list(map(str,args))
        if args[-1]=='devices':return result('List of devices attached\n')
        if args[-1]=='-list-avds':return result(m.AVD+'\n')
        if 'getprop' in args:return result('1\n')
        if args[-3:]==['emu','avd','name']:return result(m.AVD+'\nOK\n')
        return result()
    def mocks(self):
        from contextlib import ExitStack
        s=ExitStack();self.addCleanup(s.close)
        s.enter_context(patch.object(m,'call',side_effect=self.call))
        s.enter_context(patch.object(m,'avd_processes',return_value=[]))
        s.enter_context(patch.object(m,'_listen_owners',return_value=[]))
        s.enter_context(patch.object(m,'_reusable_pair',return_value=[{'state':'REUSABLE','port':5580},{'state':'REUSABLE','port':5581}]))
        s.enter_context(patch.object(m,'verify_target'))
        s.enter_context(patch.object(m.time,'sleep'))
        spawn=s.enter_context(patch.object(m.subprocess,'Popen'));spawn.return_value.poll.return_value=None
        return spawn
    def test_stopped_starts_one_existing_avd(self):
        with tempfile.TemporaryDirectory() as t:
            spawn=self.mocks();adb,started=m.attach_or_start(Path(t),Path(t))
            self.assertTrue(started);self.assertEqual(spawn.call_count,1)
            argv=spawn.call_args.args[0];self.assertIn(m.AVD,argv)
            self.assertEqual(argv[argv.index('-port')+1],'5580');self.assertNotIn('-wipe-data',argv)
    def test_online_reused_without_any_spawn(self):
        with tempfile.TemporaryDirectory() as t:
            spawn=self.mocks();base=self.call
            def call(args,timeout=8):
                return result('emulator-5580 device\n') if str(args[-1])=='devices' else base(args,timeout)
            with patch.object(m,'call',side_effect=call):_,started=m.attach_or_start(Path(t),Path(t))
            self.assertFalse(started);spawn.assert_not_called()
    def test_closing_offline_transport_disappears(self):
        with tempfile.TemporaryDirectory() as t:
            spawn=self.mocks();n=0;base=self.call
            def call(args,timeout=8):
                nonlocal n
                if str(args[-1])=='devices':
                    n+=1;return result('emulator-5580 offline\n' if n==1 else '')
                return base(args,timeout)
            with patch.object(m,'call',side_effect=call):_,started=m.attach_or_start(Path(t),Path(t))
            self.assertTrue(started);self.assertEqual(spawn.call_count,1)
    def test_unknown_port_owner_named_and_preserved(self):
        with tempfile.TemporaryDirectory() as t:
            spawn=self.mocks()
            with patch.object(m,'_listen_owners',return_value=[{'pid':731,'uid':os.getuid(),'port':5580,'executable':'other-server'}]):
                with self.assertRaisesRegex(m.Hold,'PORT_5580_LISTENER_other-server_PID_731_PRESERVED'):
                    m.attach_or_start(Path(t),Path(t))
            spawn.assert_not_called()
    def test_own_hidden_instance_waited_then_attached(self):
        with tempfile.TemporaryDirectory() as t:
            spawn=self.mocks();n=0;base=self.call
            def call(args,timeout=8):
                nonlocal n
                if str(args[-1])=='devices':
                    n+=1;return result('emulator-5580 device\n' if n>1 else '')
                return base(args,timeout)
            with patch.object(m,'call',side_effect=call),patch.object(m,'avd_processes',return_value=[(733,'qemu -avd '+m.AVD)]),patch.object(m,'_listen_owners',return_value=[{'pid':733,'uid':os.getuid(),'port':5580,'executable':'qemu'}]):
                _,started=m.attach_or_start(Path(t),Path(t))
            self.assertFalse(started);spawn.assert_not_called()
    def test_timeout_does_not_kill_or_duplicate(self):
        with tempfile.TemporaryDirectory() as t:
            spawn=self.mocks()
            with patch.object(m,'avd_processes',return_value=[(733,'qemu -avd '+m.AVD)]),patch.object(m.time,'monotonic',side_effect=[0,1,2,31]):
                with self.assertRaisesRegex(m.Hold,'NO_DUPLICATE'):
                    m.attach_or_start(Path(t),Path(t))
            spawn.assert_not_called()
    def test_different_canonical_avd_preserved(self):
        with tempfile.TemporaryDirectory() as t:
            spawn=self.mocks();base=self.call
            def call(args,timeout=8):
                if str(args[-1])=='devices':return result('emulator-5580 device\n')
                if list(map(str,args))[-3:]==['emu','avd','name']:return result('Different_AVD\nOK\n')
                return base(args,timeout)
            with patch.object(m,'call',side_effect=call),patch.object(m,'verify_target',side_effect=m.Hold('bad')):
                with self.assertRaisesRegex(m.Hold,'DIFFERENT_AVD_PRESERVED'):
                    m.attach_or_start(Path(t),Path(t))
            spawn.assert_not_called()
    def test_lsof_parsing_filters_listeners(self):
        with patch.object(m,'call',return_value=result('p124\ncqemu-system\nu'+str(os.getuid())+'\n')),patch('shutil.which',return_value='/usr/bin/lsof'):
            self.assertEqual(m._listen_owners(5580),[{'pid':124,'port':5580,'executable':'qemu-system','uid':os.getuid()}])
    def test_unreadable_listener_state_not_assumed_empty(self):
        with patch.object(m,'call',return_value=result('',1,'lsof: bad option')),patch('shutil.which',return_value='/usr/bin/lsof'):
            with self.assertRaises(m.Hold):m._listen_owners(5580)
    def test_missing_lsof_is_explicit(self):
        with patch('shutil.which',return_value=None),patch.object(Path,'is_file',return_value=False):
            with self.assertRaisesRegex(m.Hold,'LSOF_NOT_AVAILABLE'):m._listen_owners(5580)
    def test_real_time_wait_does_not_mean_listener(self):
        listener=socket.socket();listener.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
        listener.bind(('127.0.0.1',0));port=listener.getsockname()[1];listener.listen(1)
        client=socket.create_connection(('127.0.0.1',port));accepted,_=listener.accept()
        accepted.shutdown(socket.SHUT_WR);self.assertEqual(client.recv(1),b'')
        client.close();accepted.close();listener.close()
        with socket.socket() as plain:
            with self.assertRaises(OSError):plain.bind(('127.0.0.1',port))
        with socket.socket() as reusable:
            reusable.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
            reusable.bind(('127.0.0.1',port));reusable.listen(1)
    def test_real_listener_not_hidden_by_reuseaddr(self):
        with socket.socket() as first:
            first.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1);first.bind(('127.0.0.1',0));first.listen(1)
            with socket.socket() as second:
                second.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
                with self.assertRaises(OSError):second.bind(first.getsockname())
    def test_no_global_shutdown_or_destructive_flags(self):
        s=Path(m.__file__).read_text()
        for forbidden in ['kill-server','killall','-wipe-data','SO_REUSEPORT,','SIGKILL','SIGTERM']:
            self.assertNotIn(forbidden,s)
if __name__=='__main__':unittest.main()
