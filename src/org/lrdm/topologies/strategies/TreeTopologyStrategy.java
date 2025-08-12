
package org.lrdm.topologies.strategies;

import org.lrdm.effectors.TargetLinkChange;
import org.lrdm.topologies.node.TreeMirrorNode;
import org.lrdm.topologies.node.MirrorNode;
import org.lrdm.topologies.node.StructureNode;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.Action;
import org.lrdm.effectors.MirrorChange;
import org.lrdm.effectors.TopologyChange;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Eine TopologyStrategy, die Mirrors in einem Baum verbindet.
 * <p>
 * **Baum-Eigenschaften**:
 * - Baumstruktur mit hierarchischer Organisation (keine Zyklen)
 * - Genau eine Root (Head-Node)
 * - Jeder Knoten außer Root hat genau einen Parent
 * - n Knoten haben genau n-1 Kanten (charakteristisch für Bäume)
 * - Zusammenhängend (alle Knoten über Tree-Pfade erreichbar)
 * - Verwendet {@link TreeMirrorNode} für baumspezifische Funktionalität
 * <p>
 * **Aufbau-Strategie**:
 * - Breadth-First-Aufbau für gleichmäßige Verteilung
 * - Optimale Kinderzahl pro Knoten für balancierten Baum
 * - Automatische Root-Auswahl basierend auf der ersten verfügbaren Mirror
 * <p>
 * **Planungsebene vs. Ausführungsebene**:
 * - Planungsebene: `buildStructure()` - plant strukturelle Änderungen ohne Zeitbezug
 * - Ausführungsebene: `buildAndUpdateLinks()` - führt Mirror-Link-Erstellung zeitkritisch aus
 * - Automatisches Mitwachsen: MirrorNode-Ebene passt sich an StructureNode-Planung an
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class TreeTopologyStrategy extends BuildAsSubstructure {

    // ===== ÜBERSCHREIBUNG DER BUILD-AS-SUBSTRUCTURE-METHODEN =====

    /**
     * **PLANUNGSEBENE**: Erstellt die initiale Baum-Struktur.
     * Überschreibt BuildAsSubstructure für Baum-spezifische Struktur-Erstellung.
     * Verwendet TreeMirrorNode für baum-spezifische Funktionalität.
     * Integriert die gesamte Baum-Aufbau-Logik direkt.
     *
     * @param totalNodes Anzahl der zu erstellenden Knoten
     * @return Die Root-Node der erstellten Baum-Struktur
     */
    @Override
    protected MirrorNode buildStructure(int totalNodes) {
        if (totalNodes < 1) {
            return null;
        }

        // Lese Konfiguration aus Properties
        int childrenPerParent = network.getNumTargetLinksPerMirror() - 1;

        // 1. Erstelle Root-Node
        TreeMirrorNode root = getMirrorNodeFromIterator();
        if (root == null) return null;

        root.setHead(true);
        setCurrentStructureRoot(root);

        // 2. Erstelle restliche Knoten
        List<TreeMirrorNode> remainingNodes = new ArrayList<>();
        for (int i = 1; i < totalNodes; i++) {
            TreeMirrorNode node = getMirrorNodeFromIterator();
            if (node == null) break;
            remainingNodes.add(node);
        }

        if (remainingNodes.isEmpty()) {
            return root; // Nur Root-Node
        }

        // 3. Baue Baum-Struktur direkt mit Breadth-First-Strategie
        Queue<TreeMirrorNode> parentQueue = new LinkedList<>();
        parentQueue.offer(root);

        int nodeIndex = 0;

        while (!parentQueue.isEmpty() && nodeIndex < remainingNodes.size()) {
            TreeMirrorNode parent = parentQueue.poll();

            // Füge Kinder hinzu
            for (int i = 0; i < childrenPerParent && nodeIndex < remainingNodes.size(); i++) {
                TreeMirrorNode child = remainingNodes.get(nodeIndex);

                // Verbinde Kind mit Parent (nur Planungsebene)
                parent.addChild(child);

                // Füge zur nächsten Ebene hinzu
                parentQueue.offer(child);

                nodeIndex++;
            }
        }

        return root;
    }

    /**
     * **PLANUNGSEBENE**: Fügt neue Knoten zur bestehenden Baum-Struktur hinzu.
     * Überschreibt BuildAsSubstructure für baum-spezifische Einfügung.
     * Verwendet eine Breadth-First-Strategie für gleichmäßige Verteilung.
     *
     * @param nodesToAdd Anzahl der hinzuzufügenden Knoten
     * @return Tatsächliche Anzahl der hinzugefügten Knoten
     */
    @Override
    protected int addNodesToStructure(Set<Mirror> nodesToAdd) {
        if (nodesToAdd.isEmpty() || getCurrentStructureRoot() == null) {
            return 0;
        }

        TreeMirrorNode root = (TreeMirrorNode) getCurrentStructureRoot();
        if (root == null) return 0;

        int actuallyAdded = 0;

        // Füge neue Knoten mit Breadth-First-Strategie hinzu
        for (int i = 0; i < nodesToAdd.size(); i++) {
            // Erstelle neuen Knoten
            TreeMirrorNode newNode = getMirrorNodeFromIterator();
            if (newNode == null) break;

            // Finde optimalen Einfügepunkt
            TreeMirrorNode insertionPoint = findOptimalInsertionPoint(root);
            if (insertionPoint != null && insertionPoint.canAcceptMoreChildren()) {
                // Verbinde neuen Knoten mit Einfügepunkt (nur Planungsebene)
                insertionPoint.addChild(newNode);
                actuallyAdded++;
            } else {
                // Keine geeigneten Einfügepunkte mehr verfügbar
                break;
            }
        }

        return actuallyAdded;
    }

    /**
     * **PLANUNGSEBENE**: Entfernt Knoten aus der Baum-Struktur.
     * Überschreibt BuildAsSubstructure für baum-spezifische Entfernung.
     * Bevorzugt Blätter für die Entfernung um Baum-Struktur zu erhalten.
     *
     * @param nodesToRemove Anzahl der zu entfernenden Knoten
     * @return Anzahl der tatsächlich entfernten Knoten
     */
    @Override
    protected Set<MirrorNode> removeNodesFromStructure(int nodesToRemove) {
        if (nodesToRemove <= 0 || getCurrentStructureRoot() == null) {
            return new HashSet<>();
        }

        List<TreeMirrorNode> treeNodes = getAllTreeNodes();
        TreeMirrorNode root = (TreeMirrorNode) getCurrentStructureRoot();

        if (treeNodes.size() - nodesToRemove < 1) {
            nodesToRemove = treeNodes.size() - 1;
        }

        // Sortiere Kandidaten: Blätter zuerst, dann nach Tiefe
        List<TreeMirrorNode> candidates = treeNodes.stream()
                .filter(node -> node != root)
                .sorted((node1, node2) -> {
                    int childrenCompare = Integer.compare(node1.getChildren().size(), node2.getChildren().size());
                    if (childrenCompare != 0) return childrenCompare;
                    return Integer.compare(node2.getDepthInTree(), node1.getDepthInTree());
                })
                .toList();

        Set<MirrorNode> removedNodes = new HashSet<>();
        // Entferne die ersten N Kandidaten
        for (int i = 0; i < nodesToRemove && i < candidates.size(); i++) {
            TreeMirrorNode nodeToRemove = candidates.get(i);
            removeNodeFromStructuralPlanning(nodeToRemove,
                    Set.of(StructureNode.StructureType.DEFAULT,StructureNode.StructureType.MIRROR,StructureNode.StructureType.TREE));
            removedNodes.add(nodeToRemove);
        }

        return removedNodes;
    }

    /**
     * Validiert die aktuelle Baum-Struktur.
     * Überschreibt BuildAsSubstructure für baum-spezifische Validierung.
     *
     * @return true, wenn die Baum-Struktur gültig ist
     */
    @Override
    protected boolean validateTopology() {
        TreeMirrorNode root = (TreeMirrorNode) getCurrentStructureRoot();
        if (root == null) {
            return getAllStructureNodes().isEmpty();
        }

        return root.isValidStructure();
    }

    // ===== TOPOLOGY STRATEGY INTERFACE IMPLEMENTATION =====

    /**
     * Berechnet die erwartete Anzahl der Links für Bäume.
     * Bäume haben immer n-1 Links für n Knoten.
     *
     * @param n Das Netzwerk
     * @return Erwartete Anzahl Links (Anzahl Mirrors - 1)
     */
    @Override
    public int getNumTargetLinks(Network n) {
        return Math.max(0, n.getMirrorCursor().getNumUsableMirrors() - 1);
    }

    /**
     * Berechnet die erwartete Anzahl der Links, wenn die gegebene Aktion ausgeführt wird.
     * Behandelt verschiedene Action-Typen:
     * 1. MirrorChange: Berechnet Links für neue Mirror-Anzahl
     * 2. TargetLinkChange: Berechnet Links basierend auf neuen Links pro Mirror
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
            // Bei MirrorChange wird die NEUE GESAMTANZAHL gesetzt, nicht hinzugefügt
            int newMirrorCount = mc.getNewMirrors();

            // Bei TreeTopology: n-1 Links für n Mirrors (Baum-Eigenschaft)
            return Math.max(0, newMirrorCount - 1);
        }

        // 2. TargetLinkChange: Ändert die Links pro Mirror
        else if (a instanceof TargetLinkChange tlc) {
            // Bei TargetLinkChange werden die Links pro Mirror geändert
            int theoreticalLinks = getTheoreticalLinks(tlc, currentMirrors);
            int treeConstraintLinks = Math.max(0, currentMirrors - 1);

            // Für Bäume: Nimm das Minimum zwischen theoretischer Berechnung und Baum-Constraint
            return Math.min(theoreticalLinks, treeConstraintLinks);
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

    private static int getTheoreticalLinks(TargetLinkChange tlc, int currentMirrors) {
        int newLinksPerMirror = tlc.getNewLinksPerMirror();

        // Für Bäume ist die Anzahl Links immer n-1, unabhängig von Links pro Mirror
        // Aber wir können eine theoretische Berechnung machen:
        // Wenn jeder Mirror newLinksPerMirror Links haben soll, dann:
        // Gesamtlinks = (currentMirrors * newLinksPerMirror) / 2
        // Aber für echte Bäume ist es immer n-1

        // Baum-Constraint: Maximal n-1 Links für n Knoten
        return (currentMirrors * newLinksPerMirror) / 2;
    }

    // ===== BAUM-SPEZIFISCHE HILFSMETHODEN =====

    /**
     * Findet den optimalen Einfügepunkt für neue Knoten.
     * Verwendet Breadth-First-Strategie für gleichmäßige Verteilung.
     *
     * @param root Root-Node der Struktur
     * @return Optimaler Einfügepunkt oder null, wenn keiner verfügbar
     */
    private TreeMirrorNode findOptimalInsertionPoint(TreeMirrorNode root) {
        if (root == null) return null;

        // Breadth-First-Suche nach geeignetem Parent
        Queue<TreeMirrorNode> queue = new LinkedList<>();
        queue.offer(root);

        while (!queue.isEmpty()) {
            TreeMirrorNode current = queue.poll();

            if (current.canAcceptMoreChildren()) {
                return current;
            }

            // Füge alle Kinder zur Queue hinzu
            for (StructureNode child : current.getChildren()) {
                if (child instanceof TreeMirrorNode treeChild) {
                    queue.offer(treeChild);
                }
            }
        }

        return null;
    }

    /**
     * Gibt alle Knoten als TreeMirrorNode-Liste zurück.
     *
     * @return Liste aller TreeMirrorNodes
     */
    private List<TreeMirrorNode> getAllTreeNodes() {
        return getAllStructureNodes().stream()
                .filter(TreeMirrorNode.class::isInstance)
                .map(TreeMirrorNode.class::cast)
                .collect(Collectors.toList());
    }

    // ===== ÜBERSCHREIBUNG DER FACTORY-METHODE =====

    /**
     * Factory-Methode für baum-spezifische MirrorNode-Erstellung.
     * Überschreibt BuildAsSubstructure für die TreeMirrorNode-Erstellung.
     *
     * @param mirror Der Mirror, für den ein MirrorNode erstellt werden soll
     * @return Neuer TreeMirrorNode
     */
    @Override
    protected MirrorNode createMirrorNodeForMirror(Mirror mirror) {
        return new TreeMirrorNode(mirror.getID(), mirror);
    }

    /**
     * Erstellt einen neuen MirrorNode mit Mirror aus dem Iterator.
     * AKTUALISIERT: Fügt den Knoten automatisch zu structureNodes hinzu.
     *
     * @return Neuer MirrorNode mit zugeordnetem Mirror oder null
     */
    protected TreeMirrorNode getMirrorNodeFromIterator() {
        if (network.getMirrorCursor().hasNextMirror()) {
            TreeMirrorNode node = (TreeMirrorNode) super.getMirrorNodeFromIterator();
            node.addNodeType(StructureNode.StructureType.TREE);
            return node;
        }
        return null;
    }

    // ===== STRING REPRESENTATION =====

    @Override
    public String toString() {
        String baseString = super.toString();
        TreeMirrorNode root = (TreeMirrorNode) getCurrentStructureRoot();
        int maxDepth = root != null ? root.getMaxTreeDepth() : 0;
        int leaves = (int) getAllTreeNodes().stream().filter(TreeMirrorNode::isLeaf).count();

        return baseString + "[maxDepth=" + maxDepth + ", leaves=" + leaves + "]";
    }

}