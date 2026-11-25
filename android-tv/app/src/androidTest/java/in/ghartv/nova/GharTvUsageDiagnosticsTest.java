package in.ghartv.nova;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real app-private persistence tests; runs only in isolated instrumentation package. */
@RunWith(AndroidJUnit4.class)
public final class GharTvUsageDiagnosticsTest {
    private GharTvUsageDiagnostics report;
    @Before public void start() {
        report = GharTvUsageDiagnostics.get(
            InstrumentationRegistry.getInstrumentation().getTargetContext());
        report.clear();
    }
    @After public void stop() {
        report.clear();
    }
    @Test public void noDataRecordedBeforeExplicitConsent() {
        assertFalse(report.enabled());
        report.record("PHONE_CONNECTED");
        report.record("SEARCH_SUBMIT");
        assertEquals(0, report.report().optInt("total"));
        assertEquals(0, report.report().optJSONArray("steps").length());
    }
    @Test public void consentRecordsTechnicalOutcomesOnly() {
        report.begin();
        assertTrue(report.enabled());
        report.record("PHONE_CONNECTED");
        report.record("SEARCH_SUBMIT");
        report.record("SEARCH_SENT");
        report.record("SOURCE_UNAVAILABLE");
        JSONObject r = report.report();
        assertTrue(r.optInt("total") >= 5);
        assertEquals(1, r.optJSONObject("counts").optInt("SEARCH_SENT"));
        assertEquals(1, r.optJSONObject("counts").optInt("SOURCE_UNAVAILABLE"));
        assertFalse(r.optBoolean("uploaded", true));
        assertEquals(7, r.optInt("retention_days"));
    }
    @Test public void rawSearchTitlesUrlsAndCredentialsCannotBecomeReportEvents() {
        report.begin();
        report.record("https://example.invalid/movie/secret");
        report.record("password=example-secret");
        report.record("SEARCH_SUBMIT Sample Movie");
        report.record("PAIRING_KEY_abc");
        report.record("SOURCE_SELECT");
        String json = report.report().toString();
        assertFalse(json, json.contains("example.invalid"));
        assertFalse(json, json.contains("secret"));
        assertFalse(json, json.contains("Sample Movie"));
        assertFalse(json, json.contains("PAIRING_KEY"));
        assertEquals(1, report.report().optJSONObject("counts").optInt("SOURCE_SELECT"));
    }
    @Test public void stopPreventsFurtherRecordingAndClearDeletesEvenConsentTrace() {
        report.begin();
        report.record("PHONE_CONNECTED");
        int original = report.report().optInt("total");
        report.stop();
        assertFalse(report.enabled());
        report.record("SEARCH_SUBMIT");
        assertEquals(original + 1, report.report().optInt("total"));
        report.clear();
        assertEquals(0, report.report().optInt("total"));
        assertFalse(report.enabled());
    }
    @Test public void newPairingRequiresItsOwnOptInToSeePriorReport() {
        assertFalse(report.sessionAuthorized());
        report.begin();
        assertTrue(report.sessionAuthorized());
        report.record("SEARCH_SENT");
        report.stop();
        assertTrue("Viewer can inspect report after stopping this session",report.sessionAuthorized());
        report.endSession();
        assertFalse("A later pairing cannot automatically read prior usage",report.sessionAuthorized());
        assertFalse(report.enabled());
        assertTrue("The TV owner still retains an explicitly consented summary",
            report.report().optInt("total") > 0);
    }
    @Test public void disconnectedPhoneNeverRecordsPassiveTvUsage() throws Exception {
        report.begin();
        java.lang.reflect.Field field=HouseholdPhoneSession.class.getDeclaredField("usage");
        field.setAccessible(true);
        Object previous=field.get(null);
        java.lang.reflect.Method record=HouseholdPhoneSession.class.getDeclaredMethod("record",String.class);
        record.setAccessible(true);
        try {
            field.set(null,report);
            int before=report.report().optInt("total");
            // A test-only isolated app with no paired socket: no passive TV usage.
            record.invoke(null,"MODE_SEARCH");
            record.invoke(null,"PLAYBACK_PLAYING");
            assertEquals(before,report.report().optInt("total"));
            record.invoke(null,"PHONE_DISCONNECTED");
            assertEquals(before+1,report.report().optInt("total"));
        } finally {field.set(null,previous);}
    }
    @Test public void boundedTraceAndTypingThrottleNeverSaveRawCharacters() {
        report.begin();
        for (int i=0;i<75;i++) {
            report.record((i&1)==0?"LIVE_TYPING":"SEARCH_SENT");
        }
        JSONObject r=report.report();
        assertTrue(r.optJSONArray("steps").length()<=36);
        assertTrue(r.optInt("total")>40);
        assertFalse(r.toString().contains("query"));
        assertFalse(r.toString().contains("url"));
    }
}
