package org.lrdm.topologies.strategies;

import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.*;
import org.lrdm.topologies.node.*;
import org.lrdm.topologies.validators.SnowflakeTopologyValidator;
import org.lrdm.topologies.exceptions.*;
import org.lrdm.util.IDGenerator;

import java.util.*;

/**
 * Schneeflocken-Topologie-Strategie mit hierarchischer Recursions-Architektur.
 * <p>
 * REKURSIVE 4-EBENEN-STRUKTUR:
 * - Ebene 1: SnowflakeTopologyStrategy (Kern-Verwaltung)
 * - Ebene 2: RingTopologyStrategy (Kern-Ringe mit RING_BRIDGE_STEP_ON_RING-Modulierung)
 * - Ebene 3: LineTopologyStrategy (Brücken zwischen Ringen mit RING_BRIDGE_MIRROR_NUM_HEIGHT)
 * - Ebene 4: DepthLimitedTreeTopologyStrategy (externe Sterne mit EXTERN_STAR_MAX_TREE_DEPTH)
 * <p>
 * PARAMETER-KONFIGURATION:
 * - RING_BRIDGE_STEP_ON_RING: Modulo für Bridge-Positionen zwischen RingMirrorNodes
 * - RING_BRIDGE_MIRROR_NUM_HEIGHT: Anzahl Hops für Ring-zu-Ring-Brücken (LineTopologyStrategy)
 * - EXTERN_STAR_MAX_TREE_DEPTH: Maximale Tiefe der äußeren DepthLimitedTreeTopologyStrategy
 * - BRIDGE_TO_EXTERN_STAR_DISTANCE: Länge der LineTopologyStrategy-Brücken zu externen Strukturen
 * <p>
 * NUTZT BuildAsSubstructure-VERERBUNG:
 * - nodeToSubstructure aus Basisklasse für alle Substruktur-Zuordnungen
 * - structureNodes aus Basisklasse für alle Knoten-Verwaltung
 * - Keine redundanten Maps oder Listen nötig
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class SnowflakeTopologyStrategy extends BuildAsSubstructure {

    /**
     * Einfache Tupel-Klasse für die Mirror-Verteilung.
     */
    public static class MirrorDistribution {
        private final int ringMirrors;
        private final int externalMirrors;

        public MirrorDistribution(int ringMirrors, int externalMirrors) {
            this.ringMirrors = ringMirrors;
            this.externalMirrors = externalMirrors;
        }

        public int getRingMirrors() { return ringMirrors; }
        public int getExternalMirrors() { return externalMirrors; }
    }

    // ===== SCHNEEFLOCKEN-PARAMETER =====
    private static final int RING_BRIDGE_STEP_ON_RING = 3;        // Modulo für Bridge-Positionen (überspringt jeden 3. Mirror)
    private static final int RING_BRIDGE_OFFSET = 1;              // Offset für Bridge-Startposition
    private static final int RING_BRIDGE_MIRROR_NUM_HEIGHT = 2;   // Anzahl Hops für Ring-zu-Ring-Brücken (2 = 2 hops)
    private static final int MAX_RING_LAYERS = 3;                 // Maximale Anzahl von Ring-Schichten
    private static final int MINIMAL_RING_MIRROR_COUNT = 3;       // Minimale Anzahl Mirrors pro Ring
    private static final int EXTERN_STAR_MAX_TREE_DEPTH = 2;      // Maximale Tiefe der äußeren DepthLimitedTreeTopologyStrategy
    private static final int BRIDGE_TO_EXTERN_STAR_DISTANCE = 1; // Länge der Brücken zu externen Strukturen
    private static final double EXTERN_STAR_RATIO = 0.4;         // Anteil der Mirrors für externe Strukturen

    // ===== SCHNEEFLOCKEN-SPEZIFISCHE VERWALTUNG =====
    private int currentRingLayer = 0;
    private final List<MirrorNode> bridgeNodes = new ArrayList<>();
    private final List<MirrorNode> externBridgeNodes = new ArrayList<>();

    // ===== KONSTRUKTOREN =====

    public SnowflakeTopologyStrategy() {
        super();
        validateParameters();
    }

    /**
     * Validiert alle Schneeflocken-Parameter beim Start.
     */
    private void validateParameters() {
        SnowflakeTopologyValidator.validateRingParameters(
                RING_BRIDGE_STEP_ON_RING, RING_BRIDGE_OFFSET, RING_BRIDGE_MIRROR_NUM_HEIGHT,
                MAX_RING_LAYERS, MINIMAL_RING_MIRROR_COUNT
        );
        SnowflakeTopologyValidator.validateStarParameters(
                EXTERN_STAR_MAX_TREE_DEPTH, BRIDGE_TO_EXTERN_STAR_DISTANCE, EXTERN_STAR_RATIO
        );
    }

    // ===== KERN-STRUKTURAUFBAU =====

    /**
     * Erstellt die Schneeflocken-Struktur mit verschachtelten Ringen und externen Anhängen.
     * NUTZT BuildAsSubstructure.nodeToSubstructure für alle Substruktur-Zuordnungen.
     */
    @Override
    protected MirrorNode buildStructure(int totalNodes, int simTime, Properties props) {
        if (totalNodes < getMinimumRequiredMirrors()) {
            throw new SnowflakeTopologyException(
                    "Snowflake benötigt mindestens " + getMinimumRequiredMirrors() + " Knoten"
            );
        }

        // 1. Berechne Mirror-Verteilung
        MirrorDistribution distribution = snowflakeDistribution(totalNodes);
        int mirrorsForRings = distribution.getRingMirrors();
        int mirrorsForExternal = distribution.getExternalMirrors();

        SnowflakeTopologyValidator.validateMirrorDistribution(
                totalNodes, mirrorsForRings, mirrorsForExternal,
                MINIMAL_RING_MIRROR_COUNT, MAX_RING_LAYERS
        );

        // 2. Erstelle eine Kern-Ring-Struktur mit parametrisierten Brücken
        MirrorNode rootNode = buildCoreRingStructure(mirrorsForRings, simTime, props);

        // 3. Erstelle externe DepthLimitedTreeTopologyStrategy-Strukturen
        attachExternalTreeStructures(mirrorsForExternal, simTime, props);

        return rootNode;
    }

    /**
     * Erstellt die Kern-Ring-Struktur mit RING_BRIDGE_STEP_ON_RING-modulierten Bridge-Verbindungen.
     * Nutzt BuildAsSubstructure.buildStructure() für konsistente Substruktur-Erstellung.
     */
    private MirrorNode buildCoreRingStructure(int totalMirrors, int simTime, Properties props) {
        int mirrorsPerRing = Math.max(MINIMAL_RING_MIRROR_COUNT, totalMirrors / MAX_RING_LAYERS);
        int remainingMirrors = totalMirrors;
        MirrorNode snowflakeRoot = null;
        List<MirrorNode> ringRoots = new ArrayList<>();

        SnowflakeTopologyValidator.validateRingConstruction(totalMirrors, MINIMAL_RING_MIRROR_COUNT, MAX_RING_LAYERS);

        // Erstelle Ring-Schichten von innen nach außen
        for (currentRingLayer = 0; currentRingLayer < MAX_RING_LAYERS && remainingMirrors >= MINIMAL_RING_MIRROR_COUNT; currentRingLayer++) {
            int mirrorsForThisRing = Math.min(mirrorsPerRing, remainingMirrors);

            // Erstelle eine Ring-Substruktur direkt mit BuildAsSubstructure-Pattern
            RingTopologyStrategy ringStrategy = new RingTopologyStrategy(MINIMAL_RING_MIRROR_COUNT);

            // Setze verfügbare Mirrors für Ring-Strategie
            List<Mirror> ringMirrors = new ArrayList<>();
            for (int i = 0; i < mirrorsForThisRing && mirrorIterator.hasNext(); i++) {
                ringMirrors.add(mirrorIterator.next());
            }

            // Initialisiere Ring-Strategy mit verfügbaren Mirrors
            ringStrategy.network = this.network;
            ringStrategy.mirrorIterator = ringMirrors.iterator();

            // Nutze buildStructure() aus BuildAsSubstructure
            MirrorNode ringRoot = ringStrategy.buildStructure(mirrorsForThisRing, simTime, props);

            // Registriere alle Ring-Knoten in der Hauptstruktur
            for (MirrorNode ringNode : ringStrategy.getAllStructureNodes()) {
                this.addToStructureNodes(ringNode);
                this.setSubstructureForNode(ringNode, ringStrategy);
            }

            // Setze Root für innerste Ring-Schicht
            if (currentRingLayer == 0) {
                snowflakeRoot = ringRoot;
                this.setCurrentStructureRoot(snowflakeRoot);
            }

            ringRoots.add(ringRoot);
            remainingMirrors -= mirrorsForThisRing;
        }

        // Erstelle Bridge-Verbindungen zwischen Ring-Schichten mit RING_BRIDGE_MIRROR_NUM_HEIGHT-Parametrierung
        createBridgeConnectionsBetweenRings(ringRoots, simTime, props);

        return snowflakeRoot;
    }

    /**
     * Erstellt Bridge-Verbindungen zwischen Ring-Schichten mit RING_BRIDGE_STEP_ON_RING-Modulierung.
     * Jede Brücke ist eine LineTopologyStrategy mit RING_BRIDGE_MIRROR_NUM_HEIGHT-Länge.
     */
    private void createBridgeConnectionsBetweenRings(List<MirrorNode> ringRoots, int simTime, Properties props) {
        for (int i = 0; i < ringRoots.size() - 1; i++) {
            MirrorNode innerRing = ringRoots.get(i);
            MirrorNode outerRing = ringRoots.get(i + 1);

            // Finde Bridge-Knoten basierend auf RING_BRIDGE_STEP_ON_RING-Modulierung
            List<MirrorNode> innerBridgeNodes = findBridgeNodesInRing(innerRing, RING_BRIDGE_STEP_ON_RING);
            List<MirrorNode> outerBridgeNodes = findBridgeNodesInRing(outerRing, RING_BRIDGE_STEP_ON_RING, RING_BRIDGE_OFFSET);

            // Erstelle Line-Brücken zwischen den Ringen
            int bridgeConnections = Math.min(innerBridgeNodes.size(), outerBridgeNodes.size());
            for (int j = 0; j < bridgeConnections; j++) {
                createRingBridgeWithLineStrategy(innerBridgeNodes.get(j), outerBridgeNodes.get(j), simTime, props);
            }
        }
    }

    /**
     * Erstellt eine Bridge-Verbindung zwischen zwei Ring-Knoten mit LineTopologyStrategy.
     * Bridge-Länge wird durch RING_BRIDGE_MIRROR_NUM_HEIGHT bestimmt.
     */
    private void createRingBridgeWithLineStrategy(MirrorNode fromRingNode, MirrorNode toRingNode,
                                                  int simTime, Properties props) {
        if (RING_BRIDGE_MIRROR_NUM_HEIGHT == 1) {
            // 1 hop = direkte Verbindung zwischen Ring-Knoten
            createConnectionLink(fromRingNode, toRingNode, simTime, props);
            bridgeNodes.add(fromRingNode);
            bridgeNodes.add(toRingNode);
        } else {
            // Mehrere hops = LineTopologyStrategy zwischen Ring-Knoten
            LineTopologyStrategy bridgeStrategy = new LineTopologyStrategy(RING_BRIDGE_MIRROR_NUM_HEIGHT, true);

            // Erstelle Mirrors für Bridge-Linie
            List<Mirror> bridgeMirrors = new ArrayList<>();
            for (int i = 0; i < RING_BRIDGE_MIRROR_NUM_HEIGHT - 2 && mirrorIterator.hasNext(); i++) {
                bridgeMirrors.add(mirrorIterator.next());
            }

            if (!bridgeMirrors.isEmpty()) {
                // Initialisiere Bridge-Strategy
                bridgeStrategy.network = this.network;
                bridgeStrategy.mirrorIterator = bridgeMirrors.iterator();

                // Erstelle Bridge-Linie
                MirrorNode bridgeRoot = bridgeStrategy.buildStructure(bridgeMirrors.size(), simTime, props);

                // Registriere Bridge-Knoten
                for (MirrorNode bridgeNode : bridgeStrategy.getAllStructureNodes()) {
                    this.addToStructureNodes(bridgeNode);
                    this.setSubstructureForNode(bridgeNode, bridgeStrategy);
                }

                // Verbinde Ring-Knoten mit Bridge-Enden
                if (bridgeRoot != null) {
                    List<MirrorNode> bridgeEndpoints = findLineEndpoints(bridgeRoot);
                    if (bridgeEndpoints.size() >= 2) {
                        createConnectionLink(fromRingNode, bridgeEndpoints.get(0), simTime, props);
                        createConnectionLink(toRingNode, bridgeEndpoints.get(1), simTime, props);

                        bridgeNodes.add(fromRingNode);
                        bridgeNodes.add(toRingNode);
                        externBridgeNodes.addAll(bridgeEndpoints);
                    }
                }
            } else {
                // Fallback: Direkte Verbindung
                createConnectionLink(fromRingNode, toRingNode, simTime, props);
                bridgeNodes.add(fromRingNode);
                bridgeNodes.add(toRingNode);
            }
        }
    }

    /**
     * Erstellt externe DepthLimitedTreeTopologyStrategy-Strukturen an Bridge-Verbindungen.
     * Bridge-Länge zu externen Strukturen wird durch BRIDGE_TO_EXTERN_STAR_DISTANCE bestimmt.
     */
    private void attachExternalTreeStructures(int mirrorsForExternal, int simTime, Properties props) {
        if (mirrorsForExternal <= 0 || externBridgeNodes.isEmpty()) return;

        int mirrorsPerTree = Math.max(2, mirrorsForExternal / externBridgeNodes.size());
        int remainingMirrors = mirrorsForExternal;

        for (MirrorNode bridgeNode : externBridgeNodes) {
            if (remainingMirrors <= 0) break;

            int mirrorsForThisTree = Math.min(mirrorsPerTree, remainingMirrors);

            // Erstelle eine Brücke zu externer Struktur (falls BRIDGE_TO_EXTERN_STAR_DISTANCE > 0)
            MirrorNode connectionPoint = bridgeNode;
            if (BRIDGE_TO_EXTERN_STAR_DISTANCE > 0) {
                connectionPoint = createExternalBridgeWithLineStrategy(bridgeNode, simTime, props);
                if (connectionPoint == null) connectionPoint = bridgeNode;
            }

            // Erstelle DepthLimitedTreeTopologyStrategy
            DepthLimitTreeTopologyStrategy treeStrategy = new DepthLimitTreeTopologyStrategy(EXTERN_STAR_MAX_TREE_DEPTH);

            // Setze verfügbare Mirrors für Tree-Strategie
            List<Mirror> treeMirrors = new ArrayList<>();
            for (int i = 0; i < mirrorsForThisTree && mirrorIterator.hasNext(); i++) {
                treeMirrors.add(mirrorIterator.next());
            }

            // Initialisiere Tree-Strategy
            treeStrategy.network = this.network;
            treeStrategy.mirrorIterator = treeMirrors.iterator();

            // Nutze buildStructure() aus BuildAsSubstructure
            MirrorNode treeRoot = treeStrategy.buildStructure(mirrorsForThisTree, simTime, props);

            // Registriere Tree-Knoten in der Hauptstruktur
            for (MirrorNode treeNode : treeStrategy.getAllStructureNodes()) {
                this.addToStructureNodes(treeNode);
                this.setSubstructureForNode(treeNode, treeStrategy);
            }

            // Verbinde Tree-Struktur mit Connection-Point
            if (treeRoot != null) {
                createConnectionLink(connectionPoint, treeRoot, simTime, props);
            }

            remainingMirrors -= mirrorsForThisTree;
        }
    }

    /**
     * Erstellt eine Brücke zu einer externen Struktur mit LineTopologyStrategy.
     * Bridge-Länge wird durch BRIDGE_TO_EXTERN_STAR_DISTANCE bestimmt.
     */
    private MirrorNode createExternalBridgeWithLineStrategy(MirrorNode startNode, int simTime, Properties props) {
        if (BRIDGE_TO_EXTERN_STAR_DISTANCE <= 0) return startNode;

        LineTopologyStrategy bridgeStrategy = new LineTopologyStrategy(BRIDGE_TO_EXTERN_STAR_DISTANCE + 1, true);

        // Erstelle Mirrors für externe Bridge
        List<Mirror> bridgeMirrors = new ArrayList<>();
        for (int i = 0; i < BRIDGE_TO_EXTERN_STAR_DISTANCE && mirrorIterator.hasNext(); i++) {
            bridgeMirrors.add(mirrorIterator.next());
        }

        if (!bridgeMirrors.isEmpty()) {
            // Initialisiere Bridge-Strategy
            bridgeStrategy.network = this.network;
            bridgeStrategy.mirrorIterator = bridgeMirrors.iterator();

            // Erstelle Bridge-Linie
            MirrorNode bridgeRoot = bridgeStrategy.buildStructure(bridgeMirrors.size(), simTime, props);

            // Registriere Bridge-Knoten
            for (MirrorNode bridgeNode : bridgeStrategy.getAllStructureNodes()) {
                this.addToStructureNodes(bridgeNode);
                this.setSubstructureForNode(bridgeNode, bridgeStrategy);
            }

            // Verbinde Start-Knoten mit Bridge-Anfang und gibt Bridge-Ende zurück
            if (bridgeRoot != null) {
                createConnectionLink(startNode, bridgeRoot, simTime, props);

                List<MirrorNode> bridgeEndpoints = findLineEndpoints(bridgeRoot);
                // Finde das andere Ende der Bridge (nicht das Root-Ende)
                for (MirrorNode endpoint : bridgeEndpoints) {
                    if (!endpoint.equals(bridgeRoot)) {
                        return endpoint;
                    }
                }

                // Fallback: Nutze Bridge-Root as Connection-Point
                return bridgeRoot;
            }
        }

        return startNode; // Fallback: Original-Knoten
    }

    // ===== HILFSMETHODEN FÜR RING-BRIDGE-MANAGEMENT =====

    /**
     * Findet Bridge-Knoten in einem Ring basierend auf RING_BRIDGE_STEP_ON_RING-Modulierung.
     */
    private List<MirrorNode> findBridgeNodesInRing(MirrorNode ringRoot, int step) {
        return findBridgeNodesInRing(ringRoot, step, 0);
    }

    /**
     * Findet Bridge-Knoten in einem Ring basierend auf RING_BRIDGE_STEP_ON_RING-Modulierung mid Offset.
     */
    private List<MirrorNode> findBridgeNodesInRing(MirrorNode ringRoot, int step, int offset) {
        List<MirrorNode> bridgeNodes = new ArrayList<>();

        if (ringRoot == null || step <= 0) return bridgeNodes;

        // Sammle alle Ring-Knoten
        List<MirrorNode> ringNodes = new ArrayList<>();
        Set<MirrorNode> visited = new HashSet<>();
        collectRingNodes(ringRoot, ringNodes, visited);

        // Wähle Bridge-Knoten basierend auf Step-Modulierung
        for (int i = offset; i < ringNodes.size(); i += step) {
            if (i < ringNodes.size()) {
                bridgeNodes.add(ringNodes.get(i));
            }
        }

        return bridgeNodes;
    }

    /**
     * Sammelt alle Knoten eines Rings rekursiv.
     */
    private void collectRingNodes(MirrorNode current, List<MirrorNode> ringNodes, Set<MirrorNode> visited) {
        if (current == null || visited.contains(current)) return;

        visited.add(current);
        ringNodes.add(current);

        // Traversiere Ring-Struktur
        for (StructureNode child : current.getChildren()) {
            if (child instanceof MirrorNode childMirror && !visited.contains(childMirror)) {
                collectRingNodes(childMirror, ringNodes, visited);
            }
        }
    }

    /**
     * Findet die Endpunkte einer Line-Struktur.
     */
    private List<MirrorNode> findLineEndpoints(MirrorNode lineRoot) {
        List<MirrorNode> endpoints = new ArrayList<>();
        Set<MirrorNode> visited = new HashSet<>();

        findLineEndpointsRecursive(lineRoot, endpoints, visited);

        return endpoints;
    }

    /**
     * Findet Line-Endpunkte rekursiv.
     */
    private void findLineEndpointsRecursive(MirrorNode current, List<MirrorNode> endpoints, Set<MirrorNode> visited) {
        if (current == null || visited.contains(current)) return;

        visited.add(current);

        // Zähle Verbindungen
        int connectionCount = 0;
        if (current.getParent() != null) connectionCount++;
        connectionCount += current.getChildren().size();

        // Endpunkt hat maximal 1 Verbindung
        if (connectionCount <= 1) {
            endpoints.add(current);
        }

        // Traversiere weiter
        for (StructureNode child : current.getChildren()) {
            if (child instanceof MirrorNode childMirror) {
                findLineEndpointsRecursive(childMirror, endpoints, visited);
            }
        }
    }

    /**
     * Erstellt eine Verbindung zwischen zwei MirrorNodes.
     */
    private void createConnectionLink(MirrorNode from, MirrorNode to, int simTime, Properties props) {
        if (from == null || to == null) return;

        Mirror fromMirror = from.getMirror();
        Mirror toMirror = to.getMirror();

        if (fromMirror != null && toMirror != null && !isAlreadyConnected(fromMirror, toMirror)) {
            Link newLink = new Link(IDGenerator.getInstance().getNextID(), fromMirror, toMirror, simTime, props);
            if (network != null) {
                network.getLinks().add(newLink);
            }
        }
    }

    /**
     * Prüft, ob zwei Mirrors bereits verbunden sind.
     */
    private boolean isAlreadyConnected(Mirror mirror1, Mirror mirror2) {
        if (mirror1 == null || mirror2 == null) return false;

        for (Link link : mirror1.getLinks()) {
            if ((link.getSource().equals(mirror1) && link.getTarget().equals(mirror2)) ||
                    (link.getSource().equals(mirror2) && link.getTarget().equals(mirror1))) {
                return true;
            }
        }
        return false;
    }

    // ===== SUBSTRUKTUR-VERWALTUNG =====

    /**
     * Fügt neue Knoten zur Schneeflocken-Struktur hinzu.
     * Erweitert zuerst externe Strukturen, dann Kern-Ringe.
     */
    @Override
    protected int addNodesToStructure(int nodesToAdd) {
        if (nodesToAdd <= 0) return 0;

        int addedNodes = 0;

        // Erweitere zuerst die externen DepthLimitedTreeTopologyStrategy-Strukturen
        Map<MirrorNode, BuildAsSubstructure> substructureMap = getNodeToSubstructureMapping();
        for (Map.Entry<MirrorNode, BuildAsSubstructure> entry : substructureMap.entrySet()) {
            BuildAsSubstructure substructure = entry.getValue();

            if (substructure instanceof DepthLimitTreeTopologyStrategy && addedNodes < nodesToAdd) {
                int treeAdditions = Math.min(nodesToAdd - addedNodes, 3);
                addedNodes += substructure.addNodesToStructure(treeAdditions);

                // Registriere neue Knoten in der Hauptstruktur
                for (MirrorNode newNode : substructure.getAllStructureNodes()) {
                    if (!this.getAllStructureNodes().contains(newNode)) {
                        this.addToStructureNodes(newNode);
                        this.setSubstructureForNode(newNode, substructure);
                    }
                }
            }
        }

        // Erweitere dann die Kern-Ring-Strukturen
        if (addedNodes < nodesToAdd) {
            for (Map.Entry<MirrorNode, BuildAsSubstructure> entry : substructureMap.entrySet()) {
                BuildAsSubstructure substructure = entry.getValue();

                if (substructure instanceof RingTopologyStrategy && addedNodes < nodesToAdd) {
                    int ringAdditions = Math.min(nodesToAdd - addedNodes, 2);
                    addedNodes += substructure.addNodesToStructure(ringAdditions);

                    // Registriere neue Knoten in der Hauptstruktur
                    for (MirrorNode newNode : substructure.getAllStructureNodes()) {
                        if (!this.getAllStructureNodes().contains(newNode)) {
                            this.addToStructureNodes(newNode);
                            this.setSubstructureForNode(newNode, substructure);
                        }
                    }
                }
            }
        }

        return addedNodes;
    }

    /**
     * Entfernt Knoten aus der Schneeflocken-Struktur.
     * Entfernt zuerst aus externen Strukturen, dann aus Kern-Ringen.
     */
    @Override
    protected int removeNodesFromStructure(int nodesToRemove) {
        if (nodesToRemove <= 0) return 0;

        int removedNodes = 0;
        Map<MirrorNode, BuildAsSubstructure> substructureMap = getNodeToSubstructureMapping();

        // Entferne zuerst aus externen DepthLimitedTreeTopologyStrategy-Strukturen
        for (Map.Entry<MirrorNode, BuildAsSubstructure> entry : new HashMap<>(substructureMap).entrySet()) {
            BuildAsSubstructure substructure = entry.getValue();

            if (substructure instanceof DepthLimitTreeTopologyStrategy && removedNodes < nodesToRemove) {
                int treeRemovals = Math.min(nodesToRemove - removedNodes, 2);
                removedNodes += substructure.removeNodesFromStructure(treeRemovals);

                // Entferne gelöschte Knoten aus der Hauptstruktur
                Set<MirrorNode> currentNodes = new HashSet<>(this.getAllStructureNodes());
                for (MirrorNode node : currentNodes) {
                    if (!substructure.getAllStructureNodes().contains(node)) {
                        this.removeFromStructureNodes(node);
                        this.removeSubstructureForNode(node);
                    }
                }
            }
        }

        // Entferne dann aus Line-Bridge-Strukturen
        if (removedNodes < nodesToRemove) {
            for (Map.Entry<MirrorNode, BuildAsSubstructure> entry : new HashMap<>(substructureMap).entrySet()) {
                BuildAsSubstructure substructure = entry.getValue();

                if (substructure instanceof LineTopologyStrategy && removedNodes < nodesToRemove) {
                    int lineRemovals = Math.min(nodesToRemove - removedNodes, 1);
                    removedNodes += substructure.removeNodesFromStructure(lineRemovals);

                    // Entferne gelöschte Knoten aus der Hauptstruktur
                    Set<MirrorNode> currentNodes = new HashSet<>(this.getAllStructureNodes());
                    for (MirrorNode node : currentNodes) {
                        if (!substructure.getAllStructureNodes().contains(node)) {
                            this.removeFromStructureNodes(node);
                            this.removeSubstructureForNode(node);
                        }
                    }
                }
            }
        }

        // Entferne schließlich aus Kern-Ring-Strukturen (nur im Notfall)
        if (removedNodes < nodesToRemove) {
            for (Map.Entry<MirrorNode, BuildAsSubstructure> entry : new HashMap<>(substructureMap).entrySet()) {
                BuildAsSubstructure substructure = entry.getValue();

                if (substructure instanceof RingTopologyStrategy && removedNodes < nodesToRemove) {
                    int ringRemovals = Math.min(nodesToRemove - removedNodes, 1);
                    removedNodes += substructure.removeNodesFromStructure(ringRemovals);

                    // Entferne gelöschte Knoten aus der Hauptstruktur
                    Set<MirrorNode> currentNodes = new HashSet<>(this.getAllStructureNodes());
                    for (MirrorNode node : currentNodes) {
                        if (!substructure.getAllStructureNodes().contains(node)) {
                            this.removeFromStructureNodes(node);
                            this.removeSubstructureForNode(node);
                        }
                    }
                }
            }
        }

        return removedNodes;
    }

    /**
     * Erstellt und verbindet alle Links für die Schneeflocken-Struktur.
     * Sammelt Links aus allen Substrukturen (Ringe, Lines, Trees) und fügt sie zum Netzwerk hinzu.
     *
     * @param root    Die Root-Node der Struktur
     * @param props   Simulation Properties
     * @param simTime
     * @return Set aller erstellten Links
     */
    @Override
    protected Set<Link> buildAndConnectLinks(MirrorNode root, Properties props, int simTime) {
        Set<Link> allLinks = new HashSet<>();

        if (root == null || network == null) {
            return allLinks;
        }

        // Sammle Links aus allen Substrukturen
        Map<MirrorNode, BuildAsSubstructure> substructureMap = getNodeToSubstructureMapping();

        for (Map.Entry<MirrorNode, BuildAsSubstructure> entry : substructureMap.entrySet()) {
            BuildAsSubstructure substructure = entry.getValue();
            MirrorNode subRoot = entry.getKey();

            // Sammle Links aus jeder Substruktur
            if (subRoot != null) {
                Set<Link> substructureLinks = substructure.buildAndConnectLinks(subRoot, props, 0);
                allLinks.addAll(substructureLinks);
            }
        }

        // Erstelle zusätzliche Bridge-Verbindungen zwischen Substrukturen
        allLinks.addAll(createInterSubstructureBridgeLinks(props));

        // Füge alle Links zum Netzwerk hinzu
        for (Link link : allLinks) {
            network.getLinks().add(link);
        }

        return allLinks;
    }

    /**
     * Erstellt Bridge-Links zwischen verschiedenen Substrukturen.
     * Diese Links sind bereits in der buildStructure-Phase erstellt worden,
     * müssen aber hier noch einmal validiert und gesammelt werden.
     *
     * @param props Simulation Properties
     * @return Set der Bridge-Links zwischen Substrukturen
     */
    private Set<Link> createInterSubstructureBridgeLinks(Properties props) {
        Set<Link> bridgeLinks = new HashSet<>();

        // Sammle alle existierenden Links zwischen verschiedenen Substrukturen
        Set<MirrorNode> allNodes = getAllStructureNodes();

        for (MirrorNode node : allNodes) {
            Mirror mirror = node.getMirror();
            if (mirror != null) {
                for (Link link : mirror.getLinks()) {
                    // Prüfe, ob Link zwischen verschiedenen Substrukturen besteht
                    Mirror source = link.getSource();
                    Mirror target = link.getTarget();

                    if (isBridgeLink(source, target)) {
                        bridgeLinks.add(link);
                    }
                }
            }
        }

        return bridgeLinks;
    }

    /**
     * Prüft, ob ein Link eine Brücke zwischen verschiedenen Substrukturen darstellt.
     *
     * @param source Quell-Mirror
     * @param target Ziel-Mirror
     * @return true, wenn es sich um einen Bridge-Link handelt
     */
    private boolean isBridgeLink(Mirror source, Mirror target) {
        if (source == null || target == null) return false;

        // Finde entsprechende MirrorNodes
        MirrorNode sourceNode = findNodeByMirror(source);
        MirrorNode targetNode = findNodeByMirror(target);

        if (sourceNode == null || targetNode == null) return false;

        // Prüfe, ob sie zu verschiedenen Substrukturen gehören
        Map<MirrorNode, BuildAsSubstructure> substructureMap = getNodeToSubstructureMapping();
        BuildAsSubstructure sourceSubstructure = substructureMap.get(sourceNode);
        BuildAsSubstructure targetSubstructure = substructureMap.get(targetNode);

        return sourceSubstructure != null && targetSubstructure != null &&
                !sourceSubstructure.equals(targetSubstructure);
    }

    /**
     * Findet einen MirrorNode basierend auf seinem zugewiesenen Mirror.
     *
     * @param mirror Der zu suchende Mirror
     * @return Der entsprechende MirrorNode oder null
     */
    private MirrorNode findNodeByMirror(Mirror mirror) {
        if (mirror == null) return null;

        for (MirrorNode node : getAllStructureNodes()) {
            if (mirror.equals(node.getMirror())) {
                return node;
            }
        }

        return null;
    }

    // ===== TOPOLOGY STRATEGY INTERFACE IMPLEMENTATION =====

    @Override
    public Set<Link> initNetwork(Network n, Properties props) {
        this.network = n;
        this.idGenerator = IDGenerator.getInstance();

        if (n.getNumMirrors() < getMinimumRequiredMirrors()) {
            return new HashSet<>();
        }

        this.mirrorIterator = n.getMirrors().iterator();
        MirrorNode root = buildStructure(n.getNumMirrors(), 0, props);

        if (root != null) {
            setCurrentStructureRoot(root);
            return buildAndConnectLinks(root, props, 0);
        }

        return new HashSet<>();
    }

    @Override
    public void restartNetwork(Network n, Properties props, int simTime) {
        super.restartNetwork(n, props, simTime);
        bridgeNodes.clear();
        externBridgeNodes.clear();
        currentRingLayer = 0;
        initNetwork(n, props);
    }

    @Override
    public void handleAddNewMirrors(Network n, int newMirrors, Properties props, int simTime) {
        if (newMirrors <= 0) return;

        this.network = n;

        List<Mirror> addedMirrors = createMirrors(newMirrors, simTime, props);
        n.getMirrors().addAll(addedMirrors);

        this.mirrorIterator = addedMirrors.iterator();
        addNodesToStructure(newMirrors);

        MirrorNode root = getCurrentStructureRoot();
        if (root != null) {
            buildAndConnectLinks(root, props, 0);
        }
    }

    @Override
    public int getNumTargetLinks(Network n) {
        if (n == null) return 0;

        int totalMirrors = n.getNumMirrors();
        MirrorDistribution distribution = snowflakeDistribution(totalMirrors);

        // Schätze Links: Kern-Ringe + Bridge-Links + externe Bäume
        int ringLinks = distribution.getRingMirrors(); // Approximation für Ring-Links
        int treeLinks = Math.max(0, distribution.getExternalMirrors() - 1); // Baum-Links (n-1)
        int bridgeLinks = calculateBridgeLinks(distribution.getRingMirrors(), distribution.getExternalMirrors());

        return ringLinks + treeLinks + bridgeLinks;
    }

    @Override
    public int getPredictedNumTargetLinks(Action a) {
        if (a == null || network == null) {
            return network != null ? getNumTargetLinks(network) : 0;
        }

        if (a instanceof MirrorChange mirrorChange) {
            int newMirrorCount = mirrorChange.getNewMirrors();
            // Erstelle temporäres Network-Objekt für Berechnung
            try {
                TopologyStrategy currentStrategy = network.getTopologyStrategy();
                Network tempNetwork = new Network(currentStrategy, newMirrorCount, 2, 1024, new Properties());
                return getNumTargetLinks(tempNetwork);
            } catch (Exception e) {
                // Fallback: Direkte Berechnung
                MirrorDistribution distribution = snowflakeDistribution(newMirrorCount);
                int ringLinks = distribution.getRingMirrors();
                int treeLinks = Math.max(0, distribution.getExternalMirrors() - 1);
                int bridgeLinks = calculateBridgeLinks(distribution.getRingMirrors(), distribution.getExternalMirrors());
                return ringLinks + treeLinks + bridgeLinks;
            }
        }

        if (a instanceof TargetLinkChange targetLinkChange) {
            // Schneeflocken-Topologie hat feste Struktur - begrenzte Anpassbarkeit
            int currentLinks = getNumTargetLinks(network);
            int requestedTotalLinks = targetLinkChange.getNewLinksPerMirror() * network.getNumMirrors();

            // Schneeflocke kann nur in bestimmten Grenzen angepasst werden
            return Math.min(requestedTotalLinks, currentLinks + (currentLinks / 4)); // Max. 25 % Erhöhung
        }

        if (a instanceof TopologyChange topologyChange) {
            TopologyStrategy newTopology = topologyChange.getNewTopology();
            return newTopology.getNumTargetLinks(network);
        }

        return getNumTargetLinks(network);
    }

    // ===== HILFSMETHODEN =====

    /**
     * Berechnet die Mirror-Verteilung zwischen Kern-Ringen und externen Strukturen.
     */
    private MirrorDistribution snowflakeDistribution(int totalMirrors) {
        int mirrorsForExternal = (int) (totalMirrors * EXTERN_STAR_RATIO);
        int mirrorsForRings = totalMirrors - mirrorsForExternal;

        // Stelle sicher, dass genug Mirrors für Kern-Ringe vorhanden sind
        int minRingMirrors = MINIMAL_RING_MIRROR_COUNT;
        if (mirrorsForRings < minRingMirrors) {
            mirrorsForRings = minRingMirrors;
            mirrorsForExternal = Math.max(0, totalMirrors - mirrorsForRings);
        }

        return new MirrorDistribution(mirrorsForRings, mirrorsForExternal);
    }

    /**
     * Berechnet die geschätzte Anzahl der Bridge-Links.
     */
    private int calculateBridgeLinks(int ringMirrors, int externalMirrors) {
        int ringLayers = Math.min(MAX_RING_LAYERS, Math.max(1, ringMirrors / MINIMAL_RING_MIRROR_COUNT));
        int bridgeBetweenRings = Math.max(0, ringLayers - 1) * RING_BRIDGE_MIRROR_NUM_HEIGHT;
        int bridgesToExternal = Math.min(externalMirrors, bridgeNodes.size()) * (BRIDGE_TO_EXTERN_STAR_DISTANCE + 1);

        return bridgeBetweenRings + bridgesToExternal;
    }

    /**
     * Gibt die minimale Anzahl von Mirrors zurück, die für eine Schneeflocke benötigt wird.
     */
    public int getMinimumRequiredMirrors() {
        return MINIMAL_RING_MIRROR_COUNT + 2; // Mindestens ein Ring + 2 externe Knoten
    }

    @Override
    public String toString() {
        return "SnowflakeTopologyStrategy{" +
                "ringBridgeStep=" + RING_BRIDGE_STEP_ON_RING +
                ", ringBridgeHeight=" + RING_BRIDGE_MIRROR_NUM_HEIGHT +
                ", externTreeDepth=" + EXTERN_STAR_MAX_TREE_DEPTH +
                ", bridgeToExternDistance=" + BRIDGE_TO_EXTERN_STAR_DISTANCE +
                ", externRatio=" + EXTERN_STAR_RATIO +
                ", substructureId=" + getSubstructureId() +
                '}';
    }
}