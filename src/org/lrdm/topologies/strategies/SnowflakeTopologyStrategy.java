package org.lrdm.topologies.strategies;

import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.Action;
import org.lrdm.effectors.MirrorChange;
import org.lrdm.effectors.TargetLinkChange;
import org.lrdm.effectors.TopologyChange;
import org.lrdm.topologies.node.MirrorNode;
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
    public record SnowflakeProperties(
            double externalStructureRatio,
            int ringNConnectedDegree,
            int ringBridgeGap,
            int externalTreeDepthMax
    ) {
    }

    public record MirrorDistributionResult(
            int ringMirrors,
            List<Integer> externalTreeMirrors,
            int restMirrors
    ){
    }

    SnowflakeProperties snowflakeProperties;
    NConnectedTopology internNConnectedTopologie;
    List<BuildAsSubstructure> externTreeSubstructures;

    // ===== SCHNEEFLOCKEN-PARAMETER =====
    //PLANNED: private static final int RING_BRIDGE_GAP_ON_RING = 3; // Modulo für Bridge-Positionen
    //PLANNED: private static final int RING_BRIDGE_OFFSET = 1; // Offset für Bridge-Startposition
    //PLANNED: private static final int RING_BRIDGE_LENGTH = 2; // Länge der Ring-zu-Ring-Brücken
    //PLANNED: private static final int MAX_RING_LAYERS = 3; // Maximale Anzahl Ring-Schichten/Ring im Ring
    private static final int MINIMAL_RING_SIZE = 3; // STATIC: Minimale Anzahl Mirrors pro Ring
    //USED in MirrorDistribution: private static final int EXTERN_TREE_MAX_DEPTH = 2; // Maximale Tiefe externer Bäume
    //PLANNED: private static final int BRIDGE_TO_EXTERN_LENGTH = 1; // Länge der Brücken zu externen Strukturen
    //USED in MirrorDistribution: private static final double EXTERN_STRUCTURE_RATIO = 0.4; // Anteil für externe Strukturen

    // ===== KONSTRUKTOR =====

    public SnowflakeTopologyStrategy(SnowflakeProperties snowflakeProperties) {
        super();
        this.snowflakeProperties = snowflakeProperties;
    }

    private MirrorDistributionResult calculateSnowflakeDistribution(int totalMirrors, SnowflakeProperties snowflakeProperties) {
        int ringMirrors = (int) (totalMirrors*(1-snowflakeProperties.externalStructureRatio));
        int externalMirrors = (int) (totalMirrors*snowflakeProperties.externalStructureRatio);
        int restMirrors = totalMirrors - ringMirrors;
        int externalEachTreeNumMirrors = externalMirrors / snowflakeProperties.externalTreeDepthMax;

        int count = 0;
        List<Integer> externalTreeMirrors = new ArrayList<>(Collections.nCopies(ringMirrors, 0));
        for(int i=0; i<ringMirrors; i++){
            if(count%snowflakeProperties.ringBridgeGap==0){
                externalTreeMirrors.set(i,externalEachTreeNumMirrors);
                restMirrors -= externalEachTreeNumMirrors;
            }
            count++;
        }
        return new MirrorDistributionResult(ringMirrors, externalTreeMirrors, restMirrors);
    }

    /**
     * Validiert alle Schneeflocken-Parameter beim Start.
     */
    private void validateParameters() {
        SnowflakeTopologyValidator.validateRingParameters(
                /*RING_BRIDGE_GAP_ON_RING, RING_BRIDGE_OFFSET, RING_BRIDGE_LENGTH,
                MAX_RING_LAYERS,*/ MINIMAL_RING_SIZE
        );
        /*
        SnowflakeTopologyValidator.validateStarParameters(
                EXTERN_TREE_MAX_DEPTH, BRIDGE_TO_EXTERN_LENGTH, EXTERN_STRUCTURE_RATIO
        );
        */
    }

    // ===== ÜBERSCHREIBUNG DER BUILD-AS-SUBSTRUCTURE-METHODEN =====

    /**
     * **PLANUNGSEBENE**: Erstellt die hierarchische Schneeflocken-Struktur.
     * Verwendet andere TopologyStrategy-Klassen als Bausteine.
     */
    @Override
    protected MirrorNode buildStructure(int totalNodes, Properties props) {
        validateParameters();
        if (totalNodes < getMinimumRequiredMirrors()) {
            throw new IllegalArgumentException(
                    "Insufficient mirrors for snowflake: " + totalNodes + " < " + getMinimumRequiredMirrors()
            );
        }

        // Berechne Mirror-Verteilung
        MirrorDistributionResult snowflakeResult = calculateSnowflakeDistribution(totalNodes,snowflakeProperties);

        List<MirrorNode> nodes = new ArrayList<>();

        // **SCHRITT 1**: Erstelle zentralen Ring
        NConnectedTopology nConnectedTopology;

        // **SCHRITT 2**: Erstelle Tiefen beschränkte Baum-Strukturen in definierten Abständen am Ring
        List<DepthLimitTreeTopologyStrategy> depthLimitTreeTopologyStrategies = new ArrayList<>();

        return null;

        /*
        PLANNED
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
         */
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
            if (substructure instanceof NConnectedTopology) {
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
        if (nodesToRemove <= 0 || getCurrentStructureRoot() == null) {
            return new HashSet<>();
        }

        Set<MirrorNode> actuallyRemoved = new HashSet<>();

        // Finde alle Substrukturen
        Set<BuildAsSubstructure> allSubstructures = new HashSet<>(getNodeToSubstructureMapping().values());

        // Entferne aus Externen Baum-Strukturen zuerst
        for (BuildAsSubstructure substructure : allSubstructures) {
            if (actuallyRemoved.size() >= nodesToRemove) break;
            if (substructure instanceof DepthLimitTreeTopologyStrategy) {
                int toRemove = Math.min(nodesToRemove - actuallyRemoved.size(), 2);
                actuallyRemoved.addAll(substructure.removeNodesFromStructure(toRemove));
            }
        }

        // Entferne aus Ring-Strukturen (nie den zentralen Ring)
        for (BuildAsSubstructure substructure : allSubstructures) {
            if (actuallyRemoved.size() >= nodesToRemove) break;
            if (substructure instanceof NConnectedTopology) {
                int toRemove = Math.min(nodesToRemove - actuallyRemoved.size(), 1);
                actuallyRemoved.addAll(substructure.removeNodesFromStructure(toRemove));
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
        return true;
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
            MirrorDistributionResult snowflakeResult = calculateSnowflakeDistribution(newMirrorCount,snowflakeProperties);
            return calculateSnowflakeLinksForMirrorCount(newMirrorCount);
        }

        // **2. TargetLinkChange**: Links pro Mirror ändern sich
        if (a instanceof TargetLinkChange targetLinkChange) {
            Network targetNetwork = targetLinkChange.getNetwork();
            int currentMirrors = targetNetwork.getNumMirrors();
            SnowflakeProperties snowflakeProperties = new SnowflakeProperties(
                    this.snowflakeProperties.externalStructureRatio,
                    targetLinkChange.getNewLinksPerMirror(),
                    this.snowflakeProperties.ringBridgeGap,
                    this.snowflakeProperties.externalTreeDepthMax
            );
            MirrorDistributionResult snowflakeResult =
                    calculateSnowflakeDistribution(targetNetwork.getNumTargetMirrors(),snowflakeProperties);
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

    /**
     * Gibt die minimale Anzahl Mirrors für eine funktionsfähige Schneeflocke zurück.
     */
    private int getMinimumRequiredMirrors() {
        return MINIMAL_RING_SIZE + 2;
    }

    @Override
    public String toString() {
        return "SnowflakeTopologyStrategy{" +
                "totalSubstructures=" + getNodeToSubstructureMapping().size() +
                ", totalNodes=" + getAllStructureNodes().size() +
                '}';
    }
}