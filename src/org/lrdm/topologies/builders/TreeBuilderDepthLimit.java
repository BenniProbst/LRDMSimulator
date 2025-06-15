
package org.lrdm.topologies.builders;

import org.lrdm.Network;
import org.lrdm.Mirror;
import org.lrdm.topologies.base.MirrorNode;
import org.lrdm.topologies.base.StructureNode;

import java.util.*;

/**
 * TreeBuilder-Implementation mit Tiefenbeschränkung (Depth-First-Ansatz).
 * Bevorzugt das Wachstum in die Tiefe vor der Breite.
 * Fügt Knoten basierend auf der geringsten Anzahl von Kindern ein.
 * Zustandslos - keine gespeicherten Tiefenwerte.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public class TreeBuilderDepthLimit extends TreeBuilder {
    private int maxDepth;

    public TreeBuilderDepthLimit(Network network, int maxDepth) {
        super(network);
        this.maxDepth = maxDepth;
    }

    public TreeBuilderDepthLimit(Network network, Iterator<Mirror> mirrorIterator, int maxDepth) {
        super(network, mirrorIterator);
        this.maxDepth = maxDepth;
    }

    @Override
    protected int getEffectiveMaxDepth() {
        return maxDepth;
    }

    @Override
    public MirrorNode buildTree(int totalNodes, int maxDepth) {
        if (totalNodes <= 0 || !mirrorIterator.hasNext()) return null;

        int effectiveMaxDepth = (maxDepth > 0) ? maxDepth : this.maxDepth;
        MirrorNode root = getMirrorNodeFromIterator();
        if (root == null || totalNodes == 1) return root;

        // Berechne verfügbare Mirrors aus Iterator
        int availableMirrors = countAvailableMirrors();
        int nodesToBuild = Math.min(totalNodes - 1, availableMirrors);

        addNodesDepthFirstBalanced(root, nodesToBuild, effectiveMaxDepth);
        return root;
    }

    /**
     * Zählt verfügbare Mirrors im Iterator ohne ihn zu konsumieren.
     */
    private int countAvailableMirrors() {
        int count = 0;
        // Erstelle eine Kopie des Iterators um zu zählen
        List<Mirror> mirrorList = new ArrayList<>();
        while (mirrorIterator.hasNext()) {
            mirrorList.add(mirrorIterator.next());
            count++;
        }
        // Erstelle neuen Iterator mit den gesammelten Mirrors
        mirrorIterator = mirrorList.iterator();
        return count;
    }

    /**
     * Zentrale Methode für das Hinzufügen von Knoten.
     * Eliminiert Code-Duplikation zwischen buildTree und addNodesToExistingTree.
     */
    private int addNodesDepthFirstBalanced(MirrorNode root, int nodesToAdd, int effectiveMaxDepth) {
        if (nodesToAdd <= 0) return 0;

        int nodesAdded = 0;

        while (nodesAdded < nodesToAdd && mirrorIterator.hasNext()) {
            // Finde den besten Einfügepunkt (Knoten mit wenigsten Kindern in erlaubter Tiefe)
            MirrorNode bestInsertionPoint = findBestInsertionPoint(root, effectiveMaxDepth);

            if (bestInsertionPoint == null) break; // Keine gültigen Einfügepunkte mehr

            MirrorNode newChild = getMirrorNodeFromIterator();
            if (newChild != null) {
                bestInsertionPoint.addChild(newChild);
                nodesAdded++;
            } else {
                break; // Keine Mirrors mehr verfügbar
            }
        }

        return nodesAdded;
    }

    /**
     * Findet den besten Einfügepunkt - Knoten mit wenigsten Kindern in erlaubter Tiefe.
     */
    private MirrorNode findBestInsertionPoint(MirrorNode root, int effectiveMaxDepth) {
        List<MirrorNode> candidates = new ArrayList<>();
        Stack<MirrorNode> stack = new Stack<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            MirrorNode current = stack.pop();
            int currentDepth = calculateDepth(current);

            // Nur Knoten innerhalb der Tiefenbeschränkung
            if (currentDepth < effectiveMaxDepth - 1) {
                candidates.add(current);
            }

            // Füge Kinder zum Stack hinzu (Depth-First)
            List<StructureNode> children = new ArrayList<>(current.getChildren());
            Collections.reverse(children); // Umkehren für korrekte Depth-First-Reihenfolge
            for (StructureNode child : children) {
                stack.push((MirrorNode) child);
            }
        }

        if (candidates.isEmpty()) return null;

        // Sortiere nach: 1. Tiefe (tiefer zuerst), 2. Anzahl Kinder (weniger zuerst)
        candidates.sort(Comparator
                .comparingInt(this::calculateDepth).reversed() // Tiefere zuerst
                .thenComparingInt(node -> node.getChildren().size())); // Weniger Kinder zuerst

        return candidates.get(0);
    }

    @Override
    public int addNodesToExistingTree(MirrorNode existingRoot, int nodesToAdd, int maxDepth) {
        if (existingRoot == null || nodesToAdd <= 0) return 0;

        int effectiveMaxDepth = (maxDepth > 0) ? maxDepth : this.maxDepth;
        int availableMirrors = countAvailableMirrors();
        int actualNodesToAdd = Math.min(nodesToAdd, availableMirrors);

        return addNodesDepthFirstBalanced(existingRoot, actualNodesToAdd, effectiveMaxDepth);
    }

    // Getter und Setter
    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }
}