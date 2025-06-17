package org.lrdm.topologies.builders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.lrdm.topologies.validators.SnowflakeTopologyValidator;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Ring Parameter Validierung Tests")
class RingParameterValidationTest {

    @Nested
    @DisplayName("Gültige Ring-Parameter")
    class ValidRingParameterTests {

        @Test
        @DisplayName("Standard gültige Ring-Parameter sollten keine Exception werfen")
        void testValidRingParameters() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(2, 1, 2, 2, 3);
            });

            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(0, 0, 1, 1, 3);
            });

            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(5, 3, 4, 5, 10);
            });
        }

        @Test
        @DisplayName("Grenzwerte sollten gültig sein")
        void testRingParameterBoundaryValues() {
            // Minimale gültige Werte
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(0, 0, 1, 1, 3);
            });

            // Große gültige Werte
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(100, 50, 10, 20, 15);
            });
        }
    }

    @Nested
    @DisplayName("Ring-Konstruktion Validierung")
    class RingConstructionValidationTests {

        @Test
        @DisplayName("Gültige Ring-Konstruktion sollte keine Exception werfen")
        void testValidRingConstruction() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingConstruction(9, 3, 2);
            });

            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingConstruction(0, 3, 2);
            });

            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingConstruction(15, 5, 3);
            });
        }

        @Test
        @DisplayName("Exakt maximale Ring-Anzahl sollte gültig sein")
        void testExactMaxRings() {
            // 6 Mirrors, mindestens 3 pro Ring = 2 Ringe, max 2 erlaubt
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingConstruction(6, 3, 2);
            });
        }

        @Test
        @DisplayName("Grenzfälle für Ring-Konstruktion")
        void testRingConstructionBoundaryValues() {
            // Minimaler Fall: Exakt genug für einen Ring
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingConstruction(3, 3, 1);
            });

            // Null Mirrors (sollte gültig sein)
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingConstruction(0, 3, 1);
            });
        }
    }
}