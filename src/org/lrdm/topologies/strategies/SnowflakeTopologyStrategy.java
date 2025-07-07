package org.lrdm.topologies.strategies;

import org.graphstream.ui.swing.util.AttributeUtils;
import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.Action;
import org.lrdm.effectors.MirrorChange;
import org.lrdm.topologies.node.StructureNode;
import org.lrdm.util.IDGenerator;
import org.lrdm.topologies.builders.TreeBuilder;

import org.lrdm.topologies.exceptions.*;
import org.lrdm.topologies.validators.SnowflakeTopologyValidator;

import java.util.*;

public class SnowflakeTopologyStrategy extends TopologyStrategy {

    //ring variables
    private final int RING_BRIDGE_STEP_ON_RING = 2; //must be at least 0
    private final int RING_BRIDGE_OFFSET = 1; //must be at least 0
    private final int RING_BRIDGE_MIRROR_NUM_HEIGHT = 2; //must be at least
    private final int MAX_RING_LAYERS = 2; //must be at least 1
    private final int MINIMAL_RING_MIRROR_COUNT = 3; //must be at least 3 (triangle minimum)

    //star variables
    private final int EXTERN_STAR_MAX_TREE_DEPTH = 2; //must be at least 1
    private final int BRIDGE_TO_EXTERN_STAR_DISTANCE = 1; //must be at least 0
    private final double EXTERN_STAR_RATIO = 0.3; //must be between 0 and 1

    /**
     * Validiert alle Konstanten bei der Initialisierung
     */
    private void validateAllParameters() {
        try {
            SnowflakeTopologyValidator.validateRingParameters(
                    RING_BRIDGE_STEP_ON_RING,
                    RING_BRIDGE_OFFSET,
                    RING_BRIDGE_MIRROR_NUM_HEIGHT,
                    MAX_RING_LAYERS,
                    MINIMAL_RING_MIRROR_COUNT
            );

            SnowflakeTopologyValidator.validateStarParameters(
                    EXTERN_STAR_MAX_TREE_DEPTH,
                    BRIDGE_TO_EXTERN_STAR_DISTANCE,
                    EXTERN_STAR_RATIO
            );
        } catch (SnowflakeTopologyException e) {
            throw new IllegalStateException("Ungültige Snowflake-Topologie-Konfiguration: " + e.getMessage(), e);
        }
    }

    // Konstruktor oder Initialisierungsblock für Validierung
    public SnowflakeTopologyStrategy() {
        validateAllParameters();
    }


    private AttributeUtils.Tuple<Integer, Integer> snowflakeDistribution(int numMirrors) {
        // Validierung der gegebenen Mirrors am Anfang der Methode
        if (numMirrors <= 0) {
            throw new InvalidMirrorDistributionException(numMirrors, 0, 0, "Gesamtanzahl der Mirrors muss positiv sein");
        }

        //ratios (auto)
        double INTERN_ALL_RINGS_MIRRORS_RATIO = 1.0 - EXTERN_STAR_RATIO;
        double EACH_INNER_RING_MIRRORS_RATIO = INTERN_ALL_RINGS_MIRRORS_RATIO / MAX_RING_LAYERS;
        int numMirrorsOnRings = (int) Math.floor(numMirrors * EACH_INNER_RING_MIRRORS_RATIO);
        int numMirrorsOnExternStars = (int) Math.floor(numMirrors * EXTERN_STAR_RATIO);
        //in case of not satisfying mirror count, add a star node
        int allMirrorsCounted = numMirrorsOnRings + numMirrorsOnExternStars;
        if (allMirrorsCounted < numMirrors) {
            //exeption to set up an extra Mirror on the external stars in case of odd numbers
            numMirrorsOnExternStars += numMirrors - allMirrorsCounted;
        }

        // Validiere das Ergebnis
        SnowflakeTopologyValidator.validateMirrorDistribution(
                numMirrors, numMirrorsOnRings, numMirrorsOnExternStars,
                MINIMAL_RING_MIRROR_COUNT, MAX_RING_LAYERS
        );

        return new AttributeUtils.Tuple<>(numMirrorsOnRings, numMirrorsOnExternStars);
    }

    private int getRingStealingIndex(ArrayList<Integer> outsideToInsideMirrorCountOnRing) {
        for (int i = outsideToInsideMirrorCountOnRing.size() - 1; i >= 0; i--) {
            if (outsideToInsideMirrorCountOnRing.get(i) > 0) return i;
        }
        return 0;
    }

    private int getSumMirrorsLeft(ArrayList<Integer> outsideToInsideMirrorCountOnRing) {
        int sum = 0;
        for (Integer integer : outsideToInsideMirrorCountOnRing) {
            sum += integer;
        }
        return sum;
    }

    private AttributeUtils.Tuple<ArrayList<Integer>, List<List<Integer>>> getSafeRingMirrorDistribution(int numMirrorsToRings) {
        //TODO: Argument exceptions
        //calculate bridges between rings if there are any
        int safeRingCount = (int) Math.floor(numMirrorsToRings / (double) MINIMAL_RING_MIRROR_COUNT);
        if (safeRingCount == 0) safeRingCount = 1;

        ArrayList<Integer> outsideToInsideMirrorCountOnRing = new ArrayList<>(Collections.nCopies(safeRingCount, 0));
        List<List<Integer>> bridgedBetweenRings = new LinkedList<>();
        //fill most inner ring last and don't fill it fully
        for (int i = 0; i < outsideToInsideMirrorCountOnRing.size(); i++) {
            int setToRing = Math.min((int) Math.ceil((double) numMirrorsToRings / safeRingCount), numMirrorsToRings);
            outsideToInsideMirrorCountOnRing.set(i, setToRing);
            numMirrorsToRings -= setToRing;
        }
        for (int i = 0; i < outsideToInsideMirrorCountOnRing.size(); i++) {
            int realGenerateOffset = RING_BRIDGE_OFFSET % outsideToInsideMirrorCountOnRing.get(i);
            for (int j = 0; j < outsideToInsideMirrorCountOnRing.get(i); j++) {
                if (bridgedBetweenRings.size() - 1 <= j) {
                    bridgedBetweenRings.add(new LinkedList<>());
                }
                //step over each ring to plan bridge by Ring bridge step
                //fill bridging and steal mirrors from the inside out to the outside, only bridge in between if opposite Ring is available
                if ((j - realGenerateOffset) % RING_BRIDGE_STEP_ON_RING == 0 || (outsideToInsideMirrorCountOnRing.get(i) - 1 < RING_BRIDGE_OFFSET)) {
                    //steal mirrors from the innermost ring that stores greater zero mirrors
                    //in case the innermost ring mirror count is zero,steal from the next innermost ring
                    //check stealing ring on every movement
                    int numStealMirrors = RING_BRIDGE_MIRROR_NUM_HEIGHT;
                    int absoluteStolenMirrors = 0;
                    while (numStealMirrors > 0 && getSumMirrorsLeft(outsideToInsideMirrorCountOnRing) > 0) {
                        int indexStealFromRing = getRingStealingIndex(outsideToInsideMirrorCountOnRing);
                        int stealAbleMirrorsFromOutermostRing = outsideToInsideMirrorCountOnRing.get(indexStealFromRing);
                        int stolenMirrors = Math.min(stealAbleMirrorsFromOutermostRing, RING_BRIDGE_MIRROR_NUM_HEIGHT);
                        numStealMirrors -= stolenMirrors;
                        absoluteStolenMirrors += stolenMirrors;
                        outsideToInsideMirrorCountOnRing.set(indexStealFromRing, stealAbleMirrorsFromOutermostRing - stolenMirrors);
                    }
                    if (absoluteStolenMirrors >= 0) {
                        bridgedBetweenRings.get(j).add(absoluteStolenMirrors);
                    }
                }
            }
        }
        //return description of mirror rings and bridges between them
        return new AttributeUtils.Tuple<>(outsideToInsideMirrorCountOnRing, bridgedBetweenRings);
    }

    private StructureNode getDeepestNode(StructureNode node, int maxDepth) {
        if (node == null || node.getChildren().isEmpty()) return null;
        StructureNode currentDeepestMirror = node.getChildren().get(0);
        while (!currentDeepestMirror.getChildren().isEmpty()) {
            if (maxDepth == 0) break;
            currentDeepestMirror = currentDeepestMirror.getChildren().get(0);
            maxDepth--;
        }
        return currentDeepestMirror;
    }

    private ArrayList<StructureNode> getSafeExternStarDistribution(int numMirrorsOnExternStars, int numMirrorsOnFirstRing) {
        //TODO: Argument exceptions
        ArrayList<StructureNode> mirrorCountOnExternStars = new ArrayList<>(Collections.nCopies(numMirrorsOnFirstRing, null));
        int mirrorsForEachStar = (int) Math.floor((double) numMirrorsOnExternStars / numMirrorsOnFirstRing);
        int mirrorsLeftAfterBridgedToRoot = Math.max(0, numMirrorsOnExternStars - numMirrorsOnFirstRing * BRIDGE_TO_EXTERN_STAR_DISTANCE);
        int mirrorsToBridges = numMirrorsOnExternStars - mirrorsLeftAfterBridgedToRoot;
        int mirrorsToTreeOnStar = mirrorsForEachStar - BRIDGE_TO_EXTERN_STAR_DISTANCE;
        //uneven mirrors after natural distribution
        int mirrorsToBeCircularFilled = Math.max(0, mirrorsLeftAfterBridgedToRoot - numMirrorsOnFirstRing * mirrorsToTreeOnStar);

        //first circular fill bridges with available mirrors
        for (int i = 0; i < mirrorsToBridges; i++) {
            int addIndexToMirror = i % numMirrorsOnFirstRing;

            if (mirrorCountOnExternStars.get(addIndexToMirror) == null) {
                mirrorCountOnExternStars.set(addIndexToMirror, new StructureNode(IDGenerator.getInstance().getNextID(), EXTERN_STAR_MAX_TREE_DEPTH));
            } else {
                getDeepestNode(mirrorCountOnExternStars.get(addIndexToMirror), EXTERN_STAR_MAX_TREE_DEPTH).addChild(new StructureNode(IDGenerator.getInstance().getNextID(), EXTERN_STAR_MAX_TREE_DEPTH));
            }
        }
        //second fill a depth-limited tree on each star at the end of the bridges
        int mirrorsToTreeOnStarCopy = mirrorsToTreeOnStar;
        for (int i = 0; i < mirrorCountOnExternStars.size(); i++) {
            double dynamicUseOfTreeMirrors = Math.round((double) mirrorsToTreeOnStarCopy / (numMirrorsOnFirstRing - i));
            int dynamicUseOfTreeMirrorsInt = (int) dynamicUseOfTreeMirrors;
            mirrorsToTreeOnStarCopy -= dynamicUseOfTreeMirrorsInt;

            TreeBuilder builder = new TreeBuilder();
            StructureNode subTree = builder.buildTree(dynamicUseOfTreeMirrorsInt, EXTERN_STAR_MAX_TREE_DEPTH);
            getDeepestNode(mirrorCountOnExternStars.get(i), EXTERN_STAR_MAX_TREE_DEPTH).addChild(subTree);
        }
        //third fill the rest with mirrors circular to the B-trees on the stars
        for (int i = 0; i < mirrorsToBeCircularFilled; i++) {
            int addIndexToMirror = i % numMirrorsOnFirstRing;
            if (mirrorCountOnExternStars.get(addIndexToMirror) == null) {
                mirrorCountOnExternStars.set(addIndexToMirror, new StructureNode(IDGenerator.getInstance().getNextID(), EXTERN_STAR_MAX_TREE_DEPTH));
            } else {
                TreeBuilder builder = new TreeBuilder();
                builder.addNodesToExistingTreeBalanced(getDeepestNode(mirrorCountOnExternStars.get(addIndexToMirror), EXTERN_STAR_MAX_TREE_DEPTH), 1, EXTERN_STAR_MAX_TREE_DEPTH);
            }
        }

        return mirrorCountOnExternStars;
    }

    private void finalizeBridges(Properties props, Set<Link> ret, LinkedList<AttributeUtils.Tuple<Mirror, Mirror>> bridgeHeads, Iterator<Mirror> source_mirror, final int bridge_len) {
        for (AttributeUtils.Tuple<Mirror, Mirror> bridgeHead : bridgeHeads) {
            int fill_count = 1;
            Link bothLinked = bridgeHead.x.getJointMirrorLinks(bridgeHead.y).iterator().next();
            Mirror outerRingMirror = bridgeHead.x;
            Mirror innerRingMirror = bridgeHead.y;
            Mirror newInterMirror;

            while (source_mirror.hasNext() && fill_count < bridge_len) {
                newInterMirror = source_mirror.next();
                //disconnect and deleteLink from the innerRingMirror and registry and connect a new one to the newInterMirror
                outerRingMirror.removeLink(bothLinked);
                innerRingMirror.removeLink(bothLinked);
                ret.remove(bothLinked);

                bothLinked = new Link(IDGenerator.getInstance().getNextID(), outerRingMirror, newInterMirror, 0, props);
                outerRingMirror.addLink(bothLinked);
                newInterMirror.addLink(bothLinked);
                ret.add(bothLinked);

                Link newLinked = new Link(IDGenerator.getInstance().getNextID(), newInterMirror, innerRingMirror, 0, props);
                newInterMirror.addLink(newLinked);
                innerRingMirror.addLink(newLinked);
                ret.add(newLinked);
                //create a new Link between the newInterMirror and the innerRingMirror

                //make the newInterMirror the new innerRingMirror to repeat the process in case of longer bridges
                innerRingMirror = newInterMirror;
                fill_count++;
            }
            if (!source_mirror.hasNext()) {
                break;
            }
        }
    }

    private AttributeUtils.Tuple<Iterator<Mirror>, LinkedList<Mirror>> connectRingAndBridgesAndReturnSubtopologyPorts(Iterator<Mirror> source_mirror, Properties props, Set<Link> ret, ArrayList<Integer> mirrorRingsCount, List<List<Integer>> bridgesBetweenRings) {
        //connect rings
        //TODO: set exceptions
        LinkedList<Mirror> subtopologyPorts = new LinkedList<>();
        LinkedList<AttributeUtils.Tuple<Mirror, Mirror>> bridgeHeads = new LinkedList<>();
        ArrayList<Mirror> nextRingMirrorsCache = new ArrayList<>(Collections.nCopies(mirrorRingsCount.get(0), null));

        Mirror lastMirror = null;

        //for each of the snowflake inner rings
        for (int i = 0; i < mirrorRingsCount.size(); i++) {
            int ringCount = mirrorRingsCount.get(i);

            Mirror firstMirror = null;

            //iterate for each mirror on the ring
            for (int j = 0; j < ringCount; j++) {
                int j_offset = (j + i * RING_BRIDGE_OFFSET) % mirrorRingsCount.get(0);
                //Build ring itself by connecting mirrors, load next available mirror from pool
                Mirror sourceMirror;

                if (source_mirror.hasNext()) {
                    sourceMirror = source_mirror.next();
                } else {
                    throw new RuntimeException("No more mirrors available");
                }

                if (j == 0) {
                    firstMirror = sourceMirror;
                }

                //basic connection of bridges in between rings, notify bridge heads for multiple mirror extension
                if (nextRingMirrorsCache.get(j_offset) != null) {
                    Link l = new Link(IDGenerator.getInstance().getNextID(), nextRingMirrorsCache.get(j_offset), sourceMirror, 0, props);
                    nextRingMirrorsCache.get(j_offset).addLink(l);
                    sourceMirror.addLink(l);
                    ret.add(l);
                    bridgeHeads.add(new AttributeUtils.Tuple<>(nextRingMirrorsCache.get(j_offset), sourceMirror));
                }

                //store Mirror of last outer ring
                nextRingMirrorsCache.set(j_offset, sourceMirror);

                //link last mirror in case its not the first mirror of the ring
                if (lastMirror != null) {
                    Link l = new Link(IDGenerator.getInstance().getNextID(), lastMirror, sourceMirror, 0, props);
                    lastMirror.addLink(l);
                    sourceMirror.addLink(l);
                    ret.add(l);
                }

                //build bridges with a circular offset with the same stepping of subtopology ports, using the last mirror
                //edge case:
                //The bridge must be built at offset zero
                //if the chosen offset is higher than the ring count
                //This ensures a bridge is always built to the next ring by remembering the bridge heads to build
                //the bridge later on
                if (bridgesBetweenRings.get(i).get(j_offset) > 0) {
                    //set bridge heads and register end mirrors to cache
                    Mirror bridgeHead = source_mirror.next();
                    bridgeHeads.add(new AttributeUtils.Tuple<>(sourceMirror, bridgeHead));
                    nextRingMirrorsCache.set(j_offset, bridgeHead);
                }
                //build subtopology ports with a stepping using last mirror
                if (j_offset % RING_BRIDGE_STEP_ON_RING == 0) {
                    //register subtopology ports if they appear
                    subtopologyPorts.add(sourceMirror);
                }

                //close ring on last mirror of collection
                if (j == ringCount - 1 && ringCount > 2 && firstMirror != null) {
                    Link l = new Link(IDGenerator.getInstance().getNextID(), firstMirror, sourceMirror, 0, props);
                    firstMirror.addLink(l);
                    sourceMirror.addLink(l);
                    ret.add(l);
                }
                lastMirror = sourceMirror;
            }

            lastMirror = null;
        }

        //Bridge head may be longer, so fill more mirrors in between source and target mirrors of bridge heads
        finalizeBridges(props, ret, bridgeHeads, source_mirror, RING_BRIDGE_MIRROR_NUM_HEIGHT);

        return new AttributeUtils.Tuple<>(source_mirror, subtopologyPorts);
    }

    private LinkedList<AttributeUtils.Tuple<Mirror, Mirror>> connectStarBridge(Iterator<Mirror> source_mirror, Properties props, Set<Link> ret, LinkedList<Mirror> subtopologyPorts) {
        Iterator<Mirror> subtopologyPortIterator = subtopologyPorts.iterator();
        LinkedList<AttributeUtils.Tuple<Mirror, Mirror>> bridgeHeads = new LinkedList<>();
        while (subtopologyPortIterator.hasNext()) {
            Mirror subtopologyPort = subtopologyPortIterator.next();

            if (BRIDGE_TO_EXTERN_STAR_DISTANCE > 0 && source_mirror.hasNext()) {
                Mirror bridgeTarget = source_mirror.next();
                Link l = new Link(IDGenerator.getInstance().getNextID(), subtopologyPort, bridgeTarget, 0, props);
                subtopologyPort.addLink(l);
                bridgeTarget.addLink(l);
                ret.add(l);
                bridgeHeads.add(new AttributeUtils.Tuple<>(subtopologyPort, bridgeTarget));
            } else {
                break;
            }
        }
        finalizeBridges(props, ret, bridgeHeads, source_mirror, BRIDGE_TO_EXTERN_STAR_DISTANCE);

        return bridgeHeads;
    }

    private void buildBalancedMirrorTree(Iterator<Mirror> source_mirror, Properties props, Set<Link> ret, ArrayList<StructureNode> treeTemplate, LinkedList<AttributeUtils.Tuple<Mirror, Mirror>> bridgeHeads) {

        Iterator<AttributeUtils.Tuple<Mirror, Mirror>> bridgeHeadIterator = bridgeHeads.iterator();

        for (StructureNode tree : treeTemplate) {
            if (tree == null || !bridgeHeadIterator.hasNext()) continue;

            // Stack für DFS-Traversierung erstellen
            Stack<AttributeUtils.Tuple<StructureNode, Mirror>> dfsStack = new Stack<>();

            // Bridge-Heads holen für aktuellen Stern
            AttributeUtils.Tuple<Mirror, Mirror> currentBridgeHead = bridgeHeadIterator.next();
            Mirror starRootMirror = currentBridgeHead.y; // Bridge-Target wird zur Wurzel des Sterns

            // Root-Node mit entsprechendem Mirror auf Stack legen
            dfsStack.push(new AttributeUtils.Tuple<>(tree, starRootMirror));

            // DFS-Traversierung mit Stack
            while (!dfsStack.isEmpty() && source_mirror.hasNext()) {
                // Aktuellen Zustand vom Stack nehmen
                AttributeUtils.Tuple<StructureNode, Mirror> current = dfsStack.pop();
                StructureNode currentNode = current.x;
                Mirror currentMirror = current.y;

                // Für alle Kinder des aktuellen Knotens
                for (StructureNode child : currentNode.getChildren()) {
                    if (!source_mirror.hasNext()) break;

                    // Neuen Mirror für das Kind holen
                    Mirror childMirror = source_mirror.next();

                    // Link zwischen Parent und Child erstellen
                    Link childLink = new Link(IDGenerator.getInstance().getNextID(),
                            currentMirror, childMirror, 0, props);
                    currentMirror.addLink(childLink);
                    childMirror.addLink(childLink);
                    ret.add(childLink);

                    // Kind-Zustand auf Stack legen (für weitere Traversierung)
                    dfsStack.push(new AttributeUtils.Tuple<>(child, childMirror));
                }
            }
        }
    }

    private void connectStars(Iterator<Mirror> source_mirror, Properties props, Set<Link> ret, LinkedList<Mirror> subtopologyPorts, ArrayList<StructureNode> mirrorCountOnExternStars) {
        //build a new bridge at each subtopology port, reuse bridge build function in case the bridge ist longer than 1 link
        LinkedList<AttributeUtils.Tuple<Mirror, Mirror>> bridgeHeads = connectStarBridge(source_mirror, props, ret, subtopologyPorts);
        //build a balanced tree from a template
        buildBalancedMirrorTree(source_mirror, props, ret, mirrorCountOnExternStars, bridgeHeads);
    }

    @Override
    public Set<Link> initNetwork(Network n, Properties props) {
        //TODO: split Ring topology, BalancedDepthTree topology and refactor to subtopology ports that are initiated on
        //TODO: outside Ring topology of snowflake to connect also other objects than just balanced trees like rings and stars
        Set<Link> ret = new HashSet<>();
        if (n.getMirrors().isEmpty()) return ret;
        //calculate Mirror distribution

        int numMirrors = n.getMirrors().size();
        //x is innerRings, y is external stars
        AttributeUtils.Tuple<Integer, Integer> numMirrorsDistribution = snowflakeDistribution(numMirrors);
        //distribute Mirrors from outside ring to inside ring (numerical abstraction layer)
        AttributeUtils.Tuple<ArrayList<Integer>, List<List<Integer>>> outsideToInsideMirrorCountOnRing = getSafeRingMirrorDistribution(numMirrorsDistribution.x);
        ArrayList<Integer> mirrorRingsCount = outsideToInsideMirrorCountOnRing.x;
        List<List<Integer>> bridgesBetweenRings = outsideToInsideMirrorCountOnRing.y;
        //distribute mirrors from outside to inside ring (numerical abstraction layer)
        int numMirrorsToStarBridges = (mirrorRingsCount.get(0) / RING_BRIDGE_STEP_ON_RING) * RING_BRIDGE_MIRROR_NUM_HEIGHT;
        ArrayList<StructureNode> mirrorCountOnExternStars = getSafeExternStarDistribution(Math.min(0, numMirrorsDistribution.y - numMirrorsToStarBridges), mirrorRingsCount.get(0));

        //build rings and bridges from datastructures description (construction layer)
        Iterator<Mirror> source_mirror = n.getMirrors().iterator();
        AttributeUtils.Tuple<Iterator<Mirror>, LinkedList<Mirror>> ringData = connectRingAndBridgesAndReturnSubtopologyPorts(source_mirror, props, ret, mirrorRingsCount, bridgesBetweenRings);
        Iterator<Mirror> sourceMirrorTmpIterator = ringData.x;
        LinkedList<Mirror> subtopologyPorts = ringData.y;
        //build stars on the outermost ring from datastructures description (construction layer)
        connectStars(sourceMirrorTmpIterator, props, ret, subtopologyPorts, mirrorCountOnExternStars);

        return ret;
    }

    @Override
    public void handleAddNewMirrors(Network n, int newMirrors, Properties props, int simTime) {
        //TODO: add new mirrors to stars only, develop dynamic Mirror and link building strategy
        List<Mirror> mirrorsToAdd = createMirrors(newMirrors, simTime, props);
        n.getMirrors().addAll(mirrorsToAdd);
        restartNetwork(n, props, simTime);
    }

    private int countRingAndBridgeLinks(ArrayList<Integer> mirrorRingsCount, List<List<Integer>> bridgesBetweenRings) {
        int linkCount = 0;

        // Für jeden Ring
        for(int i = 0; i < mirrorRingsCount.size(); i++) {
            int ringCount = mirrorRingsCount.get(i);

            // Ring-interne Links (Ringschluss)
            if(ringCount > 2) {
                linkCount += ringCount; // Jeder Mirror mit dem nächsten + letzter mit erstem
            } else if(ringCount == 2) {
                linkCount += 1; // Nur eine Verbindung zwischen zwei Mirrors
            }

            // Brücken zwischen Ringen
            for(int j = 0; j < bridgesBetweenRings.size() && j < ringCount; j++) {
                int j_offset = (j + i * RING_BRIDGE_OFFSET) % mirrorRingsCount.get(0);
                if(j_offset < bridgesBetweenRings.size() && !bridgesBetweenRings.get(j_offset).isEmpty()) {
                    // Basis-Link zwischen Ringen
                    if(i > 0) { // Nicht für den äußersten Ring
                        linkCount += 1;
                    }

                    // Zusätzliche Bridge-Links
                    if(bridgesBetweenRings.get(j_offset).size() > i && bridgesBetweenRings.get(j_offset).get(i) > 0) {
                        linkCount += bridgesBetweenRings.get(j_offset).get(i) * RING_BRIDGE_MIRROR_NUM_HEIGHT;
                    }
                }
            }
        }

        return linkCount;
    }

    private int countStarLinks(ArrayList<StructureNode> mirrorCountOnExternStars) {
        int linkCount = 0;

        for(StructureNode tree : mirrorCountOnExternStars) {
            if(tree != null) {
                // Brücken-Links zum Stern
                linkCount += BRIDGE_TO_EXTERN_STAR_DISTANCE;

                // Stern-interne Links (DFS-Traversierung zählen)
                linkCount += countTreeLinks(tree);
            }
        }

        return linkCount;
    }

    private int countTreeLinks(StructureNode node) {
        if(node == null) return 0;

        int linkCount = 0;

        // DFS-Stack für Zählung
        Stack<StructureNode> dfsStack = new Stack<>();
        dfsStack.push(node);

        while(!dfsStack.isEmpty()) {
            StructureNode current = dfsStack.pop();

            // Für jedes Kind einen Link zählen
            for(StructureNode child : current.getChildren()) {
                linkCount += 1; // Link zwischen Parent und Child
                dfsStack.push(child); // Kind für weitere Traversierung
            }
        }

        return linkCount;
    }

    private int linkCount(int numMirrors){
        // Gleiche Verteilungslogik wie in initNetwork
        AttributeUtils.Tuple<Integer, Integer> numMirrorsDistribution = snowflakeDistribution(numMirrors);
        AttributeUtils.Tuple<ArrayList<Integer>,List<List<Integer>>> outsideToInsideMirrorCountOnRing = getSafeRingMirrorDistribution(numMirrorsDistribution.x);
        ArrayList<Integer> mirrorRingsCount = outsideToInsideMirrorCountOnRing.x;
        List<List<Integer>> bridgesBetweenRings = outsideToInsideMirrorCountOnRing.y;

        int numMirrorsToStarBridges = (mirrorRingsCount.get(0) / RING_BRIDGE_STEP_ON_RING) * RING_BRIDGE_MIRROR_NUM_HEIGHT;
        ArrayList<StructureNode> mirrorCountOnExternStars = getSafeExternStarDistribution(Math.max(0,numMirrorsDistribution.y-numMirrorsToStarBridges),mirrorRingsCount.get(0));

        // Zähle Links anstatt sie zu erstellen
        int totalLinks = 0;

        // Links für Ringe und Brücken zwischen Ringen
        totalLinks += countRingAndBridgeLinks(mirrorRingsCount, bridgesBetweenRings);

        // Links für Sterne (Brücken zu Sternen + Stern-interne Links)
        totalLinks += countStarLinks(mirrorCountOnExternStars);

        return totalLinks;
    }

    @Override
    public int getNumTargetLinks(Network n) {
        if(n.getMirrors().isEmpty()) return 0;
    
        int numMirrors = n.getMirrors().size();
    
        return linkCount(numMirrors);
    }

    @Override
    public int getPredictedNumTargetLinks(Action a) {
        int m = a.getNetwork().getNumMirrors();

        if(a instanceof MirrorChange mc) {
            m += mc.getNewMirrors();
        }
        // Bei TargetLinkChange bleibt die Topologie-Struktur gleich

        return linkCount(m);
    }

    /**Removes the requested amount of mirrors from the network. The mirrors with the largest ID will be removed.
     *
     * @param n the {@link Network}
     * @param removeMirrors number of mirrors to be removed
     * @param props {@link Properties} of the simulation
     * @param simTime current simulation time
     */
    @Override
    public void handleRemoveMirrors(Network n, int removeMirrors, Properties props, int simTime) {
        //keep a balance between removing from substructures

    }
}