
package org.lrdm.topologies.strategies;

import org.lrdm.topologies.node.DepthLimitedTreeMirrorNode;
import org.lrdm.topologies.node.MirrorNode;
import org.lrdm.topologies.node.StructureNode;
import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
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
 * - Jeder Knoten kann beliebig viele Kinder haben (keine Verzweigungsbeschränkung)
 * - Bevorzugt gleichmäßige Verteilung mit Depth-First-Wachstum innerhalb der Tiefenbeschränkung
 * - Verwendet {@link DepthLimitedTreeMirrorNode} für tiefenspezifische Funktionalität
 * <p>
 * **Tiefenmanagement**:
 * - Root-Knoten hat Tiefe 0
 * - Jede Ebene erhöht die Tiefe um 1
 * - Knoten auf maximaler Tiefe können keine Kinder haben
 * - Neue Knoten werden bevorzugt an tieferen, aber nicht maximalen Positionen eingefügt
 * - Gleichmäßige Verteilung: Jeder Knoten kann potentiell unendlich viele Kinder haben
 * <p>
 * **Planungsebene vs. Ausführungsebene**:
 * - Planungsebene: `removeNodesFromStructure()` - plant strukturelle Änderungen ohne Zeitbezug
 * - Ausführungsebene: `handleRemoveMirrors()` - führt Mirror-Shutdown innerhalb der Planungsgrenzen aus
 * - Automatisches Mitwachsen: MirrorNode-Ebene passt sich an StructureNode-Planung an
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

    /** Minimale Anzahl an Knoten für einen funktionsfähigen tiefen-beschränkten Baum */
    private int minTreeSize = 1;

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

    // ===== ÜBERSCHREIBUNG DER BUILD-AS-SUBSTRUCTURE-METHODEN =====

    /**
     * **PLANUNGSEBENE**: Erstellt die initiale tiefen-beschränkte Baum-Struktur.
     * Überschreibt BuildAsSubstructure für Tiefenbeschränkung.
     * Verwendet DepthLimitedTreeMirrorNode anstatt TreeMirrorNode.
     *
     * @param totalNodes Anzahl der zu erstellenden Knoten
     * @param props      Properties der Simulation
     * @return Die Root-Node der erstellten tiefen-beschränkten Struktur
     */
    @Override
    protected MirrorNode buildStructure(int totalNodes, Properties props) {
        if (totalNodes < minTreeSize || !hasNextMirror()) {
            return null;
        }

        // 1. Erstelle Root-Node
        Mirror rootMirror = getNextMirror();
        DepthLimitedTreeMirrorNode root = new DepthLimitedTreeMirrorNode(rootMirror.getID(), rootMirror, maxDepth);
        root.setHead(true);
        setCurrentStructureRoot(root);
        addToStructureNodes(root);

        // 2. Erstelle restliche Knoten
        List<DepthLimitedTreeMirrorNode> remainingNodes = new ArrayList<>();
        for (int i = 1; i < totalNodes; i++) {
            if (!hasNextMirror()) break;

            Mirror mirror = getNextMirror();
            DepthLimitedTreeMirrorNode node = new DepthLimitedTreeMirrorNode(mirror.getID(), mirror, maxDepth);
            remainingNodes.add(node);
            addToStructureNodes(node);
        }

        if (remainingNodes.isEmpty()) {
            return root; // Nur Root-Node
        }

        // 3. Baue tiefen-beschränkte Baum-Struktur
        buildDepthLimitedTreeStructureWithLinks(root, remainingNodes, simTime, props);

        return root;
    }

    /**
     * **PLANUNGSEBENE**: Fügt neue Knoten zur bestehenden tiefen-beschränkten Baum-Struktur hinzu.
     * Überschreibt BuildAsSubstructure für tiefenspezifische Einfügung.
     * Verwendet die konfigurierte DepthInsertionStrategy für optimale Platzierung.
     *
     * @param nodesToAdd Anzahl der hinzuzufügenden Knoten
     * @return Tatsächliche Anzahl der hinzugefügten Knoten
     */
    @Override
    protected int addNodesToStructure(int nodesToAdd) {
        if (nodesToAdd <= 0) return 0;

        DepthLimitedTreeMirrorNode root = getDepthLimitedRoot();
        if (root == null) return 0;

        int actuallyAdded = 0;

        // Füge neue Knoten entsprechend der Einfügungsstrategie hinzu
        for (int i = 0; i < nodesToAdd; i++) {
            // Erstelle neuen Knoten
            DepthLimitedTreeMirrorNode newNode = createDepthLimitedTreeNode(0, new Properties());
            if (newNode == null) break;

            // Finde optimalen Einfügepunkt
            DepthLimitedTreeMirrorNode insertionPoint = findOptimalInsertionPoint(root);
            if (insertionPoint != null && insertionPoint.canAddChildren()) {
                // Verbinde neuen Knoten mit Einfügepunkt
                attachNodeToParent(insertionPoint, newNode, 0, new Properties());
                actuallyAdded++;
            }
        }

        return actuallyAdded;
    }

    /**
     * **PLANUNGSEBENE**: Entfernt Knoten aus der tiefen-beschränkten Baum-Struktur.
     * Überschreibt BuildAsSubstructure für tiefenspezifische Entfernung.
     * Bevorzugt Knoten auf maximaler Tiefe für die Entfernung.
     *
     * @param nodesToRemove Anzahl der zu entfernenden Knoten
     * @return Anzahl der tatsächlich entfernten Knoten
     */
    @Override
    protected int removeNodesFromStructure(int nodesToRemove) {
        if (nodesToRemove <= 0) return 0;

        List<DepthLimitedTreeMirrorNode> depthLimitedNodes = getAllDepthLimitedNodes();
        if (depthLimitedNodes.size() - nodesToRemove < minTreeSize) {
            nodesToRemove = depthLimitedNodes.size() - minTreeSize;
        }
        if (nodesToRemove <= 0) return 0;

        int actuallyRemoved = 0;
        List<DepthLimitedTreeMirrorNode> removalCandidates = findRemovalCandidates();

        // Entferne die ersten N Kandidaten
        for (int i = 0; i < nodesToRemove && i < removalCandidates.size(); i++) {
            DepthLimitedTreeMirrorNode nodeToRemove = removalCandidates.get(i);
            if (nodeToRemove != null) {
                removeNodeFromDepthLimitedTreeStructuralPlanning(nodeToRemove);
                actuallyRemoved++;
            }
        }

        return actuallyRemoved;
    }

    /**
     * **AUSFÜHRUNGSEBENE**: Überschreibt die Mirror-Entfernung für tiefen-beschränkte Baum-Erhaltung.
     * Führt Mirror-Shutdown innerhalb der strukturellen Tiefenplanungsgrenzen aus.
     * Arbeitet komplementär zu removeNodesFromStructure.
     *
     * @param n Das Netzwerk
     * @param removeMirrors Anzahl der zu entfernenden Mirrors
     * @param props Properties der Simulation
     * @param simTime Aktuelle Simulationszeit
     * @return Set der entfernten Mirrors
     */
    @Override
    public Set<Mirror> handleRemoveMirrors(Network n, int removeMirrors, Properties props, int simTime) {
        if (removeMirrors <= 0) return new HashSet<>();

        DepthLimitedTreeMirrorNode root = getDepthLimitedRoot();
        if (root == null) {
            // Fallback: Verwende die TreeTopologyStrategy-Implementierung
            return super.handleRemoveMirrors(n, removeMirrors, props, simTime);
        }

        Set<Mirror> cleanedMirrors = new HashSet<>();

        List<DepthLimitedTreeMirrorNode> depthLimitedNodes = getAllDepthLimitedNodes();
        if (depthLimitedNodes.size() - removeMirrors < minTreeSize) {
            removeMirrors = depthLimitedNodes.size() - minTreeSize;
        }
        if (removeMirrors <= 0) return cleanedMirrors;

        List<DepthLimitedTreeMirrorNode> removalCandidates = findRemovalCandidates();

        // Entferne Knoten (bevorzugt tiefere Blätter)
        for (int i = 0; i < removeMirrors && i < removalCandidates.size(); i++) {
            DepthLimitedTreeMirrorNode nodeToRemove = removalCandidates.get(i);
            if (nodeToRemove == null || nodeToRemove == root) continue;

            Mirror mirrorToRemove = nodeToRemove.getMirror();
            if (mirrorToRemove != null) {
                // Entferne alle Links (aber schalte Mirror NICHT aus)
                Set<Link> linksToRemove = new HashSet<>(mirrorToRemove.getLinks());
                for (Link link : linksToRemove) {
                    link.getSource().removeLink(link);
                    link.getTarget().removeLink(link);
                    n.getLinks().remove(link);
                }

                // Entferne Mirror vom Netzwerk
                n.getMirrors().remove(mirrorToRemove);
                cleanedMirrors.add(mirrorToRemove);
            }

            // Entferne Knoten aus der Struktur
            removeNodeFromStructure(nodeToRemove);
        }

        return cleanedMirrors;
    }

    // ===== TOPOLOGY STRATEGY INTERFACE IMPLEMENTATION =====

    /**
     * Initialisiert das Netzwerk mit tiefen-beschränkter Baum-Topologie.
     * Überschreibt TreeTopologyStrategy für Tiefenbeschränkung.
     *
     * @param n Das Netzwerk
     * @param props Properties der Simulation
     * @return Set aller erstellten Links
     */
    @Override
    public Set<Link> initNetwork(Network n, Properties props) {
        // Initialisiere das Netzwerk mit tiefen-beschränkter Baum-Topologie
        this.network = n;
        this.mirrorIterator = new ArrayList<>(n.getMirrors()).iterator();

        if (n.getMirrors().isEmpty()) {
            return new HashSet<>();
        }

        // Baue die Struktur mit allen verfügbaren Mirrors auf
        MirrorNode root = buildStructure(n.getMirrors().size(), props);

        if (root == null) {
            return new HashSet<>();
        }

        // Erstelle die tatsächlichen Links
        return buildAndConnectLinks(root, props, 0);
    }

    /**
     * Startet das Netzwerk komplett neu mit der tiefen-beschränkten Baum-Topologie.
     * Überschreibt TreeTopologyStrategy für Tiefenbeschränkung.
     *
     * @param n       Das Netzwerk
     * @param props   Properties der Simulation
     * @param simTime Aktuelle Simulationszeit
     * @return
     */
    @Override
    public Set<Link> restartNetwork(Network n, Properties props, int simTime) {
        // 1. Sammle alle Links, die zu unseren MirrorNodes gehören
        Set<Link> linksToRemove = new HashSet<>();
        for (MirrorNode node : getAllStructureNodes()) {
            Mirror mirror = node.getMirror();
            if (mirror != null) {
                for (Link link : mirror.getLinks()) {
                    if (n.getLinks().contains(link)) {
                        linksToRemove.add(link);
                    }
                }
            }
        }

        // 2. Links aus Network.links entfernen
        n.getLinks().removeAll(linksToRemove);

        // 3. TopologyStrategy macht den Rest: Mirror.links.clear() für ALLE Mirrors
        super.restartNetwork(n, props, simTime);

        // 4. StructureNode-Struktur zurücksetzen
        resetInternalStateStructureOnly();

        // 5. Neu aufbauen
        this.network = n;
        this.mirrorIterator = new ArrayList<>(n.getMirrors()).iterator();

        if (!n.getMirrors().isEmpty()) {
            MirrorNode root = buildStructure(n.getMirrors().size(), props);
            if (root != null) {
                Set<Link> newLinks = buildAndConnectLinks(root, props, 0);
                n.getLinks().addAll(newLinks);
            }
        }
    }

    /**
     * Fügt die angeforderte Anzahl von Mirrors zum Netzwerk hinzu und verbindet sie entsprechend.
     * Überschreibt TreeTopologyStrategy für tiefen-beschränkte Einfügung.
     *
     * @param n Das Netzwerk
     * @param newMirrors Anzahl der hinzuzufügenden Mirrors
     * @param props Properties der Simulation
     * @param simTime Aktuelle Simulationszeit
     */
    @Override
    public void handleAddNewMirrors(Network n, int newMirrors, Properties props, int simTime) {
        this.network = n;

        // Verwende das offizielle Interface von TopologyStrategy
        List<Mirror> addedMirrors = createMirrors(newMirrors, simTime, props);
        n.getMirrors().addAll(addedMirrors);

        // Setze Iterator für die neuen Mirrors
        this.mirrorIterator = addedMirrors.iterator();

        // Füge die neuen Knoten zur Struktur hinzu
        int actuallyAdded = addNodesToStructure(newMirrors);

        if (actuallyAdded > 0 && getCurrentStructureRoot() != null) {
            // Baue nur die neuen Links auf
            Set<Link> newLinks = buildAndConnectLinks(getCurrentStructureRoot(), props, 0);
            n.getLinks().addAll(newLinks);
        }
    }

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

    /**
     * Gibt eine String-Repräsentation der Strategie zurück.
     *
     * @return String-Repräsentation
     */
    @Override
    public String toString() {
        return String.format("DepthLimitTreeTopologyStrategy[maxDepth=%d, strategy=%s, optimization=%s]",
                maxDepth, insertionStrategy, enableDepthOptimization);
    }

    // ===== TIEFENBESCHRÄNKUNGS-SPEZIFISCHE METHODEN =====

    /**
     * Baut die tiefen-beschränkte Baum-Struktur mit sowohl StructureNode-Verbindungen als auch echten Mirror-Links auf.
     * Respektiert die maximale Tiefe beim Strukturaufbau.
     *
     * @param root Root-Node der Struktur
     * @param remainingNodes Liste der noch zu verbindenden Knoten
     * @param simTime Aktuelle Simulationszeit für Link-Erstellung
     * @param props Properties der Simulation
     */
    private void buildDepthLimitedTreeStructureWithLinks(DepthLimitedTreeMirrorNode root,
                                                         List<DepthLimitedTreeMirrorNode> remainingNodes,
                                                         int simTime, Properties props) {
        if (root == null || remainingNodes.isEmpty()) return;

        // Verwende eine Queue für gleichmäßige Verteilung (Breadth-First-ähnlich)
        Queue<DepthLimitedTreeMirrorNode> parentQueue = new LinkedList<>();
        parentQueue.offer(root);

        int nodeIndex = 0;
        int optimalChildrenPerParent = calculateOptimalChildrenPerParent(remainingNodes.size() + 1);

        while (!parentQueue.isEmpty() && nodeIndex < remainingNodes.size()) {
            DepthLimitedTreeMirrorNode parent = parentQueue.poll();

            // Prüfe, ob Parent noch Kinder haben kann (Tiefenbeschränkung)
            if (!parent.canAddChildren()) {
                continue;
            }

            // Füge Kinder hinzu, respektiere Tiefenbeschränkung
            for (int i = 0; i < optimalChildrenPerParent && nodeIndex < remainingNodes.size(); i++) {
                DepthLimitedTreeMirrorNode child = remainingNodes.get(nodeIndex);

                // Verbinde Kind mit Parent
                attachNodeToParent(parent, child, simTime, props);

                // Füge zur nächsten Ebene hinzu, falls Tiefe erlaubt
                if (child.canAddChildren()) {
                    parentQueue.offer(child);
                }

                nodeIndex++;
            }
        }
    }

    /**
     * Erstellt einen neuen tiefen-beschränkten Baum-Knoten mit struktureller Planung.
     * Verwendet den mirrorIterator für die Mirror-Zuweisung.
     *
     * @param simTime Aktuelle Simulationszeit (für zukünftige Verwendung)
     * @param props Properties der Simulation
     * @return Neuer DepthLimitedTreeMirrorNode oder null, wenn keine Mirrors verfügbar
     */
    private DepthLimitedTreeMirrorNode createDepthLimitedTreeNode(int simTime, Properties props) {
        if (!hasNextMirror()) return null;

        Mirror mirror = getNextMirror();
        DepthLimitedTreeMirrorNode node = new DepthLimitedTreeMirrorNode(mirror.getID(), mirror, maxDepth);
        addToStructureNodes(node);

        return node;
    }

    /**
     * Verbindet einen neuen Knoten mit einem Parent-Knoten.
     * Erstellt sowohl StructureNode-Verbindungen als auch Mirror-Links.
     *
     * @param parent Der Parent-Knoten
     * @param child Der Child-Knoten
     * @param simTime Aktuelle Simulationszeit für Link-Erstellung
     * @param props Properties der Simulation
     */
    private void attachNodeToParent(DepthLimitedTreeMirrorNode parent, DepthLimitedTreeMirrorNode child,
                                    int simTime, Properties props) {
        if (parent == null || child == null) return;

        // Strukturplanung: Füge Child zum Parent hinzu
        parent.addChild(child);

        // Ausführungsebene: Mirror-Link
        createDepthLimitedTreeMirrorLink(parent, child, simTime, props);
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

        // Finde den Knoten mit den wenigsten Kindern (für Balance)
        return candidates.stream()
                .min(Comparator.comparingInt(node -> node.getChildren().size()))
                .orElse(null);
    }

    /**
     * Findet Kandidaten für die Entfernung aus der tiefen-beschränkten Struktur.
     * Bevorzugt tiefere Blätter für die Entfernung.
     *
     * @return Liste der Entfernungskandidaten, sortiert nach Priorität
     */
    private List<DepthLimitedTreeMirrorNode> findRemovalCandidates() {
        DepthLimitedTreeMirrorNode root = getDepthLimitedRoot();
        List<DepthLimitedTreeMirrorNode> candidates = new ArrayList<>();

        for (DepthLimitedTreeMirrorNode node : getAllDepthLimitedNodes()) {
            if (node != root) {
                // Bevorzuge Blätter (Knoten ohne Kinder)
                if (node.getChildren().isEmpty()) {
                    candidates.add(node);
                }
            }
        }

        // Sortiere: Tiefere Knoten zuerst, dann nach ID (für Determinismus)
        candidates.sort((node1, node2) -> {
            int depthCompare = Integer.compare(node2.getDepthInTree(), node1.getDepthInTree());
            return depthCompare != 0 ? depthCompare : Integer.compare(node2.getId(), node1.getId());
        });

        return candidates;
    }

    /**
     * **PLANUNGSEBENE**: Entfernt einen Knoten aus der tiefen-beschränkten Baum-Struktur-Planung.
     * Arbeitet ohne Zeitbezug - nur strukturelle Änderung.
     *
     * @param nodeToRemove Der zu entfernende Knoten
     */
    private void removeNodeFromDepthLimitedTreeStructuralPlanning(DepthLimitedTreeMirrorNode nodeToRemove) {
        if (nodeToRemove == null) return;

        // Entferne aus Parent-Child-Beziehung (setzt automatisch parent auf null)
        StructureNode parent = nodeToRemove.getParent();
        if (parent != null) {
            parent.removeChild(nodeToRemove);
        }

        // Entferne alle Kinder (für jeden wird automatisch parent auf null gesetzt)
        List<StructureNode> children = new ArrayList<>(nodeToRemove.getChildren());
        for (StructureNode child : children) {
            nodeToRemove.removeChild(child);
        }
    }

    /**
     * Entfernt einen Knoten vollständig aus der Struktur-Verwaltung (ohne Mirror-Shutdown).
     * Bereinigt alle bidirektionalen Beziehungen.
     *
     * @param nodeToRemove Der zu entfernende Knoten
     */
    private void removeNodeFromStructure(DepthLimitedTreeMirrorNode nodeToRemove) {
        // Entferne aus Parent-Child-Beziehung (setzt automatisch parent auf null)
        StructureNode parent = nodeToRemove.getParent();
        if (parent != null) {
            parent.removeChild(nodeToRemove);
        }

        // Entferne alle Kinder (für jeden wird automatisch parent auf null gesetzt)
        List<StructureNode> children = new ArrayList<>(nodeToRemove.getChildren());
        for (StructureNode child : children) {
            nodeToRemove.removeChild(child);
        }
    }

    /**
     * Erstellt einen Mirror-Link zwischen zwei tiefen-beschränkten Baum-Knoten mit Validierung.
     * Ausführungsebene: Echte Mirror-Verbindungen mit Duplikat-Prüfung.
     *
     * @param parent Parent-Node
     * @param child Child-Node
     * @param simTime Aktuelle Simulationszeit
     * @param props Properties der Simulation
     */
    private void createDepthLimitedTreeMirrorLink(DepthLimitedTreeMirrorNode parent,
                                                  DepthLimitedTreeMirrorNode child,
                                                  int simTime, Properties props) {
        Mirror parentMirror = parent.getMirror();
        Mirror childMirror = child.getMirror();

        if (parentMirror == null || childMirror == null) return;

        // Prüfe, ob bereits eine Verbindung besteht
        if (parentMirror.isAlreadyConnected(childMirror)) return;

        // Erstelle neuen Link
        Link link = new Link(
                idGenerator != null ? idGenerator.getNextID() : (int)(Math.random() * 100000),
                parentMirror,
                childMirror,
                simTime,
                props
        );

        // Verwende Mirror-Methoden anstatt direkter Collection-Modifikation
        parentMirror.addLink(link);
        childMirror.addLink(link);

        // Füge auch zu network links hinzu
        if (network != null) {
            network.getLinks().add(link);
        }
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
        // Für gleichmäßige Verteilung: totalNodes^(1/maxDepth)
        double optimalBranching = Math.pow(totalNodes, 1.0 / maxDepth);

        // Mindestens 2 Kinder pro Parent für ausgewogene Verteilung
        return Math.max(2, (int) Math.ceil(optimalBranching));
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

    public int getMinTreeSize() {
        return minTreeSize;
    }

    public void setMinTreeSize(int minTreeSize) {
        this.minTreeSize = Math.max(1, minTreeSize);
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