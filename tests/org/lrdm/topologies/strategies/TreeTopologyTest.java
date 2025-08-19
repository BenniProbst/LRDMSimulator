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

@DisplayName("TreeTopologyStrategy Tests")
class TreeTopologyTest {
    private TimedRDMSim sim;
    private static final String config = "resources/sim-test-1.conf";

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

    private Network createTreeNetwork(int mirrors) {
        TreeTopologyStrategy strategy = new TreeTopologyStrategy();
        return new Network(strategy, mirrors, 2, 30, getProps());
    }

    private Network createBalancedTreeNetwork(int mirrors) {
        BalancedTreeTopologyStrategy strategy = new BalancedTreeTopologyStrategy();
        return new Network(strategy, mirrors, 2, 30, getProps());
    }

    private Network createBalancedTreeNetwork(int mirrors, double maxDeviation) {
        BalancedTreeTopologyStrategy strategy = new BalancedTreeTopologyStrategy(maxDeviation);
        return new Network(strategy, mirrors, 2, 30, getProps());
    }

    private Network createDepthLimitTreeNetwork(int mirrors) {
        DepthLimitTreeTopologyStrategy strategy = new DepthLimitTreeTopologyStrategy();
        return new Network(strategy, mirrors, 2, 30, getProps());
    }

    private Network createDepthLimitTreeNetwork(int mirrors, int maxDepth) {
        DepthLimitTreeTopologyStrategy strategy = new DepthLimitTreeTopologyStrategy(maxDepth);
        return new Network(strategy, mirrors, 2, 30, getProps());
    }

    private Network createDepthLimitTreeNetwork(int mirrors, int maxDepth, boolean optimization) {
        DepthLimitTreeTopologyStrategy strategy = new DepthLimitTreeTopologyStrategy(maxDepth, optimization);
        return new Network(strategy, mirrors, 2, 30, getProps());
    }

    private Network createDepthLimitTreeNetwork(int mirrors, int maxDepth, boolean optimization, DepthInsertionStrategy insertionStrategy) {
        DepthLimitTreeTopologyStrategy strategy = new DepthLimitTreeTopologyStrategy(maxDepth, optimization, insertionStrategy);
        return new Network(strategy, mirrors, 2, 30, getProps());
    }

    private MirrorProbe getMirrorProbe() {
        return sim.getProbes().stream()
                .filter(MirrorProbe.class::isInstance)
                .map(p -> (MirrorProbe) p)
                .findFirst()
                .orElse(null);
    }

    private LinkProbe getLinkProbe() {
        return sim.getProbes().stream()
                .filter(LinkProbe.class::isInstance)
                .map(p -> (LinkProbe) p)
                .findFirst()
                .orElse(null);
    }

    // ===== BASIC TREE TOPOLOGY STRATEGY TESTS =====

    @Nested
    @DisplayName("Grundlegende TreeTopologyStrategy Funktionalität")
    class BasicTreeTopologyTests {

        @Test
        @DisplayName("TreeTopologyStrategy kann instanziiert werden")
        void testTreeInstantiation() {
            assertDoesNotThrow(TreeTopologyStrategy::new);
        }

        @Test
        @DisplayName("TreeTopologyStrategy toString() funktioniert")
        void testToString() {
            TreeTopologyStrategy strategy = new TreeTopologyStrategy();
            String result = strategy.toString();

            assertNotNull(result);
            assertTrue(result.contains("TreeTopologyStrategy"));
        }

        @Test
        @DisplayName("Simulator erfordert Initialisierung")
        void testInitializeRequired() throws IOException {
            initSimulator();
            assertThrows(RuntimeException.class, () -> sim.run());
        }

        @ParameterizedTest
        @ValueSource(ints = {3, 4, 5, 10, 15, 20})
        @DisplayName("Baum-Struktur wird korrekt erstellt")
        void testTreeStructureCreation(int totalMirrors) {
            Network network = createTreeNetwork(totalMirrors);
            TreeTopologyStrategy strategy = (TreeTopologyStrategy) network.getTopologyStrategy();

            // Grundlegende Struktur-Validierung
            assertTrue(strategy.validateTopology(), "Baum-Topologie sollte gültig sein");

            // Mirror-Anzahl sollte korrekt sein
            assertEquals(totalMirrors, network.getNumMirrors(), "Mirror-Anzahl sollte stimmen");

            // Links sollten vorhanden sein (n-1 für Baum)
            assertEquals(Math.max(0, totalMirrors - 1), network.getNumLinks(),
                    "Baum sollte n-1 Links haben");
        }

        @Test
        @DisplayName("Baum hat korrekte Baum-Eigenschaften")
        void testTreeProperties() {
            Network network = createTreeNetwork(10);
            TreeTopologyStrategy strategy = (TreeTopologyStrategy) network.getTopologyStrategy();

            assertTrue(strategy.validateTopology(), "Baum sollte gültig sein");

            // Baum-spezifische Eigenschaften testen
            int expectedLinks = Math.max(0, network.getNumMirrors() - 1);
            assertEquals(expectedLinks, strategy.getNumTargetLinks(network),
                    "Baum sollte n-1 Target-Links haben");
        }

        @Test
        @DisplayName("Einzelknoten-Baum")
        void testSingleNodeTree() {
            Network network = createTreeNetwork(1);
            TreeTopologyStrategy strategy = (TreeTopologyStrategy) network.getTopologyStrategy();

            assertTrue(strategy.validateTopology(), "Einzelknoten-Baum sollte gültig sein");
            assertEquals(0, strategy.getNumTargetLinks(network), "Einzelknoten sollte keine Links haben");
        }

        @Test
        @DisplayName("Leerer Baum")
        void testEmptyTree() {
            Network network = createTreeNetwork(0);
            TreeTopologyStrategy strategy = (TreeTopologyStrategy) network.getTopologyStrategy();

            assertEquals(0, strategy.getNumTargetLinks(network), "Leerer Baum sollte keine Links haben");
        }
    }

    // ===== BALANCED TREE TOPOLOGY STRATEGY TESTS =====

    @Nested
    @DisplayName("BalancedTreeTopologyStrategy Funktionalität")
    class BalancedTreeTopologyTests {

        @Test
        @DisplayName("BalancedTreeTopologyStrategy kann instanziiert werden")
        void testBalancedTreeInstantiation() {
            assertDoesNotThrow(() -> new BalancedTreeTopologyStrategy());
            assertDoesNotThrow(() -> new BalancedTreeTopologyStrategy(0.5));
            assertDoesNotThrow(() -> new BalancedTreeTopologyStrategy(2.0));
        }

        /*
        @Test
        @DisplayName("BalancedTreeTopologyStrategy toString() funktioniert")
        void testBalancedTreeToString() {
            BalancedTreeTopologyStrategy strategy = new BalancedTreeTopologyStrategy(1.5);
            String result = strategy.toString();

            assertNotNull(result);
            assertTrue(result.contains("BalancedTreeTopologyStrategy"));
            assertTrue(result.contains("1.50"));
        }

         */


        @ParameterizedTest
        @ValueSource(ints = {3, 5, 10, 15, 20, 25})
        @DisplayName("Balancierte Baum-Struktur wird korrekt erstellt")
        void testBalancedTreeStructureCreation(int totalMirrors) {
            Network network = createBalancedTreeNetwork(totalMirrors);
            BalancedTreeTopologyStrategy strategy = (BalancedTreeTopologyStrategy) network.getTopologyStrategy();

            // Grundlegende Struktur-Validierung
            assertTrue(strategy.validateTopology(), "Balancierter Baum sollte gültig sein");

            // Mirror-Anzahl sollte korrekt sein
            assertEquals(totalMirrors, network.getNumMirrors(), "Mirror-Anzahl sollte stimmen");

            // Links sollten vorhanden sein (n-1 für Baum)
            assertEquals(Math.max(0, totalMirrors - 1), network.getNumLinks(),
                    "Balancierter Baum sollte n-1 Links haben");
        }

        @ParameterizedTest
        @CsvSource({
                "10, 0.5",
                "15, 1.0",
                "20, 1.5",
                "25, 2.0"
        })
        @DisplayName("Verschiedene Balance-Abweichungen")
        void testVariousBalanceDeviations(int mirrors, double maxDeviation) {
            assertDoesNotThrow(() -> {
                Network network = createBalancedTreeNetwork(mirrors, maxDeviation);
                BalancedTreeTopologyStrategy strategy = (BalancedTreeTopologyStrategy) network.getTopologyStrategy();

                assertEquals(maxDeviation, strategy.getMaxAllowedBalanceDeviation(), 0.01,
                        "Balance-Abweichung sollte korrekt gesetzt sein");
                assertTrue(strategy.validateTopology(), "Baum mit Balance-Abweichung sollte gültig sein");
            });
        }

        @Test
        @DisplayName("Balance-Parameter können zur Laufzeit geändert werden")
        void testBalanceParameterChanges() {
            BalancedTreeTopologyStrategy strategy = new BalancedTreeTopologyStrategy(1.0);

            assertEquals(1.0, strategy.getMaxAllowedBalanceDeviation(), 0.01);

            strategy.setMaxAllowedBalanceDeviation(2.5);
            assertEquals(2.5, strategy.getMaxAllowedBalanceDeviation(), 0.01);

            // Minimum sollte eingehalten werden
            strategy.setMaxAllowedBalanceDeviation(0.05);
            assertEquals(0.1, strategy.getMaxAllowedBalanceDeviation(), 0.01);
        }

        @Test
        @DisplayName("Negative Balance-Abweichung wird korrigiert")
        void testNegativeBalanceDeviationCorrection() {
            BalancedTreeTopologyStrategy strategy = new BalancedTreeTopologyStrategy(-0.5);
            assertTrue(strategy.getMaxAllowedBalanceDeviation() >= 0.1,
                    "Negative Balance-Abweichung sollte auf Minimum korrigiert werden");
        }
    }

    // ===== DEPTH LIMIT TREE TOPOLOGY STRATEGY TESTS =====

    @Nested
    @DisplayName("DepthLimitTreeTopologyStrategy Funktionalität")
    class DepthLimitTreeTopologyTests {

        @Test
        @DisplayName("DepthLimitTreeTopologyStrategy kann instanziiert werden")
        void testDepthLimitTreeInstantiation() {
            assertDoesNotThrow(() -> new DepthLimitTreeTopologyStrategy());
            assertDoesNotThrow(() -> new DepthLimitTreeTopologyStrategy(2));
            assertDoesNotThrow(() -> new DepthLimitTreeTopologyStrategy(5, true));
            assertDoesNotThrow(() -> new DepthLimitTreeTopologyStrategy(3, false, DepthInsertionStrategy.BREADTH_FIRST));
        }

        @Test
        @DisplayName("DepthLimitTreeTopologyStrategy toString() funktioniert")
        void testDepthLimitTreeToString() {
            DepthLimitTreeTopologyStrategy strategy = new DepthLimitTreeTopologyStrategy(4, true, DepthInsertionStrategy.BALANCED);
            String result = strategy.toString();

            assertNotNull(result);
            assertTrue(result.contains("DepthLimitTreeTopologyStrategy"));
            assertTrue(result.contains("maxDepth=4"));
            assertTrue(result.contains("BALANCED"));
            assertTrue(result.contains("optimization=true"));
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2, 3, 4, 5})
        @DisplayName("Verschiedene Tiefenlimits")
        void testVariousDepthLimits(int maxDepth) {
            assertDoesNotThrow(() -> {
                Network network = createDepthLimitTreeNetwork(10, maxDepth);
                DepthLimitTreeTopologyStrategy strategy = (DepthLimitTreeTopologyStrategy) network.getTopologyStrategy();

                assertEquals(maxDepth, strategy.getMaxDepth(), "Tiefenlimit sollte korrekt gesetzt sein");
                assertTrue(strategy.validateTopology(), "Tiefen limitierter Baum sollte gültig sein");
            });
        }

        @ParameterizedTest
        @CsvSource({
                "10, 2, true",
                "15, 3, false",
                "20, 4, true",
                "25, 2, false"
        })
        @DisplayName("Verschiedene Optimierungseinstellungen")
        void testVariousOptimizationSettings(int mirrors, int maxDepth, boolean optimization) {
            assertDoesNotThrow(() -> {
                Network network = createDepthLimitTreeNetwork(mirrors, maxDepth, optimization);
                DepthLimitTreeTopologyStrategy strategy = (DepthLimitTreeTopologyStrategy) network.getTopologyStrategy();

                assertEquals(maxDepth, strategy.getMaxDepth(), "Tiefenlimit sollte korrekt gesetzt sein");
                assertEquals(optimization, strategy.isDepthOptimizationEnabled(), "Optimierungseinstellung sollte stimmen");
                assertTrue(strategy.validateTopology(), "Tiefen limitierter Baum sollte gültig sein");
            });
        }

        @Test
        @DisplayName("Alle DepthInsertionStrategy-Werte funktionieren")
        void testAllInsertionStrategies() {
            for (DepthInsertionStrategy strategy : DepthInsertionStrategy.values()) {
                assertDoesNotThrow(() -> {
                    Network network = createDepthLimitTreeNetwork(15, 3, true, strategy);
                    DepthLimitTreeTopologyStrategy treeStrategy = (DepthLimitTreeTopologyStrategy) network.getTopologyStrategy();

                    assertEquals(strategy, treeStrategy.getInsertionStrategy(), "Insertion-Strategie sollte korrekt gesetzt sein");
                    assertTrue(treeStrategy.validateTopology(), "Baum mit " + strategy + " sollte gültig sein");
                }, "DepthInsertionStrategy " + strategy + " sollte funktionieren");
            }
        }

        @Test
        @DisplayName("Tiefenlimit-Parameter können zur Laufzeit geändert werden")
        void testDepthLimitParameterChanges() {
            DepthLimitTreeTopologyStrategy strategy = new DepthLimitTreeTopologyStrategy(3, true, DepthInsertionStrategy.DEPTH_FIRST);

            assertEquals(3, strategy.getMaxDepth());
            assertTrue(strategy.isDepthOptimizationEnabled());
            assertEquals(DepthInsertionStrategy.DEPTH_FIRST, strategy.getInsertionStrategy());

            strategy.setMaxDepth(5);
            strategy.setDepthOptimizationEnabled(false);
            strategy.setInsertionStrategy(DepthInsertionStrategy.BREADTH_FIRST);

            assertEquals(5, strategy.getMaxDepth());
            assertFalse(strategy.isDepthOptimizationEnabled());
            assertEquals(DepthInsertionStrategy.BREADTH_FIRST, strategy.getInsertionStrategy());
        }

        @Test
        @DisplayName("Minimale Tiefe wird eingehalten")
        void testMinimumDepthEnforcement() {
            DepthLimitTreeTopologyStrategy strategy = new DepthLimitTreeTopologyStrategy(-5);
            assertTrue(strategy.getMaxDepth() >= 1, "Tiefe sollte mindestens 1 sein");

            strategy.setMaxDepth(0);
            assertTrue(strategy.getMaxDepth() >= 1, "Tiefe sollte mindestens 1 sein");
        }

        @Test
        @DisplayName("Null InsertionStrategy wird auf Default gesetzt")
        void testNullInsertionStrategyHandling() {
            DepthLimitTreeTopologyStrategy strategy = new DepthLimitTreeTopologyStrategy(3, true, null);
            assertEquals(DepthInsertionStrategy.DEPTH_FIRST, strategy.getInsertionStrategy(),
                    "Null InsertionStrategy sollte auf DEPTH_FIRST Default gesetzt werden");

            strategy.setInsertionStrategy(null);
            assertEquals(DepthInsertionStrategy.DEPTH_FIRST, strategy.getInsertionStrategy(),
                    "Null InsertionStrategy sollte auf DEPTH_FIRST Default gesetzt werden");
        }
    }

    // ===== LINK-BERECHNUNG TESTS =====

    @Nested
    @DisplayName("Link-Berechnung Tests")
    class LinkCalculationTests {

        @Test
        @DisplayName("TreeTopologyStrategy Link-Berechnung")
        void testTreeLinkCalculation() {
            for (int mirrors = 0; mirrors <= 20; mirrors += 3) {
                Network network = createTreeNetwork(mirrors);
                TreeTopologyStrategy strategy = (TreeTopologyStrategy) network.getTopologyStrategy();

                int targetLinks = strategy.getNumTargetLinks(network);
                int expectedLinks = Math.max(0, mirrors - 1);

                assertEquals(expectedLinks, targetLinks,
                        "Tree sollte n-1 Links haben für " + mirrors + " Mirrors");
                assertTrue(targetLinks >= 0, "Target links sollte nicht negativ sein");
            }
        }

        @Test
        @DisplayName("BalancedTreeTopologyStrategy Link-Berechnung")
        void testBalancedTreeLinkCalculation() {
            for (int mirrors = 0; mirrors <= 20; mirrors += 3) {
                Network network = createBalancedTreeNetwork(mirrors);
                BalancedTreeTopologyStrategy strategy = (BalancedTreeTopologyStrategy) network.getTopologyStrategy();

                int targetLinks = strategy.getNumTargetLinks(network);
                int expectedLinks = Math.max(0, mirrors - 1);

                assertEquals(expectedLinks, targetLinks,
                        "Balanced Tree sollte n-1 Links haben für " + mirrors + " Mirrors");
                assertTrue(targetLinks >= 0, "Target links sollte nicht negativ sein");
            }
        }

        @Test
        @DisplayName("DepthLimitTreeTopologyStrategy Link-Berechnung")
        void testDepthLimitTreeLinkCalculation() {
            for (int mirrors = 0; mirrors <= 20; mirrors += 3) {
                Network network = createDepthLimitTreeNetwork(mirrors);
                DepthLimitTreeTopologyStrategy strategy = (DepthLimitTreeTopologyStrategy) network.getTopologyStrategy();

                int targetLinks = strategy.getNumTargetLinks(network);
                int expectedLinks = Math.max(0, mirrors - 1);

                assertEquals(expectedLinks, targetLinks,
                        "Depth Limit Tree sollte n-1 Links haben für " + mirrors + " Mirrors");
                assertTrue(targetLinks >= 0, "Target links sollte nicht negativ sein");
            }
        }

        @Test
        @DisplayName("Link-Berechnung skaliert linear mit Mirror-Anzahl")
        void testLinkScalingWithMirrors() {
            Network small = createTreeNetwork(5);
            Network medium = createTreeNetwork(10);
            Network large = createTreeNetwork(20);

            TreeTopologyStrategy smallStrategy = (TreeTopologyStrategy) small.getTopologyStrategy();
            TreeTopologyStrategy mediumStrategy = (TreeTopologyStrategy) medium.getTopologyStrategy();
            TreeTopologyStrategy largeStrategy = (TreeTopologyStrategy) large.getTopologyStrategy();

            int smallLinks = smallStrategy.getNumTargetLinks(small);
            int mediumLinks = mediumStrategy.getNumTargetLinks(medium);
            int largeLinks = largeStrategy.getNumTargetLinks(large);

            assertTrue(mediumLinks > smallLinks, "Mittlerer Baum sollte mehr Links haben");
            assertTrue(largeLinks > mediumLinks, "Größerer Baum sollte mehr Links haben");

            // Lineare Beziehung prüfen
            assertEquals(4, smallLinks, "Kleiner Baum (5 Mirrors) sollte 4 Links haben");
            assertEquals(9, mediumLinks, "Mittlerer Baum (10 Mirrors) sollte 9 Links haben");
            assertEquals(19, largeLinks, "Großer Baum (20 Mirrors) sollte 19 Links haben");
        }
    }

    // ===== SIMULATION TESTS =====

    @Nested
    @DisplayName("Simulation Tests")
    class SimulationTests {

        @Test
        @DisplayName("TreeTopologyStrategy Mirror-Änderungen")
        void testTreeMirrorChanges() throws IOException {
            initSimulator();
            sim.initialize(new TreeTopologyStrategy());

            int targetMirrors = 15;
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
        @DisplayName("BalancedTreeTopologyStrategy Mirror-Änderungen")
        void testBalancedTreeMirrorChanges() throws IOException {
            initSimulator();
            sim.initialize(new BalancedTreeTopologyStrategy());

            int targetMirrors = 20;
            int changeAt = 8;
            sim.getEffector().setMirrors(targetMirrors, changeAt);

            MirrorProbe mirrorProbe = getMirrorProbe();
            assertNotNull(mirrorProbe, "MirrorProbe muss verfügbar sein");

            // Laufe der Simulation bis zum Änderungszeitpunkt
            for (int t = 1; t <= changeAt + 5 && t < sim.getSimTime(); t++) {
                sim.runStep(t);

                // Nach Änderungszeitpunkt
                if (t > changeAt) {
                    assertEquals(targetMirrors, mirrorProbe.getNumTargetMirrors(),
                            "Target mirrors sollten nach Änderung gesetzt sein");
                }
            }
        }

        @Test
        @DisplayName("DepthLimitTreeTopologyStrategy Mirror-Änderungen")
        void testDepthLimitTreeMirrorChanges() throws IOException {
            initSimulator();
            sim.initialize(new DepthLimitTreeTopologyStrategy(3));

            int targetMirrors = 12;
            int changeAt = 6;
            sim.getEffector().setMirrors(targetMirrors, changeAt);

            MirrorProbe mirrorProbe = getMirrorProbe();
            assertNotNull(mirrorProbe, "MirrorProbe muss verfügbar sein");

            // Laufe der Simulation bis zum Änderungszeitpunkt
            for (int t = 1; t <= changeAt + 5 && t < sim.getSimTime(); t++) {
                sim.runStep(t);

                // Nach Änderungszeitpunkt
                if (t > changeAt) {
                    assertEquals(targetMirrors, mirrorProbe.getNumTargetMirrors(),
                            "Target mirrors sollten nach Änderung gesetzt sein");
                }
            }
        }

        @Test
        @DisplayName("Tree Effect-Deltas sind realistisch")
        void testTreeEffectDeltas() throws IOException {
            initSimulator();
            sim.initialize(new TreeTopologyStrategy());
            MirrorProbe mirrorProbe = getMirrorProbe();

            for (int t = 1; t < Math.min(10, sim.getSimTime()); t++) {
                sim.runStep(t);

                Action a = sim.getEffector().setMirrors(mirrorProbe.getNumMirrors() + 1, t + 1);
                if (a == null || a.getEffect() == null) {
                    continue;
                }

                int ttw = a.getEffect().getDeltaTimeToWrite();
                int bw = a.getEffect().getDeltaBandwidth(sim.getProps());
                double al = a.getEffect().getDeltaActiveLinks();

                // Baum-spezifische, realistische Grenzen
                assertTrue(ttw >= -200 && ttw <= 200,
                        "TTW Delta sollte für Baum moderat sein: " + ttw);
                assertTrue(bw >= -100 && bw <= 100,
                        "Bandwidth Delta sollte für Baum moderat sein: " + bw);
                assertTrue(al >= -20 && al <= 20,
                        "Active Links Delta sollte für Baum moderat sein: " + al);

                assertDoesNotThrow(() -> a.getEffect().getLatency(),
                        "Latency-Berechnung sollte für Baum funktionieren");
            }
        }

        @Test
        @DisplayName("Mirror-Reduktion funktioniert für Bäume")
        void testTreeMirrorReduction() throws IOException {
            initSimulator();
            sim.initialize(new TreeTopologyStrategy());

            int initialMirrors = getMirrorProbe().getNumMirrors();
            int targetMirrors = Math.max(1, initialMirrors - 3); // Mindestens 1 für Baum
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
        @DisplayName("Target Link Änderungen für Bäume")
        void testTreeTargetLinkChanges() throws IOException {
            initSimulator();
            sim.initialize(new TreeTopologyStrategy());

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
        @DisplayName("Wechsel zwischen verschiedenen Baum-Topologien funktioniert")
        void testTreeTopologyTransitions() throws IOException {
            initSimulator();
            sim.initialize(new TreeTopologyStrategy());

            // Sequenz von Baum-Topologie-Wechseln
            sim.getEffector().setStrategy(new BalancedTreeTopologyStrategy(), 10);
            sim.getEffector().setStrategy(new DepthLimitTreeTopologyStrategy(), 20);
            sim.getEffector().setStrategy(new TreeTopologyStrategy(), 30);
            sim.getEffector().setStrategy(new BalancedTreeTopologyStrategy(0.5), 40);

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
            }, "Baum-Topologie-Wechsel sollten reibungslos funktionieren");
        }

        @Test
        @DisplayName("Wechsel von und zu TreeTopologyStrategy")
        void testTransitionsToAndFromTree() throws IOException {
            initSimulator();

            // Starte mit einer anderen Topologie
            sim.initialize(new FullyConnectedTopology());

            // Stelle sicher, dass zur Zeit des Wechsels mindestens 1 Mirror vorhanden ist
            MirrorProbe mirrorProbe = getMirrorProbe();
            int minRequired = 3;
            int currentTargets = mirrorProbe != null ? mirrorProbe.getNumTargetMirrors() : 0;
            if (currentTargets < minRequired) {
                sim.getEffector().setMirrors(minRequired, 1);
            }

            // Wechsle zu Baum
            TopologyChange strategyChangeAction = sim.getEffector().setStrategy(new TreeTopologyStrategy(), 8);

            // Verifiziere, dass die Strategy-Änderung korrekt geplant wurde
            assertNotNull(strategyChangeAction, "Strategy-Änderung sollte Action zurückgeben");
            assertInstanceOf(TopologyChange.class, strategyChangeAction, "Action sollte ein TopologyChange sein");
            assertInstanceOf(TreeTopologyStrategy.class, strategyChangeAction.getNewTopology(),
                    "Action sollte neue Tree-Topology referenzieren");

            // Simulation bis inkl. Wechselzeitpunkt laufen lassen
            for (int t = 1; t < Math.min(20, sim.getSimTime()); t++) {
                int finalT = t;
                assertDoesNotThrow(() -> sim.runStep(finalT),
                        "Wechsel zu Baum sollte funktionieren bei t=" + t);

                if (t >= 8) {
                    assertInstanceOf(TreeTopologyStrategy.class,
                            strategyChangeAction.getNetwork().getTopologyStrategy(),
                            "Nach dem Wechselzeitpunkt sollte die Network-Topologie Tree sein");
                    break;
                }
            }
        }

        @Test
        @DisplayName("Wechsel zwischen Baum-Varianten")
        void testTransitionsBetweenTreeVariants() throws IOException {
            initSimulator();
            sim.initialize(new TreeTopologyStrategy());

            // Stelle genügend Mirrors sicher
            sim.getEffector().setMirrors(10, 2);

            // Wechsel zu BalancedTree
            TopologyChange toBalanced = sim.getEffector().setStrategy(new BalancedTreeTopologyStrategy(), 5);
            assertNotNull(toBalanced);

            // Wechsel zu DepthLimitTree
            TopologyChange toDepthLimit = sim.getEffector().setStrategy(new DepthLimitTreeTopologyStrategy(2), 10);
            assertNotNull(toDepthLimit);

            // Zurück zu BasicTree
            TopologyChange toBasic = sim.getEffector().setStrategy(new TreeTopologyStrategy(), 15);
            assertNotNull(toBasic);

            // Simulation laufen lassen
            assertDoesNotThrow(() -> {
                for (int t = 1; t < Math.min(20, sim.getSimTime()); t++) {
                    sim.runStep(t);
                }
            }, "Wechsel zwischen Baum-Varianten sollten funktionieren");
        }
    }

    // ===== EDGE CASES UND ERROR HANDLING =====

    @Nested
    @DisplayName("Edge Cases und Error Handling")
    class EdgeCasesTests {

        @Test
        @DisplayName("Minimale Baum-Konfigurationen")
        void testMinimalTreeConfigurations() {
            // TreeTopologyStrategy
            assertDoesNotThrow(() -> {
                Network network = createTreeNetwork(1);
                TreeTopologyStrategy strategy = (TreeTopologyStrategy) network.getTopologyStrategy();
                assertEquals(0, strategy.getNumTargetLinks(network));
                assertTrue(strategy.validateTopology());
            });

            // BalancedTreeTopologyStrategy
            assertDoesNotThrow(() -> {
                Network network = createBalancedTreeNetwork(1);
                BalancedTreeTopologyStrategy strategy = (BalancedTreeTopologyStrategy) network.getTopologyStrategy();
                assertEquals(0, strategy.getNumTargetLinks(network));
                assertTrue(strategy.validateTopology());
            });

            // DepthLimitTreeTopologyStrategy
            assertDoesNotThrow(() -> {
                Network network = createDepthLimitTreeNetwork(1);
                DepthLimitTreeTopologyStrategy strategy = (DepthLimitTreeTopologyStrategy) network.getTopologyStrategy();
                assertEquals(0, strategy.getNumTargetLinks(network));
                assertTrue(strategy.validateTopology());
            });
        }

        @Test
        @DisplayName("Große Baum-Konfigurationen")
        void testLargeTreeConfigurations() {
            assertDoesNotThrow(() -> {
                Network network = createTreeNetwork(100);
                TreeTopologyStrategy strategy = (TreeTopologyStrategy) network.getTopologyStrategy();
                int links = strategy.getNumTargetLinks(network);
                assertEquals(99, links, "Baum mit 100 Knoten sollte 99 Links haben");
                assertTrue(strategy.validateTopology());
            });

            assertDoesNotThrow(() -> {
                Network network = createBalancedTreeNetwork(50);
                BalancedTreeTopologyStrategy strategy = (BalancedTreeTopologyStrategy) network.getTopologyStrategy();
                int links = strategy.getNumTargetLinks(network);
                assertEquals(49, links, "Balancierter Baum mit 50 Knoten sollte 49 Links haben");
                assertTrue(strategy.validateTopology());
            });

            assertDoesNotThrow(() -> {
                Network network = createDepthLimitTreeNetwork(75, 5);
                DepthLimitTreeTopologyStrategy strategy = (DepthLimitTreeTopologyStrategy) network.getTopologyStrategy();
                int links = strategy.getNumTargetLinks(network);
                assertEquals(74, links, "Tiefen limitierter Baum mit 75 Knoten sollte 74 Links haben");
                assertTrue(strategy.validateTopology());
            });
        }

        @Test
        @DisplayName("Null- und leere Konfigurationen")
        void testNullAndEmptyConfigurations() {
            // Leere Bäume
            assertDoesNotThrow(() -> {
                Network network = createTreeNetwork(0);
                TreeTopologyStrategy strategy = (TreeTopologyStrategy) network.getTopologyStrategy();
                assertEquals(0, strategy.getNumTargetLinks(network));
            });

            assertDoesNotThrow(() -> {
                Network network = createBalancedTreeNetwork(0);
                BalancedTreeTopologyStrategy strategy = (BalancedTreeTopologyStrategy) network.getTopologyStrategy();
                assertEquals(0, strategy.getNumTargetLinks(network));
            });

            assertDoesNotThrow(() -> {
                Network network = createDepthLimitTreeNetwork(0);
                DepthLimitTreeTopologyStrategy strategy = (DepthLimitTreeTopologyStrategy) network.getTopologyStrategy();
                assertEquals(0, strategy.getNumTargetLinks(network));
            });
        }
    }

    // ===== PERFORMANCE TESTS =====

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        /*
        @Test
        @DisplayName("Baum-Erstellung ist performant")
        void testTreeCreationPerformance() {
            long startTime = System.nanoTime();

            for (int i = 0; i < 50; i++) {
                Network network = createTreeNetwork(20);
                TreeTopologyStrategy strategy = (TreeTopologyStrategy) network.getTopologyStrategy();
                strategy.getNumTargetLinks(network);
            }

            long duration = System.nanoTime() - startTime;
            assertTrue(duration < 1_000_000_000, // 1s
                    "Baum-Erstellung sollte performant sein: " + duration + "ns");
        }

         */

        @Test
        @DisplayName("Balancierte Baum-Erstellung ist performant")
        void testBalancedTreeCreationPerformance() {
            long startTime = System.nanoTime();

            for (int i = 0; i < 30; i++) {
                Network network = createBalancedTreeNetwork(25);
                BalancedTreeTopologyStrategy strategy = (BalancedTreeTopologyStrategy) network.getTopologyStrategy();
                strategy.getNumTargetLinks(network);
            }

            long duration = System.nanoTime() - startTime;
            assertTrue(duration < 2_000_000_000L, // 2s
                    "Balancierte Baum-Erstellung sollte performant sein: " + duration + "ns");
        }

        @Test
        @DisplayName("Tiefenlimitierte Baum-Erstellung ist performant")
        void testDepthLimitTreeCreationPerformance() {
            long startTime = System.nanoTime();

            for (int i = 0; i < 30; i++) {
                Network network = createDepthLimitTreeNetwork(25, 4);
                DepthLimitTreeTopologyStrategy strategy = (DepthLimitTreeTopologyStrategy) network.getTopologyStrategy();
                strategy.getNumTargetLinks(network);
            }

            long duration = System.nanoTime() - startTime;
            assertTrue(duration < 2_000_000_000L, // 2s
                    "Tiefenlimitierte Baum-Erstellung sollte performant sein: " + duration + "ns");
        }

        @Test
        @DisplayName("Link-Berechnung ist effizient für alle Baum-Typen")
        void testLinkCalculationEfficiency() {
            Network treeNetwork = createTreeNetwork(50);
            Network balancedNetwork = createBalancedTreeNetwork(50);
            Network depthLimitNetwork = createDepthLimitTreeNetwork(50);

            TreeTopologyStrategy treeStrategy = (TreeTopologyStrategy) treeNetwork.getTopologyStrategy();
            BalancedTreeTopologyStrategy balancedStrategy = (BalancedTreeTopologyStrategy) balancedNetwork.getTopologyStrategy();
            DepthLimitTreeTopologyStrategy depthLimitStrategy = (DepthLimitTreeTopologyStrategy) depthLimitNetwork.getTopologyStrategy();

            long startTime = System.nanoTime();

            for (int i = 0; i < 5000; i++) {
                treeStrategy.getNumTargetLinks(treeNetwork);
                balancedStrategy.getNumTargetLinks(balancedNetwork);
                depthLimitStrategy.getNumTargetLinks(depthLimitNetwork);
            }

            long duration = System.nanoTime() - startTime;
            assertTrue(duration < 200_000_000, // 200ms für 5 000 · 3 Aufrufe
                    "Link-Berechnung sollte effizient sein: " + duration + "ns");
        }

        /*
        @Test
        @DisplayName("Große Bäume sind performant")
        void testLargeTreePerformance() {
            long startTime = System.nanoTime();

            Network treeNetwork = createTreeNetwork(200);
            Network balancedNetwork = createBalancedTreeNetwork(150);
            Network depthLimitNetwork = createDepthLimitTreeNetwork(150, 6);

            TreeTopologyStrategy treeStrategy = (TreeTopologyStrategy) treeNetwork.getTopologyStrategy();
            BalancedTreeTopologyStrategy balancedStrategy = (BalancedTreeTopologyStrategy) balancedNetwork.getTopologyStrategy();
            DepthLimitTreeTopologyStrategy depthLimitStrategy = (DepthLimitTreeTopologyStrategy) depthLimitNetwork.getTopologyStrategy();

            assertEquals(199, treeStrategy.getNumTargetLinks(treeNetwork));
            assertEquals(149, balancedStrategy.getNumTargetLinks(balancedNetwork));
            assertEquals(149, depthLimitStrategy.getNumTargetLinks(depthLimitNetwork));

            assertTrue(treeStrategy.validateTopology());
            assertTrue(balancedStrategy.validateTopology());
            assertTrue(depthLimitStrategy.validateTopology());

            long duration = System.nanoTime() - startTime;
            assertTrue(duration < 5_000_000_000L, // 5s für große Bäume
                    "Große Bäume sollten performant sein: " + duration + "ns");
        }

         */
    }

    // ===== BAUM-SPEZIFISCHE TESTS =====

    @Nested
    @DisplayName("Baum-spezifische Tests")
    class TreeSpecificTests {

        @Test
        @DisplayName("Baum-Eigenschaften sind korrekt")
        void testTreeProperties() {
            Network network = createTreeNetwork(15);
            TreeTopologyStrategy strategy = (TreeTopologyStrategy) network.getTopologyStrategy();

            assertTrue(strategy.validateTopology(), "Baum sollte gültig sein");

            // Baum sollte zusammenhängend sein
            assertTrue(network.getNumMirrors() > 0, "Baum sollte mindestens einen Knoten haben");

            // Anzahl Links sollte n-1 entsprechen (charakteristisch für Bäume)
            assertEquals(network.getNumMirrors() - 1, strategy.getNumTargetLinks(network),
                    "Baum sollte genau n-1 Links haben");
        }

        @Test
        @DisplayName("BalancedTree-spezifische Eigenschaften")
        void testBalancedTreeSpecificProperties() {
            Network network = createBalancedTreeNetwork(20);
            BalancedTreeTopologyStrategy strategy = (BalancedTreeTopologyStrategy) network.getTopologyStrategy();

            assertTrue(strategy.validateTopology(), "Balancierter Baum sollte gültig sein");

            // Balance-Abweichung sollte im erwarteten Bereich liegen
            assertTrue(strategy.getMaxAllowedBalanceDeviation() > 0,
                    "Balance-Abweichung sollte positiv sein");
        }

        @Test
        @DisplayName("DepthLimitTree-spezifische Eigenschaften")
        void testDepthLimitTreeSpecificProperties() {
            Network network = createDepthLimitTreeNetwork(25, 4);
            DepthLimitTreeTopologyStrategy strategy = (DepthLimitTreeTopologyStrategy) network.getTopologyStrategy();

            assertTrue(strategy.validateTopology(), "Tiefenlimitierter Baum sollte gültig sein");

            // Tiefenlimit sollte korrekt gesetzt sein
            assertEquals(4, strategy.getMaxDepth(), "Tiefenlimit sollte 4 sein");
            assertTrue(strategy.getMaxDepth() >= 1, "Tiefenlimit sollte mindestens 1 sein");
        }

        /*
        @Test
        @DisplayName("Baum-toString-Methoden sind informativ")
        void testTreeToStringMethods() {
            TreeTopologyStrategy tree = new TreeTopologyStrategy();
            BalancedTreeTopologyStrategy balanced = new BalancedTreeTopologyStrategy(1.5);
            DepthLimitTreeTopologyStrategy depthLimit = new DepthLimitTreeTopologyStrategy(3, true, DepthInsertionStrategy.BALANCED);

            String treeString = tree.toString();
            String balancedString = balanced.toString();
            String depthLimitString = depthLimit.toString();

            // Alle sollten identifizierende Informationen enthalten
            assertTrue(treeString.contains("TreeTopologyStrategy"), "Tree toString sollte Strategie-Namen enthalten");
            assertTrue(balancedString.contains("BalancedTreeTopologyStrategy"), "BalancedTree toString sollte Strategie-Namen enthalten");
            assertTrue(depthLimitString.contains("DepthLimitTreeTopologyStrategy"), "DepthLimitTree toString sollte Strategie-Namen enthalten");

            // Spezifische Parameter sollten sichtbar sein
            assertTrue(balancedString.contains("1.50"), "BalancedTree toString sollte Balance-Abweichung enthalten");
            assertTrue(depthLimitString.contains("maxDepth=3"), "DepthLimitTree toString sollte Tiefenlimit enthalten");
            assertTrue(depthLimitString.contains("BALANCED"), "DepthLimitTree toString sollte Insertion-Strategie enthalten");
        }

         */


        @Test
        @DisplayName("Baum-Strategien sind Thread-safe für Read-Operations")
        void testTreeStrategiesThreadSafety() {
            Network network = createTreeNetwork(30);
            TreeTopologyStrategy strategy = (TreeTopologyStrategy) network.getTopologyStrategy();

            // Simuliere gleichzeitige Zugriffe auf Read-Only-Operationen
            assertDoesNotThrow(() -> {
                for (int i = 0; i < 100; i++) {
                    final int iteration = i;
                    new Thread(() -> {
                        int links = strategy.getNumTargetLinks(network);
                        assertTrue(links >= 0, "Links sollten nicht negativ sein in Iteration " + iteration);
                        boolean valid = strategy.validateTopology();
                        // valid kann true oder false sein, sollte aber nicht crashen
                    }).start();
                }

                // Kurz warten, damit Threads abgeschlossen werden
                Thread.sleep(100);
            }, "Gleichzeitige Read-Zugriffe sollten thread-safe sein");
        }
    }
}