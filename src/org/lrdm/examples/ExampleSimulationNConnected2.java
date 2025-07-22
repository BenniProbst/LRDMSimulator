package org.lrdm.examples;

import org.lrdm.TimedRDMSim;
import org.lrdm.effectors.Effector;
import org.lrdm.probes.Probe;
import org.lrdm.topologies.strategies.FullyConnectedTopology;
import org.lrdm.topologies.strategies.NConnectedTopology;

import java.util.List;

/**Simple simulation runner.
 * 
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 *
 */
public class ExampleSimulationNConnected2 {
	public static void main(String[] args) {
		System.setProperty("java.util.logging.SimpleFormatter.format",
				"[%1$tF %1$tT] [%4$-7s] %5$s %n");
		TimedRDMSim sim = new TimedRDMSim();
		sim.initialize(new NConnectedTopology());
		Effector effector = sim.getEffector();
		int mirrors = 8;
		for(int t = 0; t < 200; t += 20) {
			effector.setMirrors(mirrors, t);
			mirrors += 2;
		}
		for(int t = 200; t < 400; t += 20) {
			effector.setMirrors(mirrors, t);
			mirrors -= 2;
		}
		for(int t = 500; t < 600; t += 20) {
			effector.setMirrors(mirrors, t);
			mirrors += 4;
		}
		effector.setStrategy(new FullyConnectedTopology(), 600);
		for(int t = 700; t < 800; t += 20) {
			effector.setMirrors(mirrors, t);
			mirrors -= 1;
		}
		effector.setStrategy(new NConnectedTopology(), 800);

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
