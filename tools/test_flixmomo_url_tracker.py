import json, tempfile, unittest
from pathlib import Path
from unittest import mock
import flixmomo_url_tracker as t

POLICY = 'static final String HOME="https://flixmomo.bet/";\n private static final String[] HOSTS={"flixmomo.app","flixmomo.st","www.flixmomo.st","flixmomo.bet","www.flixmomo.bet"};'


class Tracker(unittest.TestCase):
    def setUp(self):
        self.known = t.known_hosts(POLICY)

    def test_reads_policy(self):
        self.assertIn('flixmomo.bet', self.known)
        self.assertEqual(t.home_url(POLICY), 'https://flixmomo.bet/')

    def test_banner_with_new_address_is_proposed(self):
        html = '<div class="banner">Our new address is <b>FlixMomo.CC</b>, bookmark it!</div>'
        self.assertEqual(t.candidates(html, self.known), ['flixmomo.cc'])

    def test_href_and_www_are_normalised(self):
        html = '<a href="https://www.flixmomo.to/">new domain</a> flixmomo.to'
        self.assertEqual(t.candidates(html, self.known), ['flixmomo.to'])

    def test_known_hosts_are_not_proposed(self):
        self.assertEqual(t.candidates('visit flixmomo.bet or www.flixmomo.st', self.known), [])

    def test_lookalikes_are_never_proposed(self):
        html = 'flixmomo-login.xyz flixmomo.bet.evil.com myflixmomo.cc flixmomo.cc-pay.net xflixmomo.io'
        self.assertEqual(t.candidates(html, self.known), [])

    def test_proposal_file_and_exit_codes(self):
        with tempfile.TemporaryDirectory() as d:
            page = Path(d) / 'p.html'; page.write_text('Moved to flixmomo.cc')
            pol = Path(d) / 'P.java'; pol.write_text(POLICY)
            with mock.patch('builtins.print'):
                self.assertEqual(t.main(['--html', str(page), '--policy', str(pol), '--out', d + '/out']), 3)
            data = json.loads(next(Path(d, 'out').glob('*-PROPOSAL.json')).read_text())
            self.assertEqual(data['proposed_hosts'], ['flixmomo.cc'])
            self.assertEqual(data['status'], 'PROPOSED_OWNER_APPROVAL_NEEDED')
            page.write_text('nothing new, still flixmomo.bet')
            with mock.patch('builtins.print'):
                self.assertEqual(t.main(['--html', str(page), '--policy', str(pol), '--out', d + '/out2']), 0)
            self.assertFalse(Path(d, 'out2').exists())

    def test_redirect_to_new_name_is_proposed(self):
        with tempfile.TemporaryDirectory() as d:
            pol = Path(d) / 'P.java'; pol.write_text(POLICY)
            with mock.patch.object(t, 'fetch', return_value=('<html>home</html>', 'https://flixmomo.lol/')), mock.patch('builtins.print'):
                self.assertEqual(t.main(['--policy', str(pol), '--out', d + '/o']), 3)
            self.assertEqual(json.loads(next(Path(d, 'o').glob('*.json')).read_text())['proposed_hosts'], ['flixmomo.lol'])

    def test_unreadable_page_proposes_nothing(self):
        with tempfile.TemporaryDirectory() as d:
            pol = Path(d) / 'P.java'; pol.write_text(POLICY)
            with mock.patch.object(t, 'fetch', side_effect=OSError('blocked')), mock.patch('builtins.print'):
                self.assertEqual(t.main(['--policy', str(pol), '--out', d + '/o']), 2)
            self.assertFalse(Path(d, 'o').exists())

    def test_reads_the_real_policy_on_pr1_branch(self):
        import subprocess
        try:
            text = subprocess.check_output(['git', '-C', str(t.ROOT), 'show', t.POLICY_REF], text=True, stderr=subprocess.DEVNULL)
        except (subprocess.CalledProcessError, FileNotFoundError):
            self.skipTest('PR #1 branch not fetched')
        self.assertTrue({'flixmomo.bet', 'flixmomo.st'} <= t.known_hosts(text))
        self.assertTrue(t.home_url(text).startswith('https://flixmomo.'))


if __name__ == '__main__':
    unittest.main()
