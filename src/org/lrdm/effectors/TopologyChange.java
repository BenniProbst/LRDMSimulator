package org.lrdm.effectors;

import org.lrdm.Network;
import org.lrdm.topologies.TopologyStrategy;

/**An adaptation action representing the change of the topology.
 *
 * @author Anonymous
 */
public class TopologyChange extends Action {
    private TopologyStrategy newTopology;

    public TopologyChange(Network n, TopologyStrategy newTopology, int id, int time) {
        super(n, id, time);
        this.newTopology = newTopology;
    }
    public TopologyStrategy getNewTopology() {
        return newTopology;
    }

}
