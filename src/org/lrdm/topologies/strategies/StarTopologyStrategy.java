
package org.lrdm.topologies.strategies;

import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.*;
import org.lrdm.topologies.node.*;
import org.lrdm.topologies.node.StructureNode.StructureType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Eine spezialisierte {@link TopologyStrategy}, die Mirrors als Stern-Topologie mit einem
 * zentralen Hub und mehreren Blättern verknüpft. Diese Strategie ist eine Portierung der
 * {@link org.lrdm.topologies.strategies.StarTopologyStrategy} Klasse.
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

    /** Minimale Anzahl an Knoten für einen funktionsfähigen Stern (1 Zentrum + 2+ Blätter) */
    private int minStarSize = 3;

    /** Erlaubt die Erweiterung des Sterns durch weitere Blätter */
    private boolean allowStarExpansion = true;

    /** Ob das Zentrum zu einem anderen Knoten rotiert werden kann (erweiterte Funktionalität) */
    private boolean allowCenterRotation = false;

    // ===== KONSTRUKTOREN =====

    /**
     * Standard-Konstruktor mit Standardwerten.
     * minStarSize=3, allowStarExpansion=true, allowCenterRotation=false
     */
    public StarTopologyStrategy() {
        super();
    }

    /**
     * Konstruktor mit konfigurierbarer minimaler Stern-Größe.
     *
     * @param minStarSize Mindestanzahl von Knoten (minimum 3)
     */
    public StarTopologyStrategy(int minStarSize) {
        super();
        this.minStarSize = Math.max(3, minStarSize);
    }

    /**
     * Konstruktor mit minimaler Stern-Größe und Erweiterbarkeit.
     *
     * @param minStarSize Mindestanzahl von Knoten (minimum 3)
     * @param allowStarExpansion Ob der Stern erweitert werden kann
     */
    public StarTopologyStrategy(int minStarSize, boolean allowStarExpansion) {
        super();
        this.minStarSize = Math.max(3, minStarSize);
        this.allowStarExpansion = allowStarExpansion;
    }

    /**
     * Vollständiger Konstruktor mit allen Konfigurationsoptionen.
     *
     * @param minStarSize Mindestanzahl von Knoten (minimum 3)
     * @param allowStarExpansion Ob der Stern erweitert werden kann
     * @param allowCenterRotation Ob das Zentrum rotiert werden kann
     */
    public StarTopologyStrategy(int minStarSize, boolean allowStarExpansion, boolean allowCenterRotation) {
        super();
        this.minStarSize = Math.max(3, minStarSize);
        this.allowStarExpansion = allowStarExpansion;
        this.allowCenterRotation = allowCenterRotation;
    }

    // ===== ÜBERSCHREIBUNG DER BUILD-AS-SUBSTRUCTURE-METHODEN =====

    /**
     * **PLANUNGSEBENE**: Erstellt die initiale Stern-Struktur mit zentralem Hub und Blättern.
     * Überschreibt BuildAsSubstructure für Stern-spezifische Logik.
     * Portiert die buildStar-Logik aus StarBuilder.
     *
     * @param totalNodes Gesamtanzahl der zu erstellenden Knoten
     * @param simTime Aktuelle Simulationszeit für Link-Erstellung
     * @param props Properties der Simulation
     * @return Die Root-Node (Zentrum) der erstellten Stern-Struktur
     */
    @Override
    protected MirrorNode buildStructure(int totalNodes, int simTime, Properties props) {
        // Validierung: Mindestanzahl von Knoten und verfügbare Mirrors
        if (totalNodes < minStarSize || !mirrorIterator.hasNext()) {
            return null;
        }

        // Erstelle das Zentrum des Sterns
        Mirror centerMirror = mirrorIterator.next();
        StarMirrorNode center = new StarMirrorNode(centerMirror.getID(), centerMirror);
        center.setHead(StructureType.STAR, true); // Markiere als Zentrum
        setCurrentStructureRoot(center); // Setze als Root der Struktur

        // Erstelle Blätter für den Stern
        List<StarMirrorNode> leaves = new ArrayList<>();
        for (int i = 1; i < totalNodes; i++) {
            if (!mirrorIterator.hasNext()) break;

            Mirror leafMirror = mirrorIterator.next();
            StarMirrorNode leaf = new StarMirrorNode(leafMirror.getID(), leafMirror);
            leaves.add(leaf);
            addToStructureNodes(leaf); // Füge zur Struktur-Verwaltung hinzu
        }

        // Prüfe, ob genügend Blätter für einen validen Stern vorhanden sind
        if (leaves.size() < minStarSize - 1) {
            return null;
        }

        // Strukturplanung: Erstelle Stern-Verbindungen zwischen Zentrum und Blättern
        buildStarStructureWithLinks(center, leaves, simTime, props);

        return center;
    }

    /**
     * **PLANUNGSEBENE**: Fügt neue Knoten zur bestehenden Stern-Struktur hinzu.
     * Überschreibt BuildAsSubstructure für Stern-Erweiterung am Zentrum.
     * Neue Blätter werden immer direkt am Zentrum angehängt.
     *
     * @param nodesToAdd Anzahl der hinzuzufügenden Knoten
     * @return Tatsächliche Anzahl der hinzugefügten Knoten
     */
    @Override
    protected int addNodesToStructure(int nodesToAdd) {
        // Validierung: Positive Anzahl und Erweiterung erlaubt
        if (nodesToAdd <= 0 || !allowStarExpansion) {
            return 0;
        }

        // Hole das Zentrum der Stern-Struktur
        StarMirrorNode center = getStarCenter();
        if (center == null) {
            return 0;
        }

        int actuallyAdded = 0;

        // Stern-Erweiterung: Neue Blätter werden direkt am Zentrum angehängt
        for (int i = 0; i < nodesToAdd && mirrorIterator.hasNext(); i++) {
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
     *
     * @param nodesToRemove Anzahl der zu entfernenden Knoten
     * @return Tatsächliche Anzahl der entfernten Knoten
     */
    @Override
    protected int removeNodesFromStructure(int nodesToRemove) {
        // Validierung: Positive Anzahl
        if (nodesToRemove <= 0) {
            return 0;
        }

        // Berechne die maximale Anzahl entfernbarer Knoten (ohne Unterschreitung der Mindestgröße)
        List<StarMirrorNode> starNodes = getAllStarNodes();
        if (starNodes.size() - nodesToRemove < minStarSize) {
            nodesToRemove = starNodes.size() - minStarSize;
        }
        if (nodesToRemove <= 0) {
            return 0;
        }

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

    /**
     * **AUSFÜHRUNGSEBENE**: Überschreibt die Mirror-Entfernung für Stern-Erhaltung.
     * KORRIGIERT: Rückgabetyp von void zu Set<Mirror> geändert für Vererbungskonformität.
     * Führt Mirror-Shutdown innerhalb der strukturellen Stern-Planungsgrenzen aus.
     * Arbeitet komplementär zu removeNodesFromStructure.
     *
     * @param n Das Netzwerk
     * @param removeMirrors Anzahl der zu entfernenden Mirrors
     * @param props Properties der Simulation
     * @param simTime Aktuelle Simulationszeit
     * @return Set der entfernten Mirrors
     */
    @Override
    public Set<Mirror> handleRemoveMirrors(Network n, int removeMirrors, Properties props, int simTime) {
        // Validierung: Positive Anzahl
        if (removeMirrors <= 0) {
            return new HashSet<>();
        }

        // Hole das Zentrum der Stern-Struktur
        StarMirrorNode center = getStarCenter();
        if (center == null) {
            // Fallback: Verwende die BuildAsSubstructure-Implementierung
            return super.handleRemoveMirrors(n, removeMirrors, props, simTime);
        }

        Set<Mirror> cleanedMirrors = new HashSet<>();

        // Berechne die maximale Anzahl entfernbarer Mirrors (ohne Unterschreitung der Mindestgröße)
        List<StarMirrorNode> starNodes = getAllStarNodes();
        if (starNodes.size() - removeMirrors < minStarSize) {
            removeMirrors = starNodes.size() - minStarSize;
        }
        if (removeMirrors <= 0) {
            return cleanedMirrors;
        }

        List<StarMirrorNode> leaves = findStarLeaves();

        // Entferne Blätter (nie das Zentrum)
        for (int i = 0; i < removeMirrors && i < leaves.size(); i++) {
            StarMirrorNode leafToRemove = leaves.get(i);
            if (leafToRemove == null || leafToRemove == center) {
                continue;
            }

            Mirror mirrorToRemove = leafToRemove.getMirror();
            if (mirrorToRemove != null) {
                // Entferne alle Links (aber schalte Mirror NICHT aus - das macht der Effector)
                Set<Link> linksToRemove = new HashSet<>(mirrorToRemove.getLinks());
                for (Link link : linksToRemove) {
                    link.getSource().removeLink(link);
                    link.getTarget().removeLink(link);
                    n.getLinks().remove(link);
                }

                // Entferne Mirror vom Netzwerk
                n.getMirrors().remove(mirrorToRemove);
                cleanedMirrors.add(mirrorToRemove);
            }

            // Entferne Knoten aus der Struktur-Verwaltung
            removeNodeFromStructure(leafToRemove);
        }

        return cleanedMirrors;
    }

    // ===== STERN-SPEZIFISCHE HILFSMETHODEN =====

    /**
     * Baut die Stern-Struktur mit sowohl StructureNode-Verbindungen als auch echten Mirror-Links auf.
     * Portiert die Stern-Verbindungslogik aus StarBuilder.
     *
     * @param center Das Zentrum des Sterns
     * @param leaves Liste der Blätter
     * @param simTime Aktuelle Simulationszeit für Link-Erstellung
     * @param props Properties der Simulation
     */
    private void buildStarStructureWithLinks(StarMirrorNode center, List<StarMirrorNode> leaves,
                                             int simTime, Properties props) {
        if (center == null || leaves.isEmpty()) {
            return;
        }

        // Strukturplanung und Ausführung: Verbinde alle Blätter mit dem Zentrum
        for (StarMirrorNode leaf : leaves) {
            // StructureNode-Verbindung: Füge Blatt als Kind des Zentrums hinzu
            center.addChild(leaf);

            // Ausführungsebene: Erstelle echte Mirror-Links
            createStarMirrorLink(center, leaf, simTime, props);
        }
    }

    /**
     * Erstellt einen neuen Stern-Blatt-Knoten mit struktureller Planung.
     * Verwendet den mirrorIterator für die Mirror-Zuweisung.
     *
     * @param simTime Aktuelle Simulationszeit (für zukünftige Verwendung)
     * @param props Properties der Simulation
     * @return Neuer StarMirrorNode oder null, wenn keine Mirrors verfügbar
     */
    private StarMirrorNode createStarLeaf(int simTime, Properties props) {
        if (!mirrorIterator.hasNext()) {
            return null;
        }

        // Erstelle neuen Stern-Blatt-Knoten
        Mirror mirror = mirrorIterator.next();
        StarMirrorNode starLeaf = new StarMirrorNode(mirror.getID(), mirror);
        addToStructureNodes(starLeaf); // Füge zur Struktur-Verwaltung hinzu

        return starLeaf;
    }

    /**
     * Verbindet ein neues Blatt mit dem Stern-Zentrum.
     * Erstellt sowohl StructureNode-Verbindungen als auch Mirror-Links.
     * Portiert die Stern-Erweiterungslogik aus StarBuilder.
     *
     * @param center Das Zentrum des Sterns
     * @param newLeaf Das neue Blatt
     * @param simTime Aktuelle Simulationszeit für Link-Erstellung
     * @param props Properties der Simulation
     */
    private void attachLeafToCenter(StarMirrorNode center, StarMirrorNode newLeaf,
                                    int simTime, Properties props) {
        if (center == null || newLeaf == null) {
            return;
        }

        // Strukturplanung: Füge neues Blatt als Kind des Zentrums hinzu
        center.addChild(newLeaf);

        // Ausführungsebene: Erstelle Mirror-Link zwischen Zentrum und Blatt
        createStarMirrorLink(center, newLeaf, simTime, props);
    }

    /**
     * **PLANUNGSEBENE**: Entfernt ein Blatt aus der Stern-Struktur-Planung.
     * Arbeitet ohne Zeitbezug - nur strukturelle Stern-Änderung.
     * Bereinigt alle bidirektionalen Parent-Child-Beziehungen.
     *
     * @param leafToRemove Das zu entfernende Blatt
     */
    private void removeLeafFromStarStructuralPlanning(StarMirrorNode leafToRemove) {
        if (leafToRemove == null) {
            return;
        }

        // Entferne aus Parent-Child-Beziehung (setzt automatisch parent auf null)
        StructureNode parent = leafToRemove.getParent();
        if (parent != null) {
            parent.removeChild(leafToRemove);
        }

        // Entferne alle Kinder (für jeden wird automatisch parent auf null gesetzt)
        List<StructureNode> children = new ArrayList<>(leafToRemove.getChildren());
        for (StructureNode child : children) {
            leafToRemove.removeChild(child);
        }
    }

    /**
     * Entfernt einen Knoten vollständig aus der Struktur-Verwaltung (ohne Mirror-Shutdown).
     * Bereinigt alle bidirektionalen Beziehungen und entfernt den Knoten aus der Struktur-Verwaltung.
     *
     * @param nodeToRemove Der zu entfernende Knoten
     */
    private void removeNodeFromStructure(StarMirrorNode nodeToRemove) {
        // Entferne aus Parent-Child-Beziehung (setzt automatisch parent auf null)
        StructureNode parent = nodeToRemove.getParent();
        if (parent != null) {
            parent.removeChild(nodeToRemove);
        }

        // Entferne alle Kinder (für jeden wird automatisch parent auf null gesetzt)
        List<StructureNode> children = new ArrayList<>(nodeToRemove.getChildren());
        for (StructureNode child : children) {
            nodeToRemove.removeChild(child);
        }
    }

    /**
     * Erstellt einen Mirror-Link zwischen zwei Stern-Knoten mit Validierung.
     * Ausführungsebene: Echte Mirror-Verbindungen mit Duplikat-Prüfung.
     *
     * @param center Das Zentrum des Sterns
     * @param leaf Das Blatt des Sterns
     * @param simTime Aktuelle Simulationszeit für Link-Erstellung
     * @param props Properties der Simulation
     */
    private void createStarMirrorLink(StarMirrorNode center, StarMirrorNode leaf, int simTime, Properties props) {
        Mirror centerMirror = center.getMirror();
        Mirror leafMirror = leaf.getMirror();

        // Validierung: Beide Mirrors müssen vorhanden sein
        if (centerMirror == null || leafMirror == null) {
            return;
        }

        // Prüfe, ob bereits eine Verbindung besteht
        if (centerMirror.isAlreadyConnected(leafMirror)) {
            return;
        }

        // Erstelle neuen Link auf Ausführungsebene
        Link link = new Link(idGenerator.getNextID(), centerMirror, leafMirror, simTime, props);

        // Füge Link zu beiden Mirrors hinzu (bidirektional)
        centerMirror.addLink(link);
        leafMirror.addLink(link);

        // Füge Link auch zum Netzwerk hinzu
        network.getLinks().add(link);
    }

    /**
     * Prüft, ob zwei Mirrors bereits über einen Link verbunden sind.
     * Hilfsmethode für Duplikat-Vermeidung.
     *
     * @param mirror1 Erster Mirror
     * @param mirror2 Zweiter Mirror
     * @return true, wenn bereits verbunden
     */
    private boolean isAlreadyConnected(Mirror mirror1, Mirror mirror2) {
        if (mirror1 == null || mirror2 == null) {
            return false;
        }
        return mirror1.isAlreadyConnected(mirror2);
    }

    /**
     * Findet alle Blätter des Sterns (alle Knoten außer dem Zentrum).
     * Portiert die findStarLeaves-Logik aus StarBuilder.
     *
     * @return Liste aller Stern-Blätter
     */
    private List<StarMirrorNode> findStarLeaves() {
        StarMirrorNode center = getStarCenter();
        if (center == null) {
            return new ArrayList<>();
        }

        // Filtere alle Knoten: Nur Nicht-Zentrum-Knoten sind Blätter
        return getAllStarNodes().stream()
                .filter(node -> node != center)
                .filter(node -> !node.isHead(StructureType.STAR))
                .collect(Collectors.toList());
    }

    // ===== TYPSICHERE HILFSMETHODEN =====

    /**
     * Gibt das Zentrum des Sterns zurück.
     * Verwendet die aktuelle Struktur-Root als Zentrum.
     *
     * @return Das Zentrum als StarMirrorNode oder null
     */
    private StarMirrorNode getStarCenter() {
        MirrorNode root = getCurrentStructureRoot();
        return (root instanceof StarMirrorNode) ? (StarMirrorNode) root : null;
    }

    /**
     * Gibt alle Stern-Knoten als typisierte Liste zurück.
     * Filtert die Struktur-Knoten nach StarMirrorNode-Typ.
     *
     * @return Liste aller StarMirrorNodes in der Struktur
     */
    private List<StarMirrorNode> getAllStarNodes() {
        return getAllStructureNodes().stream()
                .filter(StarMirrorNode.class::isInstance)
                .map(StarMirrorNode.class::cast)
                .collect(Collectors.toList());
    }

    /**
     * Prüft, ob ein Knoten das Zentrum des Sterns ist.
     *
     * @param node Der zu prüfende Knoten
     * @return true, wenn der Knoten das Zentrum ist
     */
    private boolean isStarCenter(StarMirrorNode node) {
        return node != null && node.isHead(StructureType.STAR);
    }

    /**
     * Prüft, ob ein Knoten ein Blatt des Sterns ist.
     *
     * @param node Der zu prüfende Knoten
     * @return true, wenn der Knoten ein Blatt ist
     */
    private boolean isStarLeaf(StarMirrorNode node) {
        return node != null && !node.isHead(StructureType.STAR);
    }

    // ===== TOPOLOGY STRATEGY INTERFACE IMPLEMENTATION =====

    /**
     * Initializes the network by connecting mirrors in a star topology.
     * Erstellt die initiale Stern-Struktur und verbindet alle Mirrors entsprechend.
     *
     * @param n Das Netzwerk
     * @param props Properties der Simulation
     * @return Set aller erstellten Links
     */
    @Override
    public Set<Link> initNetwork(Network n, Properties props) {
        // Initialisiere das Netzwerk mit Stern-Topologie
        this.network = n;
        this.mirrorIterator = n.getMirrors().iterator();

        // Baue die Stern-Struktur mit allen verfügbaren Mirrors
        MirrorNode root = buildStructure(n.getMirrors().size(), 0, props);

        // Erstelle und verbinde alle Links
        return buildAndConnectLinks(root, props);
    }

    /**
     * Startet das Netzwerk komplett neu mit der Stern-Topologie.
     * Bereinigt alle bestehenden Links und baut die Struktur neu auf.
     *
     * @param n Das Netzwerk
     * @param props Properties der Simulation
     * @param simTime Aktuelle Simulationszeit
     */
    @Override
    public void restartNetwork(Network n, Properties props, int simTime) {
        super.initNetwork(n, props);

        // Initialisiere den internen Zustand neu
        this.network = n;
        this.mirrorIterator = n.getMirrors().iterator();

        int usableMirrors = Math.toIntExact(n.getMirrors().stream().filter(Mirror::isUsableForNetwork).count());

        // Baue neue Stern-Struktur mit aktueller Simulationszeit
        MirrorNode root = buildStructure(usableMirrors, simTime, props);

        // Erstelle alle Links neu
        buildAndConnectLinks(root, props);
    }

    /**
     * Adds the requested number of mirrors to the network and connects them accordingly.
     * Fügt neue Mirrors als Blätter an das bestehende Stern-Zentrum an.
     *
     * @param n Das Netzwerk
     * @param newMirrors Anzahl neuer Mirrors
     * @param props Properties der Simulation
     * @param simTime Aktuelle Simulationszeit
     */
    @Override
    public void handleAddNewMirrors(Network n, int newMirrors, Properties props, int simTime) {
        // Aktualisiere den Iterator mit allen Mirrors (inklusive neuer)
        this.mirrorIterator = n.getMirrors().iterator();

        // Überspringe bereits verwendete Mirrors
        int existingNodes = getAllStructureNodes().size();
        for (int i = 0; i < existingNodes && mirrorIterator.hasNext(); i++) {
            mirrorIterator.next();
        }

        // Füge neue Knoten zur Struktur hinzu
        int actuallyAdded = addNodesToStructure(newMirrors);

        // Erstelle neue Links falls Knoten hinzugefügt wurden
        if (actuallyAdded > 0) {
            MirrorNode root = getCurrentStructureRoot();
            buildAndConnectLinks(root, props);
        }
    }

    /**
     * Returns the expected number of total links in the network according to the star topology.
     * Für einen Stern mit n Mirrors ist die Anzahl der Links (n-1) (Zentrum zu allen Blättern).
     *
     * @param n Das Netzwerk
     * @return Anzahl der erwarteten Links
     */
    @Override
    public int getNumTargetLinks(Network n) {
        return Math.max(0, n.getMirrors().size() - 1);
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
        if (a instanceof MirrorChange) {
            MirrorChange mc = (MirrorChange) a;
            // KORRIGIERT: Verwende getNewMirrors() statt getNumMirrors()
            // getNewMirrors() gibt die NEUE Gesamtanzahl zurück, nicht die Differenz
            int predictedMirrors = mc.getNewMirrors();
            return Math.max(0, predictedMirrors - 1);
        }

        if (a instanceof TopologyChange) {
            TopologyChange tc = (TopologyChange) a;
            return getNumTargetLinks(tc.getNetwork());
        }

        // Für andere Action-Typen: Behalte aktuelle Anzahl
        return network != null ? getNumTargetLinks(network) : 0;
    }

    /**
     * Baut die tatsächlichen Links zwischen den Mirrors basierend auf der StructureNode-Struktur auf.
     * Stern-spezifische Implementierung: Verbindet das Zentrum mit allen Blättern.
     *
     * @param root Die Root-Node der Struktur (sollte das Zentrum sein)
     * @param props Properties der Simulation
     * @return Set aller erstellten Links
     */
    @Override
    protected Set<Link> buildAndConnectLinks(MirrorNode root, Properties props) {
        Set<Link> createdLinks = new HashSet<>();

        if (root == null) {
            return createdLinks;
        }

        // Bei Stern-Struktur: Zentrum mit allen Blättern verbinden
        StarMirrorNode center = (StarMirrorNode) root;
        List<StarMirrorNode> leaves = findStarLeaves();

        // Erstelle Links zwischen Zentrum und jedem Blatt
        for (StarMirrorNode leaf : leaves) {
            Mirror centerMirror = center.getMirror();
            Mirror leafMirror = leaf.getMirror();

            // Erstelle Link nur wenn beide Mirrors vorhanden und nicht bereits verbunden
            if (centerMirror != null && leafMirror != null && !centerMirror.isAlreadyConnected(leafMirror)) {
                Link link = new Link(idGenerator.getNextID(), centerMirror, leafMirror, 0, props);
                centerMirror.addLink(link);
                leafMirror.addLink(link);
                network.getLinks().add(link);
                createdLinks.add(link);
            }
        }

        return createdLinks;
    }

    // ===== STERN-ANALYSE UND DEBUGGING =====

    /**
     * Prüft, ob die Stern-Struktur intakt und valide ist.
     * Ein Stern ist intakt, wenn er ein Zentrum und mindestens die erforderlichen Blätter hat.
     *
     * @return true, wenn der Stern strukturell korrekt ist
     */
    public boolean isStarIntact() {
        StarMirrorNode center = getStarCenter();
        if (center == null) {
            return false;
        }

        List<StarMirrorNode> leaves = findStarLeaves();
        return leaves.size() >= minStarSize - 1;
    }

    /**
     * Berechnet die Stern-Effizienz (wie gut das Zentrum genutzt wird).
     * Effizienz = (Anzahl tatsächlicher Blätter) / (Anzahl möglicher Blätter)
     *
     * @return Effizienz zwischen 0.0 und 1.0
     */
    public double calculateStarEfficiency() {
        StarMirrorNode center = getStarCenter();
        if (center == null) {
            return 0.0;
        }

        List<StarMirrorNode> leaves = findStarLeaves();
        int actualLeaves = leaves.size();
        int maxPossibleLeaves = network != null ? network.getMirrors().size() - 1 : 0;

        return maxPossibleLeaves > 0 ? (double) actualLeaves / maxPossibleLeaves : 0.0;
    }

    /**
     * Gibt detaillierte Informationen über die Stern-Struktur zurück.
     * Nützlich für Debugging und Monitoring.
     *
     * @return Map mit detaillierten Stern-Informationen
     */
    public Map<String, Object> getDetailedStarInfo() {
        Map<String, Object> info = new HashMap<>();

        StarMirrorNode center = getStarCenter();
        List<StarMirrorNode> leaves = findStarLeaves();

        info.put("isStarIntact", isStarIntact());
        info.put("starEfficiency", calculateStarEfficiency());
        info.put("minStarSize", minStarSize);
        info.put("allowStarExpansion", allowStarExpansion);
        info.put("allowCenterRotation", allowCenterRotation);
        info.put("totalNodes", getAllStarNodes().size());
        info.put("centerNode", center != null ? center.getId() : null);
        info.put("leafCount", leaves.size());

        return info;
    }

    // ===== KONFIGURATION UND GETTER/SETTER =====

    /**
     * Gibt die minimale Stern-Größe zurück.
     *
     * @return Minimale Anzahl von Knoten im Stern
     */
    public int getMinStarSize() {
        return minStarSize;
    }

    /**
     * Setzt die minimale Stern-Größe.
     *
     * @param minStarSize Minimale Anzahl von Knoten (minimum 3)
     */
    public void setMinStarSize(int minStarSize) {
        this.minStarSize = Math.max(3, minStarSize);
    }

    /**
     * Prüft, ob Stern-Erweiterung erlaubt ist.
     *
     * @return true, wenn der Stern erweitert werden kann
     */
    public boolean isAllowStarExpansion() {
        return allowStarExpansion;
    }

    /**
     * Setzt die Stern-Erweiterbarkeit.
     *
     * @param allowStarExpansion Ob der Stern erweitert werden kann
     */
    public void setAllowStarExpansion(boolean allowStarExpansion) {
        this.allowStarExpansion = allowStarExpansion;
    }

    /**
     * Prüft, ob Zentrum-Rotation erlaubt ist.
     *
     * @return true, wenn das Zentrum rotiert werden kann
     */
    public boolean isAllowCenterRotation() {
        return allowCenterRotation;
    }

    /**
     * Setzt die Zentrum-Rotations-Erlaubnis.
     *
     * @param allowCenterRotation Ob das Zentrum rotiert werden kann
     */
    public void setAllowCenterRotation(boolean allowCenterRotation) {
        this.allowCenterRotation = allowCenterRotation;
    }

    /**
     * Gibt eine String-Repräsentation der Stern-Topologie-Strategie zurück.
     *
     * @return Formatierte String-Darstellung mit Konfiguration und Effizienz
     */
    @Override
    public String toString() {
        return String.format("StarTopologyStrategy{minStarSize=%d, allowStarExpansion=%b, allowCenterRotation=%b, starEfficiency=%.2f}",
                minStarSize, allowStarExpansion, allowCenterRotation, calculateStarEfficiency());
    }
}