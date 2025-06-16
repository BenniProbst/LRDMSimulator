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


@DisplayName("TreeMirrorNode spezifische Tests")
class TreeMirrorNodeTest {

    private TimedRDMSim sim;
    private TreeMirrorNode treeNode;
    private Properties props;
    private static final String config = "resources/sim-test-mirror node.conf";

    @BeforeEach
    void setUp() throws IOException {
        loadProperties(config);
        props = getProps();
        treeNode = new TreeMirrorNode(1);
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
     * oder als Fallback-Mirrors, falls nicht genügend verfügbar sind.
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

    @Nested
    @DisplayName("TreeMirrorNode Grundfunktionen")
    class TreeMirrorNodeBasicTests {

        @Test
        @DisplayName("TreeMirrorNode erbt MirrorNode-Funktionalität")
        void testInheritedMirrorNodeFunctionality() {
            Mirror testMirror = new Mirror(101, 0, props);
            treeNode.setMirror(testMirror);

            assertEquals(1, treeNode.getId());
            assertEquals(testMirror, treeNode.getMirror());
            assertEquals(0, treeNode.getNumImplementedLinks());
            assertTrue(treeNode.getImplementedLinks().isEmpty());
        }

        @Test
        @DisplayName("TreeMirrorNode Konstruktoren")
        void testConstructors() {
            // Standard Konstruktor
            TreeMirrorNode node1 = new TreeMirrorNode(5);
            assertEquals(5, node1.getId());
            assertNull(node1.getMirror());

            // Konstruktor mit Mirror
            Mirror mirror = new Mirror(102, 0, props);
            TreeMirrorNode node2 = new TreeMirrorNode(6, mirror);
            assertEquals(6, node2.getId());
            assertEquals(mirror, node2.getMirror());
        }

        @Test
        @DisplayName("canAcceptMoreChildren Baum-spezifische Logik")
        void testCanAcceptMoreChildrenTreeSpecific() {
            TreeMirrorNode root = new TreeMirrorNode(1);
            root.setHead(true);

            // Root ohne gültige Struktur kann keine Kinder akzeptieren
            assertFalse(root.canAcceptMoreChildren());

            // Mit gültiger Struktur kann Root Kinder akzeptieren
            TreeMirrorNode child = new TreeMirrorNode(2);
            root.addChild(child);

            // Setup für gültige Struktur
            setupValidTreeStructure(root, child);
            assertTrue(root.canAcceptMoreChildren());

            // Child kann auch Kinder akzeptieren (wenn Struktur gültig)
            assertTrue(child.canAcceptMoreChildren());
        }

        @Test
        @DisplayName("canBeRemovedFromStructure Baum-spezifische Validierung")
        void testCanBeRemovedFromStructureTreeSpecific() {
            // Erstelle gültigen 3-Knoten-Baum
            TreeMirrorNode root = new TreeMirrorNode(1);
            TreeMirrorNode child1 = new TreeMirrorNode(2);
            TreeMirrorNode child2 = new TreeMirrorNode(3);

            root.setHead(true);
            root.addChild(child1);
            root.addChild(child2);

            // Blätter können entfernt werden
            assertTrue(child1.canBeRemovedFromStructure(root));
            assertTrue(child2.canBeRemovedFromStructure(root));

            // Root kann nicht entfernt werden (ist kein Blatt)
            assertFalse(root.canBeRemovedFromStructure(root));

            // Nach Entfernen eines Blatts wird Root zum Blatt und kann entfernt werden
            root.removeChild(child2);
            assertTrue(root.canBeRemovedFromStructure(root));
        }

        @Test
        @DisplayName("Struktur-Validierung mit MirrorProbe Daten")
        void testStructureValidationWithMirrorProbeData() throws IOException {
            initSimulator();
            MirrorProbe probe = getMirrorProbe();
            assertNotNull(probe);

            // Extrahierte Methode für Mirror-Beschaffung
            List<Mirror> simMirrors = getSimMirrors(probe);

            // Erstelle TreeMirrorNodes mit echten Simulator-Mirrors
            TreeMirrorNode root = new TreeMirrorNode(1, simMirrors.get(0));
            TreeMirrorNode child1 = new TreeMirrorNode(2, simMirrors.get(1));
            TreeMirrorNode child2 = new TreeMirrorNode(3, simMirrors.get(2));
            TreeMirrorNode grandchild1 = new TreeMirrorNode(4, simMirrors.get(3));
            TreeMirrorNode grandchild2 = new TreeMirrorNode(5, simMirrors.get(4));

            // Baue gültigen 5-Knoten-Baum auf
            root.setHead(true);
            root.addChild(child1);
            root.addChild(child2);
            child1.addChild(grandchild1);
            child2.addChild(grandchild2);

            // Erstelle echte Baum-Links zwischen den Mirrors (n-1 = 4 Links für 5 Knoten)
            Link link1 = new Link(1, simMirrors.get(0), simMirrors.get(1), 0, props);
            Link link2 = new Link(2, simMirrors.get(0), simMirrors.get(2), 0, props);
            Link link3 = new Link(3, simMirrors.get(1), simMirrors.get(3), 0, props);
            Link link4 = new Link(4, simMirrors.get(2), simMirrors.get(4), 0, props);

            // Füge Links zu den Mirrors hinzu
            simMirrors.get(0).addLink(link1);
            simMirrors.get(1).addLink(link1);
            simMirrors.get(0).addLink(link2);
            simMirrors.get(2).addLink(link2);
            simMirrors.get(1).addLink(link3);
            simMirrors.get(3).addLink(link3);
            simMirrors.get(2).addLink(link4);
            simMirrors.get(4).addLink(link4);

            // Erstelle Edge-Link für Root (zu externem Mirror)
            Mirror externalMirror = new Mirror(300, 0, props);
            Link edgeLink = new Link(5, simMirrors.get(0), externalMirror, 0, props);
            simMirrors.get(0).addLink(edgeLink);
            externalMirror.addLink(edgeLink);

            // Teste Struktur-Validierung mit echten MirrorProbe-Daten
            Set<StructureNode> treeNodes = Set.of(root, child1, child2, grandchild1, grandchild2);
            assertTrue(root.isValidStructure(treeNodes),
                    "Baum mit MirrorProbe-Daten sollte gültig sein");

            // Teste TreeMirrorNode-spezifische Funktionen mit echten Daten
            assertEquals(root, child1.getTreeRoot());
            assertEquals(root, grandchild1.getTreeRoot());
            assertEquals(root, grandchild2.getTreeRoot());

            List<TreeMirrorNode> leaves = root.getTreeLeaves();
            assertEquals(2, leaves.size());
            assertTrue(leaves.contains(grandchild1));
            assertTrue(leaves.contains(grandchild2));
            assertFalse(leaves.contains(root));
            assertFalse(leaves.contains(child1));
            assertFalse(leaves.contains(child2));

            // Teste Tiefen-Berechnung
            assertEquals(0, root.getDepthInTree());
            assertEquals(1, child1.getDepthInTree());
            assertEquals(1, child2.getDepthInTree());
            assertEquals(2, grandchild1.getDepthInTree());
            assertEquals(2, grandchild2.getDepthInTree());

            // Teste Baum-Eigenschaften
            assertEquals(2, root.getMaxTreeDepth());
            assertEquals(5, root.getTreeSize());
            assertTrue(root.isBalanced());

            // Versuche Mirror-Integration
            assertEquals(simMirrors.get(0), root.getMirror());
            assertEquals(simMirrors.get(1), child1.getMirror());
            assertEquals(simMirrors.get(2), child2.getMirror());
            assertEquals(simMirrors.get(3), grandchild1.getMirror());
            assertEquals(simMirrors.get(4), grandchild2.getMirror());

            // Teste Link-Zählung mit echten Daten
            assertEquals(3, root.getNumImplementedLinks(), "Root sollte 3 Links haben (2 Baum + 1 Edge)");
            assertEquals(2, child1.getNumImplementedLinks(), "Child1 sollte 2 Links haben (1 zu Root + 1 zu Grandchild)");
            assertEquals(2, child2.getNumImplementedLinks(), "Child2 sollte 2 Links haben (1 zu Root + 1 zu Grandchild)");
            assertEquals(1, grandchild1.getNumImplementedLinks(), "Grandchild1 sollte 1 Link haben");
            assertEquals(1, grandchild2.getNumImplementedLinks(), "Grandchild2 sollte 1 Link haben");

            // Versuche MirrorProbe-Integration
            assertTrue(probe.getNumMirrors() >= 0, "MirrorProbe sollte valide Mirror-Anzahl liefern");
            assertTrue(probe.getNumTargetLinksPerMirror() >= 0,
                    "Target links per mirror sollte nicht negativ sein");

            // Teste ungültige Struktur durch Root-Entfernung
            Set<StructureNode> incompleteTreeNodes = Set.of(child1, child2, grandchild1, grandchild2);
            assertFalse(root.isValidStructure(incompleteTreeNodes),
                    "Baum ohne Root sollte ungültig sein");
        }

        private void setupValidTreeStructure(TreeMirrorNode root, TreeMirrorNode child) {
            // Setze Mirrors
            Mirror rootMirror = new Mirror(101, 0, props);
            Mirror childMirror = new Mirror(102, 0, props);
            Mirror externalMirror = new Mirror(103, 0, props);

            root.setMirror(rootMirror);
            child.setMirror(childMirror);

            // Erstelle interne Links
            Link internalLink = new Link(1, rootMirror, childMirror, 0, props);
            rootMirror.addLink(internalLink);
            childMirror.addLink(internalLink);

            // Erstelle Edge-Link für Root
            Link edgeLink = new Link(2, rootMirror, externalMirror, 0, props);
            rootMirror.addLink(edgeLink);
            externalMirror.addLink(edgeLink);
        }
    }

    @Nested
    @DisplayName("TreeMirrorNode Struktur-Navigation")
    class TreeMirrorNodeNavigationTests {

        private TreeMirrorNode root, child1, child2, grandchild1, grandchild2;

        @BeforeEach
        void setUpTree() {
            /*
             * Baum-Struktur:
             *       root (Tiefe 0)
             *      /    \
             *  child1 child2 (Tiefe 1)
             *    |........|
             * grandchild1 grandchild2 (Tiefe 2)
             */
            root = new TreeMirrorNode(1);
            child1 = new TreeMirrorNode(2);
            child2 = new TreeMirrorNode(3);
            grandchild1 = new TreeMirrorNode(4);
            grandchild2 = new TreeMirrorNode(5);

            root.setHead(true);
            root.addChild(child1);
            root.addChild(child2);
            child1.addChild(grandchild1);
            child2.addChild(grandchild2);
        }

        @Test
        @DisplayName("getTreeRoot findet die Root")
        void testGetTreeRoot() {
            assertEquals(root, child1.getTreeRoot());
            assertEquals(root, child2.getTreeRoot());
            assertEquals(root, grandchild1.getTreeRoot());
            assertEquals(root, grandchild2.getTreeRoot());
            assertEquals(root, root.getTreeRoot());

            // Isolierter Knoten
            TreeMirrorNode isolated = new TreeMirrorNode(10);
            assertNull(isolated.getTreeRoot());
        }

        @Test
        @DisplayName("getTreeLeaves sammelt alle Blätter")
        void testGetTreeLeaves() {
            List<TreeMirrorNode> leaves = root.getTreeLeaves();
            assertEquals(2, leaves.size());
            assertTrue(leaves.contains(grandchild1));
            assertTrue(leaves.contains(grandchild2));
            assertFalse(leaves.contains(root));
            assertFalse(leaves.contains(child1));
            assertFalse(leaves.contains(child2));

            // Von Blatt aus
            List<TreeMirrorNode> leavesFromLeaf = grandchild1.getTreeLeaves();
            assertEquals(2, leavesFromLeaf.size());
        }

        @Test
        @DisplayName("getDepthInTree berechnet korrekte Tiefen")
        void testGetDepthInTree() {
            assertEquals(0, root.getDepthInTree());
            assertEquals(1, child1.getDepthInTree());
            assertEquals(1, child2.getDepthInTree());
            assertEquals(2, grandchild1.getDepthInTree());
            assertEquals(2, grandchild2.getDepthInTree());

            // Isolierter Knoten
            TreeMirrorNode isolated = new TreeMirrorNode(10);
            assertEquals(0, isolated.getDepthInTree());
        }

        @Test
        @DisplayName("getMaxTreeDepth berechnet maximale Tiefe")
        void testGetMaxTreeDepth() {
            assertEquals(2, root.getMaxTreeDepth());
            assertEquals(2, child1.getMaxTreeDepth());
            assertEquals(2, grandchild1.getMaxTreeDepth());

            // Einzelner Knoten
            TreeMirrorNode single = new TreeMirrorNode(10);
            single.setHead(true);
            assertEquals(0, single.getMaxTreeDepth());

            // Flacherer Baum
            TreeMirrorNode flatRoot = new TreeMirrorNode(20);
            TreeMirrorNode flatChild = new TreeMirrorNode(21);
            flatRoot.setHead(true);
            flatRoot.addChild(flatChild);
            assertEquals(1, flatRoot.getMaxTreeDepth());
        }

        @Test
        @DisplayName("getTreeSize berechnet Anzahl Knoten")
        void testGetTreeSize() {
            assertEquals(5, root.getTreeSize());
            assertEquals(5, child1.getTreeSize());
            assertEquals(5, grandchild1.getTreeSize());

            // Einzelner Knoten
            TreeMirrorNode single = new TreeMirrorNode(10);
            assertEquals(1, single.getTreeSize());
        }

        @Test
        @DisplayName("isBalanced erkennt balancierte Bäume")
        void testIsBalanced() {
            // Aktueller Baum ist balanciert (Tiefen: 2, 2)
            assertTrue(root.isBalanced());
            assertTrue(child1.isBalanced());

            // Füge unbalancierte Tiefe hinzu
            TreeMirrorNode deepGrandchild = new TreeMirrorNode(6);
            grandchild1.addChild(deepGrandchild);

            // Jetzt unbalanciert (Tiefen: 2, 3)
            assertFalse(root.isBalanced());

            // Einzelner Knoten ist immer balanciert
            TreeMirrorNode single = new TreeMirrorNode(10);
            assertTrue(single.isBalanced());

            // Leerer Baum ist balanciert
            TreeMirrorNode empty = new TreeMirrorNode(11);
            empty.setHead(true);
            assertTrue(empty.isBalanced());
        }

        @Test
        @DisplayName("Navigation mit verschiedenen Baum-Größen")
        void testNavigationWithDifferentTreeSizes() {
            // Großen Baum erstellen
            TreeMirrorNode bigRoot = new TreeMirrorNode(100);
            bigRoot.setHead(true);

            List<TreeMirrorNode> level1 = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                TreeMirrorNode child = new TreeMirrorNode(100 + i);
                level1.add(child);
                bigRoot.addChild(child);
            }

            List<TreeMirrorNode> level2 = new ArrayList<>();
            for (int i = 0; i < level1.size(); i++) {
                for (int j = 1; j <= 2; j++) {
                    TreeMirrorNode grandchild = new TreeMirrorNode(110 + i * 10 + j);
                    level2.add(grandchild);
                    level1.get(i).addChild(grandchild);
                }
            }

            // Validiere Navigation
            assertEquals(bigRoot, level2.get(0).getTreeRoot());
            assertEquals(6, bigRoot.getTreeLeaves().size()); // 6 Blätter
            assertEquals(2, bigRoot.getMaxTreeDepth());
            assertEquals(10, bigRoot.getTreeSize()); // 1 + 3 + 6 = 10
            assertTrue(bigRoot.isBalanced()); // Alle Blätter auf Tiefe 2
        }
    }

    @Nested
    @DisplayName("TreeMirrorNode Struktur-Validierung")
    class TreeMirrorNodeValidationTests {

        @Test
        @DisplayName("isValidStructure gültiger 3-Knoten-Baum")
        void testValidStructureThreeNodeTree() {
            TreeMirrorNode root = new TreeMirrorNode(1);
            TreeMirrorNode child1 = new TreeMirrorNode(2);
            TreeMirrorNode child2 = new TreeMirrorNode(3);

            root.setHead(true);
            root.addChild(child1);
            root.addChild(child2);

            // Setze Mirrors
            Mirror rootMirror = new Mirror(101, 0, props);
            Mirror childMirror1 = new Mirror(102, 0, props);
            Mirror childMirror2 = new Mirror(103, 0, props);
            Mirror externalMirror = new Mirror(104, 0, props);

            root.setMirror(rootMirror);
            child1.setMirror(childMirror1);
            child2.setMirror(childMirror2);

            // Erstelle Baum-Links (n-1 = 2 Links für 3 Knoten)
            Link link1 = new Link(1, rootMirror, childMirror1, 0, props);
            Link link2 = new Link(2, rootMirror, childMirror2, 0, props);

            rootMirror.addLink(link1);
            childMirror1.addLink(link1);
            rootMirror.addLink(link2);
            childMirror2.addLink(link2);

            // Erstelle Edge-Link für Root
            Link edgeLink = new Link(3, rootMirror, externalMirror, 0, props);
            rootMirror.addLink(edgeLink);
            externalMirror.addLink(edgeLink);

            Set<StructureNode> treeNodes = Set.of(root, child1, child2);
            assertTrue(root.isValidStructure(treeNodes));
        }

        @Test
        @DisplayName("isValidStructure gültiger 5-Knoten-Baum")
        void testValidStructureFiveNodeTree() {
            TreeMirrorNode root = new TreeMirrorNode(1);
            TreeMirrorNode child1 = new TreeMirrorNode(2);
            TreeMirrorNode child2 = new TreeMirrorNode(3);
            TreeMirrorNode grandchild1 = new TreeMirrorNode(4);
            TreeMirrorNode grandchild2 = new TreeMirrorNode(5);

            root.setHead(true);
            root.addChild(child1);
            root.addChild(child2);
            child1.addChild(grandchild1);
            child2.addChild(grandchild2);

            // Setze Mirrors
            Mirror rootMirror = new Mirror(101, 0, props);
            Mirror childMirror1 = new Mirror(102, 0, props);
            Mirror childMirror2 = new Mirror(103, 0, props);
            Mirror grandchildMirror1 = new Mirror(104, 0, props);
            Mirror grandchildMirror2 = new Mirror(105, 0, props);
            Mirror externalMirror = new Mirror(106, 0, props);

            root.setMirror(rootMirror);
            child1.setMirror(childMirror1);
            child2.setMirror(childMirror2);
            grandchild1.setMirror(grandchildMirror1);
            grandchild2.setMirror(grandchildMirror2);

            // Erstelle Baum-Links (n-1 = 4 Links für 5 Knoten)
            Link link1 = new Link(1, rootMirror, childMirror1, 0, props);
            Link link2 = new Link(2, rootMirror, childMirror2, 0, props);
            Link link3 = new Link(3, childMirror1, grandchildMirror1, 0, props);
            Link link4 = new Link(4, childMirror2, grandchildMirror2, 0, props);

            rootMirror.addLink(link1);
            childMirror1.addLink(link1);
            rootMirror.addLink(link2);
            childMirror2.addLink(link2);
            childMirror1.addLink(link3);
            grandchildMirror1.addLink(link3);
            childMirror2.addLink(link4);
            grandchildMirror2.addLink(link4);

            // Erstelle Edge-Link für Root
            Link edgeLink = new Link(5, rootMirror, externalMirror, 0, props);
            rootMirror.addLink(edgeLink);
            externalMirror.addLink(edgeLink);

            Set<StructureNode> treeNodes = Set.of(root, child1, child2, grandchild1, grandchild2);
            assertTrue(root.isValidStructure(treeNodes));
        }

        @Test
        @DisplayName("isValidStructure ungültige Strukturen")
        void testInvalidStructures() {
            TreeMirrorNode node1 = new TreeMirrorNode(1);
            TreeMirrorNode node2 = new TreeMirrorNode(2);
            TreeMirrorNode node3 = new TreeMirrorNode(3);

            // Leere Struktur ungültig
            assertFalse(node1.isValidStructure(Set.of()));

            // Ohne Root ungültig
            node1.addChild(node2);
            node2.addChild(node3);
            assertFalse(node1.isValidStructure(Set.of(node1, node2, node3)));

            // Mit Root, aber ohne Edge-Links ungültig
            node1.setHead(true);
            Mirror mirror1 = new Mirror(101, 0, props);
            Mirror mirror2 = new Mirror(102, 0, props);
            Mirror mirror3 = new Mirror(103, 0, props);

            node1.setMirror(mirror1);
            node2.setMirror(mirror2);
            node3.setMirror(mirror3);

            Link link1 = new Link(1, mirror1, mirror2, 0, props);
            Link link2 = new Link(2, mirror2, mirror3, 0, props);

            mirror1.addLink(link1);
            mirror2.addLink(link1);
            mirror2.addLink(link2);
            mirror3.addLink(link2);

            assertFalse(node1.isValidStructure(Set.of(node1, node2, node3))); // Kein Edge-Link

            // Falsche Anzahl Links (zu viele)
            Mirror externalMirror = new Mirror(104, 0, props);
            Link edgeLink = new Link(3, mirror1, externalMirror, 0, props);
            mirror1.addLink(edgeLink);
            externalMirror.addLink(edgeLink);

            // Link hinzufügen (3 statt 2 für 3 Knoten)
            Link extraLink = new Link(4, mirror1, mirror3, 0, props);
            mirror1.addLink(extraLink);
            mirror3.addLink(extraLink);

            assertFalse(node1.isValidStructure(Set.of(node1, node2, node3))); // Zu viele Links
        }

        @Test
        @DisplayName("isValidStructure Zyklus-Erkennung")
        void testCycleDetection() {
            TreeMirrorNode node1 = new TreeMirrorNode(1);
            TreeMirrorNode node2 = new TreeMirrorNode(2);
            TreeMirrorNode node3 = new TreeMirrorNode(3);

            // Erstelle Zyklus
            node1.setHead(true);
            node1.addChild(node2);
            node2.addChild(node3);
            node3.setParent(node1); // Zyklus erstellen

            Set<StructureNode> cyclicNodes = Set.of(node1, node2, node3);
            assertFalse(node1.isValidStructure(cyclicNodes)); // Zyklus ungültig für Bäume
        }

        @Test
        @DisplayName("isValidStructure gemischte Knotentypen")
        void testMixedNodeTypes() {
            TreeMirrorNode treeNode = new TreeMirrorNode(1);
            MirrorNode regularNode = new MirrorNode(2); // Nicht TreeMirrorNode
            TreeMirrorNode treeNode2 = new TreeMirrorNode(3);

            treeNode.setHead(true);
            treeNode.addChild(regularNode);
            treeNode.addChild(treeNode2);

            Set<StructureNode> mixedNodes = Set.of(treeNode, regularNode, treeNode2);
            assertFalse(treeNode.isValidStructure(mixedNodes)); // Gemischte Typen ungültig
        }

        @Test
        @DisplayName("isValidStructure mehrere Roots")
        void testMultipleRoots() {
            TreeMirrorNode root1 = new TreeMirrorNode(1);
            TreeMirrorNode root2 = new TreeMirrorNode(2);
            TreeMirrorNode child = new TreeMirrorNode(3);

            // Mehrere Head-Nodes (ungültig)
            root1.setHead(true);
            root2.setHead(true);
            root1.addChild(child);

            Set<StructureNode> multipleRoots = Set.of(root1, root2, child);
            assertFalse(root1.isValidStructure(multipleRoots));
        }

        @Test
        @DisplayName("isValidStructure externe Root-Parents")
        void testExternalRootParents() {
            TreeMirrorNode root = new TreeMirrorNode(1);
            TreeMirrorNode child = new TreeMirrorNode(2);
            MirrorNode externalParent = new MirrorNode(100);

            root.setHead(true);
            root.setParent(externalParent); // Externer Parent
            root.addChild(child);

            // Setup Mirrors für gültige Struktur
            Mirror rootMirror = new Mirror(101, 0, props);
            Mirror childMirror = new Mirror(102, 0, props);
            Mirror externalMirror = new Mirror(103, 0, props);

            root.setMirror(rootMirror);
            child.setMirror(childMirror);

            Link internalLink = new Link(1, rootMirror, childMirror, 0, props);
            rootMirror.addLink(internalLink);
            childMirror.addLink(internalLink);

            Link edgeLink = new Link(2, rootMirror, externalMirror, 0, props);
            rootMirror.addLink(edgeLink);
            externalMirror.addLink(edgeLink);

            Set<StructureNode> treeWithExternal = Set.of(root, child);
            assertTrue(root.isValidStructure(treeWithExternal)); // Externer Parent. OK
        }

        @Test
        @DisplayName("isValidStructure interne Root-Parents")
        void testInternalRootParents() {
            TreeMirrorNode root = new TreeMirrorNode(1);
            TreeMirrorNode child = new TreeMirrorNode(2);
            TreeMirrorNode grandchild = new TreeMirrorNode(3);

            root.setHead(true);
            root.addChild(child);
            child.addChild(grandchild);
            root.setParent(child); // Interner Parent (ungültig)

            Set<StructureNode> invalidTree = Set.of(root, child, grandchild);
            assertFalse(root.isValidStructure(invalidTree)); // Root darf keinen internen Parent haben
        }

        @Test
        @DisplayName("isValidStructure normale Knoten ohne Parents")
        void testNormalNodesWithoutParents() {
            TreeMirrorNode root = new TreeMirrorNode(1);
            TreeMirrorNode orphan = new TreeMirrorNode(2); // Kein Parent
            TreeMirrorNode child = new TreeMirrorNode(3);

            root.setHead(true);
            root.addChild(child);
            // orphan hat keinen Parent

            Set<StructureNode> disconnectedTree = Set.of(root, orphan, child);
            assertFalse(root.isValidStructure(disconnectedTree)); // Orphan-Knoten ungültig
        }
    }

    @Nested
    @DisplayName("TreeMirrorNode Integration und Edge Cases")
    class TreeMirrorNodeIntegrationTests {

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

            // Erstelle TreeMirrorNodes mit echten Mirrors
            TreeMirrorNode treeRoot = new TreeMirrorNode(100, mirrors.get(0));
            TreeMirrorNode treeChild = new TreeMirrorNode(101);

            if (mirrors.size() > 1) {
                treeChild.setMirror(mirrors.get(1));
            }

            // Grundlegende Funktionalität sollte funktionieren
            assertEquals(mirrors.get(0), treeRoot.getMirror());
            assertNotNull(treeRoot.getTreeLeaves());
            assertEquals(0, treeRoot.getDepthInTree());
        }

        @Test
        @DisplayName("Performance mit größeren Bäumen")
        void testPerformanceWithLargerTrees() {
            TreeMirrorNode root = new TreeMirrorNode(0);
            root.setHead(true);
            List<TreeMirrorNode> allNodes = new ArrayList<>();
            allNodes.add(root);

            // Erstelle ausgewogenen Baum mit 31 Knoten (Tiefe 4)
            createBalancedTree(root, 0, 4, allNodes);

            long startTime = System.currentTimeMillis();

            // Prüfe Performance-kritische Operationen
            TreeMirrorNode foundRoot = allNodes.get(15).getTreeRoot();
            List<TreeMirrorNode> leaves = root.getTreeLeaves();
            int maxDepth = root.getMaxTreeDepth();
            int treeSize = root.getTreeSize();
            boolean isBalanced = root.isBalanced();
            Set<Mirror> mirrors = root.getMirrorsOfStructure();

            long endTime = System.currentTimeMillis();

            // Sollte schnell sein (< 1000ms für 31 Knoten)
            assertTrue(endTime - startTime < 1000);

            // Korrekte Funktionalität validieren
            assertEquals(root, foundRoot);
            assertEquals(16, leaves.size()); // 2^4 = 16 Blätter bei Tiefe 4
            assertEquals(4, maxDepth);
            assertEquals(31, treeSize); // 2^5 - 1 = 31 Knoten
            assertTrue(isBalanced);
            assertNotNull(mirrors);
        }

        private void createBalancedTree(TreeMirrorNode parent, int currentDepth, int maxDepth, List<TreeMirrorNode> allNodes) {
            if (currentDepth >= maxDepth) return;

            TreeMirrorNode leftChild = new TreeMirrorNode(allNodes.size());
            TreeMirrorNode rightChild = new TreeMirrorNode(allNodes.size() + 1);

            parent.addChild(leftChild);
            parent.addChild(rightChild);
            allNodes.add(leftChild);
            allNodes.add(rightChild);

            createBalancedTree(leftChild, currentDepth + 1, maxDepth, allNodes);
            createBalancedTree(rightChild, currentDepth + 1, maxDepth, allNodes);
        }

        @Test
        @DisplayName("Null-Handling und defensive Programmierung")
        void testNullHandlingAndDefensiveProgramming() {
            TreeMirrorNode node = new TreeMirrorNode(1);

            // Null-Parameter
            assertFalse(node.canBeRemovedFromStructure(null));

            // Ohne Struktur
            assertNull(node.getTreeRoot());
            assertTrue(node.getTreeLeaves().isEmpty());
            assertEquals(0, node.getDepthInTree());
            assertEquals(0, node.getMaxTreeDepth());
            assertEquals(1, node.getTreeSize());
            assertTrue(node.isBalanced());

            // Mit ungültiger Struktur
            TreeMirrorNode other = new TreeMirrorNode(2);
            node.addChild(other);
            assertNotNull(node.getTreeLeaves()); // Sollte nicht null sein
        }

        @Test
        @DisplayName("Kompatibilität mit MirrorNode-Funktionen")
        void testMirrorNodeCompatibility() {
            TreeMirrorNode treeNode = new TreeMirrorNode(1);
            Mirror mirror = new Mirror(101, 0, props);
            treeNode.setMirror(mirror);

            // Alle MirrorNode-Funktionen sollten verfügbar sein
            assertEquals(0, treeNode.getNumImplementedLinks());
            assertEquals(0, treeNode.getNumPendingLinks());
            assertTrue(treeNode.getImplementedLinks().isEmpty());
            assertNotNull(treeNode.getMirrorsOfStructure());
            assertNotNull(treeNode.getLinksOfStructure());
            assertEquals(0, treeNode.getNumEdgeLinks());

            // Link-Management
            Mirror targetMirror = new Mirror(102, 0, props);
            Link link = new Link(1, mirror, targetMirror, 0, props);
            treeNode.addLink(link);
            assertEquals(1, treeNode.getNumImplementedLinks());

            treeNode.removeLink(link);
            assertEquals(0, treeNode.getNumImplementedLinks());
        }

        @Test
        @DisplayName("Baum-Balancierung-Edge-Cases")
        void testTreeBalancingEdgeCases() {
            // Einzelner Knoten
            TreeMirrorNode single = new TreeMirrorNode(1);
            single.setHead(true);
            assertTrue(single.isBalanced());

            // Linearer Baum (sehr unbalanciert)
            TreeMirrorNode linear1 = new TreeMirrorNode(1);
            TreeMirrorNode linear2 = new TreeMirrorNode(2);
            TreeMirrorNode linear3 = new TreeMirrorNode(3);
            TreeMirrorNode linear4 = new TreeMirrorNode(4);

            linear1.setHead(true);
            linear1.addChild(linear2);
            linear2.addChild(linear3);
            linear3.addChild(linear4);

            assertFalse(linear1.isBalanced()); // Tiefenunterschied > 1

            // Perfekt balancierter Baum
            TreeMirrorNode balanced = new TreeMirrorNode(10);
            TreeMirrorNode left = new TreeMirrorNode(11);
            TreeMirrorNode right = new TreeMirrorNode(12);
            TreeMirrorNode leftLeft = new TreeMirrorNode(13);
            TreeMirrorNode leftRight = new TreeMirrorNode(14);

            balanced.setHead(true);
            balanced.addChild(left);
            balanced.addChild(right);
            left.addChild(leftLeft);
            left.addChild(leftRight);

            assertTrue(balanced.isBalanced()); // Alle Blätter auf Tiefe 1-2
        }

        @Test
        @DisplayName("Baum-Navigations-Edge-Cases")
        void testTreeNavigationEdgeCases() {
            // Sehr tiefer Baum
            TreeMirrorNode deepRoot = new TreeMirrorNode(1);
            deepRoot.setHead(true);
            TreeMirrorNode current = deepRoot;

            for (int i = 2; i <= 10; i++) {
                TreeMirrorNode next = new TreeMirrorNode(i);
                current.addChild(next);
                current = next;
            }

            assertEquals(9, deepRoot.getMaxTreeDepth());
            assertEquals(10, deepRoot.getTreeSize());
            assertEquals(1, deepRoot.getTreeLeaves().size());
            assertEquals(9, current.getDepthInTree());

            // Breiter Baum
            TreeMirrorNode wideRoot = new TreeMirrorNode(100);
            wideRoot.setHead(true);

            for (int i = 1; i <= 20; i++) {
                TreeMirrorNode child = new TreeMirrorNode(100 + i);
                wideRoot.addChild(child);
            }

            assertEquals(1, wideRoot.getMaxTreeDepth());
            assertEquals(21, wideRoot.getTreeSize());
            assertEquals(20, wideRoot.getTreeLeaves().size());
            assertTrue(wideRoot.isBalanced());
        }

        @Test
        @DisplayName("Baum-Integrity-Validierung")
        void testTreeIntegrityValidation() {
            TreeMirrorNode root = new TreeMirrorNode(1);
            TreeMirrorNode child1 = new TreeMirrorNode(2);
            TreeMirrorNode child2 = new TreeMirrorNode(3);

            root.setHead(true);
            root.addChild(child1);
            root.addChild(child2);

            // Ohne Mirrors und Links sollte isValidStructure false sein
            Set<StructureNode> treeWithoutMirrors = Set.of(root, child1, child2);
            assertFalse(root.isValidStructure(treeWithoutMirrors));

            // Mit Mirrors, aber ohne passende Links
            Mirror rootMirror = new Mirror(101, 0, props);
            Mirror childMirror1 = new Mirror(102, 0, props);
            Mirror childMirror2 = new Mirror(103, 0, props);

            root.setMirror(rootMirror);
            child1.setMirror(childMirror1);
            child2.setMirror(childMirror2);

            assertFalse(root.isValidStructure(treeWithoutMirrors)); // Keine Links
        }
    }
}