package org.lrdm.topologies.exceptions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.junit.jupiter.api.Assertions.*;

import org.lrdm.topologies.validators.SnowflakeTopologyValidator;

@DisplayName("Kombinierte Validierung Tests")
class CombinedValidationTest {

    @Nested
    @DisplayName("Vollständige gültige Konfigurationen")
    class CompleteValidConfigurationTests {

        @Test
        @DisplayName("Alle verfügbaren Parameter gültig - Basiskonfiguration")
        void testAllAvailableParametersValid() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(3);
                SnowflakeTopologyValidator.validateMirrorDistribution(9, 6, 3, 3, 2);
                SnowflakeTopologyValidator.validateRingConstruction(6, 3, 2);
            });
        }

        @Test
        @DisplayName("Großes Netzwerk - Skalierung")
        void testLargeNetworkConfiguration() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(5);
                SnowflakeTopologyValidator.validateMirrorDistribution(100, 50, 50, 5, 10);
                SnowflakeTopologyValidator.validateRingConstruction(50, 5, 10);
            });
        }

        @Test
        @DisplayName("Minimales Netzwerk - Untere Grenzen")
        void testMinimalNetworkConfiguration() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(3);
                SnowflakeTopologyValidator.validateMirrorDistribution(6, 3, 3, 3, 1);
                SnowflakeTopologyValidator.validateRingConstruction(3, 3, 1);
            });
        }

        @Test
        @DisplayName("Symmetrische Konfiguration")
        void testSymmetricConfiguration() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(6);
                SnowflakeTopologyValidator.validateMirrorDistribution(30, 18, 12, 6, 3);
                SnowflakeTopologyValidator.validateRingConstruction(18, 6, 3);
            });
        }

        @Test
        @DisplayName("Ausgewogene mittlere Größe")
        void testBalancedMediumConfiguration() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(4);
                SnowflakeTopologyValidator.validateMirrorDistribution(20, 12, 8, 4, 3);
                SnowflakeTopologyValidator.validateRingConstruction(12, 4, 3);
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
            assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateRingParameters(5));

            // Aber Mirror-Verteilung passt nicht (Summe stimmt nicht)
            assertThrows(InvalidMirrorDistributionException.class, () -> {
                SnowflakeTopologyValidator.validateMirrorDistribution(10, 3, 4, 5, 2); // 3+4=7, aber erwartet 10
            });
        }

        @Test
        @DisplayName("Gültige Mirror-Verteilung, aber Ring-Konstruktion fehlgeschlagen")
        void testValidDistributionButInvalidConstruction() {
            assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateMirrorDistribution(15, 10, 5, 5, 3));

            // Aber Ring-Konstruktion mit zu wenigen Mirrors
            assertThrows(InsufficientMirrorsForRingException.class, () -> {
                SnowflakeTopologyValidator.validateRingConstruction(2, 5, 3); // Nur 2 Mirrors für Ring mit Minimum 5
            });
        }

        @Test
        @DisplayName("Inkonsistente Mirror-Summe")
        void testInconsistentMirrorSum() {
            assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateRingParameters(4));

            // Mirror-Summe stimmt nicht überein
            assertThrows(InvalidMirrorDistributionException.class, () -> {
                SnowflakeTopologyValidator.validateMirrorDistribution(15, 8, 6, 4, 2); // 8+6=14, aber erwartet 15
            });
        }

        @Test
        @DisplayName("Ring-Konstruktion mit unzureichenden Mirrors")
        void testRingConstructionWithInsufficientMirrors() {
            assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateRingParameters(8));

            // Nicht genug Ring-Mirrors für die geforderte Ring-Struktur
            assertThrows(InsufficientMirrorsForRingException.class, () -> {
                SnowflakeTopologyValidator.validateRingConstruction(5, 8, 2); // Nur 5 Mirrors, aber Minimum 8 benötigt
            });
        }

        @Test
        @DisplayName("Negative Werte in Mirror-Verteilung")
        void testNegativeValuesInMirrorDistribution() {
            assertThrows(InvalidMirrorDistributionException.class, () -> SnowflakeTopologyValidator.validateMirrorDistribution(10, -5, 15, 3, 2));

            assertThrows(InvalidMirrorDistributionException.class, () -> SnowflakeTopologyValidator.validateMirrorDistribution(10, 15, -5, 3, 2));
        }
    }

    @Nested
    @DisplayName("Komplexe Szenarien")
    class ComplexScenarioTests {

        @Test
        @DisplayName("Mittlere Komplexität Konfiguration")
        void testMediumComplexityConfiguration() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(5);
                SnowflakeTopologyValidator.validateMirrorDistribution(25, 15, 10, 5, 3);
                SnowflakeTopologyValidator.validateRingConstruction(15, 5, 3);
            });
        }

        @Test
        @DisplayName("Hoher Ring-Anteil Konfiguration")
        void testHighRingRatioConfiguration() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(3);
                SnowflakeTopologyValidator.validateMirrorDistribution(20, 15, 5, 3, 5);
                SnowflakeTopologyValidator.validateRingConstruction(15, 3, 5);
            });
        }

        @Test
        @DisplayName("Niedriger Ring-Anteil Konfiguration")
        void testLowRingRatioConfiguration() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(4);
                SnowflakeTopologyValidator.validateMirrorDistribution(50, 8, 42, 4, 2);
                SnowflakeTopologyValidator.validateRingConstruction(8, 4, 2);
            });
        }

        @Test
        @DisplayName("Hierarchische Struktur mit mehreren Ebenen")
        void testMultiLevelHierarchicalStructure() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(8);
                SnowflakeTopologyValidator.validateMirrorDistribution(80, 32, 48, 8, 4);
                SnowflakeTopologyValidator.validateRingConstruction(32, 8, 4);
            });
        }

        @Test
        @DisplayName("Dichte Ring-Struktur")
        void testDenseRingStructure() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(6);
                SnowflakeTopologyValidator.validateMirrorDistribution(60, 30, 30, 6, 5);
                SnowflakeTopologyValidator.validateRingConstruction(30, 6, 5);
            });
        }
    }

    @Nested
    @DisplayName("Edge Case Kombinationen")
    class EdgeCaseCombinationTests {

        @Test
        @DisplayName("Maximale Ring-Größe")
        void testMaximumRingSize() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(20);
                SnowflakeTopologyValidator.validateMirrorDistribution(1000, 200, 800, 20, 10);
                SnowflakeTopologyValidator.validateRingConstruction(200, 20, 10);
            });
        }

        @Test
        @DisplayName("Minimale Ring-Größe")
        void testMinimumRingSize() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(3);
                SnowflakeTopologyValidator.validateMirrorDistribution(6, 3, 3, 3, 1);
                SnowflakeTopologyValidator.validateRingConstruction(3, 3, 1);
            });
        }

        @Test
        @DisplayName("Nur Ring-Topologie (keine Stars)")
        void testRingOnlyTopology() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(8);
                SnowflakeTopologyValidator.validateMirrorDistribution(40, 40, 0, 8, 5);
                SnowflakeTopologyValidator.validateRingConstruction(40, 8, 5);
            });
        }

        @Test
        @DisplayName("Nur Stern-Topologie (minimale Ringe)")
        void testStarOnlyTopology() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(3);
                SnowflakeTopologyValidator.validateMirrorDistribution(30, 3, 27, 3, 1);
                SnowflakeTopologyValidator.validateRingConstruction(3, 3, 1);
            });
        }

        @Test
        @DisplayName("Extrem asymmetrische Verteilung")
        void testExtremelyAsymmetricDistribution() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(10);
                SnowflakeTopologyValidator.validateMirrorDistribution(100, 10, 90, 10, 1);
                SnowflakeTopologyValidator.validateRingConstruction(10, 10, 1);
            });
        }

        @Test
        @DisplayName("Grenzfall: Minimale Ring-Größe mit maximalen Ebenen")
        void testMinimalRingSizeMaxLayers() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(3);
                SnowflakeTopologyValidator.validateMirrorDistribution(30, 24, 6, 3, 8);
                SnowflakeTopologyValidator.validateRingConstruction(24, 3, 8);
            });
        }
    }

    @Nested
    @DisplayName("Stress Tests mit großen Werten - Realistisch")
    class StressTests {

        @Test
        @DisplayName("Sehr große Netzwerk-Konfiguration - Realistisch")
        void testVeryLargeNetworkConfiguration() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(50);
                SnowflakeTopologyValidator.validateMirrorDistribution(5000, 1250, 3750, 50, 25);
                SnowflakeTopologyValidator.validateRingConstruction(1250, 50, 25);
            });
        }

        @Test
        @DisplayName("Hohe Ring-Dichte - Realistisch")
        void testHighRingDensity() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(20);
                SnowflakeTopologyValidator.validateMirrorDistribution(1000, 300, 700, 20, 15);
                SnowflakeTopologyValidator.validateRingConstruction(300, 20, 15);
            });
        }

        @Test
        @DisplayName("Viele kleine Ringe - Realistisch")
        void testManySmallRings() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(3);
                SnowflakeTopologyValidator.validateMirrorDistribution(500, 300, 200, 3, 100);
                SnowflakeTopologyValidator.validateRingConstruction(300, 3, 100);
            });
        }

        @Test
        @DisplayName("Maximale Kapazität Test - Realistisch")
        void testMaximalCapacity() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(100);
                SnowflakeTopologyValidator.validateMirrorDistribution(10000, 5000, 5000, 100, 50);
                SnowflakeTopologyValidator.validateRingConstruction(5000, 100, 50);
            });
        }
    }

    @Nested
    @DisplayName("Fehlerbehandlungs-Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Ungültige Gesamtanzahl Mirrors")
        void testInvalidTotalMirrors() {
            assertThrows(InvalidMirrorDistributionException.class, () -> SnowflakeTopologyValidator.validateMirrorDistribution(0, 0, 0, 3, 1));

            assertThrows(InvalidMirrorDistributionException.class, () -> SnowflakeTopologyValidator.validateMirrorDistribution(-10, 5, 5, 3, 1));
        }

        @Test
        @DisplayName("Ungültiger Ring-Parameter")
        void testInvalidRingParameter() {
            assertThrows(InvalidRingParameterException.class, () -> {
                SnowflakeTopologyValidator.validateRingParameters(2); // Minimum ist 3
            });

            assertThrows(InvalidRingParameterException.class, () -> SnowflakeTopologyValidator.validateRingParameters(0));
        }

        @Test
        @DisplayName("Unzureichende Mirrors für Ring-Konstruktion")
        void testInsufficientMirrorsForRingConstruction() {
            assertThrows(InsufficientMirrorsForRingException.class, () -> {
                SnowflakeTopologyValidator.validateRingConstruction(1, 5, 2); // 1 Mirror, aber Minimum 5 benötigt
            });

            assertThrows(InsufficientMirrorsForRingException.class, () -> {
                SnowflakeTopologyValidator.validateRingConstruction(4, 5, 1); // 4 Mirrors, aber Minimum 5 benötigt
            });
        }

        @Test
        @DisplayName("Zu viele Ring-Ebenen für verfügbare Mirrors - Korrigiert")
        void testTooManyRingLayersForAvailableMirrors() {
            // Test, bei dem tatsächlich zu viele Ring-Ebenen erforderlich wären
            // Mit 10 Mirrors, 5 pro Ring, aber nur 1 Ebene erlaubt
            // safeRingCount = Math.max(1, 10/5) = 2, aber maxRingLayers = 1
            assertThrows(InvalidMirrorDistributionException.class, () -> {
                SnowflakeTopologyValidator.validateRingConstruction(10, 5, 1); // 10 Mirrors, 5 pro Ring, nur 1 Ebene erlaubt → würde 2 Ebenen benötigen
            });
        }

    }

    @Nested
    @DisplayName("Boundary Condition Tests")
    class BoundaryConditionTests {

        @Test
        @DisplayName("Grenzfall: Genau ein Ring")
        void testExactlyOneRing() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(5);
                SnowflakeTopologyValidator.validateMirrorDistribution(10, 5, 5, 5, 1);
                SnowflakeTopologyValidator.validateRingConstruction(5, 5, 1);
            });
        }

        @Test
        @DisplayName("Maximale Ring-Größe bei einem Ring")
        void testMaxRingSizeOneRing() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(50);
                SnowflakeTopologyValidator.validateMirrorDistribution(100, 50, 50, 50, 1);
                SnowflakeTopologyValidator.validateRingConstruction(50, 50, 1);
            });
        }

        @Test
        @DisplayName("Gleichmäßige Verteilung Ring-Stern - Korrigiert")
        void testEvenRingStarDistribution() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(5);
                SnowflakeTopologyValidator.validateMirrorDistribution(50, 15, 35, 5, 3);
                SnowflakeTopologyValidator.validateRingConstruction(15, 5, 3);
            });
        }

        @Test
        @DisplayName("Minimale Konfiguration mit allen Komponenten")
        void testMinimalConfigurationWithAllComponents() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(3);
                SnowflakeTopologyValidator.validateMirrorDistribution(4, 3, 1, 3, 1);
                SnowflakeTopologyValidator.validateRingConstruction(3, 3, 1);
            });
        }
    }

    @Nested
    @DisplayName("Tests für Ring-Ebenen-Grenzen")
    class RingLayerLimitTests {

        @Test
        @DisplayName("Ring-Konstruktion mit maximalen Ebenen")
        void testRingConstructionWithMaxLayers() {
            // Ring mit 30 Mirrors, 3 pro Ring, maximal 10 Ebenen sollte funktionieren
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingConstruction(30, 3, 10); // 30/3 = 10 Ebenen möglich
            });
        }

        @Test
        @DisplayName("Ring-Konstruktion überschreitet maximale Ebenen")
        void testRingConstructionExceedsMaxLayers() {
            // Ring mit 30 Mirrors, 3 pro Ring, aber nur 5 Ebenen erlaubt - sollte fehlschlagen
            assertThrows(InvalidMirrorDistributionException.class, () -> {
                SnowflakeTopologyValidator.validateRingConstruction(30, 3, 5); // Würde 10 Ebenen benötigen, aber nur 5 erlaubt
            });
        }
    }
}