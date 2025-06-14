package org.lrdm.util;

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
     * - Genau ein Zentrumsknoten (hat nur Kinder, keinen Parent)
     * - Alle anderen Knoten sind Blätter (haben nur Parent, keine Kinder)
     * - Mindestens 3 Knoten (Zentrum + 2 Blätter)
     *
     * @return true wenn gültiger Stern
     */
    public boolean isValidStarStructure() {
        Set<TreeNode> allNodes = getAllNodesInStructure(); // Nutzt TreeNode-Methode

        if (allNodes.size() < 3) return false;

        TreeNode center = null;
        int leafCount = 0;

        for (TreeNode node : allNodes) {
            if (node.getParent() == null && !node.getChildren().isEmpty()) {
                if (center != null) return false;
                center = node;
            } else if (node.getParent() != null && node.getChildren().isEmpty()) {
                leafCount++;
            } else {
                return false;
            }
        }

        return center != null && leafCount == allNodes.size() - 1;
    }

    /**
     * Findet das Zentrum des Sterns.
     */
    public StarMirrorNode getCenter() {
        Set<TreeNode> allNodes = getAllNodesInStructure(); // Nutzt TreeNode-Methode

        for (TreeNode node : allNodes) {
            if (node.getParent() == null && !node.getChildren().isEmpty() && node instanceof StarMirrorNode) {
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
        Set<TreeNode> allNodes = getAllNodesInStructure(); // Nutzt TreeNode-Methode

        for (TreeNode node : allNodes) {
            if (node.getParent() != null && node.getChildren().isEmpty() && node instanceof StarMirrorNode) {
                leaves.add((StarMirrorNode) node);
            }
        }

        return leaves;
    }

    public boolean isCenter() {
        return getParent() == null && !getChildren().isEmpty();
    }

    public boolean isLeafOfStar() {
        return getParent() != null && getChildren().isEmpty();
    }
}