package org.lrdm.util;

import org.lrdm.Link;
import org.lrdm.Mirror;

import java.util.*;

/**
 * Erweiterte TreeNode-Klasse für Mirror-spezifische Funktionalitäten.
 * Verbindet TreeNode-Strukturen mit Mirror-Netzwerken.
 * Enthält keine Konstruktions-Metadaten, nur Mirror-spezifische Daten.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public class MirrorNode extends TreeNode {
    private Mirror mirror;
    private Set<Link> links;
    private List<MirrorNode> mirrorNodeChildren; // Alle verbundenen Mirror-Knoten
    private int pendingLinks; // Geplante aber noch nicht erstellte Links

    public MirrorNode(int id) {
        super(id);
        this.links = new HashSet<>();
        this.mirrorNodeChildren = new ArrayList<>();
        this.pendingLinks = 0;
    }

    public MirrorNode(int id, Mirror mirror) {
        this(id);
        this.mirror = mirror;
    }

    /**
     * Gibt die aktuelle Anzahl der Target-Links zurück.
     *
     * @return Anzahl der aktuellen Links
     */
    public int getNumTargetLinks() {
        return links.size();
    }

    /**
     * Berechnet die vorhergesagte Anzahl der Target-Links.
     * Berücksichtigt aktuelle und geplante Links.
     *
     * @return Vorhergesagte Anzahl der Links
     */
    public int getPredictedNumTargetLinks() {
        return links.size() + pendingLinks;
    }

    /**
     * Gibt die Anzahl der verbundenen Mirror-Knoten zurück.
     *
     * @return Anzahl der Mirror-Knoten
     */
    public int getNumMirrors() {
        return mirrorNodeChildren.size();
    }

    /**
     * Gibt eine Liste aller verbundenen Mirror-Knoten zurück.
     *
     * @return Liste der Mirror-Knoten
     */
    public List<MirrorNode> getAllMirrors() {
        return new ArrayList<>(mirrorNodeChildren);
    }

    /**
     * Gibt alle Links dieses Knotens zurück.
     *
     * @return Set der Links
     */
    public Set<Link> getAllLinks() {
        return new HashSet<>(links);
    }

    /**
     * Fügt einen Link zu diesem Knoten hinzu.
     *
     * @param link Der hinzuzufügende Link
     */
    public void addLink(Link link) {
        if (link != null) {
            links.add(link);
        }
    }

    /**
     * Entfernt einen Link von diesem Knoten.
     *
     * @param link Der zu entfernende Link
     */
    public void removeLink(Link link) {
        links.remove(link);
    }

    /**
     * Fügt einen geplanten Link hinzu (erhöht pendingLinks).
     *
     * @param count Anzahl der geplanten Links
     */
    public void addPendingLinks(int count) {
        this.pendingLinks += Math.max(0, count);
    }

    /**
     * Bestätigt geplante Links (verringert pendingLinks).
     *
     * @param count Anzahl der bestätigten Links
     */
    public void confirmPendingLinks(int count) {
        this.pendingLinks = Math.max(0, this.pendingLinks - count);
    }

    /**
     * Fügt einen MirrorNode zur Liste der verbundenen Mirrors hinzu.
     *
     * @param mirrorNode Der hinzuzufügende MirrorNode
     */
    public void addMirrorNode(MirrorNode mirrorNode) {
        if (mirrorNode != null && !mirrorNodeChildren.contains(mirrorNode)) {
            mirrorNodeChildren.add(mirrorNode);
        }
    }

    /**
     * Entfernt einen MirrorNode aus der Liste der verbundenen Mirrors.
     *
     * @param mirrorNode Der zu entfernende MirrorNode
     */
    public void removeMirrorNode(MirrorNode mirrorNode) {
        mirrorNodeChildren.remove(mirrorNode);
    }

    /**
     * Gibt das zugeordnete Mirror zurück.
     *
     * @return Das Mirror-Objekt oder null
     */
    public Mirror getMirror() {
        return mirror;
    }

    /**
     * Setzt das zugeordnete Mirror.
     *
     * @param mirror Das zu setzende Mirror
     */
    public void setMirror(Mirror mirror) {
        this.mirror = mirror;
    }

    /**
     * Gibt die Anzahl der geplanten Links zurück.
     *
     * @return Anzahl der geplanten Links
     */
    public int getPendingLinks() {
        return pendingLinks;
    }

    /**
     * Überprüft, ob dieser Knoten mit einem anderen MirrorNode verlinkt ist.
     *
     * @param other Der andere MirrorNode
     * @return true wenn verlinkt
     */
    public boolean isLinkedWith(MirrorNode other) {
        if (other == null || other.getMirror() == null || this.mirror == null) {
            return false;
        }

        return links.stream().anyMatch(link ->
                link.getSource().equals(other.getMirror()) ||
                        link.getTarget().equals(other.getMirror())
        );
    }

    @Override
    public void addChild(TreeNode child) {
        super.addChild(child);
        if (child instanceof MirrorNode) {
            addMirrorNode((MirrorNode) child);
        }
    }

    @Override
    public String toString() {
        return "MirrorNode{" +
                "id=" + getId() +
                ", mirror=" + (mirror != null ? mirror.getID() : "null") +
                ", links=" + links.size() +
                ", children=" + getChildren().size() +
                ", pendingLinks=" + pendingLinks +
                '}';
    }
}