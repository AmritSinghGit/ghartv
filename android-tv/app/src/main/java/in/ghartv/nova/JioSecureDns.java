package in.ghartv.nova;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import okhttp3.Dns;

/** System DNS first; multi-provider TLS DNS recovery for Jio-owned API names. */
final class JioSecureDns implements Dns {
    private final SecureDns delegate;

    JioSecureDns() { delegate = SecureDns.systemFirst(JioSecureDns::isJioHost); }
    JioSecureDns(Dns primary, Dns secondary) {
        delegate = new SecureDns(primary, Collections.singletonList(secondary), JioSecureDns::isJioHost, true);
    }
    private JioSecureDns(boolean secureOnly) {
        delegate = secureOnly ? SecureDns.secureOnly(JioSecureDns::isJioHost)
                : SecureDns.systemFirst(JioSecureDns::isJioHost);
    }
    static JioSecureDns secureOnly() { return new JioSecureDns(true); }

    @Override public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        return delegate.lookup(hostname);
    }

    static boolean isJioHost(String hostname) {
        if (hostname == null) return false;
        String host = hostname.trim().toLowerCase(Locale.ROOT);
        return host.equals("jio.com") || host.endsWith(".jio.com")
                || host.equals("jiocinema.com") || host.endsWith(".jiocinema.com");
    }
    long fallbackCount() { return delegate.fallbackCount(); }
    boolean usedRecently() { return delegate.usedRecently(); }
}
