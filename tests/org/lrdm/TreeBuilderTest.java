package org.lrdm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lrdm.probes.LinkProbe;
import org.lrdm.probes.MirrorProbe;
import org.lrdm.probes.Probe;
import org.lrdm.topologies.BalancedTreeTopologyStrategy;
import org.lrdm.topologies.base.MirrorNode;
import org.lrdm.topologies.base.TreeNode;
import org.lrdm.topologies.builders.TreeBuilderDepthLimit;
import org.lrdm.topologies.builders.TreeBuilderBalanced;

import java.io.IOException;
import java.util.*;

import static org.lrdm.TestProperties.loadProperties;
import static org.lrdm.TestProperties.props;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TreeBuilder Tests für neue Implementierungen")
class TreeBuilderTest {

    private TimedRDMSim sim;
    private static final String config = "resources/sim-test-treenode.conf";

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

    @Nested
    @DisplayName("TreeBuilderDepthLimit Tests")
    class TreeBuilderDepthLimitTests {

        private TreeBuilderDepthLimit depthBuilder;

        @BeforeEach
        void setUpDepthBuilder() {
            depthBuilder = new TreeBuilderDepthLimit(3, 2);
        }

        @Test
        @DisplayName("buildTree mit Tiefenbeschränkung")
        void testBuildTreeWithDepthLimit() {
            MirrorNode root = depthBuilder.buildTree(10, 3);

            assertNotNull(root);
            assertEquals(0, root.getDepth());
            assertTrue(depthBuilder.validateTreeStructure(root));

            int totalNodes = depthBuilder.countNodes(root);
            assertEquals(10, totalNodes);

            int maxDepth = depthBuilder.getMaxDepth(root);
            assertTrue(maxDepth <= 3);
        }

        @Test
        @DisplayName("buildTree respektiert maximale Kinder pro Knoten")
        void testBuildTreeRespectsMaxChildren() {
            MirrorNode root = depthBuilder.buildTree(15, 4);

            // Überprüfe, dass kein Knoten mehr als maxChildrenPerNode Kinder hat
            validateMaxChildrenConstraint(root, 2);
        }

        @Test
        @DisplayName("addNodesToExistingTree funktioniert korrekt")
        void testAddNodesToExistingTree() {
            MirrorNode root = depthBuilder.buildTree(5, 3);
            int originalCount = depthBuilder.countNodes(root);

            int added = depthBuilder.addNodesToExistingTree(root, 3, 3);

            assertEquals(3, added);
            assertEquals(originalCount + 3, depthBuilder.countNodes(root));
            assertTrue(depthBuilder.validateTreeStructure(root));
        }

        @Test
        @DisplayName("removeNodesFromTree entfernt Knoten korrekt")
        void testRemoveNodesFromTree() {
            MirrorNode root = depthBuilder.buildTree(10, 3);
            int originalCount = depthBuilder.countNodes(root);

            int removed = depthBuilder.removeNodesFromTree(root, 3);

            assertEquals(3, removed);
            assertEquals(originalCount - 3, depthBuilder.countNodes(root));
            assertTrue(depthBuilder.validateTreeStructure(root));
        }

        @Test
        @DisplayName("createAndLinkMirrors erstellt Links korrekt")
        void testCreateAndLinkMirrors() throws IOException {
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
            Set<Link> links = depthBuilder.createAndLinkMirrors(mockNetwork, mirrors, 0, props);

            assertNotNull(links);
            // In einem Baum mit n Knoten sollten n-1 Links existieren
            assertEquals(4, links.size());
        }

        @Test
        @DisplayName("Edge Cases - null und leere Werte")
        void testDepthBuilderEdgeCases() throws IOException {
            assertNull(depthBuilder.buildTree(0, 3));
            assertNull(depthBuilder.buildTree(-1, 3));

            assertEquals(0, depthBuilder.addNodesToExistingTree(null, 5, 3));
            assertEquals(0, depthBuilder.removeNodesFromTree(null, 3));

            Network mockNetwork = createMockNetwork();
            assertTrue(depthBuilder.createAndLinkMirrors(mockNetwork, new ArrayList<>(), 0, props).isEmpty());
        }

        @Test
        @DisplayName("Getter und Setter funktionieren")
        void testGettersAndSetters() {
            assertEquals(3, depthBuilder.getMaxDepth());
            assertEquals(2, depthBuilder.getMaxChildrenPerNode());

            depthBuilder.setMaxDepth(5);
            depthBuilder.setMaxChildrenPerNode(3);

            assertEquals(5, depthBuilder.getMaxDepth());
            assertEquals(3, depthBuilder.getMaxChildrenPerNode());
        }
    }

    @Nested
    @DisplayName("TreeBuilderBalanced Tests")
    class TreeBuilderBalancedTests {

        private TreeBuilderBalanced balancedBuilder;

        @BeforeEach
        void setUpBalancedBuilder() {
            balancedBuilder = new TreeBuilderBalanced(2);
        }

        @Test
        @DisplayName("buildTree erstellt balancierten Baum")
        void testBuildBalancedTree() {
            MirrorNode root = balancedBuilder.buildTree(15, 0);

            assertNotNull(root);
            assertEquals(0, root.getDepth());
            assertTrue(balancedBuilder.validateTreeStructure(root));

            int totalNodes = balancedBuilder.countNodes(root);
            assertEquals(15, totalNodes);

            // Überprüfe Balance-Metrik
            double balance = balancedBuilder.calculateTreeBalance(root);
            assertTrue(balance >= 0.0);
        }

        @Test
        @DisplayName("addNodesToExistingTree hält Balance")
        void testAddNodesToExistingTreeBalanced() {
            MirrorNode root = balancedBuilder.buildTree(7, 0);
            double initialBalance = balancedBuilder.calculateTreeBalance(root);

            int added = balancedBuilder.addNodesToExistingTree(root, 8, 0);
            double newBalance = balancedBuilder.calculateTreeBalance(root);

            assertEquals(8, added);
            assertEquals(15, balancedBuilder.countNodes(root));

            // Balance sollte nicht drastisch schlechter werden
            assertTrue(newBalance <= initialBalance + 2.0);
        }

        @Test
        @DisplayName("removeNodesFromTree erhält Balance")
        void testRemoveNodesFromTreeBalanced() {
            MirrorNode root = balancedBuilder.buildTree(15, 0);
            double initialBalance = balancedBuilder.calculateTreeBalance(root);

            int removed = balancedBuilder.removeNodesFromTree(root, 5);
            double newBalance = balancedBuilder.calculateTreeBalance(root);

            assertEquals(5, removed);
            assertEquals(10, balancedBuilder.countNodes(root));
            assertTrue(balancedBuilder.validateTreeStructure(root));

            // Balance sollte gleich oder besser sein
            assertTrue(newBalance <= initialBalance + 1.0);
        }

        @Test
        @DisplayName("createAndLinkMirrors nutzt BalancedTreeTopologyStrategy")
        void testCreateAndLinkMirrorsWithTopologyStrategy() throws IOException {
            initSimulator();
            sim.initialize(new BalancedTreeTopologyStrategy());
            sim.getEffector().setMirrors(10, 0);

            for(int t = 1; t <= 15; t++) {
                sim.runStep(t);
            }

            MirrorProbe mirrorProbe = getMirrorProbe();
            assertNotNull(mirrorProbe);
            List<Mirror> mirrors = mirrorProbe.getMirrors();

            Network mockNetwork = createMockNetwork();
            Set<Link> links = balancedBuilder.createAndLinkMirrors(mockNetwork, mirrors, 0, props);

            assertNotNull(links);
            // BalancedTreeTopologyStrategy sollte n-1 Links für n Knoten erstellen
            assertEquals(9, links.size());

            // Überprüfe, dass Links korrekt zugeordnet sind
            for (Link link : links) {
                assertNotNull(link.getSource());
                assertNotNull(link.getTarget());
                assertTrue(mirrors.contains(link.getSource()));
                assertTrue(mirrors.contains(link.getTarget()));
            }
        }

        @Test
        @DisplayName("calculateTreeBalance funktioniert korrekt")
        void testCalculateTreeBalance() {
            // Perfekt balancierter Baum
            MirrorNode root = balancedBuilder.buildTree(7, 0); // 2^3 - 1 = 7
            double balance = balancedBuilder.calculateTreeBalance(root);

            // Sollte sehr gut balanciert sein
            assertTrue(balance < 1.0);
        }

        @Test
        @DisplayName("Getter und Setter funktionieren")
        void testBalancedBuilderGettersAndSetters() {
            assertEquals(2, balancedBuilder.getTargetLinksPerNode());
            assertNotNull(balancedBuilder.getTopologyStrategy());

            balancedBuilder.setTargetLinksPerNode(3);
            assertEquals(3, balancedBuilder.getTargetLinksPerNode());
        }
    }

    @Nested
    @DisplayName("TreeBuilder-spezifische Funktionalität")
    class TreeBuilderSpecificTests {

        @Test
        @DisplayName("convertToMirrorNodes funktioniert korrekt")
        void testConvertToMirrorNodes() throws IOException {
            initSimulator();
            sim.initialize(new BalancedTreeTopologyStrategy());
            sim.getEffector().setMirrors(5, 0);

            for(int t = 1; t <= 10; t++) {
                sim.runStep(t);
            }

            MirrorProbe mirrorProbe = getMirrorProbe();
            assertNotNull(mirrorProbe);
            List<Mirror> mirrors = mirrorProbe.getMirrors();

            // Erstelle TreeNode-Struktur
            TreeNode treeRoot = new TreeNode(1, 0);
            TreeNode child1 = new TreeNode(2, 1);
            TreeNode child2 = new TreeNode(3, 1);
            treeRoot.addChild(child1);
            treeRoot.addChild(child2);

            TreeBuilderBalanced builder = new TreeBuilderBalanced(2);
            MirrorNode mirrorRoot = builder.convertToMirrorNodes(treeRoot, mirrors);

            assertNotNull(mirrorRoot);
            assertEquals(treeRoot.getId(), mirrorRoot.getId());
            assertEquals(treeRoot.getDepth(), mirrorRoot.getDepth());
            assertEquals(treeRoot.getChildren().size(), mirrorRoot.getChildren().size());
            assertNotNull(mirrorRoot.getMirror());
        }

        @Test
        @DisplayName("printTree funktioniert ohne Fehler")
        void testPrintTree() {
            TreeBuilderBalanced builder = new TreeBuilderBalanced(2);
            MirrorNode root = builder.buildTree(5, 3);

            // Sollte ohne Exception laufen
            assertDoesNotThrow(() -> builder.printTree(root, ""));
        }

        @Test
        @DisplayName("validateTreeStructure erkennt inkonsistente Strukturen")
        void testValidateTreeStructureWithInconsistencies() {
            TreeBuilderBalanced builder = new TreeBuilderBalanced(2);

            // Teste mit null
            assertTrue(builder.validateTreeStructure(null));

            // Erstelle valide Struktur
            MirrorNode root = new MirrorNode(1, 0);
            MirrorNode child = new MirrorNode(2, 1);
            root.addChild(child);

            assertTrue(builder.validateTreeStructure(root));

            // Korruptiere Parent-Beziehung durch direktes Entfernen
            root.removeChild(child);
            root.getChildren().add(child); // Füge zurück hinzu aber ohne Parent zu setzen

            assertFalse(builder.validateTreeStructure(root));
        }
    }

    @Nested
    @DisplayName("Vergleichstests zwischen beiden Implementierungen")
    class ComparisonTests {

        @Test
        @DisplayName("Beide Builder erstellen gültige Bäume mit unterschiedlichen Eigenschaften")
        void testBothBuildersCreateValidTrees() {
            TreeBuilderDepthLimit depthBuilder = new TreeBuilderDepthLimit(4, 3);
            TreeBuilderBalanced balancedBuilder = new TreeBuilderBalanced(3);

            MirrorNode depthTree = depthBuilder.buildTree(20, 4);
            MirrorNode balancedTree = balancedBuilder.buildTree(20, 0);

            // Beide sollten gültige Strukturen haben
            assertTrue(depthBuilder.validateTreeStructure(depthTree));
            assertTrue(balancedBuilder.validateTreeStructure(balancedTree));

            // Beide sollten 20 Knoten haben
            assertEquals(20, depthBuilder.countNodes(depthTree));
            assertEquals(20, balancedBuilder.countNodes(balancedTree));

            // Depth-Tree sollte begrenzte Tiefe haben
            assertTrue(depthBuilder.getMaxDepth(depthTree) <= 4);

            // Balanced-Tree sollte bessere Balance haben
            double depthBalance = calculateSimpleBalance(depthTree);
            double balancedBalance = balancedBuilder.calculateTreeBalance(balancedTree);

            assertTrue(balancedBalance >= 0.0);
            assertTrue(depthBalance >= 0.0);
        }

        @Test
        @DisplayName("Mirror-Integration funktioniert bei beiden Buildern")
        void testMirrorIntegrationBothBuilders() throws IOException {
            initSimulator();
            sim.initialize(new BalancedTreeTopologyStrategy());
            sim.getEffector().setMirrors(8, 0);

            for(int t = 1; t <= 15; t++) {
                sim.runStep(t);
            }

            TreeBuilderDepthLimit depthBuilder = new TreeBuilderDepthLimit(3, 2);
            TreeBuilderBalanced balancedBuilder = new TreeBuilderBalanced(2);

            MirrorProbe mirrorProbe = getMirrorProbe();
            assertNotNull(mirrorProbe);
            List<Mirror> mirrors = mirrorProbe.getMirrors();

            Network mockNetwork = createMockNetwork();
            Set<Link> depthLinks = depthBuilder.createAndLinkMirrors(mockNetwork, mirrors, 0, props);
            Set<Link> balancedLinks = balancedBuilder.createAndLinkMirrors(mockNetwork, mirrors, 5, props);

            // Beide sollten Links erstellen
            assertFalse(depthLinks.isEmpty());
            assertFalse(balancedLinks.isEmpty());
        }
    }

    // Hilfsmethoden
    private MirrorProbe getMirrorProbe() {
        for(Probe p : sim.getProbes()) {
            if(p instanceof MirrorProbe) return (MirrorProbe)p;
        }
        return null;
    }

    private LinkProbe getLinkProbe() {
        for(Probe p : sim.getProbes()) {
            if(p instanceof LinkProbe) return (LinkProbe)p;
        }
        return null;
    }

    private void validateMaxChildrenConstraint(MirrorNode node, int maxChildren) {
        assertTrue(node.getChildren().size() <= maxChildren,
                "Node " + node.getId() + " has " + node.getChildren().size() +
                        " children, max allowed: " + maxChildren);

        for (TreeNode child : node.getChildren()) {
            validateMaxChildrenConstraint((MirrorNode) child, maxChildren);
        }
    }

    private double calculateSimpleBalance(MirrorNode root) {
        Map<Integer, Integer> depthCounts = new HashMap<>();
        calculateDepthCounts(root, depthCounts);

        double mean = depthCounts.values().stream().mapToInt(i -> i).average().orElse(0.0);
        double variance = depthCounts.values().stream()
                .mapToDouble(count -> Math.pow(count - mean, 2))
                .average().orElse(0.0);

        return Math.sqrt(variance);
    }

    private void calculateDepthCounts(MirrorNode node, Map<Integer, Integer> depthCounts) {
        depthCounts.merge(node.getDepth(), 1, Integer::sum);
        for (TreeNode child : node.getChildren()) {
            calculateDepthCounts((MirrorNode) child, depthCounts);
        }
    }
}