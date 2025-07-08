package org.lrdm.topologies.node;

import org.lrdm.Mirror;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Spezialisierte MirrorNode-Implementation für balancierte Bäume.
 * Implementiert Breadth-First-Ansatz mit Balance-Optimierung ohne Tiefenbeschränkung.
 * Bietet erweiterte Funktionalitäten zur Balance-Messung und -Validierung.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public class BalancedTreeMirrorNode extends TreeMirrorNode {
    private int targetLinksPerNode;

    public BalancedTreeMirrorNode(int id) {
        this(id, 2);
    }

    public BalancedTreeMirrorNode(int id, int targetLinksPerNode) {
        super(id);
        this.targetLinksPerNode = Math.max(1, targetLinksPerNode);
        addNodeType(StructureType.BALANCED_TREE);
    }

    public BalancedTreeMirrorNode(int id, Mirror mirror, int targetLinksPerNode) {
        super(id, mirror);
        this.targetLinksPerNode = Math.max(1, targetLinksPerNode);
        addNodeType(StructureType.BALANCED_TREE);
    }

    @Override
    public StructureType deriveTypeId() {
        return StructureType.BALANCED_TREE;
    }

    // ===== BALANCE-SPEZIFISCHE METHODEN =====

    /**
     * Berechnet die Balance des Baums basierend auf der Tiefenverteilung.
     * Eine niedrigere Zahl bedeutet bessere Balance.
     *
     * @return Balance-Metrik (Standardabweichung der Knoten pro Tiefe)
     */
    public double calculateTreeBalance() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        
        if (head == null) return 0.0;

        Map<Integer, Integer> depthCounts = new HashMap<>();
        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head);

        for (StructureNode node : allNodes) {
            if (node instanceof TreeMirrorNode treeNode) {
                int depth = treeNode.getDepthInTree();
                depthCounts.put(depth, depthCounts.getOrDefault(depth, 0) + 1);
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

    /**
     * Prüft, ob der Baum die Balance-Kriterien erfüllt.
     * Ein Baum gilt als balanciert, wenn die Balance-Metrik unter einem Schwellwert liegt.
     *
     * @param maxAllowedBalance Maximaler Balance-Wert für akzeptable Balance
     * @return true, wenn der Baum ausreichend balanciert ist
     */
    public boolean isBalanced(double maxAllowedBalance) {
        return calculateTreeBalance() <= maxAllowedBalance;
    }

    /**
     * Findet den optimalen Einfüge-Punkt für neue Knoten zur Erhaltung der Balance.
     * Bevorzugt Knoten mit weniger Kindern in niedrigeren Tiefen.
     *
     * @return Liste von Kandidaten-Knoten, sortiert nach Einfügungsoptimalitäten
     */
    public List<BalancedTreeMirrorNode> findBalancedInsertionCandidates() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);

        if (head == null) return new ArrayList<>();

        List<BalancedTreeMirrorNode> candidates = new ArrayList<>();
        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head);

        for (StructureNode node : allNodes) {
            if (node instanceof BalancedTreeMirrorNode balancedNode) {
                int currentChildren = balancedNode.getChildren(typeId).size();
                if (currentChildren < targetLinksPerNode) {
                    candidates.add(balancedNode);
                }
            }
        }

        // Sortiere nach: 1. Tiefe (flacher zuerst), 2. Anzahl Kinder (weniger zuerst)
        candidates.sort(Comparator
                .<BalancedTreeMirrorNode>comparingInt(TreeMirrorNode::getDepthInTree)
                .thenComparingInt(node -> node.getChildren(typeId).size()));

        return candidates;
    }

    /**
     * Berechnet die optimale Anzahl Kinder für einen Knoten basierend auf Balance-Kriterien.
     *
     * @param remainingNodes Anzahl noch zu verteilender Knoten
     * @param availableParents Anzahl verfügbarer Parent-Knoten
     * @return Optimale Anzahl Kinder für diesen Knoten
     */
    public int calculateOptimalChildren(int remainingNodes, int availableParents) {
        if (remainingNodes <= 0) return 0;
        
        int baseChildren = Math.min(targetLinksPerNode, remainingNodes);
        
        if (availableParents > 0) {
            int avgChildren = (int) Math.ceil((double) remainingNodes / availableParents);
            baseChildren = Math.min(baseChildren, avgChildren);
        }
        
        return Math.max(1, baseChildren);
    }

    /**
     * Validiert die Balance-spezifischen Eigenschaften der Baum-Struktur.
     *
     * @return true, wenn die Struktur balanciert und gültig ist
     */
    public boolean validateBalancedStructure() {
        // Verwende die isValidStructure() Methode aus TreeMirrorNode
        if (!isValidStructure()) return false;

        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);

        if (head == null) return false;

        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head);

        // Prüfe, dass kein Knoten mehr als targetLinksPerNode Kinder hat
        for (StructureNode node : allNodes) {
            if (node.getChildren(typeId).size() > targetLinksPerNode) {
                return false;
            }
        }

        return true;
    }

    // ===== BREADTH-FIRST SPEZIFISCHE HILFSMETHODEN =====

    /**
     * Führt eine Breadth-First-Traversierung durch und gibt die Knoten per Ebene zurück.
     *
     * @return Map von Tiefe zu Liste der Knoten auf dieser Tiefe
     */
    public Map<Integer, List<BalancedTreeMirrorNode>> getNodesByDepth() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        
        if (head == null) return new HashMap<>();

        Map<Integer, List<BalancedTreeMirrorNode>> nodesByDepth = new HashMap<>();
        Queue<BalancedTreeMirrorNode> queue = new LinkedList<>();
        
        if (head instanceof BalancedTreeMirrorNode balancedHead) {
            queue.offer(balancedHead);
        }

        while (!queue.isEmpty()) {
            BalancedTreeMirrorNode current = queue.poll();
            int depth = current.getDepthInTree();
            
            nodesByDepth.computeIfAbsent(depth, k -> new ArrayList<>()).add(current);
            
            for (StructureNode child : current.getChildren(typeId)) {
                if (child instanceof BalancedTreeMirrorNode balancedChild) {
                    queue.offer(balancedChild);
                }
            }
        }

        return nodesByDepth;
    }

    /**
     * Berechnet die durchschnittliche Anzahl Kinder pro Ebene.
     *
     * @return Map von Tiefe zu durchschnittlicher Kinderanzahl
     */
    public Map<Integer, Double> getAverageChildrenPerDepth() {
        Map<Integer, List<BalancedTreeMirrorNode>> nodesByDepth = getNodesByDepth();
        Map<Integer, Double> averageByDepth = new HashMap<>();

        for (Map.Entry<Integer, List<BalancedTreeMirrorNode>> entry : nodesByDepth.entrySet()) {
            int depth = entry.getKey();
            List<BalancedTreeMirrorNode> nodes = entry.getValue();
            
            double avgChildren = nodes.stream()
                    .mapToInt(node -> node.getChildren(deriveTypeId()).size())
                    .average()
                    .orElse(0.0);
                    
            averageByDepth.put(depth, avgChildren);
        }

        return averageByDepth;
    }

    // ===== GETTER UND SETTER =====

    public int getTargetLinksPerNode() {
        return targetLinksPerNode;
    }

    public void setTargetLinksPerNode(int targetLinksPerNode) {
        this.targetLinksPerNode = Math.max(1, targetLinksPerNode);
    }

    /**
     * Gibt die maximale Tiefe ohne Beschränkung zurück (für Balance-Bäume).
     *
     * @return Integer.MAX_VALUE (keine Tiefenbeschränkung)
     */
    public int getEffectiveMaxDepth() {
        return Integer.MAX_VALUE;
    }

    @Override
    public String toString() {
        return String.format("BalancedTreeMirrorNode{id=%d, targetLinks=%d, balance=%.2f}", 
                           getId(), targetLinksPerNode, calculateTreeBalance());
    }
}