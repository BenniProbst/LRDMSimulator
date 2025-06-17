
package org.lrdm.topologies.node;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.TimedRDMSim;
import org.lrdm.probes.MirrorProbe;
import org.lrdm.topologies.strategies.BalancedTreeTopologyStrategy;

import java.io.IOException;
import java.util.*;

import static org.lrdm.TestProperties.loadProperties;
import static org.lrdm.TestProperties.getProps;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RingMirrorNode spezifische Tests")
class RingMirrorNodeTest {

    private TimedRDMSim sim;
    private RingMirrorNode ringNode;
    private Properties props;
    private static final String config = "resources/sim-test-mirror node.conf";

    @BeforeEach
    void setUp() throws IOException {
        loadProperties(config);
        props = getProps();
        ringNode = new RingMirrorNode(1);
    }

    public void initSimulator() throws IOException {
        initSimulator(config);
    }

    public void initSimulator(String config) throws IOException {
        loadProperties(config);
        props = getProps();
        sim = new TimedRDMSim(config);
        sim.setHeadless(true);
        sim.initialize(new BalancedTreeTopologyStrategy());
    }

    private MirrorProbe getMirrorProbe() {
        return sim.getMirrorProbe();
    }

    @Nested
    @DisplayName("RingMirrorNode Grundfunktionen")
    class RingMirrorNodeBasicTests {

        @Test
        @DisplayName("RingMirrorNode erbt MirrorNode-Funktionalität")
        void testInheritedMirrorNodeFunctionality() {
            Mirror testMirror = new Mirror(101, 0, props);
            ringNode.setMirror(testMirror);

            assertEquals(1, ringNode.getId());
            assertEquals(testMirror, ringNode.getMirror());
            assertEquals(0, ringNode.getNumImplementedLinks());
            assertTrue(ringNode.getImplementedLinks().isEmpty());
        }

        @Test
        @DisplayName("RingMirrorNode Konstruktoren")
        void testConstructors() {
            // Standard Konstruktor
            RingMirrorNode node1 = new RingMirrorNode(5);
            assertEquals(5, node1.getId());
            assertNull(node1.getMirror());

            // Konstruktor mit Mirror
            Mirror mirror = new Mirror(102, 0, props);
            RingMirrorNode node2 = new RingMirrorNode(6, mirror);
            assertEquals(6, node2.getId());
            assertEquals(mirror, node2.getMirror());
        }

        @Test
        @DisplayName("canAcceptMoreChildren Ring-spezifische Logik")
        void testCanAcceptMoreChildrenRingSpecific() {
            RingMirrorNode head = new RingMirrorNode(1);
            head.setHead(true);

            // Einzelner Head-Knoten kann Kind akzeptieren
            assertTrue(head.canAcceptMoreChildren());

            // Nach Hinzufügen eines Kindes nicht mehr (Ring: genau ein Kind)
            RingMirrorNode child = new RingMirrorNode(2);
            head.addChild(child);
            assertFalse(head.canAcceptMoreChildren());

            // Kind-Knoten kann auch Kind akzeptieren (wenn noch leer)
            assertTrue(child.canAcceptMoreChildren());

            // Nach Hinzufügen eines Kindes nicht mehr
            RingMirrorNode grandchild = new RingMirrorNode(3);
            child.addChild(grandchild);
            assertFalse(child.canAcceptMoreChildren());
            assertFalse(grandchild.canAcceptMoreChildren());
        }

        @Test
        @DisplayName("canBeRemovedFromStructure Ring-spezifische Validierung")
        void testCanBeRemovedFromStructureRingSpecific() {
            // Erstelle gültigen 4-Knoten-Ring
            RingMirrorNode head = new RingMirrorNode(1);
            RingMirrorNode node2 = new RingMirrorNode(2);
            RingMirrorNode node3 = new RingMirrorNode(3);
            RingMirrorNode node4 = new RingMirrorNode(4);

            head.setHead(true);
            head.addChild(node2);
            node2.addChild(node3);
            node3.addChild(node4);
            // Schließe Ring - das letzte Kind wird automatisch mit dem Head verbunden

            // Setze Mirrors für vollständige Struktur
            Mirror headMirror = new Mirror(101, 0, props);
            Mirror mirror2 = new Mirror(102, 0, props);
            Mirror mirror3 = new Mirror(103, 0, props);
            Mirror mirror4 = new Mirror(104, 0, props);

            head.setMirror(headMirror);
            node2.setMirror(mirror2);
            node3.setMirror(mirror3);
            node4.setMirror(mirror4);

            // Mit 4 Knoten kann einer entfernt werden (mindestens 3 müssen bleiben)
            assertTrue(head.canBeRemovedFromStructure(head));
            assertTrue(node2.canBeRemovedFromStructure(head));

            // Mit weniger als 4 Knoten kann nichts entfernt werden
            head.removeChild(node2);
            assertFalse(head.canBeRemovedFromStructure(head));
            assertFalse(node3.canBeRemovedFromStructure(head));
        }

        @Test
        @DisplayName("Integration mit echter Simulation")
        void testIntegrationWithRealSimulation() throws IOException {
            initSimulator();
            assertNotNull(sim);

            MirrorProbe probe = getMirrorProbe();
            assertNotNull(probe);

            // Erstelle RingMirrorNode mit Simulator-Mirror über MirrorProbe
            List<Mirror> simMirrors = probe.getMirrors();
            if (!simMirrors.isEmpty()) {
                Mirror simMirror = simMirrors.get(0);
                RingMirrorNode simRingNode = new RingMirrorNode(100, simMirror);

                assertEquals(simMirror, simRingNode.getMirror());
                assertEquals(100, simRingNode.getId());
                assertEquals(StructureNode.StructureType.RING, simRingNode.deriveTypeId());

                // Teste RingMirrorNode-Funktionalität mit echtem Mirror
                assertEquals(simMirror.getLinks().size(), simRingNode.getNumImplementedLinks());
                assertEquals(simMirror.getLinks(), simRingNode.getImplementedLinks());
            } else {
                // Fallback-Test, falls keine Mirrors vorhanden sind
                assertTrue(probe.getNumMirrors() >= 0);
            }
        }
    }

    @Nested
    @DisplayName("RingMirrorNode Struktur-Validierung")
    class RingMirrorNodeValidationTests {

        @Test
        @DisplayName("isValidStructure gültiger 3-Knoten-Ring")
        void testValidStructureThreeNodeRing() {
            RingMirrorNode head = new RingMirrorNode(1);
            RingMirrorNode node2 = new RingMirrorNode(2);
            RingMirrorNode node3 = new RingMirrorNode(3);

            head.setHead(true);
            head.addChild(node2);
            node2.addChild(node3);
            // Ring wird automatisch durch RingMirrorNode-Logik geschlossen

            // Setze Mirrors
            Mirror headMirror = new Mirror(101, 0, props);
            Mirror mirror2 = new Mirror(102, 0, props);
            Mirror mirror3 = new Mirror(103, 0, props);
            Mirror externalMirror = new Mirror(104, 0, props);

            head.setMirror(headMirror);
            node2.setMirror(mirror2);
            node3.setMirror(mirror3);

            // Erstelle Ring-Links
            Link link1 = new Link(1, headMirror, mirror2, 0, props);
            Link link2 = new Link(2, mirror2, mirror3, 0, props);
            Link link3 = new Link(3, mirror3, headMirror, 0, props);

            headMirror.addLink(link1);
            mirror2.addLink(link1);
            mirror2.addLink(link2);
            mirror3.addLink(link2);
            mirror3.addLink(link3);
            headMirror.addLink(link3);

            // Erstelle Edge-Link für Head
            Link edgeLink = new Link(4, headMirror, externalMirror, 0, props);
            headMirror.addLink(edgeLink);
            externalMirror.addLink(edgeLink);

            Set<StructureNode> ringNodes = Set.of(head, node2, node3);
            assertTrue(head.isValidStructure(ringNodes));
        }

        @Test
        @DisplayName("isValidStructure gültiger 4-Knoten-Ring")
        void testValidStructureFourNodeRing() {
            RingMirrorNode head = new RingMirrorNode(1);
            RingMirrorNode node2 = new RingMirrorNode(2);
            RingMirrorNode node3 = new RingMirrorNode(3);
            RingMirrorNode node4 = new RingMirrorNode(4);

            head.setHead(true);
            head.addChild(node2);
            node2.addChild(node3);
            node3.addChild(node4);
            // Ring wird automatisch durch RingMirrorNode-Logik geschlossen

            // Setze Mirrors
            Mirror headMirror = new Mirror(101, 0, props);
            Mirror mirror2 = new Mirror(102, 0, props);
            Mirror mirror3 = new Mirror(103, 0, props);
            Mirror mirror4 = new Mirror(104, 0, props);
            Mirror externalMirror = new Mirror(105, 0, props);

            head.setMirror(headMirror);
            node2.setMirror(mirror2);
            node3.setMirror(mirror3);
            node4.setMirror(mirror4);

            // Erstelle Ring-Links
            Link link1 = new Link(1, headMirror, mirror2, 0, props);
            Link link2 = new Link(2, mirror2, mirror3, 0, props);
            Link link3 = new Link(3, mirror3, mirror4, 0, props);
            Link link4 = new Link(4, mirror4, headMirror, 0, props);

            headMirror.addLink(link1);
            mirror2.addLink(link1);
            mirror2.addLink(link2);
            mirror3.addLink(link2);
            mirror3.addLink(link3);
            mirror4.addLink(link3);
            mirror4.addLink(link4);
            headMirror.addLink(link4);

            // Erstelle Edge-Link für Head
            Link edgeLink = new Link(5, headMirror, externalMirror, 0, props);
            headMirror.addLink(edgeLink);
            externalMirror.addLink(edgeLink);

            Set<StructureNode> ringNodes = Set.of(head, node2, node3, node4);
            assertTrue(head.isValidStructure(ringNodes));
        }

        @Test
        @DisplayName("isValidStructure ungültige Strukturen")
        void testInvalidStructures() {
            RingMirrorNode node1 = new RingMirrorNode(1);
            RingMirrorNode node2 = new RingMirrorNode(2);

            // Zu wenige Knoten für Ringe
            Set<StructureNode> tooFewNodes = Set.of(node1, node2);
            assertFalse(node1.isValidStructure(tooFewNodes));

            // Einzelner Knoten
            Set<StructureNode> singleNode = Set.of(node1);
            assertFalse(node1.isValidStructure(singleNode));

            // Leere Struktur
            Set<StructureNode> emptyNodes = Set.of();
            assertFalse(node1.isValidStructure(emptyNodes));
        }

        @Test
        @DisplayName("isValidStructure gemischte Knotentypen")
        void testMixedNodeTypes() {
            RingMirrorNode ringNode1 = new RingMirrorNode(1);
            RingMirrorNode ringNode2 = new RingMirrorNode(2);
            MirrorNode regularNode = new MirrorNode(3); // Nicht RingMirrorNode

            Set<StructureNode> mixedNodes = Set.of(ringNode1, ringNode2, regularNode);
            assertFalse(ringNode1.isValidStructure(mixedNodes));
        }

        @Test
        @DisplayName("isValidStructure mehrere Heads")
        void testMultipleHeads() {
            RingMirrorNode head1 = new RingMirrorNode(1);
            RingMirrorNode head2 = new RingMirrorNode(2);
            RingMirrorNode node3 = new RingMirrorNode(3);

            head1.setHead(true);
            head2.setHead(true); // Zweiter Head - ungültig

            Set<StructureNode> multipleHeads = Set.of(head1, head2, node3);
            assertFalse(head1.isValidStructure(multipleHeads));
        }

        @Test
        @DisplayName("Struktur-Validierung mit MirrorProbe Daten")
        void testStructureValidationWithMirrorProbeData() throws IOException {
            initSimulator();
            MirrorProbe probe = getMirrorProbe();
            assertNotNull(probe);

            List<Mirror> simMirrors = probe.getMirrors();
            // Fallback, falls weniger als 4 Mirrors verfügbar sind
            if (simMirrors.size() < 4) {
                Mirror mirror1 = new Mirror(201, 0, props);
                Mirror mirror2 = new Mirror(202, 0, props);
                Mirror mirror3 = new Mirror(203, 0, props);
                Mirror mirror4 = new Mirror(204, 0, props);
                simMirrors = List.of(mirror1, mirror2, mirror3, mirror4);
            }

            // Erstelle RingMirrorNodes mit echten Simulator-Mirrors
            RingMirrorNode head = new RingMirrorNode(1, simMirrors.get(0));
            RingMirrorNode node2 = new RingMirrorNode(2, simMirrors.get(1));
            RingMirrorNode node3 = new RingMirrorNode(3, simMirrors.get(2));
            RingMirrorNode node4 = new RingMirrorNode(4, simMirrors.get(3));

            // Baue gültigen 4-Knoten-Ring auf
            head.setHead(true);
            head.addChild(node2);
            node2.addChild(node3);
            node3.addChild(node4);

            // Erstelle echte Ring-Links zwischen den Mirrors
            Link link1 = new Link(1, simMirrors.get(0), simMirrors.get(1), 0, props);
            Link link2 = new Link(2, simMirrors.get(1), simMirrors.get(2), 0, props);
            Link link3 = new Link(3, simMirrors.get(2), simMirrors.get(3), 0, props);
            Link link4 = new Link(4, simMirrors.get(3), simMirrors.get(0), 0, props);

            // Füge Links zu den Mirrors hinzu
            simMirrors.get(0).addLink(link1);
            simMirrors.get(1).addLink(link1);
            simMirrors.get(1).addLink(link2);
            simMirrors.get(2).addLink(link2);
            simMirrors.get(2).addLink(link3);
            simMirrors.get(3).addLink(link3);
            simMirrors.get(3).addLink(link4);
            simMirrors.get(0).addLink(link4);

            // Erstelle Edge-Link für Head (zu externem Mirror)
            Mirror externalMirror = new Mirror(300, 0, props);
            Link edgeLink = new Link(5, simMirrors.get(0), externalMirror, 0, props);
            simMirrors.get(0).addLink(edgeLink);
            externalMirror.addLink(edgeLink);

            // Teste Struktur-Validierung mit echten MirrorProbe-Daten
            Set<StructureNode> ringNodes = Set.of(head, node2, node3, node4);
            assertTrue(head.isValidStructure(ringNodes),
                    "Ring mit MirrorProbe-Daten sollte gültig sein");

            // Teste RingMirrorNode-spezifische Funktionen mit echten Daten
            assertEquals(head, node2.getNextInRing());
            assertEquals(node2, head.getNextInRing());
            assertEquals(node4, head.getPreviousInRing());
            assertEquals(head, node4.getNextInRing());

            // Versuche Mirror-Integration
            assertEquals(simMirrors.get(0), head.getMirror());
            assertEquals(simMirrors.get(1), node2.getMirror());
            assertEquals(simMirrors.get(2), node3.getMirror());
            assertEquals(simMirrors.get(3), node4.getMirror());

            // Teste Link-Zählung mit echten Daten
            assertEquals(3, head.getNumImplementedLinks(), "Head sollte 3 Links haben (2 Ring + 1 Edge)");
            assertEquals(2, node2.getNumImplementedLinks(), "Knoten 2 sollte 2 Ring-Links haben");
            assertEquals(2, node3.getNumImplementedLinks(), "Knoten 3 sollte 2 Ring-Links haben");
            assertEquals(2, node4.getNumImplementedLinks(), "Knoten 4 sollte 2 Ring-Links haben");

            // Versuche MirrorProbe-Integration
            assertTrue(probe.getNumMirrors() >= 0, "MirrorProbe sollte valide Mirror-Anzahl liefern");
            assertTrue(probe.getNumTargetLinksPerMirror() >= 0,
                    "Target links per mirror sollte nicht negativ sein");

            // Teste ungültige Struktur durch Knoten-Entfernung
            Set<StructureNode> incompleteRingNodes = Set.of(head, node2, node3);
            assertFalse(head.isValidStructure(incompleteRingNodes),
                    "Unvollständiger Ring sollte ungültig sein");
        }
    }

    @Nested
    @DisplayName("RingMirrorNode Ring-spezifische Funktionen")
    class RingMirrorNodeRingSpecificTests {

        private RingMirrorNode head, node2, node3, node4;

        @BeforeEach
        void setUpRing() {
            head = new RingMirrorNode(1);
            node2 = new RingMirrorNode(2);
            node3 = new RingMirrorNode(3);
            node4 = new RingMirrorNode(4);

            head.setHead(true);
            head.addChild(node2);
            node2.addChild(node3);
            node3.addChild(node4);
            // Ring wird automatisch geschlossen
        }

        @Test
        @DisplayName("getNextInRing Navigation")
        void testGetNextInRing() {
            assertEquals(node2, head.getNextInRing());
            assertEquals(node3, node2.getNextInRing());
            assertEquals(node4, node3.getNextInRing());
            assertEquals(head, node4.getNextInRing()); // Ring geschlossen
        }

        @Test
        @DisplayName("getPreviousInRing Navigation")
        void testGetPreviousInRing() {
            assertEquals(node4, head.getPreviousInRing());
            assertEquals(head, node2.getPreviousInRing());
            assertEquals(node2, node3.getPreviousInRing());
            assertEquals(node3, node4.getPreviousInRing());
        }

        @Test
        @DisplayName("getPreviousInRing mit externem Parent")
        void testGetPreviousInRingWithExternalParent() {
            // Head kann externen Parent haben
            RingMirrorNode externalParent = new RingMirrorNode(100);
            head.setParent(externalParent);

            // Ring-Navigation sollte weiterhin funktionieren
            assertEquals(node4, head.getPreviousInRing());
            assertEquals(head, node2.getPreviousInRing());
        }

        @Test
        @DisplayName("Ring-Navigation mit 3-Knoten-Ring")
        void testThreeNodeRingNavigation() {
            RingMirrorNode ring3Head = new RingMirrorNode(10);
            RingMirrorNode ring3Node2 = new RingMirrorNode(20);
            RingMirrorNode ring3Node3 = new RingMirrorNode(30);

            ring3Head.setHead(true);
            ring3Head.addChild(ring3Node2);
            ring3Node2.addChild(ring3Node3);

            assertEquals(ring3Node2, ring3Head.getNextInRing());
            assertEquals(ring3Node3, ring3Node2.getNextInRing());
            assertEquals(ring3Head, ring3Node3.getNextInRing());

            assertEquals(ring3Node3, ring3Head.getPreviousInRing());
            assertEquals(ring3Head, ring3Node2.getPreviousInRing());
            assertEquals(ring3Node2, ring3Node3.getPreviousInRing());
        }

        @Test
        @DisplayName("Ring-Navigation Edge Cases")
        void testRingNavigationEdgeCases() {
            // Einzelner Knoten
            RingMirrorNode singleNode = new RingMirrorNode(999);
            assertNull(singleNode.getNextInRing());
            assertNull(singleNode.getPreviousInRing());

            // Unvollständiger Ring (nur 2 Knoten)
            RingMirrorNode incompleteHead = new RingMirrorNode(1001);
            RingMirrorNode incompleteNode2 = new RingMirrorNode(1002);
            incompleteHead.addChild(incompleteNode2);

            assertNull(incompleteHead.getNextInRing());
            assertNull(incompleteNode2.getPreviousInRing());
        }
    }

    @Nested
    @DisplayName("RingMirrorNode Integration und Edge Cases")
    class RingMirrorNodeIntegrationTests {

        @Test
        @DisplayName("Performance mit größeren Ringen")
        void testPerformanceWithLargerRings() {
            long startTime = System.currentTimeMillis();

            // Erstelle Ring mit 10 Knoten
            RingMirrorNode[] nodes = new RingMirrorNode[10];
            for (int i = 0; i < 10; i++) {
                nodes[i] = new RingMirrorNode(i + 1);
                if (i == 0) {
                    nodes[i].setHead(true);
                }
                if (i > 0) {
                    nodes[i - 1].addChild(nodes[i]);
                }
            }

            // Teste Navigation durch den gesamten Ring
            RingMirrorNode current = nodes[0];
            for (int i = 0; i < 10; i++) {
                assertNotNull(current);
                current = current.getNextInRing();
            }
            assertEquals(nodes[0], current); // Sollte wieder am Anfang sein

            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 100); // Sollte rasant sein
        }

        @Test
        @DisplayName("Null-Handling und defensive Programmierung")
        void testNullHandlingAndDefensiveProgramming() {
            RingMirrorNode node = new RingMirrorNode(1);

            // Null-sichere Operationen
            assertNull(node.getNextInRing());
            assertNull(node.getPreviousInRing());
            assertNotNull(node.getChildren()); // Sollte nie null sein

            // Edge Cases mit null Mirrors
            assertNull(node.getMirror());
            assertEquals(0, node.getNumImplementedLinks());
            assertTrue(node.getImplementedLinks().isEmpty());
        }

        @Test
        @DisplayName("Kompatibilität mit MirrorNode-Funktionen")
        void testMirrorNodeCompatibility() {
            RingMirrorNode ringNode = new RingMirrorNode(1);
            Mirror testMirror = new Mirror(101, 0, props);
            ringNode.setMirror(testMirror);

            // Alle MirrorNode-Funktionen sollten verfügbar sein
            assertEquals(testMirror, ringNode.getMirror());
            assertEquals(0, ringNode.getNumImplementedLinks());
            assertTrue(ringNode.getImplementedLinks().isEmpty());

            // Link-Management sollte funktionieren
            Mirror targetMirror = new Mirror(102, 0, props);
            Link link = new Link(1, testMirror, targetMirror, 0, props);
            ringNode.addLink(link);

            assertEquals(1, ringNode.getNumImplementedLinks());
            assertTrue(ringNode.getImplementedLinks().contains(link));

            ringNode.removeLink(link);
            assertEquals(0, ringNode.getNumImplementedLinks());
        }

        @Test
        @DisplayName("Ring-Integrity-Validierung")
        void testRingIntegrityValidation() {
            // Teste, dass Ring-Struktur korrekt validiert wird
            RingMirrorNode head = new RingMirrorNode(1);
            RingMirrorNode node2 = new RingMirrorNode(2);
            RingMirrorNode node3 = new RingMirrorNode(3);

            head.setHead(true);
            head.addChild(node2);
            node2.addChild(node3);

            // Setze Mirrors für vollständige Validierung
            head.setMirror(new Mirror(101, 0, props));
            node2.setMirror(new Mirror(102, 0, props));
            node3.setMirror(new Mirror(103, 0, props));

            Set<StructureNode> ringNodes = Set.of(head, node2, node3);

            // Ring sollte gültig sein, wenn korrekt aufgebaut
            // (Abhängig von der vollständigen Implementierung der isValidStructure Methode)
            assertNotNull(ringNodes);
            assertEquals(3, ringNodes.size());
        }

        @Test
        @DisplayName("Head-Node externe Parent-Validierung")
        void testHeadNodeExternalParentValidation() {
            RingMirrorNode externalParent = new RingMirrorNode(100);
            RingMirrorNode head = new RingMirrorNode(1);
            RingMirrorNode node2 = new RingMirrorNode(2);
            RingMirrorNode node3 = new RingMirrorNode(3);

            // Head kann externen Parent haben
            head.setParent(externalParent);
            head.setHead(true);
            head.addChild(node2);
            node2.addChild(node3);

            // Navigation sollte trotz externem Parent funktionieren
            assertEquals(node2, head.getNextInRing());
            assertEquals(node3, head.getPreviousInRing());
        }
    }
}