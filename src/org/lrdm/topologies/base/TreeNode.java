package org.lrdm.topologies.base;

import java.util.*;

/**
 * Basis-Klasse für strukturelle Knoten.
 * Verwaltet Parent-Child-Beziehungen für Baum-Strukturen.
 * Ring-sichere Implementierung aller Traversierungsfunktionen.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public class TreeNode {
    private final int id;
    private TreeNode parent;
    private List<TreeNode> children;
    private boolean isHead; // Ersetzt das Root-Konzept für Ring-Strukturen

    /**
     * Einfache Tupel-Klasse für Link-IDs (um String-Konkatenation zu vermeiden).
     */
    public static class LinkPair {
        public final int from;
        public final int to;

        public LinkPair(int from, int to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof LinkPair)) return false;
            LinkPair other = (LinkPair) obj;
            return from == other.from && to == other.to;
        }

        @Override
        public int hashCode() {
            return Objects.hash(from, to);
        }
    }

    public TreeNode(int id) {
        this.id = id;
        this.parent = null;
        this.children = new ArrayList<>();
        this.isHead = false;
    }

    /**
     * Fügt ein Kind zu diesem Knoten hinzu.
     */
    public void addChild(TreeNode child) {
        if (child != null && !children.contains(child)) {
            children.add(child);
            child.parent = this;
        }
    }

    /**
     * Entfernt ein Kind von diesem Knoten.
     */
    public void removeChild(TreeNode child) {
        if (children.remove(child)) {
            child.parent = null;
        }
    }

    /**
     * Setzt den Parent-Knoten explizit.
     */
    public void setParent(TreeNode parent) {
        this.parent = parent;
    }

    /**
     * Markiert diesen Knoten als Head oder entfernt die Head-Markierung.
     */
    public void setHead(boolean head) {
        this.isHead = head;
    }

    /**
     * Findet den Head-Knoten in dieser Struktur.
     * Ring-sichere Implementierung.
     */
    public TreeNode findHead() {
        if (isHead) return this;
        if (isRoot()) return this;

        Set<TreeNode> visited = new HashSet<>();
        Stack<TreeNode> stack = new Stack<>();
        stack.push(this);

        while (!stack.isEmpty()) {
            TreeNode current = stack.pop();
            if (visited.contains(current)) continue;
            visited.add(current);

            if (current.isHead()) return current;
            if (current.isRoot()) return current;

            if (current.parent != null && !visited.contains(current.parent)) {
                stack.push(current.parent);
            }
            for (TreeNode child : current.children) {
                if (!visited.contains(child)) {
                    stack.push(child);
                }
            }
        }

        return null;
    }

    /**
     * Berechnet die Anzahl der direkten strukturellen Links dieses Knotens.
     */
    public int getNumDirectLinksFromStructure() {
        int linkCount = 0;
        if (parent != null) linkCount++;
        linkCount += children.size();
        return linkCount;
    }

    /**
     * Berechnet die Gesamtzahl aller Links in der gesamten Struktur.
     * Ring-sichere Stack-basierte Traversierung.
     */
    public int getNumPlannedLinksFromStructure() {
        Set<TreeNode> visited = new HashSet<>();
        Stack<TreeNode> stack = new Stack<>();
        Set<LinkPair> countedLinks = new HashSet<>();

        TreeNode head = findHead();
        if (head == null) head = this;

        stack.push(head);
        int totalLinks = 0;

        while (!stack.isEmpty()) {
            TreeNode current = stack.pop();
            if (visited.contains(current)) continue;
            visited.add(current);

            // Zähle alle ausgehenden Links (Parent->Child Beziehungen)
            for (TreeNode child : current.children) {
                LinkPair linkId = new LinkPair(current.id, child.id);
                if (!countedLinks.contains(linkId)) {
                    countedLinks.add(linkId);
                    totalLinks++;
                }

                if (!visited.contains(child)) {
                    stack.push(child);
                }
            }

            // Auch Parent-Links für vollständige Abdeckung
            if (current.parent != null && !visited.contains(current.parent)) {
                stack.push(current.parent);
            }
        }

        return totalLinks;
    }

    /**
     * Überprüft, ob dieser Knoten ein Terminal-Knoten ist.
     * Terminal = hat nur eine Verbindung (entweder nur Parent oder nur ein Kind).
     * Nützlich für Linien-Strukturen und Stern-Blätter.
     */
    public boolean isTerminal() {
        int connectionCount = children.size() + (parent != null ? 1 : 0);
        return connectionCount == 1;
    }

    /**
     * Überprüft, ob dieser Knoten ein Endpunkt ist.
     * Endpunkt = entweder Terminal oder Root ohne Kinder.
     */
    public boolean isEndpoint() {
        return isTerminal() || (isRoot() && isLeaf());
    }

    /**
     * Überprüft, ob dieser Knoten ein Blatt ist (hat keine Kinder).
     * Universelle Definition für alle Topologien.
     */
    public boolean isLeaf() {
        return children.isEmpty();
    }

    /**
     * Überprüft, ob dieser Knoten die Root ist (nur für Baum-Strukturen).
     */
    public boolean isRoot() {
        return parent == null && isHead();
    }

    /**
     * Überprüft, ob dieser Knoten als Head markiert ist.
     */
    public boolean isHead() {
        return isHead;
    }

    /**
     * Gibt den Konnektivitätsgrad dieses Knotens zurück.
     * Anzahl aller Verbindungen (Parent + Kinder).
     */
    public int getConnectivityDegree() {
        return children.size() + (parent != null ? 1 : 0);
    }

    /**
     * Sammelt alle Knoten in der zusammenhängenden Struktur.
     * Ring-sichere Stack-basierte Traversierung.
     *
     * @return Set aller erreichbaren Knoten
     */
    public Set<TreeNode> getAllNodesInStructure() {
        Set<TreeNode> visited = new HashSet<>();
        Stack<TreeNode> stack = new Stack<>();
        stack.push(this);

        while (!stack.isEmpty()) {
            TreeNode current = stack.pop();
            if (visited.contains(current)) continue;
            visited.add(current);

            if (current.parent != null && !visited.contains(current.parent)) {
                stack.push(current.parent);
            }
            for (TreeNode child : current.children) {
                if (!visited.contains(child)) {
                    stack.push(child);
                }
            }
        }

        return visited;
    }


    /**
     * Berechnet die Anzahl aller Nachfahren.
     * Ring-sichere Implementierung mit Zykluserkennung.
     */
    public int getDescendantCount() {
        Set<TreeNode> visited = new HashSet<>();
        Stack<TreeNode> stack = new Stack<>();

        // Alle direkten Kinder hinzufügen
        for (TreeNode child : children) {
            stack.push(child);
        }

        int count = 0;
        while (!stack.isEmpty()) {
            TreeNode current = stack.pop();
            if (visited.contains(current)) continue; // Zyklus-Schutz
            visited.add(current);
            count++;

            // Weitere Nachfahren hinzufügen
            for (TreeNode child : current.children) {
                if (!visited.contains(child)) {
                    stack.push(child);
                }
            }
        }

        return count;
    }

    /**
     * Findet einen Knoten mit der angegebenen ID in dieser Struktur.
     * Ring-sichere Implementierung.
     */
    public TreeNode findNodeById(int id) {
        Set<TreeNode> visited = new HashSet<>();
        Stack<TreeNode> stack = new Stack<>();
        stack.push(this);

        while (!stack.isEmpty()) {
            TreeNode current = stack.pop();
            if (visited.contains(current)) continue;
            visited.add(current);

            if (current.id == id) return current;

            if (current.parent != null && !visited.contains(current.parent)) {
                stack.push(current.parent);
            }
            for (TreeNode child : current.children) {
                if (!visited.contains(child)) {
                    stack.push(child);
                }
            }
        }

        return null;
    }

    /**
     * Gibt den Pfad vom Head zu diesem Knoten zurück.
     * Ring-sichere BFS-Implementierung für kürzesten Pfad.
     */
    public List<TreeNode> getPathFromHead() {
        TreeNode head = findHead();
        if (head == null) return Collections.emptyList();
        if (head == this) return List.of(this);

        Queue<TreeNode> queue = new LinkedList<>();
        Map<TreeNode, TreeNode> predecessor = new HashMap<>();
        Set<TreeNode> visited = new HashSet<>();

        queue.offer(head);
        visited.add(head);
        predecessor.put(head, null);

        while (!queue.isEmpty()) {
            TreeNode current = queue.poll();

            if (current == this) {
                // Pfad rekonstruieren
                List<TreeNode> path = new ArrayList<>();
                TreeNode node = this;
                while (node != null) {
                    path.add(0, node);
                    node = predecessor.get(node);
                }
                return path;
            }

            // Alle Nachbarn durchsuchen
            List<TreeNode> neighbors = new ArrayList<>(current.children);
            if (current.parent != null) neighbors.add(current.parent);

            for (TreeNode neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    predecessor.put(neighbor, current);
                    queue.offer(neighbor);
                }
            }
        }

        return Collections.emptyList();
    }

    // Getter
    public int getId() { return id; }
    public TreeNode getParent() { return parent; }
    public List<TreeNode> getChildren() { return new ArrayList<>(children); }

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
                ", isHead=" + isHead +
                '}';
    }
}