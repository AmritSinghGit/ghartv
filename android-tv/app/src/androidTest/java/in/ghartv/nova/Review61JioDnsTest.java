package in.ghartv.nova;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import okhttp3.OkHttpClient;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class Review61JioDnsTest {
    @Test public void jioApiAndMediaGetScopedDnsRecoveryWithoutTor() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(JioNetwork.api(context) instanceof OkHttpClient);
        assertTrue(((OkHttpClient) JioNetwork.api(context)).dns() instanceof JioSecureDns);
        assertTrue(JioNetwork.dns() instanceof JioSecureDns);

        assertTrue(JioNetwork.media(context) instanceof OkHttpClient);
        assertTrue(JioNetwork.preview(context) instanceof OkHttpClient);
        assertTrue(((OkHttpClient) JioNetwork.media(context)).dns() instanceof SecureDns);
        assertTrue(((OkHttpClient) JioNetwork.preview(context)).dns() instanceof SecureDns);
        assertSame(JioNetwork.mediaDns(),((OkHttpClient) JioNetwork.media(context)).dns());

        assertFalse(JioNetwork.torUsedRecently());
        assertFalse(JioNetwork.recoveryLabel().toLowerCase(java.util.Locale.ROOT).contains("tor"));
    }
}
