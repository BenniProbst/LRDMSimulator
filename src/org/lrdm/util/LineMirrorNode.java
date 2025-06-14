package org.lrdm.util;

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
     * - Genau zwei Endpunkte (nur ein Kind oder nur ein Parent)
     * - Alle anderen Knoten haben genau einen Parent und ein Kind
     * - Keine Verzweigungen oder Zyklen
     *
     * @return true wenn gültige Linie
     */
    public boolean isValidLineStructure() {
        Set<TreeNode> allNodes = getAllNodesInStructure(); // Nutzt TreeNode-Methode

        if (allNodes.size() < 2) return false;

        int endpointCount = 0;

        for (TreeNode node : allNodes) {
            int connectionCount = node.getChildren().size() + (node.getParent() != null ? 1 : 0);

            if (connectionCount == 1) {
                endpointCount++;
            } else if (connectionCount == 2) {
                if (node.getChildren().size() > 1) return false;
            } else {
                return false;
            }
        }

        return endpointCount == 2;
    }

    /**
     * Findet beide Endpunkte der Linie.
     */
    public List<LineMirrorNode> getEndpoints() {
        List<LineMirrorNode> endpoints = new ArrayList<>();
        Set<TreeNode> allNodes = getAllNodesInStructure(); // Nutzt TreeNode-Methode

        for (TreeNode node : allNodes) {
            int connectionCount = node.getChildren().size() + (node.getParent() != null ? 1 : 0);
            if (connectionCount == 1 && node instanceof LineMirrorNode) {
                endpoints.add((LineMirrorNode) node);
            }
        }

        return endpoints;
    }
}