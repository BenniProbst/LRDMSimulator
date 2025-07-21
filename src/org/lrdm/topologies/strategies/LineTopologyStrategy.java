
package org.lrdm.topologies.strategies;

import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.*;
import org.lrdm.topologies.node.*;
import org.lrdm.topologies.node.StructureNode.StructureType;
import org.lrdm.util.IDGenerator;

import java.util.*;

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
     * Portiert die buildLine-Logik aus LineBuilder.
     */
    @Override
    protected MirrorNode buildStructure(int totalNodes, Properties props) {
        if (totalNodes < minLineSize || !mirrorIterator.hasNext()) return null;

        // Erstelle alle Linien-Knoten
        List<LineMirrorNode> lineNodes = new ArrayList<>();
        for (int i = 0; i < totalNodes; i++) {
            if (!mirrorIterator.hasNext()) break;
            
            Mirror mirror = mirrorIterator.next();
            LineMirrorNode lineNode = new LineMirrorNode(mirror.getID(), mirror);
            lineNodes.add(lineNode);
            this.addToStructureNodes(lineNode);
        }

        if (lineNodes.size() < minLineSize) return null;

        // Strukturplanung: Erstellen von Linien-Verbindungen
        buildLineStructureWithLinks(lineNodes, simTime, props);

        // Setze ersten Knoten als Head
        LineMirrorNode head = lineNodes.get(0);
        head.setHead(true);

        return head;
    }

    /**
     * **PLANUNGSEBENE**: Fügt neue Knoten zur Linien-Struktur hinzu.
     * Überschreibt BuildAsSubstructure für Linien-Erweiterungen an den Endpunkten.
     * Portiert die addNodesToLine-Logik aus LineBuilder.
     */
    @Override
    protected int addNodesToStructure(int nodesToAdd) {
        if (nodesToAdd <= 0 || !allowLineExpansion) return 0;

        LineMirrorNode head = getLineHead();
        if (head == null) return 0;

        List<LineMirrorNode> endpoints = findLineEndpoints(head);
        if (endpoints.isEmpty()) return 0;

        int actuallyAdded = 0;
        int endpointIndex = 0;

        // Linien-Erweiterung: Neue Knoten an den Endpunkten anhängen
        while (actuallyAdded < nodesToAdd && endpointIndex < endpoints.size() && mirrorIterator.hasNext()) {
            LineMirrorNode endpoint = endpoints.get(endpointIndex);

            // Strukturplanung: Erstelle neuen Knoten am Endpunkt
            LineMirrorNode newNode = createLineNode(0, new Properties());
            if (newNode != null) {
                // Erweitere Linie am Endpunkt
                extendLineAtEndpoint(endpoint, newNode, 0, new Properties());
                actuallyAdded++;
                
                // Aktualisiere Endpunkt-Liste für Round-Robin
                endpoints = findLineEndpoints(head);
            }

            endpointIndex = (endpointIndex + 1) % endpoints.size();
        }

        return actuallyAdded;
    }

    /**
     * **PLANUNGSEBENE**: Entfernt Knoten aus der Linien-Struktur.
     * Überschreibt BuildAsSubstructure für Linien-erhaltende Entfernung.
     * Entfernt bevorzugt Endpunkte, um die Linien-Struktur zu erhalten.
     */
    @Override
    protected int removeNodesFromStructure(int nodesToRemove) {
        if (nodesToRemove <= 0) return 0;

        List<LineMirrorNode> lineNodes = getAllLineNodes();
        if (lineNodes.size() - nodesToRemove < minLineSize) {
            nodesToRemove = lineNodes.size() - minLineSize;
        }
        if (nodesToRemove <= 0) return 0;

        int actuallyRemoved = 0;

        // Linien-Entfernung: Entferne bevorzugt Endpunkte
        for (int i = 0; i < nodesToRemove && !lineNodes.isEmpty(); i++) {
            LineMirrorNode nodeToRemove = selectEndpointForRemoval(lineNodes);
            if (nodeToRemove != null) {
                removeNodeFromLineStructuralPlanning(nodeToRemove);
                lineNodes.remove(nodeToRemove);
                actuallyRemoved++;
            }
        }

        return actuallyRemoved;
    }


    /**
     * **AUSFÜHRUNGSEBENE**: Überschreibt die Mirror-Entfernung für Linien-Erhaltung.
     * Führt Mirror-Shutdown innerhalb der strukturellen Linien-Planungsgrenzen aus.
     * Arbeitet komplementär zu removeNodesFromStructure.
     */
    @Override
    public Set<Mirror> handleRemoveMirrors(Network n, int removeMirrors, Properties props, int simTime) {
        if (removeMirrors <= 0) {
            return new HashSet<>();
        }

        List<LineMirrorNode> lineNodes = getAllLineNodes();
        if (lineNodes.size() - removeMirrors < minLineSize) {
            removeMirrors = lineNodes.size() - minLineSize;
        }
        if (removeMirrors <= 0) {
            return new HashSet<>();
        }

        Set<Mirror> cleanedMirrors = new HashSet<>();
        int actuallyRemoved = 0;

        // Ausführungsebene: Linien-bewusste Mirror-Entfernung
        for (int i = 0; i < removeMirrors && !lineNodes.isEmpty(); i++) {
            LineMirrorNode targetNode = selectEndpointForRemoval(lineNodes);
            if (targetNode != null) {
                Mirror targetMirror = targetNode.getMirror();
                if (targetMirror != null) {
                    // Mirror-Shutdown auf Ausführungsebene
                    targetMirror.shutdown(simTime);
                    cleanedMirrors.add(targetMirror);
                    actuallyRemoved++;
                    lineNodes.remove(targetNode);
                }
            }
        }

        // Synchronisiere Plannings- und Ausführungsebene
        removeNodesFromStructure(actuallyRemoved);

        return cleanedMirrors;
    }

    // ===== LINIEN-SPEZIFISCHE HILFSMETHODEN =====

    /**
     * Baut die Linien-Struktur mit echten Mirror-Links auf.
     * Portiert die Linien-Verbindungslogik aus LineBuilder.
     */
    private void buildLineStructureWithLinks(List<LineMirrorNode> lineNodes, int simTime, Properties props) {
        if (lineNodes.size() < minLineSize) return;

        // Strukturplanung und Ausführung: Verbinde Linien-Knoten sequenziell
        for (int i = 0; i < lineNodes.size() - 1; i++) {
            LineMirrorNode current = lineNodes.get(i);
            LineMirrorNode next = lineNodes.get(i + 1);

            // StructureNode-Verbindung
            Set<StructureType> typeIds = new HashSet<>();
            typeIds.add(StructureType.LINE);

            Map<StructureType, Integer> headIds = new HashMap<>();
            headIds.put(StructureType.LINE, getLineHeadId(lineNodes));

            current.addChild(next, typeIds, headIds);

            // Mirror-Link-Erstellung
            createLineMirrorLink(current, next, simTime, props);
        }
    }

    /**
     * Erstellt einen neuen Linien-Knoten mit struktureller Planung.
     */
    private LineMirrorNode createLineNode(int simTime, Properties props) {
        if (!mirrorIterator.hasNext()) return null;

        Mirror mirror = mirrorIterator.next();
        LineMirrorNode lineNode = new LineMirrorNode(mirror.getID(), mirror);
        this.addToStructureNodes(lineNode);
        
        return lineNode;
    }

    /**
     * Erweitert eine Linie an einem Endpunkt um einen neuen Knoten.
     * Portiert die Linien-Erweiterungslogik aus LineBuilder.
     */
    private void extendLineAtEndpoint(LineMirrorNode endpoint, LineMirrorNode newNode, 
                                      int simTime, Properties props) {
        // Strukturplanung: Füge neuen Knoten am Endpunkt hinzu
        Set<StructureType> typeIds = new HashSet<>();
        typeIds.add(StructureType.LINE);

        Map<StructureType, Integer> headIds = new HashMap<>();
        headIds.put(StructureType.LINE, getLineHeadId());

        endpoint.addChild(newNode, typeIds, headIds);

        // Ausführungsebene: Erstelle einen neuen Mirror-Link
        createLineMirrorLink(endpoint, newNode, simTime, props);
    }

    /**
     * Wählt einen Endpunkt für die Entfernung aus der Linie aus.
     * Bevorzugt Nicht-Head-Endpunkte, wenn möglich.
     */
    private LineMirrorNode selectEndpointForRemoval(List<LineMirrorNode> lineNodes) {
        if (lineNodes.isEmpty()) return null;

        // Finde alle Endpunkte
        List<LineMirrorNode> endpoints = new ArrayList<>();
        for (LineMirrorNode node : lineNodes) {
            if (isLineEndpoint(node)) {
                endpoints.add(node);
            }
        }

        if (endpoints.isEmpty()) return null;

        // Bevorzuge Nicht-Head-Endpunkte
        for (LineMirrorNode endpoint : endpoints) {
            if (!endpoint.isHead()) {
                return endpoint;
            }
        }

        // Fallback: Verwende ersten verfügbaren Endpunkt
        return endpoints.get(0);
    }

    /**
     * **PLANUNGSEBENE**: Entfernt Knoten aus der Linien-Struktur-Planung.
     * Arbeitet ohne Zeitbezug - nur strukturelle Linien-Änderungen.
     */
    private void removeNodeFromLineStructuralPlanning(LineMirrorNode nodeToRemove) {
        if (nodeToRemove == null) return;

        // Strukturplanung: Verbinde Parent und Child direkt
        StructureNode parent = nodeToRemove.getParent();
        List<StructureNode> children = new ArrayList<>(nodeToRemove.getChildren());

        // Entferne Knoten aus Parent-Child-Beziehungen
        if (parent != null) {
            parent.removeChild(nodeToRemove);
        }

        for (StructureNode child : children) {
            nodeToRemove.removeChild(child);
            
            // Verbinde Parent direkt mit Child (falls beide existieren)
            if (parent != null) {
                Set<StructureType> typeIds = new HashSet<>();
                typeIds.add(StructureType.LINE);

                Map<StructureType, Integer> headIds = new HashMap<>();
                headIds.put(StructureType.LINE, getLineHeadId());

                parent.addChild(child, typeIds, headIds);
            }
        }

        // Entferne aus Strukturverwaltung
        this.removeFromStructureNodes(nodeToRemove);
    }

    /**
     * Erstellt Mirror-Link mit Linien-Validierung.
     * Ausführungsebene: Echte Mirror-Verbindungen.
     */
    private void createLineMirrorLink(LineMirrorNode from, LineMirrorNode to, int simTime, Properties props) {
        if (from == null || to == null) return;

        Mirror fromMirror = from.getMirror();
        Mirror toMirror = to.getMirror();

        if (fromMirror != null && toMirror != null) {
            if (!isAlreadyConnected(fromMirror, toMirror)) {
                Link newLink = new Link(IDGenerator.getInstance().getNextID(),fromMirror, toMirror, simTime, props);
                // Links werden über das Network verwaltet
                if (network != null) {
                    network.getLinks().add(newLink);
                }
            }
        }
    }

    /**
     * Prüft bestehende Mirror-Verbindungen.
     */
    private boolean isAlreadyConnected(Mirror mirror1, Mirror mirror2) {
        if (mirror1 == null || mirror2 == null) return false;

        for (Link link : mirror1.getLinks()) {
            if ((link.getSource().equals(mirror1) && link.getTarget().equals(mirror2)) ||
                (link.getSource().equals(mirror2) && link.getTarget().equals(mirror1))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Prüft, ob ein Knoten ein Linien-Endpunkt ist.
     */
    private boolean isLineEndpoint(LineMirrorNode node) {
        if (node == null) return false;
        
        // Endpunkt hat maximal einen Nachbarn
        int connectionCount = 0;
        if (node.getParent() != null) connectionCount++;
        connectionCount += node.getChildren().size();
        
        return connectionCount <= 1;
    }

    // ===== TYPSICHERE HILFSMETHODEN =====

    /**
     * Gibt den Linien-Head zurück.
     */
    private LineMirrorNode getLineHead() {
        MirrorNode root = getCurrentStructureRoot();
        return (root instanceof LineMirrorNode) ? (LineMirrorNode) root : null;
    }

    /**
     * Gibt die Head-ID der Linie zurück.
     */
    private int getLineHeadId() {
        LineMirrorNode head = getLineHead();
        return (head != null) ? head.getId() : -1;
    }

    /**
     * Gibt die Head-ID für eine Liste von Linien-Knoten zurück.
     */
    private int getLineHeadId(List<LineMirrorNode> lineNodes) {
        if (lineNodes.isEmpty()) return -1;
        
        for (LineMirrorNode node : lineNodes) {
            if (node.isHead()) {
                return node.getId();
            }
        }
        
        // Fallback: Ersten Knoten als Head verwenden
        return lineNodes.get(0).getId();
    }

    /**
     * Gibt alle Linien-Knoten als typisierte Liste zurück.
     */
    private List<LineMirrorNode> getAllLineNodes() {
        List<LineMirrorNode> lineNodes = new ArrayList<>();
        
        for (MirrorNode node : getAllStructureNodes()) {
            if (node instanceof LineMirrorNode) {
                lineNodes.add((LineMirrorNode) node);
            }
        }
        
        return lineNodes;
    }

    /**
     * Findet alle Endpunkte der Linie.
     * Portiert die findLineEndpoints-Logik aus LineBuilder.
     */
    private List<LineMirrorNode> findLineEndpoints(LineMirrorNode root) {
        List<LineMirrorNode> endpoints = new ArrayList<>();

        if (root instanceof LineMirrorNode) {
            LineMirrorNode lineRoot = (LineMirrorNode) root;
            // Verwende LineMirrorNode-spezifische Methoden, falls verfügbar
            // sonst manuelle Endpunkt-Suche anwenden
            for (LineMirrorNode node : getAllLineNodes()) {
                if (isLineEndpoint(node)) {
                    endpoints.add(node);
                }
            }
        }

        return endpoints;
    }

    // ===== TOPOLOGY STRATEGY INTERFACE IMPLEMENTATION =====

    /**
     * Initializes the network by connecting mirrors in a line topology.
     *
     * @param n the {@link Network}
     * @param props {@link Properties} of the simulation
     * @return {@link Set} of all {@link Link}s created
     */
    @Override
    public Set<Link> initNetwork(Network n, Properties props) {
        this.network = n;
        this.idGenerator = IDGenerator.getInstance();
        
        if (n.getNumMirrors() < minLineSize) {
            return new HashSet<>();
        }

        this.mirrorIterator = n.getMirrors().iterator();
        MirrorNode root = buildStructure(n.getNumMirrors(), props);
        
        if (root != null) {
            setCurrentStructureRoot(root);
            return buildAndConnectLinks(root, props, 0);
        }
        
        return new HashSet<>();
    }

    /**
     * Startet das Netzwerk komplett neu mit der Linien-Topologie.
     *
     * @param n       Das Netzwerk
     * @param props   Simulation Properties
     * @param simTime Aktuelle Simulationszeit
     * @return
     */
    @Override
    public Set<Link> restartNetwork(Network n, Properties props, int simTime) {
        // Lösche alle bestehenden Links
        super.restartNetwork(n, props, simTime);
        
        // Baue Netzwerk neu auf
        initNetwork(n, props);
    }

    /**
     * Adds the requested number of mirrors to the network and connects them accordingly.
     *
     * @param n the {@link Network}
     * @param newMirrors number of mirrors to add
     * @param props {@link Properties} of the simulation
     * @param simTime current simulation time
     */
    @Override
    public void handleAddNewMirrors(Network n, int newMirrors, Properties props, int simTime) {
        if (newMirrors <= 0) return;

        this.network = n;
        
        // Füge neue Mirrors zum Netzwerk hinzu
        List<Mirror> addedMirrors = createMirrors(newMirrors, simTime, props);
        n.getMirrors().addAll(addedMirrors);
        
        // Aktualisiere Mirror-Iterator für neue Mirrors
        this.mirrorIterator = addedMirrors.iterator();
        
        // Füge Knoten zur bestehenden Struktur hinzu
        addNodesToStructure(newMirrors);
        
        // Erstelle Links für die neue Struktur
        MirrorNode root = getCurrentStructureRoot();
        if (root != null) {
            buildAndConnectLinks(root, props, 0);
        }
    }

    /**
     * Returns the expected number of total links in the network according to the line topology.
     * For n mirrors, the number of links is (n-1) (tree property - linear tree).
     *
     * @param n {@link Network} the network
     * @return number of total links expected for the network
     */
    @Override
    public int getNumTargetLinks(Network n) {
        if (n == null) return 0;
        
        int numMirrors = n.getNumMirrors();
        return calculateExpectedLinks(numMirrors);
    }

    /**
     * Berechnet die erwartete Anzahl der Links, wenn die gegebene Aktion ausgeführt wird.
     * Linien-spezifische Implementierung basierend auf den drei Action-Typen:
     * - MirrorChange: Verändert Linien-Größe dynamisch (n Mirrors → n-1 Links)
     * - TargetLinkChange: Hat BEGRENZTEN Effekt bei Linie (max. n-1 Links möglich)
     * - TopologyChange: Komplette Rekonstruktion mit verfügbaren Mirrors
     *
     * @param a Die Action, deren Auswirkungen berechnet werden sollen
     * @return Anzahl der erwarteten Links nach Ausführung der Action
     */
    @Override
    public int getPredictedNumTargetLinks(Action a) {
        if (a == null) {
            // Fallback: Verwende aktuelle Netzwerk-Link-Anzahl
            return network != null ? getNumTargetLinks(network) : 0;
        }

        // 1. MirrorChange: Linien-Größe ändert sich dynamisch
        if (a instanceof MirrorChange mirrorChange) {
            int newMirrorCount = mirrorChange.getNewMirrors();
            return calculateExpectedLinks(newMirrorCount);
        }

        // 2. TargetLinkChange: BEGRENZTER Effekt bei Linien-Topologie
        // Linien-Constraint: Maximal (n-1) Links möglich für n Knoten
        if (a instanceof TargetLinkChange targetLinkChange) {
            if (network != null) {
                int currentMirrors = network.getNumMirrors();
                int requestedTotalLinks = targetLinkChange.getNewLinksPerMirror() * currentMirrors;
                int maxPossibleLinks = calculateExpectedLinks(currentMirrors);
                
                // Linien-Topologie kann nur bis zu (n-1) Links haben
                return Math.min(requestedTotalLinks, maxPossibleLinks);
            }
            return 0;
        }

        // 3. TopologyChange: Komplette Rekonstruktion
        if (a instanceof TopologyChange topologyChange) {
            TopologyStrategy newTopology = topologyChange.getNewTopology();
            
            // Wenn neue Topologie auch Linie ist: Verwende aktuelle Mirror-Anzahl
            if (newTopology instanceof LineTopologyStrategy) {
                int currentMirrors = network != null ? network.getNumMirrors() : 0;
                return calculateExpectedLinks(currentMirrors);
            }
            
            // Andere Topologie: Delegierte an neue Strategie
            if (network != null) {
                return newTopology.getNumTargetLinks(network);
            }
            return 0;
        }

        // Fallback: Aktuelle Linien-Link-Anzahl über getNumTargetLinks
        return network != null ? getNumTargetLinks(network) : 0;
    }

    /**
     * Baut die tatsächlichen Links zwischen den Mirrors basierend auf der StructureNode-Struktur auf.
     *
     * @param root    Die Root-Node der Struktur
     * @param props   Simulation Properties
     * @param simTime
     * @return Set aller erstellten Links
     */
    @Override
    protected Set<Link> buildAndConnectLinks(MirrorNode root, Properties props, int simTime) {
        Set<Link> createdLinks = new HashSet<>();
        
        if (root == null || network == null) return createdLinks;

        // Durchlaufe alle Struktur-Knoten und erstelle Mirror-Links
        Set<MirrorNode> processedNodes = new HashSet<>();
        Queue<MirrorNode> nodeQueue = new LinkedList<>();
        nodeQueue.add(root);

        while (!nodeQueue.isEmpty()) {
            MirrorNode current = nodeQueue.poll();
            if (processedNodes.contains(current)) continue;
            processedNodes.add(current);

            // Erstelle Links zu allen Kindern
            for (StructureNode child : current.getChildren()) {
                if (child instanceof MirrorNode childMirror) {
                    Mirror currentMirror = current.getMirror();
                    Mirror targetMirror = childMirror.getMirror();

                    if (currentMirror != null && targetMirror != null) {
                        if (!isAlreadyConnected(currentMirror, targetMirror)) {
                            Link newLink = new Link(IDGenerator.getInstance().getNextID(),currentMirror, targetMirror, 0, props);
                            network.getLinks().add(newLink);
                            createdLinks.add(newLink);
                        }
                    }

                    nodeQueue.add(childMirror);
                }
            }
        }

        return createdLinks;
    }

    // ===== LINIEN-ANALYSE =====

    /**
     * Prüft, ob die Linien-Struktur intakt ist.
     */
    public boolean isLineIntact() {
        LineMirrorNode head = getLineHead();
        if (head == null) return false;

        List<LineMirrorNode> endpoints = findLineEndpoints(head);
        return endpoints.size() == 2; // Linie muss genau 2 Endpunkte haben
    }

    /**
     * Berechnet die durchschnittliche Pfadlänge in der Linie.
     */
    public double calculateAverageLinePathLength() {
        List<LineMirrorNode> lineNodes = getAllLineNodes();
        if (lineNodes.size() < 2) return 0.0;

        // Für Linie: Durchschnittliche Pfadlänge = (n+1)/3
        return (lineNodes.size() + 1.0) / 3.0;
    }

    /**
     * Gibt detaillierte Linien-Informationen zurück.
     */
    public Map<String, Object> getDetailedLineInfo() {
        Map<String, Object> info = new HashMap<>();
        List<LineMirrorNode> lineNodes = getAllLineNodes();
        List<LineMirrorNode> endpoints = findLineEndpoints(getLineHead());

        info.put("totalNodes", lineNodes.size());
        info.put("totalLinks", calculateExpectedLinks(lineNodes.size()));
        info.put("endpoints", endpoints.size());
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
                ", substructureId=" + getSubstructureId() +
                '}';
    }
}
