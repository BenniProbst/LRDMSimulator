
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
    protected int getEffectiveMaxDepth() {
        return maxDepth;
    }

    @Override
    public MirrorNode buildTree(int totalNodes, int maxDepth) {
        if (totalNodes <= 0) return null;

        int effectiveMaxDepth = Math.min(this.maxDepth, maxDepth > 0 ? maxDepth : this.maxDepth);

        MirrorNode root = new MirrorNode(getNextId(), 0);
        if (totalNodes == 1) return root;

        buildDepthFirst(root, 1, totalNodes, effectiveMaxDepth);
        return root;
    }

    /**
     * Rekursiver Depth-First-Aufbau des Baums.
     *
     * @param currentNode Aktueller Knoten
     * @param nodeCounter Bereits erstellte Knoten
     * @param totalNodes Gesamtanzahl zu erstellender Knoten
     * @param effectiveMaxDepth Effektive maximale Tiefe
     * @return Anzahl der erstellten Knoten
     */
    private int buildDepthFirst(MirrorNode currentNode, int nodeCounter, int totalNodes, int effectiveMaxDepth) {
        if (nodeCounter >= totalNodes || currentNode.getDepth() >= effectiveMaxDepth) {
            return nodeCounter;
        }

        int remainingNodes = totalNodes - nodeCounter;
        int maxChildren = calculateMaxChildren(currentNode.getDepth(), remainingNodes, effectiveMaxDepth);
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
            nodeCounter = buildDepthFirst(child, nodeCounter, totalNodes, effectiveMaxDepth);
            if (nodeCounter >= totalNodes) break;
        }

        return nodeCounter;
    }

    /**
     * Berechnet die maximale Anzahl von Kindern für einen Knoten.
     *
     * @param currentDepth Aktuelle Tiefe
     * @param remainingNodes Verbleibende Knoten
     * @param effectiveMaxDepth Effektive maximale Tiefe
     * @return Maximale Anzahl Kinder
     */
    private int calculateMaxChildren(int currentDepth, int remainingNodes, int effectiveMaxDepth) {
        if (currentDepth >= effectiveMaxDepth - 1) {
            // Auf der vorletzten Ebene: alle verbleibenden Knoten als Kinder
            return remainingNodes;
        }

        // Berechne optimale Verteilung basierend auf verbleibender Tiefe
        int remainingDepth = effectiveMaxDepth - currentDepth - 1;
        if (remainingDepth <= 0) return 0;

        // Versuche eine gleichmäßige Verteilung
        int optimalChildren = (int) Math.ceil(Math.pow(remainingNodes, 1.0 / remainingDepth));
        return Math.min(optimalChildren, maxChildrenPerNode);
    }

    @Override
    public int addNodesToExistingTree(MirrorNode existingRoot, int nodesToAdd, int maxDepth) {
        if (existingRoot == null || nodesToAdd <= 0) return 0;

        int effectiveMaxDepth = Math.min(this.maxDepth, maxDepth > 0 ? maxDepth : this.maxDepth);

        return addNodesDepthFirst(existingRoot, nodesToAdd, effectiveMaxDepth);
    }

    /**
     * Fügt Knoten zum bestehenden Baum hinzu (Depth-First).
     *
     * @param root Root-Knoten
     * @param nodesToAdd Anzahl hinzuzufügender Knoten
     * @param effectiveMaxDepth Effektive maximale Tiefe
     * @return Anzahl tatsächlich hinzugefügter Knoten
     */
    private int addNodesDepthFirst(MirrorNode root, int nodesToAdd, int effectiveMaxDepth) {
        if (nodesToAdd <= 0) return 0;

        List<MirrorNode> candidates = findDepthFirstInsertionPoints(root, effectiveMaxDepth);
        int added = 0;

        for (MirrorNode candidate : candidates) {
            if (added >= nodesToAdd) break;

            if (candidate.getDepth() < effectiveMaxDepth && candidate.getChildren().size() < maxChildrenPerNode) {
                MirrorNode newChild = new MirrorNode(getNextId(), candidate.getDepth() + 1);
                candidate.addChild(newChild);
                added++;

                // Rekursiv weitere Knoten zu diesem Kind hinzufügen
                if (added < nodesToAdd) {
                    added += addNodesDepthFirst(newChild, nodesToAdd - added, effectiveMaxDepth);
                }
            }
        }

        return added;
    }

    /**
     * Findet Einfügepunkte für neue Knoten (Depth-First-Reihenfolge).
     *
     * @param root Root-Knoten
     * @param effectiveMaxDepth Effektive maximale Tiefe
     * @return Liste der Einfügepunkte
     */
    private List<MirrorNode> findDepthFirstInsertionPoints(MirrorNode root, int effectiveMaxDepth) {
        List<MirrorNode> candidates = new ArrayList<>();
        findDepthFirstCandidatesRecursive(root, candidates, effectiveMaxDepth);

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
    private void findDepthFirstCandidatesRecursive(MirrorNode node, List<MirrorNode> candidates, int effectiveMaxDepth) {
        if (node.getDepth() < effectiveMaxDepth && node.getChildren().size() < maxChildrenPerNode) {
            candidates.add(node);
        }

        for (TreeNode child : node.getChildren()) {
            findDepthFirstCandidatesRecursive((MirrorNode) child, candidates, effectiveMaxDepth);
        }
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