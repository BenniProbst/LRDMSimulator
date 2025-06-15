
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
        // Nur wenn isValidStructure erfüllt ist und super.canAcceptMoreChildren auch
        return super.canAcceptMoreChildren() &&
                isValidStructure() &&
                isHead();
    }

    @Override
    public boolean canBeRemovedFromStructure(TreeNode structureRoot) {
        if (structureRoot == null) return false;

        // Verwende die korrekte Strukturermittlung
        Set<TreeNode> structureNodes = structureRoot.getAllNodesInStructure();

        // Ein Stern muss mindestens 3 Knoten haben (Zentrum + min. 2 Blätter)
        // Nach Entfernung müssen noch mindestens 3 Knoten übrig bleiben
        if (structureNodes.size() < 4) return false;

        // Nur Blätter können entfernt werden, nicht das Zentrum
        // Und nur wenn super.canBeRemovedFromStructure auch erfüllt ist
        return super.canBeRemovedFromStructure(structureRoot) && isStarLeaf();
    }

    /**
     * Erweiterte Stern-Struktur-Validierung.
     * Zusätzlich zu super.isValidStructure:
     * - Genau eine Head-Node (Zentrum)
     * - Head-Node darf einen externen Parent haben
     * - Head-Node muss mindestens einen Edge-Link haben (Verbindung nach außen)
     * - Alle Kinder der Head-Node sind entweder Blätter oder Head-Nodes
     * - Mindestens 2 Kinder am Zentrum
     */
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
        return centerNode.getNumEdgeLinks() != 0; // Zentrum muss mit externen Strukturen verbunden sein
    }

    /**
     * Validiert einen einzelnen Stern-Knoten.
     *
     * @param starNode Der zu validierende Stern-Knoten
     * @param centerNode Der Zentrum-Knoten des Sterns
     * @return true wenn der Knoten gültig ist
     */
    private boolean isValidStarNode(StarMirrorNode starNode, StarMirrorNode centerNode) {
        TreeNode parent = starNode.getParent();
        Set<TreeNode> structureNodes = starNode.getAllNodesInStructure();

        if (starNode == centerNode) {
            // Zentrum (Head-Node): darf externen Parent haben, muss mindestens 2 Kinder haben
            if (starNode.getChildren().size() < 2) return false;

            if (parent != null) {
                return !structureNodes.contains(parent); // Zentrum-Parent muss extern sein
            }
        } else {
            // Blatt-Knoten: muss das Zentrum als Parent haben, keine eigenen Kinder (außer Head-Nodes)
            if (parent != centerNode) return false; // Parent muss das Zentrum sein

            // Blatt kann entweder keine Kinder haben (echtes Blatt) oder Head einer anderen Struktur sein
            if (!starNode.getChildren().isEmpty() && !starNode.isHead()) {
                return false; // Nur Head-Nodes dürfen Kinder haben
            }
        }

        return true;
    }

    /**
     * Wiederverwendung der TreeNode findHead() Funktion.
     * Findet das Zentrum des Sterns und castet sicher.
     *
     * @return Das Zentrum oder null wenn keins gefunden wird
     */
    public StarMirrorNode getCenter() {
        TreeNode head = findHead(); // Wiederverwendung aus TreeNode
        return (head instanceof StarMirrorNode) ? (StarMirrorNode) head : null;
    }

    /**
     * Gibt alle Blätter des Sterns zurück (ohne Head-Nodes).
     * Filtert die Kinder des Zentrums nach echten Blättern.
     */
    public List<StarMirrorNode> getLeaves() {
        List<StarMirrorNode> leaves = new ArrayList<>();
        StarMirrorNode center = getCenter();

        if (center == null) return leaves;

        for (TreeNode child : center.getChildren()) {
            if (child instanceof StarMirrorNode starChild && !starChild.isHead()) {
                leaves.add(starChild);
            }
        }

        return leaves;
    }

    /**
     * Gibt alle Kind-Head-Nodes des Sterns zurück.
     * Dies sind Head-Nodes anderer Strukturen, die als Kinder des Zentrums fungieren.
     */
    public List<StarMirrorNode> getChildHeads() {
        List<StarMirrorNode> childHeads = new ArrayList<>();
        StarMirrorNode center = getCenter();

        if (center == null) return childHeads;

        for (TreeNode child : center.getChildren()) {
            if (child instanceof StarMirrorNode starChild && starChild.isHead()) {
                childHeads.add(starChild);
            }
        }

        return childHeads;
    }

    /**
     * Prüft, ob dieser Knoten das Zentrum des Sterns ist.
     * Zentrum ist die Head-Node mit Kindern.
     */
    public boolean isCenter() {
        return isHead() && !isLeaf();
    }

    /**
     * Prüft, ob dieser Knoten ein echtes Blatt des Sterns ist.
     * Echte Blätter haben keine Kinder und sind keine Head-Nodes.
     */
    public boolean isStarLeaf() {
        return isLeaf() && !isHead();
    }

    /**
     * Prüft, ob dieser Knoten eine Kind-Head-Node des Sterns ist.
     * Kind-Head-Nodes sind Head-Nodes anderer Strukturen, die am Stern hängen.
     */
    public boolean isChildHead() {
        return isHead() && getParent() != null;
    }

    /**
     * Berechnet die Anzahl der direkten Verbindungen vom Zentrum.
     * Nutzt getCenter() und zählt dessen Kinder.
     *
     * @return Anzahl der Stern-Strahlen oder -1 wenn dies nicht das Zentrum ist
     */
    public int getStarDegree() {
        if (!isCenter()) return -1;
        return getChildren().size();
    }
}