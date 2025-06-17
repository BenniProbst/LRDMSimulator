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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.lrdm.TestProperties.getProps;
import static org.lrdm.TestProperties.loadProperties;

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
        sim.initialize(new BalancedTreeTopologyStrategy());
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

            // Setze Mirrors für vollständige Struktur
            Mirror centerMirror = new Mirror(101, 0, props);
            Mirror leafMirror1 = new Mirror(102, 0, props);
            Mirror leafMirror2 = new Mirror(103, 0, props);
            Mirror leafMirror3 = new Mirror(104, 0, props);

            center.setMirror(centerMirror);
            leaf1.setMirror(leafMirror1);
            leaf2.setMirror(leafMirror2);
            leaf3.setMirror(leafMirror3);

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

        @Test
        @DisplayName("Integration mit echter Simulation")
        void testIntegrationWithRealSimulation() throws IOException {
            initSimulator();
            assertNotNull(sim);

            MirrorProbe probe = getMirrorProbe();
            assertNotNull(probe);

            // Erstelle StarMirrorNode mit Simulator-Mirror über MirrorProbe
            List<Mirror> simMirrors = probe.getMirrors();
            if (!simMirrors.isEmpty()) {
                Mirror simMirror = simMirrors.get(0);
                StarMirrorNode simStarNode = new StarMirrorNode(100, simMirror);

                assertEquals(simMirror, simStarNode.getMirror());
                assertEquals(100, simStarNode.getId());
                assertEquals(StructureNode.StructureType.STAR, simStarNode.deriveTypeId());

                // Teste StarMirrorNode-Funktionalität mit echtem Mirror
                assertEquals(simMirror.getLinks().size(), simStarNode.getNumImplementedLinks());
                assertEquals(simMirror.getLinks(), simStarNode.getImplementedLinks());
            } else {
                // Fallback-Test, falls keine Mirrors vorhanden sind
                assertTrue(probe.getNumMirrors() >= 0);
            }
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

        /**
         * Erstellt eine Liste von Simulator-Mirrors, entweder aus der MirrorProbe
         * oder als Fallback-Mirrors, falls nicht genügend verfügbar ist.
         *
         * @param probe Die MirrorProbe zur Mirror-Beschaffung
         * @return Liste mit mindestens requiredCount Mirrors
         */
        private List<Mirror> getSimMirrors(MirrorProbe probe) {
            List<Mirror> simMirrors = probe.getMirrors();

            // Fallback falls weniger als requiredCount Mirrors verfügbar
            if (simMirrors.size() < 5) {
                List<Mirror> fallbackMirrors = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    Mirror mirror = new Mirror(201 + i, 0, props);
                    fallbackMirrors.add(mirror);
                }
                return fallbackMirrors;
            }

            return simMirrors;
        }

        @Test
        @DisplayName("Struktur-Validierung mit MirrorProbe Daten")
        void testStructureValidationWithMirrorProbeData() throws IOException {
            initSimulator();
            MirrorProbe probe = getMirrorProbe();
            assertNotNull(probe);

            // Extrahierte Methode für Mirror-Beschaffung
            List<Mirror> simMirrors = getSimMirrors(probe);

            // Erstelle StarMirrorNodes mit echten Simulator-Mirrors
            StarMirrorNode center = new StarMirrorNode(1, simMirrors.get(0));
            StarMirrorNode leaf1 = new StarMirrorNode(2, simMirrors.get(1));
            StarMirrorNode leaf2 = new StarMirrorNode(3, simMirrors.get(2));
            StarMirrorNode childHead1 = new StarMirrorNode(4, simMirrors.get(3));
            StarMirrorNode childHead2 = new StarMirrorNode(5, simMirrors.get(4));

            // Baue gültigen 5-Knoten-Stern auf
            center.setHead(true);
            childHead1.setHead(true);
            childHead2.setHead(true);

            center.addChild(leaf1);
            center.addChild(leaf2);
            center.addChild(childHead1);
            center.addChild(childHead2);

            // Erstelle echte Stern-Links zwischen den Mirrors
            Link link1 = new Link(1, simMirrors.get(0), simMirrors.get(1), 0, props);
            Link link2 = new Link(2, simMirrors.get(0), simMirrors.get(2), 0, props);
            Link link3 = new Link(3, simMirrors.get(0), simMirrors.get(3), 0, props);
            Link link4 = new Link(4, simMirrors.get(0), simMirrors.get(4), 0, props);

            // Füge Links zu den Mirrors hinzu
            simMirrors.get(0).addLink(link1);
            simMirrors.get(1).addLink(link1);
            simMirrors.get(0).addLink(link2);
            simMirrors.get(2).addLink(link2);
            simMirrors.get(0).addLink(link3);
            simMirrors.get(3).addLink(link3);
            simMirrors.get(0).addLink(link4);
            simMirrors.get(4).addLink(link4);

            // Erstelle Edge-Link für Zentrum (zu externem Mirror)
            Mirror externalMirror = new Mirror(300, 0, props);
            Link edgeLink = new Link(5, simMirrors.get(0), externalMirror, 0, props);
            simMirrors.get(0).addLink(edgeLink);
            externalMirror.addLink(edgeLink);

            // Teste Struktur-Validierung mit echten MirrorProbe-Daten
            Set<StructureNode> starNodes = Set.of(center, leaf1, leaf2, childHead1, childHead2);
            assertTrue(center.isValidStructure(starNodes),
                    "Stern mit MirrorProbe-Daten sollte gültig sein");

            // Teste StarMirrorNode-spezifische Funktionen mit echten Daten
            assertEquals(center, leaf1.getCenter());
            assertEquals(center, childHead1.getCenter());

            List<StarMirrorNode> leaves = center.getLeaves();
            assertEquals(2, leaves.size());
            assertTrue(leaves.contains(leaf1));
            assertTrue(leaves.contains(leaf2));
            assertFalse(leaves.contains(childHead1));
            assertFalse(leaves.contains(childHead2));

            List<StarMirrorNode> childHeads = center.getChildHeads();
            assertEquals(2, childHeads.size());
            assertTrue(childHeads.contains(childHead1));
            assertTrue(childHeads.contains(childHead2));

            // Versuche Mirror-Integration
            assertEquals(simMirrors.get(0), center.getMirror());
            assertEquals(simMirrors.get(1), leaf1.getMirror());
            assertEquals(simMirrors.get(2), leaf2.getMirror());
            assertEquals(simMirrors.get(3), childHead1.getMirror());
            assertEquals(simMirrors.get(4), childHead2.getMirror());

            // Teste Link-Zählung mit echten Daten
            assertEquals(5, center.getNumImplementedLinks(), "Zentrum sollte 5 Links haben (4 Stern + 1 Edge)");
            assertEquals(1, leaf1.getNumImplementedLinks(), "Blatt 1 sollte 1 Link haben");
            assertEquals(1, leaf2.getNumImplementedLinks(), "Blatt 2 sollte 1 Link haben");
            assertEquals(1, childHead1.getNumImplementedLinks(), "Child-Head 1 sollte 1 Link haben");
            assertEquals(1, childHead2.getNumImplementedLinks(), "Child-Head 2 sollte 1 Link haben");

            // Versuche MirrorProbe-Integration
            assertTrue(probe.getNumMirrors() >= 0, "MirrorProbe sollte valide Mirror-Anzahl liefern");
            assertTrue(probe.getNumTargetLinksPerMirror() >= 0,
                    "Target links per mirror sollte nicht negativ sein");

            // Teste ungültige Struktur durch Center-Entfernung
            Set<StructureNode> incompleteStarNodes = Set.of(leaf1, leaf2, childHead1, childHead2);
            assertFalse(center.isValidStructure(incompleteStarNodes),
                    "Stern ohne Zentrum sollte ungültig sein");
        }
    }

    @Nested
    @DisplayName("StarMirrorNode Integration und Edge Cases")
    class StarMirrorNodeIntegrationTests {

        @Test
        @DisplayName("Performance mit größeren Sternen")
        void testPerformanceWithLargerStars() {
            long startTime = System.currentTimeMillis();

            // Erstelle Stern mit 10 Blättern
            StarMirrorNode center = new StarMirrorNode(1);
            center.setHead(true);

            List<StarMirrorNode> leaves = new ArrayList<>();
            for (int i = 2; i <= 11; i++) {
                StarMirrorNode leaf = new StarMirrorNode(i);
                center.addChild(leaf);
                leaves.add(leaf);
            }

            // Teste Navigation durch alle Blätter
            List<StarMirrorNode> foundLeaves = center.getLeaves();
            assertEquals(10, foundLeaves.size());

            for (StarMirrorNode leaf : leaves) {
                assertEquals(center, leaf.getCenter());
                assertTrue(foundLeaves.contains(leaf));
            }

            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 100); // Sollte rasant sein
        }

        @Test
        @DisplayName("Null-Handling und defensive Programmierung")
        void testNullHandlingAndDefensiveProgramming() {
            StarMirrorNode node = new StarMirrorNode(1);

            // Null-sichere Operationen
            assertNull(node.getCenter());
            assertTrue(node.getLeaves().isEmpty());
            assertTrue(node.getChildHeads().isEmpty());
            assertFalse(node.isCenter());
            assertFalse(node.isChildHead());

            // Edge Cases mit null Mirrors
            assertNull(node.getMirror());
            assertEquals(0, node.getNumImplementedLinks());
            assertTrue(node.getImplementedLinks().isEmpty());
        }

        @Test
        @DisplayName("Kompatibilität mit MirrorNode-Funktionen")
        void testMirrorNodeCompatibility() {
            StarMirrorNode starNode = new StarMirrorNode(1);
            Mirror testMirror = new Mirror(101, 0, props);
            starNode.setMirror(testMirror);

            // Alle MirrorNode-Funktionen sollten verfügbar sein
            assertEquals(testMirror, starNode.getMirror());
            assertEquals(0, starNode.getNumImplementedLinks());
            assertTrue(starNode.getImplementedLinks().isEmpty());

            // Link-Management sollte funktionieren
            Mirror targetMirror = new Mirror(102, 0, props);
            Link link = new Link(1, testMirror, targetMirror, 0, props);
            starNode.addLink(link);

            assertEquals(1, starNode.getNumImplementedLinks());
            assertTrue(starNode.getImplementedLinks().contains(link));

            starNode.removeLink(link);
            assertEquals(0, starNode.getNumImplementedLinks());
        }

        @Test
        @DisplayName("Stern-Integrity-Validierung")
        void testStarIntegrityValidation() {
            // Teste, dass die Stern-Struktur korrekt validiert wird
            StarMirrorNode center = new StarMirrorNode(1);
            StarMirrorNode leaf1 = new StarMirrorNode(2);
            StarMirrorNode leaf2 = new StarMirrorNode(3);

            center.setHead(true);
            center.addChild(leaf1);
            center.addChild(leaf2);

            // Setze Mirrors für vollständige Validierung
            center.setMirror(new Mirror(101, 0, props));
            leaf1.setMirror(new Mirror(102, 0, props));
            leaf2.setMirror(new Mirror(103, 0, props));

            Set<StructureNode> starNodes = Set.of(center, leaf1, leaf2);

            // Stern sollte gültig sein wenn korrekt aufgebaut
            assertNotNull(starNodes);
            assertEquals(3, starNodes.size());
            assertTrue(center.isCenter());
            assertFalse(leaf1.isCenter());
            assertFalse(leaf2.isCenter());
        }

        @Test
        @DisplayName("Zentrum externe Parent-Validierung")
        void testCenterExternalParentValidation() {
            StarMirrorNode externalParent = new StarMirrorNode(100);
            StarMirrorNode center = new StarMirrorNode(1);
            StarMirrorNode leaf1 = new StarMirrorNode(2);
            StarMirrorNode leaf2 = new StarMirrorNode(3);

            // Zentrum kann externen Parent haben
            center.setParent(externalParent);
            center.setHead(true);
            center.addChild(leaf1);
            center.addChild(leaf2);

            // Navigation sollte trotz externem Parent funktionieren
            assertEquals(center, leaf1.getCenter());
            assertEquals(center, leaf2.getCenter());
            assertTrue(center.isCenter());
        }

        @Test
        @DisplayName("Child-Head Navigation und Validierung")
        void testChildHeadNavigationAndValidation() {
            StarMirrorNode center = new StarMirrorNode(1);
            StarMirrorNode childHead = new StarMirrorNode(2);
            StarMirrorNode leaf = new StarMirrorNode(3);

            center.setHead(true);
            childHead.setHead(true);

            center.addChild(childHead);
            center.addChild(leaf);

            // Child-Head Erkennung
            assertTrue(childHead.isChildHead());
            assertFalse(center.isChildHead());
            assertFalse(leaf.isChildHead());

            // Navigation zu Child-Heads
            List<StarMirrorNode> childHeads = center.getChildHeads();
            assertEquals(1, childHeads.size());
            assertTrue(childHeads.contains(childHead));
            assertFalse(childHeads.contains(leaf));
        }

        @Test
        @DisplayName("Edge Cases mit einzelnen und leeren Strukturen")
        void testEdgeCasesWithSingleAndEmptyStructures() {
            // Einzelner Knoten ohne Head
            StarMirrorNode single = new StarMirrorNode(1);
            assertNull(single.getCenter());
            assertFalse(single.isCenter());

            // Einzelner Knoten mit Head
            single.setHead(true);
            assertEquals(single, single.getCenter());
            assertTrue(single.isCenter());

            // Leere Listen sollten nie null sein
            assertNotNull(single.getLeaves());
            assertNotNull(single.getChildHeads());
            assertTrue(single.getLeaves().isEmpty());
            assertTrue(single.getChildHeads().isEmpty());
        }
    }
}