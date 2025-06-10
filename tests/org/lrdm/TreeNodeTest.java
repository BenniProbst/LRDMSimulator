package org.lrdm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.lrdm.util.TreeNode;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SnowflakeStarTreeNode Tests")
class TreeNodeTest {

    private TreeNode rootNode;

    @BeforeEach
    void setUp() {
        rootNode = new TreeNode(1, 0);
    }

    @Test
    @DisplayName("Konstruktor sollte korrekte Werte setzen")
    void testConstructor() {
        assertEquals(1, rootNode.getId());
        assertEquals(0, rootNode.getDepth());
        assertNull(rootNode.getParent());
        assertTrue(rootNode.getChildren().isEmpty());
        assertTrue(rootNode.isLeaf());
    }

    @Test
    @DisplayName("addChild sollte Kind korrekt hinzufügen und Parent setzen")
    void testAddChild() {
        TreeNode child = new TreeNode(2, 1);
        
        rootNode.addChild(child);
        
        assertEquals(1, rootNode.getChildren().size());
        assertEquals(child, rootNode.getChildren().get(0));
        assertEquals(rootNode, child.getParent());
        assertFalse(rootNode.isLeaf());
        assertTrue(child.isLeaf());
    }

    @Test
    @DisplayName("Mehrere Kinder hinzufügen")
    void testAddMultipleChildren() {
        TreeNode child1 = new TreeNode(2, 1);
        TreeNode child2 = new TreeNode(3, 1);
        TreeNode child3 = new TreeNode(4, 1);
        
        rootNode.addChild(child1);
        rootNode.addChild(child2);
        rootNode.addChild(child3);
        
        assertEquals(3, rootNode.getChildren().size());
        assertTrue(rootNode.getChildren().contains(child1));
        assertTrue(rootNode.getChildren().contains(child2));
        assertTrue(rootNode.getChildren().contains(child3));
        
        assertEquals(rootNode, child1.getParent());
        assertEquals(rootNode, child2.getParent());
        assertEquals(rootNode, child3.getParent());
    }

    @Test
    @DisplayName("isLeaf sollte korrekt funktionieren")
    void testIsLeaf() {
        assertTrue(rootNode.isLeaf());
        
        TreeNode child = new TreeNode(2, 1);
        rootNode.addChild(child);
        
        assertFalse(rootNode.isLeaf());
        assertTrue(child.isLeaf());
    }

    @Test
    @DisplayName("Komplexe Baumstruktur")
    void testComplexTreeStructure() {
        // Erstelle einen Baum: Root -> Child1 -> Grandchild1
        //                          -> Child2 -> Grandchild2
        //                                    -> Grandchild3
        
        TreeNode child1 = new TreeNode(2, 1);
        TreeNode child2 = new TreeNode(3, 1);
        TreeNode grandchild1 = new TreeNode(4, 2);
        TreeNode grandchild2 = new TreeNode(5, 2);
        TreeNode grandchild3 = new TreeNode(6, 2);
        
        rootNode.addChild(child1);
        rootNode.addChild(child2);
        child1.addChild(grandchild1);
        child2.addChild(grandchild2);
        child2.addChild(grandchild3);
        
        // Überprüfe Root
        assertEquals(2, rootNode.getChildren().size());
        assertFalse(rootNode.isLeaf());
        
        // Überprüfe Child1
        assertEquals(1, child1.getChildren().size());
        assertEquals(grandchild1, child1.getChildren().get(0));
        assertFalse(child1.isLeaf());
        
        // Überprüfe Child2
        assertEquals(2, child2.getChildren().size());
        assertTrue(child2.getChildren().contains(grandchild2));
        assertTrue(child2.getChildren().contains(grandchild3));
        assertFalse(child2.isLeaf());
        
        // Überprüfe Grandchildren
        assertTrue(grandchild1.isLeaf());
        assertTrue(grandchild2.isLeaf());
        assertTrue(grandchild3.isLeaf());
        assertEquals(child1, grandchild1.getParent());
        assertEquals(child2, grandchild2.getParent());
        assertEquals(child2, grandchild3.getParent());
    }
}