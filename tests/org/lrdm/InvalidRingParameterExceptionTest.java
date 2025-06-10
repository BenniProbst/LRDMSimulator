package org.lrdm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

import org.lrdm.topologies.exceptions.InvalidRingParameterException;
import org.lrdm.topologies.validators.SnowflakeTopologyValidator;

@DisplayName("InvalidRingParameterException Tests")
class InvalidRingParameterExceptionTest {

    // Test-Konstanten für bessere Lesbarkeit
    private static final int VALID_RING_BRIDGE_STEP = 2;
    private static final int VALID_RING_BRIDGE_OFFSET = 1;
    private static final int VALID_RING_BRIDGE_MIRROR_HEIGHT = 2;
    private static final int VALID_MAX_RING_LAYERS = 2;
    private static final int VALID_MINIMAL_RING_MIRROR_COUNT = 3;

    @Test
    @DisplayName("Exception Details für RING_BRIDGE_STEP_ON_RING")
    void testRingBridgeStepOnRingExceptionDetails() {
        InvalidRingParameterException exception = assertThrows(
                InvalidRingParameterException.class,
                () -> SnowflakeTopologyValidator.validateRingParameters(-5, VALID_RING_BRIDGE_OFFSET,
                        VALID_RING_BRIDGE_MIRROR_HEIGHT, VALID_MAX_RING_LAYERS, VALID_MINIMAL_RING_MIRROR_COUNT)
        );
        assertInvalidRingParameterException(exception, "RING_BRIDGE_STEP_ON_RING", -5, "muss mindestens 0 sein");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -5, -10})
    @DisplayName("Ungültiger RING_BRIDGE_STEP_ON_RING sollte Exception werfen")
    void testInvalidRingBridgeStepOnRing(int invalidValue) {
        InvalidRingParameterException exception = assertThrows(
                InvalidRingParameterException.class,
                () -> SnowflakeTopologyValidator.validateRingParameters(invalidValue, VALID_RING_BRIDGE_OFFSET,
                        VALID_RING_BRIDGE_MIRROR_HEIGHT, VALID_MAX_RING_LAYERS, VALID_MINIMAL_RING_MIRROR_COUNT)
        );
        assertInvalidRingParameterException(exception, "RING_BRIDGE_STEP_ON_RING", invalidValue, "muss mindestens 0 sein");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -3, -7})
    @DisplayName("Ungültiger RING_BRIDGE_OFFSET sollte Exception werfen")
    void testInvalidRingBridgeOffset(int invalidValue) {
        InvalidRingParameterException exception = assertThrows(
                InvalidRingParameterException.class,
                () -> SnowflakeTopologyValidator.validateRingParameters(VALID_RING_BRIDGE_STEP, invalidValue,
                        VALID_RING_BRIDGE_MIRROR_HEIGHT, VALID_MAX_RING_LAYERS, VALID_MINIMAL_RING_MIRROR_COUNT)
        );
        assertInvalidRingParameterException(exception, "RING_BRIDGE_OFFSET", invalidValue, "muss mindestens 0 sein");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -5})
    @DisplayName("Ungültiger RING_BRIDGE_MIRROR_NUM_HEIGHT sollte Exception werfen")
    void testInvalidRingBridgeMirrorNumHeight(int invalidValue) {
        InvalidRingParameterException exception = assertThrows(
                InvalidRingParameterException.class,
                () -> SnowflakeTopologyValidator.validateRingParameters(VALID_RING_BRIDGE_STEP, VALID_RING_BRIDGE_OFFSET,
                        invalidValue, VALID_MAX_RING_LAYERS, VALID_MINIMAL_RING_MIRROR_COUNT)
        );
        assertInvalidRingParameterException(exception, "RING_BRIDGE_MIRROR_NUM_HEIGHT", invalidValue, "muss mindestens 1 sein");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -3})
    @DisplayName("Ungültiger MAX_RING_LAYERS sollte Exception werfen")
    void testInvalidMaxRingLayers(int invalidValue) {
        InvalidRingParameterException exception = assertThrows(
                InvalidRingParameterException.class,
                () -> SnowflakeTopologyValidator.validateRingParameters(VALID_RING_BRIDGE_STEP, VALID_RING_BRIDGE_OFFSET,
                        VALID_RING_BRIDGE_MIRROR_HEIGHT, invalidValue, VALID_MINIMAL_RING_MIRROR_COUNT)
        );
        assertInvalidRingParameterException(exception, "MAX_RING_LAYERS", invalidValue, "muss mindestens 1 sein");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, -1})
    @DisplayName("Ungültiger MINIMAL_RING_MIRROR_COUNT sollte Exception werfen")
    void testInvalidMinimalRingMirrorCount(int invalidValue) {
        InvalidRingParameterException exception = assertThrows(
                InvalidRingParameterException.class,
                () -> SnowflakeTopologyValidator.validateRingParameters(VALID_RING_BRIDGE_STEP, VALID_RING_BRIDGE_OFFSET,
                        VALID_RING_BRIDGE_MIRROR_HEIGHT, VALID_MAX_RING_LAYERS, invalidValue)
        );
        assertInvalidRingParameterException(exception, "MINIMAL_RING_MIRROR_COUNT", invalidValue, "muss mindestens 3 sein");
        assertTrue(exception.getMessage().contains("Dreieck-Minimum"));
    }

    /**
     * Zentrale Hilfsmethode zur Validierung von InvalidRingParameterException-Eigenschaften.
     * Reduziert Codeduplikation und stellt konsistente Assertion-Patterns sicher.
     */
    private void assertInvalidRingParameterException(InvalidRingParameterException exception,
                                                     String expectedParameterName,
                                                     int expectedValue,
                                                     String expectedConstraint) {
        assertEquals(expectedParameterName, exception.getParameterName());
        assertEquals(expectedValue, exception.getActualValue());
        assertEquals(expectedConstraint, exception.getConstraint());
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains(expectedParameterName));
        assertTrue(exception.getMessage().contains(String.valueOf(expectedValue)));
        assertTrue(exception.getMessage().contains(expectedConstraint));
    }
}