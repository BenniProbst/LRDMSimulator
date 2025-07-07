
package org.lrdm.topologies.strategies;

import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.*;
import org.lrdm.topologies.node.*;
import org.lrdm.topologies.node.StructureNode.StructureType;
import org.lrdm.util.IDGenerator;

import java.util.*;

/**
 * Eine spezialisierte {@link TopologyStrategy}, die Mirrors als Stern-Topologie mit einem
 * zentralen Hub und mehreren Blättern verknüpft. Diese Strategie ist eine Portierung der {@link org.lrdm.topologies.builders.StarBuilder} Klasse.
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
 * **Stern-Constraints**: Im Gegensatz zu Ringen und Bäumen hat der Stern einen eindeutigen
 * zentralen Knoten, der alle Verbindungen kontrolliert (Single Point of Connection).
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class StarTopologyStrategy extends BuildAsSubstructure {

    // ===== STERN-SPEZIFISCHE KONFIGURATION =====
    private int minStarSize = 3;
    private boolean allowStarExpansion = true;
    private boolean allowCenterRotation = false; // Ob Zentrum gewechselt werden kann

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

    public StarTopologyStrategy(int minStarSize, boolean allowStarExpansion, boolean allowCenterRotation) {
        super();
        this.minStarSize = Math.max(3, minStarSize);
        this.allowStarExpansion = allowStarExpansion;
        this.allowCenterRotation = allowCenterRotation;
    }

    // ===== ÜBERSCHREIBUNG DER BUILD-AS-SUBSTRUCTURE-METHODEN =====

    /**
     * **PLANUNGSEBENE**: Erstellt die Stern-Struktur mit zentralem Hub.
     * Überschreibt BuildAsSubstructure für Stern-spezifische Logik.
     * Portiert die buildStar-Logik aus StarBuilder.
     */
    @Override
    protected MirrorNode buildStructure(int totalNodes, int simTime, Properties props) {
        if (totalNodes < minStarSize || !mirrorIterator.hasNext()) return null;

        // Erstelle Zentrum
        Mirror centerMirror = mirrorIterator.next();
        StarMirrorNode center = new StarMirrorNode(centerMirror.getID(), centerMirror);
        center.setHead(true);
        this.addToStructureNodes(center);

        // Erstelle Blätter
        List<StarMirrorNode> leaves = new ArrayList<>();
        for (int i = 1; i < totalNodes; i++) {
            if (!mirrorIterator.hasNext()) break;
            
            Mirror leafMirror = mirrorIterator.next();
            StarMirrorNode leaf = new StarMirrorNode(leafMirror.getID(), leafMirror);
            leaves.add(leaf);
            this.addToStructureNodes(leaf);
        }

        if (leaves.size() < minStarSize - 1) return null;

        // Strukturplanung: Erstellen von Stern-Verbindungen
        buildStarStructureWithLinks(center, leaves, simTime, props);

        return center;
    }

    /**
     * **PLANUNGSEBENE**: Fügt neue Knoten zur Stern-Struktur hinzu.
     * Überschreibt BuildAsSubstructure für Stern-Erweiterung am Zentrum.
     * Portiert die addNodes-Logik aus StarBuilder.
     */
    @Override
    protected int addNodesToStructure(int nodesToAdd) {
        if (nodesToAdd <= 0 || !allowStarExpansion) return 0;

        StarMirrorNode center = getStarCenter();
        if (center == null) return 0;

        int actuallyAdded = 0;

        // Stern-Erweiterung: Neue Blätter werden direkt am Zentrum angehängt
        while (actuallyAdded < nodesToAdd && mirrorIterator.hasNext()) {
            // Strukturplanung: Erstelle neues Blatt
            StarMirrorNode newLeaf = createStarLeaf(0, new Properties());
            if (newLeaf != null) {
                // Verbinde neues Blatt mit Zentrum
                attachLeafToCenter(center, newLeaf, 0, new Properties());
                actuallyAdded++;
            }
        }

        return actuallyAdded;
    }

    /**
     * **PLANUNGSEBENE**: Entfernt Knoten aus der Stern-Struktur.
     * Überschreibt BuildAsSubstructure für Stern-erhaltende Entfernung.
     * Entfernt nur Blätter, nie das Zentrum (außer bei Center-Rotation).
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

        // Stern-Entfernung: Entferne nur Blätter, nicht das Zentrum
        for (int i = 0; i < nodesToRemove && i < leaves.size(); i++) {
            StarMirrorNode leafToRemove = leaves.get(i);
            if (leafToRemove != null) {
                removeLeafFromStarStructuralPlanning(leafToRemove);
                actuallyRemoved++;
            }
        }

        return actuallyRemoved;
    }

    /**
     * **AUSFÜHRUNGSEBENE**: Überschreibt die Mirror-Entfernung für Stern-Erhaltung.
     * Führt Mirror-Shutdown innerhalb der strukturellen Stern-Planungsgrenzen aus.
     * Arbeitet komplementär zu removeNodesFromStructure.
     */
    @Override
    public void handleRemoveMirrors(Network n, int removeMirrors, Properties props, int simTime) {
        if (removeMirrors <= 0) return;

        List<StarMirrorNode> starNodes = getAllStarNodes();
        if (starNodes.size() - removeMirrors < minStarSize) {
            removeMirrors = starNodes.size() - minStarSize;
        }
        if (removeMirrors <= 0) return;

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
                    actuallyRemoved++;
                }
            }
        }

        // Synchronisiere Plannings- und Ausführungsebene
        removeNodesFromStructure(actuallyRemoved);
    }

    // ===== STERN-SPEZIFISCHE HILFSMETHODEN =====

    /**
     * Baut die Stern-Struktur mit echten Mirror-Links auf.
     * Portiert die Stern-Verbindungslogik aus StarBuilder.
     */
    private void buildStarStructureWithLinks(StarMirrorNode center, List<StarMirrorNode> leaves, 
                                             int simTime, Properties props) {
        if (center == null || leaves.isEmpty()) return;

        // Strukturplanung und Ausführung: Verbinde alle Blätter mit dem Zentrum
        for (StarMirrorNode leaf : leaves) {
            // StructureNode-Verbindung
            Set<StructureType> typeIds = new HashSet<>();
            typeIds.add(StructureType.STAR);

            Map<StructureType, Integer> headIds = new HashMap<>();
            headIds.put(StructureType.STAR, center.getId());

            center.addChild(leaf, typeIds, headIds);

            // Mirror-Link-Erstellung
            createStarMirrorLink(center, leaf, simTime, props);
        }
    }

    /**
     * Erstellt einen neuen Stern-Blatt-Knoten mit struktureller Planung.
     */
    private StarMirrorNode createStarLeaf(int simTime, Properties props) {
        if (!mirrorIterator.hasNext()) return null;

        Mirror mirror = mirrorIterator.next();
        StarMirrorNode starLeaf = new StarMirrorNode(mirror.getID(), mirror);
        this.addToStructureNodes(starLeaf);
        
        return starLeaf;
    }

    /**
     * Verbindet ein neues Blatt mit dem Stern-Zentrum.
     * Portiert die Stern-Erweiterungslogik aus StarBuilder.
     */
    private void attachLeafToCenter(StarMirrorNode center, StarMirrorNode newLeaf, 
                                    int simTime, Properties props) {
        if (center == null || newLeaf == null) return;

        // Strukturplanung: Füge neues Blatt zum Zentrum hinzu
        Set<StructureType> typeIds = new HashSet<>();
        typeIds.add(StructureType.STAR);

        Map<StructureType, Integer> headIds = new HashMap<>();
        headIds.put(StructureType.STAR, center.getId());

        center.addChild(newLeaf, typeIds, headIds);

        // Ausführungsebene: Erstelle einen neuen Mirror-Link
        createStarMirrorLink(center, newLeaf, simTime, props);
    }

    /**
     * **PLANUNGSEBENE**: Entfernt ein Blatt aus der Stern-Struktur-Planung.
     * Arbeitet ohne Zeitbezug - nur strukturelle Stern-Änderung.
     */
    private void removeLeafFromStarStructuralPlanning(StarMirrorNode leafToRemove) {
        if (leafToRemove == null) return;

        // Strukturplanung: Entfernte Blätter vom Zentrum
        StructureNode parent = leafToRemove.getParent();
        if (parent != null) {
            parent.removeChild(leafToRemove);
        }

        // Entferne aus Strukturverwaltung
        this.removeFromStructureNodes(leafToRemove);
    }

    /**
     * Erstellt Mirror-Link mit Stern-Validierung.
     * Ausführungsebene: Echte Mirror-Verbindungen.
     */
    private void createStarMirrorLink(StarMirrorNode center, StarMirrorNode leaf, int simTime, Properties props) {
        if (center == null || leaf == null) return;

        Mirror centerMirror = center.getMirror();
        Mirror leafMirror = leaf.getMirror();

        if (centerMirror != null && leafMirror != null) {
            if (isAlreadyConnected(centerMirror, leafMirror)) {
                Link newLink = new Link(IDGenerator.getInstance().getNextID(), centerMirror, leafMirror, simTime, props);
                // Links werden über das Network verwaltet
                if (network != null) {
                    network.getLinks().add(newLink);
                }
            }
        }
    }

    /**
     * Prüft bestehende Mirror-Verbindungen.
     */
    private boolean isAlreadyConnected(Mirror mirror1, Mirror mirror2) {
        if (mirror1 == null || mirror2 == null) return true;

        for (Link link : mirror1.getLinks()) {
            if ((link.getSource().equals(mirror1) && link.getTarget().equals(mirror2)) ||
                (link.getSource().equals(mirror2) && link.getTarget().equals(mirror1))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Findet alle Stern-Blätter.
     * Portiert die findStarLeaves-Logik aus StarBuilder.
     */
    private List<StarMirrorNode> findStarLeaves() {
        List<StarMirrorNode> leaves = new ArrayList<>();
        
        for (MirrorNode node : getAllStructureNodes()) {
            if (node instanceof StarMirrorNode starNode) {
                // Blatt = hat Parent (Zentrum) aber keine Kinder
                if (starNode.getParent() != null && starNode.getChildren().isEmpty()) {
                    leaves.add(starNode);
                }
            }
        }
        
        return leaves;
    }

    // ===== TYPSICHERE HILFSMETHODEN =====

    /**
     * Gibt das Stern-Zentrum zurück.
     */
    private StarMirrorNode getStarCenter() {
        MirrorNode root = getCurrentStructureRoot();
        return (root instanceof StarMirrorNode) ? (StarMirrorNode) root : null;
    }

    /**
     * Gibt alle Stern-Knoten als typisierte Liste zurück.
     */
    private List<StarMirrorNode> getAllStarNodes() {
        List<StarMirrorNode> starNodes = new ArrayList<>();
        
        for (MirrorNode node : getAllStructureNodes()) {
            if (node instanceof StarMirrorNode) {
                starNodes.add((StarMirrorNode) node);
            }
        }
        
        return starNodes;
    }

    /**
     * Prüft, ob ein Knoten das Stern-Zentrum ist.
     */
    private boolean isStarCenter(StarMirrorNode node) {
        if (node == null) return false;
        
        // Zentrum = hat keine Parent und mindestens ein Kind
        return node.getParent() == null && !node.getChildren().isEmpty();
    }

    /**
     * Prüft, ob ein Knoten ein Stern-Blatt ist.
     */
    private boolean isStarLeaf(StarMirrorNode node) {
        if (node == null) return false;
        
        // Blatt = hat Parent aber keine Kinder
        return node.getParent() != null && node.getChildren().isEmpty();
    }

    /**
     * Rotiert das Stern-Zentrum zu einem anderen Knoten (falls erlaubt).
     * Erweiterte Stern-Funktionalität für dynamische Zentrum-Auswahl.
     */
    private boolean rotateCenterToNode(StarMirrorNode newCenter) {
        if (!allowCenterRotation || newCenter == null) return false;

        StarMirrorNode currentCenter = getStarCenter();
        if (currentCenter == null || newCenter.equals(currentCenter)) return false;

        // Implementierung der Zentrum-Rotation würde hier stehen
        // (Komplex, da alle Verbindungen neu organisiert werden müssen)
        
        return false; // Für jetzt nicht implementiert
    }

    // ===== TOPOLOGY STRATEGY INTERFACE IMPLEMENTATION =====

    /**
     * Initializes the network by connecting mirrors in a star topology.
     *
     * @param n the {@link Network}
     * @param props {@link Properties} of the simulation
     * @return {@link Set} of all {@link Link}s created
     */
    @Override
    public Set<Link> initNetwork(Network n, Properties props) {
        this.network = n;
        this.idGenerator = IDGenerator.getInstance();
        
        if (n.getNumMirrors() < minStarSize) {
            return new HashSet<>();
        }

        this.mirrorIterator = n.getMirrors().iterator();
        MirrorNode root = buildStructure(n.getNumMirrors(), 0, props);
        
        if (root != null) {
            setCurrentStructureRoot(root);
            return buildAndConnectLinks(root, props);
        }
        
        return new HashSet<>();
    }

    /**
     * Startet das Netzwerk komplett neu mit der Stern-Topologie.
     *
     * @param n Das Netzwerk
     * @param props Simulation Properties
     * @param simTime Aktuelle Simulationszeit
     */
    @Override
    public void restartNetwork(Network n, Properties props, int simTime) {
        // Lösche alle bestehenden Links
        super.restartNetwork(n, props, simTime);
        
        // Baue Netzwerk neu auf
        initNetwork(n, props);
    }

    /**
     * Adds the requested number of mirrors to the network and connects them accordingly.
     *
     * @param n the {@link Network}
     * @param newMirrors number of mirrors to add
     * @param props {@link Properties} of the simulation
     * @param simTime current simulation time
     */
    @Override
    public void handleAddNewMirrors(Network n, int newMirrors, Properties props, int simTime) {
        if (newMirrors <= 0) return;

        this.network = n;
        
        // Füge neue Mirrors zum Netzwerk hinzu
        List<Mirror> addedMirrors = createMirrors(newMirrors, simTime, props);
        n.getMirrors().addAll(addedMirrors);
        
        // Aktualisiere Mirror-Iterator für neue Mirrors
        this.mirrorIterator = addedMirrors.iterator();
        
        // Füge Knoten zur bestehenden Struktur hinzu
        addNodesToStructure(newMirrors);
        
        // Erstelle Links für die neue Struktur
        MirrorNode root = getCurrentStructureRoot();
        if (root != null) {
            buildAndConnectLinks(root, props);
        }
    }

    /**
     * Returns the expected number of total links in the network according to the star topology.
     * For n mirrors, the number of links is (n-1) (center connected to all leaves).
     *
     * @param n {@link Network} the network
     * @return number of total links expected for the network
     */
    @Override
    public int getNumTargetLinks(Network n) {
        if (n == null) return 0;
        
        int numMirrors = n.getNumMirrors();
        return calculateExpectedLinks(numMirrors);
    }

    /**
     * Berechnet die erwartete Anzahl der Links, wenn die gegebene Aktion ausgeführt wird.
     * Stern-spezifische Implementierung basierend auf den drei Action-Typen:
     * - MirrorChange: Verändert Stern-Größe dynamisch (n Mirrors → n-1 Links)
     * - TargetLinkChange: Hat BEGRENZTEN Effekt bei Stern (max. n-1 Links möglich)
     * - TopologyChange: Komplette Rekonstruktion mit verfügbaren Mirrors
     *
     * @param a Die Action, deren Auswirkungen berechnet werden sollen
     * @return Anzahl der erwarteten Links nach Ausführung der Action
     */
    @Override
    public int getPredictedNumTargetLinks(Action a) {
        if (a == null) {
            // Fallback: Verwende aktuelle Netzwerk-Link-Anzahl
            return network != null ? getNumTargetLinks(network) : 0;
        }

        // 1. MirrorChange: Stern-Größe ändert sich dynamisch
        if (a instanceof MirrorChange mirrorChange) {
            int newMirrorCount = mirrorChange.getNewMirrors();
            return calculateExpectedLinks(newMirrorCount);
        }

        // 2. TargetLinkChange: BEGRENZTER Effekt bei Stern-Topologie
        // Stern-Constraint: Maximal (n-1) Links möglich für n Knoten (alle durch Zentrum)
        if (a instanceof TargetLinkChange targetLinkChange) {
            if (network != null) {
                int currentMirrors = network.getNumMirrors();
                int requestedTotalLinks = targetLinkChange.getNewLinksPerMirror() * currentMirrors;
                int maxPossibleLinks = calculateExpectedLinks(currentMirrors);
                
                // Stern-Topologie kann nur bis zu (n-1) Links haben
                return Math.min(requestedTotalLinks, maxPossibleLinks);
            }
            return 0;
        }

        // 3. TopologyChange: Komplette Rekonstruktion
        if (a instanceof TopologyChange topologyChange) {
            TopologyStrategy newTopology = topologyChange.getNewTopology();
            
            // Wenn neue Topologie auch Stern ist: Verwende aktuelle Mirror-Anzahl
            if (newTopology instanceof StarTopologyStrategy) {
                int currentMirrors = network != null ? network.getNumMirrors() : 0;
                return calculateExpectedLinks(currentMirrors);
            }
            
            // Andere Topologie: Delegierte an neue Strategie
            if (network != null) {
                return newTopology.getNumTargetLinks(network);
            }
            return 0;
        }

        // Fallback: Aktuelle Stern-Link-Anzahl über getNumTargetLinks
        return network != null ? getNumTargetLinks(network) : 0;
    }

    /**
     * Baut die tatsächlichen Links zwischen den Mirrors basierend auf der StructureNode-Struktur auf.
     *
     * @param root Die Root-Node der Struktur
     * @param props Simulation Properties
     * @return Set aller erstellten Links
     */
    @Override
    protected Set<Link> buildAndConnectLinks(MirrorNode root, Properties props) {
        Set<Link> createdLinks = new HashSet<>();
        
        if (root == null || network == null) return createdLinks;

        // Stern-spezifisch: Nur Zentrum → Blattverbindungen
        if (root instanceof StarMirrorNode center) {
            Mirror centerMirror = center.getMirror();
            
            if (centerMirror != null) {
                for (StructureNode child : center.getChildren()) {
                    if (child instanceof StarMirrorNode leaf) {
                        Mirror leafMirror = leaf.getMirror();
                        
                        if (leafMirror != null && isAlreadyConnected(centerMirror, leafMirror)) {
                            Link newLink = new Link(IDGenerator.getInstance().getNextID(), centerMirror, leafMirror, 0, props);
                            network.getLinks().add(newLink);
                            createdLinks.add(newLink);
                        }
                    }
                }
            }
        }

        return createdLinks;
    }

    // ===== STERN-ANALYSE =====

    /**
     * Prüft, ob die Stern-Struktur intakt ist.
     */
    public boolean isStarIntact() {
        StarMirrorNode center = getStarCenter();
        if (center == null) return false;

        List<StarMirrorNode> leaves = findStarLeaves();
        return leaves.size() >= (minStarSize - 1); // Mindestens (minStarSize-1) Blätter
    }

    /**
     * Berechnet die Stern-Effizienz (wie gut das Zentrum genutzt wird).
     */
    public double calculateStarEfficiency() {
        StarMirrorNode center = getStarCenter();
        if (center == null) return 0.0;

        List<StarMirrorNode> leaves = findStarLeaves();
        List<StarMirrorNode> allNodes = getAllStarNodes();
        
        if (allNodes.size() <= 1) return 1.0;

        // Effizienz = Verhältnis von Blättern zu möglichen Verbindungen vom Zentrum
        return (double) leaves.size() / (allNodes.size() - 1);
    }

    /**
     * Gibt detaillierte Stern-Informationen zurück.
     */
    public Map<String, Object> getDetailedStarInfo() {
        Map<String, Object> info = new HashMap<>();
        List<StarMirrorNode> starNodes = getAllStarNodes();
        List<StarMirrorNode> leaves = findStarLeaves();
        StarMirrorNode center = getStarCenter();

        info.put("totalNodes", starNodes.size());
        info.put("totalLinks", calculateExpectedLinks(starNodes.size()));
        info.put("centerNode", center != null ? center.getId() : null);
        info.put("leaves", leaves.size());
        info.put("isIntact", isStarIntact());
        info.put("efficiency", calculateStarEfficiency());
        info.put("minStarSize", minStarSize);
        info.put("allowExpansion", allowStarExpansion);
        info.put("allowCenterRotation", allowCenterRotation);

        return info;
    }

    // ===== HILFSMETHODEN =====

    /**
     * Berechnet die erwartete Link-Anzahl für eine gegebene Knotenzahl.
     * Für Stern-Topologie: (n-1) Links für n Knoten.
     *
     * @param nodeCount Anzahl der Knoten
     * @return Erwartete Anzahl der Links für Stern-Topologie
     */
    public static int calculateExpectedLinks(int nodeCount) {
        return Math.max(0, nodeCount - 1);
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

    public boolean isAllowCenterRotation() {
        return allowCenterRotation;
    }

    public void setAllowCenterRotation(boolean allowCenterRotation) {
        this.allowCenterRotation = allowCenterRotation;
    }

    @Override
    public String toString() {
        return "StarTopologyStrategy{" +
                "minStarSize=" + minStarSize +
                ", allowExpansion=" + allowStarExpansion +
                ", allowCenterRotation=" + allowCenterRotation +
                ", substructureId=" + getSubstructureId() +
                '}';
    }
}
