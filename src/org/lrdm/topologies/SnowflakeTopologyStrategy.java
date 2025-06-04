package org.lrdm.topologies;

import org.graphstream.ui.swing.util.AttributeUtils;
import org.lrdm.Link;
import org.lrdm.Network;
import org.lrdm.effectors.Action;
import org.lrdm.util.SnowflakeStarTreeNode;

import java.util.*;

public class SnowflakeTopologyStrategy extends TopologyStrategy{

    private final int EXTERN_STAR_BUILD_DISTANCE = 2;
    private final int RING_BRIDGE_STEP = 2;
    private final int RING_BRIDGE_MIRROR_NUM_HEIGHT = 2;
    private final int BRIDGE_TO_EXTERN_STAR_DISTANCE = 1;
    private final double EXTERN_STAR_RATIO = 0.3;
    private final int MAX_RING_LAYERS = 2;
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

    private AttributeUtils.Tuple<ArrayList<Integer>,List<List<Integer>>> getSafeRingMirrorDistribution(int numMirrorsToRings) {
        //calculate bridges between rings if there are any
        int safeRingCount = (int)Math.floor(numMirrorsToRings / (double)MINIMAL_RING_MIRROR_COUNT);
        if(safeRingCount == 0) safeRingCount = 1;

        ArrayList<Integer> outsideToInsideMirrorCountOnRing = new ArrayList<>(Collections.nCopies(safeRingCount, 0));
        List<List<Integer>> bridgedBetweenRings = new ArrayList<>();
        //fill most inner ring last and don't fill it fully
        for(int i = 0; i < outsideToInsideMirrorCountOnRing.size(); i++) {
            bridgedBetweenRings.add(new LinkedList<Integer>());
            for(int j = 0; j < RING_BRIDGE_STEP; j++) {//Limit is the sum of ring elements

            }
            int setToRing = Math.min((int)Math.ceil((double)numMirrorsToRings/safeRingCount),numMirrorsToRings);
            outsideToInsideMirrorCountOnRing.set(i, setToRing);
            numMirrorsToRings -= setToRing;
        }
        //fill bridging and steal mirrors from the inside out to the outside, only bridge in between if opposite Ring is available


        return new AttributeUtils.Tuple<>(outsideToInsideMirrorCountOnRing,bridgedBetweenRings);
    }

    private ArrayList<SnowflakeStarTreeNode> getSafeExternStarDistribution(int numMirrorsOnExternStars) {
        ArrayList<SnowflakeStarTreeNode> mirrorCountOnExternStars = new ArrayList<>(Collections.nCopies(numMirrorsOnExternStars, null));
        //first fulfill templating the bridge to extern star distance, bridge before the extern tree starts
        int a = (int)Math.floor(numMirrorsOnExternStars / (double)MINIMAL_RING_MIRROR_COUNT);

        for(int i = 0; i < mirrorCountOnExternStars.size(); i++) {
            int setToRing = Math.min((int)Math.ceil((double)numMirrorsOnExternStars/mirrorCountOnExternStars.size()),numMirrorsOnExternStars);
            mirrorCountOnExternStars.set(i, setToRing);
            numMirrorsOnExternStars -= setToRing;
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
        ArrayList<SnowflakeStarTreeNode> mirrorCountOnExternStars = getSafeExternStarDistribution(numMirrorsDistribution.y);

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
