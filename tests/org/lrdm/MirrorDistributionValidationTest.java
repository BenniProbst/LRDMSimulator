package org.lrdm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.lrdm.topologies.validators.SnowflakeTopologyValidator;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Mirror Verteilung Validierung Tests")
class MirrorDistributionValidationTest {

    @Nested
    @DisplayName("Gültige Mirror-Verteilungen")
    class ValidMirrorDistributionTests {

        @Test
        @DisplayName("Standard gültige Mirror-Verteilung sollte keine Exception werfen")
        void testValidMirrorDistribution() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateMirrorDistribution(10, 7, 3, 3, 2);
            });

            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateMirrorDistribution(5, 5, 0, 3, 2);
            });

            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateMirrorDistribution(8, 0, 8, 3, 2);
            });
        }

        @Test
        @DisplayName("Exakt minimale Ring-Mirrors sollten gültig sein")
        void testExactMinimalRingMirrors() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateMirrorDistribution(6, 3, 3, 3, 2);
            });
        }

        @Test
        @DisplayName("Null Ring-Mirrors sollten gültig sein")
        void testZeroRingMirrors() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateMirrorDistribution(5, 0, 5, 3, 2);
            });
        }
    }

    @Nested
    @DisplayName("Spezielle Mirror-Verteilungs-Szenarien")
    class SpecialMirrorDistributionScenarios {

        @Test
        @DisplayName("Nur Ring-Mirrors")
        void testOnlyRingMirrors() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateMirrorDistribution(15, 15, 0, 5, 3);
            });
        }

        @Test
        @DisplayName("Nur Stern-Mirrors")
        void testOnlyStarMirrors() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateMirrorDistribution(10, 0, 10, 3, 2);
            });
        }

        @Test
        @DisplayName("Ausgewogene Verteilung")
        void testBalancedDistribution() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateMirrorDistribution(20, 10, 10, 5, 2);
            });
        }

        @Test
        @DisplayName("Minimale Gesamtanzahl")
        void testMinimalTotal() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateMirrorDistribution(1, 0, 1, 3, 1);
            });
        }
    }
}