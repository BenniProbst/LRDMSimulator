package org.lrdm.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Erweiterte Node-Klasse für den Baum mit zusätzlichen Funktionalitäten.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public class TreeNode {
    private int id;
    private List<TreeNode> children;
    private TreeNode parent;
    private int depth;

    public TreeNode(int id, int depth) {
        this.id = id;
        this.depth = depth;
        this.children = new ArrayList<>();
        this.parent = null;
    }

    public void addChild(TreeNode child) {
        children.add(child);
        child.parent = this;
    }

    public void removeChild(TreeNode child) {
        children.remove(child);
        child.parent = null;
    }

    public List<TreeNode> getChildren() {
        return children;
    }

    public int getId() {
        return id;
    }

    public int getDepth() {
        return depth;
    }

    public TreeNode getParent() {
        return parent;
    }

    protected void setParent(TreeNode parent) {
        this.parent = parent;
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    public boolean isRoot() {
        return parent == null;
    }

    /**
     * Gibt die Anzahl aller Nachfahren zurück.
     */
    public int getDescendantCount() {
        int count = children.size();
        for (TreeNode child : children) {
            count += child.getDescendantCount();
        }
        return count;
    }

    /**
     * Findet einen Knoten mit der gegebenen ID im Unterbaum.
     */
    public TreeNode findNodeById(int searchId) {
        if (this.id == searchId) return this;

        for (TreeNode child : children) {
            TreeNode found = child.findNodeById(searchId);
            if (found != null) return found;
        }

        return null;
    }

    /**
     * Gibt den Pfad vom Root zu diesem Knoten zurück.
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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        TreeNode treeNode = (TreeNode) obj;
        return id == treeNode.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return "TreeNode{id=" + id + ", depth=" + depth + ", children=" + children.size() + "}";
    }
}
