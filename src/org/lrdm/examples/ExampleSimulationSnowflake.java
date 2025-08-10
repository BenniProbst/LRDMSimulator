package org.lrdm.examples;

import org.lrdm.SubstructureFactory;
import org.lrdm.TimedRDMSim;
import org.lrdm.effectors.Effector;
import org.lrdm.probes.Probe;
import org.lrdm.topologies.node.StructureNode;
import org.lrdm.topologies.strategies.*;
import org.lrdm.topologies.strategies.SnowflakeTopologyStrategy.SnowflakeProperties;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static java.lang.Math.max;

/**Simple simulation runner.
 * 
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 *
 */
public class ExampleSimulationSnowflake {
	public static void main(String[] args) {

        Map<StructureNode.StructureType, Supplier<BuildAsSubstructure>> suppliers = Map.of(
                StructureNode.StructureType.STAR,               StarTopologyStrategy::new,
                StructureNode.StructureType.FULLY_CONNECTED,    FullyConnectedTopology::new,
                StructureNode.StructureType.N_CONNECTED,        NConnectedTopology::new,
                StructureNode.StructureType.DEPTH_LIMIT_TREE,   () -> new DepthLimitTreeTopologyStrategy(3),
                StructureNode.StructureType.BALANCED_TREE,      BalancedTreeTopologyStrategy::new
        );

        SubstructureFactory substructureFactory = SubstructureFactory.fromSuppliers(suppliers);

        System.setProperty("java.util.logging.SimpleFormatter.format",
				"[%1$tF %1$tT] [%4$-7s] %5$s %n");
		TimedRDMSim sim = new TimedRDMSim();
		sim.initialize(new SnowflakeTopologyStrategy());
		Effector effector = sim.getEffector();
		int mirrors = 10;
		for(int t = 0; t < 100; t += 10) {
			if(t == 40) continue;
			effector.setMirrors(mirrors, t);
			mirrors += 4;
		}
		for(int t = 100; t < 200; t += 10) {
			effector.setMirrors(mirrors, t);
			mirrors -= 4;
		}
		effector.setStrategy(new FullyConnectedTopology(), 20);
        effector.setTargetLinksPerMirror(2,20);

        // Reihenfolge der zyklischen Rotation (frei wählbar)
        List<StructureNode.StructureType> rotation1 = List.of(
                StructureNode.StructureType.DEPTH_LIMIT_TREE,
                StructureNode.StructureType.BALANCED_TREE,
                StructureNode.StructureType.STAR
        );


        effector.setStrategy(new SnowflakeTopologyStrategy(
                new SnowflakeProperties(
                        0.3,
                        2
                ),
                substructureFactory,
                rotation1
        ), 40);

		effector.setStrategy(new FullyConnectedTopology(), 60);
        effector.setTargetLinksPerMirror(4,60);

        List<StructureNode.StructureType> rotation2 = List.of(
                StructureNode.StructureType.N_CONNECTED,
                StructureNode.StructureType.FULLY_CONNECTED
        );

		effector.setStrategy(new SnowflakeTopologyStrategy(
                new SnowflakeProperties(
                        0.6,
                        3
                ),
                substructureFactory,
                rotation2
        ), 80);

		int startMirrors = 15;
		int count = 0;
		for(int t = 200; t < 300; t += 10) {
			effector.setMirrors(startMirrors - count++,t);
		}

		startMirrors = 5;
		count = 0;
		for(int t = 300; t < 400; t += 10) {
			effector.setMirrors(startMirrors + count,t);
			count += max(1,count);
		}

		//use this code to manually run the simulation step by step
		List<Probe> probes = sim.getProbes();
		int simTime = sim.getSimTime();
		for (int t = 1; t <= simTime; t++) {
			for(Probe p : probes) p.print(t);

			sim.runStep(t);
		}
		sim.plotLinks();
	}
}
