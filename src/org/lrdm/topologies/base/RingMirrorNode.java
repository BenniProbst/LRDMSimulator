
package org.lrdm.topologies.base;

import org.lrdm.Mirror;
import java.util.*;

/**
 * Spezialisierte MirrorNode für Ring-Strukturen.
 * Validiert, dass die Struktur einen geschlossenen Ring bildet.
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class RingMirrorNode extends MirrorNode {

    // ===== KONSTRUKTOREN =====

    public RingMirrorNode(int id) {
        super(id);
    }

    public RingMirrorNode(int id, Mirror mirror) {
        super(id, mirror);
    }

    // ===== TYP-SYSTEM INTEGRATION =====

    @Override
    protected StructureType deriveTypeId() {
        return StructureType.RING;
    }

    /**
     * Ring-spezifische Head-Finding-Logik.
     * Überschreibt die Standard-Head-Finding für optimierte Ring-Navigation.
     */
    @Override
    public StructureNode findHead(StructureType typeId) {
        if (typeId != StructureType.RING) {
            return super.findHead(typeId); // Delegiere an Standard-Implementierung
        }

        // Ring-spezifische Head-Finding-Optimierung
        Set<StructureNode> visited = new HashSet<>();
        StructureNode current = this;

        while (current != null && !visited.contains(current)) {
            visited.add(current);

            if (current.isHead(typeId)) {
                return current;
            }

            // In Ringen können wir sowohl Parent als auch Child traversieren
            StructureNode parent = current.getParent();
            if (parent != null && !visited.contains(parent)) {
                current = parent;
                continue;
            }

            // Wenn kein Parent, versuche erstes Kind
            Set<StructureNode> children = current.getChildren(typeId, current.getId());
            if (!children.isEmpty()) {
                current = children.iterator().next();
            } else {
                break;
            }
        }

        return null; // Kein Head gefunden
    }

    // ===== STRUKTUR-MANAGEMENT =====

    @Override
    public boolean canAcceptMoreChildren() {
        StructureType typeId = StructureType.RING;
        StructureNode head = findHead(typeId);

        return super.canAcceptMoreChildren() &&
                isValidStructure() &&
                getChildren(typeId, head != null ? head.getId() : this.getId()).isEmpty();
    }

    @Override
    public boolean canBeRemovedFromStructure(StructureNode structureRoot) {
        if (structureRoot == null) return false;

        StructureType typeId = StructureType.RING;
        StructureNode head = findHead(typeId);
        if (head == null) head = structureRoot;

        Set<StructureNode> structureNodes = getAllNodesInStructure(typeId, head);

        // Ring muss mindestens 3 Knoten haben
        if (structureNodes.size() < 4) return false;

        return super.canBeRemovedFromStructure(structureRoot);
    }

    // ===== STRUKTUR-VALIDIERUNG (VEREINFACHT) =====

    /**
     * Ring-Struktur-Validierung mit expliziter RING-Typ-ID.
     * ERSETZT sowohl isValidStructure als auch isRingNode.
     */
    @Override
    public boolean isValidStructure(Set<StructureNode> allNodes, StructureType typeId, StructureNode head) {
        if (!super.isValidStructure(allNodes, typeId, head)) {
            return false;
        }

        if (allNodes.size() < 3) return false;

        Set<RingMirrorNode> ringNodes = new HashSet<>();
        RingMirrorNode headNode = null;

        for (StructureNode node : allNodes) {
            if (!(node instanceof RingMirrorNode ringNode)) {
                return false;
            }

            ringNodes.add(ringNode);
            if (ringNode.isHead(typeId)) {
                if (headNode != null) return false;
                headNode = ringNode;
            }
        }

        if (headNode == null) return false;

        // Validiere alle Ring-Knoten
        for (RingMirrorNode ringNode : ringNodes) {
            if (!isValidRingNode(ringNode, headNode, typeId)) {
                return false;
            }
        }

        return hasClosedCycle(allNodes, typeId, head) &&
                headNode.getNumEdgeLinks(typeId, head) > 0;
    }

    @Override
    public boolean isValidStructure(Set<StructureNode> allNodes) {
        StructureType typeId = StructureType.RING;
        StructureNode head = findHead(typeId);
        return isValidStructure(allNodes, typeId, head != null ? head : this);
    }

    /**
     * Convenience-Methode: Prüft ob die aktuelle Ring-Struktur gültig ist.
     * ERSETZT isRingNode() - eine Methode für beide Zwecke.
     */
    public boolean isValidStructure() {
        StructureType typeId = StructureType.RING;
        StructureNode head = findHead(typeId);
        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head != null ? head : this);
        return isValidStructure(allNodes, typeId, head != null ? head : this);
    }

    /**
     * Validiert einzelnen Ring-Knoten.
     */
    private boolean isValidRingNode(RingMirrorNode ringNode, RingMirrorNode headNode, StructureType typeId) {
        final int headId = headNode.getId();
        int degree = ringNode.getConnectivityDegree(typeId, headId);

        if (degree != 2 || ringNode.getChildren(typeId, headId).size() != 1) {
            return false;
        }

        StructureNode parent = ringNode.getParent();
        Set<StructureNode> structureNodes = ringNode.getAllNodesInStructure(typeId, headNode);

        if (ringNode == headNode) {
            // Head darf externen Parent haben
            if (parent != null) {
                return !structureNodes.contains(parent);
            }
        } else {
            // Normale Knoten müssen internen Parent haben
            if (parent == null) return false;
            return structureNodes.contains(parent);
        }

        return true;
    }

    // ===== RING-NAVIGATION =====

    public RingMirrorNode getNextInRing() {
        StructureType typeId = StructureType.RING;
        StructureNode head = findHead(typeId);
        final int headId = head != null ? head.getId() : this.getId();

        Set<StructureNode> children = getChildren(typeId, headId);
        if (children.size() != 1) return null;

        StructureNode next = children.iterator().next();
        return (next instanceof RingMirrorNode) ? (RingMirrorNode) next : null;
    }

    public RingMirrorNode getPreviousInRing() {
        StructureType typeId = StructureType.RING;
        StructureNode head = findHead(typeId);

        StructureNode prev = getParent();

        if (isHead(typeId) && prev != null) {
            Set<StructureNode> structureNodes = getAllNodesInStructure(typeId, head != null ? head : this);
            if (!structureNodes.contains(prev)) {
                return null; // Externer Parent
            }
        }

        return (prev instanceof RingMirrorNode) ? (RingMirrorNode) prev : null;
    }

    public RingMirrorNode getRingHead() {
        StructureNode head = findHead(StructureType.RING);
        return (head instanceof RingMirrorNode) ? (RingMirrorNode) head : null;
    }

    public int getRingSize() {
        StructureType typeId = StructureType.RING;
        StructureNode head = findHead(typeId);
        return getAllNodesInStructure(typeId, head != null ? head : this).size();
    }

    public int getPositionInRing() {
        StructureType typeId = StructureType.RING;
        StructureNode head = findHead(typeId);

        if (head == null || !(head instanceof RingMirrorNode ringHead)) return -1;

        RingMirrorNode current = ringHead;
        int position = 0;

        do {
            if (current == this) return position;
            current = current.getNextInRing();
            position++;
        } while (current != null && current != ringHead && position < getRingSize());

        return -1;
    }

    public List<RingMirrorNode> getAllRingNodes() {
        List<RingMirrorNode> ringNodes = new ArrayList<>();
        RingMirrorNode head = getRingHead();

        if (head == null) return ringNodes;

        RingMirrorNode current = head;
        int maxNodes = getRingSize();
        int count = 0;

        do {
            ringNodes.add(current);
            current = current.getNextInRing();
            count++;
        } while (current != null && current != head && count < maxNodes);

        return ringNodes;
    }

    // ===== HILFSMETHODEN =====

    private int getConnectivityDegree(StructureType typeId, int headId) {
        int connections = 0;

        if (getParent() != null) {
            ChildRecord parentRecord = getParent().findChildRecordById(getId());
            if (parentRecord != null && parentRecord.belongsToStructure(typeId, headId)) {
                connections++;
            }
        }

        connections += getChildren(typeId, headId).size();
        return connections;
    }
}