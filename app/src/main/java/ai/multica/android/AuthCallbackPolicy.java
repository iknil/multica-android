package ai.multica.android;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/** Validates the native handoff before a one-use code enters the WebView. */
final class AuthCallbackPolicy {
    static final String EXCHANGE_URL = "https://multica.ai/auth/android/exchange";
    static final long MAX_AGE_MS = 5 * 60 * 1000L;

    private AuthCallbackPolicy() {}

    static boolean isCallback(String value) {
        try {
            URI uri = new URI(value);
            if (uri.getRawFragment() != null || uri.getRawUserInfo() != null
                    || uri.getPort() != -1) return false;
            if ("multica-android".equals(uri.getScheme())) {
                return "auth".equals(uri.getHost()) && "/callback".equals(uri.getPath());
            }
            return "https".equals(uri.getScheme()) && "multica.ai".equals(uri.getHost())
                    && uri.getPort() == -1 && "/auth/android/callback".equals(uri.getPath());
        } catch (URISyntaxException | NullPointerException exception) {
            return false;
        }
    }

    static String code(String value, String expectedState, long createdAt, long now) {
        if (!isCallback(value) || expectedState == null
                || !expectedState.matches("[A-Za-z0-9_-]{16,128}") || createdAt <= 0
                || now < createdAt || now - createdAt > MAX_AGE_MS) return null;
        try {
            URI uri = new URI(value);
            Map<String, String> values = new HashMap<>();
            if (uri.getRawQuery() == null) return null;
            for (String part : uri.getRawQuery().split("&", -1)) {
                String[] pair = part.split("=", 2);
                if (pair.length != 2 || (!"code".equals(pair[0]) && !"state".equals(pair[0]))
                        || values.containsKey(pair[0])) return null;
                values.put(pair[0], pair[1]);
            }
            String state = values.get("state");
            String code = values.get("code");
            if (state == null || code == null || !code.matches("[A-Za-z0-9_-]{16,512}"))
                return null;
            return MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),
                    expectedState.getBytes(StandardCharsets.UTF_8)) ? code : null;
        } catch (URISyntaxException exception) {
            return null;
        }
    }
}
