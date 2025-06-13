package org.lrdm.util;

import java.util.*;

/**
 * Basis-Klasse für strukturelle Knoten.
 * Verwaltet Parent-Child-Beziehungen und kann sowohl für Bäume als auch für Ringe verwendet werden.
 * Keine Metadaten der Konstruktion (wie depth) sind hier enthalten.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public class TreeNode {
    private final int id;
    private TreeNode parent;
    private List<TreeNode> children;
    private boolean isHead; // Ersetzt das Root-Konzept für Ring-Strukturen

    public TreeNode(int id) {
        this.id = id;
        this.parent = null;
        this.children = new ArrayList<>();
        this.isHead = false;
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
     * Setzt den Parent-Knoten explizit.
     * Nützlich für Ring-Strukturen wo der Parent nicht automatisch über addChild gesetzt wird.
     *
     * @param parent Der neue Parent-Knoten
     */
    public void setParent(TreeNode parent) {
        this.parent = parent;
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
     * Überprüft, ob dieser Knoten die Root ist (nur für Baum-Strukturen).
     *
     * @return true wenn der Knoten keinen Parent hat
     */
    public boolean isRoot() {
        return parent == null;
    }

    /**
     * Überprüft, ob dieser Knoten als Head markiert ist.
     * Head-Knoten dienen als Startpunkt für Traversierungen in Ring-Strukturen.
     *
     * @return true wenn der Knoten als Head markiert ist
     */
    public boolean isHead() {
        return isHead;
    }

    /**
     * Markiert diesen Knoten als Head oder entfernt die Head-Markierung.
     *
     * @param head true um den Knoten als Head zu markieren
     */
    public void setHead(boolean head) {
        this.isHead = head;
    }

    /**
     * Findet den Head-Knoten in dieser Struktur.
     * Für Bäume ist das die Root, für Ringe der explizit markierte Head.
     *
     * @return Der Head-Knoten oder null wenn keiner gefunden wurde
     */
    public TreeNode findHead() {
        // Zuerst prüfen ob dieser Knoten Head ist
        if (isHead) return this;

        // Für Baum-Strukturen: Root finden
        if (isRoot()) return this;

        // Stack-basierte Suche nach Head in der gesamten Struktur
        Set<TreeNode> visited = new HashSet<>();
        Stack<TreeNode> stack = new Stack<>();
        stack.push(this);

        while (!stack.isEmpty()) {
            TreeNode current = stack.pop();
            if (visited.contains(current)) continue;
            visited.add(current);

            if (current.isHead()) return current;
            if (current.isRoot() && !isRingStructure()) return current; // Root nur wenn kein Ring

            // Alle Nachbarn durchsuchen
            if (current.parent != null) stack.push(current.parent);
            for (TreeNode child : current.children) {
                stack.push(child);
            }
        }

        return null;
    }

    /**
     * Erkennt ob diese Struktur ein Ring ist.
     * Ein Ring liegt vor, wenn es einen Zyklus gibt (jeder Knoten hat Parent und Kind).
     *
     * @return true wenn es sich um eine Ring-Struktur handelt
     */
    public boolean isRingStructure() {
        Set<TreeNode> visited = new HashSet<>();
        Stack<TreeNode> stack = new Stack<>();
        stack.push(this);

        while (!stack.isEmpty()) {
            TreeNode current = stack.pop();
            if (visited.contains(current)) return true; // Zyklus gefunden
            visited.add(current);

            if (current.parent != null) stack.push(current.parent);
            for (TreeNode child : current.children) {
                if (!visited.contains(child)) stack.push(child);
            }
        }

        return false;
    }

    /**
     * Berechnet die Anzahl der direkten strukturellen Links dieses Knotens.
     * Für Bäume: Parent + Children
     * Für Ringe: Parent + Children (normalerweise je 1)
     *
     * @return Anzahl der direkten strukturellen Verbindungen
     */
    public int getNumDirectLinksFromStructure() {
        int linkCount = 0;

        // Parent-Verbindung zählen (außer bei Tree-Root)
        if (parent != null) {
            linkCount++;
        }

        // Kind-Verbindungen zählen
        linkCount += children.size();

        return linkCount;
    }

    /**
     * Berechnet die Gesamtzahl aller Links in der gesamten Struktur.
     * Stack-basierte Traversierung ohne Rekursion.
     * Jeder Link wird nur einmal gezählt (Parent->Child, nicht Child->Parent).
     *
     * @return Gesamtzahl der Links in der Struktur
     */
    public int getNumPlannedLinksFromStructure() {
        Set<TreeNode> visited = new HashSet<>();
        Stack<TreeNode> stack = new Stack<>();
        Set<String> countedLinks = new HashSet<>(); // Verhindert Doppelzählung

        // Beginne mit Head/Root der Struktur
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
                String linkId = current.id + "->" + child.id;
                if (!countedLinks.contains(linkId)) {
                    countedLinks.add(linkId);
                    totalLinks++;
                }

                if (!visited.contains(child)) {
                    stack.push(child);
                }
            }

            // Für Ring-Strukturen: Auch Parent-Links berücksichtigen wenn nötig
            if (isRingStructure() && current.parent != null && !visited.contains(current.parent)) {
                stack.push(current.parent);
            }
        }

        return totalLinks;
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
     * Findet einen Knoten mit der angegebenen ID in dieser Struktur.
     *
     * @param id Die zu suchende ID
     * @return Der gefundene Knoten oder null
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
     * Für Ringe wird der kürzeste Pfad gewählt.
     *
     * @return Liste der Knoten vom Head zu diesem Knoten
     */
    public List<TreeNode> getPathFromHead() {
        TreeNode head = findHead();
        if (head == null) return Collections.emptyList();

        if (head == this) return Arrays.asList(this);

        // BFS für kürzesten Pfad
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
                ", isHead=" + isHead +
                '}';
    }
}