package com.fintrack.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtUtilTest {

    private static final String SECRET =
            "test-secret-must-be-at-least-32-bytes-for-hs256-signing-key-ok";

    private JwtUtil util() {
        return new JwtUtil(SECRET, 15, 30);
    }

    @Test
    void totpChallengeRoundtripReturnsSubject() {
        JwtUtil util = util();
        String userId = UUID.randomUUID().toString();
        String token = util.generateTotpChallengeToken(userId);

        assertEquals(userId, util.validateTotpChallenge(token));
    }

    @Test
    void validateTotpChallengeRejectsAccessToken() {
        JwtUtil util = util();
        String userId = UUID.randomUUID().toString();
        String access = util.generateAccessToken(userId, "ali", "USER");

        assertNull(util.validateTotpChallenge(access));
    }

    @Test
    void validateTotpChallengeRejectsRefreshToken() {
        JwtUtil util = util();
        String userId = UUID.randomUUID().toString();
        String refresh = util.generateRefreshToken(userId);

        assertNull(util.validateTotpChallenge(refresh));
    }

    @Test
    void validateTotpChallengeRejectsGarbage() {
        JwtUtil util = util();
        assertNull(util.validateTotpChallenge("not-a-jwt"));
        assertNull(util.validateTotpChallenge(""));
    }

    @Test
    void validateTotpChallengeRejectsTokenSignedByDifferentKey() {
        JwtUtil a = util();
        JwtUtil b =
                new JwtUtil("another-secret-of-sufficient-length-for-hmac-sha256-signing", 15, 30);
        String token = a.generateTotpChallengeToken(UUID.randomUUID().toString());

        assertNull(b.validateTotpChallenge(token));
    }

    @Test
    void accessTokenClaimsExposeIdentity() {
        JwtUtil util = util();
        String userId = UUID.randomUUID().toString();
        String token = util.generateAccessToken(userId, "ali", "ADMIN");

        assertEquals(userId, util.getUserId(token));
        assertEquals("ali", util.getUsername(token));
        assertEquals("ADMIN", util.getRole(token));
        assertTrue(util.isValid(token));
    }

    @Test
    void isValidRejectsTamperedToken() {
        JwtUtil util = util();
        String token = util.generateAccessToken(UUID.randomUUID().toString(), "ali", "USER");
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertFalse(util.isValid(tampered));
    }
}
