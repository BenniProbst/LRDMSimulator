package org.lrdm.topologies;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import org.lrdm.topologies.exceptions.InsufficientMirrorsForRingException;
import org.lrdm.topologies.validators.SnowflakeTopologyValidator;

@DisplayName("InsufficientMirrorsForRingException Tests")
class InsufficientMirrorsForRingExceptionTest {

    @Test
    @DisplayName("Exception Details für unzureichende Ring-Mirrors")
    void testInsufficientMirrorsExceptionDetails() {
        InsufficientMirrorsForRingException exception = assertThrows(
            InsufficientMirrorsForRingException.class,
            () -> SnowflakeTopologyValidator.validateRingConstruction(2, 5, 2)
        );

        assertEquals(2, exception.getAvailableMirrors());
        assertEquals(5, exception.getRequiredMirrors());
        assertEquals(1, exception.getRingCount());
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("Verfügbar=2"));
        assertTrue(exception.getMessage().contains("Benötigt=5"));
    }

    @Test
    @DisplayName("Zu wenige Ring-Mirrors in Distribution sollte Exception werfen")
    void testInsufficientRingMirrorsInDistribution() {
        InsufficientMirrorsForRingException exception = assertThrows(
            InsufficientMirrorsForRingException.class,
            () -> SnowflakeTopologyValidator.validateMirrorDistribution(5, 2, 3, 3, 2)
        );

        assertEquals(2, exception.getAvailableMirrors());
        assertEquals(3, exception.getRequiredMirrors());
        assertEquals(1, exception.getRingCount());
    }

    @Test
    @DisplayName("Zu wenige Mirrors für Ring-Konstruktion sollte Exception werfen")
    void testInsufficientMirrorsForRingConstruction() {
        InsufficientMirrorsForRingException exception = assertThrows(
            InsufficientMirrorsForRingException.class,
            () -> SnowflakeTopologyValidator.validateRingConstruction(2, 3, 2)
        );

        assertEquals(2, exception.getAvailableMirrors());
        assertEquals(3, exception.getRequiredMirrors());
        assertEquals(1, exception.getRingCount());
    }

    @Test
    @DisplayName("Grenzfall: Ein Mirror weniger als minimal")
    void testBoundaryInsufficientMirrors() {
        assertThrows(InsufficientMirrorsForRingException.class, () -> {
            SnowflakeTopologyValidator.validateRingConstruction(2, 3, 1);
        });
    }

    @Test
    @DisplayName("Gültige Ring-Konstruktion mit ausreichenden Mirrors")
    void testSufficientMirrorsForRing() {
        assertDoesNotThrow(() -> {
            SnowflakeTopologyValidator.validateRingConstruction(9, 3, 2);
        });

        assertDoesNotThrow(() -> {
            SnowflakeTopologyValidator.validateRingConstruction(15, 5, 3);
        });

        // Exakt minimale Anzahl
        assertDoesNotThrow(() -> {
            SnowflakeTopologyValidator.validateRingConstruction(3, 3, 1);
        });
    }

    @Test
    @DisplayName("Null Mirrors sollten gültig sein")
    void testZeroMirrorsValidForRingConstruction() {
        assertDoesNotThrow(() -> {
            SnowflakeTopologyValidator.validateRingConstruction(0, 3, 2);
        });
    }
}