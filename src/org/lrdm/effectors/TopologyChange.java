package org.lrdm.effectors;

import org.lrdm.Network;
import org.lrdm.topologies.strategies.TopologyStrategy;

/**
 * # TopologyChange
 * <p>
 * Concrete {@link Action} that schedules a switch of the network’s {@link TopologyStrategy}
 * at a specified simulation time. Instances are typically created via
 * {@link Effector#setStrategy(TopologyStrategy, int)} and applied by
 * {@link Effector#timeStep(int)} when the scheduled tick is reached.
 *
 * <p>The predicted impact of this change—quality deltas for Active Links (AL),
 * Bandwidth (BW), Time-To-Write (TTW), and the adaptation latency—should be
 * attached using {@link #setEffect(Effect)} before the action is queued, so
 * optimizers can compare alternatives ahead of execution.</p>
 *
 * @apiNote If multiple topology changes are scheduled for the same tick {@code t},
 * the effector uses replacement semantics and keeps only the last one.
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 * @since 1.0
 * @see Action
 * @see Effector#setStrategy(TopologyStrategy, int)
 * @see Network#setTopologyStrategy(TopologyStrategy, int)
 */
public class TopologyChange extends Action {

    /**
     * The topology to activate when the action executes.
     */
    private final TopologyStrategy newTopology;

    /**
     * Creates a new topology-switch action.
     *
     * @param n            the target {@link Network}; must not be {@code null}
     * @param strategy     the {@link TopologyStrategy} to switch to at execution time
     * @param id           unique action identifier (e.g., from {@link org.lrdm.util.IDGenerator})
     * @param time         simulation time (tick) when the change shall be applied
     *
     * @implNote This action only represents intent. The actual mutation occurs when
     *           the effector calls {@link Network#setTopologyStrategy(TopologyStrategy, int)}
     *           at {@code time}.
     */
    public TopologyChange(Network n, TopologyStrategy strategy, int id, int time) {
        super(n, id, time);
        this.newTopology = strategy;
        /* retain any existing ctor logic here */
    }

    /**
     * Returns the topology that should be activated at this action’s execution time.
     *
     * @return the target {@link TopologyStrategy}
     */
    public TopologyStrategy getNewTopology() {
        return newTopology;
        /* keep your original body if different */
    }

    /**
     * Human-readable summary for logs and debugging.
     *
     * @return a concise description including id, time, and the target topology
     */
    @Override
    public String toString() {
        return "TopologyChange{id=" + getId()
                + ", time=" + getTime()
                + ", newTopology=" + newTopology
                + ", effect=" + getEffect()
                + "}";
        /* keep your existing formatting if you prefer */
    }
}
