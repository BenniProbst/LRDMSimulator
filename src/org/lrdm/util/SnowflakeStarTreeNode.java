package org.lrdm.util;

import java.util.*;

// Node-Klasse für den Baum
public class SnowflakeStarTreeNode {
    private int id;
    private List<SnowflakeStarTreeNode> children;
    private SnowflakeStarTreeNode parent;
    private int depth;

    public SnowflakeStarTreeNode(int id, int depth) {
        this.id = id;
        this.depth = depth;
        this.children = new ArrayList<>();
        this.parent = null;
    }

    public void addChild(SnowflakeStarTreeNode child) {
        children.add(child);
        child.parent = this;
    }

    public List<SnowflakeStarTreeNode> getChildren() {
        return children;
    }

    public int getId() {
        return id;
    }

    public int getDepth() {
        return depth;
    }

    public SnowflakeStarTreeNode getParent() {
        return parent;
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }
}

