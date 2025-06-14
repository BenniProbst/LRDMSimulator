package org.lrdm.topologies.base;

import java.util.*;

/**
 * Spezialisierte MirrorNode für Baum-Strukturen.
 * Validiert, dass die Struktur tatsächlich ein Baum ist.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public class TreeMirrorNode extends MirrorNode {

    public TreeMirrorNode(int id) {
        super(id);
    }

    /**
     * Validiert, dass diese Struktur ein gültiger Baum ist.
     * - Genau ein Root-Knoten (kein Parent)
     * - Keine Zyklen
     * - Zusammenhängend
     */
    public boolean isValidTreeStructure() {
        TreeNode head = findHead();
        if (head == null) return false;

        if (!head.isRoot()) return false;

        return validateTreeProperties(head);
    }

    /**
     * Validiert Baum-Eigenschaften: keine Zyklen, alle Knoten erreichbar.
     */
    private boolean validateTreeProperties(TreeNode root) {
        Set<TreeNode> visited = new HashSet<>();
        Stack<TreeNode> stack = new Stack<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            TreeNode current = stack.pop();
            if (visited.contains(current)) return false; // Zyklus gefunden
            visited.add(current);

            for (TreeNode child : current.getChildren()) {
                if (child.getParent() != current) return false;
                stack.push(child);
            }
        }

        return true;
    }

    /**
     * Findet alle Blätter im Baum.
     * Nutzt die fundamentale TreeNode isLeaf()-Methode.
     */
    public List<TreeMirrorNode> getTreeLeaves() {
        List<TreeMirrorNode> leaves = new ArrayList<>();
        Set<TreeNode> allNodes = getAllNodesInStructure();

        for (TreeNode node : allNodes) {
            if (node.isLeaf() && node instanceof TreeMirrorNode) {
                leaves.add((TreeMirrorNode) node);
            }
        }

        return leaves;
    }
}