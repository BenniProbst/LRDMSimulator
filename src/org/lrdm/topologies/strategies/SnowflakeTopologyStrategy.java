package org.lrdm.topologies.strategies;

import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.SubstructureFactory;
import org.lrdm.effectors.Action;
import org.lrdm.effectors.MirrorChange;
import org.lrdm.effectors.TargetLinkChange;
import org.lrdm.effectors.TopologyChange;
import org.lrdm.topologies.node.MirrorNode;
import org.lrdm.topologies.node.StructureNode;
import org.lrdm.topologies.validators.SnowflakeTopologyValidator;
import org.lrdm.util.IDGenerator;

import java.util.*;
import java.util.function.Supplier;

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
            int ringBridgeGap
    ) {
    }

    public record MirrorDistributionResult(
            int ringMirrors,
            List<Integer> externalStructureMirrors
    ) {
    }

    SnowflakeProperties snowflakeProperties;
    NConnectedTopology internNConnectedTopologie = new NConnectedTopology();
    SubstructureFactory substructureFactory;
    List<StructureNode.StructureType> substructureRotation;

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

    public SnowflakeTopologyStrategy() {
        this(new SnowflakeProperties(
                0.5,
                2
        ));
    }

    public SnowflakeTopologyStrategy(SnowflakeProperties snowflakeProperties) {
        super();
        Map<StructureNode.StructureType, Supplier<BuildAsSubstructure>> suppliers = Map.of(
                StructureNode.StructureType.STAR, StarTopologyStrategy::new,
                StructureNode.StructureType.FULLY_CONNECTED, FullyConnectedTopology::new,
                StructureNode.StructureType.N_CONNECTED, NConnectedTopology::new,
                StructureNode.StructureType.DEPTH_LIMIT_TREE, () -> new DepthLimitTreeTopologyStrategy(2),
                StructureNode.StructureType.BALANCED_TREE, BalancedTreeTopologyStrategy::new,
                StructureNode.StructureType.LINE, LineTopologyStrategy::new,
                StructureNode.StructureType.TREE, TreeTopologyStrategy::new
        );

        SubstructureFactory subFac = SubstructureFactory.fromSuppliers(suppliers);

        // Reihenfolge der zyklischen Rotation (frei wählbar)
        List<StructureNode.StructureType> rotation1 = List.of(
                StructureNode.StructureType.STAR
        );

        this.snowflakeProperties = snowflakeProperties;
        this.substructureFactory = subFac;
        this.substructureRotation = rotation1;
    }

    public SnowflakeTopologyStrategy(SnowflakeProperties snowflakeProperties, SubstructureFactory substructureFactory,
                                     List<StructureNode.StructureType> rotation) {
        super();
        this.snowflakeProperties = snowflakeProperties;
        this.substructureFactory = substructureFactory;
        this.substructureRotation = rotation;
    }

    private MirrorDistributionResult calculateSnowflakeDistribution(int totalMirrors, SnowflakeProperties snowflakeProperties) {
        int ringMirrors = (int) (totalMirrors * (1 - snowflakeProperties.externalStructureRatio));
        int externalMirrors = (int) (totalMirrors * snowflakeProperties.externalStructureRatio);
        int restMirrors = totalMirrors - ringMirrors;
        int externalEachTreeNumMirrors = (externalMirrors * snowflakeProperties.ringBridgeGap) / ringMirrors;

        boolean externalMirrorCalculationDone = false;
        List<Integer> externalStructureMirrorsDone = List.of();
        int count;

        while (!externalMirrorCalculationDone) {
            count = 0;
            List<Integer> externalStructureMirrors = new ArrayList<>(Collections.nCopies(ringMirrors, 0));
            for (int i = 0; i < ringMirrors; i++) {
                if (count % snowflakeProperties.ringBridgeGap == 0) {
                    externalStructureMirrors.set(i, externalEachTreeNumMirrors);
                    restMirrors -= externalEachTreeNumMirrors;
                }
                count++;
            }

            if (restMirrors < 0) {
                externalEachTreeNumMirrors--;
                restMirrors = totalMirrors - ringMirrors;
            } else {
                externalMirrorCalculationDone = true;
                externalStructureMirrorsDone = externalStructureMirrors;
            }
        }

        count = 0;
        assert ringMirrors > 0 && externalMirrors > 0;
        while (restMirrors > 0) {
            // Fill external structures on the ring
            if (count % snowflakeProperties.ringBridgeGap == 0) {
                externalStructureMirrorsDone.set(count, externalStructureMirrorsDone.get(count) + 1);
                restMirrors--;
            }

            if (count == ringMirrors - 1) {
                count = 0;
            } else {
                count++;
            }
        }

        return new MirrorDistributionResult(ringMirrors, externalStructureMirrorsDone);
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
     * Initialisiert den internen Zustand für ein Netzwerk.
     */
    public void initializeInternalState(Network n) {
        this.network = n;
        internNConnectedTopologie.initializeInternalState(n);
    }

    /**
     * **PLANUNGSEBENE**: Erstellt die hierarchische Schneeflocken-Struktur.
     * Verwendet andere TopologyStrategy-Klassen als Bausteine.
     */
    @Override
    protected MirrorNode buildStructure(int totalNodes) {
        validateParameters();
        if (totalNodes < getMinimumRequiredMirrors()) {
            throw new IllegalArgumentException(
                    "Insufficient mirrors for snowflake: " + totalNodes + " < " + getMinimumRequiredMirrors()
            );
        }

        // Berechne Mirror-Verteilung
        MirrorDistributionResult snowflakeResult = calculateSnowflakeDistribution(totalNodes, snowflakeProperties);

        // **SCHRITT 1**: Erstelle zentralen Ring und füge ihn in die Snowflake hinzu
        MirrorNode nConNodeRoot = internNConnectedTopologie.buildStructure(snowflakeResult.ringMirrors);
        // Ring in Snowflake eingliedern
        connectToStructureNodes(
                nConNodeRoot,
                internNConnectedTopologie);
        // **SCHRITT 2**: Erstelle im Wechsel gehostete Strukturen an einer host node
        List<MirrorNode> allNConNodes = internNConnectedTopologie.getAllStructureNodes().stream()
                .sorted(Comparator.comparingInt(MirrorNode::getId))
                .toList();

        int count = 0;
        for (MirrorNode nConNode : allNConNodes) {
            if (count % snowflakeProperties.ringBridgeGap == 0) {
                // build and interlink substructure with estimated mirrors
                int externStructureTypeIndex = count / snowflakeProperties.ringBridgeGap;
                BuildAsSubstructure localBuild = substructureFactory.createCycledAndInit(externStructureTypeIndex, substructureRotation, network);
                localBuild.buildStructure(snowflakeResult.externalStructureMirrors.get(count));
                // Füge externe Struktur auch in die Snowflake für alle Strukturen und MirrorNode hierarchisch hinzu
                connectToStructureNodes(nConNode, localBuild);
            }
            count++;
        }

        return nConNodeRoot;

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
        // Alle Strukturen finden und ausgliedern, dann Ring neu bauen und wieder eingliedern
        // mit gleichzeitigem Anpassen des Hinzufügens und Löschens von nodes, zusätzliche port Strukturen hinten anfügen
        if (nodesToAdd.isEmpty() || getCurrentStructureRoot() == null) {
            return 0;
        }
        int actuallyAdded = 0;

        validateParameters();
        // **SCHRITT 1**: Ermittle derzeitige Anzahl der Knoten und Ports

        // Berechne Mirror-Verteilung
        int oldTotalNodes = getAllStructureNodes().size();
        int totalNodes = oldTotalNodes + nodesToAdd.size();
        MirrorDistributionResult oldSnowflakeResult = calculateSnowflakeDistribution(oldTotalNodes, snowflakeProperties);
        MirrorDistributionResult snowflakeResult = calculateSnowflakeDistribution(totalNodes, snowflakeProperties);

        if (totalNodes < getMinimumRequiredMirrors()) {
            throw new IllegalArgumentException(
                    "Insufficient mirrors for snowflake: " + totalNodes + " < " + getMinimumRequiredMirrors()
            );
        }

        // **SCHRITT 2**: Port Strukturen aus Ring herauslösen und speichern

        // Der Ring ist nach ID sortiert, daher ist auch die Reihenfolge der Substrukturen nach ID sortierbar
        List<SubstructureTuple> disconnectedSubstructures = disconnectAllStructuresFromRing();

        // **SCHRITT 3**: Ring unter neuen Mirrors erstellen, dabei jede Struktur einzeln updaten und wieder eingliedern

        // Ring selbst ausgliedern
        internNConnectedTopologie.setCurrentStructureRoot(
                disconnectFromStructureNodes(getCurrentStructureRoot(), internNConnectedTopologie)
        );

        // Ring update und structureNodes updaten
        int ringDiff = snowflakeResult.ringMirrors - oldSnowflakeResult.ringMirrors;
        if (ringDiff > 0) {
            Set<Mirror> newNodes = nodeSubset(nodesToAdd, ringDiff);
            internNConnectedTopologie.addNodesToStructure(newNodes);
            actuallyAdded += newNodes.size();
        }
        if (ringDiff < 0) {
            internNConnectedTopologie.removeNodesFromStructure(Math.abs(ringDiff));
        }

        // Ring nach Update wieder eingliedern
        connectToStructureNodes(
                internNConnectedTopologie.getCurrentStructureRoot(),
                internNConnectedTopologie);

        // ermittle alle StructureNodes im Ring
        // only get existing nodes from ring and analyze connected external structures by disconnecting them
        List<MirrorNode> existingRingNodes = internNConnectedTopologie.getAllStructureNodes().stream()
                .sorted(Comparator.comparing(MirrorNode::getId))
                .toList();

        // External Structure update
        for (int i = 0; i < snowflakeResult.ringMirrors; i++) {
            // **SCHRITT 4**: Füge neue nodes zu Port strukturen hinzu und/oder erstelle neue Port strukturen
            if (i > oldSnowflakeResult.ringMirrors) {
                // Index übersteigt alte Ring-Struktur: neue externe Strukturen erstellen und eingliedern
                if (i % snowflakeProperties.ringBridgeGap == 0) {
                    MirrorNode current = existingRingNodes.get(i);

                    int externStructureTypeIndex = i / snowflakeProperties.ringBridgeGap;
                    BuildAsSubstructure localBuild = substructureFactory.createCycledAndInit(externStructureTypeIndex, substructureRotation, network);
                    localBuild.initializeInternalState(network);

                    localBuild.buildStructure(snowflakeResult.externalStructureMirrors.get(i));
                    // Externe Struktur eingliedern
                    connectToStructureNodes(
                            current,
                            localBuild);
                    actuallyAdded += snowflakeResult.externalStructureMirrors.get(i);
                }
            } else {
                // Wenn Muster aktiv wird, nehme erste nicht verbundene Struktur aus der Liste, update und eingliedern
                if (i % snowflakeProperties.ringBridgeGap == 0) {
                    MirrorNode current = existingRingNodes.get(i);
                    SubstructureTuple strucTup = disconnectedSubstructures.stream().findFirst().orElse(null);
                    assert strucTup != null;
                    disconnectedSubstructures.remove(strucTup);

                    int externNodesDiff = snowflakeResult.externalStructureMirrors.get(i) - oldSnowflakeResult.externalStructureMirrors.get(i);
                    if (externNodesDiff > 0) {
                        Set<Mirror> newNodes = nodeSubset(nodesToAdd, externNodesDiff);
                        strucTup.substructure().addNodesToStructure(newNodes);
                        actuallyAdded += newNodes.size();
                    }
                    if (externNodesDiff < 0) {
                        strucTup.substructure().removeNodesFromStructure(Math.abs(externNodesDiff));
                    }

                    // Externe Struktur wieder eingliedern
                    strucTup.substructure().connectToStructureNodes(
                            current,
                            strucTup.substructure());
                    disconnectedSubstructures.remove(strucTup);
                }
            }
        }

        return actuallyAdded;
    }

    private List<SubstructureTuple> disconnectAllStructuresFromRing() {
        List<SubstructureTuple> connectedStructures =
                new ArrayList<>(getAllSubstructureTuples().stream()
                        .sorted(Comparator.comparingInt(SubstructureTuple::getNodeId)).toList());

        List<SubstructureTuple> disconnectedSubstructures = new ArrayList<>();

        for (SubstructureTuple substructureTuple : connectedStructures) {
            // Disconnect entfernt auch alle StructureNodes von unserer Struktur
            MirrorNode disconnectedRoot = disconnectFromStructureNodes(substructureTuple.node(), substructureTuple.substructure());
            // ring is managed separately from external structures, so we can add them to the snowflake
            if (disconnectedRoot == null) {
                disconnectedSubstructures.add(substructureTuple);
            }
            if (substructureTuple.substructure() == internNConnectedTopologie) {
                continue;
            }
            disconnectedSubstructures.add(new SubstructureTuple(disconnectedRoot, substructureTuple.substructure()));
        }
        connectedStructures.clear();

        return disconnectedSubstructures;
    }

    private Set<Mirror> nodeSubset(Set<Mirror> inputSubset, int take) {
        Set<Mirror> newNodes = new HashSet<>();
        for (int i = 0; i < take; i++) {
            Mirror m = inputSubset.stream().findFirst().orElse(null);
            newNodes.add(m);
            inputSubset.remove(m);
        }
        return newNodes;
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

        validateParameters();
        // **SCHRITT 1**: Ermittle derzeitige Anzahl der Knoten und Ports

        // Berechne Mirror-Verteilung
        int oldTotalNodes = getAllStructureNodes().size();
        int totalNodes = oldTotalNodes - nodesToRemove;
        MirrorDistributionResult oldSnowflakeResult = calculateSnowflakeDistribution(oldTotalNodes, snowflakeProperties);
        MirrorDistributionResult snowflakeResult = calculateSnowflakeDistribution(totalNodes, snowflakeProperties);

        if (totalNodes < getMinimumRequiredMirrors()) {
            throw new IllegalArgumentException(
                    "Insufficient mirrors for snowflake: " + totalNodes + " < " + getMinimumRequiredMirrors()
            );
        }

        // **SCHRITT 2**: Vergleiche derzeitige Struktur und dokumentiere die Menge an ports.
        // Der Ring ist nach ID sortiert, daher ist auch die Reihenfolge der Substrukturen nach ID sortierbar
        List<SubstructureTuple> disconnectedSubstructures = disconnectAllStructuresFromRing();

        // **SCHRITT 3**: Schrumpfe den Ring und entferne Port Strukturen bei Bedarf, updates an bestehende Strukturen

        // Ring selbst ausgliedern
        if(this.getNodeToSubstructureMapping().containsValue(internNConnectedTopologie)){
            disconnectFromStructureNodes(internNConnectedTopologie.getCurrentStructureRoot(), internNConnectedTopologie);
        }

        // Ring update und structureNodes updaten
        int ringDiff = snowflakeResult.ringMirrors - oldSnowflakeResult.ringMirrors;
        if (ringDiff < 0) {
            actuallyRemoved.addAll(internNConnectedTopologie.removeNodesFromStructure(Math.abs(ringDiff)));
        }

        // Ring nach Update wieder eingliedern
        connectToStructureNodes(
                internNConnectedTopologie.getCurrentStructureRoot(),
                internNConnectedTopologie);

        // **SCHRITT 4**: Externe Strukturen am Ring neu ausrichten
        // ermittle alle StructureNodes im Ring
        // only get existing nodes from ring and analyze connected external structures by disconnecting them
        List<MirrorNode> existingRingNodes = internNConnectedTopologie.getAllStructureNodes().stream()
                .sorted(Comparator.comparing(MirrorNode::getId))
                .toList();

        // External Structure update
        for (int i = 0; i < snowflakeResult.ringMirrors; i++) {
            // **SCHRITT 4**: Entferne geplante node Strukutren wenn das Muster aktiv wird
            if (i % snowflakeProperties.ringBridgeGap == 0) {
                MirrorNode current = existingRingNodes.get(i);
                SubstructureTuple strucTup = disconnectedSubstructures.stream().findFirst().orElse(null);
                assert strucTup != null;
                disconnectedSubstructures.remove(strucTup);

                int externNodesDiff = snowflakeResult.externalStructureMirrors.get(i) - oldSnowflakeResult.externalStructureMirrors.get(i);
                if (externNodesDiff < 0) {
                    actuallyRemoved.addAll(strucTup.substructure().removeNodesFromStructure(Math.abs(externNodesDiff)));
                }

                // Externe Struktur wieder eingliedern
                connectToStructureNodes(
                        current,
                        strucTup.substructure());
                disconnectedSubstructures.remove(strucTup);
            }
        }

        // Lösche alle übrigen überflüssigen externen Strukturen, da sie nicht mehr benötigt oder geupdated werden
        for (SubstructureTuple subTup : disconnectedSubstructures) {
            actuallyRemoved.addAll(subTup.substructure().getAllStructureNodes());
        }

        return actuallyRemoved;
    }

    @Override
    protected boolean validateTopology() {
        // Validiere alle Substrukturen über BuildAsSubstructure.nodeToSubstructure
        // 1) Snapshot + Duplikate eliminieren
        Set<BuildAsSubstructure> uniqueSubs = new HashSet<>(getNodeToSubstructureMapping().values());

        // 2) Gegen Selbstreferenzen/zyklen absichern
        Set<BuildAsSubstructure> visited = new HashSet<>();
        for (BuildAsSubstructure sub : uniqueSubs) {
            if (sub == this) continue;           // Selbstreferenz ignorieren, falls vorhanden
            if (!visited.add(sub)) continue;     // Duplikat/zyklische Referenz überspringen

            if (!sub.validateTopology()) {
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
        // take all substructures and the ring into account
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
        // Walk down the ring and create new Actions like on adding or removing Mirrors depending on the
        // difference on change that would occur; We assume that the same network was used for all the substructures, so it will synchronize adjustments

        // only get extern structures
        List<SubstructureTuple> connectedStructures =
                new ArrayList<>(internNConnectedTopologie.getAllSubstructureTuples().stream()
                        .sorted(Comparator.comparingInt(SubstructureTuple::getNodeId)).toList());

        List<MirrorNode> allRingNodes = internNConnectedTopologie.getAllStructureNodes().stream()
                .sorted(Comparator.comparingInt(StructureNode::getId)).toList();

        int outlinks = 0;

        if (a instanceof MirrorChange || a instanceof TargetLinkChange) {

            MirrorDistributionResult newSnowflakeEstimateResult;

            if (a instanceof MirrorChange mirrorChange) {
                newSnowflakeEstimateResult = calculateSnowflakeDistribution(mirrorChange.getNewMirrors(), snowflakeProperties);
            } else {
                newSnowflakeEstimateResult = calculateSnowflakeDistribution(network.getNumTargetMirrors(), snowflakeProperties);
            }

            for (int i = 0; i < allRingNodes.size(); i++) {
                // **SCHRITT 4**: Entferne geplante node Strukturen, wenn das Muster aktiv wird
                if (i % snowflakeProperties.ringBridgeGap == 0) {
                    // Filter the correct substructure, adjust network properties and finally calculate target links of structure
                    MirrorNode current = allRingNodes.get(i);
                    BuildAsSubstructure subStructure = Objects.requireNonNull(connectedStructures.stream()
                            .filter(tuple -> tuple.node() == current).findFirst().orElse(null)).substructure();

                    // **1. MirrorChange**: Neue Mirror-Anzahl → Schneeflocken-Links berechnen
                    if (a instanceof MirrorChange mirrorChange) {
                        // create a derived action that matches the changes for a substructure
                        MirrorChange subMirrorChange = new MirrorChange(
                                network,
                                IDGenerator.getInstance().getNextID(),
                                mirrorChange.getTime(),
                                newSnowflakeEstimateResult.externalStructureMirrors().get(i)
                        );
                        outlinks += subStructure.getPredictedNumTargetLinks(subMirrorChange);
                    }

                    // **2. TargetLinkChange**: Links pro Mirror ändern sich
                    if (a instanceof TargetLinkChange targetLinkChange) {
                        // create a derived action that matches the changes for a substructure
                        // links are not devided by structure and substructure hierarchy
                        outlinks += subStructure.getPredictedNumTargetLinks(targetLinkChange);
                    }
                }
            }

            // Bewerte die Anzahl der Links im Ring selbst
            // **1. MirrorChange**: Neue Mirror-Anzahl → Schneeflocken-Links berechnen
            if (a instanceof MirrorChange mirrorChange) {
                // create a derived action that matches the changes for a substructure
                MirrorChange subMirrorChange = new MirrorChange(
                        network,
                        IDGenerator.getInstance().getNextID(),
                        mirrorChange.getTime(),
                        newSnowflakeEstimateResult.ringMirrors()
                );
                outlinks += internNConnectedTopologie.getPredictedNumTargetLinks(subMirrorChange);
            }

            // **2. TargetLinkChange**: Links pro Mirror ändern sich
            if (a instanceof TargetLinkChange targetLinkChange) {
                // create a derived action that matches the changes for a substructure
                // links are not devided by structure and substructure hierarchy
                outlinks += internNConnectedTopologie.getPredictedNumTargetLinks(targetLinkChange);
            }

            return outlinks;
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

    /**
     * Zusammengesetzte Topologien müssen ihren Strukturtypen statisch definieren.
     */
    public StructureNode.StructureType getCurrentStructureType() {
        return StructureNode.StructureType.SNOWFLAKE;
    }

    @Override
    public String toString() {
        return "SnowflakeTopologyStrategy{" +
                "totalSubstructures=" + getNodeToSubstructureMapping().size() +
                ", totalNodes=" + getAllStructureNodes().size() +
                '}';
    }
}