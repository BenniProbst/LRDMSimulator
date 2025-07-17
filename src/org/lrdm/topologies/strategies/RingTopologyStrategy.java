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
 * <p>
 * **Head-Node-basierte Operationen**:
 * - Hinzufügen: Neue Knoten werden hinter der Head-Node eingefügt
 * - Entfernen: Knoten werden von hinten nach vorne entfernt (entfernt von der Head-Node)
 * - Ring-Länge: Head-Node + n Ring-Knoten = Gesamtstruktur
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
     * HEAD-NODE-BASIERT: Neue Knoten werden hinter der Head-Node eingefügt.
     * Überschreibt BuildAsSubstructure für Ring-Einfügung hinter Head-Node.
     */
    @Override
    protected int addNodesToStructure(int nodesToAdd) {
        if (nodesToAdd <= 0 || !allowRingExpansion) return 0;

        RingMirrorNode head = getRingHead();
        if (head == null) return 0;

        // Prüfe Ring-Länge: Head-Node + n Ring-Knoten
        int currentRingLength = calculateCurrentRingLength();
        if (currentRingLength < minRingSize) return 0;

        int actuallyAdded = 0;

        // HEAD-NODE-BASIERT: Füge neue Knoten hinter der Head-Node ein
        RingMirrorNode currentInsertionPoint = head;

        for (int i = 0; i < nodesToAdd && mirrorIterator.hasNext(); i++) {
            RingMirrorNode nextNode = currentInsertionPoint.getNextInRing();
            if (nextNode == null) break;

            // Erstelle neuen Knoten
            RingMirrorNode newNode = createRingNode(0, new Properties());
            if (newNode != null) {
                // Füge hinter der aktuellen Position ein
                insertNodeIntoRing(currentInsertionPoint, newNode, nextNode, 0, new Properties());
                actuallyAdded++;

                // Nächster Knoten wird hinter dem neu eingefügten Knoten eingefügt
                currentInsertionPoint = newNode;
            }
        }

        return actuallyAdded;
    }

    /**
     * **PLANUNGSEBENE**: Entfernt Knoten aus der Ring-Struktur.
     * HEAD-NODE-BASIERT: Entfernt Knoten von hinten nach vorne (entfernt von Head-Node).
     * Überschreibt BuildAsSubstructure für Ring-erhaltende Entfernung.
     */
    @Override
    protected int removeNodesFromStructure(int nodesToRemove) {
        if (nodesToRemove <= 0) return 0;

        // Prüfe Ring-Länge: Head-Node + n Ring-Knoten
        int currentRingLength = calculateCurrentRingLength();
        if (currentRingLength - nodesToRemove < minRingSize) {
            nodesToRemove = currentRingLength - minRingSize;
        }
        if (nodesToRemove <= 0) return 0;

        RingMirrorNode head = getRingHead();
        if (head == null) return 0;

        int actuallyRemoved = 0;

        // HEAD-NODE-BASIERT: Entferne Knoten von hinten nach vorne,
        // finde den entferntesten Knoten von der Head-Node
        for (int i = 0; i < nodesToRemove; i++) {
            RingMirrorNode nodeToRemove = findNodeFarthestFromHead(head);
            if (nodeToRemove != null && nodeToRemove != head) {
                removeNodeFromRingStructuralPlanning(nodeToRemove);
                actuallyRemoved++;
            }
        }

        return actuallyRemoved;
    }

    /**
     * **AUSFÜHRUNGSEBENE**: Überschreibt die Mirror-Entfernung für Ring-Erhaltung.
     * HEAD-NODE-BASIERT: Entfernt Mirrors von hinten nach vorne (entfernt von Head-Node).
     * Führt Mirror-Shutdown innerhalb der strukturellen Ring-Planungsgrenzen aus.
     */
    @Override
    public Set<Mirror> handleRemoveMirrors(Network n, int removeMirrors, Properties props, int simTime) {
        if (removeMirrors <= 0) {
            return new HashSet<>();
        }

        // Prüfe Ring-Länge: Head-Node + n Ring-Knoten
        int currentRingLength = calculateCurrentRingLength();
        if (currentRingLength - removeMirrors < minRingSize) {
            removeMirrors = currentRingLength - minRingSize;
        }
        if (removeMirrors <= 0) {
            return new HashSet<>();
        }

        RingMirrorNode head = getRingHead();
        if (head == null) {
            return new HashSet<>();
        }

        Set<Mirror> cleanedMirrors = new HashSet<>();
        int actuallyRemoved = 0;

        // HEAD-NODE-BASIERT: Entferne Mirrors von hinten nach vorne
        for (int i = 0; i < removeMirrors; i++) {
            RingMirrorNode nodeToRemove = findNodeFarthestFromHead(head);
            if (nodeToRemove != null && nodeToRemove != head) {
                Mirror targetMirror = nodeToRemove.getMirror();
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

    // ===== FEHLENDE TOPOLOGY STRATEGY METHODEN =====

    /**
     * Startet das Netzwerk komplett neu mit der Ring-Topologie.
     * Überschreibt die Basis-Implementierung für Ring-spezifische Neustartlogik.
     */
    @Override
    public void restartNetwork(Network n, Properties props, int simTime) {
        super.restartNetwork(n, props, simTime);

        // Bereinige Ring-spezifische Zustände
        clearRingState();

        // Initialisiere Ring-Netzwerk neu
        initNetwork(n, props);
    }

    /**
     * Berechnet die erwartete Anzahl der Links, wenn die gegebene Aktion ausgeführt wird.
     * Ring-spezifische Implementierung basierend auf den drei Action-Typen.
     * Überschreibt die abstrakte Methode aus TopologyStrategy.
     */
    @Override
    public int getPredictedNumTargetLinks(Action a) {
        if (a == null) {
            return network != null ? getNumTargetLinks(network) : 0;
        }

        // 1. MirrorChange: Ring-Größe ändert sich dynamisch
        if (a instanceof MirrorChange mirrorChange) {
            int newMirrorCount = mirrorChange.getNewMirrors();
            return calculateExpectedLinks(newMirrorCount);
        }

        // 2. TargetLinkChange: KEIN Effekt bei Ring-Topologie
        if (a instanceof TargetLinkChange) {
            return network != null ? getNumTargetLinks(network) : 0;
        }

        // 3. TopologyChange: Komplette Rekonstruktion
        if (a instanceof TopologyChange topologyChange) {
            TopologyStrategy newTopology = topologyChange.getNewTopology();
            if (newTopology instanceof RingTopologyStrategy) {
                int currentMirrors = network != null ? network.getNumMirrors() : 0;
                return calculateExpectedLinks(currentMirrors);
            }
            if (network != null) {
                return newTopology.getNumTargetLinks(network);
            }
            return 0;
        }

        return network != null ? getNumTargetLinks(network) : 0;
    }

    // ===== RING-SPEZIFISCHE HILFSMETHODEN =====

    /**
     * HEAD-NODE-BASIERT: Berechnet die aktuelle Ring-Länge (Head-Node + n Ring-Knoten).
     * Prüft die Tiefe der Struktur bzw. die Länge des Rings.
     */
    private int calculateCurrentRingLength() {
        RingMirrorNode head = getRingHead();
        if (head == null) return 0;

        int length = 0;
        RingMirrorNode current = head;
        Set<RingMirrorNode> visited = new HashSet<>();

        // Durchlaufe den Ring und zähle alle Knoten
        do {
            if (visited.contains(current)) {
                break; // Verhindere Endlosschleife
            }
            visited.add(current);
            length++;
            current = current.getNextInRing();
        } while (current != null && current != head);

        return length;
    }

    /**
     * HEAD-NODE-BASIERT: Findet den Knoten, der am weitesten von der Head-Node entfernt ist.
     * Wird für die Entfernung "von hinten nach vorne" verwendet.
     */
    private RingMirrorNode findNodeFarthestFromHead(RingMirrorNode head) {
        if (head == null) return null;

        RingMirrorNode current = head;
        RingMirrorNode farthest = null;
        int maxDistance = -1;
        int currentDistance = 0;
        Set<RingMirrorNode> visited = new HashSet<>();

        // Durchlaufe den Ring und finde den entferntesten Knoten
        do {
            if (visited.contains(current)) {
                break; // Verhindere Endlosschleife
            }
            visited.add(current);

            if (current != head && currentDistance > maxDistance) {
                maxDistance = currentDistance;
                farthest = current;
            }

            current = current.getNextInRing();
            currentDistance++;
        } while (current != null && current != head);

        return farthest;
    }

    /**
     * Bereinigt. Ring-spezifische Zustände für Neustarts.
     */
    private void clearRingState() {
        // Bereinige alle Ring-spezifischen Zustände
        // (Wird bei Bedarf erweitert)
    }

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

        // Bereinige alle Verbindungen des entfernten Knotens
        Set<StructureNode> childrenToRemove = new HashSet<>();
        for (StructureNode child : nodeToRemove.getChildren()) {
            childrenToRemove.add(child);
        }

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
                        "currentRingSize=%d, ringLength=%d}",
                minRingSize, allowRingExpansion, getAllRingNodes().size(),
                calculateCurrentRingLength());
    }
}