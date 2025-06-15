package org.lrdm.topologies.base;

import org.lrdm.Mirror;

import java.util.*;

/**
 * Spezialisierte MirrorNode für Baum-Strukturen.
 * Validiert, dass die Struktur tatsächlich ein Baum ist.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public class TreeMirrorNode extends MirrorNode {

    public TreeMirrorNode(int id) {
        super(id);
    }

    public TreeMirrorNode(int id, Mirror mirror) {
        super(id, mirror);
    }

    @Override
    public boolean canAcceptMoreChildren() {
        // Bäume können normalerweise immer weitere Kinder haben
        return super.canAcceptMoreChildren() && isValidStructure();
    }

    @Override
    public boolean canBeRemovedFromStructure(StructureNode structureRoot) {
        if (structureRoot == null) return false;

        // Nutze StructureNode canBeRemovedFromStructure() - das ist für Bäume perfekt.
        // Bäume erlauben Entfernung von Blättern (auch Root, wenn er Blatt ist)
        return super.canBeRemovedFromStructure(structureRoot);
    }

    /**
     * Erweiterte Baum-Struktur-Validierung.
     * Zusätzlich zu super.isValidStructure:
     * - Genau ein Root-Knoten (kein Parent, aber Head markiert)
     * - Keine Zyklen (nutzt hasClosedCycle() aus StructureNode)
     * - n Knoten haben genau n-1 Kanten
     * - Head-Node darf externen Parent haben und muss Edge-Links haben
     */
    @Override
    public boolean isValidStructure(Set<StructureNode> allNodes) {
        // Zuerst die grundlegende MirrorNode-Struktur validierung
        if (!super.isValidStructure(allNodes)) {
            return false;
        }

        if (allNodes.isEmpty()) return false;

        // Sammle alle Baum-Knoten und finde Head-Knoten (Root)
        Set<TreeMirrorNode> treeNodes = new HashSet<>();
        TreeMirrorNode rootNode = null;

        for (StructureNode node : allNodes) {
            if (!(node instanceof TreeMirrorNode treeNode)) {
                return false; // Alle Knoten müssen TreeMirrorNodes sein
            }

            treeNodes.add(treeNode);
            if (treeNode.isHead()) {
                if (rootNode != null) return false; // Nur ein Root erlaubt
                rootNode = treeNode;
            }
        }

        if (rootNode == null) return false; // Ein Root muss vorhanden sein

        // Prüfe auf Zyklen - Bäume dürfen keine haben
        if (hasClosedCycle(allNodes)) {
            return false; // Bäume sind Zyklen frei
        }

        // Ein Baum mit n Knoten hat genau n-1 Kanten.
        // Nutze getNumPlannedLinksFromStructure() aus StructureNode
        int expectedEdges = allNodes.size() - 1;
        int actualEdges = getNumPlannedLinksFromStructure();
        if (actualEdges != expectedEdges) {
            return false;
        }

        // Validiere Baum-spezifische Eigenschaften für alle Knoten
        for (TreeMirrorNode treeNode : treeNodes) {
            if (!isValidTreeNode(treeNode, rootNode)) {
                return false;
            }
        }

        // Root muss Edge-Links haben (Verbindung nach außen)
        return rootNode.getNumEdgeLinks() != 0;
    }

    /**
     * Validiert einen einzelnen Baum-Knoten.
     */
    private boolean isValidTreeNode(TreeMirrorNode treeNode, TreeMirrorNode rootNode) {
        StructureNode parent = treeNode.getParent();
        Set<StructureNode> structureNodes = treeNode.getAllNodesInStructure();

        if (treeNode == rootNode) {
            // Root-Knoten: darf externen Parent haben, aber kein interner Parent
            if (parent != null) {
                return !structureNodes.contains(parent); // Root-Parent muss extern sein
            }
        } else {
            // Normale Knoten: müssen genau einen Parent in der Struktur haben
            if (parent == null) {
                return false; // Knoten müssen verbunden sein
            }
            return structureNodes.contains(parent); // Parent muss in der Struktur sein
        }

        return true;
    }

    /**
     * Wiederverwendung der StructureNode findHead() Funktion.
     * Findet die Root des Baums.
     */
    public TreeMirrorNode getTreeRoot() {
        StructureNode head = findHead(); // Wiederverwendung aus StructureNode
        return (head instanceof TreeMirrorNode) ? (TreeMirrorNode) head : null;
    }

    /**
     * Direkte Wiederverwendung von getEndpointsOfStructure() aus StructureNode.
     * Baum-Blätter sind exakt die Terminal-Knoten (Endpunkte) der Struktur.
     */
    public List<TreeMirrorNode> getTreeLeaves() {
        List<TreeMirrorNode> leaves = new ArrayList<>();

        // Nutze StructureNode getEndpointsOfStructure() - das sind die Blätter!
        Set<StructureNode> endpoints = getEndpointsOfStructure();

        for (StructureNode endpoint : endpoints) {
            if (endpoint instanceof TreeMirrorNode treeNode) {
                leaves.add(treeNode);
            }
        }

        return leaves;
    }

    /**
     * Berechnet die Tiefe dieses Knotens im Baum.
     * Nutzt getPathFromHead() aus StructureNode für konsistente Pfadberechnung.
     */
    public int getDepthInTree() {
        List<StructureNode> pathFromRoot = getPathFromHead(); // Wiederverwendung aus StructureNode
        return pathFromRoot.isEmpty() ? 0 : pathFromRoot.size() - 1;
    }

    /**
     * Berechnet die maximale Tiefe des Baums.
     * Nutzt getAllNodesInStructure() und getDepthInTree() für alle Knoten.
     */
    public int getMaxTreeDepth() {
        int maxDepth = 0;
        Set<StructureNode> allNodes = getAllNodesInStructure(); // Wiederverwendung aus StructureNode

        for (StructureNode node : allNodes) {
            if (node instanceof TreeMirrorNode treeNode) {
                maxDepth = Math.max(maxDepth, treeNode.getDepthInTree());
            }
        }

        return maxDepth;
    }

    /**
     * Zählt die Gesamtanzahl der Knoten im Baum.
     * Nutzt getAllNodesInStructure() aus StructureNode.
     */
    public int getTreeSize() {
        return getAllNodesInStructure().size(); // Wiederverwendung aus StructureNode
    }

    /**
     * Prüft, ob dieser Baum balanciert ist.
     * Ein Baum ist balanciert, wenn sich die Tiefen der Blätter um maximal 1 unterscheiden.
     * Nutzt getTreeLeaves() und getDepthInTree().
     */
    public boolean isBalanced() {
        List<TreeMirrorNode> leaves = getTreeLeaves(); // Wiederverwendung
        if (leaves.isEmpty()) return true;

        int minDepth = Integer.MAX_VALUE;
        int maxDepth = 0;

        for (TreeMirrorNode leaf : leaves) {
            int depth = leaf.getDepthInTree(); // Wiederverwendung
            minDepth = Math.min(minDepth, depth);
            maxDepth = Math.max(maxDepth, depth);
        }

        // Unterschied zwischen tiefstem und flachstem Blatt > 1 = unbalanciert
        return maxDepth - minDepth <= 1;
    }
}