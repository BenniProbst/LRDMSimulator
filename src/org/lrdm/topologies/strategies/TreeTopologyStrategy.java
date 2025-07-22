package org.lrdm.topologies.strategies;

import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.*;
import org.lrdm.topologies.node.*;
import org.lrdm.topologies.node.StructureNode.StructureType;

import java.util.*;

/**
 * A {@link TopologyStrategy} which connects mirrors in a tree topology.
 * Each mirror (except the root) has exactly one parent, creating a hierarchical structure
 * with no cycles. This results in exactly n-1 links for n mirrors.
 * <p>
 * Verwendet {@link TreeMirrorNode} für Struktur-Management und erweitert {@link BuildAsSubstructure}
 * für konsistente StructureBuilder-Integration.
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class TreeTopologyStrategy extends BuildAsSubstructure {

    // ===== BUILD SUBSTRUCTURE IMPLEMENTATION =====

    /**
     * Baut eine Baum-Struktur mit der angegebenen Anzahl von Knoten auf.
     * Erstellt eine hierarchische Struktur mit einem Root-Knoten und n-1 Links.
     * Erstellt sowohl StructureNode-Verbindungen als auch echte Mirror-Links.
     *
     * @param totalNodes Anzahl der zu erstellenden Knoten
     * @param props      Properties der Simulation
     * @return Die Root-Node der erstellten Struktur
     */
    @Override
    protected MirrorNode buildStructure(int totalNodes, Properties props) {
        if (totalNodes <= 0 || network == null) {
            return null;
        }

        List<TreeMirrorNode> nodes = new ArrayList<>();

        // 1. Erstelle TreeMirrorNodes mit Mirror-Zuordnung
        for (int i = 0; i < totalNodes && hasNextMirror(); i++) {
            Mirror mirror = getNextMirror();
            if (mirror != null) {
                TreeMirrorNode node = new TreeMirrorNode(mirror.getID(), mirror);
                nodes.add(node);
                addToStructureNodes(node); // Registriere bei BuildAsSubstructure
            }
        }

        if (nodes.isEmpty()) {
            return null;
        }

        // 2. Setze den ersten Knoten als Root und Head
        TreeMirrorNode root = nodes.get(0);
        root.setHead(true);
        setCurrentStructureRoot(root);

        // 3. Baue Baum-Struktur: StructureNode-Verbindungen UND echte Mirror-Links
        Set<Link> createdLinks = buildTreeStructureWithLinks(nodes, simTime, props);

        // 4. Registriere alle Links im Network
        network.getLinks().addAll(createdLinks);

        return root;
    }

    /**
     * Erstellt die Baum-Struktur zwischen den Knoten mit echten Mirror-Links.
     * Jeder Knoten (außer Root) wird mit genau einem Parent verbunden.
     * Erstellt sowohl StructureNode-Verbindungen als auch echte Mirror-Links.
     *
     * @param nodes Liste der TreeMirrorNodes
     * @param simTime Aktuelle Simulationszeit für Link-Erstellung
     * @param props Properties der Simulation
     * @return Set aller erstellten Links
     */
    private Set<Link> buildTreeStructureWithLinks(List<TreeMirrorNode> nodes, int simTime, Properties props) {
        Set<Link> createdLinks = new HashSet<>();

        if (nodes.size() <= 1) {
            return createdLinks; // Kein Link bei 0 oder 1 Knoten
        }

        TreeMirrorNode root = nodes.get(0);

        // Erstelle eine einfache Breadth-First-Baum-Struktur
        Queue<TreeMirrorNode> parentsQueue = new LinkedList<>();
        parentsQueue.offer(root);

        int nodeIndex = 1; // Beginne bei Index 1 (nach Root)
        int maxChildrenPerParent = 2; // Binärbaum als Standard

        while (!parentsQueue.isEmpty() && nodeIndex < nodes.size()) {
            TreeMirrorNode parent = parentsQueue.poll();
            Mirror parentMirror = parent.getMirror();

            // Füge bis zu maxChildrenPerParent Kinder hinzu
            for (int i = 0; i < maxChildrenPerParent && nodeIndex < nodes.size(); i++) {
                TreeMirrorNode child = nodes.get(nodeIndex);
                Mirror childMirror = child.getMirror();

                if (parentMirror != null && childMirror != null) {
                    // Erstelle echten Mirror-Link
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

                    // Füge StructureNode-Verbindungen hinzu (Parent-Child-Beziehung)
                    parent.addChild(child);
                    child.setParent(parent);

                    createdLinks.add(link);
                }

                // Füge das Kind zur Queue für die nächste Ebene hinzu
                parentsQueue.offer(child);
                nodeIndex++;
            }
        }

        return createdLinks;
    }

    /**
     * Fügt neue Knoten zur bestehenden Baum-Struktur hinzu.
     * Neue Knoten werden als Blätter an bestehende Knoten angehängt.
     *
     * @param nodesToAdd Anzahl der hinzuzufügenden Knoten
     * @return Tatsächliche Anzahl der hinzugefügten Knoten
     */
    @Override
    protected int addNodesToStructure(int nodesToAdd) {
        if (nodesToAdd <= 0 || getCurrentStructureRoot() == null) {
            return 0;
        }

        // Finde alle Blatt-Knoten (Knoten ohne Kinder)
        List<TreeMirrorNode> leafNodes = getAllStructureNodes().stream()
                .filter(node -> node instanceof TreeMirrorNode)
                .map(node -> (TreeMirrorNode) node)
                .filter(node -> node.getChildren().isEmpty())
                .toList();

        if (leafNodes.isEmpty()) {
            return 0; // Keine Blätter verfügbar
        }

        List<TreeMirrorNode> newNodes = new ArrayList<>();
        int actuallyAdded = 0;

        // Erstelle neue TreeMirrorNodes
        for (int i = 0; i < nodesToAdd && hasNextMirror(); i++) {
            Mirror mirror = getNextMirror();
            if (mirror != null) {
                TreeMirrorNode newNode = new TreeMirrorNode(mirror.getID(), mirror);
                newNodes.add(newNode);
                addToStructureNodes(newNode);
                actuallyAdded++;
            }
        }

        // Verbinde neue Knoten mit vorhandenen Blatt-Knoten
        int leafIndex = 0;
        for (TreeMirrorNode newNode : newNodes) {
            TreeMirrorNode parent = leafNodes.get(leafIndex % leafNodes.size());
            
            // StructureNode-Verbindung
            parent.addChild(newNode);
            newNode.setParent(parent);

            leafIndex++;
        }

        return actuallyAdded;
    }

    /**
     * Entfernt Knoten aus einer bestehenden Baum-Struktur.
     * Bevorzugt Blatt-Knoten für die Entfernung, um die Baum-Struktur zu erhalten.
     *
     * @param nodesToRemove Anzahl der zu entfernenden Knoten
     * @return Anzahl der tatsächlich entfernten Knoten
     */
    @Override
    protected int removeNodesFromStructure(int nodesToRemove) {
        if (nodesToRemove <= 0 || getAllStructureNodes().isEmpty()) {
            return 0;
        }

        // Sammle Blatt-Knoten (außer Root) für die Entfernung
        List<TreeMirrorNode> leafCandidates = new ArrayList<>();

        for (MirrorNode node : getAllStructureNodes()) {
            if (node != getCurrentStructureRoot() && node instanceof TreeMirrorNode treeNode) {
                // Nur Blätter (Knoten ohne Kinder) entfernen
                if (treeNode.getChildren().isEmpty()) {
                    leafCandidates.add(treeNode);
                }
            }
        }

        // Begrenze auf verfügbare Anzahl
        int actualRemovalCount = Math.min(nodesToRemove, leafCandidates.size());

        if (actualRemovalCount == 0) {
            return 0;
        }

        // Sortiere nach ID (höchste zuerst) für deterministische Entfernung
        leafCandidates.sort((node1, node2) -> Integer.compare(node2.getId(), node1.getId()));

        // Entferne die ersten N Kandidaten
        List<MirrorNode> nodesToRemoveList = new ArrayList<>();
        for (int i = 0; i < actualRemovalCount; i++) {
            TreeMirrorNode nodeToRemove = leafCandidates.get(i);

            // Entferne Parent-Child-Beziehungen
            removeNodeFromTreeStructure(nodeToRemove);

            nodesToRemoveList.add(nodeToRemove);
        }

        // Bereinige die StructureNode-Verwaltung
        cleanupStructureNodes(nodesToRemoveList);

        return actualRemovalCount;
    }

    @Override
    protected boolean validateTopology() {
        return false;
    }

    /**
     * Entfernt einen Knoten vollständig aus der Baum-Struktur.
     * Bereinigt die Parent-Child-Beziehungen.
     *
     * @param nodeToRemove Der zu entfernende TreeMirrorNode
     */
    private void removeNodeFromTreeStructure(TreeMirrorNode nodeToRemove) {
        // Entferne aus Parent
        StructureNode parent = nodeToRemove.getParent();
        if (parent != null) {
            parent.removeChild(nodeToRemove);
        }

        // Entferne alle Children (sollten keine sein bei Blatt-Knoten)
        List<StructureNode> children = new ArrayList<>(nodeToRemove.getChildren());
        for (StructureNode child : children) {
            nodeToRemove.removeChild(child);
            if (child instanceof TreeMirrorNode treeChild) {
                treeChild.setParent(null);
            }
        }

        // Entferne der Parent-Referenz
        nodeToRemove.setParent(null);
    }

    /**
     * Baut die tatsächlichen Links zwischen den Mirrors basierend auf der StructureNode-Struktur auf.
     * Erstellt für jede Parent-Child-Beziehung einen entsprechenden Mirror-Link.
     *
     * @param root    Die Root-Node der Struktur
     * @param props   Simulation Properties
     * @param simTime
     * @return Set aller erstellten Links
     */
    @Override
    protected Set<Link> buildAndConnectLinks(MirrorNode root, Properties props, int simTime) {
        Set<Link> allLinks = new HashSet<>();

        if (!(root instanceof TreeMirrorNode treeRoot)) {
            return allLinks;
        }

        // Durchlaufe alle Knoten in der Struktur
        Set<StructureNode> allNodes = treeRoot.getAllNodesInStructure(StructureType.TREE, treeRoot);

        for (StructureNode node : allNodes) {
            if (!(node instanceof TreeMirrorNode treeNode)) {
                continue;
            }

            Mirror nodeMirror = treeNode.getMirror();
            if (nodeMirror == null) {
                continue;
            }

            // Erstelle Links zu allen Kindern
            for (StructureNode child : treeNode.getChildren()) {
                if (!(child instanceof TreeMirrorNode treeChild)) {
                    continue;
                }

                Mirror childMirror = treeChild.getMirror();
                if (childMirror == null) {
                    continue;
                }

                // Prüfe, ob die Mirrors bereits verbunden sind
                if (!nodeMirror.isAlreadyConnected(childMirror)) {
                    Link link = new Link(idGenerator.getNextID(), nodeMirror, childMirror,
                            0, props);
                    nodeMirror.addLink(link);
                    childMirror.addLink(link);
                    allLinks.add(link);
                }
            }
        }

        return allLinks;
    }

    // ===== TOPOLOGY STRATEGY INTERFACE IMPLEMENTATION =====

    /**
     * Initializes the network by connecting mirrors in a tree topology.
     *
     * @param n the {@link Network}
     * @param props {@link Properties} of the simulation
     * @return {@link Set} of all {@link Link}s created
     */
    @Override
    public Set<Link> initNetwork(Network n, Properties props) {
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
     * Startet das Netzwerk komplett neu mit der aktuellen Topologie.
     *
     * @param n       Das Netzwerk
     * @param props   Simulation Properties
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
     * Returns the expected number of total links in the network according to the tree topology.
     * For n mirrors, the number of links is n-1 (tree property).
     *
     * @param n {@link Network} the network
     * @return number of total links expected for the network
     */
    @Override
    public int getNumTargetLinks(Network n) {
        int numMirrors = n.getNumMirrors();
        return Math.max(0, numMirrors - 1); // n-1 Links für n Knoten in einem Baum
    }

    /**
     * Berechnet die erwartete Anzahl der Links, wenn die gegebene Aktion ausgeführt wird.
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
            return Math.max(0, newMirrorCount - 1); // n-1 Links für n Mirrors
        }

        // 2. TargetLinkChange: Für Baum-Topologie immer n-1 Links
        else if (a instanceof TargetLinkChange) {
            return Math.max(0, currentMirrors - 1);
        }

        // 3. TopologyChange: Delegiere an neue Topology-Strategie
        else if (a instanceof TopologyChange tc) {
            TopologyStrategy newTopology = tc.getNewTopology();
            return newTopology.getNumTargetLinks(network);
        }

        // Fallback: Verwende aktuellen Zustand
        return getNumTargetLinks(network);
    }

    // ===== HILFSMETHODEN =====

    /**
     * Berechnet die erwartete Link-Anzahl für eine gegebene Knotenzahl.
     * Für Baum-Topologie: n-1 Links für n Knoten.
     *
     * @param nodeCount Anzahl der Knoten
     * @return Erwartete Anzahl der Links für Baum-Topologie
     */
    public static int calculateExpectedLinks(int nodeCount) {
        return Math.max(0, nodeCount - 1);
    }

    @Override
    public String toString() {
        int nodeCount = getAllStructureNodes().size();
        int expectedLinks = calculateExpectedLinks(nodeCount);
        
        return String.format("TreeTopologyStrategy{nodes=%d, expectedLinks=%d, substructureId=%d}",
                nodeCount, expectedLinks, getSubstructureId());
    }
}