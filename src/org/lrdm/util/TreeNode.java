package org.lrdm.util;

import java.util.*;

// Node-Klasse für den Baum
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

    public boolean isLeaf() {
        return children.isEmpty();
    }
}

