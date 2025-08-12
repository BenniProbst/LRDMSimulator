package org.lrdm.effectors;

import org.lrdm.Network;
import org.lrdm.topologies.strategies.TopologyStrategy;
import org.lrdm.util.IDGenerator;

import java.util.HashMap;
import java.util.Map;

/**
 * # Effector
 * <p>
 * Central scheduling component that collects and triggers reconfiguration requests
 * for a latency-aware remote data mirroring network. The {@code Effector} allows
 * client code (e.g., planners/optimizers) to enqueue {@link Action}s to:
 *
 * <ul>
 *   <li>change the number of mirrors ({@link #setMirrors(int, int)}),</li>
 *   <li>switch the topology strategy ({@link #setStrategy(TopologyStrategy, int)}),</li>
 *   <li>adjust the targeted links per mirror ({@link #setTargetLinksPerMirror(int, int)}).</li>
 * </ul>
 *
 * Actions are keyed by the simulation time (tick) at which they should be applied.
 * On each time step, {@link #timeStep(int)} applies any pending changes for that tick
 * by delegating to the underlying {@link Network}.
 *
 * <p><b>Usage</b></p>
 * <pre>{@code
 * Effector eff = sim.getEffector();
 * // schedule a series of changes
 * Action a1 = eff.setMirrors(10, 120);
 * Action a2 = eff.setTargetLinksPerMirror(3, 140);
 * TopologyChange a3 = eff.setStrategy(new NConnectedTopology(), 160);
 *
 * // optionally, remove/replace before they trigger
 * eff.removeAction(a2);
 *
 * // simulation loop
 * for (int t = 1; t <= simTime; t++) {
 *     eff.timeStep(t);  // applies any change scheduled for t
 *     sim.runStep(t);
 * }
 * }</pre>
 *
 * @apiNote If multiple actions of the same type are scheduled for the same tick,
 *          the most recently scheduled one is retained in the internal map (by key overwrite).
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 * @since 1.0
 * @see Action
 * @see MirrorChange
 * @see TargetLinkChange
 * @see TopologyChange
 * @see Network
 */
public class Effector {

    /** The target network to which scheduled actions will be applied. */
    private final Network n;

    /** Map of scheduled mirror-count changes keyed by simulation time (sim_time → action). */
    private final Map<Integer, MirrorChange> setMirrorChanges;

    /** Map of scheduled topology switches keyed by simulation time (sim_time → action). */
    private final Map<Integer, TopologyChange> setStrategyChanges;

    /** Map of scheduled target-links-per-mirror changes keyed by simulation time (sim_time → action). */
    private final Map<Integer, TargetLinkChange> setTargetedLinkChanges;

    /**
     * Creates a new effector bound to a specific {@link Network}.
     *
     * @param n the network to operate on; must not be {@code null}
     */
    public Effector(Network n) {
        this.n = n;
        setMirrorChanges = new HashMap<>();
        setStrategyChanges = new HashMap<>();
        setTargetedLinkChanges = new HashMap<>();
    }

    /**
     * Schedules a change of the number of mirrors at simulation time {@code t}.
     *
     * @param m the desired number of mirrors
     * @param t the simulation time (tick) when the change should be applied
     * @return a newly created {@link Action} (specifically a {@link MirrorChange}) representing this adaptation
     * @implNote If an action of the same type was already scheduled for {@code t},
     *           it will be replaced by this call (map overwrite).
     */
    public Action setMirrors(int m, int t) {
        MirrorChange a = new MirrorChange(n, IDGenerator.getInstance().getNextID(), t, m);
        setMirrorChanges.put(t, a);
        return a;
    }

    /**
     * Schedules a topology strategy switch at simulation time {@code t}.
     *
     * @param strategy the {@link TopologyStrategy} to switch to
     * @param t        the simulation time (tick) when the switch should occur
     * @return a newly created {@link TopologyChange} action representing this adaptation
     * @implNote If a topology change was already scheduled for {@code t},
     *           it will be replaced by this call (map overwrite).
     */
    public TopologyChange setStrategy(TopologyStrategy strategy, int t) {
        TopologyChange change = new TopologyChange(n, strategy, IDGenerator.getInstance().getNextID(), t);
        setStrategyChanges.put(t, change);
        return change;
    }

    /**
     * Schedules a change to the number of targeted links per mirror at simulation time {@code t}.
     *
     * @param numTargetedLinks the new target links per mirror
     * @param t                the simulation time (tick) when the change should be applied
     * @return a newly created {@link TargetLinkChange} action representing this adaptation
     * @implNote If a target-link change was already scheduled for {@code t},
     *           it will be replaced by this call (map overwrite).
     */
    public TargetLinkChange setTargetLinksPerMirror(int numTargetedLinks, int t) {
        TargetLinkChange tlc = new TargetLinkChange(n, IDGenerator.getInstance().getNextID(), t, numTargetedLinks);
        setTargetedLinkChanges.put(t, tlc);
        return tlc;
    }

    /**
     * Removes a previously scheduled {@link Action} from the queue if it is still pending.
     *
     * @param a the action to remove
     * @implNote Removal is keyed by the action's scheduled time; if an action for the same time
     *           was subsequently overwritten by another action of the same type, this call
     *           will remove only if the map still holds the same instance {@code a}.
     */
    public void removeAction(Action a) {
        if (a instanceof MirrorChange) {
            setMirrorChanges.remove(a.getTime(), a);
        } else if (a instanceof TopologyChange) {
            setStrategyChanges.remove(a.getTime(), a);
        } else if (a instanceof TargetLinkChange) {
            setTargetedLinkChanges.remove(a.getTime(), a);
        }
    }

    /**
     * Applies any scheduled adaptations for the given simulation time tick by delegating to the {@link Network}.
     *
     * <p>The application order is: topology change → mirror count change → target links per mirror.
     * This mirrors the existing implementation and ensures deterministic behavior.</p>
     *
     * @param t current simulation time (tick)
     * @implNote This method is side-effecting and should be called exactly once per simulation tick
     *           before/after the network step, depending on your simulation semantics.
     */
    public void timeStep(int t) {
        if (setStrategyChanges.get(t) != null) {
            n.setTopologyStrategy(setStrategyChanges.get(t).getNewTopology(), t);
        }
        if (setMirrorChanges.get(t) != null) {
            n.setNumMirrors(setMirrorChanges.get(t).getNewMirrors(), t);
        }
        if (setTargetedLinkChanges.get(t) != null) {
            n.setNumTargetedLinksPerMirror(setTargetedLinkChanges.get(t).getNewLinksPerMirror(), t);
        }
    }
}
