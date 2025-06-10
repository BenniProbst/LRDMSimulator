package org.lrdm.util;

import java.util.List;
import java.util.Queue;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.Comparator;

// Algorithmus zum Aufbau des Baums
public class TreeBuilder {
    private int max_depth; // Beispielwert

    public TreeNode buildTree(int totalNodes, int max_depth) {
        this.max_depth = max_depth;
        if (totalNodes <= 0) {
            return null;
        }

        TreeNode root = new TreeNode(1, 0);
        int nodeCounter = 2; // Nächste verfügbare Node-ID

        // Depth-First Aufbau mit Rekursion
        nodeCounter = buildDepthFirst(root, nodeCounter, totalNodes);

        return root;
    }

    private int buildDepthFirst(TreeNode currentNode, int nodeCounter, int totalNodes) {
        // Abbruchbedingung: Maximale Tiefe erreicht oder alle Nodes verbraucht
        if (currentNode.getDepth() >= max_depth || nodeCounter > totalNodes) {
            return nodeCounter;
        }

        // Berechne wie viele Kinder dieser Node haben soll
        int remainingNodes = totalNodes - nodeCounter + 1;
        int maxPossibleChildren = calculateMaxChildren(currentNode.getDepth(), remainingNodes);

        // Füge Kinder hinzu (depth-first)
        for (int i = 0; i < maxPossibleChildren && nodeCounter <= totalNodes; i++) {
            TreeNode child = new TreeNode(nodeCounter++, currentNode.getDepth() + 1);
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
    public void printTree(TreeNode node, String prefix) {
        if (node == null) return;

        System.out.println(prefix + "Node " + node.getId() + " (Depth: " + node.getDepth() + ")");

        for (TreeNode child : node.getChildren()) {
            printTree(child, prefix + "  ");
        }
    }

    // Baum-Traversierung depth-first
    public void traverseDepthFirst(TreeNode node, List<Integer> result) {
        if (node == null) return;

        result.add(node.getId());

        for (TreeNode child : node.getChildren()) {
            traverseDepthFirst(child, result);
        }
    }

    /**
     * Fügt eine Anzahl neuer Knoten zu einem bestehenden Baum hinzu und
     * verbessert dabei schrittweise die Balance, unabhängig vom Ausgangszustand
     */
    public int addNodesToExistingTree(TreeNode existingRoot, int nodesToAdd, int maxDepth) {
        if (existingRoot == null || nodesToAdd <= 0) {
            return 0;
        }
        
        this.max_depth = maxDepth;
        int nodeCounter = findHighestNodeId(existingRoot) + 1;
        int addedNodes = 0;
        
        while (addedNodes < nodesToAdd) {
            // Finde die beste Position für den nächsten Knoten
            TreeNode bestParent = findBestInsertionPoint(existingRoot);
            
            if (bestParent == null || bestParent.getDepth() >= maxDepth) {
                break; // Keine weitere Einfügung möglich
            }
            
            // Füge neuen Knoten hinzu
            TreeNode newNode = new TreeNode(nodeCounter++, bestParent.getDepth() + 1);
            bestParent.addChild(newNode);
            addedNodes++;
        }
        
        return addedNodes;
    }

    /**
     * Findet den optimalen Einfügepunkt für einen neuen Knoten
     * Priorisiert Knoten mit weniger Kindern und geringerer Tiefe
     */
    private TreeNode findBestInsertionPoint(TreeNode root) {
        if (root == null) return null;
        
        Queue<TreeNode> queue = new LinkedList<>();
        queue.offer(root);
        
        TreeNode bestCandidate = null;
        int minChildren = Integer.MAX_VALUE;
        int minDepth = Integer.MAX_VALUE;
        
        // Level-Order Traversierung für bessere Balance
        while (!queue.isEmpty()) {
            TreeNode current = queue.poll();
            
            // Überprüfe ob dieser Knoten als Einfügepunkt geeignet ist
            if (current.getDepth() < max_depth) {
                int childrenCount = current.getChildren().size();
                
                // Priorisiere Knoten mit weniger Kindern oder geringerer Tiefe
                if (childrenCount < minChildren || 
                    (childrenCount == minChildren && current.getDepth() < minDepth)) {
                    bestCandidate = current;
                    minChildren = childrenCount;
                    minDepth = current.getDepth();
                }
            }
            
            // Füge Kinder zur Queue hinzu
            for (TreeNode child : current.getChildren()) {
                queue.offer(child);
            }
        }
        
        return bestCandidate;
    }

    /**
     * Erweiterte Version mit Balancierungs-Heuristik
     */
    public int addNodesToExistingTreeBalanced(TreeNode existingRoot, int nodesToAdd, int maxDepth) {
        if (existingRoot == null || nodesToAdd <= 0) {
            return 0;
        }
        
        this.max_depth = maxDepth;
        int nodeCounter = findHighestNodeId(existingRoot) + 1;
        int addedNodes = 0;
        
        while (addedNodes < nodesToAdd) {
            // Analysiere aktuellen Baum-Zustand
            List<TreeNode> candidateParents = findBalancedInsertionCandidates(existingRoot);
            
            if (candidateParents.isEmpty()) {
                break; // Keine weitere Einfügung möglich
            }
            
            // Wähle besten Kandidaten basierend auf Balance-Kriterien
            TreeNode bestParent = selectBestBalancedParent(candidateParents);
            
            // Füge neuen Knoten hinzu
            TreeNode newNode = new TreeNode(nodeCounter++, bestParent.getDepth() + 1);
            bestParent.addChild(newNode);
            addedNodes++;
        }
        
        return addedNodes;
    }

    /**
     * Findet alle möglichen Einfügepunkte und bewertet sie nach Balance-Kriterien
     */
    private List<TreeNode> findBalancedInsertionCandidates(TreeNode root) {
        List<TreeNode> candidates = new ArrayList<>();
        
        Queue<TreeNode> queue = new LinkedList<>();
        queue.offer(root);
        
        while (!queue.isEmpty()) {
            TreeNode current = queue.poll();
            
            // Nur Knoten betrachten, die noch Kinder haben können
            if (current.getDepth() < max_depth) {
                candidates.add(current);
            }
            
            for (TreeNode child : current.getChildren()) {
                queue.offer(child);
            }
        }
        
        return candidates;
    }

    /**
     * Wählt den besten Einfügepunkt basierend auf Balance-Kriterien
     */
    private TreeNode selectBestBalancedParent(List<TreeNode> candidates) {
        return candidates.stream()
            .min(Comparator
                .comparingInt((TreeNode n) -> n.getChildren().size()) // Weniger Kinder = besser
                .thenComparingInt(TreeNode::getDepth)) // Geringere Tiefe = besser
            .orElse(null);
    }

    /**
     * Hilfsmethode: Findet die höchste Node-ID im bestehenden Baum
     */
    private int findHighestNodeId(TreeNode root) {
        if (root == null) return 0;
        
        int maxId = root.getId();
        Queue<TreeNode> queue = new LinkedList<>();
        queue.offer(root);
        
        while (!queue.isEmpty()) {
            TreeNode current = queue.poll();
            maxId = Math.max(maxId, current.getId());
            
            for (TreeNode child : current.getChildren()) {
                queue.offer(child);
            }
        }
        
        return maxId;
    }
}