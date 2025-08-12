package org.lrdm.effectors;

import org.lrdm.Network;

/**
 * Represents a scheduled reconfiguration request in the latency-aware remote
 * data mirroring simulator.
 * <p>
 * An {@code Action} is created by the {@link Effector} (or client code using it)
 * to be executed at a specific simulation time (see {@link #getTime()}).
 * Each action carries:
 * <ul>
 *   <li>a unique identifier ({@link #getId()}) for tracking and replacement,</li>
 *   <li>the simulation time at which it should be applied ({@link #getTime()}),</li>
 *   <li>the target {@link Network} instance it modifies ({@link #getNetwork()}),</li>
 *   <li>and an {@link Effect} object summarizing the predicted impact of the action
 *       on the system’s quality objectives (reliability via active links, cost via
 *       bandwidth, performance via TTW) and its adaptation latency ({@link #getEffect()}).</li>
 * </ul>
 *
 * @apiNote The {@code Effect} should normally be computed by the planner or effector
 *          before queuing, so that optimizers can compare alternative actions before
 *          execution.
 *
 * @implSpec Instances are immutable with respect to {@code id}, {@code time}, and
 *           {@code network}. The {@code effect} field may be set once before queuing.
 *           After an action is queued, treat it as read-only.
 *
 * @implNote The simulator treats {@code time} as a discrete step (t ≥ 0).
 *
 * @author Benjamin-Elias Probst {@code <benjamineliasprobst@gmail.com>}
 * @author Sebastian Götz {@code <sebastian.goetz1@tu-dresden.de>}
 * @since 1.0
 * @see Effector
 * @see Effect
 * @see Network
 */
public class Action {

    /** Monotonically increasing identifier (assigned by {@link org.lrdm.util.IDGenerator}). */
    private final int id;

    /** Simulation time step at which the action should be applied. */
    private final int time;

    /** Target network to which this action applies. */
    private final Network network;

    /**
     * Predicted impact of executing this action: deltas for Active Links (AL),
     * Bandwidth (BW), Time-To-Write (TTW), and adaptation latency (in timesteps).
     * <p>
     * May be {@code null} if the effect has not been computed yet.
     */
    private Effect effect;

    /**
     * Creates a new scheduled action.
     *
     * @param network the network instance to operate on (must not be {@code null})
     * @param id      unique identifier for this action
     * @param time    simulation time step when the action should be executed (t ≥ 0)
     * @throws NullPointerException if {@code network} is {@code null}
     * @throws IllegalArgumentException if {@code time} is negative
     * @apiNote Typically constructed by the {@link Effector} when planning reconfiguration.
     * @since 1.0
     */
    public Action(Network network, int id, int time) {
        this.network = network;
        this.id = id;
        this.time = time;
    }

    /**
     * Returns the unique identifier of this action.
     *
     * @return the globally unique id assigned to this action
     * @apiNote Useful for comparing, replacing, or removing scheduled actions.
     * @since 1.0
     */
    public int getId() {
        return id;
    }

    /**
     * Returns the simulation time step at which this action is scheduled to execute.
     *
     * @return the scheduled time step (t ≥ 0)
     * @since 1.0
     */
    public int getTime() {
        return time;
    }

    /**
     * Returns the target {@link Network} this action will modify.
     *
     * @return the non-null network instance associated with this action
     * @since 1.0
     */
    public Network getNetwork() {
        return network;
    }

    /**
     * Returns the predicted {@link Effect} describing quality deltas and adaptation latency.
     *
     * @return the predicted effect, or {@code null} if not yet assigned
     * @implNote Optimizers use this to reason about trade-offs before execution.
     * @since 1.0
     */
    public Effect getEffect() {
        return effect;
    }

    /**
     * Attaches or replaces the predicted {@link Effect} of this action.
     *
     * @param effect the predicted impact summary (non-null in normal operation)
     * @throws NullPointerException if {@code effect} is {@code null} in production use
     * @apiNote This is typically called by the {@link Effector} before queuing.
     * @since 1.0
     */
    public void setEffect(Effect effect) {
        this.effect = effect;
    }

    /**
     * Returns a human-readable description, helpful for logs and debugging.
     *
     * @return a string containing the id, scheduled time, and effect summary
     * @since 1.0
     */
    @Override
    public String toString() {
        return "Action{id=" + id +
                ", time=" + time +
                ", effect=" + effect +
                '}';
    }
}
