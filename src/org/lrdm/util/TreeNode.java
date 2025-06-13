package org.lrdm.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Basis-Klasse für Tree-Knoten.
 * Verwaltet nur die strukturellen Beziehungen zwischen Knoten.
 * Keine Metadaten der Konstruktion (wie depth) sind hier enthalten.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public class TreeNode {
    private final int id;
    private TreeNode parent;
    private List<TreeNode> children;

    public TreeNode(int id) {
        this.id = id;
        this.parent = null;
        this.children = new ArrayList<>();
    }

    /**
     * Fügt ein Kind zu diesem Knoten hinzu.
     *
     * @param child Das hinzuzufügende Kind
     */
    public void addChild(TreeNode child) {
        if (child != null && !children.contains(child)) {
            children.add(child);
            child.parent = this;
        }
    }

    /**
     * Entfernt ein Kind von diesem Knoten.
     *
     * @param child Das zu entfernende Kind
     */
    public void removeChild(TreeNode child) {
        if (children.remove(child)) {
            child.parent = null;
        }
    }

    /**
     * Überprüft, ob dieser Knoten ein Blatt ist.
     *
     * @return true wenn der Knoten keine Kinder hat
     */
    public boolean isLeaf() {
        return children.isEmpty();
    }

    /**
     * Überprüft, ob dieser Knoten die Root ist.
     *
     * @return true wenn der Knoten keinen Parent hat
     */
    public boolean isRoot() {
        return parent == null;
    }

    /**
     * Berechnet die Anzahl aller Nachfahren.
     *
     * @return Anzahl der Nachfahren
     */
    public int getDescendantCount() {
        int count = 0;
        for (TreeNode child : children) {
            count += 1 + child.getDescendantCount();
        }
        return count;
    }

    /**
     * Findet einen Knoten mit der angegebenen ID in diesem Teilbaum.
     *
     * @param id Die zu suchende ID
     * @return Der gefundene Knoten oder null
     */
    public TreeNode findNodeById(int id) {
        if (this.id == id) {
            return this;
        }

        for (TreeNode child : children) {
            TreeNode found = child.findNodeById(id);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    /**
     * Gibt den Pfad von der Root zu diesem Knoten zurück.
     *
     * @return Liste der Knoten vom Root zu diesem Knoten
     */
    public List<TreeNode> getPathFromRoot() {
        List<TreeNode> path = new ArrayList<>();
        TreeNode current = this;

        while (current != null) {
            path.add(0, current);
            current = current.parent;
        }

        return path;
    }

    // Getter und Setter
    public int getId() {
        return id;
    }

    public TreeNode getParent() {
        return parent;
    }

    public List<TreeNode> getChildren() {
        return new ArrayList<>(children); // Defensive Kopie
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        TreeNode treeNode = (TreeNode) obj;
        return id == treeNode.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "TreeNode{" +
                "id=" + id +
                ", children=" + children.size() +
                ", isLeaf=" + isLeaf() +
                ", isRoot=" + isRoot() +
                '}';
    }
}