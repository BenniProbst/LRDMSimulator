
package org.lrdm.topologies.base;

import org.lrdm.Mirror;

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

    public RingMirrorNode(int id, Mirror mirror) {
        super(id, mirror);
    }

    @Override
    public boolean canAcceptMoreChildren() {
        // In einem Ring hat jeder Knoten genau ein Kind (zyklische Struktur)
        return getChildren().isEmpty();
    }

    @Override
    public boolean canBeRemovedFromStructure(TreeNode structureRoot) {
        // Prüfe, ob der Ring nach Entfernung noch mindestens 3 Knoten hat
        Set<TreeNode> allNodes = getAllNodesInStructure();

        // Ein Ring muss mindestens 3 Knoten haben
        // Wenn wir nur 3 Knoten haben, kann keiner entfernt werden
        return allNodes.size() >= 3 && super.canBeRemovedFromStructure(structureRoot);
    }

    /**
     * Validiert, dass diese Struktur ein gültiger Ring ist.
     * - Jeder Knoten hat Konnektivitätsgrad 2
     * - Bildet einen geschlossenen Zyklus
     * - Mindestens 3 Knoten
     */
    @Override
    public boolean isValidStructure(Set<TreeNode> allNodes) {

        if (allNodes.size() < 3) return false;

        // Jeder Knoten muss Konnektivitätsgrad 2 haben
        for (TreeNode node : allNodes) {
            if (node.getConnectivityDegree() != 2 || node.getChildren().size() != 1) {
                return false;
            }
        }

        return hasClosedCycle(allNodes);
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

    /**
     * Im Ring gibt es keine "Blätter" im traditionellen Sinne.
     * Alle Knoten haben den gleichen Konnektivitätsgrad.
     */
    public boolean isRingNode() {
        return getConnectivityDegree() == 2;
    }
}