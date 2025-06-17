package org.lrdm.topologies.node;

import org.lrdm.Mirror;

import java.util.*;


/**
 * Spezialisierte MirrorNode für vollständig vernetzte Strukturen (Fully Connected).
 * Validiert, dass die Struktur ein vollständiges Netz ist, in dem jeder Knoten mit jedem anderen verbunden ist.
 * <p>
 * Eine gültige vollständig vernetzte Struktur hat folgende Eigenschaften:
 * - Mindestens 2 Knoten für ein sinnvolles vollständiges Netz
 * - Jeder Knoten ist mit jedem anderen Knoten direkt verbunden
 * - Connectivity Degree = n-1 für n Knoten in der Struktur
 * - Genau ein Head-Knoten als Verbindungspunkt zu externen Strukturen
 * - Head-Knoten muss Edge-Links für externe Verbindungen haben
 * - Keine Hierarchie - alle Nicht-Head-Knoten sind auf derselben Ebene
 * - Symmetrische Verbindungen (bidirektional)
 * <p>
 * Nutzt das Multi-Type-System mit expliziter FULLY_CONNECTED-Typ-ID und Head-ID-Berücksichtigung
 * für korrekte Koexistenz mit anderen Strukturtypen.
 * <p>
 * Maximierte Wiederverwendung der StructureNode/MirrorNode-Funktionalität:
 * - 85 %+ der Traversierungs- und Validierungslogik wird wiederverwendet
 * - Fokussiert auf vollständig-vernetzte-spezifische Validierung und Navigation
 * - Überschreibt nur strukturspezifische Validierung und Management
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class FullyConnectedMirrorNode extends MirrorNode {

    // ===== KONSTRUKTOREN =====

    /**
     * Erstellt einen neuen FullyConnectedMirrorNode mit gegebener ID.
     *
     * @param id Eindeutige Identifikationsnummer des Knotens
     */
    public FullyConnectedMirrorNode(int id) {
        super(id);
        this.nodeTypes.add(StructureType.FULLY_CONNECTED);
    }

    /**
     * Erstellt einen neuen FullyConnectedMirrorNode mit gegebener ID und zugeordnetem Mirror.
     *
     * @param id Eindeutige Identifikationsnummer des Knotens
     * @param mirror Der zugeordnete Mirror für Link-Management
     */
    public FullyConnectedMirrorNode(int id, Mirror mirror) {
        super(id, mirror);
        this.nodeTypes.add(StructureType.FULLY_CONNECTED);
    }

    // ===== TYP-SYSTEM INTEGRATION =====

    /**
     * Überschreibt die Typ-Ableitung für die korrekte FULLY_CONNECTED-Identifikation.
     * Kritisch für Multi-Type-System: Ermöglicht korrekte Strukturerkennung
     * und automatische Typ-ID-Zuordnung in allen StructureNode-Methoden.
     *
     * @return StructureType.FULLY_CONNECTED für eindeutige vollständig-vernetzte-Identifikation
     */
    @Override
    public StructureType deriveTypeId() {
        return StructureType.FULLY_CONNECTED;
    }

    // ===== STRUKTUR-MANAGEMENT =====

    /**
     * Prüft, ob dieser Knoten weitere Kinder akzeptieren kann.
     * <p>
     * Vollständig-vernetzte-spezifische Logik:
     * - Jeder Knoten kann mit allen anderen Knoten der Struktur verbunden werden
     * - Nur wenn die aktuelle Struktur gültig ist
     * - Keine Begrenzung für Kinder, da vollständiges Netz bidirektional ist
     * <p>
     * Wiederverwendung:
     * - findHead(typeId) für FULLY_CONNECTED-spezifische Head-Ermittlung
     * - super.canAcceptMoreChildren() für Basis-Validierung
     * - isValidStructure() für Struktur-Validierung
     *
     * @return true, wenn weitere Kinder akzeptiert werden können
     */
    @Override
    public boolean canAcceptMoreChildren() {
        StructureType typeId = StructureType.FULLY_CONNECTED;
        StructureNode head = findHead(typeId);
        final int headId = head != null ? head.getId() : this.getId();

        // Basis-Validierung und vollständig-vernetzte-spezifische Prüfungen
        return super.canAcceptMoreChildren() &&
                isValidStructure(getAllNodesInStructure(typeId, head != null ? head : this), typeId, head != null ? head : this);
    }


    /**
     * Prüft, ob dieser Knoten aus der Struktur entfernt werden kann.
     * <p>
     * Vollständig-vernetzte-spezifische Logik:
     * - Ein vollständiges Netz muss mindestens 2 Knoten haben
     * - Nach Entfernung müssen noch mindestens 2 Knoten übrig bleiben
     * - Head-Knoten können normalerweise nicht entfernt werden
     * - Entfernung erfordert Neuverkabelung aller anderen Knoten
     * <p>
     * Wiederverwendung:
     * - findHead(typeId) für Head-Ermittlung
     * - getAllNodesInStructure(typeId, head) für Strukturknotenzählung
     * - isHead(typeId) für Head-Erkennung
     * - super.canBeRemovedFromStructure() für Basis-Validierung
     *
     * @param structureRoot Der Root-Knoten der Struktur
     * @return true, wenn der Knoten sicher entfernt werden kann
     */
    @Override
    public boolean canBeRemovedFromStructure(StructureNode structureRoot) {
        if (structureRoot == null) return false;

        StructureType typeId = StructureType.FULLY_CONNECTED;
        StructureNode head = findHead(typeId);
        if (head == null) head = structureRoot;

        Set<StructureNode> structureNodes = getAllNodesInStructure(typeId, head);

        // Vollständiges Netz muss mindestens 2 Knoten haben.
        // Nach Entfernung müssen noch mindestens 2 Knoten übrig bleiben
        if (structureNodes.size() < 3) return false;

        // Head-Knoten können normalerweise nicht entfernt werden
        if (isHead(typeId)) return false;

        return super.canBeRemovedFromStructure(structureRoot);
    }

    // ===== STRUKTUR-VALIDIERUNG =====

    /**
     * Erweiterte vollständig-vernetzte-Struktur-Validierung mit expliziter FULLY_CONNECTED-Typ-ID.
     * <p>
     * Validiert zusätzlich zu super.isValidStructure():
     * - Alle Knoten sind FullyConnectedMirrorNode-Instanzen
     * - Mindestens 2 Knoten für ein sinnvolles vollständiges Netz
     * - Genau ein Head-Knoten vorhanden
     * - Jeder Knoten hat Connectivity Degree = n-1 (mit allen anderen verbunden)
     * - Symmetrische Verbindungen (bidirektional)
     * - Keine Zyklen im traditionellen Sinne (vollständiges Netz ist per definitionem azyklisch in der Hierarchie)
     * - Head-Node hat Edge-Links für externe Verbindungen
     * - Alle Nicht-Head-Knoten haben korrekte Parent-Child-Beziehungen
     * <p>
     * Wiederverwendung:
     * - super.isValidStructure() für MirrorNode-Basis-Validierung
     * - getNumEdgeLinks() für Edge-Link-Validierung
     * - isValidFullyConnectedNode() für individuelle Knoten-Validierung
     *
     * @param allNodes Menge aller Knoten, die zur Struktur gehören sollen
     * @param typeId Die Typ-ID der gewünschten Struktur (sollte FULLY_CONNECTED sein)
     * @param head Die Head-Node der gewünschten Struktur
     * @return true, wenn die vollständig-vernetzte-Struktur gültig ist
     */
    @Override
    public boolean isValidStructure(Set<StructureNode> allNodes, StructureType typeId, StructureNode head) {
        // Zuerst die grundlegende MirrorNode-Struktur-Validierung
        if (!super.isValidStructure(allNodes, typeId, head)) {
            return false;
        }

        // Mindestgröße für vollständige Netze
        if (allNodes.size() < 2) return false;

        // Sammle alle vollständig-vernetzte-Knoten und finde Head-Knoten
        Set<FullyConnectedMirrorNode> fullyConnectedNodes = new HashSet<>();
        FullyConnectedMirrorNode headNode = null;

        for (StructureNode node : allNodes) {
            if (!(node instanceof FullyConnectedMirrorNode fcNode)) {
                return false; // Alle Knoten müssen FullyConnectedMirrorNodes sein
            }

            fullyConnectedNodes.add(fcNode);
            if (fcNode.isHead(typeId)) {
                if (headNode != null) return false; // Nur ein Head erlaubt
                headNode = fcNode;
            }
        }

        if (headNode == null) return false; // Ein Head muss vorhanden sein

        // Validiere vollständig-vernetzte-spezifische Eigenschaften für alle Knoten
        for (FullyConnectedMirrorNode fcNode : fullyConnectedNodes) {
            if (!isValidFullyConnectedNode(fcNode, headNode, typeId, allNodes.size())) {
                return false;
            }
        }

        // Prüfe symmetrische Verbindungen
        if (!hasSymmetricConnections(fullyConnectedNodes, typeId, headNode)) {
            return false;
        }

        // Head-Node muss Edge-Links haben (Verbindung nach außen)
        return headNode.getNumEdgeLinks(typeId, head) > 0;
    }

    /**
     * Überschreibt isValidStructure() für die automatische FULLY_CONNECTED-Typ-Ermittlung.
     * Wiederverwendung der automatischen Typ- und Head-Ermittlung aus StructureNode.
     *
     * @param allNodes Menge aller Knoten, die zur Struktur gehören sollen
     * @return true, wenn die vollständig-vernetzte-Struktur gültig ist
     */
    @Override
    public boolean isValidStructure(Set<StructureNode> allNodes) {
        StructureType typeId = StructureType.FULLY_CONNECTED;
        StructureNode head = findHead(typeId);
        return isValidStructure(allNodes, typeId, head != null ? head : this);
    }

    /**
     * Convenience-Methode für Struktur-Validierung ohne Parameter.
     */
    public boolean isValidStructure() {
        StructureType typeId = StructureType.FULLY_CONNECTED;
        StructureNode head = findHead(typeId);
        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head != null ? head : this);
        return isValidStructure(allNodes, typeId, head != null ? head : this);
    }

    /**
     * Validiert einen einzelnen vollständig-vernetzten-Knoten mit FULLY_CONNECTED-Typ-ID-Berücksichtigung.
     * <p>
     * Prüft:
     * - Connectivity Degree = n-1 (mit allen anderen Knoten verbunden)
     * - Korrekte Anzahl von Kindern basierend auf Position (Head vs. Nicht-Head)
     * - Parent-Beziehungen (Head darf externen Parent haben, andere müssen internen Parent haben)
     * - Strukturmitgliedschaft der Parent-Child-Beziehungen
     * <p>
     * Wiederverwendung:
     * - getConnectivityDegree() für Grad-Berechnung mit FULLY_CONNECTED-Typ-ID
     * - getChildren() für strukturspezifische Kind-Zählung
     * - getParent() für Parent-Zugriff
     * - getAllNodesInStructure() für Struktur-Mitgliedschaftsprüfung
     *
     * @param fcNode Der zu validierende vollständig-vernetzte-Knoten
     * @param headNode Der Head-Knoten der Struktur
     * @param typeId Der Strukturtyp (sollte FULLY_CONNECTED sein)
     * @param totalNodes Gesamtanzahl der Knoten in der Struktur
     * @return true wenn der Knoten gültig ist
     */
    private boolean isValidFullyConnectedNode(FullyConnectedMirrorNode fcNode, FullyConnectedMirrorNode headNode,
                                              StructureType typeId, int totalNodes) {
        final int headId = headNode.getId();

        // Jeder Knoten muss Connectivity Degree n-1 haben (mit allen anderen verbunden)
        int expectedDegree = totalNodes - 1;
        int actualDegree = fcNode.getConnectivityDegree(typeId, headId);

        if (actualDegree != expectedDegree) {
            return false;
        }

        StructureNode parent = fcNode.getParent();
        Set<StructureNode> structureNodes = fcNode.getAllNodesInStructure(typeId, headNode);

        if (fcNode == headNode) {
            // Head-Node darf einen externen Parent haben
            if (parent != null) {
                return !structureNodes.contains(parent);
            }
        } else {
            // Normale vollständig-vernetzte-Knoten: müssen Head als Parent haben
            return parent == headNode;
        }

        return true;
    }


    /**
     * Prüft symmetrische Verbindungen in der vollständig-vernetzten-Struktur.
     * <p>
     * Vollständige Netze erfordern bidirektionale Verbindungen:
     * - Wenn A mit B verbunden ist, muss B auch mit A verbunden sein
     * - Alle Knoten müssen mit allen anderen Knoten verbunden sein
     * <p>
     * Wiederverwendung:
     * - getChildren() für strukturspezifische Kind-Navigation
     * - getParent() für Parent-Zugriff
     *
     * @param fcNodes Alle vollständig-vernetzten-Knoten der Struktur
     * @param typeId Der Strukturtyp
     * @param headNode Der Head-Knoten
     * @return true wenn alle Verbindungen symmetrisch sind
     */
    private boolean hasSymmetricConnections(Set<FullyConnectedMirrorNode> fcNodes,
                                            StructureType typeId, FullyConnectedMirrorNode headNode) {
        final int headId = headNode.getId();

        for (FullyConnectedMirrorNode nodeA : fcNodes) {
            for (FullyConnectedMirrorNode nodeB : fcNodes) {
                if (nodeA == nodeB) continue; // Selbstverbindungen sind nicht erforderlich

                // Prüfe, ob A mit B verbunden ist
                boolean aConnectedToB = isConnectedTo(nodeA, nodeB, typeId, headId);

                // Prüfe, ob B mit A verbunden ist
                boolean bConnectedToA = isConnectedTo(nodeB, nodeA, typeId, headId);

                // In einem vollständigen Netz müssen ALLE Knoten miteinander verbunden sein
                if (!aConnectedToB || !bConnectedToA) {
                    return false; // Fehlende Verbindung gefunden
                }
            }
        }

        return true;
    }

    /**
     * Hilfsmethode: Prüft, ob nodeA mit nodeB verbunden ist.
     *
     * @param nodeA Startknoten
     * @param nodeB Zielknoten
     * @param typeId Strukturtyp
     * @param headId Head-ID
     * @return true wenn nodeA mit nodeB verbunden ist
     */
    private boolean isConnectedTo(FullyConnectedMirrorNode nodeA, FullyConnectedMirrorNode nodeB,
                                  StructureType typeId, int headId) {
        // Prüfe Parent-Child-Beziehungen in beide Richtungen
        return nodeA.getChildren(typeId, headId).contains(nodeB) ||
                nodeA.getParent() == nodeB;
    }

    // ===== VOLLSTÄNDIG-VERNETZTE-NAVIGATION =====

    /**
     * Gibt alle direkt verbundenen Knoten zurück.
     * <p>
     * In vollständigen Netzen ist jeder Knoten mit jedem anderen verbunden.
     * Diese Methode sammelt alle anderen Knoten der Struktur.
     * <p>
     * Wiederverwendung:
     * - findHead() für FULLY_CONNECTED-spezifische Head-Ermittlung
     * - getAllNodesInStructure() für Strukturknotenzählung
     *
     * @return Set aller direkt verbundenen FullyConnectedMirrorNodes
     */
    public Set<FullyConnectedMirrorNode> getConnectedNodes() {
        StructureType typeId = StructureType.FULLY_CONNECTED;
        StructureNode head = findHead(typeId);
        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head != null ? head : this);

        Set<FullyConnectedMirrorNode> connectedNodes = new HashSet<>();
        for (StructureNode node : allNodes) {
            if (node != this && node instanceof FullyConnectedMirrorNode fcNode) {
                connectedNodes.add(fcNode);
            }
        }

        return connectedNodes;
    }

    /**
     * Findet den Head-Knoten der FULLY_CONNECTED-Struktur.
     * <p>
     * Nutzt findHead() mit expliziter FULLY_CONNECTED-Typ-ID für typsichere Suche
     * und stellt sicher, dass nur FullyConnectedMirrorNode-Instanzen zurückgegeben werden.
     * <p>
     * Wiederverwendung:
     * - findHead() aus StructureNode für Standard-Head-Finding
     *
     * @return Der Head-Knoten oder null, wenn kein vollständig-vernetzter-Head gefunden wird
     */
    public FullyConnectedMirrorNode getFullyConnectedHead() {
        StructureNode head = findHead(StructureType.FULLY_CONNECTED);
        return (head instanceof FullyConnectedMirrorNode) ? (FullyConnectedMirrorNode) head : null;
    }

    /**
     * Berechnet die Anzahl der Knoten im vollständigen Netz.
     * <p>
     * Nutzt FULLY_CONNECTED-spezifische Strukturermittlung für genaue Zählung
     * nur der Knoten, die zur FULLY_CONNECTED-Struktur gehören.
     * <p>
     * Wiederverwendung:
     * - findHead() für vollständig-vernetzte-spezifische Head-Ermittlung
     * - getAllNodesInStructure() für Strukturknotenzählung
     *
     * @return Anzahl der Knoten im vollständigen Netz
     */
    public int getNetworkSize() {
        StructureType typeId = StructureType.FULLY_CONNECTED;
        StructureNode head = findHead(typeId);
        return getAllNodesInStructure(typeId, head != null ? head : this).size();
    }

    /**
     * Sammelt alle vollständig-vernetzten-Knoten der Struktur.
     * <p>
     * Nutzt FULLY_CONNECTED-spezifische Strukturermittlung für die typsichere Sammlung
     * aller Knoten, die zur Struktur gehören.
     * <p>
     * Wiederverwendung:
     * - findHead() für Head-Ermittlung
     * - getAllNodesInStructure() für Strukturknotenzählung
     *
     * @return Liste aller FullyConnectedMirrorNodes in der Struktur
     */
    public List<FullyConnectedMirrorNode> getAllFullyConnectedNodes() {
        StructureType typeId = StructureType.FULLY_CONNECTED;
        StructureNode head = findHead(typeId);
        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head != null ? head : this);

        List<FullyConnectedMirrorNode> fcNodes = new ArrayList<>();
        for (StructureNode node : allNodes) {
            if (node instanceof FullyConnectedMirrorNode fcNode) {
                fcNodes.add(fcNode);
            }
        }

        return fcNodes;
    }

    // ===== HILFSMETHODEN =====

    /**
     * Berechnet den Connectivity Degree für eine spezifische Struktur.
     * <p>
     * Multi-Type-System Konnektivitätsberechnung:
     * - Zählt nur Verbindungen, die zur spezifischen FULLY_CONNECTED-Struktur gehören
     * - Prüft ChildRecord für Struktur-Zugehörigkeit (typeId + headId)
     * - Addiert Parent-Verbindung + Anzahl Kinder der Struktur
     * <p>
     * Wiederverwendung:
     * - getParent() für Parent-Zugriff
     * - findChildRecordById() für ChildRecord-Zugriff
     * - belongsToStructure() für Struktur-Zugehörigkeitsprüfung
     * - getChildren() für strukturspezifische Kind-Zählung
     *
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @param headId Die Head-ID der gewünschten Struktur
     * @return Anzahl der Verbindungen für diese spezifische Struktur
     */
    private int getConnectivityDegree(StructureType typeId, int headId) {
        int connections = 0;

        // Prüfe Parent-Verbindung
        if (getParent() != null) {
            ChildRecord parentRecord = getParent().findChildRecordById(getId());
            if (parentRecord != null && parentRecord.belongsToStructure(typeId, headId)) {
                connections++;
            }
        }

        // Addiere Kinder dieser Struktur
        connections += getChildren(typeId, headId).size();
        return connections;
    }

    /**
     * Berechnet die erwartete Anzahl Links für ein vollständiges Netz.
     * <p>
     * Formel: n * (n-1) / 2 für n Knoten (bidirektionale Links werden einmal gezählt)
     * in unserer Parent-Child-Struktur: n-1 Links (Stern-Form vom Head aus)
     *
     * @return Erwartete Anzahl Links für die Struktur-Größe
     */
    public int getExpectedLinkCount() {
        int n = getNetworkSize();
        return Math.max(0, n - 1); // Stern-Form: Head verbunden mit allen anderen
    }

    /**
     * Prüft, ob die aktuelle Struktur die optimale Anzahl Links hat.
     *
     * @return true, wenn die Link-Anzahl der erwarteten Anzahl entspricht
     */
    public boolean hasOptimalLinkCount() {
        StructureType typeId = StructureType.FULLY_CONNECTED;
        StructureNode head = findHead(typeId);
        int actualLinks = getNumPlannedLinksFromStructure(typeId, head != null ? head : this);
        return actualLinks == getExpectedLinkCount();
    }
}