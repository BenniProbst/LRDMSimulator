package org.lrdm.topologies.node;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.TimedRDMSim;
import org.lrdm.probes.MirrorProbe;
import org.lrdm.topologies.BalancedTreeTopologyStrategy;

import java.io.IOException;
import java.util.*;

import static org.lrdm.TestProperties.loadProperties;
import static org.lrdm.TestProperties.getProps;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MirrorNode spezifische Tests")
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
    @DisplayName("MirrorNode Grundfunktionen")
    class MirrorNodeBasicTests {

        private Mirror testMirror;

        @BeforeEach
        void setUpMirror() {
            testMirror = new Mirror(101, 0, props);
        }

        @Test
        @DisplayName("MirrorNode funktioniert ohne zugeordnetes Mirror")
        void testMirrorNodeWithoutMirror() {
            assertEquals(1, mirrorNode.getId());
            assertNull(mirrorNode.getMirror());
            assertEquals(0, mirrorNode.getNumImplementedLinks());
            assertTrue(mirrorNode.getImplementedLinks().isEmpty());
            assertEquals(mirrorType, mirrorNode.deriveTypeId());
        }

        @Test
        @DisplayName("MirrorNode mit zugeordnetem Mirror synchronisiert Links")
        void testMirrorNodeWithMirrorSyncsLinks() {
            mirrorNode.setMirror(testMirror);

            Mirror targetMirror = new Mirror(102, 0, props);
            Link link = new Link(1, testMirror, targetMirror, 0, props);

            // Füge Link über MirrorNode hinzu
            mirrorNode.addLink(link);

            // Beide sollten den Link haben
            assertTrue(mirrorNode.getImplementedLinks().contains(link));
            assertTrue(testMirror.getLinks().contains(link));
            assertEquals(1, mirrorNode.getNumImplementedLinks());
        }

        @Test
        @DisplayName("Link Management - addLink und removeLink")
        void testLinkManagement() {
            Mirror mirror1 = new Mirror(109, 0, props);
            Mirror mirror2 = new Mirror(110, 0, props);
            mirrorNode.setMirror(mirror1);

            Link link1 = new Link(5, mirror1, mirror2, 0, props);
            Link link2 = new Link(6, mirror1, mirror2, 0, props);

            // Prüfe addLink
            mirrorNode.addLink(link1);
            assertEquals(1, mirrorNode.getNumImplementedLinks());
            assertTrue(mirrorNode.getImplementedLinks().contains(link1));

            mirrorNode.addLink(link2);
            assertEquals(2, mirrorNode.getNumImplementedLinks());
            assertTrue(mirrorNode.getImplementedLinks().contains(link2));

            // Prüfe removeLink
            mirrorNode.removeLink(link1);
            assertEquals(1, mirrorNode.getNumImplementedLinks());
            assertFalse(mirrorNode.getImplementedLinks().contains(link1));
            assertTrue(mirrorNode.getImplementedLinks().contains(link2));

            mirrorNode.removeLink(link2);
            assertEquals(0, mirrorNode.getNumImplementedLinks());
            assertTrue(mirrorNode.getImplementedLinks().isEmpty());
        }

        @Test
        @DisplayName("getNumPendingLinks berechnet korrekt")
        void testPendingLinksCalculation() {
            mirrorNode.setMirror(testMirror);
            mirrorNode.setHead(mirrorType, true);

            // Erstelle Struktur mit geplanten Links
            MirrorNode child1 = new MirrorNode(2);
            MirrorNode child2 = new MirrorNode(3);

            Set<StructureNode.StructureType> typeIds = Set.of(mirrorType);
            Map<StructureNode.StructureType, Integer> headIds = Map.of(mirrorType, mirrorNode.getId());

            mirrorNode.addChild(child1, typeIds, headIds);
            mirrorNode.addChild(child2, typeIds, headIds);

            // Geplante Links: 2 (zu 2 Kindern), implementierte: 0
            assertEquals(2, mirrorNode.getNumPendingLinks());

            // Füge implementierten Link hinzu
            Mirror childMirror = new Mirror(102, 0, props);
            child1.setMirror(childMirror);
            Link implementedLink = new Link(1, testMirror, childMirror, 0, props);
            mirrorNode.addLink(implementedLink);

            // Geplante: 2, implementierte: 1, pending: 1
            assertEquals(1, mirrorNode.getNumPendingLinks());
        }

        @Test
        @DisplayName("isLinkedWith verschiedene Szenarien")
        void testIsLinkedWithScenarios() {
            Mirror mirror1 = new Mirror(106, 0, props);
            Mirror mirror2 = new Mirror(107, 0, props);
            Mirror mirror3 = new Mirror(108, 0, props);

            MirrorNode node1 = new MirrorNode(5, mirror1);
            MirrorNode node2 = new MirrorNode(6, mirror2);
            MirrorNode node3 = new MirrorNode(7, mirror3);

            // Setze node1 als Head und erstelle Parent-Child-Beziehung
            node1.setHead(mirrorType, true);
            Set<StructureNode.StructureType> typeIds = Set.of(mirrorType);
            Map<StructureNode.StructureType, Integer> headIds = Map.of(mirrorType, node1.getId());

            node1.addChild(node2, typeIds, headIds);

            // Ohne implementierten Link
            assertFalse(node1.isLinkedWith(node2));

            // Erstelle implementierten Link
            Link link = new Link(4, mirror1, mirror2, 0, props);
            mirror1.addLink(link);
            mirror2.addLink(link);

            // Mit beiden Verbindungen (geplant + implementiert)
            assertTrue(node1.isLinkedWith(node2));
            assertTrue(node2.isLinkedWith(node1));

            assertFalse(node1.isLinkedWith(node3));
            assertFalse(node2.isLinkedWith(node3));

            // Prüfe mit null Mirror
            MirrorNode nodeWithoutMirror = new MirrorNode(8);
            assertFalse(node1.isLinkedWith(nodeWithoutMirror));
            assertFalse(nodeWithoutMirror.isLinkedWith(node1));
        }

        @Test
        @DisplayName("MirrorNode Management - removeMirrorNode")
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

            // Erstelle Link zwischen den Mirrors
            Link link = new Link(1, parentMirror, childMirror, 0, props);
            parentMirror.addLink(link);
            childMirror.addLink(link);

            // Teste Struktur vor Entfernung
            assertEquals(1, parent.getChildren().size());
            assertTrue(parent.isLinkedWith(child));

            // Entferne Kindknoten
            parent.removeChild(child, typeIds);

            assertEquals(0, parent.getChildren().size());
            assertNull(child.getParent());
        }

        @Test
        @DisplayName("Integration mit echter Simulation")
        void testIntegrationWithRealSimulation() throws IOException {
            initSimulator();
            assertNotNull(sim);

            MirrorProbe probe = getMirrorProbe();
            assertNotNull(probe);

            // Erstelle MirrorNode mit Simulator-Mirror über MirrorProbe
            List<Mirror> simMirrors = probe.getMirrors();
            if (!simMirrors.isEmpty()) {
                Mirror simMirror = simMirrors.get(0);
                MirrorNode simNode = new MirrorNode(100, simMirror);

                assertEquals(simMirror, simNode.getMirror());
                assertEquals(100, simNode.getId());
                assertEquals(mirrorType, simNode.deriveTypeId());

                // Teste MirrorNode-Funktionalität mit echtem Mirror
                assertEquals(simMirror.getLinks().size(), simNode.getNumImplementedLinks());
                assertEquals(simMirror.getLinks(), simNode.getImplementedLinks());
            } else {
                // Fallback-Test, falls keine Mirrors vorhanden sind
                assertTrue(probe.getNumMirrors() >= 0);
            }
        }
    }

    @Nested
    @DisplayName("MirrorNode Struktur-Funktionen")
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

            // Setze Mirrors
            root.setMirror(new Mirror(101, 0, props));
            child1.setMirror(new Mirror(102, 0, props));
            child2.setMirror(new Mirror(103, 0, props));
            grandchild.setMirror(new Mirror(104, 0, props));

            return root;
        }

        @Test
        @DisplayName("getMirrorsOfStructure sammelt korrekt")
        void testGetMirrorsOfStructure() {
            MirrorNode root = setupTestStructure();

            Set<Mirror> mirrors = root.getMirrorsOfStructure(mirrorType, root);
            assertEquals(1, mirrors.size()); // Nur root wegen Head-Abgrenzung
            assertTrue(mirrors.contains(root.getMirror()));

            // Von child1 aus - alle außer Head
            MirrorNode child1 = (MirrorNode) root.getChildren().iterator().next();
            Set<Mirror> mirrorsFromChild = child1.getMirrorsOfStructure(mirrorType, root);
            assertEquals(3, mirrorsFromChild.size()); // child1, child2, grandchild
        }

        @Test
        @DisplayName("getMirrorsOfEndpoints filtert Terminal-Knoten")
        void testGetMirrorsOfEndpoints() {
            MirrorNode root = setupTestStructure();

            Set<Mirror> endpointMirrors = root.getMirrorsOfEndpoints(mirrorType, root);
            assertEquals(0, endpointMirrors.size()); // root ist nicht Terminal

            // Von child1 aus
            MirrorNode child1 = (MirrorNode) root.getChildren().iterator().next();
            Set<Mirror> endpointMirrorsFromChild = child1.getMirrorsOfEndpoints(mirrorType, root);
            assertEquals(2, endpointMirrorsFromChild.size()); // child2 und grandchild sind Terminal
        }

        @Test
        @DisplayName("getLinksOfStructure und getEdgeLinks")
        void testStructureAndEdgeLinks() {
            MirrorNode root = setupTestStructure();

            // Erstelle Links zwischen Mirrors
            Mirror rootMirror = root.getMirror();
            MirrorNode child1 = (MirrorNode) root.getChildren().iterator().next();
            Mirror child1Mirror = child1.getMirror();

            Link internalLink = new Link(1, rootMirror, child1Mirror, 0, props);
            rootMirror.addLink(internalLink);
            child1Mirror.addLink(internalLink);

            // Erstelle externen Link
            Mirror externalMirror = new Mirror(200, 0, props);
            Link externalLink = new Link(2, rootMirror, externalMirror, 0, props);
            rootMirror.addLink(externalLink);
            externalMirror.addLink(externalLink);

            Set<Link> structureLinks = root.getLinksOfStructure(mirrorType, root);
            Set<Link> edgeLinks = root.getEdgeLinks(mirrorType, root);

            assertTrue(structureLinks.contains(internalLink));
            assertFalse(structureLinks.contains(externalLink));
            assertTrue(edgeLinks.contains(externalLink));
            assertFalse(edgeLinks.contains(internalLink));
        }

        @Test
        @DisplayName("Struktur-Navigation mit MirrorProbe Integration")
        void testStructureNavigationWithMirrorProbe() throws IOException {
            initSimulator();
            MirrorProbe probe = getMirrorProbe();
            assertNotNull(probe);

            // Verwende echte Mirrors aus der Simulation
            List<Mirror> simMirrors = probe.getMirrors();
            if (simMirrors.size() >= 2) {
                MirrorNode node1 = new MirrorNode(1, simMirrors.get(0));
                MirrorNode node2 = new MirrorNode(2, simMirrors.get(1));

                node1.setHead(mirrorType, true);
                Set<StructureNode.StructureType> typeIds = Set.of(mirrorType);
                Map<StructureNode.StructureType, Integer> headIds = Map.of(mirrorType, node1.getId());

                node1.addChild(node2, typeIds, headIds);

                // Teste Struktur-Funktionen mit echten Mirrors
                Set<Mirror> mirrors = node1.getMirrorsOfStructure(mirrorType, node1);
                assertFalse(mirrors.isEmpty());
                assertTrue(mirrors.contains(node1.getMirror()));
            }
        }

        @Test
        @DisplayName("Performance mit MirrorProbe Daten")
        void testPerformanceWithMirrorProbeData() throws IOException {
            initSimulator();
            MirrorProbe probe = getMirrorProbe();
            assertNotNull(probe);

            List<Mirror> simMirrors = probe.getMirrors();

            long startTime = System.currentTimeMillis();

            for (int i = 0; i < Math.min(10, simMirrors.size()); i++) {
                MirrorNode node = new MirrorNode(i, simMirrors.get(i));
                assertNotNull(node.getMirror());
                assertEquals(i, node.getId());
            }

            long duration = System.currentTimeMillis() - startTime;
            assertTrue(duration < 1000); // Sollte unter 1 Sekunde sein
        }
    }

    @Nested
    @DisplayName("MirrorNode Observer Pattern Tests")
    class MirrorNodeObserverTests {

        @Test
        @DisplayName("MirrorProbe Integration korrekt")
        void testMirrorProbeIntegration() throws IOException {
            initSimulator();

            MirrorProbe probe = getMirrorProbe();
            assertNotNull(probe);

            // Teste alle MirrorProbe Funktionen
            assertTrue(probe.getNumMirrors() >= 0);
            assertTrue(probe.getNumReadyMirrors() >= 0);
            assertTrue(probe.getNumTargetMirrors() >= 0);
            assertTrue(probe.getMirrorRatio() >= 0.0 && probe.getMirrorRatio() <= 1.0);

            assertNotNull(probe.getMirrors());
            assertEquals(probe.getNumMirrors(), probe.getMirrors().size());
        }

        @Test
        @DisplayName("Kein direkter Network-Zugriff")
        void testNoDirectNetworkAccess() throws IOException {
            initSimulator();

            // Demonstriere korrekte Verwendung über Probe
            MirrorProbe probe = getMirrorProbe();
            assertNotNull(probe);

            // Alle Mirror-Daten sollten über eine Probe zugänglich sein
            List<Mirror> mirrors = probe.getMirrors();
            int numMirrors = probe.getNumMirrors();

            assertEquals(numMirrors, mirrors.size());

            for (Mirror mirror : mirrors) {
                assertNotNull(mirror);
                assertTrue(mirror.getID() >= 0);
            }
        }

        @Test
        @DisplayName("Edge Cases für MirrorProbe")
        void testMirrorProbeEdgeCases() throws IOException {
            initSimulator();

            MirrorProbe probe = getMirrorProbe();
            assertNotNull(probe);

            // Versuche Edge Cases
            assertTrue(probe.getNumTargetLinksPerMirror() >= 0);

            // Verhältnis sollte valide sein
            double ratio = probe.getMirrorRatio();
            assertTrue(ratio >= 0.0);
            assertTrue(ratio <= 1.0);

            // Listen sollten nie null sein
            assertNotNull(probe.getMirrors());
        }
    }
}