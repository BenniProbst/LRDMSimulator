package org.lrdm.topologies.base;

import org.lrdm.Mirror;
import java.util.*;

/**
 * Spezialisierte MirrorNode für Linien-Strukturen.
 * Validiert, dass die Struktur eine gerade Linie ist.
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
        // In einer Linie kann jeder Knoten maximal 1 Kind haben (außer Endpunkten)
        // Endpunkte (Terminal-Knoten) können 1 Kind haben
        // Mittlere Knoten dürfen nur 1 Kind haben (keine Verzweigungen)
        return getChildren().size() == 0 || (isTerminal() && getChildren().size() < 1);
    }

    @Override
    public boolean canBeRemovedFromStructure(MirrorNode structureRoot) {
        if (structureRoot == null) return false;
        if (this == structureRoot) return false; // Root kann nicht entfernt werden

        // Nur Endpunkte können sicher entfernt werden ohne die Linie zu zerbrechen
        return isLineEndpoint();
    }

    /**
     * Grundlegende Linien-Struktur-Validierung.
     * Fundamentale Methode für Linienstrukturen.
     */
    public boolean isValidStructure(Set<TreeNode> allNodes) {
        if (allNodes.size() < 2) return false;

        int terminalCount = 0;

        for (TreeNode node : allNodes) {
            int degree = node.getConnectivityDegree();

            if (degree == 1) {
                terminalCount++; // Terminal-Knoten (Endpunkt)
            } else if (degree == 2) {
                // Mittlerer Knoten - OK, aber keine Verzweigungen
                if (node.getChildren().size() > 1) return false;
            } else {
                return false; // Ungültiger Konnektivitätsgrad
            }
        }

        return terminalCount == 2; // Linie hat genau 2 Endpunkte
    }

    /**
     * Findet beide Endpunkte der Linie.
     */
    public List<LineMirrorNode> getEndpoints() {
        List<LineMirrorNode> endpoints = new ArrayList<>();
        Set<TreeNode> allNodes = getAllNodes();

        for (TreeNode node : allNodes) {
            if (node.isTerminal() && node instanceof LineMirrorNode) {
                endpoints.add((LineMirrorNode) node);
            }
        }

        return endpoints;
    }

    /**
     * Prüft, ob dieser Knoten ein Endpunkt der Linie ist.
     */
    public boolean isLineEndpoint() {
        return isTerminal();
    }
}