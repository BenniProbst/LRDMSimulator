package org.lrdm.topologies.base;

import org.lrdm.Mirror;

import java.util.*;

/**
 * Spezialisierte MirrorNode für Stern-Strukturen.
 * Validiert, dass die Struktur ein Stern ist (ein Zentrum mit mehreren Blättern oder Head-Nodes).
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public class StarMirrorNode extends MirrorNode {

    public StarMirrorNode(int id) {
        super(id);
    }

    public StarMirrorNode(int id, Mirror mirror) {
        super(id, mirror);
    }

    @Override
    public boolean canAcceptMoreChildren() {
        // Nur das Zentrum (Head-Node) kann Kinder akzeptieren
        return super.canAcceptMoreChildren() &&
                isValidStructure() &&
                isHead();
    }

    @Override
    public boolean canBeRemovedFromStructure(StructureNode structureRoot) {
        if (structureRoot == null) return false;

        Set<StructureNode> structureNodes = structureRoot.getAllNodesInStructure();

        // Ein Stern muss mindestens 3 Knoten haben (Zentrum + min. 2 Blätter)
        // Nach Entfernung müssen noch mindestens 3 Knoten übrig bleiben
        if (structureNodes.size() < 4) return false;

        // Nur Blätter (Terminal-Knoten, keine Head-Nodes) können entfernt werden
        return super.canBeRemovedFromStructure(structureRoot) &&
                isTerminal() && !isHead();
    }

    @Override
    public boolean isValidStructure(Set<StructureNode> allNodes) {
        // Zuerst die grundlegende MirrorNode-Strukturvalidierung
        if (!super.isValidStructure(allNodes)) {
            return false;
        }

        if (allNodes.size() < 3) return false; // Mindestens Zentrum + 2 Blätter

        // Sammle alle Stern-Knoten und finde Head-Knoten (Zentrum)
        Set<StarMirrorNode> starNodes = new HashSet<>();
        StarMirrorNode centerNode = null;

        for (StructureNode node : allNodes) {
            if (!(node instanceof StarMirrorNode starNode)) {
                return false; // Alle Knoten müssen StarMirrorNodes sein
            }

            starNodes.add(starNode);
            if (starNode.isHead()) {
                if (centerNode != null) return false; // Nur ein Zentrum erlaubt
                centerNode = starNode;
            }
        }

        if (centerNode == null) return false; // Ein Zentrum muss vorhanden sein

        // Validiere Stern-spezifische Eigenschaften
        for (StarMirrorNode starNode : starNodes) {
            if (!isValidStarNode(starNode, centerNode)) {
                return false;
            }
        }

        // Zentrum muss mindestens 2 Kinder haben
        if (centerNode.getChildren().size() < 2) return false;

        // Zentrum muss Edge-Links haben (Verbindung nach außen)
        return centerNode.getNumEdgeLinks() != 0;
    }

    /**
     * Validiert einen einzelnen Stern-Knoten.
     */
    private boolean isValidStarNode(StarMirrorNode starNode, StarMirrorNode centerNode) {
        StructureNode parent = starNode.getParent();
        Set<StructureNode> structureNodes = starNode.getAllNodesInStructure();

        if (starNode == centerNode) {
            // Zentrum: darf externen Parent haben, muss mindestens 2 Kinder haben
            if (starNode.getChildren().size() < 2) return false;

            if (parent != null) {
                return !structureNodes.contains(parent); // Zentrum-Parent muss extern sein
            }
        } else {
            // Blatt-Knoten: muss das Zentrum als Parent haben
            if (parent != centerNode) return false;

            // Blatt kann entweder keine Kinder haben oder Head einer anderen Struktur sein
            return starNode.isLeaf() || starNode.isHead(); // Nur Head-Nodes dürfen Kinder haben
        }

        return true;
    }

    /**
     * Wiederverwendung der StructureNode findHead() Funktion.
     * Findet das Zentrum des Sterns.
     */
    public StarMirrorNode getCenter() {
        StructureNode head = findHead(); // Wiederverwendung aus StructureNode
        return (head instanceof StarMirrorNode) ? (StarMirrorNode) head : null;
    }

    /**
     * Direkte Wiederverwendung von getEndpointsOfStructure() aus StructureNode.
     * Stern-Blätter sind exakt die Terminal-Knoten (Endpunkte) der Struktur.
     */
    public List<StarMirrorNode> getLeaves() {
        List<StarMirrorNode> leaves = new ArrayList<>();

        // Nutze StructureNode getEndpointsOfStructure() - das sind die Blätter!
        Set<StructureNode> endpoints = getEndpointsOfStructure();

        for (StructureNode endpoint : endpoints) {
            // Filtere nur echte Blätter (keine Head-Nodes)
            if (endpoint instanceof StarMirrorNode starNode && !starNode.isHead()) {
                leaves.add(starNode);
            }
        }

        return leaves;
    }

    /**
     * Nutzt getAllNodesInStructure() + StructureNode isHead() für alle Kind-Head-Nodes.
     */
    public List<StarMirrorNode> getChildHeads() {
        List<StarMirrorNode> childHeads = new ArrayList<>();

        Set<StructureNode> allNodes = getAllNodesInStructure();

        for (StructureNode node : allNodes) {
            // Head-Nodes mit Parent (außer Zentrum)
            if (node instanceof StarMirrorNode starNode &&
                    starNode.isHead() && starNode.getParent() != null) {
                childHeads.add(starNode);
            }
        }

        return childHeads;
    }

    /**
     * Prüft, ob dieser Knoten das Zentrum des Sterns ist.
     * Nutzt StructureNode isHead() und isLeaf() direkt.
     */
    public boolean isCenter() {
        return isHead() && !isLeaf();
    }

    /**
     * Prüft, ob dieser Knoten eine Kind-Head-Node des Sterns ist.
     * Nutzt StructureNode isHead() und getParent() direkt.
     */
    public boolean isChildHead() {
        return isHead() && getParent() != null;
    }
}