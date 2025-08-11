package org.lrdm.topologies.strategies;

import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.Action;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Interface to be used by all Topology strategies. Specifies methods to be used for initializing a network, handling added and removed mirrors as well as to compute the number of target links.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public abstract class TopologyStrategy {
    public Set<Link> initNetwork(Network n, Properties props) {
        return restartNetwork(n, props, 0);
    }

    public Set<Link> restartNetwork(Network n, Properties props, int simTime) {
        n.getLinks().forEach(Link::shutdown);
        Set<Mirror> mirrorSet = n.getMirrors().stream()
                .filter(mirror -> !mirror.isRoot() && mirror.isUsableForNetwork()).collect(Collectors.toSet());
        mirrorSet.forEach(mirror -> mirror.shutdown(simTime));
        n.getMirrorCursor().createMirrors(mirrorSet.size(), simTime);
        //replace crashed mirrors with new ones
        int usableMirrorCount = Math.toIntExact(n.getMirrors().stream()
                .filter(Mirror::isUsableForNetwork).count());
        while (usableMirrorCount < n.getNumTargetMirrors()) {
            n.getMirrorCursor().createMirrors(1, simTime);
            usableMirrorCount++;
        }
        n.getMirrorCursor().resetMirrorCursor();
        return n.getLinks().stream().filter(Link::isActive).collect(Collectors.toSet());
    }

    public abstract void handleAddNewMirrors(Network n, int newMirrors, Properties props, int simTime);

    /**
     * Remove the requested number of links from the network.
     * The mirrors with the highest ID will be removed first.
     * Does not directly remove the mirrors, but calls {@link Mirror#shutdown(int)}.
     *
     * @param n             the {@link Network}
     * @param removeMirrors the number of {@link Mirror}s to remove
     * @param props         {@link Properties} of the simulation
     * @param simTime       current simulation time
     */
    public void handleRemoveMirrors(Network n, int removeMirrors, Properties props, int simTime) {
        for (int i = 0; i < removeMirrors; i++) {
            Mirror m = n.getMirrorsSortedById().get(n.getNumMirrors() - 1 - i);
            m.shutdown(simTime);
        }
    }

    /**
     * Is meant to return the expected number of total links in the network according to the respective topology.
     *
     * @param n {@link Network} the network
     * @return number of total links expected for the network
     */
    public abstract int getNumTargetLinks(Network n);

    /**
     * Is meant to return the expected number of total links in the network if the action is executed.
     *
     * @param a the {@link Action} which might be executed
     * @return number of predicted total links
     */
    public abstract int getPredictedNumTargetLinks(Action a);

    public abstract String toString();
}
