
package org.lrdm.topologies.base;

import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.lrdm.TestProperties.loadProperties;

@DisplayName("StructureNode Basis-Funktionalität und erweiterte Features")
class StructureNodeTest {

    private static final String config = "resources/sim-test-structure node.conf";

    @BeforeEach
    void setUpProperties() throws IOException {
        loadProperties(config);
    }

    @Nested
    @DisplayName("StructureNode Grundfunktionen")
    class StructureNodeBasicTests {

        private StructureNode rootNode;
        private StructureNode child1, child2, grandchild;
        private final StructureNode.StructureType defaultType = StructureNode.StructureType.DEFAULT;

        @BeforeEach
        void setUp() {
            rootNode = new StructureNode(1);
            child1 = new StructureNode(2);
            child2 = new StructureNode(3);
            grandchild = new StructureNode(4);

            // Setze rootNode als Head für DEFAULT-Typ
            rootNode.setHead(defaultType, true);

            // Erstelle eine Struktur mit expliziten Multi-Type-Parametern
            Set<StructureNode.StructureType> typeIds = Set.of(defaultType);
            Map<StructureNode.StructureType, Integer> headIds = Map.of(defaultType, rootNode.getId());

            rootNode.addChild(child1, typeIds, headIds);
            rootNode.addChild(child2, typeIds, headIds);
            child1.addChild(grandchild, typeIds, headIds);
        }

        @Test
        @DisplayName("Basis StructureNode Konstruktor und Eigenschaften")
        void testStructureNodeBasics() {
            assertEquals(1, rootNode.getId());
            assertTrue(rootNode.isHead(defaultType));
            assertEquals(0, rootNode.getNumDirectLinksFromStructure());
        }

        @Test
        @DisplayName("Parent-Child Beziehungen mit Multi-Type-System")
        void testParentChildRelationships() {
            assertEquals(rootNode, child1.getParent());
            assertEquals(rootNode, child2.getParent());
            assertEquals(child1, grandchild.getParent());

            // Teste strukturspezifische Kinder-Zugriffe
            assertEquals(2, rootNode.getChildren(defaultType, rootNode.getId()).size());
            assertEquals(1, child1.getChildren(defaultType, rootNode.getId()).size());
            assertEquals(0, child2.getChildren(defaultType, rootNode.getId()).size());
        }

        @Test
        @DisplayName("isLeaf und isTerminal Tests")
        void testNodeTypes() {
            // Root Tests - Root ist Head und hat Kinder
            assertFalse(rootNode.isLeaf());
            assertFalse(rootNode.isTerminal()); // Hat 2 Verbindungen (zu 2 Kindern)

            // Child1 Tests - hat Parent + Kind, also 2 Verbindungen
            assertFalse(child1.isLeaf());
            assertFalse(child1.isTerminal()); // 2 Verbindungen

            // Child2 Tests - hat nur Parent, also 1 Verbindung
            assertTrue(child2.isLeaf());
            assertTrue(child2.isTerminal()); // 1 Verbindung

            // Grandchild Tests - hat nur Parent, also 1 Verbindung
            assertTrue(grandchild.isLeaf());
            assertTrue(grandchild.isTerminal());
        }

        @Test
        @DisplayName("getAllNodesInStructure mit Multi-Type")
        void testGetAllNodesInStructure() {
            // Von rootNode aus: nur rootNode in Struktur (Head-Abgrenzung)
            Set<StructureNode> structureNodes = rootNode.getAllNodesInStructure(defaultType, rootNode);
            assertEquals(1, structureNodes.size());
            assertTrue(structureNodes.contains(rootNode));

            // Von child1 aus sollten alle Knoten außer dem Head gefunden werden
            Set<StructureNode> structureFromChild = child1.getAllNodesInStructure(defaultType, rootNode);
            assertEquals(3, structureFromChild.size()); // child1, child2, grandchild (stoppt bei rootNode als Head)
            assertTrue(structureFromChild.contains(child1));
            assertTrue(structureFromChild.contains(child2));
            assertTrue(structureFromChild.contains(grandchild));
        }

        @Test
        @DisplayName("getEndpointsOfStructure korrekte Terminal-Identifikation")
        void testGetEndpointsOfStructure() {
            // Von rootNode aus: nur rootNode in Struktur (Head-Abgrenzung)
            Set<StructureNode> endpointsFromRoot = rootNode.getEndpointsOfStructure(defaultType, rootNode);
            assertEquals(0, endpointsFromRoot.size()); // rootNode ist nicht Terminal

            // Von child1 aus: child2 und grandchild sind Terminal
            Set<StructureNode> endpointsFromChild = child1.getEndpointsOfStructure(defaultType, rootNode);
            assertEquals(2, endpointsFromChild.size());
            assertTrue(endpointsFromChild.contains(child2));
            assertTrue(endpointsFromChild.contains(grandchild));
        }

        @Test
        @DisplayName("findHead und getPathFromHead Tests")
        void testFindHeadAndPath() {
            // findHead sollte rootNode finden
            StructureNode foundHead = child1.findHead(defaultType);
            assertEquals(rootNode, foundHead);

            StructureNode foundHeadFromGrandchild = grandchild.findHead(defaultType);
            assertEquals(rootNode, foundHeadFromGrandchild);

            // getPathFromHead sollte korrekten Pfad zurückgeben
            List<StructureNode> pathFromHead = grandchild.getPathFromHead(defaultType, rootNode);
            assertEquals(3, pathFromHead.size());
            assertEquals(rootNode, pathFromHead.get(0));
            assertEquals(child1, pathFromHead.get(1));
            assertEquals(grandchild, pathFromHead.get(2));

            // Path von Head zu sich selbst
            List<StructureNode> headPath = rootNode.getPathFromHead(defaultType, rootNode);
            assertEquals(1, headPath.size());
            assertEquals(rootNode, headPath.get(0));
        }

        @Test
        @DisplayName("getNumPlannedLinksFromStructure")
        void testLinkCounting() {
            // getNumPlannedLinksFromStructure() zählt alle Links in der Substruktur
            // von rootNode: nur rootNode in Struktur, also 0 interne Links
            assertEquals(0, rootNode.getNumPlannedLinksFromStructure(defaultType, rootNode));

            // Von child1: child1, child2, grandchild = 3 Knoten = 2 Links
            assertEquals(2, child1.getNumPlannedLinksFromStructure(defaultType, rootNode));

            // DirectLinks per Knoten (Parent + Kinder)
            assertEquals(2, rootNode.getNumDirectLinksFromStructure()); // 2 Kinder
            assertEquals(2, child1.getNumDirectLinksFromStructure()); // 1 Parent + 1 Kind
            assertEquals(1, child2.getNumDirectLinksFromStructure()); // 1 Parent
            assertEquals(1, grandchild.getNumDirectLinksFromStructure()); // 1 Parent
        }

        @Test
        @DisplayName("hasClosedCycle mit Multi-Type-System")
        void testHasClosedCycle() {
            // Normale Baumstruktur hat keinen Zyklus
            Set<StructureNode> allNodes = Set.of(rootNode, child1, child2, grandchild);
            assertFalse(rootNode.hasClosedCycle(allNodes, defaultType, rootNode));

            // Teste auch ohne Zyklen mit getAllNodes
            Set<StructureNode> discoveredNodes = rootNode.getAllNodes();
            assertFalse(rootNode.hasClosedCycle(discoveredNodes, defaultType, rootNode));
        }

        @Test
        @DisplayName("deriveTypeId Override-Funktionalität")
        void testDeriveTypeId() {
            StructureNode defaultNode = new StructureNode(1);
            assertEquals(StructureNode.StructureType.DEFAULT, defaultNode.deriveTypeId());

            // Teste mit anonymer Klasse
            StructureNode customNode = new StructureNode(2) {
                @Override
                protected StructureType deriveTypeId() {
                    return StructureType.TREE;
                }
            };
            assertEquals(StructureNode.StructureType.TREE, customNode.deriveTypeId());
        }

        @Test
        @DisplayName("Maximale Kinder-Limit")
        void testMaxChildrenLimit() {
            StructureNode limitedNode = new StructureNode(1, 1); // Maximal 1 Kind
            limitedNode.setHead(defaultType, true);

            Set<StructureNode.StructureType> typeIds = Set.of(defaultType);
            Map<StructureNode.StructureType, Integer> headIds = Map.of(defaultType, limitedNode.getId());

            limitedNode.addChild(child1, typeIds, headIds);
            assertEquals(1, limitedNode.getChildren().size());

            // Try canAcceptMoreChildren
            assertFalse(limitedNode.canAcceptMoreChildren());
        }
    }

    @Nested
    @DisplayName("ChildRecord und Multi-Type Funktionalität")
    class MultiTypeAdvancedTests {

        @Test
        @DisplayName("ChildRecord Funktionalität")
        void testChildRecord() {
            StructureNode.ChildRecord record = createTestChildRecord();

            assertTrue(record.hasType(StructureNode.StructureType.TREE));
            assertTrue(record.hasType(StructureNode.StructureType.RING));
            assertFalse(record.hasType(StructureNode.StructureType.LINE));

            assertTrue(record.belongsToStructure(StructureNode.StructureType.TREE, 1));
            assertTrue(record.belongsToStructure(StructureNode.StructureType.RING, 2));
            assertFalse(record.belongsToStructure(StructureNode.StructureType.TREE, 2));

            assertEquals(Integer.valueOf(1), record.getHeadId(StructureNode.StructureType.TREE));
            assertEquals(Integer.valueOf(2), record.getHeadId(StructureNode.StructureType.RING));
            assertNull(record.getHeadId(StructureNode.StructureType.LINE));
        }

        private StructureNode.ChildRecord createTestChildRecord() {
            Set<StructureNode.StructureType> types = Set.of(
                    StructureNode.StructureType.TREE,
                    StructureNode.StructureType.RING
            );
            Map<StructureNode.StructureType, Integer> headIds = Map.of(
                    StructureNode.StructureType.TREE, 1,
                    StructureNode.StructureType.RING, 2
            );

            StructureNode child = new StructureNode(10);
            return new StructureNode.ChildRecord(child, types, headIds);
        }

        @Test
        @DisplayName("Multi-Type Head-Status Management")
        void testMultiTypeHeadStatus() {
            StructureNode node = new StructureNode(10);

            // Teste verschiedene Strukturtypen
            node.setHead(StructureNode.StructureType.TREE, true);
            node.setHead(StructureNode.StructureType.RING, false);
            node.setHead(StructureNode.StructureType.LINE, true);

            assertTrue(node.isHead(StructureNode.StructureType.TREE));
            assertFalse(node.isHead(StructureNode.StructureType.RING));
            assertTrue(node.isHead(StructureNode.StructureType.LINE));
        }

        @Test
        @DisplayName("Koexistenz verschiedener Strukturtypen")
        void testCoexistingStructureTypes() {
            StructureNode node1 = new StructureNode(1);
            StructureNode node2 = new StructureNode(2);
            StructureNode node3 = new StructureNode(3);

            // node1 ist Head für beide Strukturtypen
            node1.setHead(StructureNode.StructureType.TREE, true);
            node1.setHead(StructureNode.StructureType.RING, true);

            // Füge Kinder für verschiedene Strukturtypen hinzu
            Set<StructureNode.StructureType> treeTypes = Set.of(StructureNode.StructureType.TREE);
            Set<StructureNode.StructureType> ringTypes = Set.of(StructureNode.StructureType.RING);

            Map<StructureNode.StructureType, Integer> treeHeadIds = Map.of(StructureNode.StructureType.TREE, node1.getId());
            Map<StructureNode.StructureType, Integer> ringHeadIds = Map.of(StructureNode.StructureType.RING, node1.getId());

            node1.addChild(node2, treeTypes, treeHeadIds);
            node1.addChild(node3, ringTypes, ringHeadIds);

            // Teste strukturspezifische Kinder-Zugriffe
            Set<StructureNode> treeChildren = node1.getChildren(StructureNode.StructureType.TREE, node1.getId());
            Set<StructureNode> ringChildren = node1.getChildren(StructureNode.StructureType.RING, node1.getId());

            assertEquals(1, treeChildren.size());
            assertEquals(1, ringChildren.size());
            assertTrue(treeChildren.contains(node2));
            assertTrue(ringChildren.contains(node3));
        }
    }

    @Nested
    @DisplayName("Basis-Tests ohne Multi-Type-Komplexität")
    class SimplifiedBasicTests {

        @Test
        @DisplayName("Einfache Knoten-Erstellung und ID-Zugriff")
        void testSimpleNodeCreation() {
            StructureNode node = new StructureNode(42);
            assertEquals(42, node.getId());
            assertNull(node.getParent());
            assertTrue(node.getChildren().isEmpty());
        }

        @Test
        @DisplayName("LinkPair Record Funktionalität")
        void testLinkPair() {
            StructureNode.LinkPair pair1 = new StructureNode.LinkPair(1, 2);
            StructureNode.LinkPair pair2 = new StructureNode.LinkPair(1, 2);
            StructureNode.LinkPair pair3 = new StructureNode.LinkPair(2, 1);

            assertEquals(pair1, pair2);
            assertNotEquals(pair1, pair3);
            assertEquals(1, pair1.from());
            assertEquals(2, pair1.to());
        }

        @Test
        @DisplayName("StructureType Enum Funktionalität")
        void testStructureTypeEnum() {
            assertEquals(0, StructureNode.StructureType.DEFAULT.getId());
            assertEquals(1, StructureNode.StructureType.MIRROR.getId());
            assertEquals(2, StructureNode.StructureType.TREE.getId());
            assertEquals(3, StructureNode.StructureType.RING.getId());
            assertEquals(4, StructureNode.StructureType.LINE.getId());
            assertEquals(5, StructureNode.StructureType.STAR.getId());
        }

        @Test
        @DisplayName("isRoot und isEndpoint ohne Multi-Type")
        void testBasicNodeProperties() {
            StructureNode isolatedNode = new StructureNode(1);
            assertTrue(isolatedNode.isRoot());
            assertTrue(isolatedNode.isLeaf());
            assertTrue(isolatedNode.isTerminal());
            assertTrue(isolatedNode.isEndpoint());
        }

        @Test
        @DisplayName("getAllNodes einfache Traversierung")
        void testSimpleGetAllNodes() {
            StructureNode node1 = new StructureNode(1);
            StructureNode node2 = new StructureNode(2);

            // Ohne explizite Multi-Type-Parameter (verwendet Standard-Verhalten)
            node1.setParent(node2);

            Set<StructureNode> allNodes = node1.getAllNodes();
            assertEquals(2, allNodes.size());
            assertTrue(allNodes.contains(node1));
            assertTrue(allNodes.contains(node2));
        }
    }
}
