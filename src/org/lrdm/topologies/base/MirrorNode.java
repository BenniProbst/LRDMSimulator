
package org.lrdm.topologies.base;

import org.lrdm.Link;
import org.lrdm.Mirror;

import java.util.*;

/**
 * Repräsentiert einen Knoten in einer Struktur (Baum oder Ring), der einem Mirror zugeordnet werden kann.
 * Diese Klasse trennt die Planungsschicht (TreeNode-Struktur) von der
 * Implementierungsschicht (Mirror mit Links).
 * <p>
 * Vollständig zustandslos bezüglich Link-Informationen - alle werden über das Mirror verwaltet.
 * Unterstützt sowohl Baum- als auch Ring-Strukturen.
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class MirrorNode extends TreeNode {
    private Mirror mirror;

    /**
     * Erstellt einen neuen MirrorNode.
     *
     * @param id Eindeutige ID des Knotens
     */
    public MirrorNode(int id) {
        super(id);
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
     * Berechnet die Anzahl der noch ausstehenden Links.
     * Dies ist die Differenz zwischen geplanten und implementierten Links.
     *
     * @return Anzahl der ausstehenden Links
     */
    public int getNumPendingLinks() {
        return Math.max(0, getNumPlannedLinksFromStructure() - getNumImplementedLinks());
    }

    /**
     * Prüft, ob dieser Knoten mit einem anderen MirrorNode verlinkt ist.
     * Funktioniert nur, wenn beide Knoten Mirrors haben und die Planungsschicht
     * mit der Implementierungsschicht umgesetzt wurde.
     * Unterstützt sowohl Baum- als auch Ring-Strukturen.
     *
     * @param other Der andere MirrorNode
     * @return true, wenn eine Verbindung existiert
     */
    public boolean isLinkedWith(MirrorNode other) {
        if (other == null || this.mirror == null || other.mirror == null) {
            return false;
        }

        // Prüfe sowohl geplante als auch implementierte Verbindungen
        boolean plannedConnection = isPlannedConnectionWith(other);
        boolean implementedConnection = hasImplementedConnectionWith(other);

        return plannedConnection && implementedConnection;
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
            Set<Link> linksToRemove = mirror.getJointMirrorLinks(child.mirror);
            for (Link link : linksToRemove) {
                mirror.removeLink(link);
                child.mirror.removeLink(link);
            }
        }
    }

    /**
     * Prüft, ob eine geplante Verbindung zu einem anderen Knoten existiert.
     * Einfache Parent-Child-Beziehung prüfen.
     */
    private boolean isPlannedConnectionWith(MirrorNode other) {
        // Direkte Parent-Child-Beziehung
        return this.getParent() == other || other.getParent() == this;
    }

    /**
     * Prüft, ob eine implementierte Verbindung zu einem anderen Knoten existiert.
     * Delegiert an das zugeordnete Mirror.
     */
    private boolean hasImplementedConnectionWith(MirrorNode other) {
        if (other == null || other.mirror == null || this.mirror == null) {
            return false;
        }

        return mirror.isLinkedWith(other.mirror);
    }

    @Override
    public String toString() {
        return String.format("MirrorNode{id=%d, mirror=%s, planned=%d, implemented=%d, pending=%d, isHead=%s}",
                getId(),
                mirror != null ? mirror.getID() : "null",
                getNumPlannedLinksFromStructure(),
                getNumImplementedLinks(),
                getNumPendingLinks(),
                isHead());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MirrorNode)) return false;
        MirrorNode other = (MirrorNode) obj;
        return getId() == other.getId();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}