package in.ghartv.nova;
public final class TorWebUrlPolicySmoke {
    static int count=0;
    static void ok(boolean b){count++;if(!b)throw new AssertionError("URL check "+count);}
    public static void main(String[]a){
      ok("https://example.org".equals(TorWebUrlPolicy.normalize("example.org")));
      ok(TorWebUrlPolicy.allowed("https://www.wikipedia.org/wiki/Main_Page"));
      ok(TorWebUrlPolicy.allowed("https://example.org/search?q=test"));
      ok(TorWebUrlPolicy.allowed("https://testsite.onion/"));
      for(String invalid:new String[]{"http://example.com","javascript:alert(1)","file:///etc/passwd",
          "https://127.0.0.1/","https://192.168.1.2/","https://10.0.0.1/","https://[::1]/",
          "https://localhost/","https://api.github.com/","https://user:pw@example.com/",
          "https://example.com:8443/","https://example.com/a#b","https://example.test/",
          "https://flixmomo.bet.evil.invalid/","https://exa mple.com/","https://example.com@localhost/"})
            ok(!TorWebUrlPolicy.allowed(invalid));
      System.out.println("TOR_BROWSER_URL_POLICY="+count+" PASS");
    }
}
