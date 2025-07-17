package org.lrdm.topologies.strategies;

import org.lrdm.topologies.node.DepthLimitedTreeMirrorNode;
import org.lrdm.topologies.node.MirrorNode;
import org.lrdm.topologies.node.StructureNode;
import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.effectors.Action;
import org.lrdm.effectors.MirrorChange;
import org.lrdm.effectors.TopologyChange;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Eine spezialisierte {@link TreeTopologyStrategy}, die Mirrors in einem tiefen-beschränkten Baum
 * mit einer maximalen Tiefe verbindet. Diese Strategie erweitert die standard Baum-Topologie
 * durch eine konfigurierbare Tiefenbeschränkung.
 * <p>
 * **Tiefen-beschränkter Baum-Eigenschaften**:
 * - Baumstruktur mit hierarchischer Organisation (keine Zyklen)
 * - Maximale Tiefe ist konfigurierbar (Standard: 3)
 * - Bevorzugt Depth-First-Wachstum innerhalb der Tiefenbeschränkung
 * - Verwendet {@link DepthLimitedTreeMirrorNode} für tiefenspezifische Funktionalität
 * - Automatische Tiefenvalidierung und -optimierung
 * <p>
 * **Tiefenmanagement**:
 * - Root-Knoten hat Tiefe 0
 * - Jede Ebene erhöht die Tiefe um 1
 * - Knoten auf maximaler Tiefe können keine Kinder haben
 * - Neue Knoten werden bevorzugt an tieferen, aber nicht maximalen Positionen eingefügt
 * <p>
 * **Vererbung von TreeTopologyStrategy**:
 * - Nutzt alle Tree-Funktionalitäten (n-1 Links für n Knoten)
 * - Erweitert um Tiefenbeschränkung und Depth-First-Strategien
 * - Überschreibt Struktur-Erstellung und -Erweiterung für Tiefenmanagement
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class DepthLimitTreeTopologyStrategy extends TreeTopologyStrategy {

    // ===== TIEFENBESCHRÄNKUNGS-KONFIGURATION =====

    /** Maximale Tiefe des Baums (Standard: 3) */
    private int maxDepth = 3;

    /** Ob automatische Tiefenoptimierung aktiviert ist */
    private boolean enableDepthOptimization = true;

    /** Bevorzugte Strategie für Knoteneinfügung */
    private DepthInsertionStrategy insertionStrategy = DepthInsertionStrategy.DEPTH_FIRST;

    // ===== KONSTRUKTOREN =====

    /**
     * Standard-Konstruktor mit Standardwerten.
     * maxDepth=3, enableDepthOptimization=true, insertionStrategy=DEPTH_FIRST
     */
    public DepthLimitTreeTopologyStrategy() {
        super();
    }

    /**
     * Konstruktor mit konfigurierbarer maximaler Tiefe.
     *
     * @param maxDepth Maximale Tiefe des Baums (mindestens 1)
     */
    public DepthLimitTreeTopologyStrategy(int maxDepth) {
        super();
        this.maxDepth = Math.max(1, maxDepth);
    }

    /**
     * Konstruktor mit maximaler Tiefe und Optimierungseinstellung.
     *
     * @param maxDepth Maximale Tiefe des Baums (mindestens 1)
     * @param enableDepthOptimization Ob automatische Tiefenoptimierung aktiviert ist
     */
    public DepthLimitTreeTopologyStrategy(int maxDepth, boolean enableDepthOptimization) {
        super();
        this.maxDepth = Math.max(1, maxDepth);
        this.enableDepthOptimization = enableDepthOptimization;
    }

    /**
     * Vollständiger Konstruktor mit allen Konfigurationsoptionen.
     *
     * @param maxDepth Maximale Tiefe des Baums (mindestens 1)
     * @param enableDepthOptimization Ob automatische Tiefenoptimierung aktiviert ist
     * @param insertionStrategy Strategie für Knoteneinfügung
     */
    public DepthLimitTreeTopologyStrategy(int maxDepth, boolean enableDepthOptimization,
                                          DepthInsertionStrategy insertionStrategy) {
        super();
        this.maxDepth = Math.max(1, maxDepth);
        this.enableDepthOptimization = enableDepthOptimization;
        this.insertionStrategy = insertionStrategy != null ? insertionStrategy : DepthInsertionStrategy.DEPTH_FIRST;
    }

    // ===== ÜBERSCHREIBUNG DER TREE-TOPOLOGY-METHODEN =====

    /**
     * Erstellt einen tiefen-beschränkten Baum mit der angegebenen Anzahl von Knoten.
     * Überschreibt TreeTopologyStrategy für Tiefenbeschränkung.
     * Verwendet DepthLimitedTreeMirrorNode anstatt TreeMirrorNode.
     *
     * @param totalNodes Anzahl der zu erstellenden Knoten
     * @param simTime Aktuelle Simulationszeit für Link-Erstellung
     * @param props Properties der Simulation
     * @return Die Root-Node der erstellten tiefen-beschränkten Struktur
     */
    @Override
    protected MirrorNode buildStructure(int totalNodes, int simTime, Properties props) {
        if (totalNodes <= 0 || network == null) {
            return null;
        }

        List<DepthLimitedTreeMirrorNode> nodes = new ArrayList<>();

        // 1. Erstelle DepthLimitedTreeMirrorNodes mit Mirror-Zuordnung
        for (int i = 0; i < totalNodes && hasNextMirror(); i++) {
            Mirror mirror = getNextMirror();
            if (mirror != null) {
                DepthLimitedTreeMirrorNode node = new DepthLimitedTreeMirrorNode(mirror.getID(), mirror, maxDepth);
                nodes.add(node);
                addToStructureNodes(node); // Registriere bei BuildAsSubstructure
            }
        }

        if (nodes.isEmpty()) {
            return null;
        }

        // 2. Setze den ersten Knoten als Root und Head
        DepthLimitedTreeMirrorNode root = nodes.get(0);
        root.setHead(true);
        setCurrentStructureRoot(root);

        // 3. Baue tiefen-beschränkte Baum-Struktur
        Set<Link> createdLinks = buildDepthLimitedTreeStructureWithLinks(nodes, simTime, props);

        // 4. Registriere alle Links im Network
        network.getLinks().addAll(createdLinks);

        return root;
    }

    /**
     * Fügt neue Knoten zur bestehenden tiefen-beschränkten Baum-Struktur hinzu.
     * Überschreibt TreeTopologyStrategy für tiefenspezifische Einfügung.
     * Verwendet die konfigurierte DepthInsertionStrategy.
     *
     * @param nodesToAdd Anzahl der hinzuzufügenden Knoten
     * @return Tatsächliche Anzahl der hinzugefügten Knoten
     */
    @Override
    protected int addNodesToStructure(int nodesToAdd) {
        if (nodesToAdd <= 0 || getCurrentStructureRoot() == null) {
            return 0;
        }

        DepthLimitedTreeMirrorNode root = getDepthLimitedRoot();
        if (root == null) {
            return 0;
        }

        List<DepthLimitedTreeMirrorNode> newNodes = new ArrayList<>();
        int actuallyAdded = 0;

        // Erstelle neue DepthLimitedTreeMirrorNodes
        for (int i = 0; i < nodesToAdd && hasNextMirror(); i++) {
            Mirror mirror = getNextMirror();
            if (mirror != null) {
                DepthLimitedTreeMirrorNode newNode = new DepthLimitedTreeMirrorNode(mirror.getID(), mirror, maxDepth);
                newNodes.add(newNode);
                addToStructureNodes(newNode);
                actuallyAdded++;
            }
        }

        // Füge neue Knoten entsprechend der Einfügungsstrategie hinzu
        for (DepthLimitedTreeMirrorNode newNode : newNodes) {
            DepthLimitedTreeMirrorNode insertionPoint = findOptimalInsertionPoint(root);
            if (insertionPoint != null && insertionPoint.canAddChildren()) {
                // StructureNode-Verbindung
                insertionPoint.addChild(newNode);
                newNode.setParent(insertionPoint);
            }
        }

        return actuallyAdded;
    }

    /**
     * Entfernt Knoten aus der tiefen-beschränkten Baum-Struktur.
     * Überschreibt TreeTopologyStrategy für tiefenspezifische Entfernung.
     * Bevorzugt Knoten auf maximaler Tiefe für die Entfernung.
     *
     * @param nodesToRemove Anzahl der zu entfernenden Knoten
     * @return Anzahl der tatsächlich entfernten Knoten
     */
    @Override
    protected int removeNodesFromStructure(int nodesToRemove) {
        if (nodesToRemove <= 0 || getAllStructureNodes().isEmpty()) {
            return 0;
        }

        DepthLimitedTreeMirrorNode root = getDepthLimitedRoot();
        if (root == null) {
            return 0;
        }

        // Sammle Kandidaten für Entfernung (bevorzugt tiefere Blätter)
        List<DepthLimitedTreeMirrorNode> removalCandidates = findRemovalCandidates(root);

        // Begrenze auf verfügbare Anzahl
        int actualRemovalCount = Math.min(nodesToRemove, removalCandidates.size());

        if (actualRemovalCount == 0) {
            return 0;
        }

        // Entferne die ersten N Kandidaten
        List<MirrorNode> nodesToRemoveList = new ArrayList<>();
        for (int i = 0; i < actualRemovalCount; i++) {
            DepthLimitedTreeMirrorNode nodeToRemove = removalCandidates.get(i);

            // Entferne Parent-Child-Beziehungen
            removeNodeFromDepthLimitedTreeStructure(nodeToRemove);

            nodesToRemoveList.add(nodeToRemove);
        }

        // Bereinige die StructureNode-Verwaltung
        cleanupStructureNodes(nodesToRemoveList);

        return actualRemovalCount;
    }

    // ===== TIEFENBESCHRÄNKUNGS-SPEZIFISCHE METHODEN =====

    /**
     * Erstellt die tiefen-beschränkte Baum-Struktur mit echten Mirror-Links.
     * Respektiert die maximale Tiefe beim Strukturaufbau.
     *
     * @param nodes Liste der DepthLimitedTreeMirrorNodes
     * @param simTime Aktuelle Simulationszeit für Link-Erstellung
     * @param props Properties der Simulation
     * @return Set aller erstellten Links
     */
    private Set<Link> buildDepthLimitedTreeStructureWithLinks(List<DepthLimitedTreeMirrorNode> nodes,
                                                              int simTime, Properties props) {
        Set<Link> createdLinks = new HashSet<>();

        if (nodes.size() <= 1) {
            return createdLinks;
        }

        DepthLimitedTreeMirrorNode root = nodes.get(0);
        Queue<DepthLimitedTreeMirrorNode> parentsQueue = new LinkedList<>();
        parentsQueue.offer(root);

        int nodeIndex = 1;
        int maxChildrenPerParent = calculateOptimalChildrenPerParent(nodes.size());

        while (!parentsQueue.isEmpty() && nodeIndex < nodes.size()) {
            DepthLimitedTreeMirrorNode parent = parentsQueue.poll();

            // Prüfe, ob Parent noch Kinder haben kann (Tiefenbeschränkung)
            if (!parent.canAddChildren()) {
                continue;
            }

            Mirror parentMirror = parent.getMirror();

            // Füge Kinder hinzu, respektiere Tiefenbeschränkung
            for (int i = 0; i < maxChildrenPerParent && nodeIndex < nodes.size(); i++) {
                DepthLimitedTreeMirrorNode child = nodes.get(nodeIndex);

                // Prüfe, ob das Kind innerhalb der Tiefenbeschränkung liegt
                if (parentMirror != null && child.getMirror() != null) {
                    // Erstelle echten Mirror-Link
                    Link link = createDepthLimitedTreeMirrorLink(parent, child, simTime, props);
                    if (link != null) {
                        createdLinks.add(link);
                    }

                    // Füge StructureNode-Verbindungen hinzu
                    parent.addChild(child);
                    child.setParent(parent);

                    // Füge zur nächsten Ebene hinzu, falls Tiefe erlaubt
                    if (child.canAddChildren()) {
                        parentsQueue.offer(child);
                    }
                }

                nodeIndex++;
            }
        }

        return createdLinks;
    }

    /**
     * Findet den optimalen Einfügepunkt für neue Knoten basierend auf der Tiefenstrategie.
     *
     * @param root Root-Node der Struktur
     * @return Optimaler Einfügepunkt oder null, wenn keiner verfügbar
     */
    private DepthLimitedTreeMirrorNode findOptimalInsertionPoint(DepthLimitedTreeMirrorNode root) {
        if (root == null) {
            return null;
        }

        switch (insertionStrategy) {
            case DEPTH_FIRST:
                return root.findBestInsertionPoint();
            case BREADTH_FIRST:
                return findBreadthFirstInsertionPoint(root);
            case BALANCED:
                return findBalancedInsertionPoint(root);
            default:
                return root.findBestInsertionPoint();
        }
    }

    /**
     * Findet Einfügepunkt mit Breadth-First-Strategie.
     *
     * @param root Root-Node der Struktur
     * @return Einfügepunkt oder null
     */
    private DepthLimitedTreeMirrorNode findBreadthFirstInsertionPoint(DepthLimitedTreeMirrorNode root) {
        Queue<DepthLimitedTreeMirrorNode> queue = new LinkedList<>();
        queue.offer(root);

        while (!queue.isEmpty()) {
            DepthLimitedTreeMirrorNode current = queue.poll();

            if (current.canAddChildren()) {
                return current;
            }

            // Füge alle Kinder zur Queue hinzu
            for (StructureNode child : current.getChildren()) {
                if (child instanceof DepthLimitedTreeMirrorNode depthChild) {
                    queue.offer(depthChild);
                }
            }
        }

        return null;
    }

    /**
     * Findet Einfügepunkt mit Balance-Strategie (gleichmäßige Verteilung).
     *
     * @param root Root-Node der Struktur
     * @return Einfügepunkt oder null
     */
    private DepthLimitedTreeMirrorNode findBalancedInsertionPoint(DepthLimitedTreeMirrorNode root) {
        List<DepthLimitedTreeMirrorNode> candidates = getAllDepthLimitedNodes()
                .stream()
                .filter(DepthLimitedTreeMirrorNode::canAddChildren)
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            return null;
        }

        // Finde den Knoten mit den wenigsten Kindern
        return candidates.stream()
                .min(Comparator.comparingInt(node -> node.getChildren().size()))
                .orElse(null);
    }

    /**
     * Findet Kandidaten für die Entfernung aus der tiefen-beschränkten Struktur.
     * Bevorzugt tiefere Blätter.
     *
     * @param root Root-Node der Struktur
     * @return Liste der Entfernungskandidaten, sortiert nach Priorität
     */
    private List<DepthLimitedTreeMirrorNode> findRemovalCandidates(DepthLimitedTreeMirrorNode root) {
        List<DepthLimitedTreeMirrorNode> candidates = new ArrayList<>();

        for (MirrorNode node : getAllStructureNodes()) {
            if (node != root && node instanceof DepthLimitedTreeMirrorNode depthNode) {
                // Bevorzuge Blätter (Knoten ohne Kinder)
                if (depthNode.getChildren().isEmpty()) {
                    candidates.add(depthNode);
                }
            }
        }

        // Sortiere: Tiefere Knoten zuerst, dann nach ID
        candidates.sort((node1, node2) -> {
            int depthCompare = Integer.compare(node2.getDepthInTree(), node1.getDepthInTree());
            return depthCompare != 0 ? depthCompare : Integer.compare(node2.getId(), node1.getId());
        });

        return candidates;
    }

    /**
     * Entfernt einen Knoten aus der tiefen-beschränkten Baum-Struktur.
     *
     * @param nodeToRemove Der zu entfernende DepthLimitedTreeMirrorNode
     */
    private void removeNodeFromDepthLimitedTreeStructure(DepthLimitedTreeMirrorNode nodeToRemove) {
        // Entferne aus Parent
        StructureNode parent = nodeToRemove.getParent();
        if (parent != null) {
            parent.removeChild(nodeToRemove);
        }

        // Entferne alle Children (sollten keine sein bei Blatt-Knoten)
        List<StructureNode> children = new ArrayList<>(nodeToRemove.getChildren());
        for (StructureNode child : children) {
            nodeToRemove.removeChild(child);
            if (child instanceof DepthLimitedTreeMirrorNode depthChild) {
                depthChild.setParent(null);
            }
        }

        // Entferne die Parent-Referenz
        nodeToRemove.setParent(null);
    }

    /**
     * Erstellt einen Mirror-Link zwischen zwei tiefen-beschränkten Baum-Knoten.
     *
     * @param parent Parent-Node
     * @param child Child-Node
     * @param simTime Aktuelle Simulationszeit
     * @param props Properties der Simulation
     * @return Erstellter Link oder null
     */
    private Link createDepthLimitedTreeMirrorLink(DepthLimitedTreeMirrorNode parent,
                                                  DepthLimitedTreeMirrorNode child,
                                                  int simTime, Properties props) {
        Mirror parentMirror = parent.getMirror();
        Mirror childMirror = child.getMirror();

        if (parentMirror == null || childMirror == null) {
            return null;
        }

        // Prüfe, ob bereits eine Verbindung besteht
        if (parentMirror.isAlreadyConnected(childMirror)) {
            return null;
        }

        // Erstelle neuen Link
        Link link = new Link(
                idGenerator != null ? idGenerator.getNextID() : (int)(Math.random() * 100000),
                parentMirror,
                childMirror,
                simTime,
                props
        );

        // Füge Link zu beiden Mirrors hinzu
        parentMirror.addLink(link);
        childMirror.addLink(link);

        return link;
    }

    /**
     * Berechnet die optimale Anzahl von Kindern pro Parent basierend auf der Gesamtanzahl
     * der Knoten und der maximalen Tiefe.
     *
     * @param totalNodes Gesamtanzahl der Knoten
     * @return Optimale Anzahl von Kindern pro Parent
     */
    private int calculateOptimalChildrenPerParent(int totalNodes) {
        if (totalNodes <= 1 || maxDepth <= 1) {
            return 1;
        }

        // Berechne die optimale Verzweigung für die gegebene Tiefe
        double optimalBranching = Math.pow(totalNodes, 1.0 / maxDepth);
        return Math.max(1, (int) Math.ceil(optimalBranching));
    }

    // ===== TIEFENBESCHRÄNKUNGS-ANALYSE =====

    /**
     * Prüft, ob die Tiefenbeschränkung in der gesamten Struktur eingehalten wird.
     *
     * @return true, wenn alle Knoten die Tiefenbeschränkung respektieren
     */
    public boolean validateDepthConstraints() {
        DepthLimitedTreeMirrorNode root = getDepthLimitedRoot();
        if (root == null) {
            return true;
        }

        return root.validateDepthConstraints();
    }

    /**
     * Berechnet die Tiefeneffizienz des Baums.
     *
     * @return Effizienz zwischen 0.0 und 1.0
     */
    public double calculateDepthEfficiency() {
        DepthLimitedTreeMirrorNode root = getDepthLimitedRoot();
        if (root == null) {
            return 0.0;
        }

        return root.calculateDepthUtilization();
    }

    /**
     * Gibt Statistiken über die Tiefenverteilung zurück.
     *
     * @return Map von Tiefe zu Anzahl der Knoten auf dieser Tiefe
     */
    public Map<Integer, Integer> getDepthStatistics() {
        DepthLimitedTreeMirrorNode root = getDepthLimitedRoot();
        if (root == null) {
            return new HashMap<>();
        }

        Map<Integer, List<DepthLimitedTreeMirrorNode>> nodesByDepth = root.getNodesByDepthDFS();
        Map<Integer, Integer> statistics = new HashMap<>();

        for (Map.Entry<Integer, List<DepthLimitedTreeMirrorNode>> entry : nodesByDepth.entrySet()) {
            statistics.put(entry.getKey(), entry.getValue().size());
        }

        return statistics;
    }

    // ===== TYPSICHERE HILFSMETHODEN =====

    /**
     * Gibt den Root-Node als DepthLimitedTreeMirrorNode zurück.
     *
     * @return Root-Node oder null
     */
    private DepthLimitedTreeMirrorNode getDepthLimitedRoot() {
        MirrorNode root = getCurrentStructureRoot();
        return (root instanceof DepthLimitedTreeMirrorNode) ? (DepthLimitedTreeMirrorNode) root : null;
    }

    /**
     * Gibt alle Knoten als DepthLimitedTreeMirrorNode-Liste zurück.
     *
     * @return Liste aller DepthLimitedTreeMirrorNodes
     */
    private List<DepthLimitedTreeMirrorNode> getAllDepthLimitedNodes() {
        return getAllStructureNodes().stream()
                .filter(DepthLimitedTreeMirrorNode.class::isInstance)
                .map(DepthLimitedTreeMirrorNode.class::cast)
                .collect(Collectors.toList());
    }

    // ===== TOPOLOGY STRATEGY ÜBERSCHREIBUNGEN =====

    /**
     * Berechnet die erwartete Anzahl der Links für tiefen-beschränkte Bäume.
     * Identisch mit TreeTopologyStrategy: n-1 Links für n Knoten.
     *
     * @param a Die Action, deren Auswirkungen berechnet werden sollen
     * @return Anzahl der erwarteten Links nach Ausführung der Action
     */
    @Override
    public int getPredictedNumTargetLinks(Action a) {
        if (a instanceof MirrorChange) {
            MirrorChange mc = (MirrorChange) a;
            int predictedMirrors = mc.getNewMirrors();
            return Math.max(0, predictedMirrors - 1);
        }

        if (a instanceof TopologyChange) {
            TopologyChange tc = (TopologyChange) a;
            return getNumTargetLinks(tc.getNetwork());
        }

        // Für andere Action-Typen: Behalte aktuelle Anzahl
        return network != null ? getNumTargetLinks(network) : 0;
    }

    // ===== GETTER UND SETTER =====

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = Math.max(1, maxDepth);
    }

    public boolean isDepthOptimizationEnabled() {
        return enableDepthOptimization;
    }

    public void setDepthOptimizationEnabled(boolean enableDepthOptimization) {
        this.enableDepthOptimization = enableDepthOptimization;
    }

    public DepthInsertionStrategy getInsertionStrategy() {
        return insertionStrategy;
    }

    public void setInsertionStrategy(DepthInsertionStrategy insertionStrategy) {
        this.insertionStrategy = insertionStrategy != null ? insertionStrategy : DepthInsertionStrategy.DEPTH_FIRST;
    }

    // ===== ENUM FÜR EINFÜGUNGSSTRATEGIEN =====

    /**
     * Enum für verschiedene Strategien der Knoteneinfügung in tiefen-beschränkten Bäumen.
     */
    public enum DepthInsertionStrategy {
        /** Bevorzugt tiefere Knoten für Einfügung (Depth-First) */
        DEPTH_FIRST,
        /** Bevorzugt breitere Verteilung (Breadth-First) */
        BREADTH_FIRST,
        /** Versucht einen ausgeglichenen Baum zu erstellen */
        BALANCED
    }
}