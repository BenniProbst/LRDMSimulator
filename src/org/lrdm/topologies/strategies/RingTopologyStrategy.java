package org.lrdm.topologies.strategies;

import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.Action;
import org.lrdm.effectors.MirrorChange;
import org.lrdm.effectors.TargetLinkChange;
import org.lrdm.effectors.TopologyChange;
import org.lrdm.topologies.node.*;
import org.lrdm.topologies.node.StructureNode.StructureType;
import org.lrdm.util.IDGenerator;

import java.util.*;

/**
 * Eine spezialisierte {@link TopologyStrategy}, die Mirrors als Ring-Topologie mit einer
 * geschlossenen Schleife verknüpft. Diese Strategie ist eine Portierung der {@link org.lrdm.topologies.strategies.RingTopologyStrategy} Klasse.
 * <p>
 * **Ring-Topologie-Eigenschaften**:
 * - Jeder Mirror ist mit genau zwei anderen Mirrors verbunden (Vorgänger und Nachfolger)
 * - Bildet eine geschlossene Schleife ohne Anfang oder Ende
 * - Benötigt mindestens 3 Knoten für einen funktionsfähigen Ring
 * - Anzahl der Links ist gleich der Anzahl der Knoten (n Links für n Knoten)
 * - Verwendet {@link RingMirrorNode} für spezifische Ring-Funktionalität
 * <p>
 * **Planungsebene vs. Ausführungsebene**:
 * - Planungsebene: `removeNodesFromStructure()` - plant strukturelle Ring-Änderungen ohne Zeitbezug
 * - Ausführungsebene: `handleRemoveMirrors()` - führt Mirror-Shutdown innerhalb der Ring-Planungsgrenzen aus
 * - Automatisches Mitwachsen: MirrorNode-Ebene passt sich an StructureNode-Ring-Planung an
 * <p>
 * **Ring-Constraints**: Im Gegensatz zu Bäumen ist die Ring-Struktur zyklisch und
 * hat strenge topologische Anforderungen (jeder Knoten hat genau 2 Nachbarn).
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class RingTopologyStrategy extends BuildAsSubstructure {

    // ===== RING-SPEZIFISCHE KONFIGURATION =====
    private int minRingSize = 3;
    private boolean allowRingExpansion = true;

    // ===== KONSTRUKTOREN =====

    public RingTopologyStrategy() {
        super();
    }

    public RingTopologyStrategy(int minRingSize) {
        super();
        this.minRingSize = Math.max(3, minRingSize);
    }

    public RingTopologyStrategy(int minRingSize, boolean allowRingExpansion) {
        super();
        this.minRingSize = Math.max(3, minRingSize);
        this.allowRingExpansion = allowRingExpansion;
    }

    // ===== ÜBERSCHREIBUNG DER BUILD-AS-SUBSTRUCTURE-METHODEN =====

    /**
     * **PLANUNGSEBENE**: Erstellt die Ring-Struktur mit geschlossener Schleife.
     * Überschreibt BuildAsSubstructure für Ring-spezifische Logik.
     * Portiert die buildRing-Logik aus RingBuilder.
     */
    @Override
    protected MirrorNode buildStructure(int totalNodes, int simTime, Properties props) {
        if (totalNodes < minRingSize || !mirrorIterator.hasNext()) return null;

        // Erstelle alle Ring-Knoten
        List<RingMirrorNode> ringNodes = new ArrayList<>();
        for (int i = 0; i < totalNodes; i++) {
            if (!mirrorIterator.hasNext()) break;
            
            Mirror mirror = mirrorIterator.next();
            RingMirrorNode ringNode = new RingMirrorNode(mirror.getID(), mirror);
            ringNodes.add(ringNode);
            this.addToStructureNodes(ringNode);
        }

        if (ringNodes.size() < minRingSize) return null;

        // Strukturplanung: Erstellen von Ring-Verbindungen
        buildRingStructureWithLinks(ringNodes, simTime, props);

        // Setze ersten Knoten als Head
        RingMirrorNode head = ringNodes.get(0);
        head.setHead(true);

        return head;
    }

    /**
     * **PLANUNGSEBENE**: Fügt neue Knoten zur Ring-Struktur hinzu.
     * Überschreibt BuildAsSubstructure für Ring-Einfügung zwischen bestehenden Knoten.
     * Portiert die addNodesToRing-Logik aus RingBuilder.
     */
    @Override
    protected int addNodesToStructure(int nodesToAdd) {
        if (nodesToAdd <= 0 || !allowRingExpansion) return 0;

        RingMirrorNode head = getRingHead();
        if (head == null) return 0;

        List<RingMirrorNode> ringNodes = getAllRingNodes();
        if (ringNodes.isEmpty()) return 0;

        int actuallyAdded = 0;
        int nodeIndex = 0;

        // Ring-Einfügung: Neue Knoten zwischen bestehenden Knoten einfügen
        while (actuallyAdded < nodesToAdd && nodeIndex < ringNodes.size() && mirrorIterator.hasNext()) {
            RingMirrorNode current = ringNodes.get(nodeIndex);
            RingMirrorNode next = current.getNextInRing();

            if (next != null) {
                // Strukturplanung: Erstelle neuen Knoten zwischen current und next
                RingMirrorNode newNode = createRingNode(0, new Properties());
                if (newNode != null) {
                    // Reorganisiere Ring-Verbindungen
                    insertNodeIntoRing(current, newNode, next, 0, new Properties());
                    actuallyAdded++;
                    
                    // Aktualisiere Ring-Liste für nächste Iteration
                    ringNodes.add(nodeIndex + 1, newNode);
                }
            }

            nodeIndex++;
        }

        return actuallyAdded;
    }

    /**
     * **PLANUNGSEBENE**: Entfernt Knoten aus der Ring-Struktur.
     * Überschreibt BuildAsSubstructure für Ring-erhaltende Entfernung.
     * Erhält immer die Ring-Topologie durch Neuverbindung der Nachbarn.
     */
    @Override
    protected int removeNodesFromStructure(int nodesToRemove) {
        if (nodesToRemove <= 0) return 0;

        List<RingMirrorNode> ringNodes = getAllRingNodes();
        if (ringNodes.size() - nodesToRemove < minRingSize) {
            nodesToRemove = ringNodes.size() - minRingSize;
        }
        if (nodesToRemove <= 0) return 0;

        int actuallyRemoved = 0;

        // Ring-Entfernung: Entferne Knoten und verbinde Nachbarn neu
        for (int i = 0; i < nodesToRemove && !ringNodes.isEmpty(); i++) {
            // Wähle Knoten zur Entfernung (nicht den Head, wenn möglich)
            RingMirrorNode nodeToRemove = selectNodeForRemoval(ringNodes);
            if (nodeToRemove != null) {
                removeNodeFromRingStructuralPlanning(nodeToRemove);
                ringNodes.remove(nodeToRemove);
                actuallyRemoved++;
            }
        }

        return actuallyRemoved;
    }

    /**
     * **AUSFÜHRUNGSEBENE**: Überschreibt die Mirror-Entfernung für Ring-Erhaltung.
     * Führt Mirror-Shutdown innerhalb der strukturellen Ring-Planungsgrenzen aus.
     * Arbeitet komplementär zu removeNodesFromStructure.
     */
    @Override
    public Set<Mirror> handleRemoveMirrors(Network n, int removeMirrors, Properties props, int simTime) {
        if (removeMirrors <= 0) {
            return new HashSet<>();
        }

        List<RingMirrorNode> ringNodes = getAllRingNodes();
        if (ringNodes.size() - removeMirrors < minRingSize) {
            removeMirrors = ringNodes.size() - minRingSize;
        }
        if (removeMirrors <= 0) {
            return new HashSet<>();
        }

        Set<Mirror> cleanedMirrors = new HashSet<>();
        int actuallyRemoved = 0;

        // Ausführungsebene: Ring-bewusste Mirror-Entfernung
        for (int i = 0; i < removeMirrors && !ringNodes.isEmpty(); i++) {
            RingMirrorNode targetNode = selectNodeForRemoval(ringNodes);
            if (targetNode != null) {
                Mirror targetMirror = targetNode.getMirror();
                if (targetMirror != null) {
                    // Mirror-Shutdown auf Ausführungsebene
                    targetMirror.shutdown(simTime);
                    cleanedMirrors.add(targetMirror);
                    actuallyRemoved++;
                    ringNodes.remove(targetNode);
                }
            }
        }

        // Synchronisiere Plannings- und Ausführungsebene
        removeNodesFromStructure(actuallyRemoved);

        return cleanedMirrors;
    }

    // ===== RING-SPEZIFISCHE HILFSMETHODEN =====

    /**
     * Baut die Ring-Struktur mit echten Mirror-Links auf.
     * Portiert die Ring-Verbindungslogik aus RingBuilder.
     */
    private void buildRingStructureWithLinks(List<RingMirrorNode> ringNodes, int simTime, Properties props) {
        if (ringNodes.size() < minRingSize) return;

        // Strukturplanung und Ausführung: Verbinde Ring-Knoten zyklisch
        for (int i = 0; i < ringNodes.size(); i++) {
            RingMirrorNode current = ringNodes.get(i);
            RingMirrorNode next = ringNodes.get((i + 1) % ringNodes.size());

            // StructureNode-Verbindung
            Set<StructureType> typeIds = new HashSet<>();
            typeIds.add(StructureType.RING);

            Map<StructureType, Integer> headIds = new HashMap<>();
            headIds.put(StructureType.RING, getRingHeadId(ringNodes));

            current.addChild(next, typeIds, headIds);

            // Mirror-Link-Erstellung
            createRingMirrorLink(current, next, simTime, props);
        }
    }

    /**
     * Erstellt einen neuen Ring-Knoten mit struktureller Planung.
     */
    private RingMirrorNode createRingNode(int simTime, Properties props) {
        if (!mirrorIterator.hasNext()) return null;

        Mirror mirror = mirrorIterator.next();
        RingMirrorNode ringNode = new RingMirrorNode(mirror.getID(), mirror);
        this.addToStructureNodes(ringNode);
        
        return ringNode;
    }

    /**
     * Fügt einen neuen Knoten in den Ring zwischen zwei bestehenden Knoten ein.
     * Portiert die Ring-Einfügungslogik aus RingBuilder.
     */
    private void insertNodeIntoRing(RingMirrorNode current, RingMirrorNode newNode, 
                                    RingMirrorNode next, int simTime, Properties props) {
        // Strukturplanung: Entferne alte Verbindung current -> next
        current.removeChild(next);

        // Strukturplanung: Füge neue Verbindungen hinzu
        Set<StructureType> typeIds = new HashSet<>();
        typeIds.add(StructureType.RING);

        Map<StructureType, Integer> headIds = new HashMap<>();
        headIds.put(StructureType.RING, getRingHeadId());

        // current -> newNode -> next
        current.addChild(newNode, typeIds, headIds);
        newNode.addChild(next, typeIds, headIds);

        // Ausführungsebene: Erstelle neue Mirror-Links
        createRingMirrorLink(current, newNode, simTime, props);
        createRingMirrorLink(newNode, next, simTime, props);
    }

    /**
     * Wählt einen Knoten für die Entfernung aus dem Ring aus.
     * Bevorzugt Nicht-Head-Knoten, wenn möglich.
     */
    private RingMirrorNode selectNodeForRemoval(List<RingMirrorNode> ringNodes) {
        if (ringNodes.isEmpty()) return null;

        // Bevorzuge Nicht-Head-Knoten
        for (RingMirrorNode node : ringNodes) {
            if (!node.isHead()) {
                return node;
            }
        }

        // Fallback: Nimm irgendeinen Knoten (außer es ist der letzte Head)
        return ringNodes.get(ringNodes.size() - 1);
    }

    /**
     * **PLANUNGSEBENE**: Entfernt Knoten aus der Ring-Struktur-Planung.
     * Arbeitet ohne Zeitbezug - nur strukturelle Ring-Änderungen.
     */
    private void removeNodeFromRingStructuralPlanning(RingMirrorNode nodeToRemove) {
        RingMirrorNode previous = nodeToRemove.getPreviousInRing();
        RingMirrorNode next = nodeToRemove.getNextInRing();

        if (previous != null && next != null) {
            // Strukturplanung: Verbinde Vorgänger direkt mit Nachfolgern
            previous.removeChild(nodeToRemove);
            nodeToRemove.removeChild(next);

            Set<StructureType> typeIds = new HashSet<>();
            typeIds.add(StructureType.RING);

            Map<StructureType, Integer> headIds = new HashMap<>();
            headIds.put(StructureType.RING, getRingHeadId());

            previous.addChild(next, typeIds, headIds);
        }

        // Bereinige alle Verbindungen des entfernten Knotens.
        // Bereinige alle Verbindungen des entfernten Knotens.
        // Verwende die einzelnen Methoden statt removeAllChildren()
        Set<StructureNode> childrenToRemove = new HashSet<>();
        childrenToRemove.addAll(nodeToRemove.getChildren());

        for (StructureNode child : childrenToRemove) {
            nodeToRemove.removeChild(child);
        }

        nodeToRemove.setParent(null);
    }

    /**
     * Erstellt Mirror-Link mit Ring-Validierung.
     * Ausführungsebene: Echte Mirror-Verbindungen.
     */
    private void createRingMirrorLink(RingMirrorNode from, RingMirrorNode to, int simTime, Properties props) {
        Mirror fromMirror = from.getMirror();
        Mirror toMirror = to.getMirror();

        if (fromMirror == null || toMirror == null) return;
        if (isAlreadyConnected(fromMirror, toMirror)) return;

        // Erstelle Link auf Ausführungsebene
        Link link = new Link(idGenerator.getNextID(), fromMirror, toMirror, simTime, props);

        // Füge Links zu Mirrors hinzu
        fromMirror.addLink(link);
        toMirror.addLink(link);

        // Füge auch zu network links hinzu
        if (network != null) {
            network.getLinks().add(link);
        }
    }

    /**
     * Prüft bestehende Mirror-Verbindungen.
     */
    private boolean isAlreadyConnected(Mirror mirror1, Mirror mirror2) {
        return mirror1.getLinks().stream()
                .anyMatch(link ->
                        (link.getTarget().equals(mirror2) && link.getSource().equals(mirror1)) ||
                        (link.getTarget().equals(mirror1) && link.getSource().equals(mirror2)));
    }

    // ===== TYPSICHERE HILFSMETHODEN =====

    /**
     * Gibt den Ring-Head zurück.
     */
    private RingMirrorNode getRingHead() {
        MirrorNode root = getCurrentStructureRoot();
        return (root instanceof RingMirrorNode) ? (RingMirrorNode) root : null;
    }

    /**
     * Gibt die Head-ID des Rings zurück.
     */
    private int getRingHeadId() {
        RingMirrorNode head = getRingHead();
        return (head != null) ? head.getId() : -1;
    }

    /**
     * Gibt die Head-ID für eine Liste von Ring-Knoten zurück.
     */
    private int getRingHeadId(List<RingMirrorNode> ringNodes) {
        return ringNodes.isEmpty() ? -1 : ringNodes.get(0).getId();
    }

    /**
     * Gibt alle Ring-Knoten als typisierte Liste zurück.
     */
    private List<RingMirrorNode> getAllRingNodes() {
        return getAllStructureNodes().stream()
                .filter(RingMirrorNode.class::isInstance)
                .map(RingMirrorNode.class::cast)
                .toList();
    }

    // ===== TOPOLOGY STRATEGY INTERFACE IMPLEMENTATION =====

    /**
     * Initializes the network by connecting mirrors in a ring topology.
     *
     * @param n the {@link Network}
     * @param props {@link Properties} of the simulation
     * @return {@link Set} of all {@link Link}s created
     */
    @Override
    public Set<Link> initNetwork(Network n, Properties props) {
        this.network = n;
        this.idGenerator = IDGenerator.getInstance();
        this.mirrorIterator = n.getMirrors().iterator();

        int totalMirrors = n.getNumMirrors();
        if (totalMirrors < minRingSize) {
            throw new IllegalArgumentException("Ring-Topologie benötigt mindestens " + minRingSize + " Mirrors");
        }

        MirrorNode root = buildStructure(totalMirrors, 0, props);
        setCurrentStructureRoot(root);

        return buildAndConnectLinks(root, props);
    }

    /**
     * Startet das Netzwerk komplett neu mit der Ring-Topologie.
     *
     * @param n Das Netzwerk
     * @param props Simulation Properties
     * @param simTime Aktuelle Simulationszeit
     */
    @Override
    public void restartNetwork(Network n, Properties props, int simTime) {
        super.restartNetwork(n, props, simTime);
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
        if (newMirrors <= 0 || !allowRingExpansion) return;

        // Erstelle neue Mirrors
        List<Mirror> addedMirrors = createMirrors(newMirrors, simTime, props);
        n.getMirrors().addAll(addedMirrors);

        // Aktualisiere mirrorIterator für neue Mirrors
        this.mirrorIterator = addedMirrors.iterator();

        // Füge zur Ring-Struktur hinzu
        addNodesToStructure(newMirrors);

        // Erstelle neue Links
        MirrorNode root = getCurrentStructureRoot();
        if (root != null) {
            buildAndConnectLinks(root, props);
        }
    }

    /**
     * Returns the expected number of total links in the network according to the ring topology.
     * For n mirrors, the number of links is n (ring property - each node connects to its neighbor).
     *
     * @param n {@link Network} the network
     * @return number of total links expected for the network
     */
    @Override
    public int getNumTargetLinks(Network n) {
        int numMirrors = n.getNumMirrors();
        return (numMirrors >= minRingSize) ? numMirrors : 0;
    }

    /**
     * Berechnet die erwartete Anzahl der Links, wenn die gegebene Aktion ausgeführt wird.
     * Ring-spezifische Implementierung basierend auf den drei Action-Typen:
     * - MirrorChange: Verändert Ring-Größe dynamisch (n Mirrors -> n Links)
     * - TargetLinkChange: Hat KEINEN Effekt bei Ring (immer 2 Links pro Knoten)
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

        // 1. MirrorChange: Ring-Größe ändert sich dynamisch
        if (a instanceof MirrorChange mirrorChange) {
            int newMirrorCount = mirrorChange.getNewMirrors();
            return calculateExpectedLinks(newMirrorCount);
        }

        // 2. TargetLinkChange: KEIN Effekt bei Ring-Topologie
        // Ring-Constraint: Jeder Knoten hat IMMER genau 2 Links (Vorgänger + Nachfolger)
        // Unabhängig von TargetLinkChange-Parametern
        if (a instanceof TargetLinkChange) {
            // Ring-Invariante: Links = Anzahl aktuelle Mirrors
            // Verwende die bestehende getNumTargetLinks-Methode
            return network != null ? getNumTargetLinks(network) : 0;
        }

        // 3. TopologyChange: Komplette Rekonstruktion
        if (a instanceof TopologyChange topologyChange) {
            TopologyStrategy newTopology = topologyChange.getNewTopology();

            // Wenn neue Topologie auch Ring ist: Verwende aktuelle Mirror-Anzahl
            if (newTopology instanceof RingTopologyStrategy) {
                int currentMirrors = network != null ? network.getNumMirrors() : 0;
                return calculateExpectedLinks(currentMirrors);
            }

            // Andere Topologie: Delegiere an neue Strategie
            if (network != null) {
                return newTopology.getNumTargetLinks(network);
            }
            return 0;
        }

        // Fallback: Aktuelle Ring-Link-Anzahl über getNumTargetLinks
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
        Set<Link> allLinks = new HashSet<>();

        if (root == null) return allLinks;

        // Sammle alle Ring-Knoten
        List<RingMirrorNode> ringNodes = getAllRingNodes();
        
        // Erstelle Links basierend auf Ring-Struktur
        for (RingMirrorNode node : ringNodes) {
            Mirror nodeMirror = node.getMirror();
            if (nodeMirror == null) continue;

            RingMirrorNode nextNode = node.getNextInRing();
            if (nextNode != null && nextNode.getMirror() != null) {
                Mirror nextMirror = nextNode.getMirror();
                
                if (!isAlreadyConnected(nodeMirror, nextMirror)) {
                    Link link = new Link(idGenerator.getNextID(), nodeMirror, nextMirror, 0, props);
                    allLinks.add(link);
                    
                    // Füge zu Network hinzu
                    if (network != null) {
                        network.getLinks().add(link);
                    }
                }
            }
        }

        return allLinks;
    }

    // ===== RING-ANALYSE =====

    /**
     * Prüft, ob die Ring-Struktur intakt ist.
     */
    public boolean isRingIntact() {
        List<RingMirrorNode> ringNodes = getAllRingNodes();
        if (ringNodes.size() < minRingSize) return false;

        // Prüfe, ob jeder Knoten genau zwei Nachbarn hat
        for (RingMirrorNode node : ringNodes) {
            if (node.getNextInRing() == null || node.getPreviousInRing() == null) {
                return false;
            }
        }

        return true;
    }

    /**
     * Berechnet die durchschnittliche Ring-Pfadlänge.
     */
    public double calculateAverageRingPathLength() {
        List<RingMirrorNode> ringNodes = getAllRingNodes();
        if (ringNodes.size() < minRingSize) return 0.0;

        // In einem Ring ist die durchschnittliche Pfadlänge n/4 für gerade n, (n²-1)/(4n) für ungerade n
        int n = ringNodes.size();
        return (n % 2 == 0) ? n / 4.0 : (double)(n * n - 1) / (4.0 * n);
    }

    /**
     * Gibt detaillierte Ring-Informationen zurück.
     */
    public Map<String, Object> getDetailedRingInfo() {
        List<RingMirrorNode> ringNodes = getAllRingNodes();
        Map<String, Object> info = new HashMap<>();

        info.put("minRingSize", minRingSize);
        info.put("currentRingSize", ringNodes.size());
        info.put("isRingIntact", isRingIntact());
        info.put("allowRingExpansion", allowRingExpansion);
        info.put("averagePathLength", calculateAverageRingPathLength());
        info.put("expectedLinks", calculateExpectedLinks(ringNodes.size()));
        info.put("hasRingConstraints", true);

        return info;
    }

    // ===== HILFSMETHODEN =====

    /**
     * Berechnet die erwartete Link-Anzahl für eine gegebene Knotenzahl.
     * Für Ring-Topologie: n Links für n Knoten.
     *
     * @param nodeCount Anzahl der Knoten
     * @return Erwartete Anzahl der Links für Ring-Topologie
     */
    public static int calculateExpectedLinks(int nodeCount) {
        return (nodeCount >= 3) ? nodeCount : 0;
    }

    // ===== KONFIGURATION =====

    public int getMinRingSize() {
        return minRingSize;
    }

    public void setMinRingSize(int minRingSize) {
        this.minRingSize = Math.max(3, minRingSize);
    }

    public boolean isAllowRingExpansion() {
        return allowRingExpansion;
    }

    public void setAllowRingExpansion(boolean allowRingExpansion) {
        this.allowRingExpansion = allowRingExpansion;
    }

    @Override
    public String toString() {
        return String.format("RingTopologyStrategy{minRingSize=%d, allowRingExpansion=%s, " +
                           "currentRingSize=%d, isRingIntact=%s, averagePathLength=%.2f}",
                           minRingSize, allowRingExpansion, getAllRingNodes().size(), 
                           isRingIntact(), calculateAverageRingPathLength());
    }
}
