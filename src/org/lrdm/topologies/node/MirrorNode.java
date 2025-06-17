
package org.lrdm.topologies.node;

import org.lrdm.Link;
import org.lrdm.Mirror;

import java.util.*;

/**
 * Repräsentiert einen Knoten in einer Struktur (Baum oder Ring), der einem Mirror zugeordnet werden kann.
 * Diese Klasse trennt die Planungsschicht (StructureNode-Struktur) von der
 * Implementierungsschicht (Mirror mit Links).
 * <p>
 * Vollständig zustandslos bezüglich Link-Informationen - alle werden über das Mirror verwaltet.
 * Unterstützt Multi-Type-Strukturen mit Typ-ID und Head-ID-Berücksichtigung.
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class MirrorNode extends StructureNode {
    private Mirror mirror;

    /**
     * Erstellt einen neuen MirrorNode.
     *
     * @param id Eindeutige ID des Knotens
     */
    public MirrorNode(int id) {
        super(id);
        this.nodeTypes.add(StructureType.MIRROR);
    }

    /**
     * Erstellt einen neuen MirrorNode mit zugeordnetem Mirror.
     *
     * @param id     Eindeutige ID des Knotens
     * @param mirror Der zugeordnete Mirror
     */
    public MirrorNode(int id, Mirror mirror) {
        super(id);
        this.mirror = mirror;
    }

    /**
     * Bestimmt die Typ-ID basierend auf der MirrorNode-Instanz.
     * Überschreibt StructureNode.deriveTypeId() für automatische Typ-Erkennung.
     *
     * @return Der Standard-Strukturtyp für MirrorNodes
     */
    @Override
    public StructureType deriveTypeId() {
        return StructureType.MIRROR;
    }

    // ===== MIRROR MANAGEMENT =====

    /**
     * Setzt den Mirror für diesen Knoten.
     * Sollte nur über Builder (TreeBuilder/RingBuilder) gesetzt werden, nicht direkt.
     *
     * @param mirror Der zu setzende Mirror
     */
    public void setMirror(Mirror mirror) {
        this.mirror = mirror;
    }

    /**
     * Gibt den zugeordneten Mirror zurück.
     *
     * @return Der zugeordnete Mirror oder null
     */
    public Mirror getMirror() {
        return mirror;
    }

    // ===== LINK MANAGEMENT =====

    /**
     * Fügt einen implementierten Link hinzu.
     * Delegiert an das zugeordnete Mirror.
     *
     * @param link Der hinzuzufügende Link
     */
    public void addLink(Link link) {
        if (mirror != null && link != null) {
            mirror.addLink(link);
        }
    }

    /**
     * Entfernt einen implementierten Link.
     * Delegiert an das zugeordnete Mirror.
     *
     * @param link Der zu entfernende Link
     */
    public void removeLink(Link link) {
        if (mirror != null && link != null) {
            mirror.removeLink(link);
        }
    }

    /**
     * Gibt alle implementierten Links zurück.
     * Delegiert an das zugeordnete Mirror.
     *
     * @return Set der implementierten Links
     */
    public Set<Link> getImplementedLinks() {
        if (mirror != null) {
            return new HashSet<>(mirror.getLinks());
        }
        return new HashSet<>();
    }

    /**
     * Berechnet die Anzahl der entwickelten/implementierten Links.
     * Dies sind die Links, die tatsächlich zwischen Mirrors implementiert wurden.
     * Delegiert an das zugeordnete Mirror.
     *
     * @return Anzahl der implementierten Links
     */
    public int getNumImplementedLinks() {
        if (mirror != null) {
            return mirror.getLinks().size();
        }
        return 0;
    }

    /**
     * Berechnet die Anzahl der noch ausstehenden Links für eine spezifische Struktur.
     * Dies ist die Differenz zwischen geplanten und implementierten Links.
     *
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @param head Die Head-Node der gewünschten Struktur
     * @return Anzahl der ausstehenden Links
     */
    public int getNumPendingLinks(StructureType typeId, StructureNode head) {
        return Math.max(0, getNumPlannedLinksFromStructure(typeId, head) - getNumImplementedLinks());
    }

    /**
     * Berechnet die Anzahl der noch ausstehenden Links.
     * Verwendet automatische Typ- und Head-Ermittlung.
     *
     * @return Anzahl der ausstehenden Links
     */
    public int getNumPendingLinks() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        return getNumPendingLinks(typeId, head != null ? head : this);
    }

    // ===== VERBINDUNGSPRÜFUNG =====

    /**
     * Prüft, ob dieser Knoten mit einem anderen MirrorNode über eine spezifische Struktur verlinkt ist.
     * Berücksichtigt sowohl geplante als auch implementierte Verbindungen für die spezifische Struktur.
     *
     * @param other Der andere MirrorNode
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @param head Die Head-Node der gewünschten Struktur
     * @return true, wenn eine Verbindung über den spezifischen Kanal existiert
     */
    public boolean isLinkedWith(MirrorNode other, StructureType typeId, StructureNode head) {
        if (other == null || this.mirror == null || other.mirror == null) {
            return false;
        }

        // Prüfe sowohl geplante als auch implementierte Verbindungen für spezifische Struktur
        boolean plannedConnection = isPlannedConnectionWith(other, typeId, head);
        boolean implementedConnection = hasImplementedConnectionWith(other, typeId, head);

        return plannedConnection && implementedConnection;
    }

    /**
     * Prüft, ob dieser Knoten mit einem anderen MirrorNode verlinkt ist.
     * Verwendet automatische Typ- und Head-Ermittlung.
     *
     * @param other Der andere MirrorNode
     * @return true, wenn eine Verbindung existiert
     */
    public boolean isLinkedWith(MirrorNode other) {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        return isLinkedWith(other, typeId, head != null ? head : this);
    }

    /**
     * Prüft, ob eine geplante Verbindung zu einem anderen Knoten über eine spezifische Struktur existiert.
     * Verwendet Stack-basierte Traversierung zur Abschätzung der Link-Verbindungen.
     *
     * @param other Der andere MirrorNode
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @param head Die Head-Node der gewünschten Struktur
     * @return true, wenn eine geplante Verbindung über den spezifischen Kanal existiert
     */
    private boolean isPlannedConnectionWith(MirrorNode other, StructureType typeId, StructureNode head) {
        if (other == null || head == null) return false;

        final int headId = head.getId();
        Set<StructureNode> visited = new HashSet<>();
        Stack<StructureNode> stack = new Stack<>();
        stack.push(this);

        while (!stack.isEmpty()) {
            StructureNode current = stack.pop();

            if (visited.contains(current)) continue;
            visited.add(current);

            // Gefunden?
            if (current == other) return true;

            // Parent traversieren (wenn Typ-ID und Head-ID passen)
            if (current.getParent() != null) {
                ChildRecord parentRecord = current.getParent().findChildRecordById(current.getId());
                if (parentRecord != null && parentRecord.belongsToStructure(typeId, headId)) {
                    stack.push(current.getParent());
                }
            }

            // Kinder traversieren (wenn Typ-ID und Head-ID passen)
            Set<StructureNode> structureChildren = current.getChildren(typeId, headId);
            stack.addAll(structureChildren);
        }

        return false;
    }

    /**
     * Prüft, ob eine implementierte Verbindung zu einem anderen Knoten über eine spezifische Struktur existiert.
     * Verifiziert die Head-ID und Typ-ID-Identität vor der Mirror-Link-Prüfung.
     *
     * @param other Der andere MirrorNode
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @param head Die Head-Node der gewünschten Struktur
     * @return true, wenn eine implementierte Verbindung über den spezifischen Kanal existiert
     */
    private boolean hasImplementedConnectionWith(MirrorNode other, StructureType typeId, StructureNode head) {
        if (other == null || this.mirror == null || other.mirror == null || head == null) {
            return false;
        }

        // Prüfe, ob beide Knoten zur gleichen Struktur (typeId + headId) gehören
        final int headId = head.getId();

        // Prüfe eigene Zugehörigkeit zur Struktur
        boolean thisInStructure;
        if (this.getParent() != null) {
            ChildRecord thisRecord = this.getParent().findChildRecordById(this.getId());
            thisInStructure = thisRecord != null && thisRecord.belongsToStructure(typeId, headId);
        } else {
            // Kein Parent - prüfe, ob wir der Head sind
            thisInStructure = this.isHead(typeId) && this.getId() == headId;
        }

        // Prüfe andere Knoten Zugehörigkeit zur Struktur
        boolean otherInStructure;
        if (other.getParent() != null) {
            ChildRecord otherRecord = other.getParent().findChildRecordById(other.getId());
            otherInStructure = otherRecord != null && otherRecord.belongsToStructure(typeId, headId);
        } else {
            // Kein Parent - prüfe, ob der andere der Head ist
            otherInStructure = other.isHead(typeId) && other.getId() == headId;
        }

        // Beide müssen zur gleichen Struktur gehören
        if (!thisInStructure || !otherInStructure) {
            return false;
        }

        // Jetzt prüfe ich die implementierte Mirror-Verbindung
        return this.mirror.isLinkedWith(other.mirror);
    }

    /**
     * Entfernt einen MirrorNode (für Builder).
     *
     * @param child Der zu entfernende Kindknoten
     */
    public void removeMirrorNode(MirrorNode child) {
        removeChild(child);
        // Entferne auch alle zugehörigen implementierten Links über das Mirror
        if (child != null && child.mirror != null && this.mirror != null) {
            Set<Link> linksToRemove = new HashSet<>();
            for (Link link : mirror.getLinks()) {
                if (link.getSource().getID() == child.mirror.getID() ||
                        link.getTarget().getID() == child.mirror.getID()) {
                    linksToRemove.add(link);
                }
            }
            linksToRemove.forEach(this::removeLink);
        }
    }

    // ===== STRUKTUR-SAMMLUNG MIT MULTI-TYPE-UNTERSTÜTZUNG =====

    /**
     * Sammelt alle Mirrors einer spezifischen Substruktur.
     * Verwendet getAllNodesInStructure() für konsistente Substruktur-Abgrenzung.
     *
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @param head Die Head-Node der gewünschten Struktur
     * @return Set aller Mirror-Instanzen in der spezifischen Substruktur (ohne null-Werte)
     */
    public Set<Mirror> getMirrorsOfStructure(StructureType typeId, StructureNode head) {
        Set<Mirror> mirrors = new HashSet<>();
        Set<StructureNode> structureNodes = getAllNodesInStructure(typeId, head);

        for (StructureNode node : structureNodes) {
            if (node instanceof MirrorNode mirrorNode && mirrorNode.getMirror() != null) {
                mirrors.add(mirrorNode.getMirror());
            }
        }

        return mirrors;
    }

    /**
     * Sammelt alle Mirrors der Substruktur.
     * Verwendet automatische Typ- und Head-Ermittlung.
     *
     * @return Set aller Mirror-Instanzen in der Substruktur (ohne null-Werte)
     */
    public Set<Mirror> getMirrorsOfStructure() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        return getMirrorsOfStructure(typeId, head != null ? head : this);
    }

    /**
     * Sammelt alle Mirrors der Endpunkte einer spezifischen Substruktur.
     *
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @param head Die Head-Node der gewünschten Struktur
     * @return Set aller Mirror-Instanzen der Endpunkte (ohne null-Werte)
     */
    public Set<Mirror> getMirrorsOfEndpoints(StructureType typeId, StructureNode head) {
        Set<Mirror> mirrors = new HashSet<>();
        Set<StructureNode> endpoints = getEndpointsOfStructure(typeId, head);

        for (StructureNode endpoint : endpoints) {
            if (endpoint instanceof MirrorNode mirrorNode && mirrorNode.getMirror() != null) {
                mirrors.add(mirrorNode.getMirror());
            }
        }

        return mirrors;
    }

    /**
     * Sammelt alle Mirrors der Endpunkte der Substruktur.
     * Verwendet automatische Typ- und Head-Ermittlung.
     *
     * @return Set aller Mirror-Instanzen der Endpunkte (ohne null-Werte)
     */
    public Set<Mirror> getMirrorsOfEndpoints() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        return getMirrorsOfEndpoints(typeId, head != null ? head : this);
    }

    // ===== LINK-SAMMLUNG MIT MULTI-TYPE-UNTERSTÜTZUNG =====

    /**
     * Sammelt alle Links, die vollständig zu einer spezifischen Substruktur gehören.
     * Ein Link gehört zur Struktur, wenn sowohl Source als auch Target
     * Mirrors von MirrorNodes der spezifischen Substruktur sind.
     *
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @param head Die Head-Node der gewünschten Struktur
     * @return Set aller Links, die Teil der spezifischen Substruktur sind
     */
    public Set<Link> getLinksOfStructure(StructureType typeId, StructureNode head) {
        Set<Link> structureLinks = new HashSet<>();
        Set<Mirror> structureMirrors = getMirrorsOfStructure(typeId, head);

        // Sammle alle Links von allen Mirrors der Struktur
        for (Mirror mirror : structureMirrors) {
            for (Link link : mirror.getLinks()) {
                if (isLinkOfStructure(link, structureMirrors)) {
                    structureLinks.add(link);
                }
            }
        }

        return structureLinks;
    }

    /**
     * Sammelt alle Links, die vollständig zur Substruktur gehören.
     * Verwendet automatische Typ- und Head-Ermittlung.
     *
     * @return Set aller Links, die Teil der Substruktur sind
     */
    public Set<Link> getLinksOfStructure() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        return getLinksOfStructure(typeId, head != null ? head : this);
    }

    /**
     * Sammelt alle Edge-Links (Randverbindungen) einer spezifischen Substruktur.
     * Ein Edge-Link ist ein Link, bei dem nur entweder Source ODER Target
     * ein Mirror der spezifischen Substruktur ist (aber nicht beide).
     *
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @param head Die Head-Node der gewünschten Struktur
     * @return Set aller Edge-Links der spezifischen Substruktur
     */
    public Set<Link> getEdgeLinks(StructureType typeId, StructureNode head) {
        Set<Link> edgeLinks = new HashSet<>();
        Set<Mirror> structureMirrors = getMirrorsOfStructure(typeId, head);

        // Sammle alle Links von allen Mirrors der Struktur
        for (Mirror mirror : structureMirrors) {
            for (Link link : mirror.getLinks()) {
                if (isEdgeLink(link, structureMirrors)) {
                    edgeLinks.add(link);
                }
            }
        }

        return edgeLinks;
    }

    /**
     * Sammelt alle Edge-Links (Randverbindungen).
     * Verwendet automatische Typ- und Head-Ermittlung.
     *
     * @return Set aller Edge-Links der Substruktur
     */
    public Set<Link> getEdgeLinks() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        return getEdgeLinks(typeId, head != null ? head : this);
    }

    // ===== LINK-KLASSIFIKATION =====

    /**
     * Prüft, ob ein Link vollständig zu einer spezifischen Substruktur gehört.
     *
     * @param link Der zu prüfende Link
     * @param structureMirrors Die Mirrors der spezifischen Struktur
     * @return true, wenn beide Endpoints zur Struktur gehören
     */
    private boolean isLinkOfStructure(Link link, Set<Mirror> structureMirrors) {
        if (link == null || structureMirrors == null) return false;

        boolean sourceInStructure = structureMirrors.contains(link.getSource());
        boolean targetInStructure = structureMirrors.contains(link.getTarget());

        return sourceInStructure && targetInStructure;
    }

    /**
     * Prüft, ob ein Link vollständig zur Substruktur gehört.
     * Verwendet automatische Typ- und Head-Ermittlung.
     *
     * @param link Der zu prüfende Link
     * @return true, wenn beide Endpoints zur Struktur gehören
     */
    public boolean isLinkOfStructure(Link link) {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        Set<Mirror> structureMirrors = getMirrorsOfStructure(typeId, head != null ? head : this);
        return isLinkOfStructure(link, structureMirrors);
    }

    /**
     * Prüft, ob ein Link ein Edge-Link einer spezifischen Substruktur ist.
     *
     * @param link Der zu prüfende Link
     * @param structureMirrors Die Mirrors der spezifischen Struktur
     * @return true, wenn nur ein Endpoint zur Struktur gehört
     */
    private boolean isEdgeLink(Link link, Set<Mirror> structureMirrors) {
        if (link == null || structureMirrors == null) return false;

        boolean sourceInStructure = structureMirrors.contains(link.getSource());
        boolean targetInStructure = structureMirrors.contains(link.getTarget());

        return sourceInStructure != targetInStructure; // XOR: nur einer ist in der Struktur
    }

    /**
     * Prüft, ob ein Link ein Edge-Link der Substruktur ist.
     * Verwendet automatische Typ- und Head-Ermittlung.
     *
     * @param link Der zu prüfende Link
     * @return true, wenn nur ein Endpoint zur Struktur gehört
     */
    public boolean isEdgeLink(Link link) {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        Set<Mirror> structureMirrors = getMirrorsOfStructure(typeId, head != null ? head : this);
        return isEdgeLink(link, structureMirrors);
    }

    // ===== LINK-ZÄHLUNG =====

    /**
     * Zählt die Anzahl der struktur-internen Links einer spezifischen Struktur.
     *
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @param head Die Head-Node der gewünschten Struktur
     * @return Anzahl der Links innerhalb der spezifischen Substruktur
     */
    public int getNumLinksOfStructure(StructureType typeId, StructureNode head) {
        return getLinksOfStructure(typeId, head).size();
    }

    /**
     * Zählt die Anzahl der struktur-internen Links.
     * Verwendet automatische Typ- und Head-Ermittlung.
     *
     * @return Anzahl der Links innerhalb der Substruktur
     */
    public int getNumLinksOfStructure() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        return getNumLinksOfStructure(typeId, head != null ? head : this);
    }

    /**
     * Zählt die Anzahl der Edge-Links einer spezifischen Struktur.
     *
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @param head Die Head-Node der gewünschten Struktur
     * @return Anzahl der Edge-Links der spezifischen Substruktur
     */
    public int getNumEdgeLinks(StructureType typeId, StructureNode head) {
        return getEdgeLinks(typeId, head).size();
    }

    /**
     * Zählt die Anzahl der Edge-Links.
     * Verwendet automatische Typ- und Head-Ermittlung.
     *
     * @return Anzahl der Edge-Links der Substruktur
     */
    public int getNumEdgeLinks() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        return getNumEdgeLinks(typeId, head != null ? head : this);
    }

    // ===== STRUKTURVALIDIERUNG =====

    /**
     * Erweiterte Struktur Validierung für MirrorNode mit spezifischer Struktur.
     * Überprüft zusätzlich zur StructureNode-Validierung die Mirror-Link-Konsistenz.
     *
     * @param allNodes Menge aller Knoten, die zur Struktur gehören sollen
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @param head Die Head-Node der gewünschten Struktur
     * @return true, wenn sowohl StructureNode- als auch Mirror-Validierung erfolgreich sind
     */
    public boolean isValidStructure(Set<StructureNode> allNodes, StructureType typeId, StructureNode head) {
        // Zuerst die grundlegende StructureNode-Validierung
        if (!super.isValidStructure(allNodes)) {
            return false;
        }

        if (head == null) return false;

        // Sammle alle Mirrors der spezifischen Struktur
        Set<Mirror> structureMirrors = getMirrorsOfStructure(typeId, head);
        Set<Link> structureLinks = getLinksOfStructure(typeId, head);

        // Validiere jeden Mirror in der spezifischen Struktur
        for (Mirror mirror : structureMirrors) {
            if (!isValidMirrorInStructure(mirror, structureMirrors, structureLinks)) {
                return false;
            }
        }

        // Validiere jeden Link in der spezifischen Struktur
        for (Link link : structureLinks) {
            if (!isValidLinkInStructure(link, structureMirrors)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Erweiterte Struktur Validierung für MirrorNode.
     * Verwendet automatische Typ- und Head-Ermittlung.
     *
     * @param allNodes Menge aller Knoten, die zur Struktur gehören sollen
     * @return true, wenn sowohl StructureNode- als auch Mirror-Validierung erfolgreich sind
     */
    @Override
    public boolean isValidStructure(Set<StructureNode> allNodes) {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        return isValidStructure(allNodes, typeId, head != null ? head : this);
    }

    /**
     * Convenience-Methode für Struktur validierung ohne Parameter.
     * Verwendet getAllNodesInStructure(), um die relevanten Knoten zu ermitteln.
     *
     * @return true wenn die Struktur gültig ist
     */
    public boolean isValidStructure() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head != null ? head : this);
        return isValidStructure(allNodes, typeId, head != null ? head : this);
    }

    /**
     * Validiert einen einzelnen Mirror innerhalb der Struktur.
     */
    private boolean isValidMirrorInStructure(Mirror mirror, Set<Mirror> structureMirrors, Set<Link> structureLinks) {
        if (mirror == null) return false;

        // Mirror muss in der Struktur enthalten sein
        if (!structureMirrors.contains(mirror)) {
            return false;
        }

        // Mirror muss mindestens einen Link zu anderen Struktur-Mirrors haben
        boolean hasStructureConnection = false;
        for (Link link : mirror.getLinks()) {
            if (structureLinks.contains(link)) {
                hasStructureConnection = true;
                break;
            }
        }

        // Prüfe auf Self-Links (sollten nicht existieren)
        for (Link link : mirror.getLinks()) {
            if (link.getSource().getID() == link.getTarget().getID()) {
                return false; // Self-Link gefunden
            }
        }

        return hasStructureConnection;
    }


    /**
     * Validiert einen einzelnen Link innerhalb der Struktur.
     */
    private boolean isValidLinkInStructure(Link link, Set<Mirror> structureMirrors) {
        if (link == null) return false;

        Mirror source = link.getSource();
        Mirror target = link.getTarget();

        // Beide Mirrors müssen zur Struktur gehören
        boolean sourceInStructure = structureMirrors.contains(source);
        boolean targetInStructure = structureMirrors.contains(target);

        return sourceInStructure && targetInStructure;
    }

    @Override
    public String toString() {
        return String.format("MirrorNode{id=%d, mirror=%s, children=%d}",
                getId(), mirror != null ? mirror.getID() : "null", getChildren().size());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MirrorNode other)) return false;
        return getId() == other.getId();
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(getId());
    }
}