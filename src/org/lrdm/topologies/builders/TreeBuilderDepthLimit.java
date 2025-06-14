package org.lrdm.topologies.builders;

import org.lrdm.Network;
import org.lrdm.Mirror;
import org.lrdm.topologies.base.MirrorNode;
import org.lrdm.topologies.base.TreeNode;

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

    public TreeBuilderDepthLimit(Network network, int maxDepth) {
        this(network, maxDepth, Integer.MAX_VALUE);
    }

    public TreeBuilderDepthLimit(Network network, int maxDepth, int maxChildrenPerNode) {
        super(network);
        this.maxDepth = maxDepth;
        this.maxChildrenPerNode = Math.max(1, maxChildrenPerNode);
    }

    public TreeBuilderDepthLimit(Network network, Iterator<Mirror> mirrorIterator, int maxDepth, int maxChildrenPerNode) {
        super(network, mirrorIterator);
        this.maxDepth = maxDepth;
        this.maxChildrenPerNode = Math.max(1, maxChildrenPerNode);
    }

    @Override
    protected int getEffectiveMaxDepth() {
        return maxDepth;
    }

    @Override
    public MirrorNode buildTree(int totalNodes, int maxDepth) {
        if (totalNodes <= 0 || !mirrorIterator.hasNext()) return null;

        int effectiveMaxDepth = (maxDepth > 0) ? maxDepth : this.maxDepth;
        MirrorNode root = createMirrorNodeFromIterator();
        if (root == null || totalNodes == 1) return root;

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

        while (!stack.isEmpty() && mirrorIterator.hasNext()) {
            NodeInfo info = stack.pop();
            MirrorNode current = info.node;
            int currentDepth = calculateDepth(current);

            if (currentDepth >= effectiveMaxDepth - 1) continue;

            int maxChildren = calculateMaxChildren(currentDepth, info.remainingNodes, effectiveMaxDepth);
            int actualChildren = Math.min(maxChildren, info.remainingNodes);

            for (int i = 0; i < actualChildren && mirrorIterator.hasNext(); i++) {
                MirrorNode child = createMirrorNodeFromIterator();
                if (child != null) {
                    current.addChild(child);

                    // Für Depth-First: Neue Kinder sofort auf Stack
                    if (currentDepth < effectiveMaxDepth - 2) {
                        stack.push(new NodeInfo(child, info.remainingNodes - i - 1));
                    }
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
        if (currentDepth >= effectiveMaxDepth - 1) return 0;

        int maxPossible = Math.min(maxChildrenPerNode, remainingNodes);

        // Tiefenbeschränkung berücksichtigen
        int depthsLeft = effectiveMaxDepth - currentDepth - 1;
        if (depthsLeft > 0) {
            // Verteile Knoten über verbleibende Tiefen
            int avgPerDepth = (int) Math.ceil((double) remainingNodes / depthsLeft);
            maxPossible = Math.min(maxPossible, avgPerDepth);
        }

        return Math.max(1, maxPossible);
    }

    @Override
    public int addNodesToExistingTree(MirrorNode existingRoot, int nodesToAdd, int maxDepth) {
        if (existingRoot == null || nodesToAdd <= 0) return 0;
        int effectiveMaxDepth = (maxDepth > 0) ? maxDepth : this.maxDepth;
        return addNodesDepthFirst(existingRoot, nodesToAdd, effectiveMaxDepth);
    }

    /**
     * Fügt Knoten zum bestehenden Baum hinzu (Stack-basiert).
     */
    private int addNodesDepthFirst(MirrorNode root, int nodesToAdd, int effectiveMaxDepth) {
        List<MirrorNode> insertionPoints = findDepthFirstInsertionPoints(root, effectiveMaxDepth);
        int added = 0;

        for (MirrorNode insertionPoint : insertionPoints) {
            if (added >= nodesToAdd || !mirrorIterator.hasNext()) break;

            int currentDepth = calculateDepth(insertionPoint);
            if (currentDepth >= effectiveMaxDepth - 1) continue;

            int childrenToAdd = Math.min(
                    maxChildrenPerNode - insertionPoint.getChildren().size(),
                    nodesToAdd - added
            );

            for (int i = 0; i < childrenToAdd && mirrorIterator.hasNext(); i++) {
                MirrorNode newChild = createMirrorNodeFromIterator();
                if (newChild != null) {
                    insertionPoint.addChild(newChild);
                    added++;
                }
            }
        }

        return added;
    }

    /**
     * Findet Einfügepunkte für neue Knoten (Stack-basiert).
     */
    private List<MirrorNode> findDepthFirstInsertionPoints(MirrorNode root, int effectiveMaxDepth) {
        List<MirrorNode> insertionPoints = new ArrayList<>();
        Stack<MirrorNode> stack = new Stack<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            MirrorNode current = stack.pop();
            int currentDepth = calculateDepth(current);

            if (currentDepth < effectiveMaxDepth - 1 &&
                    current.getChildren().size() < maxChildrenPerNode) {
                insertionPoints.add(current);
            }

            // Depth-First: Kinder in umgekehrter Reihenfolge hinzufügen
            List<TreeNode> children = new ArrayList<>(current.getChildren());
            Collections.reverse(children);
            for (TreeNode child : children) {
                stack.push((MirrorNode) child);
            }
        }

        // Sortiere nach Tiefe (tiefere zuerst für Depth-First)
        insertionPoints.sort(Comparator.comparingInt(this::calculateDepth).reversed());
        return insertionPoints;
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
        this.maxChildrenPerNode = Math.max(1, maxChildrenPerNode);
    }
}