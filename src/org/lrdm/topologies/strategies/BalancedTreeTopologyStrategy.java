package org.lrdm.topologies.strategies;

import org.lrdm.effectors.MirrorChange;
import org.lrdm.effectors.TargetLinkChange;
import org.lrdm.effectors.TopologyChange;
import org.lrdm.topologies.node.BalancedTreeMirrorNode;
import org.lrdm.topologies.node.MirrorNode;
import org.lrdm.topologies.node.StructureNode;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.Action;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Eine spezialisierte {@link TreeTopologyStrategy}, die Mirrors als balancierten Baum mit einer
 * einzelnen Root verknüpft. Jeder Mirror hat maximal {@link Network#getNumTargetLinksPerMirror()} Kinder.
 * <p>
 * **Balance-Eigenschaften**:
 * - Erweitert TreeTopologyStrategy um Balance-Optimierung
 * - Breadth-First-Aufbau für gleichmäßige Tiefenverteilung
 * - Konfigurierbare maximale Abweichung der Balance
 * - Verwendet {@link BalancedTreeMirrorNode} für Balance-spezifische Funktionalität
 * <p>
 * **Wiederverwendung von TreeTopologyStrategy**:
 * - Alle TopologyStrategy-Interface-Methoden werden vererbt
 * - Grundlegende Baum-Logik wird wiederverwendet
 * - Nur Balance-spezifische Methoden werden überschrieben
 * <p>
 * **Planungsebene vs. Ausführungsebene**:
 * - Planungsebene: `removeNodesFromStructure()` - plant strukturelle Änderungen ohne Zeitbezug
 * - Ausführungsebene: `handleRemoveMirrors()` - führt Mirror-Shutdown innerhalb der Planungsgrenzen aus
 * - Automatisches Mitwachsen: MirrorNode-Ebene passt sich an StructureNode-Planung an
 * <p>
 * **Unbegrenztes Baumwachstum**: Im Gegensatz zu statischen Strukturen (Quadrate, Ringe)
 * können Bäume dynamisch in alle Richtungen wachsen ohne strukturelle Limitierungen.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class BalancedTreeTopologyStrategy extends TreeTopologyStrategy {

    // ===== BALANCE-SPEZIFISCHE KONFIGURATION =====
    private int targetLinksPerNode = 2;
    private double maxAllowedBalanceDeviation = 1.0;

    // ===== KONSTRUKTOREN =====

    public BalancedTreeTopologyStrategy() {
        super();
    }

    public BalancedTreeTopologyStrategy(int targetLinksPerNode) {
        super();
        this.targetLinksPerNode = Math.max(1, targetLinksPerNode);
    }

    public BalancedTreeTopologyStrategy(int targetLinksPerNode, double maxAllowedBalanceDeviation) {
        super();
        this.targetLinksPerNode = Math.max(1, targetLinksPerNode);
        this.maxAllowedBalanceDeviation = Math.max(0.0, maxAllowedBalanceDeviation);
    }

    // ===== ÜBERSCHREIBUNG NUR DER BALANCE-SPEZIFISCHEN METHODEN =====

    /**
     * **PLANUNGSEBENE**: Erstellt die balancierte Baum-Struktur mit optimaler Verteilung.
     * Überschreibt TreeTopologyStrategy für Balance-spezifische Logik.
     * Ermöglicht unbegrenztes Baumwachstum ohne strukturelle Einschränkungen.
     *
     * @param totalNodes Anzahl der zu erstellenden Knoten
     * @param props      Properties der Simulation
     * @return Die Root-Node der erstellten balancierten Baum-Struktur
     */
    @Override
    protected MirrorNode buildStructure(int totalNodes, Properties props) {
        if (totalNodes < 1 || !hasNextMirror()) {
            return null;
        }

        // 1. Erstelle Root-Node als BalancedTreeMirrorNode
        BalancedTreeMirrorNode root = (BalancedTreeMirrorNode) getMirrorNodeFromIterator();
        if (root == null) return null;

        root.setHead(true);
        setCurrentStructureRoot(root);

        if (totalNodes == 1) {
            return root; // Nur Root-Node
        }

        // 2. Baue balancierte Struktur mit Breadth-First für optimale Balance
        buildBalancedTreeBreadthFirst(root, totalNodes - 1, targetLinksPerNode, props);

        return root;
    }

    /**
     * **PLANUNGSEBENE**: Fügt neue Knoten zur balancierten Struktur hinzu.
     * Überschreibt TreeTopologyStrategy für Balance-optimierte Einfügung.
     * Automatisches Mitwachsen: MirrorNode-Ebene passt sich der Planung an.
     *
     * @param nodesToAdd Anzahl der hinzuzufügenden Knoten
     * @return Tatsächliche Anzahl der hinzugefügten Knoten
     */
    @Override
    protected int addNodesToStructure(int nodesToAdd) {
        if (nodesToAdd <= 0) return 0;

        BalancedTreeMirrorNode root = getBalancedTreeRoot();
        if (root == null) return 0;

        int actuallyAdded = 0;

        // Füge neue Knoten mit Balance-optimierter Strategie hinzu
        for (int i = 0; i < nodesToAdd; i++) {
            // Erstelle neuen Knoten
            BalancedTreeMirrorNode newNode = (BalancedTreeMirrorNode) getMirrorNodeFromIterator();
            if (newNode == null) break;

            // Finde optimalen Balance-bewussten Einfügepunkt
            BalancedTreeMirrorNode insertionParent = findOptimalBalancedInsertionParent(root);
            if (insertionParent != null && insertionParent.canAcceptMoreChildren()) {
                // Verbinde neuen Knoten mit Einfügepunkt (nur Planungsebene)
                insertionParent.addChild(newNode);
                actuallyAdded++;
            } else {
                // Keine geeigneten Einfügepunkte mehr verfügbar
                break;
            }
        }

        return actuallyAdded;
    }

    /**
     * **PLANUNGSEBENE**: Entfernt Knoten aus der Struktur-Planung.
     * Überschreibt TreeTopologyStrategy für Balance-erhaltende Entfernung.
     * Arbeitet ohne Zeitbezug - plant nur die strukturellen Änderungen.
     *
     * @param nodesToRemove Anzahl der zu entfernenden Knoten
     * @return Anzahl der tatsächlich entfernten Knoten
     */
    @Override
    protected int removeNodesFromStructure(int nodesToRemove) {
        if (nodesToRemove <= 0) return 0;

        List<BalancedTreeMirrorNode> balancedNodes = getAllBalancedTreeNodes();
        BalancedTreeMirrorNode root = getBalancedTreeRoot();

        if (balancedNodes.size() - nodesToRemove < 1) {
            nodesToRemove = balancedNodes.size() - 1;
        }
        if (nodesToRemove <= 0) return 0;

        int actuallyRemoved = 0;

        // Balance-erhaltende Entfernung: Tiefste Blätter zuerst
        for (int i = 0; i < nodesToRemove; i++) {
            BalancedTreeMirrorNode nodeToRemove = findDeepestLeafForBalancedRemoval(root);
            if (nodeToRemove != null && nodeToRemove != root) {
                removeNodeFromStructuralPlanning(nodeToRemove);
                actuallyRemoved++;
            } else {
                break;
            }
        }

        return actuallyRemoved;
    }

    /**
     * **AUSFÜHRUNGSEBENE**: Überschreibt die Mirror-Entfernung für balancierte Bäume.
     * Delegiert an TreeTopologyStrategy mit BALANCED_TREE-Struktur-Typ.
     *
     * @param n Das Netzwerk
     * @param removeMirrors Anzahl zu entfernender Mirrors
     * @param props Properties der Simulation
     * @param simTime Aktuelle Simulationszeit
     * @return Set der entfernten Mirrors
     */
    @Override
    public Set<Mirror> handleRemoveMirrors(Network n, int removeMirrors, Properties props, int simTime) {
        // Delegiert an TreeTopologyStrategy mit spezifischem StructureType
        return handleRemoveMirrorsWithStructureType(n, removeMirrors, props, simTime,
                StructureNode.StructureType.BALANCED_TREE);
    }

    // ===== TOPOLOGY STRATEGY INTERFACE IMPLEMENTATION =====

    /**
     * Berechnet die erwartete Anzahl der Links für balancierte Bäume.
     * Überschreibt TreeTopologyStrategy, da balancierte Bäume mehr als n-1 Links haben können.
     * <p>
     * Bei balancierten Bäumen ist die Link-Anzahl abhängig von:
     * - Anzahl der Knoten (n)
     * - Target Links pro Knoten (konfigurierbar)
     * - Balance-Constraints
     *
     * @param n Das Netzwerk
     * @return Erwartete Anzahl Links für balancierten Baum
     */
    @Override
    public int getNumTargetLinks(Network n) {
        if (n == null || n.getNumMirrors() <= 0) {
            return 0;
        }

        int numMirrors = n.getNumMirrors();

        // Für einen einzelnen Knoten: keine Links
        if (numMirrors == 1) {
            return 0;
        }

        // Berechne theoretische Links basierend auf targetLinksPerNode
        // Jeder Knoten hat maximal targetLinksPerNode ausgehende Links (Kinder)
        // Die Gesamtanzahl Links in einem Baum ist die Summe aller Parent-Child-Verbindungen

        // Bei einem balancierten Baum mit targetLinksPerNode Kindern pro Knoten:
        // - Root hat targetLinksPerNode Kinder
        // - Jeder Knoten auf Ebene i hat targetLinksPerNode Kinder (falls möglich)
        // - Gesamtlinks = n-1 (Baum-Minimum) bis zu einer balanceabhängigen Obergrenze

        // Berechne maximale Links für die aktuelle Konfiguration
        int maxLinksPerNode = Math.max(targetLinksPerNode, n.getNumTargetLinksPerMirror());

        // Balance-bewusste Berechnung:
        // In einem perfekt balancierten Baum mit k Kindern pro Knoten
        // sind (n-1) Links das Minimum, aber wir können bis zu
        // (n * maxLinksPerNode) / 2 Links haben (jeder Link wird von beiden Seiten gezählt)

        int theoreticalMaxLinks = (numMirrors * maxLinksPerNode) / 2;
        int treeMinimumLinks = numMirrors - 1;

        // Für balancierte Bäume: Verwende das theoretische Maximum, aber mindestens n-1
        return Math.max(treeMinimumLinks, Math.min(theoreticalMaxLinks, calculateOptimalLinksForBalancedTree(numMirrors, maxLinksPerNode)));
    }

    /**
     * Berechnet die erwartete Anzahl der Links, wenn die gegebene Aktion ausgeführt wird.
     * Überschreibt TreeTopologyStrategy für Balance-spezifische Link-Berechnung.
     * <p>
     * Behandelt verschiedene Action-Typen mit Balance-Bewusstsein:
     * 1. MirrorChange: Berechnet Links für neue Mirror-Anzahl mit Balance-Optimierung
     * 2. TargetLinkChange: Berechnet Links basierend auf neuen Links pro Mirror mit Balance-Constraints
     * 3. TopologyChange: Delegiert an neue Topology-Strategie
     *
     * @param a Die Action, deren Auswirkungen berechnet werden sollen
     * @return Anzahl der erwarteten Links nach Ausführung der Action
     */
    @Override
    public int getPredictedNumTargetLinks(Action a) {
        if (a == null || a.getNetwork() == null) {
            return 0;
        }

        Network network = a.getNetwork();
        int currentMirrors = network.getNumMirrors();

        // 1. MirrorChange: Ändert die Anzahl der Mirrors
        if (a instanceof MirrorChange mc) {
            int newMirrorCount = mc.getNewMirrors();

            if (newMirrorCount <= 0) return 0;
            if (newMirrorCount == 1) return 0;

            // Balance-bewusste Berechnung für neue Mirror-Anzahl
            int maxLinksPerNode = Math.max(targetLinksPerNode, network.getNumTargetLinksPerMirror());
            return calculateOptimalLinksForBalancedTree(newMirrorCount, maxLinksPerNode);
        }

        // 2. TargetLinkChange: Ändert die Links pro Mirror
        else if (a instanceof TargetLinkChange tlc) {
            int newLinksPerMirror = tlc.getNewLinksPerMirror();

            if (currentMirrors <= 1) return 0;

            // Balance-bewusste Berechnung mit neuen Links pro Mirror
            int effectiveLinksPerNode = Math.max(targetLinksPerNode, newLinksPerMirror);
            return calculateOptimalLinksForBalancedTree(currentMirrors, effectiveLinksPerNode);
        }

        // 3. TopologyChange: Delegiert an neue Topology-Strategie
        else if (a instanceof TopologyChange tc) {
            TopologyStrategy newStrategy = tc.getNewTopology();
            if (newStrategy != null) {
                return newStrategy.getNumTargetLinks(tc.getNetwork());
            }
            // Fallback: Aktuelle Strategie verwenden
            return getNumTargetLinks(tc.getNetwork());
        }

        // 4. Unbekannter Action-Typ: Verwende aktuelle Netzwerk-Konfiguration
        return getNumTargetLinks(network);
    }

    // ===== BALANCE-SPEZIFISCHE LINK-BERECHNUNG =====

    /**
     * Berechnet die optimale Anzahl Links für einen balancierten Baum.
     * <p>
     * Berücksichtigt:
     * - Baum-Minimum: n-1 Links
     * - Balance-Optimierung: Gleichmäßige Verteilung
     * - Konfigurierte maximale Links pro Knoten
     *
     * @param numNodes Anzahl der Knoten im Baum
     * @param maxLinksPerNode Maximale ausgehende Links pro Knoten
     * @return Optimale Anzahl Links für balancierten Baum
     */
    private int calculateOptimalLinksForBalancedTree(int numNodes, int maxLinksPerNode) {
        if (numNodes <= 1) return 0;

        int treeMinimum = numNodes - 1;

        // Für einen perfekt balancierten Baum mit k Kindern pro Knoten:
        // Berechne die Anzahl der Ebenen und die Verteilung

        // Einfache Heuristik: In einem balancierten Baum mit maxLinksPerNode Kindern
        // pro Knoten sind die meisten Knoten interne Knoten (haben Kinder)
        // Nur die Blätter haben keine Kinder

        // Berechne ungefähre Anzahl der internen Knoten
        // In einem k-nären Baum ist etwa 1/k der Knoten Blätter
        double leafRatio = 1.0 / maxLinksPerNode;
        int approximateInternalNodes = (int) Math.ceil(numNodes * (1.0 - leafRatio));

        // Jeder interne Knoten hat im Durchschnitt targetLinksPerNode ausgehende Links
        int balancedLinks = approximateInternalNodes * targetLinksPerNode;

        // Stelle sicher, dass wir mindestens das Baum-Minimum haben
        // und nicht mehr als theoretisch möglich
        int maxPossibleLinks = (numNodes * maxLinksPerNode) / 2;

        return Math.max(treeMinimum, Math.min(balancedLinks, maxPossibleLinks));
    }

    // ===== FACTORY-METHODEN-ÜBERSCHREIBUNG =====

    /**
     * Factory-Methode für Balance-spezifische MirrorNode-Erstellung.
     * Überschreibt TreeTopologyStrategy für BalancedTreeMirrorNode-Erstellung.
     *
     * @param mirror Der Mirror, für den ein MirrorNode erstellt werden soll
     * @return Neuer BalancedTreeMirrorNode
     */
    @Override
    protected MirrorNode createMirrorNodeForMirror(Mirror mirror) {
        return new BalancedTreeMirrorNode(mirror.getID(), mirror, targetLinksPerNode);
    }

    // ===== BALANCE-SPEZIFISCHE HILFSMETHODEN =====

    /**
     * Baut die balancierte Baum-Struktur mit Breadth-First-Ansatz auf.
     * Ermöglicht optimale Verteilung ohne strukturelle Limitierungen.
     *
     * @param root           Root-Node der Struktur
     * @param remainingNodes Anzahl der noch zu verbindenden Knoten
     * @param linksPerNode   Maximale Links pro Knoten
     * @param props          Properties der Simulation
     */
    private void buildBalancedTreeBreadthFirst(BalancedTreeMirrorNode root, int remainingNodes,
                                               int linksPerNode, Properties props) {
        if (remainingNodes <= 0) return;

        Queue<BalancedTreeMirrorNode> parentQueue = new LinkedList<>();
        parentQueue.offer(root);

        int nodesCreated = 0;

        while (!parentQueue.isEmpty() && nodesCreated < remainingNodes) {
            BalancedTreeMirrorNode parent = parentQueue.poll();

            // Berechne optimale Kinder-Anzahl für Balance
            int optimalChildren = calculateOptimalChildrenForBalance(parent,
                    remainingNodes - nodesCreated, linksPerNode, parentQueue.size());

            // Füge Kinder hinzu
            for (int i = 0; i < optimalChildren && nodesCreated < remainingNodes; i++) {
                BalancedTreeMirrorNode child = getMirrorNodeFromIterator();
                if (child == null) break;

                // Verbinde Kind mit Parent (nur Planungsebene)
                parent.addChild(child);

                // Füge zur nächsten Ebene hinzu
                parentQueue.offer(child);

                nodesCreated++;
            }
        }
    }

    /**
     * Erstellt einen neuen MirrorNode mit Mirror aus dem Iterator.
     * AKTUALISIERT: Fügt den Knoten automatisch zu structureNodes hinzu.
     *
     * @return Neuer MirrorNode mit zugeordnetem Mirror oder null
     */
    @Override
    protected BalancedTreeMirrorNode getMirrorNodeFromIterator() {
        if (mirrorIterator != null && mirrorIterator.hasNext()) {
            BalancedTreeMirrorNode node = (BalancedTreeMirrorNode) super.getMirrorNodeFromIterator();
            node.addNodeType(StructureNode.StructureType.BALANCED_TREE);
            return node;
        }
        return null;
    }

    /**
     * Berechnet optimale Kinder-Anzahl für Balance-Erhaltung.
     * Berücksichtigt unbegrenztes Baumwachstum ohne strukturelle Limitierungen.
     *
     * @param parent Der Parent-Knoten
     * @param remainingNodes Noch zu verteilende Knoten
     * @param maxLinksPerNode Maximale Links pro Knoten
     * @param queueSize Aktuelle Queue-Größe
     * @return Optimale Anzahl Kinder für diesen Parent
     */
    private int calculateOptimalChildrenForBalance(BalancedTreeMirrorNode parent,
                                                   int remainingNodes, int maxLinksPerNode,
                                                   int queueSize) {
        // Basis: Maximale Links pro Knoten
        int maxChildren = maxLinksPerNode;

        // Balance-Optimierung: Gleichmäßige Verteilung
        if (queueSize > 0) {
            int fairShare = (remainingNodes + queueSize - 1) / queueSize; // Aufrunden
            maxChildren = Math.min(maxChildren, fairShare);
        }

        // Sicherstellen, dass nicht mehr Knoten erstellt werden als verfügbar
        return Math.min(maxChildren, remainingNodes);
    }

    /**
     * Findet optimalen Parent für Balance-erhaltende Einfügung.
     * Nutzt unbegrenztes Baumwachstum für optimale Verteilung.
     *
     * @param root Root-Node der Struktur
     * @return Optimaler Parent oder null, wenn keiner verfügbar
     */
    private BalancedTreeMirrorNode findOptimalBalancedInsertionParent(BalancedTreeMirrorNode root) {
        if (root == null) return null;

        // Breadth-First-Suche nach geeignetem Parent mit Balance-Bewertung
        Queue<BalancedTreeMirrorNode> queue = new LinkedList<>();
        queue.offer(root);

        BalancedTreeMirrorNode bestCandidate = null;
        int minDepth = Integer.MAX_VALUE;

        while (!queue.isEmpty()) {
            BalancedTreeMirrorNode current = queue.poll();

            if (current.canAcceptMoreChildren()) {
                int currentDepth = current.getDepthInTree();

                // Bevorzuge geringste Tiefe für Balance
                if (currentDepth < minDepth) {
                    minDepth = currentDepth;
                    bestCandidate = current;
                }
            }

            // Füge alle Kinder zur Queue hinzu
            for (StructureNode child : current.getChildren()) {
                if (child instanceof BalancedTreeMirrorNode balancedChild) {
                    queue.offer(balancedChild);
                }
            }
        }

        return bestCandidate;
    }

    /**
     * Findet tiefsten Blatt-Knoten für Balance-erhaltende Entfernung.
     * Typsichere Implementierung ohne redundante instanceof-Checks.
     *
     * @param root Root-Node der Struktur
     * @return Tiefster Blatt-Knoten oder null, wenn keiner verfügbar
     */
    private BalancedTreeMirrorNode findDeepestLeafForBalancedRemoval(BalancedTreeMirrorNode root) {
        if (root == null) return null;

        List<BalancedTreeMirrorNode> leaves = new ArrayList<>();
        collectBalancedLeaves(root, leaves);

        if (leaves.isEmpty()) return null;

        // Finde tiefsten Blatt-Knoten (höchste Tiefe)
        return leaves.stream()
                .max(Comparator.comparingInt(BalancedTreeMirrorNode::getDepthInTree))
                .orElse(null);
    }

    /**
     * Sammelt alle Blatt-Knoten der balancierten Struktur.
     *
     * @param node Aktueller Knoten
     * @param leaves Liste zum Sammeln der Blätter
     */
    private void collectBalancedLeaves(BalancedTreeMirrorNode node, List<BalancedTreeMirrorNode> leaves) {
        if (node == null) return;

        if (node.isLeaf()) {
            leaves.add(node);
            return;
        }

        // Rekursiv alle Kinder durchsuchen
        for (StructureNode child : node.getChildren()) {
            if (child instanceof BalancedTreeMirrorNode balancedChild) {
                collectBalancedLeaves(balancedChild, leaves);
            }
        }
    }

    /**
     * **PLANUNGSEBENE**: Entfernt Knoten aus struktureller Planung.
     * Arbeitet ohne Zeitbezug - nur strukturelle Änderungen.
     * KORRIGIERT: Verwendet die BuildAsSubstructure-API anstatt direkter Collection-Modifikation
     *
     * @param nodeToRemove Der zu entfernende Knoten
     */
    private void removeNodeFromStructuralPlanning(BalancedTreeMirrorNode nodeToRemove) {
        // Entferne aus Parent-Child-Beziehung
        StructureNode parent = nodeToRemove.getParent();
        if (parent != null) {
            parent.removeChild(nodeToRemove);
        }

        // Verweise Kinder an Großeltern (Balance-erhaltend)
        List<StructureNode> children = new ArrayList<>(nodeToRemove.getChildren());
        for (StructureNode child : children) {
            nodeToRemove.removeChild(child);
            if (parent != null) {
                parent.addChild(child);
            }
        }

        // Entferne aus der StructureNode-Verwaltung
        removeFromStructureNodes(nodeToRemove);
    }

    // ===== TYPSICHERE HILFSMETHODEN =====

    /**
     * Gibt Root als BalancedTreeMirrorNode zurück.
     *
     * @return Root-Node als BalancedTreeMirrorNode oder null
     */
    private BalancedTreeMirrorNode getBalancedTreeRoot() {
        MirrorNode root = getCurrentStructureRoot();
        return (root instanceof BalancedTreeMirrorNode) ? (BalancedTreeMirrorNode) root : null;
    }

    /**
     * Gibt alle Knoten als BalancedTreeMirrorNode-Liste zurück.
     *
     * @return Liste aller BalancedTreeMirrorNodes
     */
    private List<BalancedTreeMirrorNode> getAllBalancedTreeNodes() {
        return getAllStructureNodes().stream()
                .filter(BalancedTreeMirrorNode.class::isInstance)
                .map(BalancedTreeMirrorNode.class::cast)
                .collect(Collectors.toList());
    }

    // ===== BALANCE-ANALYSE =====

    /**
     * Berechnet aktuelle Baum-Balance.
     * Nutzt die Balance-Analyse-Funktionen von BalancedTreeMirrorNode.
     *
     * @return Aktuelle Balance-Metrik
     */
    public double calculateCurrentTreeBalance() {
        BalancedTreeMirrorNode root = getBalancedTreeRoot();
        return root != null ? root.calculateTreeBalance() : 0.0;
    }

    /**
     * Prüft Balance-Kriterien.
     *
     * @return true, wenn der Baum innerhalb der erlaubten Balance-Abweichung liegt
     */
    public boolean isCurrentTreeBalanced() {
        double currentBalance = calculateCurrentTreeBalance();
        return currentBalance <= maxAllowedBalanceDeviation;
    }

    /**
     * Detaillierte Balance-Informationen.
     *
     * @return Map mit detaillierten Balance-Metriken
     */
    public Map<String, Object> getDetailedBalanceInfo() {
        Map<String, Object> info = new HashMap<>();
        BalancedTreeMirrorNode root = getBalancedTreeRoot();

        if (root != null) {
            info.put("currentBalance", calculateCurrentTreeBalance());
            info.put("isBalanced", isCurrentTreeBalanced());
            info.put("maxDepth", root.getMaxTreeDepth());
            info.put("nodeCount", root.getTreeSize());
            info.put("targetLinksPerNode", targetLinksPerNode);
            info.put("maxAllowedDeviation", maxAllowedBalanceDeviation);
        }

        return info;
    }

    // ===== KONFIGURATION =====

    public int getTargetLinksPerNode() {
        return targetLinksPerNode;
    }

    public void setTargetLinksPerNode(int targetLinksPerNode) {
        this.targetLinksPerNode = Math.max(1, targetLinksPerNode);
    }

    public double getMaxAllowedBalanceDeviation() {
        return maxAllowedBalanceDeviation;
    }

    public void setMaxAllowedBalanceDeviation(double maxAllowedBalanceDeviation) {
        this.maxAllowedBalanceDeviation = Math.max(0.0, maxAllowedBalanceDeviation);
    }

    // ===== STRING REPRESENTATION =====

    @Override
    public String toString() {
        try {
            String baseString = super.toString();

            // Sicherheitsprüfungen für die Balance-Berechnung
            BalancedTreeMirrorNode root = getBalancedTreeRoot();
            if (root == null) {
                return baseString + "[targetLinksPerNode=" + targetLinksPerNode +
                        ", balance=undefined (no root), isBalanced=unknown]";
            }

            double currentBalance = calculateCurrentTreeBalance();
            boolean isBalanced = isCurrentTreeBalanced();

            return baseString + "[targetLinksPerNode=" + targetLinksPerNode +
                    ", balance=" + String.format("%.2f", currentBalance) +
                    ", isBalanced=" + isBalanced + "]";

        } catch (Exception e) {
            // Fallback für alle möglichen Exceptions bei der String-Erstellung
            return getClass().getSimpleName() + "[targetLinksPerNode=" + targetLinksPerNode +
                    ", status=error (" + e.getClass().getSimpleName() + ")]";
        }
    }
}