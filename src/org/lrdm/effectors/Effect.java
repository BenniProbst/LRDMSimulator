package org.lrdm.effectors;

import org.lrdm.Network;
import org.lrdm.topologies.strategies.BalancedTreeTopologyStrategy;
import org.lrdm.topologies.strategies.FullyConnectedTopology;
import org.lrdm.topologies.strategies.NConnectedTopology;
import org.lrdm.topologies.strategies.TopologyStrategy;

import java.util.Properties;

/**
 * # Effect
 * <p>
 * Represents the predicted impact of executing a given {@link Action} in the
 * latency-aware remote data mirroring simulator. The {@code Effect} provides
 * methods to estimate the change in key quality metrics:
 * <ul>
 *   <li><b>Active Links (AL):</b> The relative change in the proportion of active network links.</li>
 *   <li><b>Bandwidth (BW):</b> The relative change in available network bandwidth.</li>
 *   <li><b>Time-To-Write (TTW):</b> The relative change in time required to commit data to all mirrors.</li>
 *   <li><b>Adaptation Latency:</b> The estimated number of simulation steps needed to apply the change.</li>
 * </ul>
 * <p>
 * The computations take into account:
 * <ul>
 *   <li>The current network topology and its parameters (mirrors, target links per mirror).</li>
 *   <li>The type of adaptation ({@link MirrorChange}, {@link TargetLinkChange}, {@link TopologyChange}).</li>
 *   <li>Network configuration parameters supplied via {@link Properties}.</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * Action a = effector.setMirrors(8, 120);
 * Effect e = new Effect(a);
 * double deltaAL = e.getDeltaActiveLinks();
 * int deltaBW = e.getDeltaBandwidth(props);
 * int deltaTTW = e.getDeltaTimeToWrite();
 * int latency = e.getLatency();
 * }</pre>
 *
 * @param action The {@link Action} for which this effect is predicted.
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 * @implNote This class is tightly bound to the {@link Action} provided at construction
 * and uses its associated {@link Network} for all calculations.
 * @see Action
 * @see Effector
 * @since 1.0
 */
public record Effect(Action action) {

    /**
     * Creates a new {@code Effect} bound to a specific {@link Action}.
     *
     * @param action the action whose predicted impact will be computed (non-null)
     */
    public Effect {
    }

    /**
     * Calculates the predicted relative change in active links (0..1) caused by this action.
     *
     * @return the relative change in active links as a decimal fraction (positive = gain, negative = loss)
     */
    public double getDeltaActiveLinks() {
        TopologyStrategy topo = action.getNetwork().getTopologyStrategy();
        int m = action.getNetwork().getNumMirrors();
        int lpm = action.getNetwork().getNumTargetLinksPerMirror();
        if (action instanceof MirrorChange a) {
            return -1 * getDeltaActiveLinksForMirrorChange(a, topo, m, lpm);
        } else if (action instanceof TargetLinkChange a) {
            return -1 * getDeltaActiveLinksForTargetLinkChange(a, topo, m, lpm);
        } else {
            return -1 * getDeltaActiveLinksForTopologyChange((TopologyChange) action, topo, m, lpm);
        }
    }

    /**
     * Helper: Delta AL for a change in the number of mirrors.
     */
    private double getDeltaActiveLinksForMirrorChange(MirrorChange mc, TopologyStrategy topo, int m1, int lpm) {
        int m2 = mc.getNewMirrors();
        if (topo instanceof FullyConnectedTopology) {
            return 0;
        } else if (topo instanceof NConnectedTopology) {
            return (2.0 * lpm * (m2 - m1)) / ((m1 - 1) * (m2 - 1));
        } else {
            return (2.0 * (m2 - m1)) / (m1 * m2);
        }
    }

    /**
     * Helper: Delta AL for a change in target links per mirror.
     */
    private double getDeltaActiveLinksForTargetLinkChange(TargetLinkChange tlc, TopologyStrategy topo, int m, int lpm1) {
        int lpm2 = tlc.getNewLinksPerMirror();
        if (topo instanceof NConnectedTopology) {
            return (2.0 * (lpm1 - lpm2)) / (m - 1);
        }
        return 0;
    }

    /**
     * Helper: Delta AL for a change in topology strategy.
     */
    private double getDeltaActiveLinksForTopologyChange(TopologyChange tc, TopologyStrategy topo, int m, int lpm) {
        TopologyStrategy newTopology = tc.getNewTopology();
        if (topo instanceof FullyConnectedTopology && newTopology instanceof NConnectedTopology) {
            return (2 * lpm) / (double) (m - 1);
        } else if (topo instanceof FullyConnectedTopology && newTopology instanceof BalancedTreeTopologyStrategy) {
            return 1 - (2 / (double) m);
        } else if (topo instanceof NConnectedTopology && newTopology instanceof FullyConnectedTopology) {
            return (2 * lpm) / (double) (m - 1) - 1;
        } else if (topo instanceof NConnectedTopology && newTopology instanceof BalancedTreeTopologyStrategy) {
            return ((2 * m * (1 - lpm)) - 2) / (double) (m * m - m);
        } else if (topo instanceof BalancedTreeTopologyStrategy && newTopology instanceof FullyConnectedTopology) {
            return (2 / (double) m) - 1;
        } else if (topo instanceof BalancedTreeTopologyStrategy && newTopology instanceof NConnectedTopology) {
            return (2 * m * (lpm - 1) + 2) / (double) (m * m - m);
        }
        return 0;
    }

    /**
     * Calculates the predicted relative change in bandwidth (0..100) caused by this action.
     * <p>Uses the network's bandwidth history, topology prediction, and max bandwidth per link.</p>
     *
     * @param props network configuration properties containing at least "max_bandwidth"
     * @return the change in relative bandwidth (positive = reduction, negative = increase)
     */
    public int getDeltaBandwidth(Properties props) {
        int currentRelativeBandwidth = action.getNetwork().getBandwidthHistory()
                .get(action.getNetwork().getCurrentTimeStep());
        int maxBandwidthPerLink = Integer.parseInt(props.getProperty("max_bandwidth"));
        int predictedMaxTotalBandwidth = action.getNetwork().getTopologyStrategy()
                .getPredictedNumTargetLinks(action) * maxBandwidthPerLink;
        int predictedTotalBandwidth = action.getNetwork()
                .getPredictedBandwidth(action.getTime() + getLatency() - 1);
        int newRelativeBandwidth = 100 * predictedTotalBandwidth / predictedMaxTotalBandwidth;
        return currentRelativeBandwidth - newRelativeBandwidth;
    }

    /**
     * Calculates the predicted relative change in time-to-write (TTW) caused by this action.
     *
     * @return the change in TTW as a percentage (0..100)
     */
    public int getDeltaTimeToWrite() {
        int currentRelativeTTW = action.getNetwork().getTtwHistory()
                .get(action.getNetwork().getCurrentTimeStep());
        int m = action.getNetwork().getNumTargetMirrors();
        int lpm = action.getNetwork().getNumTargetLinksPerMirror();

        if (action instanceof TopologyChange tc && tc.getNewTopology() instanceof FullyConnectedTopology
                || !(action instanceof TopologyChange) && action.getNetwork().getTopologyStrategy() instanceof FullyConnectedTopology) {
            return 100 - currentRelativeTTW;
        } else if (action instanceof TopologyChange tc && tc.getNewTopology() instanceof BalancedTreeTopologyStrategy) {
            return getDeltaTimeToWriteForBalancedTrees(m, currentRelativeTTW, lpm);
        } else if (action instanceof MirrorChange mc && action.getNetwork().getTopologyStrategy() instanceof BalancedTreeTopologyStrategy) {
            m = mc.getNewMirrors();
        } else if (action instanceof TargetLinkChange tlc && tlc.getNetwork().getTopologyStrategy() instanceof BalancedTreeTopologyStrategy) {
            lpm = tlc.getNewLinksPerMirror();
        } else {
            return 0;
        }
        return getDeltaTimeToWriteForBalancedTrees(m, currentRelativeTTW, lpm);
    }

    /**
     * Helper: TTW delta for Balanced Tree topology changes.
     */
    private static int getDeltaTimeToWriteForBalancedTrees(int m, int currentRelativeTTW, int lpm) {
        int maxTTW = Math.round(m / 2f);
        if (maxTTW == 1) {
            return 100 - currentRelativeTTW;
        } else {
            return currentRelativeTTW -
                    (100 - 100 * ((int) (Math.round(Math.log((m + 1) / 2f) / Math.log(lpm))) - 1) / (maxTTW - 1));
        }
    }

    /**
     * Calculates the predicted latency (in simulation time steps) of applying this action.
     * <p>Latency is based on configured min/max times for startup, ready, and link activation phases.</p>
     *
     * @return the predicted adaptation latency in simulation ticks
     */
    public int getLatency() {
        int minStartup = Integer.parseInt(action.getNetwork().getProps().getProperty("startup_time_min"));
        int maxStartup = Integer.parseInt(action.getNetwork().getProps().getProperty("startup_time_max"));
        int minReady = Integer.parseInt(action.getNetwork().getProps().getProperty("ready_time_min"));
        int maxReady = Integer.parseInt(action.getNetwork().getProps().getProperty("ready_time_min")); // Note: seems like a bug (max = min)
        int minActive = Integer.parseInt(action.getNetwork().getProps().getProperty("link_activation_time_min"));
        int maxActive = Integer.parseInt(action.getNetwork().getProps().getProperty("link_activation_time_max"));
        int time = 0;

        if (action instanceof MirrorChange mc) {
            if (mc.getNewMirrors() > action.getNetwork().getNumTargetMirrors()) {
                time = Math.round((maxStartup - minStartup) / 2f
                        + (maxReady - minReady) / 2f
                        + (maxActive - minActive) / 2f);
            }
        } else if (action instanceof TargetLinkChange || action instanceof TopologyChange) {
            time = Math.round((maxActive - minActive) / 2f);
        }
        return time;
    }
}
