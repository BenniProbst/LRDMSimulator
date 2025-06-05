package org.lrdm.util;

import java.util.List;

// Algorithmus zum Aufbau des Baums
public class SnowflakeTreeBuilder {
    private int max_depth; // Beispielwert

    public SnowflakeStarTreeNode buildTree(int totalNodes, int max_depth) {
        this.max_depth = max_depth;
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
        if (currentNode.getDepth() >= max_depth || nodeCounter > totalNodes) {
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
        int remainingDepth = max_depth - currentDepth;

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
