package org.lrdm.util;

import java.util.*;

/**
 * TreeBuilder-Implementation mit Tiefenbeschränkung (Depth-First-Ansatz).
 * Bevorzugt das Wachstum in die Tiefe vor der Breite.
 * Zustandslos - keine gespeicherten Tiefenwerte.
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

        MirrorNode root = createMirrorNode();
        if (totalNodes == 1) return root;

        buildDepthFirstIterative(root, totalNodes - 1, effectiveMaxDepth);
        return root;
    }

    /**
     * Stack-basierter Depth-First-Aufbau des Baums.
     */
    private void buildDepthFirstIterative(MirrorNode root, int remainingNodes, int effectiveMaxDepth) {
        if (remainingNodes <= 0) return;

        Stack<NodeInfo> stack = new Stack<>();
        stack.push(new NodeInfo(root, remainingNodes));

        while (!stack.isEmpty() && remainingNodes > 0) {
            NodeInfo info = stack.pop();
            MirrorNode currentNode = info.node;
            int nodesToDistribute = info.remainingNodes;
            int currentDepth = calculateDepth(currentNode);

            if (currentDepth >= effectiveMaxDepth || nodesToDistribute <= 0) {
                continue;
            }

            int maxChildren = calculateMaxChildren(currentDepth, nodesToDistribute, effectiveMaxDepth);
            maxChildren = Math.min(maxChildren, maxChildrenPerNode);
            maxChildren = Math.min(maxChildren, nodesToDistribute);

            // Erstelle Kinder
            List<MirrorNode> children = new ArrayList<>();
            for (int i = 0; i < maxChildren; i++) {
                MirrorNode child = createMirrorNode();
                currentNode.addChild(child);
                children.add(child);
                remainingNodes--;
            }

            // Verteile verbleibende Knoten auf die Kinder
            int nodesPerChild = (nodesToDistribute - maxChildren) / Math.max(1, maxChildren);
            int extraNodes = (nodesToDistribute - maxChildren) % Math.max(1, maxChildren);

            for (int i = 0; i < children.size(); i++) {
                int childNodes = nodesPerChild + (i < extraNodes ? 1 : 0);
                if (childNodes > 0) {
                    stack.push(new NodeInfo(children.get(i), childNodes));
                }
            }
        }
    }

    /**
     * Hilfklasse für Stack-Verwaltung.
     */
    private static class NodeInfo {
        final MirrorNode node;
        final int remainingNodes;

        NodeInfo(MirrorNode node, int remainingNodes) {
            this.node = node;
            this.remainingNodes = remainingNodes;
        }
    }

    /**
     * Berechnet die maximale Anzahl von Kindern für einen Knoten.
     */
    private int calculateMaxChildren(int currentDepth, int remainingNodes, int effectiveMaxDepth) {
        if (currentDepth >= effectiveMaxDepth - 1) {
            return remainingNodes;
        }

        int remainingDepth = effectiveMaxDepth - currentDepth - 1;
        if (remainingDepth <= 0) return 0;

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
     * Fügt Knoten zum bestehenden Baum hinzu (Stack-basiert).
     */
    private int addNodesDepthFirst(MirrorNode root, int nodesToAdd, int effectiveMaxDepth) {
        if (nodesToAdd <= 0) return 0;

        List<MirrorNode> candidates = findDepthFirstInsertionPoints(root, effectiveMaxDepth);
        int added = 0;

        for (MirrorNode candidate : candidates) {
            if (added >= nodesToAdd) break;

            int candidateDepth = calculateDepth(candidate);
            if (candidateDepth < effectiveMaxDepth && candidate.getChildren().size() < maxChildrenPerNode) {
                MirrorNode newChild = createMirrorNode();
                candidate.addChild(newChild);
                added++;

                // Nutze Stack für weitere Einfügungen unter diesem Kind
                if (added < nodesToAdd) {
                    added += addNodesDepthFirst(newChild, nodesToAdd - added, effectiveMaxDepth);
                }
            }
        }

        return added;
    }

    /**
     * Findet Einfügepunkte für neue Knoten (Stack-basiert).
     */
    private List<MirrorNode> findDepthFirstInsertionPoints(MirrorNode root, int effectiveMaxDepth) {
        List<MirrorNode> candidates = new ArrayList<>();

        Stack<MirrorNode> stack = new Stack<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            MirrorNode node = stack.pop();
            int nodeDepth = calculateDepth(node);

            if (nodeDepth < effectiveMaxDepth && node.getChildren().size() < maxChildrenPerNode) {
                candidates.add(node);
            }

            for (TreeNode child : node.getChildren()) {
                stack.push((MirrorNode) child);
            }
        }

        // Sortiere nach Tiefe (tiefere zuerst) und dann nach Anzahl Kinder
        candidates.sort((a, b) -> {
            int depthA = calculateDepth(a);
            int depthB = calculateDepth(b);
            int depthDiff = Integer.compare(depthB, depthA); // Tiefere zuerst
            if (depthDiff != 0) return depthDiff;
            return Integer.compare(a.getChildren().size(), b.getChildren().size()); // Weniger Kinder zuerst
        });

        return candidates;
    }

    // Getter und Setter
    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public int getMaxChildrenPerNode() {
        return maxChildrenPerNode;
    }

    public void setMaxChildrenPerNode(int maxChildrenPerNode) {
        this.maxChildrenPerNode = maxChildrenPerNode;
    }
}