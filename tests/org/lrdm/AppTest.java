package org.lrdm;

import org.lrdm.effectors.Action;
import org.lrdm.effectors.Effector;
import org.lrdm.examples.ExampleOptimizer;
import org.lrdm.examples.ExampleSimulation;
import org.lrdm.probes.LinkProbe;
import org.lrdm.probes.MirrorProbe;
import org.lrdm.probes.Probe;
import org.lrdm.topologies.BalancedTreeTopologyStrategy;
import org.lrdm.topologies.FullyConnectedTopology;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.lrdm.topologies.NConnectedTopology;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lrdm.TestUtils.loadProperties;
import static org.lrdm.TestUtils.props;
import static org.junit.jupiter.api.Assertions.*;

class AppTest {
    private TimedRDMSim sim;
    private static final String config = "resources/sim-test-1.conf";
    int startup_time_min;
    int startup_time_max;
    int ready_time_min;
    int ready_time_max;
    int link_activation_time_min;
    int link_activation_time_max;

    @BeforeEach
    void setHeadlessMode() {
        // Sicherstellen, dass Headless-Modus für alle Tests gesetzt ist
        System.setProperty("java.awt.headless", "true");
    }

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
        sim.setHeadless(true); // Headless-Modus für Tests
    }

    @Test
    void testMissingConfig() {
        assertDoesNotThrow(() -> {
            TimedRDMSim testSim = new TimedRDMSim("does-not-exist.conf");
            testSim.setHeadless(true);
        });
    }

    @Test
    void testUnreadableConfig() throws IOException {
        try (RandomAccessFile f = new RandomAccessFile(config,"rw")) {
            FileChannel channel = f.getChannel();
            FileLock lock = channel.lock();
            try {
                assertDoesNotThrow(() -> {
                    TimedRDMSim testSim = new TimedRDMSim(config);
                    testSim.setHeadless(true);
                });
            } finally {
                if (lock != null && lock.isValid()) {
                    lock.release();
                }
            }
        }
    }

    @Test
    void testHeadlessNoDebug() throws IOException {
        initSimulator("resources/sim-test-short.conf");
        sim.setHeadless(true);
        sim.initialize(null);
        assertDoesNotThrow(() -> sim.run());
    }

    @Test
    void testWrongUsageOfRunStep() throws IOException {
        initSimulator();
        sim.initialize(null);
        assertDoesNotThrow(() -> sim.runStep(5));
    }

    @Test()
    void testInitializeHasToBeCalled() throws IOException {
        initSimulator();
        assertThrows(RuntimeException.class, () -> sim.run());
    }

    @Test
    void testExampleSimulator() {
        // Headless-Modus für ExampleSimulation setzen
        System.setProperty("java.awt.headless", "true");
        assertDoesNotThrow(() -> {
            // ExampleSimulation Logik direkt ausführen statt main() zu rufen
            System.setProperty("java.util.logging.SimpleFormatter.format",
                    "[%1$tF %1$tT] [%4$-7s] %5$s %n");
            TimedRDMSim testSim = new TimedRDMSim();
            testSim.setHeadless(true); // Explizit Headless setzen
            testSim.initialize(new BalancedTreeTopologyStrategy());

            Effector effector = testSim.getEffector();
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
            effector.setStrategy(new BalancedTreeTopologyStrategy(), 40);
            effector.setStrategy(new FullyConnectedTopology(), 60);
            effector.setStrategy(new BalancedTreeTopologyStrategy(), 80);

            List<Probe> probes = testSim.getProbes();
            int simTime = testSim.getSimTime();
            for (int t = 1; t <= simTime; t++) {
                for(Probe p : probes) p.print(t);
                testSim.runStep(t);
            }
            testSim.plotLinks();
        });
    }

    @Test
    void testSzenarioZero() {
        // Headless-Modus für ExampleOptimizer setzen
        System.setProperty("java.awt.headless", "true");
        assertDoesNotThrow(() -> {
            // ExampleOptimizer Logik direkt ausführen statt main() zu rufen
            System.setProperty("java.util.logging.SimpleFormatter.format",
                    "[%1$tF %1$tT] [%4$-7s] %5$s %n");
            TimedRDMSim testSim = new TimedRDMSim();
            testSim.setHeadless(true); // Explizit Headless setzen
            testSim.initialize(new NConnectedTopology());

            LinkProbe lp = testSim.getLinkProbe();
            MirrorProbe mp = testSim.getMirrorProbe();

            int mirrors = mp.getNumMirrors();
            int lpm = mp.getNumTargetLinksPerMirror();
            int epsilon = 2;

            for (int t = 1; t < testSim.getSimTime(); t++) {
                testSim.runStep(t);
                if (lp.getLinkRatio() > 0.75 && lp.getActiveLinkMetric(t) < 35 - epsilon) {
                    mirrors--;
                    lpm++;
                    Action a = testSim.getEffector().setMirrors(mirrors, t + 1);
                    Action b = testSim.getEffector().setTargetLinksPerMirror(lpm, t + 1);
                    if (a.getEffect().getLatency() > b.getEffect().getLatency()) {
                        testSim.getEffector().removeAction(a);
                        mirrors++;
                    } else {
                        testSim.getEffector().removeAction(b);
                        lpm--;
                    }
                }
            }
        });
    }

    @Test
    void testMirrorChange() throws IOException {
        initSimulator();
        sim.initialize(new BalancedTreeTopologyStrategy());
        sim.getEffector().setMirrors(20, 10);
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
        MirrorProbe mp = getMirrorProbe();
        assertNotNull(mp, "MirrorProbe should not be null");

        for(int t = 1; t < sim.getSimTime(); t++) {
            int currentMirrorCount = mp.getNumMirrors();
            System.out.println("timestep: "+t+" mirrors: "+currentMirrorCount);
            sim.runStep(t);
            Action a = sim.getEffector().setMirrors(currentMirrorCount+1, t+1);
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
    void testMirrorStartupTime() throws IOException {
        MirrorProbe mp = initTimeTest();
        Map<Integer,Integer> startupTimes = getTimeToStateForMirrorFromSimulation(mp, Mirror.State.UP);
        double avg = getAvg(startupTimes);
        assertTrue(avg > startup_time_min && avg < startup_time_max);
        for(Mirror m : mp.getMirrors()) {
            assertEquals(m.getStartupTime(), startupTimes.get(m.getID()));
        }
    }

    @Test
    void testMirrorReadyTime() throws IOException {
        MirrorProbe mp = initTimeTest();
        Map<Integer, Integer> readyTimes = getTimeToStateForMirrorFromSimulation(mp, Mirror.State.READY);
        double avg = getAvg(readyTimes);
        assertTrue(avg > ready_time_min+startup_time_min && avg < ready_time_max+startup_time_max);
        for(Mirror m : mp.getMirrors()) {
            assertEquals(m.getReadyTime()+m.getStartupTime(), readyTimes.get(m.getID()));
        }
    }

    @Test
    void testLinkActiveTime() throws IOException {
        initTimeTest();
        LinkProbe lp = getLinkProbe();
        Map<Integer,Integer> activeTimes = new HashMap<>();
        for(int i = 1; i < sim.getSimTime(); i++) {
            for(Link l : lp.getLinks()) {
                if(l.getState().equals(Link.State.ACTIVE) && activeTimes.get(l.getID()) == null) {
                    activeTimes.put(l.getID(), i);
                }
            }
            sim.runStep(i);
        }
        double avg = getAvg(activeTimes);
        assertTrue(avg > startup_time_min+link_activation_time_min && avg < startup_time_max+link_activation_time_max);
        for(Link l : lp.getLinks()) {
            int expected = l.getActivationTime() + Math.max(l.getSource().getStartupTime(), l.getTarget().getStartupTime());
            assertEquals(expected, activeTimes.get(l.getID()));
        }
    }

    @Test
    void testTopologyChange() throws IOException {
        initSimulator();
        sim.initialize(new FullyConnectedTopology());
        sim.getEffector().setStrategy(new BalancedTreeTopologyStrategy(),10);
        sim.getEffector().setStrategy(new FullyConnectedTopology(), 20);
        sim.getEffector().setStrategy(new BalancedTreeTopologyStrategy(),30);
        sim.getEffector().setStrategy(new FullyConnectedTopology(),40);
        assertDoesNotThrow(() -> sim.run());
    }

    private MirrorProbe initTimeTest() throws IOException {
        initSimulator();
        sim.initialize(new BalancedTreeTopologyStrategy());
        MirrorProbe mp = getMirrorProbe();
        assertNotNull(mp);
        return mp;
    }

    private Map<Integer, Integer> getTimeToStateForMirrorFromSimulation(MirrorProbe mp, Mirror.State state) {
        Map<Integer,Integer> stateTimes = new HashMap<>();
        for(int i = 1; i < sim.getSimTime(); i++) {
            for(Mirror m : mp.getMirrors()) {
                if(m.getState().equals(state) && stateTimes.get(m.getID()) == null) {
                    stateTimes.put(m.getID(), i);
                }
            }
            sim.runStep(i);
        }
        return stateTimes;
    }

    private static double getAvg(Map<Integer, Integer> times) {
        int total = 0;
        for(int t : times.values()) total += t;
        return (double)total / times.size();
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
