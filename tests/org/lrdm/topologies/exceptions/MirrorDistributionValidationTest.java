package org.lrdm.topologies.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import org.lrdm.topologies.validators.SnowflakeTopologyValidator;

@DisplayName("Mirror Verteilung Validierung Tests")
class MirrorDistributionValidationTest {

    @Nested
    @DisplayName("Gültige Mirror-Verteilungen")
    class ValidMirrorDistributionTests {

        @Test
        @DisplayName("Standard gültige Mirror-Verteilung sollte keine Exception werfen")
        void testValidMirrorDistribution() {
            assertDoesNotThrow(() ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(10, 7, 3, 3, 2)
            );
            assertDoesNotThrow(() ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(5, 5, 0, 3, 2)
            );
            assertDoesNotThrow(() ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(8, 0, 8, 3, 2)
            );
        }

        @Test
        @DisplayName("Exakt minimale Ring-Mirrors sollten gültig sein")
        void testExactMinimalRingMirrors() {
            assertDoesNotThrow(() ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(6, 3, 3, 3, 2)
            );
        }

        @Test
        @DisplayName("Null Ring-Mirrors sollten gültig sein (alle Mirrors in Stern)")
        void testZeroRingMirrors() {
            assertDoesNotThrow(() ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(5, 0, 5, 3, 2)
            );
        }

        @Test
        @DisplayName("Nur Ring-Mirrors (keine Sterne)")
        void testOnlyRingMirrors() {
            assertDoesNotThrow(() ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(15, 15, 0, 5, 3)
            );
        }

        @Test
        @DisplayName("Ausgewogene Verteilung 50/50")
        void testBalancedDistribution() {
            assertDoesNotThrow(() ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(20, 10, 10, 5, 2)
            );
        }

        @Test
        @DisplayName("Minimale sinnvolle Gesamtanzahl (>=1)")
        void testMinimalTotal() {
            assertDoesNotThrow(() ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(1, 0, 1, 3, 1)
            );
        }
    }

    @Nested
    @DisplayName("Ungültige Summen und negative Werte")
    class InvalidSumAndNegativeTests {

        @Test
        @DisplayName("Gesamtanzahl muss > 0 sein")
        void testInvalidTotal() {
            assertThrows(InvalidMirrorDistributionException.class, () ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(0, 0, 0, 3, 1)
            );
            assertThrows(InvalidMirrorDistributionException.class, () ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(-5, 0, 0, 3, 1)
            );
        }

        @Test
        @DisplayName("Summe (Ring + Stern) muss Total entsprechen")
        void testMismatchedSum() {
            // 3 + 4 != 10
            assertThrows(InvalidMirrorDistributionException.class, () ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(10, 3, 4, 3, 2)
            );
            // 12 + 5 != 20
            assertThrows(InvalidMirrorDistributionException.class, () ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(20, 12, 5, 3, 2)
            );
            // 7 + 9 != 15
            assertThrows(InvalidMirrorDistributionException.class, () ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(15, 7, 9, 3, 2)
            );
        }

        @Test
        @DisplayName("Ring-/Stern-Mirrors dürfen nicht negativ sein")
        void testNegativeComponents() {
            assertThrows(InvalidMirrorDistributionException.class, () ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(10, -1, 11, 3, 2)
            );
            assertThrows(InvalidMirrorDistributionException.class, () ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(10, 11, -1, 3, 2)
            );
            assertThrows(InvalidMirrorDistributionException.class, () ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(5, -2, 7, 3, 2)
            );
        }
    }

    @Nested
    @DisplayName("Mindestanforderung für Ring-Mirrors")
    class MinimalRingRequirementTests {

        @Test
        @DisplayName("Positiver Ring-Anteil unter Minimum wirft InsufficientMirrorsForRingException")
        void testPositiveRingBelowMinimum() {
            // ringMirrors > 0 UND ringMirrors < minimalRingMirrorCount → InsufficientMirrorsForRingException
            InsufficientMirrorsForRingException ex = assertThrows(
                    InsufficientMirrorsForRingException.class,
                    () -> SnowflakeTopologyValidator.validateMirrorDistribution(10, 2, 8, 5, 3)
            );
            assertEquals(2, ex.getAvailableMirrors());
            assertEquals(5, ex.getRequiredMirrors());
            assertEquals(1, ex.getRingCount());
        }

        @Test
        @DisplayName("Ring-Anteil gleich Minimum ist gültig")
        void testRingEqualsMinimum() {
            assertDoesNotThrow(() ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(8, 3, 5, 3, 2)
            );
        }

        @Test
        @DisplayName("Kein Ring-Anteil (0) ignoriert die Mindestanforderung")
        void testZeroRingIgnoresMinimum() {
            // ringMirrors == 0 ist erlaubt (alle Mirrors gehen in Sterne)
            assertDoesNotThrow(() ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(12, 0, 12, 5, 4)
            );
        }
    }

    @Nested
    @DisplayName("Boundary Conditions und realistische Szenarien")
    class BoundaryAndRealisticScenarios {

        @Test
        @DisplayName("Große validierte Konfiguration")
        void testLargeValidConfiguration() {
            assertDoesNotThrow(() ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(1000, 600, 400, 10, 20)
            );
        }

        @Test
        @DisplayName("Asymmetrische Verteilung (viele Sterne, wenige Ringe)")
        void testAsymmetricStarHeavy() {
            assertDoesNotThrow(() ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(100, 10, 90, 10, 5)
            );
        }

        @Test
        @DisplayName("Asymmetrische Verteilung (viele Ringe, wenige Sterne)")
        void testAsymmetricRingHeavy() {
            assertDoesNotThrow(() ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(100, 90, 10, 3, 10)
            );
        }

        @Test
        @DisplayName("Grenzfall: Total klein, dennoch Mindestanforderung erfüllt")
        void testSmallTotalButMinimumMet() {
            assertDoesNotThrow(() ->
                    SnowflakeTopologyValidator.validateMirrorDistribution(3, 3, 0, 3, 1)
            );
        }
    }
}