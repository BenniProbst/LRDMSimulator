package org.lrdm.util;

import org.lrdm.Link;
import org.lrdm.Mirror;

import java.util.*;

/**
 * Abstrakte Basisklasse für TreeBuilder-Implementierungen.
 * Zustandslos - jede Methode analysiert/erstellt vollständig neu.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public abstract class TreeBuilder {
    protected IDGenerator idGenerator;

    public TreeBuilder() {
        this.idGenerator = IDGenerator.getInstance();
    }

    /**
     * Erstellt einen neuen Baum mit der angegebenen Anzahl von Knoten.
     */
    public abstract MirrorNode buildTree(int totalNodes, int maxDepth);

    /**
     * Fügt Knoten zu einem bestehenden Baum hinzu.
     */
    public abstract int addNodesToExistingTree(MirrorNode existingRoot, int nodesToAdd, int maxDepth);

    /**
     * Entfernt Knoten aus einem bestehenden Baum.
     */
    public int removeNodesFromTree(MirrorNode root, int nodesToRemove) {
        if (root == null || nodesToRemove <= 0) return 0;

        List<MirrorNode> leaves = findLeaves(root);
        int removed = 0;

        for (MirrorNode leaf : leaves) {
            if (removed >= nodesToRemove) break;
            if (leaf != root) { // Root nicht entfernen
                MirrorNode parent = (MirrorNode) leaf.getParent();
                if (parent != null) {
                    parent.getChildren().remove(leaf);
                    parent.removeMirrorNode(leaf);
                    removed++;
                }
            }
        }

        return removed;
    }

    /**
     * Erstellt Mirror-Verbindungen basierend auf der Tree-Struktur.
     * Network Parameter entfernt - wird nicht benötigt.
     */
    public final Set<Link> createAndLinkMirrors(List<Mirror> mirrors, int simTime, Properties props) {
        if (mirrors.isEmpty()) return new HashSet<>();

        MirrorNode root = createTreeStructureWithMirrors(mirrors.size(), getEffectiveMaxDepth(), mirrors);
        if (root == null) return new HashSet<>();

        return createLinksFromTreeStructure(root, simTime, props);
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
     * Imperativ implementierte Link-Erstellung mit Stack statt Rekursion.
     */
    protected final Set<Link> createLinksFromTreeStructure(MirrorNode root, int simTime, Properties props) {
        Set<Link> links = new HashSet<>();
        if (root == null) return links;

        // Stack-basierte Implementierung statt Rekursion
        Stack<MirrorNode> stack = new Stack<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            MirrorNode current = stack.pop();
            if (current.getMirror() == null) continue;

            for (TreeNode child : current.getChildren()) {
                MirrorNode mirrorChild = (MirrorNode) child;
                if (mirrorChild.getMirror() != null) {
                    Link link = new Link(
                            idGenerator.getNextID(),
                            current.getMirror(),
                            mirrorChild.getMirror(),
                            simTime,
                            props
                    );
                    links.add(link);

                    current.getMirror().addLink(link);
                    mirrorChild.getMirror().addLink(link);

                    current.addLink(link);
                    mirrorChild.addLink(link);
                }
                stack.push(mirrorChild);
            }
        }

        return links;
    }

    /**
     * Erstellt eine MirrorNode-Struktur und ordnet Mirrors zu.
     */
    protected final MirrorNode createTreeStructureWithMirrors(int totalNodes, int maxDepth, List<Mirror> mirrors) {
        if (mirrors.isEmpty()) return null;

        MirrorNode root = buildTree(totalNodes, maxDepth);
        if (root == null) return null;

        assignMirrorsToNodes(root, mirrors);
        return root;
    }

    /**
     * Ordnet Mirrors den MirrorNodes zu - Stack-basiert statt rekursiv.
     */
    protected final void assignMirrorsToNodes(MirrorNode root, List<Mirror> mirrors) {
        if (root == null || mirrors.isEmpty()) return;

        Queue<MirrorNode> queue = new LinkedList<>();
        queue.offer(root);
        int mirrorIndex = 0;

        while (!queue.isEmpty() && mirrorIndex < mirrors.size()) {
            MirrorNode current = queue.poll();

            if (mirrorIndex < mirrors.size()) {
                current.setMirror(mirrors.get(mirrorIndex));
                mirrorIndex++;
            }

            for (TreeNode child : current.getChildren()) {
                queue.offer((MirrorNode) child);
            }
        }
    }

    /**
     * Findet alle Blätter des Baums - Stack-basiert.
     */
    protected List<MirrorNode> findLeaves(MirrorNode root) {
        List<MirrorNode> leaves = new ArrayList<>();
        if (root == null) return leaves;

        Stack<MirrorNode> stack = new Stack<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            MirrorNode current = stack.pop();

            if (current.getChildren().isEmpty()) {
                leaves.add(current);
            } else {
                for (TreeNode child : current.getChildren()) {
                    stack.push((MirrorNode) child);
                }
            }
        }

        // Sortiere nach Tiefe (tiefste zuerst)
        leaves.sort((a, b) -> Integer.compare(calculateDepth(b), calculateDepth(a)));

        return leaves;
    }

    /**
     * Hilfsmethode: Erstellt MirrorNode mit neuer ID.
     */
    protected final MirrorNode createMirrorNode() {
        return new MirrorNode(getNextId());
    }

    /**
     * Generiert nächste ID.
     */
    protected final int getNextId() {
        return idGenerator.getNextID();
    }

    /**
     * Hilfsmethoden für Tests - zählt Knoten im Baum.
     */
    public int countNodes(MirrorNode root) {
        if (root == null) return 0;

        int count = 0;
        Stack<MirrorNode> stack = new Stack<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            MirrorNode current = stack.pop();
            count++;

            for (TreeNode child : current.getChildren()) {
                stack.push((MirrorNode) child);
            }
        }

        return count;
    }

    /**
     * Hilfsmethode für Tests - findet maximale Tiefe.
     */
    public int getMaxDepth(MirrorNode root) {
        if (root == null) return -1;

        int maxDepth = 0;
        Stack<MirrorNode> stack = new Stack<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            MirrorNode current = stack.pop();
            int depth = calculateDepth(current);
            maxDepth = Math.max(maxDepth, depth);

            for (TreeNode child : current.getChildren()) {
                stack.push((MirrorNode) child);
            }
        }

        return maxDepth;
    }

    /**
     * Hilfsmethode für Tests - validiert Baumstruktur.
     */
    public boolean validateTreeStructure(MirrorNode root) {
        if (root == null) return true;

        Stack<MirrorNode> stack = new Stack<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            MirrorNode current = stack.pop();

            // Validiere Parent-Child-Beziehungen
            for (TreeNode child : current.getChildren()) {
                if (child.getParent() != current) return false;
                stack.push((MirrorNode) child);
            }
        }

        return true;
    }
}