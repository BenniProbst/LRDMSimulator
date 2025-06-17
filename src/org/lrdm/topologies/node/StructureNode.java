package org.lrdm.topologies.node;

import java.util.*;

/**
 * Abstrakte Basis-Klasse für strukturelle Knoten.
 * Verwaltet Parent-Child-Beziehungen und definiert Struktur-Validierung.
 * Ring-sichere Implementierung aller Traversierungsfunktionen.
 * <p>
 * Multi-Type-System mit Head-ID-Unterstützung:
 * - Jeder Knoten kann für verschiedene Strukturtypen gleichzeitig Head sein
 * - Kinder werden mit Sets von Typ-IDs und Head-IDs verknüpft
 * - Ermöglicht Koexistenz verschiedener Strukturtypen mit eindeutiger Identifikation
 * - Unterstützt mehrere Strukturen desselben Typs durch Head-ID-Trennung
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class StructureNode {
    private final int id;
    private StructureNode parent;
    private final Set<ChildRecord> children;
    private final Map<StructureType, Boolean> headStatus; // StructureType -> isHead für diesen Typ
    protected Set<StructureType> nodeTypes; // Welche Typen dieser Knoten selbst repräsentiert
    private int maxChildren = Integer.MAX_VALUE; // Standardmäßig unbegrenzt


    /**
     * Enum für Standard-Strukturtypen.
     * Ermöglicht die typsichere Verwendung verschiedener Strukturarten.
     */
    public enum StructureType {
        DEFAULT(0),
        MIRROR(1),
        TREE(2),
        RING(3),
        LINE(4),
        STAR(5),
        FULLY_CONNECTED(6),
        N_CONNECTED(7);  // Neu hinzugefügt für echte N-Connected-Unterstützung

        private final int id;

        StructureType(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }
    }

    /**
     * Record für Kind-Knoten mit zugehörigen Struktur-Typ-IDs und Head-IDs.
     * Ermöglicht es einem Kind, gleichzeitig zu mehreren Strukturtypen zu gehören.
     */
    public record ChildRecord(StructureNode child, Set<StructureType> typeIds, Map<StructureType, Integer> headIds) {
        public ChildRecord {
            // Defensive Kopien zur Unveränderlichkeit
            typeIds = new HashSet<>(typeIds);
            headIds = new HashMap<>(headIds);
        }

        /**
         * Prüft, ob dieses Kind zu einem bestimmten Strukturtyp gehört.
         */
        public boolean hasType(StructureType typeId) {
            return typeIds.contains(typeId);
        }

        /**
         * Prüft, ob dieses Kind zu einer bestimmten Struktur (Typ + Head-ID) gehört.
         */
        public boolean belongsToStructure(StructureType typeId, int headId) {
            return typeIds.contains(typeId) && headIds.get(typeId) != null && headIds.get(typeId) == headId;
        }

        /**
         * Gibt die Head-ID für einen bestimmten Strukturtyp zurück.
         */
        public Integer getHeadId(StructureType typeId) {
            return headIds.get(typeId);
        }

        /**
         * Gibt eine unveränderliche Kopie der Typ-IDs zurück.
         */
        public Set<StructureType> getTypeIds() {
            return new HashSet<>(typeIds);
        }

        /**
         * Gibt eine unveränderliche Kopie der Head-IDs zurück.
         */
        public Map<StructureType, Integer> getHeadIds() {
            return new HashMap<>(headIds);
        }
    }

    /**
     * Einfache Tupel-Klasse für Link-IDs (um String-Konkatenation zu vermeiden).
     * Verwendet für die eindeutige Identifikation von Verbindungen zwischen Knoten.
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

    // ===== KONSTRUKTOREN =====

    /**
     * Erstellt einen neuen StructureNode mit gegebener ID.
     * Initialisiert alle internen Strukturen und fügt den Standard-Typ hinzu.
     *
     * @param id Eindeutige Identifikationsnummer des Knotens
     */
    public StructureNode(int id) {
        this.id = id;
        this.parent = null;
        this.children = new HashSet<>();
        this.headStatus = new HashMap<>();
        this.nodeTypes = new HashSet<>();

        // Typ-Ermittlung über Instanz-Analyse
        this.nodeTypes.add(StructureType.DEFAULT);
    }

    /**
     * Erstellt einen neuen StructureNode mit gegebener ID und maximaler Kindanzahl.
     *
     * @param id          Eindeutige Identifikationsnummer des Knotens
     * @param maxChildren Maximale Anzahl von Kindern, die dieser Knoten haben kann (muss >= 0 sein)
     */
    public StructureNode(int id, int maxChildren) {
        this(id);
        this.maxChildren = Math.max(0, maxChildren);
    }

    // ===== STRUKTURTYP-ERMITTLUNG =====

    /**
     * Bestimmt die Typ-ID basierend auf der Instanz der StructureNode.
     * Kann von Kindklassen überschrieben werden für automatische Typ-Erkennung.
     *
     * @return Der Standard-Strukturtyp für diese Knotenart
     */
    protected StructureType deriveTypeId() {
        return StructureType.DEFAULT;
    }

    // ===== NODE TYPES MANAGEMENT =====

    /**
     * Gibt die Set von Strukturtypen zurück, die dieser Knoten repräsentiert.
     * Ermöglicht es, zu prüfen, welche Strukturtypen ein Knoten unterstützt.
     *
     * @return Unveränderliche Kopie der Knotentypen
     */
    public Set<StructureType> getNodeTypes() {
        return new HashSet<>(nodeTypes);
    }

    /**
     * Setzt die Set von Strukturtypen für diesen Knoten.
     * Überschreibt die aktuellen Knotentypen vollständig.
     *
     * @param nodeTypes Die neuen Knotentypen (darf nicht null sein)
     */
    public void setNodeTypes(Set<StructureType> nodeTypes) {
        if (nodeTypes == null) {
            throw new IllegalArgumentException("NodeTypes dürfen nicht null sein");
        }
        this.nodeTypes.clear();
        this.nodeTypes.addAll(nodeTypes);
    }

    /**
     * Fügt einen Strukturtyp zu diesem Knoten hinzu.
     * Ergänzt die bereits vorhandenen Knotentypen.
     *
     * @param nodeType Der hinzuzufügende Strukturtyp (darf nicht null sein)
     */
    public void addNodeType(StructureType nodeType) {
        if (nodeType == null) {
            throw new IllegalArgumentException("NodeType darf nicht null sein");
        }
        this.nodeTypes.add(nodeType);
    }

    /**
     * Entfernt einen Strukturtyp von diesem Knoten.
     * Behält andere vorhandene Knotentypen bei.
     *
     * @param nodeType Der zu entfernende Strukturtyp
     */
    public void removeNodeType(StructureType nodeType) {
        this.nodeTypes.remove(nodeType);
    }

    /**
     * Prüft, ob dieser Knoten einen bestimmten Strukturtyp unterstützt.
     *
     * @param nodeType Der zu prüfende Strukturtyp
     * @return true, wenn der Knoten diesen Typ unterstützt
     */
    public boolean hasNodeType(StructureType nodeType) {
        return nodeTypes.contains(nodeType);
    }

    // ===== MULTI-TYPE TRAVERSIERUNG =====

    /**
     * Sammelt alle Knoten der Substruktur für einen bestimmten Strukturtyp und Head-Node.
     * Traversiert nur über Verbindungen mit der angegebenen Typ-ID und Head-ID.
     * Stoppt bei Head-Knoten des gleichen Typs (Substruktur-Abgrenzung).
     *
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @param head Die Head-Node der gewünschten Struktur
     * @return Set aller Knoten dieser Struktur
     */
    public Set<StructureNode> getAllNodesInStructure(StructureType typeId, StructureNode head) {
        if (head == null) return Set.of(this);

        final int headId = head.getId(); // Head-ID einmal bestimmen und festhalten
        Set<StructureNode> result = new HashSet<>();
        Stack<StructureNode> stack = new Stack<>();
        stack.push(this);

        while (!stack.isEmpty()) {
            StructureNode current = stack.pop();

            if (result.contains(current)) continue; // Bereits besucht - Ring-sicher
            result.add(current);

            // Head-Knoten stoppen hier - keine weitere Traversierung über Parent
            // Dies ermöglicht Substruktur-Abgrenzung bei verschachtelten Strukturen
            if (current.isHead(typeId) && current.getId() == headId) continue;

            // Parent traversieren (wenn Typ-ID und Head-ID passen)
            if (current.parent != null) {
                ChildRecord parentRecord = current.parent.findChildRecordById(current.getId());
                if (parentRecord != null && parentRecord.belongsToStructure(typeId, headId)) {
                    stack.push(current.parent);
                }
            }

            // Kinder traversieren (wenn Typ-ID und Head-ID passen)
            Set<StructureNode> structureChildren = current.getChildren(typeId, headId);
            stack.addAll(structureChildren);
        }

        return result;
    }

    /**
     * Sammelt alle Endpunkte (Terminal-Knoten) für einen bestimmten Strukturtyp und Head-Node.
     * Endpunkte sind Knoten mit genau einer Verbindung in der Struktur.
     *
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @param head Die Head-Node der gewünschten Struktur
     * @return Set aller Endpunkte für diese Struktur
     */
    public Set<StructureNode> getEndpointsOfStructure(StructureType typeId, StructureNode head) {
        if (head == null) return Set.of(this);

        final int headId = head.getId(); // Head-ID einmal bestimmen
        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head);
        Set<StructureNode> endpoints = new HashSet<>();

        for (StructureNode node : allNodes) {
            if (isEndpoint(node, typeId, headId)) {
                endpoints.add(node);
            }
        }

        return endpoints;
    }

    // ===== Zyklusprüfung - ALLE METHODEN ZUSAMMEN GRUPPIERT =====

    /**
     * Prüft auf geschlossene Zyklen in einer bestimmten Struktur.
     * Berücksichtigt Typ-ID und Head-Node für korrekte Kanal-Traversierung.
     * Stack-basierte Implementierung zur Vermeidung von Rekursion.
     *
     * @param allNodes Alle Knoten der zu prüfenden Struktur
     * @param typeId Der Strukturtyp
     * @param head Die Head-Node
     * @return true, wenn ein geschlossener Zyklus gefunden wird
     */
    public boolean hasClosedCycle(Set<StructureNode> allNodes, StructureType typeId, StructureNode head) {
        if (allNodes == null || allNodes.isEmpty() || head == null) return false;

        final int headId = head.getId();
        Set<StructureNode> visited = new HashSet<>();
        Set<StructureNode> globalRecursionStack = new HashSet<>();

        // DFS für jeden unbesuchten Knoten (stack-basiert)
        for (StructureNode startNode : allNodes) {
            if (!visited.contains(startNode)) {
                if (hasCycleDFS(startNode, visited, globalRecursionStack, typeId, headId)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Stack-basierte DFS-Hilfsmethode für Zykluserkennung mit Typ-/Head-ID-Berücksichtigung.
     * Verwendet expliziten Stack statt Rekursion für bessere Kontrolle und Performance.
     */
    private boolean hasCycleDFS(StructureNode startNode, Set<StructureNode> visited,
                                Set<StructureNode> globalRecursionStack, StructureType typeId, int headId) {

        // Stack für DFS-Traversierung: (Knoten, isProcessing)
        // isProcessing = true: Knoten werden gerade verarbeitet (Pre-Order)
        // isProcessing = false: Knoten-Verarbeitung abgeschlossen (Post-Order)
        Stack<StackEntry> stack = new Stack<>();
        Set<StructureNode> localRecursionStack = new HashSet<>();

        stack.push(new StackEntry(startNode, true));

        while (!stack.isEmpty()) {
            StackEntry entry = stack.pop();
            StructureNode node = entry.node;
            boolean isProcessing = entry.isProcessing;

            if (isProcessing) {
                // Pre-Order: Knoten zum ersten Mal besucht
                if (localRecursionStack.contains(node) || globalRecursionStack.contains(node)) {
                    return true; // Zyklus gefunden
                }

                if (visited.contains(node)) {
                    continue; // Bereits vollständig verarbeitet
                }

                visited.add(node);
                localRecursionStack.add(node);
                globalRecursionStack.add(node);

                // Post-Order Verarbeitung für diesen Knoten einplanen
                stack.push(new StackEntry(node, false));

                // Alle Kinder für den spezifischen Typ und Head hinzufügen
                Set<StructureNode> children = node.getChildren(typeId, headId);
                for (StructureNode child : children) {
                    stack.push(new StackEntry(child, true));
                }

            } else {
                // Post-Order: Knoten-Verarbeitung abgeschlossen
                localRecursionStack.remove(node);
                globalRecursionStack.remove(node);
            }
        }

        return false;
    }

    /**
     * Grundlegende Zyklusprüfung für beliebige Knotenstrukturen.
     * Fundamentale statische Hilfsmethode, die von Kindklassen genutzt werden kann.
     * Speziell für geschlossene Ring-Zyklen optimiert.
     * Stack-basierte Implementierung zur Vermeidung von Rekursion.
     *
     * @param nodes Menge von Knoten, die auf geschlossenen Zyklus geprüft werden sollen
     * @return true, wenn alle Knoten genau einen geschlossenen Zyklus bilden, false bei leerer Menge oder anderen Strukturen
     */
    public static boolean hasClosedCycle(Set<StructureNode> nodes) {
        if (nodes.isEmpty()) return false;

        // Für geschlossene Zyklen: Jeder Knoten muss genau ein Kind haben
        for (StructureNode node : nodes) {
            if (node.getChildren().size() != 1) {
                return false;
            }
        }

        // Stack-basierte Zyklus-Traversierung
        StructureNode start = nodes.iterator().next();
        Set<StructureNode> visitedInCycle = new HashSet<>();
        StructureNode current = start;

        // Folge der Kette, bis wir entweder einen Zyklus finden oder die Menge verlassen
        do {
            if (visitedInCycle.contains(current)) {
                // Zyklus gefunden - prüfe, ob es ein vollständiger Zyklus ist
                return current == start && visitedInCycle.size() == nodes.size();
            }

            visitedInCycle.add(current);

            // Folge dem einzigen Kind
            Set<StructureNode> children = current.getChildren();
            if (children.size() != 1) {
                return false; // Sollte nicht passieren, da bereits geprüft
            }

            StructureNode child = children.iterator().next();

            // Prüfe, ob das Kind in der ursprünglichen Menge ist
            if (!nodes.contains(child)) {
                return false; // Zyklus verlässt die Knotenmenge
            }

            current = child;

        } while (!visitedInCycle.contains(current));

        // Wenn wir hier ankommen, haben wir einen Zyklus gefunden.
        // Prüfe, ob es ein vollständiger Zyklus zurück zum Start ist
        return current == start && visitedInCycle.size() == nodes.size();
    }

    // ===== HILFSKLASSE FÜR STACK-BASIERTE DFS =====

    /**
         * Hilfsklasse für Stack-basierte DFS-Traversierung.
         * Speichert Knoten und Verarbeitungsstatus (Pre-Order vs Post-Order).
         */
        private record StackEntry(StructureNode node, boolean isProcessing) {
    }

    // ===== KIND-MANAGEMENT =====

    /**
     * Fügt einen Kindknoten mit Struktur-Typ-IDs und Head-IDs hinzu.
     * Unterstützt das Multi-Type-System durch explizite Typ- und Head-ID-Zuordnung.
     *
     * @param child   Der hinzuzufügende Kindknoten (darf nicht null sein)
     * @param typeIds Die Struktur-Typ-IDs für diese Verbindung
     * @param headIds Map von Typ-ID zu Head-ID für Strukturidentifikation
     */
    public void addChild(StructureNode child, Set<StructureType> typeIds, Map<StructureType, Integer> headIds) {
        if (child == null || typeIds == null || typeIds.isEmpty()) {
            return;
        }

        // Automatische Typ-ID-Ableitung wenn nicht im Set
        Set<StructureType> finalTypeIds = new HashSet<>(typeIds);
        StructureType derivedTypeId = child.deriveTypeId();
        finalTypeIds.add(derivedTypeId);

        // Validierung: Alle Typ-IDs müssen in headIds vertreten sein
        Map<StructureType, Integer> finalHeadIds = new HashMap<>(headIds);
        for (StructureType typeId : finalTypeIds) {
            if (!finalHeadIds.containsKey(typeId)) {
                throw new IllegalArgumentException("Head-ID fehlt für Typ-ID: " + typeId);
            }
        }

        // Prüfe, ob ein Kind bereits existiert (nach ID)
        ChildRecord existingRecord = findChildRecordById(child.getId());

        if (existingRecord != null) {
            // Kind existiert bereits - Typ-IDs und Head-IDs zusammenführen
            Set<StructureType> mergedTypes = new HashSet<>(existingRecord.typeIds());
            mergedTypes.addAll(finalTypeIds);

            Map<StructureType, Integer> mergedHeadIds = new HashMap<>(existingRecord.headIds());
            mergedHeadIds.putAll(finalHeadIds);

            // Alten Record entfernen und neuen hinzufügen
            children.remove(existingRecord);
            children.add(new ChildRecord(child, mergedTypes, mergedHeadIds));
            child.setParent(this);
        } else {
            // Neues Kind hinzufügen (nur wenn unter dem Limit)
            if (children.size() < maxChildren) {
                children.add(new ChildRecord(child, finalTypeIds, finalHeadIds));
                child.setParent(this);
            }
        }
    }

    /**
     * Fügt einen Kindknoten mit automatischer Typ- und Head-ID-Ermittlung hinzu.
     * Verwendet den abgeleiteten Typ und sucht den Head automatisch.
     *
     * @param child Der hinzuzufügende Kindknoten (darf nicht null sein)
     */
    public void addChild(StructureNode child) {
        if (child == null) return;

        StructureType derivedTypeId = child.deriveTypeId();
        StructureNode head = findHead(derivedTypeId);
        int headId = (head != null) ? head.getId() : this.getId();

        addChild(child, Set.of(derivedTypeId), Map.of(derivedTypeId, headId));
    }

    /**
     * Entfernt einen Kindknoten nur für bestimmte Strukturtypen.
     * Ermöglicht selektive Entfernung aus Multi-Type-Strukturen.
     *
     * @param child   Der Kindknoten
     * @param typeIds Die zu entfernenden Typ-IDs
     */
    public void removeChild(StructureNode child, Set<StructureType> typeIds) {
        if (child == null || typeIds == null) return;

        ChildRecord existingRecord = findChildRecordById(child.getId());
        if (existingRecord == null) return;

        Set<StructureType> remainingTypes = new HashSet<>(existingRecord.typeIds());
        remainingTypes.removeAll(typeIds);

        Map<StructureType, Integer> remainingHeadIds = new HashMap<>(existingRecord.headIds());
        typeIds.forEach(remainingHeadIds::remove);

        if (remainingTypes.isEmpty()) {
            // Keine Typen mehr übrig - Kind komplett entfernen
            children.remove(existingRecord);
            if (child.getParent() == this) {
                child.setParent(null);
            }
        } else {
            // Typ-IDs aktualisieren
            children.remove(existingRecord);
            children.add(new ChildRecord(child, remainingTypes, remainingHeadIds));
        }
    }

    /**
     * Entfernt einen Kindknoten komplett aus allen Strukturtypen.
     * Setzt auch die Parent-Beziehung zurück.
     *
     * @param child Der zu entfernende Kindknoten (null wird ignoriert)
     */
    public void removeChild(StructureNode child) {
        if (child != null) {
            children.removeIf(record -> record.child().getId() == child.getId());
            if (child.getParent() == this) {
                child.setParent(null);
            }
        }
    }

    /**
     * Findet einen ChildRecord anhand der Knoten-ID.
     * Hilfsmethode für interne Kind-Verwaltung.
     */
    protected ChildRecord findChildRecordById(int childId) {
        return children.stream()
                .filter(record -> record.child().getId() == childId)
                .findFirst()
                .orElse(null);
    }

    // ===== KIND-ZUGRIFF =====

    /**
     * Gibt alle direkten Kindknoten zurück (ohne Typ-Filter).
     * Sammelt alle Kinder aus allen ChildRecords.
     *
     * @return Set aller direkten Kindknoten
     */
    public Set<StructureNode> getChildren() {
        Set<StructureNode> allChildren = new HashSet<>();
        for (ChildRecord record : children) {
            allChildren.add(record.child());
        }
        return allChildren;
    }

    /**
     * Gibt alle direkten Kindknoten für einen bestimmten Strukturtyp zurück.
     *
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @return Set aller Kindknoten für den gegebenen Typ
     */
    public Set<StructureNode> getChildren(StructureType typeId) {
        Set<StructureNode> typeChildren = new HashSet<>();
        for (ChildRecord record : children) {
            if (record.hasType(typeId)) {
                typeChildren.add(record.child());
            }
        }
        return typeChildren;
    }

    /**
     * Gibt alle direkten Kindknoten für eine spezifische Struktur zurück.
     *
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @param headId Die Head-ID der gewünschten Struktur
     * @return Set aller Kindknoten für die spezifische Struktur
     */
    public Set<StructureNode> getChildren(StructureType typeId, int headId) {
        Set<StructureNode> structureChildren = new HashSet<>();
        for (ChildRecord record : children) {
            if (record.belongsToStructure(typeId, headId)) {
                structureChildren.add(record.child());
            }
        }
        return structureChildren;
    }

    // ===== HEAD-SUCHE =====


    /**
     * Findet den Head-Knoten für einen bestimmten Strukturtyp.
     * Sucht strikt nach Head-Knoten und gibt null zurück, wenn niemand gefunden wird.
     * Verwendet Stack-basierte Traversierung für Ring-Sicherheit.
     *
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @return Head-Knoten für diesen Strukturtyp oder null
     */
    public StructureNode findHead(StructureType typeId) {
        if (typeId == null) {
            return null;
        }

        // Stack-basierte Traversierung für Ring-Sicherheit
        Set<StructureNode> visited = new HashSet<>();
        Stack<StructureNode> stack = new Stack<>();
        stack.push(this);

        while (!stack.isEmpty()) {
            StructureNode current = stack.pop();

            if (visited.contains(current)) {
                continue; // Bereits besucht - verhindert Zyklen
            }
            visited.add(current);

            // Prüfe, ob der aktuelle Knoten ein Head für den gewünschten Typ ist
            if (current.isHead(typeId)) {
                return current; // Head-Knoten gefunden - gibt Head-Knoten zurück, nicht dessen ID
            }

            // Füge Parent hinzu (nicht Kinder - wir suchen nach oben zum Head)
            if (current.getParent() != null && !visited.contains(current.getParent())) {
                stack.push(current.getParent());
            }
        }

        // Kein Head gefunden - gibt null zurück, anstatt nach Kindern zu suchen
        return null;
    }

    // ===== ENDPUNKT-ERKENNUNG =====

    /**
     * Prüft, ob ein Knoten ein Endpunkt in einer bestimmten Struktur ist.
     * Ein Endpunkt hat genau eine Verbindung in der Struktur (Terminal-Knoten).
     *
     * @param node   Der zu prüfende Knoten
     * @param typeId Die Typ-ID der Struktur
     * @param headId Die Head-ID der Struktur
     * @return true, wenn der Knoten ein Endpunkt in dieser Struktur ist
     */
    public boolean isEndpoint(StructureNode node, StructureType typeId, int headId) {
        int connections = 0;

        // Parent-Verbindung zählen (wenn Typ-ID und Head-ID passen)
        if (node.parent != null) {
            ChildRecord parentRecord = node.parent.findChildRecordById(node.getId());
            if (parentRecord != null && parentRecord.belongsToStructure(typeId, headId)) {
                connections++;
            }
        }

        // Kind-Verbindungen zählen (wenn Typ-ID und Head-ID passen)
        connections += node.getChildren(typeId, headId).size();

        return connections == 1; // Terminal = genau eine Verbindung
    }

    // ===== VOLLSTÄNDIGE TRAVERSIERUNG (Legacy und Universell) =====

    /**
     * Sammelt ALLE verbundenen Knoten (vollständige Traversierung ohne Head-Stops).
     * Unterschied zu getAllNodesInStructure(): stoppt NICHT bei Head-Knoten.
     * Verwendet für vollständige Netzwerk-Analyse und Validierung.
     *
     * @return Set aller erreichbaren Knoten von diesem Startpunkt
     */
    public Set<StructureNode> getAllNodes() {
        Set<StructureNode> result = new HashSet<>();
        Stack<StructureNode> stack = new Stack<>();
        stack.push(this);

        while (!stack.isEmpty()) {
            StructureNode current = stack.pop();

            if (result.contains(current)) continue; // Ring-sicher
            result.add(current);

            // Parent hinzufügen (ohne Typ-Beschränkung)
            if (current.parent != null) {
                stack.push(current.parent);
            }

            // Alle Kinder hinzufügen (ohne Typ-Beschränkung)
            current.getChildren().forEach(stack::push);
        }

        return result;
    }

    // Füge diese Methode zu StructureNode hinzu:

    /**
     * Berechnet den Pfad von einer spezifischen Head-Node zu diesem Knoten.
     * Berücksichtigt Typ-ID und Head-ID für korrekte Multi-Type-Traversierung.
     * Verwendet BFS für den kürzesten Pfad in ungewichteten Strukturen.
     *
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @param head Die Head-Node der gewünschten Struktur
     * @return Liste der Knoten vom Head zu diesem Knoten (inklusive Head und this)
     */
    public List<StructureNode> getPathFromHead(StructureType typeId, StructureNode head) {
        if (head == null) return List.of(this);

        List<StructureNode> path = new ArrayList<>();
        Stack<StructureNode> stack = new Stack<>();
        Map<StructureNode, StructureNode> parentMap = new HashMap<>();
        Set<StructureNode> visited = new HashSet<>();

        // BFS von Head zu diesem Knoten für kürzesten Pfad
        Queue<StructureNode> queue = new LinkedList<>();
        queue.offer(head);
        visited.add(head);
        parentMap.put(head, null);

        while (!queue.isEmpty()) {
            StructureNode current = queue.poll();

            if (current == this) {
                // Pfad rekonstruieren (rückwärts)
                StructureNode pathNode = this;
                while (pathNode != null) {
                    stack.push(pathNode);
                    pathNode = parentMap.get(pathNode);
                }

                // Stack umkehren für korrekten Pfad (Head -> ... -> this)
                while (!stack.isEmpty()) {
                    path.add(stack.pop());
                }

                return path;
            }

            // Nur strukturspezifische Nachbarn besuchen
            List<StructureNode> neighbors = new ArrayList<>();

            // Parent, nur wenn er zur gleichen Struktur gehört
            if (current.parent != null) {
                // Prüfe, ob Parent zur selben Struktur gehört
                Set<StructureNode> structureNodes = head.getAllNodesInStructure(typeId, head);
                if (structureNodes.contains(current.parent)) {
                    neighbors.add(current.parent);
                }
            }

            // Nur Kinder der spezifischen Struktur
            neighbors.addAll(current.getChildren(typeId, head.getId()));

            for (StructureNode neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    parentMap.put(neighbor, current);
                    queue.offer(neighbor);
                }
            }
        }

        // Kein Pfad gefunden - zurück zu diesem Knoten allein
        return List.of(this);
    }

    /**
     * Berechnet den Pfad von der Head-Node zu diesem Knoten.
     * Verwendet automatische Typ- und Head-Ermittlung.
     * Verwendet BFS für den kürzesten Pfad in ungewichteten Strukturen.
     *
     * @return Liste der Knoten vom Head zu diesem Knoten (inklusive Head und this)
     */
    public List<StructureNode> getPathFromHead() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);

        if (head == null) return List.of(this);

        return getPathFromHead(typeId, head);
    }


// ===== LINK-ZÄHLUNG =====

    /**
     * Berechnet die Anzahl der geplanten Links in einer spezifischen Struktur.
     * Berücksichtigt Typ-ID und Head-ID für korrekte Kanal-Traversierung.
     *
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @param head Die Head-Node der gewünschten Struktur
     * @return Anzahl der geplanten Links in der spezifischen Struktur
     */
    public int getNumPlannedLinksFromStructure(StructureType typeId, StructureNode head) {
        if (head == null) return 0;

        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head);
        if (allNodes.size() <= 1) return 0;

        // Für Bäume: n Knoten = n-1 Links
        // für Ringe: n Knoten = n Links
        // für andere Strukturen kann dies überschrieben werden
        if (typeId == StructureType.RING) {
            return allNodes.size(); // Ring: Jeder Knoten hat genau einen ausgehenden Link
        } else {
            return allNodes.size() - 1; // Baum/Standard: n-1 Links
        }
    }

    /**
     * Berechnet die Anzahl der geplanten Links in der Struktur.
     * Verwendet automatische Typ- und Head-Ermittlung.
     *
     * @return Anzahl der geplanten Links in der Struktur
     */
    public int getNumPlannedLinksFromStructure() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        return getNumPlannedLinksFromStructure(typeId, head != null ? head : this);
    }

    /**
     * Berechnet die Anzahl direkter Verbindungen dieses Knotens.
     * Zählt Parent-Verbindung (falls vorhanden) plus alle Kinder.
     * Wichtig für Terminal-Erkennung (1 Verbindung) und Struktur-Analyse.
     *
     * @return Anzahl direkter Verbindungen (0 bis maxChildren+1)
     */
    public int getNumDirectLinksFromStructure() {
        int linkCount = 0;
        if (parent != null) linkCount++; // Parent-Verbindung
        linkCount += children.size(); // Alle Kind-Verbindungen
        return linkCount;
    }

    // ===== NACHFAHREN-ZÄHLUNG =====

    /**
     * Zählt alle Nachfahren dieses Knotens (Kinder, Enkel, etc.).
     * Verwendet Stack-basierte Traversierung zur Vermeidung von Rekursion.
     *
     * @return Anzahl aller Nachfahren
     */
    public int getDescendantCount() {
        Set<StructureNode> descendants = new HashSet<>();
        Stack<StructureNode> stack = new Stack<>();

        // Alle direkten Kinder als Start
        stack.addAll(getChildren());

        while (!stack.isEmpty()) {
            StructureNode current = stack.pop();
            if (descendants.contains(current)) continue; // Ring-sicher

            descendants.add(current);
            stack.addAll(current.getChildren()); // Enkel hinzufügen
        }

        return descendants.size();
    }

    // ===== KNOTEN-SUCHE =====

    /**
     * Findet einen Knoten anhand seiner ID in der gesamten verbundenen Struktur.
     * Traversiert alle erreichbaren Knoten ohne Typ-Beschränkung.
     *
     * @param nodeId Die gesuchte Knoten-ID
     * @return Der gefundene Knoten oder null
     */
    public StructureNode findNodeById(int nodeId) {
        Set<StructureNode> allNodes = getAllNodes();

        for (StructureNode node : allNodes) {
            if (node.getId() == nodeId) {
                return node;
            }
        }

        return null; // Knoten nicht gefunden
    }

    // ===== STRUKTUR-MANAGEMENT =====

    /**
     * Prüft, ob dieser Knoten aus der Struktur entfernt werden kann.
     * Ein Knoten kann entfernt werden, wenn:
     * - Er Teil der Struktur ist (über getAllNodesInStructure ermittelt)
     * - Seine Entfernung die Strukturintegrität nicht gefährdet
     * - Er ein Blatt ist (keine Kinder hat, um Fragmentierung zu vermeiden)
     * <p>
     * Der strukturRoot kann ebenfalls entfernt werden, wenn er ein Blatt ist.
     *
     * @param structureRoot Der Root-Knoten der Struktur für Referenz (darf nicht null sein)
     * @return true, wenn der Knoten sicher entfernt werden kann
     */
    public boolean canBeRemovedFromStructure(StructureNode structureRoot) {
        if (structureRoot == null) return false;

        // Verwende die korrekte Strukturermittlung
        Set<StructureNode> structureNodes = structureRoot.getAllNodesInStructure();

        // Prüfe, ob dieser Knoten Teil der Struktur ist
        if (!structureNodes.contains(this)) {
            return false; // Knoten ist nicht Teil der Struktur
        }

        // Ein Knoten kann entfernt werden, wenn er ein Blatt ist.
        // Dies verhindert die Fragmentierung der Struktur.
        // Auch der Head/Root kann entfernt werden, wenn er ein Blatt ist
        return this.isLeaf();
    }

    // ===== GRUNDLEGENDE STRUKTUR-VALIDIERUNG =====

    /**
     * Grundlegende Struktur-Validierung - prüft, ob alle Knoten miteinander verbunden sind.
     * Verhindert isolierte Knoten in der Struktur.
     * Unterstützt sowohl Baum- als auch Ring-Strukturen.
     *
     * @param allNodes Menge aller Knoten, die zur Struktur gehören sollen
     * @return true, wenn alle Knoten zusammenhängend sind, false bei isolierten Knoten oder null/leerer Menge
     */
    public boolean isValidStructure(Set<StructureNode> allNodes) {
        if (allNodes == null || allNodes.isEmpty()) return false;
        if (allNodes.size() == 1) return true; // Ein einzelner Knoten ist gültig

        // Prüfe, ob alle Knoten zusammenhängend sind
        return isConnectedStructure(allNodes);
    }

    /**
     * Prüft, ob alle Knoten in der gegebenen Menge zusammenhängend sind.
     * Verwendet BFS, um zu überprüfen, dass alle Knoten erreichbar sind.
     * Ring-sichere Implementierung für alle Strukturtypen.
     *
     * @param allNodes Menge aller zu prüfenden Knoten
     * @return true, wenn alle Knoten von einem beliebigen Startknoten erreichbar sind
     */
    private boolean isConnectedStructure(Set<StructureNode> allNodes) {
        if (allNodes.isEmpty()) return false;

        // Starte von einem beliebigen Knoten
        StructureNode startNode = allNodes.iterator().next();
        Set<StructureNode> visited = new HashSet<>();
        Queue<StructureNode> queue = new LinkedList<>();

        queue.offer(startNode);
        visited.add(startNode);

        // BFS-Traversierung über alle Verbindungen
        while (!queue.isEmpty()) {
            StructureNode current = queue.poll();

            // Besuche alle Nachbarn (Parent und Children)
            List<StructureNode> neighbors = new ArrayList<>();

            // Parent hinzufügen (falls vorhanden)
            if (current.parent != null) {
                neighbors.add(current.parent);
            }

            // Alle Kinder hinzufügen
            neighbors.addAll(current.getChildren());

            // Besuche fehlende Knoten
            for (StructureNode neighbor : neighbors) {
                if (allNodes.contains(neighbor) && !visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.offer(neighbor);
                }
            }
        }

        // Alle Knoten müssen erreichbar sein
        return visited.size() == allNodes.size();
    }

    // ===== HEAD-STATUS METHODEN =====

    /**
     * Markiert diesen Knoten als Head für einen bestimmten Strukturtyp.
     * Ermöglicht Multi-Type-Head-Status.
     *
     * @param typeId Die Typ-ID der Struktur
     * @param isHead true, um Head zu markieren, false, um Head-Status zu entfernen
     */
    public void setHead(StructureType typeId, boolean isHead) {
        if (isHead) {
            headStatus.put(typeId, true);
        } else {
            headStatus.remove(typeId);
        }
    }

    /**
     * Markiert diesen Knoten als Head für alle seine Strukturtypen.
     * Legacy-Methode für einfache Verwendung.
     *
     * @param isHead true, um Head zu markieren, false, um Head-Status zu entfernen
     */
    public void setHead(boolean isHead) {
        for (StructureType typeId : nodeTypes) {
            setHead(typeId, isHead);
        }
    }

    /**
     * Prüft, ob dieser Knoten Head für einen bestimmten Strukturtyp ist.
     *
     * @param typeId Die zu prüfende Typ-ID
     * @return true, wenn dieser Knoten Head für den Typ ist
     */
    public boolean isHead(StructureType typeId) {
        return headStatus.getOrDefault(typeId, false);
    }

    /**
     * Prüft, ob dieser Knoten Head für irgendeinen Strukturtyp ist.
     * Legacy-Methode für einfache Verwendung.
     *
     * @return true, wenn dieser Knoten für mindestens einen Typ Head ist
     */
    public boolean isHead() {
        return headStatus.values().stream().anyMatch(Boolean::booleanValue);
    }

    // ===== LEGACY-METHODEN FÜR Rückwärtskompatibilität =====

    /**
     * Sammelt alle Knoten der Default-Struktur.
     * Legacy-Methode, die den DEFAULT-Typ verwendet.
     *
     * @return Set aller Knoten in der Default-Struktur
     */
    public Set<StructureNode> getAllNodesInStructure() {
        StructureType defaultType = StructureType.DEFAULT;
        StructureNode head = findHead(defaultType);
        if (head == null) head = this; // Fallback für Legacy-Kompatibilität
        return getAllNodesInStructure(defaultType, head);
    }

    /**
     * Findet den Head der Default-Struktur mit Root-Fallback.
     * Legacy-Methode für einfache Verwendung.
     *
     * @return Head-Knoten der Default-Struktur oder Root-Knoten
     */
    public StructureNode findHead() {
        StructureNode head = findHead(StructureType.DEFAULT);
        if (head != null) return head;

        // Fallback: Suche nach Root-Knoten (für Legacy-Kompatibilität)
        Set<StructureNode> visited = new HashSet<>();
        Stack<StructureNode> stack = new Stack<>();
        stack.push(this);

        while (!stack.isEmpty()) {
            StructureNode current = stack.pop();
            if (visited.contains(current)) continue;
            visited.add(current);

            if (current.isRoot()) return current;

            if (current.parent != null && !visited.contains(current.parent)) {
                stack.push(current.parent);
            }
            for (StructureNode child : current.getChildren()) {
                if (!visited.contains(child)) {
                    stack.push(child);
                }
            }
        }

        return null;
    }

    /**
     * Sammelt alle Endpunkte der Default-Struktur.
     * Legacy-Methode für einfache Verwendung.
     *
     * @return Set aller Endpunkte in der Default-Struktur
     */
    public Set<StructureNode> getEndpointsOfStructure() {
        StructureType defaultType = StructureType.DEFAULT;
        StructureNode head = findHead(defaultType);
        if (head == null) head = this;
        return getEndpointsOfStructure(defaultType, head);
    }

    /**
     * Prüft, ob dieser Knoten ein Endpunkt in der Default-Struktur ist.
     * Legacy-Methode für einfache Verwendung.
     *
     * @return true, wenn dieser Knoten ein Endpunkt ist
     */
    public boolean isEndpoint() {
        StructureType defaultType = StructureType.DEFAULT;
        StructureNode head = findHead(defaultType);
        int headId = (head != null) ? head.getId() : this.getId();
        return isEndpoint(this, defaultType, headId);
    }

    // ===== GRUNDLEGENDE NODE-EIGENSCHAFTEN =====

    // ===== KNOTEN-TYP-ERKENNUNG (MULTI-TYPE-SYSTEM) =====

    /**
     * Prüft, ob dieser Knoten ein Blatt ist (keine Kinder hat) für eine spezifische Struktur.
     * Berücksichtigt Typ-ID und Head-ID für korrekte Multi-Type-Strukturerkennung.
     *
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @param headId Die Head-ID der gewünschten Struktur
     * @return true, wenn keine Kinder für diese spezifische Struktur vorhanden sind
     */
    public boolean isLeaf(StructureType typeId, int headId) {
        return getChildren(typeId, headId).isEmpty();
    }

    /**
     * Prüft, ob dieser Knoten ein Blatt ist (keine Kinder hat).
     * Verwendet automatische Typ- und Head-Ermittlung.
     *
     * @return true, wenn keine Kinder vorhanden sind
     */
    public boolean isLeaf() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        if (head == null) {
            return children.isEmpty(); // Fallback auf globale Kinder-Prüfung
        }
        return isLeaf(typeId, head.getId());
    }

    /**
     * Prüft, ob dieser Knoten terminal ist (genau eine Verbindung hat) für eine spezifische Struktur.
     * Terminal-Knoten sind Endpunkte in Strukturen.
     *
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @param headId Die Head-ID der gewünschten Struktur
     * @return true, wenn genau eine Verbindung in dieser spezifischen Struktur vorhanden ist
     */
    public boolean isTerminal(StructureType typeId, int headId) {
        return isEndpoint(this, typeId, headId);
    }

    /**
     * Prüft, ob dieser Knoten terminal ist (genau eine Verbindung hat).
     * Verwendet automatische Typ- und Head-Ermittlung.
     * Terminal-Knoten sind Endpunkte in Strukturen.
     *
     * @return true, wenn genau eine Verbindung vorhanden ist
     */
    public boolean isTerminal() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        if (head == null) {
            // Fallback: Zähle Parent + Kinder
            return (parent != null ? 1 : 0) + children.size() == 1;
        }
        return isTerminal(typeId, head.getId());
    }

    /**
     * Prüft, ob dieser Knoten ein Root für eine spezifische Struktur ist.
     * Root-Knoten sind sowohl Head-Knoten als auch haben keinen Parent in der Struktur.
     *
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @return true, wenn dieser Knoten Head und ohne Parent in der Struktur ist
     */
    public boolean isRoot(StructureType typeId) {
        // Root = Head + kein Parent in dieser Strukturform
        return isHead(typeId) && (parent == null || !hasParentInStructure(typeId));
    }

    /**
     * Prüft, ob dieser Knoten ein Root ist (keinen Parent hat und Head ist).
     * Verwendet automatische Typ-Ermittlung.
     * Root-Knoten sind Startpunkte für Traversierung.
     *
     * @return true, wenn kein Parent vorhanden ist und dieser Knoten Head ist
     */
    public boolean isRoot() {
        StructureType typeId = deriveTypeId();
        return isRoot(typeId);
    }

    /**
     * Hilfsmethode: Prüft, ob dieser Knoten einen Parent in einer spezifischen Struktur hat.
     *
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @return true, wenn ein Parent in dieser Struktur existiert
     */
    private boolean hasParentInStructure(StructureType typeId) {
        if (parent == null) return false;

        // Prüfe, ob der Parent diesen Knoten als Kind für die spezifische Struktur führt
        StructureNode head = findHead(typeId);
        if (head == null) return parent != null; // Fallback

        Set<StructureNode> parentChildren = parent.getChildren(typeId, head.getId());
        return parentChildren.contains(this);
    }

    /**
     * Erweiterte canAcceptMoreChildren für spezifische Struktur.
     * Berücksichtigt Typ-ID und Head-ID.
     *
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @param headId Die Head-ID der gewünschten Struktur
     * @return true, wenn weitere Kinder für diese Struktur akzeptiert werden können
     */
    public boolean canAcceptMoreChildren(StructureType typeId, int headId) {
        Set<StructureNode> currentChildren = getChildren(typeId, headId);
        return currentChildren.size() < maxChildren;
    }

    /**
     * Prüft, ob dieser Knoten weitere Kinder akzeptieren kann.
     * Verwendet automatische Typ- und Head-Ermittlung.
     *
     * @return true, wenn weitere Kinder akzeptiert werden können
     */
    public boolean canAcceptMoreChildren() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        if (head == null) {
            return children.size() < maxChildren; // Fallback
        }
        return canAcceptMoreChildren(typeId, head.getId());
    }

    /**
     * Berechnet den Konnektivität-grad (Anzahl aller Verbindungen).
     * Summe aus Parent-Verbindung und Kind-Verbindungen.
     *
     * @return Gesamtanzahl der Verbindungen
     */
    public int getConnectivityDegree() {
        return (parent != null ? 1 : 0) + children.size();
    }

    // ===== GETTER UND SETTER =====

    /**
     * Gibt die eindeutige ID dieses Knotens zurück.
     *
     * @return Die Knoten-ID
     */
    public int getId() {
        return id;
    }

    /**
     * Gibt den Parent-Knoten zurück.
     *
     * @return Der Parent-Knoten oder null
     */
    public StructureNode getParent() {
        return parent;
    }

    /**
     * Setzt den Parent-Knoten direkt (Vorsicht: kann Inkonsistenzen verursachen).
     * Normalerweise sollte addChild() verwendet werden.
     *
     * @param parent Der neue Parent-Knoten (kann null sein)
     */
    public void setParent(StructureNode parent) {
        this.parent = parent;
    }

    /**
     * Gibt die maximale Anzahl von Kindern zurück.
     *
     * @return Die maximale Kindanzahl
     */
    public int getMaxChildren() {
        return maxChildren;
    }

    /**
     * Setzt die maximale Anzahl von Kindern, die dieser Knoten haben kann.
     *
     * @param maxChildren Maximale Kindanzahl (wird auf mindestens 0 begrenzt)
     */
    public void setMaxChildren(int maxChildren) {
        this.maxChildren = Math.max(0, maxChildren);
    }

    // ===== OBJECT-METHODEN =====

    /**
     * Vergleicht zwei StructureNodes basierend auf ihrer ID.
     * Zwei Knoten sind gleich, wenn sie die gleiche ID haben.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof StructureNode other)) return false;
        return id == other.id;
    }

    /**
     * Berechnet den Hash-Code basierend auf der ID.
     * Konsistent mit equals() für korrekte Verwendung in Collections.
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * Gibt eine String-Repräsentation des Knotens zurück.
     * Nützlich für Debugging und Logging.
     */
    @Override
    public String toString() {
        return String.format("StructureNode{id=%d, types=%s, heads=%s, children=%d}",
                id, nodeTypes, headStatus, children.size());
    }
}