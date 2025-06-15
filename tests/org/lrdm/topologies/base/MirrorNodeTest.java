package org.lrdm.topologies.base;

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
    private static final String config = "resources/sim-test-mirrornode.conf";

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

            // Teste addLink
            mirrorNode.addLink(link1);
            assertEquals(1, mirrorNode.getNumImplementedLinks());
            assertTrue(mirrorNode.getImplementedLinks().contains(link1));

            mirrorNode.addLink(link2);
            assertEquals(2, mirrorNode.getNumImplementedLinks());
            assertTrue(mirrorNode.getImplementedLinks().contains(link2));

            // Teste removeLink
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

            // Erstelle Struktur mit geplanten Links
            MirrorNode child1 = new MirrorNode(2);
            MirrorNode child2 = new MirrorNode(3);
            mirrorNode.addChild(child1);
            mirrorNode.addChild(child2);

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

            // Erstelle Parent-Child-Beziehung (geplante Verbindung)
            node1.addChild(node2);

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

            // Teste mit null Mirror
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
            parent.addChild(child);

            // Erstelle Link zwischen den Mirrors
            Link link = new Link(1, parentMirror, childMirror, 0, props);
            parentMirror.addLink(link);
            childMirror.addLink(link);

            assertEquals(1, parent.getChildren().size());
            assertTrue(parentMirror.getLinks().contains(link));
            assertTrue(childMirror.getLinks().contains(link));

            // Entferne MirrorNode
            parent.removeMirrorNode(child);

            assertEquals(0, parent.getChildren().size());
            assertNull(child.getParent());
        }
    }

    @Nested
    @DisplayName("MirrorNode Struktur-Funktionen")
    class MirrorNodeStructureTests {

        @Test
        @DisplayName("getMirrorsOfStructure sammelt korrekt")
        void testGetMirrorsOfStructure() {
            MirrorNode root = new MirrorNode(1);
            MirrorNode child1 = new MirrorNode(2);
            MirrorNode child2 = new MirrorNode(3);

            root.setHead(true);
            root.addChild(child1);
            root.addChild(child2);

            // Setze Mirrors
            Mirror rootMirror = new Mirror(101, 0, props);
            Mirror childMirror1 = new Mirror(102, 0, props);
            Mirror childMirror2 = new Mirror(103, 0, props);

            root.setMirror(rootMirror);
            child1.setMirror(childMirror1);
            child2.setMirror(childMirror2);

            // Von root aus (Head-Abgrenzung)
            Set<Mirror> rootMirrors = root.getMirrorsOfStructure();
            assertEquals(1, rootMirrors.size());
            assertTrue(rootMirrors.contains(rootMirror));

            // Von child1 aus
            Set<Mirror> childMirrors = child1.getMirrorsOfStructure();
            assertEquals(2, childMirrors.size());
            assertTrue(childMirrors.contains(childMirror1));
            assertTrue(childMirrors.contains(childMirror2));
            assertFalse(childMirrors.contains(rootMirror)); // Head-Abgrenzung
        }

        @Test
        @DisplayName("getMirrorsOfEndpoints filtert Terminal-Knoten")
        void testGetMirrorsOfEndpoints() {
            MirrorNode root = new MirrorNode(1);
            MirrorNode child1 = new MirrorNode(2);
            MirrorNode grandchild = new MirrorNode(3);

            root.setHead(true);
            root.addChild(child1);
            child1.addChild(grandchild);

            // Setze Mirrors
            Mirror rootMirror = new Mirror(101, 0, props);
            Mirror childMirror = new Mirror(102, 0, props);
            Mirror grandchildMirror = new Mirror(103, 0, props);

            root.setMirror(rootMirror);
            child1.setMirror(childMirror);
            grandchild.setMirror(grandchildMirror);

            // Von child1 aus: nur grandchild ist Terminal
            Set<Mirror> endpointMirrors = child1.getMirrorsOfEndpoints();
            assertEquals(1, endpointMirrors.size());
            assertTrue(endpointMirrors.contains(grandchildMirror));
        }

        @Test
        @DisplayName("getLinksOfStructure und getEdgeLinks")
        void testStructureAndEdgeLinks() {
            MirrorNode node1 = new MirrorNode(1);
            MirrorNode node2 = new MirrorNode(2);

            Mirror mirror1 = new Mirror(101, 0, props);
            Mirror mirror2 = new Mirror(102, 0, props);
            Mirror externalMirror = new Mirror(103, 0, props);

            node1.setMirror(mirror1);
            node2.setMirror(mirror2);
            node1.addChild(node2);

            // Interner Link
            Link internalLink = new Link(1, mirror1, mirror2, 0, props);
            mirror1.addLink(internalLink);
            mirror2.addLink(internalLink);

            // Edge Link
            Link edgeLink = new Link(2, mirror1, externalMirror, 0, props);
            mirror1.addLink(edgeLink);
            externalMirror.addLink(edgeLink);

            // Test Structure Links
            Set<Link> structureLinks = node1.getLinksOfStructure();
            assertEquals(1, structureLinks.size());
            assertTrue(structureLinks.contains(internalLink));
            assertFalse(structureLinks.contains(edgeLink));

            // Test Edge Links
            Set<Link> edgeLinks = node1.getEdgeLinks();
            assertEquals(1, edgeLinks.size());
            assertTrue(edgeLinks.contains(edgeLink));
            assertFalse(edgeLinks.contains(internalLink));

            // Test Helper Methods
            assertTrue(node1.isLinkOfStructure(internalLink));
            assertFalse(node1.isLinkOfStructure(edgeLink));
            assertTrue(node1.isEdgeLink(edgeLink));
            assertFalse(node1.isEdgeLink(internalLink));

            // Test Counting
            assertEquals(1, node1.getNumLinksOfStructure());
            assertEquals(1, node1.getNumEdgeLinks());
        }
    }

    @Nested
    @DisplayName("MirrorNode Struktur-Validierung")
    class MirrorNodeValidationTests {

        @Test
        @DisplayName("isValidStructure erweiterte Validierung")
        void testValidStructureWithMirrors() {
            MirrorNode node1 = new MirrorNode(1);
            MirrorNode node2 = new MirrorNode(2);
            MirrorNode isolated = new MirrorNode(3);

            // Test ohne Mirrors (sollte StructureNode-Validierung verwenden)
            node1.addChild(node2);
            assertTrue(node1.isValidStructure(Set.of(node1, node2)));
            assertFalse(node1.isValidStructure(Set.of(node1, node2, isolated)));

            // Test mit Mirrors
            Mirror mirror1 = new Mirror(101, 0, props);
            Mirror mirror2 = new Mirror(102, 0, props);

            node1.setMirror(mirror1);
            node2.setMirror(mirror2);

            // Mit Link zwischen Mirrors
            Link link = new Link(1, mirror1, mirror2, 0, props);
            mirror1.addLink(link);
            mirror2.addLink(link);

            Set<StructureNode> nodeSet = Set.of(node1, node2);
            assertTrue(node1.isValidStructure(nodeSet));
        }

        @Test
        @DisplayName("Struktur-Validierung mit einzelnem Mirror")
        void testValidStructureSingleMirror() {
            MirrorNode singleNode = new MirrorNode(1);
            Mirror singleMirror = new Mirror(101, 0, props);
            singleNode.setMirror(singleMirror);

            // Einzelner Knoten mit Mirror sollte gültig sein
            assertTrue(singleNode.isValidStructure(Set.of(singleNode)));
        }
    }

    @Nested
    @DisplayName("Integration und Edge Cases")
    class MirrorNodeIntegrationTests {

        @Test
        @DisplayName("Edge Cases und Null-Handling")
        void testEdgeCasesAndNullHandling() {
            // Teste mit null Mirror
            assertNull(mirrorNode.getMirror());
            assertEquals(0, mirrorNode.getNumImplementedLinks());

            // Teste isLinkedWith mit null
            assertFalse(mirrorNode.isLinkedWith(null));

            // Tests ohne Mirror
            Set<Mirror> emptyMirrors = mirrorNode.getMirrorsOfStructure();
            assertTrue(emptyMirrors.isEmpty());

            Set<Link> emptyLinks = mirrorNode.getLinksOfStructure();
            assertTrue(emptyLinks.isEmpty());

            assertEquals(0, mirrorNode.getNumLinksOfStructure());
            assertEquals(0, mirrorNode.getNumEdgeLinks());

            // Tests mit null
            assertFalse(mirrorNode.isLinkOfStructure(null));
            assertFalse(mirrorNode.isEdgeLink(null));
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
            MirrorNode nodeWithRealMirror = new MirrorNode(100, realMirror);

            assertEquals(realMirror.getID(), nodeWithRealMirror.getMirror().getID());
            assertEquals(realMirror.getLinks().size(), nodeWithRealMirror.getNumImplementedLinks());

            // Teste Links
            Set<Link> implementedLinks = nodeWithRealMirror.getImplementedLinks();
            assertEquals(realMirror.getLinks().size(), implementedLinks.size());
        }

        @Test
        @DisplayName("MirrorNode mit TreeMirrorNode Kompatibilität")
        void testCompatibilityWithTreeMirrorNode() {
            // Teste, dass MirrorNode als Basis für TreeMirrorNode funktioniert
            TreeMirrorNode treeNode = new TreeMirrorNode(1);
            Mirror treeMirror = new Mirror(101, 0, props);
            treeNode.setMirror(treeMirror);

            // Grundlegende MirrorNode-Funktionen sollten funktionieren
            assertEquals(treeMirror, treeNode.getMirror());
            assertEquals(0, treeNode.getNumImplementedLinks());
            assertEquals(0, treeNode.getNumPendingLinks());

            // Struktur-Funktionen
            Set<Mirror> mirrors = treeNode.getMirrorsOfStructure();
            assertEquals(1, mirrors.size());
            assertTrue(mirrors.contains(treeMirror));
        }

        @Test
        @DisplayName("Performance bei größeren Strukturen")
        void testPerformanceWithLargerStructures() {
            // Erstelle größere Struktur
            MirrorNode root = new MirrorNode(1);
            List<MirrorNode> children = new ArrayList<>();

            for (int i = 2; i <= 25; i++) {
                MirrorNode child = new MirrorNode(i);
                Mirror childMirror = new Mirror(100 + i, 0, props);
                child.setMirror(childMirror);
                children.add(child);
                root.addChild(child);
            }

            // Teste Performance-kritische Operationen
            long startTime = System.currentTimeMillis();

            Set<Mirror> mirrors = root.getMirrorsOfStructure();
            Set<Mirror> endpointMirrors = root.getMirrorsOfEndpoints();
            Set<Link> links = root.getLinksOfStructure();

            long endTime = System.currentTimeMillis();

            // Sollte schnell sein (< 1000ms für 25 Knoten)
            assertTrue(endTime - startTime < 1000);

            // Korrekte Größen
            assertEquals(1, mirrors.size()); // Head-Abgrenzung
            assertEquals(24, endpointMirrors.size()); // Alle Kinder sind Terminal
            assertTrue(links.isEmpty()); // Keine implementierten Links
        }

        @Test
        @DisplayName("RingMirrorNode Kompatibilität")
        void testCompatibilityWithRingMirrorNode() {
            // Teste, dass MirrorNode als Basis für RingMirrorNode funktioniert
            RingMirrorNode ringNode = new RingMirrorNode(1);
            Mirror ringMirror = new Mirror(101, 0, props);
            ringNode.setMirror(ringMirror);

            // Grundlegende MirrorNode-Funktionen sollten funktionieren
            assertEquals(ringMirror, ringNode.getMirror());
            assertEquals(0, ringNode.getNumImplementedLinks());
            assertEquals(0, ringNode.getNumPendingLinks());

            // Struktur-Funktionen
            Set<Mirror> mirrors = ringNode.getMirrorsOfStructure();
            assertEquals(1, mirrors.size());
            assertTrue(mirrors.contains(ringMirror));

            // Ring-spezifische Funktionen
            assertTrue(ringNode.canAcceptMoreChildren()); // Ohne Struktur sollte es möglich sein
        }
    }
}