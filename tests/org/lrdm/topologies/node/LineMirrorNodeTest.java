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
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.lrdm.TestProperties.getProps;
import static org.lrdm.TestProperties.loadProperties;

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
        sim.initialize(new BalancedTreeTopologyStrategy());
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

        @Test
        @DisplayName("Integration mit echter Simulation")
        void testIntegrationWithRealSimulation() throws IOException {
            initSimulator();
            assertNotNull(sim);

            MirrorProbe probe = getMirrorProbe();
            assertNotNull(probe);

            // Erstelle LineMirrorNode mit Simulator-Mirror über MirrorProbe
            List<Mirror> simMirrors = probe.getMirrors();
            if (!simMirrors.isEmpty()) {
                Mirror simMirror = simMirrors.get(0);
                LineMirrorNode simLineNode = new LineMirrorNode(100, simMirror);

                assertEquals(simMirror, simLineNode.getMirror());
                assertEquals(100, simLineNode.getId());
                assertEquals(StructureNode.StructureType.LINE, simLineNode.deriveTypeId());

                // Teste LineMirrorNode-Funktionalität mit echtem Mirror
                assertEquals(simMirror.getLinks().size(), simLineNode.getNumImplementedLinks());
                assertEquals(simMirror.getLinks(), simLineNode.getImplementedLinks());
            } else {
                // Fallback-Test, falls keine Mirrors vorhanden sind
                assertTrue(probe.getNumMirrors() >= 0);
            }
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


        @Test
        @DisplayName("Struktur-Validierung mit MirrorProbe Daten")
        void testStructureValidationWithMirrorProbeData() throws IOException {
            initSimulator();
            MirrorProbe probe = getMirrorProbe();
            assertNotNull(probe);
            
            List<Mirror> simMirrors = probe.getMirrors();
            // Verwende normale Annahmen-Prüfung statt assumeTrue
            if (simMirrors.size() < 3) {
                // Fallback: Erstelle eigene Test-Mirrors
                Mirror mirror1 = new Mirror(101, 0, props);
                Mirror mirror2 = new Mirror(102, 0, props);
                Mirror mirror3 = new Mirror(103, 0, props);
                simMirrors = List.of(mirror1, mirror2, mirror3);
            }
            
            // Erstelle LineMirrorNodes mit echten Simulator-Mirrors
            LineMirrorNode head = new LineMirrorNode(1, simMirrors.get(0));
            LineMirrorNode middle = new LineMirrorNode(2, simMirrors.get(1)); 
            LineMirrorNode end = new LineMirrorNode(3, simMirrors.get(2));
            
            // Baue gültige 3-Knoten-Linie auf
            head.setHead(true);
            head.addChild(middle);
            middle.addChild(end);
            
            // Erstelle echte Links zwischen den Mirrors
            Link link1 = new Link(1, simMirrors.get(0), simMirrors.get(1), 0, props);
            Link link2 = new Link(2, simMirrors.get(1), simMirrors.get(2), 0, props);
            
            // Füge Links zu den Mirrors hinzu
            simMirrors.get(0).addLink(link1);
            simMirrors.get(1).addLink(link1);
            simMirrors.get(1).addLink(link2);
            simMirrors.get(2).addLink(link2);
            
            // Erstelle Edge-Link für Head (zu externem Mirror falls verfügbar)
            if (simMirrors.size() >= 4) {
                Link edgeLink = new Link(3, simMirrors.get(0), simMirrors.get(3), 0, props);
                simMirrors.get(0).addLink(edgeLink);
                simMirrors.get(3).addLink(edgeLink);
            } else {
                // Fallback: Erstelle externen Mirror für Edge-Link
                Mirror externalMirror = new Mirror(104, 0, props);
                Link edgeLink = new Link(3, simMirrors.get(0), externalMirror, 0, props);
                simMirrors.get(0).addLink(edgeLink);
                externalMirror.addLink(edgeLink);
            }
            
            // Teste Struktur-Validierung mit echten MirrorProbe-Daten
            Set<StructureNode> lineNodes = Set.of(head, middle, end);
            assertTrue(head.isValidStructure(lineNodes), 
                "Linie mit MirrorProbe-Daten sollte gültig sein");
            
            // Teste LineMirrorNode-spezifische Funktionen mit echten Daten
            List<LineMirrorNode> endpoints = head.getEndpoints();
            assertEquals(2, endpoints.size(), "Linie sollte genau 2 Endpunkte haben");
            assertTrue(endpoints.contains(head), "Head sollte Endpunkt sein");
            assertTrue(endpoints.contains(end), "End sollte Endpunkt sein");
            assertFalse(endpoints.contains(middle), "Middle sollte kein Endpunkt sein");
            
            // Teste Navigation mit echten Mirrors
            assertEquals(head, end.getOtherEndpoint(), "Other endpoint von end sollte head sein");
            assertEquals(end, head.getOtherEndpoint(), "Other endpoint von head sollte end sein");
            assertNull(middle.getOtherEndpoint(), "Middle node sollte keinen other endpoint haben");
            
            // Versuche Mirror-Integration
            assertEquals(simMirrors.get(0), head.getMirror());
            assertEquals(simMirrors.get(1), middle.getMirror());
            assertEquals(simMirrors.get(2), end.getMirror());
            
            // Teste Link-Zählung mit echten Daten
            assertTrue(head.getNumImplementedLinks() >= 1, "Head sollte mindestens 1 Link haben");
            assertEquals(2, middle.getNumImplementedLinks(), "Middle sollte genau 2 Links haben");
            assertTrue(end.getNumImplementedLinks() >= 1, "End sollte mindestens 1 Link haben");
            
            // Versuche MirrorProbe-Integration
            assertTrue(probe.getNumMirrors() >= 0, "MirrorProbe sollte valide Mirror-Anzahl liefern");
            assertTrue(probe.getNumTargetLinksPerMirror() >= 0, 
                "Target links per mirror sollte nicht negativ sein");
            
            // Teste ungültige Struktur durch Zyklus-Einfügung
            middle.addChild(head); // Erstelle Zyklus
            Set<StructureNode> invalidLineNodes = Set.of(head, middle, end);
            assertFalse(head.isValidStructure(invalidLineNodes), 
                "Linie mit Zyklus sollte ungültig sein");
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
            assertFalse(endpoints.contains(middle)); // Middle ist kein Endpunkt
        }

        @Test
        @DisplayName("getOtherEndpoint findet anderen Endpunkt")
        void testGetOtherEndpoint() {
            LineMirrorNode otherFromHead = head.getOtherEndpoint();
            assertEquals(end, otherFromHead);

            LineMirrorNode otherFromEnd = end.getOtherEndpoint();
            assertEquals(head, otherFromEnd);

            // Middle ist kein Endpunkt, sollte null zurückgeben
            LineMirrorNode otherFromMiddle = middle.getOtherEndpoint();
            assertNull(otherFromMiddle);
        }

        @Test
        @DisplayName("getLineHead findet Head-Knoten")
        void testGetLineHead() {
            assertEquals(head, head.getLineHead());
            assertEquals(head, middle.getLineHead());
            assertEquals(head, end.getLineHead());
        }

        @Test
        @DisplayName("isMiddleNode identifiziert mittlere Knoten")
        void testIsMiddleNode() {
            assertFalse(head.isMiddleNode());
            assertTrue(middle.isMiddleNode());
            assertFalse(end.isMiddleNode());
        }

        @Test
        @DisplayName("getPositionInLine berechnet Position korrekt")
        void testGetPositionInLine() {
            assertEquals(0, head.getPositionInLine());
            assertEquals(1, middle.getPositionInLine());
            assertEquals(2, end.getPositionInLine());
        }

        @Test
        @DisplayName("Linien-Navigation mit längerer Linie")
        void testLongerLineNavigation() {
            LineMirrorNode node4 = new LineMirrorNode(4);
            LineMirrorNode node5 = new LineMirrorNode(5);

            end.addChild(node4);
            node4.addChild(node5);

            List<LineMirrorNode> endpoints = head.getEndpoints();
            assertEquals(2, endpoints.size());
            assertTrue(endpoints.contains(head));
            assertTrue(endpoints.contains(node5));

            assertEquals(0, head.getPositionInLine());
            assertEquals(1, middle.getPositionInLine());
            assertEquals(2, end.getPositionInLine());
            assertEquals(3, node4.getPositionInLine());
            assertEquals(4, node5.getPositionInLine());
        }

        @Test
        @DisplayName("Linien-Navigation mit MirrorProbe Integration")
        void testLineNavigationWithMirrorProbe() throws IOException {
            initSimulator();
            MirrorProbe probe = getMirrorProbe();
            assertNotNull(probe);

            // Verwende echte Mirrors für Navigations-Tests
            List<Mirror> simMirrors = probe.getMirrors();
            if (simMirrors.size() >= 3) {
                LineMirrorNode realHead = new LineMirrorNode(10, simMirrors.get(0));
                LineMirrorNode realMiddle = new LineMirrorNode(11, simMirrors.get(1));
                LineMirrorNode realEnd = new LineMirrorNode(12, simMirrors.get(2));

                realHead.setHead(true);
                realHead.addChild(realMiddle);
                realMiddle.addChild(realEnd);

                // Teste Navigation mit echten Mirrors
                assertEquals(realHead, realHead.getLineHead());
                assertEquals(realHead, realMiddle.getLineHead());
                assertEquals(realHead, realEnd.getLineHead());

                List<LineMirrorNode> realEndpoints = realHead.getEndpoints();
                assertEquals(2, realEndpoints.size());
                assertTrue(realEndpoints.contains(realHead));
                assertTrue(realEndpoints.contains(realEnd));
            }
        }
    }

    @Nested
    @DisplayName("LineMirrorNode Edge Cases und Integration")
    class LineMirrorNodeEdgeCasesTests {

        @Test
        @DisplayName("Edge Cases für Endpunkt-Funktionen")
        void testEndpointEdgeCases() {
            // Einzelner Knoten
            LineMirrorNode singleNode = new LineMirrorNode(1);
            List<LineMirrorNode> endpoints = singleNode.getEndpoints();
            assertEquals(1, endpoints.size());
            assertTrue(endpoints.contains(singleNode));

            // Zwei-Knoten-Linie
            LineMirrorNode node1 = new LineMirrorNode(1);
            LineMirrorNode node2 = new LineMirrorNode(2);
            node1.setHead(true);
            node1.addChild(node2);

            endpoints = node1.getEndpoints();
            assertEquals(2, endpoints.size());
            assertTrue(endpoints.contains(node1));
            assertTrue(endpoints.contains(node2));
        }

        @Test
        @DisplayName("Edge Cases für Head-Funktionen")
        void testHeadEdgeCases() {
            LineMirrorNode orphanNode = new LineMirrorNode(1);
            assertNull(orphanNode.getLineHead()); // Kein Head in isoliertem Knoten

            LineMirrorNode headlessNode = new LineMirrorNode(2);
            LineMirrorNode child = new LineMirrorNode(3);
            headlessNode.addChild(child);
            // Ohne explizit gesetzten Head
            assertNull(headlessNode.getLineHead());
            assertNull(child.getLineHead());
        }

        @Test
        @DisplayName("Performance mit größeren Linien und MirrorProbe")
        void testPerformanceWithLargerLines() throws IOException {
            initSimulator();
            MirrorProbe probe = getMirrorProbe();
            assertNotNull(probe);

            List<Mirror> simMirrors = probe.getMirrors();

            // Erstelle längere Linie mit verfügbaren Mirrors
            int lineLength = Math.min(10, simMirrors.size());
            if (lineLength >= 3) {
                long startTime = System.currentTimeMillis();

                LineMirrorNode head = new LineMirrorNode(1, simMirrors.get(0));
                head.setHead(true);
                LineMirrorNode current = head;

                for (int i = 1; i < lineLength; i++) {
                    LineMirrorNode next = new LineMirrorNode(i + 1, simMirrors.get(i));
                    current.addChild(next);
                    current = next;
                }

                // Performance-Tests
                List<LineMirrorNode> endpoints = head.getEndpoints();
                assertEquals(2, endpoints.size());

                long duration = System.currentTimeMillis() - startTime;
                assertTrue(duration < 1000); // Sollte unter 1 Sekunde sein
            }
        }


        @Test
        @DisplayName("Null-Handling und defensive Programmierung")
        void testNullHandlingAndDefensiveProgramming() {
            LineMirrorNode node = new LineMirrorNode(1);

            // Null-sichere Operationen
            assertNull(node.getOtherEndpoint()); // Korrigiert: keine Parameter
            assertNotNull(node.getEndpoints()); // Sollte nie null sein

            // Edge Cases mit null Mirrors
            assertNull(node.getMirror());
            assertEquals(0, node.getNumImplementedLinks());
            assertTrue(node.getImplementedLinks().isEmpty());
        }

        @Test
        @DisplayName("MirrorProbe Integration Edge Cases")
        void testMirrorProbeIntegrationEdgeCases() throws IOException {
            initSimulator();
            MirrorProbe probe = getMirrorProbe();
            assertNotNull(probe);

            // Versuche Edge Cases mit MirrorProbe
            assertTrue(probe.getNumMirrors() >= 0);
            assertTrue(probe.getNumReadyMirrors() >= 0);
            assertTrue(probe.getNumTargetMirrors() >= 0);

            double ratio = probe.getMirrorRatio();
            assertTrue(ratio >= 0.0 && ratio <= 1.0);

            // Listen sollten nie null sein
            assertNotNull(probe.getMirrors());
            assertEquals(probe.getNumMirrors(), probe.getMirrors().size());
        }

        @Test
        @DisplayName("Kompatibilität mit MirrorNode-Funktionen")
        void testMirrorNodeCompatibility() throws IOException {
            initSimulator();
            MirrorProbe probe = getMirrorProbe();
            assertNotNull(probe);

            List<Mirror> simMirrors = probe.getMirrors();
            if (!simMirrors.isEmpty()) {
                Mirror simMirror = simMirrors.get(0);
                LineMirrorNode lineNode = new LineMirrorNode(1, simMirror);

                // Teste, dass LineMirrorNode alle MirrorNode-Funktionen unterstützt
                assertEquals(simMirror, lineNode.getMirror());
                assertEquals(simMirror.getLinks().size(), lineNode.getNumImplementedLinks());
                assertEquals(simMirror.getLinks(), lineNode.getImplementedLinks());

                // Teste Typ-Ableitung
                assertEquals(StructureNode.StructureType.LINE, lineNode.deriveTypeId());
            }
        }
    }
}