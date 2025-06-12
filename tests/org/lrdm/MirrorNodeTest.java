
package org.lrdm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.lrdm.util.*;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

@DisplayName("MirrorNode spezifische Tests")
class MirrorNodeTest {

    private MirrorNode mirrorNode;
    private Properties mockProperties;

    @BeforeEach
    void setUp() {
        mirrorNode = new MirrorNode(1, 0);
        mockProperties = createMockProperties();
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
        Mirror mirror = new Mirror(101, 0, mockProperties);
        mirrorNode.setMirror(mirror);
        
        Mirror targetMirror = new Mirror(102, 0, mockProperties);
        Link link = new Link(1, mirror, targetMirror, 0, mockProperties);
        
        // Füge Link über MirrorNode hinzu
        mirrorNode.addLink(link);
        
        // Beide sollten den Link haben
        assertTrue(mirrorNode.getAllLinks().contains(link));
        assertTrue(mirror.getLinks().contains(link));
        assertEquals(1, mirrorNode.getNumTargetLinks());
    }

    @Test
    @DisplayName("createAndLinkMirrors mit leerer Liste")
    void testCreateAndLinkMirrorsEmpty() {
        Set<Link> links = mirrorNode.createAndLinkMirrors(new ArrayList<>(), 0, mockProperties);
        assertTrue(links.isEmpty());
    }

    @Test
    @DisplayName("createAndLinkMirrors mit einem Mirror")
    void testCreateAndLinkMirrorsSingle() {
        List<Mirror> mirrors = Arrays.asList(new Mirror(103, 0, mockProperties));
        Set<Link> links = mirrorNode.createAndLinkMirrors(mirrors, 0, mockProperties);
        
        // Ein einzelnes Mirror sollte keine Links benötigen
        assertTrue(links.isEmpty());
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
        Mirror source = new Mirror(104, 0, mockProperties);
        Mirror target = new Mirror(105, 0, mockProperties);
        Link link1 = new Link(2, source, target, 0, mockProperties);
        Link link2 = new Link(3, source, target, 0, mockProperties);
        
        mirrorNode.addLink(link1);
        mirrorNode.addLink(link2);
        
        assertEquals(2, mirrorNode.getNumTargetLinks());
        assertEquals(7, mirrorNode.getPredictedNumTargetLinks()); // 2 + 5 pending
        
        mirrorNode.confirmPendingLinks(3);
        assertEquals(2, mirrorNode.getPendingLinks());
        assertEquals(4, mirrorNode.getPredictedNumTargetLinks()); // 2 + 2 pending
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
        Mirror mirror1 = new Mirror(106, 0, mockProperties);
        Mirror mirror2 = new Mirror(107, 0, mockProperties);
        Mirror mirror3 = new Mirror(108, 0, mockProperties);
        
        MirrorNode node1 = new MirrorNode(5, 0, mirror1);
        MirrorNode node2 = new MirrorNode(6, 1, mirror2);
        MirrorNode node3 = new MirrorNode(7, 1, mirror3);
        
        // Erstelle Link zwischen mirror1 und mirror2
        Link link = new Link(4, mirror1, mirror2, 0, mockProperties);
        mirror1.addLink(link);
        mirror2.addLink(link);
        
        assertTrue(node1.isLinkedWith(node2));
        assertTrue(node2.isLinkedWith(node1));
        assertFalse(node1.isLinkedWith(node3));
        assertFalse(node2.isLinkedWith(node3));
    }

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
}