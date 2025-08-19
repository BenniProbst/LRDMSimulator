package org.lrdm.topologies.node;

import org.lrdm.Mirror;

import java.util.*;

/**
 * Spezialisierte MirrorNode-Implementation für tiefen-beschränkte Bäume.
 * Implementiert den Depth-First-Ansatz mit strikter Tiefenbegrenzung.
 * Bevorzugt Wachstum in die Tiefe vor der Breite unter Einhaltung der Maximaltiefe.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public class DepthLimitedTreeMirrorNode extends TreeMirrorNode {
    private int maxDepth;

    public DepthLimitedTreeMirrorNode(int id, int maxDepth) {
        super(id);
        this.maxDepth = Math.max(1, maxDepth);
        addNodeType(StructureType.DEPTH_LIMIT_TREE);
    }

    public DepthLimitedTreeMirrorNode(int id, Mirror mirror, int maxDepth) {
        super(id, mirror);
        this.maxDepth = Math.max(1, maxDepth);
        addNodeType(StructureType.DEPTH_LIMIT_TREE);
    }

    // ===== TIEFENBESCHRÄNKUNGS-SPEZIFISCHE METHODEN =====

    /**
     * Prüft, ob dieser Knoten innerhalb der erlaubten Tiefenbeschränkung liegt.
     *
     * @return true, wenn die Tiefe unter dem Maximum liegt
     */
    public boolean isWithinDepthLimit() {
        return getDepthInTree() < maxDepth;
    }

    /**
     * Prüft, ob an diesem Knoten weitere Kinder hinzugefügt werden können.
     * Berücksichtigt sowohl die Tiefenbeschränkung als auch andere Einschränkungen.
     *
     * @return true, wenn neue Kinder hinzugefügt werden können
     */
    public boolean canAddChildren() {
        return getDepthInTree() < maxDepth - 1;
    }

    /**
     * Überschreibt die Typ-Ableitung für die korrekte BALANCED_TREE-Identifikation.
     */
    @Override
    public StructureType deriveTypeId() {
        return StructureType.DEPTH_LIMIT_TREE;
    }

    /**
     * Berechnet die verbleibende Tiefe bis zum Maximum.
     *
     * @return Anzahl der noch möglichen Tiefenebenen
     */
    public int getRemainingDepth() {
        return Math.max(0, maxDepth - getDepthInTree() - 1);
    }

    /**
     * Findet den besten Einfüge-Punkt für neue Knoten basierend auf der Depth-First-Strategie.
     * Bevorzugt tiefere Knoten mit weniger Kindern.
     *
     * @return Der optimale Knoten für das Hinzufügen neuer Kinder, oder null, wenn nichts verfügbar ist
     */
    public DepthLimitedTreeMirrorNode findBestInsertionPoint() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        
        if (head == null) return null;

        List<DepthLimitedTreeMirrorNode> candidates = new ArrayList<>();
        Stack<DepthLimitedTreeMirrorNode> stack = new Stack<>();
        
        if (head instanceof DepthLimitedTreeMirrorNode depthHead) {
            stack.push(depthHead);
        }

        // Depth-First-Traversierung zur Kandidatensammlung
        while (!stack.isEmpty()) {
            DepthLimitedTreeMirrorNode current = stack.pop();
            
            // Nur Knoten innerhalb der Tiefenbeschränkung
            if (current.canAddChildren()) {
                candidates.add(current);
            }

            // Füge Kinder zum Stack hinzu (umgekehrte Reihenfolge für korrekte DFS)
            List<StructureNode> children = new ArrayList<>(current.getChildren(typeId));
            Collections.reverse(children);
            for (StructureNode child : children) {
                if (child instanceof DepthLimitedTreeMirrorNode depthChild) {
                    stack.push(depthChild);
                }
            }
        }

        if (candidates.isEmpty()) return null;

        // Sortiere nach: 1. Tiefe (tiefer zuerst), 2. Anzahl Kinder (weniger zuerst)
        candidates.sort(Comparator
                .comparingInt(DepthLimitedTreeMirrorNode::getDepthInTree).reversed()
                .thenComparingInt(node -> node.getChildren(typeId).size()));

        return candidates.get(0);
    }

    /**
     * Sammelt alle Knoten, die in der maximalen Tiefe stehen.
     *
     * @return Liste der Knoten auf der maximalen erlaubten Tiefe
     */
    public List<DepthLimitedTreeMirrorNode> getNodesAtMaxDepth() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        
        if (head == null) return new ArrayList<>();

        List<DepthLimitedTreeMirrorNode> maxDepthNodes = new ArrayList<>();
        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head);

        for (StructureNode node : allNodes) {
            if (node instanceof DepthLimitedTreeMirrorNode depthNode) {
                if (depthNode.getDepthInTree() == maxDepth - 1) {
                    maxDepthNodes.add(depthNode);
                }
            }
        }

        return maxDepthNodes;
    }


    /**
     * Berechnet die durchschnittliche Auslastung der Tiefenebenen.
     * Gibt ein Maß dafür, wie gut die verfügbare Tiefe genutzt wird.
     *
     * @return Verhältnis von genutzter zu maximaler Tiefe (0.0 bis 1.0)
     */
    public double calculateDepthUtilization() {
        int actualMaxDepth = findActualMaxDepthInTree();
        return actualMaxDepth == 0 ? 0.0 : (double) (actualMaxDepth + 1) / maxDepth;
    }

    /**
     * Findet die tatsächlich erreichte maximale Tiefe im Baum.
     * Nutzt die vorhandene getDepthInTree() Methode aus TreeMirrorNode.
     *
     * @return Die tatsächlich erreichte maximale Tiefe
     */
    private int findActualMaxDepthInTree() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);

        if (head == null) return 0;

        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head);
        int maxFoundDepth = 0;

        for (StructureNode node : allNodes) {
            if (node instanceof TreeMirrorNode treeNode) {
                // Nutze die vorhandene getDepthInTree() Methode
                maxFoundDepth = Math.max(maxFoundDepth, treeNode.getDepthInTree());
            }
        }

        return maxFoundDepth;
    }

    /**
     * Validiert, dass die Tiefenbeschränkung eingehalten wird.
     *
     * @return true, wenn alle Knoten die Tiefenbeschränkung respektieren
     */
    public boolean validateDepthConstraints() {
        // Verwende die isValidStructure() Methode aus TreeMirrorNode
        if (!isValidStructure()) return false;

        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);

        if (head == null) return false;

        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head);

        for (StructureNode node : allNodes) {
            if (node instanceof TreeMirrorNode treeNode) {
                if (treeNode.getDepthInTree() >= maxDepth) {
                    return false;
                }
            }
        }

        return true;
    }

    // ===== DEPTH-FIRST SPEZIFISCHE HILFSMETHODEN =====

    /**
     * Führt eine Depth-First-Traversierung durch und sammelt Knoten nach Tiefe.
     *
     * @return Map von Tiefe zu Liste der Knoten auf dieser Tiefe
     */
    public Map<Integer, List<DepthLimitedTreeMirrorNode>> getNodesByDepthDFS() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        
        if (head == null) return new HashMap<>();

        Map<Integer, List<DepthLimitedTreeMirrorNode>> nodesByDepth = new HashMap<>();
        Stack<DepthLimitedTreeMirrorNode> stack = new Stack<>();
        
        if (head instanceof DepthLimitedTreeMirrorNode depthHead) {
            stack.push(depthHead);
        }

        while (!stack.isEmpty()) {
            DepthLimitedTreeMirrorNode current = stack.pop();
            int depth = current.getDepthInTree();
            
            nodesByDepth.computeIfAbsent(depth, k -> new ArrayList<>()).add(current);
            
            // Füge Kinder in umgekehrter Reihenfolge hinzu für korrekte DFS
            List<StructureNode> children = new ArrayList<>(current.getChildren(typeId));
            Collections.reverse(children);
            for (StructureNode child : children) {
                if (child instanceof DepthLimitedTreeMirrorNode depthChild) {
                    stack.push(depthChild);
                }
            }
        }

        return nodesByDepth;
    }

    /**
     * Berechnet die Anzahl möglicher Einfüge-Punkte pro Tiefenebene.
     *
     * @return Map von Tiefe zu Anzahl verfügbarer Einfüge-Punkte
     */
    public Map<Integer, Integer> getInsertionPointsByDepth() {
        Map<Integer, List<DepthLimitedTreeMirrorNode>> nodesByDepth = getNodesByDepthDFS();
        Map<Integer, Integer> insertionPoints = new HashMap<>();

        for (Map.Entry<Integer, List<DepthLimitedTreeMirrorNode>> entry : nodesByDepth.entrySet()) {
            int depth = entry.getKey();
            List<DepthLimitedTreeMirrorNode> nodes = entry.getValue();
            
            int availableInsertions = 0;
            for (DepthLimitedTreeMirrorNode node : nodes) {
                if (node.canAddChildren()) {
                    availableInsertions++;
                }
            }
            
            insertionPoints.put(depth, availableInsertions);
        }

        return insertionPoints;
    }

    // ===== GETTER UND SETTER =====

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = Math.max(1, maxDepth);
    }

    /**
     * Gibt die effektive maximale Tiefe zurück.
     *
     * @return Die konfigurierte maximale Tiefe
     */
    public int getEffectiveMaxDepth() {
        return maxDepth;
    }

    @Override
    public String toString() {
        return String.format("DepthLimitedTreeMirrorNode{id=%d, maxDepth=%d, currentDepth=%d, utilization=%.2f}", 
                           getId(), maxDepth, getDepthInTree(), calculateDepthUtilization());
    }
}