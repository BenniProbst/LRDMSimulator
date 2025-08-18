package org.lrdm.topologies.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import org.lrdm.topologies.validators.SnowflakeTopologyValidator;

@DisplayName("Ring Parameter Validierung Tests")
class RingParameterValidationTest {

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
            // 2 Ringe à 3 Mirrors = 6 möglich
            assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateRingConstruction(9, 3, 2));
            // 0 Mirrors (keine Ringe geplant) ist zulässig
            assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateRingConstruction(0, 3, 2));
            // 3 Ringe à 5 Mirrors = 15 möglich
            assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateRingConstruction(15, 5, 3));
            // genau 1 Ring möglich
            assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateRingConstruction(3, 3, 1));
        }

        @Test
        @DisplayName("Unzureichende Mirrors für einen Ring")
        void testInsufficientMirrors() {
            InsufficientMirrorsForRingException ex1 = assertThrows(
                    InsufficientMirrorsForRingException.class,
                    () -> SnowflakeTopologyValidator.validateRingConstruction(1, 5, 2)
            );
            assertEquals(1, ex1.getAvailableMirrors());
            assertEquals(5, ex1.getRequiredMirrors());
            assertEquals(1, ex1.getRingCount());

            InsufficientMirrorsForRingException ex2 = assertThrows(
                    InsufficientMirrorsForRingException.class,
                    () -> SnowflakeTopologyValidator.validateRingConstruction(4, 5, 1)
            );
            assertEquals(4, ex2.getAvailableMirrors());
            assertEquals(5, ex2.getRequiredMirrors());
            assertEquals(1, ex2.getRingCount());
        }

        @Test
        @DisplayName("Zu viele Ring-Ebenen für verfügbare Mirrors")
        void testTooManyLayersForAvailableMirrors() {
            // 10 Mirrors, 10 pro Ring, 50 Ebenen -> erfordert 500 Mirrors
            InvalidMirrorDistributionException ex = assertThrows(
                    InvalidMirrorDistributionException.class,
                    () -> SnowflakeTopologyValidator.validateRingConstruction(100, 10, 50)
            );
            assertTrue(ex.getMessage().contains("überschreitet") || ex.getMessage().contains("Ring-Anzahl"),
                    "Message sollte Grenzüberschreitung der Ring-Anzahl erwähnen");
        }

        @Test
        @DisplayName("Exakt maximale Ring-Anzahl ist gültig")
        void testExactMaxRings() {
            // 6 Mirrors, min 3 pro Ring -> 2 Ringe exakt das Maximum
            assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateRingConstruction(6, 3, 2));
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

            // konsistente Verteilung
            assertDoesNotThrow(() ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(10, 6, 4, 3, 2));

            // konsistente Konstruktion mit 2 Ebenen
            assertDoesNotThrow(() ->
                    SnowflakeTopologyValidator.validateRingConstruction(6, 3, 2));
        }

        @Test
        @DisplayName("Fehlerfall: Verteilung mit positivem Ring-Anteil unter Minimum")
        void testDistributionBelowMinimumRing() {
            InsufficientMirrorsForRingException ex = assertThrows(
                    InsufficientMirrorsForRingException.class,
                    () -> SnowflakeTopologyValidator.validateMirrorDistribution(10, 2, 8, 5, 3)
            );
            assertEquals(2, ex.getAvailableMirrors());
            assertEquals(5, ex.getRequiredMirrors());
            assertEquals(1, ex.getRingCount());
        }

        @Test
        @DisplayName("Fehlerfall: Summeninkonsistenz in Verteilung")
        void testDistributionSumMismatch() {
            assertThrows(InvalidMirrorDistributionException.class,
                    () -> SnowflakeTopologyValidator.validateMirrorDistribution(10, 3, 4, 3, 2));
        }
    }
}