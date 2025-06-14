package org.lrdm.topologies.builders;

import org.lrdm.topologies.base.MirrorNode;
import org.lrdm.topologies.base.TreeNode;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TreeBuilder-Implementation für balancierte Bäume (Breadth-First-Ansatz).
 * Ohne Tiefenbeschränkung, aber mit balanciertem Einfügen und Löschen.
 * Zustandslos - keine gespeicherten Tiefenwerte.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public class TreeBuilderBalanced extends TreeBuilder {
    private int targetLinksPerNode;

    public TreeBuilderBalanced() {
        this(2); // Standard: Binärbaum
    }

    public TreeBuilderBalanced(int targetLinksPerNode) {
        super();
        this.targetLinksPerNode = targetLinksPerNode;
    }

    @Override
    protected int getEffectiveMaxDepth() {
        return 0; // Keine Tiefenbeschränkung für balancierte Bäume
    }

    @Override
    public MirrorNode buildTree(int totalNodes, int maxDepth) {
        if (totalNodes <= 0) return null;

        MirrorNode root = new MirrorNode(idGenerator.getNextID());
        if (totalNodes == 1) return root;

        buildBalanced(root, totalNodes - 1, maxDepth);
        return root;
    }

    /**
     * Erstellt einen balancierten Baum mit Breadth-First-Ansatz (Queue-basiert).
     */
    private void buildBalanced(MirrorNode root, int remainingNodes, int maxDepth) {
        if (remainingNodes <= 0) return;

        Queue<MirrorNode> queue = new LinkedList<>();
        queue.offer(root);
        int nodesAdded = 0;

        while (!queue.isEmpty() && nodesAdded < remainingNodes) {
            MirrorNode current = queue.poll();
            int currentDepth = calculateDepth(current);

            if (maxDepth > 0 && currentDepth >= maxDepth - 1) {
                continue;
            }

            int childrenToAdd = calculateOptimalChildren(remainingNodes - nodesAdded, queue.size());

            for (int i = 0; i < childrenToAdd && nodesAdded < remainingNodes; i++) {
                MirrorNode child = new MirrorNode(idGenerator.getNextID());
                current.addChild(child);
                queue.offer(child);
                nodesAdded++;
            }
        }
    }

    private int calculateOptimalChildren(int remainingNodes, int queueSize) {
        if (remainingNodes <= 0) return 0;
        int baseChildren = Math.min(targetLinksPerNode, remainingNodes);
        if (queueSize > 0) {
            int avgChildren = (int) Math.ceil((double) remainingNodes / (queueSize + 1));
            baseChildren = Math.min(baseChildren, avgChildren);
        }
        return Math.max(1, baseChildren);
    }

    @Override
    public int addNodesToExistingTree(MirrorNode existingRoot, int nodesToAdd, int maxDepth) {
        if (existingRoot == null || nodesToAdd <= 0) return 0;
        return addNodesToExistingTreeBalanced(existingRoot, nodesToAdd, maxDepth);
    }

    private int addNodesToExistingTreeBalanced(MirrorNode existingRoot, int nodesToAdd, int maxDepth) {
        List<MirrorNode> candidates = findBalancedInsertionCandidates(existingRoot, maxDepth);
        int added = 0;

        while (added < nodesToAdd && !candidates.isEmpty()) {
            MirrorNode bestCandidate = selectBestBalancedParent(candidates);

            if (bestCandidate != null && bestCandidate.getChildren().size() < targetLinksPerNode) {
                int candidateDepth = calculateDepth(bestCandidate);

                if (maxDepth > 0 && candidateDepth >= maxDepth - 1) {
                    candidates.remove(bestCandidate);
                    continue;
                }

                MirrorNode newChild = new MirrorNode(idGenerator.getNextID());
                bestCandidate.addChild(newChild);
                added++;

                candidates.add(newChild);

                if (bestCandidate.getChildren().size() >= targetLinksPerNode) {
                    candidates.remove(bestCandidate);
                }
            } else {
                if (bestCandidate != null) {
                    candidates.remove(bestCandidate);
                }
                if (candidates.isEmpty()) break;
            }
        }

        return added;
    }

    private List<MirrorNode> findBalancedInsertionCandidates(MirrorNode root, int maxDepth) {
        List<MirrorNode> candidates = new ArrayList<>();
        Stack<MirrorNode> stack = new Stack<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            MirrorNode node = stack.pop();
            int nodeDepth = calculateDepth(node);

            if (maxDepth <= 0 || nodeDepth < maxDepth - 1) {
                candidates.add(node);
            }

            for (TreeNode child : node.getChildren()) {
                stack.push((MirrorNode) child);
            }
        }

        candidates = candidates.stream()
                .filter(node -> node.getChildren().size() < targetLinksPerNode)
                .filter(node -> maxDepth <= 0 || calculateDepth(node) < maxDepth - 1)
                .collect(Collectors.toList());

        candidates.sort(Comparator.comparingInt(this::calculateDepth)
                .thenComparingInt(a -> a.getChildren().size()));

        return candidates;
    }

    private MirrorNode selectBestBalancedParent(List<MirrorNode> candidates) {
        if (candidates.isEmpty()) return null;
        return candidates.stream()
                .min(Comparator.comparingInt(this::calculateDepth)
                        .thenComparingInt(a -> a.getChildren().size()))
                .orElse(null);
    }

    public double calculateTreeBalance(MirrorNode root) {
        if (root == null) return 0.0;

        Map<Integer, Integer> depthCounts = new HashMap<>();
        Stack<MirrorNode> stack = new Stack<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            MirrorNode node = stack.pop();
            int depth = calculateDepth(node);
            depthCounts.put(depth, depthCounts.getOrDefault(depth, 0) + 1);

            for (TreeNode child : node.getChildren()) {
                stack.push((MirrorNode) child);
            }
        }

        if (depthCounts.size() <= 1) return 0.0;

        double avgNodesPerDepth = depthCounts.values().stream()
                .mapToInt(Integer::intValue).average().orElse(0.0);

        double variance = depthCounts.values().stream()
                .mapToDouble(count -> Math.pow(count - avgNodesPerDepth, 2))
                .average().orElse(0.0);

        return Math.sqrt(variance);
    }

    public int getTargetLinksPerNode() {
        return targetLinksPerNode;
    }

    public void setTargetLinksPerNode(int targetLinksPerNode) {
        this.targetLinksPerNode = Math.max(1, targetLinksPerNode);
    }
}