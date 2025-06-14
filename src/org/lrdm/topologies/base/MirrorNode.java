package org.lrdm.topologies.base;

import org.lrdm.Link;
import org.lrdm.Mirror;

import java.util.*;

/**
 * Repräsentiert einen Knoten in einer Struktur (Baum oder Ring), der einem Mirror zugeordnet werden kann.
 * Diese Klasse trennt die Planungsschicht (TreeNode-Struktur) von der
 * Implementierungsschicht (Mirror mit Links).
 *
 * Vollständig zustandslos bezüglich Link-Informationen - alle werden dynamisch berechnet.
 * Unterstützt sowohl Baum- als auch Ring-Strukturen.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public class MirrorNode extends TreeNode {
    private Mirror mirror;
    private Set<Link> implementedLinks;

    /**
     * Erstellt einen neuen MirrorNode.
     *
     * @param id Eindeutige ID des Knotens
     */
    public MirrorNode(int id) {
        super(id);
        this.implementedLinks = new HashSet<>();
    }

    /**
     * Erstellt einen neuen MirrorNode mit zugeordnetem Mirror.
     *
     * @param id Eindeutige ID des Knotens
     * @param mirror Der zugeordnete Mirror
     */
    public MirrorNode(int id, Mirror mirror) {
        super(id);
        this.mirror = mirror;
        this.implementedLinks = new HashSet<>();
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
     *
     * @param link Der hinzuzufügende Link
     */
    public void addLink(Link link) {
        if (link != null) {
            implementedLinks.add(link);
        }
    }

    /**
     * Entfernt einen implementierten Link.
     *
     * @param link Der zu entfernende Link
     */
    public void removeLink(Link link) {
        implementedLinks.remove(link);
    }

    /**
     * Gibt alle implementierten Links zurück.
     *
     * @return Set der implementierten Links
     */
    public Set<Link> getImplementedLinks() {
        return new HashSet<>(implementedLinks);
    }

    /**
     * Berechnet die Anzahl der geplanten Links basierend auf der TreeNode-Struktur.
     * Nutzt die TreeNode-Implementierung für sowohl Baum- als auch Ring-Strukturen.
     *
     * @return Anzahl der geplanten Links
     */
    public int getNumPlannedLinks() {
        return getConnectivityDegree();
    }

    // Rest der ursprünglichen MirrorNode-Implementierung bleibt unverändert...
}