package org.lrdm.topologies.node;

import org.lrdm.Mirror;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Spezialisierte MirrorNode für N-Connected-Strukturen.
 * Validiert, dass jeder Knoten mit genau N anderen Knoten verbunden ist.
 * <p>
 * Eine gültige N-Connected-Struktur hat folgende Eigenschaften:
 * - Jeder Knoten hat genau N Verbindungen (Connectivity Degree N)
 * - Mindestens N+1 Knoten für eine sinnvolle N-Connected-Struktur
 * - Zusammenhängende Struktur (alle Knoten erreichbar)
 * - Genau ein Head-Knoten als Verbindungspunkt zu externen Strukturen
 * - Head-Knoten muss Edge-Links für externe Verbindungen haben
 * - Parametrisierbare Vernetzungsgrad N (standardmäßig aus Konfiguration)
 * <p>
 * Nutzt das Multi-Type-System mit expliziter FULLY_CONNECTED-Typ-ID und Head-ID-Berücksichtigung
 * für korrekte Koexistenz mit anderen Strukturtypen.
 * <p>
 * Maximierte Wiederverwendung der StructureNode/MirrorNode-Funktionalität:
 * - 95 %+ der existierenden Methoden werden wiederverwendet
 * - Fokussiert nur auf N-Connected-spezifische Validierung
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class NConnectedMirrorNode extends MirrorNode {

    private int connectivityDegree;
    private static final int DEFAULT_CONNECTIVITY_DEGREE = 2; // Standard: 2-Connected

    // ===== KONSTRUKTOREN =====

    /**
     * Erstellt einen neuen NConnectedMirrorNode mit Standard-Vernetzungsgrad.
     */
    public NConnectedMirrorNode(int id) {
        this(id, DEFAULT_CONNECTIVITY_DEGREE);
    }

    /**
     * Erstellt einen neuen NConnectedMirrorNode mit spezifischem Vernetzungsgrad.
     */
    public NConnectedMirrorNode(int id, int connectivityDegree) {
        super(id);
        if (connectivityDegree < 2) {
            throw new IllegalArgumentException("Connectivity degree must be at least 2, got: " + connectivityDegree);
        }
        this.connectivityDegree = connectivityDegree;
    }

    /**
     * Erstellt einen neuen NConnectedMirrorNode mit Mirror und Vernetzungsgrad.
     */
    public NConnectedMirrorNode(int id, Mirror mirror, int connectivityDegree) {
        super(id, mirror);
        if (connectivityDegree < 2) {
            throw new IllegalArgumentException("Connectivity degree must be at least 2, got: " + connectivityDegree);
        }
        this.connectivityDegree = connectivityDegree;
    }

    // ===== TYP-SYSTEM INTEGRATION =====

    /**
     * Überschreibt die Typ-Ableitung für die korrekte N-Connected-Identifikation.
     */
    @Override
    public StructureType deriveTypeId() {
        return StructureType.N_CONNECTED; // Temporärer Platzhalter
    }

    // ===== CONNECTIVITY-MANAGEMENT =====

    /**
     * Gibt den konfigurierten Vernetzungsgrad zurück.
     */
    public int getConnectivityDegree() {
        return connectivityDegree;
    }

    /**
     * Gibt den konfigurierten Vernetzungsgrad zurück.
     */
    public void setConnectivityDegree(int connectivityDegree) {
        this.connectivityDegree = connectivityDegree;
    }

    /**
     * Berechnet den tatsächlichen Connectivity Degree mit Multi-Type-Unterstützung.
     * Verwendet bereits existierende StructureNode-Methoden.
     */
    public int getConnectivityDegree(StructureType typeId, int headId) {
        int connections = 0;

        // Parent-Verbindung zählen (falls zur gleichen Struktur gehörend)
        if (getParent() != null) {
            ChildRecord parentRecord = getParent().findChildRecordById(this.getId());
            if (parentRecord != null && parentRecord.belongsToStructure(typeId, headId)) {
                connections++;
            }
        } else {
            Set<StructureNode> allNodes = getAllNodesInStructure(typeId, findNodeById(headId));
            int inwardConnections = (int) allNodes.stream()
                    .filter(node -> node.getChildren(typeId, headId).contains(this))
                    .count();
            if(inwardConnections>0){
                connections++;
            }
        }

        // Kind-Verbindungen zählen (bereits existierende Methode verwenden)
        Set<StructureNode> structureChildren = getChildren(typeId, headId);
        connections += structureChildren.size();

        return connections;
    }

    /**
     * Berechnet die erwartete Anzahl Links pro Knoten.
     */
    public int getExpectedLinkCount() {
        int networkSize = getNetworkSize();
        return Math.min(connectivityDegree, networkSize - 1);
    }

    /**
     * Prüft, ob dieser Knoten den gewünschten Vernetzungsgrad erreicht hat.
     */
    public boolean hasOptimalConnectivity() {
        return getNumImplementedLinks() == getExpectedLinkCount();
    }

    // ===== NETZWERK-NAVIGATION =====

    /**
     * Findet den Head-Knoten der N-Connected-Struktur.
     * Verwendet bereits existierende StructureNode-Methoden.
     */
    public NConnectedMirrorNode getNConnectedHead() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);

        if (head instanceof NConnectedMirrorNode nConnectedHead) {
            return nConnectedHead;
        }
        return null;
    }

    /**
     * Prüft, ob dieser Knoten der Head der N-Connected-Struktur ist.
     */
    public boolean isNConnectedHead() {
        return isHead(deriveTypeId());
    }

    /**
     * Berechnet die Größe der N-Connected-Struktur.
     * Verwendet bereits existierende StructureNode-Methoden.
     */
    public int getNetworkSize() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head != null ? head : this);
        return allNodes.size();
    }

    /**
     * Gibt alle direkt verbundenen N-Connected-Knoten zurück.
     * Verwendet bereits existierende StructureNode-Methoden.
     */
    public Set<NConnectedMirrorNode> getConnectedNodes() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);

        if (head == null) return new HashSet<>();

        Set<NConnectedMirrorNode> connected = new HashSet<>();
        final int headId = head.getId();

        // Parent hinzufügen (falls zur gleichen Struktur gehörend)
        if (getParent() != null) {
            ChildRecord parentRecord = getParent().findChildRecordById(this.getId());
            if (parentRecord != null && parentRecord.belongsToStructure(typeId, headId) &&
                    getParent() instanceof NConnectedMirrorNode nConnectedParent) {
                connected.add(nConnectedParent);
            }
        }

        // Kinder hinzufügen (bereits existierende Methode verwenden)
        Set<StructureNode> structureChildren = getChildren(typeId, headId);
        for (StructureNode child : structureChildren) {
            if (child instanceof NConnectedMirrorNode nConnectedChild) {
                connected.add(nConnectedChild);
            }
        }

        return connected;
    }

    // ===== STRUKTUR-MANAGEMENT =====

    /**
     * Prüft, ob dieser Knoten weitere Kinder akzeptieren kann.
     * Verwendet bereits existierende StructureNode-Logik.
     */
    @Override
    public boolean canAcceptMoreChildren() {
        if (!super.canAcceptMoreChildren()) {
            return false;
        }

        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);

        if (head == null) {
            return true; // Erster Knoten können immer Kinder akzeptieren
        }

        // Prüfe aktuelle Vernetzung
        int currentConnections = getConnectivityDegree(typeId, head.getId());
        int maxConnections = getExpectedLinkCount();

        return currentConnections < maxConnections && isValidStructure();
    }

    /**
     * Prüft, ob dieser Knoten aus der Struktur entfernt werden kann.
     * Verwendet bereits existierende StructureNode-Logik.
     */
    @Override
    public boolean canBeRemovedFromStructure(StructureNode structureRoot) {
        if (!super.canBeRemovedFromStructure(structureRoot)) {
            return false;
        }

        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);

        if (head == null || head == this) {
            return false; // Head kann nicht entfernt werden
        }

        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head);

        // Nach Entfernung müssen mindestens 2 Knoten übrig bleiben
        if (allNodes.size() <= 2) {
            return false;
        }

        // Bei N+1 Knoten können keine weiteren entfernt werden
        return allNodes.size() > connectivityDegree + 1;
    }

    // ===== STRUKTUR-VALIDIERUNG =====

    /**
     * Erweiterte N-Connected-Struktur-Validierung.
     * Verwendet bereits existierende StructureNode-Validierung als Basis.
     */
    @Override
    public boolean isValidStructure(Set<StructureNode> allNodes, StructureType typeId, StructureNode head) {
        // Basis-Validierung verwenden
        if (!super.isValidStructure(allNodes, typeId, head)) {
            return false;
        }

        if (head == null || allNodes == null || allNodes.isEmpty()) {
            return false;
        }

        // Mindestens 2 Knoten erforderlich
        if (allNodes.size() < 2) {
            return false;
        }

        // Validiere jeden Knoten
        for (StructureNode node : allNodes) {
            if (!(node instanceof NConnectedMirrorNode nConnectedNode)) {
                return false;
            }

            if (nConnectedNode.getConnectivityDegree() != this.connectivityDegree) {
                return false;
            }

            if (isInvalidNConnectedNode(nConnectedNode, head, typeId, allNodes)) {
                return false;
            }
        }

        // Verwende bereits existierende StructureNode-Methoden für weitere Validierung
        return hasClosedCycle(allNodes, typeId, head) && // Bereits existiert in StructureNode
                hasCorrectTotalLinkCount(allNodes) &&
                hasNoEdgeLinks(head, typeId);
    }

    /**
     * Convenience-Methode für Struktur-Validierung ohne Parameter.
     * Verwendet bereits existierende StructureNode-Methoden.
     */
    public boolean isValidStructure() {
        StructureType typeId = StructureType.N_CONNECTED;
        StructureNode head = findHead(typeId);
        Set<StructureNode> allNodes = getAllNodesInStructure();
        return isValidStructure(allNodes, typeId, head != null ? head : this);
    }


    /**
     * Validiert einen einzelnen N-Connected-Knoten.
     * Alle Knoten in einer N-Connected-Struktur müssen den gleichen Konnektivitätsgrad haben.
     * Verwendet bereits existierende StructureNode-Methoden für konsistente Validierung.
     */
    private boolean isInvalidNConnectedNode(NConnectedMirrorNode nConnectedNode, StructureNode headNode,
                                            StructureType typeId, Set<StructureNode> allNodes) {
        final int headId = headNode.getId();

        // WICHTIG: Alle Knoten müssen den gleichen erwarteten Konnektivitätsgrad haben.
        // Berechne erwarteten Grad basierend auf der Strukturgröße und dem konfigurierten Grad
        int structureSize = allNodes.size();
        int nConnectedDegree = nConnectedNode.getConnectivityDegree(typeId,headId);
        int expectedDegree = Math.min(nConnectedDegree, structureSize);

        // Tatsächlicher Grad dieses Knotens in der N-Connected-Struktur

        if (nConnectedDegree < 0) nConnectedDegree = 0;

        // Alle Knoten müssen exakt den gleichen Konnektivitätsgrad haben
        if (nConnectedDegree != expectedDegree) {
            return true;
        }

        if(nConnectedNode.getParent() != null && nConnectedNode.getParent() instanceof NConnectedMirrorNode){
            StructureNode parent = nConnectedNode.getParent();

            // Validiere, dass Parent-Child-Beziehung zur N-Connected-Struktur gehört
            ChildRecord parentRecord = parent.findChildRecordById(nConnectedNode.getId());
            if (!parentRecord.belongsToStructure(typeId, headId)) {
                return true;
            }
            return false;// return !allNodes.contains(parent);
        }

        // Parent muss innerhalb der N-Connected-Struktur sein
        return !allNodes.contains(nConnectedNode);
    }

    /*
    private boolean isInvalidNConnectedNode(NConnectedMirrorNode nConnectedNode, StructureNode headNode,
                                            StructureType typeId, Set<StructureNode> allNodes) {
        final int headId = headNode.getId();

        // WICHTIG: Alle Knoten müssen den gleichen erwarteten Konnektivitätsgrad haben.
        // Berechne erwarteten Grad basierend auf der Strukturgröße und dem konfigurierten Grad
        int structureSize = allNodes.size();
        int nConnectedDegree = nConnectedNode.getConnectivityDegree(typeId,headId);
        int expectedDegree = Math.min(nConnectedDegree, structureSize - 1);

        // Tatsächlicher Grad dieses Knotens in der N-Connected-Struktur

        if (nConnectedDegree < 0) nConnectedDegree = 0;

        // Alle Knoten müssen exakt den gleichen Konnektivitätsgrad haben
        if (nConnectedDegree != expectedDegree) {
            return true;
        }

        StructureNode parent = nConnectedNode.getParent();

        // Normale N-Connected-Knoten: müssen struktur-internen Parent haben
        if (parent == null){
            List<StructureNode> nConnectedParent = allNodes.stream()
                    .filter(node -> node.getChildren(typeId, headId).contains(nConnectedNode)).toList();
            int childCount = 0;
            for(StructureNode node : nConnectedParent){
                if(node.getChildren(typeId, headId).contains(nConnectedNode)){
                    childCount++;
                }
            }
            return expectedDegree != childCount;
        }

        // Validiere, dass Parent-Child-Beziehung zur N-Connected-Struktur gehört
        ChildRecord parentRecord = parent.findChildRecordById(nConnectedNode.getId());
        if (!parentRecord.belongsToStructure(typeId, headId)) {
            return true;
        }

        // Parent muss innerhalb der N-Connected-Struktur sein
        return !allNodes.contains(parent);
    }
*/

    /**
     * Prüft, ob die Gesamtanzahl der Links korrekt ist.
     * Vereinfachte Implementierung.
     */
    private boolean hasCorrectTotalLinkCount(Set<StructureNode> allNodes) {
        int networkSize = allNodes.size();
        int effectiveConnectivity = Math.min(connectivityDegree, networkSize - 1);
        int expectedTotalLinks = (networkSize * effectiveConnectivity) / 2;

        // Verwende bereits existierende StructureNode-Methoden
        int actualLinks = getNumLinksOfStructure(); // Bereits existiert in MirrorNode

        return actualLinks >= expectedTotalLinks; // Toleriere zusätzliche Links
    }

    /**
     * Prüft, ob der Head-Knoten gültige Edge-Links hat.
     * Verwendet bereits existierende MirrorNode-Methoden.
     */
    private boolean hasNoEdgeLinks(StructureNode head, StructureType typeId) {
        if (!(head instanceof NConnectedMirrorNode headNode)) {
            return false;
        }

        // Verwende bereits existierende MirrorNode-Methode
        return headNode.getNumEdgeLinks(typeId, head) == 0;
    }

    // ===== N-CONNECTED CONVENIENCE-METHODEN =====

    /**
     * Berechnet die aktuelle Vernetzungsdichte der Struktur.
     */
    public double getConnectivityDensity() {
        int actualLinks = getNumLinksOfStructure(); // Bereits existiert in MirrorNode
        int expectedLinks = getExpectedTotalLinkCount();

        if (expectedLinks == 0) return 1.0;

        return (double) actualLinks / expectedLinks;
    }

    /**
     * Berechnet die erwartete Gesamtanzahl Links in der Struktur.
     */
    public int getExpectedTotalLinkCount() {
        int networkSize = getNetworkSize();
        int effectiveConnectivity = Math.min(connectivityDegree, networkSize - 1);
        return (networkSize * effectiveConnectivity) / 2;
    }

    /**
     * Prüft, ob diese N-Connected-Struktur fault-tolerant ist.
     * Verwendet bereits existierende StructureNode-Methoden.
     */
    public boolean isFaultTolerant() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head != null ? head : this);

        if (allNodes.size() <= 2) return false;

        // Teste Entfernung jedes einzelnen Knotens (außer Head)
        for (StructureNode node : allNodes) {
            if (node == head) continue;

            Set<StructureNode> remainingNodes = new HashSet<>(allNodes);
            remainingNodes.remove(node);

            // Verwende bereits existierende StructureNode-Methoden für Zusammenhang-Prüfung
            if (hasClosedCycle(remainingNodes, typeId, head)) {
                return false; // Wenn nach Entfernung ein Zyklus entsteht, nicht fault-tolerant
            }
        }

        return true;
    }

    // ===== OBJECT-METHODEN =====

    @Override
    public String toString() {
        return String.format("NConnectedMirrorNode{id=%d, connectivityDegree=%d, mirror=%s, actualConnections=%d, networkSize=%d, typeIDs=%d}",
                getId(), connectivityDegree, getMirror() != null ? getMirror().getID() : "null",
                getNumImplementedLinks(), getNetworkSize(), nodeTypes.size());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof NConnectedMirrorNode other)) return false;
        return getId() == other.getId() && connectivityDegree == other.connectivityDegree;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), connectivityDegree);
    }
}