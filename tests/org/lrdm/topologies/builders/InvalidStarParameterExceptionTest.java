package org.lrdm.topologies.builders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

import org.lrdm.topologies.exceptions.InvalidStarParameterException;
import org.lrdm.topologies.validators.SnowflakeTopologyValidator;

@DisplayName("InvalidStarParameterException Tests")
class InvalidStarParameterExceptionTest {

    // Test-Konstanten für gültige Parameterwerte
    private static final int VALID_TREE_DEPTH = 2;
    private static final int VALID_BRIDGE_DISTANCE = 1;
    private static final double VALID_STAR_RATIO = 0.3;

    @Test
    @DisplayName("Exception Details für EXTERN_STAR_RATIO")
    void shouldProvideCorrectExceptionDetailsForExternStarRatio() {
        double invalidRatio = 1.5;

        InvalidStarParameterException exception = assertThrows(
                InvalidStarParameterException.class,
                () -> SnowflakeTopologyValidator.validateStarParameters(VALID_TREE_DEPTH, VALID_BRIDGE_DISTANCE, invalidRatio)
        );

        assertExceptionDetails(exception, "EXTERN_STAR_RATIO", invalidRatio, "muss zwischen 0.0 und 1.0 liegen");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -5})
    @DisplayName("Ungültiger EXTERN_STAR_MAX_TREE_DEPTH sollte Exception werfen")
    void shouldThrowExceptionForInvalidExternStarMaxTreeDepth(int invalidValue) {
        InvalidStarParameterException exception = assertThrows(
                InvalidStarParameterException.class,
                () -> SnowflakeTopologyValidator.validateStarParameters(invalidValue, VALID_BRIDGE_DISTANCE, VALID_STAR_RATIO)
        );

        assertExceptionDetails(exception, "EXTERN_STAR_MAX_TREE_DEPTH", invalidValue, "muss mindestens 1 sein");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -3, -10})
    @DisplayName("Ungültiger BRIDGE_TO_EXTERN_STAR_DISTANCE sollte Exception werfen")
    void shouldThrowExceptionForInvalidBridgeToExternStarDistance(int invalidValue) {
        InvalidStarParameterException exception = assertThrows(
                InvalidStarParameterException.class,
                () -> SnowflakeTopologyValidator.validateStarParameters(VALID_TREE_DEPTH, invalidValue, VALID_STAR_RATIO)
        );

        assertExceptionDetails(exception, "BRIDGE_TO_EXTERN_STAR_DISTANCE", invalidValue, "muss mindestens 0 sein");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, -1.0, 1.1, 1.5, 2.0})
    @DisplayName("Ungültiger EXTERN_STAR_RATIO sollte Exception werfen")
    void shouldThrowExceptionForInvalidExternStarRatio(double invalidValue) {
        InvalidStarParameterException exception = assertThrows(
                InvalidStarParameterException.class,
                () -> SnowflakeTopologyValidator.validateStarParameters(VALID_TREE_DEPTH, VALID_BRIDGE_DISTANCE, invalidValue)
        );

        assertExceptionDetails(exception, "EXTERN_STAR_RATIO", invalidValue, "zwischen 0.0 und 1.0");
    }

    @Test
    @DisplayName("Grenzwerte für Stern-Parameter sollten gültig sein")
    void shouldAcceptValidBoundaryValues() {
        // Minimale gültige Werte
        assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateStarParameters(1, 0, 0.0));

        // Maximale gültige Werte  
        assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateStarParameters(100, 50, 1.0));

        // Exakte Grenzwerte für Ratio
        assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateStarParameters(2, 1, 0.0));
        assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateStarParameters(2, 1, 1.0));
    }

    /**
     * Helper-Methode zur zentralen Validierung von Exception-Details.
     * Reduziert Code-Duplikation und verbessert Wartbarkeit.
     */
    private void assertExceptionDetails(InvalidStarParameterException exception,
                                        String expectedParameterName,
                                        Object expectedValue,
                                        String expectedConstraintPart) {
        assertEquals(expectedParameterName, exception.getParameterName());
        assertEquals(expectedValue, exception.getActualValue());
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains(expectedParameterName));
        assertTrue(exception.getMessage().contains(String.valueOf(expectedValue)));
        assertTrue(exception.getMessage().contains(expectedConstraintPart));
    }
}