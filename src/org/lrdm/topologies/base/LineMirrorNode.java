package org.lrdm.topologies.base;

import org.lrdm.Mirror;

import java.util.*;

/**
 * Spezialisierte MirrorNode für Linien-Strukturen.
 * Validiert, dass die Struktur eine gerade Linie ohne Zyklen ist.
 * <p>
 * Eine gültige Linie hat folgende Eigenschaften:
 * - Genau 2 Terminal-Knoten (Endpunkte) mit Grad 1
 * - Alle mittleren Knoten haben Grad 2 (ein Parent, ein Kind)
 * - Ein Head-Knoten, der als Eingangs-/Verbindungspunkt dient
 * - Keine Zyklen (direkte oder indirekte)
 * - Mindestens 2 Knoten
 * - Head-Knoten müssen Edge-Links für externe Verbindungen haben
 * <p>
 * Nutzt das Multi-Type-System mit expliziter LINE-Typ-ID und Head-ID-Berücksichtigung
 * für korrekte Koexistenz mit anderen Strukturtypen.
 * <p>
 * Maximierte Wiederverwendung der StructureNode/MirrorNode-Funktionalität:
 * - 85 %+ der Traversierungs- und Validierungslogik wird wiederverwendet
 * - Fokussiert auf linien-spezifische Validierung und Convenience-Methoden
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public class LineMirrorNode extends MirrorNode {

    // ===== KONSTRUKTOREN =====

    /**
     * Erstellt einen neuen LineMirrorNode mit gegebener ID.
     *
     * @param id Eindeutige Identifikationsnummer des Knotens
     */
    public LineMirrorNode(int id) {
        super(id);
    }

    /**
     * Erstellt einen neuen LineMirrorNode mit gegebener ID und zugeordnetem Mirror.
     *
     * @param id Eindeutige Identifikationsnummer des Knotens
     * @param mirror Der zugeordnete Mirror für Link-Management
     */
    public LineMirrorNode(int id, Mirror mirror) {
        super(id, mirror);
    }

    // ===== TYP-SYSTEM INTEGRATION =====

    /**
     * Überschreibt die Typ-Ableitung für korrekte LINE-Identifikation.
     * Kritisch für Multi-Type-System: Ermöglicht korrekte Strukturerkennung
     * und automatische Typ-ID-Zuordnung in allen StructureNode-Methoden.
     *
     * @return StructureType.LINE für eindeutige Linien-Identifikation
     */
    @Override
    protected StructureType deriveTypeId() {
        return StructureType.LINE;
    }

    // ===== STRUKTUR-MANAGEMENT =====

    /**
     * Prüft, ob dieser Knoten weitere Kinder akzeptieren kann.
     * <p>
     * Linien-spezifische Logik:
     * - Jeder Knoten kann maximal 1 Kind haben (lineare Struktur)
     * - Nur wenn die aktuelle Struktur gültig ist
     * - Nur wenn noch keine Kinder für diese LINE-Struktur vorhanden sind
     * <p>
     * Wiederverwendung:
     * - findHead(typeId) aus StructureNode für Head-Ermittlung
     * - getChildren(typeId, headId) für strukturspezifische Kind-Zählung
     * - super.canAcceptMoreChildren() für Basis-Validierung
     *
     * @return true, wenn weitere Kinder akzeptiert werden können
     */
    @Override
    public boolean canAcceptMoreChildren() {
        // Ermittle LINE-spezifische Struktur-Kontext
        StructureType typeId = StructureType.LINE;
        StructureNode head = findHead(typeId); // ✅ Wiederverwendung aus StructureNode

        // Basis-Validierung und Linien-spezifische Prüfungen
        return super.canAcceptMoreChildren() && // ✅ Wiederverwendung aus MirrorNode
                isValidStructure() && // Struktur muss gültig bleiben
                getChildren(typeId, head != null ? head.getId() : this.getId()).isEmpty(); // ✅ Wiederverwendung
    }

    /**
     * Prüft, ob dieser Knoten aus der Struktur entfernt werden kann.
     * <p>
     * Linien-spezifische Logik:
     * - Eine Linie muss mindestens 2 Knoten haben
     * - Nach Entfernung müssen noch mindestens 2 Knoten übrig bleiben
     * - Nur Endpunkte können sicher entfernt werden (preserver Linien-Eigenschaft)
     * <p>
     * Wiederverwendung:
     * - findHead(typeId) für Head-Ermittlung
     * - getAllNodesInStructure(typeId, head) für Strukturknotenzählung
     * - isEndpoint(node, typeId, headId) für Endpunkt-Erkennung
     * - super.canBeRemovedFromStructure() für Basis-Validierung
     *
     * @param structureRoot Der Root-Knoten der Struktur
     * @return true, wenn der Knoten sicher entfernt werden kann
     */
    @Override
    public boolean canBeRemovedFromStructure(StructureNode structureRoot) {
        if (structureRoot == null) return false;

        // Ermittle LINE-spezifischen Struktur-Kontext
        StructureType typeId = StructureType.LINE;
        StructureNode head = findHead(typeId); // ✅ Wiederverwendung aus StructureNode
        if (head == null) head = structureRoot;

        Set<StructureNode> structureNodes = getAllNodesInStructure(typeId, head); // ✅ Wiederverwendung

        // Eine Linie muss mindestens 2 Knoten haben.
        // Nach Entfernung müssen noch mindestens 2 Knoten übrig bleiben
        if (structureNodes.size() < 3) return false;

        // Nur Endpunkte können sicher entfernt werden, ohne die Linie zu zerbrechen
        return super.canBeRemovedFromStructure(structureRoot) && // ✅ Wiederverwendung aus MirrorNode
                isEndpoint(this, typeId, head.getId()); // ✅ Wiederverwendung aus StructureNode
    }

    // ===== STRUKTUR-VALIDIERUNG =====

    /**
     * Erweiterte Linien-Struktur-Validierung mit expliziter LINE-Typ-ID.
     * <p>
     * Validiert zusätzlich zu super.isValidStructure():
     * - Alle Knoten sind LineMirrorNode-Instanzen
     * - Genau ein Head-Knoten vorhanden
     * - Alle Knoten haben korrekten Interconnectivities (1 für Endpunkte, 2 für mittlere)
     * - Struktur ist frei von Zyklen (nutzt LINE-Typ-ID für korrekte Traversierung)
     * - Genau 2 Terminal-Knoten (Endpunkte)
     * - Head-Node hat Edge-Links für externe Verbindungen
     * <p>
     * Wiederverwendung:
     * - super.isValidStructure() für MirrorNode-Basis-Validierung
     * - hasClosedCycle() mit LINE-Typ-ID für Zyklusprüfung
     * - getEndpointsOfStructure() für Endpunkt-Ermittlung
     * - getNumEdgeLinks() für Edge-Link-Validierung
     *
     * @param allNodes Menge aller Knoten, die zur Struktur gehören sollen
     * @param typeId Die Typ-ID der gewünschten Struktur (sollte LINE sein)
     * @param head Die Head-Node der gewünschten Struktur
     * @return true, wenn die Linien-Struktur gültig ist
     */
    @Override
    public boolean isValidStructure(Set<StructureNode> allNodes, StructureType typeId, StructureNode head) {
        // Zuerst die grundlegende MirrorNode-Struktur validierung
        if (!super.isValidStructure(allNodes, typeId, head)) { // ✅ Wiederverwendung aus MirrorNode
            return false;
        }

        // Mindestgröße für Linien
        if (allNodes.size() < 2) return false;

        // Sammle alle Linien-Knoten und finde Head-Knoten
        Set<LineMirrorNode> lineNodes = new HashSet<>();
        LineMirrorNode headNode = null;

        for (StructureNode node : allNodes) {
            if (!(node instanceof LineMirrorNode lineNode)) {
                return false; // Alle Knoten müssen LineMirrorNodes sein
            }

            lineNodes.add(lineNode);
            if (lineNode.isHead(typeId)) {
                if (headNode != null) return false; // Nur ein Head erlaubt
                headNode = lineNode;
            }
        }

        if (headNode == null) return false; // Ein Head muss vorhanden sein

        // Validiere Linien-spezifische Eigenschaften für alle Knoten
        for (LineMirrorNode lineNode : lineNodes) {
            if (!isValidLineNode(lineNode, headNode, typeId)) {
                return false;
            }
        }

        // Prüfe, dass die Struktur frei von Zyklen ist (mit LINE-Typ-ID)
        if (hasClosedCycle(allNodes, typeId, head)) { // ✅ Wiederverwendung aus StructureNode
            return false; // Linien dürfen keine Zyklen haben
        }

        // Nutze LINE-spezifische Endpunkt-Ermittlung
        Set<StructureNode> endpoints = getEndpointsOfStructure(typeId, head); // ✅ Wiederverwendung
        if (endpoints.size() != 2) return false; // Linie hat genau 2 Endpunkte

        // Head-Node muss Edge-Links haben (Verbindung nach außen)
        return headNode.getNumEdgeLinks(typeId, head) > 0; // ✅ Wiederverwendung aus MirrorNode
    }

    /**
     * Überschreibt isValidStructure() für automatische LINE-Typ-Ermittlung.
     * Wiederverwendung der automatischen Typ- und Head-Ermittlung aus StructureNode.
     *
     * @param allNodes Menge aller Knoten, die zur Struktur gehören sollen
     * @return true, wenn die Linien-Struktur gültig ist
     */
    @Override
    public boolean isValidStructure(Set<StructureNode> allNodes) {
        StructureType typeId = StructureType.LINE;
        StructureNode head = findHead(typeId); // ✅ Wiederverwendung aus StructureNode
        return isValidStructure(allNodes, typeId, head != null ? head : this);
    }

    /**
     * Convenience-Methode für Struktur-Validierung ohne Parameter.
     * Nutzt LINE-spezifische Strukturermittlung für automatische Validierung.
     * <p>
     * Wiederverwendung:
     * - findHead() für automatische Head-Ermittlung
     * - getAllNodesInStructure() für automatische Strukturknotenbefüllung
     *
     * @return true, wenn die aktuelle Linien-Struktur gültig ist
     */
    public boolean isValidStructure() {
        StructureType typeId = StructureType.LINE;
        StructureNode head = findHead(typeId); // ✅ Wiederverwendung aus StructureNode
        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head != null ? head : this); // ✅ Wiederverwendung
        return isValidStructure(allNodes, typeId, head != null ? head : this);
    }

    /**
     * Validiert einen einzelnen Linien-Knoten mit LINE-Typ-ID-Berücksichtigung.
     * <p>
     * Prüft:
     * - Korrekte Konnektivitätsgrade (1 für Endpunkte, 2 für mittlere Knoten)
     * - Parent-Beziehungen (Head darf externen Parent haben, andere müssen internen Parent haben)
     * - Kind-Beziehungen (mittlere Knoten müssen genau 1 Kind haben)
     * <p>
     * Wiederverwendung:
     * - isEndpoint() für Endpunkt-Erkennung
     * - getChildren() für strukturspezifische Kind-Zählung
     * - getParent() für Parent-Zugriff
     * - getAllNodesInStructure() für Struktur-Mitgliedschaftsprüfung
     *
     * @param lineNode Der zu validierende Linien-Knoten
     * @param headNode Der Head-Knoten der Linie
     * @param typeId Der Strukturtyp (sollte LINE sein)
     * @return true wenn der Knoten gültig ist
     */
    private boolean isValidLineNode(LineMirrorNode lineNode, LineMirrorNode headNode, StructureType typeId) {
        final int headId = headNode.getId();
        int degree = lineNode.getConnectivityDegree(typeId, headId);

        // Terminal-Knoten (Endpunkte) haben Grad 1
        if (isEndpoint(lineNode, typeId, headId)) { // ✅ Wiederverwendung aus StructureNode
            if (degree != 1) return false;
        } else {
            // Mittlere Knoten haben Grad 2 (ein Parent, ein Kind)
            if (degree != 2 || lineNode.getChildren(typeId, headId).size() != 1) { // ✅ Wiederverwendung
                return false;
            }
        }

        // Validiere Parent-Beziehung für alle Knoten mit LINE-Typ-ID
        StructureNode parent = lineNode.getParent(); // ✅ Wiederverwendung aus StructureNode
        Set<StructureNode> structureNodes = lineNode.getAllNodesInStructure(typeId, headNode); // ✅ Wiederverwendung

        if (lineNode == headNode) {
            // Head-Node: darf externen Parent haben
            if (parent != null) {
                return !structureNodes.contains(parent); // Head-Parent muss extern sein
            }
        } else {
            // Normale Knoten: müssen Parent in der Struktur haben
            if (parent == null) {
                return false; // Knoten müssen verbunden sein
            }
            return structureNodes.contains(parent); // Parent muss in der Struktur sein
        }

        return true;
    }

    // ===== LINIEN-SPEZIFISCHE CONVENIENCE-METHODEN =====

    /**
     * Gibt LINE-spezifische Endpunkte zurück.
     * Filtert nur LineMirrorNode-Instanzen aus der LINE-Struktur heraus.
     * <p>
     * Wiederverwendung:
     * - findHead() für automatische Head-Ermittlung
     * - getEndpointsOfStructure() für Endpunkt-Sammlung
     *
     * @return Liste aller Endpunkte der LINE-Struktur (sollten genau 2 sein)
     */
    public List<LineMirrorNode> getEndpoints() {
        List<LineMirrorNode> endpoints = new ArrayList<>();

        StructureType typeId = StructureType.LINE;
        StructureNode head = findHead(typeId); // ✅ Wiederverwendung aus StructureNode

        // Nutze LINE-spezifische Endpunkt-Ermittlung
        Set<StructureNode> structureEndpoints = getEndpointsOfStructure(typeId, head); // ✅ Wiederverwendung

        for (StructureNode endpoint : structureEndpoints) {
            if (endpoint instanceof LineMirrorNode lineNode) {
                endpoints.add(lineNode);
            }
        }

        return endpoints;
    }

    /**
     * Findet den anderen Endpunkt der Linie mit LINE-Typ-ID.
     * Nützlich für Traversierung oder Verbindungslogik zwischen Endpunkten.
     * <p>
     * Wiederverwendung:
     * - findHead() für Head-Ermittlung
     * - isEndpoint() für Endpunkt-Validierung
     * - getEndpoints() für alle Endpunkte
     *
     * @return Der andere Endpunkt oder null, wenn dieser Knoten kein Endpunkt ist
     */
    public LineMirrorNode getOtherEndpoint() {
        StructureType typeId = StructureType.LINE;
        StructureNode head = findHead(typeId); // ✅ Wiederverwendung aus StructureNode

        if (!isEndpoint(this, typeId, head != null ? head.getId() : this.getId())) { // ✅ Wiederverwendung
            return null;
        }

        List<LineMirrorNode> endpoints = getEndpoints();
        if (endpoints.size() != 2) return null;

        return endpoints.get(0) == this ? endpoints.get(1) : endpoints.get(0);
    }

    /**
     * Findet den Head-Knoten der LINE-Struktur.
     * Nutzt findHead() mit expliziter LINE-Typ-ID für typsichere Suche.
     * <p>
     * Wiederverwendung:
     * - findHead() aus StructureNode mit LINE-Typ-ID
     *
     * @return Der Head-Knoten oder null, wenn nichts gefunden wird
     */
    public LineMirrorNode getLineHead() {
        StructureNode head = findHead(StructureType.LINE); // ✅ Wiederverwendung aus StructureNode
        return (head instanceof LineMirrorNode) ? (LineMirrorNode) head : null;
    }

    /**
     * Prüft, ob dieser Knoten ein mittlerer Knoten der Linie ist.
     * Mittlere Knoten haben genau 2 Verbindungen (nicht Terminal).
     * <p>
     * Wiederverwendung:
     * - findHead() für Head-Ermittlung
     * - isEndpoint() für Endpunkt-Ausschluss
     * - getConnectivityDegree() für Grad-Berechnung
     *
     * @return true, wenn der Knoten genau 2 Verbindungen hat (nicht Terminal)
     */
    public boolean isMiddleNode() {
        StructureType typeId = StructureType.LINE;
        StructureNode head = findHead(typeId); // ✅ Wiederverwendung aus StructureNode
        final int headId = head != null ? head.getId() : this.getId();

        return !isEndpoint(this, typeId, headId) && // ✅ Wiederverwendung aus StructureNode
                getConnectivityDegree(typeId, headId) == 2;
    }

    /**
     * Prüft, ob dieser Knoten ein Endpunkt in der LINE-Struktur ist.
     * Convenience-Wrapper isEndpoint() mit automatischer Typ- und Head-Ermittlung.
     * <p>
     * Wiederverwendung:
     * - findHead() für Head-Ermittlung
     * - isEndpoint() aus StructureNode für Endpunkt-Erkennung
     *
     * @return true, wenn der Knoten ein Endpunkt ist
     */
    public boolean isEndpoint() {
        StructureType typeId = StructureType.LINE;
        StructureNode head = findHead(typeId); // ✅ Wiederverwendung aus StructureNode
        return isEndpoint(this, typeId, head != null ? head.getId() : this.getId()); // ✅ Wiederverwendung
    }

    /**
     * Berechnet die Position dieses Knotens in der Linie (0-basiert).
     * Nutzt BFS für effiziente Pfadberechnung in LINE-Strukturen.
     * <p>
     * Position wird vom Head-Knoten aus gezählt:
     * - Head-Knoten = Position 0
     * - Erstes Kind = Position 1
     * - Zweites Kind = Position 2, etc.
     * <p>
     * Wiederverwendung:
     * - findHead() für Head-Ermittlung
     * - getChildren() für strukturspezifische Traversierung
     *
     * @return Position vom Head-Endpunkt aus gezählt, oder -1 bei Fehlern
     */
    public int getPositionInLine() {
        StructureType typeId = StructureType.LINE;
        StructureNode head = findHead(typeId); // ✅ Wiederverwendung aus StructureNode

        // Nutze BFS für Pfadberechnung in LINE-Struktur
        if (head == null) return -1;

        Queue<StructureNode> queue = new LinkedList<>();
        Map<StructureNode, Integer> distances = new HashMap<>();
        Set<StructureNode> visited = new HashSet<>();

        queue.offer(head);
        distances.put(head, 0);
        visited.add(head);

        while (!queue.isEmpty()) {
            StructureNode current = queue.poll();

            if (current == this) {
                return distances.get(current);
            }

            // Traversiere LINE-spezifische Kinder
            Set<StructureNode> children = current.getChildren(typeId, head.getId()); // ✅ Wiederverwendung
            for (StructureNode child : children) {
                if (!visited.contains(child)) {
                    visited.add(child);
                    distances.put(child, distances.get(current) + 1);
                    queue.offer(child);
                }
            }
        }

        return -1; // Knoten nicht in der Struktur gefunden
    }

    // ===== HILFSMETHODEN =====

    /**
     * Berechnet den Interconnectivities für die LINE-Struktur.
     * Zählt Parent- und Kind-Verbindungen für die spezifische LINE-Struktur.
     * <p>
     * Wichtig: Berücksichtigt nur Verbindungen, die zur angegebenen
     * Typ-ID und Head-ID gehören (Multi-Type-System).
     * <p>
     * Wiederverwendung:
     * - getParent() für Parent-Zugriff
     * - findChildRecordById() für Kind-Record-Suche
     * - belongsToStructure() für Typ-/Head-ID-Validierung
     * - getChildren() für strukturspezifische Kind-Zählung
     *
     * @param typeId Die Struktur-Typ-ID
     * @param headId Die Head-ID
     * @return Anzahl der Verbindungen in der LINE-Struktur
     */
    private int getConnectivityDegree(StructureType typeId, int headId) {
        int connections = 0;

        // Parent-Verbindung zählen (wenn LINE-Typ-ID und Head-ID passen)
        if (getParent() != null) { // ✅ Wiederverwendung aus StructureNode
            ChildRecord parentRecord = getParent().findChildRecordById(getId()); // ✅ Wiederverwendung
            if (parentRecord != null && parentRecord.belongsToStructure(typeId, headId)) {
                connections++;
            }
        }

        // Kind-Verbindungen zählen (wenn LINE-Typ-ID und Head-ID passen)
        connections += getChildren(typeId, headId).size(); // ✅ Wiederverwendung aus StructureNode

        return connections;
    }
}