package org.lrdm.topologies.base;

import org.lrdm.Mirror;

import java.util.*;

/**
 * Spezialisierte MirrorNode für Linien-Strukturen.
 * Validiert, dass die Struktur eine gerade Linie ohne Zyklen ist.
 * Nutzt das Multi-Type-System mit expliziter LINE-Typ-ID und Head-ID-Berücksichtigung.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public class LineMirrorNode extends MirrorNode {

    public LineMirrorNode(int id) {
        super(id);
    }

    public LineMirrorNode(int id, Mirror mirror) {
        super(id, mirror);
    }

    /**
     * Überschreibt die Typ-Ableitung für korrekte LINE-Identifikation.
     * Kritisch für Multi-Type-System: Ermöglicht korrekte Strukturerkennung.
     */
    @Override
    protected StructureType deriveTypeId() {
        return StructureType.LINE;
    }

    @Override
    public boolean canAcceptMoreChildren() {
        // In einer Linie kann jeder Knoten maximal 1 Kind haben.
        // Nutze LINE-spezifische Struktur-Ermittlung
        StructureType typeId = StructureType.LINE;
        StructureNode head = findHead(typeId);

        return super.canAcceptMoreChildren() &&
                isValidStructure(getAllNodesInStructure(typeId, head), typeId, head) &&
                getChildren(typeId, head != null ? head.getId() : this.getId()).isEmpty();
    }

    @Override
    public boolean canBeRemovedFromStructure(StructureNode structureRoot) {
        if (structureRoot == null) return false;

        // Verwende LINE-spezifische Strukturermittlung
        StructureType typeId = StructureType.LINE;
        StructureNode head = findHead(typeId);
        if (head == null) head = structureRoot;

        Set<StructureNode> structureNodes = getAllNodesInStructure(typeId, head);

        // Eine Linie muss mindestens 2 Knoten haben.
        // Nach Entfernung müssen noch mindestens 2 Knoten übrig bleiben
        if (structureNodes.size() < 3) return false;

        // Nur Endpunkte können sicher entfernt werden, ohne die Linie zu zerbrechen
        return super.canBeRemovedFromStructure(structureRoot) &&
                isEndpoint(this, typeId, head.getId());
    }

    /**
     * Erweiterte Linien-Struktur-Validierung mit expliziter LINE-Typ-ID.
     * Zusätzlich zu super.isValidStructure:
     * - Alle MirrorNodes haben maximal einen Parent und ein Kind
     * - Head-Node darf einen externen Parent haben
     * - Head-Node muss mindestens einen Edge-Link haben (Verbindung nach außen)
     * - Struktur ist frei von Zyklen
     * - Genau 2 Terminal-Knoten (Endpunkte)
     */
    @Override
    public boolean isValidStructure(Set<StructureNode> allNodes, StructureType typeId, StructureNode head) {
        // Zuerst die grundlegende MirrorNode-Struktur validierung
        if (!super.isValidStructure(allNodes, typeId, head)) {
            return false;
        }

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

        // Validiere Linien-spezifische Eigenschaften
        for (LineMirrorNode lineNode : lineNodes) {
            if (!isValidLineNode(lineNode, headNode, typeId)) {
                return false;
            }
        }

        // Prüfe, dass die Struktur frei von Zyklen ist (mit LINE-Typ-ID)
        if (hasClosedCycle(allNodes, typeId, head)) {
            return false; // Linien dürfen keine Zyklen haben
        }

        // Nutze LINE-spezifische Endpunkt-Ermittlung
        Set<StructureNode> endpoints = getEndpointsOfStructure(typeId, head);
        if (endpoints.size() != 2) return false; // Linie hat genau 2 Endpunkte

        // Head-Node muss Edge-Links haben (Verbindung nach außen)
        return headNode.getNumEdgeLinks(typeId, head) > 0; // Head muss mit externen Strukturen verbunden sein
    }

    /**
     * Überschreibt isValidStructure() für automatische LINE-Typ-Ermittlung.
     */
    @Override
    public boolean isValidStructure(Set<StructureNode> allNodes) {
        StructureType typeId = StructureType.LINE;
        StructureNode head = findHead(typeId);
        return isValidStructure(allNodes, typeId, head != null ? head : this);
    }

    /**
     * Convenience-Methode für Struktur validierung ohne Parameter.
     * Nutzt LINE-spezifische Strukturermittlung.
     */
    public boolean isValidStructure() {
        StructureType typeId = StructureType.LINE;
        StructureNode head = findHead(typeId);
        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head != null ? head : this);
        return isValidStructure(allNodes, typeId, head != null ? head : this);
    }

    /**
     * Validiert einen einzelnen Linien-Knoten mit LINE-Typ-ID-Berücksichtigung.
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
        if (isEndpoint(lineNode, typeId, headId)) {
            if (degree != 1) return false;
        } else {
            // Mittlere Knoten haben Grad 2 (ein Parent, ein Kind)
            if (degree != 2 || lineNode.getChildren(typeId, headId).size() != 1) {
                return false;
            }
        }

        // Validiere Parent-Beziehung für alle Knoten mit LINE-Typ-ID
        StructureNode parent = lineNode.getParent();
        Set<StructureNode> structureNodes = lineNode.getAllNodesInStructure(typeId, headNode);

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

    /**
     * Gibt LINE-spezifische Endpunkte zurück.
     * Filtert nur LineMirrorNode-Instanzen aus der LINE-Struktur heraus.
     */
    public List<LineMirrorNode> getEndpoints() {
        List<LineMirrorNode> endpoints = new ArrayList<>();

        StructureType typeId = StructureType.LINE;
        StructureNode head = findHead(typeId);

        // Nutze LINE-spezifische Endpunkt-Ermittlung
        Set<StructureNode> structureEndpoints = getEndpointsOfStructure(typeId, head);

        for (StructureNode endpoint : structureEndpoints) {
            if (endpoint instanceof LineMirrorNode lineNode) {
                endpoints.add(lineNode);
            }
        }

        return endpoints;
    }

    /**
     * Findet den anderen Endpunkt der Linie mit LINE-Typ-ID.
     *
     * @return Der andere Endpunkt oder null, wenn dieser Knoten kein Endpunkt ist
     */
    public LineMirrorNode getOtherEndpoint() {
        StructureType typeId = StructureType.LINE;
        StructureNode head = findHead(typeId);

        if (!isEndpoint(this, typeId, head != null ? head.getId() : this.getId())) {
            return null;
        }

        List<LineMirrorNode> endpoints = getEndpoints();
        if (endpoints.size() != 2) return null;

        return endpoints.get(0) == this ? endpoints.get(1) : endpoints.get(0);
    }

    /**
     * Findet den Head-Knoten der LINE-Struktur.
     * Nutzt findHead() mit expliziter LINE-Typ-ID.
     *
     * @return Der Head-Knoten oder null, wenn nichts gefunden wird
     */
    public LineMirrorNode getLineHead() {
        StructureNode head = findHead(StructureType.LINE);
        return (head instanceof LineMirrorNode) ? (LineMirrorNode) head : null;
    }

    /**
     * Prüft, ob dieser Knoten ein mittlerer Knoten der Linie ist.
     * Nutzt LINE-spezifische Endpunkt-Erkennung.
     *
     * @return true, wenn der Knoten genau 2 Verbindungen hat (nicht Terminal)
     */
    public boolean isMiddleNode() {
        StructureType typeId = StructureType.LINE;
        StructureNode head = findHead(typeId);
        final int headId = head != null ? head.getId() : this.getId();

        return !isEndpoint(this, typeId, headId) &&
                getConnectivityDegree(typeId, headId) == 2;
    }

    /**
     * Prüft, ob dieser Knoten ein Endpunkt in der LINE-Struktur ist.
     *
     * @return true, wenn der Knoten ein Endpunkt ist
     */
    public boolean isEndpoint() {
        StructureType typeId = StructureType.LINE;
        StructureNode head = findHead(typeId);
        return isEndpoint(this, typeId, head != null ? head.getId() : this.getId());
    }

    /**
     * Berechnet die Position dieses Knotens in der Linie (0-basiert).
     * Nutzt LINE-spezifische Pfadberechnung.
     *
     * @return Position vom Head-Endpunkt aus gezählt, oder -1 bei Fehlern
     */
    public int getPositionInLine() {
        StructureType typeId = StructureType.LINE;
        StructureNode head = findHead(typeId);

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
            Set<StructureNode> children = current.getChildren(typeId, head.getId());
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

    /**
     * Berechnet den Konnektivitätsgrad für die LINE-Struktur.
     * Zählt Parent- und Kind-Verbindungen für die spezifische LINE-Struktur.
     *
     * @param typeId Die Struktur-Typ-ID
     * @param headId Die Head-ID
     * @return Anzahl der Verbindungen in der LINE-Struktur
     */
    private int getConnectivityDegree(StructureType typeId, int headId) {
        int connections = 0;

        // Parent-Verbindung zählen (wenn LINE-Typ-ID und Head-ID passen)
        if (getParent() != null) {
            ChildRecord parentRecord = getParent().findChildRecordById(getId());
            if (parentRecord != null && parentRecord.belongsToStructure(typeId, headId)) {
                connections++;
            }
        }

        // Kind-Verbindungen zählen (wenn LINE-Typ-ID und Head-ID passen)
        connections += getChildren(typeId, headId).size();

        return connections;
    }
}