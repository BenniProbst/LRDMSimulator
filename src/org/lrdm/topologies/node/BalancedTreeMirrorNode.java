package org.lrdm.topologies.node;

import org.lrdm.Mirror;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Spezialisierte MirrorNode-Implementation für balancierte Bäume.
 * Implementiert Breadth-First-Ansatz mit Balance-Optimierung ohne Tiefenbeschränkung.
 * Bietet erweiterte Funktionalitäten zur Balance-Messung und -Validierung.
 * <p>
 * **Balance-Eigenschaften**:
 * - Breadth-First-Wachstum für gleichmäßige Tiefenverteilung
 * - Konfigurierbare maximale Abweichung der Balance
 * - Balance-bewusste Einfügung und Entfernung von Knoten
 * - Unterstützt Balance-Metriken basierend auf Tiefenverteilung
 * <p>
 * **Balance-Erhaltung bei Entfernung**:
 * - Bevorzugt Entfernung von Knoten, die die Balance am wenigsten beeinträchtigen
 * - Berücksichtigt Tiefenverteilung und Knotendichte pro Ebene
 * - Versucht gleichmäßige Verteilung nach Entfernung zu erhalten
 * <p>
 * **Wiederverwendung der TreeMirrorNode-Funktionalität**:
 * - 90%+ der Traversierungs- und Validierungslogik wird wiederverwendet
 * - Fokussiert auf Balance-spezifische Validierung und Optimierung
 * - Erweiterte Balance-Analysefunktionen
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class BalancedTreeMirrorNode extends TreeMirrorNode {
    private int targetLinksPerNode;
    private double maxAllowedBalanceDeviation = 1.0;

    // ===== KONSTRUKTOREN =====

    public BalancedTreeMirrorNode(int id) {
        this(id, 2);
    }

    public BalancedTreeMirrorNode(int id, int targetLinksPerNode) {
        super(id);
        this.targetLinksPerNode = Math.max(1, targetLinksPerNode);
        this.maxAllowedBalanceDeviation = 1.0;
        addNodeType(StructureType.BALANCED_TREE);
    }

    public BalancedTreeMirrorNode(int id, Mirror mirror, int targetLinksPerNode) {
        super(id, mirror);
        this.targetLinksPerNode = Math.max(1, targetLinksPerNode);
        this.maxAllowedBalanceDeviation = 1.0;
        addNodeType(StructureType.BALANCED_TREE);
    }

    public BalancedTreeMirrorNode(int id, int targetLinksPerNode, double maxAllowedBalanceDeviation) {
        super(id);
        this.targetLinksPerNode = Math.max(1, targetLinksPerNode);
        this.maxAllowedBalanceDeviation = Math.max(0.1, maxAllowedBalanceDeviation);
        addNodeType(StructureType.BALANCED_TREE);
    }

    public BalancedTreeMirrorNode(int id, Mirror mirror, int targetLinksPerNode, double maxAllowedBalanceDeviation) {
        super(id, mirror);
        this.targetLinksPerNode = Math.max(1, targetLinksPerNode);
        this.maxAllowedBalanceDeviation = Math.max(0.1, maxAllowedBalanceDeviation);
        addNodeType(StructureType.BALANCED_TREE);
    }

    // ===== TYP-SYSTEM INTEGRATION =====

    /**
     * Überschreibt die Typ-Ableitung für die korrekte BALANCED_TREE-Identifikation.
     */
    @Override
    public StructureType deriveTypeId() {
        return StructureType.BALANCED_TREE;
    }

    // ===== STRUKTUR-MANAGEMENT =====

    /**
     * Prüft, ob dieser Knoten weitere Kinder akzeptieren kann unter Balance-Gesichtspunkten.
     */
    @Override
    public boolean canAcceptMoreChildren() {
        if (!super.canAcceptMoreChildren()) {
            return false;
        }

        StructureType typeId = deriveTypeId();
        int currentChildren = getChildren(typeId).size();

        return currentChildren < targetLinksPerNode;
    }

    /**
     * Prüft, ob dieser Knoten aus der Struktur entfernt werden kann unter Balance-Gesichtspunkten.
     */
    @Override
    public boolean canBeRemovedFromStructure(StructureNode structureRoot) {
        if (!super.canBeRemovedFromStructure(structureRoot)) {
            return false;
        }

        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);

        if (head == null) return false;

        Set<StructureNode> structureNodes = getAllNodesInStructure(typeId, head);

        // Mindestens 2 Knoten müssen für einen funktionsfähigen Baum bleiben
        if (structureNodes.size() < 3) return false;

        // Zusätzliche Balance-Prüfung: niedrigerer Balance-Impact ist besser
        double balanceImpact = calculateRemovalBalanceImpact();
        return balanceImpact <= maxAllowedBalanceDeviation;
    }

    /**
     * Erweiterte Balance-Baum-Struktur-Validierung mit expliziter BALANCED_TREE-Typ-ID.
     */
    @Override
    public boolean isValidStructure(Set<StructureNode> allNodes, StructureType typeId, StructureNode head) {
        // Basis-Validierung durch TreeMirrorNode
        if (!super.isValidStructure(allNodes, typeId, head)) {
            return false;
        }

        // Balance-spezifische Validierung nur für BALANCED_TREE
        if (typeId != StructureType.BALANCED_TREE) {
            return false;
        }

        // Balance-Validierung
        if (!isBalanced()) {
            return false;
        }

        // Validiere targetLinksPerNode für alle Knoten
        for (StructureNode node : allNodes) {
            if (node instanceof BalancedTreeMirrorNode balancedNode) {
                int childrenCount = balancedNode.getChildren(typeId).size();
                if (childrenCount > balancedNode.getTargetLinksPerNode()) {
                    return false;
                }
            }
        }

        return true;
    }

    // ===== BALANCE-SPEZIFISCHE METHODEN =====

    /**
     * Berechnet die Balance des Baums basierend auf der Tiefenverteilung.
     * Eine niedrigere Zahl bedeutet bessere Balance.
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
     * Prüft, ob der Baum mit den konfigurierten Einstellungen balanciert ist.
     */
    public boolean isBalanced() {
        return calculateTreeBalance() <= maxAllowedBalanceDeviation;
    }

    /**
     * Findet den optimalen Einfüge-Punkt für neue Knoten zur Erhaltung der Balance.
     */
    public List<BalancedTreeMirrorNode> findBalancedInsertionCandidates() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);

        if (head == null) return new ArrayList<>();

        List<BalancedTreeMirrorNode> candidates = new ArrayList<>();
        Queue<BalancedTreeMirrorNode> queue = new LinkedList<>();

        if (head instanceof BalancedTreeMirrorNode balancedHead) {
            queue.offer(balancedHead);
        }

        // Breadth-First-Traversierung zur Kandidatensammlung
        while (!queue.isEmpty()) {
            BalancedTreeMirrorNode current = queue.poll();

            if (current.canAcceptMoreChildren()) {
                candidates.add(current);
            }

            for (StructureNode child : current.getChildren(typeId)) {
                if (child instanceof BalancedTreeMirrorNode balancedChild) {
                    queue.offer(balancedChild);
                }
            }
        }

        return candidates.stream()
                .sorted(this::compareInsertionCandidates)
                .collect(Collectors.toList());
    }

    /**
     * Findet die besten Kandidaten für die Balance-erhaltende Entfernung.
     */
    public List<BalancedTreeMirrorNode> findBalancedRemovalCandidates() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);

        if (head == null) return new ArrayList<>();

        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head);
        List<BalancedTreeMirrorNode> candidates = allNodes.stream()
                .filter(node -> node != head && node instanceof BalancedTreeMirrorNode)
                .map(BalancedTreeMirrorNode.class::cast)
                .filter(node -> node.canBeRemovedFromStructure(head))
                .toList();

        return candidates.stream()
                .sorted(this::compareRemovalCandidates)
                .collect(Collectors.toList());
    }

    /**
     * Berechnet den Balance-Impact, wenn dieser Knoten entfernt würde.
     */
    public double calculateRemovalBalanceImpact() {
        StructureType typeId = deriveTypeId();

        double currentBalance = calculateTreeBalance();

        StructureNode originalParent = getParent();
        Set<StructureNode> originalChildren = new HashSet<>(getChildren(typeId));

        try {
            if (originalParent != null) {
                originalParent.removeChild(this, Set.of(typeId));
            }

            if (originalParent != null) {
                for (StructureNode child : originalChildren) {
                    originalParent.addChild(child, Set.of(typeId), Map.of(typeId, originalParent.getId()));
                }
            }

            double newBalance = calculateTreeBalance();
            return Math.abs(newBalance - currentBalance);

        } finally {
            if (originalParent != null) {
                originalParent.addChild(this, Set.of(typeId), Map.of(typeId, originalParent.getId()));
                for (StructureNode child : originalChildren) {
                    originalParent.removeChild(child, Set.of(typeId));
                    this.addChild(child, Set.of(typeId), Map.of(typeId, this.getId()));
                }
            }
        }
    }

    /**
     * Berechnet die Tiefenverteilung im Baum.
     */
    public Map<Integer, Integer> getDepthDistribution() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);

        if (head == null) return new HashMap<>();

        Map<Integer, Integer> depthCounts = new HashMap<>();
        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head);

        for (StructureNode node : allNodes) {
            if (node instanceof TreeMirrorNode treeNode) {
                int depth = treeNode.getDepthInTree();
                depthCounts.put(depth, depthCounts.getOrDefault(depth, 0) + 1);
            }
        }

        return depthCounts;
    }

    /**
     * Findet den besten Einfüge-Punkt für neue Knoten basierend auf der Balance-Strategie.
     */
    public BalancedTreeMirrorNode findBestInsertionPoint() {
        List<BalancedTreeMirrorNode> candidates = findBalancedInsertionCandidates();
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /**
     * Ermittelt die optimale Tiefe für neue Knoten basierend auf der aktuellen Balance.
     */
    public int getOptimalInsertionDepth() {
        Map<Integer, Integer> depthDistribution = getDepthDistribution();

        if (depthDistribution.isEmpty()) return 0;

        return depthDistribution.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(0);
    }

    // ===== HILFSMETHODEN FÜR BALANCE-OPTIMIERUNG =====

    private int compareInsertionCandidates(BalancedTreeMirrorNode candidate1, BalancedTreeMirrorNode candidate2) {
        int depthCompare = Integer.compare(candidate1.getDepthInTree(), candidate2.getDepthInTree());
        if (depthCompare != 0) return depthCompare;

        StructureType typeId = deriveTypeId();
        int children1 = candidate1.getChildren(typeId).size();
        int children2 = candidate2.getChildren(typeId).size();
        int childrenCompare = Integer.compare(children1, children2);
        if (childrenCompare != 0) return childrenCompare;

        int capacity1 = candidate1.getTargetLinksPerNode() - children1;
        int capacity2 = candidate2.getTargetLinksPerNode() - children2;
        int capacityCompare = Integer.compare(capacity2, capacity1);
        if (capacityCompare != 0) return capacityCompare;

        return Integer.compare(candidate1.getId(), candidate2.getId());
    }

    private int compareRemovalCandidates(BalancedTreeMirrorNode candidate1, BalancedTreeMirrorNode candidate2) {
        StructureType typeId = deriveTypeId();

        int children1 = candidate1.getChildren(typeId).size();
        int children2 = candidate2.getChildren(typeId).size();
        int childrenCompare = Integer.compare(children1, children2);
        if (childrenCompare != 0) return childrenCompare;

        int depthCompare = Integer.compare(candidate2.getDepthInTree(), candidate1.getDepthInTree());
        if (depthCompare != 0) return depthCompare;

        double impact1 = candidate1.calculateRemovalBalanceImpact();
        double impact2 = candidate2.calculateRemovalBalanceImpact();
        int impactCompare = Double.compare(impact1, impact2);
        if (impactCompare != 0) return impactCompare;

        return Integer.compare(candidate2.getId(), candidate1.getId());
    }

    // ===== GETTER UND SETTER =====

    public int getTargetLinksPerNode() {
        return targetLinksPerNode;
    }

    public void setTargetLinksPerNode(int targetLinksPerNode) {
        this.targetLinksPerNode = Math.max(1, targetLinksPerNode);
    }

    public double getMaxAllowedBalanceDeviation() {
        return maxAllowedBalanceDeviation;
    }

    public void setMaxAllowedBalanceDeviation(double maxAllowedBalanceDeviation) {
        this.maxAllowedBalanceDeviation = Math.max(0.1, maxAllowedBalanceDeviation);
    }

    // ===== STRING REPRESENTATION =====

    @Override
    public String toString() {
        String baseString = super.toString();
        double balance = calculateTreeBalance();
        boolean isBalanced = isBalanced();
        StructureType typeId = deriveTypeId();
        int childrenCount = getChildren(typeId).size();

        return baseString + String.format("[balance=%.2f, isBalanced=%s, children=%d/%d, maxDev=%.2f]",
                balance, isBalanced, childrenCount, targetLinksPerNode, maxAllowedBalanceDeviation);
    }
}