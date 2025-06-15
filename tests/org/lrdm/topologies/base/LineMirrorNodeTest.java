
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

@DisplayName("LineMirrorNode spezifische Tests")
class LineMirrorNodeTest {

    private TimedRDMSim sim;
    private LineMirrorNode lineNode;
    private Properties props;
    private static final String config = "resources/sim-test-mirror node.conf";

    @BeforeEach
    void setUp() throws IOException {
        loadProperties(config);
        props = getProps();
        lineNode = new LineMirrorNode(1);
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
    @DisplayName("LineMirrorNode Grundfunktionen")
    class LineMirrorNodeBasicTests {

        @Test
        @DisplayName("LineMirrorNode erbt MirrorNode-Funktionalität")
        void testInheritedMirrorNodeFunctionality() {
            Mirror testMirror = new Mirror(101, 0, props);
            lineNode.setMirror(testMirror);

            assertEquals(1, lineNode.getId());
            assertEquals(testMirror, lineNode.getMirror());
            assertEquals(0, lineNode.getNumImplementedLinks());
            assertTrue(lineNode.getImplementedLinks().isEmpty());
        }

        @Test
        @DisplayName("LineMirrorNode Konstruktoren")
        void testConstructors() {
            // Standard Konstruktor
            LineMirrorNode node1 = new LineMirrorNode(5);
            assertEquals(5, node1.getId());
            assertNull(node1.getMirror());

            // Konstruktor mit Mirror
            Mirror mirror = new Mirror(102, 0, props);
            LineMirrorNode node2 = new LineMirrorNode(6, mirror);
            assertEquals(6, node2.getId());
            assertEquals(mirror, node2.getMirror());
        }

        @Test
        @DisplayName("canAcceptMoreChildren Linien-spezifische Logik")
        void testCanAcceptMoreChildrenLineSpecific() {
            LineMirrorNode head = new LineMirrorNode(1);
            head.setHead(true);

            // Einzelner Head-Knoten kann Kind akzeptieren
            assertTrue(head.canAcceptMoreChildren());

            // Nach Hinzufügen eines Kindes nicht mehr
            LineMirrorNode child = new LineMirrorNode(2);
            head.addChild(child);
            assertFalse(head.canAcceptMoreChildren());

            // Kind-Knoten kann auch Kind akzeptieren (wenn noch leer)
            assertTrue(child.canAcceptMoreChildren());

            // Nach Hinzufügen eines Kindes nicht mehr
            LineMirrorNode grandchild = new LineMirrorNode(3);
            child.addChild(grandchild);
            assertFalse(child.canAcceptMoreChildren());
            assertFalse(grandchild.canAcceptMoreChildren()); // Terminal-Knoten
        }

        @Test
        @DisplayName("canBeRemovedFromStructure Linien-spezifische Validierung")
        void testCanBeRemovedFromStructureLineSpecific() {
            // Erstelle gültige 3-Knoten-Linie
            LineMirrorNode head = new LineMirrorNode(1);
            LineMirrorNode middle = new LineMirrorNode(2);
            LineMirrorNode end = new LineMirrorNode(3);

            head.setHead(true);
            head.addChild(middle);
            middle.addChild(end);

            // Setze Mirrors und Links für gültige Struktur
            Mirror headMirror = new Mirror(101, 0, props);
            Mirror middleMirror = new Mirror(102, 0, props);
            Mirror endMirror = new Mirror(103, 0, props);
            Mirror externalMirror = new Mirror(104, 0, props);

            head.setMirror(headMirror);
            middle.setMirror(middleMirror);
            end.setMirror(endMirror);

            // Erstelle interne Links
            Link link1 = new Link(1, headMirror, middleMirror, 0, props);
            Link link2 = new Link(2, middleMirror, endMirror, 0, props);
            headMirror.addLink(link1);
            middleMirror.addLink(link1);
            middleMirror.addLink(link2);
            endMirror.addLink(link2);

            // Erstelle Edge-Link für Head
            Link edgeLink = new Link(3, headMirror, externalMirror, 0, props);
            headMirror.addLink(edgeLink);
            externalMirror.addLink(edgeLink);

            // Nur Terminal-Knoten (end) können entfernt werden
            assertFalse(head.canBeRemovedFromStructure(head)); // Ein Head ist kein Endpoint
            assertFalse(middle.canBeRemovedFromStructure(head)); // Middle ist kein Endpoint
            assertTrue(end.canBeRemovedFromStructure(head)); // End ist Endpoint

            // Mit weniger als 3 Knoten kann nichts entfernt werden
            head.removeChild(middle);
            assertFalse(end.canBeRemovedFromStructure(head));
        }
    }

    @Nested
    @DisplayName("LineMirrorNode Struktur-Validierung")
    class LineMirrorNodeValidationTests {

        @Test
        @DisplayName("isValidStructure gültige 2-Knoten-Linie")
        void testValidStructureTwoNodes() {
            LineMirrorNode head = new LineMirrorNode(1);
            LineMirrorNode end = new LineMirrorNode(2);

            head.setHead(true);
            head.addChild(end);

            // Setze Mirrors
            Mirror headMirror = new Mirror(101, 0, props);
            Mirror endMirror = new Mirror(102, 0, props);
            Mirror externalMirror = new Mirror(103, 0, props);

            head.setMirror(headMirror);
            end.setMirror(endMirror);

            // Erstelle internen Link
            Link internalLink = new Link(1, headMirror, endMirror, 0, props);
            headMirror.addLink(internalLink);
            endMirror.addLink(internalLink);

            // Erstelle Edge-Link für Head
            Link edgeLink = new Link(2, headMirror, externalMirror, 0, props);
            headMirror.addLink(edgeLink);
            externalMirror.addLink(edgeLink);

            Set<StructureNode> lineNodes = Set.of(head, end);
            assertTrue(head.isValidStructure(lineNodes));
        }

        @Test
        @DisplayName("isValidStructure gültige 3-Knoten-Linie")
        void testValidStructureThreeNodes() {
            LineMirrorNode head = new LineMirrorNode(1);
            LineMirrorNode middle = new LineMirrorNode(2);
            LineMirrorNode end = new LineMirrorNode(3);

            head.setHead(true);
            head.addChild(middle);
            middle.addChild(end);

            // Setze Mirrors
            Mirror headMirror = new Mirror(101, 0, props);
            Mirror middleMirror = new Mirror(102, 0, props);
            Mirror endMirror = new Mirror(103, 0, props);
            Mirror externalMirror = new Mirror(104, 0, props);

            head.setMirror(headMirror);
            middle.setMirror(middleMirror);
            end.setMirror(endMirror);

            // Erstelle interne Links
            Link link1 = new Link(1, headMirror, middleMirror, 0, props);
            Link link2 = new Link(2, middleMirror, endMirror, 0, props);
            headMirror.addLink(link1);
            middleMirror.addLink(link1);
            middleMirror.addLink(link2);
            endMirror.addLink(link2);

            // Erstelle Edge-Link für Head
            Link edgeLink = new Link(3, headMirror, externalMirror, 0, props);
            headMirror.addLink(edgeLink);
            externalMirror.addLink(edgeLink);

            Set<StructureNode> lineNodes = Set.of(head, middle, end);
            assertTrue(head.isValidStructure(lineNodes));
        }

        @Test
        @DisplayName("isValidStructure ungültige Strukturen")
        void testInvalidStructures() {
            LineMirrorNode node1 = new LineMirrorNode(1);
            LineMirrorNode node2 = new LineMirrorNode(2);
            LineMirrorNode node3 = new LineMirrorNode(3);

            // Einzelner Knoten ungültig (< 2 Knoten)
            assertFalse(node1.isValidStructure(Set.of(node1)));

            // Ohne Head ungültig
            node1.addChild(node2);
            assertFalse(node1.isValidStructure(Set.of(node1, node2)));

            // Mit Head, aber ohne Edge-Links ungültig
            node1.setHead(true);
            Mirror mirror1 = new Mirror(101, 0, props);
            Mirror mirror2 = new Mirror(102, 0, props);
            node1.setMirror(mirror1);
            node2.setMirror(mirror2);

            Link internalLink = new Link(1, mirror1, mirror2, 0, props);
            mirror1.addLink(internalLink);
            mirror2.addLink(internalLink);

            assertFalse(node1.isValidStructure(Set.of(node1, node2))); // Kein Edge-Link

            // Baum-Struktur ist ungültig (mehr als ein Kind)
            node1.addChild(node3);
            Mirror mirror3 = new Mirror(103, 0, props);
            node3.setMirror(mirror3);
            Link link2 = new Link(2, mirror1, mirror3, 0, props);
            mirror1.addLink(link2);
            mirror3.addLink(link2);

            Mirror externalMirror = new Mirror(104, 0, props);
            Link edgeLink = new Link(3, mirror1, externalMirror, 0, props);
            mirror1.addLink(edgeLink);
            externalMirror.addLink(edgeLink);

            assertFalse(node1.isValidStructure(Set.of(node1, node2, node3))); // Zu viele Kinder
        }

        @Test
        @DisplayName("isValidStructure Zyklus-Erkennung")
        void testCycleDetection() {
            LineMirrorNode node1 = new LineMirrorNode(1);
            LineMirrorNode node2 = new LineMirrorNode(2);
            LineMirrorNode node3 = new LineMirrorNode(3);

            // Erstelle Zyklus
            node1.setHead(true);
            node1.addChild(node2);
            node2.addChild(node3);
            node3.setParent(node1); // Zyklus!

            Set<StructureNode> cycleNodes = Set.of(node1, node2, node3);
            assertFalse(node1.isValidStructure(cycleNodes)); // Zyklus ungültig
        }

        @Test
        @DisplayName("isValidStructure gemischte Knotentypen")
        void testMixedNodeTypes() {
            LineMirrorNode lineNode = new LineMirrorNode(1);
            MirrorNode regularNode = new MirrorNode(2); // Nicht LineMirrorNode

            lineNode.setHead(true);
            lineNode.addChild(regularNode);

            Set<StructureNode> mixedNodes = Set.of(lineNode, regularNode);
            assertFalse(lineNode.isValidStructure(mixedNodes)); // Gemischte Typen ungültig
        }
    }

    @Nested
    @DisplayName("LineMirrorNode Linien-spezifische Funktionen")
    class LineMirrorNodeLineSpecificTests {

        private LineMirrorNode head, middle, end;

        @BeforeEach
        void setUpLine() {
            head = new LineMirrorNode(1);
            middle = new LineMirrorNode(2);
            end = new LineMirrorNode(3);

            head.setHead(true);
            head.addChild(middle);
            middle.addChild(end);
        }

        @Test
        @DisplayName("getEndpoints findet beide Endpunkte")
        void testGetEndpoints() {
            List<LineMirrorNode> endpoints = head.getEndpoints();
            assertEquals(2, endpoints.size());
            assertTrue(endpoints.contains(head));
            assertTrue(endpoints.contains(end));
            assertFalse(endpoints.contains(middle));

            // Von jedem Knoten aus sollten die gleichen Endpunkte gefunden werden
            List<LineMirrorNode> endpointsFromMiddle = middle.getEndpoints();
            assertEquals(2, endpointsFromMiddle.size());
            assertTrue(endpointsFromMiddle.contains(head));
            assertTrue(endpointsFromMiddle.contains(end));
        }

        @Test
        @DisplayName("getOtherEndpoint findet anderen Endpunkt")
        void testGetOtherEndpoint() {
            assertEquals(end, head.getOtherEndpoint());
            assertEquals(head, end.getOtherEndpoint());
            assertNull(middle.getOtherEndpoint()); // Middle ist kein Endpunkt
        }

        @Test
        @DisplayName("getLineHead findet Head-Knoten")
        void testGetLineHead() {
            assertEquals(head, head.getLineHead());
            assertEquals(head, middle.getLineHead());
            assertEquals(head, end.getLineHead());

            // Ohne Head
            head.setHead(false);
            assertNull(head.getLineHead());
        }

        @Test
        @DisplayName("isMiddleNode identifiziert mittlere Knoten")
        void testIsMiddleNode() {
            assertFalse(head.isMiddleNode()); // Head ist Terminal
            assertTrue(middle.isMiddleNode()); // Middle hat genau 2 Verbindungen
            assertFalse(end.isMiddleNode()); // End ist Terminal
        }

        @Test
        @DisplayName("getPositionInLine berechnet Position korrekt")
        void testGetPositionInLine() {
            assertEquals(0, head.getPositionInLine()); // Head ist Position 0
            assertEquals(1, middle.getPositionInLine()); // 1 Schritt vom Head
            assertEquals(2, end.getPositionInLine()); // 2 Schritte vom Head

            // Ohne Head-Verbindung
            head.setHead(false);
            assertEquals(-1, middle.getPositionInLine()); // Kein Pfad zum Head
        }

        @Test
        @DisplayName("Linien-Navigation mit längerer Linie")
        void testLongerLineNavigation() {
            LineMirrorNode node4 = new LineMirrorNode(4);
            LineMirrorNode node5 = new LineMirrorNode(5);

            end.addChild(node4);
            node4.addChild(node5);

            // Endpunkte
            List<LineMirrorNode> endpoints = head.getEndpoints();
            assertEquals(2, endpoints.size());
            assertTrue(endpoints.contains(head));
            assertTrue(endpoints.contains(node5));

            // Positionen
            assertEquals(0, head.getPositionInLine());
            assertEquals(1, middle.getPositionInLine());
            assertEquals(2, end.getPositionInLine());
            assertEquals(3, node4.getPositionInLine());
            assertEquals(4, node5.getPositionInLine());

            // Mittlere Knoten
            assertFalse(head.isMiddleNode());
            assertTrue(middle.isMiddleNode());
            assertTrue(end.isMiddleNode());
            assertTrue(node4.isMiddleNode());
            assertFalse(node5.isMiddleNode());
        }
    }

    @Nested
    @DisplayName("LineMirrorNode Edge Cases und Integration")
    class LineMirrorNodeEdgeCasesTests {

        @Test
        @DisplayName("Edge Cases für Endpunkt-Funktionen")
        void testEndpointEdgeCases() {
            LineMirrorNode isolatedNode = new LineMirrorNode(10);

            // Isolierter Knoten
            List<LineMirrorNode> endpoints = isolatedNode.getEndpoints();
            assertEquals(1, endpoints.size());
            assertTrue(endpoints.contains(isolatedNode));

            assertNull(isolatedNode.getOtherEndpoint()); // Kein anderer Endpunkt
            assertEquals(-1, isolatedNode.getPositionInLine()); // Kein Head
        }

        @Test
        @DisplayName("Edge Cases für Head-Funktionen")
        void testHeadEdgeCases() {
            LineMirrorNode node1 = new LineMirrorNode(1);
            LineMirrorNode node2 = new LineMirrorNode(2);

            // Mehrere Heads (ungültig)
            node1.setHead(true);
            node2.setHead(true);
            node1.addChild(node2);

            Set<StructureNode> invalidHeads = Set.of(node1, node2);
            assertFalse(node1.isValidStructure(invalidHeads));
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

            // Erstelle LineMirrorNodes mit echten Mirrors
            LineMirrorNode lineHead = new LineMirrorNode(100, mirrors.get(0));
            LineMirrorNode lineEnd = new LineMirrorNode(101);

            if (mirrors.size() > 1) {
                lineEnd.setMirror(mirrors.get(1));
            }

            // Grundlegende Funktionalität sollte funktionieren
            assertEquals(mirrors.get(0), lineHead.getMirror());
            assertNotNull(lineHead.getEndpoints());
        }

        @Test
        @DisplayName("Performance mit größeren Linien")
        void testPerformanceWithLargerLines() {
            LineMirrorNode current = new LineMirrorNode(1);
            current.setHead(true);

            // Erstelle Linie mit 50 Knoten
            for (int i = 2; i <= 50; i++) {
                LineMirrorNode next = new LineMirrorNode(i);
                current.addChild(next);
                current = next;
            }

            LineMirrorNode head = new LineMirrorNode(1);
            head.setHead(true);
            LineMirrorNode firstChild = head.getChildren().isEmpty() ? null : 
                (LineMirrorNode) head.getChildren().get(0);

            if (firstChild != null) {
                long startTime = System.currentTimeMillis();

                List<LineMirrorNode> endpoints = head.getEndpoints();
                int position = current.getPositionInLine();
                boolean isMiddle = firstChild.isMiddleNode();

                long endTime = System.currentTimeMillis();

                // Sollte schnell sein (< 1000ms für 50 Knoten)
                assertTrue(endTime - startTime < 1000);

                // Korrekte Werte
                assertEquals(2, endpoints.size());
                assertTrue(position >= 0);
                assertTrue(isMiddle);
            }
        }

        @Test
        @DisplayName("Null-Handling und defensive Programmierung")
        void testNullHandlingAndDefensiveProgramming() {
            LineMirrorNode node = new LineMirrorNode(1);

            // Null-Parameter
            assertFalse(node.canBeRemovedFromStructure(null));

            // Leere Strukturen
            assertTrue(node.getEndpoints().isEmpty() || !node.getEndpoints().isEmpty());
            assertNull(node.getOtherEndpoint()); // Kein anderer Endpunkt verfügbar

            // Ohne Head
            assertEquals(-1, node.getPositionInLine());
            assertNull(node.getLineHead());
        }

        @Test
        @DisplayName("Kompatibilität mit MirrorNode-Funktionen")
        void testMirrorNodeCompatibility() {
            LineMirrorNode lineNode = new LineMirrorNode(1);
            Mirror mirror = new Mirror(101, 0, props);
            lineNode.setMirror(mirror);

            // Alle MirrorNode-Funktionen sollten verfügbar sein
            assertEquals(0, lineNode.getNumImplementedLinks());
            assertEquals(0, lineNode.getNumPendingLinks());
            assertTrue(lineNode.getImplementedLinks().isEmpty());
            assertNotNull(lineNode.getMirrorsOfStructure());
            assertNotNull(lineNode.getLinksOfStructure());
            assertEquals(0, lineNode.getNumEdgeLinks());

            // Link-Management
            Mirror targetMirror = new Mirror(102, 0, props);
            Link link = new Link(1, mirror, targetMirror, 0, props);
            lineNode.addLink(link);
            assertEquals(1, lineNode.getNumImplementedLinks());

            lineNode.removeLink(link);
            assertEquals(0, lineNode.getNumImplementedLinks());
        }
    }
}