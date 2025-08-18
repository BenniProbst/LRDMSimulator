package org.lrdm.topologies.exceptions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

import org.lrdm.topologies.validators.SnowflakeTopologyValidator;

@DisplayName("InvalidRingParameterException Tests")
class InvalidRingParameterExceptionTest {

    // Aktuell ist nur MINIMAL_RING_MIRROR_COUNT aktiv implementiert
    private static final int VALID_MINIMAL_RING_MIRROR_COUNT = 3;

    // Helper method für konsistente Exception-Erstellung
    private InvalidRingParameterException createRingParameterException(int minimalRingMirrorCount) {
        return assertThrows(
                InvalidRingParameterException.class,
                () -> SnowflakeTopologyValidator.validateRingParameters(minimalRingMirrorCount)
        );
    }

    @Nested
    @DisplayName("Aktive Parameter Tests")
    class ActiveParameterTests {

        @Test
        @DisplayName("Exception Details für MINIMAL_RING_MIRROR_COUNT")
        void testMinimalRingMirrorCountExceptionDetails() {
            InvalidRingParameterException exception = createRingParameterException(2);

            assertEquals("MINIMAL_RING_MIRROR_COUNT", exception.getParameterName());
            assertEquals(2, exception.getActualValue());
            assertEquals("muss mindestens 3 sein (Dreieck-Minimum)", exception.getConstraint());
            assertNotNull(exception.getMessage());
            assertTrue(exception.getMessage().contains("MINIMAL_RING_MIRROR_COUNT"));
            assertTrue(exception.getMessage().contains("2"));
            assertTrue(exception.getMessage().contains("Dreieck-Minimum"));
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 2, -1, -5, -10})
        @DisplayName("Ungültiger MINIMAL_RING_MIRROR_COUNT sollte Exception werfen")
        void testInvalidMinimalRingMirrorCount(int invalidValue) {
            InvalidRingParameterException exception = createRingParameterException(invalidValue);

            assertEquals("MINIMAL_RING_MIRROR_COUNT", exception.getParameterName());
            assertEquals(invalidValue, exception.getActualValue());
            assertEquals("muss mindestens 3 sein (Dreieck-Minimum)", exception.getConstraint());
            assertTrue(exception.getMessage().contains("Dreieck-Minimum"));
        }

        @Test
        @DisplayName("Grenzwert: Genau unter dem Minimum")
        void testExactlyBelowMinimum() {
            InvalidRingParameterException exception = createRingParameterException(2);

            assertEquals(2, exception.getActualValue());
            assertTrue(exception.getMessage().contains("muss mindestens 3 sein"));
        }

        @Test
        @DisplayName("Extremer negativer Wert")
        void testExtremeNegativeValue() {
            InvalidRingParameterException exception = createRingParameterException(-100);

            assertEquals(-100, exception.getActualValue());
            assertTrue(exception.getMessage().contains("MINIMAL_RING_MIRROR_COUNT"));
        }
    }

    @Nested
    @DisplayName("Grenzwerte und Edge Cases")
    class BoundaryAndEdgeCaseTests {

        @ParameterizedTest
        @CsvSource({
                "0, Null Wert",
                "1, Ein Mirror",
                "2, Zwei Mirrors",
                "-1, Negativer Wert",
                "-999, Extrem negativer Wert"
        })
        @DisplayName("Verschiedene ungültige Werte mit Beschreibungen")
        void testVariousInvalidValuesWithDescriptions(int invalidValue, String description) {
            InvalidRingParameterException exception = createRingParameterException(invalidValue);

            assertEquals("MINIMAL_RING_MIRROR_COUNT", exception.getParameterName());
            assertEquals(invalidValue, exception.getActualValue());
            assertTrue(exception.getMessage().contains("muss mindestens 3 sein"));
            assertNotNull(exception.getMessage(), "Exception message should not be null for: " + description);
        }

        @Test
        @DisplayName("Grenzwert-Test: Direkt unter Minimum")
        void testDirectlyBelowMinimum() {
            // Test für 2 (direkt unter dem Minimum von 3)
            InvalidRingParameterException exception = createRingParameterException(2);

            assertEquals(2, exception.getActualValue());
            assertTrue(exception.getMessage().contains("3"));
        }

        @Test
        @DisplayName("Grenzwert-Test: Minimal gültiger Wert sollte keine Exception werfen")
        void testMinimalValidValue() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(3);
            });
        }

        @Test
        @DisplayName("Große gültige Werte sollten keine Exception werfen")
        void testLargeValidValues() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(10);
                SnowflakeTopologyValidator.validateRingParameters(100);
                SnowflakeTopologyValidator.validateRingParameters(1000);
            });
        }
    }

    @Nested
    @DisplayName("Exception Format und Nachricht Tests")
    class ExceptionFormatAndMessageTests {

        @Test
        @DisplayName("Exception Nachricht Format und Inhalt")
        void testExceptionMessageFormatAndContent() {
            InvalidRingParameterException exception = createRingParameterException(1);

            String message = exception.getMessage();
            assertNotNull(message);
            assertTrue(message.contains("MINIMAL_RING_MIRROR_COUNT"));
            assertTrue(message.contains("1"));
            assertTrue(message.contains("muss mindestens 3 sein"));
            assertTrue(message.contains("Dreieck-Minimum"));
        }

        @Test
        @DisplayName("Exception Getter-Methoden funktionieren korrekt")
        void testExceptionGettersWorkCorrectly() {
            InvalidRingParameterException exception = createRingParameterException(0);

            assertEquals("MINIMAL_RING_MIRROR_COUNT", exception.getParameterName());
            assertEquals(0, exception.getActualValue());
            assertEquals("muss mindestens 3 sein (Dreieck-Minimum)", exception.getConstraint());
            assertNotNull(exception.getMessage());
        }

        @Test
        @DisplayName("Exception toString() enthält relevante Informationen")
        void testExceptionToStringContainsRelevantInfo() {
            InvalidRingParameterException exception = createRingParameterException(-5);

            String toString = exception.toString();
            assertTrue(toString.contains("InvalidRingParameterException"));
            assertTrue(toString.contains("MINIMAL_RING_MIRROR_COUNT") || toString.contains("-5"));
        }

        @ParameterizedTest
        @ValueSource(ints = {-10, -1, 0, 1, 2})
        @DisplayName("Exception Nachrichten sind konsistent für verschiedene Werte")
        void testExceptionMessagesAreConsistentForDifferentValues(int invalidValue) {
            InvalidRingParameterException exception = createRingParameterException(invalidValue);

            // Alle Nachrichten sollten die gleichen Kern-Elemente enthalten
            String message = exception.getMessage();
            assertTrue(message.contains("MINIMAL_RING_MIRROR_COUNT"));
            assertTrue(message.contains(String.valueOf(invalidValue)));
            assertTrue(message.contains("3"));
            assertTrue(message.contains("Dreieck-Minimum"));
        }
    }

    @Nested
    @DisplayName("Gültige Konfigurationen Tests")
    class ValidConfigurationTests {

        @Test
        @DisplayName("Minimal gültige Ring-Parameter")
        void testMinimalValidRingParameters() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(3);
            });
        }

        @ParameterizedTest
        @ValueSource(ints = {3, 4, 5, 6, 10, 15, 20, 50, 100})
        @DisplayName("Verschiedene gültige MINIMAL_RING_MIRROR_COUNT Werte")
        void testVariousValidMinimalRingMirrorCountValues(int validValue) {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(validValue);
            });
        }

        @Test
        @DisplayName("Große gültige Werte für Ring-Parameter")
        void testLargeValidRingParameterValues() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(500);
                SnowflakeTopologyValidator.validateRingParameters(1000);
            });
        }

        @Test
        @DisplayName("Typische Anwendungsfälle")
        void testTypicalUseCases() {
            // Typische Werte die in der Praxis verwendet werden könnten
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingParameters(3);  // Dreieck
                SnowflakeTopologyValidator.validateRingParameters(4);  // Quadrat
                SnowflakeTopologyValidator.validateRingParameters(5);  // Pentagon
                SnowflakeTopologyValidator.validateRingParameters(6);  // Hexagon
                SnowflakeTopologyValidator.validateRingParameters(8);  // Oktagon
            });
        }
    }

    @Nested
    @DisplayName("Auskommentierte Parameter Tests - Für zukünftige Aktivierung")
    class CommentedParametersTests {

        @Test
        @DisplayName("Info: Derzeit auskommentierte Parameter werden nicht getestet")
        void testCurrentlyCommentedParametersInfo() {
            // Dieser Test dokumentiert, welche Parameter derzeit nicht aktiv sind
            // und dient als Erinnerung für zukünftige Implementierung

            String[] commentedParameters = {
                    "RING_BRIDGE_STEP_ON_RING",
                    "RING_BRIDGE_OFFSET",
                    "RING_BRIDGE_MIRROR_NUM_HEIGHT",
                    "MAX_RING_LAYERS"
            };

            // Dokumentiere die auskommentierte Parameter für zukünftige Referenz
            assertNotNull(commentedParameters);
            assertEquals(4, commentedParameters.length);

            // Hinweis: Wenn diese Parameter wieder aktiviert werden,
            // sollten entsprechende Tests hinzugefügt werden
        }

        // TODO: Wenn Parameter wieder aktiviert werden, diese Tests einkommentieren:

        /*
        @ParameterizedTest
        @ValueSource(ints = {-1, -5, -10})
        @DisplayName("Ungültiger RING_BRIDGE_STEP_ON_RING sollte Exception werfen")
        void testInvalidRingBridgeStepOnRing(int invalidValue) {
            // Wird aktiviert wenn validateRingParameters wieder mehr Parameter akzeptiert
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, -3, -7})
        @DisplayName("Ungültiger RING_BRIDGE_OFFSET sollte Exception werfen")
        void testInvalidRingBridgeOffset(int invalidValue) {
            // Wird aktiviert wenn validateRingParameters wieder mehr Parameter akzeptiert
        }
        */
    }

    /**
     * Zentrale Hilfsmethode zur Validierung von InvalidRingParameterException-Eigenschaften.
     * Reduziert Code-Duplikation und stellt konsistente Assertion-Patterns sicher.
     *
     * @param exception Die zu validierende Exception
     * @param expectedParameterName Der erwartete Parameter-Name
     * @param expectedValue Der erwartete ungültige Wert
     * @param expectedConstraint Die erwartete Constraint-Beschreibung
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
        assertTrue(exception.getMessage().contains("muss mindestens"));
    }
}