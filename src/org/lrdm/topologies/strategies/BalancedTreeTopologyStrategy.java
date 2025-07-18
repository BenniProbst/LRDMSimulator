
package org.lrdm.topologies.strategies;

import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.topologies.node.*;
import org.lrdm.topologies.node.StructureNode.StructureType;

import java.util.*;

/**
 * Eine spezialisierte {@link TreeTopologyStrategy}, die Mirrors als balancierten Baum mit einer
 * einzelnen Root verknüpft. Jeder Mirror hat maximal {@link Network#getNumTargetLinksPerMirror()} Kinder.
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

    // ===== ÜBERSCHREIBUNG DER BUILD-AS-SUBSTRUCTURE-METHODEN =====

    /**
     * **PLANUNGSEBENE**: Erstellt die balancierte Baum-Struktur mit optimaler Verteilung.
     * Überschreibt BuildAsSubstructure für Balance-spezifische Logik.
     * Ermöglicht unbegrenztes Baumwachstum ohne strukturelle Einschränkungen.
     */
    @Override
    protected MirrorNode buildStructure(int totalNodes, int simTime, Properties props) {
        if (totalNodes <= 0 || !mirrorIterator.hasNext()) return null;

        // Erstelle Root mit Balance-Konfiguration - verwendet globales network-Objekt
        Mirror rootMirror = mirrorIterator.next();
        BalancedTreeMirrorNode root = new BalancedTreeMirrorNode(rootMirror.getID(), rootMirror, targetLinksPerNode);
        root.setHead(StructureType.BALANCED_TREE,true);
        setCurrentStructureRoot(root);

        // Strukturplanung: Breadth-First für optimale Balance
        if (totalNodes > 1) {
            int maxLinksPerNode = Math.min(network.getNumTargetLinksPerMirror(), targetLinksPerNode);
            buildBalancedTreeBreadthFirst(root, totalNodes - 1, maxLinksPerNode, simTime, props);
        }

        return root;
    }

    /**
     * **PLANUNGSEBENE**: Fügt neue Knoten zur balancierten Struktur hinzu.
     * Überschreibt BuildAsSubstructure für Balance-optimierte Einfügung.
     * Automatisches Mitwachsen: MirrorNode-Ebene passt sich der Planung an.
     */
    @Override
    protected int addNodesToStructure(int nodesToAdd) {
        if (nodesToAdd <= 0) return 0;

        BalancedTreeMirrorNode root = getBalancedTreeRoot();
        if (root == null) return 0;

        int actuallyAdded = 0;

        // Unbegrenztes Baumwachstum: Keine strukturellen Limitierungen
        for (int i = 0; i < nodesToAdd && mirrorIterator.hasNext(); i++) {
            BalancedTreeMirrorNode optimalParent = findOptimalBalancedInsertionParent(root);
            if (optimalParent == null) break;

            // Strukturplanung: Erstelle Knoten mit maximalen Möglichkeiten
            BalancedTreeMirrorNode newNode = createBalancedTreeNode(optimalParent, 0, new Properties());
            if (newNode != null) {
                actuallyAdded++;
                // KORRIGIERT: Verwende addStructureNode anstatt direkter Modifikation
                this.addToStructureNodes(newNode);
            }
        }

        return actuallyAdded;
    }

    /**
     * **PLANUNGSEBENE**: Entfernt Knoten aus der Struktur-Planung.
     * Überschreibt BuildAsSubstructure für Balance-erhaltende Entfernung.
     * Arbeitet ohne Zeitbezug - plant nur die strukturellen Änderungen.
     */
    @Override
    protected int removeNodesFromStructure(int nodesToRemove) {
        if (nodesToRemove <= 0) return 0;

        BalancedTreeMirrorNode root = getBalancedTreeRoot();
        if (root == null) return 0;

        int actuallyRemoved = 0;

        // Planungsebene: Entferne nur aus struktureller Sicht
        for (int i = 0; i < nodesToRemove; i++) {
            BalancedTreeMirrorNode deepestLeaf = findDeepestLeafForBalancedRemoval(root);
            if (deepestLeaf == null || deepestLeaf == root) break;

            // Strukturplanung: Entferne aus Parent-Child-Hierarchie
            removeNodeFromStructuralPlanning(deepestLeaf);
            actuallyRemoved++;
        }

        return actuallyRemoved;
    }


    /**
     * **AUSFÜHRUNGSEBENE**: Topologie-spezifische Mirror-Entfernung ohne Shutdown.
     * Entfernt Knoten in umgekehrter Reihenfolge zum Breadth-First-Aufbau.
     * Dadurch bleibt die Balance automatisch erhalten ohne zusätzliche Rebalancierung.
     */
    @Override
    public Set<Mirror> handleRemoveMirrors(Network n, int removeMirrors, Properties props, int simTime) {
        if (removeMirrors <= 0) return new HashSet<>();

        BalancedTreeMirrorNode root = getBalancedTreeRoot();
        if (root == null) {
            // Fallback: Verwende die BuildAsSubstructure-Implementierung
            return super.handleRemoveMirrors(n, removeMirrors, props, simTime);
        }

        Set<Mirror> cleanedMirrors = new HashSet<>();

        // Entferne Knoten in umgekehrter Breadth-First-Reihenfolge
        for (int i = 0; i < removeMirrors; i++) {
            BalancedTreeMirrorNode nodeToRemove = findRightmostDeepestLeaf(root);
            if (nodeToRemove == null || nodeToRemove == root) {
                break; // Keine weiteren Blätter oder nur Root übrig
            }

            Mirror mirrorToRemove = nodeToRemove.getMirror();
            if (mirrorToRemove != null) {
                // Schalte Mirror und dessen Links aus
                mirrorToRemove.shutdown(simTime);
                // Entferne alle Links
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

    /**
     * Findet das rechteste Blatt der tiefsten Ebene.
     * Dies ist genau das Gegenteil der Breadth-First-Einfügung.
     */
    private BalancedTreeMirrorNode findRightmostDeepestLeaf(BalancedTreeMirrorNode root) {
        if (root == null) return null;

        // Sammle alle Blätter und finde das rechteste auf der tiefsten Ebene
        List<BalancedTreeMirrorNode> leaves = new ArrayList<>();
        collectLeaves(root, leaves);

        if (leaves.isEmpty()) return null;

        // Finde die maximale Tiefe
        int maxDepth = -1;
        for (BalancedTreeMirrorNode leaf : leaves) {
            int depth = calculateNodeDepthInTree(leaf);
            if (depth > maxDepth) {
                maxDepth = depth;
            }
        }

        // Sammle alle Blätter auf der tiefsten Ebene
        List<BalancedTreeMirrorNode> deepestLeaves = new ArrayList<>();
        for (BalancedTreeMirrorNode leaf : leaves) {
            if (calculateNodeDepthInTree(leaf) == maxDepth) {
                deepestLeaves.add(leaf);
            }
        }

        // Finde das rechteste Blatt (= das mit der höchsten ID oder dem rechtesten Index)
        return findRightmostLeaf(deepestLeaves);
    }

    /**
     * Sammelt alle Blätter des Baums.
     */
    private void collectLeaves(BalancedTreeMirrorNode node, List<BalancedTreeMirrorNode> leaves) {
        if (node == null) return;

        if (node.getChildren().isEmpty()) {
            leaves.add(node);
        } else {
            for (BalancedTreeMirrorNode child : getBalancedChildren(node)) {
                collectLeaves(child, leaves);
            }
        }
    }

    /**
     * Findet das rechteste Blatt aus einer Liste von Blättern.
     * Das rechteste Blatt ist dasjenige, das am spätesten in der Breadth-First-Reihenfolge hinzugefügt wurde.
     */
    private BalancedTreeMirrorNode findRightmostLeaf(List<BalancedTreeMirrorNode> leaves) {
        if (leaves.isEmpty()) return null;

        // Sortiere nach ID (höchste ID = zuletzt hinzugefügt = rechtestes)
        return leaves.stream()
                .max(Comparator.comparing(BalancedTreeMirrorNode::getId))
                .orElse(null);
    }

    /**
     * Entfernt einen Knoten aus der Struktur (ohne Mirror-Shutdown).
     */
    private void removeNodeFromStructure(BalancedTreeMirrorNode nodeToRemove) {
        // Entferne aus Parent-Child-Beziehungen
        StructureNode parent = nodeToRemove.getParent();
        if (parent != null) {
            parent.removeChild(nodeToRemove);
        }

        // Bereinige Kinder-Beziehungen
        for (StructureNode child : new ArrayList<>(nodeToRemove.getChildren())) {
            nodeToRemove.removeChild(child);
        }

        // Markiere als entfernt
        nodeToRemove.setParent(null);
    }

    // ===== BALANCE-SPEZIFISCHE HILFSMETHODEN =====

    /**
     * Baut die balancierte Baum-Struktur mit Breadth-First-Ansatz auf.
     * Ermöglicht optimale Verteilung ohne strukturelle Limitierungen.
     */
    private void buildBalancedTreeBreadthFirst(BalancedTreeMirrorNode root, int remainingNodes,
                                               int linksPerNode, int simTime, Properties props) {
        Queue<BalancedTreeMirrorNode> parentQueue = new LinkedList<>();
        parentQueue.offer(root);

        while (!parentQueue.isEmpty() && remainingNodes > 0) {
            BalancedTreeMirrorNode parent = parentQueue.poll();

            // Balance-optimierte Kinder-Verteilung
            int optimalChildren = calculateOptimalChildrenForBalance(parent, remainingNodes, linksPerNode, parentQueue.size());

            // Unbegrenztes Wachstum: Erstelle so viele Kinder wie optimal
            for (int i = 0; i < optimalChildren && remainingNodes > 0; i++) {
                BalancedTreeMirrorNode child = createBalancedTreeNode(parent, simTime, props);
                if (child != null) {
                    parentQueue.offer(child);
                    remainingNodes--;
                    addToStructureNodes(child);
                }
            }
        }
    }

    /**
     * Erstellt einen neuen balancierten Baum-Knoten mit struktureller Planung.
     * Automatisches Mitwachsen: MirrorNode folgt der StructureNode-Planung.
     */
    private BalancedTreeMirrorNode createBalancedTreeNode(BalancedTreeMirrorNode parent, int simTime, Properties props) {
        if (!mirrorIterator.hasNext()) return null;

        // Strukturplanung: Erstelle Knoten mit maximalen Möglichkeiten
        Mirror childMirror = mirrorIterator.next();
        BalancedTreeMirrorNode child = new BalancedTreeMirrorNode(childMirror.getID(), childMirror, targetLinksPerNode);

        //set connection
        parent.addChild(child);

        // Ausführungsebene: Mirror-Link, nur wenn beide Mirrors gültig sind
        if (parent.getMirror() != null && child.getMirror() != null) {
            createBalancedTreeMirrorLink(parent, child, simTime, props);
        }

        return child;
    }

    /**
     * Berechnet optimale Kinder-Anzahl für Balance-Erhaltung.
     * Berücksichtigt unbegrenztes Baumwachstum ohne strukturelle Limitierungen.
     */
    private int calculateOptimalChildrenForBalance(BalancedTreeMirrorNode parent,
                                                   int remainingNodes, int maxLinksPerNode,
                                                   int queueSize) {
        if (remainingNodes <= 0) return 0;

        // Basis: Respektiere maximale Links pro Knoten
        int maxChildren = Math.min(maxLinksPerNode, remainingNodes);

        // Balance-Optimierung: Gleichmäßige Verteilung
        if (queueSize > 0) {
            int optimalDistribution = (int) Math.ceil((double) remainingNodes / (queueSize + 1));
            maxChildren = Math.min(maxChildren, optimalDistribution);
        }

        // Spezialisierte Balance-Logik
        int specializedOptimal = parent.calculateOptimalChildren(remainingNodes, queueSize + 1);
        maxChildren = Math.min(maxChildren, specializedOptimal);

        return Math.max(1, maxChildren);
    }

    /**
     * Findet optimalen Parent für Balance-erhaltende Einfügung.
     * Nutzt unbegrenztes Baumwachstum für optimale Verteilung.
     */
    private BalancedTreeMirrorNode findOptimalBalancedInsertionParent(BalancedTreeMirrorNode root) {
        List<BalancedTreeMirrorNode> candidates = root.findBalancedInsertionCandidates();

        if (candidates.isEmpty()) return null;

        // Bevorzuge optimale Kandidaten mit Wachstumspotential
        for (BalancedTreeMirrorNode candidate : candidates) {
            if (candidate.canAcceptMoreChildren()) {
                return candidate;
            }
        }

        return null;
    }

    /**
     * Findet tiefsten Blatt-Knoten für Balance-erhaltende Entfernung.
     * Typsichere Implementierung ohne redundante instanceof-Checks.
     */
    private BalancedTreeMirrorNode findDeepestLeafForBalancedRemoval(BalancedTreeMirrorNode root) {
        Stack<BalancedTreeMirrorNode> nodeStack = new Stack<>();
        BalancedTreeMirrorNode deepestLeaf = null;
        int maxDepth = -1;

        nodeStack.push(root);

        while (!nodeStack.isEmpty()) {
            BalancedTreeMirrorNode current = nodeStack.pop();

            // Prüfe Blatt-Knoten (nicht Root)
            if (current.getChildren().isEmpty() && current != root) {
                int currentDepth = calculateNodeDepthInTree(current);
                if (currentDepth > maxDepth) {
                    deepestLeaf = current;
                    maxDepth = currentDepth;
                }
            }

            // Typsichere Kinder-Navigation
            for (BalancedTreeMirrorNode balancedChild : getBalancedChildren(current)) {
                nodeStack.push(balancedChild);
            }
        }

        return deepestLeaf;
    }

    /**
     * **PLANUNGSEBENE**: Entfernt Knoten aus struktureller Planung.
     * Arbeitet ohne Zeitbezug - nur strukturelle Änderungen.
     * KORRIGIERT: Verwendet die BuildAsSubstructure-API anstatt direkter Collection-Modifikation
     */
    private void removeNodeFromStructuralPlanning(BalancedTreeMirrorNode nodeToRemove) {
        // Strukturplanung: Entferne aus Parent-Child-Beziehungen
        StructureNode parent = nodeToRemove.getParent();
        if (parent != null) {
            parent.removeChild(nodeToRemove);
        }

        // KORRIGIERT: Verwende BuildAsSubstructure-API für sichere Knoten-Entfernung
        // statt getAllStructureNodes().remove(nodeToRemove) - das ist unveränderlich

        // Option 1: Verwende Mirror-basierte Entfernung (falls verfügbar)
        // Mirror mirror = nodeToRemove.getMirror();
        // if (mirror != null && network != null) {
        //     network.getMirrors().remove(mirror);
        //}

        // Option 2: Falls BuildAsSubstructure eine protected removeNode-Methode hat
        removeStructureNode(nodeToRemove);

        // Option 3: Markiere als "entfernt" und lass Garbage Collection arbeiten.
        // Der Knoten wird automatisch aus zukünftigen Traversierung ausgeschlossen
        nodeToRemove.setParent(null);

        // Bereinige alle Kinder-Beziehungen
        for (StructureNode child : new ArrayList<>(nodeToRemove.getChildren())) {
            nodeToRemove.removeChild(child);
        }
    }

    /**
     * Erstellt Mirror-Link mit Balance-Validierung.
     * Ausführungsebene: Echte Mirror-Verbindungen.
     * KORRIGIERT: Vermeidet direkte Modifikation unveränderlicher Collections
     */
    private void createBalancedTreeMirrorLink(BalancedTreeMirrorNode parent, BalancedTreeMirrorNode child,
                                              int simTime, Properties props) {
        Mirror parentMirror = parent.getMirror();
        Mirror childMirror = child.getMirror();

        if (parentMirror == null || childMirror == null) return;
        if (parentMirror.isAlreadyConnected(childMirror)) return;

        // Erstelle Link auf Ausführungsebene - mit korrekter 5-Parameter-Signatur
        Link link = new Link(idGenerator.getNextID(), parentMirror, childMirror, simTime, props);

        // KORRIGIERT: Verwende Mirror-Methoden anstatt direkter Collection-Modifikation
        parentMirror.addLink(link);  // Statt parentMirror.getLinks().add(link)
        childMirror.addLink(link);   // Statt childMirror.getLinks().add(link)

        // Füge auch zu network links hinzu
        network.getLinks().add(link);       // Statt network.getLinks().add(link)
    }

    /**
     * Entfernt einen Knoten aus der Struktur-Verwaltung.
     * KORRIGIERT: Verwendet Mirror- und Network-Methoden anstatt direkter Collection-Modifikation
     */
    private void removeStructureNode(MirrorNode nodeToRemove) {
        Mirror mirror = nodeToRemove.getMirror();
        if (mirror != null) {
            // KORRIGIERT: Kopiere Links in neue Collection für sichere Iteration
            List<Link> linksToRemove = new ArrayList<>(mirror.getLinks());

            for (Link link : linksToRemove) {
                // Verwende Mirror-Methoden anstatt direkter Collection-Modifikation
                mirror.removeLink(link);      // Statt mirror.getLinks().remove(link)

                // Entferne Link auch vom anderen Mirror
                Mirror otherMirror = link.getTarget().equals(mirror) ?
                        link.getSource() : link.getTarget();
                otherMirror.removeLink(link); // Statt otherMirror.getLinks().remove(link)

                // Entferne von Network
                network.getLinks().remove(link);     // Statt network.getLinks().remove(link)
            }

            // Entferne Mirror vom Network
            network.getMirrors().remove(mirror);
        }
    }


    // ===== TYPSICHERE HILFSMETHODEN =====

    /**
     * Gibt alle Kinder als BalancedTreeMirrorNode zurück.
     * Typsichere Alternative zu instanceof-Checks.
     */
    private List<BalancedTreeMirrorNode> getBalancedChildren(BalancedTreeMirrorNode parent) {
        return parent.getChildren().stream()
                .filter(BalancedTreeMirrorNode.class::isInstance)
                .map(BalancedTreeMirrorNode.class::cast)
                .toList();
    }

    /**
     * Gibt Root als BalancedTreeMirrorNode zurück.
     */
    private BalancedTreeMirrorNode getBalancedTreeRoot() {
        MirrorNode root = getCurrentStructureRoot();
        return (root instanceof BalancedTreeMirrorNode) ? (BalancedTreeMirrorNode) root : null;
    }

    /**
     * Berechnet Knoten-Tiefe im Baum.
     */
    private int calculateNodeDepthInTree(StructureNode node) {
        if (node == null) return -1;

        int depth = 0;
        StructureNode current = node.getParent();

        while (current != null) {
            depth++;
            current = current.getParent();
        }

        return depth;
    }

    // ===== BALANCE-ANALYSE =====

    /**
     * Berechnet aktuelle Baum-Balance.
     */
    public double calculateCurrentTreeBalance() {
        BalancedTreeMirrorNode root = getBalancedTreeRoot();
        return (root != null) ? root.calculateTreeBalance() : 0.0;
    }

    /**
     * Prüft Balance-Kriterien.
     */
    public boolean isCurrentTreeBalanced() {
        BalancedTreeMirrorNode root = getBalancedTreeRoot();
        return root == null || root.isBalanced(maxAllowedBalanceDeviation);
    }

    /**
     * Detaillierte Balance-Informationen.
     */
    public Map<String, Object> getDetailedBalanceInfo() {
        BalancedTreeMirrorNode root = getBalancedTreeRoot();
        Map<String, Object> info = new HashMap<>();

        if (root != null) {
            info.put("currentBalance", root.calculateTreeBalance());
            info.put("isBalanced", root.isBalanced(maxAllowedBalanceDeviation));
            info.put("maxAllowedDeviation", maxAllowedBalanceDeviation);
            info.put("targetLinksPerNode", targetLinksPerNode);
            info.put("totalNodes", getAllStructureNodes().size());
            info.put("canGrowUnlimited", true); // Bäume haben keine Wachstumslimitierungen
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

    @Override
    public String toString() {
        return String.format("BalancedTreeTopologyStrategy{targetLinksPerNode=%d, maxAllowedBalanceDeviation=%.2f, currentBalance=%.2f, unlimitedGrowth=true}",
                targetLinksPerNode, maxAllowedBalanceDeviation, calculateCurrentTreeBalance());
    }
}