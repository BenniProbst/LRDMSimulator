
package org.lrdm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lrdm.probes.MirrorProbe;
import org.lrdm.probes.Probe;
import org.lrdm.topologies.BalancedTreeTopologyStrategy;
import org.lrdm.util.MirrorNode;

import java.io.IOException;
import java.util.*;

import static org.lrdm.TestProperties.loadProperties;
import static org.lrdm.TestProperties.props;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MirrorNode spezifische Tests")
class MirrorNodeTest {

    private TimedRDMSim sim;
    private MirrorNode mirrorNode;
    private static final String config = "resources/sim-test-treenode.conf";

    @BeforeEach
    void setUp() throws IOException {
        loadProperties(config);
        mirrorNode = new MirrorNode(1, 0);
    }

    public void initSimulator() throws IOException {
        initSimulator(config);
    }

    public void initSimulator(String config) throws IOException {
        loadProperties(config);
        sim = new TimedRDMSim(config);
        sim.setHeadless(true);
    }

    private Network createMockNetwork() throws IOException {
        loadProperties(config);
        return new Network(new BalancedTreeTopologyStrategy(), 5, 2, 1000, props);
    }

    @Test
    @DisplayName("MirrorNode funktioniert ohne zugeordnetes Mirror")
    void testMirrorNodeWithoutMirror() {
        assertEquals(0, mirrorNode.getNumTargetLinks());
        assertEquals(0, mirrorNode.getPredictedNumTargetLinks());
        assertTrue(mirrorNode.getAllLinks().isEmpty());
    }

    @Test
    @DisplayName("MirrorNode mit zugeordnetem Mirror synchronisiert Links")
    void testMirrorNodeWithMirrorSyncsLinks() {
        Mirror mirror = new Mirror(101, 0, props);
        mirrorNode.setMirror(mirror);

        Mirror targetMirror = new Mirror(102, 0, props);
        Link link = new Link(1, mirror, targetMirror, 0, props);

        // Füge Link über MirrorNode hinzu
        mirrorNode.addLink(link);

        // Beide sollten den Link haben
        assertTrue(mirrorNode.getAllLinks().contains(link));
        assertTrue(mirror.getLinks().contains(link));
        assertEquals(1, mirrorNode.getNumTargetLinks());
    }

    @Test
    @DisplayName("createAndLinkMirrors mit leerer Liste")
    void testCreateAndLinkMirrorsEmpty() throws IOException {
        Network mockNetwork = createMockNetwork();
        Set<Link> links = mirrorNode.createAndLinkMirrors(mockNetwork, new ArrayList<>(), 0, props);
        assertTrue(links.isEmpty());
    }

    @Test
    @DisplayName("createAndLinkMirrors mit einem Mirror")
    void testCreateAndLinkMirrorsSingle() throws IOException {
        initSimulator();
        sim.initialize(new BalancedTreeTopologyStrategy());
        sim.getEffector().setMirrors(1, 0);

        for(int t = 1; t <= 10; t++) {
            sim.runStep(t);
        }

        MirrorProbe mirrorProbe = getMirrorProbe();
        assertNotNull(mirrorProbe);
        List<Mirror> mirrors = mirrorProbe.getMirrors();

        Network mockNetwork = createMockNetwork();
        Set<Link> links = mirrorNode.createAndLinkMirrors(mockNetwork, mirrors, 0, props);

        // Ein einzelnes Mirror sollte keine Links benötigen
        assertTrue(links.isEmpty());
    }

    @Test
    @DisplayName("createAndLinkMirrors mit mehreren Mirrors")
    void testCreateAndLinkMirrorsMultiple() throws IOException {
        initSimulator();
        sim.initialize(new BalancedTreeTopologyStrategy());
        sim.getEffector().setMirrors(5, 0);

        for(int t = 1; t <= 15; t++) {
            sim.runStep(t);
        }

        MirrorProbe mirrorProbe = getMirrorProbe();
        assertNotNull(mirrorProbe);
        List<Mirror> mirrors = mirrorProbe.getMirrors();

        Network mockNetwork = createMockNetwork();
        Set<Link> links = mirrorNode.createAndLinkMirrors(mockNetwork, mirrors, 0, props);

        // Mit 5 Mirrors sollten 4 Links erstellt werden (n-1 für Baum)
        assertNotNull(links);
        assertEquals(4, links.size());

        // Überprüfe, dass alle Links gültige Source und Target haben
        for (Link link : links) {
            assertNotNull(link.getSource());
            assertNotNull(link.getTarget());
            assertTrue(mirrors.contains(link.getSource()));
            assertTrue(mirrors.contains(link.getTarget()));
        }
    }

    @Test
    @DisplayName("Pending Links Management")
    void testPendingLinksManagement() {
        assertEquals(0, mirrorNode.getPendingLinks());
        assertEquals(0, mirrorNode.getPredictedNumTargetLinks());

        mirrorNode.addPendingLinks(5);
        assertEquals(5, mirrorNode.getPendingLinks());
        assertEquals(5, mirrorNode.getPredictedNumTargetLinks());

        // Füge tatsächliche Links hinzu
        Mirror source = new Mirror(104, 0, props);
        Mirror target = new Mirror(105, 0, props);
        Link link1 = new Link(2, source, target, 0, props);
        Link link2 = new Link(3, source, target, 0, props);

        mirrorNode.addLink(link1);
        mirrorNode.addLink(link2);

        assertEquals(2, mirrorNode.getNumTargetLinks());
        assertEquals(7, mirrorNode.getPredictedNumTargetLinks()); // 2 + 5 pending

        mirrorNode.confirmPendingLinks(3);
        assertEquals(2, mirrorNode.getPendingLinks());
        assertEquals(4, mirrorNode.getPredictedNumTargetLinks()); // 2 + 2 pending

        // Teste negative Bestätigung
        mirrorNode.confirmPendingLinks(10);
        assertEquals(0, mirrorNode.getPendingLinks()); // Sollte nicht unter 0 gehen
    }

    @Test
    @DisplayName("Mirror-Node Hierarchie Management")
    void testMirrorNodeHierarchy() {
        MirrorNode child1 = new MirrorNode(2, 1);
        MirrorNode child2 = new MirrorNode(3, 1);
        MirrorNode grandchild = new MirrorNode(4, 2);

        mirrorNode.addChild(child1);
        mirrorNode.addChild(child2);
        child1.addChild(grandchild);

        // TreeNode-Hierarchie
        assertEquals(2, mirrorNode.getChildren().size());
        assertEquals(1, child1.getChildren().size());
        assertEquals(0, child2.getChildren().size());

        // MirrorNode-spezifische Hierarchie
        assertEquals(2, mirrorNode.getNumMirrors()); // Nur direkte Kinder
        assertEquals(1, child1.getNumMirrors());     // Nur direktes Kind
        assertEquals(0, child2.getNumMirrors());     // Keine Kinder

        List<MirrorNode> mirrors = mirrorNode.getAllMirrors();
        assertTrue(mirrors.contains(child1));
        assertTrue(mirrors.contains(child2));
        assertFalse(mirrors.contains(grandchild)); // Nicht direktes Kind
    }

    @Test
    @DisplayName("isLinkedWith verschiedene Szenarien")
    void testIsLinkedWithScenarios() {
        Mirror mirror1 = new Mirror(106, 0, props);
        Mirror mirror2 = new Mirror(107, 0, props);
        Mirror mirror3 = new Mirror(108, 0, props);

        MirrorNode node1 = new MirrorNode(5, 0, mirror1);
        MirrorNode node2 = new MirrorNode(6, 1, mirror2);
        MirrorNode node3 = new MirrorNode(7, 1, mirror3);

        // Erstelle Link zwischen mirror1 und mirror2
        Link link = new Link(4, mirror1, mirror2, 0, props);
        mirror1.addLink(link);
        mirror2.addLink(link);

        assertTrue(node1.isLinkedWith(node2));
        assertTrue(node2.isLinkedWith(node1));
        assertFalse(node1.isLinkedWith(node3));
        assertFalse(node2.isLinkedWith(node3));

        // Teste mit null Mirror
        MirrorNode nodeWithoutMirror = new MirrorNode(8, 0);
        assertFalse(node1.isLinkedWith(nodeWithoutMirror));
        assertFalse(nodeWithoutMirror.isLinkedWith(node1));
    }

    @Test
    @DisplayName("Link Management - addLink und removeLink")
    void testLinkManagement() {
        Mirror mirror1 = new Mirror(109, 0, props);
        Mirror mirror2 = new Mirror(110, 0, props);
        mirrorNode.setMirror(mirror1);

        Link link1 = new Link(5, mirror1, mirror2, 0, props);
        Link link2 = new Link(6, mirror1, mirror2, 0, props);

        // Teste addLink
        mirrorNode.addLink(link1);
        assertEquals(1, mirrorNode.getNumTargetLinks());
        assertTrue(mirrorNode.getAllLinks().contains(link1));

        mirrorNode.addLink(link2);
        assertEquals(2, mirrorNode.getNumTargetLinks());
        assertTrue(mirrorNode.getAllLinks().contains(link2));

        // Teste removeLink
        mirrorNode.removeLink(link1);
        assertEquals(1, mirrorNode.getNumTargetLinks());
        assertFalse(mirrorNode.getAllLinks().contains(link1));
        assertTrue(mirrorNode.getAllLinks().contains(link2));

        mirrorNode.removeLink(link2);
        assertEquals(0, mirrorNode.getNumTargetLinks());
        assertTrue(mirrorNode.getAllLinks().isEmpty());
    }

    @Test
    @DisplayName("MirrorNode Management - addMirrorNode und removeMirrorNode")
    void testMirrorNodeManagement() {
        MirrorNode otherNode1 = new MirrorNode(10, 1);
        MirrorNode otherNode2 = new MirrorNode(11, 1);

        assertEquals(0, mirrorNode.getNumMirrors());

        // Teste addMirrorNode
        mirrorNode.addMirrorNode(otherNode1);
        assertEquals(1, mirrorNode.getNumMirrors());
        assertTrue(mirrorNode.getAllMirrors().contains(otherNode1));

        // Teste doppeltes Hinzufügen
        mirrorNode.addMirrorNode(otherNode1);
        assertEquals(1, mirrorNode.getNumMirrors()); // Sollte nicht doppelt hinzugefügt werden

        mirrorNode.addMirrorNode(otherNode2);
        assertEquals(2, mirrorNode.getNumMirrors());

        // Teste removeMirrorNode
        mirrorNode.removeMirrorNode(otherNode1);
        assertEquals(1, mirrorNode.getNumMirrors());
        assertFalse(mirrorNode.getAllMirrors().contains(otherNode1));
        assertTrue(mirrorNode.getAllMirrors().contains(otherNode2));
    }

    @Test
    @DisplayName("Edge Cases und Null-Handling")
    void testEdgeCasesAndNullHandling() {
        // Teste mit null Mirror
        assertNull(mirrorNode.getMirror());
        assertEquals(0, mirrorNode.getNumTargetLinks());

        // Teste mit null Network
        assertDoesNotThrow(() -> {
            Set<Link> links = mirrorNode.createAndLinkMirrors(null, new ArrayList<>(), 0, props);
            assertNotNull(links);
        });

        // Teste isLinkedWith mit null
        assertFalse(mirrorNode.isLinkedWith(null));

        // Teste toString ohne Mirror
        String str = mirrorNode.toString();
        assertNotNull(str);
        assertTrue(str.contains("mirrorId=null"));
    }

    @Test
    @DisplayName("Integration mit echter Simulation")
    void testIntegrationWithRealSimulation() throws IOException {
        initSimulator();
        sim.initialize(new BalancedTreeTopologyStrategy());
        sim.getEffector().setMirrors(3, 0);

        for(int t = 1; t <= 10; t++) {
            sim.runStep(t);
        }

        MirrorProbe mirrorProbe = getMirrorProbe();
        assertNotNull(mirrorProbe);
        List<Mirror> mirrors = mirrorProbe.getMirrors();
        assertFalse(mirrors.isEmpty());

        // Teste mit echten Mirrors
        Mirror realMirror = mirrors.get(0);
        MirrorNode nodeWithRealMirror = new MirrorNode(100, 0, realMirror);

        assertEquals(realMirror.getID(), nodeWithRealMirror.getMirror().getID());
        assertEquals(realMirror.getLinks().size(), nodeWithRealMirror.getNumTargetLinks());

        // Teste toString mit echtem Mirror
        String str = nodeWithRealMirror.toString();
        assertNotNull(str);
        assertTrue(str.contains("mirrorId=" + realMirror.getID()));
    }

    // Hilfsmethoden
    private MirrorProbe getMirrorProbe() {
        for(Probe p : sim.getProbes()) {
            if(p instanceof MirrorProbe) return (MirrorProbe)p;
        }
        return null;
    }
}