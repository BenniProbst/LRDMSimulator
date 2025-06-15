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

@DisplayName("StarMirrorNode spezifische Tests")
class StarMirrorNodeTest {

    private TimedRDMSim sim;
    private StarMirrorNode starNode;
    private Properties props;
    private static final String config = "resources/sim-test-mirror node.conf";

    @BeforeEach
    void setUp() throws IOException {
        loadProperties(config);
        props = getProps();
        starNode = new StarMirrorNode(1);
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
    @DisplayName("StarMirrorNode Grundfunktionen")
    class StarMirrorNodeBasicTests {

        @Test
        @DisplayName("StarMirrorNode erbt MirrorNode-Funktionalität")
        void testInheritedMirrorNodeFunctionality() {
            Mirror testMirror = new Mirror(101, 0, props);
            starNode.setMirror(testMirror);

            assertEquals(1, starNode.getId());
            assertEquals(testMirror, starNode.getMirror());
            assertEquals(0, starNode.getNumImplementedLinks());
            assertTrue(starNode.getImplementedLinks().isEmpty());
        }

        @Test
        @DisplayName("StarMirrorNode Konstruktoren")
        void testConstructors() {
            // Standard Konstruktor
            StarMirrorNode node1 = new StarMirrorNode(5);
            assertEquals(5, node1.getId());
            assertNull(node1.getMirror());

            // Konstruktor mit Mirror
            Mirror mirror = new Mirror(102, 0, props);
            StarMirrorNode node2 = new StarMirrorNode(6, mirror);
            assertEquals(6, node2.getId());
            assertEquals(mirror, node2.getMirror());
        }

        @Test
        @DisplayName("canAcceptMoreChildren Stern-spezifische Logik")
        void testCanAcceptMoreChildrenStarSpecific() {
            StarMirrorNode center = new StarMirrorNode(1);
            center.setHead(true);

            // Zentrum ohne gültige Struktur kann keine Kinder akzeptieren
            assertFalse(center.canAcceptMoreChildren());

            // Blatt-Knoten können nie Kinder akzeptieren (nur Zentrum)
            StarMirrorNode leaf = new StarMirrorNode(2);
            assertFalse(leaf.canAcceptMoreChildren());

            // Head-Knoten (nicht Zentrum) können Kinder akzeptieren
            StarMirrorNode headNode = new StarMirrorNode(3);
            headNode.setHead(true);
            center.addChild(headNode);
            assertTrue(headNode.canAcceptMoreChildren());
        }

        @Test
        @DisplayName("canBeRemovedFromStructure Stern-spezifische Validierung")
        void testCanBeRemovedFromStructureStarSpecific() {
            // Erstelle gültigen 4-Knoten-Stern
            StarMirrorNode center = new StarMirrorNode(1);
            StarMirrorNode leaf1 = new StarMirrorNode(2);
            StarMirrorNode leaf2 = new StarMirrorNode(3);
            StarMirrorNode leaf3 = new StarMirrorNode(4);

            center.setHead(true);
            center.addChild(leaf1);
            center.addChild(leaf2);
            center.addChild(leaf3);

            // Mit 4 Knoten kann ein Blatt entfernt werden (mindestens 3 müssen bleiben)
            assertTrue(leaf1.canBeRemovedFromStructure(center));
            assertTrue(leaf2.canBeRemovedFromStructure(center));
            assertTrue(leaf3.canBeRemovedFromStructure(center));

            // Zentrum kann nicht entfernt werden (ist Head und kein Terminal)
            assertFalse(center.canBeRemovedFromStructure(center));

            // Mit nur 3 Knoten kann nichts entfernt werden
            center.removeChild(leaf3);
            assertFalse(leaf1.canBeRemovedFromStructure(center));
            assertFalse(leaf2.canBeRemovedFromStructure(center));
        }

        @Test
        @DisplayName("isCenter und isChildHead Erkennung")
        void testCenterAndChildHeadDetection() {
            StarMirrorNode center = new StarMirrorNode(1);
            StarMirrorNode leaf = new StarMirrorNode(2);
            StarMirrorNode childHead = new StarMirrorNode(3);

            center.setHead(true);
            childHead.setHead(true);
            center.addChild(leaf);
            center.addChild(childHead);

            // Zentrum-Erkennung
            assertTrue(center.isCenter());
            assertFalse(leaf.isCenter());
            assertFalse(childHead.isCenter());

            // Child-Head-Erkennung
            assertFalse(center.isChildHead());
            assertFalse(leaf.isChildHead());
            assertTrue(childHead.isChildHead());
        }
    }

    @Nested
    @DisplayName("StarMirrorNode Struktur-Navigation")
    class StarMirrorNodeNavigationTests {

        private StarMirrorNode center, leaf1, leaf2, childHead1, childHead2;

        @BeforeEach
        void setUpStar() {
            center = new StarMirrorNode(1);
            leaf1 = new StarMirrorNode(2);
            leaf2 = new StarMirrorNode(3);
            childHead1 = new StarMirrorNode(4);
            childHead2 = new StarMirrorNode(5);

            center.setHead(true);
            childHead1.setHead(true);
            childHead2.setHead(true);

            center.addChild(leaf1);
            center.addChild(leaf2);
            center.addChild(childHead1);
            center.addChild(childHead2);
        }

        @Test
        @DisplayName("getCenter findet das Zentrum")
        void testGetCenter() {
            assertEquals(center, leaf1.getCenter());
            assertEquals(center, leaf2.getCenter());
            assertEquals(center, childHead1.getCenter());
            assertEquals(center, center.getCenter());

            // Isolierter Knoten
            StarMirrorNode isolated = new StarMirrorNode(10);
            assertNull(isolated.getCenter());
        }

        @Test
        @DisplayName("getLeaves sammelt echte Blätter")
        void testGetLeaves() {
            List<StarMirrorNode> leaves = center.getLeaves();
            assertEquals(2, leaves.size());
            assertTrue(leaves.contains(leaf1));
            assertTrue(leaves.contains(leaf2));
            assertFalse(leaves.contains(childHead1)); // Head-Nodes sind keine Blätter
            assertFalse(leaves.contains(childHead2));
            assertFalse(leaves.contains(center));

            // Von Blatt aus
            List<StarMirrorNode> leavesFromLeaf = leaf1.getLeaves();
            assertEquals(2, leavesFromLeaf.size());
        }

        @Test
        @DisplayName("getChildHeads sammelt Head-Knoten mit Parent")
        void testGetChildHeads() {
            List<StarMirrorNode> childHeads = center.getChildHeads();
            assertEquals(2, childHeads.size());
            assertTrue(childHeads.contains(childHead1));
            assertTrue(childHeads.contains(childHead2));
            assertFalse(childHeads.contains(leaf1)); // Keine Head-Nodes
            assertFalse(childHeads.contains(leaf2));
            assertFalse(childHeads.contains(center)); // Zentrum hat keinen Parent

            // Von Child-Head aus
            List<StarMirrorNode> childHeadsFromChild = childHead1.getChildHeads();
            assertEquals(2, childHeadsFromChild.size());
        }

        @Test
        @DisplayName("Navigation mit leerer Struktur")
        void testNavigationWithEmptyStructure() {
            StarMirrorNode empty = new StarMirrorNode(10);

            assertNull(empty.getCenter());
            assertTrue(empty.getLeaves().isEmpty());
            assertTrue(empty.getChildHeads().isEmpty());
            assertFalse(empty.isCenter());
            assertFalse(empty.isChildHead());
        }

        @Test
        @DisplayName("Navigation mit einzelnem Knoten")
        void testNavigationWithSingleNode() {
            StarMirrorNode single = new StarMirrorNode(10);
            single.setHead(true);

            assertEquals(single, single.getCenter());
            assertTrue(single.getLeaves().isEmpty()); // Keine echten Blätter
            assertTrue(single.getChildHeads().isEmpty());
            assertTrue(single.isCenter());
            assertFalse(single.isChildHead());
        }
    }

    @Nested
    @DisplayName("StarMirrorNode Struktur-Validierung")
    class StarMirrorNodeValidationTests {

        @Test
        @DisplayName("isValidStructure gültiger 3-Knoten-Stern")
        void testValidStructureThreeNodeStar() {
            StarMirrorNode center = new StarMirrorNode(1);
            StarMirrorNode leaf1 = new StarMirrorNode(2);
            StarMirrorNode leaf2 = new StarMirrorNode(3);

            center.setHead(true);
            center.addChild(leaf1);
            center.addChild(leaf2);

            // Setze Mirrors
            Mirror centerMirror = new Mirror(101, 0, props);
            Mirror leafMirror1 = new Mirror(102, 0, props);
            Mirror leafMirror2 = new Mirror(103, 0, props);
            Mirror externalMirror = new Mirror(104, 0, props);

            center.setMirror(centerMirror);
            leaf1.setMirror(leafMirror1);
            leaf2.setMirror(leafMirror2);

            // Erstelle Stern-Links (Zentrum zu allen Blättern)
            Link link1 = new Link(1, centerMirror, leafMirror1, 0, props);
            Link link2 = new Link(2, centerMirror, leafMirror2, 0, props);

            centerMirror.addLink(link1);
            leafMirror1.addLink(link1);
            centerMirror.addLink(link2);
            leafMirror2.addLink(link2);

            // Erstelle Edge-Link für Zentrum
            Link edgeLink = new Link(3, centerMirror, externalMirror, 0, props);
            centerMirror.addLink(edgeLink);
            externalMirror.addLink(edgeLink);

            Set<StructureNode> starNodes = Set.of(center, leaf1, leaf2);
            assertTrue(center.isValidStructure(starNodes));
        }

        @Test
        @DisplayName("isValidStructure gültiger 5-Knoten-Stern mit Child-Heads")
        void testValidStructureFiveNodeStarWithChildHeads() {
            StarMirrorNode center = new StarMirrorNode(1);
            StarMirrorNode leaf1 = new StarMirrorNode(2);
            StarMirrorNode leaf2 = new StarMirrorNode(3);
            StarMirrorNode childHead1 = new StarMirrorNode(4);
            StarMirrorNode childHead2 = new StarMirrorNode(5);

            center.setHead(true);
            childHead1.setHead(true);
            childHead2.setHead(true);

            center.addChild(leaf1);
            center.addChild(leaf2);
            center.addChild(childHead1);
            center.addChild(childHead2);

            // Setze Mirrors
            Mirror centerMirror = new Mirror(101, 0, props);
            Mirror leafMirror1 = new Mirror(102, 0, props);
            Mirror leafMirror2 = new Mirror(103, 0, props);
            Mirror headMirror1 = new Mirror(104, 0, props);
            Mirror headMirror2 = new Mirror(105, 0, props);
            Mirror externalMirror = new Mirror(106, 0, props);

            center.setMirror(centerMirror);
            leaf1.setMirror(leafMirror1);
            leaf2.setMirror(leafMirror2);
            childHead1.setMirror(headMirror1);
            childHead2.setMirror(headMirror2);

            // Erstelle Stern-Links
            Link link1 = new Link(1, centerMirror, leafMirror1, 0, props);
            Link link2 = new Link(2, centerMirror, leafMirror2, 0, props);
            Link link3 = new Link(3, centerMirror, headMirror1, 0, props);
            Link link4 = new Link(4, centerMirror, headMirror2, 0, props);

            centerMirror.addLink(link1);
            leafMirror1.addLink(link1);
            centerMirror.addLink(link2);
            leafMirror2.addLink(link2);
            centerMirror.addLink(link3);
            headMirror1.addLink(link3);
            centerMirror.addLink(link4);
            headMirror2.addLink(link4);

            // Erstelle Edge-Link für Zentrum
            Link edgeLink = new Link(5, centerMirror, externalMirror, 0, props);
            centerMirror.addLink(edgeLink);
            externalMirror.addLink(edgeLink);

            Set<StructureNode> starNodes = Set.of(center, leaf1, leaf2, childHead1, childHead2);
            assertTrue(center.isValidStructure(starNodes));
        }

        @Test
        @DisplayName("isValidStructure ungültige Strukturen")
        void testInvalidStructures() {
            StarMirrorNode node1 = new StarMirrorNode(1);
            StarMirrorNode node2 = new StarMirrorNode(2);

            // Weniger als 3 Knoten ungültig
            assertFalse(node1.isValidStructure(Set.of(node1)));
            assertFalse(node1.isValidStructure(Set.of(node1, node2)));

            // Ohne Head ungültig
            StarMirrorNode node3 = new StarMirrorNode(3);
            node1.addChild(node2);
            node1.addChild(node3);
            assertFalse(node1.isValidStructure(Set.of(node1, node2, node3)));

            // Mit Head, aber nur einem Kind ungültig
            node1.setHead(true);
            node1.removeChild(node3);
            assertFalse(node1.isValidStructure(Set.of(node1, node2)));

            // Mit Head, aber ohne Edge-Links ungültig
            node1.addChild(node3);
            Mirror mirror1 = new Mirror(101, 0, props);
            Mirror mirror2 = new Mirror(102, 0, props);
            Mirror mirror3 = new Mirror(103, 0, props);

            node1.setMirror(mirror1);
            node2.setMirror(mirror2);
            node3.setMirror(mirror3);

            Link link1 = new Link(1, mirror1, mirror2, 0, props);
            Link link2 = new Link(2, mirror1, mirror3, 0, props);

            mirror1.addLink(link1);
            mirror2.addLink(link1);
            mirror1.addLink(link2);
            mirror3.addLink(link2);

            assertFalse(node1.isValidStructure(Set.of(node1, node2, node3))); // Kein Edge-Link
        }

        @Test
        @DisplayName("isValidStructure gemischte Knotentypen")
        void testMixedNodeTypes() {
            StarMirrorNode starNode = new StarMirrorNode(1);
            MirrorNode regularNode = new MirrorNode(2); // Nicht StarMirrorNode
            StarMirrorNode starNode2 = new StarMirrorNode(3);

            starNode.setHead(true);
            starNode.addChild(regularNode);
            starNode.addChild(starNode2);

            Set<StructureNode> mixedNodes = Set.of(starNode, regularNode, starNode2);
            assertFalse(starNode.isValidStructure(mixedNodes)); // Gemischte Typen ungültig
        }

        @Test
        @DisplayName("isValidStructure mehrere Zentren")
        void testMultipleCenters() {
            StarMirrorNode center1 = new StarMirrorNode(1);
            StarMirrorNode center2 = new StarMirrorNode(2);
            StarMirrorNode leaf = new StarMirrorNode(3);

            // Mehrere Head-Nodes als Zentren (ungültig)
            center1.setHead(true);
            center2.setHead(true);
            center1.addChild(leaf);
            center2.addChild(leaf); // Unmöglich, aber für Test

            Set<StructureNode> invalidCenters = Set.of(center1, center2, leaf);
            assertFalse(center1.isValidStructure(invalidCenters));
        }

        @Test
        @DisplayName("isValidStructure falsche Blatt-Parents")
        void testInvalidLeafParents() {
            StarMirrorNode center = new StarMirrorNode(1);
            StarMirrorNode leaf1 = new StarMirrorNode(2);
            StarMirrorNode leaf2 = new StarMirrorNode(3);

            center.setHead(true);
            center.addChild(leaf1);
            // leaf2 hat falschen Parent (nicht das Zentrum)
            leaf1.addChild(leaf2);

            Set<StructureNode> invalidStar = Set.of(center, leaf1, leaf2);
            assertFalse(center.isValidStructure(invalidStar));
        }
    }

    @Nested
    @DisplayName("StarMirrorNode Integration und Edge Cases")
    class StarMirrorNodeIntegrationTests {

        @Test
        @DisplayName("Integration mit echter Simulation")
        void testIntegrationWithRealSimulation() throws IOException {
            initSimulator();
            sim.initialize(new BalancedTreeTopologyStrategy());
            sim.getEffector().setMirrors(5, 0);

            for(int t = 1; t <= 10; t++) {
                sim.runStep(t);
            }

            MirrorProbe mirrorProbe = getMirrorProbe();
            assertNotNull(mirrorProbe);
            List<Mirror> mirrors = mirrorProbe.getMirrors();
            assertFalse(mirrors.isEmpty());

            // Erstelle StarMirrorNodes mit echten Mirrors
            StarMirrorNode starCenter = new StarMirrorNode(100, mirrors.get(0));
            StarMirrorNode starLeaf = new StarMirrorNode(101);

            if (mirrors.size() > 1) {
                starLeaf.setMirror(mirrors.get(1));
            }

            // Grundlegende Funktionalität sollte funktionieren
            assertEquals(mirrors.get(0), starCenter.getMirror());
            assertNotNull(starCenter.getLeaves());
            assertNotNull(starCenter.getChildHeads());
        }

        @Test
        @DisplayName("Performance mit größeren Sternen")
        void testPerformanceWithLargerStars() {
            StarMirrorNode center = new StarMirrorNode(0);
            center.setHead(true);
            List<StarMirrorNode> leaves = new ArrayList<>();

            // Erstelle Stern mit 50 Blättern
            for (int i = 1; i <= 50; i++) {
                StarMirrorNode leaf = new StarMirrorNode(i);
                leaves.add(leaf);
                center.addChild(leaf);
            }

            long startTime = System.currentTimeMillis();

            // Prüfe Performance-kritische Operationen
            StarMirrorNode foundCenter = leaves.get(0).getCenter();
            List<StarMirrorNode> allLeaves = center.getLeaves();
            List<StarMirrorNode> childHeads = center.getChildHeads();
            boolean isCenter = center.isCenter();
            Set<Mirror> mirrors = center.getMirrorsOfStructure();

            long endTime = System.currentTimeMillis();

            // Sollte schnell sein (< 1000ms für 50 Knoten)
            assertTrue(endTime - startTime < 1000);

            // Korrekte Funktionalität validieren
            assertEquals(center, foundCenter);
            assertEquals(50, allLeaves.size());
            assertTrue(childHeads.isEmpty()); // Keine Child-Heads
            assertTrue(isCenter);
            assertNotNull(mirrors);
        }

        @Test
        @DisplayName("Null-Handling und defensive Programmierung")
        void testNullHandlingAndDefensiveProgramming() {
            StarMirrorNode node = new StarMirrorNode(1);

            // Null-Parameter
            assertFalse(node.canBeRemovedFromStructure(null));

            // Ohne Struktur
            assertNull(node.getCenter());
            assertTrue(node.getLeaves().isEmpty());
            assertTrue(node.getChildHeads().isEmpty());
            assertFalse(node.isCenter());
            assertFalse(node.isChildHead());

            // Mit ungültiger Struktur
            StarMirrorNode other = new StarMirrorNode(2);
            node.addChild(other);
            assertNotNull(node.getLeaves()); // Sollte nicht null sein
        }

        @Test
        @DisplayName("Kompatibilität mit MirrorNode-Funktionen")
        void testMirrorNodeCompatibility() {
            StarMirrorNode starNode = new StarMirrorNode(1);
            Mirror mirror = new Mirror(101, 0, props);
            starNode.setMirror(mirror);

            // Alle MirrorNode-Funktionen sollten verfügbar sein
            assertEquals(0, starNode.getNumImplementedLinks());
            assertEquals(0, starNode.getNumPendingLinks());
            assertTrue(starNode.getImplementedLinks().isEmpty());
            assertNotNull(starNode.getMirrorsOfStructure());
            assertNotNull(starNode.getLinksOfStructure());
            assertEquals(0, starNode.getNumEdgeLinks());

            // Link-Management
            Mirror targetMirror = new Mirror(102, 0, props);
            Link link = new Link(1, mirror, targetMirror, 0, props);
            starNode.addLink(link);
            assertEquals(1, starNode.getNumImplementedLinks());

            starNode.removeLink(link);
            assertEquals(0, starNode.getNumImplementedLinks());
        }

        @Test
        @DisplayName("Stern-Integrity-Validierung")
        void testStarIntegrityValidation() {
            StarMirrorNode center = new StarMirrorNode(1);
            StarMirrorNode leaf1 = new StarMirrorNode(2);
            StarMirrorNode leaf2 = new StarMirrorNode(3);

            center.setHead(true);
            center.addChild(leaf1);
            center.addChild(leaf2);

            // Ohne Mirrors und Links sollte isValidStructure false sein
            Set<StructureNode> starWithoutMirrors = Set.of(center, leaf1, leaf2);
            assertFalse(center.isValidStructure(starWithoutMirrors));
        }

        @Test
        @DisplayName("Zentrum externe Parent-Validierung")
        void testCenterExternalParentValidation() {
            StarMirrorNode center = new StarMirrorNode(1);
            StarMirrorNode leaf1 = new StarMirrorNode(2);
            StarMirrorNode leaf2 = new StarMirrorNode(3);
            MirrorNode externalParent = new MirrorNode(100);

            center.setHead(true);
            center.setParent(externalParent); // Externer Parent
            center.addChild(leaf1);
            center.addChild(leaf2);

            // Zentrum mit externem Parent sollte gültig sein (wenn andere Bedingungen erfüllt)
            Set<StructureNode> starWithExternal = Set.of(center, leaf1, leaf2);

            // Ohne Mirrors und Links wird es false sein, aber die externe Parent-Logik wird geprüft
            assertFalse(center.isValidStructure(starWithExternal));

            // Prüfe Navigation mit externem Parent
            assertEquals(center, leaf1.getCenter());
            assertTrue(center.isCenter());
        }

        @Test
        @DisplayName("Child-Head Navigation und Validierung")
        void testChildHeadNavigationAndValidation() {
            StarMirrorNode center = new StarMirrorNode(1);
            StarMirrorNode leaf = new StarMirrorNode(2);
            StarMirrorNode childHead = new StarMirrorNode(3);
            StarMirrorNode grandchild = new StarMirrorNode(4);

            center.setHead(true);
            childHead.setHead(true);
            center.addChild(leaf);
            center.addChild(childHead);
            childHead.addChild(grandchild);

            // Child-Head kann Kinder akzeptieren
            assertTrue(childHead.canAcceptMoreChildren());

            // Navigation funktioniert
            List<StarMirrorNode> childHeads = center.getChildHeads();
            assertEquals(1, childHeads.size());
            assertTrue(childHeads.contains(childHead));

            List<StarMirrorNode> leaves = center.getLeaves();
            assertEquals(1, leaves.size());
            assertTrue(leaves.contains(leaf));
            assertFalse(leaves.contains(childHead)); // Child-Head ist kein Blatt

            // Child-Head Erkennung
            assertTrue(childHead.isChildHead());
            assertFalse(childHead.isCenter());
            assertFalse(leaf.isChildHead());
        }

        @Test
        @DisplayName("Edge Cases mit einzelnen und leeren Strukturen")
        void testEdgeCasesWithSingleAndEmptyStructures() {
            // Einzelner Knoten als Zentrum
            StarMirrorNode singleCenter = new StarMirrorNode(1);
            singleCenter.setHead(true);

            assertTrue(singleCenter.isCenter());
            assertFalse(singleCenter.isChildHead());
            assertEquals(singleCenter, singleCenter.getCenter());
            assertTrue(singleCenter.getLeaves().isEmpty());
            assertTrue(singleCenter.getChildHeads().isEmpty());

            // Isolierter Knoten
            StarMirrorNode isolated = new StarMirrorNode(2);

            assertFalse(isolated.isCenter());
            assertFalse(isolated.isChildHead());
            assertNull(isolated.getCenter());
            assertTrue(isolated.getLeaves().isEmpty());
            assertTrue(isolated.getChildHeads().isEmpty());
        }
    }
}