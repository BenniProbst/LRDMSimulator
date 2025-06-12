package org.lrdm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.util.*;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

@DisplayName("TreeNode und MirrorNode Tests")
class TreeNodeTest {

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
        private Properties mockProperties;

        @BeforeEach
        void setUp() {
            mirrorRoot = new MirrorNode(1, 0);
            mockProperties = createMockProperties();
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
        @DisplayName("MirrorNode mit Mirror-Zuordnung")
        void testMirrorNodeWithMirror() {
            Mirror mirror = new Mirror(101, 0, mockProperties);
            MirrorNode nodeWithMirror = new MirrorNode(2, 1, mirror);
            
            assertEquals(mirror, nodeWithMirror.getMirror());
            assertEquals(mirror.getID(), nodeWithMirror.getMirror().getID());
        }

        @Test
        @DisplayName("setMirror und getMirror funktionieren")
        void testSetAndGetMirror() {
            Mirror mirror = new Mirror(102, 0, mockProperties);
            
            mirrorRoot.setMirror(mirror);
            
            assertEquals(mirror, mirrorRoot.getMirror());
        }

        @Test
        @DisplayName("addLink und removeLink funktionieren")
        void testAddAndRemoveLink() {
            Mirror source = new Mirror(103, 0, mockProperties);
            Mirror target = new Mirror(104, 0, mockProperties);
            Link link = new Link(1, source, target, 0, mockProperties);
            
            mirrorRoot.addLink(link);
            
            assertEquals(1, mirrorRoot.getAllLinks().size());
            assertTrue(mirrorRoot.getAllLinks().contains(link));
            
            mirrorRoot.removeLink(link);
            
            assertEquals(0, mirrorRoot.getAllLinks().size());
            assertFalse(mirrorRoot.getAllLinks().contains(link));
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
        @DisplayName("isLinkedWith funktioniert mit Mirrors")
        void testIsLinkedWithMirrors() {
            Mirror mirror1 = new Mirror(105, 0, mockProperties);
            Mirror mirror2 = new Mirror(106, 0, mockProperties);
            Link link = new Link(2, mirror1, mirror2, 0, mockProperties);
            
            MirrorNode node1 = new MirrorNode(4, 0, mirror1);
            MirrorNode node2 = new MirrorNode(5, 1, mirror2);
            
            // Füge Link zu den Mirrors hinzu
            mirror1.addLink(link);
            mirror2.addLink(link);
            
            assertTrue(node1.isLinkedWith(node2));
            assertTrue(node2.isLinkedWith(node1));
        }

        @Test
        @DisplayName("isLinkedWith funktioniert ohne Mirrors")
        void testIsLinkedWithoutMirrors() {
            Mirror source = new Mirror(107, 0, mockProperties);
            Mirror target = new Mirror(108, 0, mockProperties);
            Link link = new Link(3, source, target, 0, mockProperties);
            
            MirrorNode node1 = new MirrorNode(6, 0);
            MirrorNode node2 = new MirrorNode(7, 1);
            
            node1.addLink(link);
            
            // Diese Implementation würde eine spezielle Logik benötigen
            // oder sollte false zurückgeben wenn keine Mirrors zugeordnet sind
            assertFalse(node1.isLinkedWith(node2));
        }

        @Test
        @DisplayName("createAndLinkMirrors funktioniert")
        void testCreateAndLinkMirrors() {
            List<Mirror> mirrors = createMockMirrors(5);
            
            Set<Link> links = mirrorRoot.createAndLinkMirrors(mirrors, 0, mockProperties);
            
            assertNotNull(links);
            // In einem Baum mit 5 Knoten sollten 4 Links existieren
            assertEquals(4, links.size());
            
            // Überprüfe, dass Links gültig sind
            for (Link link : links) {
                assertNotNull(link.getSource());
                assertNotNull(link.getTarget());
                assertTrue(mirrors.contains(link.getSource()));
                assertTrue(mirrors.contains(link.getTarget()));
            }
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

        @Test
        @DisplayName("toString gibt korrekte Information zurück")
        void testToString() {
            Mirror mirror = new Mirror(109, 0, mockProperties);
            MirrorNode node = new MirrorNode(9, 2, mirror);
            node.addPendingLinks(3);
            
            String result = node.toString();
            
            assertTrue(result.contains("id=9"));
            assertTrue(result.contains("depth=2"));
            assertTrue(result.contains("mirrorId=109"));
            assertTrue(result.contains("pendingLinks=3"));
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Komplexe MirrorNode-Hierarchie")
        void testComplexMirrorNodeHierarchy() {
            // Erstelle eine komplexe Hierarchie mit Mirrors und Links
            Mirror rootMirror = new Mirror(201, 0, createMockProperties());
            Mirror child1Mirror = new Mirror(202, 0, createMockProperties());
            Mirror child2Mirror = new Mirror(203, 0, createMockProperties());
            
            MirrorNode root = new MirrorNode(10, 0, rootMirror);
            MirrorNode child1 = new MirrorNode(11, 1, child1Mirror);
            MirrorNode child2 = new MirrorNode(12, 1, child2Mirror);
            
            root.addChild(child1);
            root.addChild(child2);
            
            // Erstelle Links
            Link link1 = new Link(10, rootMirror, child1Mirror, 0, createMockProperties());
            Link link2 = new Link(11, rootMirror, child2Mirror, 0, createMockProperties());
            
            root.addLink(link1);
            root.addLink(link2);
            child1.addLink(link1);
            child2.addLink(link2);
            
            // Validierungen
            assertEquals(2, root.getChildren().size());
            assertEquals(2, root.getNumMirrors());
            assertEquals(2, root.getNumTargetLinks());
            assertEquals(1, child1.getNumTargetLinks());
            assertEquals(1, child2.getNumTargetLinks());
            
            assertTrue(root.isLinkedWith(child1));
            assertTrue(root.isLinkedWith(child2));
            assertFalse(child1.isLinkedWith(child2));
        }

        @Test
        @DisplayName("TreeNode zu MirrorNode Konvertierung")
        void testTreeNodeToMirrorNodeConversion() {
            // Erstelle TreeNode-Struktur
            TreeNode treeRoot = new TreeNode(20, 0);
            TreeNode treeChild1 = new TreeNode(21, 1);
            TreeNode treeChild2 = new TreeNode(22, 1);
            
            treeRoot.addChild(treeChild1);
            treeRoot.addChild(treeChild2);
            
            // Konvertiere zu MirrorNode (dies würde in TreeBuilder gemacht)
            MirrorNode mirrorRoot = new MirrorNode(treeRoot.getId(), treeRoot.getDepth());
            
            for (TreeNode child : treeRoot.getChildren()) {
                MirrorNode mirrorChild = new MirrorNode(child.getId(), child.getDepth());
                mirrorRoot.addChild(mirrorChild);
            }
            
            // Validiere Konvertierung
            assertEquals(treeRoot.getId(), mirrorRoot.getId());
            assertEquals(treeRoot.getDepth(), mirrorRoot.getDepth());
            assertEquals(treeRoot.getChildren().size(), mirrorRoot.getChildren().size());
            
            for (int i = 0; i < treeRoot.getChildren().size(); i++) {
                TreeNode origChild = treeRoot.getChildren().get(i);
                MirrorNode convertedChild = (MirrorNode) mirrorRoot.getChildren().get(i);
                assertEquals(origChild.getId(), convertedChild.getId());
                assertEquals(origChild.getDepth(), convertedChild.getDepth());
            }
        }
    }

    // Hilfsmethoden
    private Properties createMockProperties() {
        Properties props = new Properties();
        props.setProperty("network.target_links_per_mirror", "2");
        props.setProperty("mirror.startup_time_min", "100");
        props.setProperty("mirror.startup_time_max", "200");
        props.setProperty("mirror.ready_time_min", "50");
        props.setProperty("mirror.ready_time_max", "100");
        props.setProperty("mirror.stop_time_min", "50");
        props.setProperty("mirror.stop_time_max", "75");
        return props;
    }

    private List<Mirror> createMockMirrors(int count) {
        List<Mirror> mirrors = new ArrayList<>();
        Properties props = createMockProperties();
        
        for (int i = 0; i < count; i++) {
            mirrors.add(new Mirror(IDGenerator.getInstance().getNextID(), 0, props));
        }
        
        return mirrors;
    }
}