
package org.lrdm.topologies.node;

import org.lrdm.Mirror;

import java.util.*;

/**
 * Spezialisierte MirrorNode für Ring-Strukturen.
 * Validiert, dass die Struktur einen geschlossenen Ring bildet.
 * <p>
 * Eine gültige Ring-Struktur hat folgende Eigenschaften:
 * - Mindestens 3 Knoten für einen echten Ring
 * - Jeder Knoten hat genau 2 Verbindungen (Connectivity Degree 2)
 * - Bildet einen geschlossenen Zyklus ohne Unterbrechungen
 * - Genau ein Head-Knoten als Verbindungspunkt zu externen Strukturen
 * - Head-Knoten muss Edge-Links für externe Verbindungen haben
 * - Alle anderen Knoten haben Parents und Children innerhalb der Ring-Struktur
 * <p>
 * Nutzt das Multi-Type-System mit expliziter RING-Typ-ID und Head-ID-Berücksichtigung
 * für korrekte Koexistenz mit anderen Strukturtypen (Bäume, Linien, etc.).
 * <p>
 * Maximierte Wiederverwendung der StructureNode/MirrorNode-Funktionalität:
 * - 90 %+ der Traversierungs- und Validierungslogik wird wiederverwendet
 * - Fokussiert auf ring-spezifische Validierung und Navigation
 * - Überschreibt nur Head-Finding für optimierte Ring-Navigation
 * <p>
 * Architektur-Verbesserungen:
 * - Override findHead() für konsistente spezialisierte Head-Finding-Logik
 * - Entfernung redundanter isRingNode() - ersetzt durch isValidStructure()
 * - Vereinfachte API mit klaren Verantwortlichkeiten
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class RingMirrorNode extends MirrorNode {

    // ===== KONSTRUKTOREN =====

    /**
     * Erstellt einen neuen RingMirrorNode mit gegebener ID.
     *
     * @param id Eindeutige Identifikationsnummer des Knotens
     */
    public RingMirrorNode(int id) {
        super(id);
    }

    /**
     * Erstellt einen neuen RingMirrorNode mit gegebener ID und zugeordnetem Mirror.
     *
     * @param id Eindeutige Identifikationsnummer des Knotens
     * @param mirror Der zugeordnete Mirror für Link-Management
     */
    public RingMirrorNode(int id, Mirror mirror) {
        super(id, mirror);
    }

    // ===== TYP-SYSTEM INTEGRATION =====

    /**
     * Überschreibt die Typ-Ableitung für korrekte RING-Identifikation.
     * Kritisch für Multi-Type-System: Ermöglicht korrekte Strukturerkennung
     * und automatische Typ-ID-Zuordnung in allen StructureNode-Methoden.
     *
     * @return StructureType.RING für eindeutige Ring-Identifikation
     */
    @Override
    public StructureType deriveTypeId() {
        return StructureType.RING;
    }

    // ===== STRUKTUR-MANAGEMENT =====

    /**
     * Prüft, ob dieser Knoten weitere Kinder akzeptieren kann.
     * <p>
     * Ring-spezifische Logik:
     * - Jeder Knoten kann maximal 1 Kind haben (zyklische Struktur)
     * - Nur wenn die aktuelle Struktur gültig ist
     * - Nur wenn noch keine Kinder für diese RING-Struktur vorhanden sind
     * <p>
     * Wiederverwendung:
     * - findHead(typeId) für Ring-spezifische Head-Ermittlung
     * - getChildren(typeId, headId) für strukturspezifische Kind-Zählung
     * - super.canAcceptMoreChildren() für Basis-Validierung
     *
     * @return true, wenn weitere Kinder akzeptiert werden können
     */
    @Override
    public boolean canAcceptMoreChildren() {
        StructureType typeId = StructureType.RING;
        StructureNode head = findHead(typeId);
        final int headId = head != null ? head.getId() : this.getId();

        return super.canAcceptMoreChildren() &&
                isValidStructure() &&
                getChildren(typeId, headId).isEmpty();
    }

    /**
     * Prüft, ob dieser Knoten aus der Struktur entfernt werden kann.
     * <p>
     * Ring-spezifische Logik:
     * - Ein Ring muss mindestens 3 Knoten haben
     * - Nach Entfernung müssen noch mindestens 3 Knoten übrig bleiben
     * - Entfernung darf die Ring-Eigenschaft nicht zerstören
     * <p>
     * Wiederverwendung:
     * - findHead(typeId) für Ring-spezifische Head-Ermittlung
     * - getAllNodesInStructure(typeId, head) für Strukturknotenzählung
     * - super.canBeRemovedFromStructure() für Basis-Validierung
     *
     * @param structureRoot Der Root-Knoten der Struktur
     * @return true, wenn der Knoten sicher entfernt werden kann
     */
    @Override
    public boolean canBeRemovedFromStructure(StructureNode structureRoot) {
        if (structureRoot == null) return false;

        StructureType typeId = StructureType.RING;
        StructureNode head = findHead(typeId);
        if (head == null) head = structureRoot;

        Set<StructureNode> structureNodes = getAllNodesInStructure(typeId, head);

        // Ring muss mindestens 3 Knoten haben - nach Entfernung noch mindestens 3
        if (structureNodes.size() < 4) return false;

        return super.canBeRemovedFromStructure(structureRoot);
    }

    // ===== STRUKTUR-VALIDIERUNG =====

    /**
     * Erweiterte Ring-Struktur-Validierung mit expliziter RING-Typ-ID.
     * <p>
     * Validiert zusätzlich zu super.isValidStructure():
     * - Alle Knoten sind RingMirrorNode-Instanzen
     * - Mindestens 3 Knoten für einen echten Ring
     * - Genau ein Head-Knoten vorhanden
     * - Alle Knoten haben korrekten Connectivity Degree 2
     * - Jeder Knoten hat genau 1 Kind für zyklische Struktur
     * - Struktur bildet geschlossenen Zyklus (nutzt RING-Typ-ID für korrekte Traversierung)
     * - Head-Node hat Edge-Links für externe Verbindungen
     * - Korrekte Parent-Child-Beziehungen (Head darf externen Parent haben)
     * <p>
     * Wiederverwendung:
     * - super.isValidStructure() für MirrorNode-Basis-Validierung
     * - hasClosedCycle() mit RING-Typ-ID für Zyklusprüfung
     * - getNumEdgeLinks() für Edge-Link-Validierung
     * - isValidRingNode() für individuelle Knoten Validierung
     *
     * @param allNodes Menge aller Knoten, die zur Struktur gehören sollen
     * @param typeId Die Typ-ID der gewünschten Struktur (sollte RING sein)
     * @param head Die Head-Node der gewünschten Struktur
     * @return true, wenn die Ring-Struktur gültig ist
     */
    @Override
    public boolean isValidStructure(Set<StructureNode> allNodes, StructureType typeId, StructureNode head) {
        // Zuerst die grundlegende MirrorNode-Struktur validierung
        if (!super.isValidStructure(allNodes, typeId, head)) {
            return false;
        }

        if (allNodes.size() < 3) return false; // Mindestens 3 Knoten für einen Ring

        // Sammle alle Ring-Knoten und finde Head-Knoten
        Set<RingMirrorNode> ringNodes = new HashSet<>();
        RingMirrorNode headNode = null;

        for (StructureNode node : allNodes) {
            if (!(node instanceof RingMirrorNode ringNode)) {
                return false; // Alle Knoten müssen RingMirrorNodes sein
            }

            ringNodes.add(ringNode);
            if (ringNode.isHead(typeId)) {
                if (headNode != null) return false; // Nur ein Head erlaubt
                headNode = ringNode;
            }
        }

        if (headNode == null) return false; // Ein Head muss vorhanden sein

        // Validiere alle Ring-Knoten mit RING-spezifischer Logik
        for (RingMirrorNode ringNode : ringNodes) {
            if (!isValidRingNode(ringNode, headNode, typeId)) {
                return false;
            }
        }

        // Prüfe geschlossenen Zyklus für die RING-Struktur
        if (!hasClosedCycle(allNodes, typeId, head)) {
            return false;
        }

        // Head-Node muss Edge-Links haben (Verbindung nach außen)
        final int headId = headNode.getId();
        return headNode.getNumEdgeLinks(typeId, head) > 0;
    }

    /**
     * Überschreibt isValidStructure() für automatische RING-Typ-Ermittlung.
     * Wiederverwendung der automatischen Typ- und Head-Ermittlung aus StructureNode.
     *
     * @param allNodes Menge aller Knoten, die zur Struktur gehören sollen
     * @return true, wenn die Ring-Struktur gültig ist
     */
    @Override
    public boolean isValidStructure(Set<StructureNode> allNodes) {
        StructureType typeId = StructureType.RING;
        StructureNode head = findHead(typeId);
        return isValidStructure(allNodes, typeId, head != null ? head : this);
    }

    /**
     * Convenience-Methode für Struktur-Validierung ohne Parameter.
     * <p>
     * ERSETZT die frühere isRingNode()-Methode für klarere API:
     * - Eine Methode für Struktur-Validierung statt zwei redundanter Methoden
     * - Nutzt RING-spezifische Strukturermittlung für automatische Validierung
     * - Konsistenz mit anderen StructureNode-Validierungsmethoden
     * <p>
     * Wiederverwendung:
     * - findHead() für automatische Head-Ermittlung
     * - getAllNodesInStructure() für automatische Strukturknotenbefüllung
     *
     * @return true, wenn die aktuelle Ring-Struktur gültig ist
     */
    public boolean isValidStructure() {
        StructureType typeId = StructureType.RING;
        StructureNode head = findHead(typeId);
        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head != null ? head : this);
        return isValidStructure(allNodes, typeId, head != null ? head : this);
    }

    /**
     * Validiert einen einzelnen Ring-Knoten mit RING-Typ-ID-Berücksichtigung.
     * <p>
     * Prüft:
     * - Connectivity Degree 2 (zwei Verbindungen für Ring-Struktur)
     * - Genau 1 Kind (für zyklische Navigation)
     * - Parent-Beziehungen (Head darf externen Parent haben, andere müssen internen Parent haben)
     * - Strukturmitgliedschaft der Parent-Child-Beziehungen
     * <p>
     * Wiederverwendung:
     * - getConnectivityDegree() für Grad-Berechnung mit RING-Typ-ID
     * - getChildren() für strukturspezifische Kind-Zählung
     * - getParent() für Parent-Zugriff
     * - getAllNodesInStructure() für Struktur-Mitgliedschaftsprüfung
     *
     * @param ringNode Der zu validierende Ring-Knoten
     * @param headNode Der Head-Knoten des Rings
     * @param typeId Der Strukturtyp (sollte RING sein)
     * @return true wenn der Knoten gültig ist
     */
    private boolean isValidRingNode(RingMirrorNode ringNode, RingMirrorNode headNode, StructureType typeId) {
        final int headId = headNode.getId();

        // Jeder Knoten muss Connectivity Degree 2 haben und genau ein Kind
        int degree = ringNode.getConnectivityDegree(typeId, headId);
        Set<StructureNode> children = ringNode.getChildren(typeId, headId);

        if (degree != 2 || children.size() != 1) {
            return false;
        }

        StructureNode parent = ringNode.getParent();
        Set<StructureNode> structureNodes = ringNode.getAllNodesInStructure(typeId, headNode);

        if (ringNode == headNode) {
            // Head-Node darf einen externen Parent haben
            if (parent != null) {
                // Parent darf nicht Teil der Ring-Struktur sein (extern)
                return !structureNodes.contains(parent);
            }
        } else {
            // Normale Ring-Knoten: müssen internen Parent haben
            if (parent == null) {
                return false; // Normale Knoten müssen einen Parent haben
            }

            // Parent muss Teil der Ring-Struktur sein
            return structureNodes.contains(parent);
        }

        return true;
    }

    // ===== RING-NAVIGATION =====

    /**
     * Gibt den nächsten Knoten im Ring zurück (im Uhrzeigersinn).
     * <p>
     * Ring-Navigation-Logik:
     * - Folgt dem ersten (und einzigen) Kind für Ring-Traversierung
     * - Nutzt RING-Typ-ID für korrekte Strukturabgrenzung
     * - Typsichere Rückgabe nur für RingMirrorNode-Instanzen
     * <p>
     * Wiederverwendung:
     * - findHead() für Ring-spezifische Head-Ermittlung
     * - getChildren() für strukturspezifische Kind-Navigation
     *
     * @return Der nächste RingMirrorNode oder null, wenn Navigation nicht möglich ist
     */
    public RingMirrorNode getNextInRing() {
        StructureType typeId = StructureType.RING;
        StructureNode head = findHead(typeId);
        final int headId = head != null ? head.getId() : this.getId();

        Set<StructureNode> children = getChildren(typeId, headId);
        if (children.size() != 1) return null;

        StructureNode next = children.iterator().next();
        return (next instanceof RingMirrorNode) ? (RingMirrorNode) next : null;
    }

    /**
     * Gibt den vorherigen Knoten im Ring zurück (gegen den Uhrzeigersinn).
     * <p>
     * Ring-Navigation-Logik:
     * - Folgt dem Parent für Rückwärts-Navigation
     * - Behandelt Head-Node-Sonderfall (externer Parent möglich)
     * - Nutzt RING-Typ-ID für korrekte Strukturabgrenzung
     * <p>
     * Wiederverwendung:
     * - findHead() für Head-Ermittlung und Head-Validierung
     * - getParent() für Parent-Zugriff
     * - getAllNodesInStructure() für Struktur-Mitgliedschaftsprüfung
     *
     * @return Der vorherige RingMirrorNode oder null, wenn Navigation nicht möglich ist
     */
    public RingMirrorNode getPreviousInRing() {
        StructureType typeId = StructureType.RING;
        StructureNode head = findHead(typeId);

        StructureNode prev = getParent();

        // Für Head-Node kann der Parent extern sein - prüfe Strukturmitgliedschaft
        if (isHead(typeId) && prev != null) {
            Set<StructureNode> structureNodes = getAllNodesInStructure(typeId, head != null ? head : this);
            if (!structureNodes.contains(prev)) {
                return null; // Externer Parent - keine Ring-Navigation möglich
            }
        }

        return (prev instanceof RingMirrorNode) ? (RingMirrorNode) prev : null;
    }

    // ===== RING-SPEZIFISCHE CONVENIENCE-METHODEN =====


    /**
     * Ring-spezifische Head-Finding-Logik mit Optimierung für zyklische Strukturen.
     * Überschreibt MirrorNode.findHead() für RING-spezifische Optimierung.
     * Sucht strikt nach Head-Knoten und gibt null zurück, wenn niemand gefunden wird.
     *
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @return Head-Knoten für diesen Strukturtyp oder null
     */
    @Override
    public StructureNode findHead(StructureType typeId) {
        if (typeId == null) {
            return null;
        }

        // Für RING-Strukturen: Bidirektionale Traversierung zur Vermeidung von Endlos-Loops
        if (typeId == StructureType.RING) {
            Set<StructureNode> visited = new HashSet<>();
            Queue<StructureNode> queue = new LinkedList<>();
            queue.offer(this);

            while (!queue.isEmpty()) {
                StructureNode current = queue.poll();

                if (visited.contains(current)) {
                    continue; // Bereits besucht - verhindert Endlos-Loops in Ringen
                }
                visited.add(current);

                // Prüfe, ob der aktuelle Knoten ein Head für RING ist
                if (current.isHead(StructureType.RING)) {
                    return current; // Head-Knoten gefunden
                }

                // Füge sowohl Parent als auch Kinder für bidirektionale Ring-Navigation hinzu
                if (current.getParent() != null && !visited.contains(current.getParent())) {
                    queue.offer(current.getParent());
                }

                // Für Ring-Strukturen: auch Kinder durchsuchen wegen zyklischer Natur
                for (StructureNode child : current.getChildren(StructureType.RING)) {
                    if (!visited.contains(child)) {
                        queue.offer(child);
                    }
                }
            }

            // Kein HEAD für RING gefunden
            return null;
        }

        // Für andere Strukturtypen: verwenden Sie die Standard-Implementierung
        return super.findHead(typeId);
    }

    /**
     * Findet den Head-Knoten der RING-Struktur.
     * <p>
     * Nutzt findHead() mit expliziter RING-Typ-ID für typsichere Suche
     * und stellt sicher, dass nur RingMirrorNode-Instanzen zurückgegeben werden.
     * <p>
     * Wiederverwendung:
     * - findHead() aus überschriebener Ring-spezifischer Implementierung
     *
     * @return Der Head-Knoten oder null, wenn kein Ring-Head gefunden wird
     */
    public RingMirrorNode getRingHead() {
        StructureNode head = findHead(StructureType.RING);
        return (head instanceof RingMirrorNode) ? (RingMirrorNode) head : null;
    }

    /**
     * Berechnet die Anzahl der Knoten im Ring.
     * <p>
     * Nutzt RING-spezifische Strukturermittlung für genaue Zählung
     * nur der Knoten, die zur RING-Struktur gehören.
     * <p>
     * Wiederverwendung:
     * - findHead() für Ring-spezifische Head-Ermittlung
     * - getAllNodesInStructure() für Strukturknotenzählung
     *
     * @return Anzahl der Knoten im Ring
     */
    public int getRingSize() {
        StructureType typeId = StructureType.RING;
        StructureNode head = findHead(typeId);
        return getAllNodesInStructure(typeId, head != null ? head : this).size();
    }

    /**
     * Berechnet die Position dieses Knotens im Ring (0-basiert).
     * <p>
     * Startet beim Head-Knoten und zählt über Ring-Navigation bis zu diesem Knoten.
     * Position 0 ist der Head-Knoten, Position 1 ist das erste Kind, etc.
     * <p>
     * Ring-sichere Navigation:
     * - Nutzt getNextInRing() für sichere Traversierung
     * - Begrenzt Iteration durch Ring-Größe (verhindert Endlos-Loops)
     * - Erkennt Rückkehr zum Head (geschlossener Ring)
     *
     * @return Position im Ring (0-basiert) oder -1 wenn nicht gefunden
     */
    public int getPositionInRing() {
        StructureType typeId = StructureType.RING;
        StructureNode head = findHead(typeId);

        if (!(head instanceof RingMirrorNode ringHead)) return -1;

        RingMirrorNode current = ringHead;
        int position = 0;
        int maxNodes = getRingSize(); // Verhindere Endlos-Loops

        do {
            if (current == this) return position;
            current = current.getNextInRing();
            position++;
        } while (current != null && current != ringHead && position < maxNodes);

        return -1; // Knoten nicht im Ring gefunden
    }

    /**
     * Sammelt alle Ring-Knoten in Navigationsreihenfolge.
     * <p>
     * Startet beim Head-Knoten und sammelt alle Knoten über Ring-Navigation
     * in der Reihenfolge der Ring-Traversierung (Uhrzeigersinn).
     * <p>
     * Ring-sichere Sammlung:
     * - Nutzt getNextInRing() für sichere Traversierung
     * - Begrenzt Iteration durch Ring-Größe (verhindert Endlos-Loops)
     * - Erkennt Rückkehr zum Head (geschlossener Ring)
     * - Garantierte Reihenfolge der Navigation
     *
     * @return Liste aller Ring-Knoten in Navigationsreihenfolge
     */
    public List<RingMirrorNode> getAllRingNodes() {
        List<RingMirrorNode> ringNodes = new ArrayList<>();
        RingMirrorNode head = getRingHead();

        if (head == null) return ringNodes;

        RingMirrorNode current = head;
        int maxNodes = getRingSize(); // Verhindere Endlos-Loops
        int count = 0;

        do {
            ringNodes.add(current);
            current = current.getNextInRing();
            count++;
        } while (current != null && current != head && count < maxNodes);

        return ringNodes;
    }

    // ===== HILFSMETHODEN =====

    /**
     * Berechnet den Connectivity Degree für eine spezifische Struktur.
     * <p>
     * Multi-Type-System Konnektivitätsberechnung:
     * - Zählt nur Verbindungen, die zur spezifischen RING-Struktur gehören
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
}