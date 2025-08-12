package org.lrdm.probes;

import org.lrdm.Network;

/**
 * Abstract base class for all RDM network probes.
 * <p>
 * Probes are observers attached to a {@link Network} to collect and optionally
 * print diagnostic or monitoring information at each simulation time step.
 * </p>
 * <p>
 * Concrete subclasses implement:
 * <ul>
 *   <li>{@link #update(int)} — collect measurements</li>
 *   <li>{@link #print(int)} — output measurements</li>
 * </ul>
 * </p>
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 * @since 1.0
 */
public abstract class Probe {

    /** The network being monitored by this probe. */
    protected final Network n;

    /**
     * Constructs a new probe for the given {@link Network}.
     *
     * @param n the network to observe; must not be {@code null}
     */
    protected Probe(Network n) {
        this.n = n;
    }

    /**
     * Called at each simulation time step to update the probe's internal state.
     *
     * @param simTime current simulation time step
     */
    public abstract void update(int simTime);

    /**
     * Called after {@link #update(int)} to output the probe's current state.
     *
     * @param simTime current simulation time step
     */
    public abstract void print(int simTime);
}
