package org.lrdm.topologies.builders;

import org.lrdm.topologies.base.TreeNode;
import org.lrdm.topologies.base.MirrorNode;
import org.lrdm.topologies.base.TreeMirrorNode;

/**
 * Abstrakte Basisklasse für TreeBuilder-Implementierungen.
 * Erweitert StructureBuilder für Baum-spezifische Funktionalität.
 * Zustandslos - jede Methode analysiert/erstellt vollständig neu.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public abstract class TreeBuilder extends StructureBuilder {

    /**
     * Erstellt einen neuen Baum mit der angegebenen Anzahl von Knoten.
     *
     * @param totalNodes Anzahl der zu erstellenden Knoten
     * @param maxDepth Maximale Tiefe des Baums
     * @return Root-Knoten des erstellten Baums
     */
    public abstract MirrorNode buildTree(int totalNodes, int maxDepth);

    /**
     * Implementiert StructureBuilder.build() für Bäume.
     * Nutzt getEffectiveMaxDepth() als Standard-Tiefe.
     */
    @Override
    public final MirrorNode build(int totalNodes) {
        return buildTree(totalNodes, getEffectiveMaxDepth());
    }

    /**
     * Fügt Knoten zu einem bestehenden Baum hinzu.
     *
     * @param existingRoot Bestehender Baum-Root
     * @param nodesToAdd Anzahl hinzuzufügender Knoten
     * @param maxDepth Maximale Tiefe
     * @return Anzahl tatsächlich hinzugefügter Knoten
     */
    public abstract int addNodesToExistingTree(MirrorNode existingRoot, int nodesToAdd, int maxDepth);

    /**
     * Überschreibt StructureBuilder.addNodes() für Bäume.
     */
    @Override
    public final int addNodes(MirrorNode existingRoot, int nodesToAdd) {
        return addNodesToExistingTree(existingRoot, nodesToAdd, getEffectiveMaxDepth());
    }

    /**
     * Gibt die effektive maximale Tiefe für diese TreeBuilder-Implementierung zurück.
     */
    protected abstract int getEffectiveMaxDepth();

    /**
     * Berechnet die Tiefe eines Knotens basierend auf der Baumstruktur.
     * Zustandslos - analysiert bei jedem Aufruf neu.
     */
    protected final int calculateDepth(MirrorNode node) {
        if (node == null) return -1;
        if (node.isRoot()) return 0;

        MirrorNode parent = (MirrorNode) node.getParent();
        return parent != null ? calculateDepth(parent) + 1 : 0;
    }

    /**
     * Validiert Baumstruktur.
     */
    @Override
    public boolean validateStructure(MirrorNode root) {
        if (root == null) return false;
        if (!root.isRoot()) return false;

        // Verwende TreeMirrorNode-Validierung wenn verfügbar
        if (root instanceof TreeMirrorNode) {
            return ((TreeMirrorNode) root).isValidTreeStructure();
        }

        // Fallback: grundlegende Baumvalidierung
        return validateBasicTreeStructure(root);
    }

    /**
     * Grundlegende Baumvalidierung ohne Zyklen.
     */
    private boolean validateBasicTreeStructure(MirrorNode root) {
        java.util.Set<MirrorNode> visited = new java.util.HashSet<>();
        java.util.Stack<MirrorNode> stack = new java.util.Stack<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            MirrorNode current = stack.pop();
            if (visited.contains(current)) return false; // Zyklus gefunden
            visited.add(current);

            for (TreeNode child : current.getChildren()) {
                if (child.getParent() != current) return false;
                stack.push((MirrorNode) child);
            }
        }

        return true;
    }

    /**
     * Für Bäume sind Blätter eindeutig definiert.
     */
    @Override
    protected boolean isLeafInStructure(MirrorNode node) {
        return node.isLeaf();
    }

    /**
     * Bäume benötigen keine Parent-Links.
     */
    @Override
    protected boolean needsParentLinks() {
        return false;
    }
}