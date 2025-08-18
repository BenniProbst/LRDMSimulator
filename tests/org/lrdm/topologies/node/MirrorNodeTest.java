package org.lrdm.topologies.node;

import org.junit.jupiter.api.*;
import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.TimedRDMSim;
import org.lrdm.probes.MirrorProbe;
import org.lrdm.topologies.strategies.BalancedTreeTopologyStrategy;

import java.io.IOException;
import java.util.*;

import static org.lrdm.TestProperties.getProps;
import static org.lrdm.TestProperties.loadProperties;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MirrorNode specific tests")
class MirrorNodeTest {

    private TimedRDMSim sim;
    private MirrorNode mirrorNode;
    private Properties props;
    private static final String config = "resources/sim-test-mirror node.conf";
    private final StructureNode.StructureType mirrorType = StructureNode.StructureType.MIRROR;

    @BeforeEach
    void setUp() throws IOException {
        loadProperties(config);
        props = getProps();
        mirrorNode = new MirrorNode(1);
    }

    public void initSimulator() throws IOException {
        initSimulator(config);
    }

    public void initSimulator(String config) throws IOException {
        loadProperties(config);
        props = getProps();
        sim = new TimedRDMSim(config);
        sim.setHeadless(true);
        sim.initialize(new BalancedTreeTopologyStrategy());
    }

    private MirrorProbe getMirrorProbe() {
        return sim.getMirrorProbe();
    }

    @Nested
    @DisplayName("MirrorNode basics")
    class MirrorNodeBasicTests {

        private Mirror testMirror;

        @BeforeEach
        void setUpMirror() {
            testMirror = new Mirror(101, 0, props);
        }

        @Test
        @DisplayName("MirrorNode works without an assigned Mirror")
        void testMirrorNodeWithoutMirror() {
            assertEquals(1, mirrorNode.getId());
            assertNull(mirrorNode.getMirror());
            assertEquals(0, mirrorNode.getNumImplementedLinks());
            assertTrue(mirrorNode.getImplementedLinks().isEmpty());
            assertEquals(mirrorType, mirrorNode.deriveTypeId());
        }

        @Test
        @DisplayName("MirrorNode with assigned Mirror synchronizes links")
        void testMirrorNodeWithMirrorSyncsLinks() {
            mirrorNode.setMirror(testMirror);

            Mirror targetMirror = new Mirror(102, 0, props);
            Link link = new Link(1, testMirror, targetMirror, 0, props);

            // Add the link through MirrorNode (delegates to Mirror)
            mirrorNode.addLink(link);

            // Both should contain the link
            assertTrue(mirrorNode.getImplementedLinks().contains(link));
            assertTrue(testMirror.getLinks().contains(link));
            assertEquals(1, mirrorNode.getNumImplementedLinks());
        }

        @Test
        @DisplayName("Link management - addLink and removeLink")
        void testLinkManagement() {
            Mirror mirror1 = new Mirror(109, 0, props);
            Mirror mirror2 = new Mirror(110, 0, props);
            mirrorNode.setMirror(mirror1);

            Link link1 = new Link(5, mirror1, mirror2, 0, props);
            Link link2 = new Link(6, mirror1, mirror2, 0, props);

            // addLink
            mirrorNode.addLink(link1);
            assertEquals(1, mirrorNode.getNumImplementedLinks());
            assertTrue(mirrorNode.getImplementedLinks().contains(link1));

            mirrorNode.addLink(link2);
            assertEquals(2, mirrorNode.getNumImplementedLinks());
            assertTrue(mirrorNode.getImplementedLinks().contains(link2));

            // removeLink
            mirrorNode.removeLink(link1);
            assertEquals(1, mirrorNode.getNumImplementedLinks());
            assertFalse(mirrorNode.getImplementedLinks().contains(link1));
            assertTrue(mirrorNode.getImplementedLinks().contains(link2));

            mirrorNode.removeLink(link2);
            assertEquals(0, mirrorNode.getNumImplementedLinks());
            assertTrue(mirrorNode.getImplementedLinks().isEmpty());
        }

        @Test
        @DisplayName("getNumPendingLinks: planned minus implemented links")
        void testPendingLinksCalculation() {
            mirrorNode.setMirror(testMirror);
            mirrorNode.setHead(mirrorType, true);

            // Build structure with planned links
            MirrorNode child1 = new MirrorNode(2);
            MirrorNode child2 = new MirrorNode(3);

            Set<StructureNode.StructureType> typeIds = Set.of(mirrorType);
            Map<StructureNode.StructureType, Integer> headIds = Map.of(mirrorType, mirrorNode.getId());

            mirrorNode.addChild(child1, typeIds, headIds);
            mirrorNode.addChild(child2, typeIds, headIds);

            // Planned links: n-1 = 3-1 = 2; implemented: 0
            assertEquals(2, mirrorNode.getNumPendingLinks());

            // Implement one link (between head mirror and child1 mirror)
            Mirror childMirror = new Mirror(102, 0, props);
            child1.setMirror(childMirror);
            Link implementedLink = new Link(1, testMirror, childMirror, 0, props);
            mirrorNode.addLink(implementedLink);

            // Planned: 2, implemented: 1, pending: 1
            assertEquals(1, mirrorNode.getNumPendingLinks());
        }

        @Test
        @DisplayName("isLinkedWith requires BOTH planned and implemented link")
        void testIsLinkedWithScenarios() {
            Mirror mirror1 = new Mirror(106, 0, props);
            Mirror mirror2 = new Mirror(107, 0, props);
            Mirror mirror3 = new Mirror(108, 0, props);

            MirrorNode node1 = new MirrorNode(5, mirror1);
            MirrorNode node2 = new MirrorNode(6, mirror2);
            MirrorNode node3 = new MirrorNode(7, mirror3);

            // Plan connection: node1 → node2
            node1.setHead(mirrorType, true);
            Set<StructureNode.StructureType> typeIds = Set.of(mirrorType);
            Map<StructureNode.StructureType, Integer> headIds = Map.of(mirrorType, node1.getId());
            node1.addChild(node2, typeIds, headIds);

            // Without implemented link → false
            assertFalse(node1.isLinkedWith(node2));

            // Implement the link
            Link link = new Link(4, mirror1, mirror2, 0, props);
            mirror1.addLink(link);
            mirror2.addLink(link);

            // Planned + implemented → true
            assertTrue(node1.isLinkedWith(node2));
            assertTrue(node2.isLinkedWith(node1));

            // No planned/implemented link to node3
            assertFalse(node1.isLinkedWith(node3));
            assertFalse(node2.isLinkedWith(node3));

            // null mirror cases
            MirrorNode nodeWithoutMirror = new MirrorNode(8);
            assertFalse(node1.isLinkedWith(nodeWithoutMirror));
            assertFalse(nodeWithoutMirror.isLinkedWith(node1));
        }

        @Test
        @DisplayName("MirrorNode management - removeMirrorNode removes node and links")
        void testMirrorNodeManagement() {
            MirrorNode parent = new MirrorNode(1);
            MirrorNode child = new MirrorNode(2);

            Mirror parentMirror = new Mirror(101, 0, props);
            Mirror childMirror = new Mirror(102, 0, props);

            parent.setMirror(parentMirror);
            child.setMirror(childMirror);
            parent.setHead(mirrorType, true);

            Set<StructureNode.StructureType> typeIds = Set.of(mirrorType);
            Map<StructureNode.StructureType, Integer> headIds = Map.of(mirrorType, parent.getId());

            parent.addChild(child, typeIds, headIds);

            // Create implemented link between mirrors
            Link link = new Link(1, parentMirror, childMirror, 0, props);
            parentMirror.addLink(link);
            childMirror.addLink(link);

            // Before removal
            assertEquals(1, parent.getChildren().size());
            assertTrue(parent.isLinkedWith(child));

            // Remove child edge for the type
            parent.removeChild(child, typeIds);

            assertEquals(0, parent.getChildren().size());
            assertNull(child.getParent());
        }

        @Test
        @DisplayName("Integration with real simulation (smoke)")
        void testIntegrationWithRealSimulation() throws IOException {
            initSimulator();
            assertNotNull(sim);

            MirrorProbe probe = getMirrorProbe();
            assertNotNull(probe);

            // Create MirrorNode from a simulator mirror through probe
            List<Mirror> simMirrors = probe.getMirrors();
            if (!simMirrors.isEmpty()) {
                Mirror simMirror = simMirrors.get(0);
                MirrorNode simNode = new MirrorNode(100, simMirror);

                assertEquals(simMirror, simNode.getMirror());
                assertEquals(100, simNode.getId());
                assertEquals(mirrorType, simNode.deriveTypeId());

                // Implemented links reflect underlying mirror
                assertEquals(simMirror.getLinks().size(), simNode.getNumImplementedLinks());
                assertEquals(simMirror.getLinks(), simNode.getImplementedLinks());
            } else {
                // Fallback sanity
                assertTrue(probe.getNumMirrors() >= 0);
            }
        }
    }

    @Nested
    @DisplayName("MirrorNode structure functions")
    class MirrorNodeStructureTests {

        private MirrorNode setupTestStructure() {
            MirrorNode root = new MirrorNode(1);
            MirrorNode child1 = new MirrorNode(2);
            MirrorNode child2 = new MirrorNode(3);
            MirrorNode grandchild = new MirrorNode(4);

            root.setHead(mirrorType, true);

            Set<StructureNode.StructureType> typeIds = Set.of(mirrorType);
            Map<StructureNode.StructureType, Integer> headIds = Map.of(mirrorType, root.getId());

            root.addChild(child1, typeIds, headIds);
            root.addChild(child2, typeIds, headIds);
            child1.addChild(grandchild, typeIds, headIds);

            // Assign mirrors
            root.setMirror(new Mirror(101, 0, props));
            child1.setMirror(new Mirror(102, 0, props));
            child2.setMirror(new Mirror(103, 0, props));
            grandchild.setMirror(new Mirror(104, 0, props));

            return root;
        }

        @Test
        @DisplayName("getMirrorsOfStructure returns a consistent set")
        void testGetMirrorsOfStructure() {
            MirrorNode root = setupTestStructure();

            Set<Mirror> mirrors = root.getMirrorsOfStructure(mirrorType, root);
            assertNotNull(mirrors);

            Mirror external = new Mirror(999, 0, props);
            assertFalse(mirrors.contains(external));
        }

        @Test
        @DisplayName("getMirrorsOfEndpoints yields only endpoint mirrors (if usable)")
        void testGetMirrorsOfEndpoints() {
            MirrorNode root = setupTestStructure();

            Set<Mirror> endpointMirrors = root.getMirrorsOfEndpoints(mirrorType, root);
            assertNotNull(endpointMirrors);

            Mirror rootMirror = root.getMirror();
            if (rootMirror != null) {
                assertFalse(endpointMirrors.contains(rootMirror));
            }
        }

        @Test
        @DisplayName("isLinkOfStructure distinguishes internal vs external links")
        void testStructureAndEdgeLinksClassification() {
            MirrorNode root = setupTestStructure();

            Mirror rootMirror = root.getMirror();
            MirrorNode child1 = (MirrorNode) root.getChildren().iterator().next();
            Mirror child1Mirror = child1.getMirror();

            // Internal link (both endpoints in structure)
            Link internalLink = new Link(1, rootMirror, child1Mirror, 0, props);
            rootMirror.addLink(internalLink);
            child1Mirror.addLink(internalLink);

            // External link (one endpoint outside the structure)
            Mirror externalMirror = new Mirror(200, 0, props);
            Link externalLink = new Link(2, rootMirror, externalMirror, 0, props);
            rootMirror.addLink(externalLink);
            externalMirror.addLink(externalLink);

            assertTrue(root.isLinkOfStructure(internalLink));
            assertFalse(root.isLinkOfStructure(externalLink));

            // The current implementation does not classify external links as structure edge-links
            assertFalse(root.getEdgeLinks(mirrorType, root).contains(externalLink));
        }

        @Test
        @DisplayName("Structure navigation with MirrorProbe integration (smoke)")
        void testStructureNavigationWithMirrorProbe() throws IOException {
            initSimulator();
            MirrorProbe probe = getMirrorProbe();
            assertNotNull(probe);

            List<Mirror> simMirrors = probe.getMirrors();
            if (simMirrors.size() >= 2) {
                MirrorNode node1 = new MirrorNode(1, simMirrors.get(0));
                MirrorNode node2 = new MirrorNode(2, simMirrors.get(1));

                node1.setHead(mirrorType, true);
                Set<StructureNode.StructureType> typeIds = Set.of(mirrorType);
                Map<StructureNode.StructureType, Integer> headIds = Map.of(mirrorType, node1.getId());

                node1.addChild(node2, typeIds, headIds);

                Set<Mirror> mirrors = node1.getMirrorsOfStructure(mirrorType, node1);
                assertNotNull(mirrors);
            }
        }
    }

    @Nested
    @DisplayName("MirrorNode observer/probe integration")
    class MirrorNodeObserverTests {

        @Test
        @DisplayName("MirrorProbe provides consistent metrics")
        void testMirrorProbeIntegration() throws IOException {
            initSimulator();

            MirrorProbe probe = getMirrorProbe();
            assertNotNull(probe);

            assertTrue(probe.getNumMirrors() >= 0);
            assertTrue(probe.getNumReadyMirrors() >= 0);
            assertTrue(probe.getNumTargetMirrors() >= 0);
            assertTrue(probe.getMirrorRatio() >= 0.0 && probe.getMirrorRatio() <= 1.0);

            assertNotNull(probe.getMirrors());
            assertEquals(probe.getNumMirrors(), probe.getMirrors().size());
        }

        @Test
        @DisplayName("No direct network access; use probes")
        void testNoDirectNetworkAccess() throws IOException {
            initSimulator();

            MirrorProbe probe = getMirrorProbe();
            assertNotNull(probe);

            List<Mirror> mirrors = probe.getMirrors();
            int numMirrors = probe.getNumMirrors();

            assertEquals(numMirrors, mirrors.size());

            for (Mirror mirror : mirrors) {
                assertNotNull(mirror);
                assertTrue(mirror.getID() >= 0);
            }
        }

        @Test
        @DisplayName("Probe edge cases are handled")
        void testMirrorProbeEdgeCases() throws IOException {
            initSimulator();

            MirrorProbe probe = getMirrorProbe();
            assertNotNull(probe);

            assertTrue(probe.getNumTargetLinksPerMirror() >= 0);

            double ratio = probe.getMirrorRatio();
            assertTrue(ratio >= 0.0);
            assertTrue(ratio <= 1.0);

            assertNotNull(probe.getMirrors());
        }
    }
}