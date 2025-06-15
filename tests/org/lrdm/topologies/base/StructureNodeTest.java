
package org.lrdm.topologies.base;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

import static org.lrdm.TestProperties.loadProperties;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StructureNode Basis-Funktionalität und erweiterte Features")
class StructureNodeTest {

    private static final String config = "resources/sim-test-treenode.conf";

    @BeforeEach
    void setUpProperties() throws IOException {
        loadProperties(config);
    }

    @Nested
    @DisplayName("StructureNode Grundfunktionen")
    class StructureNodeBasicTests {

        private StructureNode rootNode;
        private StructureNode child1, child2, grandchild;

        @BeforeEach
        void setUp() {
            rootNode = new StructureNode(1);
            rootNode.setHead(true);
            child1 = new StructureNode(2);
            child2 = new StructureNode(3);
            grandchild = new StructureNode(4);
        }

        @Test
        @DisplayName("Basis StructureNode Konstruktor und Eigenschaften")
        void testStructureNodeBasics() {
            assertEquals(1, rootNode.getId());
            assertTrue(rootNode.isHead());
            assertTrue(rootNode.isLeaf());
            assertTrue(rootNode.isRoot());
            assertEquals(0, rootNode.getNumDirectLinksFromStructure());
        }

        @Test
        @DisplayName("addChild und Parent-Child Beziehungen")
        void testParentChildRelationships() {
            rootNode.addChild(child1);
            rootNode.addChild(child2);
            child1.addChild(grandchild);

            assertEquals(rootNode, child1.getParent());
            assertEquals(rootNode, child2.getParent());
            assertEquals(child1, grandchild.getParent());
            assertEquals(2, rootNode.getChildren().size());
            assertEquals(1, child1.getChildren().size());
            assertEquals(0, child2.getChildren().size());
        }

        @Test
        @DisplayName("isLeaf, isTerminal und isEndpoint Tests")
        void testNodeTypes() {
            rootNode.addChild(child1);
            rootNode.addChild(child2);
            child1.addChild(grandchild);

            // Root Tests - Root ist Head und hat Kinder
            assertFalse(rootNode.isLeaf());
            assertFalse(rootNode.isTerminal()); // Hat 2 Verbindungen (zu 2 Kindern)
            assertFalse(rootNode.isEndpoint()); // Root mit Kindern ist kein Endpunkt

            // Child1 Tests - hat Parent + Kind, also 2 Verbindungen
            assertFalse(child1.isLeaf());
            assertFalse(child1.isTerminal()); // 2 Verbindungen
            assertFalse(child1.isEndpoint());

            // Child2 Tests - hat nur Parent, also 1 Verbindung
            assertTrue(child2.isLeaf());
            assertTrue(child2.isTerminal()); // 1 Verbindung
            assertTrue(child2.isEndpoint());

            // Grandchild Tests - hat nur Parent, also 1 Verbindung
            assertTrue(grandchild.isLeaf());
            assertTrue(grandchild.isTerminal());
            assertTrue(grandchild.isEndpoint());
        }

        @Test
        @DisplayName("getAllNodes vs getAllNodesInStructure Unterschiede")
        void testGetAllNodesVsStructure() {
            rootNode.addChild(child1);
            rootNode.addChild(child2);
            child1.addChild(grandchild);

            // getAllNodes() - sammelt ALLE verbundenen Knoten (vollständige Traversierung)
            Set<StructureNode> allNodes = rootNode.getAllNodes();
            assertEquals(4, allNodes.size());
            assertTrue(allNodes.contains(rootNode));
            assertTrue(allNodes.contains(child1));
            assertTrue(allNodes.contains(child2));
            assertTrue(allNodes.contains(grandchild));

            // getAllNodesInStructure() - stoppt bei Head-Knoten (Substruktur-Abgrenzung),
            // da rootNode Head ist, wird nur rootNode zurückgegeben
            Set<StructureNode> structureNodes = rootNode.getAllNodesInStructure();
            assertEquals(1, structureNodes.size());
            assertTrue(structureNodes.contains(rootNode));

            // Von child1 aus sollten alle Knoten außer dem Head gefunden werden
            Set<StructureNode> structureFromChild = child1.getAllNodesInStructure();
            assertEquals(3, structureFromChild.size()); // child1, child2, grandchild (stoppt bei rootNode als Head)
        }

        @Test
        @DisplayName("getEndpointsOfStructure korrekte Terminal-Identifikation")
        void testGetEndpointsOfStructure() {
            rootNode.addChild(child1);
            rootNode.addChild(child2);
            child1.addChild(grandchild);

            // Von rootNode aus: nur rootNode in Struktur (Head-Abgrenzung)
            Set<StructureNode> endpointsFromRoot = rootNode.getEndpointsOfStructure();
            assertEquals(0, endpointsFromRoot.size()); // rootNode ist nicht Terminal

            // Von child1 aus: child2 und grandchild sind Terminal
            Set<StructureNode> endpointsFromChild = child1.getEndpointsOfStructure();
            assertEquals(2, endpointsFromChild.size());
            assertTrue(endpointsFromChild.contains(child2));
            assertTrue(endpointsFromChild.contains(grandchild));
        }

        @Test
        @DisplayName("findHead und getPathFromHead Tests")
        void testFindHeadAndPath() {
            rootNode.addChild(child1);
            child1.addChild(grandchild);

            // findHead sollte rootNode finden
            StructureNode foundHead = child1.findHead();
            assertEquals(rootNode, foundHead);

            StructureNode foundHeadFromGrandchild = grandchild.findHead();
            assertEquals(rootNode, foundHeadFromGrandchild);

            // getPathFromHead sollte korrekten Pfad zurückgeben
            List<StructureNode> pathFromHead = grandchild.getPathFromHead();
            assertEquals(3, pathFromHead.size());
            assertEquals(rootNode, pathFromHead.get(0));
            assertEquals(child1, pathFromHead.get(1));
            assertEquals(grandchild, pathFromHead.get(2));

            // Path von Head zu sich selbst
            List<StructureNode> headPath = rootNode.getPathFromHead();
            assertEquals(1, headPath.size());
            assertEquals(rootNode, headPath.get(0));
        }

        @Test
        @DisplayName("getNumPlannedLinksFromStructure und getNumDirectLinksFromStructure")
        void testLinkCounting() {
            rootNode.addChild(child1);
            rootNode.addChild(child2);
            child1.addChild(grandchild);

            // getNumPlannedLinksFromStructure() zählt alle Links in der Substruktur
            // von rootNode: nur rootNode in Struktur, also 0 interne Links
            assertEquals(0, rootNode.getNumPlannedLinksFromStructure());

            // Von child1: child1, child2, grandchild = 3 Knoten = 2 Links
            assertEquals(2, child1.getNumPlannedLinksFromStructure());

            // DirectLinks per Knoten (Parent + Kinder)
            assertEquals(2, rootNode.getNumDirectLinksFromStructure()); // 2 Kinder
            assertEquals(2, child1.getNumDirectLinksFromStructure()); // 1 Parent + 1 Kind
            assertEquals(1, child2.getNumDirectLinksFromStructure()); // 1 Parent
            assertEquals(1, grandchild.getNumDirectLinksFromStructure()); // 1 Parent
        }

        @Test
        @DisplayName("canBeRemovedFromStructure Tests")
        void testCanBeRemovedFromStructure() {
            rootNode.addChild(child1);
            rootNode.addChild(child2);
            child1.addChild(grandchild);

            // canBeRemovedFromStructure prüft, ob Knoten Blatt ist und Teil der Struktur
            // rootNode ist Head, also separate Struktur
            assertFalse(rootNode.canBeRemovedFromStructure(rootNode));

            // child1 hat Kinder, also kein Blatt
            assertFalse(child1.canBeRemovedFromStructure(child1));

            // child2 und grandchild sind Blätter
            assertTrue(child2.canBeRemovedFromStructure(child1)); // child2 ist Teil von child1 Struktur
            assertTrue(grandchild.canBeRemovedFromStructure(child1)); // grandchild ist Teil von child1's Struktur
        }

        @Test
        @DisplayName("hasClosedCycle statische Hilfsfunktion")
        void testHasClosedCycle() {
            // Erstelle einen einfachen Zyklus
            StructureNode node1 = new StructureNode(10);
            StructureNode node2 = new StructureNode(11);
            StructureNode node3 = new StructureNode(12);

            node1.addChild(node2);
            node2.addChild(node3);
            node3.setParent(node1); // Manuell setzen für Zyklus (vorsichtig!)

            Set<StructureNode> cycleNodes = Set.of(node1, node2, node3);
            assertTrue(StructureNode.hasClosedCycle(cycleNodes));

            // Normale Baumstruktur hat keinen Zyklus
            Set<StructureNode> treeNodes = Set.of(rootNode, child1, child2, grandchild);
            assertFalse(StructureNode.hasClosedCycle(treeNodes));
        }

        @Test
        @DisplayName("Strukturmitgliedschaft und Endpunkt-Tests")
        void testStructureMembership() {
            rootNode.addChild(child1);
            child1.addChild(grandchild);

            StructureNode outsideNode = new StructureNode(99);

            // Test der Struktur-Zugehörigkeit basierend auf getAllNodesInStructure()
            Set<StructureNode> child1Structure = child1.getAllNodesInStructure();
            assertTrue(child1Structure.contains(child1));
            assertTrue(child1Structure.contains(grandchild));
            assertFalse(child1Structure.contains(rootNode)); // Head-Abgrenzung
            assertFalse(child1Structure.contains(outsideNode));

            // Endpunkt-Tests
            assertTrue(grandchild.isTerminal());
            assertFalse(child1.isTerminal());
        }

        @Test
        @DisplayName("getDescendantCount sollte alle Nachfahren zählen")
        void testGetDescendantCount() {
            rootNode.addChild(child1);
            rootNode.addChild(child2);
            child1.addChild(grandchild);

            assertEquals(3, rootNode.getDescendantCount());
            assertEquals(1, child1.getDescendantCount());
            assertEquals(0, child2.getDescendantCount());
            assertEquals(0, grandchild.getDescendantCount());
        }

        @Test
        @DisplayName("findNodeById sollte Knoten korrekt finden")
        void testFindNodeById() {
            rootNode.addChild(child1);
            rootNode.addChild(child2);
            child1.addChild(grandchild);

            assertEquals(rootNode, rootNode.findNodeById(1));
            assertEquals(child1, rootNode.findNodeById(2));
            assertEquals(child2, rootNode.findNodeById(3));
            assertEquals(grandchild, rootNode.findNodeById(4));
            assertNull(rootNode.findNodeById(999));
        }

        @Test
        @DisplayName("equals und hashCode funktionieren korrekt")
        void testEqualsAndHashCode() {
            StructureNode node1 = new StructureNode(1);
            StructureNode node2 = new StructureNode(1);
            StructureNode node3 = new StructureNode(2);

            assertEquals(node1, node2);
            assertNotEquals(node1, node3);
            assertEquals(node1.hashCode(), node2.hashCode());
            assertNotEquals(node1.hashCode(), node3.hashCode());
        }
    }

    @Nested
    @DisplayName("StructureNode Validierung und Strukturprüfung")
    class StructureNodeValidationTests {

        @Test
        @DisplayName("isValidStructure mit verschiedenen Strukturen")
        void testStructureValidation() {
            StructureNode root = new StructureNode(1);
            StructureNode child1 = new StructureNode(2);
            StructureNode child2 = new StructureNode(3);

            // Einzelner Knoten ist gültig
            assertTrue(root.isValidStructure(Set.of(root)));

            // Verbundene Struktur ist gültig
            root.addChild(child1);
            root.addChild(child2);
            assertTrue(root.isValidStructure(Set.of(root, child1, child2)));

            // Isolierter Knoten macht Struktur ungültig
            StructureNode isolated = new StructureNode(4);
            assertFalse(root.isValidStructure(Set.of(root, child1, child2, isolated)));

            // Leere Menge ist ungültig
            assertFalse(root.isValidStructure(new HashSet<>()));
            assertFalse(root.isValidStructure(null));
        }

        @Test
        @DisplayName("canAcceptMoreChildren mit maxChildren")
        void testCanAcceptMoreChildren() {
            StructureNode limitedNode = new StructureNode(1, 2); // Max. 2 Kinder
            StructureNode child1 = new StructureNode(2);
            StructureNode child2 = new StructureNode(3);

            assertTrue(limitedNode.canAcceptMoreChildren());

            limitedNode.addChild(child1);
            assertTrue(limitedNode.canAcceptMoreChildren());

            limitedNode.addChild(child2);
            assertFalse(limitedNode.canAcceptMoreChildren());

            // Nach Hinzufügung von 2 Kindern sollten es genau 2 sein
            assertEquals(2, limitedNode.getChildren().size());
        }

        @Test
        @DisplayName("Head-Node Substruktur-Abgrenzung")
        void testHeadNodeStructureBoundary() {
            StructureNode root = new StructureNode(1);
            StructureNode child = new StructureNode(2);
            StructureNode grandchild = new StructureNode(3);

            root.setHead(true);
            root.addChild(child);
            child.addChild(grandchild);

            // Von root aus: nur root in Struktur (Head-Abgrenzung)
            Set<StructureNode> rootStructure = root.getAllNodesInStructure();
            assertEquals(1, rootStructure.size());
            assertTrue(rootStructure.contains(root));

            // Von child aus: child und grandchild (stoppt bei root als Head)
            Set<StructureNode> childStructure = child.getAllNodesInStructure();
            assertEquals(2, childStructure.size());
            assertTrue(childStructure.contains(child));
            assertTrue(childStructure.contains(grandchild));
            assertFalse(childStructure.contains(root)); // Head-Abgrenzung
        }
    }

    @Nested
    @DisplayName("StructureNode erweiterte Funktionen")
    class StructureNodeAdvancedTests {

        @Test
        @DisplayName("Terminal- und Endpunkt-Erkennung in komplexen Strukturen")
        void testComplexTerminalDetection() {
            StructureNode root = new StructureNode(1);
            StructureNode child1 = new StructureNode(2);
            StructureNode child2 = new StructureNode(3);
            StructureNode grandchild1 = new StructureNode(4);
            StructureNode grandchild2 = new StructureNode(5);

            root.setHead(true);
            root.addChild(child1);
            root.addChild(child2);
            child1.addChild(grandchild1);
            child1.addChild(grandchild2);

            // Terminal-Tests (genau 1 Verbindung)
            assertFalse(root.isTerminal()); // 2 Kinder
            assertFalse(child1.isTerminal()); // 1 Parent + 2 Kinder = 3 Verbindungen
            assertTrue(child2.isTerminal()); // 1 Parent
            assertTrue(grandchild1.isTerminal()); // 1 Parent
            assertTrue(grandchild2.isTerminal()); // 1 Parent

            // Endpunkt-Tests
            assertFalse(root.isEndpoint()); // Root mit Kindern
            assertFalse(child1.isEndpoint()); // Nicht Terminal
            assertTrue(child2.isEndpoint()); // Terminal
            assertTrue(grandchild1.isEndpoint()); // Terminal
            assertTrue(grandchild2.isEndpoint()); // Terminal
        }

        @Test
        @DisplayName("Head-Node-Funktionalität mit mehreren Heads")
        void testMultipleHeadNodes() {
            StructureNode head1 = new StructureNode(1);
            StructureNode head2 = new StructureNode(2);
            StructureNode child = new StructureNode(3);

            head1.setHead(true);
            head2.setHead(true);
            head1.addChild(child);

            // findHead sollte einen Head finden
            StructureNode foundHead = child.findHead();
            assertTrue(foundHead.isHead());
            assertTrue(foundHead == head1 || foundHead == head2);
        }

        @Test
        @DisplayName("Pfad-Berechnung in verzweigten Strukturen")
        void testPathCalculationInBranchedStructures() {
            StructureNode root = new StructureNode(1);
            StructureNode left = new StructureNode(2);
            StructureNode right = new StructureNode(3);
            StructureNode leftChild = new StructureNode(4);

            root.setHead(true);
            root.addChild(left);
            root.addChild(right);
            left.addChild(leftChild);

            // Pfad von leftChild zum Head
            List<StructureNode> path = leftChild.getPathFromHead();
            assertEquals(3, path.size());
            assertEquals(root, path.get(0));
            assertEquals(left, path.get(1));
            assertEquals(leftChild, path.get(2));

            // Pfad von right zum Head
            List<StructureNode> rightPath = right.getPathFromHead();
            assertEquals(2, rightPath.size());
            assertEquals(root, rightPath.get(0));
            assertEquals(right, rightPath.get(1));
        }

        @Test
        @DisplayName("LinkPair Klasse und Funktionalität")
        void testLinkPairFunctionality() {
            // Test der LinkPair record Klasse
            StructureNode.LinkPair pair1 = new StructureNode.LinkPair(1, 2);
            StructureNode.LinkPair pair2 = new StructureNode.LinkPair(1, 2);
            StructureNode.LinkPair pair3 = new StructureNode.LinkPair(2, 1);

            assertEquals(pair1, pair2);
            assertNotEquals(pair1, pair3); // Reihenfolge ist wichtig
            assertEquals(pair1.hashCode(), pair2.hashCode());

            assertEquals(1, pair1.from());
            assertEquals(2, pair1.to());
        }

        @Test
        @DisplayName("MaxChildren Grenzen und Validierung")
        void testMaxChildrenConstraints() {
            // Test setMaxChildren mit verschiedenen Werten
            StructureNode node = new StructureNode(1);

            // Standard ist unbegrenzt
            assertTrue(node.canAcceptMoreChildren());

            // Setze auf 1
            node.setMaxChildren(1);
            assertTrue(node.canAcceptMoreChildren());

            // Füge Kind hinzu
            StructureNode child = new StructureNode(2);
            node.addChild(child);
            assertFalse(node.canAcceptMoreChildren());

            // Negative Werte sollten auf 0 begrenzt werden
            node.setMaxChildren(-5);
            assertFalse(node.canAcceptMoreChildren());
        }

        @Test
        @DisplayName("removeChild und Parent-Beziehungen")
        void testRemoveChildFunctionality() {
            StructureNode parent = new StructureNode(1);
            StructureNode child1 = new StructureNode(2);
            StructureNode child2 = new StructureNode(3);

            parent.addChild(child1);
            parent.addChild(child2);

            assertEquals(2, parent.getChildren().size());
            assertEquals(parent, child1.getParent());

            // Entferne child1
            parent.removeChild(child1);
            assertEquals(1, parent.getChildren().size());
            assertNull(child1.getParent());
            assertFalse(parent.getChildren().contains(child1));

            // removeChild mit null sollte sicher sein
            parent.removeChild(null);
            assertEquals(1, parent.getChildren().size());
        }

        @Test
        @DisplayName("Root-Blatt als Endpunkt testen")
        void testRootLeafEndpoint() {
            StructureNode singleRoot = new StructureNode(1);
            singleRoot.setHead(true);

            // Ein einzelner Root-Knoten ohne Kinder
            assertTrue(singleRoot.isRoot());
            assertTrue(singleRoot.isLeaf());
            assertTrue(singleRoot.isEndpoint()); // Root-Blatt ist Endpunkt

            // Füge Kind hinzu - Root ist kein Blatt mehr
            StructureNode child = new StructureNode(2);
            singleRoot.addChild(child);

            assertTrue(singleRoot.isRoot());
            assertFalse(singleRoot.isLeaf());
            assertFalse(singleRoot.isEndpoint()); // Root mit Kindern ist kein Endpunkt
        }
    }
}