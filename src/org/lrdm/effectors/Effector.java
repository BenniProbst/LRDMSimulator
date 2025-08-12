package org.lrdm.effectors;

import org.lrdm.Network;
import org.lrdm.topologies.strategies.TopologyStrategy;
import org.lrdm.util.IDGenerator;

import java.util.HashMap;
import java.util.Map;

/**
 * # Effector
 * <p>
 * Queues and applies reconfiguration requests for the latency-aware remote
 * data mirroring simulator. The {@code Effector} exposes convenience methods
 * to schedule changes to:
 *
 * <ul>
 *   <li>the number of mirrors ({@link #setMirrors(int, int)}),</li>
 *   <li>the topology strategy ({@link #setStrategy(TopologyStrategy, int)}), and</li>
 *   <li>the number of targeted links per mirror ({@link #setTargetLinksPerMirror(int, int)}).</li>
 * </ul>
 *
 * Each scheduling method creates an {@link Action} (a concrete subtype such as
 * {@link MirrorChange}, {@link TopologyChange}, or {@link TargetLinkChange}),
 * associates it with a simulation time {@code t}, and stores it internally.
 * When {@link #timeStep(int)} is called with {@code t}, the effector triggers
 * the requested change on the underlying {@link Network}.
 *
 * <p><b>Action identity &amp; replacement:</b>
 * There is at most one queued action of a given type per simulation time step.
 * If you schedule a second action of the same typing at the same {@code t}, it will
 * overwrite the previous one in the internal map (later call wins).
 *
 * <p><b>Removing scheduled actions:</b>
 * {@link #removeAction(Action)} removes a previously queued action by matching
 * both its key (time) and the specific instance (value). This is a no-op if the
 * action is not currently queued.
 *
 * <p><b>Threading:</b>
 * This class is not synchronized. External synchronization is required if it is
 * accessed from multiple threads concurrently.
 *
 * @apiNote Typical usage is: construct {@code Effector} from a {@code Network},
 * schedule one or more actions, and invoke {@link #timeStep(int)} from the
 * simulation driver each tick to apply due actions.
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 * @since 1.0
 * @see Action
 * @see MirrorChange
 * @see TopologyChange
 * @see TargetLinkChange
 * @see Network
 */
public class Effector {
    /** Backing network to mutate when actions become due. */
    private final Network n;

    /** Pending mirror-count changes, keyed by simulation time (sim_time → action). */
    private final Map<Integer, MirrorChange> setMirrorChanges;

    /** Pending topology changes, keyed by simulation time (sim_time → action). */
    private final Map<Integer, TopologyChange> setStrategyChanges;

    /** Pending target-links-per-mirror changes, keyed by simulation time (sim_time → action). */
    private final Map<Integer, TargetLinkChange> setTargetedLinkChanges;

    /**
     * Constructs an effector bound to a {@link Network}.
     *
     * @param n the target network; must not be {@code null}
     * @implNote This class keeps only a reference; the network’s lifetime must
     *           outlive the effector or be managed by the caller.
     */
    public Effector(Network n) {
        this.n = n;
        setMirrorChanges = new HashMap<>();
        setStrategyChanges = new HashMap<>();
        setTargetedLinkChanges = new HashMap<>();
    }

    /**
     * Schedules a change to the number of mirrors at simulation time {@code t}.
     *
     * <p>If an existing mirror-change is already scheduled for the same time {@code t},
     * it will be replaced by this new request.</p>
     *
     * @param m the desired number of mirrors to enforce at time {@code t}
     * @param t the simulation time (tick) when this change should be applied
     * @return the concrete {@link MirrorChange} action that was created and queued
     * @implNote The returned action contains a unique ID provided by {@link IDGenerator}.
     *           Callers may attach an {@link Effect} to the returned action
     *           (via {@link Action#setEffect(Effect)}) before the step becomes due.
     */
    public Action setMirrors(int m, int t) {
        MirrorChange a = new MirrorChange(n, IDGenerator.getInstance().getNextID(), t, m);
        setMirrorChanges.put(t, a);
        return a;
    }

    /**
     * Schedules a topology switch to {@code strategy} at simulation time {@code t}.
     *
     * <p>If an existing topology-change is already scheduled for the same time {@code t},
     * it will be replaced by this new request.</p>
     *
     * @param strategy the {@link TopologyStrategy} to activate at time {@code t}
     * @param t        the simulation time (tick) when this change should be applied
     * @return the concrete {@link TopologyChange} action that was created and queued
     * @implNote The actual network mutation is performed inside {@link #timeStep(int)}
     *           using {@link Network#setTopologyStrategy(TopologyStrategy, int)}.
     */
    public TopologyChange setStrategy(TopologyStrategy strategy, int t) {
        TopologyChange change = new TopologyChange(n, strategy, IDGenerator.getInstance().getNextID(), t);
        setStrategyChanges.put(t, change);
        return change;
    }

    /**
     * Schedules a change to the number of targeted links per mirror at time {@code t}.
     *
     * <p>If an existing target-link-change is already scheduled for the same time {@code t},
     * it will be replaced by this new request.</p>
     *
     * @param numTargetedLinks the new target-links-per-mirror value to enforce
     * @param t                the simulation time (tick) when this change should be applied
     * @return the concrete {@link TargetLinkChange} action that was created and queued
     * @implNote The actual network mutation is performed inside {@link #timeStep(int)}
     *           using {@link Network#setNumTargetedLinksPerMirror(int, int)}.
     */
    public TargetLinkChange setTargetLinksPerMirror(int numTargetedLinks, int t) {
        TargetLinkChange tlc = new TargetLinkChange(n, IDGenerator.getInstance().getNextID(), t, numTargetedLinks);
        setTargetedLinkChanges.put(t, tlc);
        return tlc;
    }

    /**
     * Removes a previously queued {@link Action} from the effector.
     *
     * <p>This method looks up the internal map by {@link Action#getTime()} and removes
     * the mapping only if it is associated with the exact same {@code Action} instance.
     * Passing an action that is not currently queued is a no-op.</p>
     *
     * @param a the {@link Action} to remove; ignored if {@code null} or not present
     * @implNote Removal uses {@code Map.remove(key, value)} semantics for safety.
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
     * Applies any actions that are due at simulation time {@code t}.
     *
     * <p>The application order at a given tick is:
     * <ol>
     *   <li>Topology change (if present),</li>
     *   <li>Mirror-count change (if present),</li>
     *   <li>Target-links-per-mirror change (if present).</li>
     * </ol>
     * This order is intentional, so the topology exists before changing the population
     * and link targets.</p>
     *
     * @param t the current simulation time (tick)
     * @implNote If no action is queued for {@code t}, this method is a no-op.
     * @see Network#setTopologyStrategy(TopologyStrategy, int)
     * @see Network#setNumMirrors(int, int)
     * @see Network#setNumTargetedLinksPerMirror(int, int)
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
