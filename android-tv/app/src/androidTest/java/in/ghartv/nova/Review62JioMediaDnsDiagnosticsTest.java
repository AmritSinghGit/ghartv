package in.ghartv.nova;

import android.content.Context;

import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

import okhttp3.OkHttpClient;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class Review62JioMediaDnsDiagnosticsTest {
    private Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}

    @Test public void mediaAndPreviewClientsUseSystemFirstSecureDnsAndNeverTor(){
        OkHttpClient live=(OkHttpClient)JioNetwork.media(context());
        OkHttpClient preview=(OkHttpClient)JioNetwork.preview(context());
        assertTrue(live.dns() instanceof SecureDns);
        assertSame(live.dns(),preview.dns());
        assertSame(JioNetwork.mediaDns(),live.dns());
        assertFalse(JioNetwork.torUsedRecently());
    }

    @Test public void productionMediaAndDrmFactoriesUseTheRealOkHttpRoute(){
        assertTrue(JioMediaDataSource.liveHttp(context(),Collections.emptyMap())
                instanceof OkHttpDataSource.Factory);
        assertTrue(JioMediaDataSource.previewHttp(context(),Collections.emptyMap())
                instanceof OkHttpDataSource.Factory);
        assertTrue(JioMediaDataSource.liveSourceFactory(context(),Collections.emptyMap())
                instanceof DefaultMediaSourceFactory);
        assertTrue(JioMediaDataSource.previewSourceFactory(context(),Collections.emptyMap())
                instanceof DefaultMediaSourceFactory);
    }

    @Test public void secureDnsRecoveryNeverAdmitsLocalOrNumericTargets(){
        assertFalse(SecureDns.publicHostname("localhost"));
        assertFalse(SecureDns.publicHostname("router.local"));
        assertFalse(SecureDns.publicHostname("192.168.1.1"));
        assertFalse(SecureDns.publicHostname("10.0.0.1"));
        assertTrue(SecureDns.publicHostname("jiotv.catchup.cdn.jio.com"));
    }

    @Test public void connectionDiagnosticIncludesJioMediaAndWidevineStages() throws Exception {
        java.lang.reflect.Field field=NetworkDiagnostics.class.getDeclaredField("HOSTS");
        field.setAccessible(true);
        String[][] hosts=(String[][])field.get(null);
        boolean media=false;
        for(String[] row:hosts)if("jio_media_cdn".equals(row[0]))media=true;
        assertTrue(media);
        java.lang.reflect.Method method=NetworkDiagnostics.class.getDeclaredMethod("widevine");
        method.setAccessible(true);
        JSONObject result=(JSONObject)method.invoke(null);
        assertTrue(result.has("supported"));
        assertTrue(result.has("security_level"));
    }
}
