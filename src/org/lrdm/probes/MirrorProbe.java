package org.lrdm.probes;

import org.lrdm.Mirror;
import org.lrdm.Network;

import java.text.NumberFormat;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link Probe} implementation for monitoring mirrors in an RDM {@link Network}.
 * <p>
 * This probe tracks and reports:
 * <ul>
 *     <li>Total number of mirrors</li>
 *     <li>Number of mirrors in the "ready" state</li>
 *     <li>Target number of mirrors</li>
 *     <li>The ratio between ready mirrors and target mirrors (0..1)</li>
 * </ul>
 * </p>
 * <p>
 * Intended for runtime simulation diagnostics and logging purposes.
 * </p>
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 * @since 1.0
 */
public class MirrorProbe extends Probe {

    /** Ratio of ready mirrors to target mirrors (0..1). */
    private double mirrorRatio;

    /**
     * Creates a new mirror probe bound to a given {@link Network}.
     *
     * @param n the network to observe; must not be {@code null}
     */
    public MirrorProbe(Network n) {
        super(n);
    }

    /**
     * Updates the probe's internal state for a simulation time step.
     * <p>
     * This calculates the {@code mirrorRatio} as:
     * {@code readyMirrors / targetMirrors}.
     * </p>
     *
     * @param simTime current simulation time step
     */
    @Override
    public void update(int simTime) {
        // Compute ratio between ready and target mirrors
        mirrorRatio = (double) n.getNumReadyMirrors() / (double) n.getNumTargetMirrors();
    }

    /**
     * Logs the current mirror statistics to the console.
     * <p>
     * Output format:
     * {@code [time] [Mirror] All/Ready/Target/Ratio: all | ready | target | ratio}
     * </p>
     *
     * @param simTime current simulation time step
     */
    @Override
    public void print(int simTime) {
        Logger.getLogger(this.getClass().getName()).log(
                Level.INFO,
                "[{0}] [Mirror] All/Ready/Target/Ratio: {1} | {2} | {3} | {4}",
                new Object[]{
                        simTime,
                        n.getNumMirrors(),
                        n.getNumReadyMirrors(),
                        n.getNumTargetMirrors(),
                        NumberFormat.getInstance().format(mirrorRatio)
                }
        );
    }

    /**
     * @return total number of mirrors in the network (all states)
     */
    public int getNumMirrors() {
        return n.getNumMirrors();
    }

    /**
     * @return list of all {@link Mirror} objects in the network
     */
    public List<Mirror> getMirrors() {
        return n.getMirrors();
    }

    /**
     * @return number of mirrors currently in the "ready" state
     */
    public int getNumReadyMirrors() {
        return n.getNumReadyMirrors();
    }

    /**
     * @return configured number of mirrors that should be ready (target mirrors)
     */
    public int getNumTargetMirrors() {
        return n.getNumTargetMirrors();
    }

    /**
     * @return number of targeted links per mirror in the network
     */
    public int getNumTargetLinksPerMirror() {
        return n.getNumTargetLinksPerMirror();
    }

    /**
     * @return ratio between ready mirrors and target mirrors (0..1).
     *         {@code 1.0} means all target mirrors are ready.
     */
    public double getMirrorRatio() {
        return mirrorRatio;
    }
}
