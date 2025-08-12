package org.lrdm.effectors;

import org.lrdm.Network;

/**
 * # TargetLinkChange
 * <p>
 * Concrete {@link Action} that schedules a change to the number of targeted links per mirror
 * in the network at a specified simulation time.
 * This is one of the adaptation primitives supported by the latency-aware remote data mirroring
 * simulator and is typically created via {@link Effector#setTargetLinksPerMirror(int, int)}.
 *
 * <p>The {@link Effect} associated with this action should summarize the expected quality
 * changes (Active Links, Bandwidth, Time-To-Write) and adaptation latency for this modification,
 * so that planners/optimizers can compare trade-offs before execution.</p>
 *
 * @apiNote If multiple target link changes are scheduled for the same tick {@code t},
 * the effector will keep only the latest one.
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 * @since 1.0
 * @see Action
 * @see Effector#setTargetLinksPerMirror(int, int)
 * @see Network#setNumTargetedLinksPerMirror(int, int)
 */
public class TargetLinkChange extends Action {

    /**
     * New number of target links per mirror that should be set when this action executes.
     */
    private final int newLinksPerMirror;

    /**
     * Creates a new scheduled action to change the number of targeted links per mirror.
     *
     * @param n                 the target {@link Network}; must not be {@code null}
     * @param id                unique identifier for this action (e.g., from {@link org.lrdm.util.IDGenerator})
     * @param time              simulation time (tick) when the change will be applied
     * @param newLinksPerMirror the desired number of target links per mirror after this action
     */
    public TargetLinkChange(Network n, int id, int time, int newLinksPerMirror) {
        super(n, id, time);
        this.newLinksPerMirror = newLinksPerMirror;
    }

    /**
     * Returns the desired number of target links per mirror after this change.
     *
     * @return the new number of targeted links per mirror
     */
    public int getNewLinksPerMirror() {
        return newLinksPerMirror;
    }

    /**
     * Human-readable summary for logs and debugging.
     *
     * @return a concise string with id, time, and the target number of links per mirror
     */
    @Override
    public String toString() {
        return "TargetLinkChange{id=" + getId()
                + ", time=" + getTime()
                + ", newLinksPerMirror=" + newLinksPerMirror
                + ", effect=" + getEffect()
                + "}";
    }
}
