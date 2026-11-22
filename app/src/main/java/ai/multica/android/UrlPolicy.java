package ai.multica.android;

import java.net.URI;
import java.net.URISyntaxException;

/** URL decisions shared by navigation, deep links and downloads. */
final class UrlPolicy {
    static final String HOME = "https://multica.ai/login";

    private UrlPolicy() {}

    private static URI parse(String value) throws URISyntaxException {
        if (value == null || value.isEmpty()) throw new URISyntaxException("", "empty URL");
        // Browser links can contain these unescaped characters. The host and
        // scheme still need strict URI parsing so lookalike hosts stay external.
        return new URI(value.replace(" ", "%20").replace("|", "%7C"));
    }

    static boolean isWebUrl(String value) {
        try {
            URI uri = parse(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (URISyntaxException | NullPointerException exception) {
            return false;
        }
    }

    static boolean isAppUrl(String value) {
        if (!isWebUrl(value)) return false;
        try {
            URI uri = parse(value);
            return "multica.ai".equalsIgnoreCase(uri.getHost()) && uri.getPort() == -1;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    static boolean isExternalIntentUrl(String value) {
        try {
            URI uri = parse(value);
            String scheme = uri.getScheme();
            return "mailto".equalsIgnoreCase(scheme) || "tel".equalsIgnoreCase(scheme)
                    || "sms".equalsIgnoreCase(scheme);
        } catch (URISyntaxException | NullPointerException exception) {
            return false;
        }
    }

    static boolean isAppBlobUrl(String value) {
        return value != null && value.startsWith("blob:") && isAppUrl(value.substring(5));
    }

    static boolean isGoogleOAuthUrl(String value) {
        if (!isWebUrl(value)) return false;
        try {
            URI uri = parse(value);
            return "accounts.google.com".equalsIgnoreCase(uri.getHost())
                    && uri.getPort() == -1 && uri.getPath() != null
                    && uri.getPath().startsWith("/o/oauth2/");
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
