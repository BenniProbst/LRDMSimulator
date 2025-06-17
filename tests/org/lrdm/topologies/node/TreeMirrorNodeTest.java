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

import static org.junit.jupiter.api.Assertions.*;
import static org.lrdm.TestProperties.getProps;
import static org.lrdm.TestProperties.loadProperties;


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
     * oder als Fallback-Mirrors, falls nicht genügend verfügbar ist.
     *
     * @param probe Die MirrorProbe zur Mirror-Beschaffung
     * @return Liste mit mindestens requiredCount Mirrors
     */
    private List<Mirror> getSimMirrors(MirrorProbe probe) {
        List<Mirror> simMirrors = probe.getMirrors();

        // Fallback falls weniger als requiredCount Mirrors verfügbar
        if (simMirrors.size() < 7) {
            List<Mirror> fallbackMirrors = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
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
        @DisplayName("deriveTypeId gibt TREE zurück")
        void testDeriveTypeId() {
            assertEquals(StructureNode.StructureType.TREE, treeNode.deriveTypeId());

            // Auch nach Strukturveränderungen
            TreeMirrorNode child = new TreeMirrorNode(2);
            treeNode.addChild(child);
            assertEquals(StructureNode.StructureType.TREE, treeNode.deriveTypeId());
            assertEquals(StructureNode.StructureType.TREE, child.deriveTypeId());
        }

        @Test
        @DisplayName("canAcceptMoreChildren Baum-spezifische Logik")
        void testCanAcceptMoreChildrenTreeSpecific() {
            TreeMirrorNode root = new TreeMirrorNode(1);
            root.setHead(true);

            // Root ohne gültige Struktur kann keine Kinder akzeptieren
            assertFalse(root.canAcceptMoreChildren());

            // Mit gültiger Struktur kann Root beliebig viele Kinder akzeptieren
            TreeMirrorNode child1 = new TreeMirrorNode(2);
            TreeMirrorNode child2 = new TreeMirrorNode(3);
            root.addChild(child1);
            root.addChild(child2);

            // Setup für gültige Struktur
            setupValidTreeStructure(root, Arrays.asList(child1, child2));
            assertTrue(root.canAcceptMoreChildren());

            // Jeder Knoten im Baum kann weitere Kinder akzeptieren
            assertTrue(child1.canAcceptMoreChildren());
            assertTrue(child2.canAcceptMoreChildren());

            // Auch nach weiteren Ebenen
            TreeMirrorNode grandchild = new TreeMirrorNode(4);
            child1.addChild(grandchild);
            setupValidTreeStructure(root, Arrays.asList(child1, child2, grandchild));
            assertTrue(grandchild.canAcceptMoreChildren());
        }

        @Test
        @DisplayName("canBeRemovedFromStructure Baum-spezifische Validierung")
        void testCanBeRemovedFromStructureTreeSpecific() {
            // Erstelle gültigen 5-Knoten-Baum
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

            setupValidTreeStructure(root, Arrays.asList(child1, child2, grandchild1, grandchild2));

            // Blätter können entfernt werden
            assertTrue(grandchild1.canBeRemovedFromStructure(root));
            assertTrue(grandchild2.canBeRemovedFromStructure(root));

            // Innere Knoten können nicht entfernt werden (würden Teilbäume isolieren)
            assertFalse(child1.canBeRemovedFromStructure(root));
            assertFalse(child2.canBeRemovedFromStructure(root));

            // Root kann nicht entfernt werden
            assertFalse(root.canBeRemovedFromStructure(root));

            // Nach Entfernen aller Grandchildren können die Children entfernt werden
            root.removeChild(child1); // Entfernt auch grandchild1
            root.removeChild(child2); // Entfernt auch grandchild2

            // Jetzt ist root ein isolierter Knoten und kann sich selbst entfernen
            assertTrue(root.canBeRemovedFromStructure(root));
        }

        @Test
        @DisplayName("Struktur-Validierung mit MirrorProbe Daten")
        void testStructureValidationWithMirrorProbeData() throws IOException {
            initSimulator();
            MirrorProbe probe = getMirrorProbe();
            assertNotNull(probe);

            // Verwende extrahierte Methoden für Mirror-Beschaffung
            List<Mirror> simMirrors = getSimMirrors(probe);

            // Erstelle komplexen Baum mit echten Simulator-Mirrors
            TreeMirrorNode root = new TreeMirrorNode(1, simMirrors.get(0));
            TreeMirrorNode child1 = new TreeMirrorNode(2, simMirrors.get(1));
            TreeMirrorNode child2 = new TreeMirrorNode(3, simMirrors.get(2));
            TreeMirrorNode child3 = new TreeMirrorNode(4, simMirrors.get(3));
            TreeMirrorNode grandchild1 = new TreeMirrorNode(5, simMirrors.get(4));
            TreeMirrorNode grandchild2 = new TreeMirrorNode(6, simMirrors.get(5));
            TreeMirrorNode greatGrandchild = new TreeMirrorNode(7, simMirrors.get(6));

            // Baue komplexen 7-Knoten-Baum auf
            root.setHead(true);
            root.addChild(child1);
            root.addChild(child2);
            root.addChild(child3);
            child1.addChild(grandchild1);
            child2.addChild(grandchild2);
            grandchild1.addChild(greatGrandchild);

            // Erstelle Baum-Links zwischen den Mirrors (n-1 = 6 Links für 7 Knoten)
            Link link1 = new Link(1, simMirrors.get(0), simMirrors.get(1), 0, props);
            Link link2 = new Link(2, simMirrors.get(0), simMirrors.get(2), 0, props);
            Link link3 = new Link(3, simMirrors.get(0), simMirrors.get(3), 0, props);
            Link link4 = new Link(4, simMirrors.get(1), simMirrors.get(4), 0, props);
            Link link5 = new Link(5, simMirrors.get(2), simMirrors.get(5), 0, props);
            Link link6 = new Link(6, simMirrors.get(4), simMirrors.get(6), 0, props);

            // Füge Links zu den Mirrors hinzu
            simMirrors.get(0).addLink(link1);
            simMirrors.get(1).addLink(link1);
            simMirrors.get(0).addLink(link2);
            simMirrors.get(2).addLink(link2);
            simMirrors.get(0).addLink(link3);
            simMirrors.get(3).addLink(link3);
            simMirrors.get(1).addLink(link4);
            simMirrors.get(4).addLink(link4);
            simMirrors.get(2).addLink(link5);
            simMirrors.get(5).addLink(link5);
            simMirrors.get(4).addLink(link6);
            simMirrors.get(6).addLink(link6);

            // Erstelle Edge-Link für Root (zu externem Mirror)
            Mirror externalMirror = new Mirror(300, 0, props);
            Link edgeLink = new Link(7, simMirrors.get(0), externalMirror, 0, props);
            simMirrors.get(0).addLink(edgeLink);
            externalMirror.addLink(edgeLink);

            // Teste Struktur-Validierung mit echten MirrorProbe-Daten
            Set<StructureNode> treeNodes = Set.of(root, child1, child2, child3, grandchild1, grandchild2, greatGrandchild);
            assertTrue(root.isValidStructure(treeNodes),
                    "Baum mit MirrorProbe-Daten sollte gültig sein");

            // Teste TreeMirrorNode-spezifische Navigations-Funktionen mit echten Daten
            assertEquals(root, child1.getTreeRoot());
            assertEquals(root, grandchild1.getTreeRoot());
            assertEquals(root, greatGrandchild.getTreeRoot());

            List<TreeMirrorNode> leaves = root.getTreeLeaves();
            assertEquals(3, leaves.size());
            assertTrue(leaves.contains(child3));
            assertTrue(leaves.contains(grandchild2));
            assertTrue(leaves.contains(greatGrandchild));
            assertFalse(leaves.contains(root));
            assertFalse(leaves.contains(child1));
            assertFalse(leaves.contains(child2));

            // Teste Tiefen-Berechnung mit realer Baum-Struktur
            assertEquals(0, root.getDepthInTree());
            assertEquals(1, child1.getDepthInTree());
            assertEquals(1, child2.getDepthInTree());
            assertEquals(1, child3.getDepthInTree());
            assertEquals(2, grandchild1.getDepthInTree());
            assertEquals(2, grandchild2.getDepthInTree());
            assertEquals(3, greatGrandchild.getDepthInTree());

            // Teste Baum-Analyse-Funktionen
            assertEquals(3, root.getMaxTreeDepth());
            assertEquals(7, root.getTreeSize());
            assertFalse(root.isBalanced()); // Unbalanciert: greatGrandchild auf Tiefe 3, child3 auf Tiefe 1

            // Teste Mirror-Integration mit echten Simulator-Daten
            for (int i = 0; i < 7; i++) {
                TreeMirrorNode node = switch (i) {
                    case 0 -> root;
                    case 1 -> child1;
                    case 2 -> child2;
                    case 3 -> child3;
                    case 4 -> grandchild1;
                    case 5 -> grandchild2;
                    case 6 -> greatGrandchild;
                    default -> throw new IllegalStateException("Unexpected value: " + i);
                };
                assertEquals(simMirrors.get(i), node.getMirror());
            }

            // Teste Link-Zählung mit echten Daten
            assertEquals(4, root.getNumImplementedLinks(), "Root sollte 4 Links haben (3 Baum + 1 Edge)");
            assertEquals(2, child1.getNumImplementedLinks(), "Child1 sollte 2 Links haben");
            assertEquals(2, child2.getNumImplementedLinks(), "Child2 sollte 2 Links haben");
            assertEquals(1, child3.getNumImplementedLinks(), "Child3 sollte 1 Link haben (Blatt)");
            assertEquals(2, grandchild1.getNumImplementedLinks(), "Grandchild1 sollte 2 Links haben");
            assertEquals(1, grandchild2.getNumImplementedLinks(), "Grandchild2 sollte 1 Link haben (Blatt)");
            assertEquals(1, greatGrandchild.getNumImplementedLinks(), "GreatGrandchild sollte 1 Link haben (Blatt)");

            // Teste MirrorProbe-spezifische Funktionalität
            assertTrue(probe.getNumMirrors() >= 0, "MirrorProbe sollte valide Mirror-Anzahl liefern");
            assertTrue(probe.getNumTargetLinksPerMirror() >= 0,
                    "Target links per mirror sollte nicht negativ sein");

            // Teste Struktur-Integrität nach Modifikationen
            Set<StructureNode> invalidTreeNodes = Set.of(child1, child2, child3, grandchild1, grandchild2, greatGrandchild);
            assertFalse(root.isValidStructure(invalidTreeNodes),
                    "Baum ohne Root sollte ungültig sein");

            // Teste mit zyklischer Verbindung (ungültig für Bäume)
            Link cyclicLink = new Link(8, simMirrors.get(6), simMirrors.get(0), 0, props);
            simMirrors.get(6).addLink(cyclicLink);
            simMirrors.get(0).addLink(cyclicLink);

            assertFalse(root.isValidStructure(treeNodes),
                    "Baum mit Zyklus sollte ungültig sein");
        }

        private void setupValidTreeStructure(TreeMirrorNode root, List<TreeMirrorNode> children) {
            // Setze Mirrors
            Mirror rootMirror = new Mirror(101, 0, props);
            root.setMirror(rootMirror);

            for (int i = 0; i < children.size(); i++) {
                Mirror childMirror = new Mirror(102 + i, 0, props);
                children.get(i).setMirror(childMirror);

                // Erstelle interne Links
                Link internalLink = new Link(i + 1, rootMirror, childMirror, 0, props);
                rootMirror.addLink(internalLink);
                childMirror.addLink(internalLink);
            }

            // Erstelle Edge-Link für Root
            Mirror externalMirror = new Mirror(200, 0, props);
            Link edgeLink = new Link(100, rootMirror, externalMirror, 0, props);
            rootMirror.addLink(edgeLink);
            externalMirror.addLink(edgeLink);
        }
    }

    @Nested
    @DisplayName("TreeMirrorNode Baum-Navigation")
    class TreeMirrorNodeNavigationTests {

        private TreeMirrorNode root, child1, child2, child3, grandchild1, grandchild2, grandchild3, greatGrandchild;

        @BeforeEach
        void setUpComplexTree() {
            /*
             * Komplexe Baum-Struktur:
             *           root (Tiefe 0)
             *        /    |     \
             *   child1  child2  child3 (Tiefe 1)
             *     |       |
             * grandchild1 grandchild2 grandchild3 (Tiefe 2)
             *     |
             * greatGrandchild (Tiefe 3)
             */
            root = new TreeMirrorNode(1);
            child1 = new TreeMirrorNode(2);
            child2 = new TreeMirrorNode(3);
            child3 = new TreeMirrorNode(4); // Jetzt als Instanzvariable verfügbar
            grandchild1 = new TreeMirrorNode(5);
            grandchild2 = new TreeMirrorNode(6);
            grandchild3 = new TreeMirrorNode(7);
            greatGrandchild = new TreeMirrorNode(8);

            root.setHead(true);
            root.addChild(child1);
            root.addChild(child2);
            root.addChild(child3);
            child1.addChild(grandchild1);
            child2.addChild(grandchild2);
            child2.addChild(grandchild3);
            grandchild1.addChild(greatGrandchild);
        }

        @Test
        @DisplayName("getTreeLeaves sammelt alle Blatt-Knoten")
        void testGetTreeLeaves() {
            List<TreeMirrorNode> leaves = root.getTreeLeaves();
            assertEquals(4, leaves.size());

            // Erwartete Blätter: child3, grandchild2, grandchild3, greatGrandchild
            Set<TreeMirrorNode> expectedLeaves = Set.of(
                    child3,          // Blatt auf Tiefe 1
                    grandchild2,     // Blatt auf Tiefe 2
                    grandchild3,     // Blatt auf Tiefe 2
                    greatGrandchild  // Blatt auf Tiefe 3
            );
            assertTrue(leaves.containsAll(expectedLeaves));

            // Nicht-Blätter sollten nicht enthalten sein
            assertFalse(leaves.contains(root));
            assertFalse(leaves.contains(child1));
            assertFalse(leaves.contains(child2));
            assertFalse(leaves.contains(grandchild1));

            // Von beliebigem Knoten aus sollte das gleiche Ergebnis kommen
            List<TreeMirrorNode> leavesFromChild = child1.getTreeLeaves();
            assertEquals(4, leavesFromChild.size());
            assertTrue(leavesFromChild.containsAll(expectedLeaves));

            // Einzelner Knoten
            TreeMirrorNode single = new TreeMirrorNode(20);
            single.setHead(true);
            assertTrue(single.getTreeLeaves().isEmpty()); // Ein einzelner Head ist kein Blatt
        }


        @Test
        @DisplayName("getDepthInTree berechnet korrekte Tiefen")
        void testGetDepthInTree() {
            assertEquals(0, root.getDepthInTree());
            assertEquals(1, child1.getDepthInTree());
            assertEquals(1, child2.getDepthInTree());
            assertEquals(2, grandchild1.getDepthInTree());
            assertEquals(2, grandchild2.getDepthInTree());
            assertEquals(2, grandchild3.getDepthInTree());
            assertEquals(3, greatGrandchild.getDepthInTree());

            // Isolierter Knoten
            TreeMirrorNode isolated = new TreeMirrorNode(10);
            assertEquals(0, isolated.getDepthInTree());

            // Sehr tiefer linearer Baum
            TreeMirrorNode deepRoot = new TreeMirrorNode(30);
            deepRoot.setHead(true);
            TreeMirrorNode current = deepRoot;

            for (int i = 1; i <= 10; i++) {
                TreeMirrorNode next = new TreeMirrorNode(30 + i);
                current.addChild(next);
                assertEquals(i, next.getDepthInTree());
                current = next;
            }
        }

        @Test
        @DisplayName("getMaxTreeDepth berechnet maximale Tiefe korrekt")
        void testGetMaxTreeDepth() {
            assertEquals(3, root.getMaxTreeDepth());
            assertEquals(3, child1.getMaxTreeDepth()); // Gleich von jedem Knoten
            assertEquals(3, greatGrandchild.getMaxTreeDepth());

            // Einzelner Knoten
            TreeMirrorNode single = new TreeMirrorNode(10);
            single.setHead(true);
            assertEquals(0, single.getMaxTreeDepth());

            // Flacher breiter Baum
            TreeMirrorNode wideRoot = new TreeMirrorNode(20);
            wideRoot.setHead(true);
            for (int i = 1; i <= 10; i++) {
                TreeMirrorNode child = new TreeMirrorNode(20 + i);
                wideRoot.addChild(child);
            }
            assertEquals(1, wideRoot.getMaxTreeDepth());

            // Tiefer linearer Baum
            TreeMirrorNode deepRoot = new TreeMirrorNode(40);
            deepRoot.setHead(true);
            TreeMirrorNode current = deepRoot;
            for (int i = 1; i <= 5; i++) {
                TreeMirrorNode next = new TreeMirrorNode(40 + i);
                current.addChild(next);
                current = next;
            }
            assertEquals(5, deepRoot.getMaxTreeDepth());
        }

        @Test
        @DisplayName("getTreeSize berechnet Knotenzahl korrekt")
        void testGetTreeSize() {
            assertEquals(8, root.getTreeSize()); // 1 root + 3 children + 3 grandchildren + 1 great-grandchild
            assertEquals(8, child1.getTreeSize()); // Gleich von jedem Knoten
            assertEquals(8, greatGrandchild.getTreeSize());

            // Einzelner Knoten
            TreeMirrorNode single = new TreeMirrorNode(10);
            assertEquals(1, single.getTreeSize());

            // Nach Hinzufügen von Kindern
            single.setHead(true);
            TreeMirrorNode child = new TreeMirrorNode(11);
            single.addChild(child);
            assertEquals(2, single.getTreeSize());
            assertEquals(2, child.getTreeSize());
        }

        @Test
        @DisplayName("isBalanced erkennt balancierte und unbalancierte Bäume")
        void testIsBalanced() {
            // Aktueller Baum ist unbalanciert (Tiefen: 1, 2, 2, 3)
            assertFalse(root.isBalanced());
            assertFalse(child1.isBalanced()); // Gleich von jedem Knoten

            // Einzelner Knoten ist immer balanciert
            TreeMirrorNode single = new TreeMirrorNode(10);
            assertTrue(single.isBalanced());

            // Perfekt balancierter binärer Baum
            TreeMirrorNode balancedRoot = new TreeMirrorNode(20);
            TreeMirrorNode left = new TreeMirrorNode(21);
            TreeMirrorNode right = new TreeMirrorNode(22);
            TreeMirrorNode leftLeft = new TreeMirrorNode(23);
            TreeMirrorNode leftRight = new TreeMirrorNode(24);
            TreeMirrorNode rightLeft = new TreeMirrorNode(25);
            TreeMirrorNode rightRight = new TreeMirrorNode(26);

            balancedRoot.setHead(true);
            balancedRoot.addChild(left);
            balancedRoot.addChild(right);
            left.addChild(leftLeft);
            left.addChild(leftRight);
            right.addChild(rightLeft);
            right.addChild(rightRight);

            assertTrue(balancedRoot.isBalanced()); // Alle Blätter auf Tiefe 2

            // Fast-balancierter Baum (Tiefenunterschied = 1)
            TreeMirrorNode almostBalanced = new TreeMirrorNode(30);
            TreeMirrorNode level1_1 = new TreeMirrorNode(31);
            TreeMirrorNode level1_2 = new TreeMirrorNode(32);
            TreeMirrorNode level2_1 = new TreeMirrorNode(33);
            TreeMirrorNode level3_1 = new TreeMirrorNode(34);

            almostBalanced.setHead(true);
            almostBalanced.addChild(level1_1);
            almostBalanced.addChild(level1_2); // Blatt auf Tiefe 1
            level1_1.addChild(level2_1);
            level2_1.addChild(level3_1); // Blatt auf Tiefe 3

            assertFalse(almostBalanced.isBalanced()); // Tiefenunterschied > 1
        }

        @Test
        @DisplayName("Navigation mit speziellen Baum-Strukturen")
        void testNavigationWithSpecialTreeStructures() {
            // Breiter Baum (viele Kinder auf einer Ebene)
            TreeMirrorNode wideRoot = new TreeMirrorNode(50);
            wideRoot.setHead(true);
            List<TreeMirrorNode> children = new ArrayList<>();

            for (int i = 1; i <= 20; i++) {
                TreeMirrorNode child = new TreeMirrorNode(50 + i);
                children.add(child);
                wideRoot.addChild(child);
            }

            assertEquals(wideRoot, children.get(10).getTreeRoot());
            assertEquals(20, wideRoot.getTreeLeaves().size()); // Alle Kinder sind Blätter
            assertEquals(1, wideRoot.getMaxTreeDepth());
            assertEquals(21, wideRoot.getTreeSize()); // 1 + 20
            assertTrue(wideRoot.isBalanced()); // Alle Blätter auf gleicher Tiefe

            // Vollständiger binärer Baum (Tiefe 4)
            TreeMirrorNode binaryRoot = createCompleteBinaryTree(60, 4);
            assertEquals(31, binaryRoot.getTreeSize()); // 2^5 - 1
            assertEquals(4, binaryRoot.getMaxTreeDepth());
            assertEquals(16, binaryRoot.getTreeLeaves().size()); // 2^4 Blätter
            assertTrue(binaryRoot.isBalanced());
        }

        private TreeMirrorNode createCompleteBinaryTree(int baseId, int depth) {
            TreeMirrorNode root = new TreeMirrorNode(baseId);
            root.setHead(true);

            if (depth > 0) {
                TreeMirrorNode left = createCompleteBinaryTree(baseId * 2, depth - 1);
                TreeMirrorNode right = createCompleteBinaryTree(baseId * 2 + 1, depth - 1);
                root.addChild(left);
                root.addChild(right);
            }

            return root;
        }
    }

    @Nested
    @DisplayName("TreeMirrorNode Struktur-Validierung")
    class TreeMirrorNodeValidationTests {

        @Test
        @DisplayName("isValidStructure gültiger einfacher Baum")
        void testValidStructureSimpleTree() {
            TreeMirrorNode root = new TreeMirrorNode(1);
            TreeMirrorNode child1 = new TreeMirrorNode(2);
            TreeMirrorNode child2 = new TreeMirrorNode(3);

            root.setHead(true);
            root.addChild(child1);
            root.addChild(child2);

            setupTreeMirrorsAndLinks(root, Arrays.asList(child1, child2));

            Set<StructureNode> treeNodes = Set.of(root, child1, child2);
            assertTrue(root.isValidStructure(treeNodes));
            assertTrue(root.isValidStructure()); // Convenience-Methode
        }

        @Test
        @DisplayName("isValidStructure gültiger komplexer Baum")
        void testValidStructureComplexTree() {
            TreeMirrorNode root = new TreeMirrorNode(1);
            TreeMirrorNode child1 = new TreeMirrorNode(2);
            TreeMirrorNode child2 = new TreeMirrorNode(3);
            TreeMirrorNode child3 = new TreeMirrorNode(4);
            TreeMirrorNode grandchild1 = new TreeMirrorNode(5);
            TreeMirrorNode grandchild2 = new TreeMirrorNode(6);
            TreeMirrorNode greatGrandchild = new TreeMirrorNode(7);

            // Aufbau der Struktur
            root.setHead(true);
            root.addChild(child1);
            root.addChild(child2);
            root.addChild(child3);
            child1.addChild(grandchild1);
            child2.addChild(grandchild2);
            grandchild1.addChild(greatGrandchild);

            // Setup Mirrors und Links
            setupComplexTreeMirrorsAndLinks(root, Arrays.asList(child1, child2, child3),
                    Arrays.asList(grandchild1, grandchild2), List.of(greatGrandchild));

            Set<StructureNode> treeNodes = Set.of(root, child1, child2, child3, grandchild1, grandchild2, greatGrandchild);
            assertTrue(root.isValidStructure(treeNodes));

            // Test charakteristische Baum-Eigenschaft: n Knoten haben n-1 Kanten
            int nodeCount = treeNodes.size();
            int expectedLinks = nodeCount - 1;

            int actualInternalLinks = treeNodes.stream()
                    .mapToInt(node -> ((TreeMirrorNode) node).getNumImplementedLinks()) // Cast zu TreeMirrorNode
                    .sum() / 2; // Jeder Link wird von beiden Seiten gezählt

            // Abzüglich Edge-Links (nur Root hat Edge-Links)
            int edgeLinks = root.getNumEdgeLinks();
            assertEquals(expectedLinks, actualInternalLinks - edgeLinks);
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

            // Mit Root, aber zyklisch (ungültig für Bäume)
            node1.setHead(true);
            node3.setParent(node1); // Zyklus erstellen

            Mirror mirror1 = new Mirror(101, 0, props);
            Mirror mirror2 = new Mirror(102, 0, props);
            Mirror mirror3 = new Mirror(103, 0, props);
            Mirror externalMirror = new Mirror(104, 0, props);

            node1.setMirror(mirror1);
            node2.setMirror(mirror2);
            node3.setMirror(mirror3);

            // Erstelle zyklische Links
            Link link1 = new Link(1, mirror1, mirror2, 0, props);
            Link link2 = new Link(2, mirror2, mirror3, 0, props);
            Link link3 = new Link(3, mirror3, mirror1, 0, props); // Zyklus-Link

            mirror1.addLink(link1);
            mirror2.addLink(link1);
            mirror2.addLink(link2);
            mirror3.addLink(link2);
            mirror3.addLink(link3);
            mirror1.addLink(link3);

            // Edge-Link für Root
            Link edgeLink = new Link(4, mirror1, externalMirror, 0, props);
            mirror1.addLink(edgeLink);
            externalMirror.addLink(edgeLink);

            assertFalse(node1.isValidStructure(Set.of(node1, node2, node3))); // Zyklus ungültig
        }

        @Test
        @DisplayName("isValidStructure gemischte Knotentypen")
        void testMixedNodeTypes() {
            TreeMirrorNode treeNode = new TreeMirrorNode(1);
            MirrorNode regularNode = new MirrorNode(2);
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
        @DisplayName("isValidStructure n-1 Kanten-Regel")
        void testEdgeCountValidation() {
            TreeMirrorNode root = new TreeMirrorNode(1);
            TreeMirrorNode child1 = new TreeMirrorNode(2);
            TreeMirrorNode child2 = new TreeMirrorNode(3);

            root.setHead(true);
            root.addChild(child1);
            root.addChild(child2);

            // Setup Mirrors
            Mirror rootMirror = new Mirror(101, 0, props);
            Mirror childMirror1 = new Mirror(102, 0, props);
            Mirror childMirror2 = new Mirror(103, 0, props);
            Mirror externalMirror = new Mirror(104, 0, props);

            root.setMirror(rootMirror);
            child1.setMirror(childMirror1);
            child2.setMirror(childMirror2);

            // Zu viele interne Links (3 statt 2 für 3 Knoten)
            Link link1 = new Link(1, rootMirror, childMirror1, 0, props);
            Link link2 = new Link(2, rootMirror, childMirror2, 0, props);
            Link link3 = new Link(3, childMirror1, childMirror2, 0, props); // Extra Link

            rootMirror.addLink(link1);
            childMirror1.addLink(link1);
            rootMirror.addLink(link2);
            childMirror2.addLink(link2);
            childMirror1.addLink(link3);
            childMirror2.addLink(link3);

            // Edge-Link
            Link edgeLink = new Link(4, rootMirror, externalMirror, 0, props);
            rootMirror.addLink(edgeLink);
            externalMirror.addLink(edgeLink);

            Set<StructureNode> treeNodes = Set.of(root, child1, child2);
            assertFalse(root.isValidStructure(treeNodes)); // Zu viele Kanten
        }

        @Test
        @DisplayName("isValidStructure externe vs. interne Parents")
        void testExternalVsInternalParents() {
            TreeMirrorNode root = new TreeMirrorNode(1);
            TreeMirrorNode child = new TreeMirrorNode(2);
            MirrorNode externalParent = new MirrorNode(100);

            root.setHead(true);
            root.setParent(externalParent); // Externer Parent. OK
            root.addChild(child);

            setupTreeMirrorsAndLinks(root, List.of(child));

            Set<StructureNode> treeWithExternal = Set.of(root, child);
            assertTrue(root.isValidStructure(treeWithExternal)); // Externer Parent. OK

            // Interner Parent für Root (ungültig)
            root.setParent(child); // Zyklus erstellen
            assertFalse(root.isValidStructure(treeWithExternal));
        }

        @Test
        @DisplayName("isValidStructure isolierte Knoten")
        void testDisconnectedNodes() {
            TreeMirrorNode root = new TreeMirrorNode(1);
            TreeMirrorNode orphan = new TreeMirrorNode(2);
            TreeMirrorNode child = new TreeMirrorNode(3);

            root.setHead(true);
            root.addChild(child);
            // orphan ist isoliert

            Set<StructureNode> disconnectedTree = Set.of(root, orphan, child);
            assertFalse(root.isValidStructure(disconnectedTree)); // Isolierte Knoten ungültig
        }

        private void setupTreeMirrorsAndLinks(TreeMirrorNode root, List<TreeMirrorNode> children) {
            Mirror rootMirror = new Mirror(101, 0, props);
            root.setMirror(rootMirror);

            // Erstelle Links zu allen Kindern
            for (int i = 0; i < children.size(); i++) {
                TreeMirrorNode child = children.get(i);
                Mirror childMirror = new Mirror(102 + i, 0, props);
                child.setMirror(childMirror);

                Link link = new Link(i + 1, rootMirror, childMirror, 0, props);
                rootMirror.addLink(link);
                childMirror.addLink(link);
            }

            // Edge-Link für Root
            Mirror externalMirror = new Mirror(200, 0, props);
            Link edgeLink = new Link(100, rootMirror, externalMirror, 0, props);
            rootMirror.addLink(edgeLink);
            externalMirror.addLink(edgeLink);
        }

        private void setupComplexTreeMirrorsAndLinks(TreeMirrorNode root, List<TreeMirrorNode> children,
                                                     List<TreeMirrorNode> grandchildren, List<TreeMirrorNode> greatGrandchildren) {

            Mirror rootMirror = new Mirror(101, 0, props);
            root.setMirror(rootMirror);

            int linkId = 1;
            int mirrorId = 102;

            // Links zu direkten Kindern
            for (TreeMirrorNode child : children) {
                Mirror childMirror = new Mirror(mirrorId++, 0, props);
                child.setMirror(childMirror);

                Link link = new Link(linkId++, rootMirror, childMirror, 0, props);
                rootMirror.addLink(link);
                childMirror.addLink(link);
            }

            // Links zu Enkelkindern
            for (int i = 0; i < grandchildren.size(); i++) {
                TreeMirrorNode grandchild = grandchildren.get(i);
                TreeMirrorNode parent = children.get(i < children.size() ? i : 0);

                Mirror grandchildMirror = new Mirror(mirrorId++, 0, props);
                grandchild.setMirror(grandchildMirror);

                Link link = new Link(linkId++, parent.getMirror(), grandchildMirror, 0, props);
                parent.getMirror().addLink(link);
                grandchildMirror.addLink(link);
            }

            // Links zu Urenkelkindern
            for (int i = 0; i < greatGrandchildren.size(); i++) {
                TreeMirrorNode greatGrandchild = greatGrandchildren.get(i);
                TreeMirrorNode parent = grandchildren.get(i < grandchildren.size() ? i : 0);

                Mirror greatGrandchildMirror = new Mirror(mirrorId++, 0, props);
                greatGrandchild.setMirror(greatGrandchildMirror);

                Link link = new Link(linkId++, parent.getMirror(), greatGrandchildMirror, 0, props);
                parent.getMirror().addLink(link);
                greatGrandchildMirror.addLink(link);
            }

            // Edge-Link für Root
            Mirror externalMirror = new Mirror(300, 0, props);
            Link edgeLink = new Link(linkId, rootMirror, externalMirror, 0, props);
            rootMirror.addLink(edgeLink);
            externalMirror.addLink(edgeLink);
        }
    }

    @Nested
    @DisplayName("TreeMirrorNode Performance und Edge Cases")
    class TreeMirrorNodePerformanceTests {

        @Test
        @DisplayName("Performance mit großen Bäumen")
        void testPerformanceWithLargeTrees() {
            TreeMirrorNode root = new TreeMirrorNode(0);
            root.setHead(true);
            List<TreeMirrorNode> allNodes = new ArrayList<>();
            allNodes.add(root);

            // Erstelle ausgewogenen Baum mit 255 Knoten (Tiefe 7)
            createBalancedTree(root, 0, 7, allNodes);

            long startTime = System.currentTimeMillis();

            // Performance-kritische Operationen
            TreeMirrorNode foundRoot = allNodes.get(100).getTreeRoot();
            List<TreeMirrorNode> leaves = root.getTreeLeaves();
            int maxDepth = root.getMaxTreeDepth();
            int treeSize = root.getTreeSize();
            boolean isBalanced = root.isBalanced();

            long endTime = System.currentTimeMillis();

            // Sollte schnell sein (< 2000ms für 255 Knoten)
            assertTrue(endTime - startTime < 2000);

            // Korrekte Funktionalität validieren
            assertEquals(root, foundRoot);
            assertEquals(128, leaves.size()); // 2^7 = 128 Blätter bei Tiefe 7
            assertEquals(7, maxDepth);
            assertEquals(255, treeSize); // 2^8 - 1 = 255 Knoten
            assertTrue(isBalanced);
        }

        @Test
        @DisplayName("Extreme Baum-Strukturen")
        void testExtremeTreeStructures() {
            // Sehr tiefer linearer Baum
            TreeMirrorNode deepRoot = createLinearTree(1000, 50);
            assertEquals(50, deepRoot.getTreeSize());
            assertEquals(49, deepRoot.getMaxTreeDepth());
            assertEquals(1, deepRoot.getTreeLeaves().size());
            assertFalse(deepRoot.isBalanced());

            // Sehr breiter flacher Baum
            TreeMirrorNode wideRoot = createWideTree(2000, 100);
            assertEquals(101, wideRoot.getTreeSize());
            assertEquals(1, wideRoot.getMaxTreeDepth());
            assertEquals(100, wideRoot.getTreeLeaves().size());
            assertTrue(wideRoot.isBalanced());
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
        @DisplayName("Integration mit echter Simulation - komplexer Test")
        void testComplexIntegrationWithRealSimulation() throws IOException {
            initSimulator();
            sim.initialize(new BalancedTreeTopologyStrategy());
            sim.getEffector().setMirrors(10, 0);

            for (int t = 1; t <= 20; t++) {
                sim.runStep(t);
            }

            MirrorProbe mirrorProbe = getMirrorProbe();
            assertNotNull(mirrorProbe);
            List<Mirror> mirrors = mirrorProbe.getMirrors();
            assertFalse(mirrors.isEmpty());

            // Erstelle TreeMirrorNodes mit echten Mirrors
            if (mirrors.size() >= 3) {
                TreeMirrorNode treeRoot = new TreeMirrorNode(100, mirrors.get(0));
                TreeMirrorNode treeChild1 = new TreeMirrorNode(101, mirrors.get(1));
                TreeMirrorNode treeChild2 = new TreeMirrorNode(102, mirrors.get(2));

                treeRoot.setHead(true);
                treeRoot.addChild(treeChild1);
                treeRoot.addChild(treeChild2);

                // Teste TreeMirrorNode-Funktionalität mit echten Mirrors
                assertEquals(mirrors.get(0), treeRoot.getMirror());
                assertEquals(treeRoot, treeChild1.getTreeRoot());
                assertEquals(treeRoot, treeChild2.getTreeRoot());

                assertEquals(0, treeRoot.getDepthInTree());
                assertEquals(1, treeChild1.getDepthInTree());
                assertEquals(1, treeChild2.getDepthInTree());

                assertEquals(3, treeRoot.getTreeSize());
                assertEquals(1, treeRoot.getMaxTreeDepth());
                assertTrue(treeRoot.isBalanced());

                List<TreeMirrorNode> leaves = treeRoot.getTreeLeaves();
                assertEquals(2, leaves.size());
                assertTrue(leaves.contains(treeChild1));
                assertTrue(leaves.contains(treeChild2));
            }
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

        private TreeMirrorNode createLinearTree(int baseId, int size) {
            TreeMirrorNode root = new TreeMirrorNode(baseId);
            root.setHead(true);
            TreeMirrorNode current = root;

            for (int i = 1; i < size; i++) {
                TreeMirrorNode next = new TreeMirrorNode(baseId + i);
                current.addChild(next);
                current = next;
            }

            return root;
        }

        private TreeMirrorNode createWideTree(int baseId, int childCount) {
            TreeMirrorNode root = new TreeMirrorNode(baseId);
            root.setHead(true);

            for (int i = 1; i <= childCount; i++) {
                TreeMirrorNode child = new TreeMirrorNode(baseId + i);
                root.addChild(child);
            }

            return root;
        }
    }
}