package ai.multica.android;

import static org.junit.Assert.*;
import org.junit.Test;

public class UrlPolicyTest {
    @Test public void onlyProductionOriginStaysInApp() {
        assertTrue(UrlPolicy.isAppUrl("https://multica.ai/acme/issues/GANY-26?tab=activity"));
        assertTrue(UrlPolicy.isAppUrl("https://MULTICA.AI/login"));
        assertFalse(UrlPolicy.isAppUrl("https://multica.ai.evil.example/login"));
        assertFalse(UrlPolicy.isAppUrl("http://multica.ai/login"));
        assertFalse(UrlPolicy.isAppUrl("https://multica.ai:444/login"));
        assertFalse(UrlPolicy.isAppUrl("javascript:alert(1)"));
        assertFalse(UrlPolicy.isAppUrl("file:///etc/passwd"));
        assertTrue(UrlPolicy.isAppUrl("https://multica.ai/search?q=two words|more"));
        assertFalse(UrlPolicy.isAppUrl("https://multica.ai\\@evil.example/path"));
    }

    @Test public void blobExportMustBelongToAppOrigin() {
        assertTrue(UrlPolicy.isAppBlobUrl("blob:https://multica.ai/1234"));
        assertFalse(UrlPolicy.isAppBlobUrl("blob:https://evil.example/1234"));
    }

    @Test public void externalDownloadsMustUseHttps() {
        assertTrue(UrlPolicy.isWebUrl("https://storage.example.com/file.pdf"));
        assertFalse(UrlPolicy.isWebUrl("http://storage.example.com/file.pdf"));
        assertFalse(UrlPolicy.isWebUrl("data:text/html,hello"));
    }

    @Test public void onlyKnownDeviceSchemesAreAllowed() {
        assertTrue(UrlPolicy.isExternalIntentUrl("mailto:hello@example.com"));
        assertTrue(UrlPolicy.isExternalIntentUrl("tel:+123456789"));
        assertFalse(UrlPolicy.isExternalIntentUrl("intent://something"));
        assertFalse(UrlPolicy.isExternalIntentUrl("javascript:alert(1)"));
    }
}
