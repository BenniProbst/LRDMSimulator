
package org.lrdm.topologies.strategies;

import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.Action;
import org.lrdm.effectors.MirrorChange;
import org.lrdm.effectors.TargetLinkChange;
import org.lrdm.effectors.TopologyChange;
import org.lrdm.topologies.node.MirrorNode;
import org.lrdm.topologies.node.StarMirrorNode;

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
    private int minStarSize = 3;
    private boolean allowStarExpansion = true;

    // ===== KONSTRUKTOREN =====

    public StarTopologyStrategy() {
        super();
    }

    public StarTopologyStrategy(int minStarSize) {
        super();
        this.minStarSize = Math.max(3, minStarSize);
    }

    public StarTopologyStrategy(int minStarSize, boolean allowStarExpansion) {
        super();
        this.minStarSize = Math.max(3, minStarSize);
        this.allowStarExpansion = allowStarExpansion;
    }

    // ===== ÜBERSCHREIBUNG DER BUILD-AS-SUBSTRUCTURE-METHODEN =====

    /**
     * **PLANUNGSEBENE**: Erstellt die Stern-Struktur (Zentrum + Blätter).
     * Überschreibt BuildAsSubstructure für Stern-spezifische Logik.
     * NUR STRUKTURPLANUNG - keine Mirror-Links!
     */
    @Override
    protected MirrorNode buildStructure(int totalNodes, Properties props) {
        if (totalNodes < minStarSize || !hasNextMirror()) return null;

        // Erstelle das Zentrum des Sterns
        Mirror centerMirror = getNextMirror();
        assert centerMirror != null;
        StarMirrorNode center = new StarMirrorNode(centerMirror.getID(), centerMirror);
        center.setHead(true);
        addToStructureNodes(center);
        setCurrentStructureRoot(center);

        // Erstelle Blätter für den Stern
        List<StarMirrorNode> leaves = new ArrayList<>();
        for (int i = 1; i < totalNodes; i++) {
            if (!hasNextMirror()) break;

            Mirror leafMirror = getNextMirror();
            StarMirrorNode leaf = new StarMirrorNode(leafMirror.getID(), leafMirror);
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
        if (nodesToAdd.isEmpty() || getCurrentStructureRoot() == null || !allowStarExpansion) {
            return 0;
        }

        StarMirrorNode center = getStarCenter();
        if (center == null) return 0;

        int actuallyAdded = 0;

        // Stern-Erweiterung: Neue Blätter am Zentrum anhängen
        for (int i = 0; i < nodesToAdd.size() && hasNextMirror(); i++) {
            // **NUR STRUKTURPLANUNG**: Erstelle neues Blatt
            StarMirrorNode newLeaf = createStarNodeForStructure();
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
    protected int removeNodesFromStructure(int nodesToRemove) {
        if (nodesToRemove <= 0) return 0;

        List<StarMirrorNode> starNodes = getAllStarNodes();
        if (starNodes.size() - nodesToRemove < minStarSize) {
            nodesToRemove = starNodes.size() - minStarSize;
        }
        if (nodesToRemove <= 0) return 0;

        int actuallyRemoved = 0;
        List<StarMirrorNode> leaves = findStarLeaves();

        // Stern-Entfernung: Entferne nur Blätter, nie das Zentrum
        for (int i = 0; i < nodesToRemove && i < leaves.size(); i++) {
            StarMirrorNode leafToRemove = leaves.get(i);
            if (leafToRemove != null) {
                removeLeafFromStarStructuralPlanning(leafToRemove);
                actuallyRemoved++;
            }
        }

        return actuallyRemoved;
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

    /**
     * **AUSFÜHRUNGSEBENE**: Überschreibt die Mirror-Entfernung für Stern-Erhaltung.
     * Führt Mirror-Shutdown innerhalb der strukturellen Stern-Planungsgrenzen aus.
     * Arbeitet komplementär zu removeNodesFromStructure.
     *
     * @param n Das Netzwerk
     * @param removeMirrors Anzahl zu entfernender Mirrors
     * @param props Properties der Simulation
     * @param simTime Aktuelle Simulationszeit
     * @return Set der entfernten Mirrors
     */
    @Override
    public Set<Mirror> handleRemoveMirrors(Network n, int removeMirrors, Properties props, int simTime) {
        if (removeMirrors <= 0) {
            return new HashSet<>();
        }

        List<StarMirrorNode> starNodes = getAllStarNodes();
        if (starNodes.size() - removeMirrors < minStarSize) {
            removeMirrors = starNodes.size() - minStarSize;
        }
        if (removeMirrors <= 0) {
            return new HashSet<>();
        }

        Set<Mirror> cleanedMirrors = new HashSet<>();
        int actuallyRemoved = 0;
        List<StarMirrorNode> leaves = findStarLeaves();

        // Ausführungsebene: Stern-bewusste Mirror-Entfernung (nur Blätter)
        for (int i = 0; i < removeMirrors && i < leaves.size(); i++) {
            StarMirrorNode targetLeaf = leaves.get(i);
            if (targetLeaf != null) {
                Mirror targetMirror = targetLeaf.getMirror();
                if (targetMirror != null) {
                    // Mirror-Shutdown auf Ausführungsebene
                    targetMirror.shutdown(simTime);
                    cleanedMirrors.add(targetMirror);
                    actuallyRemoved++;
                }
            }
        }

        // Synchronisiere Plannings- und Ausführungsebene
        removeNodesFromStructure(actuallyRemoved);

        return cleanedMirrors;
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
     * **NUR PLANUNGSEBENE**: Erstellt einen neuen Stern-Knoten mit struktureller Planung.
     */
    private StarMirrorNode createStarNodeForStructure() {
        if (!hasNextMirror()) return null;

        Mirror mirror = getNextMirror();
        assert mirror != null;
        StarMirrorNode starNode = new StarMirrorNode(mirror.getID(), mirror);
        addToStructureNodes(starNode);

        return starNode;
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
     * **PLANUNGSEBENE**: Entfernt ein Blatt aus der Stern-Struktur-Planung.
     * Arbeitet ohne Zeitbezug - nur strukturelle Stern-Änderung.
     */
    private void removeLeafFromStarStructuralPlanning(StarMirrorNode leafToRemove) {
        if (leafToRemove == null) return;

        StarMirrorNode center = (StarMirrorNode) leafToRemove.getParent();
        if (center != null) {
            // Entferne Verbindung zwischen Zentrum und Blatt
            center.removeChild(leafToRemove);
            leafToRemove.setParent(null);
        }

        // Entferne Blatt aus der Struktur
        removeFromStructureNodes(leafToRemove);
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

    public boolean isAllowStarExpansion() {
        return allowStarExpansion;
    }

    public void setAllowStarExpansion(boolean allowStarExpansion) {
        this.allowStarExpansion = allowStarExpansion;
    }

    @Override
    public String toString() {
        return "StarTopologyStrategy{" +
                "minStarSize=" + minStarSize +
                ", allowStarExpansion=" + allowStarExpansion +
                ", nodes=" + getAllStarNodes().size() +
                ", center=" + (getStarCenter() != null ? getStarCenter().getId() : "none") +
                '}';
    }
}