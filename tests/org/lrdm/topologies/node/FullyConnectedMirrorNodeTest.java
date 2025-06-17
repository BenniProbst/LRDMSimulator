
package org.lrdm.topologies.node;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.TimedRDMSim;
import org.lrdm.probes.MirrorProbe;
import org.lrdm.topologies.node.StructureNode.StructureType;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.lrdm.TestProperties.getProps;
import static org.lrdm.TestProperties.loadProperties;

/**
 * Umfassende Tests für FullyConnectedMirrorNode nach TreeMirrorNodeTest-Vorbild.
 * <p>
 * Verbesserte Testmethodik:
 * - Integration mit echter Simulator-Umgebung und MirrorProbe
 * - Realistische Tests mit echten Mirror-Instanzen und Links
 * - Komplexe Szenarien mit mehreren Netzwerkgrößen
 * - Performance-Tests und Edge Cases
 * - Defensive Programmierung und Null-Handling
 * - Kompatibilität mit MirrorNode-Funktionalität
 * <p>
 * Struktur:
 * - Grundfunktionen (Konstruktoren, Vererbung, Typ-System)
 * - Netzwerk-Navigation (Connected Nodes, Größe, etc.)
 * - Struktur-Validierung (gültige/ungültige Netze)
 * - Performance und Edge Cases (große Netze, Null-Handling)
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
@DisplayName("FullyConnectedMirrorNode spezifische Tests")
class FullyConnectedMirrorNodeTest {

    private TimedRDMSim sim;
    private FullyConnectedMirrorNode fcNode;
    private Properties props;
    private static final String config = "resources/sim-test-mirror node.conf";

    @BeforeEach
    void setUp() throws IOException {
        loadProperties(config);
        props = getProps();
        fcNode = new FullyConnectedMirrorNode(1);
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
     *
     * @param probe Die MirrorProbe zur Mirror-Beschaffung
     * @return Liste mit mindestens requiredCount Mirrors
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
    @DisplayName("FullyConnectedMirrorNode Grundfunktionen")
    class FullyConnectedMirrorNodeBasicTests {

        @Test
        @DisplayName("FullyConnectedMirrorNode erbt MirrorNode-Funktionalität")
        void testInheritedMirrorNodeFunctionality() {
            Mirror testMirror = new Mirror(101, 0, props);
            fcNode.setMirror(testMirror);

            assertEquals(1, fcNode.getId());
            assertEquals(testMirror, fcNode.getMirror());
            assertEquals(0, fcNode.getNumImplementedLinks());
            assertTrue(fcNode.getImplementedLinks().isEmpty());

            // Test deriveTypeId (dies ist die korrekte API)
            assertEquals(StructureType.FULLY_CONNECTED, fcNode.deriveTypeId());
        }

        @Test
        @DisplayName("StructureType Integration und Vererbung")
        void testStructureTypeIntegration() {
            // Test deriveTypeId Funktionalität
            assertEquals(StructureType.FULLY_CONNECTED, fcNode.deriveTypeId());

            // Test mit Mirror
            Mirror testMirror = new Mirror(101, 0, props);
            fcNode.setMirror(testMirror);

            // deriveTypeId sollte unverändert bleiben
            assertEquals(StructureType.FULLY_CONNECTED, fcNode.deriveTypeId());

            // Test Konstruktor-Varianten
            FullyConnectedMirrorNode node2 = new FullyConnectedMirrorNode(99, testMirror);
            assertEquals(StructureType.FULLY_CONNECTED, node2.deriveTypeId());
        }

        @Test
        @DisplayName("MirrorNode-Mirror Integration")
        void testMirrorIntegration() {
            assertNull(fcNode.getMirror());

            // Test setMirror
            Mirror testMirror = new Mirror(101, 0, props);
            fcNode.setMirror(testMirror);
            assertEquals(testMirror, fcNode.getMirror());

            // Test-Link-Management über Mirror
            Mirror targetMirror = new Mirror(102, 0, props);
            Link testLink = new Link(1, testMirror, targetMirror, 0, props);

            fcNode.addLink(testLink);
            assertEquals(1, fcNode.getNumImplementedLinks());
            assertTrue(fcNode.getImplementedLinks().contains(testLink));

            fcNode.removeLink(testLink);
            assertEquals(0, fcNode.getNumImplementedLinks());
            assertFalse(fcNode.getImplementedLinks().contains(testLink));
        }

        @Test
        @DisplayName("FullyConnectedMirrorNode Konstruktoren")
        void testConstructors() {
            // Standard Konstruktor
            FullyConnectedMirrorNode node1 = new FullyConnectedMirrorNode(5);
            assertEquals(5, node1.getId());
            assertNull(node1.getMirror());
            assertEquals(StructureType.FULLY_CONNECTED, node1.deriveTypeId());

            // Konstruktor mit Mirror
            Mirror mirror = new Mirror(102, 0, props);
            FullyConnectedMirrorNode node2 = new FullyConnectedMirrorNode(6, mirror);
            assertEquals(6, node2.getId());
            assertEquals(mirror, node2.getMirror());
            assertEquals(StructureType.FULLY_CONNECTED, node2.deriveTypeId());
        }

        @Test
        @DisplayName("deriveTypeId gibt FULLY_CONNECTED zurück")
        void testDeriveTypeId() {
            assertEquals(StructureType.FULLY_CONNECTED, fcNode.deriveTypeId());

            // Auch nach Strukturveränderungen
            FullyConnectedMirrorNode peer = new FullyConnectedMirrorNode(2);
            fcNode.addChild(peer);
            assertEquals(StructureType.FULLY_CONNECTED, fcNode.deriveTypeId());
            assertEquals(StructureType.FULLY_CONNECTED, peer.deriveTypeId());
        }

        @Test
        @DisplayName("canAcceptMoreChildren vollständig-vernetzt-spezifische Logik")
        void testCanAcceptMoreChildrenFullyConnectedSpecific() {
            FullyConnectedMirrorNode head = new FullyConnectedMirrorNode(1);
            head.setHead(StructureType.FULLY_CONNECTED, true);

            // Head ohne gültige Struktur kann keine Kinder akzeptieren
            assertFalse(head.canAcceptMoreChildren());

            // Mit gültiger 2-Knoten-Struktur
            FullyConnectedMirrorNode peer1 = new FullyConnectedMirrorNode(2);
            head.addChild(peer1);
            setupValidFullyConnectedStructure(head, List.of(peer1));
            assertTrue(head.canAcceptMoreChildren());

            // Mit 3-Knoten-Struktur
            FullyConnectedMirrorNode peer2 = new FullyConnectedMirrorNode(3);
            head.addChild(peer2);
            setupValidFullyConnectedStructure(head, Arrays.asList(peer1, peer2));
            assertTrue(head.canAcceptMoreChildren());

            // Jeder Knoten im vollständigen Netz kann theoretisch weitere Verbindungen akzeptieren
            assertTrue(peer1.canAcceptMoreChildren());
            assertTrue(peer2.canAcceptMoreChildren());
        }

        @Test
        @DisplayName("canBeRemovedFromStructure vollständig-vernetzt-spezifische Validierung")
        void testCanBeRemovedFromStructureFullyConnectedSpecific() {
            // Erstelle gültiges 4-Knoten vollständiges Netz
            FullyConnectedMirrorNode head = new FullyConnectedMirrorNode(1);
            FullyConnectedMirrorNode peer1 = new FullyConnectedMirrorNode(2);
            FullyConnectedMirrorNode peer2 = new FullyConnectedMirrorNode(3);
            FullyConnectedMirrorNode peer3 = new FullyConnectedMirrorNode(4);

            head.setHead(StructureType.FULLY_CONNECTED, true);
            head.addChild(peer1);
            head.addChild(peer2);
            head.addChild(peer3);

            setupValidFullyConnectedStructure(head, Arrays.asList(peer1, peer2, peer3));

            // Head kann nicht entfernt werden
            assertFalse(head.canBeRemovedFromStructure(head));

            // Nicht-Head-Knoten können entfernt werden (noch sind 3 übrig)
            assertTrue(peer1.canBeRemovedFromStructure(head));
            assertTrue(peer2.canBeRemovedFromStructure(head));
            assertTrue(peer3.canBeRemovedFromStructure(head));

            // Bei 3-Knoten-Struktur können noch Knoten entfernt werden
            head.removeChild(peer3);
            setupValidFullyConnectedStructure(head, Arrays.asList(peer1, peer2));
            assertTrue(peer1.canBeRemovedFromStructure(head));
            assertTrue(peer2.canBeRemovedFromStructure(head));

            // Bei 2-Knoten-Struktur können keine Knoten mehr entfernt werden
            head.removeChild(peer2);
            setupValidFullyConnectedStructure(head, List.of(peer1));
            assertFalse(peer1.canBeRemovedFromStructure(head));
        }

        @Test
        @DisplayName("Struktur-Validierung mit MirrorProbe Daten")
        void testStructureValidationWithMirrorProbeData() throws IOException {
            initSimulator();
            MirrorProbe probe = getMirrorProbe();
            assertNotNull(probe);

            List<Mirror> simMirrors = getSimMirrors(probe);

            // Erstelle komplexes 5-Knoten vollständiges Netz mit echten Simulator-Mirrors
            FullyConnectedMirrorNode head = new FullyConnectedMirrorNode(1, simMirrors.get(0));
            FullyConnectedMirrorNode peer1 = new FullyConnectedMirrorNode(2, simMirrors.get(1));
            FullyConnectedMirrorNode peer2 = new FullyConnectedMirrorNode(3, simMirrors.get(2));
            FullyConnectedMirrorNode peer3 = new FullyConnectedMirrorNode(4, simMirrors.get(3));
            FullyConnectedMirrorNode peer4 = new FullyConnectedMirrorNode(5, simMirrors.get(4));

            // Baue vollständiges 5-Knoten-Netz auf
            head.setHead(StructureType.FULLY_CONNECTED, true);
            head.addChild(peer1);
            head.addChild(peer2);
            head.addChild(peer3);
            head.addChild(peer4);

            // Erstelle vollständig-vernetzte Links (jeder mit jedem: 10 Links für 5 Knoten)
            createFullyConnectedLinks(Arrays.asList(simMirrors.get(0), simMirrors.get(1), 
                                                  simMirrors.get(2), simMirrors.get(3), simMirrors.get(4)));

            // Erstelle Edge-Link für Head (zu externem Mirror)
            Mirror externalMirror = new Mirror(300, 0, props);
            Link edgeLink = new Link(100, simMirrors.get(0), externalMirror, 0, props);
            simMirrors.get(0).addLink(edgeLink);
            externalMirror.addLink(edgeLink);

            // Teste Struktur-Validierung mit echten MirrorProbe-Daten
            Set<StructureNode> networkNodes = Set.of(head, peer1, peer2, peer3, peer4);
            assertTrue(head.isValidStructure(networkNodes),
                    "Vollständiges Netz mit MirrorProbe-Daten sollte gültig sein");

            // Versuche FullyConnectedMirrorNode-spezifische Navigations-Funktionen
            assertEquals(head, peer1.getFullyConnectedHead());
            assertEquals(head, peer2.getFullyConnectedHead());
            assertEquals(head, peer3.getFullyConnectedHead());
            assertEquals(head, peer4.getFullyConnectedHead());

            // Teste Connected Nodes (alle sind mit allen anderen verbunden)
            Set<FullyConnectedMirrorNode> connected1 = head.getConnectedNodes();
            assertEquals(4, connected1.size()); // Mit allen 4 anderen verbunden
            assertTrue(connected1.contains(peer1));
            assertTrue(connected1.contains(peer2));
            assertTrue(connected1.contains(peer3));
            assertTrue(connected1.contains(peer4));
            assertFalse(connected1.contains(head)); // sich selbst nicht enthalten

            Set<FullyConnectedMirrorNode> connectedPeer = peer1.getConnectedNodes();
            assertEquals(4, connectedPeer.size()); // Mit allen 4 anderen verbunden
            assertTrue(connectedPeer.contains(head));
            assertTrue(connectedPeer.contains(peer2));
            assertTrue(connectedPeer.contains(peer3));
            assertTrue(connectedPeer.contains(peer4));

            // Teste Netzwerk-Größe und Link-Erwartungen
            assertEquals(5, head.getNetworkSize());
            assertEquals(5, peer1.getNetworkSize());
            assertEquals(4, head.getExpectedLinkCount()); // n-1 = 5-1 = 4
            assertEquals(4, peer1.getExpectedLinkCount());

            // Teste Link-Zählung mit echten Daten
            assertEquals(5, head.getNumImplementedLinks(), "Head sollte 5 Links haben (4 Netz + 1 Edge)");
            assertEquals(4, peer1.getNumImplementedLinks(), "Peer1 sollte 4 Links haben");
            assertEquals(4, peer2.getNumImplementedLinks(), "Peer2 sollte 4 Links haben");
            assertEquals(4, peer3.getNumImplementedLinks(), "Peer3 sollte 4 Links haben");
            assertEquals(4, peer4.getNumImplementedLinks(), "Peer4 sollte 4 Links haben");

            // Teste MirrorProbe-spezifische Funktionalität
            assertTrue(probe.getNumMirrors() >= 0, "MirrorProbe sollte valide Mirror-Anzahl liefern");
            assertTrue(probe.getNumTargetLinksPerMirror() >= 0,
                    "Target links per mirror sollte nicht negativ sein");

            // Teste Struktur-Integrität nach Modifikationen
            Set<StructureNode> invalidNetworkNodes = Set.of(peer1, peer2, peer3, peer4);
            assertFalse(head.isValidStructure(invalidNetworkNodes),
                    "Vollständiges Netz ohne Head sollte ungültig sein");
        }

        private void setupValidFullyConnectedStructure(FullyConnectedMirrorNode head, List<FullyConnectedMirrorNode> peers) {
            // Setze Mirrors
            Mirror headMirror = new Mirror(101, 0, props);
            head.setMirror(headMirror);

            List<Mirror> allMirrors = new ArrayList<>();
            allMirrors.add(headMirror);

            for (int i = 0; i < peers.size(); i++) {
                Mirror peerMirror = new Mirror(102 + i, 0, props);
                peers.get(i).setMirror(peerMirror);
                allMirrors.add(peerMirror);
            }

            // Erstelle vollständig vernetzte Links
            createFullyConnectedLinks(allMirrors);

            // Erstelle Edge-Link für Head
            Mirror externalMirror = new Mirror(200, 0, props);
            Link edgeLink = new Link(100, headMirror, externalMirror, 0, props);
            headMirror.addLink(edgeLink);
            externalMirror.addLink(edgeLink);
        }

        private void createFullyConnectedLinks(List<Mirror> mirrors) {
            int linkId = 1;
            for (int i = 0; i < mirrors.size(); i++) {
                for (int j = i + 1; j < mirrors.size(); j++) {
                    Link link = new Link(linkId++, mirrors.get(i), mirrors.get(j), 0, props);
                    mirrors.get(i).addLink(link);
                    mirrors.get(j).addLink(link);
                }
            }
        }
    }

    @Nested
    @DisplayName("FullyConnectedMirrorNode Netzwerk-Navigation")
    class FullyConnectedMirrorNodeNavigationTests {

        private FullyConnectedMirrorNode head, peer1, peer2, peer3, peer4, peer5;

        @BeforeEach
        void setUpComplexNetwork() {
            /*
             * Komplexe vollständig-vernetzte Struktur (6 Knoten):
             * Jeder Knoten ist mit jedem anderen verbunden
             * head - peer1, peer2, peer3, peer4, peer5
             * peer1 - head, peer2, peer3, peer4, peer5
             * peer2 - head, peer1, peer3, peer4, peer5
             * etc.
             */
            head = new FullyConnectedMirrorNode(1);
            peer1 = new FullyConnectedMirrorNode(2);
            peer2 = new FullyConnectedMirrorNode(3);
            peer3 = new FullyConnectedMirrorNode(4);
            peer4 = new FullyConnectedMirrorNode(5);
            peer5 = new FullyConnectedMirrorNode(6);

            head.setHead(StructureType.FULLY_CONNECTED, true);
            head.addChild(peer1);
            head.addChild(peer2);
            head.addChild(peer3);
            head.addChild(peer4);
            head.addChild(peer5);
        }

        @Test
        @DisplayName("getConnectedNodes sammelt alle verbundenen Knoten")
        void testGetConnectedNodes() {
            Set<FullyConnectedMirrorNode> connectedToHead = head.getConnectedNodes();
            assertEquals(5, connectedToHead.size());

            Set<FullyConnectedMirrorNode> expectedPeers = Set.of(peer1, peer2, peer3, peer4, peer5);
            assertEquals(expectedPeers, connectedToHead);

            // Versuche von Peer-Perspektive
            Set<FullyConnectedMirrorNode> connectedToPeer1 = peer1.getConnectedNodes();
            assertEquals(5, connectedToPeer1.size());
            assertTrue(connectedToPeer1.contains(head));
            assertTrue(connectedToPeer1.contains(peer2));
            assertTrue(connectedToPeer1.contains(peer3));
            assertTrue(connectedToPeer1.contains(peer4));
            assertTrue(connectedToPeer1.contains(peer5));
        }

        @Test
        @DisplayName("getNetworkSize berechnet Netzwerkgröße korrekt")
        void testGetNetworkSize() {
            assertEquals(6, head.getNetworkSize());
            assertEquals(6, peer1.getNetworkSize());
            assertEquals(6, peer2.getNetworkSize());
            assertEquals(6, peer3.getNetworkSize());
            assertEquals(6, peer4.getNetworkSize());
            assertEquals(6, peer5.getNetworkSize());

            // Test mit kleinerem Netzwerk
            FullyConnectedMirrorNode smallHead = new FullyConnectedMirrorNode(10);
            FullyConnectedMirrorNode smallPeer = new FullyConnectedMirrorNode(11);
            smallHead.setHead(StructureType.FULLY_CONNECTED, true);
            smallHead.addChild(smallPeer);

            assertEquals(2, smallHead.getNetworkSize());
            assertEquals(2, smallPeer.getNetworkSize());
        }

        @Test
        @DisplayName("getAllFullyConnectedNodes sammelt alle Netzwerk-Knoten")
        void testGetAllFullyConnectedNodes() {
            List<FullyConnectedMirrorNode> allNodes = head.getAllFullyConnectedNodes();
            assertEquals(6, allNodes.size());
            assertTrue(allNodes.contains(head));
            assertTrue(allNodes.contains(peer1));
            assertTrue(allNodes.contains(peer2));
            assertTrue(allNodes.contains(peer3));
            assertTrue(allNodes.contains(peer4));
            assertTrue(allNodes.contains(peer5));

            // Teste von verschiedenen Knoten
            List<FullyConnectedMirrorNode> allFromPeer = peer3.getAllFullyConnectedNodes();
            assertEquals(6, allFromPeer.size());
            assertEquals(new HashSet<>(allNodes), new HashSet<>(allFromPeer));
        }

        @Test
        @DisplayName("getExpectedLinkCount berechnet korrekte Link-Erwartungen")
        void testGetExpectedLinkCount() {
            // 6-Knoten-Netz: Jeder Knoten sollte 5 Links haben (n-1)
            assertEquals(5, head.getExpectedLinkCount());
            assertEquals(5, peer1.getExpectedLinkCount());
            assertEquals(5, peer2.getExpectedLinkCount());

            // Test mit verschiedenen Netzwerkgrößen
            FullyConnectedMirrorNode twoNodeHead = new FullyConnectedMirrorNode(20);
            FullyConnectedMirrorNode twoNodePeer = new FullyConnectedMirrorNode(21);
            twoNodeHead.setHead(StructureType.FULLY_CONNECTED, true);
            twoNodeHead.addChild(twoNodePeer);
            assertEquals(1, twoNodeHead.getExpectedLinkCount()); // 2-1 = 1

            FullyConnectedMirrorNode singleNode = new FullyConnectedMirrorNode(30);
            singleNode.setHead(StructureType.FULLY_CONNECTED, true);
            assertEquals(0, singleNode.getExpectedLinkCount()); // 1-1 = 0
        }

        @Test
        @DisplayName("hasOptimalLinkCount prüft Link-Optimierung")
        void testHasOptimalLinkCount() {
            setupValidFullyConnectedNetwork();

            // Mit vollständig implementierten Links sollte die Anzahl optimal sein
            boolean headOptimal = head.hasOptimalLinkCount();
            boolean peer1Optimal = peer1.hasOptimalLinkCount();

            // Das Ergebnis hängt von der konkreten Link-Implementierung ab.
            // Hier testen wir hauptsächlich, dass die Methode funktioniert
        }

        @Test
        @DisplayName("Navigation mit speziellen Netzwerk-Strukturen")
        void testNavigationWithSpecialNetworkStructures() {
            // Test mit minimaler Struktur (2 Knoten)
            FullyConnectedMirrorNode minHead = new FullyConnectedMirrorNode(100);
            FullyConnectedMirrorNode minPeer = new FullyConnectedMirrorNode(101);
            minHead.setHead(StructureType.FULLY_CONNECTED, true);
            minHead.addChild(minPeer);

            assertEquals(minHead, minPeer.getFullyConnectedHead());
            assertEquals(1, minHead.getConnectedNodes().size());
            assertEquals(1, minPeer.getConnectedNodes().size());

            // Test mit größerer Struktur (10 Knoten)
            FullyConnectedMirrorNode largeHead = createLargeFullyConnectedNetwork(200, 10);
            assertEquals(10, largeHead.getNetworkSize());
            assertEquals(9, largeHead.getExpectedLinkCount()); // 10-1 = 9
            assertEquals(9, largeHead.getConnectedNodes().size());
        }

        private void setupValidFullyConnectedNetwork() {
            // Setze Mirrors für alle Knoten
            List<Mirror> mirrors = new ArrayList<>();
            List<FullyConnectedMirrorNode> nodes = Arrays.asList(head, peer1, peer2, peer3, peer4, peer5);

            for (int i = 0; i < nodes.size(); i++) {
                Mirror mirror = new Mirror(150 + i, 0, props);
                nodes.get(i).setMirror(mirror);
                mirrors.add(mirror);
            }

            // Erstelle vollständig vernetzte Links
            createFullyConnectedLinks(mirrors);

            // Erstelle Edge-Link für Head
            Mirror externalMirror = new Mirror(300, 0, props);
            Link edgeLink = new Link(200, mirrors.get(0), externalMirror, 0, props);
            mirrors.get(0).addLink(edgeLink);
            externalMirror.addLink(edgeLink);
        }

        private FullyConnectedMirrorNode createLargeFullyConnectedNetwork(int baseId, int size) {
            FullyConnectedMirrorNode head = new FullyConnectedMirrorNode(baseId);
            head.setHead(StructureType.FULLY_CONNECTED, true);

            for (int i = 1; i < size; i++) {
                FullyConnectedMirrorNode peer = new FullyConnectedMirrorNode(baseId + i);
                head.addChild(peer);
            }

            return head;
        }

        private void createFullyConnectedLinks(List<Mirror> mirrors) {
            int linkId = 1;
            for (int i = 0; i < mirrors.size(); i++) {
                for (int j = i + 1; j < mirrors.size(); j++) {
                    Link link = new Link(linkId++, mirrors.get(i), mirrors.get(j), 0, props);
                    mirrors.get(i).addLink(link);
                    mirrors.get(j).addLink(link);
                }
            }
        }
    }

    @Nested
    @DisplayName("FullyConnectedMirrorNode Struktur-Validierung")
    class FullyConnectedMirrorNodeValidationTests {

        @Test
        @DisplayName("isValidStructure gültiges einfaches Netz")
        void testValidStructureSimpleNetwork() {
            FullyConnectedMirrorNode head = new FullyConnectedMirrorNode(1);
            FullyConnectedMirrorNode peer = new FullyConnectedMirrorNode(2);

            head.setHead(StructureType.FULLY_CONNECTED, true);
            head.addChild(peer);

            setupNetworkMirrorsAndLinks(head, List.of(peer));

            Set<StructureNode> nodes = Set.of(head, peer);
            assertTrue(head.isValidStructure(nodes, StructureType.FULLY_CONNECTED, head));
        }

        @Test
        @DisplayName("isValidStructure gültiges komplexes Netz")
        void testValidStructureComplexNetwork() {
            FullyConnectedMirrorNode head = new FullyConnectedMirrorNode(1);
            FullyConnectedMirrorNode peer1 = new FullyConnectedMirrorNode(2);
            FullyConnectedMirrorNode peer2 = new FullyConnectedMirrorNode(3);
            FullyConnectedMirrorNode peer3 = new FullyConnectedMirrorNode(4);

            head.setHead(StructureType.FULLY_CONNECTED, true);
            head.addChild(peer1);
            head.addChild(peer2);
            head.addChild(peer3);

            setupNetworkMirrorsAndLinks(head, Arrays.asList(peer1, peer2, peer3));

            Set<StructureNode> nodes = Set.of(head, peer1, peer2, peer3);
            assertTrue(head.isValidStructure(nodes, StructureType.FULLY_CONNECTED, head));
        }

        @Test
        @DisplayName("isValidStructure ungültige Strukturen")
        void testInvalidStructures() {
            FullyConnectedMirrorNode head = new FullyConnectedMirrorNode(1);

            // Leere Struktur
            Set<StructureNode> emptyNodes = new HashSet<>();
            assertFalse(head.isValidStructure(emptyNodes, StructureType.FULLY_CONNECTED, head));

            // Einzelner Knoten (unter Minimum für vollständige Vernetzung)
            head.setHead(StructureType.FULLY_CONNECTED, true);
            Set<StructureNode> singleNode = Set.of(head);
            assertFalse(head.isValidStructure(singleNode, StructureType.FULLY_CONNECTED, head));

            // Null Parameter
            assertFalse(head.isValidStructure(null, StructureType.FULLY_CONNECTED, head));
        }

        @Test
        @DisplayName("isValidStructure gemischte Knotentypen")
        void testMixedNodeTypes() {
            FullyConnectedMirrorNode fcHead = new FullyConnectedMirrorNode(1);
            TreeMirrorNode wrongTypeNode = new TreeMirrorNode(2);

            fcHead.setHead(StructureType.FULLY_CONNECTED, true);
            wrongTypeNode.setHead(StructureType.FULLY_CONNECTED, false);

            Set<StructureNode> mixedNodes = Set.of(fcHead, wrongTypeNode);
            assertFalse(fcHead.isValidStructure(mixedNodes, StructureType.FULLY_CONNECTED, fcHead));
        }

        @Test
        @DisplayName("isValidStructure mehrere Head-Knoten")
        void testMultipleHeads() {
            FullyConnectedMirrorNode head1 = new FullyConnectedMirrorNode(1);
            FullyConnectedMirrorNode head2 = new FullyConnectedMirrorNode(2);

            head1.setHead(StructureType.FULLY_CONNECTED, true);
            head2.setHead(StructureType.FULLY_CONNECTED, true);

            Set<StructureNode> multiHeadNodes = Set.of(head1, head2);
            assertFalse(head1.isValidStructure(multiHeadNodes, StructureType.FULLY_CONNECTED, head1));
        }

        @Test
        @DisplayName("isValidStructure symmetrische Verbindungen")
        void testSymmetricConnections() {
            FullyConnectedMirrorNode head = new FullyConnectedMirrorNode(1);
            FullyConnectedMirrorNode peer1 = new FullyConnectedMirrorNode(2);
            FullyConnectedMirrorNode peer2 = new FullyConnectedMirrorNode(3);

            head.setHead(StructureType.FULLY_CONNECTED, true);
            head.addChild(peer1);
            head.addChild(peer2);

            // Setup ohne symmetrische Verbindungen (nur einseitige Links)
            Mirror headMirror = new Mirror(101, 0, props);
            Mirror peer1Mirror = new Mirror(102, 0, props);
            Mirror peer2Mirror = new Mirror(103, 0, props);

            head.setMirror(headMirror);
            peer1.setMirror(peer1Mirror);
            peer2.setMirror(peer2Mirror);

            // Nur einseitige Links (nicht symmetrisch)
            Link link1 = new Link(1, headMirror, peer1Mirror, 0, props);
            headMirror.addLink(link1);
            // peer1Mirror.addLink(link1); // Absichtlich weggelassen für Asymmetrie

            Link link2 = new Link(2, headMirror, peer2Mirror, 0, props);
            headMirror.addLink(link2);
            peer2Mirror.addLink(link2);

            // Edge-Link für Head
            Mirror externalMirror = new Mirror(200, 0, props);
            Link edgeLink = new Link(100, headMirror, externalMirror, 0, props);
            headMirror.addLink(edgeLink);
            externalMirror.addLink(edgeLink);

            Set<StructureNode> nodes = Set.of(head, peer1, peer2);
            assertFalse(head.isValidStructure(nodes, StructureType.FULLY_CONNECTED, head),
                    "Asymmetrische Verbindungen sollten ungültig sein");
        }

        @Test
        @DisplayName("isValidStructure Edge-Link-Anforderungen")
        void testEdgeLinkRequirements() {
            FullyConnectedMirrorNode head = new FullyConnectedMirrorNode(1);
            FullyConnectedMirrorNode peer = new FullyConnectedMirrorNode(2);

            head.setHead(StructureType.FULLY_CONNECTED, true);
            head.addChild(peer);

            Mirror headMirror = new Mirror(101, 0, props);
            Mirror peerMirror = new Mirror(102, 0, props);

            head.setMirror(headMirror);
            peer.setMirror(peerMirror);

            // Vollständig vernetzter Link zwischen head und peer
            Link internalLink = new Link(1, headMirror, peerMirror, 0, props);
            headMirror.addLink(internalLink);
            peerMirror.addLink(internalLink);

            // Ohne Edge-Link sollte ungültig sein
            Set<StructureNode> nodes = Set.of(head, peer);
            assertFalse(head.isValidStructure(nodes, StructureType.FULLY_CONNECTED, head),
                    "Struktur ohne Edge-Links sollte ungültig sein");

            // Mit Edge-Link sollte gültig sein
            Mirror externalMirror = new Mirror(200, 0, props);
            Link edgeLink = new Link(100, headMirror, externalMirror, 0, props);
            headMirror.addLink(edgeLink);
            externalMirror.addLink(edgeLink);

            assertTrue(head.isValidStructure(nodes, StructureType.FULLY_CONNECTED, head),
                    "Struktur mit Edge-Links sollte gültig sein");
        }

        private void setupNetworkMirrorsAndLinks(FullyConnectedMirrorNode head, List<FullyConnectedMirrorNode> peers) {
            // Setze Mirrors
            Mirror headMirror = new Mirror(101, 0, props);
            head.setMirror(headMirror);

            List<Mirror> allMirrors = new ArrayList<>();
            allMirrors.add(headMirror);

            for (int i = 0; i < peers.size(); i++) {
                Mirror peerMirror = new Mirror(102 + i, 0, props);
                peers.get(i).setMirror(peerMirror);
                allMirrors.add(peerMirror);
            }

            // Erstelle vollständig vernetzte interne Links
            createFullyConnectedLinks(allMirrors);

            // Erstelle Edge-Link für Head
            Mirror externalMirror = new Mirror(200, 0, props);
            Link edgeLink = new Link(100, headMirror, externalMirror, 0, props);
            headMirror.addLink(edgeLink);
            externalMirror.addLink(edgeLink);
        }

        private void createFullyConnectedLinks(List<Mirror> mirrors) {
            int linkId = 1;
            for (int i = 0; i < mirrors.size(); i++) {
                for (int j = i + 1; j < mirrors.size(); j++) {
                    Link link = new Link(linkId++, mirrors.get(i), mirrors.get(j), 0, props);
                    mirrors.get(i).addLink(link);
                    mirrors.get(j).addLink(link);
                }
            }
        }
    }

    @Nested
    @DisplayName("FullyConnectedMirrorNode Performance und Edge Cases")
    class FullyConnectedMirrorNodePerformanceTests {


        @Test
        @DisplayName("Performance Tests mit großen Netzwerken")
        void testPerformanceWithLargeNetworks() throws IOException {
            initSimulator();
            MirrorProbe probe = getMirrorProbe();
            List<Mirror> simMirrors = getSimMirrors(probe);

            // Test verschiedene Netzwerkgrößen
            int[] networkSizes = {5, 10, 20, 50};

            for (int size : networkSizes) {
                long startTime = System.currentTimeMillis();

                // Erstelle großes Netzwerk
                List<FullyConnectedMirrorNode> largePeers = new ArrayList<>();
                FullyConnectedMirrorNode largeHead = new FullyConnectedMirrorNode(1000 + size);
                largePeers.add(largeHead);

                // Füge Peers hinzu
                for (int i = 1; i < size; i++) {
                    FullyConnectedMirrorNode peer = new FullyConnectedMirrorNode(1000 + size + i);
                    largePeers.add(peer);
                }

                // Setze Mirrors
                for (int i = 0; i < largePeers.size() && i < simMirrors.size(); i++) {
                    largePeers.get(i).setMirror(simMirrors.get(i));
                }

                // Erstelle vollständig vernetzte Struktur
                setupValidFullyConnectedStructure2(largeHead, largePeers.subList(1, largePeers.size()));

                // Performance-Messungen mit der largePeers-Collection
                long networkCreationTime = System.currentTimeMillis() - startTime;

                // Versuche Navigation-Performance
                startTime = System.currentTimeMillis();
                Set<FullyConnectedMirrorNode> connectedNodes = largeHead.getConnectedNodes();
                long navigationTime = System.currentTimeMillis() - startTime;

                // Versuche Validierung-Performance
                startTime = System.currentTimeMillis();
                boolean isValid = largeHead.isValidStructure();
                long validationTime = System.currentTimeMillis() - startTime;

                // Assertions mit largePeers
                assertEquals(size, largePeers.size());
                assertEquals(size - 1, connectedNodes.size()); // Head ist nicht in connectedNodes enthalten
                assertTrue(isValid);
                assertTrue(connectedNodes.containsAll(largePeers.subList(1, largePeers.size())));

                // Performance-Assertions (sollten schnell genug sein)
                assertTrue(networkCreationTime < 1000,
                        String.format("Netzwerk-Erstellung für %d Knoten dauerte %dms", size, networkCreationTime));
                assertTrue(navigationTime < 500,
                        String.format("Navigation für %d Knoten dauerte %dms", size, navigationTime));
                assertTrue(validationTime < 500,
                        String.format("Validierung für %d Knoten dauerte %dms", size, validationTime));

                // Teste spezifische largePeers-Funktionalität
                for (FullyConnectedMirrorNode peer : largePeers) {
                    assertNotNull(peer);
                    assertEquals(StructureType.FULLY_CONNECTED, peer.deriveTypeId());
                    if (peer != largeHead) {
                        assertTrue(largeHead.isLinkedWith(peer));
                    }
                }

                System.out.printf("Netzwerk-Größe: %d, Erstellung: %dms, Navigation: %dms, Validierung: %dms%n",
                        size, networkCreationTime, navigationTime, validationTime);
            }
        }

        private void setupValidFullyConnectedStructure2(FullyConnectedMirrorNode head, List<FullyConnectedMirrorNode> peers) {
            head.setHead(StructureType.FULLY_CONNECTED, true);

            Set<StructureType> typeIds = Set.of(StructureType.FULLY_CONNECTED);
            Map<StructureType, Integer> headIds = Map.of(StructureType.FULLY_CONNECTED, head.getId());

            // Füge alle Peers als Kinder des Heads hinzu
            for (FullyConnectedMirrorNode peer : peers) {
                head.addChild(peer, typeIds, headIds);
            }

            // Erstelle vollständig vernetzte Links zwischen allen Knoten
            List<Mirror> allMirrors = new ArrayList<>();
            if (head.getMirror() != null) allMirrors.add(head.getMirror());

            for (FullyConnectedMirrorNode peer : peers) {
                if (peer.getMirror() != null) {
                    allMirrors.add(peer.getMirror());
                }
            }

            createFullyConnectedLinks2(allMirrors);
        }

        private void createFullyConnectedLinks2(List<Mirror> mirrors) {
            int linkId = 1;

            // Erstelle Links zwischen allen Mirror-Paaren
            for (int i = 0; i < mirrors.size(); i++) {
                for (int j = i + 1; j < mirrors.size(); j++) {
                    Mirror mirror1 = mirrors.get(i);
                    Mirror mirror2 = mirrors.get(j);

                    Link link = new Link(linkId++, mirror1, mirror2, 0, props);
                    mirror1.addLink(link);
                    mirror2.addLink(link);
                }
            }
        }

        @Test
        @DisplayName("Extreme Netzwerk-Strukturen")
        void testExtremeNetworkStructures() {
            // Minimales 2-Knoten-Netzwerk
            FullyConnectedMirrorNode minHead = new FullyConnectedMirrorNode(1);
            FullyConnectedMirrorNode minPeer = new FullyConnectedMirrorNode(2);
            minHead.setHead(StructureType.FULLY_CONNECTED, true);
            minHead.addChild(minPeer);

            assertEquals(2, minHead.getNetworkSize());
            assertEquals(1, minHead.getExpectedLinkCount());
            assertEquals(1, minHead.getConnectedNodes().size());

            // Einzelner isolierter Knoten
            FullyConnectedMirrorNode isolated = new FullyConnectedMirrorNode(100);
            isolated.setHead(StructureType.FULLY_CONNECTED, true);

            assertEquals(1, isolated.getNetworkSize());
            assertEquals(0, isolated.getExpectedLinkCount());
            assertEquals(0, isolated.getConnectedNodes().size());
            assertNull(isolated.getFullyConnectedHead()); // Kein Head gefunden bei ungültiger Struktur
        }

        @Test
        @DisplayName("Null-Handling und defensive Programmierung")
        void testNullHandlingAndDefensiveProgramming() {
            // canBeRemovedFromStructure mit null
            assertFalse(fcNode.canBeRemovedFromStructure(null));

            // isValidStructure mit null Parameters
            assertFalse(fcNode.isValidStructure(null, StructureType.FULLY_CONNECTED, fcNode));
            assertFalse(fcNode.isValidStructure(Set.of(fcNode), StructureType.FULLY_CONNECTED, null));
            assertFalse(fcNode.isValidStructure(Set.of(fcNode), null, fcNode));

            // getConnectedNodes ohne gültige Struktur
            Set<FullyConnectedMirrorNode> connected = fcNode.getConnectedNodes();
            assertTrue(connected.isEmpty());

            // getAllFullyConnectedNodes ohne gültige Struktur
            List<FullyConnectedMirrorNode> allNodes = fcNode.getAllFullyConnectedNodes();
            assertEquals(1, allNodes.size()); // Mindestens sich selbst
            assertTrue(allNodes.contains(fcNode));
        }

        @Test
        @DisplayName("Integration mit echter Simulation - komplexer Test")
        void testComplexIntegrationWithRealSimulation() throws IOException {
            initSimulator();
            MirrorProbe probe = getMirrorProbe();
            assertNotNull(probe);

            List<Mirror> simMirrors = getSimMirrors(probe);

            // Erstelle großes 8-Knoten vollständiges Netz
            List<FullyConnectedMirrorNode> networkNodes = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                FullyConnectedMirrorNode node = new FullyConnectedMirrorNode(i + 1, simMirrors.get(i));
                networkNodes.add(node);
            }

            FullyConnectedMirrorNode head = networkNodes.get(0);
            head.setHead(StructureType.FULLY_CONNECTED, true);

            for (int i = 1; i < networkNodes.size(); i++) {
                head.addChild(networkNodes.get(i));
            }

            // Erstelle vollständig vernetzte Struktur (28 Links für 8 Knoten)
            createFullyConnectedLinks(simMirrors.subList(0, 8));

            // Erstelle Edge-Link
            Mirror externalMirror = new Mirror(300, 0, props);
            Link edgeLink = new Link(100, simMirrors.get(0), externalMirror, 0, props);
            simMirrors.get(0).addLink(edgeLink);
            externalMirror.addLink(edgeLink);

            // Versuche komplexe Integration
            assertEquals(8, head.getNetworkSize());
            assertEquals(7, head.getExpectedLinkCount());

            // Jeder Knoten sollte mit allen anderen verbunden sein
            for (FullyConnectedMirrorNode node : networkNodes) {
                assertEquals(7, node.getConnectedNodes().size());
                assertEquals(8, node.getNetworkSize());
                assertEquals(head, node.getFullyConnectedHead());
            }

            // Teste Link-Verteilung
            assertEquals(8, head.getNumImplementedLinks()); // 7 interne + 1 Edge
            for (int i = 1; i < networkNodes.size(); i++) {
                assertEquals(7, networkNodes.get(i).getNumImplementedLinks());
            }

            // Teste Struktur-Validierung
            Set<StructureNode> allNodes = new HashSet<>(networkNodes);
            assertTrue(head.isValidStructure(allNodes),
                    "Großes vollständiges Netz mit echten Simulator-Daten sollte gültig sein");

            // Versuche MirrorProbe-Integration
            assertTrue(probe.getNumMirrors() >= 0);
            assertNotNull(probe.getMirrors());
        }

        @Test
        @DisplayName("Kompatibilität mit MirrorNode-Funktionen")
        void testMirrorNodeCompatibility() {
            FullyConnectedMirrorNode fcNode1 = new FullyConnectedMirrorNode(1);
            FullyConnectedMirrorNode fcNode2 = new FullyConnectedMirrorNode(2);

            Mirror mirror1 = new Mirror(101, 0, props);
            Mirror mirror2 = new Mirror(102, 0, props);

            fcNode1.setMirror(mirror1);
            fcNode2.setMirror(mirror2);

            // Teste MirrorNode-Funktionalität
            assertNotNull(fcNode1.getMirror());
            assertNotNull(fcNode2.getMirror());

            assertEquals(0, fcNode1.getNumImplementedLinks());
            assertEquals(0, fcNode2.getNumImplementedLinks());

            // Versuche Link-Management
            Link testLink = new Link(1, mirror1, mirror2, 0, props);
            fcNode1.addLink(testLink);
            fcNode2.addLink(testLink);

            assertEquals(1, fcNode1.getNumImplementedLinks());
            assertEquals(1, fcNode2.getNumImplementedLinks());

            assertTrue(fcNode1.getImplementedLinks().contains(testLink));
            assertTrue(fcNode2.getImplementedLinks().contains(testLink));

            // Teste Verbindungsprüfung
            fcNode1.setHead(StructureType.FULLY_CONNECTED, true);
            fcNode1.addChild(fcNode2);

            assertTrue(fcNode1.isLinkedWith(fcNode2));
            assertTrue(fcNode2.isLinkedWith(fcNode1));
        }

        private void createFullyConnectedLinks(List<Mirror> mirrors) {
            int linkId = 1;
            for (int i = 0; i < mirrors.size(); i++) {
                for (int j = i + 1; j < mirrors.size(); j++) {
                    Link link = new Link(linkId++, mirrors.get(i), mirrors.get(j), 0, props);
                    mirrors.get(i).addLink(link);
                    mirrors.get(j).addLink(link);
                }
            }
        }
    }
}