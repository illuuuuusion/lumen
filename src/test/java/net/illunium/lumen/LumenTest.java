package net.illunium.lumen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LumenTest {

    @Test
    void requireEnvReturnsExistingValue() {
        assertEquals(System.getenv("PATH"), Lumen.requireEnv("PATH"));
    }

    @Test
    void requireEnvFailsOnMissingValue() {
        assertThrows(IllegalStateException.class, () -> Lumen.requireEnv("LUMEN_DEFINITELY_UNSET"));
    }
}
