package org.lrdm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lrdm.probes.LinkProbe;
import org.lrdm.probes.MirrorProbe;
import org.lrdm.probes.Probe;
import org.lrdm.topologies.BalancedTreeTopologyStrategy;
import org.lrdm.util.MirrorNode;
import org.lrdm.util.TreeNode;

import java.io.IOException;
import java.util.*;

import static org.lrdm.TestProperties.loadProperties;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TreeNode und MirrorNode Tests")
class TreeNodeTest {

    private TimedRDMSim sim;
    private static final String config = "resources/sim-test-treenode.conf";

    public void initSimulator() throws IOException {
        initSimulator(config);
    }

    public void initSimulator(String config) throws IOException {
        loadProperties(config);
        sim = new TimedRDMSim(config);
        // sim.setHeadless(true);
    }

    @Nested
    @DisplayName("TreeNode Basis-Funktionalität")
    class TreeNodeBasicTests {

        private TreeNode rootNode;

        @BeforeEach
        void setUp() {
            rootNode = new TreeNode(1, 0);
        }

        @Test
        @DisplayName("Konstruktor sollte korrekte Werte setzen")
        void testConstructor() {
            assertEquals(1, rootNode.getId());
            assertEquals(0, rootNode.getDepth());
            assertNull(rootNode.getParent());
            assertTrue(rootNode.getChildren().isEmpty());
            assertTrue(rootNode.isLeaf());
            assertTrue(rootNode.isRoot());
        }

        @Test
        @DisplayName("addChild sollte Kind korrekt hinzufügen und Parent setzen")
        void testAddChild() {
            TreeNode child = new TreeNode(2, 1);
            
            rootNode.addChild(child);
            
            assertEquals(1, rootNode.getChildren().size());
            assertEquals(child, rootNode.getChildren().get(0));
            assertEquals(rootNode, child.getParent());
            assertFalse(rootNode.isLeaf());
            assertTrue(child.isLeaf());
            assertFalse(child.isRoot());
        }

        @Test
        @DisplayName("removeChild sollte Kind korrekt entfernen")
        void testRemoveChild() {
            TreeNode child = new TreeNode(2, 1);
            rootNode.addChild(child);
            
            rootNode.removeChild(child);
            
            assertTrue(rootNode.getChildren().isEmpty());
            assertNull(child.getParent());
            assertTrue(rootNode.isLeaf());
        }

        @Test
        @DisplayName("getDescendantCount sollte alle Nachfahren zählen")
        void testGetDescendantCount() {
            TreeNode child1 = new TreeNode(2, 1);
            TreeNode child2 = new TreeNode(3, 1);
            TreeNode grandchild = new TreeNode(4, 2);
            
            rootNode.addChild(child1);
            rootNode.addChild(child2);
            child1.addChild(grandchild);
            
            assertEquals(3, rootNode.getDescendantCount()); // 2 Kinder + 1 Enkelkind
            assertEquals(1, child1.getDescendantCount()); // 1 Enkelkind
            assertEquals(0, child2.getDescendantCount()); // Keine Kinder
            assertEquals(0, grandchild.getDescendantCount()); // Keine Kinder
        }

        @Test
        @DisplayName("findNodeById sollte Knoten korrekt finden")
        void testFindNodeById() {
            TreeNode child1 = new TreeNode(2, 1);
            TreeNode child2 = new TreeNode(3, 1);
            TreeNode grandchild = new TreeNode(4, 2);
            
            rootNode.addChild(child1);
            rootNode.addChild(child2);
            child1.addChild(grandchild);
            
            assertEquals(rootNode, rootNode.findNodeById(1));
            assertEquals(child1, rootNode.findNodeById(2));
            assertEquals(child2, rootNode.findNodeById(3));
            assertEquals(grandchild, rootNode.findNodeById(4));
            assertNull(rootNode.findNodeById(999));
        }

        @Test
        @DisplayName("getPathFromRoot sollte korrekten Pfad zurückgeben")
        void testGetPathFromRoot() {
            TreeNode child1 = new TreeNode(2, 1);
            TreeNode grandchild = new TreeNode(3, 2);
            
            rootNode.addChild(child1);
            child1.addChild(grandchild);
            
            List<TreeNode> rootPath = rootNode.getPathFromRoot();
            assertEquals(1, rootPath.size());
            assertEquals(rootNode, rootPath.get(0));
            
            List<TreeNode> childPath = child1.getPathFromRoot();
            assertEquals(2, childPath.size());
            assertEquals(rootNode, childPath.get(0));
            assertEquals(child1, childPath.get(1));
            
            List<TreeNode> grandchildPath = grandchild.getPathFromRoot();
            assertEquals(3, grandchildPath.size());
            assertEquals(rootNode, grandchildPath.get(0));
            assertEquals(child1, grandchildPath.get(1));
            assertEquals(grandchild, grandchildPath.get(2));
        }

        @Test
        @DisplayName("equals und hashCode funktionieren korrekt")
        void testEqualsAndHashCode() {
            TreeNode node1 = new TreeNode(1, 0);
            TreeNode node2 = new TreeNode(1, 0);
            TreeNode node3 = new TreeNode(2, 0);
            
            assertEquals(node1, node2);
            assertNotEquals(node1, node3);
            assertEquals(node1.hashCode(), node2.hashCode());
            assertNotEquals(node1.hashCode(), node3.hashCode());
        }
    }

    @Nested
    @DisplayName("MirrorNode Erweiterte Funktionalität")
    class MirrorNodeTests {

        private MirrorNode mirrorRoot;

        @BeforeEach
        void setUp() {
            mirrorRoot = new MirrorNode(1, 0);
        }

        @Test
        @DisplayName("MirrorNode Konstruktor")
        void testMirrorNodeConstructor() {
            assertEquals(1, mirrorRoot.getId());
            assertEquals(0, mirrorRoot.getDepth());
            assertNull(mirrorRoot.getMirror());
            assertEquals(0, mirrorRoot.getNumTargetLinks());
            assertEquals(0, mirrorRoot.getNumMirrors());
            assertTrue(mirrorRoot.getAllLinks().isEmpty());
            assertTrue(mirrorRoot.getAllMirrors().isEmpty());
        }

        @Test
        @DisplayName("MirrorNode mit Mirror-Zuordnung über Simulation")
        void testMirrorNodeWithMirrorFromSimulation() throws IOException {
            initSimulator();
            sim.initialize(new BalancedTreeTopologyStrategy());
            sim.getEffector().setMirrors(3, 0);
            sim.runStep(1);
            
            MirrorProbe mirrorProbe = getMirrorProbe();
            assertNotNull(mirrorProbe);
            
            List<Mirror> mirrors = mirrorProbe.getMirrors();
            assertFalse(mirrors.isEmpty());
            
            Mirror mirror = mirrors.get(0);
            MirrorNode nodeWithMirror = new MirrorNode(2, 1, mirror);
            
            assertEquals(mirror, nodeWithMirror.getMirror());
            assertEquals(mirror.getID(), nodeWithMirror.getMirror().getID());
        }

        @Test
        @DisplayName("setMirror und getMirror funktionieren mit echten Mirrors")
        void testSetAndGetMirrorWithRealMirror() throws IOException {
            initSimulator();
            sim.initialize(new BalancedTreeTopologyStrategy());
            sim.getEffector().setMirrors(2, 0);
            sim.runStep(1);

            MirrorProbe mirrorProbe = getMirrorProbe();
            assertNotNull(mirrorProbe);
            Mirror firstMirror = mirrorProbe.getMirrors().get(0);

            mirrorRoot.setMirror(firstMirror);

            assertEquals(firstMirror, mirrorRoot.getMirror());
        }

        @Test
        @DisplayName("addLink und removeLink funktionieren mit echten Links")
        void testAddAndRemoveLinkWithRealLinks() throws IOException {
            initSimulator();
            sim.initialize(new BalancedTreeTopologyStrategy());
            sim.getEffector().setMirrors(5, 0);

            // Lasse Simulation laufen um Links zu erstellen
            for(int t = 1; t <= 10; t++) {
                sim.runStep(t);
            }

            LinkProbe linkProbe = getLinkProbe();
            assertNotNull(linkProbe);

            Set<Link> links = linkProbe.getLinks();
            if (!links.isEmpty()) {
                Link link = links.iterator().next();

                mirrorRoot.addLink(link);

                assertEquals(1, mirrorRoot.getAllLinks().size());
                assertTrue(mirrorRoot.getAllLinks().contains(link));

                mirrorRoot.removeLink(link);

                assertEquals(0, mirrorRoot.getAllLinks().size());
                assertFalse(mirrorRoot.getAllLinks().contains(link));
            }
        }


        @Test
        @DisplayName("addPendingLinks und confirmPendingLinks funktionieren")
        void testPendingLinks() {
            assertEquals(0, mirrorRoot.getPendingLinks());
            
            mirrorRoot.addPendingLinks(3);
            assertEquals(3, mirrorRoot.getPendingLinks());
            assertEquals(3, mirrorRoot.getPredictedNumTargetLinks());
            
            mirrorRoot.confirmPendingLinks(2);
            assertEquals(1, mirrorRoot.getPendingLinks());
            assertEquals(1, mirrorRoot.getPredictedNumTargetLinks());
            
            mirrorRoot.confirmPendingLinks(5); // Mehr als vorhanden
            assertEquals(0, mirrorRoot.getPendingLinks());
        }

        @Test
        @DisplayName("addMirrorNode und removeMirrorNode funktionieren")
        void testMirrorNodeManagement() {
            MirrorNode child1 = new MirrorNode(2, 1);
            MirrorNode child2 = new MirrorNode(3, 1);
            
            mirrorRoot.addMirrorNode(child1);
            mirrorRoot.addMirrorNode(child2);
            
            assertEquals(2, mirrorRoot.getNumMirrors());
            assertTrue(mirrorRoot.getAllMirrors().contains(child1));
            assertTrue(mirrorRoot.getAllMirrors().contains(child2));
            
            // Doppeltes Hinzufügen sollte keine Duplikate erstellen
            mirrorRoot.addMirrorNode(child1);
            assertEquals(2, mirrorRoot.getNumMirrors());
            
            mirrorRoot.removeMirrorNode(child1);
            assertEquals(1, mirrorRoot.getNumMirrors());
            assertFalse(mirrorRoot.getAllMirrors().contains(child1));
            assertTrue(mirrorRoot.getAllMirrors().contains(child2));
        }

        @Test
        @DisplayName("isLinkedWith funktioniert mit echten Mirrors")
        void testIsLinkedWithRealMirrors() throws IOException {
            initSimulator();
            sim.initialize(new BalancedTreeTopologyStrategy());
            sim.getEffector().setMirrors(4, 0);
            
            // Lasse Simulation laufen um Links zu erstellen
            for(int t = 1; t <= 15; t++) {
                sim.runStep(t);
            }
            
            MirrorProbe mirrorProbe = getMirrorProbe();
            assertNotNull(mirrorProbe);
            List<Mirror> mirrors = mirrorProbe.getMirrors();
            
            if (mirrors.size() >= 2) {
                Mirror mirror1 = mirrors.get(0);
                Mirror mirror2 = mirrors.get(1);
                
                MirrorNode node1 = new MirrorNode(4, 0, mirror1);
                MirrorNode node2 = new MirrorNode(5, 1, mirror2);
                
                // Prüfe ob Mirrors verlinkt sind (abhängig von der Topologie)
                boolean expectedLinked = mirror1.getLinks().stream()
                    .anyMatch(link -> link.getSource() == mirror2 || link.getTarget() == mirror2);
                
                assertEquals(expectedLinked, node1.isLinkedWith(node2));
            }
        }

        @Test
        @DisplayName("createAndLinkMirrors funktioniert mit Mock-Properties")
        void testCreateAndLinkMirrorsWithMockProperties() throws IOException {
            initSimulator();

            int expectedNumberOfMirrors = 5;
            int expectedNumberOfLinks = expectedNumberOfMirrors - 1; // In einem Baum mit n Knoten gibt es n-1 Links
            int simulationTimeStep = 0;
            int simulationRunSteps = 15;

            sim.initialize(new BalancedTreeTopologyStrategy());
            sim.getEffector().setMirrors(expectedNumberOfMirrors, simulationTimeStep);

            // Lasse Simulation laufen um Links zu erstellen
            for(int timeStep = 1; timeStep <= simulationRunSteps; timeStep++) {
                sim.runStep(timeStep);
            }

            MirrorProbe mirrorProbe = getMirrorProbe();
            LinkProbe linkProbe = getLinkProbe();

            assertNotNull(mirrorProbe);
            assertNotNull(linkProbe);

            // Validiere dass die erwartete Anzahl Mirrors erstellt wurde
            assertEquals(expectedNumberOfMirrors, mirrorProbe.getNumMirrors());

            // Validiere dass die erwartete Anzahl Links erstellt wurde (Baum-Eigenschaft)
            assertEquals(expectedNumberOfLinks, linkProbe.getLinks().size());

            // Überprüfe, dass Links gültig sind
            List<Mirror> mirrors = mirrorProbe.getMirrors();
            Set<Link> links = linkProbe.getLinks();

            for (Link link : links) {
                assertNotNull(link.getSource());
                assertNotNull(link.getTarget());
                assertTrue(mirrors.contains(link.getSource()));
                assertTrue(mirrors.contains(link.getTarget()));
            }
        }

        @Test
        @DisplayName("Baum ist konnektiert - alle Mirrors sind über Links erreichbar")
        void testTreeConnectivity() throws IOException {
            initSimulator();

            int numberOfMirrors = 7;
            int simulationTime = 0;
            int simulationRunSteps = 15;

            sim.initialize(new BalancedTreeTopologyStrategy());
            sim.getEffector().setMirrors(numberOfMirrors, simulationTime);

            for(int timeStep = 1; timeStep <= simulationRunSteps; timeStep++) {
                sim.runStep(timeStep);
            }

            MirrorProbe mirrorProbe = getMirrorProbe();
            LinkProbe linkProbe = getLinkProbe();

            // Validiere Konnektivität - in einem Baum ist jeder Knoten von jedem anderen erreichbar
            assertNotNull(mirrorProbe);
            List<Mirror> mirrors = mirrorProbe.getMirrors();
            assertNotNull(linkProbe);
            Set<Link> links = linkProbe.getLinks();

            // Baue Adjacency-Map für Konnektivitätstest
            Map<Integer, Set<Integer>> adjacencyMap = buildAdjacencyMap(mirrors, links);

            // Teste ob der Graph zusammenhängend ist (alle Knoten erreichbar)
            boolean isConnected = isGraphConnected(adjacencyMap, mirrors);
            assertTrue(isConnected, "Baum sollte vollständig zusammenhängend sein");
        }

        @Test
        @DisplayName("Baum hat keine Zyklen - azyklische Eigenschaft")
        void testTreeAcyclicity() throws IOException {
            initSimulator();

            int numberOfMirrors = 6;
            int simulationTime = 0;
            int simulationRunSteps = 15;

            sim.initialize(new BalancedTreeTopologyStrategy());
            sim.getEffector().setMirrors(numberOfMirrors, simulationTime);

            for(int timeStep = 1; timeStep <= simulationRunSteps; timeStep++) {
                sim.runStep(timeStep);
            }

            MirrorProbe mirrorProbe = getMirrorProbe();
            LinkProbe linkProbe = getLinkProbe();

            assertNotNull(mirrorProbe);
            List<Mirror> mirrors = mirrorProbe.getMirrors();
            assertNotNull(linkProbe);
            Set<Link> links = linkProbe.getLinks();

            // Teste ob der Graph azyklisch ist
            Map<Integer, Set<Integer>> adjacencyMap = buildAdjacencyMap(mirrors, links);
            boolean hasNoCycles = isAcyclic(adjacencyMap, mirrors);
            assertTrue(hasNoCycles, "Baum sollte keine Zyklen enthalten");
        }

        @Test
        @DisplayName("Baum-Balance - maximale Tiefe sollte logarithmisch sein")
        void testTreeBalance() throws IOException {
            initSimulator();

            int numberOfMirrors = 15; // Größerer Baum für Balance-Test
            int simulationTime = 0;
            int simulationRunSteps = 20;

            sim.initialize(new BalancedTreeTopologyStrategy());
            sim.getEffector().setMirrors(numberOfMirrors, simulationTime);

            for(int timeStep = 1; timeStep <= simulationRunSteps; timeStep++) {
                sim.runStep(timeStep);
            }

            MirrorProbe mirrorProbe = getMirrorProbe();
            LinkProbe linkProbe = getLinkProbe();

            assertNotNull(mirrorProbe);
            List<Mirror> mirrors = mirrorProbe.getMirrors();
            assertNotNull(linkProbe);
            Set<Link> links = linkProbe.getLinks();

            // Baue Baum-Struktur auf und messe Tiefe
            Map<Integer, Set<Integer>> adjacencyMap = buildAdjacencyMap(mirrors, links);
            int maxDepth = calculateMaxDepth(adjacencyMap, mirrors);

            // Für einen balancierten Baum sollte die maximale Tiefe ≈ log(n) sein
            double expectedMaxDepth = Math.log(numberOfMirrors) / Math.log(2); // log2(n)
            int reasonableMaxDepth = (int) Math.ceil(expectedMaxDepth) + 2; // +2 für Toleranz

            assertTrue(maxDepth <= reasonableMaxDepth,
                    String.format("Baum-Tiefe (%d) sollte für %d Knoten nicht größer als %d sein",
                            maxDepth, numberOfMirrors, reasonableMaxDepth));
        }

        @Test
        @DisplayName("Dynamisches Hinzufügen von Mirrors erhält Baum-Eigenschaften")
        void testDynamicMirrorAddition() throws IOException {
            initSimulator();

            int initialMirrors = 3;
            int additionalMirrors = 4;
            int simulationTime = 0;
            int midSimulationTime = 10;
            int finalSimulationSteps = 25;

            sim.initialize(new BalancedTreeTopologyStrategy());
            sim.getEffector().setMirrors(initialMirrors, simulationTime);
            sim.getEffector().setMirrors(initialMirrors + additionalMirrors, midSimulationTime);

            for(int timeStep = 1; timeStep <= finalSimulationSteps; timeStep++) {
                sim.runStep(timeStep);
            }

            MirrorProbe mirrorProbe = getMirrorProbe();
            LinkProbe linkProbe = getLinkProbe();

            int totalExpectedMirrors = initialMirrors + additionalMirrors;
            int expectedLinks = totalExpectedMirrors - 1;

            assertNotNull(mirrorProbe);
            assertEquals(totalExpectedMirrors, mirrorProbe.getNumMirrors());
            assertNotNull(linkProbe);
            assertEquals(expectedLinks, linkProbe.getLinks().size()); // KORRIGIERT: .getLinks().size()

            // Validiere dass der erweiterte Graph immer noch Baum-Eigenschaften hat
            List<Mirror> mirrors = mirrorProbe.getMirrors();
            Set<Link> links = linkProbe.getLinks();
            Map<Integer, Set<Integer>> adjacencyMap = buildAdjacencyMap(mirrors, links);

            boolean isConnected = isGraphConnected(adjacencyMap, mirrors);
            boolean hasNoCycles = isAcyclic(adjacencyMap, mirrors);

            assertTrue(isConnected, "Erweiterter Baum sollte zusammenhängend bleiben");
            assertTrue(hasNoCycles, "Erweiterter Baum sollte azyklisch bleiben");
        }


        // Hilfsmethoden für Graph Algorithmen
        private Map<Integer, Set<Integer>> buildAdjacencyMap(List<Mirror> mirrors, Set<Link> links) {
            Map<Integer, Set<Integer>> adjacencyMap = new HashMap<>();

            // Initialisiere alle Knoten
            for (Mirror mirror : mirrors) {
                adjacencyMap.put(mirror.getID(), new HashSet<>());
            }

            // Füge Edges hinzu (ungerichtet)
            for (Link link : links) {
                int sourceId = link.getSource().getID();
                int targetId = link.getTarget().getID();
                adjacencyMap.get(sourceId).add(targetId);
                adjacencyMap.get(targetId).add(sourceId);
            }

            return adjacencyMap;
        }

        private boolean isGraphConnected(Map<Integer, Set<Integer>> adjacencyMap, List<Mirror> mirrors) {
            if (mirrors.isEmpty()) return true;

            Set<Integer> visited = new HashSet<>();
            Queue<Integer> queue = new LinkedList<>();

            // Starte DFS vom ersten Knoten
            int startNode = mirrors.get(0).getID();
            queue.offer(startNode);
            visited.add(startNode);

            while (!queue.isEmpty()) {
                int current = queue.poll();
                for (int neighbor : adjacencyMap.get(current)) {
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        queue.offer(neighbor);
                    }
                }
            }

            // Alle Knoten sollten besucht worden sein
            return visited.size() == mirrors.size();
        }

        private boolean isAcyclic(Map<Integer, Set<Integer>> adjacencyMap, List<Mirror> mirrors) {
            Set<Integer> visited = new HashSet<>();

            for (Mirror mirror : mirrors) {
                int nodeId = mirror.getID();
                if (!visited.contains(nodeId)) {
                    if (hasCycleDFS(adjacencyMap, nodeId, -1, visited)) {
                        return false;
                    }
                }
            }

            return true;
        }

        private boolean hasCycleDFS(Map<Integer, Set<Integer>> adjacencyMap, int current, int parent, Set<Integer> visited) {
            visited.add(current);

            for (int neighbor : adjacencyMap.get(current)) {
                if (neighbor == parent) continue; // Ignoriere den Pfad zurück zum Parent

                if (visited.contains(neighbor)) {
                    return true; // Zyklus gefunden
                }

                if (hasCycleDFS(adjacencyMap, neighbor, current, visited)) {
                    return true;
                }
            }

            return false;
        }

        private int calculateMaxDepth(Map<Integer, Set<Integer>> adjacencyMap, List<Mirror> mirrors) {
            if (mirrors.isEmpty()) return 0;

            // Finde Root (Knoten mit nur einem Nachbarn, oder nimm den ersten)
            int root = mirrors.get(0).getID();
            for (Mirror mirror : mirrors) {
                if (adjacencyMap.get(mirror.getID()).size() == 1) {
                    root = mirror.getID();
                    break;
                }
            }

            return calculateDepthDFS(adjacencyMap, root, -1, 0);
        }

        private int calculateDepthDFS(Map<Integer, Set<Integer>> adjacencyMap, int current, int parent, int currentDepth) {
            int maxDepth = currentDepth;

            for (int neighbor : adjacencyMap.get(current)) {
                if (neighbor != parent) {
                    int childDepth = calculateDepthDFS(adjacencyMap, neighbor, current, currentDepth + 1);
                    maxDepth = Math.max(maxDepth, childDepth);
                }
            }

            return maxDepth;
        }


        @Test
        @DisplayName("addChild fügt auch MirrorNode hinzu")
        void testAddChildAddsToMirrorNodes() {
            MirrorNode child = new MirrorNode(8, 1);
            
            mirrorRoot.addChild(child);
            
            // Sollte sowohl als TreeNode-Kind als auch als MirrorNode hinzugefügt werden
            assertEquals(1, mirrorRoot.getChildren().size());
            assertEquals(1, mirrorRoot.getNumMirrors());
            assertTrue(mirrorRoot.getAllMirrors().contains(child));
        }
    }

    @Nested
    @DisplayName("Integration Tests mit echter Simulation")
    class IntegrationTests {

        @Test
        @DisplayName("Komplexe MirrorNode-Hierarchie mit echter Simulation")
        void testComplexMirrorNodeHierarchyWithRealSimulation() throws IOException {
            initSimulator();
            sim.initialize(new BalancedTreeTopologyStrategy());
            sim.getEffector().setMirrors(6, 0);
            
            // Lasse Simulation laufen
            for(int t = 1; t <= 20; t++) {
                sim.runStep(t);
            }
            
            MirrorProbe mirrorProbe = getMirrorProbe();
            assertNotNull(mirrorProbe);
            List<Mirror> mirrors = mirrorProbe.getMirrors();
            
            assertTrue(mirrors.size() >= 3);
            
            // Erstelle MirrorNode-Hierarchie mit echten Mirrors
            Mirror rootMirror = mirrors.get(0);
            Mirror child1Mirror = mirrors.get(1);
            Mirror child2Mirror = mirrors.get(2);
            
            MirrorNode root = new MirrorNode(10, 0, rootMirror);
            MirrorNode child1 = new MirrorNode(11, 1, child1Mirror);
            MirrorNode child2 = new MirrorNode(12, 1, child2Mirror);
            
            root.addChild(child1);
            root.addChild(child2);
            
            // Validierungen
            assertEquals(2, root.getChildren().size());
            assertEquals(2, root.getNumMirrors());
            assertTrue(root.getAllMirrors().contains(child1));
            assertTrue(root.getAllMirrors().contains(child2));
        }

        @Test
        @DisplayName("TreeBuilder Integration mit BalancedTreeTopologyStrategy")
        void testTreeBuilderIntegrationWithTopologyStrategy() throws IOException {
            initSimulator();
            sim.initialize(new BalancedTreeTopologyStrategy());
            
            // Teste dynamisches Hinzufügen von Mirrors wie in ExampleSimulation
            int mirrors = 8;
            for(int t = 0; t < 80; t += 20) {
                sim.getEffector().setMirrors(mirrors, t);
                mirrors += 2;
            }
            
            // Lasse Simulation laufen
            for(int t = 1; t <= 50; t++) {
                sim.runStep(t);
            }
            
            MirrorProbe mirrorProbe = getMirrorProbe();
            LinkProbe linkProbe = getLinkProbe();
            
            assertNotNull(mirrorProbe);
            assertNotNull(linkProbe);
            
            assertTrue(mirrorProbe.getNumMirrors() >= 8);
            assertTrue(linkProbe.getLinks().size() >= 7); // n-1 für Baum
            
            // Validiere Baum-Eigenschaften
            int numMirrors = mirrorProbe.getNumMirrors();
            int numLinks = linkProbe.getLinks().size();
            
            // In einem Baum: links = mirrors - 1
            assertTrue(numLinks >= numMirrors - 1);
            assertTrue(numLinks <= numMirrors * mirrorProbe.getNumTargetLinksPerMirror() / 2);
        }

        @Test
        @DisplayName("TreeNode zu MirrorNode Konvertierung mit echten Mirrors")
        void testTreeNodeToMirrorNodeConversionWithRealMirrors() throws IOException {
            initSimulator();
            sim.initialize(new BalancedTreeTopologyStrategy());
            sim.getEffector().setMirrors(3, 0);
            
            for(int t = 1; t <= 10; t++) {
                sim.runStep(t);
            }
            
            MirrorProbe mirrorProbe = getMirrorProbe();
            assertNotNull(mirrorProbe);
            List<Mirror> mirrors = mirrorProbe.getMirrors();
            assertEquals(3, mirrors.size());
            
            // Erstelle TreeNode-Struktur
            TreeNode treeRoot = new TreeNode(20, 0);
            TreeNode treeChild1 = new TreeNode(21, 1);
            TreeNode treeChild2 = new TreeNode(22, 1);
            
            treeRoot.addChild(treeChild1);
            treeRoot.addChild(treeChild2);
            
            // Konvertiere zu MirrorNode mit echten Mirrors
            MirrorNode mirrorRoot = new MirrorNode(treeRoot.getId(), treeRoot.getDepth(), mirrors.get(0));
            
            for (int i = 0; i < treeRoot.getChildren().size() && i + 1 < mirrors.size(); i++) {
                TreeNode child = treeRoot.getChildren().get(i);
                MirrorNode mirrorChild = new MirrorNode(child.getId(), child.getDepth(), mirrors.get(i + 1));
                mirrorRoot.addChild(mirrorChild);
            }
            
            // Validiere Konvertierung
            assertEquals(treeRoot.getId(), mirrorRoot.getId());
            assertEquals(treeRoot.getDepth(), mirrorRoot.getDepth());
            assertEquals(treeRoot.getChildren().size(), mirrorRoot.getChildren().size());
            assertNotNull(mirrorRoot.getMirror());
            assertEquals(mirrors.get(0), mirrorRoot.getMirror());
        }

        @Test
        @DisplayName("Performance Test mit vielen Mirrors")
        void testPerformanceWithManyMirrors() throws IOException {
            initSimulator();
            sim.initialize(new BalancedTreeTopologyStrategy());
            
            // Teste mit mehr Mirrors
            sim.getEffector().setMirrors(20, 0);
            
            long startTime = System.currentTimeMillis();
            
            for(int t = 1; t <= 30; t++) {
                sim.runStep(t);
            }
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            MirrorProbe mirrorProbe = getMirrorProbe();
            LinkProbe linkProbe = getLinkProbe();

            assertNotNull(mirrorProbe);
            assertEquals(20, mirrorProbe.getNumMirrors());
            assertNotNull(linkProbe);
            assertTrue(linkProbe.getLinks().size() >= 19); // mindestens n-1 für Baum
            
            // Performance sollte unter 5 Sekunden sein
            assertTrue(duration < 5000, "Simulation dauerte zu lange: " + duration + "ms");
        }

        @Test
        @DisplayName("Mirror Startup-Zeit Validierung")
        void testMirrorStartupTimeValidation() throws IOException {
            initSimulator();
            sim.initialize(new BalancedTreeTopologyStrategy());
            sim.getEffector().setMirrors(5, 0);
            
            // Lasse genug Zeit für Startup
            for(int t = 1; t <= 20; t++) {
                sim.runStep(t);
            }
            
            MirrorProbe mirrorProbe = getMirrorProbe();
            assertNotNull(mirrorProbe);
            List<Mirror> mirrors = mirrorProbe.getMirrors();
            
            for(Mirror mirror : mirrors) {
                assertTrue(mirror.getStartupTime() >= 1);
                assertTrue(mirror.getStartupTime() <= 3);
            }
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

    private Properties createMockProperties() {
        Properties props = new Properties();
        props.setProperty("network.target_links_per_mirror", "2");
        props.setProperty("mirror.startup_time_min", "1");
        props.setProperty("mirror.startup_time_max", "3");
        props.setProperty("mirror.ready_time_min", "1");
        props.setProperty("mirror.ready_time_max", "2");
        props.setProperty("mirror.stop_time_min", "1");
        props.setProperty("mirror.stop_time_max", "2");
        return props;
    }
}