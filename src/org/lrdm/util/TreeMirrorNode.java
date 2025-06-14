package org.lrdm.util;

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
     *
     * @return true wenn gültiger Baum
     */
    public boolean isValidTreeStructure() {
        TreeNode head = findHead();
        if (head == null) return false;
        
        // Muss Root sein (kein Parent)
        if (!head.isRoot()) return false;
        
        // Prüfe auf Zyklen und Zusammenhang
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
            
            // Nur Kinder besuchen (bei Bäumen keine Parent-Traversierung nötig)
            for (TreeNode child : current.getChildren()) {
                if (child.getParent() != current) return false; // Inkonsistente Parent-Child-Beziehung
                stack.push(child);
            }
        }
        
        return true;
    }
}