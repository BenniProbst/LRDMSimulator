package org.lrdm.util;

import java.util.*;

// Node-Klasse für den Baum
public class SnowflakeStarTreeNode {
    private int id;
    private List<SnowflakeStarTreeNode> children;
    private SnowflakeStarTreeNode parent;
    private int depth;

    public SnowflakeStarTreeNode(int id, int depth) {
        this.id = id;
        this.depth = depth;
        this.children = new ArrayList<>();
        this.parent = null;
    }

    public void addChild(SnowflakeStarTreeNode child) {
        children.add(child);
        child.parent = this;
    }

    public List<SnowflakeStarTreeNode> getChildren() {
        return children;
    }

    public int getId() {
        return id;
    }

    public int getDepth() {
        return depth;
    }

    public SnowflakeStarTreeNode getParent() {
        return parent;
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }
}

// Algorithmus zum Aufbau des Baums
class SnowflakeTreeBuilder {
    private static final int BRIDGE_TO_EXTERN_STAR_DISTANCE = 5; // Beispielwert

    public SnowflakeStarTreeNode buildTree(int totalNodes) {
        if (totalNodes <= 0) {
            return null;
        }

        SnowflakeStarTreeNode root = new SnowflakeStarTreeNode(1, 0);
        int nodeCounter = 2; // Nächste verfügbare Node-ID

        // Depth-First Aufbau mit Rekursion
        nodeCounter = buildDepthFirst(root, nodeCounter, totalNodes);

        return root;
    }

    private int buildDepthFirst(SnowflakeStarTreeNode currentNode, int nodeCounter, int totalNodes) {
        // Abbruchbedingung: Maximale Tiefe erreicht oder alle Nodes verbraucht
        if (currentNode.getDepth() >= BRIDGE_TO_EXTERN_STAR_DISTANCE || nodeCounter > totalNodes) {
            return nodeCounter;
        }

        // Berechne wie viele Kinder dieser Node haben soll
        int remainingNodes = totalNodes - nodeCounter + 1;
        int maxPossibleChildren = calculateMaxChildren(currentNode.getDepth(), remainingNodes);

        // Füge Kinder hinzu (depth-first)
        for (int i = 0; i < maxPossibleChildren && nodeCounter <= totalNodes; i++) {
            SnowflakeStarTreeNode child = new SnowflakeStarTreeNode(nodeCounter++, currentNode.getDepth() + 1);
            currentNode.addChild(child);

            // Rekursiv das Kind weiter ausbauen (depth-first)
            nodeCounter = buildDepthFirst(child, nodeCounter, totalNodes);
        }

        return nodeCounter;
    }

    private int calculateMaxChildren(int currentDepth, int remainingNodes) {
        // Dynamische Berechnung der Kinderanzahl basierend auf verbleibenden Nodes
        // und verbleibender Tiefe
        int remainingDepth = BRIDGE_TO_EXTERN_STAR_DISTANCE - currentDepth;

        if (remainingDepth <= 1) {
            return remainingNodes; // Alle verbleibenden Nodes als Blätter
        }

        // Heuristik: Verteile Nodes gleichmäßig über die verbleibenden Ebenen
        return Math.min(remainingNodes, (int) Math.ceil(Math.sqrt(remainingNodes)));
    }

    // Hilfsmethode zur Ausgabe des Baums
    public void printTree(SnowflakeStarTreeNode node, String prefix) {
        if (node == null) return;

        System.out.println(prefix + "Node " + node.getId() + " (Depth: " + node.getDepth() + ")");

        for (SnowflakeStarTreeNode child : node.getChildren()) {
            printTree(child, prefix + "  ");
        }
    }

    // Baum-Traversierung depth-first
    public void traverseDepthFirst(SnowflakeStarTreeNode node, List<Integer> result) {
        if (node == null) return;

        result.add(node.getId());

        for (SnowflakeStarTreeNode child : node.getChildren()) {
            traverseDepthFirst(child, result);
        }
    }
}
