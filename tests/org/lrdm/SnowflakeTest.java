package org.lrdm;

import org.junit.jupiter.api.Test;
import org.lrdm.effectors.Action;
import org.lrdm.probes.LinkProbe;
import org.lrdm.probes.MirrorProbe;
import org.lrdm.probes.Probe;
import org.lrdm.topologies.BalancedTreeTopologyStrategy;
import org.lrdm.topologies.FullyConnectedTopology;
import org.lrdm.topologies.NConnectedTopology;
import org.lrdm.topologies.SnowflakeTopologyStrategy;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.lrdm.TestProperties.loadProperties;
import static org.lrdm.TestProperties.props;

class SnowflakeTest {
    private TimedRDMSim sim;
    private static final String config = "resources/sim-test-snowflake.conf";
    int startup_time_min;
    int startup_time_max;
    int ready_time_min;
    int ready_time_max;
    int link_activation_time_min;
    int link_activation_time_max;

    public void initSimulator() throws IOException {
        initSimulator(config);
    }

    public void initSimulator(String config) throws IOException {
        loadProperties(config);
        startup_time_min = Integer.parseInt(props.get("startup_time_min").toString());
        startup_time_max = Integer.parseInt(props.get("startup_time_max").toString());
        ready_time_min = Integer.parseInt(props.get("ready_time_min").toString());
        ready_time_max = Integer.parseInt(props.get("ready_time_max").toString());
        link_activation_time_min = Integer.parseInt(props.get("link_activation_time_min").toString());
        link_activation_time_max = Integer.parseInt(props.get("link_activation_time_max").toString());
        sim = new TimedRDMSim(config);
        sim.setHeadless(false);
    }

    @Test()
    void testInitializeHasToBeCalled() throws IOException {
        initSimulator();
        assertThrows(RuntimeException.class, () -> sim.run());
    }

    @Test
    void testMirrorChange() throws IOException {
        initSimulator();
        sim.initialize(new SnowflakeTopologyStrategy());
        sim.getEffector().setMirrors(140, 10);
        MirrorProbe mp = null;
        for(Probe p : sim.getProbes()) {
            if(p instanceof  MirrorProbe) {
                mp = (MirrorProbe) p;
            }
        }
        assert(mp != null);
        for(int t = 1; t < sim.getSimTime(); t++) {
            System.out.println("timestep: "+t+" mirrors: "+mp.getNumMirrors());
            sim.runStep(t);
            if(t < 10) assertEquals(10, mp.getNumMirrors());
            else if(t >= 30) assertEquals(20, mp.getNumMirrors());
            assertFalse(mp.getMirrors().isEmpty());
            assertTrue(mp.getNumReadyMirrors() <= mp.getNumTargetMirrors());
            assertEquals(mp.getMirrorRatio(), (double) mp.getNumReadyMirrors() / mp.getNumTargetMirrors());
        }
    }

    @Test
    void testDeltaEffects() throws IOException {
        initSimulator();
        sim.initialize(new NConnectedTopology());
        MirrorProbe mp = null;
        for(Probe p : sim.getProbes()) {
            if(p instanceof  MirrorProbe) {
                mp = (MirrorProbe) p;
            }
        }
        for(int t = 1; t < sim.getSimTime(); t++) {
            assertNotNull(mp);
            System.out.println("timestep: "+t+" mirrors: "+mp.getNumMirrors());
            sim.runStep(t);
            Action a = sim.getEffector().setMirrors(mp.getNumMirrors()+1, t+1);
            int ttw = a.getEffect().getDeltaTimeToWrite();
            int bw = a.getEffect().getDeltaBandwidth(sim.getProps());
            double al = a.getEffect().getDeltaActiveLinks();
            assertTrue(ttw >= -100);
            assertTrue(ttw <= 100);
            assertTrue(bw >= -100);
            assertTrue(bw <= 100);
            assertTrue(al >= -100);
            assertTrue(al <= 100);
            assertDoesNotThrow(() -> a.getEffect().getLatency());
        }
    }

    @Test
    void testMirrorReduction() throws IOException {
        initSimulator();
        sim.initialize(new BalancedTreeTopologyStrategy());
        sim.getEffector().setMirrors(2, 10);
        MirrorProbe mp = getMirrorProbe();
        assert(mp != null);
        for(int t = 1; t < sim.getSimTime(); t++) {
            sim.runStep(t);
            if(t < 10) assertEquals(10, mp.getNumMirrors());
            else if(t >= 15) assertEquals(2, mp.getNumMirrors());
        }
    }

    @Test
    void testTargetLinkChange() throws IOException {
        initSimulator();
        sim.initialize(new BalancedTreeTopologyStrategy());
        sim.getEffector().setTargetLinksPerMirror(5, 10);
        LinkProbe lp = getLinkProbe();
        assert(lp != null);
        assertDoesNotThrow(() -> sim.run());
    }
    @Test
    void testTopologyChange() throws IOException {
        initSimulator();
        sim.initialize(new FullyConnectedTopology());
        sim.getEffector().setStrategy(new BalancedTreeTopologyStrategy(),10);
        sim.getEffector().setStrategy(new FullyConnectedTopology(), 20);
        sim.getEffector().setStrategy(new BalancedTreeTopologyStrategy(),30);
        sim.getEffector().setStrategy(new FullyConnectedTopology(),40);
        sim.getEffector().setStrategy(new SnowflakeTopologyStrategy(),50);
        assertDoesNotThrow(() -> sim.run());
    }

    private MirrorProbe getMirrorProbe() {
        MirrorProbe mp = null;
        for(Probe p : sim.getProbes()) {
            if(p instanceof MirrorProbe) mp = (MirrorProbe)p;
        }
        return mp;
    }
    private LinkProbe getLinkProbe() {
        LinkProbe lp = null;
        for(Probe p : sim.getProbes()) {
            if(p instanceof LinkProbe) lp = (LinkProbe)p;
        }
        return lp;
    }

}
