package org.lrdm.topologies;

import org.graphstream.ui.swing.util.AttributeUtils;
import org.lrdm.Link;
import org.lrdm.Network;
import org.lrdm.effectors.Action;
import org.lrdm.util.*;

import java.util.*;

public class SnowflakeTopologyStrategy extends TopologyStrategy{

    //ring variables
    private final int RING_BRIDGE_STEP_ON_RING = 2;
    private final int RING_BRIDGE_OFFSET = 1;
    private final int RING_BRIDGE_MIRROR_NUM_HEIGHT = 2;
    private final int MAX_RING_LAYERS = 2;

    //star variables
    private final int EXTERN_STAR_MAX_TREE_DEPTH = 2;
    private final int BRIDGE_TO_EXTERN_STAR_DISTANCE = 1;
    private final double EXTERN_STAR_RATIO = 0.3;

    //ratios
    private final double INTERN_ALL_RINGS_MIRRORS_RATIO = 1.0-EXTERN_STAR_RATIO;
    private final double EACH_INNER_RING_MIRRORS_RATIO = INTERN_ALL_RINGS_MIRRORS_RATIO / MAX_RING_LAYERS;
    private final int MINIMAL_RING_MIRROR_COUNT = 3;

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
                if((j-realGenerateOffset)% RING_BRIDGE_STEP_ON_RING == 0) {
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
            //TODO: find out how to set the number of tree mirrors to use dynamically
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

    @Override
    public Set<Link> initNetwork(Network n, Properties props) {
        Set<Link> ret = new HashSet<>();
        //calculate Mirror distribution

        int numMirrors = n.getMirrors().size();
        //x is innerRings, y is external stars
        AttributeUtils.Tuple<Integer, Integer> numMirrorsDistribution = snowflakeDistribution(numMirrors);
        //distribute Mirrors from outside ring to inside ring
        AttributeUtils.Tuple<ArrayList<Integer>,List<List<Integer>>> outsideToInsideMirrorCountOnRing = getSafeRingMirrorDistribution(numMirrorsDistribution.x);
        ArrayList<Integer> mirrorRingsCount = outsideToInsideMirrorCountOnRing.x;
        List<List<Integer>> bridgesBetweenRings = outsideToInsideMirrorCountOnRing.y;
        //distribute mirrors from outside to inside ring
        ArrayList<SnowflakeStarTreeNode> mirrorCountOnExternStars = getSafeExternStarDistribution(numMirrorsDistribution.y,mirrorRingsCount.get(0));

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
