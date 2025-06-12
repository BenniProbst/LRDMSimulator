
package org.lrdm.util;

import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;

import java.util.*;

/**
 * TreeBuilder-Implementation mit Tiefenbeschränkung (Depth-First-Ansatz).
 * Bevorzugt das Wachstum in die Tiefe vor der Breite.
 * 
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public class TreeBuilderDepthLimit extends TreeBuilder {
    private int maxDepth;
    private int maxChildrenPerNode;
    
    public TreeBuilderDepthLimit(int maxDepth) {
        this(maxDepth, Integer.MAX_VALUE);
    }
    
    public TreeBuilderDepthLimit(int maxDepth, int maxChildrenPerNode) {
        super();
        this.maxDepth = maxDepth;
        this.maxChildrenPerNode = maxChildrenPerNode;
    }
    
    @Override
    public MirrorNode buildTree(int totalNodes, int maxDepth) {
        if (totalNodes <= 0) return null;
        
        this.maxDepth = Math.min(this.maxDepth, maxDepth > 0 ? maxDepth : this.maxDepth);
        
        MirrorNode root = new MirrorNode(getNextId(), 0);
        if (totalNodes == 1) return root;
        
        buildDepthFirst(root, 1, totalNodes);
        return root;
    }
    
    /**
     * Rekursiver Depth-First-Aufbau des Baums.
     * 
     * @param currentNode Aktueller Knoten
     * @param nodeCounter Bereits erstellte Knoten
     * @param totalNodes Gesamtanzahl zu erstellender Knoten
     * @return Anzahl der erstellten Knoten
     */
    private int buildDepthFirst(MirrorNode currentNode, int nodeCounter, int totalNodes) {
        if (nodeCounter >= totalNodes || currentNode.getDepth() >= maxDepth) {
            return nodeCounter;
        }
        
        int remainingNodes = totalNodes - nodeCounter;
        int maxChildren = calculateMaxChildren(currentNode.getDepth(), remainingNodes);
        maxChildren = Math.min(maxChildren, maxChildrenPerNode);
        
        // Erstelle Kinder
        List<MirrorNode> children = new ArrayList<>();
        for (int i = 0; i < maxChildren && nodeCounter < totalNodes; i++) {
            MirrorNode child = new MirrorNode(getNextId(), currentNode.getDepth() + 1);
            currentNode.addChild(child);
            children.add(child);
            nodeCounter++;
        }
        
        // Rekursiv in die Tiefe gehen
        for (MirrorNode child : children) {
            nodeCounter = buildDepthFirst(child, nodeCounter, totalNodes);
            if (nodeCounter >= totalNodes) break;
        }
        
        return nodeCounter;
    }
    
    /**
     * Berechnet die maximale Anzahl von Kindern für einen Knoten.
     * 
     * @param currentDepth Aktuelle Tiefe
     * @param remainingNodes Verbleibende Knoten
     * @return Maximale Anzahl Kinder
     */
    private int calculateMaxChildren(int currentDepth, int remainingNodes) {
        if (currentDepth >= maxDepth - 1) {
            // Auf der vorletzten Ebene: alle verbleibenden Knoten als Kinder
            return remainingNodes;
        }
        
        // Berechne optimale Verteilung basierend auf verbleibender Tiefe
        int remainingDepth = maxDepth - currentDepth - 1;
        if (remainingDepth <= 0) return 0;
        
        // Versuche eine gleichmäßige Verteilung
        int optimalChildren = (int) Math.ceil(Math.pow(remainingNodes, 1.0 / remainingDepth));
        return Math.min(optimalChildren, maxChildrenPerNode);
    }
    
    @Override
    public int addNodesToExistingTree(MirrorNode existingRoot, int nodesToAdd, int maxDepth) {
        if (existingRoot == null || nodesToAdd <= 0) return 0;
        
        this.maxDepth = Math.min(this.maxDepth, maxDepth > 0 ? maxDepth : this.maxDepth);
        
        return addNodesDepthFirst(existingRoot, nodesToAdd);
    }
    
    /**
     * Fügt Knoten zum bestehenden Baum hinzu (Depth-First).
     * 
     * @param root Root-Knoten
     * @param nodesToAdd Anzahl hinzuzufügender Knoten
     * @return Anzahl tatsächlich hinzugefügter Knoten
     */
    private int addNodesDepthFirst(MirrorNode root, int nodesToAdd) {
        if (nodesToAdd <= 0) return 0;
        
        List<MirrorNode> candidates = findDepthFirstInsertionPoints(root);
        int added = 0;
        
        for (MirrorNode candidate : candidates) {
            if (added >= nodesToAdd) break;
            
            if (candidate.getDepth() < maxDepth && candidate.getChildren().size() < maxChildrenPerNode) {
                MirrorNode newChild = new MirrorNode(getNextId(), candidate.getDepth() + 1);
                candidate.addChild(newChild);
                added++;
                
                // Rekursiv weitere Knoten zu diesem Kind hinzufügen
                if (added < nodesToAdd) {
                    added += addNodesDepthFirst(newChild, nodesToAdd - added);
                }
            }
        }
        
        return added;
    }
    
    /**
     * Findet Einfügepunkte für neue Knoten (Depth-First-Reihenfolge).
     * 
     * @param root Root-Knoten
     * @return Liste der Einfügepunkte
     */
    private List<MirrorNode> findDepthFirstInsertionPoints(MirrorNode root) {
        List<MirrorNode> candidates = new ArrayList<>();
        findDepthFirstCandidatesRecursive(root, candidates);
        
        // Sortiere nach Tiefe (tiefere zuerst) und dann nach Anzahl Kinder
        candidates.sort((a, b) -> {
            int depthDiff = Integer.compare(b.getDepth(), a.getDepth()); // Tiefere zuerst
            if (depthDiff != 0) return depthDiff;
            return Integer.compare(a.getChildren().size(), b.getChildren().size()); // Weniger Kinder zuerst
        });
        
        return candidates;
    }
    
    /**
     * Rekursive Suche nach Einfügekandidaten.
     */
    private void findDepthFirstCandidatesRecursive(MirrorNode node, List<MirrorNode> candidates) {
        if (node.getDepth() < maxDepth && node.getChildren().size() < maxChildrenPerNode) {
            candidates.add(node);
        }
        
        for (TreeNode child : node.getChildren()) {
            findDepthFirstCandidatesRecursive((MirrorNode) child, candidates);
        }
    }
    
    @Override
    public int removeNodesFromTree(MirrorNode root, int nodesToRemove) {
        if (root == null || nodesToRemove <= 0) return 0;
        
        return removeNodesDepthFirst(root, nodesToRemove);
    }
    
    /**
     * Entfernt Knoten vom Baum (Depth-First, von den Blättern her).
     * 
     * @param root Root-Knoten
     * @param nodesToRemove Anzahl zu entfernender Knoten
     * @return Anzahl tatsächlich entfernter Knoten
     */
    private int removeNodesDepthFirst(MirrorNode root, int nodesToRemove) {
        List<MirrorNode> leaves = findLeavesDepthFirst(root);
        int removed = 0;
        
        for (MirrorNode leaf : leaves) {
            if (removed >= nodesToRemove) break;
            if (leaf != root) { // Root nicht entfernen
                MirrorNode parent = (MirrorNode) leaf.getParent();
                if (parent != null) {
                    parent.getChildren().remove(leaf);
                    parent.removeMirrorNode(leaf);
                    removed++;
                }
            }
        }
        
        return removed;
    }
    
    /**
     * Findet alle Blätter des Baums (Depth-First-Reihenfolge).
     * 
     * @param root Root-Knoten
     * @return Liste der Blätter
     */
    private List<MirrorNode> findLeavesDepthFirst(MirrorNode root) {
        List<MirrorNode> leaves = new ArrayList<>();
        findLeavesRecursive(root, leaves);
        
        // Sortiere nach Tiefe (tiefste zuerst)
        leaves.sort((a, b) -> Integer.compare(b.getDepth(), a.getDepth()));
        
        return leaves;
    }
    
    /**
     * Rekursive Suche nach Blättern.
     */
    private void findLeavesRecursive(MirrorNode node, List<MirrorNode> leaves) {
        if (node.isLeaf()) {
            leaves.add(node);
        } else {
            for (TreeNode child : node.getChildren()) {
                findLeavesRecursive((MirrorNode) child, leaves);
            }
        }
    }
    
    @Override
    public Set<Link> createAndLinkMirrors(Network n, List<Mirror> mirrors, int simTime, Properties props) {
        if (mirrors.isEmpty()) return new HashSet<>();
        
        // Erstelle MirrorNode-Struktur
        MirrorNode root = buildTree(mirrors.size(), maxDepth);
        
        // Zuordnung von Mirrors zu MirrorNodes
        assignMirrorsToNodes(root, mirrors, 0);
        
        // Erstelle Links basierend auf der Tree-Struktur
        return root.createAndLinkMirrors(n, mirrors, simTime, props);
    }
    
    /**
     * Ordnet Mirrors den MirrorNodes zu.
     */
    private int assignMirrorsToNodes(MirrorNode node, List<Mirror> mirrors, int currentIndex) {
        if (currentIndex < mirrors.size()) {
            node.setMirror(mirrors.get(currentIndex));
            currentIndex++;
        }
        
        for (TreeNode child : node.getChildren()) {
            currentIndex = assignMirrorsToNodes((MirrorNode) child, mirrors, currentIndex);
        }
        
        return currentIndex;
    }
    
    /**
     * Getter für maximale Tiefe.
     */
    public int getMaxDepth() {
        return maxDepth;
    }
    
    /**
     * Setter für maximale Tiefe.
     */
    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }
    
    /**
     * Getter für maximale Kinder pro Knoten.
     */
    public int getMaxChildrenPerNode() {
        return maxChildrenPerNode;
    }
    
    /**
     * Setter für maximale Kinder pro Knoten.
     */
    public void setMaxChildrenPerNode(int maxChildrenPerNode) {
        this.maxChildrenPerNode = maxChildrenPerNode;
    }
}