package org.lrdm.topologies;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.lrdm.topologies.validators.SnowflakeTopologyValidator;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Stern Parameter Validierung Tests")
class StarParameterValidationTest {

    @Nested
    @DisplayName("Gültige Stern-Parameter")
    class ValidStarParameterTests {

        @Test
        @DisplayName("Standard gültige Stern-Parameter sollten keine Exception werfen")
        void testValidStarParameters() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateStarParameters(2, 1, 0.3);
            });

            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateStarParameters(1, 0, 0.0);
            });

            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateStarParameters(5, 3, 1.0);
            });
        }

        @Test
        @DisplayName("Grenzwerte für Stern-Parameter sollten gültig sein")
        void testStarParameterBoundaryValues() {
            // Minimale gültige Werte
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateStarParameters(1, 0, 0.0);
            });

            // Maximale gültige Werte
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateStarParameters(100, 50, 1.0);
            });

            // Exakte Grenzwerte für Ratio
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateStarParameters(2, 1, 0.0);
            });

            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateStarParameters(2, 1, 1.0);
            });
        }
    }

    @Nested
    @DisplayName("Typische Stern-Konfigurationen")
    class TypicalStarConfigurationTests {

        @Test
        @DisplayName("Kleine Stern-Konfiguration")
        void testSmallStarConfiguration() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateStarParameters(1, 0, 0.2);
            });
        }

        @Test
        @DisplayName("Mittlere Stern-Konfiguration")
        void testMediumStarConfiguration() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateStarParameters(3, 1, 0.5);
            });
        }

        @Test
        @DisplayName("Große Stern-Konfiguration")
        void testLargeStarConfiguration() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateStarParameters(5, 2, 0.8);
            });
        }
    }
}