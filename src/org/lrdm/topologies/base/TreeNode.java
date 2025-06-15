package org.lrdm.topologies.base;

import java.util.*;

/**
 * Abstrakte Basis-Klasse für strukturelle Knoten.
 * Verwaltet Parent-Child-Beziehungen und definiert Strukturvalidierung.
 * Ring-sichere Implementierung aller Traversierungsfunktionen.
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class TreeNode {
    private final int id;
    private TreeNode parent;
    private List<TreeNode> children;
    private boolean isHead; // Ersetzt das Root-Konzept für Ring-Strukturen
    private int maxChildren = Integer.MAX_VALUE; // Standardmäßig unbegrenzt

    /**
         * Einfache Tupel-Klasse für Link-IDs (um String-Konkatenation zu vermeiden).
         */
        public record LinkPair(int from, int to) {
        /**
         * Erstellt ein neues LinkPair.
         *
         * @param from ID des Quellknotens
         * @param to   ID des Zielknotens
         */
        public LinkPair {
        }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (!(obj instanceof LinkPair other)) return false;
                return from == other.from && to == other.to;
            }

    }

    /**
     * Erstellt einen neuen TreeNode mit gegebener ID.
     *
     * @param id Eindeutige Identifikationsnummer des Knotens
     */
    public TreeNode(int id) {
        this.id = id;
        this.parent = null;
        this.children = new ArrayList<>();
        this.isHead = false;
    }

    /**
     * Erstellt einen neuen TreeNode mit gegebener ID und maximaler Kindanzahl.
     *
     * @param id          Eindeutige Identifikationsnummer des Knotens
     * @param maxChildren Maximale Anzahl von Kindern, die dieser Knoten haben kann (muss >= 0 sein)
     */
    public TreeNode(int id, int maxChildren) {
        this(id);
        this.maxChildren = Math.max(0, maxChildren);
    }

    /**
     * Grundlegende Strukturvalidierung - prüft ob alle Knoten miteinander verbunden sind.
     * Verhindert isolierte Knoten in der Struktur.
     * Unterstützt sowohl Baum- als auch Ring-Strukturen.
     *
     * @param allNodes Menge aller Knoten, die zur Struktur gehören sollen
     * @return true wenn alle Knoten zusammenhängend sind, false bei isolierten Knoten oder null/leerer Menge
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
     *
     * @param allNodes Menge aller zu prüfenden Knoten
     * @return true wenn alle Knoten von einem beliebigen Startknoten erreichbar sind
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
     * Prüft, ob dieser Knoten weitere Kinder akzeptieren kann.
     * Basiert auf der maximalen Anzahl von Kindern.
     *
     * @return true wenn die aktuelle Kindanzahl kleiner als maxChildren ist
     */
    public boolean canAcceptMoreChildren() {
        return children.size() < maxChildren;
    }

    /**
     * Prüft, ob dieser Knoten aus der Struktur entfernt werden kann.
     * Ein Knoten kann entfernt werden, wenn:
     * - Er gefunden werden kann in der Struktur
     * - Er keine Kinder hat (um Fragmentierung zu vermeiden)
     * - Er nicht der strukturRoot ist
     *
     * @param structureRoot Der Root-Knoten der Struktur (darf nicht null sein)
     * @return true wenn der Knoten sicher entfernt werden kann, false bei null structureRoot,
     *         wenn er der Root ist, nicht gefunden wird oder Kinder hat
     */
    public boolean canBeRemovedFromStructure(TreeNode structureRoot) {
        if (structureRoot == null) return false;
        if (this == structureRoot) return false; // Root kann nicht entfernt werden

        // Prüfe ob dieser Knoten in der Struktur gefunden werden kann
        Set<TreeNode> allNodes = getAllNodes();
        if (!allNodes.contains(structureRoot)) return false;

        // Knoten kann nur entfernt werden, wenn er keine Kinder hat
        // Dies verhindert Fragmentierung der Struktur
        return structureRoot.getChildren().isEmpty();
    }

    /**
     * Grundlegende Zyklusprüfung für beliebige Knotenstrukturen.
     * Fundamentale statische Hilfsmethode, die von Kindklassen genutzt werden kann.
     * Prüft ob alle gegebenen Knoten einen geschlossenen Zyklus bilden.
     *
     * @param nodes Menge von Knoten, die auf geschlossenen Zyklus geprüft werden sollen
     * @return true wenn alle Knoten genau einen geschlossenen Zyklus bilden, false bei leerer Menge oder anderen Strukturen
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
     * Fügt einen Kindknoten hinzu, falls möglich.
     * Setzt automatisch die Parent-Beziehung und prüft Kapazitätsgrenzen.
     *
     * @param child Der hinzuzufügende Kindknoten (darf nicht null sein, nicht bereits Kind sein,
     *             und maxChildren darf nicht überschritten werden)
     */
    public void addChild(TreeNode child) {
        if (child != null && !children.contains(child) && canAcceptMoreChildren()) {
            children.add(child);
            child.parent = this;
        }
    }

    /**
     * Entfernt einen Kindknoten und dessen Parent-Beziehung.
     *
     * @param child Der zu entfernende Kindknoten (null wird ignoriert)
     */
    public void removeChild(TreeNode child) {
        if (children.remove(child)) {
            child.parent = null;
        }
    }

    /**
     * Setzt den Parent-Knoten direkt (Vorsicht: kann Inkonsistenzen verursachen).
     * Normalerweise sollte addChild() verwendet werden.
     *
     * @param parent Der neue Parent-Knoten (kann null sein)
     */
    public void setParent(TreeNode parent) {
        this.parent = parent;
    }

    /**
     * Markiert diesen Knoten als Head der Struktur.
     * Head-Knoten dienen als Einstiegspunkt für Traversierungen.
     *
     * @param head true um als Head zu markieren, false um Head-Status zu entfernen
     */
    public void setHead(boolean head) {
        this.isHead = head;
    }

    /**
     * Setzt die maximale Anzahl von Kindern, die dieser Knoten haben kann.
     *
     * @param maxChildren Maximale Kindanzahl (wird auf mindestens 0 begrenzt)
     */
    public void setMaxChildren(int maxChildren) {
        this.maxChildren = Math.max(0, maxChildren);
    }

    /**
     * Findet den Head-Knoten der Struktur.
     * Sucht zunächst explizit markierte Head-Knoten, dann Root-Knoten.
     * Verwendet DFS-Traversierung über alle verbundenen Knoten.
     *
     * @return Head-Knoten der Struktur oder null wenn keiner gefunden wird
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
     * Berechnet die Anzahl direkter Verbindungen dieses Knotens.
     * Zählt Parent-Verbindung (falls vorhanden) plus alle Kinder.
     *
     * @return Anzahl direkter Verbindungen (0 bis maxChildren+1)
     */
    public int getNumDirectLinksFromStructure() {
        int linkCount = 0;
        if (parent != null) linkCount++;
        linkCount += children.size();
        return linkCount;
    }

    /**
     * Berechnet die Gesamtanzahl geplanter Links in der Substruktur.
     * Ermittelt alle Links, die benötigt werden, um alle TreeNodes entsprechend
     * ihrer Parent-Child-Beziehungen miteinander zu verbinden.
     * Verwendet die getAllNodesInStructure() Methode für konsistente Substruktur-Abgrenzung.
     * Vermeidet doppelte Zählung durch LinkPair-Set.
     *
     * @return Gesamtanzahl aller geplanten Links in der Substruktur
     */
    public int getNumPlannedLinksFromStructure() {
        Set<TreeNode> allNodes = getAllNodesInStructure();
        Set<LinkPair> countedLinks = new HashSet<>();

        // Durchlaufe alle Knoten der Substruktur
        for (TreeNode current : allNodes) {
            // Sammle alle Nachbarn dieses Knotens (nur die, die auch in der Substruktur sind)
            List<TreeNode> neighbors = new ArrayList<>();

            // Parent hinzufügen (falls in der Substruktur)
            if (current.parent != null && allNodes.contains(current.parent)) {
                neighbors.add(current.parent);
            }

            // Alle Kinder hinzufügen (die bereits durch getAllNodesInStructure() gefiltert sind)
            for (TreeNode child : current.children) {
                if (allNodes.contains(child)) {
                    neighbors.add(child);
                }
            }

            // Für jeden Nachbarn einen Link zählen (aber nur einmal pro Paar)
            for (TreeNode neighbor : neighbors) {
                // Erstelle LinkPair mit konsistenter Reihenfolge (kleinere ID zuerst)
                int fromId = Math.min(current.id, neighbor.id);
                int toId = Math.max(current.id, neighbor.id);
                LinkPair linkPair = new LinkPair(fromId, toId);

                countedLinks.add(linkPair);
            }
        }

        return countedLinks.size();
    }


    /**
     * Prüft ob dieser Knoten ein Terminal-Knoten ist.
     * Terminal-Knoten haben genau eine Verbindung (entweder Parent oder ein Kind).
     *
     * @return true wenn der Knoten genau eine Verbindung hat
     */
    public boolean isTerminal() {
        int connectionCount = children.size() + (parent != null ? 1 : 0);
        return connectionCount == 1;
    }

    /**
     * Prüft ob dieser Knoten ein Endpunkt der Struktur ist.
     * Endpunkte sind entweder Terminal-Knoten oder Root-Blätter.
     *
     * @return true wenn der Knoten ein Terminal ist oder ein Root-Blatt
     */
    public boolean isEndpoint() {
        return isTerminal() || (isRoot() && isLeaf());
    }

    /**
     * Prüft ob dieser Knoten ein Blatt ist.
     * Blatt-Knoten haben keine Kinder.
     *
     * @return true wenn der Knoten keine Kinder hat
     */
    public boolean isLeaf() {
        return children.isEmpty();
    }

    /**
     * Prüft ob dieser Knoten ein Root-Knoten ist.
     * Root-Knoten haben keinen Parent und sind als Head markiert.
     *
     * @return true wenn der Knoten keinen Parent hat und Head ist
     */
    public boolean isRoot() {
        return parent == null && isHead();
    }

    /**
     * Prüft ob dieser Knoten als Head markiert ist.
     *
     * @return true wenn der Knoten als Head markiert ist
     */
    public boolean isHead() {
        return isHead;
    }

    /**
     * Berechnet den Konnektivitätsgrad dieses Knotens.
     * Entspricht der Anzahl direkter Nachbarn (Parent + Kinder).
     *
     * @return Anzahl direkter Verbindungen zu anderen Knoten
     */
    public int getConnectivityDegree() {
        return children.size() + (parent != null ? 1 : 0);
    }

    /**
     * Sammelt alle Knoten der Struktur, zu der dieser Knoten gehört.
     * Verwendet DFS-Traversierung über Parent- und Child-Beziehungen.
     * Ring-sicher durch Visited-Set.
     *
     * @return Set aller Knoten in der zusammenhängenden Struktur
     */
    public Set<TreeNode> getAllNodes() {
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
     * Zählt alle Nachfahren dieses Knotens.
     * Traversiert nur über Child-Beziehungen, nicht über Parent.
     * Zyklus-sicher durch Visited-Set.
     *
     * @return Anzahl aller Nachfahren (Kinder, Enkel, etc.)
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
     * Findet einen Knoten in der Struktur anhand seiner ID.
     * Durchsucht alle verbundenen Knoten mit DFS-Traversierung.
     *
     * @param id Die ID des gesuchten Knotens
     * @return Der gefundene Knoten oder null wenn nicht gefunden
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
     * Berechnet den Pfad vom Head-Knoten zu diesem Knoten.
     * Verwendet BFS mit Predecessor-Tracking für kürzesten Pfad.
     * Ring-sicher durch Visited-Set.
     *
     * @return Liste von Knoten vom Head zu diesem Knoten, leer wenn Head nicht gefunden oder nicht erreichbar
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

    // Getter-Methoden

    /**
     * Gibt die eindeutige ID dieses Knotens zurück.
     *
     * @return Die ID des Knotens
     */
    public int getId() { return id; }

    /**
     * Gibt den Parent-Knoten zurück.
     *
     * @return Der Parent-Knoten oder null wenn keiner vorhanden
     */
    public TreeNode getParent() { return parent; }

    /**
     * Gibt eine Kopie der Kinderliste zurück.
     * Verhindert externe Modifikation der internen Liste.
     *
     * @return Neue ArrayList mit allen Kindern
     */
    public List<TreeNode> getChildren() { return new ArrayList<>(children); }

    /**
     * Gibt die maximale Anzahl von Kindern zurück.
     *
     * @return Maximale Kindanzahl (Integer.MAX_VALUE bedeutet unbegrenzt)
     */
    public int getMaxChildren() { return maxChildren; }


    /**
     * Sammelt alle Knoten der Substruktur, zu der dieser Knoten gehört.
     * Stoppt bei Head-Knoten, um Substrukturen abzugrenzen.
     * Verwendet imperative Stack-basierte Traversierung ohne Rekursion.
     *
     * @return Set aller Knoten in der zusammenhängenden Substruktur
     */
    public Set<TreeNode> getAllNodesInStructure() {
        Set<TreeNode> allNodes = new HashSet<>();
        Set<TreeNode> visited = new HashSet<>();
        Stack<TreeNode> stack = new Stack<>();

        stack.push(this);

        while (!stack.isEmpty()) {
            TreeNode current = stack.pop();

            if (visited.contains(current)) {
                continue;
            }

            visited.add(current);
            allNodes.add(current);

            // Füge Parent hinzu, wenn es kein Head-Knoten ist
            if (current.parent != null && !current.parent.isHead()) {
                if (!visited.contains(current.parent)) {
                    stack.push(current.parent);
                }
            }

            // Füge alle Kinder hinzu, die keine Head-Knoten sind
            for (TreeNode child : current.children) {
                if (!child.isHead() && !visited.contains(child)) {
                    stack.push(child);
                }
            }
        }

        return allNodes;
    }

    /**
     * Ermittelt alle Endpunkte (Terminal-Knoten) der Struktur.
     * Filtert aus der Menge aller Knoten der Substruktur die Terminal-Knoten heraus.
     * Verwendet imperative Stack-basierte Traversierung ohne Rekursion.
     *
     * @return Set aller Terminal-Knoten (Endpunkte) in der Substruktur
     */
    public Set<TreeNode> getEndpointsOfStructure() {
        Set<TreeNode> endpoints = new HashSet<>();
        Set<TreeNode> allNodes = getAllNodesInStructure();

        // Filtere Terminal-Knoten aus der Gesamtmenge
        for (TreeNode node : allNodes) {
            if (node.isTerminal()) {
                endpoints.add(node);
            }
        }

        return endpoints;
    }

    /**
     * Prüft, ob ein gegebener TreeNode Teil der Substruktur ist.
     * Verwendet die bereits implementierte getAllNodes() Methode zur effizienten Prüfung.
     *
     * @param node Der zu prüfende TreeNode (null wird als false behandelt)
     * @return true wenn der Knoten Teil der Substruktur ist, false sonst
     */
    public boolean isPartOfStructure(TreeNode node) {
        if (node == null) {
            return false;
        }

        // Verwende die bereits implementierte getAllNodes() Methode
        Set<TreeNode> allNodes = getAllNodesInStructure();
        return allNodes.contains(node);
    }

    /**
     * Prüft, ob ein gegebener TreeNode ein Endpunkt der Substruktur ist.
     * Verwendet die bereits implementierte getEndpointsOfStructure() Methode zur effizienten Prüfung.
     *
     * @param node Der zu prüfende TreeNode (null wird als false behandelt)
     * @return true wenn der Knoten ein Endpunkt der Substruktur ist, false sonst
     */
    public boolean isEndpointOfStructure(TreeNode node) {
        if (node == null) {
            return false;
        }

        // Verwende die bereits implementierte getEndpointsOfStructure() Methode
        Set<TreeNode> endpoints = getEndpointsOfStructure();
        return endpoints.contains(node);
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
                ", maxChildren=" + maxChildren +
                ", isLeaf=" + isLeaf() +
                ", isRoot=" + isRoot() +
                ", isHead=" + isHead +
                '}';
    }
}