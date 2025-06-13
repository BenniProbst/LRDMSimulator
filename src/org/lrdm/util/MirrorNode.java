package org.lrdm.util;

import org.lrdm.Link;
import org.lrdm.Mirror;

import java.util.*;

/**
 * Repräsentiert einen Knoten in einem Baum, der einem Mirror zugeordnet werden kann.
 * Diese Klasse trennt die Planungsschicht (TreeNode-Struktur) von der
 * Implementierungsschicht (Mirror mit Links).
 *
 * Vollständig zustandslos bezüglich Link-Informationen - alle werden dynamisch berechnet.
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
     * Setzt den Mirror für diesen Knoten.
     * Sollte nur über TreeBuilder gesetzt werden, nicht direkt.
     *
     * @param mirror Der zu setzende Mirror
     */
    protected void setMirror(Mirror mirror) {
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
     * Dies sind die Links, die laut Baumstruktur existieren sollten.
     * Stack-basierte Implementierung.
     *
     * @return Anzahl der geplanten Links
     */
    public int getNumPlannedLinks() {
        return calculatePlannedLinksFromStructure();
    }

    /**
     * Berechnet die Anzahl der entwickelten/implementierten Links.
     * Dies sind die Links, die tatsächlich zwischen Mirrors implementiert wurden.
     *
     * @return Anzahl der implementierten Links
     */
    public int getNumImplementedLinks() {
        return implementedLinks.size();
    }

    /**
     * Berechnet die Anzahl der noch ausstehenden Links.
     * Dies ist die Differenz zwischen geplanten und implementierten Links.
     *
     * @return Anzahl der ausstehenden Links
     */
    public int getNumPendingLinks() {
        return Math.max(0, getNumPlannedLinks() - getNumImplementedLinks());
    }

    /**
     * Prüft, ob dieser Knoten mit einem anderen MirrorNode verlinkt ist.
     * Funktioniert nur, wenn beide Knoten Mirrors haben und die Planungsschicht
     * mit der Implementierungsschicht umgesetzt wurde.
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
     * Entfernt einen MirrorNode (für TreeBuilder).
     *
     * @param child Der zu entfernende Kindknoten
     */
    public void removeMirrorNode(MirrorNode child) {
        removeChild(child);
        // Entferne auch alle zugehörigen implementierten Links
        if (child != null) {
            implementedLinks.removeIf(link ->
                    (link.getSource() == child.mirror) || (link.getTarget() == child.mirror));
        }
    }

    /**
     * Stack-basierte Berechnung der geplanten Links aus der TreeNode-Struktur.
     */
    private int calculatePlannedLinksFromStructure() {
        int plannedLinks = 0;

        // Stack für die Traversierung
        Stack<TreeNode> stack = new Stack<>();
        Set<TreeNode> visited = new HashSet<>();

        stack.push(this);

        while (!stack.isEmpty()) {
            TreeNode current = stack.pop();

            if (visited.contains(current)) {
                continue;
            }
            visited.add(current);

            // Jedes Kind stellt eine geplante Verbindung dar
            for (TreeNode child : current.getChildren()) {
                if (child == this || isAncestorOf((TreeNode) this, child)) {
                    plannedLinks++;
                }
                if (!visited.contains(child)) {
                    stack.push(child);
                }
            }

            // Auch Parent-Verbindung zählen, wenn dieser Knoten nicht die Wurzel ist
            TreeNode parent = current.getParent();
            if (parent != null && (current == this || isAncestorOf((TreeNode) this, current))) {
                if (current == this) {
                    plannedLinks++; // Parent-Verbindung für diesen Knoten
                }
                if (!visited.contains(parent)) {
                    stack.push(parent);
                }
            }
        }

        return plannedLinks;
    }

    /**
     * Prüft, ob eine geplante Verbindung zu einem anderen Knoten existiert.
     */
    private boolean isPlannedConnectionWith(MirrorNode other) {
        // Direkte Parent-Child-Beziehung
        if (this.getParent() == other || other.getParent() == this) {
            return true;
        }

        // Geschwister-Beziehung (gleicher Parent)
        if (this.getParent() != null && this.getParent() == other.getParent()) {
            return true;
        }

        return false;
    }

    /**
     * Prüft, ob eine implementierte Verbindung zu einem anderen Knoten existiert.
     */
    private boolean hasImplementedConnectionWith(MirrorNode other) {
        if (other == null || other.mirror == null) {
            return false;
        }

        return implementedLinks.stream().anyMatch(link ->
                link.getSource() == other.mirror || link.getTarget() == other.mirror);
    }

    /**
     * Hilfsmethode: Prüft, ob ein Knoten Vorfahre eines anderen ist.
     */
    private boolean isAncestorOf(TreeNode ancestor, TreeNode descendant) {
        TreeNode current = descendant.getParent();
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("MirrorNode{id=%d, mirror=%s, planned=%d, implemented=%d, pending=%d}",
                getId(),
                mirror != null ? mirror.getID() : "null",
                getNumPlannedLinks(),
                getNumImplementedLinks(),
                getNumPendingLinks());
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