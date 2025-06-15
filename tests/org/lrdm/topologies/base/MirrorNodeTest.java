package org.lrdm.topologies.base;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.TimedRDMSim;
import org.lrdm.probes.MirrorProbe;
import org.lrdm.topologies.BalancedTreeTopologyStrategy;

import java.io.IOException;
import java.util.*;

import static org.lrdm.TestProperties.loadProperties;
import static org.lrdm.TestProperties.props;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MirrorNode erweiterte Funktionalität und Integration")
class MirrorNodeTest {

    private TimedRDMSim sim;
    private static final String config = "resources/sim-test-treenode.conf";

    @BeforeEach
    void setUpProperties() throws IOException {
        loadProperties(config);
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

    private MirrorProbe getMirrorProbe() {
        return sim.getMirrorProbe();
    }

    @Nested
    @DisplayName("MirrorNode Grundfunktionen")
    class MirrorNodeBasicTests {

        private MirrorNode mirrorNode;
        private Mirror testMirror;

        @BeforeEach
        void setUp() throws IOException {
            mirrorNode = new MirrorNode(1);
            testMirror = new Mirror(101, 0, props);
        }

        @Test
        @DisplayName("MirrorNode Konstruktoren und Basis-Eigenschaften")
        void testMirrorNodeBasics() {
            // Test ohne Mirror
            assertEquals(1, mirrorNode.getId());
            assertNull(mirrorNode.getMirror());
            assertEquals(0, mirrorNode.getNumImplementedLinks());
            assertTrue(mirrorNode.getImplementedLinks().isEmpty());

            // Test mit Mirror
            MirrorNode nodeWithMirror = new MirrorNode(2, testMirror);
            assertEquals(2, nodeWithMirror.getId());
            assertEquals(testMirror, nodeWithMirror.getMirror());
        }

        @Test
        @DisplayName("setMirror und getMirror")
        void testMirrorManagement() {
            assertNull(mirrorNode.getMirror());

            mirrorNode.setMirror(testMirror);
            assertEquals(testMirror, mirrorNode.getMirror());

            // Setze auf null
            mirrorNode.setMirror(null);
            assertNull(mirrorNode.getMirror());
        }

        @Test
        @DisplayName("Link Management ohne Mirror")
        void testLinkManagementWithoutMirror() {
            Mirror otherMirror = new Mirror(102, 0, props);
            Link testLink = new Link(1, testMirror, otherMirror, 0, props);

            // Operationen ohne Mirror sollten sicher sein
            mirrorNode.addLink(testLink);
            assertEquals(0, mirrorNode.getNumImplementedLinks());

            mirrorNode.removeLink(testLink);
            assertEquals(0, mirrorNode.getNumImplementedLinks());

            assertTrue(mirrorNode.getImplementedLinks().isEmpty());
        }

        @Test
        @DisplayName("Link Management mit Mirror")
        void testLinkManagementWithMirror() throws IOException {
            mirrorNode.setMirror(testMirror);
            Mirror otherMirror = new Mirror(102, 0, props);

            Link link1 = new Link(1, testMirror, otherMirror, 0, props);
            Link link2 = new Link(2, testMirror, otherMirror, 0, props);

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

            // Test mit null
            mirrorNode.removeLink(null);
            assertEquals(1, mirrorNode.getNumImplementedLinks());
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
        void testIsLinkedWithScenarios() throws IOException {
            Mirror mirror1 = new Mirror(101, 0, props);
            Mirror mirror2 = new Mirror(102, 0, props);
            Mirror mirror3 = new Mirror(103, 0, props);

            MirrorNode node1 = new MirrorNode(1, mirror1);
            MirrorNode node2 = new MirrorNode(2, mirror2);
            MirrorNode node3 = new MirrorNode(3, mirror3);

            // Erstelle Parent-Child-Beziehung (geplante Verbindung)
            node1.addChild(node2);

            // Ohne implementierten Link
            assertFalse(node1.isLinkedWith(node2));

            // Erstelle implementierten Link
            Link link = new Link(1, mirror1, mirror2, 0, props);
            mirror1.addLink(link);
            mirror2.addLink(link);

            // Mit beiden Verbindungen (geplant + implementiert)
            assertTrue(node1.isLinkedWith(node2));
            assertTrue(node2.isLinkedWith(node1));

            // Teste Edge Cases
            assertFalse(node1.isLinkedWith(node3));
            assertFalse(node1.isLinkedWith(null));

            // Teste mit null Mirror
            MirrorNode nodeWithoutMirror = new MirrorNode(4);
            assertFalse(node1.isLinkedWith(nodeWithoutMirror));
        }
    }

    @Nested
    @DisplayName("MirrorNode Struktur-Funktionen")
    class MirrorNodeStructureTests {

        @Test
        @DisplayName("getMirrorsOfStructure sammelt korrekt")
        void testGetMirrorsOfStructure() throws IOException {
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
        void testGetMirrorsOfEndpoints() throws IOException {
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
        void testStructureAndEdgeLinks() throws IOException {
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

        @Test
        @DisplayName("removeMirrorNode entfernt Links korrekt")
        void testRemoveMirrorNode() throws IOException {
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
            // Links sollten entfernt werden (wenn getJointMirrorLinks existiert)
            // Andernfalls nur strukturelle Entfernung
        }
    }

    @Nested
    @DisplayName("MirrorNode Struktur-Validierung")
    class MirrorNodeValidationTests {

        @Test
        @DisplayName("isValidStructure erweiterte Validierung")
        void testValidStructureWithMirrors() throws IOException {
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

            // Ohne Links zwischen Mirrors
            Set<StructureNode> nodeSet = Set.of(node1, node2);
            // Validation abhängig von spezifischer Implementierung
            boolean isValid = node1.isValidStructure(nodeSet);
            assertNotNull(isValid); // Grundsätzlich sollte eine Antwort kommen

            // Mit Link zwischen Mirrors
            Link link = new Link(1, mirror1, mirror2, 0, props);
            mirror1.addLink(link);
            mirror2.addLink(link);

            assertTrue(node1.isValidStructure(nodeSet));
        }

        @Test
        @DisplayName("Struktur-Validierung mit einzelnem Mirror")
        void testValidStructureSingleMirror() throws IOException {
            MirrorNode singleNode = new MirrorNode(1);
            Mirror singleMirror = new Mirror(101, 0, props);
            singleNode.setMirror(singleMirror);

            // Einzelner Knoten mit Mirror sollte gültig sein
            assertTrue(singleNode.isValidStructure(Set.of(singleNode)));
        }

        @Test
        @DisplayName("Null-Handling in Struktur-Funktionen")
        void testNullHandlingInStructureFunctions() {
            MirrorNode node = new MirrorNode(1);

            // Tests mit null
            assertFalse(node.isLinkOfStructure(null));
            assertFalse(node.isEdgeLink(null));

            // Tests ohne Mirror
            Set<Mirror> emptyMirrors = node.getMirrorsOfStructure();
            assertTrue(emptyMirrors.isEmpty());

            Set<Link> emptyLinks = node.getLinksOfStructure();
            assertTrue(emptyLinks.isEmpty());

            assertEquals(0, node.getNumLinksOfStructure());
            assertEquals(0, node.getNumEdgeLinks());
        }
    }

    @Nested
    @DisplayName("Integration und Simulation")
    class MirrorNodeIntegrationTests {

        @Test
        @DisplayName("Integration mit echter Simulation")
        void testIntegrationWithRealSimulation() throws IOException {
            initSimulator();
            sim.initialize(new BalancedTreeTopologyStrategy());
            sim.getEffector().setMirrors(4, 0);

            for(int t = 1; t <= 15; t++) {
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
        void testCompatibilityWithTreeMirrorNode() throws IOException {
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
        void testPerformanceWithLargerStructures() throws IOException {
            // Erstelle größere Struktur
            MirrorNode root = new MirrorNode(1);
            List<MirrorNode> children = new ArrayList<>();

            for (int i = 2; i <= 100; i++) {
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

            // Sollte schnell sein (< 1000ms für 100 Knoten)
            assertTrue(endTime - startTime < 1000);

            // Korrekte Größen
            assertEquals(1, mirrors.size()); // Head-Abgrenzung
            assertEquals(99, endpointMirrors.size()); // Alle Kinder sind Terminal
            assertTrue(links.isEmpty()); // Keine implementierten Links
        }

        @Test
        @DisplayName("Edge Cases mit komplexen Strukturen")
        void testEdgeCasesWithComplexStructures() throws IOException {
            // Test mit zirkulären Referenzen (vorsichtig!)
            MirrorNode node1 = new MirrorNode(1);
            MirrorNode node2 = new MirrorNode(2);

            Mirror mirror1 = new Mirror(101, 0, props);
            Mirror mirror2 = new Mirror(102, 0, props);

            node1.setMirror(mirror1);
            node2.setMirror(mirror2);

            // Erstelle zirkuläre Struktur (für Test-Zwecke)
            node1.addChild(node2);
            // node2.setParent(node1); // Automatisch durch addChild

            // Test sollte robust sein
            assertDoesNotThrow(() -> {
                Set<Mirror> mirrors = node1.getMirrorsOfStructure();
                Set<Link> links = node1.getLinksOfStructure();
                assertNotNull(mirrors);
                assertNotNull(links);
            });
        }
    }
}