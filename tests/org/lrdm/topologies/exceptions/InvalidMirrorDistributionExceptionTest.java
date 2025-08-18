
package org.lrdm.topologies.exceptions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

import org.lrdm.topologies.validators.SnowflakeTopologyValidator;

@DisplayName("InvalidMirrorDistributionException Tests")
class InvalidMirrorDistributionExceptionTest {

    private static final int DEFAULT_MIN_RING_MIRROR_COUNT = 3;
    private static final int DEFAULT_MAX_RING_LAYERS = 2;

    // Helper method für konsistente Exception-Erstellung
    private InvalidMirrorDistributionException createMirrorDistributionException(
            int totalMirrors, int ringMirrors, int starMirrors) {
        return assertThrows(
                InvalidMirrorDistributionException.class,
                () -> SnowflakeTopologyValidator.validateMirrorDistribution(
                        totalMirrors, ringMirrors, starMirrors,
                        DEFAULT_MIN_RING_MIRROR_COUNT, DEFAULT_MAX_RING_LAYERS)
        );
    }

    @Nested
    @DisplayName("Ungültige Gesamtanzahl Tests")
    class InvalidTotalMirrorsTests {

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
        @ValueSource(ints = {0, -1, -5, -10, -100})
        @DisplayName("Ungültige Gesamtanzahl Mirrors sollte Exception werfen")
        void testInvalidTotalMirrors(int invalidTotal) {
            InvalidMirrorDistributionException exception = createMirrorDistributionException(invalidTotal, 0, 0);

            assertEquals(invalidTotal, exception.getTotalMirrors());
            assertTrue(exception.getMessage().contains("Gesamtanzahl der Mirrors muss größer als 0 sein"));
        }

        @Test
        @DisplayName("Null Mirrors mit korrekter Summe")
        void testZeroMirrorsButCorrectSum() {
            InvalidMirrorDistributionException exception = createMirrorDistributionException(0, 0, 0);

            assertEquals(0, exception.getTotalMirrors());
            assertTrue(exception.getMessage().contains("Gesamtanzahl der Mirrors muss größer als 0 sein"));
        }
    }

    @Nested
    @DisplayName("Summen-Inkonsistenz Tests")
    class SumInconsistencyTests {

        @Test
        @DisplayName("Summe Ring + Stern != Total sollte Exception werfen")
        void testMismatchedMirrorSum() {
            InvalidMirrorDistributionException exception = createMirrorDistributionException(10, 6, 3);

            assertEquals(10, exception.getTotalMirrors());
            assertEquals(6, exception.getRingMirrors());
            assertEquals(3, exception.getStarMirrors());
            assertTrue(exception.getMessage().contains("entspricht nicht der Gesamtanzahl"));
        }

        @ParameterizedTest
        @CsvSource({
                "10, 3, 4", // 3+4=7, sollte 10 sein
                "5, 2, 1",  // 2+1=3, sollte 5 sein
                "15, 8, 4", // 8+4=12, sollte 15 sein
                "20, 12, 5" // 12+5=17, sollte 20 sein
        })
        @DisplayName("Verschiedene Summen-Inkonsistenzen")
        void testVariousSumInconsistencies(int total, int ring, int star) {
            InvalidMirrorDistributionException exception = createMirrorDistributionException(total, ring, star);

            assertEquals(total, exception.getTotalMirrors());
            assertEquals(ring, exception.getRingMirrors());
            assertEquals(star, exception.getStarMirrors());
            assertTrue(exception.getMessage().contains("entspricht nicht der Gesamtanzahl"));
        }

        @Test
        @DisplayName("Summe größer als Total")
        void testSumGreaterThanTotal() {
            InvalidMirrorDistributionException exception = createMirrorDistributionException(5, 4, 3); // 4+3=7 > 5

            assertTrue(exception.getMessage().contains("entspricht nicht der Gesamtanzahl"));
        }

        @Test
        @DisplayName("Summe kleiner als Total")
        void testSumLessThanTotal() {
            InvalidMirrorDistributionException exception = createMirrorDistributionException(10, 2, 3); // 2+3=5 < 10

            assertTrue(exception.getMessage().contains("entspricht nicht der Gesamtanzahl"));
        }
    }

    @Nested
    @DisplayName("Negative Werte Tests")
    class NegativeValuesTests {

        @Test
        @DisplayName("Negative Ring-Mirrors sollte Exception werfen")
        void testNegativeRingMirrors() {
            InvalidMirrorDistributionException exception = createMirrorDistributionException(5, -2, 7);

            assertEquals(5, exception.getTotalMirrors());
            assertEquals(-2, exception.getRingMirrors());
            assertEquals(7, exception.getStarMirrors());
            assertTrue(exception.getMessage().contains("nicht-negativ sein"));
        }

        @Test
        @DisplayName("Negative Stern-Mirrors sollte Exception werfen")
        void testNegativeStarMirrors() {
            InvalidMirrorDistributionException exception = createMirrorDistributionException(5, 7, -2);

            assertEquals(5, exception.getTotalMirrors());
            assertEquals(7, exception.getRingMirrors());
            assertEquals(-2, exception.getStarMirrors());
            assertTrue(exception.getMessage().contains("nicht-negativ sein"));
        }

        @ParameterizedTest
        @CsvSource({
                "-1, 5",   // Ring negativ
                "5, -1",   // Star negativ
                "-1, -1",  // Beide negativ
                "-5, 10",  // Ring stark negativ
                "10, -5"   // Star stark negativ
        })
        @DisplayName("Verschiedene negative Werte-Kombinationen")
        void testVariousNegativeValueCombinations(int ringMirrors, int starMirrors) {
            int totalMirrors = Math.max(ringMirrors + starMirrors, 1); // Stelle sicher, dass Total positiv ist

            InvalidMirrorDistributionException exception = createMirrorDistributionException(totalMirrors, ringMirrors, starMirrors);
            assertTrue(exception.getMessage().contains("nicht-negativ sein"));
        }
    }

    @Nested
    @DisplayName("Ring-Konstruktion Konflikte Tests")
    class RingConstructionConflictTests {

        @Test
        @DisplayName("Zu viele Ring-Ebenen für verfügbare Mirrors")
        void testTooManyRingLayersForAvailableMirrors() {
            // validateRingConstruction sollte InvalidMirrorDistributionException werfen
            // wenn die Ring-Ebenen zu viele Mirrors erfordern würden
            assertThrows(InvalidMirrorDistributionException.class, () -> {
                SnowflakeTopologyValidator.validateRingConstruction(10, 5, 10); // 5*10=50 > 10
            });
        }

        @Test
        @DisplayName("Ring-Konstruktion Exception Message Format")
        void testRingConstructionExceptionMessageFormat() {
            InvalidMirrorDistributionException exception = assertThrows(
                    InvalidMirrorDistributionException.class,
                    () -> SnowflakeTopologyValidator.validateRingConstruction(20, 10, 5) // 10*5=50 > 20
            );

            String message = exception.getMessage();
            assertTrue(message.contains("Ring-Anzahl") || message.contains("überschreitet"));
        }

        @Test
        @DisplayName("Extreme Ring-Ebenen Anforderungen")
        void testExtremeRingLayerRequirements() {
            assertThrows(InvalidMirrorDistributionException.class, () -> {
                SnowflakeTopologyValidator.validateRingConstruction(100, 20, 100); // 20*100=2000 > 100
            });
        }
    }

    @Nested
    @DisplayName("Gültige Konfigurationen Tests")
    class ValidConfigurationTests {

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

        @ParameterizedTest
        @CsvSource({
                "10, 5, 5",     // Gleichverteilung
                "20, 15, 5",    // Ring-Fokus
                "20, 5, 15",    // Star-Fokus
                "100, 50, 50",  // Große gleichmäßige Verteilung
                "50, 30, 20",   // Asymmetrische Verteilung
                "3, 3, 0",      // Minimale Ring-Only
                "3, 0, 3"       // Star-Only
        })
        @DisplayName("Verschiedene gültige Verteilungen")
        void testVariousValidDistributions(int total, int ring, int star) {
            assertDoesNotThrow(() ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(total, ring, star, DEFAULT_MIN_RING_MIRROR_COUNT, DEFAULT_MAX_RING_LAYERS));
        }

        @Test
        @DisplayName("Große gültige Konfigurationen")
        void testLargeValidConfigurations() {
            assertDoesNotThrow(() ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(1000, 600, 400, 10, 20));

            assertDoesNotThrow(() ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(500, 250, 250, 5, 10));
        }
    }

    @Nested
    @DisplayName("Edge Cases und Boundary Tests")
    class EdgeCasesAndBoundaryTests {

        @Test
        @DisplayName("Minimale gültige Konfiguration")
        void testMinimalValidConfiguration() {
            assertDoesNotThrow(() ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(1, 1, 0, 1, 1));
        }

        @Test
        @DisplayName("Ring-Mirrors unter Minimum führt zu InsufficientMirrorsForRingException")
        void testRingMirrorsBelowMinimumLeadsToInsufficientException() {
            // Ring-Mirrors sind positiv aber unter dem Minimum - sollte InsufficientMirrorsForRingException werfen
            assertThrows(InsufficientMirrorsForRingException.class, () -> {
                SnowflakeTopologyValidator.validateMirrorDistribution(4, 2, 2, 3, 2); // 2 < 3 (Minimum)
            });
        }

        @Test
        @DisplayName("Grenzwert: Exakt Minimum Ring-Mirrors")
        void testExactlyMinimumRingMirrors() {
            // Exakt das Minimum sollte funktionieren
            assertDoesNotThrow(() ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(5, 3, 2, 3, 2));
        }

        @Test
        @DisplayName("Alle Mirrors zu Ringen bei maximalen Ebenen")
        void testAllMirrorsToRingsMaxLayers() {
            assertDoesNotThrow(() ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(20, 20, 0, 5, 4)); // 5*4=20, passt genau
        }

        @Test
        @DisplayName("Alle Mirrors zu Stars")
        void testAllMirrorsToStars() {
            assertDoesNotThrow(() ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(100, 0, 100, 3, 5));
        }
    }

    @Nested
    @DisplayName("Exception Details und Format Tests")
    class ExceptionDetailsAndFormatTests {

        @Test
        @DisplayName("Exception getMessage() Format für negative Werte")
        void testExceptionMessageFormatForNegativeValues() {
            InvalidMirrorDistributionException exception = createMirrorDistributionException(10, -3, 13);

            String message = exception.getMessage();
            assertNotNull(message);
            assertTrue(message.contains("Ring=-3"));
            assertTrue(message.contains("Star=13"));
            assertTrue(message.contains("Total=10"));
        }

        @Test
        @DisplayName("Exception Getter-Methoden funktionieren korrekt")
        void testExceptionGettersWorkCorrectly() {
            InvalidMirrorDistributionException exception = createMirrorDistributionException(15, 8, 4);

            assertEquals(15, exception.getTotalMirrors());
            assertEquals(8, exception.getRingMirrors());
            assertEquals(4, exception.getStarMirrors());
            assertNotNull(exception.getReason());
        }

        @Test
        @DisplayName("Exception mit verschiedenen Fehlergründen")
        void testExceptionWithDifferentReasons() {
            // Test für negative Total
            InvalidMirrorDistributionException negativeTotal = createMirrorDistributionException(-1, 0, 0);
            assertTrue(negativeTotal.getReason().contains("größer als 0"));

            // Test für Summen-Mismatch
            InvalidMirrorDistributionException sumMismatch = createMirrorDistributionException(10, 3, 4);
            assertTrue(sumMismatch.getReason().contains("entspricht nicht"));

            // Test für negative Komponenten
            InvalidMirrorDistributionException negativeRing = createMirrorDistributionException(5, -1, 6);
            assertTrue(negativeRing.getReason().contains("nicht-negativ"));
        }

        @Test
        @DisplayName("Exception toString() enthält alle wichtigen Informationen")
        void testExceptionToStringContainsImportantInfo() {
            InvalidMirrorDistributionException exception = createMirrorDistributionException(10, 3, 4);

            String toString = exception.toString();
            assertTrue(toString.contains("InvalidMirrorDistributionException"));
            assertTrue(toString.contains("Total=10") || toString.contains("10"));
        }
    }
}