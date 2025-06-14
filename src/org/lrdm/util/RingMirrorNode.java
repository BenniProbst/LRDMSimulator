package org.lrdm.util;

import java.util.*;

/**
 * Spezialisierte MirrorNode für Ring-Strukturen.
 * Validiert, dass die Struktur tatsächlich ein Ring ist.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public class RingMirrorNode extends MirrorNode {

    public RingMirrorNode(int id) {
        super(id);
    }

    /**
     * Validiert, dass diese Struktur ein gültiger Ring ist.
     * - Jeder Knoten hat genau einen Parent und ein Kind
     * - Bildet einen geschlossenen Zyklus
     * - Mindestens 3 Knoten
     *
     * @return true wenn gültiger Ring
     */
    public boolean isValidRingStructure() {
        Set<TreeNode> allNodes = getAllNodesInStructure(); // Nutzt TreeNode-Methode

        if (allNodes.size() < 3) return false;

        for (TreeNode node : allNodes) {
            if (node.getParent() == null || node.getChildren().size() != 1) {
                return false;
            }
        }

        return hasClosedCycle(allNodes);
    }

    /**
     * Prüft, ob die Knoten einen geschlossenen Zyklus bilden.
     */
    private boolean hasClosedCycle(Set<TreeNode> nodes) {
        if (nodes.isEmpty()) return false;

        TreeNode start = nodes.iterator().next();
        TreeNode current = start;
        Set<TreeNode> visitedInCycle = new HashSet<>();

        do {
            if (visitedInCycle.contains(current)) {
                return visitedInCycle.size() == nodes.size();
            }
            visitedInCycle.add(current);

            if (current.getChildren().size() != 1) return false;
            current = current.getChildren().get(0);

        } while (current != start && visitedInCycle.size() <= nodes.size());

        return current == start && visitedInCycle.size() == nodes.size();
    }

    public RingMirrorNode getNextInRing() {
        if (getChildren().size() != 1) return null;
        TreeNode next = getChildren().get(0);
        return (next instanceof RingMirrorNode) ? (RingMirrorNode) next : null;
    }

    public RingMirrorNode getPreviousInRing() {
        TreeNode prev = getParent();
        return (prev instanceof RingMirrorNode) ? (RingMirrorNode) prev : null;
    }
}