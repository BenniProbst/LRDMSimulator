
package org.lrdm.topologies.strategies;

import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.Action;
import org.lrdm.effectors.MirrorChange;
import org.lrdm.effectors.TargetLinkChange;
import org.lrdm.effectors.TopologyChange;
import org.lrdm.topologies.node.MirrorNode;
import org.lrdm.topologies.node.StarMirrorNode;
import org.lrdm.topologies.node.StructureNode;

import java.util.*;

/**
 * Eine spezialisierte {@link TopologyStrategy}, die Mirrors als Stern-Topologie mit einem
 * zentralen Hub und mehreren Blättern verknüpft. Basiert auf dem modernisierten
 * BuildAsSubstructure-Framework.
 * <p>
 * **Stern-Topologie-Eigenschaften**:
 * - Ein zentraler Knoten (Hub) ist mit allen anderen Knoten (Blättern) verbunden
 * - Blätter sind nur mit dem Zentrum verbunden, nicht untereinander
 * - Bildet eine zentrale Hub-and-Spoke-Struktur
 * - Benötigt mindestens 3 Knoten für einen funktionsfähigen Stern (1 Zentrum + 2 Blätter)
 * - Anzahl der Links ist (n-1) für n Knoten (Baum-Eigenschaft mit Stern-Form)
 * - Verwendet {@link StarMirrorNode} für spezifische Stern-Funktionalität
 * <p>
 * **Planungsebene vs. Ausführungsebene**:
 * - Planungsebene: `removeNodesFromStructure()` - plant strukturelle Stern-Änderungen ohne Zeitbezug
 * - Ausführungsebene: `handleRemoveMirrors()` - führt Mirror-Shutdown innerhalb der Stern-Planungsgrenzen aus
 * - Automatisches Mitwachsen: MirrorNode-Ebene passt sich an StructureNode-Stern-Planung an
 * <p>
 * **Stern-Constraints**: Im Gegensatz zu Ringen und Linien hat der Stern einen eindeutigen
 * zentralen Knoten, der alle Verbindungen kontrolliert (Single Point of Connection).
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class StarTopologyStrategy extends BuildAsSubstructure {

    // ===== STERN-SPEZIFISCHE KONFIGURATION =====
    private int minStarSize = 1;

    // ===== KONSTRUKTOREN =====

    public StarTopologyStrategy() {
        super();
    }

    // ===== ÜBERSCHREIBUNG DER BUILD-AS-SUBSTRUCTURE-METHODEN =====

    /**
     * **PLANUNGSEBENE**: Erstellt die Stern-Struktur (Zentrum + Blätter).
     * Überschreibt BuildAsSubstructure für Stern-spezifische Logik.
     * NUR STRUKTURPLANUNG - keine Mirror-Links!
     */
    @Override
    protected MirrorNode buildStructure(int totalNodes) {
        if (totalNodes < minStarSize) return null;

        // Erstelle das Zentrum des Sterns
        StarMirrorNode center = getMirrorNodeFromIterator();
        center.setHead(true);
        setCurrentStructureRoot(center);

        // Erstelle Blätter für den Stern
        List<StarMirrorNode> leaves = new ArrayList<>();
        for (int i = 1; i < totalNodes; i++) {
            if (!network.getMirrorCursor().hasNextMirror()) break;
            StarMirrorNode leaf = getMirrorNodeFromIterator();
            leaves.add(leaf);
            addToStructureNodes(leaf);
        }

        if (leaves.size() < minStarSize - 1) return null;

        // **NUR STRUKTURPLANUNG**: Erstelle Stern-Struktur (Zentrum ↔ Blätter)
        buildStarStructurePlanning(center, leaves);

        return center;
    }

    /**
     * **PLANUNGSEBENE**: Fügt neue Knoten zur Stern-Struktur hinzu.
     * Stern-Erweiterung: Neue Blätter werden direkt am Zentrum angehängt.
     * NUR STRUKTURPLANUNG - keine Mirror-Links!
     */
    @Override
    protected int addNodesToStructure(Set<Mirror> nodesToAdd) {
        if (nodesToAdd.isEmpty() || getCurrentStructureRoot() == null) {
            return 0;
        }

        StarMirrorNode center = getStarCenter();
        if (center == null) return 0;

        int actuallyAdded = 0;

        // Stern-Erweiterung: Neue Blätter am Zentrum anhängen
        for (int i = 0; i < nodesToAdd.size(); i++) {
            // **NUR STRUKTURPLANUNG**: Erstelle neues Blatt
            StarMirrorNode newLeaf = getMirrorNodeFromIterator();
            if (newLeaf != null) {
                // **NUR STRUKTURPLANUNG**: Verbinde Blatt mit Zentrum
                attachLeafToCenterStructuralPlanning(center, newLeaf);
                actuallyAdded++;
            }
        }

        return actuallyAdded;
    }

    /**
     * **PLANUNGSEBENE**: Entfernt Knoten aus der Stern-Struktur.
     * Stern-Entfernung: Entfernt nur Blätter, nie das Zentrum.
     */
    @Override
    protected Set<MirrorNode> removeNodesFromStructure(int nodesToRemove) {
        if (nodesToRemove <= 0 || getCurrentStructureRoot() == null) {
            return new HashSet<>();
        }

        List<StarMirrorNode> starNodes = getAllStarNodes();
        if (starNodes.size() - nodesToRemove < minStarSize) {
            nodesToRemove = starNodes.size() - minStarSize;
        }

        List<StarMirrorNode> leaves = findStarLeaves();
        Set<MirrorNode> removedNodes = new HashSet<>();

        // Stern-Entfernung: Entferne nur Blätter, nie das Zentrum
        for (int i = 0; i < nodesToRemove && i < leaves.size(); i++) {
            StarMirrorNode leafToRemove = leaves.get(i);
            if (leafToRemove != null) {
                removeNodeFromStructuralPlanning(leafToRemove,
                        Set.of(StructureNode.StructureType.DEFAULT,StructureNode.StructureType.MIRROR,StructureNode.StructureType.STAR));
                removedNodes.add(leafToRemove);
            }
        }

        return removedNodes;
    }

    @Override
    protected boolean validateTopology() {
        return isStarIntact();
    }

    /**
     * Factory-Methode für Stern-spezifische MirrorNode-Erstellung.
     * Überschreibt BuildAsSubstructure für die StarMirrorNode-Erstellung.
     *
     * @param mirror Der Mirror, für den ein MirrorNode erstellt werden soll
     * @return Neuer StarMirrorNode
     */
    @Override
    protected MirrorNode createMirrorNodeForMirror(Mirror mirror) {
        return new StarMirrorNode(mirror.getID(), mirror);
    }

    // ===== TOPOLOGY STRATEGY METHODEN =====

    /**
     * Gibt die erwartete Anzahl Links für das Netzwerk gemäß Stern-Topologie zurück.
     * Stern-Topologie: (n-1) Links für n Knoten (alle Blätter mit Zentrum verbunden).
     *
     * @param n Das Netzwerk
     * @return Anzahl der erwarteten Links für Stern-Topologie
     */
    @Override
    public int getNumTargetLinks(Network n) {
        if (n == null) return 0;
        return calculateExpectedLinks(n.getNumMirrors());
    }

    /**
     * Berechnet die erwartete Anzahl der Links, wenn die gegebene Aktion ausgeführt wird.
     * Stern-spezifische Implementierung basierend auf den drei Action-Typen.
     * Überschreibt die abstrakte Methode aus TopologyStrategy.
     *
     * @param a Die Action, deren Auswirkungen berechnet werden sollen
     * @return Anzahl der erwarteten Links nach Ausführung der Action
     */
    @Override
    public int getPredictedNumTargetLinks(Action a) {
        if (a instanceof MirrorChange mirrorChange) {
            // MirrorChange: Neue Mirror-Anzahl → neue Link-Anzahl
            int newMirrors = mirrorChange.getNewMirrors();
            return calculateExpectedLinks(newMirrors);
        } else if (a instanceof TargetLinkChange targetLinkChange) {
            // TargetLinkChange: Begrenzt durch Stern-Eigenschaft
            Network network = targetLinkChange.getNetwork();
            int maxPossibleLinks = network.getNumMirrors() - 1; // Stern: (n-1) Links für n Knoten
            int requestedTotalLinks = targetLinkChange.getNewLinksPerMirror() * network.getNumMirrors() / 2;
            return Math.min(requestedTotalLinks, maxPossibleLinks);
        } else if (a instanceof TopologyChange topologyChange) {
            // TopologyChange: Delegiere an neue Strategie
            return topologyChange.getNewTopology().getPredictedNumTargetLinks(a);
        }

        return 0;
    }

    // ===== STERN-SPEZIFISCHE HILFSMETHODEN - NUR PLANUNGSEBENE =====

    /**
     * **NUR PLANUNGSEBENE**: Baut die Stern-Struktur auf (Zentrum ↔ Blätter).
     * Erstellt KEINE Mirror-Links - nur StructureNode-Verbindungen!
     */
    private void buildStarStructurePlanning(StarMirrorNode center, List<StarMirrorNode> leaves) {
        if (center == null || leaves.isEmpty()) return;

        // Verbinde jedes Blatt mit dem Zentrum (nur StructureNode-Ebene)
        for (StarMirrorNode leaf : leaves) {
            // Zentrum → Blatt (Parent-Child-Beziehung)
            center.addChild(leaf);
            leaf.setParent(center);
        }

        // KEINE Mirror-Link-Erstellung hier! Nur Strukturplanung!
    }

    /**
     * **NUR PLANUNGSEBENE**: Verbindet ein neues Blatt mit dem Zentrum.
     * Erstellt KEINE Mirror-Links - nur StructureNode-Verbindungen!
     */
    private void attachLeafToCenterStructuralPlanning(StarMirrorNode center, StarMirrorNode newLeaf) {
        if (center == null || newLeaf == null) return;

        // Verbinde neues Blatt mit Zentrum (nur StructureNode-Ebene)
        center.addChild(newLeaf);
        newLeaf.setParent(center);

        // KEINE Mirror-Link-Erstellung hier! Nur Strukturplanung!
    }

    /**
     * Findet alle Blätter des Sterns (alle Nicht-Zentrum-Knoten).
     */
    private List<StarMirrorNode> findStarLeaves() {
        List<StarMirrorNode> leaves = new ArrayList<>();
        StarMirrorNode center = getStarCenter();

        if (center != null) {
            for (StarMirrorNode node : getAllStarNodes()) {
                if (node != center) {
                    leaves.add(node);
                }
            }
        }

        return leaves;
    }

    /**
     * Prüft, ob die Stern-Struktur intakt ist.
     */
    private boolean isStarIntact() {
        StarMirrorNode center = getStarCenter();
        if (center == null) return false;

        List<StarMirrorNode> starNodes = getAllStarNodes();
        if (starNodes.size() < minStarSize) return false;

        // Prüfe, ob alle Nicht-Zentrum-Knoten Blätter sind (genau ein Parent = Zentrum)
        for (StarMirrorNode node : starNodes) {
            if (node != center) {
                if (node.getParent() != center || !node.getChildren().isEmpty()) {
                    return false; // Blatt muss Zentrum als Parent haben und keine Kinder
                }
            }
        }

        // Prüfe, ob Zentrum die richtige Anzahl Kinder hat
        return center.getChildren().size() == starNodes.size() - 1;
    }

    /**
     * Berechnet die erwartete Link-Anzahl für eine gegebene Knotenzahl.
     * Stern-Topologie: (n-1) Links für n Knoten (alle Blätter mit Zentrum verbunden).
     */
    private int calculateExpectedLinks(int nodeCount) {
        if (nodeCount < minStarSize) return 0;
        return nodeCount - 1; // Stern: (n-1) Links für n Knoten
    }

    // ===== TYPSICHERE HILFSMETHODEN =====

    /**
     * Gibt das Stern-Zentrum zurück.
     */
    private StarMirrorNode getStarCenter() {
        MirrorNode root = getCurrentStructureRoot();
        return (root instanceof StarMirrorNode && root.isHead()) ? (StarMirrorNode) root : null;
    }

    /**
     * Gibt alle Stern-Knoten als typisierte Liste zurück.
     */
    private List<StarMirrorNode> getAllStarNodes() {
        return getAllStructureNodes().stream()
                .filter(node -> node instanceof StarMirrorNode)
                .map(node -> (StarMirrorNode) node)
                .collect(java.util.stream.Collectors.toList());
    }

    // ===== KONFIGURATION =====

    public int getMinStarSize() {
        return minStarSize;
    }

    public void setMinStarSize(int minStarSize) {
        this.minStarSize = Math.max(3, minStarSize);
    }

    /**
     * Erstellt einen neuen MirrorNode mit Mirror aus dem Iterator.
     * AKTUALISIERT: Fügt den Knoten automatisch zu structureNodes hinzu.
     *
     * @return Neuer MirrorNode mit zugeordnetem Mirror oder null
     */
    @Override
    protected StarMirrorNode getMirrorNodeFromIterator() {
        if (network.getMirrorCursor().hasNextMirror()) {
            StarMirrorNode node = (StarMirrorNode) super.getMirrorNodeFromIterator();
            node.addNodeType(StructureNode.StructureType.STAR);
            return node;
        }
        return null;
    }

    @Override
    public String toString() {
        return "StarTopologyStrategy{" +
                "minStarSize=" + minStarSize +
                ", allowStarExpansion=" + true +
                ", nodes=" + getAllStarNodes().size() +
                ", center=" + (getStarCenter() != null ? getStarCenter().getId() : "none") +
                '}';
    }
}