package org.lrdm.effectors;

import org.lrdm.Network;

/**
 * # MirrorChange
 * <p>
 * Concrete {@link Action} that schedules a change of the total mirror count
 * in the network at a given simulation time. This request is created via
 * {@link Effector#setMirrors(int, int)} and applied by {@link Effector#timeStep(int)}
 * when the scheduled tick is reached.
 *
 * <p>The predicted impact (quality deltas for Active Links/Bandwidth/TTW and
 * adaptation latency) should be attached using {@link #setEffect(Effect)} before
 * the action is queued, allowing optimizers to compare alternatives.</p>
 *
 * @apiNote If multiple mirror changes are scheduled for the same time {@code t},
 * the effector keeps only the last one (replacement semantics).
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 * @since 1.0
 * @see Action
 * @see Effector#setMirrors(int, int)
 * @see Network#setNumMirrors(int, int)
 */
public class MirrorChange extends Action {

    /**
     * Target the number of mirrors that should be enforced when the action executes.
     */
    private final int newMirrors;

    /**
     * Creates a new mirror-count change action.
     *
     * @param n           the target {@link Network}; must not be {@code null}
     * @param id          unique action identifier (typically from {@link org.lrdm.util.IDGenerator})
     * @param time        simulation time (tick) when the change should be applied
     * @param newMirrors  total number of mirrors to set at {@code time}
     *
     * @implNote The action is <em>scheduled</em> only; the actual mutation is performed by
     *           the effector calling {@link Network#setNumMirrors(int, int)} at {@code time}.
     */
    public MirrorChange(Network n, int id, int time, int newMirrors) {
        super(n, id, time);
        this.newMirrors = newMirrors;
        /* existing body (if any additional logic) */
    }

    /**
     * Returns the requested total number of mirrors to set when this action executes.
     *
     * @return the target mirror count
     */
    public int getNewMirrors() {
        return newMirrors;
        /* existing body if different */
    }

    /**
     * Human-readable summary, useful in logs and debugging.
     *
     * @return a concise description containing id, time, and the target mirror count
     */
    @Override
    public String toString() {
        return "MirrorChange{id=" + getId()
                + ", time=" + getTime()
                + ", newMirrors=" + newMirrors
                + ", effect=" + getEffect()
                + "}";
        /* keep your existing formatting if you prefer */
    }
}
