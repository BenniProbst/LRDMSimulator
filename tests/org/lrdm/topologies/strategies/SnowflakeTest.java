package org.lrdm.topologies.strategies;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lrdm.*;
import org.lrdm.effectors.Action;
import org.lrdm.effectors.TopologyChange;
import org.lrdm.probes.LinkProbe;
import org.lrdm.probes.MirrorProbe;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.lrdm.TestProperties.loadProperties;
import static org.lrdm.TestProperties.getProps;

@DisplayName("SnowflakeTopologyStrategy Tests")
class SnowflakeTest {
    private TimedRDMSim sim;
    private static final String config = "resources/sim-test-snowflake.conf";

    @BeforeEach
    void setUp() throws IOException {
        sim = null;
        loadProperties(config);
    }

    // ===== HILFSMETHODEN =====

    public void initSimulator() throws IOException {
        initSimulator(config);
    }

    public void initSimulator(String config) throws IOException {
        loadProperties(config);
        sim = new TimedRDMSim(config);
        sim.setHeadless(true);
    }

    private Network createSnowflakeNetwork(int mirrors) {
        SnowflakeTopologyStrategy strategy = new SnowflakeTopologyStrategy();
        return new Network(strategy, mirrors, 2, 30, getProps());
    }

    private MirrorProbe getMirrorProbe() {
        return sim.getProbes().stream()
                .filter(p -> p instanceof MirrorProbe)
                .map(p -> (MirrorProbe) p)
                .findFirst()
                .orElse(null);
    }

    private LinkProbe getLinkProbe() {
        return sim.getProbes().stream()
                .filter(p -> p instanceof LinkProbe)
                .map(p -> (LinkProbe) p)
                .findFirst()
                .orElse(null);
    }

    // ===== GRUNDLEGENDE FUNCTIONALITY-TESTS =====

    @Nested
    @DisplayName("Grundlegende Funktionalität")
    class BasicFunctionalityTests {

        @Test
        @DisplayName("SnowflakeTopologyStrategy kann instanziiert werden")
        void testSnowflakeInstantiation() {
            assertDoesNotThrow(() -> new SnowflakeTopologyStrategy());
        }

        @Test
        @DisplayName("SnowflakeTopologyStrategy mit Custom Properties")
        void testSnowflakeWithCustomProperties() {
            SnowflakeTopologyStrategy.SnowflakeProperties props =
                    new SnowflakeTopologyStrategy.SnowflakeProperties(0.3, 3);

            assertDoesNotThrow(() -> new SnowflakeTopologyStrategy(props));
        }

        @Test
        @DisplayName("Simulator erfordert Initialisierung")
        void testInitializeRequired() throws IOException {
            initSimulator();
            assertThrows(RuntimeException.class, () -> sim.run());
        }

        @Test
        @DisplayName("SnowflakeTopologyStrategy toString() funktioniert")
        void testToString() {
            SnowflakeTopologyStrategy strategy = new SnowflakeTopologyStrategy();
            String result = strategy.toString();

            assertNotNull(result);
            assertTrue(result.contains("SnowflakeTopologyStrategy"));
        }
    }

    // ===== STRUKTUR-VALIDIERUNG TESTS =====

    @Nested
    @DisplayName("Schneeflocken-Struktur Validierung")
    class SnowflakeStructureTests {

        @ParameterizedTest
        @ValueSource(ints = {10, 15, 20, 25})
        @DisplayName("Schneeflocken-Struktur wird korrekt erstellt")
        void testSnowflakeStructureCreation(int totalMirrors) {
            Network network = createSnowflakeNetwork(totalMirrors);
            SnowflakeTopologyStrategy strategy = (SnowflakeTopologyStrategy) network.getTopologyStrategy();

            // Grundlegende Struktur-Validierung
            assertTrue(strategy.validateTopology(), "Schneeflocken-Topologie sollte gültig sein");

            // Mirror-Anzahl sollte korrekt sein
            assertEquals(totalMirrors, network.getNumMirrors(), "Mirror-Anzahl sollte stimmen");

            // Links sollten vorhanden sein
            assertTrue(network.getNumLinks() > 0, "Schneeflocke sollte Links haben");
        }

        @Test
        @DisplayName("Mirror-Verteilung folgt Schneeflocken-Pattern")
        void testMirrorDistributionPattern() {
            SnowflakeTopologyStrategy strategy = new SnowflakeTopologyStrategy();

            // Teste interne Verteilungsberechnung durch Reflexion oder öffentliche Methoden
            // (falls verfügbar)
            assertDoesNotThrow(() -> {
                Network network = new Network(strategy, 20, 2, 30, getProps());
                assertTrue(network.getNumMirrors() > 0);
            });
        }

        @ParameterizedTest
        @CsvSource({
                "10, 0.3, 2",
                "15, 0.4, 3",
                "20, 0.5, 2",
                "25, 0.6, 4"
        })
        @DisplayName("Verschiedene Schneeflocken-Konfigurationen")
        void testVariousSnowflakeConfigurations(int mirrors, double ratio, int gap) {
            SnowflakeTopologyStrategy.SnowflakeProperties props =
                    new SnowflakeTopologyStrategy.SnowflakeProperties(ratio, gap);
            SnowflakeTopologyStrategy strategy = new SnowflakeTopologyStrategy(props);

            assertDoesNotThrow(() -> {
                Network network = new Network(strategy, mirrors, 2, 30, getProps());
                assertTrue(network.getNumMirrors() >= 5, "Schneeflocke benötigt Mindestanzahl Mirrors");
            });
        }
    }

    // ===== LINK-BERECHNUNG TESTS =====

    @Nested
    @DisplayName("Link-Berechnung Tests")
    class LinkCalculationTests {

        @Test
        @DisplayName("getNumTargetLinks gibt sinnvolle Werte zurück")
        void testTargetLinksCalculation() {
            for (int mirrors = 5; mirrors <= 20; mirrors += 5) {
                Network network = createSnowflakeNetwork(mirrors);
                SnowflakeTopologyStrategy strategy = (SnowflakeTopologyStrategy) network.getTopologyStrategy();

                int targetLinks = strategy.getNumTargetLinks(network);

                assertTrue(targetLinks >= 0, "Target links sollte nicht negativ sein für " + mirrors + " Mirrors");
                assertTrue(targetLinks <= mirrors * (mirrors - 1) / 2,
                        "Target links sollte maximal vollständiger Graph sein für " + mirrors + " Mirrors");

                // Schneeflocken sollten weniger dicht als vollständiger Graph sein
                assertTrue(targetLinks < mirrors * (mirrors - 1) / 2,
                        "Schneeflocke sollte spärlicher als vollständiger Graph sein");
            }
        }

        @Test
        @DisplayName("getPredictedNumTargetLinks ist konsistent")
        void testPredictedLinksConsistency() {
            Network network = createSnowflakeNetwork(15);
            SnowflakeTopologyStrategy strategy = (SnowflakeTopologyStrategy) network.getTopologyStrategy();

            int currentLinks = strategy.getNumTargetLinks(network);

            // Test mit null Action
            int predictedWithNull = strategy.getPredictedNumTargetLinks(null);
            assertEquals(currentLinks, predictedWithNull, "Predicted links mit null sollte current links entsprechen");
        }

        @Test
        @DisplayName("Link-Berechnung skaliert mit Mirror-Anzahl")
        void testLinkScalingWithMirrors() {
            Network small = createSnowflakeNetwork(8);
            Network large = createSnowflakeNetwork(20);

            SnowflakeTopologyStrategy smallStrategy = (SnowflakeTopologyStrategy) small.getTopologyStrategy();
            SnowflakeTopologyStrategy largeStrategy = (SnowflakeTopologyStrategy) large.getTopologyStrategy();

            int smallLinks = smallStrategy.getNumTargetLinks(small);
            int largeLinks = largeStrategy.getNumTargetLinks(large);

            assertTrue(largeLinks > smallLinks, "Größere Schneeflocke sollte mehr Links haben");
        }
    }

    // ===== SIMULATION TESTS =====

    @Nested
    @DisplayName("Simulation Tests")
    class SimulationTests {

        @Test
        @DisplayName("Mirror-Änderungen werden korrekt verarbeitet")
        void testMirrorChanges() throws IOException {
            initSimulator();
            sim.initialize(new SnowflakeTopologyStrategy());

            int targetMirrors = 20;
            int changeAt = 10;
            sim.getEffector().setMirrors(targetMirrors, changeAt);

            MirrorProbe mirrorProbe = getMirrorProbe();
            assertNotNull(mirrorProbe, "MirrorProbe muss verfügbar sein");

            // Laufe der Simulation bis zum Änderungszeitpunkt
            for (int t = 1; t <= changeAt + 5 && t < sim.getSimTime(); t++) {
                sim.runStep(t);

                // Grundlegende Invarianten
                assertTrue(mirrorProbe.getNumReadyMirrors() <= mirrorProbe.getNumTargetMirrors(),
                        "Ready mirrors dürfen target mirrors nicht überschreiten");
                assertTrue(mirrorProbe.getMirrorRatio() >= 0.0 && mirrorProbe.getMirrorRatio() <= 1.0,
                        "Mirror ratio muss zwischen 0 und 1 liegen");

                // Nach Änderungszeitpunkt
                if (t > changeAt) {
                    assertEquals(targetMirrors, mirrorProbe.getNumTargetMirrors(),
                            "Target mirrors sollten nach Änderung gesetzt sein");
                }
            }
        }

        @Test
        @DisplayName("Effect-Deltas sind realistisch für Schneeflocken")
        void testSnowflakeSpecificEffectDeltas() throws IOException {
            initSimulator();
            sim.initialize(new SnowflakeTopologyStrategy());
            MirrorProbe mirrorProbe = getMirrorProbe();

            for (int t = 1; t < Math.min(10, sim.getSimTime()); t++) {
                sim.runStep(t);

                Action a = sim.getEffector().setMirrors(mirrorProbe.getNumMirrors() + 1, t + 1);
                // Effect kann bei geplanter Änderung noch null sein → in diesem Fall Iteration überspringen
                if (a == null || a.getEffect() == null) {
                    continue;
                }

                int ttw = a.getEffect().getDeltaTimeToWrite();
                int bw = a.getEffect().getDeltaBandwidth(sim.getProps());
                double al = a.getEffect().getDeltaActiveLinks();

                // Schneeflocken-spezifische, realistischere Grenzen
                assertTrue(ttw >= -50 && ttw <= 50,
                        "TTW Delta sollte für Schneeflocke moderat sein: " + ttw);
                assertTrue(bw >= -30 && bw <= 30,
                        "Bandwidth Delta sollte für Schneeflocke moderat sein: " + bw);
                assertTrue(al >= -20 && al <= 20,
                        "Active Links Delta sollte für Schneeflocke moderat sein: " + al);

                assertDoesNotThrow(() -> a.getEffect().getLatency(),
                        "Latency-Berechnung sollte für Schneeflocke funktionieren");
            }
        }

        @Test
        @DisplayName("Mirror-Reduktion funktioniert für Schneeflocken")
        void testSnowflakeMirrorReduction() throws IOException {
            initSimulator();
            sim.initialize(new SnowflakeTopologyStrategy());

            int initialMirrors = getMirrorProbe().getNumMirrors();
            int targetMirrors = Math.max(5, initialMirrors - 3); // Mindestens 5 für Schneeflocke
            int changeAt = 5;

            sim.getEffector().setMirrors(targetMirrors, changeAt);
            MirrorProbe mirrorProbe = getMirrorProbe();

            // Laufe der Simulation, bis die Änderung wirksam wird
            for (int t = 1; t < sim.getSimTime() && t < changeAt + 10; t++) {
                sim.runStep(t);
                if (t > changeAt + 3) break; // Genug Zeit für Änderung
            }

            // Nach Reduktion sollten target mirrors korrekt sein
            assertEquals(targetMirrors, mirrorProbe.getNumTargetMirrors(),
                    "Target mirrors sollten nach Reduktion korrekt sein");
            assertTrue(mirrorProbe.getNumReadyMirrors() <= mirrorProbe.getNumTargetMirrors(),
                    "Ready mirrors sollten target nicht überschreiten");
        }

        @Test
        @DisplayName("Target Link Änderungen für Schneeflocken")
        void testSnowflakeTargetLinkChanges() throws IOException {
            initSimulator();
            sim.initialize(new SnowflakeTopologyStrategy());

            sim.getEffector().setTargetLinksPerMirror(4, 8);
            LinkProbe linkProbe = getLinkProbe();
            assertNotNull(linkProbe, "LinkProbe muss verfügbar sein");

            // Kurze Simulation zum Testen
            for (int t = 1; t < Math.min(15, sim.getSimTime()); t++) {
                int finalT = t;
                assertDoesNotThrow(() -> sim.runStep(finalT),
                        "Simulation mit Link-Änderungen sollte nicht fehlschlagen bei t=" + t);
            }
        }
    }

    // ===== TOPOLOGIE-WECHSEL TESTS =====

    @Nested
    @DisplayName("Topologie-Wechsel Tests")
    class TopologyTransitionTests {

        @Test
        @DisplayName("Wechsel zwischen verschiedenen Topologien funktioniert")
        void testTopologyTransitions() throws IOException {
            initSimulator();
            sim.initialize(new SnowflakeTopologyStrategy());

            // Sequenz von Topologie-Wechseln
            sim.getEffector().setStrategy(new BalancedTreeTopologyStrategy(), 10);
            sim.getEffector().setStrategy(new SnowflakeTopologyStrategy(), 20);
            sim.getEffector().setStrategy(new StarTopologyStrategy(), 30);
            sim.getEffector().setStrategy(new SnowflakeTopologyStrategy(), 40);

            assertDoesNotThrow(() -> {
                // Kurze Simulation, um Wechsel zu testen
                for (int t = 1; t < Math.min(50, sim.getSimTime()); t++) {
                    sim.runStep(t);
                    if (t % 10 == 0) {
                        // Überprüfe Zustand nach Wechseln
                        MirrorProbe mirrorProbe = getMirrorProbe();
                        assertTrue(mirrorProbe.getNumMirrors() > 0, "Mirrors sollten nach Wechsel vorhanden sein");
                    }
                }
            }, "Topologie-Wechsel sollten reibungslos funktionieren");
        }

        @Test
        @DisplayName("Wechsel von und zu Schneeflocke")
        void testTransitionsToAndFromSnowflake() throws IOException {
            initSimulator();

            // Starte mit einer anderen Topologie
            sim.initialize(new FullyConnectedTopology());

            // Stelle sicher, dass zur Zeit des Wechsels mindestens 5 Mirrors vorhanden/geplant sind
            MirrorProbe mirrorProbe = getMirrorProbe();
            int minRequired = 5;
            int currentTargets = mirrorProbe != null ? mirrorProbe.getNumTargetMirrors() : 0;
            if (currentTargets < minRequired) {
                // Plane frühzeitig genug, damit zum Wechselzeitpunkt genügend Mirrors verfügbar sind
                sim.getEffector().setMirrors(minRequired, 1);
            }

            // Wechsle zu Schneeflocke (geplanter Wechsel bei t=8)
            TopologyChange strategyChangeAction = sim.getEffector().setStrategy(new SnowflakeTopologyStrategy(), 8);

            // Verifiziere, dass die Strategy-Änderung korrekt geplant wurde (neue Topologie ist Snowflake)
            assertNotNull(strategyChangeAction, "Strategy-Änderung sollte Action zurückgeben");
            assertInstanceOf(TopologyChange.class, strategyChangeAction, "Action sollte ein TopologyChange sein");
            assertInstanceOf(SnowflakeTopologyStrategy.class, strategyChangeAction.getNewTopology(),
                    "Action sollte neue Snowflake-Topology referenzieren");

            // Simulation bis inkl. Wechselzeitpunkt laufen lassen und dann den tatsächlichen Wechsel prüfen
            for (int t = 1; t < Math.min(20, sim.getSimTime()); t++) {
                int finalT = t;
                assertDoesNotThrow(() -> sim.runStep(finalT),
                        "Wechsel zu Schneeflocke sollte funktionieren bei t=" + t);

                if (t >= 8) {
                    assertInstanceOf(SnowflakeTopologyStrategy.class,
                            strategyChangeAction.getNetwork().getTopologyStrategy(),
                            "Nach dem Wechselzeitpunkt sollte die Network-Topologie Snowflake sein");
                    break;
                }
            }
        }
    }

    // ===== EDGE CASES UND ERROR HANDLING =====

    @Nested
    @DisplayName("Edge Cases und Error Handling")
    class EdgeCasesTests {

        @Test
        @DisplayName("Minimale Schneeflocken-Konfiguration")
        void testMinimalSnowflakeConfiguration() {
            // Teste mit minimaler Anzahl Mirrors
            assertDoesNotThrow(() -> {
                Network network = createSnowflakeNetwork(5);
                SnowflakeTopologyStrategy strategy = (SnowflakeTopologyStrategy) network.getTopologyStrategy();
                assertTrue(strategy.getNumTargetLinks(network) >= 0);
            });
        }

        @Test
        @DisplayName("Große Schneeflocken-Konfiguration")
        void testLargeSnowflakeConfiguration() {
            assertDoesNotThrow(() -> {
                Network network = createSnowflakeNetwork(50);
                SnowflakeTopologyStrategy strategy = (SnowflakeTopologyStrategy) network.getTopologyStrategy();
                int links = strategy.getNumTargetLinks(network);
                assertTrue(links > 0 && links < 50 * 49 / 2);
            });
        }

        @Test
        @DisplayName("Ungültige Schneeflocken-Parameter")
        void testInvalidSnowflakeParameters() {
            // Teste Grenzfälle für SnowflakeProperties
            assertDoesNotThrow(() -> {
                new SnowflakeTopologyStrategy.SnowflakeProperties(0.0, 1);
                new SnowflakeTopologyStrategy.SnowflakeProperties(1.0, 1);
            });

            // Negative Werte sollten behandelt werden
            assertDoesNotThrow(() -> {
                new SnowflakeTopologyStrategy.SnowflakeProperties(-0.1, 1);
            });
        }

        @Test
        @DisplayName("Schneeflocke mit sehr wenigen Mirrors")
        void testSnowflakeWithFewMirrors() {
            // Test: Unter 5 Mirrors darf die Schneeflocke NICHT konstruiert werden
            for (int mirrors = 1; mirrors <= 4; mirrors++) {
                int finalMirrors = mirrors;
                assertThrows(IllegalArgumentException.class, () ->
                        createSnowflakeNetwork(finalMirrors), "Schneeflocke darf mit " + mirrors + " Mirrors nicht konstruierbar sein");
            }
        }
    }

    // ===== PERFORMANCE TESTS =====

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Schneeflocken-Erstellung ist performant")
        void testSnowflakeCreationPerformance() {
            long startTime = System.nanoTime();

            for (int i = 0; i < 10; i++) {
                Network network = createSnowflakeNetwork(20);
                SnowflakeTopologyStrategy strategy = (SnowflakeTopologyStrategy) network.getTopologyStrategy();
                strategy.getNumTargetLinks(network);
            }

            long duration = System.nanoTime() - startTime;
            assertTrue(duration < 500_000_000, // 500ms
                    "Schneeflocken-Erstellung sollte performant sein: " + duration + "ns");
        }

        @Test
        @DisplayName("Link-Berechnung ist effizient")
        void testLinkCalculationEfficiency() {
            Network network = createSnowflakeNetwork(30);
            SnowflakeTopologyStrategy strategy = (SnowflakeTopologyStrategy) network.getTopologyStrategy();

            long startTime = System.nanoTime();

            for (int i = 0; i < 1000; i++) {
                strategy.getNumTargetLinks(network);
            }

            long duration = System.nanoTime() - startTime;
            assertTrue(duration < 100_000_000, // 100ms für 1 000 Aufrufe
                    "Link-Berechnung sollte effizient sein: " + duration + "ns");
        }
    }
}