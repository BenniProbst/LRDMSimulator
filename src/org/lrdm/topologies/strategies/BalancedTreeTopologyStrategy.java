
package org.lrdm.topologies.strategies;

import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.topologies.node.*;

import java.util.*;

/**
 * Eine spezialisierte {@link TreeTopologyStrategy}, die Mirrors als balancierten Baum mit einer
 * einzelnen Root verknüpft. Jeder Mirror hat maximal {@link Network#getNumTargetLinksPerMirror()} Kinder.
 * Die Strategie zielt darauf ab, eine Baumstruktur zu erstellen, bei der jeder Zweig
 * die gleiche Anzahl von Vorgängern hat (soweit möglich).
 * <p>
 * Erweitert {@link TreeTopologyStrategy} um Balance-spezifische Logik:
 * - Breadth-First-Aufbau für optimale Verteilung
 * - Balance-bewusste Einfügung und Entfernung
 * - Optimierte Kinder-Verteilung pro Ebene
 * - Tiefste-Blatt-Entfernung zur Balance-Erhaltung
 * <p>
 * Verwendet {@link BalancedTreeMirrorNode} für spezialisierte Balance-Funktionen.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class BalancedTreeTopologyStrategy extends TreeTopologyStrategy {

    // ===== BALANCE-SPEZIFISCHE KONFIGURATION =====
    private int targetLinksPerNode = 2; // Standard-Verzweigungsfaktor

    // ===== KONSTRUKTOREN =====

    /**
     * Erstellt eine neue BalancedTreeTopologyStrategy mit Standard-Verzweigungsfaktor (2).
     */
    public BalancedTreeTopologyStrategy() {
        super();
    }

    /**
     * Erstellt eine neue BalancedTreeTopologyStrategy mit spezifischem Verzweigungsfaktor.
     *
     * @param targetLinksPerNode Gewünschter Verzweigungsfaktor (maximale Kinder pro Knoten)
     */
    public BalancedTreeTopologyStrategy(int targetLinksPerNode) {
        super();
        this.targetLinksPerNode = Math.max(1, targetLinksPerNode);
    }

    // ===== ÜBERSCHREIBUNG DER TREE-STRATEGIEN FÜR BALANCE =====

    /**
     * Überschreibt die allgemeine Baum-Struktur-Erstellung mit Balance-spezifischer Logik.
     * Erstellt eine balancierte Baum-Struktur mit optimaler Verteilung.
     *
     * @param totalNodes Anzahl der zu erstellenden Knoten
     * @param simTime Aktuelle Simulationszeit für Link-Erstellung
     * @param props Properties der Simulation
     * @return Die Root-Node der erstellten balancierten Struktur
     */
    @Override
    protected MirrorNode buildStructure(int totalNodes, int simTime, Properties props) {
        if (totalNodes <= 0 || !mirrorIterator.hasNext()) return null;

        // Bestimme Links pro Node basierend auf Network-Einstellungen oder Konfiguration
        int linksPerNode = (network != null) ?
                network.getNumTargetLinksPerMirror() : targetLinksPerNode;

        // Erstelle Root-Node mit erstem Mirror
        Mirror rootMirror = mirrorIterator.next();
        BalancedTreeMirrorNode root = new BalancedTreeMirrorNode(
                idGenerator.getNextID(), rootMirror, linksPerNode);

        // Setze Root als Head - nur der Head gibt den Strukturtyp vor
        root.setHead(true);

        // Erstelle balancierte Baum-Struktur mit Breadth-First-Ansatz
        buildBalancedTreeBreadthFirst(root, totalNodes - 1, linksPerNode, simTime, props);

        return root;
    }

    /**
     * Baut die balancierte Baum-Struktur mit Breadth-First-Ansatz auf.
     * Optimiert die Verteilung für die bestmögliche Balance.
     *
     * @param root Root-Node der Struktur
     * @param remainingNodes Anzahl noch zu erstellender Knoten
     * @param linksPerNode Maximale Anzahl Kinder pro Knoten
     * @param simTime Aktuelle Simulationszeit
     * @param props Properties der Simulation
     */
    private void buildBalancedTreeBreadthFirst(BalancedTreeMirrorNode root, int remainingNodes,
                                               int linksPerNode, int simTime, Properties props) {
        if (remainingNodes <= 0 || !mirrorIterator.hasNext()) return;

        // Queue für Breadth-First-Aufbau (optimiert für Balance)
        Queue<BalancedTreeMirrorNode> parentQueue = new LinkedList<>();
        parentQueue.offer(root);

        while (remainingNodes > 0 && !parentQueue.isEmpty() && mirrorIterator.hasNext()) {
            BalancedTreeMirrorNode currentParent = parentQueue.poll();

            // Berechne optimale Kindanzahl für diesen Parent zur Balance-Erhaltung
            int childrenToAdd = calculateOptimalChildrenForBalance(
                    currentParent, remainingNodes, linksPerNode, parentQueue.size());

            // Füge Kinder zu diesem Parent hinzu
            for (int i = 0; i < childrenToAdd && remainingNodes > 0 && mirrorIterator.hasNext(); i++) {
                Mirror childMirror = mirrorIterator.next();
                BalancedTreeMirrorNode child = new BalancedTreeMirrorNode(
                        idGenerator.getNextID(), childMirror, linksPerNode);

                // Erstelle StructureNode-Verbindung (nur Root hat Head-Status)
                currentParent.addChild(child);
                child.setParent(currentParent);

                // Erstelle echten Mirror-Link
                createTreeMirrorLink(currentParent, child, simTime, props);

                // Füge Kind für nächste Ebene zur Queue hinzu
                parentQueue.offer(child);
                remainingNodes--;
            }
        }
    }

    /**
     * Berechnet die optimale Anzahl Kinder für einen Parent-Knoten zur Balance-Erhaltung.
     * Berücksichtigt die noch zu verteilenden Knoten und die Anzahl wartender Parents.
     *
     * @param parent Der Parent-Knoten
     * @param remainingNodes Anzahl noch zu verteilender Knoten
     * @param maxLinksPerNode Maximale Links pro Knoten
     * @param queueSize Anzahl wartender Parent-Knoten
     * @return Optimale Anzahl Kinder für diesen Parent
     */
    private int calculateOptimalChildrenForBalance(BalancedTreeMirrorNode parent,
                                                   int remainingNodes, int maxLinksPerNode,
                                                   int queueSize) {
        if (remainingNodes <= 0) return 0;

        // Basis: Maximale Kinder pro Knoten respektieren
        int maxChildren = Math.min(maxLinksPerNode, remainingNodes);

        // Balance-Optimierung: Gleichmäßige Verteilung auf wartende Parents
        if (queueSize > 0) {
            int optimalDistribution = (int) Math.ceil((double) remainingNodes / (queueSize + 1));
            maxChildren = Math.min(maxChildren, optimalDistribution);
        }

        // Mindestens 1 Kind, wenn noch Knoten verfügbar
        return Math.max(1, maxChildren);
    }

    /**
     * Erstellt einen Mirror-Link zwischen Parent und Child (wiederverwendet aus TreeTopologyStrategy).
     */
    private void createTreeMirrorLink(BalancedTreeMirrorNode parent, BalancedTreeMirrorNode child,
                                      int simTime, Properties props) {
        Link link = new Link(idGenerator.getNextID(),
                parent.getMirror(), child.getMirror(), simTime, props);
        parent.getMirror().addLink(link);
        child.getMirror().addLink(link);
    }

    /**
     * Überschreibt die allgemeine Knoten-Hinzufügung mit Balance-spezifischer Logik.
     * Neue Knoten werden an den optimalen Stellen eingefügt, um die Balance zu bewahren.
     *
     * @param nodesToAdd Anzahl der hinzuzufügenden Knoten
     * @return Tatsächliche Anzahl der hinzugefügten Knoten
     */
    @Override
    protected int addNodesToStructure(int nodesToAdd) {
        if (nodesToAdd <= 0 || getCurrentStructureRoot() == null) return 0;

        BalancedTreeMirrorNode root = (BalancedTreeMirrorNode) getCurrentStructureRoot();
        int actuallyAdded = 0;

        while (actuallyAdded < nodesToAdd && mirrorIterator.hasNext()) {
            // Finde besten Einfüge-Punkt mit Balance-Berücksichtigung
            BalancedTreeMirrorNode bestParent = findOptimalBalancedInsertionParent(root);
            if (bestParent == null) break;

            // Erstelle neuen Knoten
            Mirror mirror = mirrorIterator.next();
            BalancedTreeMirrorNode newNode = new BalancedTreeMirrorNode(
                    idGenerator.getNextID(), mirror, targetLinksPerNode);

            // Füge zur Struktur hinzu
            bestParent.addChild(newNode);
            newNode.setParent(bestParent);

            // Erstelle Mirror-Link
            createTreeMirrorLink(bestParent, newNode, 0, new Properties()); // TODO: simTime/props

            actuallyAdded++;
        }

        return actuallyAdded;
    }

    /**
     * Findet den optimalen Parent für das Einfügen eines neuen Knotens mit Balance-Erhaltung.
     * Bevorzugt Knoten mit niedrigerer Tiefe und weniger Kindern.
     */
    private BalancedTreeMirrorNode findOptimalBalancedInsertionParent(BalancedTreeMirrorNode root) {
        Stack<BalancedTreeMirrorNode> candidateStack = new Stack<>();
        BalancedTreeMirrorNode bestCandidate = null;
        int lowestDepth = Integer.MAX_VALUE;
        int fewestChildren = Integer.MAX_VALUE;

        // Stack-basierte Traversierung aller Knoten
        candidateStack.push(root);

        while (!candidateStack.isEmpty()) {
            BalancedTreeMirrorNode current = candidateStack.pop();

            // Prüfe, ob dieser Knoten weitere Kinder akzeptieren kann
            if (current.canAcceptMoreChildren()) {
                int currentDepth = calculateNodeDepthInTree(current);
                int currentChildren = current.getChildren().size();

                // Bevorzuge niedrigere Tiefe, dann weniger Kinder
                if (currentDepth < lowestDepth ||
                        (currentDepth == lowestDepth && currentChildren < fewestChildren)) {
                    bestCandidate = current;
                    lowestDepth = currentDepth;
                    fewestChildren = currentChildren;
                }
            }

            // Füge Kinder zum Stack für weitere Exploration hinzu
            for (StructureNode child : current.getChildren()) {
                if (child instanceof BalancedTreeMirrorNode balancedChild) {
                    candidateStack.push(balancedChild);
                }
            }
        }

        return bestCandidate;
    }

    /**
     * Überschreibt die allgemeine Knoten-Entfernung mit Balance-spezifischer Logik.
     * Entfernt bevorzugt tiefste Blatt-Knoten, um die Balance zu erhalten.
     *
     * @param nodesToRemove Anzahl der zu entfernenden Knoten
     * @return Anzahl der tatsächlich entfernten Knoten
     */
    @Override
    protected int removeNodesFromStructure(int nodesToRemove) {
        if (nodesToRemove <= 0 || getCurrentStructureRoot() == null) return 0;

        BalancedTreeMirrorNode root = (BalancedTreeMirrorNode) getCurrentStructureRoot();
        int actuallyRemoved = 0;

        while (actuallyRemoved < nodesToRemove) {
            // Finde tiefsten Blatt-Knoten (außer Root) für Balance-erhaltende Entfernung
            BalancedTreeMirrorNode leafToRemove = findDeepestLeafForBalancedRemoval(root);
            if (leafToRemove == null || leafToRemove == root) break;

            removeBalancedTreeNode(leafToRemove);
            actuallyRemoved++;
        }

        return actuallyRemoved;
    }

    /**
     * Findet den tiefsten Blatt-Knoten für Balance-erhaltende Entfernung.
     * Bevorzugt Knoten mit der größten Tiefe, die keine Kinder haben.
     */
    private BalancedTreeMirrorNode findDeepestLeafForBalancedRemoval(BalancedTreeMirrorNode root) {
        Stack<BalancedTreeMirrorNode> nodeStack = new Stack<>();
        BalancedTreeMirrorNode deepestLeaf = null;
        int maxDepth = -1;

        nodeStack.push(root);

        while (!nodeStack.isEmpty()) {
            BalancedTreeMirrorNode current = nodeStack.pop();

            // Prüfe, ob dies ein Blatt-Knoten ist (keine Kinder) und nicht die Root
            if (current.getChildren().isEmpty() && current != root) {
                int currentDepth = calculateNodeDepthInTree(current);
                if (currentDepth > maxDepth) {
                    deepestLeaf = current;
                    maxDepth = currentDepth;
                }
            }

            // Füge Kinder zum Stack hinzu
            for (StructureNode child : current.getChildren()) {
                if (child instanceof BalancedTreeMirrorNode balancedChild) {
                    nodeStack.push(balancedChild);
                }
            }
        }

        return deepestLeaf;
    }

    /**
     * Entfernt einen Knoten aus der balancierten Baum-Struktur.
     * Wiederverwendet die Tree-Logik der Elternklasse.
     */
    private void removeBalancedTreeNode(BalancedTreeMirrorNode nodeToRemove) {
        // Entferne Mirror-Links durch Mirror-Shutdown
        Mirror mirror = nodeToRemove.getMirror();
        if (mirror != null) {
            mirror.shutdown(0); // TODO: simTime
        }

        // Bereinige StructureNode-Beziehungen
        StructureNode parent = nodeToRemove.getParent();
        if (parent != null) {
            parent.removeChild(nodeToRemove);
        }
    }

    /**
     * Berechnet die Tiefe eines Knotens im Baum durch rekursive Parent-Navigation.
     */
    private int calculateNodeDepthInTree(StructureNode node) {
        int depth = 0;
        StructureNode current = node;

        while (current.getParent() != null) {
            depth++;
            current = current.getParent();
        }

        return depth;
    }

    // ===== BALANCE-SPEZIFISCHE ANALYSEMETHODEN =====

    /**
     * Berechnet die Balance des aktuellen Baums.
     * Misst die Standardabweichung der Knotenzahl pro Tiefenebene.
     *
     * @return Balance-Wert (0.0 = perfekt balanciert, höhere Werte = unbalanciert)
     */
    public double calculateCurrentTreeBalance() {
        MirrorNode root = getCurrentStructureRoot();
        if (!(root instanceof BalancedTreeMirrorNode balancedRoot)) return Double.MAX_VALUE;

        return calculateTreeBalance(balancedRoot);
    }

    /**
     * Berechnet die Balance eines Baums basierend auf der Tiefenverteilung.
     */
    private double calculateTreeBalance(BalancedTreeMirrorNode root) {
        if (root == null) return 0.0;

        Map<Integer, Integer> depthCounts = new HashMap<>();
        Stack<BalancedTreeMirrorNode> nodeStack = new Stack<>();
        nodeStack.push(root);

        // Sammle Knotenzahl pro Tiefe
        while (!nodeStack.isEmpty()) {
            BalancedTreeMirrorNode node = nodeStack.pop();
            int depth = calculateNodeDepthInTree(node);
            depthCounts.put(depth, depthCounts.getOrDefault(depth, 0) + 1);

            for (StructureNode child : node.getChildren()) {
                if (child instanceof BalancedTreeMirrorNode balancedChild) {
                    nodeStack.push(balancedChild);
                }
            }
        }

        if (depthCounts.size() <= 1) return 0.0;

        // Berechne Standardabweichung
        double avgNodesPerDepth = depthCounts.values().stream()
                .mapToInt(Integer::intValue).average().orElse(0.0);

        double variance = depthCounts.values().stream()
                .mapToDouble(count -> Math.pow(count - avgNodesPerDepth, 2))
                .average().orElse(0.0);

        return Math.sqrt(variance);
    }

    // ===== KONFIGURATION =====

    /**
     * Gibt den aktuellen Verzweigungsfaktor zurück.
     */
    public int getTargetLinksPerNode() {
        return targetLinksPerNode;
    }

    /**
     * Setzt den Verzweigungsfaktor für neue Strukturen.
     */
    public void setTargetLinksPerNode(int targetLinksPerNode) {
        this.targetLinksPerNode = Math.max(1, targetLinksPerNode);
    }

    @Override
    public String toString() {
        return "BalancedTreeTopologyStrategy{" +
                "substructureId=" + getSubstructureId() +
                ", targetLinksPerNode=" + targetLinksPerNode +
                ", balance=" + String.format("%.2f", calculateCurrentTreeBalance()) +
                "}";
    }
}