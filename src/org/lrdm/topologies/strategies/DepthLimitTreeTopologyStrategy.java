
package org.lrdm.topologies.strategies;

import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.topologies.node.*;
import org.lrdm.topologies.node.StructureNode.StructureType;

import java.util.*;

/**
 * Eine spezialisierte {@link TreeTopologyStrategy}, die Mirrors als tiefenbeschränkten Baum mit einer
 * einzelnen Root verknüpft. Diese Strategie ist eine Portierung der {@link org.lrdm.topologies.builders.TreeBuilderDepthLimit} Klasse.
 * <p>
 * **Tiefenbeschränkter Depth-First-Ansatz**:
 * - Bevorzugt Wachstum in die Tiefe vor der Breite
 * - Respektiert eine konfigurierbare maximale Tiefe
 * - Fügt Knoten basierend auf der geringsten Anzahl von Kindern in erlaubter Tiefe ein
 * - Verwendet {@link DepthLimitedTreeMirrorNode} für spezifische Depth-First-Funktionalität
 * <p>
 * **Planungsebene vs. Ausführungsebene**:
 * - Planungsebene: `removeNodesFromStructure()` - plant strukturelle Änderungen ohne Zeitbezug
 * - Ausführungsebene: `handleRemoveMirrors()` - führt Mirror-Shutdown innerhalb der Planungsgrenzen aus
 * - Automatisches Mitwachsen: MirrorNode-Ebene passt sich an StructureNode-Planung an
 * <p>
 * **Tiefenbeschränkung**: Im Gegensatz zu unbegrenztem Baumwachstum werden Knoten nur bis zur
 * maximalen Tiefe hinzugefügt, was strukturelle Limitierungen einführt.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class DepthLimitTreeTopologyStrategy extends TreeTopologyStrategy {

    // ===== TIEFENBESCHRÄNKUNGS-SPEZIFISCHE KONFIGURATION =====
    private int maxDepth = 3;
    private boolean preferDepthOverBreadth = true;

    // ===== KONSTRUKTOREN =====

    public DepthLimitTreeTopologyStrategy() {
        super();
    }

    public DepthLimitTreeTopologyStrategy(int maxDepth) {
        super();
        this.maxDepth = Math.max(1, maxDepth);
    }

    public DepthLimitTreeTopologyStrategy(int maxDepth, boolean preferDepthOverBreadth) {
        super();
        this.maxDepth = Math.max(1, maxDepth);
        this.preferDepthOverBreadth = preferDepthOverBreadth;
    }

    // ===== ÜBERSCHREIBUNG DER BUILD-AS-SUBSTRUCTURE-METHODEN =====

    /**
     * **PLANUNGSEBENE**: Erstellt die tiefenbeschränkte Baum-Struktur mit Depth-First-Priorität.
     * Überschreibt BuildAsSubstructure für Tiefenbeschränkungs-spezifische Logik.
     * Portiert die Logik aus TreeBuilderDepthLimit.
     */
    @Override
    protected MirrorNode buildStructure(int totalNodes, int simTime, Properties props) {
        if (totalNodes <= 0 || !mirrorIterator.hasNext()) return null;

        // Erstelle Root mit Tiefenbeschränkung - verwendet globales network-Objekt
        Mirror rootMirror = mirrorIterator.next();
        DepthLimitedTreeMirrorNode root = new DepthLimitedTreeMirrorNode(rootMirror.getID(), rootMirror, maxDepth);

        // Strukturplanung: Depth-First für tiefenbeschränktes Wachstum
        if (totalNodes > 1) {
            buildDepthLimitedTreeDepthFirst(root, totalNodes - 1, simTime, props);
        }

        return root;
    }

    /**
     * **PLANUNGSEBENE**: Fügt neue Knoten zur tiefenbeschränkten Struktur hinzu.
     * Überschreibt BuildAsSubstructure für Depth-First-optimierte Einfügung.
     * Portiert die addNodesDepthFirstBalanced-Logik aus TreeBuilderDepthLimit.
     */
    @Override
    protected int addNodesToStructure(int nodesToAdd) {
        if (nodesToAdd <= 0) return 0;

        DepthLimitedTreeMirrorNode root = getDepthLimitedTreeRoot();
        if (root == null) return 0;

        int actuallyAdded = 0;

        // Depth-First-Wachstum mit Tiefenbeschränkung
        for (int i = 0; i < nodesToAdd && mirrorIterator.hasNext(); i++) {
            DepthLimitedTreeMirrorNode bestInsertionPoint = findBestDepthFirstInsertionPoint(root);
            if (bestInsertionPoint == null) break;

            // Strukturplanung: Erstelle Knoten mit Tiefenbeschränkung (ohne simTime)
            DepthLimitedTreeMirrorNode newNode = createDepthLimitedTreeNode(bestInsertionPoint, 0, new Properties());
            if (newNode != null) {
                actuallyAdded++;
                // Füge zur Strukturverwaltung hinzu
                this.addToStructureNodes(newNode);
            }
        }

        return actuallyAdded;
    }

    /**
     * **PLANUNGSEBENE**: Entfernt Knoten aus der Struktur-Planung.
     * Überschreibt BuildAsSubstructure für tiefenbeschränkungserhaltende Entfernung.
     * Bevorzugte Entfernung von Blatt-Knoten in der maximalen Tiefe.
     */
    @Override
    protected int removeNodesFromStructure(int nodesToRemove) {
        if (nodesToRemove <= 0) return 0;

        DepthLimitedTreeMirrorNode root = getDepthLimitedTreeRoot();
        if (root == null) return 0;

        int actuallyRemoved = 0;

        // Planungsebene: Entferne bevorzugt von maximaler Tiefe
        for (int i = 0; i < nodesToRemove; i++) {
            DepthLimitedTreeMirrorNode leafToRemove = findDeepestLeafForDepthLimitedRemoval(root);
            if (leafToRemove == null || leafToRemove == root) break;

            // Strukturplanung: Entferne aus Parent-Child-Hierarchie
            removeNodeFromDepthLimitedStructuralPlanning(leafToRemove);
            actuallyRemoved++;
        }

        return actuallyRemoved;
    }

    /**
     * **AUSFÜHRUNGSEBENE**: Überschreibt die Mirror-Entfernung für Tiefenbeschränkungs-Erhaltung.
     * Führt Mirror-Shutdown innerhalb der strukturellen Planungsgrenzen aus.
     * Arbeitet komplementär zu removeNodesFromStructure.
     */
    @Override
    public void handleRemoveMirrors(Network n, int removeMirrors, Properties props, int simTime) {
        if (removeMirrors <= 0) return;

        DepthLimitedTreeMirrorNode root = getDepthLimitedTreeRoot();
        if (root == null) {
            // Fallback: Standard-Verhalten, wenn keine Struktur vorhanden ist
            super.handleRemoveMirrors(n, removeMirrors, props, simTime);
            return;
        }

        int actuallyRemoved = 0;

        // Ausführungsebene: Tiefenbeschränkungs-bewusste Mirror-Entfernung
        for (int i = 0; i < removeMirrors; i++) {
            DepthLimitedTreeMirrorNode targetNode = findDeepestLeafForDepthLimitedRemoval(root);
            if (targetNode == null || targetNode == root) break;

            Mirror targetMirror = targetNode.getMirror();
            if (targetMirror != null) {
                // Mirror-Shutdown auf Ausführungsebene
                targetMirror.shutdown(simTime);
                actuallyRemoved++;
            }
        }

        // Synchronisiere Plannings- und Ausführungsebene
        removeNodesFromStructure(actuallyRemoved);
    }

    // ===== TIEFENBESCHRÄNKUNGS-SPEZIFISCHE HILFSMETHODEN =====

    /**
     * Baut die tiefenbeschränkte Baum-Struktur mit Depth-First-Ansatz auf.
     * Portiert die Logik aus TreeBuilderDepthLimit.buildTree().
     */
    private void buildDepthLimitedTreeDepthFirst(DepthLimitedTreeMirrorNode root, int remainingNodes,
                                                 int simTime, Properties props) {
        int nodesAdded = 0;

        while (nodesAdded < remainingNodes && mirrorIterator.hasNext()) {
            // Portiert findBestInsertionPoint aus TreeBuilderDepthLimit
            DepthLimitedTreeMirrorNode bestInsertionPoint = findBestDepthFirstInsertionPoint(root);

            if (bestInsertionPoint == null) break; // Keine gültigen Einfügepunkte mehr

            DepthLimitedTreeMirrorNode newChild = createDepthLimitedTreeNode(bestInsertionPoint, simTime, props);
            if (newChild != null) {
                nodesAdded++;
            } else {
                break; // Keine Mirrors sind mehr verfügbar
            }
        }
    }

    /**
     * Erstellt einen neuen tiefenbeschränkten Baum-Knoten mit struktureller Planung.
     * Portiert die Knoten-Erstellung aus TreeBuilderDepthLimit.
     */
    private DepthLimitedTreeMirrorNode createDepthLimitedTreeNode(DepthLimitedTreeMirrorNode parent,
                                                                  int simTime, Properties props) {
        if (!mirrorIterator.hasNext()) return null;

        // Strukturplanung: Erstelle Knoten mit Tiefenbeschränkung
        Mirror childMirror = mirrorIterator.next();
        DepthLimitedTreeMirrorNode child = new DepthLimitedTreeMirrorNode(childMirror.getID(), childMirror, maxDepth);

        // Planungsebene: StructureNode-Verbindung
        Set<StructureType> typeIds = new HashSet<>();
        typeIds.add(StructureType.DEPTH_LIMIT_TREE);

        Map<StructureType, Integer> headIds = new HashMap<>();
        headIds.put(StructureType.DEPTH_LIMIT_TREE, getRootId());

        parent.addChild(child, typeIds, headIds);

        // Ausführungsebene: Mirror-Link, nur wenn beide Mirrors gültig sind
        if (parent.getMirror() != null && child.getMirror() != null) {
            createDepthLimitedTreeMirrorLink(parent, child, simTime, props);
        }

        return child;
    }

    /**
     * Findet den besten Einfügepunkt für Depth-First-Wachstum.
     * Portiert die findBestInsertionPoint-Logik aus TreeBuilderDepthLimit.
     */
    private DepthLimitedTreeMirrorNode findBestDepthFirstInsertionPoint(DepthLimitedTreeMirrorNode root) {
        List<DepthLimitedTreeMirrorNode> candidates = new ArrayList<>();
        Stack<DepthLimitedTreeMirrorNode> stack = new Stack<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            DepthLimitedTreeMirrorNode current = stack.pop();

            // Nur Knoten innerhalb der Tiefenbeschränkung (portiert aus TreeBuilderDepthLimit)
            if (current.canAddChildren()) {
                candidates.add(current);
            }

            // Füge Kinder zum Stack hinzu (Depth-First, umgekehrte Reihenfolge)
            List<DepthLimitedTreeMirrorNode> children = getDepthLimitedChildren(current);
            Collections.reverse(children);
            for (DepthLimitedTreeMirrorNode child : children) {
                stack.push(child);
            }
        }

        if (candidates.isEmpty()) return null;

        // Sortiere nach: 1. Tiefe (tiefer zuerst), 2. Anzahl Kinder (weniger zuerst)
        // portiert aus TreeBuilderDepthLimit
        candidates.sort(Comparator
                .comparingInt(DepthLimitedTreeMirrorNode::getDepthInTree).reversed() // Tiefere zuerst
                .thenComparingInt(node -> getDepthLimitedChildren(node).size())); // Weniger Kinder zuerst

        return candidates.get(0);
    }

    /**
     * Findet den tiefsten Blatt-Knoten für tiefenbeschränkungserhaltende Entfernung.
     * Bevorzugt Knoten in der maximalen Tiefe.
     */
    private DepthLimitedTreeMirrorNode findDeepestLeafForDepthLimitedRemoval(DepthLimitedTreeMirrorNode root) {
        Stack<DepthLimitedTreeMirrorNode> nodeStack = new Stack<>();
        DepthLimitedTreeMirrorNode deepestLeaf = null;
        int maxDepthFound = -1;

        nodeStack.push(root);

        while (!nodeStack.isEmpty()) {
            DepthLimitedTreeMirrorNode current = nodeStack.pop();

            // Prüfe Blatt-Knoten (nicht Root)
            if (getDepthLimitedChildren(current).isEmpty() && current != root) {
                int currentDepth = current.getDepthInTree();
                if (currentDepth > maxDepthFound) {
                    deepestLeaf = current;
                    maxDepthFound = currentDepth;
                }
            }

            // Füge Kinder hinzu
            for (DepthLimitedTreeMirrorNode child : getDepthLimitedChildren(current)) {
                nodeStack.push(child);
            }
        }

        return deepestLeaf;
    }

    /**
     * **PLANUNGSEBENE**: Entfernt Knoten aus struktureller Planung.
     * Arbeitet ohne Zeitbezug - nur strukturelle Änderungen.
     */
    private void removeNodeFromDepthLimitedStructuralPlanning(DepthLimitedTreeMirrorNode nodeToRemove) {
        // Strukturplanung: Entferne aus Parent-Child-Beziehungen
        StructureNode parent = nodeToRemove.getParent();
        if (parent != null) {
            parent.removeChild(nodeToRemove);
        }

        // Bereinige alle Kinder-Beziehungen
        List<StructureNode> children = new ArrayList<>(nodeToRemove.getChildren());
        for (StructureNode child : children) {
            nodeToRemove.removeChild(child);
        }

        // Markiere als entfernt
        nodeToRemove.setParent(null);
    }

    /**
     * Erstellt Mirror-Link mit Tiefenbeschränkungs-Validierung.
     * Ausführungsebene: Echte Mirror-Verbindungen.
     */
    private void createDepthLimitedTreeMirrorLink(DepthLimitedTreeMirrorNode parent,
                                                  DepthLimitedTreeMirrorNode child,
                                                  int simTime, Properties props) {
        Mirror parentMirror = parent.getMirror();
        Mirror childMirror = child.getMirror();

        if (parentMirror == null || childMirror == null) return;
        if (isAlreadyConnected(parentMirror, childMirror)) return;

        // Erstelle Link auf Ausführungsebene
        Link link = new Link(idGenerator.getNextID(), parentMirror, childMirror, simTime, props);

        // Füge Links zu Mirrors hinzu
        parentMirror.addLink(link);
        childMirror.addLink(link);

        // Füge auch zu network links hinzu
        if (network != null) {
            network.getLinks().add(link);
        }
    }

    /**
     * Prüft bestehende Mirror-Verbindungen.
     */
    private boolean isAlreadyConnected(Mirror mirror1, Mirror mirror2) {
        return mirror1.getLinks().stream()
                .anyMatch(link ->
                        (link.getTarget().equals(mirror2) && link.getSource().equals(mirror1)) ||
                                (link.getTarget().equals(mirror1) && link.getSource().equals(mirror2)));
    }

    // ===== TYPSICHERE HILFSMETHODEN =====

    /**
     * Gibt alle Kinder als DepthLimitedTreeMirrorNode zurück.
     * Typsichere Alternative zu instanceof-Checks.
     */
    private List<DepthLimitedTreeMirrorNode> getDepthLimitedChildren(DepthLimitedTreeMirrorNode parent) {
        return parent.getChildren().stream()
                .filter(DepthLimitedTreeMirrorNode.class::isInstance)
                .map(DepthLimitedTreeMirrorNode.class::cast)
                .toList();
    }

    /**
     * Gibt Root als DepthLimitedTreeMirrorNode zurück.
     */
    private DepthLimitedTreeMirrorNode getDepthLimitedTreeRoot() {
        MirrorNode root = getCurrentStructureRoot();
        return (root instanceof DepthLimitedTreeMirrorNode) ? (DepthLimitedTreeMirrorNode) root : null;
    }

    /**
     * Gibt die Root-ID zurück.
     */
    private int getRootId() {
        MirrorNode root = getCurrentStructureRoot();
        return (root != null) ? root.getId() : -1;
    }

    // ===== TIEFENBESCHRÄNKUNGS-ANALYSE =====

    /**
     * Berechnet die aktuelle Tiefenauslastung des Baums.
     * Portiert die calculateDepthUtilization-Logik aus DepthLimitedTreeMirrorNode.
     */
    public double calculateCurrentDepthUtilization() {
        DepthLimitedTreeMirrorNode root = getDepthLimitedTreeRoot();
        return (root != null) ? root.calculateDepthUtilization() : 0.0;
    }

    /**
     * Prüft, ob die Tiefenbeschränkung eingehalten wird.
     */
    public boolean isDepthLimitRespected() {
        DepthLimitedTreeMirrorNode root = getDepthLimitedTreeRoot();
        return root == null || root.validateDepthConstraints();
    }

    /**
     * Gibt alle Knoten auf der maximalen Tiefe zurück.
     */
    public List<DepthLimitedTreeMirrorNode> getNodesAtMaxDepth() {
        DepthLimitedTreeMirrorNode root = getDepthLimitedTreeRoot();
        return (root != null) ? root.getNodesAtMaxDepth() : new ArrayList<>();
    }

    /**
     * Berechnet die Anzahl verfügbarer Einfügepunkte pro Tiefe.
     */
    public Map<Integer, Integer> getInsertionPointsByDepth() {
        DepthLimitedTreeMirrorNode root = getDepthLimitedTreeRoot();
        return (root != null) ? root.getInsertionPointsByDepth() : new HashMap<>();
    }

    /**
     * Detaillierte Tiefenbeschränkungs-Informationen.
     */
    public Map<String, Object> getDetailedDepthLimitInfo() {
        DepthLimitedTreeMirrorNode root = getDepthLimitedTreeRoot();
        Map<String, Object> info = new HashMap<>();

        if (root != null) {
            info.put("maxDepth", maxDepth);
            info.put("currentDepthUtilization", root.calculateDepthUtilization());
            info.put("isDepthLimitRespected", root.validateDepthConstraints());
            info.put("nodesAtMaxDepth", root.getNodesAtMaxDepth().size());
            info.put("insertionPointsByDepth", root.getInsertionPointsByDepth());
            info.put("preferDepthOverBreadth", preferDepthOverBreadth);
            info.put("totalNodes", getAllStructureNodes().size());
            info.put("hasDepthLimitation", true);
        }

        return info;
    }

    // ===== KONFIGURATION =====

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = Math.max(1, maxDepth);
    }

    public boolean isPreferDepthOverBreadth() {
        return preferDepthOverBreadth;
    }

    public void setPreferDepthOverBreadth(boolean preferDepthOverBreadth) {
        this.preferDepthOverBreadth = preferDepthOverBreadth;
    }

    @Override
    public String toString() {
        return String.format("DepthLimitTreeTopologyStrategy{maxDepth=%d, preferDepthOverBreadth=%s, " +
                        "currentDepthUtilization=%.2f, depthLimitRespected=%s}",
                maxDepth, preferDepthOverBreadth, calculateCurrentDepthUtilization(),
                isDepthLimitRespected());
    }
}