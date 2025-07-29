package org.lrdm.topologies.strategies;

import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.Action;
import org.lrdm.effectors.MirrorChange;
import org.lrdm.effectors.TargetLinkChange;
import org.lrdm.effectors.TopologyChange;
import org.lrdm.topologies.node.MirrorNode;
import org.lrdm.topologies.node.RingMirrorNode;
import org.lrdm.topologies.node.StructureNode;
import org.lrdm.topologies.validators.SnowflakeTopologyValidator;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Schneeflocken-Topologie-Strategie mit hierarchischer Multi-Topologie-Architektur.
 * <p>
 * **HIERARCHISCHE STRUKTUR**:
 * - **Zentrum**: Zentraler Ring (RingTopologyStrategy)
 * - **Ring-Schichten**: Bis zu 3 konzentrische Ringe
 * - **Ring-Brücken**: LineTopologyStrategy-Verbindungen zwischen Ringen
 * - **Externe Bäume**: DepthLimitTreeTopologyStrategy an Ring-Brücken
 * <p>
 * **VERWENDUNG BuildAsSubstructure**:
 * - Alle Substrukturen werden über BuildAsSubstructure.nodeToSubstructure verwaltet
 * - Jede Substruktur hat nur eine Head-Node als Eingangspunkt
 * - Ring-, Linien- und Baum-Strategien werden als Bausteine verwendet
 * - Keine redundante Map- oder Listen-Verwaltung
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class SnowflakeTopologyStrategy extends BuildAsSubstructure {

    /**
     * Einfache Tupel-Klasse für die Mirror-Verteilung.
     */
    public record MirrorDistribution(int ringMirrors, int externalMirrors) {
    }

    // ===== SCHNEEFLOCKEN-PARAMETER =====
    private static final int RING_BRIDGE_STEP_ON_RING = 3;        // Modulo für Bridge-Positionen
    private static final int RING_BRIDGE_OFFSET = 1;              // Offset für Bridge-Startposition
    private static final int RING_BRIDGE_LENGTH = 2;              // Länge der Ring-zu-Ring-Brücken
    private static final int MAX_RING_LAYERS = 3;                 // Maximale Anzahl Ring-Schichten
    private static final int MINIMAL_RING_SIZE = 3;               // Minimale Anzahl Mirrors pro Ring
    private static final int EXTERN_TREE_MAX_DEPTH = 2;           // Maximale Tiefe externer Bäume
    private static final int BRIDGE_TO_EXTERN_LENGTH = 1;         // Länge der Brücken zu externen Strukturen
    private static final double EXTERN_STRUCTURE_RATIO = 0.4;     // Anteil für externe Strukturen

    // ===== KONSTRUKTOR =====

    public SnowflakeTopologyStrategy() {
        super();
        validateParameters();
    }

    /**
     * Validiert alle Schneeflocken-Parameter beim Start.
     */
    private void validateParameters() {
        SnowflakeTopologyValidator.validateRingParameters(
                RING_BRIDGE_STEP_ON_RING, RING_BRIDGE_OFFSET, RING_BRIDGE_LENGTH,
                MAX_RING_LAYERS, MINIMAL_RING_SIZE
        );
        SnowflakeTopologyValidator.validateStarParameters(
                EXTERN_TREE_MAX_DEPTH, BRIDGE_TO_EXTERN_LENGTH, EXTERN_STRUCTURE_RATIO
        );
    }

    // ===== ÜBERSCHREIBUNG DER BUILD-AS-SUBSTRUCTURE-METHODEN =====

    /**
     * **PLANUNGSEBENE**: Erstellt die hierarchische Schneeflocken-Struktur.
     * Verwendet andere TopologyStrategy-Klassen als Bausteine.
     */
    @Override
    protected MirrorNode buildStructure(int totalNodes, Properties props) {
        if (totalNodes < getMinimumRequiredMirrors()) {
            throw new IllegalArgumentException(
                    "Insufficient mirrors for snowflake: " + totalNodes + " < " + getMinimumRequiredMirrors()
            );
        }

        // Berechne Mirror-Verteilung
        MirrorDistribution distribution = calculateMirrorDistribution(totalNodes);

        // **SCHRITT 1**: Erstelle zentralen Ring
        MirrorNode centralRingHead = buildCentralRing(distribution.ringMirrors(), props);
        if (centralRingHead == null) {
            return null;
        }

        // **SCHRITT 2**: Erstelle konzentrische Ring-Schichten
        buildConcentricRingLayers(centralRingHead, distribution.ringMirrors(), props);

        // **SCHRITT 3**: Erstelle Ring-zu-Ring-Brücken
        connectRingsWithBridges(props);

        // **SCHRITT 4**: Erstelle externe Baum-Strukturen
        attachExternalTreeStructures(distribution.externalMirrors(), props);

        return centralRingHead;
    }

    /**
     * **PLANUNGSEBENE**: Fügt neue Knoten zur Schneeflocken-Struktur hinzu.
     * Delegiert an die entsprechenden Substrukturen über BuildAsSubstructure.nodeToSubstructure.
     */
    @Override
    protected int addNodesToStructure(Set<Mirror> nodesToAdd) {
        if (nodesToAdd.isEmpty() || getCurrentStructureRoot() == null) {
            return 0;
        }

        int actuallyAdded = 0;

        // Finde alle Substrukturen über BuildAsSubstructure.nodeToSubstructure
        Set<BuildAsSubstructure> allSubstructures = new HashSet<>(getNodeToSubstructureMapping().values());

        // Erweitere externe Baum-Strukturen zuerst
        for (BuildAsSubstructure substructure : allSubstructures) {
            if (actuallyAdded >= nodesToAdd.size()) break;
            if (substructure instanceof DepthLimitTreeTopologyStrategy) {
                int toAdd = Math.min(nodesToAdd.size() - actuallyAdded, 3);
                actuallyAdded += substructure.addNodesToStructure(nodesToAdd.stream().limit(toAdd).collect(Collectors.toSet()));
            }
        }

        // Erweitere Ring-Strukturen
        for (BuildAsSubstructure substructure : allSubstructures) {
            if (actuallyAdded >= nodesToAdd.size()) break;
            if (substructure instanceof RingTopologyStrategy) {
                int toAdd = Math.min(nodesToAdd.size() - actuallyAdded, 2);
                actuallyAdded += substructure.addNodesToStructure(nodesToAdd.stream().limit(toAdd).collect(Collectors.toSet()));
            }
        }

        return actuallyAdded;
    }

    /**
     * **PLANUNGSEBENE**: Entfernt Knoten aus der Schneeflocken-Struktur.
     * Entfernt zuerst aus externen Bäumen, dann aus äußeren Ringen.
     */
    @Override
    protected Set<MirrorNode> removeNodesFromStructure(int nodesToRemove) {
        if (nodesToRemove <= 0) return 0;

        int actuallyRemoved = 0;

        // Finde alle Substrukturen
        Set<BuildAsSubstructure> allSubstructures = new HashSet<>(getNodeToSubstructureMapping().values());

        // Entferne aus Externen Baum-Strukturen zuerst
        for (BuildAsSubstructure substructure : allSubstructures) {
            if (actuallyRemoved >= nodesToRemove) break;
            if (substructure instanceof DepthLimitTreeTopologyStrategy) {
                int toRemove = Math.min(nodesToRemove - actuallyRemoved, 2);
                actuallyRemoved += substructure.removeNodesFromStructure(toRemove, );
            }
        }

        // Entferne aus Ring-Strukturen (nie den zentralen Ring)
        for (BuildAsSubstructure substructure : allSubstructures) {
            if (actuallyRemoved >= nodesToRemove) break;
            if (substructure instanceof RingTopologyStrategy && !isCentralRing(substructure)) {
                int toRemove = Math.min(nodesToRemove - actuallyRemoved, 1);
                actuallyRemoved += substructure.removeNodesFromStructure(toRemove, );
            }
        }

        return actuallyRemoved;
    }

    @Override
    protected boolean validateTopology() {
        // Validiere alle Substrukturen über BuildAsSubstructure.nodeToSubstructure
        for (BuildAsSubstructure substructure : getNodeToSubstructureMapping().values()) {
            if (!substructure.validateTopology()) {
                return false;
            }
        }
        return isSnowflakeStructureIntact();
    }

    /**
     * Factory-Methode: Schneeflocke verwendet RingMirrorNode als Standard.
     */
    @Override
    protected MirrorNode createMirrorNodeForMirror(Mirror mirror) {
        return new RingMirrorNode(mirror.getID(), mirror);
    }

    // ===== TOPOLOGY STRATEGY METHODEN =====

    /**
     * Berechnet die Gesamt-Link-Anzahl für die Schneeflocken-Struktur.
     * Summiert Links aus allen Substrukturen.
     */
    @Override
    public int getNumTargetLinks(Network n) {
        if (n == null) return 0;

        int totalLinks = 0;

        // Summiere Links aus allen Substrukturen
        for (BuildAsSubstructure substructure : getNodeToSubstructureMapping().values()) {
            totalLinks += substructure.getNumTargetLinks(n);
        }

        return totalLinks;
    }

    /**
     * Berechnet die erwartete Anzahl der Links, wenn die gegebene Aktion ausgeführt wird.
     * **KORREKTE ARCHITEKTUR**: Direkte mathematische Berechnung statt Selbstaufruf!
     */
    @Override
    public int getPredictedNumTargetLinks(Action a) {
        if (a == null) {
            return network != null ? getNumTargetLinks(network) : 0;
        }

        // **1. MirrorChange**: Neue Mirror-Anzahl → Schneeflocken-Links berechnen
        if (a instanceof MirrorChange mirrorChange) {
            int newMirrorCount = mirrorChange.getNewMirrors();
            return calculateSnowflakeLinksForMirrorCount(newMirrorCount);
        }

        // **2. TargetLinkChange**: Links pro Mirror ändern sich
        if (a instanceof TargetLinkChange targetLinkChange) {
            Network targetNetwork = targetLinkChange.getNetwork();
            int currentMirrors = targetNetwork.getNumMirrors();
            // Bei Schneeflocken ist die Struktur fix, TargetLinkChange hat wenig Einfluss
            return calculateSnowflakeLinksForMirrorCount(currentMirrors);
        }

        // **3. TopologyChange**: KEINE SELBSTAUFRUFE!
        if (a instanceof TopologyChange topologyChange) {
            TopologyStrategy newTopology = topologyChange.getNewTopology();
            Network targetNetwork = topologyChange.getNetwork();

            // **Direkte Delegation an die NEUE Topologie** (nicht Selbstaufruf!)
            if (newTopology != this && targetNetwork != null) {
                return newTopology.getNumTargetLinks(targetNetwork);
            }

            // Falls es dieselbe Topologie ist: Aktuelle Struktur beibehalten
            return network != null ? getNumTargetLinks(network) : 0;
        }

        // **Fallback**: Aktueller Zustand
        return network != null ? getNumTargetLinks(network) : 0;
    }

    // ===== SCHNEEFLOCKEN-AUFBAU METHODEN =====

    /**
     * Erstellt den zentralen Ring mit RingTopologyStrategy.
     */
    private MirrorNode buildCentralRing(int ringMirrors, Properties props) {
        int centralRingSize = Math.max(MINIMAL_RING_SIZE, ringMirrors / MAX_RING_LAYERS);

        RingTopologyStrategy centralRingStrategy = new RingTopologyStrategy(MINIMAL_RING_SIZE);

        // Übertrage Mirrors an die Ring-Strategie
        transferMirrorsToSubstructure(centralRingStrategy, centralRingSize);

        // Baue Ring-Struktur
        MirrorNode centralRingHead = centralRingStrategy.buildStructure(centralRingSize, props);
        if (centralRingHead != null) {
            // Registriere Substruktur über BuildAsSubstructure
            registerSubstructure(centralRingHead, centralRingStrategy);
            setCurrentStructureRoot(centralRingHead);
        }

        return centralRingHead;
    }

    /**
     * Erstellt konzentrische Ring-Schichten um den zentralen Ring.
     */
    private void buildConcentricRingLayers(MirrorNode centralRingHead, int remainingMirrors, Properties props) {
        int ringsBuilt = 1; // Zentraler Ring bereits erstellt
        int mirrorsPerRing = Math.max(MINIMAL_RING_SIZE, remainingMirrors / (MAX_RING_LAYERS - 1));

        while (ringsBuilt < MAX_RING_LAYERS && remainingMirrors >= MINIMAL_RING_SIZE) {
            int mirrorsForThisRing = Math.min(mirrorsPerRing, remainingMirrors);
            if (mirrorsForThisRing < MINIMAL_RING_SIZE) break;

            MirrorNode ringHead = buildSingleRing(mirrorsForThisRing, props);
            if (ringHead != null) {
                remainingMirrors -= mirrorsForThisRing;
                ringsBuilt++;
            }
        }
    }

    /**
     * Erstellt einen einzelnen Ring mit RingTopologyStrategy.
     */
    private MirrorNode buildSingleRing(int ringSize, Properties props) {
        RingTopologyStrategy ringStrategy = new RingTopologyStrategy(MINIMAL_RING_SIZE);

        transferMirrorsToSubstructure(ringStrategy, ringSize);

        MirrorNode ringHead = ringStrategy.buildStructure(ringSize, props);
        if (ringHead != null) {
            registerSubstructure(ringHead, ringStrategy);
        }

        return ringHead;
    }

    /**
     * Verbindet Ringe mit LineTopologyStrategy-Brücken.
     */
    private void connectRingsWithBridges(Properties props) {
        List<MirrorNode> ringHeads = findAllRingHeads();

        for (int i = 0; i < ringHeads.size() - 1; i++) {
            MirrorNode innerRingHead = ringHeads.get(i);
            MirrorNode outerRingHead = ringHeads.get(i + 1);

            createBridgeBetweenRings(innerRingHead, outerRingHead, props);
        }
    }

    /**
     * Erstellt eine Brücke zwischen zwei Ringen mit LineTopologyStrategy.
     */
    private void createBridgeBetweenRings(MirrorNode innerRingHead, MirrorNode outerRingHead, Properties props) {
        LineTopologyStrategy bridgeStrategy = new LineTopologyStrategy(RING_BRIDGE_LENGTH);

        transferMirrorsToSubstructure(bridgeStrategy, RING_BRIDGE_LENGTH);

        MirrorNode bridgeHead = bridgeStrategy.buildStructure(RING_BRIDGE_LENGTH, props);
        if (bridgeHead != null) {
            registerSubstructure(bridgeHead, bridgeStrategy);

            // Verbinde einer Bridge mit Ring-Knoten (auf StructureNode-Ebene)
            List<MirrorNode> innerBridgeNodes = findBridgeNodesInRing(innerRingHead, RING_BRIDGE_STEP_ON_RING);
            List<MirrorNode> outerBridgeNodes = findBridgeNodesInRing(outerRingHead, RING_BRIDGE_STEP_ON_RING, RING_BRIDGE_OFFSET);

            if (!innerBridgeNodes.isEmpty() && !outerBridgeNodes.isEmpty()) {
                List<MirrorNode> bridgeEndpoints = findLineEndpoints(bridgeHead);
                if (bridgeEndpoints.size() >= 2) {
                    connectNodesThroughStructure(innerBridgeNodes.get(0), bridgeEndpoints.get(0));
                    connectNodesThroughStructure(outerBridgeNodes.get(0), bridgeEndpoints.get(1));
                }
            }
        }
    }

    /**
     * Erstellt externe Baum-Strukturen mit DepthLimitTreeTopologyStrategy.
     */
    private void attachExternalTreeStructures(int mirrorsForExternal, Properties props) {
        if (mirrorsForExternal <= 0) return;

        List<MirrorNode> bridgePoints = findBridgeAttachmentPoints();
        if (bridgePoints.isEmpty()) return;

        int mirrorsPerTree = Math.max(2, mirrorsForExternal / Math.min(bridgePoints.size(), 3));
        int treesCreated = 0;

        for (MirrorNode bridgePoint : bridgePoints) {
            if (mirrorsForExternal == 0 || treesCreated >= 3) break;

            MirrorNode treeHead = buildExternalTree(Math.min(mirrorsPerTree, mirrorsForExternal), props);
            if (treeHead != null) {
                // Verbinde über eine Brücke mit dem Ring
                createExternalBridge(bridgePoint, treeHead, props);
                mirrorsForExternal -= Math.min(mirrorsPerTree, mirrorsForExternal);
                treesCreated++;
            }
        }
    }

    /**
     * Erstellt einen einzelnen externen Baum mit DepthLimitTreeTopologyStrategy.
     */
    private MirrorNode buildExternalTree(int treeSize, Properties props) {
        DepthLimitTreeTopologyStrategy treeStrategy = new DepthLimitTreeTopologyStrategy(EXTERN_TREE_MAX_DEPTH);

        transferMirrorsToSubstructure(treeStrategy, treeSize);

        MirrorNode treeHead = treeStrategy.buildStructure(treeSize, props);
        if (treeHead != null) {
            registerSubstructure(treeHead, treeStrategy);
        }

        return treeHead;
    }

    /**
     * Erstellt eine Brücke zu einem externen Baum.
     */
    private void createExternalBridge(MirrorNode ringNode, MirrorNode treeHead, Properties props) {
        LineTopologyStrategy bridgeStrategy = new LineTopologyStrategy(BRIDGE_TO_EXTERN_LENGTH);

        transferMirrorsToSubstructure(bridgeStrategy, BRIDGE_TO_EXTERN_LENGTH);

        MirrorNode bridgeHead = bridgeStrategy.buildStructure(BRIDGE_TO_EXTERN_LENGTH, props);
        if (bridgeHead != null) {
            registerSubstructure(bridgeHead, bridgeStrategy);

            // Verbinde Ring → Brücke → Baum
            List<MirrorNode> bridgeEndpoints = findLineEndpoints(bridgeHead);
            if (bridgeEndpoints.size() >= 2) {
                connectNodesThroughStructure(ringNode, bridgeEndpoints.get(0));
                connectNodesThroughStructure(bridgeEndpoints.get(1), treeHead);
            }
        }
    }

    // ===== HILFSMETHODEN =====

    /**
     * Registriert eine Substruktur über BuildAsSubstructure.nodeToSubstructure.
     */
    private void registerSubstructure(MirrorNode headNode, BuildAsSubstructure substructure) {
        setSubstructureForNode(headNode, substructure);
    }

    /**
     * Überträgt verfügbare Mirrors an eine Substruktur.
     */
    private void transferMirrorsToSubstructure(BuildAsSubstructure substructure, int mirrorCount) {
        // Vereinfachte Implementierung - in der Realität wird hier der Mirror-Iterator verwendet
        for (int i = 0; i < mirrorCount && hasNextMirror(); i++) {
            Mirror mirror = getNextMirror();
            // Substruktur würde Mirror über ihren eigenen Iterator erhalten
        }
    }

    /**
     * Verbindet zwei Knoten auf Struktur-Planungsebene.
     */
    private void connectNodesThroughStructure(MirrorNode from, MirrorNode to) {
        if (from != null && to != null) {
            from.addChild(to);
            to.setParent(from);
        }
    }

    /**
     * Findet alle Ring-Head-Nodes.
     */
    private List<MirrorNode> findAllRingHeads() {
        List<MirrorNode> ringHeads = new ArrayList<>();
        for (Map.Entry<MirrorNode, BuildAsSubstructure> entry : getNodeToSubstructureMapping().entrySet()) {
            if (entry.getValue() instanceof RingTopologyStrategy) {
                ringHeads.add(entry.getKey());
            }
        }
        return ringHeads;
    }

    /**
     * Findet Bridge-Attachment-Points in den Ringen.
     */
    private List<MirrorNode> findBridgeAttachmentPoints() {
        List<MirrorNode> attachmentPoints = new ArrayList<>();
        for (MirrorNode ringHead : findAllRingHeads()) {
            List<MirrorNode> bridgeNodes = findBridgeNodesInRing(ringHead, RING_BRIDGE_STEP_ON_RING);
            attachmentPoints.addAll(bridgeNodes);
        }
        return attachmentPoints;
    }

    /**
     * Findet Bridge-Knoten in einem Ring.
     */
    private List<MirrorNode> findBridgeNodesInRing(MirrorNode ringHead, int step) {
        return findBridgeNodesInRing(ringHead, step, 0);
    }

    /**
     * Findet Bridge-Knoten in einem Ring mit Offset.
     */
    private List<MirrorNode> findBridgeNodesInRing(MirrorNode ringHead, int step, int offset) {
        List<MirrorNode> bridgeNodes = new ArrayList<>();
        List<MirrorNode> allRingNodes = collectAllRingNodes(ringHead);

        for (int i = offset; i < allRingNodes.size(); i += step) {
            bridgeNodes.add(allRingNodes.get(i));
        }

        return bridgeNodes;
    }

    /**
     * Sammelt alle Knoten eines Rings.
     */
    private List<MirrorNode> collectAllRingNodes(MirrorNode ringHead) {
        return collectAllMirrorNodesFromStructure(ringHead);
    }

    /**
     * Sammelt alle MirrorNodes aus einer Struktur-Hierarchie (type-safe).
     * Verwendet Breadth-First-Traversierung mit visited-Set für Zyklusvermeidung.
     *
     * @param root Root-Node der zu durchsuchenden Struktur
     * @return Alle MirrorNodes in der Struktur
     */
    private List<MirrorNode> collectAllMirrorNodesFromStructure(MirrorNode root) {
        if (root == null) return List.of();

        List<MirrorNode> allNodes = new ArrayList<>();
        Set<MirrorNode> visited = new HashSet<>();
        Queue<MirrorNode> queue = new ArrayDeque<>();

        queue.offer(root);
        visited.add(root);

        while (!queue.isEmpty()) {
            MirrorNode current = queue.poll();
            allNodes.add(current);

            // Type-safe Traversierung
            for (MirrorNode child : getMirrorNodeChildren(current)) {
                if (!visited.contains(child)) {
                    visited.add(child);
                    queue.offer(child);
                }
            }
        }

        return allNodes;
    }

    /**
     * Konvertiert StructureNode-Kinder zu MirrorNode-Liste (type-safe).
     * Filtert automatisch Nicht-MirrorNode-Instanzen heraus.
     *
     * @param structureNode Der StructureNode, dessen Kinder konvertiert werden sollen
     * @return Liste aller MirrorNode-Kinder
     */
    private List<MirrorNode> getMirrorNodeChildren(StructureNode structureNode) {
        if (structureNode == null) return List.of();

        return structureNode.getChildren().stream()
                .filter(child -> child instanceof MirrorNode)
                .map(child -> (MirrorNode) child)
                .toList();
    }

    /**
     * Findet Endpunkte einer Linien-Struktur.
     */
    private List<MirrorNode> findLineEndpoints(MirrorNode lineHead) {
        List<MirrorNode> endpoints = new ArrayList<>();

        for (MirrorNode node : collectAllMirrorNodesFromStructure(lineHead)) {
            int connectionCount = getMirrorNodeChildren(node).size() + (node.getParent() != null ? 1 : 0);
            if (connectionCount <= 1) {
                endpoints.add(node);
            }
        }

        return endpoints;
    }

    /**
     * Prüft, ob eine Substruktur der zentrale Ring ist.
     */
    private boolean isCentralRing(BuildAsSubstructure substructure) {
        // Der zentrale Ring ist die Root-Substruktur
        MirrorNode root = getCurrentStructureRoot();
        if (root != null) {
            BuildAsSubstructure rootSubstructure = findSubstructureForNode(root);
            return rootSubstructure == substructure;
        }
        return false;
    }

    /**
     * **KERNMETHODE**: Direkte mathematische Berechnung der Schneeflocken-Links.
     * Keine Rekursion, keine Selbstaufrufe - nur pure Mathematik!
     *
     * @param totalMirrors Gesamtanzahl der Mirrors
     * @return Erwartete Anzahl Links für Schneeflocken-Struktur
     */
    private int calculateSnowflakeLinksForMirrorCount(int totalMirrors) {
        if (totalMirrors < getMinimumRequiredMirrors()) {
            return 0;
        }

        // Berechne Mirror-Verteilung
        MirrorDistribution distribution = calculateMirrorDistribution(totalMirrors);
        int ringMirrors = distribution.ringMirrors();
        int externalMirrors = distribution.externalMirrors();

        // **SCHNEEFLOCKEN-LINK-FORMEL**:
        // = Ring-Links + Bridge-Links + Externe-Baum-Links

        // 1. Ring-Links: Für n Ring-Mirrors in MAX_RING_LAYERS Ringen
        int ringLinks = calculateMultipleRingLinks(ringMirrors);

        // 2. Bridge-Links: Ring-zu-Ring-Brücken + Ring-zu-Extern-Brücken
        int bridgeLinks = calculateBridgeLinks(ringMirrors, externalMirrors);

        // 3. Externe Baum-Links: Baum-Eigenschaft = n-1 Links für n Knoten
        int externalTreeLinks = Math.max(0, externalMirrors - countExternalTrees(externalMirrors));

        return ringLinks + bridgeLinks + externalTreeLinks;
    }

    /**
     * Berechnet Links für mehrere konzentrische Ringe.
     */
    private int calculateMultipleRingLinks(int totalRingMirrors) {
        int totalRingLinks = 0;
        int mirrorsDistributed = 0;

        for (int layer = 0; layer < MAX_RING_LAYERS && mirrorsDistributed < totalRingMirrors; layer++) {
            int mirrorsInThisRing = Math.min(
                    Math.max(MINIMAL_RING_SIZE, totalRingMirrors / MAX_RING_LAYERS),
                    totalRingMirrors - mirrorsDistributed
            );

            if (mirrorsInThisRing >= MINIMAL_RING_SIZE) {
                // Ring-Formel: n Links für n Knoten (geschlossene Schleife)
                totalRingLinks += mirrorsInThisRing;
                mirrorsDistributed += mirrorsInThisRing;
            }
        }

        return totalRingLinks;
    }

    /**
     * Berechnet Links für alle Brücken-Strukturen.
     */
    private int calculateBridgeLinks(int ringMirrors, int externalMirrors) {
        // Ring-zu-Ring-Brücken: (Anzahl Ring-Schichten - 1) * RING_BRIDGE_LENGTH
        int ringToRingBridges = Math.max(0, (MAX_RING_LAYERS - 1)) * RING_BRIDGE_LENGTH;

        // Ring-zu-Extern-Brücken: Pro externem Baum eine Brücke
        int externalTrees = countExternalTrees(externalMirrors);
        int ringToExternBridges = externalTrees * BRIDGE_TO_EXTERN_LENGTH;

        return ringToRingBridges + ringToExternBridges;
    }

    /**
     * Zählt die Anzahl der externen Bäume basierend auf verfügbaren Mirrors.
     */
    private int countExternalTrees(int externalMirrors) {
        if (externalMirrors <= 0) return 0;

        // Maximal 3 externe Bäume, mindestens 2 Mirrors pro Baum
        int maxTrees = 3;
        int minMirrorsPerTree = 2;

        return Math.min(maxTrees, externalMirrors / minMirrorsPerTree);
    }

    /**
     * Berechnet die Mirror-Verteilung zwischen Ringen und externen Strukturen.
     */
    private MirrorDistribution calculateMirrorDistribution(int totalMirrors) {
        int externalMirrors = (int) (totalMirrors * EXTERN_STRUCTURE_RATIO);
        int ringMirrors = totalMirrors - externalMirrors;

        if (ringMirrors < MINIMAL_RING_SIZE) {
            ringMirrors = MINIMAL_RING_SIZE;
            externalMirrors = Math.max(0, totalMirrors - ringMirrors);
        }

        return new MirrorDistribution(ringMirrors, externalMirrors);
    }

    /**
     * Gibt die minimale Anzahl Mirrors für eine funktionsfähige Schneeflocke zurück.
     */
    private int getMinimumRequiredMirrors() {
        return MINIMAL_RING_SIZE + 2;
    }

    /**
     * Prüft, ob die Schneeflocken-Struktur intakt ist.
     */
    private boolean isSnowflakeStructureIntact() {
        // Mindestens ein zentraler Ring muss vorhanden sein
        return getCurrentStructureRoot() != null && !getNodeToSubstructureMapping().isEmpty();
    }

    @Override
    public String toString() {
        return "SnowflakeTopologyStrategy{" +
                "totalSubstructures=" + getNodeToSubstructureMapping().size() +
                ", totalNodes=" + getAllStructureNodes().size() +
                '}';
    }
}