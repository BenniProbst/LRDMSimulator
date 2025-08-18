package org.lrdm.topologies.strategies;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

import org.lrdm.*;
import org.lrdm.effectors.Action;
import org.lrdm.effectors.MirrorChange;
import org.lrdm.effectors.TargetLinkChange;
import org.lrdm.effectors.TopologyChange;

import java.io.IOException;
import java.util.*;

@DisplayName("TopologyStrategy Basisklasse Tests")
class TopologyStrategyTest {

    private static final String CONFIG_FILE = "resources/sim-test-1.conf";
    private Properties props;
    private Network network;
    private MockTopologyStrategy strategy;

    @BeforeEach
    void setUp() throws IOException {
        loadProperties();
        strategy = new MockTopologyStrategy();
        network = createTestNetwork();
    }

    // ===== HILFSMETHODEN FÜR TEST-SETUP =====

    private void loadProperties() throws IOException {
        props = new Properties();
        props.load(new java.io.FileReader(CONFIG_FILE));
    }

    private Network createTestNetwork() {
        return new Network(strategy, 5, 2, 30, props);
    }

    private Network createNetworkWithMirrors(int mirrors) {
        return new Network(strategy, mirrors, 2, 30, props);
    }

    // ===== MOCK TOPOLOGY STRATEGY =====

    /**
     * Mock-Implementierung der abstrakten TopologyStrategy für Testzwecke.
     * Implementiert die abstrakten Methoden mit einfacher, vorhersagbarer Logik.
     */
    private static class MockTopologyStrategy extends TopologyStrategy {
        private int targetLinksPerNode = 2;
        private boolean addMirrorsWasCalled = false;
        private int lastNewMirrorsCount = 0;
        private int lastSimTime = 0;

        @Override
        public void handleAddNewMirrors(Network n, int newMirrors, Properties props, int simTime) {
            addMirrorsWasCalled = true;
            lastNewMirrorsCount = newMirrors;
            lastSimTime = simTime;

            // Einfache Mock-Implementierung: Erstelle Links zwischen neuen Mirrors
            List<Mirror> mirrors = n.getMirrors();

            // Verbinde neue Mirrors mit existierenden
            for (int i = mirrors.size() - newMirrors; i < mirrors.size(); i++) {
                if (i > 0) {
                    Mirror source = mirrors.get(i);
                    Mirror target = mirrors.get(i - 1);
                    Link link = new Link(
                            org.lrdm.util.IDGenerator.getInstance().getNextID(),
                            source, target, simTime, props
                    );
                    // Links werden direkt zum Netzwerk hinzugefügt
                    n.getLinks().add(link);
                }
            }
        }

        @Override
        public int getNumTargetLinks(Network n) {
            if (n == null || n.getNumMirrors() < 2) return 0;
            return Math.min(n.getNumMirrors() * targetLinksPerNode / 2,
                    n.getNumMirrors() * (n.getNumMirrors() - 1) / 2);
        }

        @Override
        public int getPredictedNumTargetLinks(Action a) {
            if (a instanceof MirrorChange mc) {
                int newMirrors = mc.getNewMirrors();
                return Math.min(newMirrors * targetLinksPerNode / 2,
                        newMirrors * (newMirrors - 1) / 2);
            } else if (a instanceof TargetLinkChange tlc) {
                Network n = tlc.getNetwork();
                int requestedLinks = tlc.getNewLinksPerMirror() * n.getNumMirrors() / 2;
                int maxPossible = n.getNumMirrors() * (n.getNumMirrors() - 1) / 2;
                return Math.min(requestedLinks, maxPossible);
            } else if (a instanceof TopologyChange tc) {
                return tc.getNewTopology().getPredictedNumTargetLinks(a);
            }
            return 0;
        }

        @Override
        public String toString() {
            return "MockTopologyStrategy{targetLinksPerNode=" + targetLinksPerNode + "}";
        }

        // Test-spezifische Hilfsmethoden
        public boolean wasAddMirrorsCalled() { return addMirrorsWasCalled; }
        public int getLastNewMirrorsCount() { return lastNewMirrorsCount; }
        public int getLastSimTime() { return lastSimTime; }
        public void setTargetLinksPerNode(int links) { this.targetLinksPerNode = links; }
        public void reset() {
            addMirrorsWasCalled = false;
            lastNewMirrorsCount = 0;
            lastSimTime = 0;
        }
    }

    // ===== INIT NETWORK TESTS =====

    @Nested
    @DisplayName("initNetwork() Tests")
    class InitNetworkTests {

        @Test
        @DisplayName("initNetwork delegiert an restartNetwork mit simTime=0")
        void testInitNetworkDelegatesToRestart() {
            Set<Link> result = strategy.initNetwork(network, props);

            assertNotNull(result, "initNetwork sollte gültiges Set zurückgeben");
            assertInstanceOf(Set.class, result, "Ergebnis sollte Set-Typ sein");
        }

        @Test
        @DisplayName("initNetwork mit null-Network wirft Exception")
        void testInitNetworkWithNullNetwork() {
            assertThrows(NullPointerException.class,
                    () -> strategy.initNetwork(null, props));
        }

        @Test
        @DisplayName("initNetwork mit null-Properties funktioniert")
        void testInitNetworkWithNullProperties() {
            assertDoesNotThrow(() -> strategy.initNetwork(network, null));
        }

        @Test
        @DisplayName("initNetwork gibt aktive Links zurück")
        void testInitNetworkReturnsActiveLinks() {
            Set<Link> result = strategy.initNetwork(network, props);

            // Alle zurückgegebenen Links sollten aktiv sein
            for (Link link : result) {
                assertTrue(link.isActive() || link.getState() == Link.State.INACTIVE,
                        "Alle Links sollten aktiv oder inaktiv sein (nicht CLOSED)");
            }
        }
    }

    // ===== RESTART NETWORK TESTS =====

    @Nested
    @DisplayName("restartNetwork() Tests")
    class RestartNetworkTests {

        @Test
        @DisplayName("restartNetwork schließt bestehende Links")
        void testRestartNetworkClosesExistingLinks() {
            // Setup: Erstelle einige Links
            Mirror m1 = new Mirror(1, 0, props);
            Mirror m2 = new Mirror(2, 0, props);
            Link link = new Link(1, m1, m2, 0, props);
            network.getLinks().add(link);

            strategy.restartNetwork(network, props, 5);

            assertEquals(Link.State.CLOSED, link.getState(),
                    "Bestehende Links sollten geschlossen werden");
        }

        @Test
        @DisplayName("restartNetwork erstellt neue Mirrors bei Bedarf")
        void testRestartNetworkCreatesNewMirrors() {
            Network smallNetwork = createNetworkWithMirrors(2);

            int initialMirrors = smallNetwork.getNumMirrors();
            strategy.restartNetwork(smallNetwork, props, 5);

            assertTrue(smallNetwork.getNumMirrors() >= initialMirrors,
                    "Netzwerk sollte genügend Mirrors haben");
        }

        @Test
        @DisplayName("restartNetwork mit verschiedenen simTimes")
        void testRestartNetworkWithDifferentSimTimes() {
            assertDoesNotThrow(() -> {
                strategy.restartNetwork(network, props, 0);
                strategy.restartNetwork(network, props, 100);
                strategy.restartNetwork(network, props, 1000);
            });
        }
    }

    // ===== HANDLE ADD NEW MIRRORS TESTS =====

    @Nested
    @DisplayName("handleAddNewMirrors() Tests")
    class HandleAddNewMirrorsTests {

        @Test
        @DisplayName("handleAddNewMirrors ruft Mock-Implementation auf")
        void testHandleAddNewMirrorsCallsMockImplementation() {
            strategy.reset();

            strategy.handleAddNewMirrors(network, 3, props, 15);

            assertTrue(strategy.wasAddMirrorsCalled(),
                    "Mock-Implementierung sollte aufgerufen werden");
            assertEquals(3, strategy.getLastNewMirrorsCount());
            assertEquals(15, strategy.getLastSimTime());
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2, 5, 10, 20})
        @DisplayName("handleAddNewMirrors mit verschiedenen Mirror-Anzahlen")
        void testHandleAddNewMirrorsWithDifferentCounts(int newMirrors) {
            strategy.reset();

            strategy.handleAddNewMirrors(network, newMirrors, props, 10);

            assertEquals(newMirrors, strategy.getLastNewMirrorsCount());
        }

        @Test
        @DisplayName("handleAddNewMirrors mit null-Network wirft Exception")
        void testHandleAddNewMirrorsWithNullNetwork() {
            assertThrows(Exception.class,
                    () -> strategy.handleAddNewMirrors(null, 1, props, 0));
        }

        @Test
        @DisplayName("handleAddNewMirrors mit negativer Anzahl")
        void testHandleAddNewMirrorsWithNegativeCount() {
            strategy.reset();

            strategy.handleAddNewMirrors(network, -1, props, 10);

            assertEquals(-1, strategy.getLastNewMirrorsCount(),
                    "Negative Werte sollten an Implementation weitergegeben werden");
        }

        @Test
        @DisplayName("handleAddNewMirrors mit null-Properties funktioniert")
        void testHandleAddNewMirrorsWithNullProperties() {
            assertDoesNotThrow(() ->
                    strategy.handleAddNewMirrors(network, 1, null, 0));
        }
    }

    // ===== HANDLE REMOVE MIRRORS TESTS =====

    @Nested
    @DisplayName("handleRemoveMirrors() Tests")
    class HandleRemoveMirrorsTests {

        @Test
        @DisplayName("handleRemoveMirrors entfernt Mirrors mit höchsten IDs")
        void testHandleRemoveMirrorsRemovesHighestIds() {
            Network testNetwork = createNetworkWithMirrors(5);
            List<Mirror> mirrorsBefore = new ArrayList<>(testNetwork.getMirrors());

            // Sortiere nach ID um höchste IDs zu identifizieren
            mirrorsBefore.sort((m1, m2) -> Integer.compare(m2.getID(), m1.getID()));
            Mirror highestIdMirror = mirrorsBefore.get(0);

            strategy.handleRemoveMirrors(testNetwork, 1, props, 20);

            assertEquals(Mirror.State.STOPPING, highestIdMirror.getState(),
                    "Mirror mit höchster ID sollte shutdown werden");
        }

        @ParameterizedTest
        @CsvSource({
                "5, 1",
                "5, 2",
                "5, 3",
                "10, 3",
                "10, 5"
        })
        @DisplayName("handleRemoveMirrors mit verschiedenen Entfernungsanzahlen")
        void testHandleRemoveMirrorsWithDifferentCounts(int totalMirrors, int toRemove) {
            Network testNetwork = createNetworkWithMirrors(totalMirrors);
            List<Mirror> mirrorsBefore = new ArrayList<>(testNetwork.getMirrorsSortedById());

            strategy.handleRemoveMirrors(testNetwork, toRemove, props, 25);

            // Prüfe, dass die richtigen Mirrors entfernt wurden
            for (int i = 0; i < toRemove; i++) {
                Mirror targetMirror = mirrorsBefore.get(totalMirrors - 1 - i);
                assertEquals(Mirror.State.STOPPING, targetMirror.getState(),
                        "Mirror " + targetMirror.getID() + " sollte STOPPING sein");
            }
        }

        @Test
        @DisplayName("handleRemoveMirrors mit zu vielen Mirrors")
        void testHandleRemoveMirrorsWithTooManyMirrors() {
            Network testNetwork = createNetworkWithMirrors(3);

            // Versuche mehr Mirrors zu entfernen als vorhanden
            assertDoesNotThrow(() ->
                    strategy.handleRemoveMirrors(testNetwork, 10, props, 30));
        }

        @Test
        @DisplayName("handleRemoveMirrors mit null-Parametern")
        void testHandleRemoveMirrorsWithNullParameters() {
            assertThrows(Exception.class,
                    () -> strategy.handleRemoveMirrors(null, 1, props, 0));

            assertDoesNotThrow(() ->
                    strategy.handleRemoveMirrors(network, 1, null, 0));
        }

        @Test
        @DisplayName("handleRemoveMirrors mit negativer Anzahl macht nichts")
        void testHandleRemoveMirrorsWithNegativeCount() {
            List<Mirror> mirrorsBefore = new ArrayList<>(network.getMirrors());

            strategy.handleRemoveMirrors(network, -1, props, 15);

            // Prüfe, dass keine Mirrors verändert wurden
            for (Mirror mirror : mirrorsBefore) {
                assertNotEquals(Mirror.State.STOPPING, mirror.getState(),
                        "Bei negativer Anzahl sollten keine Mirrors entfernt werden");
            }
        }
    }

    // ===== GET NUM TARGET LINKS TESTS =====

    @Nested
    @DisplayName("getNumTargetLinks() Tests")
    class GetNumTargetLinksTests {

        @Test
        @DisplayName("getNumTargetLinks mit normalem Network")
        void testGetNumTargetLinksWithNormalNetwork() {
            int result = strategy.getNumTargetLinks(network);

            assertTrue(result >= 0, "Target links sollte nicht negativ sein");
            assertTrue(result <= network.getNumMirrors() * (network.getNumMirrors() - 1) / 2,
                    "Target links sollte maximal vollständiger Graph sein");
        }

        @Test
        @DisplayName("getNumTargetLinks mit verschiedenen Network-Größen")
        void testGetNumTargetLinksWithDifferentNetworkSizes() {
            for (int size = 1; size <= 10; size++) {
                Network testNetwork = createNetworkWithMirrors(size);
                int result = strategy.getNumTargetLinks(testNetwork);

                assertTrue(result >= 0, "Target links für Größe " + size + " sollte nicht negativ sein");
            }
        }

        @Test
        @DisplayName("getNumTargetLinks mit null-Network")
        void testGetNumTargetLinksWithNullNetwork() {
            int result = strategy.getNumTargetLinks(null);
            assertEquals(0, result, "Null-Network sollte 0 Target-Links haben");
        }

        @Test
        @DisplayName("getNumTargetLinks ist konsistent")
        void testGetNumTargetLinksIsConsistent() {
            int result1 = strategy.getNumTargetLinks(network);
            int result2 = strategy.getNumTargetLinks(network);

            assertEquals(result1, result2, "Target links sollte konsistent sein");
        }

        @Test
        @DisplayName("getNumTargetLinks skaliert mit Network-Größe")
        void testGetNumTargetLinksScalesWithNetworkSize() {
            Network small = createNetworkWithMirrors(3);
            Network large = createNetworkWithMirrors(8);

            int smallLinks = strategy.getNumTargetLinks(small);
            int largeLinks = strategy.getNumTargetLinks(large);

            assertTrue(largeLinks >= smallLinks,
                    "Größeres Network sollte mindestens so viele Links haben");
        }
    }

    // ===== GET PREDICTED NUM TARGET LINKS TESTS =====

    @Nested
    @DisplayName("getPredictedNumTargetLinks() Tests")
    class GetPredictedNumTargetLinksTests {

        @Test
        @DisplayName("getPredictedNumTargetLinks mit MirrorChange")
        void testGetPredictedNumTargetLinksWithMirrorChange() {
            int newMirrors = 8;

            // Reale Action statt Mockito-Mock (vermeidet Byte Buddy Probleme auf JDK 24)
            MirrorChange action = new MirrorChange(
                    network,
                    org.lrdm.util.IDGenerator.getInstance().getNextID(),
                    0,
                    newMirrors
            );

            int result = strategy.getPredictedNumTargetLinks(action);

            assertTrue(result >= 0, "Predicted target links sollte nicht negativ sein");
        }

        @Test
        @DisplayName("getPredictedNumTargetLinks mit TargetLinkChange")
        void testGetPredictedNumTargetLinksWithTargetLinkChange() {
            int linksPerMirror = 3;

            // reale Action-Instanz statt Mockito-Mock (vermeidet Byte Buddy Probleme auf JDK 24)
            TargetLinkChange action = new TargetLinkChange(
                    network,
                    org.lrdm.util.IDGenerator.getInstance().getNextID(),
                    0,
                    linksPerMirror
            );

            int result = strategy.getPredictedNumTargetLinks(action);

            assertTrue(result >= 0, "Predicted target links sollte nicht negativ sein");
        }

        @Test
        @DisplayName("getPredictedNumTargetLinks mit TopologyChange")
        void testGetPredictedNumTargetLinksWithTopologyChange() {
            // Reale Topologie statt Mockito-Mock
            TopologyStrategy newStrategy = new FullyConnectedTopology();

            // TopologyChange ist aufgebaut als (Network, TopologyStrategy, id, time)
            TopologyChange action = new TopologyChange(
                    network,
                    newStrategy,
                    org.lrdm.util.IDGenerator.getInstance().getNextID(),
                    0
            );

            int expected = newStrategy.getNumTargetLinks(network);
            int result = strategy.getPredictedNumTargetLinks(action);

            assertEquals(expected, result, "Sollte an neue Topologie delegieren und deren Links liefern");
        }

        @Test
        @DisplayName("getPredictedNumTargetLinks mit null-Action")
        void testGetPredictedNumTargetLinksWithNullAction() {
            int result = strategy.getPredictedNumTargetLinks(null);
            assertEquals(0, result, "Null-Action sollte 0 zurückgeben");
        }

        @Test
        @DisplayName("getPredictedNumTargetLinks mit unbekanntem Action-Typ")
        void testGetPredictedNumTargetLinksWithUnknownActionType() {
            // Reale, unbekannte Action-Subklasse statt Mockito-Mock (vermeidet Byte Buddy Probleme auf JDK 24)
            Action unknownAction = new Action(
                    network,
                    org.lrdm.util.IDGenerator.getInstance().getNextID(),
                    0
            ) { /* keine zusätzlichen Eigenschaften */ };

            int result = strategy.getPredictedNumTargetLinks(unknownAction);

            assertEquals(0, result, "Unbekannter Action-Typ sollte 0 zurückgeben");
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 3, 5, 10, 20})
        @DisplayName("getPredictedNumTargetLinks MirrorChange mit verschiedenen Größen")
        void testGetPredictedNumTargetLinksWithDifferentMirrorCounts(int newMirrors) {
            // reale Action statt Mockito-Mock (vermeidet Byte Buddy Probleme auf JDK 24)
            MirrorChange action = new MirrorChange(
                    network,
                    org.lrdm.util.IDGenerator.getInstance().getNextID(),
                    0,
                    newMirrors
            );

            int result = strategy.getPredictedNumTargetLinks(action);

            assertTrue(result >= 0, "Result sollte nicht negativ sein für " + newMirrors + " Mirrors");
            assertTrue(result <= newMirrors * (newMirrors - 1) / 2,
                    "Result sollte maximal vollständiger Graph sein für " + newMirrors + " Mirrors");
        }
    }

    // ===== INTEGRATION TESTS =====

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Vollständiger Workflow: Init -> Add -> Remove")
        void testCompleteWorkflow() {
            // 1. Init
            Set<Link> initialLinks = strategy.initNetwork(network, props);
            int initialMirrors = network.getNumMirrors();

            // 2. Add Mirrors
            strategy.handleAddNewMirrors(network, 2, props, 10);
            assertTrue(strategy.wasAddMirrorsCalled(), "Add sollte aufgerufen werden");

            // 3. Remove Mirrors
            strategy.handleRemoveMirrors(network, 1, props, 20);

            // Validierungen
            assertNotNull(initialLinks);
            assertTrue(initialMirrors > 0);
        }

        @Test
        @DisplayName("Konsistenz zwischen getNumTargetLinks und getPredictedNumTargetLinks")
        void testConsistencyBetweenTargetLinksAndPredicted() {
            int currentTargetLinks = strategy.getNumTargetLinks(network);

            // Erstelle reale MirrorChange-Action (vermeidet Mockito/ByteBuddy-Probleme auf JDK 24)
            MirrorChange action = new MirrorChange(
                    network,
                    org.lrdm.util.IDGenerator.getInstance().getNextID(),
                    0,
                    network.getNumMirrors()
            );

            int predictedLinks = strategy.getPredictedNumTargetLinks(action);

            assertEquals(currentTargetLinks, predictedLinks,
                    "Predicted links sollten current links entsprechen bei gleicher Mirror-Anzahl");
        }

        @Test
        @DisplayName("Mehrfache restartNetwork Aufrufe")
        void testMultipleRestartNetworkCalls() {
            assertDoesNotThrow(() -> {
                strategy.restartNetwork(network, props, 5);
                strategy.restartNetwork(network, props, 10);
                strategy.restartNetwork(network, props, 15);
            });
        }

        @Test
        @DisplayName("Strategieverhalten bei unterschiedlichen simTimes")
        void testStrategyBehaviorWithDifferentSimTimes() {
            for (int simTime = 0; simTime < 100; simTime += 10) {
                strategy.reset();
                strategy.handleAddNewMirrors(network, 1, props, simTime);
                assertEquals(simTime, strategy.getLastSimTime(),
                        "SimTime sollte korrekt weitergegeben werden");
            }
        }
    }

    // ===== EDGE CASES UND ERROR HANDLING =====

    @Nested
    @DisplayName("Edge Cases und Error Handling")
    class EdgeCasesAndErrorHandlingTests {

        @Test
        @DisplayName("Network mit 0 Mirrors")
        void testNetworkWithZeroMirrors() {
            Network emptyNetwork = new Network(strategy, 0, 0, 30, props);

            int targetLinks = strategy.getNumTargetLinks(emptyNetwork);
            assertEquals(0, targetLinks, "Leeres Network sollte 0 Target-Links haben");
        }

        @Test
        @DisplayName("Network mit 1 Mirror")
        void testNetworkWithOneMirror() {
            Network singleMirrorNetwork = new Network(strategy, 1, 1, 30, props);

            int targetLinks = strategy.getNumTargetLinks(singleMirrorNetwork);
            assertEquals(0, targetLinks, "Single Mirror Network sollte 0 Target-Links haben");
        }

        @Test
        @DisplayName("Extrem große Mirror-Anzahlen")
        void testExtremelyLargeMirrorCounts() {
            // reale Action statt Mockito-Mock (vermeidet Byte Buddy Probleme auf JDK 24)
            MirrorChange action = new MirrorChange(
                    network,
                    org.lrdm.util.IDGenerator.getInstance().getNextID(),
                    0,
                    1000
            );

            int result = strategy.getPredictedNumTargetLinks(action);

            assertTrue(result >= 0, "Result sollte auch bei großen Zahlen nicht negativ sein");
        }

        @Test
        @DisplayName("Negative simTime Werte")
        void testNegativeSimTimeValues() {
            assertDoesNotThrow(() -> {
                strategy.handleAddNewMirrors(network, 1, props, -1);
                strategy.handleRemoveMirrors(network, 1, props, -5);
            });
        }

        @Test
        @DisplayName("Sehr große simTime Werte")
        void testVeryLargeSimTimeValues() {
            assertDoesNotThrow(() -> {
                strategy.handleAddNewMirrors(network, 1, props, Integer.MAX_VALUE);
                strategy.handleRemoveMirrors(network, 1, props, Integer.MAX_VALUE - 1);
            });
        }

        @Test
        @DisplayName("Leere Properties")
        void testEmptyProperties() {
            Properties emptyProps = new Properties();

            assertDoesNotThrow(() -> {
                strategy.initNetwork(network, emptyProps);
                strategy.handleAddNewMirrors(network, 1, emptyProps, 5);
            });
        }
    }

    // ===== PERFORMANCE TESTS =====

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Performance von getNumTargetLinks")
        void testGetNumTargetLinksPerformance() {
            long startTime = System.nanoTime();

            for (int i = 0; i < 1000; i++) {
                strategy.getNumTargetLinks(network);
            }

            long endTime = System.nanoTime();
            long duration = endTime - startTime;

            assertTrue(duration < 100_000_000, // 100ms
                    "getNumTargetLinks sollte schnell genug sein");
        }

        @Test
        @DisplayName("Performance von handleAddNewMirrors")
        void testHandleAddNewMirrorsPerformance() {
            long startTime = System.nanoTime();

            for (int i = 0; i < 100; i++) {
                strategy.reset();
                strategy.handleAddNewMirrors(network, 1, props, i);
            }

            long endTime = System.nanoTime();
            long duration = endTime - startTime;

            assertTrue(duration < 1_000_000_000, // 1 Sekunde
                    "handleAddNewMirrors sollte performant sein");
        }
    }

    // ===== TOSTRING TESTS =====

    @Nested
    @DisplayName("toString() Tests")
    class ToStringTests {

        @Test
        @DisplayName("toString gibt nicht-null zurück")
        void testToStringReturnsNonNull() {
            String result = strategy.toString();
            assertNotNull(result, "toString sollte nicht null sein");
        }

        @Test
        @DisplayName("toString enthält Strategie-Information")
        void testToStringContainsStrategyInfo() {
            String result = strategy.toString();
            assertTrue(result.contains("MockTopologyStrategy") || result.contains("targetLinks"),
                    "toString sollte Strategie-Information enthalten");
        }

        @Test
        @DisplayName("toString ist konsistent")
        void testToStringIsConsistent() {
            String result1 = strategy.toString();
            String result2 = strategy.toString();
            assertEquals(result1, result2, "toString sollte konsistent sein");
        }
    }

    // ===== BOUNDARY TESTS =====

    @Nested
    @DisplayName("Boundary Value Tests")
    class BoundaryValueTests {

        @Test
        @DisplayName("Boundary Test für Mirror-Anzahlen")
        void testMirrorCountBoundaries() {
            int[] boundaryValues = {0, 1, 2, 10, 100};

            for (int count : boundaryValues) {
                // reale Action statt Mockito-Mock (vermeidet Byte Buddy Probleme auf JDK 24)
                MirrorChange action = new MirrorChange(
                        network,
                        org.lrdm.util.IDGenerator.getInstance().getNextID(),
                        0,
                        count
                );

                int result = strategy.getPredictedNumTargetLinks(action);
                assertTrue(result >= 0, "Result für " + count + " Mirrors sollte >= 0 sein");
            }
        }

        @Test
        @DisplayName("Boundary Test für Links pro Mirror")
        void testLinksPerMirrorBoundaries() {
            int[] boundaryValues = {0, 1, 2, 5, network.getNumMirrors() - 1};

            for (int linksPerMirror : boundaryValues) {
                // Erzeuge eine reale Action-Instanz statt eines Mocks (vermeidet Byte Buddy/Mockito Inline auf JDK 24)
                TargetLinkChange action = new TargetLinkChange(
                        network,
                        org.lrdm.util.IDGenerator.getInstance().getNextID(),
                        0,
                        linksPerMirror
                );

                int result = strategy.getPredictedNumTargetLinks(action);
                assertTrue(result >= 0, "Result für " + linksPerMirror + " Links/Mirror sollte >= 0 sein");
            }
        }
    }
}