package org.lrdm.topologies;

import org.graphstream.ui.swing.util.AttributeUtils;
import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.Action;
import org.lrdm.util.*;

import java.util.*;

public class SnowflakeTopologyStrategy extends TopologyStrategy{

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

    //ratios (auto)
    private final double INTERN_ALL_RINGS_MIRRORS_RATIO = 1.0-EXTERN_STAR_RATIO;
    private final double EACH_INNER_RING_MIRRORS_RATIO = INTERN_ALL_RINGS_MIRRORS_RATIO / MAX_RING_LAYERS;

    private AttributeUtils.Tuple<Integer, Integer> snowflakeDistribution(int numMirrors) {
        int numMirrorsOnRings = (int)Math.floor(numMirrors * EACH_INNER_RING_MIRRORS_RATIO);
        int numMirrorsOnExternStars = (int)Math.floor(numMirrors * EXTERN_STAR_RATIO);
        //in case of not satisfying mirror count, add a star node
        int allMirrorsCounted = numMirrorsOnRings+numMirrorsOnExternStars;
        if(allMirrorsCounted < numMirrors) {
            //exeption to set up an extra Mirror on the external stars in case of odd numbers
            numMirrorsOnExternStars += numMirrors-allMirrorsCounted;
        }
        return new AttributeUtils.Tuple<>(numMirrorsOnRings,numMirrorsOnExternStars);
    }

    private int getRingStealingIndex(ArrayList<Integer> outsideToInsideMirrorCountOnRing) {
        for(int i = outsideToInsideMirrorCountOnRing.size()-1; i >= 0; i--) {
            if(outsideToInsideMirrorCountOnRing.get(i) > 0) return i;
        }
        return 0;
    }

    private int getSumMirrorsLeft(ArrayList<Integer> outsideToInsideMirrorCountOnRing) {
        int sum = 0;
        for(int i = 0; i < outsideToInsideMirrorCountOnRing.size(); i++) {
            sum += outsideToInsideMirrorCountOnRing.get(i);
        }
        return sum;
    }

    private AttributeUtils.Tuple<ArrayList<Integer>,List<List<Integer>>> getSafeRingMirrorDistribution(int numMirrorsToRings) {
        //TODO: Argument exceptions
        //calculate bridges between rings if there are any
        int safeRingCount = (int)Math.floor(numMirrorsToRings / (double)MINIMAL_RING_MIRROR_COUNT);
        if(safeRingCount == 0) safeRingCount = 1;

        ArrayList<Integer> outsideToInsideMirrorCountOnRing = new ArrayList<>(Collections.nCopies(safeRingCount, 0));
        List<List<Integer>> bridgedBetweenRings = new LinkedList<>();
        //fill most inner ring last and don't fill it fully
        for(int i = 0; i < outsideToInsideMirrorCountOnRing.size(); i++) {
            int setToRing = Math.min((int)Math.ceil((double)numMirrorsToRings/safeRingCount),numMirrorsToRings);
            outsideToInsideMirrorCountOnRing.set(i, setToRing);
            numMirrorsToRings -= setToRing;
        }
        for(int i = 0; i < outsideToInsideMirrorCountOnRing.size(); i++) {
            int realGenerateOffset = RING_BRIDGE_OFFSET%outsideToInsideMirrorCountOnRing.get(i);
            for(int j = 0; j < outsideToInsideMirrorCountOnRing.get(i); j++) {
                if(bridgedBetweenRings.size()-1 <= j){
                    bridgedBetweenRings.add(new LinkedList<>());
                }
                //step over each ring to plan bridge by Ring bridge step
                //fill bridging and steal mirrors from the inside out to the outside, only bridge in between if opposite Ring is available
                if((j-realGenerateOffset)% RING_BRIDGE_STEP_ON_RING == 0 || (outsideToInsideMirrorCountOnRing.get(i)-1 < RING_BRIDGE_OFFSET)) {
                    //steal mirrors from the innermost ring that stores greater zero mirrors
                    //in case the innermost ring mirror count is zero,steal from the next innermost ring
                    //check stealing ring on every movement
                    int numStealMirrors = RING_BRIDGE_MIRROR_NUM_HEIGHT;
                    int absoluteStolenMirrors = 0;
                    while(numStealMirrors > 0 && getSumMirrorsLeft(outsideToInsideMirrorCountOnRing) > 0) {
                        int indexStealFromRing = getRingStealingIndex(outsideToInsideMirrorCountOnRing);
                        int stealAbleMirrorsFromOutermostRing = outsideToInsideMirrorCountOnRing.get(indexStealFromRing);
                        int stolenMirrors = Math.min(stealAbleMirrorsFromOutermostRing,RING_BRIDGE_MIRROR_NUM_HEIGHT);
                        numStealMirrors -= stolenMirrors;
                        absoluteStolenMirrors += stolenMirrors;
                        outsideToInsideMirrorCountOnRing.set(indexStealFromRing, stealAbleMirrorsFromOutermostRing-stolenMirrors);
                    }
                    if(absoluteStolenMirrors >= 0) {
                        bridgedBetweenRings.get(j).add(absoluteStolenMirrors);
                    }
                }
            }
        }
        //return description of mirror rings and bridges between them
        return new AttributeUtils.Tuple<>(outsideToInsideMirrorCountOnRing,bridgedBetweenRings);
    }

    private SnowflakeStarTreeNode getDeepestNode(SnowflakeStarTreeNode node,int maxDepth) {
        if(node == null || node.getChildren().isEmpty()) return null;
        SnowflakeStarTreeNode currentDeepestMirror = node.getChildren().get(0);
        while(!currentDeepestMirror.getChildren().isEmpty()){
            if(maxDepth==0)break;
            currentDeepestMirror = currentDeepestMirror.getChildren().get(0);
            maxDepth--;
        }
        return currentDeepestMirror;
    }

    private ArrayList<SnowflakeStarTreeNode> getSafeExternStarDistribution(int numMirrorsOnExternStars, int numMirrorsOnFirstRing) {
        //TODO: Argument exceptions
        ArrayList<SnowflakeStarTreeNode> mirrorCountOnExternStars = new ArrayList<>(Collections.nCopies(numMirrorsOnFirstRing, null));
        int mirrorsForEachStar = (int)Math.floor((double)numMirrorsOnExternStars / numMirrorsOnFirstRing);
        int mirrorsLeftAfterBridgedToRoot = Math.max(0,numMirrorsOnExternStars - numMirrorsOnFirstRing * BRIDGE_TO_EXTERN_STAR_DISTANCE);
        int mirrorsToBridges = numMirrorsOnExternStars - mirrorsLeftAfterBridgedToRoot;
        int mirrorsToTreeOnStar = mirrorsForEachStar - BRIDGE_TO_EXTERN_STAR_DISTANCE;
        //uneven mirrors after natural distribution
        int mirrorsToBeCircularFilled = Math.max(0,mirrorsLeftAfterBridgedToRoot - numMirrorsOnFirstRing * mirrorsToTreeOnStar);

        //first circular fill bridges with available mirrors
        for(int i = 0; i < mirrorsToBridges; i++) {
            int addIndexToMirror = i%numMirrorsOnFirstRing;

            if(mirrorCountOnExternStars.get(addIndexToMirror) == null) {
                mirrorCountOnExternStars.set(addIndexToMirror, new SnowflakeStarTreeNode(IDGenerator.getInstance().getNextID(), EXTERN_STAR_MAX_TREE_DEPTH));
            }
            else{
                getDeepestNode(mirrorCountOnExternStars.get(addIndexToMirror),EXTERN_STAR_MAX_TREE_DEPTH).addChild(new SnowflakeStarTreeNode(IDGenerator.getInstance().getNextID(), EXTERN_STAR_MAX_TREE_DEPTH));
            }
        }
        //second fill a depth-limited tree on each star at the end of the bridges
        int mirrorsToTreeOnStarCopy = mirrorsToTreeOnStar;
        for(int i = 0; i < mirrorCountOnExternStars.size(); i++) {
            double dynamicUseOfTreeMirrors = Math.round((double)mirrorsToTreeOnStarCopy/(numMirrorsOnFirstRing-i));
            int dynamicUseOfTreeMirrorsInt = (int)dynamicUseOfTreeMirrors;
            mirrorsToTreeOnStarCopy -= dynamicUseOfTreeMirrorsInt;

            SnowflakeTreeBuilder builder = new SnowflakeTreeBuilder();
            SnowflakeStarTreeNode subTree = builder.buildTree(dynamicUseOfTreeMirrorsInt, EXTERN_STAR_MAX_TREE_DEPTH);
            getDeepestNode(mirrorCountOnExternStars.get(i),EXTERN_STAR_MAX_TREE_DEPTH).addChild(subTree);
        }
        //third fill the rest with mirrors circular to the B-trees on the stars
        for(int i = 0; i < mirrorsToBeCircularFilled; i++) {
            int addIndexToMirror = i%numMirrorsOnFirstRing;
            if(mirrorCountOnExternStars.get(addIndexToMirror) == null) {
                mirrorCountOnExternStars.set(addIndexToMirror, new SnowflakeStarTreeNode(IDGenerator.getInstance().getNextID(), EXTERN_STAR_MAX_TREE_DEPTH));
            }
            else{
                SnowflakeTreeBuilder builder = new SnowflakeTreeBuilder();
                builder.addNodesToExistingTreeBalanced(getDeepestNode(mirrorCountOnExternStars.get(addIndexToMirror),EXTERN_STAR_MAX_TREE_DEPTH),1, EXTERN_STAR_MAX_TREE_DEPTH);
            }
        }

        return mirrorCountOnExternStars;
    }

    private void finalizeBridges(Network n, Properties props, LinkedList<AttributeUtils.Tuple<Mirror,Mirror>> bridgeHeads, Iterator<Mirror> source_mirror) {

    }

    private Set<Mirror> connectRingAndBridgesAndReturnStarPorts(Network n, Properties props, Set<Link> ret, ArrayList<Integer> mirrorRingsCount, List<List<Integer>> bridgesBetweenRings) {
        //connect rings
        //TODO: set exceptions
        Set<Mirror> starPorts = new HashSet<>();
        LinkedList<AttributeUtils.Tuple<Mirror,Mirror>> bridgeHeads = new LinkedList<>();
        ArrayList<Mirror> nextRingMirrorsCache = new ArrayList<>(Collections.nCopies(mirrorRingsCount.get(0), null));

        Iterator<Mirror> source_mirror = n.getMirrors().iterator();
        Mirror lastMirror = null;

        //for each of the snowflake inner rings
        for(int i = 0; i < mirrorRingsCount.size(); i++) {
            int ringCount = mirrorRingsCount.get(i);

            Mirror firstMirror = null;

            //iterate for each mirror on the ring
            for(int j = 0; j < ringCount; j++) {
                int j_offset = (j+i*RING_BRIDGE_STEP_ON_RING)%mirrorRingsCount.get(0);
                //Build ring itself by connecting mirrors, load next available mirror from pool
                Mirror sourceMirror;

                if(source_mirror.hasNext()) {
                    sourceMirror = source_mirror.next();
                } else {
                    throw new RuntimeException("No more mirrors available");
                }

                if(j == 0) {
                    firstMirror = sourceMirror;
                }

                //basic connection of bridges in between rings, notify bridge heads for multiple mirror extension
                if(nextRingMirrorsCache.get(j_offset) != null) {
                    Link l = new Link(IDGenerator.getInstance().getNextID(), nextRingMirrorsCache.get(j_offset), sourceMirror, 0, props);
                    nextRingMirrorsCache.get(j_offset).addLink(l);
                    sourceMirror.addLink(l);
                    ret.add(l);
                    bridgeHeads.add(new AttributeUtils.Tuple<>(nextRingMirrorsCache.get(j_offset),sourceMirror));
                }

                //store Mirror of last outer ring
                nextRingMirrorsCache.set(j_offset, sourceMirror);

                //link last mirror in case its not the first mirror of the ring
                if(lastMirror != null) {
                    Link l = new Link(IDGenerator.getInstance().getNextID(), lastMirror, sourceMirror, 0, props);
                    lastMirror.addLink(l);
                    sourceMirror.addLink(l);
                    ret.add(l);
                }

                //build bridges with a circular offset with the same stepping of starports, using the last mirror
                //edge case:
                //The bridge must be built at offset zero
                //if the chosen offset is higher than the ring count
                //This ensures a bridge is always built to the next ring by remembering the bridge heads to build
                //the bridge later on
                if(bridgesBetweenRings.get(i).get(j_offset) > 0){
                    //set bridge heads and register end mirrors to cache
                    Mirror bridgeHead = source_mirror.next();
                    bridgeHeads.add(new AttributeUtils.Tuple<>(sourceMirror,bridgeHead));
                    nextRingMirrorsCache.set(j_offset, bridgeHead);
                }
                //build starports with a stepping using last mirror
                if(j_offset%RING_BRIDGE_STEP_ON_RING == 0){
                    //register starPorts if they appear
                    starPorts.add(sourceMirror);
                }

                //close ring on last mirror of collection
                if(j == ringCount-1 && ringCount > 2 && firstMirror != null){
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
        finalizeBridges(n, props, bridgeHeads, source_mirror);

        return starPorts;
    }

    private void connectStars(Network n, Properties props, Set<Link> ret, ArrayList<SnowflakeStarTreeNode> mirrorCountOnExternStars, Set<Mirror> starPorts) {
        //build a new bridge at each star port

        //build a balanced tree

    }

    @Override
    public Set<Link> initNetwork(Network n, Properties props) {
        Set<Link> ret = new HashSet<>();
        if(n.getMirrors().isEmpty()) return ret;
        //calculate Mirror distribution

        int numMirrors = n.getMirrors().size();
        //x is innerRings, y is external stars
        AttributeUtils.Tuple<Integer, Integer> numMirrorsDistribution = snowflakeDistribution(numMirrors);
        //distribute Mirrors from outside ring to inside ring (numerical abstraction layer)
        AttributeUtils.Tuple<ArrayList<Integer>,List<List<Integer>>> outsideToInsideMirrorCountOnRing = getSafeRingMirrorDistribution(numMirrorsDistribution.x);
        ArrayList<Integer> mirrorRingsCount = outsideToInsideMirrorCountOnRing.x;
        List<List<Integer>> bridgesBetweenRings = outsideToInsideMirrorCountOnRing.y;
        //distribute mirrors from outside to inside ring (numerical abstraction layer)
        ArrayList<SnowflakeStarTreeNode> mirrorCountOnExternStars = getSafeExternStarDistribution(numMirrorsDistribution.y,mirrorRingsCount.get(0));

        //build rings and bridges from datastructures description (construction layer)
        Set<Mirror> starPorts = connectRingAndBridgesAndReturnStarPorts(n,props,ret,mirrorRingsCount,bridgesBetweenRings);
        //build stars on the outermost ring from datastructures description (construction layer)
        connectStars(n,props,ret,mirrorCountOnExternStars, starPorts);

        return ret;
    }

    @Override
    public void handleAddNewMirrors(Network n, int newMirrors, Properties props, int simTime) {

    }

    @Override
    public int getNumTargetLinks(Network n) {
        return 0;
    }

    @Override
    public int getPredictedNumTargetLinks(Action a) {
        return 0;
    }
}
