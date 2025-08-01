package org.lrdm.topologies.strategies;

import org.lrdm.topologies.node.*;
import org.lrdm.Mirror;
import org.lrdm.Network;

import java.util.*;

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
        this.maxAllowedBalanceDeviation = Math.max(0.1, maxAllowedBalanceDeviation);
    }

    // ===== ÜBERSCHREIBUNG DER BUILD-AS-SUBSTRUCTURE-METHODEN =====

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
        if (totalNodes <= 0) return null;

        // Erstelle Root-Node als BalancedTreeMirrorNode
        BalancedTreeMirrorNode root = getNodeFromIterator();
        if (root == null) return null;

        setCurrentStructureRoot(root);
        List<BalancedTreeMirrorNode> remainingNodes = new ArrayList<>();

        // Erstelle alle weiteren Knoten
        for (int i = 1; i < totalNodes; i++) {
            BalancedTreeMirrorNode node = getNodeFromIterator();
            if (node != null) {
                remainingNodes.add(node);
            }
        }

        // Baue balancierte Struktur mit Breadth-First-Ansatz
        root.setHead(StructureNode.StructureType.BALANCED_TREE,true);
        buildBalancedTreeStructureOnly(root, remainingNodes);

        return root;
    }

    /**
     * **PLANUNGSEBENE**: Fügt neue Knoten zur balancierten Struktur hinzu.
     * Überschreibt TreeTopologyStrategy für Balance-spezifische Einfügung.
     * Verwendet Breadth-First-Einfügung für optimale Balance-Erhaltung.
     *
     * @param nodesToAdd Set der hinzuzufügenden Mirrors
     * @return Tatsächliche Anzahl der hinzugefügten Knoten
     */
    @Override
    protected int addNodesToStructure(Set<Mirror> nodesToAdd) {
        if (nodesToAdd == null || nodesToAdd.isEmpty()) return 0;

        MirrorNode currentRoot = getCurrentStructureRoot();
        if (!(currentRoot instanceof BalancedTreeMirrorNode)) {
            return 0;
        }

        int addedCount = 0;
        StructureNode.StructureType typeId = currentRoot.deriveTypeId();

        // Sammle alle verfügbaren Einfüge-Punkte (Balance-optimiert)
        List<BalancedTreeMirrorNode> insertionCandidates = findBalancedInsertionCandidates((BalancedTreeMirrorNode) currentRoot);

        for (Mirror mirror : nodesToAdd) {
            if (insertionCandidates.isEmpty()) break;

            // Erstelle neuen BalancedTreeMirrorNode
            BalancedTreeMirrorNode newNode = new BalancedTreeMirrorNode(
                    mirror.getID(), mirror, targetLinksPerNode, maxAllowedBalanceDeviation);

            // Finde besten Einfüge-Punkt basierend auf Balance
            BalancedTreeMirrorNode bestParent = insertionCandidates.get(0);

            // Verbinde auf StructureNode-Ebene
            bestParent.addChild(newNode, Set.of(typeId), Map.of(typeId, bestParent.getId()));

            // Füge zu strukturNodes hinzu
            addToStructureNodes(newNode);
            addedCount++;

            // Aktualisiere Kandidatenliste
            insertionCandidates = findBalancedInsertionCandidates((BalancedTreeMirrorNode) currentRoot);
        }

        return addedCount;
    }

    /**
     * **PLANUNGSEBENE**: Entfernt Knoten aus der balancierten Baum-Struktur.
     * Überschreibt TreeTopologyStrategy für Balance-spezifische Entfernung.
     * Entfernt Knoten basierend auf Balance-Impact-Minimierung.
     *
     * @param nodesToRemove Anzahl der zu entfernenden Knoten
     * @return Set der tatsächlich entfernten Knoten
     */
    @Override
    protected Set<MirrorNode> removeNodesFromStructure(int nodesToRemove) {
        Set<MirrorNode> removedNodes = new HashSet<>();
        if (nodesToRemove <= 0) return removedNodes;

        MirrorNode currentRoot = getCurrentStructureRoot();
        if (!(currentRoot instanceof BalancedTreeMirrorNode balancedRoot)) {
            return removedNodes;
        }

        StructureNode.StructureType typeId = currentRoot.deriveTypeId();

        for (int i = 0; i < nodesToRemove; i++) {
            // Finde Balance-optimierten Entfernungskandidaten
            List<BalancedTreeMirrorNode> removalCandidates = findBalancedRemovalCandidates(balancedRoot);

            if (removalCandidates.isEmpty()) break;

            BalancedTreeMirrorNode nodeToRemove = removalCandidates.get(0);

            // Sammle Kinder vor Entfernung
            Set<StructureNode> children = new HashSet<>(nodeToRemove.getChildren(typeId));
            StructureNode parent = nodeToRemove.getParent();

            if (parent != null) {
                // Entferne Knoten aus Parent
                parent.removeChild(nodeToRemove, Set.of(typeId));

                // Verbinde Kinder mit Parent (Balance-erhaltend)
                for (StructureNode child : children) {
                    parent.addChild(child, Set.of(typeId), Map.of(typeId, parent.getId()));
                }
            }

            // Entferne aus strukturNodes
            removeFromStructureNodes(nodeToRemove);
            removedNodes.add(nodeToRemove);
        }

        return removedNodes;
    }

    // ===== BALANCE-SPEZIFISCHE HILFSMETHODEN =====

    /**
     * Baut die balancierte Baum-Struktur mit Breadth-First-Ansatz auf.
     * NUR StructureNode-Ebene - keine Mirror-Links!
     *
     * @param root Root-Node der Struktur
     * @param remainingNodes Liste der noch zu verbindenden Knoten
     */
    private void buildBalancedTreeStructureOnly(BalancedTreeMirrorNode root, List<BalancedTreeMirrorNode> remainingNodes) {
        if (remainingNodes.isEmpty()) return;

        StructureNode.StructureType typeId = root.deriveTypeId();
        Queue<BalancedTreeMirrorNode> queue = new LinkedList<>();
        queue.offer(root);

        Iterator<BalancedTreeMirrorNode> nodeIterator = remainingNodes.iterator();

        // Breadth-First-Aufbau für optimale Balance
        while (!queue.isEmpty() && nodeIterator.hasNext()) {
            BalancedTreeMirrorNode current = queue.poll();

            // Füge Kinder bis zur Kapazität hinzu
            int currentChildren = current.getChildren(typeId).size();
            int maxChildren = Math.min(targetLinksPerNode,
                    targetLinksPerNode - currentChildren);

            for (int i = 0; i < maxChildren && nodeIterator.hasNext(); i++) {
                BalancedTreeMirrorNode child = nodeIterator.next();
                current.addChild(child);
                queue.offer(child); // Für nächste Ebene
            }
        }
    }

    /**
     * Findet Balance-optimierte Einfüge-Punkte in der bestehenden Struktur.
     *
     * @param root Root der Struktur
     * @return Sortierte Liste der besten Einfüge-Punkte
     */
    private List<BalancedTreeMirrorNode> findBalancedInsertionCandidates(BalancedTreeMirrorNode root) {
        List<BalancedTreeMirrorNode> candidates = new ArrayList<>();
        StructureNode.StructureType typeId = root.deriveTypeId();
        Queue<BalancedTreeMirrorNode> queue = new LinkedList<>();
        queue.offer(root);

        // Breadth-First-Sammlung verfügbarer Kandidaten
        while (!queue.isEmpty()) {
            BalancedTreeMirrorNode current = queue.poll();

            // Prüfe, ob die Knoten weitere Kinder akzeptieren können
            if (current.canAcceptMoreChildren()) {
                candidates.add(current);
            }

            // Füge Kinder zur Queue hinzu
            for (StructureNode child : current.getChildren(typeId)) {
                if (child instanceof BalancedTreeMirrorNode balancedChild) {
                    queue.offer(balancedChild);
                }
            }
        }

        // Sortiere nach Balance-Kriterien
        candidates.sort(this::compareInsertionCandidates);
        return candidates;
    }

    /**
     * Findet Balance-optimierte Entfernungskandidaten.
     *
     * @param root Root der Struktur
     * @return Sortierte Liste der besten Entfernungskandidaten
     */
    private List<BalancedTreeMirrorNode> findBalancedRemovalCandidates(BalancedTreeMirrorNode root) {
        List<BalancedTreeMirrorNode> candidates = new ArrayList<>();
        StructureNode.StructureType typeId = root.deriveTypeId();
        Set<StructureNode> allNodes = root.getAllNodesInStructure(typeId, root);

        for (StructureNode node : allNodes) {
            if (node != root && node instanceof BalancedTreeMirrorNode balancedNode) {
                if (balancedNode.canBeRemovedFromStructure(root)) {
                    candidates.add(balancedNode);
                }
            }
        }

        // Sortiere nach Balance-Impact
        candidates.sort(this::compareRemovalCandidates);
        return candidates;
    }

    /**
     * Vergleicht Einfüge-Kandidaten für Balance-Optimierung.
     */
    private int compareInsertionCandidates(BalancedTreeMirrorNode candidate1, BalancedTreeMirrorNode candidate2) {
        StructureNode.StructureType typeId = candidate1.deriveTypeId();

        // Bevorzuge flachere Ebenen (Breadth-First)
        int depth1 = candidate1.getDepthInTree();
        int depth2 = candidate2.getDepthInTree();
        int depthCompare = Integer.compare(depth1, depth2);
        if (depthCompare != 0) return depthCompare;

        // Bei gleicher Tiefe: Bevorzuge Knoten mit weniger Kindern
        int children1 = candidate1.getChildren(typeId).size();
        int children2 = candidate2.getChildren(typeId).size();
        int childrenCompare = Integer.compare(children1, children2);
        if (childrenCompare != 0) return childrenCompare;

        // Bei gleicher Kinderanzahl: Bevorzuge niedrigere IDs (deterministische Auswahl)
        return Integer.compare(candidate1.getId(), candidate2.getId());
    }

    /**
     * Vergleicht Entfernungskandidaten für Balance-Optimierung.
     */
    private int compareRemovalCandidates(BalancedTreeMirrorNode candidate1, BalancedTreeMirrorNode candidate2) {
        StructureNode.StructureType typeId = candidate1.deriveTypeId();

        // Bevorzuge Blätter (weniger Kinder)
        int children1 = candidate1.getChildren(typeId).size();
        int children2 = candidate2.getChildren(typeId).size();
        int childrenCompare = Integer.compare(children1, children2);
        if (childrenCompare != 0) return childrenCompare;

        // Bei gleicher Kinderanzahl: Bevorzuge tiefere Knoten
        int depth1 = candidate1.getDepthInTree();
        int depth2 = candidate2.getDepthInTree();
        int depthCompare = Integer.compare(depth2, depth1); // Umgekehrte Reihenfolge
        if (depthCompare != 0) return depthCompare;

        // Bei gleicher Tiefe: Bevorzuge höhere IDs (deterministische Auswahl)
        return Integer.compare(candidate2.getId(), candidate1.getId());
    }

    /**
     * Erstellt einen neuen BalancedTreeMirrorNode aus dem Mirror-Iterator.
     *
     * @return Neuer BalancedTreeMirrorNode oder null, wenn keine Mirrors verfügbar sind
     */
    protected BalancedTreeMirrorNode getNodeFromIterator() {
        if (mirrorIterator != null && mirrorIterator.hasNext()) {
            Mirror mirror = getNextMirror();
            MirrorNode node = createMirrorNodeForMirror(mirror);
            if (node != null) {
                node.addNodeType(StructureNode.StructureType.MIRROR);
                node.addNodeType(StructureNode.StructureType.TREE);
                node.addNodeType(StructureNode.StructureType.BALANCED_TREE);
                node.setMirror(mirror);
                addToStructureNodes(node); // Aktiv hinzufügen
            }
            return (BalancedTreeMirrorNode) node;
        }
        return null;
    }

    /**
     * Factory-Methode für baum-spezifische MirrorNode-Erstellung.
     * Überschreibt BuildAsSubstructure für die TreeMirrorNode-Erstellung.
     *
     * @param mirror Der Mirror, für den ein MirrorNode erstellt werden soll
     * @return Neuer TreeMirrorNode
     */
    @Override
    protected MirrorNode createMirrorNodeForMirror(Mirror mirror) {
        return new BalancedTreeMirrorNode(mirror.getID(), mirror, targetLinksPerNode, maxAllowedBalanceDeviation);
    }

    // ===== GETTER UND SETTER =====

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
        this.maxAllowedBalanceDeviation = Math.max(0.1, maxAllowedBalanceDeviation);
    }

    @Override
    public String toString() {
        return String.format("BalancedTreeTopologyStrategy[targetLinks=%d, maxDev=%.2f]",
                targetLinksPerNode, maxAllowedBalanceDeviation);
    }
}