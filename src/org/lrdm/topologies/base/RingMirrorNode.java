
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
        // nur wenn isValidStructure erfüllt ist und super.canAcceptMoreChildren auch
        return super.canAcceptMoreChildren() &&
                isValidStructure() &&
                getChildren().isEmpty();
    }

    @Override
    public boolean canBeRemovedFromStructure(StructureNode structureRoot) {
        if (structureRoot == null) return false;

        // Verwende die korrekte Strukturermittlung
        Set<StructureNode> structureNodes = structureRoot.getAllNodesInStructure();

        // Ein Ring muss mindestens 3 Knoten haben.
        // Nach Entfernung müssen noch mindestens 3 Knoten übrig bleiben
        if (structureNodes.size() < 4) return false;

        // Nur wenn super.canBeRemovedFromStructure auch erfüllt ist
        return super.canBeRemovedFromStructure(structureRoot);
    }

    /**
     * Erweiterte Ring-Struktur-Validierung.
     * Zusätzlich zu super.isValidStructure und den bisherigen Ring-Validierungen:
     * - Alle MirrorNodes haben genau ein Parent und ein Kind
     * - Ausnahme: Head-Node darf einen externen Parent haben
     * - Head-Node muss mindestens einen Edge-Link haben (Verbindung nach außen)
     * - Alle anderen Knoten bilden einen geschlossenen Zyklus
     */
    @Override
    public boolean isValidStructure(Set<StructureNode> allNodes) {
        // Zuerst die grundlegende MirrorNode-Struktur validierung
        if (!super.isValidStructure(allNodes)) {
            return false;
        }

        if (allNodes.size() < 3) return false;

        // Sammle alle Ring-Knoten und finde Head-Knoten
        Set<RingMirrorNode> ringNodes = new HashSet<>();
        RingMirrorNode headNode = null;

        for (StructureNode node : allNodes) {
            if (!(node instanceof RingMirrorNode ringNode)) {
                return false; // Alle Knoten müssen RingMirrorNodes sein
            }

            ringNodes.add(ringNode);
            if (ringNode.isHead()) {
                if (headNode != null) return false; // Nur ein Head erlaubt
                headNode = ringNode;
            }
        }

        if (headNode == null) return false; // Ein Head muss vorhanden sein

        // Validiere Ring-spezifische Eigenschaften
        for (RingMirrorNode ringNode : ringNodes) {
            if (!isValidRingNode(ringNode, headNode)) {
                return false;
            }
        }

        // Prüfe geschlossenen Zyklus für alle Knoten
        if (!hasClosedCycle(allNodes)) {
            return false;
        }

        // Head-Node muss Edge-Links haben (Verbindung nach außen)
        return headNode.getNumEdgeLinks() != 0; // Head muss mit externen Strukturen verbunden sein
    }

    /**
     * Validiert einen einzelnen Ring-Knoten.
     *
     * @param ringNode Der zu validierende Ring-Knoten
     * @param headNode Der Head-Knoten des Rings
     * @return true wenn der Knoten gültig ist
     */
    private boolean isValidRingNode(RingMirrorNode ringNode, RingMirrorNode headNode) {
        // Jeder Knoten muss Interconnectivities 2 haben und genau ein Kind
        if (ringNode.getConnectivityDegree() != 2 || ringNode.getChildren().size() != 1) {
            return false;
        }

        if (ringNode == headNode) {
            // Head-Node darf einen externen Parent haben.
            // Parent kann null sein (kein externer Parent) oder außerhalb der Struktur
            StructureNode parent = ringNode.getParent();
            if (parent != null) {
                Set<StructureNode> structureNodes = ringNode.getAllNodesInStructure();
                // Parent darf nicht Teil der Ring-Struktur sein
                return !structureNodes.contains(parent); // Head-Parent muss extern sein
            }
        } else {
            // Normale Ring-Knoten: genau ein Parent und ein Kind, beide in der Struktur
            if (ringNode.getParent() == null) {
                return false; // Normale Knoten müssen einen Parent haben
            }

            // Parent muss Teil der Ring-Struktur sein
            Set<StructureNode> structureNodes = ringNode.getAllNodesInStructure();
            return structureNodes.contains(ringNode.getParent()); // Parent muss in der Struktur sein
        }

        return true;
    }

    /**
     * Erweiterte Ring-Knoten-Validierung.
     * Ein Ring-Knoten ist nur gültig, wenn:
     * - Die gesamte Struktur einen geschlossenen Zyklus bildet
     * - Die gesamte Struktur gültig ist
     * - Der Konnektivität-Grad 2 ist
     */
    public boolean isRingNode() {
        // Prüfe grundlegende Ring-Eigenschaften
        if (getConnectivityDegree() != 2) {
            return false;
        }

        // Sammle alle Knoten der Struktur
        Set<StructureNode> structureNodes = getAllNodesInStructure();

        // Prüfe geschlossenen Zyklus für alle MirrorNodes mit Mirrors
        Set<StructureNode> mirrorNodes = new HashSet<>();
        for (StructureNode node : structureNodes) {
            if (node instanceof MirrorNode mirrorNode && mirrorNode.getMirror() != null) {
                mirrorNodes.add(node);
            }
        }

        // Struktur muss gültig sein und geschlossenen Zyklus bilden
        return isValidStructure() &&
                !mirrorNodes.isEmpty() &&
                hasClosedCycle(mirrorNodes);
    }

    public RingMirrorNode getNextInRing() {
        if (getChildren().size() != 1) return null;
        StructureNode next = getChildren().get(0);
        return (next instanceof RingMirrorNode) ? (RingMirrorNode) next : null;
    }

    public RingMirrorNode getPreviousInRing() {
        StructureNode prev = getParent();
        // Für Head-Node kann der Parent extern sein
        if (isHead() && prev != null) {
            Set<StructureNode> structureNodes = getAllNodesInStructure();
            if (!structureNodes.contains(prev)) {
                return null; // Externer Parent
            }
        }
        return (prev instanceof RingMirrorNode) ? (RingMirrorNode) prev : null;
    }
}