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

    // Konstanten für Tests (entsprechend SnowflakeTopologyValidator)
    private static final int DEFAULT_MIN_RING_MIRROR_COUNT = 3;
    private static final int DEFAULT_MAX_RING_LAYERS = 5;

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
            assertThrows(InsufficientMirrorsForRingException.class, () -> SnowflakeTopologyValidator.validateRingConstruction(availableMirrors, minimalRequired, layers));
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
        @ValueSource(ints = {1, 2})
        @DisplayName("Ring-Mirrors unter Standard-Minimum von 3")
        void testRingMirrorsBelowMinimumOfThree(int ringMirrors) {
            int totalMirrors = ringMirrors + 5; // Ausreichend Star-Mirrors für korrekte Summe

            InsufficientMirrorsForRingException exception = assertThrows(
                    InsufficientMirrorsForRingException.class,
                    () -> SnowflakeTopologyValidator.validateMirrorDistribution(
                            totalMirrors, ringMirrors, 5, DEFAULT_MIN_RING_MIRROR_COUNT, DEFAULT_MAX_RING_LAYERS)
            );

            assertEquals(ringMirrors, exception.getAvailableMirrors());
            assertEquals(DEFAULT_MIN_RING_MIRROR_COUNT, exception.getRequiredMirrors());
            assertEquals(1, exception.getRingCount());
        }
    }

    @Nested
    @DisplayName("Gültige Szenarien Tests")
    class ValidScenarioTests {

        @Test
        @DisplayName("Gültige Ring-Konstruktion mit ausreichenden Mirrors")
        void testSufficientMirrorsForRing() {
            // Teste mit Mirrors, die nicht zu viele Ringe ergeben würden
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingConstruction(6, 3, 2); // 6/3 = 2 Ringe, max ist 5
            });

            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingConstruction(15, 5, 3); // 15/5 = 3 Ringe, max ist 5
            });

            // Exakt minimale Anzahl
            assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateRingConstruction(3, 3, 1));
        }

        @Test
        @DisplayName("Null Mirrors sollten gültig sein für Ring-Konstruktion")
        void testZeroMirrorsValidForRingConstruction() {
            assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateRingConstruction(0, 3, 2));
        }

        @ParameterizedTest
        @CsvSource({
                "3, 3, 1",    // 3/3 = 1 Ring, max ist 5
                "6, 3, 2",    // 6/3 = 2 Ringe, max ist 5
                "15, 5, 3",   // 15/5 = 3 Ringe, max ist 5
                "20, 5, 4"    // 20/5 = 4 Ringe, max ist 5
        })
        @DisplayName("Exakte Minimal-Anforderungen erfüllt ohne Überschreitung der Ring-Grenzen")
        void testExactMinimalRequirements(int availableMirrors, int minimalRequired, int layers) {
            assertDoesNotThrow(() -> SnowflakeTopologyValidator.validateRingConstruction(availableMirrors, minimalRequired, layers));
        }

        @Test
        @DisplayName("Große Anzahl verfügbarer Mirrors innerhalb der Ring-Grenzen")
        void testLargeNumberOfAvailableMirrorsWithinLimits() {
            // Berechne sichere Werte, die nicht das Maximum von 5 Ringen überschreiten
            assertDoesNotThrow(() -> {
                // 25 Mirrors / 5 pro Ring = 5 Ringe (genau das Maximum)
                SnowflakeTopologyValidator.validateRingConstruction(25, 5, 5);
            });

            assertDoesNotThrow(() -> {
                // 12 Mirrors / 4 pro Ring = 3 Ringe (unter dem Maximum)
                SnowflakeTopologyValidator.validateRingConstruction(12, 4, 3);
            });
        }
    }

    @Nested
    @DisplayName("Komplexe Validierungs-Szenarien")
    class ComplexValidationScenarioTests {

        @Test
        @DisplayName("Ring-Anzahl überschreitet Maximum sollte InvalidMirrorDistribution werfen")
        void testRingCountExceedsMaximum() {
            // Zu viele berechnete Ringe sollte InvalidMirrorDistributionException werfen
            assertThrows(InvalidMirrorDistributionException.class, () -> {
                // 30 Mirrors / 3 pro Ring = 10 Ringe, aber Maximum ist 5
                SnowflakeTopologyValidator.validateRingConstruction(30, 3, 5);
            });
        }

        @Test
        @DisplayName("Kombination aus Mirror-Distribution und Ring-Konstruktion")
        void testCombinedDistributionAndConstruction() {
            // Erst Distribution validieren (mit sicheren Werten)
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateMirrorDistribution(15, 9, 6, 3, 5); // 9/3 = 3 Ringe
            });

            // Dann Ring-Konstruktion validieren
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateRingConstruction(9, 3, 5); // 9/3 = 3 Ringe
            });
        }

        @Test
        @DisplayName("Inkonsistente Distribution und Konstruktion")
        void testInconsistentDistributionAndConstruction() {
            // Distribution ist gültig
            assertDoesNotThrow(() -> {
                SnowflakeTopologyValidator.validateMirrorDistribution(12, 9, 3, 3, 5); // 9/3 = 3 Ringe
            });

            // Aber Ring-Konstruktion mit weniger Mirrors als in Distribution angegeben
            assertThrows(InsufficientMirrorsForRingException.class, () -> {
                SnowflakeTopologyValidator.validateRingConstruction(2, 3, 1); // Nur 2 statt 9 aus Distribution
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
            assertTrue(message.contains("Ring") || message.toLowerCase().contains("ring"));
            assertTrue(message.toLowerCase().contains("genügend") ||
                    message.toLowerCase().contains("sufficient"));
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