
package org.lrdm.topologies.strategies;

import org.lrdm.effectors.TargetLinkChange;
import org.lrdm.topologies.node.TreeMirrorNode;
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
     * @param props      Properties der Simulation
     * @return Die Root-Node der erstellten Baum-Struktur
     */
    @Override
    protected MirrorNode buildStructure(int totalNodes, Properties props) {
        if (totalNodes < 1 || !hasNextMirror()) {
            return null;
        }

        // Lese Konfiguration aus Properties
        int childrenPerParent = Integer.parseInt(props.getProperty("preferredChildrenPerParent", "2"));

        // 1. Erstelle Root-Node
        TreeMirrorNode root = (TreeMirrorNode) getMirrorNodeFromIterator();
        if (root == null) return null;

        root.setHead(true);
        setCurrentStructureRoot(root);

        // 2. Erstelle restliche Knoten
        List<TreeMirrorNode> remainingNodes = new ArrayList<>();
        for (int i = 1; i < totalNodes; i++) {
            TreeMirrorNode node = (TreeMirrorNode) getMirrorNodeFromIterator();
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
     * Verwendet Breadth-First-Strategie für gleichmäßige Verteilung.
     *
     * @param nodesToAdd Anzahl der hinzuzufügenden Knoten
     * @return Tatsächliche Anzahl der hinzugefügten Knoten
     */
    @Override
    protected int addNodesToStructure(int nodesToAdd) {
        if (nodesToAdd <= 0) return 0;

        TreeMirrorNode root = (TreeMirrorNode) getCurrentStructureRoot();
        if (root == null) return 0;

        int actuallyAdded = 0;

        // Füge neue Knoten mit Breadth-First-Strategie hinzu
        for (int i = 0; i < nodesToAdd; i++) {
            // Erstelle neuen Knoten
            TreeMirrorNode newNode = (TreeMirrorNode) getMirrorNodeFromIterator();
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
    protected int removeNodesFromStructure(int nodesToRemove) {
        if (nodesToRemove <= 0) return 0;

        List<TreeMirrorNode> treeNodes = getAllTreeNodes();
        TreeMirrorNode root = (TreeMirrorNode) getCurrentStructureRoot();

        if (treeNodes.size() - nodesToRemove < 1) {
            nodesToRemove = treeNodes.size() - 1;
        }
        if (nodesToRemove <= 0) return 0;

        int actuallyRemoved = 0;

        // Sortiere Kandidaten: Blätter zuerst, dann nach Tiefe
        List<TreeMirrorNode> candidates = treeNodes.stream()
                .filter(node -> node != root)
                .sorted((node1, node2) -> {
                    int childrenCompare = Integer.compare(node1.getChildren().size(), node2.getChildren().size());
                    if (childrenCompare != 0) return childrenCompare;
                    return Integer.compare(node2.getDepthInTree(), node1.getDepthInTree());
                })
                .toList();

        // Entferne die ersten N Kandidaten
        for (int i = 0; i < nodesToRemove && i < candidates.size(); i++) {
            TreeMirrorNode nodeToRemove = candidates.get(i);

            // Entferne aus Parent-Child-Beziehung
            StructureNode parent = nodeToRemove.getParent();
            if (parent != null) {
                parent.removeChild(nodeToRemove);
            }

            // Verweise Kinder an Großeltern
            List<StructureNode> children = new ArrayList<>(nodeToRemove.getChildren());
            for (StructureNode child : children) {
                nodeToRemove.removeChild(child);
                if (parent != null) {
                    parent.addChild(child);
                }
            }

            removeFromStructureNodes(nodeToRemove);
            actuallyRemoved++;
        }

        return actuallyRemoved;
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
     * Adds the requested number of mirrors to the network and connects them accordingly.
     *
     * @param n the {@link Network}
     * @param newMirrors number of mirrors to add
     * @param props {@link Properties} of the simulation
     * @param simTime current simulation time
     */
    @Override
    public void handleAddNewMirrors(Network n, int newMirrors, Properties props, int simTime) {
        this.network = n;

        // Verwende das offizielle Interface von TopologyStrategy
        List<Mirror> addedMirrors = createMirrors(newMirrors, simTime, props);
        n.getMirrors().addAll(addedMirrors);

        // Setze Iterator für die neuen Mirrors
        setMirrorIterator(n.getMirrors().iterator());

        // Füge die neuen Knoten zur Struktur hinzu
        int actuallyAdded = addNodesToStructure(newMirrors);

        if (actuallyAdded > 0 && getCurrentStructureRoot() != null) {
            // Baue nur die neuen Links auf
            Set<Link> newLinks = buildAndUpdateLinks(getCurrentStructureRoot(), props, 0, StructureNode.StructureType.TREE);
            n.getLinks().addAll(newLinks);
        }
    }

    /**
     * Berechnet die erwartete Anzahl der Links für Bäume.
     * Bäume haben immer n-1 Links für n Knoten.
     *
     * @param n Das Netzwerk
     * @return Erwartete Anzahl Links (Anzahl Mirrors - 1)
     */
    @Override
    public int getNumTargetLinks(Network n) {
        return Math.max(0, n.getNumMirrors() - 1);
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
            int newLinksPerMirror = tlc.getNewLinksPerMirror();

            // Für Bäume ist die Anzahl Links immer n-1, unabhängig von Links pro Mirror
            // Aber wir können eine theoretische Berechnung machen:
            // Wenn jeder Mirror newLinksPerMirror Links haben soll, dann:
            // Gesamtlinks = (currentMirrors * newLinksPerMirror) / 2
            // Aber für echte Bäume ist es immer n-1

            // Baum-Constraint: Maximal n-1 Links für n Knoten
            int theoreticalLinks = (currentMirrors * newLinksPerMirror) / 2;
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
     * Überschreibt BuildAsSubstructure für TreeMirrorNode-Erstellung.
     *
     * @param mirror Der Mirror, für den ein MirrorNode erstellt werden soll
     * @return Neuer TreeMirrorNode
     */
    @Override
    protected MirrorNode createMirrorNodeForMirror(Mirror mirror) {
        return new TreeMirrorNode(mirror.getID(), mirror);
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

    /**
     * **AUSFÜHRUNGSEBENE**: Gemeinsame handleRemoveMirrors-Implementierung für alle Baum-Varianten.
     * Verwendet StructureType-Parameter für spezifische Filterung und Entfernungslogik.
     * <p>
     * **3-Phasen-Architektur mit Baum-spezifischer Tiefenmessung**:
     * - Phase 1: Structure Nodes nach Entfernung zur Wurzel sortieren (tiefste zuerst)
     * - Phase 2: Links über buildAndUpdateLinks komplett neu aufbauen
     * - Phase 3: Unverbundene Mirrors herunterfahren und sammeln
     *
     * @param n Das Netzwerk
     * @param removeMirrors Anzahl zu entfernender Mirrors
     * @param props Properties der Simulation
     * @param simTime Aktuelle Simulationszeit
     * @param structureType Struktur-Typ für spezifische Filterung (TREE, BALANCED_TREE, DEPTH_LIMITED_TREE)
     * @return Set der heruntergefahrenen Mirrors
     */
    protected Set<Mirror> handleRemoveMirrorsWithStructureType(Network n, int removeMirrors,
                                                               Properties props, int simTime,
                                                               StructureNode.StructureType structureType) {

        //Set<Link> touchedLinks = initNetwork(n,props);


        if (removeMirrors <= 0 || getCurrentStructureRoot() == null) {
            return Set.of(); // Keine Entfernung erforderlich
        }

        // ===== PHASE 1: PLANUNGSEBENE - Structure Nodes nach Tiefe sortieren (tiefste zuerst) =====

        // 1.1. Alle Baum-Knoten des spezifischen Typs sammeln
        List<TreeMirrorNode> allTreeNodes = getAllTreeNodesOfType(structureType);

        // 1.2. Nach Entfernung zur Wurzel sortieren (tiefste Knoten zuerst, dann höchste IDs)
        List<TreeMirrorNode> sortedForRemoval = allTreeNodes.stream()
                .sorted(Comparator
                        .comparingInt(TreeMirrorNode::getDepthInTree).reversed() // Tiefste zuerst
                        .thenComparing((node1, node2) -> Integer.compare(node2.getId(), node1.getId()))) // Höchste IDs zuerst
                .toList();

        // 1.3. Zu entfernende Knoten basierend auf Tiefe und ID auswählen
        int actualNodesToRemove = Math.min(removeMirrors, sortedForRemoval.size());
        List<TreeMirrorNode> nodesToRemove = sortedForRemoval.subList(0, actualNodesToRemove);

        // 1.4. Structure Nodes auf Planungsebene entkoppeln (delegiert an Subklasse)
        nodesToRemove.forEach(this::removeFromStructureNodes);
        for(TreeMirrorNode treeNode:allTreeNodes) {
            for(StructureNode child:treeNode.getChildren()) {
                if(nodesToRemove.contains(child)) {
                    treeNode.removeChild(child);
                }
            }
        }

        // ===== PHASE 2: LINK-UPDATE - Komplette Link-Neuerstellung =====

        // 2.1. Alle bestehenden Links über buildAndUpdateLinks neu aufbauen
        if (getCurrentStructureRoot() != null) {
            buildAndUpdateLinks(getCurrentStructureRoot(), props, simTime, structureType);
        }

        // ===== PHASE 3: MIRROR-SHUTDOWN - Unverbundene Mirrors sammeln und herunterfahren =====
        Set<Mirror> shutdownMirrors = network.getMirrorsSortedById().stream()
                .filter(mirror -> !mirror.isUsableForNetwork())
                .collect(Collectors.toSet());

        // 3.5. Root-Update falls Root-Mirror heruntergefahren wurde
        updateTreeRootAfterMirrorShutdown(shutdownMirrors, structureType);

        return shutdownMirrors;
    }

    /**
     * Sammelt alle Baum-Knoten eines spezifischen StructureTypes.
     *
     * @param structureType Der gewünschte Struktur-Typ
     * @return Liste aller TreeMirrorNodes des angegebenen Typs
     */
    private List<TreeMirrorNode> getAllTreeNodesOfType(StructureNode.StructureType structureType) {
        return getAllStructureNodes().stream()
                .filter(node -> node instanceof TreeMirrorNode)
                .map(node -> (TreeMirrorNode) node)
                .filter(treeNode -> treeNode.hasNodeType(structureType))
                .collect(Collectors.toList());
    }

    /**
     * Baum-spezifische Root-Update-Logik nach Mirror-Shutdown.
     * Wählt den Knoten mit geringster Tiefe und niedrigster ID als neue Root.
     *
     * @param shutdownMirrors Set der heruntergefahrenen Mirrors
     * @param structureType Struktur-Typ für Filterung
     */
    private void updateTreeRootAfterMirrorShutdown(Set<Mirror> shutdownMirrors,
                                                   StructureNode.StructureType structureType) {
        if (getCurrentStructureRoot() != null) {
            Mirror rootMirror = getCurrentStructureRoot().getMirror();

            // Prüfen, ob Root-Mirror heruntergefahren wurde
            if (rootMirror != null && shutdownMirrors.contains(rootMirror)) {
                // Neue Root aus verbleibenden Baum-Knoten wählen
                List<TreeMirrorNode> remainingTreeNodes = getAllTreeNodesOfType(structureType);

                if (!remainingTreeNodes.isEmpty()) {
                    // Knoten mit geringster Tiefe und niedrigster ID als neue Root wählen
                    TreeMirrorNode newRoot = remainingTreeNodes.stream()
                            .min(Comparator
                                    .comparingInt(TreeMirrorNode::getDepthInTree) // Geringste Tiefe zuerst
                                    .thenComparingInt(MirrorNode::getId)) // Niedrigste ID zuerst
                            .orElse(null);

                    newRoot.setHead(true);
                    setCurrentStructureRoot(newRoot);
                } else {
                    // Keine Baum-Knoten mehr vorhanden - Root auf null setzen
                    setCurrentStructureRoot(null);
                }
            }
        }
    }

}