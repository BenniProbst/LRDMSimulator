package org.lrdm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

import org.lrdm.topologies.exceptions.InvalidMirrorDistributionException;
import org.lrdm.topologies.validators.SnowflakeTopologyValidator;

@DisplayName("InvalidMirrorDistributionException Tests")
class InvalidMirrorDistributionExceptionTest {

    private static final int DEFAULT_MIN_RING_MIRROR_COUNT = 3;
    private static final int DEFAULT_MAX_RING_LAYERS = 2;

    private InvalidMirrorDistributionException createMirrorDistributionException(
            int totalMirrors, int ringMirrors, int starMirrors) {
        return assertThrows(
                InvalidMirrorDistributionException.class,
                () -> SnowflakeTopologyValidator.validateMirrorDistribution(
                        totalMirrors, ringMirrors, starMirrors,
                        DEFAULT_MIN_RING_MIRROR_COUNT, DEFAULT_MAX_RING_LAYERS)
        );
    }

    @Test
    @DisplayName("Exception Details für ungültige Gesamtanzahl")
    void testInvalidTotalMirrorsExceptionDetails() {
        InvalidMirrorDistributionException exception = createMirrorDistributionException(-5, 0, 0);

        assertEquals(-5, exception.getTotalMirrors());
        assertEquals(0, exception.getRingMirrors());
        assertEquals(0, exception.getStarMirrors());
        assertEquals("Gesamtanzahl der Mirrors muss größer als 0 sein", exception.getReason());
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("Total=-5"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -5})
    @DisplayName("Ungültige Gesamtanzahl Mirrors sollte Exception werfen")
    void testInvalidTotalMirrors(int invalidTotal) {
        InvalidMirrorDistributionException exception = createMirrorDistributionException(invalidTotal, 0, 0);

        assertEquals(invalidTotal, exception.getTotalMirrors());
        assertTrue(exception.getMessage().contains("Gesamtanzahl der Mirrors muss größer als 0 sein"));
    }

    @Test
    @DisplayName("Summe Ring + Stern != Total sollte Exception werfen")
    void testMismatchedMirrorSum() {
        InvalidMirrorDistributionException exception = createMirrorDistributionException(10, 6, 3);

        assertEquals(10, exception.getTotalMirrors());
        assertEquals(6, exception.getRingMirrors());
        assertEquals(3, exception.getStarMirrors());
        assertTrue(exception.getMessage().contains("entspricht nicht der Gesamtanzahl"));
    }

    @Test
    @DisplayName("Negative Ring-Mirrors sollte Exception werfen")
    void testNegativeRingMirrors() {
        InvalidMirrorDistributionException exception = createMirrorDistributionException(5, -2, 7);

        assertTrue(exception.getMessage().contains("nicht-negativ sein"));
    }

    @Test
    @DisplayName("Negative Stern-Mirrors sollte Exception werfen")
    void testNegativeStarMirrors() {
        InvalidMirrorDistributionException exception = createMirrorDistributionException(5, 7, -2);

        assertTrue(exception.getMessage().contains("nicht-negativ sein"));
    }

    @Test
    @DisplayName("Gültige Mirror-Verteilung sollte keine Exception werfen")
    void testValidMirrorDistribution() {
        assertDoesNotThrow(() ->
                SnowflakeTopologyValidator.validateMirrorDistribution(10, 7, 3, DEFAULT_MIN_RING_MIRROR_COUNT, DEFAULT_MAX_RING_LAYERS));
        assertDoesNotThrow(() ->
                SnowflakeTopologyValidator.validateMirrorDistribution(5, 5, 0, DEFAULT_MIN_RING_MIRROR_COUNT, DEFAULT_MAX_RING_LAYERS));
        assertDoesNotThrow(() ->
                SnowflakeTopologyValidator.validateMirrorDistribution(8, 0, 8, DEFAULT_MIN_RING_MIRROR_COUNT, DEFAULT_MAX_RING_LAYERS));
    }

    @Test
    @DisplayName("Exakt minimale Ring-Mirrors sollten gültig sein")
    void testExactMinimalRingMirrors() {
        assertDoesNotThrow(() ->
                SnowflakeTopologyValidator.validateMirrorDistribution(6, 3, 3, DEFAULT_MIN_RING_MIRROR_COUNT, DEFAULT_MAX_RING_LAYERS));
    }

    @Test
    @DisplayName("Null Ring-Mirrors sollten gültig sein")
    void testZeroRingMirrors() {
        assertDoesNotThrow(() ->
                SnowflakeTopologyValidator.validateMirrorDistribution(5, 0, 5, DEFAULT_MIN_RING_MIRROR_COUNT, DEFAULT_MAX_RING_LAYERS));
    }
}