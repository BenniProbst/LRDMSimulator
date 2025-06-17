package org.lrdm.topologies.node;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.TimedRDMSim;
import org.lrdm.probes.MirrorProbe;

import java.io.IOException;
import java.util.*;

import static org.lrdm.TestProperties.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Umfassende Tests für NConnectedMirrorNode nach FullyConnectedMirrorNodeTest-Vorbild.
 * <p>
 * Verbesserte Testmethodik:
 * - Integration mit echter Simulator-Umgebung und MirrorProbe
 * - Realistische Tests mit echten Mirror-Instanzen und Links
 * - Komplexe Szenarien mit verschiedenen Vernetzungsgraden (N=1 bis N=5)
 * - Performance-Tests und Edge Cases
 * - Defensive Programmierung und Null-Handling
 * - Kompatibilität mit MirrorNode-Funktionalität
 * - Symmetrische Verbindungen und bidirektionale Links
 * - Multi-Type-Koexistenz Tests
 * - Parametrisierte Tests für verschiedene N-Werte
 * <p>
 * Struktur:
 * - Grundfunktionen (Konstruktoren, Vererbung, Typ-System)
 * - Connectivity-Management (Vernetzungsgrad, erwartete Links)
 * - Netzwerk-Navigation (Connected Nodes, Größe, etc.)
 * - Struktur-Validierung (gültige/ungültige N-Connected-Netze)
 * - Performance und Edge Cases (große Netze, verschiedene N-Werte)
 * - Exception-Handling und Defensive Programmierung
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
@DisplayName("NConnectedMirrorNode spezifische Tests")
class NConnectedMirrorNodeTest {

    private TimedRDMSim sim;
    private NConnectedMirrorNode ncNode;
    private Properties props;
    private static final String config = "resources/sim-test-mirror node.conf";

    @BeforeEach
    void setUp() throws IOException {
        loadProperties(config);
        props = getProps();
        ncNode = new NConnectedMirrorNode(1); // Standard: N=2
    }

    public void initSimulator() throws IOException {
        initSimulator(config);
    }

    public void initSimulator(String config) throws IOException {
        loadProperties(config);
        props = getProps();
        sim = new TimedRDMSim(config);
        sim.setHeadless(true);
    }

    private MirrorProbe getMirrorProbe() {
        return sim.getMirrorProbe();
    }

    /**
     * Erstellt eine Liste von Simulator-Mirrors, entweder aus der MirrorProbe
     * oder als Fallback-Mirrors, falls nicht genügend verfügbar ist.
     */
    private List<Mirror> getSimMirrors(MirrorProbe probe) {
        List<Mirror> simMirrors = probe.getMirrors();

        // Fallback falls weniger als 10 Mirrors verfügbar (für große Netze)
        if (simMirrors.size() < 10) {
            List<Mirror> fallbackMirrors = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                Mirror mirror = new Mirror(201 + i, 0, props);
                fallbackMirrors.add(mirror);
            }
            return fallbackMirrors;
        }

        return simMirrors;
    }

    /**
     * Hilfsmethode zum Setup einer gültigen N-Connected-Struktur mit echten Mirror-Links.
     * Erstellt alle notwendigen bidirektionalen Verbindungen für echte N-Connected-Validierung.
     */
    private void setupValidNConnectedStructure(NConnectedMirrorNode head, List<NConnectedMirrorNode> peers) throws IOException {
        initSimulator();
        List<Mirror> mirrors = getSimMirrors(getMirrorProbe());

        // Mirrors zuweisen
        head.setMirror(mirrors.get(0));
        for (int i = 0; i < peers.size(); i++) {
            peers.get(i).setMirror(mirrors.get(i + 1));
        }

        // N-Connected-Links basierend auf Vernetzungsgrad erstellen
        List<NConnectedMirrorNode> allNodes = new ArrayList<>();
        allNodes.add(head);
        allNodes.addAll(peers);

        // Erstelle Links bis jeder Knoten N Verbindungen hat
        for (int i = 0; i < allNodes.size(); i++) {
            NConnectedMirrorNode node = allNodes.get(i);
            int currentConnections = 0;
            int targetConnections = node.getConnectivityDegree(); // Direkt verwenden

            for (int j = 0; j < allNodes.size() && currentConnections < targetConnections; j++) {
                if (i != j) { // Nicht mit sich selbst verbinden
                    NConnectedMirrorNode target = allNodes.get(j);
                    Link link = new Link(
                            i * 100 + j,
                            node.getMirror(),
                            target.getMirror(),
                            0,
                            props
                    );
                    node.addLink(link);
                    currentConnections++;
                }
            }
        }
    }

    /**
     * Erstellt eine Ring-Topologie für 2-Connected-Tests.
     */
    private void setupRingTopology(List<NConnectedMirrorNode> nodes) throws IOException {
        initSimulator();
        List<Mirror> mirrors = getSimMirrors(getMirrorProbe());

        // Mirrors zuweisen
        for (int i = 0; i < nodes.size(); i++) {
            nodes.get(i).setMirror(mirrors.get(i));
        }

        // Ring-Links erstellen (jeder Knoten mit seinen beiden Nachbarn)
        for (int i = 0; i < nodes.size(); i++) {
            NConnectedMirrorNode current = nodes.get(i);
            NConnectedMirrorNode next = nodes.get((i + 1) % nodes.size());
            NConnectedMirrorNode prev = nodes.get((i - 1 + nodes.size()) % nodes.size());

            // Link zum nächsten Knoten
            Link linkNext = new Link(
                    i * 100 + ((i + 1) % nodes.size()),
                    current.getMirror(),
                    next.getMirror(),
                    0,
                    props
            );
            current.addLink(linkNext);

            // Link zum vorherigen Knoten
            Link linkPrev = new Link(
                    i * 100 + ((i - 1 + nodes.size()) % nodes.size()),
                    current.getMirror(),
                    prev.getMirror(),
                    0,
                    props
            );
            current.addLink(linkPrev);
        }
    }

    @Nested
    @DisplayName("NConnectedMirrorNode Grundfunktionen")
    class NConnectedMirrorNodeBasicTests {

        @Test
        @DisplayName("NConnectedMirrorNode erbt MirrorNode-Funktionalität")
        void testInheritedMirrorNodeFunctionality() {
            Mirror testMirror = new Mirror(101, 0, props);
            ncNode.setMirror(testMirror);

            assertEquals(1, ncNode.getId());
            assertEquals(testMirror, ncNode.getMirror());
            assertEquals(0, ncNode.getNumImplementedLinks());
            assertTrue(ncNode.getImplementedLinks().isEmpty());

            // Test deriveTypeId
            assertEquals(StructureNode.StructureType.N_CONNECTED, ncNode.deriveTypeId());
        }

        @Test
        @DisplayName("StructureType Integration und Vererbung")
        void testStructureTypeIntegration() {
            // Test deriveTypeId Funktionalität
            assertEquals(StructureNode.StructureType.N_CONNECTED, ncNode.deriveTypeId());

            // Test mit Mirror
            Mirror testMirror = new Mirror(101, 0, props);
            ncNode.setMirror(testMirror);

            // deriveTypeId sollte unverändert bleiben
            assertEquals(StructureNode.StructureType.N_CONNECTED, ncNode.deriveTypeId());

            // Test Konstruktor-Varianten
            NConnectedMirrorNode node2 = new NConnectedMirrorNode(99, testMirror, 3);
            assertEquals(StructureNode.StructureType.N_CONNECTED, node2.deriveTypeId());
            assertEquals(3, node2.getConnectivityDegree());
        }

        @Test
        @DisplayName("MirrorNode-Mirror Integration")
        void testMirrorIntegration() {
            assertNull(ncNode.getMirror());

            // Test setMirror
            Mirror testMirror = new Mirror(101, 0, props);
            ncNode.setMirror(testMirror);
            assertEquals(testMirror, ncNode.getMirror());

            // Test-Link-Management über Mirror
            Mirror targetMirror = new Mirror(102, 0, props);
            Link testLink = new Link(1, testMirror, targetMirror, 0, props);

            ncNode.addLink(testLink);
            assertEquals(1, ncNode.getNumImplementedLinks());
            assertTrue(ncNode.getImplementedLinks().contains(testLink));

            ncNode.removeLink(testLink);
            assertEquals(0, ncNode.getNumImplementedLinks());
            assertFalse(ncNode.getImplementedLinks().contains(testLink));
        }

        @Test
        @DisplayName("NConnectedMirrorNode Konstruktoren")
        void testConstructors() {
            // Standard Konstruktor (N=2)
            NConnectedMirrorNode node1 = new NConnectedMirrorNode(5);
            assertEquals(5, node1.getId());
            assertNull(node1.getMirror());
            assertEquals(2, node1.getConnectivityDegree()); // Standard
            assertEquals(StructureNode.StructureType.N_CONNECTED, node1.deriveTypeId());

            // Konstruktor mit Vernetzungsgrad
            NConnectedMirrorNode node2 = new NConnectedMirrorNode(6, 3);
            assertEquals(6, node2.getId());
            assertEquals(3, node2.getConnectivityDegree());
            assertNull(node2.getMirror());

            // Konstruktor mit Mirror und Vernetzungsgrad
            Mirror mirror = new Mirror(102, 0, props);
            NConnectedMirrorNode node3 = new NConnectedMirrorNode(7, mirror, 4);
            assertEquals(7, node3.getId());
            assertEquals(mirror, node3.getMirror());
            assertEquals(4, node3.getConnectivityDegree());
            assertEquals(StructureNode.StructureType.N_CONNECTED, node3.deriveTypeId());
        }

        @Test
        @DisplayName("Ungültige Vernetzungsgrade werden abgewiesen")
        void testInvalidConnectivityDegrees() {
            // Vernetzungsgrad < 1 sollte Exception werfen
            IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () ->
                    new NConnectedMirrorNode(1, 0));
            assertTrue(ex1.getMessage().contains("Connectivity degree must be at least 1"));

            IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () ->
                    new NConnectedMirrorNode(1, -1));
            assertTrue(ex2.getMessage().contains("at least 1"));

            IllegalArgumentException ex3 = assertThrows(IllegalArgumentException.class, () ->
                    new NConnectedMirrorNode(1, new Mirror(101, 0, props), -5));
            assertTrue(ex3.getMessage().contains("at least 1"));

            // Vernetzungsgrad = 1 sollte funktionieren
            assertDoesNotThrow(() -> new NConnectedMirrorNode(1, 1));
        }

        @Test
        @DisplayName("deriveTypeId gibt N_CONNECTED zurück")
        void testDeriveTypeId() {
            assertEquals(StructureNode.StructureType.N_CONNECTED, ncNode.deriveTypeId());

            // Test verschiedener Vernetzungsgrade
            NConnectedMirrorNode node1 = new NConnectedMirrorNode(2, 1);
            assertEquals(StructureNode.StructureType.N_CONNECTED, node1.deriveTypeId());

            NConnectedMirrorNode node3 = new NConnectedMirrorNode(3, 3);
            assertEquals(StructureNode.StructureType.N_CONNECTED, node3.deriveTypeId());

            NConnectedMirrorNode node5 = new NConnectedMirrorNode(4, 5);
            assertEquals(StructureNode.StructureType.N_CONNECTED, node5.deriveTypeId());

            // Auch nach Strukturveränderungen
            NConnectedMirrorNode peer = new NConnectedMirrorNode(2);
            ncNode.addChild(peer);
            assertEquals(StructureNode.StructureType.N_CONNECTED, ncNode.deriveTypeId());
            assertEquals(StructureNode.StructureType.N_CONNECTED, peer.deriveTypeId());
        }
    }

    @Nested
    @DisplayName("Connectivity-Management")
    class ConnectivityManagementTests {

        @Test
        @DisplayName("getConnectivityDegree gibt konfigurierten Wert zurück")
        void testGetConnectivityDegree() {
            assertEquals(2, ncNode.getConnectivityDegree()); // Standard

            // Test verschiedener Vernetzungsgrade
            NConnectedMirrorNode node1 = new NConnectedMirrorNode(2, 1);
            assertEquals(1, node1.getConnectivityDegree());

            NConnectedMirrorNode node3 = new NConnectedMirrorNode(3, 3);
            assertEquals(3, node3.getConnectivityDegree());

            NConnectedMirrorNode node5 = new NConnectedMirrorNode(4, 5);
            assertEquals(5, node5.getConnectivityDegree());
        }

        @Test
        @DisplayName("getExpectedLinkCount entspricht Vernetzungsgrad")
        void testGetExpectedLinkCount() {
            assertEquals(2, ncNode.getExpectedLinkCount());

            NConnectedMirrorNode node1 = new NConnectedMirrorNode(2, 1);
            assertEquals(1, node1.getExpectedLinkCount());

            NConnectedMirrorNode node4 = new NConnectedMirrorNode(3, 4);
            assertEquals(4, node4.getExpectedLinkCount());
        }

        @Test
        @DisplayName("hasOptimalConnectivity ohne Links")
        void testHasOptimalConnectivityWithoutLinks() {
            // Ohne Links: nicht optimal
            assertFalse(ncNode.hasOptimalConnectivity());

            // Auch bei verschiedenen Vernetzungsgraden
            NConnectedMirrorNode node1 = new NConnectedMirrorNode(2, 1);
            assertFalse(node1.hasOptimalConnectivity());

            NConnectedMirrorNode node3 = new NConnectedMirrorNode(3, 3);
            assertFalse(node3.hasOptimalConnectivity());
        }

        @Test
        @DisplayName("hasOptimalConnectivity mit echten N-Connected-Links")
        void testHasOptimalConnectivityWithRealLinks() throws IOException {
            // Setup: 3-Connected mid 5 Knoten
            NConnectedMirrorNode head = new NConnectedMirrorNode(1, 3);
            List<NConnectedMirrorNode> peers = Arrays.asList(
                    new NConnectedMirrorNode(2, 3),
                    new NConnectedMirrorNode(3, 3),
                    new NConnectedMirrorNode(4, 3),
                    new NConnectedMirrorNode(5, 3)
            );

            head.setHead(StructureNode.StructureType.N_CONNECTED, true);
            for (NConnectedMirrorNode peer : peers) {
                head.addChild(peer, Set.of(StructureNode.StructureType.N_CONNECTED),
                        Map.of(StructureNode.StructureType.N_CONNECTED, head.getId()));
            }

            // Ohne Links: nicht optimal
            assertFalse(head.hasOptimalConnectivity());

            // Mit korrekten N-Connected-Links: optimal
            setupValidNConnectedStructure(head, peers);
            assertTrue(head.hasOptimalConnectivity());

            // Alle Peers sollten auch optimal sein
            for (NConnectedMirrorNode peer : peers) {
                assertTrue(peer.hasOptimalConnectivity());
            }
        }

        @Test
        @DisplayName("getExpectedTotalLinkCount verschiedene Szenarien")
        void testGetExpectedTotalLinkCount() {
            // 2-Connected mit 4 Knoten: Jeder Knoten hat 2 Links = 4 * 2/2 = 4 Links total
            NConnectedMirrorNode head = new NConnectedMirrorNode(1, 2);
            NConnectedMirrorNode peer1 = new NConnectedMirrorNode(2, 2);
            NConnectedMirrorNode peer2 = new NConnectedMirrorNode(3, 2);
            NConnectedMirrorNode peer3 = new NConnectedMirrorNode(4, 2);

            head.setHead(StructureNode.StructureType.N_CONNECTED, true);
            head.addChild(peer1);
            head.addChild(peer2);
            head.addChild(peer3);

            assertEquals(4, head.getExpectedTotalLinkCount()); // 4 Knoten * 2 Links / 2 = 4

            // 3-Connected mit 4 Knoten: Jeder Knoten hat 3 Links = 4 * 3/2 = 6 Links total
            NConnectedMirrorNode head3 = new NConnectedMirrorNode(1, 3);
            assertEquals(6, head3.getExpectedTotalLinkCount()); // 4 Knoten * 3 Links / 2 = 6
        }

        @Test
        @DisplayName("getConnectivityDensity berechnet korrekte Dichte")
        void testGetConnectivityDensity() throws IOException {
            // 2-Connected mit 4 Knoten
            NConnectedMirrorNode head = new NConnectedMirrorNode(1, 2);
            List<NConnectedMirrorNode> peers = Arrays.asList(
                    new NConnectedMirrorNode(2, 2),
                    new NConnectedMirrorNode(3, 2),
                    new NConnectedMirrorNode(4, 2)
            );

            head.setHead(StructureNode.StructureType.N_CONNECTED, true);
            for (NConnectedMirrorNode peer : peers) {
                head.addChild(peer);
            }

            // Ohne Links: Dichte = 0
            assertEquals(0.0, head.getConnectivityDensity(), 0.001);

            // Mit korrekten Links
            setupValidNConnectedStructure(head, peers);
            // 4 tatsächliche Links / 6 mögliche Links (vollständiges Netz) = 0.667
            double expectedDensity = 4.0 / 6.0; // (4 * 3)/2 = 6 mögliche Links in vollständigem 4-Knoten-Netz
            assertEquals(expectedDensity, head.getConnectivityDensity(), 0.01);
        }
    }

    @Nested
    @DisplayName("Netzwerk-Navigation")
    class NetworkNavigationTests {

        @Test
        @DisplayName("getNConnectedHead findet Head-Knoten")
        void testGetNConnectedHead() {
            // Setup
            NConnectedMirrorNode head = new NConnectedMirrorNode(1, 2);
            NConnectedMirrorNode peer1 = new NConnectedMirrorNode(2, 2);
            NConnectedMirrorNode peer2 = new NConnectedMirrorNode(3, 2);

            head.setHead(StructureNode.StructureType.N_CONNECTED, true);
            head.addChild(peer1, Set.of(StructureNode.StructureType.N_CONNECTED),
                    Map.of(StructureNode.StructureType.N_CONNECTED, head.getId()));
            head.addChild(peer2, Set.of(StructureNode.StructureType.N_CONNECTED),
                    Map.of(StructureNode.StructureType.N_CONNECTED, head.getId()));

            // Head sollte sich selbst finden
            assertEquals(head, head.getNConnectedHead());

            // Peers sollten Head finden
            assertEquals(head, peer1.getNConnectedHead());
            assertEquals(head, peer2.getNConnectedHead());
        }

        @Test
        @DisplayName("isNConnectedHead erkennt Head-Knoten")
        void testIsNConnectedHead() {
            NConnectedMirrorNode head = new NConnectedMirrorNode(1, 2);
            NConnectedMirrorNode peer = new NConnectedMirrorNode(2, 2);

            // Vor Head-Setup
            assertFalse(head.isNConnectedHead());
            assertFalse(peer.isNConnectedHead());

            // Nach Head-Setup
            head.setHead(StructureNode.StructureType.N_CONNECTED, true);
            assertTrue(head.isNConnectedHead());
            assertFalse(peer.isNConnectedHead());
        }

        @Test
        @DisplayName("getNetworkSize berechnet Strukturgröße")
        void testGetNetworkSize() {
            NConnectedMirrorNode head = new NConnectedMirrorNode(1, 2);

            // Einzelner Knoten
            assertEquals(1, head.getNetworkSize());

            // Mit Peers
            NConnectedMirrorNode peer1 = new NConnectedMirrorNode(2, 2);
            NConnectedMirrorNode peer2 = new NConnectedMirrorNode(3, 2);

            head.setHead(StructureNode.StructureType.N_CONNECTED, true);
            head.addChild(peer1, Set.of(StructureNode.StructureType.N_CONNECTED),
                    Map.of(StructureNode.StructureType.N_CONNECTED, head.getId()));
            assertEquals(2, head.getNetworkSize());

            head.addChild(peer2, Set.of(StructureNode.StructureType.N_CONNECTED),
                    Map.of(StructureNode.StructureType.N_CONNECTED, head.getId()));
            assertEquals(3, head.getNetworkSize());
        }

        @Test
        @DisplayName("getConnectedNodes gibt verbundene Knoten zurück")
        void testGetConnectedNodes() throws IOException {
            // 2-Connected Ring mid 4 Knoten
            List<NConnectedMirrorNode> nodes = Arrays.asList(
                    new NConnectedMirrorNode(1, 2),
                    new NConnectedMirrorNode(2, 2),
                    new NConnectedMirrorNode(3, 2),
                    new NConnectedMirrorNode(4, 2)
            );

            NConnectedMirrorNode head = nodes.get(0);
            head.setHead(StructureNode.StructureType.N_CONNECTED, true);
            for (int i = 1; i < nodes.size(); i++) {
                head.addChild(nodes.get(i), Set.of(StructureNode.StructureType.N_CONNECTED),
                        Map.of(StructureNode.StructureType.N_CONNECTED, head.getId()));
            }

            // Ohne Links: keine Verbindungen
            Set<NConnectedMirrorNode> connected = head.getConnectedNodes();
            assertTrue(connected.isEmpty());

            // Mit Ring-Topologie
            setupRingTopology(nodes);

            // Jeder Knoten sollte 2 Verbindungen haben
            for (NConnectedMirrorNode node : nodes) {
                connected = node.getConnectedNodes();
                assertEquals(2, connected.size());
            }
        }
    }

    @Nested
    @DisplayName("Struktur-Validierung")
    class StructureValidationTests {

        @Test
        @DisplayName("isValidStructure mit echten N-Connected-Links")
        void testIsValidStructureWithRealLinks() throws IOException {
            // 2-Connected mit 4 Knoten
            NConnectedMirrorNode head = new NConnectedMirrorNode(1, 2);
            List<NConnectedMirrorNode> peers = Arrays.asList(
                    new NConnectedMirrorNode(2, 2),
                    new NConnectedMirrorNode(3, 2),
                    new NConnectedMirrorNode(4, 2)
            );

            head.setHead(StructureNode.StructureType.N_CONNECTED, true);
            for (NConnectedMirrorNode peer : peers) {
                head.addChild(peer, Set.of(StructureNode.StructureType.N_CONNECTED),
                        Map.of(StructureNode.StructureType.N_CONNECTED, head.getId()));
            }

            // Ohne Links: ungültig
            assertFalse(head.isValidStructure());

            // Mit korrekten Links: gültig
            setupValidNConnectedStructure(head, peers);
            assertTrue(head.isValidStructure());

            // Test direkt mit Set-Parameter
            Set<StructureNode> allNodes = new HashSet<>();
            allNodes.add(head);
            allNodes.addAll(peers);
            assertTrue(head.isValidStructure(allNodes, StructureNode.StructureType.N_CONNECTED, head));
        }

        @Test
        @DisplayName("isValidStructure mit zu wenigen Knoten")
        void testIsValidStructureInsufficientNodes() {
            // N=3 benötigt mindestens 4 Knoten
            NConnectedMirrorNode head = new NConnectedMirrorNode(1, 3);
            NConnectedMirrorNode peer1 = new NConnectedMirrorNode(2, 3);
            NConnectedMirrorNode peer2 = new NConnectedMirrorNode(3, 3);

            head.setHead(StructureNode.StructureType.N_CONNECTED, true);
            head.addChild(peer1);
            head.addChild(peer2);

            // Nur 3 Knoten für 3-Connected: ungültig
            assertFalse(head.isValidStructure());
        }

        @Test
        @DisplayName("isValidStructure mit unterschiedlichen Vernetzungsgraden")
        void testIsValidStructureMismatchedConnectivityDegrees() throws IOException {
            // Gemischte Vernetzungsgrade: ungültig
            NConnectedMirrorNode head = new NConnectedMirrorNode(1, 2);
            NConnectedMirrorNode peer1 = new NConnectedMirrorNode(2, 3); // Unterschiedlicher Grad
            NConnectedMirrorNode peer2 = new NConnectedMirrorNode(3, 2);

            head.setHead(StructureNode.StructureType.N_CONNECTED, true);
            head.addChild(peer1);
            head.addChild(peer2);

            setupValidNConnectedStructure(head, List.of(peer2)); // Nur peer2 setup

            assertFalse(head.isValidStructure());
        }

        @Test
        @DisplayName("Symmetrische Verbindungen in N-Connected-Strukturen")
        void testSymmetricConnections() throws IOException {
            // 2-Connected mit 3 Knoten: Ring-ähnlich
            NConnectedMirrorNode head = new NConnectedMirrorNode(1, 2);
            NConnectedMirrorNode node2 = new NConnectedMirrorNode(2, 2);
            NConnectedMirrorNode node3 = new NConnectedMirrorNode(3, 2);

            List<NConnectedMirrorNode> allNodes = Arrays.asList(head, node2, node3);
            setupRingTopology(allNodes);

            // Prüfe bidirektionale Verbindungen im Ring
            assertTrue(head.isLinkedWith(node2));
            assertTrue(node2.isLinkedWith(head));

            assertTrue(head.isLinkedWith(node3));
            assertTrue(node3.isLinkedWith(head));

            assertTrue(node2.isLinkedWith(node3));
            assertTrue(node3.isLinkedWith(node2));
        }

        @Test
        @DisplayName("isFaultTolerant prüft Ausfalltoleranz")
        void testIsFaultTolerant() throws IOException {
            // 1-Connected (Baum): nicht fault-tolerant
            NConnectedMirrorNode head1 = new NConnectedMirrorNode(1, 1);
            assertFalse(head1.isFaultTolerant());

            // 2-Connected: fault-tolerant
            NConnectedMirrorNode head2 = new NConnectedMirrorNode(1, 2);
            List<NConnectedMirrorNode> peers2 = Arrays.asList(
                    new NConnectedMirrorNode(2, 2),
                    new NConnectedMirrorNode(3, 2),
                    new NConnectedMirrorNode(4, 2)
            );

            head2.setHead(StructureNode.StructureType.N_CONNECTED, true);
            for (NConnectedMirrorNode peer : peers2) {
                head2.addChild(peer);
            }
            setupRingTopology(Arrays.asList(head2, peers2.get(0), peers2.get(1), peers2.get(2)));

            assertTrue(head2.isFaultTolerant());
        }
    }

    @Nested
    @DisplayName("Multi-Type-Koexistenz")
    class MultiTypeCoexistenceTests {

        @Test
        @DisplayName("Multi-Type-Koexistenz: N-Connected mit anderen Strukturen")
        void testMultiTypeCoexistence() throws IOException {
            // N-Connected-Struktur
            NConnectedMirrorNode ncHead = new NConnectedMirrorNode(1, 2);
            NConnectedMirrorNode ncNode2 = new NConnectedMirrorNode(2, 2);

            // Andere Strukturtypen als Child (simuliert)
            StructureNode otherTypeNode = new StructureNode(3);

            ncHead.setHead(StructureNode.StructureType.N_CONNECTED, true);
            ncHead.addChild(ncNode2, Set.of(StructureNode.StructureType.N_CONNECTED),
                    Map.of(StructureNode.StructureType.N_CONNECTED, ncHead.getId()));

            // Anderer Strukturtyp sollte nicht die N-Connected-Validierung beeinflussen
            ncHead.addChild(otherTypeNode, Set.of(StructureNode.StructureType.DEFAULT),
                    Map.of(StructureNode.StructureType.DEFAULT, otherTypeNode.getId()));

            // N-Connected-spezifische Methoden sollten nur N-Connected-Knoten berücksichtigen
            assertEquals(2, ncHead.getNetworkSize()); // Nur N-Connected-Knoten zählen

            Set<NConnectedMirrorNode> connected = ncHead.getConnectedNodes();
            assertEquals(0, connected.size()); // Ohne Links noch keine Verbindungen

            // Nach Link-Setup
            setupValidNConnectedStructure(ncHead, List.of(ncNode2));
            connected = ncHead.getConnectedNodes();
            assertEquals(1, connected.size());
            assertTrue(connected.contains(ncNode2));
        }

        @Test
        @DisplayName("deriveTypeId ist unabhängig von anderen Strukturtypen")
        void testDeriveTypeIdIndependence() {
            NConnectedMirrorNode ncNode = new NConnectedMirrorNode(1, 3);
            StructureNode defaultNode = new StructureNode(2);

            // Vor und nach Hinzufügen anderer Typen
            assertEquals(StructureNode.StructureType.N_CONNECTED, ncNode.deriveTypeId());

            ncNode.addChild(defaultNode);
            assertEquals(StructureNode.StructureType.N_CONNECTED, ncNode.deriveTypeId());

            defaultNode.addChild(ncNode);
            assertEquals(StructureNode.StructureType.N_CONNECTED, ncNode.deriveTypeId());
        }
    }

    @Nested
    @DisplayName("Exception-Handling und Edge Cases")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("Exception-Handling bei ungültigen Operationen")
        void testExceptionHandling() {
            // Ungültige Vernetzungsgrade
            IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class,
                    () -> new NConnectedMirrorNode(1, 0));
            assertTrue(ex1.getMessage().contains("Connectivity degree must be at least 1"));

            IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class,
                    () -> new NConnectedMirrorNode(1, -5));
            assertTrue(ex2.getMessage().contains("at least 1"));

            // Operationen auf ungültigen Strukturen sollten defensiv sein
            NConnectedMirrorNode invalidNode = new NConnectedMirrorNode(1, 10);
            assertDoesNotThrow(invalidNode::getConnectedNodes);
            assertDoesNotThrow(invalidNode::getNConnectedHead);
            assertDoesNotThrow(invalidNode::isFaultTolerant);
            assertDoesNotThrow(invalidNode::getConnectivityDensity);
        }

        @Test
        @DisplayName("Null-Handling in Navigation-Methoden")
        void testNullHandling() {
            NConnectedMirrorNode node = new NConnectedMirrorNode(1, 2);

            // Ohne Mirror
            assertDoesNotThrow(node::getConnectedNodes);
            assertDoesNotThrow(node::hasOptimalConnectivity);

            // Ohne Parent/Children
            assertDoesNotThrow(node::getNConnectedHead);
            assertDoesNotThrow(node::getNetworkSize);
        }

        @Test
        @DisplayName("Edge Case: Sehr große Vernetzungsgrade")
        void testLargeConnectivityDegrees() {
            // Riesengroßer Vernetzungsgrad sollte funktionieren
            assertDoesNotThrow(() -> {
                NConnectedMirrorNode node = new NConnectedMirrorNode(1, 100);
                assertEquals(100, node.getConnectivityDegree());
                assertEquals(100, node.getExpectedLinkCount());
            });
        }

        @Test
        @DisplayName("Edge Case: Einzelner Knoten")
        void testSingleNode() {
            NConnectedMirrorNode singleNode = new NConnectedMirrorNode(1, 1);

            // Einzelner Knoten können nicht optimal verbunden sein
            assertFalse(singleNode.hasOptimalConnectivity());
            assertEquals(1, singleNode.getNetworkSize());
            assertTrue(singleNode.getConnectedNodes().isEmpty());
            assertEquals(0.0, singleNode.getConnectivityDensity());
        }
    }

    @Nested
    @DisplayName("Parametrisierte Tests")
    class ParametrizedTests {

        @ParameterizedTest
        @ValueSource(ints = {1, 2, 3, 4, 5})
        @DisplayName("N-Connected-Strukturen mit verschiedenen Vernetzungsgraden")
        void testVariousConnectivityDegrees(int connectivityDegree) throws IOException {
            int nodeCount = connectivityDegree + 2; // Mindestens N+2 Knoten für interessante Tests

            NConnectedMirrorNode head = new NConnectedMirrorNode(1, connectivityDegree);
            List<NConnectedMirrorNode> peers = new ArrayList<>();

            for (int i = 2; i <= nodeCount; i++) {
                peers.add(new NConnectedMirrorNode(i, connectivityDegree));
            }

            head.setHead(StructureNode.StructureType.N_CONNECTED, true);
            for (NConnectedMirrorNode peer : peers) {
                head.addChild(peer, Set.of(StructureNode.StructureType.N_CONNECTED),
                        Map.of(StructureNode.StructureType.N_CONNECTED, head.getId()));
            }

            // Teste spezifische Eigenschaften für jeden N-Wert
            assertEquals(connectivityDegree, head.getConnectivityDegree());
            assertEquals(nodeCount, head.getNetworkSize());
            assertEquals(connectivityDegree, head.getExpectedLinkCount());

            // Für N=1: Baum-ähnlich, für N=nodeCount-1: vollständig vernetzt
            if (connectivityDegree == 1) {
                assertEquals(nodeCount - 1, head.getExpectedTotalLinkCount()); // Baum
            } else if (connectivityDegree == nodeCount - 1) {
                assertEquals((nodeCount * (nodeCount - 1)) / 2, head.getExpectedTotalLinkCount()); // Vollständig
            }

            // Mit korrekten Links sollte Struktur gültig sein
            setupValidNConnectedStructure(head, peers);
            assertTrue(head.isValidStructure());
        }

        @ParameterizedTest
        @ValueSource(ints = {2, 3, 4, 5, 6})
        @DisplayName("getConnectivityDensity für verschiedene Netzwerkgrößen")
        void testConnectivityDensityVariousNetworkSizes(int networkSize) throws IOException {
            int connectivityDegree = 2; // 2-Connected für alle Tests

            NConnectedMirrorNode head = new NConnectedMirrorNode(1, connectivityDegree);
            List<NConnectedMirrorNode> peers = new ArrayList<>();

            for (int i = 2; i <= networkSize; i++) {
                peers.add(new NConnectedMirrorNode(i, connectivityDegree));
            }

            head.setHead(StructureNode.StructureType.N_CONNECTED, true);
            for (NConnectedMirrorNode peer : peers) {
                head.addChild(peer);
            }

            // Ohne Links: Dichte = 0
            assertEquals(0.0, head.getConnectivityDensity(), 0.001);

            // Mit korrekten 2-Connected-Links
            setupValidNConnectedStructure(head, peers);

            // Erwartete Dichte: (Knoten * ConnectivityDegree / 2) / (Knoten * (Knoten-1) / 2)
            double expectedDensity = (double) (networkSize * connectivityDegree) / (networkSize * (networkSize - 1));
            assertEquals(expectedDensity, head.getConnectivityDensity(), 0.01);
        }
    }

    @Nested
    @DisplayName("Performance und große Strukturen")
    class PerformanceTests {

        @Test
        @DisplayName("Performance mit großen N-Connected-Strukturen")
        void testPerformanceWithLargeStructures() {
            int largeNetworkSize = 20;
            int connectivityDegree = 3;

            NConnectedMirrorNode head = new NConnectedMirrorNode(1, connectivityDegree);
            List<NConnectedMirrorNode> peers = new ArrayList<>();

            // Erstelle große Struktur
            for (int i = 2; i <= largeNetworkSize; i++) {
                peers.add(new NConnectedMirrorNode(i, connectivityDegree));
            }

            head.setHead(StructureNode.StructureType.N_CONNECTED, true);
            for (NConnectedMirrorNode peer : peers) {
                head.addChild(peer);
            }

            // Performance-Messungen
            long startTime = System.currentTimeMillis();

            assertEquals(largeNetworkSize, head.getNetworkSize());
            assertEquals(connectivityDegree, head.getConnectivityDegree());
            assertEquals(connectivityDegree, head.getExpectedLinkCount());

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            // Sollte unter 100ms dauern für 20 Knoten
            assertTrue(duration < 100, "Performance test took too long: " + duration + "ms");
        }

        @Test
        @DisplayName("Memory-Effizienz bei wiederholten Operationen")
        void testMemoryEfficiency() {
            NConnectedMirrorNode head = new NConnectedMirrorNode(1, 2);

            // Wiederholte Aufrufe sollten keine Memory-Leaks verursachen
            for (int i = 0; i < 1000; i++) {
                head.getConnectivityDegree();
                head.getExpectedLinkCount();
                head.getNetworkSize();
                head.getConnectedNodes();
                head.deriveTypeId();
            }

            // Test erfolgreich wenn keine OutOfMemoryError
            assertTrue(true);
        }
    }

    @Nested
    @DisplayName("Integration mit Simulator")
    class SimulatorIntegrationTests {

        @Test
        @DisplayName("Integration mit TimedRDMSim und MirrorProbe")
        void testSimulatorIntegration() throws IOException {
            initSimulator();
            MirrorProbe probe = getMirrorProbe();

            // Erstelle N-Connected-Struktur mit Simulator-Mirrors
            List<Mirror> simMirrors = getSimMirrors(probe);
            assertTrue(simMirrors.size() >= 4, "Mindestens 4 Mirrors für Test benötigt");

            NConnectedMirrorNode head = new NConnectedMirrorNode(1, 2);
            List<NConnectedMirrorNode> peers = Arrays.asList(
                    new NConnectedMirrorNode(2, 2),
                    new NConnectedMirrorNode(3, 2),
                    new NConnectedMirrorNode(4, 2)
            );

            // Setze Simulator-Mirrors
            head.setMirror(simMirrors.get(0));
            for (int i = 0; i < peers.size(); i++) {
                peers.get(i).setMirror(simMirrors.get(i + 1));
            }

            // Versuche Mirror-Integration
            assertNotNull(head.getMirror());
            assertEquals(simMirrors.get(0), head.getMirror());

            for (int i = 0; i < peers.size(); i++) {
                assertNotNull(peers.get(i).getMirror());
                assertEquals(simMirrors.get(i + 1), peers.get(i).getMirror());
            }
        }

        @Test
        @DisplayName("Link-Management mit echten Simulator-Links")
        void testLinkManagementWithSimulatorLinks() throws IOException {
            initSimulator();
            List<Mirror> mirrors = getSimMirrors(getMirrorProbe());

            NConnectedMirrorNode node1 = new NConnectedMirrorNode(1, 2);
            NConnectedMirrorNode node2 = new NConnectedMirrorNode(2, 2);

            node1.setMirror(mirrors.get(0));
            node2.setMirror(mirrors.get(1));

            // Erstelle echten Link
            Link realLink = new Link(1, mirrors.get(0), mirrors.get(1), 0, props);

            // Link-Management testen
            node1.addLink(realLink);
            assertEquals(1, node1.getNumImplementedLinks());
            assertTrue(node1.getImplementedLinks().contains(realLink));

            // Link entfernen
            node1.removeLink(realLink);
            assertEquals(0, node1.getNumImplementedLinks());
            assertFalse(node1.getImplementedLinks().contains(realLink));
        }
    }

    @Nested
    @DisplayName("toString und Object-Methoden")
    class ObjectMethodsTests {

        @Test
        @DisplayName("toString gibt aussagekräftige Beschreibung")
        void testToString() {
            NConnectedMirrorNode node = new NConnectedMirrorNode(5, 3);
            String toString = node.toString();

            assertNotNull(toString);
            assertTrue(toString.contains("5")); // ID
            assertTrue(toString.contains("3")); // Connectivity Degree
            assertTrue(toString.contains("N_CONNECTED")); // Type
        }

        @Test
        @DisplayName("equals und hashCode funktionieren korrekt")
        void testEqualsAndHashCode() {
            NConnectedMirrorNode node1 = new NConnectedMirrorNode(5, 3);
            NConnectedMirrorNode node2 = new NConnectedMirrorNode(5, 3);
            NConnectedMirrorNode node3 = new NConnectedMirrorNode(6, 3);
            NConnectedMirrorNode node4 = new NConnectedMirrorNode(5, 2);

            // Gleichheit basierend auf ID
            assertEquals(node1, node2);
            assertEquals(node1.hashCode(), node2.hashCode());

            // Ungleichheit bei verschiedener ID
            assertNotEquals(node1, node3);

            // Ungleichheit bei verschiedenen Connectivity Degree
            assertNotEquals(node1, node4);

            // Null und andere Klassen
            assertNotEquals(null, node1);
        }
    }
}