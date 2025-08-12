package org.lrdm.effectors;

import org.lrdm.topologies.strategies.BalancedTreeTopologyStrategy;
import org.lrdm.topologies.strategies.FullyConnectedTopology;
import org.lrdm.topologies.strategies.NConnectedTopology;
import org.lrdm.topologies.strategies.TopologyStrategy;

import java.util.Properties;

/**
 * Represents the predicted impact (the “effect”) of applying an adaptation {@link Action}.
 * It provides deltas for the three goals—relative Active Links (AL), relative Bandwidth (BW),
 * and relative Time-To-Write (TTW)—as well as the adaptation latency (in simulation timesteps).
 *
 * <p><b>Sign convention:</b>
 * All delta methods return values where a <em>positive</em> number indicates an
 * <em>improvement</em> with respect to the goal:
 * <ul>
 *   <li>{@code getDeltaActiveLinks()} &gt; 0 —&gt; relative AL increases (more reliability)</li>
 *   <li>{@code getDeltaBandwidth(...)} &gt; 0 —&gt; relative BW decreases (lower cost)</li>
 *   <li>{@code getDeltaTimeToWrite()} &gt; 0 —&gt; relative TTW improves (faster)</li>
 * </ul>
 *
 * <p>Latency is computed from the simulator configuration (min/max startup/ready/activation times)
 * using the <em>average</em> of each min/max range.
 *
 * @param action The action for which this effect is predicted.
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 * @since 1.0
 */
public record Effect(Action action) {
    /**
     * Creates a new effect bound to the given {@link Action}.
     *
     * @param action the action this effect describes; must not be {@code null}
     */
    public Effect {
    }

    /**
     * Returns the change of the <em>relative active links</em> (AL) introduced by performing the action.
     * Positive values indicate an improvement (i.e., more relative active links).
     *
     * @return the change in percent (0..1), where positive means improvement
     */
    public double getDeltaActiveLinks() {
        TopologyStrategy topo = action.getNetwork().getTopologyStrategy();
        int m = action.getNetwork().getNumMirrors();
        int lpm = action.getNetwork().getNumTargetLinksPerMirror();

        if (action instanceof MirrorChange a) {
            // Keep existing semantics: negate helper so positive -> improvement
            return -1 * getDeltaActiveLinksForMirrorChange(a, topo, m, lpm);
        } else if (action instanceof TargetLinkChange a) {
            return -1 * getDeltaActiveLinksForTargetLinkChange(a, topo, m, lpm);
        } else {
            return -1 * getDeltaActiveLinksForTopologyChange((TopologyChange) action, topo, m, lpm);
        }
    }

    private double getDeltaActiveLinksForMirrorChange(MirrorChange mc, TopologyStrategy topo, int m1, int lpm) {
        int m2 = mc.getNewMirrors();
        if (topo instanceof FullyConnectedTopology) {
            return 0;
        } else if (topo instanceof NConnectedTopology) {
            // guard to avoid division by zero
            if ((m1 - 1) == 0 || (m2 - 1) == 0) return 0;
            return (2.0 * lpm * (m2 - m1)) / ((m1 - 1) * (m2 - 1));
        } else {
            // Balanced tree case: guard to avoid division by zero
            if (m1 == 0 || m2 == 0) return 0;
            return (2.0 * (m2 - m1)) / (m1 * m2);
        }
    }

    private double getDeltaActiveLinksForTargetLinkChange(TargetLinkChange tlc, TopologyStrategy topo, int m, int lpm1) {
        int lpm2 = tlc.getNewLinksPerMirror();
        if (topo instanceof NConnectedTopology) {
            if ((m - 1) == 0) return 0; // guard
            return (2.0 * (lpm1 - lpm2)) / (m - 1);
        }
        return 0;
    }

    private double getDeltaActiveLinksForTopologyChange(TopologyChange tc, TopologyStrategy topo, int m, int lpm) {
        TopologyStrategy newTopology = tc.getNewTopology();
        if (topo instanceof FullyConnectedTopology && newTopology instanceof NConnectedTopology) {
            if ((m - 1) == 0) return 0; // guard
            return (2 * lpm) / (double) (m - 1);
        } else if (topo instanceof FullyConnectedTopology && newTopology instanceof BalancedTreeTopologyStrategy) {
            if (m == 0) return 0; // guard
            return 1 - (2 / (double) m);
        } else if (topo instanceof NConnectedTopology && newTopology instanceof FullyConnectedTopology) {
            if ((m - 1) == 0) return 0; // guard
            return (2 * lpm) / (double) (m - 1) - 1;
        } else if (topo instanceof NConnectedTopology && newTopology instanceof BalancedTreeTopologyStrategy) {
            if ((m * m - m) == 0) return 0; // guard
            return ((2 * m * (1 - lpm)) - 2) / (double) (m * m - m);
        } else if (topo instanceof BalancedTreeTopologyStrategy && newTopology instanceof FullyConnectedTopology) {
            if (m == 0) return 0; // guard
            return (2 / (double) m) - 1;
        } else if (topo instanceof BalancedTreeTopologyStrategy && newTopology instanceof NConnectedTopology) {
            if ((m * m - m) == 0) return 0; // guard
            return (2 * m * (lpm - 1) + 2) / (double) (m * m - m);
        }
        return 0;
    }

    /**
     * Returns the change of the <em>relative bandwidth</em> (BW) introduced by the action, evaluated
     * at {@code action.getTime() + getLatency() - 1}. Positive values indicate an improvement
     * (i.e., lower relative bandwidth).
     *
     * <p>Relative BW is computed as {@code totalBandwidth / predictedMaxTotalBandwidth}.
     *
     * @param props simulator/system properties (needs at least {@code max_bandwidth})
     * @return the change in percent (0..100), where positive means improvement (lower BW)
     * @throws IllegalStateException if required, properties are missing or malformed
     */
    public int getDeltaBandwidth(Properties props) {
        var net = action.getNetwork();
        int now = net.getCurrentTimeStep();

        int currentRelativeBandwidth = net.getBandwidthHistory().get(now);

        int maxBandwidthPerLink = requireIntProp(props, "max_bandwidth");

        int predictedMaxTotalBandwidth =
                net.getTopologyStrategy().getPredictedNumTargetLinks(action) * maxBandwidthPerLink;

        // Clamp evaluation time to be at least "now" to avoid indexing t-1 when the latency is zero.
        int evalTime = Math.max(now, action.getTime() + getLatency() - 1);
        int predictedTotalBandwidth = net.getPredictedBandwidth(evalTime);

        if (predictedMaxTotalBandwidth <= 0) {
            // Nothing to compare against (e.g., no links predicted) -> define no change
            return 0;
        }

        int newRelativeBandwidth = 100 * predictedTotalBandwidth / predictedMaxTotalBandwidth;
        // positive if current > new -> improvement (lower BW)
        return currentRelativeBandwidth - newRelativeBandwidth;
    }

    /**
     * Returns the change of the <em>relative time to write</em> (TTW) introduced by the action.
     * Positive values indicate an improvement (i.e., faster TTW).
     *
     * <p>Notes:
     * <ul>
     *   <li>For the fully connected topology, TTW is considered 100% (the best case).</li>
     *   <li>For the balanced tree topology, TTW is estimated via the tree depth.</li>
     *   <li>Other combinations are currently returned as 0 (subject to future work).</li>
     * </ul>
     *
     * @return the change in percent (0..100), where positive means improvement (faster TTW)
     */
    public int getDeltaTimeToWrite() {
        int currentRelativeTTW = action.getNetwork().getTtwHistory().get(action.getNetwork().getCurrentTimeStep());
        int m = action.getNetwork().getNumTargetMirrors();
        int lpm = action.getNetwork().getNumTargetLinksPerMirror();

        // Fully connected now or after the topology change => 100% (fastest, one hop)
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
            // other combinations are subject to future work
            return 0;
        }
        return getDeltaTimeToWriteForBalancedTrees(m, currentRelativeTTW, lpm);
    }

    private static int getDeltaTimeToWriteForBalancedTrees(int m, int currentRelativeTTW, int lpm) {
        int maxTTW = Math.round(m / 2f);
        if (maxTTW == 1) {
            // Only one hop possible => fully connected behavior
            return 100 - currentRelativeTTW;
        }
        // lpm must be >= 2 to define a meaningful branching factor for depth-based estimate.
        if (lpm <= 1) return 0;

        double depth = Math.log((m + 1) / 2f) / Math.log(lpm);
        int term = (int) (Math.round(depth) - 1);
        return currentRelativeTTW - (100 - 100 * term / (maxTTW - 1));
    }

    /**
     * Returns the predicted adaptation latency (in simulation timesteps) for this action.
     * For adding mirrors, latency is {@code avg(startup) + avg(ready) + avg(link_activation)}.
     * For removing mirrors, latency is 0. For link/topology changes, latency is {@code avg(link_activation)}.
     *
     * <p>All averages are computed as {@code (min + max) / 2}.
     *
     * @return the latency in simulation timesteps (never negative)
     * @throws IllegalStateException if required, properties are missing or malformed
     */
    public int getLatency() {
        Properties props = action.getNetwork().getProps();

        int minStartup = requireIntProp(props, "startup_time_min");
        int maxStartup = requireIntProp(props, "startup_time_max");
        int minReady = requireIntProp(props, "ready_time_min");
        int maxReady = requireIntProp(props, "ready_time_max"); // fixed key
        int minActive = requireIntProp(props, "link_activation_time_min");
        int maxActive = requireIntProp(props, "link_activation_time_max");

        float avgStartup = (minStartup + maxStartup) / 2f;
        float avgReady = (minReady + maxReady) / 2f;
        float avgActive = (minActive + maxActive) / 2f;

        int time = getTime(avgStartup, avgReady, avgActive);
        return Math.max(0, time);
    }

    private int getTime(float avgStartup, float avgReady, float avgActive) {
        int time = 0;
        if (action instanceof MirrorChange mc) {
            // adding mirrors -> need startup + ready + activation
            // removing mirrors -> immediate (0)
            if (mc.getNewMirrors() > action.getNetwork().getNumTargetMirrors()) {
                time = Math.round(avgStartup + avgReady + avgActive);
            }
        } else if (action instanceof TargetLinkChange || action instanceof TopologyChange) {
            // re-initialize links; mirrors assumed up/ready already
            time = Math.round(avgActive);
        }
        return time;
    }

    // ---- internal helpers ---------------------------------------------------

    private static int requireIntProp(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null) {
            throw new IllegalStateException("Missing required property: " + key);
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Property " + key + " must be an integer, got: " + v, ex);
        }
    }
}
