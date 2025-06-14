package org.lrdm.topologies.base;

import java.util.*;

/**
 * Spezialisierte MirrorNode für Stern-Strukturen.
 * Validiert, dass die Struktur ein Stern ist (ein Zentrum mit mehreren Blättern).
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public class StarMirrorNode extends MirrorNode {

    public StarMirrorNode(int id) {
        super(id);
    }

    /**
     * Validiert, dass diese Struktur ein gültiger Stern ist.
     * - Genau ein Zentrumsknoten (Root mit Kindern)
     * - Alle anderen Knoten sind Blätter (Terminal-Knoten)
     * - Mindestens 3 Knoten (Zentrum + 2 Blätter)
     */
    public boolean isValidStarStructure() {
        Set<TreeNode> allNodes = getAllNodesInStructure();

        if (allNodes.size() < 3) return false;

        TreeNode center = null;
        int leafCount = 0;

        for (TreeNode node : allNodes) {
            if (node.isRoot() && !node.isLeaf()) {
                if (center != null) return false; // Nur ein Zentrum erlaubt
                center = node;
            } else if (node.isTerminal() && !node.isRoot()) {
                leafCount++;
            } else {
                return false; // Ungültige Knotenkonfiguration
            }
        }

        return center != null && leafCount == allNodes.size() - 1;
    }

    /**
     * Findet das Zentrum des Sterns.
     */
    public StarMirrorNode getCenter() {
        Set<TreeNode> allNodes = getAllNodesInStructure();

        for (TreeNode node : allNodes) {
            if (node.isRoot() && !node.isLeaf() && node instanceof StarMirrorNode) {
                return (StarMirrorNode) node;
            }
        }

        return null;
    }

    /**
     * Gibt alle Blätter des Sterns zurück.
     */
    public List<StarMirrorNode> getLeaves() {
        List<StarMirrorNode> leaves = new ArrayList<>();
        Set<TreeNode> allNodes = getAllNodesInStructure();

        for (TreeNode node : allNodes) {
            if (node.isTerminal() && !node.isRoot() && node instanceof StarMirrorNode) {
                leaves.add((StarMirrorNode) node);
            }
        }

        return leaves;
    }

    /**
     * Prüft, ob dieser Knoten das Zentrum des Sterns ist.
     */
    public boolean isCenter() {
        return isRoot() && !isLeaf();
    }

    /**
     * Prüft, ob dieser Knoten ein Blatt des Sterns ist.
     * Nutzt die fundamentale TreeNode-Methode.
     */
    public boolean isStarLeaf() {
        return isTerminal() && !isRoot();
    }
}