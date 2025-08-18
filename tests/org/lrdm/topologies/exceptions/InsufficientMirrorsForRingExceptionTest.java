package org.lrdm.topologies.exceptions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

import org.lrdm.topologies.validators.SnowflakeTopologyValidator;

@DisplayName("InsufficientMirrorsForRingException Tests")
class InsufficientMirrorsForRingExceptionTest {

    @Nested
    @DisplayName("Ring-Konstruktion Exception Tests")
    class RingConstructionExceptionTests {

        @Test
        @DisplayName("Exception Details für unzureichende Ring-Mirrors")
        void testInsufficientMirrorsExceptionDetails() {
            InsufficientMirrorsForRingException exception = assertThrows(
                    InsufficientMirrorsForRingException.class,
                    () -> SnowflakeTopologyValidator.validateRingConstruction(2, 5, 2)
            );

            assertEquals(2, exception.getAvailableMirrors());
            assertEquals(5, exception.getRequiredMirrors());
            assertEquals(1, exception.getRingCount());
            assertNotNull(exception.getMessage());
            assertTrue(exception.getMessage().contains("Verfügbar=2"));
            assertTrue(exception.getMessage().contains("Benötigt=5"));
        }

        @Test
        @DisplayName("Zu wenige Mirrors für Ring-Konstruktion sollte Exception werfen")
        void testInsufficientMirrorsForRingConstruction() {
            InsufficientMirrorsForRingException exception = assertThrows(
                    InsufficientMirrorsForRingException.class,
                    () -> SnowflakeTopologyValidator.validateRingConstruction(2, 3, 2)
            );

            assertEquals(2, exception.getAvailableMirrors());
            assertEquals(3, exception.getRequiredMirrors());
            assertEquals(1, exception.getRingCount());
        }

        @ParameterizedTest
        @CsvSource({
                "1, 3, 1",
                "2, 3, 1",
                "4, 5, 1",
                "1, 5, 2",
                "3, 4, 1"
        })
        @DisplayName("Grenzfälle: Weniger Mirrors als minimal")
        void testBoundaryInsufficientMirrors(int availableMirrors, int minimalRequired, int layers) {
            assertThrows(InsufficientMirrorsForRingException.class, () -> {
                SnowflakeTopologyValidator.validateRingConstruction(availableMirrors, minimalRequired, layers);
            });
        }

        @Test
        @DisplayName("Extrem wenige Mirrors")
        void testExtremelyFewMirrors() {
            InsufficientMirrorsForRingException exception = assertThrows(
                    InsufficientMirrorsForRingException.class,
                    () -> SnowflakeTopologyValidator.validateRingConstruction(1, 10, 1)
            );

            assertEquals(1, exception.getAvailableMirrors());
            assertEquals(10, exception.getRequiredMirrors());
            assertEquals(1, exception.getRingCount());
        }
    }

    @Nested
    @DisplayName("Mirror-Distribution Exception Tests")
    class MirrorDistributionExceptionTests {

        @Test
        @DisplayName("Zu wenige Ring-Mirrors in Distribution sollte Exception werfen")
        void testInsufficientRingMirrorsInDistribution() {
            InsufficientMirrorsForRingException exception = assertThrows(
                    InsufficientMirrorsForRingException.class,
                    () -> SnowflakeTopologyValidator.validateMirrorDistribution(5, 2, 3, 3, 2)
            );

            assertEquals(2, exception.getAvailableMirrors());
            assertEquals(3, exception.getRequiredMirrors());
            assertEquals(1, exception.getRingCount());
        }

        @Test
        @DisplayName("Ring-Mirrors unter Minimum bei positiver Anzahl")
        void testRingMirrorsBelowMinimumWhenPositive() {
            // Ring-Mirrors sind positiv aber unter dem Minimum
            InsufficientMirrorsForRingException exception = assertThrows(
                    InsufficientMirrorsForRingException.class,
                    () -> SnowflakeTopologyValidator.validateMirrorDistribution(10, 2, 8, 5, 3)
            );

            assertEquals(2, exception.getAvailableMirrors());
            assertEquals(5, exception.getRequiredMirrors());
            assertEquals(1, exception.getRingCount());
        }

        @Test
        @DisplayName("Null Ring-Mirrors sollten keine InsufficientMirrors Exception werfen")
        void testZeroRingMirrorsDoNotThrowInsufficientException() {
            // Diese sollten andere Exceptions werfen, aber nicht InsufficientMirrorsForRingException
            assertThrows(InvalidMirrorDistributionException.class, () -> {
                SnowflakeTopologyValidator.validateMirrorDistribution(5, 0, 4, 3, 2); // Summe stimmt nicht
            });
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2, 4})
        @DisplayName("Ring-Mirrors unter Minimum von 5")
        void testRingMirrorsBelowMinimumOfFive(int ringMirrors) {
            int totalMirrors = ringMirrors + 5; // Ausreichend Star-Mirrors für korrekte Summe

            InsufficientMirrorsForRingException exception = assertThrows(
                    InsufficientMirrorsForRingException.class,
                    () -> SnowflakeTopologyValidator.validateMirrorDistribution(totalMirrors, ringMirrors, 5, 5, 2)
            );

            assertEquals(ringMirrors, exception.getAvailableMirrors());
            assertEquals(5, exception.getRequiredMirrors());
            assertEquals(1, exception.getRingCount());
        }
    }

    @Nested
    @DisplayName("Gültige Szenarien Tests")
    class ValidScenarioTests {

        @Test
        @DisplayName("Gültige Ring-Konstruktion mit ausreichenden Mirrors")
        void testSufficientMirrorsForRing() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingConstruction(9, 3, 2);
            });

            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingConstruction(15, 5, 3);
            });

            // Exakt minimale Anzahl
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingConstruction(3, 3, 1);
            });
        }

        @Test
        @DisplayName("Null Mirrors sollten gültig sein für Ring-Konstruktion")
        void testZeroMirrorsValidForRingConstruction() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingConstruction(0, 3, 2);
            });
        }

        @ParameterizedTest
        @CsvSource({
                "3, 3, 1",
                "6, 3, 2",
                "15, 5, 3",
                "20, 4, 5",
                "50, 10, 5"
        })
        @DisplayName("Exakte Minimal-Anforderungen erfüllt")
        void testExactMinimalRequirements(int availableMirrors, int minimalRequired, int layers) {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingConstruction(availableMirrors, minimalRequired, layers);
            });
        }

        @Test
        @DisplayName("Große Anzahl verfügbarer Mirrors")
        void testLargeNumberOfAvailableMirrors() {
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingConstruction(1000, 10, 5);
            });

            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingConstruction(500, 3, 100);
            });
        }
    }

    @Nested
    @DisplayName("Komplexe Validierungs-Szenarien")
    class ComplexValidationScenarioTests {

        @Test
        @DisplayName("Ring-Ebenen überschreiten verfügbare Mirrors")
        void testRingLayersExceedAvailableMirrors() {
            // Zu viele Ring-Ebenen für verfügbare Mirrors sollte andere Exception werfen
            assertThrows(InvalidMirrorDistributionException.class, () -> {
                SnowflakeTopologyValidator.validateRingConstruction(10, 5, 50); // 5*50=250, aber nur 10 verfügbar
            });
        }

        @Test
        @DisplayName("Kombination aus Mirror-Distribution und Ring-Konstruktion")
        void testCombinedDistributionAndConstruction() {
            // Erst Distribution validieren
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateMirrorDistribution(20, 12, 8, 4, 3);
            });

            // Dann Ring-Konstruktion validieren
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingConstruction(12, 4, 3);
            });
        }

        @Test
        @DisplayName("Inkonsistente Distribution und Konstruktion")
        void testInconsistentDistributionAndConstruction() {
            // Distribution ist gültig
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateMirrorDistribution(15, 10, 5, 3, 3);
            });

            // Aber Ring-Konstruktion mit weniger Mirrors als in Distribution angegeben
            assertThrows(InsufficientMirrorsForRingException.class, () -> {
                SnowflakeTopologyValidator.validateRingConstruction(2, 3, 1); // Nur 2 statt 10 aus Distribution
            });
        }
    }

    @Nested
    @DisplayName("Exception Nachricht Tests")
    class ExceptionMessageTests {

        @Test
        @DisplayName("Exception Nachricht enthält alle relevanten Informationen")
        void testExceptionMessageContent() {
            InsufficientMirrorsForRingException exception = assertThrows(
                    InsufficientMirrorsForRingException.class,
                    () -> SnowflakeTopologyValidator.validateRingConstruction(2, 8, 1)
            );

            String message = exception.getMessage();
            assertNotNull(message);
            assertTrue(message.contains("Verfügbar=2"));
            assertTrue(message.contains("Benötigt=8"));
            assertTrue(message.contains("Ring"));
            assertTrue(message.toLowerCase().contains("unzureichend") ||
                    message.toLowerCase().contains("insufficient"));
        }

        @Test
        @DisplayName("Exception Getter-Methoden funktionieren korrekt")
        void testExceptionGetters() {
            InsufficientMirrorsForRingException exception = assertThrows(
                    InsufficientMirrorsForRingException.class,
                    () -> SnowflakeTopologyValidator.validateRingConstruction(5, 10, 2)
            );

            assertEquals(5, exception.getAvailableMirrors());
            assertEquals(10, exception.getRequiredMirrors());
            assertEquals(1, exception.getRingCount()); // safeRingCount wird berechnet
        }

        @Test
        @DisplayName("Exception-Details bei Distribution-Validierung")
        void testExceptionDetailsFromDistributionValidation() {
            InsufficientMirrorsForRingException exception = assertThrows(
                    InsufficientMirrorsForRingException.class,
                    () -> SnowflakeTopologyValidator.validateMirrorDistribution(8, 1, 7, 5, 2)
            );

            assertEquals(1, exception.getAvailableMirrors());
            assertEquals(5, exception.getRequiredMirrors());
            assertEquals(1, exception.getRingCount());
        }
    }
}