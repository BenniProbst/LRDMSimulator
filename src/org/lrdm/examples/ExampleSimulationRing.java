package org.lrdm.examples;

import org.lrdm.TimedRDMSim;
import org.lrdm.effectors.Effector;
import org.lrdm.probes.Probe;
import org.lrdm.topologies.strategies.RingTopologyStrategy;
import org.lrdm.topologies.strategies.FullyConnectedTopology;

import java.util.List;

import static java.lang.Math.max;

/**Simple simulation runner.
 * 
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 *
 */
public class ExampleSimulationRing {
	public static void main(String[] args) {
		System.setProperty("java.util.logging.SimpleFormatter.format",
				"[%1$tF %1$tT] [%4$-7s] %5$s %n");
		TimedRDMSim sim = new TimedRDMSim();
		sim.initialize(new RingTopologyStrategy());
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
		effector.setStrategy(new RingTopologyStrategy(), 40);
		effector.setStrategy(new FullyConnectedTopology(), 60);
		effector.setStrategy(new RingTopologyStrategy(), 80);

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
