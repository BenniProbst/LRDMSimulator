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
    public boolean canBeRemovedFromStructure(TreeNode structureRoot) {
        if (structureRoot == null) return false;

        Set<TreeNode> structureNodes = structureRoot.getAllNodesInStructure();

        // Ein Stern muss mindestens 3 Knoten haben (Zentrum + min. 2 Blätter)
        // Nach Entfernung müssen noch mindestens 3 Knoten übrig bleiben
        if (structureNodes.size() < 4) return false;

        // Nur Blätter (Terminal-Knoten, keine Head-Nodes) können entfernt werden
        return super.canBeRemovedFromStructure(structureRoot) &&
                isTerminal() && !isHead();
    }

    @Override
    public boolean isValidStructure(Set<TreeNode> allNodes) {
        // Zuerst die grundlegende MirrorNode-Strukturvalidierung
        if (!super.isValidStructure(allNodes)) {
            return false;
        }

        if (allNodes.size() < 3) return false; // Mindestens Zentrum + 2 Blätter

        // Sammle alle Stern-Knoten und finde Head-Knoten (Zentrum)
        Set<StarMirrorNode> starNodes = new HashSet<>();
        StarMirrorNode centerNode = null;

        for (TreeNode node : allNodes) {
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
        TreeNode parent = starNode.getParent();
        Set<TreeNode> structureNodes = starNode.getAllNodesInStructure();

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
            if (!starNode.isLeaf() && !starNode.isHead()) {
                return false; // Nur Head-Nodes dürfen Kinder haben
            }
        }

        return true;
    }

    /**
     * Wiederverwendung der TreeNode findHead() Funktion.
     * Findet das Zentrum des Sterns.
     */
    public StarMirrorNode getCenter() {
        TreeNode head = findHead(); // Wiederverwendung aus TreeNode
        return (head instanceof StarMirrorNode) ? (StarMirrorNode) head : null;
    }

    /**
     * Direkte Wiederverwendung von getEndpointsOfStructure() aus TreeNode.
     * Stern-Blätter sind exakt die Terminal-Knoten (Endpunkte) der Struktur.
     */
    public List<StarMirrorNode> getLeaves() {
        List<StarMirrorNode> leaves = new ArrayList<>();

        // Nutze TreeNode getEndpointsOfStructure() - das sind die Blätter!
        Set<TreeNode> endpoints = getEndpointsOfStructure();

        for (TreeNode endpoint : endpoints) {
            // Filtere nur echte Blätter (keine Head-Nodes)
            if (endpoint instanceof StarMirrorNode starNode && !starNode.isHead()) {
                leaves.add(starNode);
            }
        }

        return leaves;
    }

    /**
     * Nutzt getAllNodesInStructure() + TreeNode isHead() für alle Kind-Head-Nodes.
     */
    public List<StarMirrorNode> getChildHeads() {
        List<StarMirrorNode> childHeads = new ArrayList<>();

        Set<TreeNode> allNodes = getAllNodesInStructure();

        for (TreeNode node : allNodes) {
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
     * Nutzt TreeNode isHead() und isLeaf() direkt.
     */
    public boolean isCenter() {
        return isHead() && !isLeaf();
    }

    /**
     * Prüft, ob dieser Knoten eine Kind-Head-Node des Sterns ist.
     * Nutzt TreeNode isHead() und getParent() direkt.
     */
    public boolean isChildHead() {
        return isHead() && getParent() != null;
    }
}