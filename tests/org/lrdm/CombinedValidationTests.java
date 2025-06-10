package org.lrdm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.junit.jupiter.api.Assertions.*;
import org.lrdm.topologies.exceptions.*;
import org.lrdm.topologies.validators.SnowflakeTopologyValidator;

@DisplayName("Kombinierte Validierung Tests")
class CombinedValidationTest {

    @Nested
    @DisplayName("Vollständige gültige Konfigurationen")
    class CompleteValidConfigurationTests {

        @Test
        @DisplayName("Alle Parameter gültig")
        void testAllParametersValid() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(2, 1, 2, 2, 3);
                SnowflakeTopologyValidator.validateStarParameters(2, 1, 0.3);
                SnowflakeTopologyValidator.validateMirrorDistribution(10, 7, 3, 3, 2);
                SnowflakeTopologyValidator.validateRingConstruction(7, 3, 2);
            });
        }

        @Test
        @DisplayName("Großes Netzwerk")
        void testLargeNetworkConfiguration() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(5, 2, 3, 5, 10);
                SnowflakeTopologyValidator.validateStarParameters(4, 2, 0.4);
                SnowflakeTopologyValidator.validateMirrorDistribution(100, 60, 40, 10, 5);
                SnowflakeTopologyValidator.validateRingConstruction(60, 10, 5);
            });
        }

        @Test
        @DisplayName("Minimales Netzwerk")
        void testMinimalNetworkConfiguration() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(0, 0, 1, 1, 3);
                SnowflakeTopologyValidator.validateStarParameters(1, 0, 0.0);
                SnowflakeTopologyValidator.validateMirrorDistribution(3, 3, 0, 3, 1);
                SnowflakeTopologyValidator.validateRingConstruction(3, 3, 1);
            });
        }
    }

    @Nested
    @DisplayName("Inkonsistente Parameter-Kombinationen")
    class InconsistentParameterTests {

        @Test
        @DisplayName("Ring-Parameter gültig, aber Mirror-Verteilung inkonsistent")
        void testInconsistentParameterCombinations() {
            // Ring-Parameter sind gültig
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(2, 1, 2, 2, 5);
            });

            // Aber Mirror-Verteilung passt nicht
            assertThrows(InsufficientMirrorsForRingException.class, () -> {
                SnowflakeTopologyValidator.validateMirrorDistribution(7, 3, 4, 5, 2);
            });
        }

        @Test
        @DisplayName("Stern-Parameter gültig, aber zu wenige Mirrors")
        void testValidStarParametersButInsufficientMirrors() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateStarParameters(3, 1, 0.5);
            });

            assertThrows(InsufficientMirrorsForRingException.class, () -> {
                SnowflakeTopologyValidator.validateRingConstruction(2, 3, 2);
            });
        }
    }

    @Nested
    @DisplayName("Komplexe Szenarien")
    class ComplexScenarioTests {

        @Test
        @DisplayName("Mittlere Komplexität Konfiguration")
        void testMediumComplexityConfiguration() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(3, 1, 2, 3, 5);
                SnowflakeTopologyValidator.validateStarParameters(2, 1, 0.3);
                SnowflakeTopologyValidator.validateMirrorDistribution(25, 18, 7, 5, 3);
                SnowflakeTopologyValidator.validateRingConstruction(18, 5, 3);
            });
        }

        @Test
        @DisplayName("Hohe Stern-Ratio Konfiguration")
        void testHighStarRatioConfiguration() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(1, 0, 1, 1, 3);
                SnowflakeTopologyValidator.validateStarParameters(3, 2, 0.8);
                SnowflakeTopologyValidator.validateMirrorDistribution(20, 4, 16, 3, 1);
                SnowflakeTopologyValidator.validateRingConstruction(4, 3, 1);
            });
        }

        @Test
        @DisplayName("Niedrige Stern-Ratio Konfiguration")
        void testLowStarRatioConfiguration() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(2, 1, 3, 4, 4);
                SnowflakeTopologyValidator.validateStarParameters(2, 1, 0.1);
                SnowflakeTopologyValidator.validateMirrorDistribution(50, 45, 5, 4, 4);
                SnowflakeTopologyValidator.validateRingConstruction(45, 4, 4);
            });
        }
    }

    @Nested
    @DisplayName("Edge Case Kombinationen")
    class EdgeCaseCombinationTests {

        @Test
        @DisplayName("Maximale Werte Kombination")
        void testMaximumValuesCombination() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(10, 5, 5, 10, 20);
                SnowflakeTopologyValidator.validateStarParameters(10, 5, 1.0);
                SnowflakeTopologyValidator.validateMirrorDistribution(1000, 0, 1000, 20, 10);
                SnowflakeTopologyValidator.validateRingConstruction(0, 20, 10);
            });
        }

        @Test
        @DisplayName("Minimale Werte Kombination")
        void testMinimumValuesCombination() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(0, 0, 1, 1, 3);
                SnowflakeTopologyValidator.validateStarParameters(1, 0, 0.0);
                SnowflakeTopologyValidator.validateMirrorDistribution(3, 3, 0, 3, 1);
                SnowflakeTopologyValidator.validateRingConstruction(3, 3, 1);
            });
        }
    }
}