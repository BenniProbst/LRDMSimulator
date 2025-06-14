package org.lrdm.topologies.base;

import java.util.*;

/**
 * Abstrakte Basis-Klasse für strukturelle Knoten.
 * Verwaltet Parent-Child-Beziehungen und definiert Strukturvalidierung.
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
     * Grundlegende Strukturvalidierung - prüft ob alle Knoten miteinander verbunden sind.
     * Verhindert isolierte Knoten in der Struktur.
     * Unterstützt sowohl Baum- als auch Ring-Strukturen.
     */
    public boolean isValidStructure(Set<TreeNode> allNodes) {
        if (allNodes == null || allNodes.isEmpty()) return false;
        if (allNodes.size() == 1) return true; // Ein einzelner Knoten ist gültig

        // Prüfe ob alle Knoten zusammenhängend sind
        return isConnectedStructure(allNodes);
    }

    /**
     * Prüft ob alle Knoten in der gegebenen Menge zusammenhängend sind.
     * Verwendet BFS um zu überprüfen, dass alle Knoten erreichbar sind.
     * Ring-sichere Implementierung für alle Strukturtypen.
     */
    private boolean isConnectedStructure(Set<TreeNode> allNodes) {
        if (allNodes.isEmpty()) return false;

        // Starte von einem beliebigen Knoten
        TreeNode startNode = allNodes.iterator().next();
        Set<TreeNode> visited = new HashSet<>();
        Queue<TreeNode> queue = new LinkedList<>();

        queue.offer(startNode);
        visited.add(startNode);

        // BFS-Traversierung über alle Verbindungen
        while (!queue.isEmpty()) {
            TreeNode current = queue.poll();

            // Besuche alle Nachbarn (Parent und Children)
            List<TreeNode> neighbors = new ArrayList<>();

            // Parent hinzufügen (falls vorhanden)
            if (current.parent != null) {
                neighbors.add(current.parent);
            }

            // Alle Kinder hinzufügen
            neighbors.addAll(current.children);

            // Besuche unbesuchte Nachbarn
            for (TreeNode neighbor : neighbors) {
                if (allNodes.contains(neighbor) && !visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.offer(neighbor);
                }
            }
        }

        // Alle Knoten müssen erreichbar sein
        return visited.size() == allNodes.size();
    }

    /**
     * Grundlegende Zyklusprüfung für beliebige Knotenstrukturen.
     * Fundamentale statische Hilfsmethode, die von Kindklassen genutzt werden kann.
     */
    public static boolean hasClosedCycle(Set<TreeNode> nodes) {
        if (nodes.isEmpty()) return false;

        TreeNode start = nodes.iterator().next();
        TreeNode current = start;
        Set<TreeNode> visitedInCycle = new HashSet<>();

        do {
            if (visitedInCycle.contains(current)) {
                return visitedInCycle.size() == nodes.size();
            }
            visitedInCycle.add(current);

            if (current.getChildren().size() != 1) return false;
            current = current.getChildren().get(0);

        } while (current != start && visitedInCycle.size() <= nodes.size());

        return current == start && visitedInCycle.size() == nodes.size();
    }

    /**
     * Abstrakte Methode - muss von Kindklassen implementiert werden.
     * Prüft, ob dieser Knoten weitere Kinder akzeptieren kann.
     *
     * @return true wenn weitere Kinder akzeptiert werden können
     */
    public boolean canAcceptMoreChildren();

    /**
     * Abstrakte Methode - muss von Kindklassen implementiert werden.
     * Prüft, ob dieser Knoten aus der Struktur entfernt werden kann.
     *
     * @param structureRoot Root der Gesamtstruktur
     * @return true wenn der Knoten entfernt werden kann
     */
    public boolean canBeRemovedFromStructure(TreeNode structureRoot);

    public void addChild(TreeNode child) {
        if (child != null && !children.contains(child)) {
            children.add(child);
            child.parent = this;
        }
    }

    public void removeChild(TreeNode child) {
        if (children.remove(child)) {
            child.parent = null;
        }
    }

    public void setParent(TreeNode parent) {
        this.parent = parent;
    }

    public void setHead(boolean head) {
        this.isHead = head;
    }

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

    public int getNumDirectLinksFromStructure() {
        int linkCount = 0;
        if (parent != null) linkCount++;
        linkCount += children.size();
        return linkCount;
    }

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

    public boolean isTerminal() {
        int connectionCount = children.size() + (parent != null ? 1 : 0);
        return connectionCount == 1;
    }

    public boolean isEndpoint() {
        return isTerminal() || (isRoot() && isLeaf());
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    public boolean isRoot() {
        return parent == null && isHead();
    }

    public boolean isHead() {
        return isHead;
    }

    public int getConnectivityDegree() {
        return children.size() + (parent != null ? 1 : 0);
    }

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