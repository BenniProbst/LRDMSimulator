package org.lrdm.topologies.strategies;

import org.lrdm.DataPackage;
import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.Action;
import org.lrdm.util.IDGenerator;

import java.util.*;

/**Interface to be used by all Topology strategies. Specifies methods to be used for initializing a network, handling added and removed mirrors as well as to compute the number of target links.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public abstract class TopologyStrategy {
	public abstract Set<Link> initNetwork(Network n, Properties props);
	public void restartNetwork(Network n, Properties props, int simTime) {
		n.getLinks().forEach(Link::shutdown);
		for(Mirror m:n.getMirrors()){
			if(!m.isRoot())m.shutdown(simTime);
		}
		n.getMirrors().addAll(createMirrors(n.getNumMirrors()-1, simTime, props));
	}
	public abstract void handleAddNewMirrors(Network n, int newMirrors, Properties props, int simTime);

	/**Remove the requested number of links from the network.
	 * The mirrors with the highest ID will be removed first.
	 * Does not directly remove the mirrors, but calls {@link Mirror#shutdown(int)}.
	 *
	 * @param n the {@link Network}
	 * @param removeMirrors the number of {@link Mirror}s to remove
	 * @param props {@link Properties} of the simulation
	 * @param simTime current simulation time
	 */
	public Set<Mirror> handleRemoveMirrors(Network n, int removeMirrors, Properties props, int simTime) {
		Set<Mirror> cleanedMirrors = new HashSet<>();
		for(int i = 0; i < removeMirrors; i++) {
			Mirror m = n.getMirrorsSortedById().get(n.getNumMirrors()-1-i);
			m.shutdown(simTime);
			cleanedMirrors.add(m);
		}
		return cleanedMirrors;
	}

	/**Is meant to return the expected number of total links in the network according to the respective topology.
	 *
	 * @param n {@link Network} the network
	 * @return number of total links expected for the network
	 */
	public abstract int getNumTargetLinks(Network n);

	/**Is meant to return the expected number of total links in the network if the action is executed.
	 *
	 * @param a the {@link Action} which might be executed
	 * @return number of predicted total links
	 */
	public abstract int getPredictedNumTargetLinks(Action a);

	/**Creates the given number of mirrors and adds them to the network.
	 *
	 * @param numberOfMirrors the number of mirrors to add
	 * @param simTime the current simulation time
	 * @param props the {@link Properties} of the simulation
	 * @return a list of added {@link Mirror}s
	 */
	protected List<Mirror> createMirrors(int numberOfMirrors, int simTime, Properties props) {
		List<Mirror> addedMirrors = new ArrayList<>();
		for(int i = 0; i < numberOfMirrors; i++) {
			Mirror m = new Mirror(IDGenerator.getInstance().getNextID(), simTime, props);
			addedMirrors.add(m);
		}
		return addedMirrors;
	}

	public abstract String toString();
}
