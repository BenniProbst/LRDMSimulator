package org.lrdm.topologies.node;

import org.lrdm.Mirror;

import java.util.*;

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

    // Map für die Balance-Berechnung nach Tiefe und Knotenverteilung
    private Map<Integer, Map<BalancedTreeMirrorNode, BalanceInfo>> balanceMap = new HashMap<>();

    /**
     * Innere Klasse zur Speicherung der Balance-Informationen pro Knoten
     */
    private static class BalanceInfo {
        int actualChildCount;      // Tatsächliche Anzahl der Kinder
        int expectedChildCount;    // Erwartete Anzahl basierend auf targetLinksPerNode

        public BalanceInfo(int actual, int expected) {
            this.actualChildCount = actual;
            this.expectedChildCount = expected;
        }

        public double getDeviation() {
            return Math.abs(actualChildCount - expectedChildCount);
        }

        @Override
        public String toString() {
            return String.format("actual=%d, expected=%d, deviation=%.2f",
                    actualChildCount, expectedChildCount, getDeviation());
        }
    }

    /**
     * Aktualisiert die Balance-Map für den gesamten Baum.
     * Dies sollte nach jeder strukturellen Änderung aufgerufen werden.
     */
    private void updateBalanceMap() {
        balanceMap.clear();
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);

        if (head == null) return;

        // Sammle alle Knoten und analysiere deren Balance
        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head);
        Map<Integer, List<BalancedTreeMirrorNode>> nodesByDepth = new HashMap<>();

        // Klassifiziere Knoten nach Tiefe
        for (StructureNode node : allNodes) {
            if (node instanceof BalancedTreeMirrorNode treeNode) {
                int depth = treeNode.getDepthInTree();
                nodesByDepth.computeIfAbsent(depth, k -> new ArrayList<>()).add(treeNode);
            }
        }

        // Berechne Balance für jeden Knoten in jeder Tiefe
        for (Map.Entry<Integer, List<BalancedTreeMirrorNode>> entry : nodesByDepth.entrySet()) {
            int depth = entry.getKey();
            List<BalancedTreeMirrorNode> nodesAtDepth = entry.getValue();
            Map<BalancedTreeMirrorNode, BalanceInfo> balanceInfoMap = new HashMap<>();

            for (BalancedTreeMirrorNode node : nodesAtDepth) {
                int childCount = node.getChildren(typeId).size();
                // Der erwartete Wert ist targetLinksPerNode, außer für die tiefsten Knoten (Blätter)
                int expectedChildCount = node.isLeaf() ? 0 : node.targetLinksPerNode;
                balanceInfoMap.put(node, new BalanceInfo(childCount, expectedChildCount));
            }

            balanceMap.put(depth, balanceInfoMap);
        }
    }

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
     * Berechnet ein Maß für die Balance des Baums.
     * <p>
     * Die Balance wird durch folgende Faktoren bestimmt:
     * 1. Abweichung vom Zielwert (targetLinksPerNode) für jeden inneren Knoten
     * 2. Einheitlichkeit der Knotenverteilung auf gleicher Ebene
     *
     * @return Ein Wert zwischen 0.0 (perfekt balanciert) und höheren Werten (weniger balanciert)
     */
    public double calculateTreeBalance() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);

        if (head == null) return 0.0; // Ein leerer Baum ist perfekt balanciert

        // 1. Sammle Knoten nach Tiefe
        Map<Integer, List<BalancedTreeMirrorNode>> nodesByDepth = new HashMap<>();
        getAllNodesInStructure(typeId, head).stream()
                .filter(node -> node instanceof BalancedTreeMirrorNode)
                .map(node -> (BalancedTreeMirrorNode) node)
                .forEach(node -> {
                    int depth = node.getDepthInTree();
                    nodesByDepth.computeIfAbsent(depth, k -> new ArrayList<>()).add(node);
                });

        // 2. Berechne die zwei Balance-Komponenten
        double targetDeviation = calculateTargetDeviation(nodesByDepth, typeId);
        double levelUniformity = calculateLevelUniformity(nodesByDepth, typeId);

        // 3. Gewichtete Kombination der Komponenten (kann angepasst werden)
        return (targetDeviation + levelUniformity) / 2.0;
    }

    /**
     * Berechnet die durchschnittliche Abweichung vom targetLinksPerNode für alle inneren Knoten.
     */
    private double calculateTargetDeviation(Map<Integer, List<BalancedTreeMirrorNode>> nodesByDepth,
                                            StructureType typeId) {
        int totalNodes = 0;
        double totalDeviation = 0.0;

        for (List<BalancedTreeMirrorNode> nodes : nodesByDepth.values()) {
            for (BalancedTreeMirrorNode node : nodes) {
                // Blätter werden nicht berücksichtigt
                if (!node.isLeaf()) {
                    int childCount = node.getChildren(typeId).size();
                    double deviation = Math.abs(childCount - targetLinksPerNode);
                    totalDeviation += deviation;
                    totalNodes++;
                }
            }
        }

        return totalNodes > 0 ? totalDeviation / totalNodes : 0.0;
    }

    /**
     * Berechnet, wie einheitlich die Kinder auf gleicher Ebene verteilt sind.
     */
    private double calculateLevelUniformity(Map<Integer, List<BalancedTreeMirrorNode>> nodesByDepth,
                                            StructureType typeId) {
        double totalVariance = 0.0;
        int levels = 0;

        for (List<BalancedTreeMirrorNode> nodesAtLevel : nodesByDepth.values()) {
            // Nur Ebenen mit mehreren Knoten berücksichtigen
            if (nodesAtLevel.size() <= 1) continue;

            // Anzahl der Kinder für jeden Knoten auf dieser Ebene
            List<Integer> childCounts = nodesAtLevel.stream()
                    .map(node -> node.getChildren(typeId).size())
                    .toList();

            // Durchschnitt und Varianz berechnen
            double average = childCounts.stream().mapToInt(Integer::intValue).average().orElse(0.0);
            double variance = childCounts.stream()
                    .mapToDouble(count -> Math.pow(count - average, 2))
                    .average().orElse(0.0);

            totalVariance += Math.sqrt(variance); // Standardabweichung verwenden
            levels++;
        }

        return levels > 0 ? totalVariance / levels : 0.0;
    }

    /**
     * Prüft, ob der Baum mit den konfigurierten Einstellungen balanciert ist.
     */
    public boolean isBalanced() {
        return calculateTreeBalance() <= maxAllowedBalanceDeviation;
    }

    /**
     * Berechnet die Auswirkung der Entfernung dieses Knotens auf die Baum-Balance.
     */
    public double calculateRemovalBalanceImpact() {
        StructureType typeId = deriveTypeId();

        // Aktuelle Balance vor der Entfernung
        double currentBalance = calculateTreeBalance();

        // Temporäre Entfernung simulieren
        StructureNode originalParent = getParent();
        Set<StructureNode> originalChildren = new HashSet<>(getChildren(typeId));

        try {
            // Entferne temporär den Knoten aus der Struktur
            if (originalParent != null) {
                originalParent.removeChild(this);
            }

            // Kinder zum Elternknoten hinzufügen (falls vorhanden)
            if (originalParent != null) {
                for (StructureNode child : originalChildren) {
                    this.removeChild(child);
                    originalParent.addChild(child);
                }
            }

            // Neue Balance nach der Entfernung berechnen
            updateBalanceMap(); // Map aktualisieren für die neue Struktur
            double newBalance = calculateTreeBalance();

            // Auswirkung als absolute Differenz
            return Math.abs(newBalance - currentBalance);

        } finally {
            // Struktur wiederherstellen
            if (originalParent != null) {
                // Kinder zurück zum ursprünglichen Knoten hinzufügen
                for (StructureNode child : originalChildren) {
                    if (child.getParent() == originalParent) {
                        originalParent.removeChild(child);
                        this.addChild(child);
                    }
                }

                // Knoten wieder zum Elternteil hinzufügen
                originalParent.addChild(this);
            }

            // Map-Aktualisierung nach Wiederherstellung
            updateBalanceMap();
        }
    }

    /**
     * Findet Knoten, die optimale Kandidaten für das Hinzufügen neuer Kinder sind.
     * Diese sollten die Balance des Baums am wenigsten beeinträchtigen.
     */
    public List<BalancedTreeMirrorNode> findBalancedInsertionCandidates() {
        updateBalanceMap();
        StructureType typeId = deriveTypeId();

        List<BalancedTreeMirrorNode> candidates = new ArrayList<>();

        // Finde Knoten mit der größten negativen Abweichung (weniger Kinder als erwartet)
        for (Map<BalancedTreeMirrorNode, BalanceInfo> depthMap : balanceMap.values()) {
            for (Map.Entry<BalancedTreeMirrorNode, BalanceInfo> entry : depthMap.entrySet()) {
                BalancedTreeMirrorNode node = entry.getKey();
                BalanceInfo info = entry.getValue();

                // Knoten mit weniger Kindern als erwartet sind gute Kandidaten
                if (info.actualChildCount < info.expectedChildCount) {
                    candidates.add(node);
                }
            }
        }

        // Sortiere nach der Abweichung (größte negative Abweichung zuerst)
        candidates.sort(Comparator.comparingDouble(node -> {
            int depth = ((BalancedTreeMirrorNode) node).getDepthInTree();
            BalanceInfo info = balanceMap.get(depth).get(node);
            return info.expectedChildCount - info.actualChildCount; // Negative Werte zuerst
        }).reversed());

        return candidates;
    }

    /**
     * Findet Knoten, die optimale Kandidaten für das Entfernen sind.
     * Diese sollten die Balance des Baums am wenigsten beeinträchtigen.
     */
    public List<BalancedTreeMirrorNode> findBalancedRemovalCandidates() {
        updateBalanceMap();

        List<BalancedTreeMirrorNode> candidates = new ArrayList<>();

        // Finde Knoten mit der größten positiven Abweichung (mehr Kinder als erwartet)
        for (Map<BalancedTreeMirrorNode, BalanceInfo> depthMap : balanceMap.values()) {
            for (Map.Entry<BalancedTreeMirrorNode, BalanceInfo> entry : depthMap.entrySet()) {
                BalancedTreeMirrorNode node = entry.getKey();
                BalanceInfo info = entry.getValue();

                // Knoten mit mehr Kindern als erwartet sind gute Kandidaten für Entfernung
                if (info.actualChildCount > info.expectedChildCount) {
                    candidates.add(node);
                }
            }
        }

        // Sortiere nach der Abweichung (größte positive Abweichung zuerst)
        candidates.sort(Comparator.comparingDouble(node -> {
            int depth = ((BalancedTreeMirrorNode) node).getDepthInTree();
            BalanceInfo info = balanceMap.get(depth).get(node);
            return info.actualChildCount - info.expectedChildCount; // Positive Werte zuerst
        }).reversed());

        return candidates;
    }


    /**
     * Berechnet die Tiefen verteilung im Baum.
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

    /**
     * Erweitertes toString() für die Darstellung des Balancierten Baums
     */
    @Override
    public String toString() {
        String baseString = super.toString();
        updateBalanceMap(); // Sicherstellen, dass die Balance-Map aktuell ist

        StringBuilder builder = new StringBuilder(baseString);
        builder.append("\nBalance Information:\n");
        builder.append(String.format("Balance Score: %.2f (max allowed: %.2f)\n",
                calculateTreeBalance(), maxAllowedBalanceDeviation));
        builder.append(String.format("Is Balanced: %s\n", isBalanced() ? "Yes" : "No"));
        builder.append(String.format("Target Links Per Node: %d\n", targetLinksPerNode));

        // Balance-Details nach Tiefe anzeigen
        builder.append("Balance Details by Depth:\n");
        for (Map.Entry<Integer, Map<BalancedTreeMirrorNode, BalanceInfo>> entry : balanceMap.entrySet()) {
            int depth = entry.getKey();
            Map<BalancedTreeMirrorNode, BalanceInfo> nodesAtDepth = entry.getValue();

            builder.append(String.format("  Depth %d (%d nodes):\n", depth, nodesAtDepth.size()));
            for (Map.Entry<BalancedTreeMirrorNode, BalanceInfo> nodeEntry : nodesAtDepth.entrySet()) {
                BalancedTreeMirrorNode node = nodeEntry.getKey();
                BalanceInfo info = nodeEntry.getValue();

                builder.append(String.format("    Node %d: %s\n", node.getId(), info));
            }
        }

        return builder.toString();
    }
}