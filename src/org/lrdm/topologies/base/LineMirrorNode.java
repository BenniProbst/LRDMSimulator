
package org.lrdm.topologies.base;

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

    /**
     * Validiert, dass diese Struktur eine gültige Linie ist.
     * - Genau zwei Terminal-Knoten (Endpunkte)
     * - Alle anderen Knoten haben Konnektivitätsgrad 2
     * - Keine Verzweigungen oder Zyklen
     */
    public boolean isValidLineStructure() {
        Set<TreeNode> allNodes = getAllNodesInStructure();

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
        Set<TreeNode> allNodes = getAllNodesInStructure();

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