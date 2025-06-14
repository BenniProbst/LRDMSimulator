package org.lrdm.topologies.builders;

import org.lrdm.Network;
import org.lrdm.topologies.base.MirrorNode;
import org.lrdm.topologies.base.TreeNode;
import org.lrdm.Mirror;

import java.util.*;

/**
 * Abstrakte Basisklasse für alle Tree-Builder.
 * Erweitert StructureBuilder für Baum-spezifische Funktionalitäten.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public abstract class TreeBuilder extends StructureBuilder {

    public TreeBuilder(Network network) {
        super(network);
    }

    public TreeBuilder(Network network, Iterator<Mirror> mirrorIterator) {
        super(network, mirrorIterator);
    }

    @Override
    public final MirrorNode build(int totalNodes) {
        return buildTree(totalNodes, getEffectiveMaxDepth());
    }

    /**
     * Erstellt einen Baum mit der angegebenen Anzahl von Knoten.
     * Muss von Kindklassen implementiert werden.
     *
     * @param totalNodes Anzahl der zu erstellenden Knoten
     * @param maxDepth Maximale Tiefe des Baums
     * @return Root-Knoten des erstellten Baums
     */
    public abstract MirrorNode buildTree(int totalNodes, int maxDepth);

    /**
     * Fügt Knoten zu einem bestehenden Baum hinzu.
     *
     * @param existingRoot Bestehender Baum-Root
     * @param nodesToAdd Anzahl hinzuzufügender Knoten
     * @param maxDepth Maximale Tiefe des Baums
     * @return Anzahl tatsächlich hinzugefügter Knoten
     */
    public abstract int addNodesToExistingTree(MirrorNode existingRoot, int nodesToAdd, int maxDepth);

    /**
     * Gibt die effektive maximale Tiefe zurück.
     * Wird von Kindklassen überschrieben.
     */
    protected abstract int getEffectiveMaxDepth();

    @Override
    public final boolean validateStructure(MirrorNode root) {
        return validateTreeStructure(root);
    }

    /**
     * Validiert, dass die Struktur ein gültiger Baum ist.
     */
    protected boolean validateTreeStructure(MirrorNode root) {
        if (root == null) return false;

        Set<TreeNode> allNodes = root.getAllNodesInStructure();
        Set<TreeNode> visited = new HashSet<>();

        // Prüfe auf Zyklen und korrekte Baum-Struktur
        return !hasCycles(root, visited, null) &&
                allNodes.size() == visited.size() &&
                hasValidTreeProperties(allNodes);
    }

    /**
     * Prüft auf Zyklen im Baum.
     */
    private boolean hasCycles(TreeNode node, Set<TreeNode> visited, TreeNode parent) {
        if (visited.contains(node)) return true;
        visited.add(node);

        for (TreeNode child : node.getChildren()) {
            if (child != parent && hasCycles(child, visited, node)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Validiert Baum-spezifische Eigenschaften.
     */
    private boolean hasValidTreeProperties(Set<TreeNode> allNodes) {
        // Ein Baum mit n Knoten hat genau n-1 Kanten
        int totalEdges = 0;
        for (TreeNode node : allNodes) {
            totalEdges += node.getChildren().size();
        }
        return totalEdges == allNodes.size() - 1;
    }

    /**
     * Berechnet die Tiefe eines Knotens relativ zur Root.
     */
    protected int calculateDepth(MirrorNode node) {
        int depth = 0;
        TreeNode current = node;

        while (current.getParent() != null) {
            depth++;
            current = current.getParent();
        }

        return depth;
    }

    @Override
    protected boolean isLeafInStructure(MirrorNode node) {
        return node.isLeaf();
    }

    @Override
    protected List<MirrorNode> findInsertionCandidates(MirrorNode root) {
        return findLeaves(root);
    }

    @Override
    protected List<MirrorNode> findRemovableNodes(MirrorNode root) {
        return findLeaves(root);
    }

    @Override
    protected boolean canAddNodeTo(MirrorNode node) {
        return super.canAddNodeTo(node) &&
                calculateDepth(node) < getEffectiveMaxDepth() - 1;
    }
}