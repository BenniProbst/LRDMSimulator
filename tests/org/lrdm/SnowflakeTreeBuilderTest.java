package org.lrdm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.lrdm.util.SnowflakeStarTreeNode;
import org.lrdm.util.SnowflakeTreeBuilder;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

@DisplayName("SnowflakeTreeBuilder Tests")
class SnowflakeTreeBuilderTest {

    private SnowflakeTreeBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new SnowflakeTreeBuilder();
    }

    @Test
    @DisplayName("buildTree mit null oder negativen Werten")
    void testBuildTreeEdgeCases() {
        assertNull(builder.buildTree(0, 3));
        assertNull(builder.buildTree(-1, 3));
    }

    @Test
    @DisplayName("buildTree mit einem Knoten")
    void testBuildTreeSingleNode() {
        SnowflakeStarTreeNode root = builder.buildTree(1, 3);
        
        assertNotNull(root);
        assertEquals(1, root.getId());
        assertEquals(0, root.getDepth());
        assertTrue(root.isLeaf());
        assertNull(root.getParent());
    }

    @Test
    @DisplayName("buildTree mit mehreren Knoten und begrenzter Tiefe")
    void testBuildTreeMultipleNodes() {
        SnowflakeStarTreeNode root = builder.buildTree(5, 2);
        
        assertNotNull(root);
        assertEquals(1, root.getId());
        assertEquals(0, root.getDepth());
        
        // Zähle alle Knoten im Baum
        int totalNodes = countNodes(root);
        assertEquals(5, totalNodes);
        
        // Überprüfe maximale Tiefe
        int maxDepth = findMaxDepth(root);
        assertTrue(maxDepth <= 2);
    }

    @Test
    @DisplayName("buildTree respektiert maximale Tiefe")
    void testBuildTreeMaxDepthRespected() {
        SnowflakeStarTreeNode root = builder.buildTree(10, 1);
        
        // Bei maxDepth = 1 sollten alle Knoten auf Level 0 oder 1 sein
        int maxDepth = findMaxDepth(root);
        assertTrue(maxDepth <= 1);
        
        // Root sollte Kinder haben, aber Kinder sollten Blätter sein
        if (!root.isLeaf()) {
            for (SnowflakeStarTreeNode child : root.getChildren()) {
                assertTrue(child.isLeaf());
            }
        }
    }

    @Test
    @DisplayName("traverseDepthFirst funktioniert korrekt")
    void testTraverseDepthFirst() {
        SnowflakeStarTreeNode root = builder.buildTree(5, 3);
        List<Integer> result = new ArrayList<>();
        
        builder.traverseDepthFirst(root, result);
        
        assertEquals(5, result.size());
        assertEquals(Integer.valueOf(1), result.get(0)); // Root sollte zuerst sein
        
        // Alle IDs sollten einzigartig sein
        assertEquals(5, result.stream().distinct().count());
    }

    @Test
    @DisplayName("traverseDepthFirst mit null")
    void testTraverseDepthFirstWithNull() {
        List<Integer> result = new ArrayList<>();
        
        builder.traverseDepthFirst(null, result);
        
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("addNodesToExistingTree Edge Cases")
    void testAddNodesToExistingTreeEdgeCases() {
        SnowflakeStarTreeNode root = new SnowflakeStarTreeNode(1, 0);
        
        // Null root
        assertEquals(0, builder.addNodesToExistingTree(null, 5, 3));
        
        // Negative nodesToAdd
        assertEquals(0, builder.addNodesToExistingTree(root, -1, 3));
        
        // Zero nodesToAdd
        assertEquals(0, builder.addNodesToExistingTree(root, 0, 3));
    }

    @Test
    @DisplayName("addNodesToExistingTree fügt Knoten korrekt hinzu")
    void testAddNodesToExistingTree() {
        SnowflakeStarTreeNode root = new SnowflakeStarTreeNode(1, 0);
        
        int addedNodes = builder.addNodesToExistingTree(root, 3, 2);
        
        assertEquals(3, addedNodes);
        
        // Gesamtanzahl der Knoten sollte 4 sein (1 ursprünglich + 3 hinzugefügt)
        int totalNodes = countNodes(root);
        assertEquals(4, totalNodes);
        
        // Maximale Tiefe sollte respektiert werden
        int maxDepth = findMaxDepth(root);
        assertTrue(maxDepth <= 2);
    }

    @Test
    @DisplayName("addNodesToExistingTreeBalanced verteilt Knoten ausgewogen")
    void testAddNodesToExistingTreeBalanced() {
        // Erstelle einen bestehenden Baum
        SnowflakeStarTreeNode root = new SnowflakeStarTreeNode(1, 0);
        SnowflakeStarTreeNode child1 = new SnowflakeStarTreeNode(2, 1);
        root.addChild(child1);
        
        int addedNodes = builder.addNodesToExistingTreeBalanced(root, 4, 3);
        
        assertEquals(4, addedNodes);
        
        // Überprüfe Gesamtanzahl
        int totalNodes = countNodes(root);
        assertEquals(6, totalNodes); // 2 ursprünglich + 4 hinzugefügt
        
        // Überprüfe Balance: Root sollte jetzt mehrere Kinder haben
        assertTrue(root.getChildren().size() > 1);
    }

    @Test
    @DisplayName("addNodesToExistingTreeBalanced respektiert maxDepth")
    void testAddNodesToExistingTreeBalancedMaxDepth() {
        SnowflakeStarTreeNode root = new SnowflakeStarTreeNode(1, 0);
        
        // Versuche viele Knoten mit sehr geringer Tiefe hinzuzufügen
        int addedNodes = builder.addNodesToExistingTreeBalanced(root, 10, 1);
        
        // Sollte nur so viele hinzufügen können, wie mit maxDepth=1 möglich
        assertTrue(addedNodes <= 10);
        
        // Maximale Tiefe sollte 1 sein
        int maxDepth = findMaxDepth(root);
        assertTrue(maxDepth <= 1);
    }

    @Test
    @DisplayName("Komplexer Baum mit verschiedenen Operationen")
    void testComplexTreeOperations() {
        // Baue initial einen Baum
        SnowflakeStarTreeNode root = builder.buildTree(7, 3);
        
        // Zähle ursprüngliche Knoten
        int originalNodes = countNodes(root);
        assertEquals(7, originalNodes);
        
        // Füge weitere Knoten hinzu
        int addedNodes = builder.addNodesToExistingTreeBalanced(root, 5, 3);
        
        // Überprüfe Gesamtanzahl
        int totalNodes = countNodes(root);
        assertEquals(originalNodes + addedNodes, totalNodes);
        
        // Teste Traversierung
        List<Integer> traversalResult = new ArrayList<>();
        builder.traverseDepthFirst(root, traversalResult);
        assertEquals(totalNodes, traversalResult.size());
        
        // Alle IDs sollten einzigartig sein
        assertEquals(totalNodes, traversalResult.stream().distinct().count());
    }

    // Hilfsmethoden für Tests
    private int countNodes(SnowflakeStarTreeNode node) {
        if (node == null) return 0;
        
        int count = 1; // Current node
        for (SnowflakeStarTreeNode child : node.getChildren()) {
            count += countNodes(child);
        }
        return count;
    }

    private int findMaxDepth(SnowflakeStarTreeNode node) {
        if (node == null) return -1;
        
        int maxDepth = node.getDepth();
        for (SnowflakeStarTreeNode child : node.getChildren()) {
            maxDepth = Math.max(maxDepth, findMaxDepth(child));
        }
        return maxDepth;
    }
}