package ai.multica.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class AuthCallbackPolicyTest {
    private static final String CODE = "AbCdEfGhIjKlMnOpQrStUvWxYz012345";
    private static final String STATE = "correct_state_123456789";
    private static final long START = 1_000_000L;

    @Test public void acceptsAppSpecificAndVerifiedWebCallbacks() {
        assertEquals(CODE, AuthCallbackPolicy.code("multica-android://auth/callback?code="
                + CODE + "&state=" + STATE, STATE, START, START + 1000));
        assertEquals(CODE, AuthCallbackPolicy.code("https://multica.ai/auth/android/callback?code="
                + CODE + "&state=" + STATE, STATE, START, START + 1000));
    }

    @Test public void rejectsForeignHostsExpiredStateAndTokenHandoff() {
        assertNull(AuthCallbackPolicy.code("https://multica.ai.evil.test/auth/android/callback?code="
                + CODE + "&state=" + STATE, STATE, START, START + 1000));
        assertNull(AuthCallbackPolicy.code("multica-android://auth/callback?code="
                + CODE + "&state=wrong", STATE, START, START + 1000));
        assertNull(AuthCallbackPolicy.code("multica-android://auth/callback?code="
                + CODE + "&state=" + STATE, STATE, START, START + AuthCallbackPolicy.MAX_AGE_MS + 1));
        assertNull(AuthCallbackPolicy.code("multica://auth/callback?token=" + CODE,
                STATE, START, START + 1000));
        assertNull(AuthCallbackPolicy.code("multica-android://auth/callback?code="
                + CODE + "&code=" + CODE + "&state=" + STATE,
                STATE, START, START + 1000));
    }
}
