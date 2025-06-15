package org.lrdm.topologies.base;

import org.lrdm.Mirror;
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

    public TreeMirrorNode(int id, Mirror mirror) {
        super(id, mirror);
    }

    /**
     * Validiert, dass diese Struktur ein gültiger Baum ist.
     * Überschreibt die TreeNode-Methode mit baum-spezifischer Logik.
     * - Genau ein Root-Knoten (kein Parent)
     * - Keine Zyklen
     * - Zusammenhängend
     * - n Knoten haben genau n-1 Kanten
     */
    @Override
    public boolean isValidStructure(Set<TreeNode> allNodes) {
        if (allNodes.isEmpty()) return false;

        // Ein Baum mit n Knoten hat genau n-1 Kanten
        int totalEdges = 0;
        TreeNode root = null;
        int rootCount = 0;

        for (TreeNode node : allNodes) {
            totalEdges += node.getChildren().size();

            // Zähle Root-Knoten
            if (node.isRoot()) {
                rootCount++;
                root = node;
            }
        }

        // Genau ein Root und korrekte Anzahl Kanten
        if (rootCount != 1 || totalEdges != allNodes.size() - 1) {
            return false;
        }

        // Prüfe auf Zyklen und Zusammenhang
        return validateTreeProperties(root, allNodes);
    }

    /**
     * Validiert Baum-Eigenschaften: keine Zyklen, alle Knoten erreichbar.
     * Verwendet DFS für Zykluserkennung und Zusammenhangsprüfung.
     */
    private boolean validateTreeProperties(TreeNode root, Set<TreeNode> allNodes) {
        if (root == null) return false;

        Set<TreeNode> visited = new HashSet<>();
        Stack<TreeNode> stack = new Stack<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            TreeNode current = stack.pop();
            if (visited.contains(current)) return false; // Zyklus gefunden
            visited.add(current);

            // Validiere Parent-Child-Konsistenz
            for (TreeNode child : current.getChildren()) {
                if (child.getParent() != current) return false;
                stack.push(child);
            }
        }

        // Alle Knoten müssen erreichbar sein (Zusammenhang)
        return visited.size() == allNodes.size();
    }

    @Override
    public boolean canAcceptMoreChildren() {
        // Bäume können normalerweise immer weitere Kinder haben
        return true;
    }

    @Override
    public boolean canBeRemovedFromStructure(MirrorNode structureRoot) {
        if (structureRoot == null) return false;
        if (this == structureRoot) return false; // Root kann nicht entfernt werden

        // In Bäumen können normalerweise Blätter entfernt werden
        return isLeaf();
    }

    /**
     * Findet alle Blätter im Baum.
     * Nutzt die fundamentale TreeNode isLeaf()-Methode.
     */
    public List<TreeMirrorNode> getTreeLeaves() {
        List<TreeMirrorNode> leaves = new ArrayList<>();
        Set<TreeNode> allNodes = getAllNodes();

        for (TreeNode node : allNodes) {
            if (node.isLeaf() && node instanceof TreeMirrorNode) {
                leaves.add((TreeMirrorNode) node);
            }
        }

        return leaves;
    }

    /**
     * Findet die Root des Baums.
     */
    public TreeMirrorNode getTreeRoot() {
        Set<TreeNode> allNodes = getAllNodes();

        for (TreeNode node : allNodes) {
            if (node.isRoot() && node instanceof TreeMirrorNode) {
                return (TreeMirrorNode) node;
            }
        }

        return null;
    }

    /**
     * Berechnet die Tiefe dieses Knotens im Baum.
     */
    public int getDepthInTree() {
        int depth = 0;
        TreeNode current = this;

        while (current.getParent() != null) {
            depth++;
            current = current.getParent();
        }

        return depth;
    }

    /**
     * Berechnet die maximale Tiefe des Baums.
     */
    public int getMaxTreeDepth() {
        TreeMirrorNode root = getTreeRoot();
        if (root == null) return 0;

        return calculateMaxDepthFromNode(root);
    }

    /**
     * Hilfsmethode zur rekursiven Tiefenberechnung.
     */
    private int calculateMaxDepthFromNode(TreeNode node) {
        if (node.isLeaf()) return 0;

        int maxChildDepth = 0;
        for (TreeNode child : node.getChildren()) {
            maxChildDepth = Math.max(maxChildDepth, calculateMaxDepthFromNode(child));
        }

        return 1 + maxChildDepth;
    }

    /**
     * Zählt die Gesamtanzahl der Knoten im Baum.
     */
    public int getTreeSize() {
        return getAllNodes().size();
    }

    /**
     * Prüft, ob dieser Baum balanciert ist.
     * Ein Baum istbalanciert, wenn sich die Tiefen der Blätter um maximal 1 unterscheiden.
     */
    public boolean isBalanced() {
        TreeMirrorNode root = getTreeRoot();
        if (root == null) return false;

        return checkBalance(root) != -1;
    }

    /**
     * Hilfsmethode für Balancierung-Check.
     * Gibt -1 zurück wenn unbalanciert, sonst die Tiefe.
     */
    private int checkBalance(TreeNode node) {
        if (node.isLeaf()) return 0;

        int maxDepth = 0;
        int minDepth = Integer.MAX_VALUE;

        for (TreeNode child : node.getChildren()) {
            int childDepth = checkBalance(child);
            if (childDepth == -1) return -1; // Bereits unbalanciert

            maxDepth = Math.max(maxDepth, childDepth);
            minDepth = Math.min(minDepth, childDepth);
        }

        // Unterschied zwischen tiefster und flachster Subtree > 1 = unbalanciert
        if (maxDepth - minDepth > 1) return -1;

        return 1 + maxDepth;
    }
}