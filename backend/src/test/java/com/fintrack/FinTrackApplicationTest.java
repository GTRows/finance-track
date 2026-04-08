package com.fintrack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Verifies the application main class exists and is valid.
 */
class FinTrackApplicationTest {

    @Test
    void contextLoadsMainClass() {
        assertDoesNotThrow(() -> Class.forName("com.fintrack.FinTrackApplication"));
    }
}
