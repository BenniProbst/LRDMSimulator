package org.lrdm.topologies.base;

import org.lrdm.Mirror;
import java.util.*;

/**
 * Spezialisierte MirrorNode für Linien-Strukturen.
 * Validiert, dass die Struktur eine gerade Linie ohne Zyklen ist.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public class LineMirrorNode extends MirrorNode {

    public LineMirrorNode(int id) {
        super(id);
    }

    public LineMirrorNode(int id, Mirror mirror) {
        super(id, mirror);
    }

    @Override
    public boolean canAcceptMoreChildren() {
        // In einer Linie kann jeder Knoten maximal 1 Kind haben
        // Nur wenn isValidStructure erfüllt ist und super.canAcceptMoreChildren auch
        return super.canAcceptMoreChildren() &&
                isValidStructure() &&
                getChildren().isEmpty();
    }

    @Override
    public boolean canBeRemovedFromStructure(TreeNode structureRoot) {
        if (structureRoot == null) return false;

        // Verwende die korrekte Strukturermittlung
        Set<TreeNode> structureNodes = structureRoot.getAllNodesInStructure();

        // Eine Linie muss mindestens 2 Knoten haben
        // Nach Entfernung müssen noch mindestens 2 Knoten übrig bleiben
        if (structureNodes.size() < 3) return false;

        // Nur Endpunkte können sicher entfernt werden ohne die Linie zu zerbrechen
        // Und nur wenn super.canBeRemovedFromStructure auch erfüllt ist
        return super.canBeRemovedFromStructure(structureRoot) && isEndpoint();
    }

    /**
     * Erweiterte Linien-Struktur-Validierung.
     * Zusätzlich zu super.isValidStructure:
     * - Alle MirrorNodes haben maximal einen Parent und ein Kind
     * - Head-Node darf einen externen Parent haben
     * - Head-Node muss mindestens einen Edge-Link haben (Verbindung nach außen)
     * - Struktur ist frei von Zyklen
     * - Genau 2 Terminal-Knoten (Endpunkte)
     */
    @Override
    public boolean isValidStructure(Set<TreeNode> allNodes) {
        // Zuerst die grundlegende MirrorNode-Strukturvalidierung
        if (!super.isValidStructure(allNodes)) {
            return false;
        }

        if (allNodes.size() < 2) return false;

        // Sammle alle Linien-Knoten und finde Head-Knoten
        Set<LineMirrorNode> lineNodes = new HashSet<>();
        LineMirrorNode headNode = null;

        for (TreeNode node : allNodes) {
            if (!(node instanceof LineMirrorNode lineNode)) {
                return false; // Alle Knoten müssen LineMirrorNodes sein
            }

            lineNodes.add(lineNode);
            if (lineNode.isHead()) {
                if (headNode != null) return false; // Nur ein Head erlaubt
                headNode = lineNode;
            }
        }

        if (headNode == null) return false; // Ein Head muss vorhanden sein

        // Validiere Linien-spezifische Eigenschaften
        for (LineMirrorNode lineNode : lineNodes) {
            if (!isValidLineNode(lineNode, headNode)) {
                return false;
            }
        }

        // Prüfe dass die Struktur frei von Zyklen ist (TreeNode-Funktion)
        if (hasClosedCycle(allNodes)) {
            return false; // Linien dürfen keine Zyklen haben
        }

        // Nutze bereits vorhandene getEndpointsOfStructure() aus TreeNode
        Set<TreeNode> endpoints = getEndpointsOfStructure();
        if (endpoints.size() != 2) return false; // Linie hat genau 2 Endpunkte

        // Head-Node muss Edge-Links haben (Verbindung nach außen)
        return headNode.getNumEdgeLinks() != 0; // Head muss mit externen Strukturen verbunden sein
    }

    /**
     * Validiert einen einzelnen Linien-Knoten.
     *
     * @param lineNode Der zu validierende Linien-Knoten
     * @param headNode Der Head-Knoten der Linie
     * @return true wenn der Knoten gültig ist
     */
    private boolean isValidLineNode(LineMirrorNode lineNode, LineMirrorNode headNode) {
        int degree = lineNode.getConnectivityDegree();

        // Terminal-Knoten (Endpunkte) haben Grad 1
        if (lineNode.isTerminal()) {
            if (degree != 1) return false;

            if (lineNode == headNode) {
                // Head-Endpunkt: darf externen Parent haben
                TreeNode parent = lineNode.getParent();
                if (parent != null) {
                    Set<TreeNode> structureNodes = lineNode.getAllNodesInStructure();
                    // Parent darf nicht Teil der Linien-Struktur sein
                    return !structureNodes.contains(parent); // Head-Parent muss extern sein
                }
            } else {
                // Normaler Endpunkt: muss einen Parent in der Struktur haben
                if (lineNode.getParent() == null) {
                    return false; // Endpunkt muss verbunden sein
                }

                Set<TreeNode> structureNodes = lineNode.getAllNodesInStructure();
                return structureNodes.contains(lineNode.getParent()); // Parent muss in der Struktur sein
            }
        } else {
            // Mittlere Knoten haben Grad 2 (ein Parent, ein Kind)
            if (degree != 2 || lineNode.getChildren().size() != 1) {
                return false;
            }

            if (lineNode == headNode) {
                // Head-Mittelknoten: darf externen Parent haben
                TreeNode parent = lineNode.getParent();
                if (parent != null) {
                    Set<TreeNode> structureNodes = lineNode.getAllNodesInStructure();
                    return !structureNodes.contains(parent); // Head-Parent muss extern sein
                }
            } else {
                // Normale Mittelknoten: Parent muss in der Struktur sein
                if (lineNode.getParent() == null) {
                    return false; // Mittelknoten muss Parent haben
                }

                Set<TreeNode> structureNodes = lineNode.getAllNodesInStructure();
                return structureNodes.contains(lineNode.getParent()); // Parent muss in der Struktur sein
            }
        }

        return true;
    }

    /**
     * Wiederverwendung der TreeNode getEndpointsOfStructure() Funktion.
     * Filtert nur LineMirrorNode-Instanzen heraus.
     */
    public List<LineMirrorNode> getEndpoints() {
        List<LineMirrorNode> endpoints = new ArrayList<>();

        // Nutze die bereits vorhandene TreeNode-Funktion
        Set<TreeNode> structureEndpoints = getEndpointsOfStructure();

        for (TreeNode endpoint : structureEndpoints) {
            if (endpoint instanceof LineMirrorNode lineNode) {
                endpoints.add(lineNode);
            }
        }

        return endpoints;
    }

    /**
     * Findet den anderen Endpunkt der Linie.
     *
     * @return Der andere Endpunkt oder null wenn dieser Knoten kein Endpunkt ist
     */
    public LineMirrorNode getOtherEndpoint() {
        if (!isEndpoint()) return null;

        List<LineMirrorNode> endpoints = getEndpoints();
        if (endpoints.size() != 2) return null;

        return endpoints.get(0) == this ? endpoints.get(1) : endpoints.get(0);
    }

    /**
     * Findet den Head-Knoten der Linie.
     * Nutzt findHead() aus TreeNode und castet sicher.
     *
     * @return Der Head-Knoten oder null wenn keiner gefunden wird
     */
    public LineMirrorNode getLineHead() {
        TreeNode head = findHead(); // Wiederverwendung aus TreeNode
        return (head instanceof LineMirrorNode) ? (LineMirrorNode) head : null;
    }

    /**
     * Prüft, ob dieser Knoten ein mittlerer Knoten der Linie ist.
     * Nutzt isTerminal() aus TreeNode.
     *
     * @return true wenn der Knoten genau 2 Verbindungen hat (nicht Terminal)
     */
    public boolean isMiddleNode() {
        return !isTerminal() && getConnectivityDegree() == 2;
    }

    /**
     * Berechnet die Position dieses Knotens in der Linie (0-basiert).
     * Nutzt getPathFromHead() aus TreeNode.
     *
     * @return Position vom Head-Endpunkt aus gezählt, oder -1 bei Fehler
     */
    public int getPositionInLine() {
        List<TreeNode> pathFromHead = getPathFromHead(); // Wiederverwendung aus TreeNode
        return pathFromHead.isEmpty() ? -1 : pathFromHead.size() - 1;
    }
}