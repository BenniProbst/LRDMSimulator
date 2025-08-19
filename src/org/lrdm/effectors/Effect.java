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
 * <p><b>Sign convention:</b> All delta methods return values where a <em>positive</em> number
 * indicates an <em>improvement</em> with respect to the goal:
 * <ul>
 *   <li>{@code getDeltaActiveLinks()} &gt; 0 —&gt; relative AL increases (more reliability)</li>
 *   <li>{@code getDeltaBandwidth(...)} &gt; 0 —&gt; relative BW decreases (lower cost)</li>
 *   <li>{@code getDeltaTimeToWrite()} &gt; 0 —&gt; relative TTW improves (faster)</li>
 * </ul>
 *
 * <p><b>Latency model:</b> Latency is computed from simulator configuration using
 * the <em>average</em> of each min/max range.
 *
 * <p><b>Topology awareness:</b> Calculations are topology-specific. For some unsupported
 * or non-applicable combinations, methods may return {@code 0}.
 *
 * @param action the {@link Action} for which this effect is predicted
 * @author Benjamin-Elias Probst &lt;benjamineliasprobst@gmail.com&gt;
 * @author Sebastian Götz &lt;sebastian.goetz1@tu-dresden.de&gt;
 * @since 1.0
 * @see Action
 * @see MirrorChange
 * @see TargetLinkChange
 * @see TopologyChange
 * @see TopologyStrategy
 */
public record Effect(Action action) {

    /**
     * Creates a new effect bound to the given {@link Action}.
     *
     * @param action the action this effect describes; must not be {@code null}
     * @throws NullPointerException if {@code action} is {@code null}
     */
    public Effect {
        // Intentionally no additional checks here; callers must provide a non-null action.
        // (If desired, add: Objects.requireNonNull(action, "action");
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

    /**
     * Computes AL delta for a mirror count change, given the topology and the current/target mirror counts.
     * Guards against division-by-zero for invalid inputs.
     *
     * @param mc   the mirror change action
     * @param topo the current topology
     * @param m1   current number of mirrors
     * @param lpm  number of target links per mirror
     * @return the raw delta (prior to outer sign convention)
     */
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

    /**
     * Computes AL delta for a change of target links per mirror under N-connected topology.
     * Guards against division-by-zero for invalid inputs.
     *
     * @param tlc  the target-link change action
     * @param topo the current topology
     * @param m    current number of mirrors
     * @param lpm1 current links per mirror
     * @return the raw delta (prior to outer sign convention)
     */
    private double getDeltaActiveLinksForTargetLinkChange(TargetLinkChange tlc, TopologyStrategy topo, int m, int lpm1) {
        int lpm2 = tlc.getNewLinksPerMirror();
        if (topo instanceof NConnectedTopology) {
            if ((m - 1) == 0) return 0; // guard
            return (2.0 * (lpm1 - lpm2)) / (m - 1);
        }
        return 0;
    }

    /**
     * Computes AL delta for a topology change, relative to the current topology.
     * Guards against division-by-zero for invalid inputs.
     *
     * @param tc   the topology change action
     * @param topo the current topology
     * @param m    current number of mirrors
     * @param lpm  current links per mirror
     * @return the raw delta (prior to outer sign convention)
     */
    private double getDeltaActiveLinksForTopologyChange(TopologyChange tc, TopologyStrategy topo, int m, int lpm) {
        TopologyStrategy newTopology = tc.getNewTopology();

        return switch (getTopologyTransition(topo, newTopology)) {
            case FULLY_TO_N -> calculateFullyToNConnectedDelta(m, lpm);
            case FULLY_TO_BALANCED -> calculateFullyToBalancedTreeDelta(m);
            case N_TO_FULLY -> calculateNToFullyConnectedDelta(m, lpm);
            case N_TO_BALANCED -> calculateNToBalancedTreeDelta(m, lpm);
            case BALANCED_TO_FULLY -> calculateBalancedToFullyConnectedDelta(m);
            case BALANCED_TO_N -> calculateBalancedToNConnectedDelta(m, lpm);
            default -> 0.0;
        };
    }

    /**
     * Determines the type of topology transition.
     */
    private TopologyTransition getTopologyTransition(TopologyStrategy from, TopologyStrategy to) {
        if (from instanceof FullyConnectedTopology && to instanceof NConnectedTopology) {
            return TopologyTransition.FULLY_TO_N;
        }
        if (from instanceof FullyConnectedTopology && to instanceof BalancedTreeTopologyStrategy) {
            return TopologyTransition.FULLY_TO_BALANCED;
        }
        if (from instanceof NConnectedTopology && to instanceof FullyConnectedTopology) {
            return TopologyTransition.N_TO_FULLY;
        }
        if (from instanceof NConnectedTopology && to instanceof BalancedTreeTopologyStrategy) {
            return TopologyTransition.N_TO_BALANCED;
        }
        if (from instanceof BalancedTreeTopologyStrategy && to instanceof FullyConnectedTopology) {
            return TopologyTransition.BALANCED_TO_FULLY;
        }
        if (from instanceof BalancedTreeTopologyStrategy && to instanceof NConnectedTopology) {
            return TopologyTransition.BALANCED_TO_N;
        }
        return TopologyTransition.UNSUPPORTED;
    }

    /**
     * Calculates delta for transition from FullyConnected to NConnected topology.
     */
    private double calculateFullyToNConnectedDelta(int m, int lpm) {
        if ((m - 1) == 0) return 0; // guard
        return (2 * lpm) / (double) (m - 1);
    }

    /**
     * Calculates delta for transition from FullyConnected to BalancedTree topology.
     */
    private double calculateFullyToBalancedTreeDelta(int m) {
        if (m == 0) return 0; // guard
        return 1 - (2 / (double) m);
    }

    /**
     * Calculates delta for transition from NConnected to FullyConnected topology.
     */
    private double calculateNToFullyConnectedDelta(int m, int lpm) {
        if ((m - 1) == 0) return 0; // guard
        return (2 * lpm) / (double) (m - 1) - 1;
    }

    /**
     * Calculates delta for transition from NConnected to BalancedTree topology.
     */
    private double calculateNToBalancedTreeDelta(int m, int lpm) {
        if ((m * m - m) == 0) return 0; // guard
        return ((2 * m * (1 - lpm)) - 2) / (double) (m * m - m);
    }

    /**
     * Calculates delta for transition from BalancedTree to FullyConnected topology.
     */
    private double calculateBalancedToFullyConnectedDelta(int m) {
        if (m == 0) return 0; // guard
        return (2 / (double) m) - 1;
    }

    /**
     * Calculates delta for transition from BalancedTree to NConnected topology.
     */
    private double calculateBalancedToNConnectedDelta(int m, int lpm) {
        if ((m * m - m) == 0) return 0; // guard
        return (2 * m * (lpm - 1) + 2) / (double) (m * m - m);
    }

    /**
     * Enum representing different topology transition types.
     */
    private enum TopologyTransition {
        FULLY_TO_N,
        FULLY_TO_BALANCED,
        N_TO_FULLY,
        N_TO_BALANCED,
        BALANCED_TO_FULLY,
        BALANCED_TO_N,
        UNSUPPORTED
    }

    /**
     * Returns the change of the <em>relative bandwidth</em> (BW) introduced by the action, evaluated
     * at {@code action.getTime() + getLatency() - 1}. Positive values indicate an improvement
     * (i.e., lower relative bandwidth).
     *
     * <p>Relative BW is computed as {@code totalBandwidth / predictedMaxTotalBandwidth}, both in percent.
     *
     * @param props simulator/system properties (requires at least {@code max_bandwidth})
     * @return the change in percent (0..100), where positive means improvement (lower BW)
     * @throws IllegalStateException if required properties are missing or malformed
     * @see #getLatency()
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
     *   <li>For the fully connected topology, TTW is considered 100% (best case).</li>
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

    /**
     * Computes TTW delta for balanced tree topology based on an estimated tree depth.
     * Uses {@code depth ≈ round(log((m+1)/2) / log(lpm))} and normalizes against {@code maxTTW = round(m/2)}.
     *
     * @param m                 number of mirrors
     * @param currentRelativeTTW current relative TTW at evaluation time
     * @param lpm               target links per mirror (branching factor); must be {@code >= 2} to be meaningful
     * @return TTW delta in percent (0..100), positive indicates improvement
     */
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
     * <ul>
     *   <li>Adding mirrors: {@code avg(startup) + avg(ready) + avg(link_activation)}</li>
     *   <li>Removing mirrors: {@code 0}</li>
     *   <li>Link/topology changes: {@code avg(link_activation)}</li>
     * </ul>
     *
     * <p>All averages are computed as {@code (min + max) / 2}.
     *
     * @return the latency in simulation timesteps (never negative)
     * @throws IllegalStateException if required properties are missing or malformed
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

    /**
     * Helper that maps the action type to its latency formula, using provided averages.
     *
     * @param avgStartup average startup time
     * @param avgReady   average ready time
     * @param avgActive  average link activation time
     * @return the predicted latency (may be zero for removals)
     */
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

    /**
     * Retrieves a required integer property.
     *
     * @param p   properties bag
     * @param key property key
     * @return parsed integer value
     * @throws IllegalStateException if the property is missing or cannot be parsed as integer
     */
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
