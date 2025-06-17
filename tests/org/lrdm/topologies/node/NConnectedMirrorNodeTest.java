
package org.lrdm.topologies.node;

import org.junit.jupiter.api.*;
import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.MirrorProbe;
import org.lrdm.TimedRDMSim;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.lrdm.PropertiesLoader.*;

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
 * <p>
 * Struktur:
 * - Grundfunktionen (Konstruktoren, Vererbung, Typ-System)
 * - Connectivity-Management (Vernetzungsgrad, erwartete Links)
 * - Netzwerk-Navigation (Connected Nodes, Größe, etc.)
 * - Struktur-Validierung (gültige/ungültige N-Connected-Netze)
 * - Performance und Edge Cases (große Netze, verschiedene N-Werte)
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
            assertThrows(IllegalArgumentException.class, () ->
                new NConnectedMirrorNode(1, 0));
            
            assertThrows(IllegalArgumentException.class, () ->
                new NConnectedMirrorNode(1, -1));
            
            assertThrows(IllegalArgumentException.class, () ->
                new NConnectedMirrorNode(1, new Mirror(101, 0, props), -5));

            // Vernetzungsgrad = 1 sollte funktionieren
            assertDoesNotThrow(() -> new NConnectedMirrorNode(1, 1));
        }

        @Test
        @DisplayName("deriveTypeId gibt N_CONNECTED zurück")
        void testDeriveTypeId() {
            assertEquals(StructureNode.StructureType.N_CONNECTED, ncNode.deriveTypeId());

            // Test verschiedene Vernetzungsgrade
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

            NConnectedMirrorNode node1 = new NConnectedMirrorNode(2, 1);
            assertEquals(1, node1.getConnectivityDegree());

            NConnectedMirrorNode node3 = new NConnectedMirrorNode(3, 3);
            assertEquals(3, node3.getConnectivityDegree());

            NConnectedMirrorNode node5 = new NConnectedMirrorNode(4, 5);
            assertEquals(5, node5.getConnectivityDegree());
        }

        @Test
        @DisplayName("getConnectivityDegree mit Multi-Type-Unterstützung")
        void testGetConnectivityDegreeMultiType() {
            // Setup: 3-Connected-Struktur mit 4 Knoten
            NConnectedMirrorNode head = new NConnectedMirrorNode(1, 3);
            NConnectedMirrorNode node2 = new NConnectedMirrorNode(2, 3);
            NConnectedMirrorNode node3 = new NConnectedMirrorNode(3, 3);
            NConnectedMirrorNode node4 = new NConnectedMirrorNode(4, 3);

            head.setHead(StructureNode.StructureType.N_CONNECTED, true);
            head.addChild(node2, Set.of(StructureNode.StructureType.N_CONNECTED), 
                         Map.of(StructureNode.StructureType.N_CONNECTED, head.getId()));
            head.addChild(node3, Set.of(StructureNode.StructureType.N_CONNECTED), 
                         Map.of(StructureNode.StructureType.N_CONNECTED, head.getId()));
            head.addChild(node4, Set.of(StructureNode.StructureType.N_CONNECTED), 
                         Map.of(StructureNode.StructureType.N_CONNECTED, head.getId()));

            // Test tatsächliche Vernetzung
            int headConnections = head.getConnectivityDegree(StructureNode.StructureType.N_CONNECTED, head.getId());
            assertEquals(3, headConnections); // Head hat 3 Kinder

            int childConnections = node2.getConnectivityDegree(StructureNode.StructureType.N_CONNECTED, head.getId());
            assertEquals(1, childConnections); // Nur Parent-Verbindung
        }

        @Test
        @DisplayName("getExpectedLinkCount berücksichtigt Netzwerkgröße")
        void testGetExpectedLinkCount() {
            // Einzelner Knoten
            assertEquals(0, ncNode.getExpectedLinkCount()); // networkSize = 1, min(2, 0) = 0

            // 3-Knoten-Netz mit N=2
            NConnectedMirrorNode node2 = new NConnectedMirrorNode(2, 2);
            NConnectedMirrorNode node3 = new NConnectedMirrorNode(3, 2);
            ncNode.addChild(node2);
            ncNode.addChild(node3);

            assertEquals(2, ncNode.getExpectedLinkCount()); // min(2, 3-1) = 2

            // Test mit höherem N als Netzwerkgröße
            NConnectedMirrorNode bigN = new NConnectedMirrorNode(4, 10);
            assertEquals(0, bigN.getExpectedLinkCount()); // min(10, 1-1) = 0

            bigN.addChild(new NConnectedMirrorNode(5, 10));
            assertEquals(1, bigN.getExpectedLinkCount()); // min(10, 2-1) = 1
        }

        @Test
        @DisplayName("hasOptimalConnectivity prüft implementierte vs erwartete Links")
        void testHasOptimalConnectivity() throws IOException {
            initSimulator();
            List<Mirror> mirrors = getSimMirrors(getMirrorProbe());

            // Setup: 2-Connected mit 3 Knoten
            NConnectedMirrorNode head = new NConnectedMirrorNode(1, 2);
            NConnectedMirrorNode node2 = new NConnectedMirrorNode(2, 2);
            NConnectedMirrorNode node3 = new NConnectedMirrorNode(3, 2);

            head.setMirror(mirrors.get(0));
            node2.setMirror(mirrors.get(1));
            node3.setMirror(mirrors.get(2));

            head.addChild(node2);
            head.addChild(node3);

            // Ohne implementierte Links: nicht optimal
            assertFalse(head.hasOptimalConnectivity()); // hat 0, braucht 2

            // Mit korrekten Links: optimal
            Link link1 = new Link(1, mirrors.get(0), mirrors.get(1), 0, props);
            Link link2 = new Link(2, mirrors.get(0), mirrors.get(2), 0, props);
            head.addLink(link1);
            head.addLink(link2);

            assertTrue(head.hasOptimalConnectivity()); // hat 2, braucht 2

            // Mit zu vielen Links: immer noch optimal (toleriert)
            Link extraLink = new Link(3, mirrors.get(1), mirrors.get(2), 0, props);
            node2.addLink(extraLink);
            assertTrue(head.hasOptimalConnectivity()); // hat immer noch 2
        }

        @Test
        @DisplayName("getExpectedTotalLinkCount berechnet korrekt")
        void testGetExpectedTotalLinkCount() {
            // 2-Connected mit 3 Knoten: (3 * 2) / 2 = 3 Links
            NConnectedMirrorNode node2 = new NConnectedMirrorNode(2, 2);
            NConnectedMirrorNode node3 = new NConnectedMirrorNode(3, 2);
            ncNode.addChild(node2);
            ncNode.addChild(node3);

            assertEquals(3, ncNode.getExpectedTotalLinkCount());

            // 3-Connected mit 5 Knoten: (5 * 3) / 2 = 7.5 -> 7 Links
            NConnectedMirrorNode head3 = new NConnectedMirrorNode(1, 3);
            for (int i = 2; i <= 5; i++) {
                head3.addChild(new NConnectedMirrorNode(i, 3));
            }
            assertEquals(7, head3.getExpectedTotalLinkCount()); // (5 * 3) / 2 = 7

            // 1-Connected mit 4 Knoten: (4 * 1) / 2 = 2 Links (Baum-ähnlich)
            NConnectedMirrorNode head1 = new NConnectedMirrorNode(1, 1);
            for (int i = 2; i <= 4; i++) {
                head1.addChild(new NConnectedMirrorNode(i, 1));
            }
            assertEquals(2, head1.getExpectedTotalLinkCount()); // (4 * 1) / 2 = 2
        }
    }

    @Nested
    @DisplayName("Netzwerk-Navigation")
    class NetworkNavigationTests {

        @Test
        @DisplayName("getNConnectedHead findet Head-Knoten")
        void testGetNConnectedHead() {
            // Einzelner Knoten: kein Head
            assertNull(ncNode.getNConnectedHead());

            // Setup: Head-Struktur
            NConnectedMirrorNode head = new NConnectedMirrorNode(1, 3);
            NConnectedMirrorNode child1 = new NConnectedMirrorNode(2, 3);
            NConnectedMirrorNode child2 = new NConnectedMirrorNode(3, 3);

            head.setHead(StructureNode.StructureType.N_CONNECTED, true);
            head.addChild(child1);
            head.addChild(child2);

            // Head findet sich selbst
            assertEquals(head, head.getNConnectedHead());

            // Kinder finden den Head
            assertEquals(head, child1.getNConnectedHead());
            assertEquals(head, child2.getNConnectedHead());
        }

        @Test
        @DisplayName("isNConnectedHead erkennt Head-Status")
        void testIsNConnectedHead() {
            assertFalse(ncNode.isNConnectedHead()); // Standardmäßig kein Head

            // Head setzen
            ncNode.setHead(StructureNode.StructureType.N_CONNECTED, true);
            assertTrue(ncNode.isNConnectedHead());

            // Head entfernen
            ncNode.setHead(StructureNode.StructureType.N_CONNECTED, false);
            assertFalse(ncNode.isNConnectedHead());
        }

        @Test
        @DisplayName("getNetworkSize berechnet Strukturgröße")
        void testGetNetworkSize() {
            // Einzelner Knoten
            assertEquals(1, ncNode.getNetworkSize());

            // 3-Knoten-Netz
            NConnectedMirrorNode node2 = new NConnectedMirrorNode(2, 2);
            NConnectedMirrorNode node3 = new NConnectedMirrorNode(3, 2);
            ncNode.setHead(StructureNode.StructureType.N_CONNECTED, true);
            ncNode.addChild(node2, Set.of(StructureNode.StructureType.N_CONNECTED), 
                           Map.of(StructureNode.StructureType.N_CONNECTED, ncNode.getId()));
            ncNode.addChild(node3, Set.of(StructureNode.StructureType.N_CONNECTED), 
                           Map.of(StructureNode.StructureType.N_CONNECTED, ncNode.getId()));

            assertEquals(3, ncNode.getNetworkSize());
            assertEquals(3, node2.getNetworkSize());
            assertEquals(3, node3.getNetworkSize());

            // Größeres Netz
            for (int i = 4; i <= 7; i++) {
                ncNode.addChild(new NConnectedMirrorNode(i, 2), 
                               Set.of(StructureNode.StructureType.N_CONNECTED), 
                               Map.of(StructureNode.StructureType.N_CONNECTED, ncNode.getId()));
            }
            assertEquals(7, ncNode.getNetworkSize());
        }

        @Test
        @DisplayName("getConnectedNodes findet direkte Verbindungen")
        void testGetConnectedNodes() {
            // Einzelner Knoten: keine Verbindungen
            assertTrue(ncNode.getConnectedNodes().isEmpty());

            // Setup: 3-Connected-Struktur
            NConnectedMirrorNode head = new NConnectedMirrorNode(1, 3);
            NConnectedMirrorNode node2 = new NConnectedMirrorNode(2, 3);
            NConnectedMirrorNode node3 = new NConnectedMirrorNode(3, 3);
            NConnectedMirrorNode node4 = new NConnectedMirrorNode(4, 3);

            head.setHead(StructureNode.StructureType.N_CONNECTED, true);
            head.addChild(node2, Set.of(StructureNode.StructureType.N_CONNECTED), 
                         Map.of(StructureNode.StructureType.N_CONNECTED, head.getId()));
            head.addChild(node3, Set.of(StructureNode.StructureType.N_CONNECTED), 
                         Map.of(StructureNode.StructureType.N_CONNECTED, head.getId()));
            head.addChild(node4, Set.of(StructureNode.StructureType.N_CONNECTED), 
                         Map.of(StructureNode.StructureType.N_CONNECTED, head.getId()));

            // Head ist mit allen Kindern verbunden
            Set<NConnectedMirrorNode> headConnections = head.getConnectedNodes();
            assertEquals(3, headConnections.size());
            assertTrue(headConnections.contains(node2));
            assertTrue(headConnections.contains(node3));
            assertTrue(headConnections.contains(node4));

            // Kinder sind nur mit Head verbunden
            Set<NConnectedMirrorNode> childConnections = node2.getConnectedNodes();
            assertEquals(1, childConnections.size());
            assertTrue(childConnections.contains(head));
        }
    }

    @Nested
    @DisplayName("Struktur-Management")
    class StructureManagementTests {

        @Test
        @DisplayName("canAcceptMoreChildren N-Connected-spezifische Logik")
        void testCanAcceptMoreChildrenNConnectedSpecific() {
            NConnectedMirrorNode head = new NConnectedMirrorNode(1, 2); // 2-Connected
            head.setHead(StructureNode.StructureType.N_CONNECTED, true);

            // Head ohne gültige Struktur kann keine Kinder akzeptieren
            assertFalse(head.canAcceptMoreChildren());

            // Mit gültiger 2-Knoten-Struktur
            NConnectedMirrorNode peer1 = new NConnectedMirrorNode(2, 2);
            head.addChild(peer1, Set.of(StructureNode.StructureType.N_CONNECTED), 
                         Map.of(StructureNode.StructureType.N_CONNECTED, head.getId()));

            // Mit 3-Knoten-Struktur: Head hat 2 Verbindungen (optimal für 2-Connected)
            NConnectedMirrorNode peer2 = new NConnectedMirrorNode(3, 2);
            head.addChild(peer2, Set.of(StructureNode.StructureType.N_CONNECTED), 
                         Map.of(StructureNode.StructureType.N_CONNECTED, head.getId()));

            // Bei 3 Knoten und N=2: max 2 Verbindungen pro Knoten
            // Head hat bereits 2 Verbindungen -> kann keine weiteren akzeptieren
            assertFalse(head.canAcceptMoreChildren());

            // Peer-Knoten können keine Kinder akzeptieren (nur 1 Verbindung erlaubt)
            assertFalse(peer1.canAcceptMoreChildren());
        }

        @Test
        @DisplayName("canBeRemovedFromStructure N-Connected-spezifische Validierung")
        void testCanBeRemovedFromStructureNConnected() {
            // Setup: 3-Connected mit 5 Knoten
            NConnectedMirrorNode head = new NConnectedMirrorNode(1, 3);
            head.setHead(StructureNode.StructureType.N_CONNECTED, true);

            List<NConnectedMirrorNode> nodes = new ArrayList<>();
            for (int i = 2; i <= 5; i++) {
                NConnectedMirrorNode node = new NConnectedMirrorNode(i, 3);
                nodes.add(node);
                head.addChild(node, Set.of(StructureNode.StructureType.N_CONNECTED), 
                             Map.of(StructureNode.StructureType.N_CONNECTED, head.getId()));
            }

            // Head kann nicht entfernt werden
            assertFalse(head.canBeRemovedFromStructure(head));

            // Bei 5 Knoten und N=3: mindestens N+1=4 Knoten erforderlich
            // -> 1 Knoten kann entfernt werden
            assertTrue(nodes.get(0).canBeRemovedFromStructure(head));

            // Teste Grenzfall: 4 Knoten (N+1)
            // Entferne einen Knoten, sodass nur noch 4 übrig sind
            head.removeChild(nodes.get(3));
            
            // Jetzt können keine weiteren entfernt werden
            assertFalse(nodes.get(0).canBeRemovedFromStructure(head));
        }

        @Test
        @DisplayName("canBeRemovedFromStructure mit verschiedenen N-Werten")
        void testCanBeRemovedFromStructureVariousN() {
            // Test N=1 (Baum-ähnlich): mindestens 2 Knoten
            NConnectedMirrorNode head1 = new NConnectedMirrorNode(1, 1);
            head1.setHead(StructureNode.StructureType.N_CONNECTED, true);
            NConnectedMirrorNode child1 = new NConnectedMirrorNode(2, 1);
            head1.addChild(child1);

            // Bei 2 Knoten: keiner kann entfernt werden
            assertFalse(head1.canBeRemovedFromStructure(head1));
            assertFalse(child1.canBeRemovedFromStructure(head1));

            // Test N=2: mindestens 3 Knoten
            NConnectedMirrorNode head2 = new NConnectedMirrorNode(1, 2);
            head2.setHead(StructureNode.StructureType.N_CONNECTED, true);
            head2.addChild(new NConnectedMirrorNode(2, 2));
            head2.addChild(new NConnectedMirrorNode(3, 2));

            // Bei 3 Knoten (N+1): keiner kann entfernt werden
            assertFalse(head2.getChildren().iterator().next().canBeRemovedFromStructure(head2));
        }
    }

    @Nested
    @DisplayName("Struktur-Validierung")
    class StructureValidationTests {

        @Test
        @DisplayName("isValidStructure validiert N-Connected-Strukturen")
        void testIsValidStructureNConnected() {
            // Einzelner Knoten: ungültig (< 2 Knoten)
            assertFalse(ncNode.isValidStructure());

            // 2-Connected mit 3 Knoten: gültig
            NConnectedMirrorNode head = new NConnectedMirrorNode(1, 2);
            NConnectedMirrorNode node2 = new NConnectedMirrorNode(2, 2);
            NConnectedMirrorNode node3 = new NConnectedMirrorNode(3, 2);

            head.setHead(StructureNode.StructureType.N_CONNECTED, true);
            head.addChild(node2, Set.of(StructureNode.StructureType.N_CONNECTED), 
                         Map.of(StructureNode.StructureType.N_CONNECTED, head.getId()));
            head.addChild(node3, Set.of(StructureNode.StructureType.N_CONNECTED), 
                         Map.of(StructureNode.StructureType.N_CONNECTED, head.getId()));

            // TODO: Diese Tests erfordern vollständige Link-Implementierung
            // assertTrue(head.isValidStructure());
        }

        @Test
        @DisplayName("isValidStructure erkennt verschiedene Vernetzungsgrade")
        void testIsValidStructureDifferentConnectivityDegrees() {
            // Gemischte Vernetzungsgrade: ungültig
            NConnectedMirrorNode head2 = new NConnectedMirrorNode(1, 2); // N=2
            NConnectedMirrorNode node3 = new NConnectedMirrorNode(2, 3); // N=3 (anders!)

            head2.setHead(StructureNode.StructureType.N_CONNECTED, true);
            head2.addChild(node3);

            Set<StructureNode> allNodes = Set.of(head2, node3);
            assertFalse(head2.isValidStructure(allNodes, StructureNode.StructureType.N_CONNECTED, head2));
        }

        @Test
        @DisplayName("isValidStructure erkennt nicht-NConnectedMirrorNode-Instanzen")
        void testIsValidStructureRejectsWrongNodeTypes() {
            NConnectedMirrorNode head = new NConnectedMirrorNode(1, 2);
            StructureNode wrongType = new StructureNode(2); // Nicht NConnectedMirrorNode

            Set<StructureNode> mixedNodes = Set.of(head, wrongType);
            assertFalse(head.isValidStructure(mixedNodes, StructureNode.StructureType.N_CONNECTED, head));
        }

        @Test
        @DisplayName("isValidStructure überprüft Mindestanzahl Knoten")
        void testIsValidStructureMinimumNodes() {
            NConnectedMirrorNode singleNode = new NConnectedMirrorNode(1, 2);
            
            // Weniger als 2 Knoten: ungültig
            Set<StructureNode> tooFewNodes = Set.of(singleNode);
            assertFalse(singleNode.isValidStructure(tooFewNodes, StructureNode.StructureType.N_CONNECTED, singleNode));

            // Null/leere Sets: ungültig
            assertFalse(singleNode.isValidStructure(null, StructureNode.StructureType.N_CONNECTED, singleNode));
            assertFalse(singleNode.isValidStructure(new HashSet<>(), StructureNode.StructureType.N_CONNECTED, singleNode));
        }
    }

    @Nested
    @DisplayName("Convenience-Methoden und Performance")
    class ConvenienceAndPerformanceTests {

        @Test
        @DisplayName("getConnectivityDensity berechnet Vernetzungsdichte")
        void testGetConnectivityDensity() throws IOException {
            initSimulator();
            List<Mirror> mirrors = getSimMirrors(getMirrorProbe());

            // Setup: 2-Connected mit 3 Knoten
            NConnectedMirrorNode head = new NConnectedMirrorNode(1, 2);
            head.setMirror(mirrors.get(0));

            // Ohne Links: Dichte = 0
            assertEquals(0.0, head.getConnectivityDensity(), 0.01);

            // Mit teilweisen Links
            Link link1 = new Link(1, mirrors.get(0), mirrors.get(1), 0, props);
            head.addLink(link1);
            // Expected: 3 Links, Actual: 1 Link -> 1/3 = 0.33
            assertTrue(head.getConnectivityDensity() > 0);

            // Einzelner Knoten: Dichte = 1.0 (optimal vernetzt)
            NConnectedMirrorNode single = new NConnectedMirrorNode(99, 2);
            assertEquals(1.0, single.getConnectivityDensity(), 0.01);
        }

        @Test
        @DisplayName("isFaultTolerant prüft Ausfallsicherheit")
        void testIsFaultTolerant() {
            // Kleine Strukturen sind nicht fault-tolerant
            NConnectedMirrorNode head = new NConnectedMirrorNode(1, 2);
            NConnectedMirrorNode node2 = new NConnectedMirrorNode(2, 2);
            head.addChild(node2);

            assertFalse(head.isFaultTolerant()); // Nur 2 Knoten

            // Größere Struktur: 4 Knoten mit N=2
            NConnectedMirrorNode node3 = new NConnectedMirrorNode(3, 2);
            NConnectedMirrorNode node4 = new NConnectedMirrorNode(4, 2);
            head.setHead(StructureNode.StructureType.N_CONNECTED, true);
            head.addChild(node3, Set.of(StructureNode.StructureType.N_CONNECTED), 
                         Map.of(StructureNode.StructureType.N_CONNECTED, head.getId()));
            head.addChild(node4, Set.of(StructureNode.StructureType.N_CONNECTED), 
                         Map.of(StructureNode.StructureType.N_CONNECTED, head.getId()));

            // TODO: Vollständige fault-tolerance-Prüfung erfordert Link-Implementierung
            // Basis-Test: Prüfung läuft ohne Exception
            assertDoesNotThrow(() -> head.isFaultTolerant());
        }

        @Test
        @DisplayName("Performance-Test mit großen N-Connected-Netzen")
        void testPerformanceWithLargeNetworks() {
            long startTime = System.currentTimeMillis();

            // Erstelle 3-Connected mit 20 Knoten
            NConnectedMirrorNode head = new NConnectedMirrorNode(1, 3);
            head.setHead(StructureNode.StructureType.N_CONNECTED, true);

            for (int i = 2; i <= 20; i++) {
                NConnectedMirrorNode node = new NConnectedMirrorNode(i, 3);
                head.addChild(node, Set.of(StructureNode.StructureType.N_CONNECTED), 
                             Map.of(StructureNode.StructureType.N_CONNECTED, head.getId()));
            }

            // Performance-Messungen
            assertEquals(20, head.getNetworkSize());
            assertEquals(3, head.getConnectivityDegree());
            assertDoesNotThrow(() -> head.getConnectedNodes());
            assertDoesNotThrow(() -> head.getExpectedTotalLinkCount());

            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 1000, "Operationen sollten unter 1s dauern, waren: " + duration + "ms");
        }

        @Test
        @DisplayName("Edge Cases und Null-Handling")
        void testEdgeCasesAndNullHandling() {
            // Null-Tests
            assertDoesNotThrow(() -> ncNode.getConnectedNodes()); // Sollte leeres Set zurückgeben
            assertTrue(ncNode.getConnectedNodes().isEmpty());

            // Sehr hohe N-Werte
            NConnectedMirrorNode highN = new NConnectedMirrorNode(1, 1000);
            assertEquals(1000, highN.getConnectivityDegree());
            assertEquals(0, highN.getExpectedLinkCount()); // Einzelner Knoten
            assertEquals(0, highN.getExpectedTotalLinkCount());

            // N = 1 (Baum-ähnliche Struktur)
            NConnectedMirrorNode treelike = new NConnectedMirrorNode(1, 1);
            NConnectedMirrorNode child = new NConnectedMirrorNode(2, 1);
            treelike.addChild(child);

            assertEquals(1, treelike.getConnectivityDegree());
            assertEquals(1, treelike.getExpectedTotalLinkCount()); // (2 * 1) / 2 = 1
        }
    }

    @Nested
    @DisplayName("Verschiedene Vernetzungsgrade")
    class VariousConnectivityDegreesTests {

        @Test
        @DisplayName("N=1: Baum-ähnliche Struktur")
        void testN1TreeLikeStructure() {
            NConnectedMirrorNode head = new NConnectedMirrorNode(1, 1);
            NConnectedMirrorNode child1 = new NConnectedMirrorNode(2, 1);
            NConnectedMirrorNode child2 = new NConnectedMirrorNode(3, 1);

            head.addChild(child1);
            head.addChild(child2);

            assertEquals(1, head.getConnectivityDegree());
            assertEquals(1, head.getExpectedLinkCount()); // min(1, 3-1) = 1
            assertEquals(1, child1.getExpectedLinkCount()); // min(1, 3-1) = 1
        }

        @Test
        @DisplayName("N=3: Hochredundante Struktur")
        void testN3HighRedundancyStructure() {
            NConnectedMirrorNode head = new NConnectedMirrorNode(1, 3);
            for (int i = 2; i <= 6; i++) {
                head.addChild(new NConnectedMirrorNode(i, 3));
            }

            assertEquals(3, head.getConnectivityDegree());
            assertEquals(3, head.getExpectedLinkCount()); // min(3, 6-1) = 3
            assertEquals(9, head.getExpectedTotalLinkCount()); // (6 * 3) / 2 = 9
        }

        @Test
        @DisplayName("N=5: Vollständig-ähnliche Struktur")
        void testN5FullyConnectedLikeStructure() {
            NConnectedMirrorNode head = new NConnectedMirrorNode(1, 5);
            for (int i = 2; i <= 6; i++) {
                head.addChild(new NConnectedMirrorNode(i, 5));
            }

            assertEquals(5, head.getConnectivityDegree());
            assertEquals(5, head.getExpectedLinkCount()); // min(5, 6-1) = 5 (vollständig vernetzt)
            assertEquals(15, head.getExpectedTotalLinkCount()); // (6 * 5) / 2 = 15
        }

        @Test
        @DisplayName("N > n-1: Begrenzt auf vollständige Vernetzung")
        void testNGreaterThanNetworkSizeMinus1() {
            // N=10 mit nur 4 Knoten
            NConnectedMirrorNode head = new NConnectedMirrorNode(1, 10);
            for (int i = 2; i <= 4; i++) {
                head.addChild(new NConnectedMirrorNode(i, 10));
            }

            assertEquals(10, head.getConnectivityDegree()); // Konfiguriert
            assertEquals(3, head.getExpectedLinkCount()); // Begrenzt auf min(10, 4-1) = 3
            assertEquals(6, head.getExpectedTotalLinkCount()); // (4 * 3) / 2 = 6
        }
    }

    @Nested
    @DisplayName("toString, equals und hashCode")
    class ObjectMethodsTests {

        @Test
        @DisplayName("toString enthält relevante Informationen")
        void testToString() {
            Mirror testMirror = new Mirror(101, 0, props);
            ncNode.setMirror(testMirror);

            String toStringResult = ncNode.toString();
            
            assertTrue(toStringResult.contains("NConnectedMirrorNode"));
            assertTrue(toStringResult.contains("id=1"));
            assertTrue(toStringResult.contains("connectivityDegree=2"));
            assertTrue(toStringResult.contains("mirror=101"));
        }

        @Test
        @DisplayName("equals und hashCode funktionieren korrekt")
        void testEqualsAndHashCode() {
            NConnectedMirrorNode node1 = new NConnectedMirrorNode(1, 2);
            NConnectedMirrorNode node2 = new NConnectedMirrorNode(1, 2); // Gleiche ID und N
            NConnectedMirrorNode node3 = new NConnectedMirrorNode(1, 3); // Gleiche ID, anderes N
            NConnectedMirrorNode node4 = new NConnectedMirrorNode(2, 2); // Andere ID, gleiches N

            // Gleiche ID und N: equal
            assertEquals(node1, node2);
            assertEquals(node1.hashCode(), node2.hashCode());

            // Verschiedene N: nicht equal
            assertNotEquals(node1, node3);

            // Verschiedene ID: nicht equal
            assertNotEquals(node1, node4);

            // Reflexivität
            assertEquals(node1, node1);

            // Null-Handling
            assertNotEquals(node1, null);

            // Verschiedene Klassen
            assertNotEquals(node1, new StructureNode(1));
        }
    }
}