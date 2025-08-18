package org.lrdm.probes;

import org.lrdm.Link;
import org.lrdm.Network;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * # LinkProbe
 *
 * Observes link-related state in the RDM network and exposes:
 * <ul>
 *   <li>the fraction of currently active links relative to the topology’s target links
 *       ({@link #getLinkRatio()}), and</li>
 *   <li>the “active links” metric as recorded by the {@link Network} for a given timestep
 *       ({@link #getActiveLinkMetric(int)}).</li>
 * </ul>
 *
 * <p>The probe is intended to be called once per simulation step via {@link #update(int)},
 * which recomputes and caches the current ratio for fast access. It does not mutate the
 * {@link Network}; it only reads current/recorded values and optionally prints a
 * human-readable line via {@link #print(int)}.
 *
 * <p><b>Thread-safety:</b> This class is not synchronized. Use it from the simulation
 * thread that owns the {@link Network} instance.
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 * @since 1.0
 */
public class LinkProbe extends Probe {

    /**
     * Network under observation (never {@code null}).
     */
    private final Network n;

    /**
     * Cached ratio of active links to target links after the last {@link #update(int)}.
     * Value range: {@code [0.0, 1.0]}.
     */
    private double linkRatio;

    /**
     * Creates a new {@code LinkProbe} bound to the given {@link Network}.
     *
     * @param n the network to observe; must not be {@code null}
     */
    public LinkProbe(Network n) {
        super(n);
        this.n = n;
        this.linkRatio = 0.0;
    }

    /**
     * Recomputes the ratio of {@code activeLinks / targetLinks} for the given simulation step.
     * The value is cached and later returned by {@link #getLinkRatio()}.
     *
     * <p>Note: The {@link Network} remains authoritative for any historic “active links”
     * metric series; this probe does not write to network history.
     *
     * @param simTime current simulation timestep
     */
    @Override
    public void update(int simTime) {
        final int active = n.getNumActiveLinks();
        final int target = n.getNumTargetLinks();
        linkRatio = (target > 0) ? (active / (double) target) : 0.0;
    }

    /**
     * Prints a human-readable snapshot for the given timestep, including
     * active links, target links and the current ratio.
     *
     * <p>Output format example:
     * <pre>
     * [120] Active Links: 45 / 60 (75.0%)
     * </pre>
     *
     * @param simTime simulation timestep to annotate in the output
     */
    @Override
    public void print(int simTime) {
        final int num_links = n.getNumLinks();
        final int active = n.getNumActiveLinks();
        final int target = n.getNumTargetLinks();
        final NumberFormat pct = NumberFormat.getPercentInstance(Locale.ROOT);
        pct.setMinimumFractionDigits(1);
        pct.setMaximumFractionDigits(1);

        // Keep it simple: stdout logging; adapt to your logging facility if desired.
        Logger.getLogger(this.getClass().getName()).log(
                Level.INFO,
                "[{0}] [Link] All/Active/Target/Ratio: {1} | {2} | {3} | {4}",
                new Object[]{
                        simTime,
                        num_links,
                        active,
                        target,
                        pct.format(linkRatio)
                }
        );
    }

    /**
     * Returns the ratio of active to target links computed in the last {@link #update(int)} call.
     *
     * @return the cached ratio in {@code [0.0, 1.0]} (0 if target is 0)
     */
    public double getLinkRatio() {
        return linkRatio;
    }

    /**
     * Returns the “active links” metric the {@link Network} recorded at the given timestep.
     * This is a historical value managed by the network (e.g., percentage in 0..100).
     *
     * <p><b>Important:</b> This method does <em>not</em> compute anything; it merely
     * retrieves what the network has stored for {@code simTime}.
     *
     * @param simTime the simulation step for which to retrieve the metric
     * @return the recorded active links metric for {@code simTime}
     * @throws NullPointerException if the network has not stored a value for {@code simTime}
     */
    public int getActiveLinkMetric(int simTime) {
        return n.getActiveLinksHistory().get(simTime);
    }

    /**
     * Returns all links from the monitored network.
     * Delegates the call to the underlying {@link Network} instance.
     *
     * @return immutable view of all links in the network
     */
    public Set<Link> getLinks() {
        return n.getLinks();
    }
}
