package ai.multica.android;

import java.net.URI;
import java.net.URISyntaxException;

/** URL decisions shared by navigation, deep links and downloads. */
final class UrlPolicy {
    static final String HOME = "https://multica.ai/login";

    private UrlPolicy() {}

    static boolean isWebUrl(String value) {
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (URISyntaxException | NullPointerException exception) {
            return false;
        }
    }

    static boolean isAppUrl(String value) {
        if (!isWebUrl(value)) return false;
        try {
            URI uri = new URI(value);
            return "multica.ai".equalsIgnoreCase(uri.getHost()) && uri.getPort() == -1;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    static boolean isExternalIntentUrl(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            return "mailto".equalsIgnoreCase(scheme) || "tel".equalsIgnoreCase(scheme)
                    || "sms".equalsIgnoreCase(scheme);
        } catch (URISyntaxException | NullPointerException exception) {
            return false;
        }
    }
}
