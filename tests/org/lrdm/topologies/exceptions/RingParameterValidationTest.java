
package org.lrdm.topologies.exceptions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.junit.jupiter.api.Assertions.*;

import org.lrdm.topologies.validators.SnowflakeTopologyValidator;

@DisplayName("Ring Parameter Validierung Tests")
class RingParameterValidationTest {

    // Konstanten entsprechend der aktuellen SnowflakeTopologyValidator-Implementation
    private static final int DEFAULT_MAX_RING_LAYERS = 5;

    @Nested
    @DisplayName("Aktive Ring-Parameter (MINIMAL_RING_MIRROR_COUNT)")
    class ActiveRingParameterTests {

        @Test
        @DisplayName("Gültige minimale Spiegelzahl: Standardfälle")
        void testValidMinimalRingMirrorCountStandard() {
            assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateRingParameters(3));
            assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateRingParameters(4));
            assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateRingParameters(10));
        }

        @Test
        @DisplayName("Grenzwerte: Exakt Minimum (3) und große Werte")
        void testBoundaryValues() {
            // exakt Minimum
            assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateRingParameters(3));
            // größere plausible Werte
            assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateRingParameters(50));
            assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateRingParameters(1_000));
        }

        @Test
        @DisplayName("Ungültige Werte: < 3 lösen InvalidRingParameterException aus")
        void testInvalidValuesBelowTriangleMinimum() {
            // 2, 1, 0, negative
            int[] invalid = {2, 1, 0, -1, -10};
            for (int value : invalid) {
                InvalidRingParameterException ex = assertThrows(
                        InvalidRingParameterException.class,
                        () -> SnowflakeTopologyValidator.validateRingParameters(value),
                        () -> "Erwartete InvalidRingParameterException für Wert=" + value
                );
                assertEquals("MINIMAL_RING_MIRROR_COUNT", ex.getParameterName());
                assertEquals(value, ex.getActualValue());
                assertTrue(ex.getConstraint().contains("mindestens 3"), "Constraint sollte Minimum 3 erwähnen");
                assertTrue(ex.getMessage().toLowerCase().contains("dreieck"), "Message sollte Dreieck-Minimum erwähnen");
            }
        }
    }

    @Nested
    @DisplayName("Ring-Konstruktion Validierung")
    class RingConstructionValidationTests {

        @Test
        @DisplayName("Gültige Ring-Konstruktion: typische Fälle")
        void testValidRingConstruction() {
            // Sichere Kombinationen, die maxRingLayers von 5 nicht überschreiten

            // 6 Mirrors / 3 pro Ring = 2 Ringe ≤ 5 (OK)
            assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateRingConstruction(6, 3, 5));

            // 0 Mirrors (keine Ringe geplant) ist zulässig
            assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateRingConstruction(0, 3, 5));

            // 15 Mirrors / 5 pro Ring = 3 Ringe ≤ 5 (OK)
            assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateRingConstruction(15, 5, 5));

            // 3 Mirrors / 3 pro Ring = 1 Ring ≤ 5 (OK)
            assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateRingConstruction(3, 3, 5));

            // Exakt 5 Ringe (Maximum)
            assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateRingConstruction(15, 3, 5)); // 15/3 = 5
        }

        @Test
        @DisplayName("Unzureichende Mirrors für einen Ring")
        void testInsufficientMirrors() {
            InsufficientMirrorsForRingException ex1 = assertThrows(
                    InsufficientMirrorsForRingException.class,
                    () -> SnowflakeTopologyValidator.validateRingConstruction(1, 5, 5)
            );
            assertEquals(1, ex1.getAvailableMirrors());
            assertEquals(5, ex1.getRequiredMirrors());
            assertEquals(1, ex1.getRingCount());

            InsufficientMirrorsForRingException ex2 = assertThrows(
                    InsufficientMirrorsForRingException.class,
                    () -> SnowflakeTopologyValidator.validateRingConstruction(4, 5, 5)
            );
            assertEquals(4, ex2.getAvailableMirrors());
            assertEquals(5, ex2.getRequiredMirrors());
            assertEquals(1, ex2.getRingCount());
        }

        @Test
        @DisplayName("Zu viele Ring-Ebenen für verfügbare Mirrors")
        void testTooManyLayersForAvailableMirrors() {
            // 30 Mirrors / 3 pro Ring = 10 Ringe > 5 (Maximum) → Exception
            InvalidMirrorDistributionException ex = assertThrows(
                    InvalidMirrorDistributionException.class,
                    () -> SnowflakeTopologyValidator.validateRingConstruction(30, 3, 5)
            );
            assertTrue(ex.getMessage().contains("überschreitet") || ex.getMessage().contains("Ring-Anzahl"),
                    "Message sollte Grenzüberschreitung der Ring-Anzahl erwähnen");
            assertTrue(ex.getMessage().contains("10") && ex.getMessage().contains("5"),
                    "Message sollte berechnete Ringanzahl (10) und Maximum (5) enthalten");
        }

        @Test
        @DisplayName("Weitere Fälle für zu viele Ringe")
        void testMoreCasesWithTooManyRings() {
            // 60 Mirrors / 3 pro Ring = 20 Ringe > 5
            assertThrows(InvalidMirrorDistributionException.class, () -> SnowflakeTopologyValidator.validateRingConstruction(60, 3, 5));

            // 100 Mirrors / 4 pro Ring = 25 Ringe > 5
            assertThrows(InvalidMirrorDistributionException.class, () -> SnowflakeTopologyValidator.validateRingConstruction(100, 4, 5));
        }

        @Test
        @DisplayName("Exakt maximale Ring-Anzahl ist gültig")
        void testExactMaxRings() {
            // 15 Mirrors / 3 pro Ring = 5 Ringe = Maximum (OK)
            assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateRingConstruction(15, 3, 5));

            // 20 Mirrors / 4 pro Ring = 5 Ringe = Maximum (OK)
            assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateRingConstruction(20, 4, 5));
        }
    }

    @Nested
    @DisplayName("Integration: Minimalwert vs. Distribution/Konstruktion")
    class IntegrationWithOtherValidations {

        @Test
        @DisplayName("Konsistenz: Mindestwert 3; Distribution und Konstruktion passen zusammen")
        void testConsistencyAcrossValidators() {
            // setze Mindestwert 3 (gültig)
            assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateRingParameters(3));

            // konsistente Verteilung mit sicheren Ring-Werten
            assertDoesNotThrow(() ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(10, 6, 4, 3, 5));

            // konsistente Konstruktion: 6/3 = 2 Ringe ≤ 5
            assertDoesNotThrow(() ->
                    SnowflakeTopologyValidator.validateRingConstruction(6, 3, 5));
        }

        @Test
        @DisplayName("Fehlerfall: Verteilung mit positivem Ring-Anteil unter Minimum")
        void testDistributionBelowMinimumRing() {
            InsufficientMirrorsForRingException ex = assertThrows(
                    InsufficientMirrorsForRingException.class,
                    () -> SnowflakeTopologyValidator.validateMirrorDistribution(10, 2, 8, 5, 5)
            );
            assertEquals(2, ex.getAvailableMirrors());
            assertEquals(5, ex.getRequiredMirrors());
            assertEquals(1, ex.getRingCount());
        }

        @Test
        @DisplayName("Fehlerfall: Summeninkonsistenz in Verteilung")
        void testDistributionSumMismatch() {
            assertThrows(InvalidMirrorDistributionException.class,
                    () -> SnowflakeTopologyValidator.validateMirrorDistribution(10, 3, 4, 3, 5));
        }

        @Test
        @DisplayName("Fehlerfall: Ring-Konstruktion mit zu vielen berechneten Ringen")
        void testConstructionWithTooManyCalculatedRings() {
            // Diese sollten InvalidMirrorDistributionException werfen, da zu viele Ringe berechnet werden

            // 18 Mirrors / 3 pro Ring = 6 Ringe > 5
            assertThrows(InvalidMirrorDistributionException.class, () -> SnowflakeTopologyValidator.validateRingConstruction(18, 3, 5));

            // 24 Mirrors / 4 pro Ring = 6 Ringe > 5
            assertThrows(InvalidMirrorDistributionException.class, () -> SnowflakeTopologyValidator.validateRingConstruction(24, 4, 5));
        }

        @Test
        @DisplayName("Grenzwerte: Verschiedene maxRingLayers-Werte")
        void testDifferentMaxRingLayerValues() {
            // Mit maxRingLayers = 2
            assertDoesNotThrow(() ->
                    SnowflakeTopologyValidator.validateRingConstruction(6, 3, 2)); // 6/3 = 2 Ringe = Max

            assertThrows(InvalidMirrorDistributionException.class, () ->
                    SnowflakeTopologyValidator.validateRingConstruction(9, 3, 2)); // 9/3 = 3 > 2

            // Mit maxRingLayers = 10
            assertDoesNotThrow(() ->
                    SnowflakeTopologyValidator.validateRingConstruction(30, 3, 10)); // 30/3 = 10 Ringe = Max

            assertThrows(InvalidMirrorDistributionException.class, () ->
                    SnowflakeTopologyValidator.validateRingConstruction(33, 3, 10)); // 33/3 = 11 > 10
        }
    }
}