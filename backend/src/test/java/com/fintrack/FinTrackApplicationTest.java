package com.fintrack;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

/** Verifies the application main class exists and is valid. */
class FinTrackApplicationTest {

    @Test
    void contextLoadsMainClass() {
        assertDoesNotThrow(() -> Class.forName("com.fintrack.FinTrackApplication"));
    }
}
