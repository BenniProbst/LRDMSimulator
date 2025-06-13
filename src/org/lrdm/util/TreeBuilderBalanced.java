
package org.lrdm.util;

import java.util.*;

/**
 * TreeBuilder-Implementation für balancierte Bäume (Breadth-First-Ansatz).
 * Ohne Tiefenbeschränkung, aber mit balanciertem Einfügen und Löschen.
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

        MirrorNode root = new MirrorNode(getNextId(), 0);
        if (totalNodes == 1) return root;

        buildBalanced(root, totalNodes - 1, maxDepth); // -1 weil root bereits erstellt
        return root;
    }

    /**
     * Erstellt einen balancierten Baum mit Breadth-First-Ansatz.
     *
     * @param root Root-Knoten
     * @param remainingNodes Verbleibende zu erstellende Knoten
     * @param maxDepth Maximale Tiefe (0 für unbegrenzt)
     */
    private void buildBalanced(MirrorNode root, int remainingNodes, int maxDepth) {
        if (remainingNodes <= 0) return;

        Queue<MirrorNode> queue = new LinkedList<>();
        queue.offer(root);
        int nodesAdded = 0;

        while (!queue.isEmpty() && nodesAdded < remainingNodes) {
            MirrorNode current = queue.poll();

            // Prüfe Tiefenbeschränkung
            if (maxDepth > 0 && current.getDepth() >= maxDepth - 1) {
                continue;
            }

            // Berechne optimale Anzahl Kinder für Balance
            int childrenToAdd = calculateOptimalChildren(current, remainingNodes - nodesAdded, queue.size());

            for (int i = 0; i < childrenToAdd && nodesAdded < remainingNodes; i++) {
                MirrorNode child = new MirrorNode(getNextId(), current.getDepth() + 1);
                current.addChild(child);
                queue.offer(child);
                nodesAdded++;
            }
        }
    }

    /**
     * Berechnet die optimale Anzahl von Kindern für einen Knoten.
     *
     * @param node Aktueller Knoten
     * @param remainingNodes Verbleibende Knoten
     * @param queueSize Aktuelle Größe der Queue
     * @return Optimale Anzahl Kinder
     */
    private int calculateOptimalChildren(MirrorNode node, int remainingNodes, int queueSize) {
        if (remainingNodes <= 0) return 0;

        // Standardmäßig targetLinksPerNode Kinder
        int baseChildren = Math.min(targetLinksPerNode, remainingNodes);

        // Anpassung für bessere Balance
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

    /**
     * Fügt Knoten balanciert zu einem bestehenden Baum hinzu.
     *
     * @param existingRoot Root des bestehenden Baums
     * @param nodesToAdd Anzahl hinzuzufügender Knoten
     * @param maxDepth Maximale Tiefe
     * @return Anzahl tatsächlich hinzugefügter Knoten
     */
    private int addNodesToExistingTreeBalanced(MirrorNode existingRoot, int nodesToAdd, int maxDepth) {
        List<MirrorNode> candidates = findBalancedInsertionCandidates(existingRoot, maxDepth);
        int added = 0;

        // Breadth-First-Einfügung für Balance
        while (added < nodesToAdd && !candidates.isEmpty()) {
            // Wähle den besten Kandidaten (niedrigste Tiefe, wenigste Kinder)
            MirrorNode bestCandidate = selectBestBalancedParent(candidates);

            if (bestCandidate != null && bestCandidate.getChildren().size() < targetLinksPerNode) {
                // Prüfe Tiefenbeschränkung
                if (maxDepth > 0 && bestCandidate.getDepth() >= maxDepth - 1) {
                    candidates.remove(bestCandidate);
                    continue;
                }

                MirrorNode newChild = new MirrorNode(getNextId(), bestCandidate.getDepth() + 1);
                bestCandidate.addChild(newChild);
                added++;

                // Füge neues Kind zur Kandidatenliste hinzu
                candidates.add(newChild);

                // Entferne Kandidat wenn er voll ist
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

    /**
     * Findet alle möglichen Einfügepunkte und bewertet sie nach Balance-Kriterien.
     *
     * @param root Root-Knoten
     * @param maxDepth Maximale Tiefe
     * @return Liste sortierter Einfügekandidaten
     */
    private List<MirrorNode> findBalancedInsertionCandidates(MirrorNode root, int maxDepth) {
        List<MirrorNode> candidates = new ArrayList<>();
        findCandidatesRecursive(root, candidates, maxDepth);

        // Filtere nur Knoten, die noch Platz für Kinder haben
        candidates = candidates.stream()
                .filter(node -> node.getChildren().size() < targetLinksPerNode)
                .filter(node -> maxDepth <= 0 || node.getDepth() < maxDepth - 1)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        // Sortiere nach Balance-Kriterien: niedrigere Tiefe zuerst, dann weniger Kinder
        candidates.sort(Comparator.comparingInt((MirrorNode a) -> a.getDepth())
                .thenComparingInt(a -> a.getChildren().size()));

        return candidates;
    }

    /**
     * Rekursive Suche nach Einfügekandidaten.
     */
    private void findCandidatesRecursive(MirrorNode node, List<MirrorNode> candidates, int maxDepth) {
        if (maxDepth <= 0 || node.getDepth() < maxDepth - 1) {
            candidates.add(node);
        }

        for (TreeNode child : node.getChildren()) {
            findCandidatesRecursive((MirrorNode) child, candidates, maxDepth);
        }
    }

    /**
     * Wählt den besten Einfügepunkt basierend auf Balance-Kriterien.
     *
     * @param candidates Liste der Kandidaten
     * @return Bester Kandidat oder null
     */
    private MirrorNode selectBestBalancedParent(List<MirrorNode> candidates) {
        if (candidates.isEmpty()) return null;

        // Wähle Knoten mit niedrigster Tiefe und wenigsten Kindern
        return candidates.stream()
                .min(Comparator.comparingInt((MirrorNode a) -> a.getDepth())
                        .thenComparingInt(a -> a.getChildren().size()))
                .orElse(null);
    }

    /**
     * Berechnet eine Balance-Metrik für den Baum.
     *
     * @param root Root-Knoten
     * @return Balance-Wert (niedrigere Werte = bessere Balance)
     */
    public double calculateTreeBalance(MirrorNode root) {
        if (root == null) return 0.0;

        Map<Integer, Integer> depthCounts = new HashMap<>();
        calculateDepthDistribution(root, depthCounts);

        // Berechne Standardabweichung der Tiefenverteilung
        double avgNodesPerDepth = depthCounts.values().stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);

        double variance = depthCounts.values().stream()
                .mapToDouble(count -> Math.pow(count - avgNodesPerDepth, 2))
                .average()
                .orElse(0.0);

        return Math.sqrt(variance);
    }

    /**
     * Berechnet die Verteilung der Knoten nach Tiefe.
     */
    private void calculateDepthDistribution(MirrorNode node, Map<Integer, Integer> depthCounts) {
        depthCounts.merge(node.getDepth(), 1, Integer::sum);

        for (TreeNode child : node.getChildren()) {
            calculateDepthDistribution((MirrorNode) child, depthCounts);
        }
    }

    /**
     * Getter für Target-Links pro Knoten.
     */
    public int getTargetLinksPerNode() {
        return targetLinksPerNode;
    }

    /**
     * Setter für Target-Links pro Knoten.
     */
    public void setTargetLinksPerNode(int targetLinksPerNode) {
        this.targetLinksPerNode = targetLinksPerNode;
    }
}