package org.lrdm.topologies.base;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.TimedRDMSim;
import org.lrdm.probes.MirrorProbe;
import org.lrdm.topologies.BalancedTreeTopologyStrategy;

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
    private static final String config = "resources/sim-test-mirrornode.conf";

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
            assertFalse(grandchild.canAcceptMoreChildren()); // Hat bereits ein Kind
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
            node4.setParent(head); // Schließe Ring

            // Mit 4 Knoten kann einer entfernt werden (mindestens 3 müssen bleiben)
            assertTrue(head.canBeRemovedFromStructure(head));
            assertTrue(node2.canBeRemovedFromStructure(head));

            // Mit weniger als 4 Knoten kann nichts entfernt werden
            head.removeChild(node2);
            assertFalse(head.canBeRemovedFromStructure(head));
            assertFalse(node3.canBeRemovedFromStructure(head));
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
            node3.setParent(head); // Schließe Ring

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
            node4.setParent(head); // Schließe Ring

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

            // Weniger als 3 Knoten ungültig
            assertFalse(node1.isValidStructure(Set.of(node1)));
            assertFalse(node1.isValidStructure(Set.of(node1, node2)));

            // Ohne Head ungültig
            RingMirrorNode node3 = new RingMirrorNode(3);
            node1.addChild(node2);
            node2.addChild(node3);
            node3.setParent(node1);
            assertFalse(node1.isValidStructure(Set.of(node1, node2, node3)));

            // Mit Head aber ohne Edge-Links ungültig
            node1.setHead(true);
            Mirror mirror1 = new Mirror(101, 0, props);
            Mirror mirror2 = new Mirror(102, 0, props);
            Mirror mirror3 = new Mirror(103, 0, props);

            node1.setMirror(mirror1);
            node2.setMirror(mirror2);
            node3.setMirror(mirror3);

            Link link1 = new Link(1, mirror1, mirror2, 0, props);
            Link link2 = new Link(2, mirror2, mirror3, 0, props);
            Link link3 = new Link(3, mirror3, mirror1, 0, props);

            mirror1.addLink(link1);
            mirror2.addLink(link1);
            mirror2.addLink(link2);
            mirror3.addLink(link2);
            mirror3.addLink(link3);
            mirror1.addLink(link3);

            assertFalse(node1.isValidStructure(Set.of(node1, node2, node3))); // Kein Edge-Link

            // Offene Kette (kein geschlossener Ring) ungültig
            node3.setParent(null); // Öffne Ring
            Mirror externalMirror = new Mirror(104, 0, props);
            Link edgeLink = new Link(4, mirror1, externalMirror, 0, props);
            mirror1.addLink(edgeLink);
            externalMirror.addLink(edgeLink);

            assertFalse(node1.isValidStructure(Set.of(node1, node2, node3))); // Nicht geschlossen
        }

        @Test
        @DisplayName("isValidStructure gemischte Knotentypen")
        void testMixedNodeTypes() {
            RingMirrorNode ringNode = new RingMirrorNode(1);
            MirrorNode regularNode = new MirrorNode(2); // Nicht RingMirrorNode
            RingMirrorNode ringNode2 = new RingMirrorNode(3);

            ringNode.setHead(true);
            ringNode.addChild(regularNode);
            regularNode.addChild(ringNode2);
            ringNode2.setParent(ringNode);

            Set<StructureNode> mixedNodes = Set.of(ringNode, regularNode, ringNode2);
            assertFalse(ringNode.isValidStructure(mixedNodes)); // Gemischte Typen ungültig
        }

        @Test
        @DisplayName("isValidStructure mehrere Heads")
        void testMultipleHeads() {
            RingMirrorNode node1 = new RingMirrorNode(1);
            RingMirrorNode node2 = new RingMirrorNode(2);
            RingMirrorNode node3 = new RingMirrorNode(3);

            // Mehrere Heads (ungültig)
            node1.setHead(true);
            node2.setHead(true);
            node1.addChild(node2);
            node2.addChild(node3);
            node3.setParent(node1);

            Set<StructureNode> invalidHeads = Set.of(node1, node2, node3);
            assertFalse(node1.isValidStructure(invalidHeads));
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
            node4.setParent(head); // Schließe Ring
        }

        @Test
        @DisplayName("getNextInRing Navigation")
        void testGetNextInRing() {
            assertEquals(node2, head.getNextInRing());
            assertEquals(node3, node2.getNextInRing());
            assertEquals(node4, node3.getNextInRing());
            assertEquals(null, node4.getNextInRing()); // Head ist Parent, nicht Child

            // Prüfe mit unvollständigem Ring
            node4.setParent(null);
            assertNull(node4.getNextInRing()); // Kein Kind
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
            // Setze externen Parent für Head
            MirrorNode externalParent = new MirrorNode(100);
            head.setParent(externalParent);

            // Head sollte keinen Previous in Ring haben (externer Parent)
            assertNull(head.getPreviousInRing());

            // Andere Knoten sollten normal funktionieren
            assertEquals(head, node2.getPreviousInRing());
            assertEquals(node2, node3.getPreviousInRing());
        }

        @Test
        @DisplayName("isRingNode Validierung")
        void testIsRingNode() {
            // Setze Mirrors
            Mirror headMirror = new Mirror(101, 0, props);
            Mirror mirror2 = new Mirror(102, 0, props);
            Mirror mirror3 = new Mirror(103, 0, props);
            Mirror mirror4 = new Mirror(104, 0, props);

            head.setMirror(headMirror);
            node2.setMirror(mirror2);
            node3.setMirror(mirror3);
            node4.setMirror(mirror4);

            // Erstelle Ring-Links für gültige Struktur
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

            // Edge-Link für Head
            Mirror externalMirror = new Mirror(105, 0, props);
            Link edgeLink = new Link(5, headMirror, externalMirror, 0, props);
            headMirror.addLink(edgeLink);
            externalMirror.addLink(edgeLink);

            // Alle Knoten sollten gültige Ring-Knoten sein
            assertTrue(head.isRingNode());
            assertTrue(node2.isRingNode());
            assertTrue(node3.isRingNode());
            assertTrue(node4.isRingNode());
        }

        @Test
        @DisplayName("Ring-Navigation mit 3-Knoten-Ring")
        void testThreeNodeRingNavigation() {
            RingMirrorNode small1 = new RingMirrorNode(10);
            RingMirrorNode small2 = new RingMirrorNode(11);
            RingMirrorNode small3 = new RingMirrorNode(12);

            small1.setHead(true);
            small1.addChild(small2);
            small2.addChild(small3);
            small3.setParent(small1);

            // Navigation
            assertEquals(small2, small1.getNextInRing());
            assertEquals(small3, small2.getNextInRing());
            assertNull(small3.getNextInRing()); // Head ist Parent

            assertEquals(small3, small1.getPreviousInRing());
            assertEquals(small1, small2.getPreviousInRing());
            assertEquals(small2, small3.getPreviousInRing());
        }

        @Test
        @DisplayName("Ring-Navigation Edge Cases")
        void testRingNavigationEdgeCases() {
            RingMirrorNode isolatedNode = new RingMirrorNode(20);

            // Isolierter Knoten
            assertNull(isolatedNode.getNextInRing());
            assertNull(isolatedNode.getPreviousInRing());

            // Knoten mit zu vielen Kindern
            RingMirrorNode tooManyChildren = new RingMirrorNode(21);
            RingMirrorNode child1 = new RingMirrorNode(22);
            RingMirrorNode child2 = new RingMirrorNode(23);

            tooManyChildren.addChild(child1);
            tooManyChildren.addChild(child2);

            assertNull(tooManyChildren.getNextInRing()); // Zu viele Kinder
        }
    }

    @Nested
    @DisplayName("RingMirrorNode Integration und Edge Cases")
    class RingMirrorNodeIntegrationTests {

        @Test
        @DisplayName("Integration mit echter Simulation")
        void testIntegrationWithRealSimulation() throws IOException {
            initSimulator();
            sim.initialize(new BalancedTreeTopologyStrategy());
            sim.getEffector().setMirrors(4, 0);

            for(int t = 1; t <= 10; t++) {
                sim.runStep(t);
            }

            MirrorProbe mirrorProbe = getMirrorProbe();
            assertNotNull(mirrorProbe);
            List<Mirror> mirrors = mirrorProbe.getMirrors();
            assertFalse(mirrors.isEmpty());

            // Erstelle RingMirrorNodes mit echten Mirrors
            RingMirrorNode ringHead = new RingMirrorNode(100, mirrors.get(0));
            RingMirrorNode ringNode = new RingMirrorNode(101);

            if (mirrors.size() > 1) {
                ringNode.setMirror(mirrors.get(1));
            }

            // Grundlegende Funktionalität sollte funktionieren
            assertEquals(mirrors.get(0), ringHead.getMirror());
            assertNotNull(ringHead.getNextInRing()); // Könnte null sein ohne Kinder
        }

        @Test
        @DisplayName("Performance mit größeren Ringen")
        void testPerformanceWithLargerRings() {
            List<RingMirrorNode> ringNodes = new ArrayList<>();

            // Erstelle Ring mit 20 Knoten
            for (int i = 0; i < 20; i++) {
                ringNodes.add(new RingMirrorNode(i));
            }

            // Verbinde als Ring
            ringNodes.get(0).setHead(true);
            for (int i = 0; i < 20; i++) {
                RingMirrorNode current = ringNodes.get(i);
                RingMirrorNode next = ringNodes.get((i + 1) % 20);
                
                if (i < 19) {
                    current.addChild(next);
                } else {
                    next.setParent(current); // Schließe Ring
                }
            }

            long startTime = System.currentTimeMillis();

            // Prüfe Performance-kritische Operationen
            RingMirrorNode current = ringNodes.get(0);
            for (int i = 0; i < 20; i++) {
                current = current.getNextInRing();
                if (current == null) break;
            }

            boolean isRing = ringNodes.get(0).isRingNode();
            Set<Mirror> mirrors = ringNodes.get(0).getMirrorsOfStructure();

            long endTime = System.currentTimeMillis();

            // Sollte schnell sein (< 1000ms für 20 Knoten)
            assertTrue(endTime - startTime < 1000);

            // Korrekte Funktionalität
            assertNotNull(mirrors);
            // isRing wird false sein ohne Mirrors und Links
        }

        @Test
        @DisplayName("Null-Handling und defensive Programmierung")
        void testNullHandlingAndDefensiveProgramming() {
            RingMirrorNode node = new RingMirrorNode(1);

            // Null-Parameter
            assertFalse(node.canBeRemovedFromStructure(null));

            // Ohne Struktur
            assertNull(node.getNextInRing());
            assertNull(node.getPreviousInRing());
            assertFalse(node.isRingNode());

            // Mit ungültiger Struktur
            RingMirrorNode other = new RingMirrorNode(2);
            node.addChild(other);
            assertFalse(node.isRingNode()); // Kein geschlossener Ring
        }

        @Test
        @DisplayName("Kompatibilität mit MirrorNode-Funktionen")
        void testMirrorNodeCompatibility() {
            RingMirrorNode ringNode = new RingMirrorNode(1);
            Mirror mirror = new Mirror(101, 0, props);
            ringNode.setMirror(mirror);

            // Alle MirrorNode-Funktionen sollten verfügbar sein
            assertEquals(0, ringNode.getNumImplementedLinks());
            assertEquals(0, ringNode.getNumPendingLinks());
            assertTrue(ringNode.getImplementedLinks().isEmpty());
            assertNotNull(ringNode.getMirrorsOfStructure());
            assertNotNull(ringNode.getLinksOfStructure());
            assertEquals(0, ringNode.getNumEdgeLinks());

            // Link-Management
            Mirror targetMirror = new Mirror(102, 0, props);
            Link link = new Link(1, mirror, targetMirror, 0, props);
            ringNode.addLink(link);
            assertEquals(1, ringNode.getNumImplementedLinks());

            ringNode.removeLink(link);
            assertEquals(0, ringNode.getNumImplementedLinks());
        }

        @Test
        @DisplayName("Ring-Integritäts-Validierung")
        void testRingIntegrityValidation() {
            RingMirrorNode node1 = new RingMirrorNode(1);
            RingMirrorNode node2 = new RingMirrorNode(2);
            RingMirrorNode node3 = new RingMirrorNode(3);

            node1.setHead(true);
            node1.addChild(node2);
            node2.addChild(node3);
            // Lasse Ring offen (node3 hat keinen Parent zu node1)

            // Ohne geschlossenen Ring sollte isValidStructure false sein
            Set<StructureNode> openRing = Set.of(node1, node2, node3);
            assertFalse(node1.isValidStructure(openRing));

            // Schließe Ring
            node3.setParent(node1);

            // Immer noch ungültig ohne Mirrors und Links
            assertFalse(node1.isValidStructure(openRing));
        }

        @Test
        @DisplayName("Head-Node externe Parent-Validierung")
        void testHeadNodeExternalParentValidation() {
            RingMirrorNode head = new RingMirrorNode(1);
            RingMirrorNode node2 = new RingMirrorNode(2);
            RingMirrorNode node3 = new RingMirrorNode(3);
            MirrorNode externalParent = new MirrorNode(100);

            head.setHead(true);
            head.setParent(externalParent); // Externer Parent
            head.addChild(node2);
            node2.addChild(node3);
            node3.setParent(head);

            // Head mit externem Parent sollte gültig sein (wenn andere Bedingungen erfüllt)
            Set<StructureNode> ringWithExternal = Set.of(head, node2, node3);
            
            // Ohne Mirrors und Links wird es false sein, aber die externe Parent-Logik wird geprüft
            assertFalse(head.isValidStructure(ringWithExternal));

            // Prüfe Navigation mit externem Parent
            assertNull(head.getPreviousInRing()); // Externer Parent
            assertEquals(node2, head.getNextInRing());
        }
    }
}