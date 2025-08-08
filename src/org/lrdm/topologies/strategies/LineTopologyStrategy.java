
package org.lrdm.topologies.strategies;

import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.*;
import org.lrdm.topologies.node.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Eine spezialisierte {@link TopologyStrategy}, die Mirrors als Linien-Topologie mit zwei
 * Endpunkten verknüpft. Diese Strategie ist eine Portierung der {@link org.lrdm.topologies.strategies.LineTopologyStrategy} Klasse.
 * <p>
 * **Linien-Topologie-Eigenschaften**:
 * - Jeder Mirror (außer den Endpunkten) ist mit genau zwei anderen Mirrors verbunden
 * - Die beiden Endpunkte haben jeweils nur einen Nachbarn
 * - Bildet eine gerade Linie ohne Zyklen
 * - Benötigt mindestens 2 Knoten für eine funktionsfähige Linie
 * - Anzahl der Links ist (n-1) für n Knoten (Baum-Eigenschaft)
 * - Verwendet {@link LineMirrorNode} für spezifische Linien-Funktionalität
 * <p>
 * **Planungsebene vs. Ausführungsebene**:
 * - Planungsebene: `removeNodesFromStructure()` - plant strukturelle Linien-Änderungen ohne Zeitbezug
 * - Ausführungsebene: `handleRemoveMirrors()` - führt Mirror-Shutdown innerhalb der Linien-Planungsgrenzen aus
 * - Automatisches Mitwachsen: MirrorNode-Ebene passt sich an StructureNode-Linien-Planung an
 * <p>
 * **Linien-Constraints**: Im Gegensatz zu Ringen ist die Linien-Struktur azyklisch und
 * hat zwei eindeutige Endpunkte (Baum-Struktur mit maximalem Pfad).
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class LineTopologyStrategy extends BuildAsSubstructure {

    // ===== LINIEN-SPEZIFISCHE KONFIGURATION =====
    private int minLineSize = 2;
    private boolean allowLineExpansion = true;

    // ===== KONSTRUKTOREN =====

    public LineTopologyStrategy() {
        super();
    }

    public LineTopologyStrategy(int minLineSize) {
        super();
        this.minLineSize = Math.max(2, minLineSize);
    }

    public LineTopologyStrategy(int minLineSize, boolean allowLineExpansion) {
        super();
        this.minLineSize = Math.max(2, minLineSize);
        this.allowLineExpansion = allowLineExpansion;
    }

    // ===== ÜBERSCHREIBUNG DER BUILD-AS-SUBSTRUCTURE-METHODEN =====

    /**
     * **PLANUNGSEBENE**: Erstellt die Linien-Struktur mit zwei Endpunkten.
     * Überschreibt BuildAsSubstructure für Linien-spezifische Logik.
     * NUR STRUKTURPLANUNG - keine Mirror-Links!
     */
    @Override
    protected MirrorNode buildStructure(int totalNodes) {
        if (totalNodes <= 0 || network == null) {
            return null;
        }

        // Erstelle alle Linien-Knoten
        List<LineMirrorNode> lineNodes = new ArrayList<>();

        for (int i = 0; i < totalNodes; i++) {
            LineMirrorNode lineNode = getMirrorNodeFromIterator();
            lineNodes.add(lineNode);
            addToStructureNodes(lineNode);
            if (lineNode.getMirror().isRoot()) {
                setCurrentStructureRoot(lineNode);
                lineNode.setHead(StructureNode.StructureType.LINE, true);
            }
        }

        if (lineNodes.size() < minLineSize) return null;

        // **NUR STRUKTURPLANUNG**: Erstelle StructureNode-Verbindungen (keine Mirror-Links!)
        buildLineStructurePlanning(lineNodes);

        // Setze erste Knoten als Head und Root
        // 2. Setze den ersten Knoten als Head und Root
        LineMirrorNode root = lineNodes.stream()
                .filter(node -> node.getMirror() != null && node.isRoot())
                .findFirst()
                .orElse(null);
        if (root != null) root.setParent(null);

        return root;
    }

    /**
     * **PLANUNGSEBENE**: Fügt neue Knoten zur Linien-Struktur hinzu.
     * Überschreibt BuildAsSubstructure für Linien-Erweiterungen an den Endpunkten.
     * NUR STRUKTURPLANUNG - keine Mirror-Links!
     */
    @Override
    protected int addNodesToStructure(Set<Mirror> nodesToAdd) {
        if (nodesToAdd.isEmpty() || getCurrentStructureRoot() == null || !allowLineExpansion) {
            return 0;
        }

        LineMirrorNode head = getLineHead();
        if (head == null) return 0;

        setMirrorIterator(nodesToAdd.iterator());
        List<LineMirrorNode> lineNodes = getAllLineNodes();
        int actuallyAdded = 0;

        for(int i = 0; i < nodesToAdd.size(); i++) {
            LineMirrorNode nodeAtEnd = selectEndpoint(lineNodes);
            if(nodeAtEnd!=null)nodeAtEnd.addChild(getMirrorNodeFromIterator());
            else{
                continue;
            }
            actuallyAdded++;
        }

        return actuallyAdded;
    }

    /**
     * **PLANUNGSEBENE**: Entfernt Knoten aus der Linien-Struktur.
     * Überschreibt BuildAsSubstructure für Linien-erhaltende Entfernung.
     * Entfernt bevorzugt Endpunkte, um die Linien-Struktur zu erhalten.
     */
    @Override
    protected Set<MirrorNode> removeNodesFromStructure(int nodesToRemove) {
        if (nodesToRemove <= 0 || getCurrentStructureRoot() == null) {
            return new HashSet<>();
        }

        List<LineMirrorNode> lineNodes = getAllLineNodes();
        if (lineNodes.size() - nodesToRemove < minLineSize) {
            nodesToRemove = lineNodes.size() - minLineSize;
        }

        Set<MirrorNode> removedMirrorNodes = new HashSet<>();

        // Linien-Entfernung: Entferne bevorzugt Endpunkte
        for (int i = 0; i < nodesToRemove && !lineNodes.isEmpty(); i++) {
            LineMirrorNode nodeToRemove = selectEndpoint(lineNodes);
            if (nodeToRemove != null) {
                LineMirrorNode parentLineNode = (LineMirrorNode) nodeToRemove.getParent();
                if (parentLineNode == null) continue;
                parentLineNode.removeChild(nodeToRemove);
                removeFromStructureNodes(nodeToRemove);
                removedMirrorNodes.add(nodeToRemove);
                lineNodes.remove(nodeToRemove);
            }
        }

        return removedMirrorNodes;
    }

    @Override
    protected boolean validateTopology() {
        return isLineIntact();
    }

    /**
     * Factory-Methode für Linien-spezifische MirrorNode-Erstellung.
     * Überschreibt BuildAsSubstructure für die LineMirrorNode-Erstellung.
     *
     * @param mirror Der Mirror, für den ein MirrorNode erstellt werden soll
     * @return Neuer LineMirrorNode
     */
    @Override
    protected MirrorNode createMirrorNodeForMirror(Mirror mirror) {
        return new LineMirrorNode(mirror.getID(), mirror);
    }

    /**
     * Erstellt einen neuen MirrorNode mit Mirror aus dem Iterator.
     * AKTUALISIERT: Fügt den Knoten automatisch zu structureNodes hinzu.
     *
     * @return Neuer MirrorNode mit zugeordnetem Mirror oder null
     */
    @Override
    protected LineMirrorNode getMirrorNodeFromIterator() {
        if (mirrorIterator != null && mirrorIterator.hasNext()) {
            LineMirrorNode node = (LineMirrorNode) super.getMirrorNodeFromIterator();
            node.addNodeType(StructureNode.StructureType.LINE);
            return node;
        }
        return null;
    }

    // ===== FEHLENDE TOPOLOGY STRATEGY METHODEN =====

    /**
     * Berechnet die erwartete Anzahl der Links, wenn die gegebene Aktion ausgeführt wird.
     * Linien-spezifische Implementierung basierend auf den drei Action-Typen.
     * Überschreibt die abstrakte Methode aus TopologyStrategy.
     *
     * @param a Die Action, deren Auswirkungen berechnet werden sollen
     * @return Anzahl der erwarteten Links nach Ausführung der Action
     */
    @Override
    public int getPredictedNumTargetLinks(Action a) {
        if (a instanceof MirrorChange mirrorChange) {
            // MirrorChange: Neue Mirror-Anzahl → neue Link-Anzahl
            int newMirrors = mirrorChange.getNewMirrors();
            return calculateExpectedLinks(newMirrors);
        } else if (a instanceof TargetLinkChange targetLinkChange) {
            // TargetLinkChange: Begrenzt durch Linien-Eigenschaft
            Network network = targetLinkChange.getNetwork();
            int maxPossibleLinks = Math.max(0, network.getNumMirrors() - 1);
            int requestedTotalLinks = targetLinkChange.getNewLinksPerMirror() * network.getNumMirrors();
            return Math.min(requestedTotalLinks, maxPossibleLinks);
        } else if (a instanceof TopologyChange topologyChange) {
            // TopologyChange: Delegiere an neue Strategie
            return topologyChange.getNewTopology().getPredictedNumTargetLinks(a);
        }

        return 0;
    }

    // ===== LINIEN-SPEZIFISCHE HILFSMETHODEN - NUR PLANUNGSEBENE =====

    /**
     * **NUR PLANUNGSEBENE**: Baut die Linien-Struktur auf.
     * Erstellt KEINE Mirror-Links - nur StructureNode-Verbindungen!
     */
    private void buildLineStructurePlanning(List<LineMirrorNode> lineNodes) {
        if (lineNodes.size() < minLineSize) return;

        // **NUR STRUKTURPLANUNG**: Verbinde Linien-Knoten sequenziell (keine Mirror-Links!)
        for (int i = 0; i < lineNodes.size() - 1; i++) {
            LineMirrorNode current = lineNodes.get(i);
            LineMirrorNode next = lineNodes.get(i + 1);

            // StructureNode-Verbindung
            current.addChild(next);
        }
    }

    /**
     * **NUR PLANUNGSEBENE**: Erstellt einen neuen Linien-Knoten mit struktureller Planung.
     */
    private LineMirrorNode createLineNodeForStructure() {
        if (!hasNextMirror()) return null;

        Mirror mirror = getNextMirror();
        assert mirror != null;
        LineMirrorNode lineNode = new LineMirrorNode(mirror.getID(), mirror);
        addToStructureNodes(lineNode);

        return lineNode;
    }

    /**
     * **NUR PLANUNGSEBENE**: Erweitert eine Linie an einem Endpunkt um einen neuen Knoten.
     * Erstellt KEINE Mirror-Links - nur StructureNode-Verbindungen!
     */
    private void extendLineAtEndpointStructuralPlanning(LineMirrorNode endpoint, LineMirrorNode newNode) {
        if (endpoint == null || newNode == null) return;

        // **NUR STRUKTURPLANUNG**: StructureNode-Verbindung (keine Mirror-Links!)
        endpoint.addChild(newNode);
        newNode.setParent(endpoint);

        // KEINE Mirror-Link-Erstellung hier! Nur Strukturplanung!
    }

    /**
     * Wählt einen Endpunkt für die Entfernung aus der Linie aus.
     * Bevorzugt Nicht-Head-Endpunkte, wenn möglich.
     */
    private LineMirrorNode selectEndpoint(List<LineMirrorNode> lineNodes) {
        if (lineNodes.isEmpty()) return null;

        List<LineMirrorNode> endpoints = new ArrayList<>(lineNodes.stream()
                .filter(this::isLineEndpoint)
                .toList());

        if (endpoints.isEmpty()) return null;

        LineMirrorNode currentHead = endpoints.get(0);

        while (!currentHead.getChildren(StructureNode.StructureType.LINE).isEmpty()) {
            if (endpoints.isEmpty()) return null;
            currentHead = (LineMirrorNode) currentHead.getChildren().stream().findFirst().orElse(null);
            endpoints.remove(currentHead);
            if (currentHead == null) return null;
        }

        return currentHead;
    }

    /**
     * Prüft, ob ein Knoten ein Linien-Endpunkt ist.
     */
    private boolean isLineEndpoint(LineMirrorNode node) {
        if (node == null) return false;

        // Ein Endpunkt hat entweder nur einen Nachbarn (normale Endpunkte)
        // oder ist die Wurzel ohne Parent (aber mit nur einem Kind)
        int neighbors = node.getChildren().size();
        if (node.getParent() != null) {
            neighbors++; // Parent zählt als Nachbar
        }

        return neighbors == 1;
    }

    // ===== TYPSICHERE HILFSMETHODEN =====

    /**
     * Gibt den Linien-Head zurück.
     */
    private LineMirrorNode getLineHead() {
        return (LineMirrorNode) getAllStructureNodes().stream()
                .filter(StructureNode::isHead)
                .findFirst()
                .orElse(null);
    }

    /**
     * Gibt die Head-ID der Linie zurück.
     */
    private int getLineHeadId() {
        LineMirrorNode head = getLineHead();
        return head != null ? head.getId() : -1;
    }

    /**
     * Gibt alle Linien-Knoten als typisierte Liste zurück.
     */
    private List<LineMirrorNode> getAllLineNodes() {
        return getAllStructureNodes().stream()
                .filter(node -> node instanceof LineMirrorNode)
                .map(node -> (LineMirrorNode) node)
                .collect(Collectors.toList());
    }

    /**
     * Findet alle Endpunkte der Linie.
     */
    private List<LineMirrorNode> findLineEndpoints(LineMirrorNode root) {
        if (root == null) return new ArrayList<>();

        List<LineMirrorNode> endpoints = new ArrayList<>();
        List<LineMirrorNode> allNodes = getAllLineNodes();

        for (LineMirrorNode node : allNodes) {
            if (isLineEndpoint(node)) {
                endpoints.add(node);
            }
        }

        return endpoints;
    }

    // ===== TOPOLOGY STRATEGY INTERFACE IMPLEMENTATION =====

    /**
     * Returns the expected number of total links in the network according to the line topology.
     * For n mirrors, the number of links is (n-1) (tree property - linear tree).
     *
     * @param n {@link Network} the network
     * @return number of total links expected for the network
     */
    @Override
    public int getNumTargetLinks(Network n) {
        int numMirrors = n.getNumMirrors();
        return Math.max(0, numMirrors - 1);
    }

    // ===== LINIEN-ANALYSE =====

    /**
     * Prüft, ob die Linien-Struktur intakt ist.
     */
    public boolean isLineIntact() {
        List<LineMirrorNode> lineNodes = getAllLineNodes();
        if (lineNodes.size() < minLineSize) return false;

        // Prüfe, ob genau 2 Endpunkte existieren
        long endpointCount = lineNodes.stream()
                .filter(this::isLineEndpoint)
                .count();

        return endpointCount == 2;
    }

    /**
     * Berechnet die durchschnittliche Pfadlänge in der Linie.
     */
    public double calculateAverageLinePathLength() {
        List<LineMirrorNode> lineNodes = getAllLineNodes();
        if (lineNodes.size() <= 1) return 0.0;

        // In einer Linie ist die durchschnittliche Pfadlänge (n-1)/2
        return (lineNodes.size() - 1) / 2.0;
    }

    /**
     * Gibt detaillierte Linien-Informationen zurück.
     */
    public Map<String, Object> getDetailedLineInfo() {
        Map<String, Object> info = new HashMap<>();
        List<LineMirrorNode> lineNodes = getAllLineNodes();

        info.put("totalNodes", lineNodes.size());
        info.put("expectedLinks", Math.max(0, lineNodes.size() - 1));
        info.put("endpointCount", lineNodes.stream().filter(this::isLineEndpoint).count());
        info.put("isIntact", isLineIntact());
        info.put("averagePathLength", calculateAverageLinePathLength());
        info.put("minLineSize", minLineSize);
        info.put("allowExpansion", allowLineExpansion);

        return info;
    }

    // ===== HILFSMETHODEN =====

    /**
     * Berechnet die erwartete Link-Anzahl für eine gegebene Knotenzahl.
     * Für Linien-Topologie: (n-1) Links für n Knoten.
     *
     * @param nodeCount Anzahl der Knoten
     * @return Erwartete Anzahl der Links für Linien-Topologie
     */
    public static int calculateExpectedLinks(int nodeCount) {
        return Math.max(0, nodeCount - 1);
    }

    // ===== KONFIGURATION =====

    public int getMinLineSize() {
        return minLineSize;
    }

    public void setMinLineSize(int minLineSize) {
        this.minLineSize = Math.max(2, minLineSize);
    }

    public boolean isAllowLineExpansion() {
        return allowLineExpansion;
    }

    public void setAllowLineExpansion(boolean allowLineExpansion) {
        this.allowLineExpansion = allowLineExpansion;
    }

    @Override
    public String toString() {
        return "LineTopologyStrategy{" +
                "minLineSize=" + minLineSize +
                ", allowExpansion=" + allowLineExpansion +
                ", nodes=" + getAllStructureNodes().size() +
                "}";
    }
}