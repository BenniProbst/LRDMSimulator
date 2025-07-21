package org.lrdm.topologies.strategies;

import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.*;
import org.lrdm.topologies.node.FullyConnectedMirrorNode;
import org.lrdm.topologies.node.MirrorNode;
import org.lrdm.topologies.node.StructureNode.StructureType;

import java.util.*;

/**
 * A {@link TopologyStrategy} which links each {@link Mirror} of the {@link Network} with each other mirror.
 * Links are considered undirected, i.e., there will be exactly one link between each pair of mirrors of the network.
 * <p>
 * Verwendet {@link FullyConnectedMirrorNode} für Struktur-Management und -Validierung.
 * Erweitert {@link BuildAsSubstructure} für konsistente StructureBuilder-Integration.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class FullyConnectedTopology extends BuildAsSubstructure {

    // ===== BUILD SUBSTRUCTURE IMPLEMENTATION =====

    /**
     * Baut eine vollständig vernetzte Struktur mit der angegebenen Anzahl von Knoten auf.
     * Jeder Knoten wird mit jedem anderen Knoten verbunden (vollständiger Graph).
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

        List<FullyConnectedMirrorNode> nodes = new ArrayList<>();

        // 1. Erstelle FullyConnectedMirrorNodes mit Mirror-Zuordnung
        for (int i = 0; i < totalNodes && hasNextMirror(); i++) {
            Mirror mirror = getNextMirror();
            if (mirror != null) {
                FullyConnectedMirrorNode node = new FullyConnectedMirrorNode(mirror.getID(), mirror);
                nodes.add(node);
                addToStructureNodes(node); // Registriere bei BuildAsSubstructure
                if(mirror.isRoot()) {
                    setCurrentStructureRoot(node);
                    node.setHead(true);
                }
            }
        }

        if (nodes.isEmpty()) {
            return null;
        }

        // 2. Setze den ersten Knoten als Head und Root
        FullyConnectedMirrorNode root = nodes.stream()
                .filter(node -> node.getMirror() != null && node.isRoot())
                .findFirst()
                .orElse(null);

        if (root == null) {
            throw new IllegalStateException("No root node found in FullyConnected structure");
        }

        // 3. Plane Strukturebene
        // vollständige Vernetzung: jeder mit jedem (außer sich selbst)
        for (int i = 0; i < nodes.size(); i++) {
            FullyConnectedMirrorNode sourceNode = nodes.get(i);

            for (int j = 0; j < nodes.size(); j++) {
                if (i == j) continue;
                FullyConnectedMirrorNode targetNode = nodes.get(j);

                // Füge bidirektionale StructureNode-Verbindung hinzu
                sourceNode.addChild(targetNode);
                targetNode.addChild(sourceNode);
            }
        }

        return root;
    }

    /**
     * Fügt neue Knoten zur bestehenden vollständig vernetzten Struktur hinzu.
     * Jeder neue Knoten wird mit allen bestehenden Knoten verbunden.
     *
     * @param nodesToAdd Anzahl der hinzuzufügenden Knoten
     * @return Tatsächliche Anzahl der hinzugefügten Knoten
     */
    @Override
    protected int addNodesToStructure(int nodesToAdd) {
        if (nodesToAdd <= 0 || getCurrentStructureRoot() == null) {
            return 0;
        }

        List<FullyConnectedMirrorNode> existingNodes = getAllStructureNodes().stream()
                .filter(node -> node instanceof FullyConnectedMirrorNode)
                .map(node -> (FullyConnectedMirrorNode) node)
                .toList();

        List<FullyConnectedMirrorNode> newNodes = new ArrayList<>();
        int actuallyAdded = 0;

        // Erstelle neue FullyConnectedMirrorNodes - Verwende das saubere Interface
        for (int i = 0; i < nodesToAdd && hasNextMirror(); i++) {
            Mirror mirror = getNextMirror();
            assert mirror != null;
            FullyConnectedMirrorNode newNode = new FullyConnectedMirrorNode(mirror.getID(), mirror);
            newNodes.add(newNode);
            addToStructureNodes(newNode);
            actuallyAdded++;
        }

        // Verbinde jeden neuen Knoten mit allen bestehenden Knoten
        for (FullyConnectedMirrorNode newNode : newNodes) {
            for (FullyConnectedMirrorNode existingNode : existingNodes) {
                // Bidirektionale StructureNode-Verbindungen
                newNode.addChild(existingNode);
                existingNode.addChild(newNode);
            }

            // Verbinde neue Knoten auch untereinander
            for (FullyConnectedMirrorNode otherNewNode : newNodes) {
                if (!newNode.equals(otherNewNode)) {
                    newNode.addChild(otherNewNode);
                    otherNewNode.addChild(newNode);
                }
            }
        }

        // Validiere die erweiterte Struktur
        if (getCurrentStructureRoot() instanceof FullyConnectedMirrorNode fcRoot) {
            if (!fcRoot.isValidStructure()) {
                throw new IllegalStateException("FullyConnected structure became invalid after adding nodes");
            }
        }

        return actuallyAdded;
    }

    /**
     * Entfernt Knoten aus einer bestehenden vollständig vernetzten Struktur.
     * Da in einer vollständigen Vernetzung alle Knoten gleich wichtig sind,
     * können beliebige Knoten entfernt werden (außer der Root).
     *
     * @param nodesToRemove Anzahl der zu entfernenden Knoten
     * @return Anzahl der tatsächlich entfernten Knoten
     */
    @Override
    protected int removeNodesFromStructure(int nodesToRemove) {
        if (nodesToRemove <= 0 || getAllStructureNodes().isEmpty()) {
            return 0;
        }

        // Sammle alle Knoten außer Root für die Entfernung
        List<FullyConnectedMirrorNode> candidatesForRemoval = new ArrayList<>();

        for (MirrorNode node : getAllStructureNodes()) {
            // Root-Node nie entfernen, alle anderen sind gleichwertige Kandidaten
            if (node != getCurrentStructureRoot() && node instanceof FullyConnectedMirrorNode fcNode) {
                candidatesForRemoval.add(fcNode);
            }
        }

        // Begrenze auf verfügbare Anzahl
        int actualRemovalCount = Math.min(nodesToRemove, candidatesForRemoval.size());

        if (actualRemovalCount == 0) {
            return 0;
        }

        // Sortiere nach ID (höchste zuerst) für deterministische Entfernung
        candidatesForRemoval.sort((node1, node2) -> Integer.compare(node2.getId(), node1.getId()));

        // Entferne die ersten N Kandidaten
        List<MirrorNode> nodesToRemoveList = new ArrayList<>();
        for (int i = 0; i < actualRemovalCount; i++) {
            FullyConnectedMirrorNode nodeToRemove = candidatesForRemoval.get(i);

            // Entferne alle StructureNode-Verbindungen zu diesem Knoten
            removeNodeFromFullyConnectedStructure(nodeToRemove);

            nodesToRemoveList.add(nodeToRemove);
        }

        // Bereinige die StructureNode-Verwaltung
        cleanupStructureNodes(nodesToRemoveList);

        return actualRemovalCount;
    }

    /**
     * Entfernt einen Knoten vollständig aus der vollständig vernetzten Struktur.
     * Bereinigt alle bidirektionalen Verbindungen zu anderen Knoten.
     *
     * @param nodeToRemove Der zu entfernende FullyConnectedMirrorNode
     */
    private void removeNodeFromFullyConnectedStructure(FullyConnectedMirrorNode nodeToRemove) {
        // Sammle alle verbundenen Knoten
        Set<FullyConnectedMirrorNode> connectedNodes = nodeToRemove.getConnectedNodes();

        // Entferne bidirektionale Verbindungen
        for (FullyConnectedMirrorNode connectedNode : connectedNodes) {
            // Entferne nodeToRemove aus den Kindern von connectedNode
            connectedNode.removeChild(nodeToRemove);
            // Entferne connectedNode aus den Kindern von nodeToRemove
            nodeToRemove.removeChild(connectedNode);
        }
    }

    /**
     * Baut die tatsächlichen Links zwischen den Mirrors basierend auf der StructureNode-Struktur auf.
     * Erstellt für jede StructureNode-Verbindung einen entsprechenden Mirror-Link.
     *
     * @param root    Die Root-Node der Struktur
     * @param props   Simulation Properties
     * @param simTime Zeitpunkt der Simulation
     * @return Set aller erstellten Links
     */
    @Override
    protected Set<Link> buildAndConnectLinks(MirrorNode root, Properties props, int simTime) {
        Set<Link> allLinks = new HashSet<>();

        if (!(root instanceof FullyConnectedMirrorNode fcRoot)) {
            return allLinks;
        }

        // Sammle alle Knoten in der Struktur
        List<FullyConnectedMirrorNode> nodeList = fcRoot.getAllNodesInStructure(StructureType.FULLY_CONNECTED, fcRoot)
                .stream()
                .filter(node -> node instanceof FullyConnectedMirrorNode)
                .map(node -> (FullyConnectedMirrorNode) node).distinct().toList();

        // Erstelle Links zwischen allen Paaren von Knoten
        for (int i = 0; i < nodeList.size(); i++) {
            FullyConnectedMirrorNode node1 = nodeList.get(i);

            for (int j = 0; j < nodeList.size(); j++) {
                FullyConnectedMirrorNode node2 = nodeList.get(j);

                if(node1.equals(node2)) continue;

                // Prüfe, ob die Mirrors bereits verbunden sind
                if (!node1.getMirror().isAlreadyConnected(node2.getMirror())) {
                    //Mirror nicht verbunden, sollte er verbunden sein → Link erstellen
                    Link link = new Link(idGenerator.getNextID(), node1.getMirror(), node2.getMirror(),
                            simTime, props);
                    node1.getMirror().addLink(link);
                    node2.getMirror().addLink(link);
                    allLinks.add(link);
                }
                else{
                    //Mirror verbunden, solle er nicht verbunden sein → Link löschen

                }
            }
        }

        return allLinks;
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

        // Setze Iterator für die neuen Mirrors - BuildAsSubstructure erwartet diesen
        this.mirrorIterator = addedMirrors.iterator();

        // Füge die neuen Knoten zur Struktur hinzu
        int actuallyAdded = addNodesToStructure(newMirrors);

        if (actuallyAdded > 0 && getCurrentStructureRoot() != null) {
            // Baue nur die neuen Links auf
            Set<Link> newLinks = buildAndConnectLinks(getCurrentStructureRoot(), props, 0);
            n.getLinks().addAll(newLinks);
        }
    }

    // ===== TOPOLOGY STRATEGY INTERFACE IMPLEMENTATION =====

    /**
     * Returns the expected number of total links in the network according to the fully connected topology.
     * For n mirrors, the number of links is n*(n-1)/2.
     *
     * @param n {@link Network} the network
     * @return number of total links expected for the network
     */
    @Override
    public int getNumTargetLinks(Network n) {
        int numMirrors = n.getNumMirrors();
        return numMirrors * (numMirrors - 1) / 2;
    }

    /**
     * Berechnet die erwartete Anzahl der Links, wenn die gegebene Aktion ausgeführt wird.
     * Behandelt drei verschiedene Action-Typen:
     * 1. MirrorChange: Berechnet Links für neue Mirror-Anzahl (verwendet getNewMirrors(), nicht getNewMirrors())
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

            // Bei FullyConnectedTopology: n * (n-1) / 2 Links für n Mirrors
            return newMirrorCount * (newMirrorCount - 1) / 2;
        }

        // 2. TargetLinkChange: Ändert die Links pro Mirror
        else if (a instanceof TargetLinkChange tlc) {
            // Bei TargetLinkChange werden die Links pro Mirror geändert
            int newLinksPerMirror = tlc.getNewLinksPerMirror();

            // Behalte aktuelle Mirror-Anzahl, aber berechne mit neuen Links pro Mirror.
            // Für FullyConnected: Falls newLinksPerMirror >= (n-1), vollständig vernetzt,
            // sonst begrenzt durch Links pro Mirror
            if (newLinksPerMirror >= currentMirrors - 1) {
                // Vollständige Vernetzung möglich
                return currentMirrors * (currentMirrors - 1) / 2;
            } else {
                // Begrenzt durch Links pro Mirror
                return currentMirrors * newLinksPerMirror / 2;
            }
        }

        // 3. TopologyChange: Ändert die Topology-Strategie
        else if (a instanceof TopologyChange tc) {
            TopologyStrategy newTopology = tc.getNewTopology();

            // Delegiere an die neue Topology-Strategie mit aktueller Mirror-Anzahl
            return newTopology.getNumTargetLinks(network);
        }

        // Fallback: Unbekannter Action-Typ - verwende aktuellen Zustand
        return getNumTargetLinks(network);
    }

    /**
     * Berechnet die erwartete Link-Anzahl für eine gegebene Knotenzahl.
     * Für vollständige Vernetzung: n * (n-1) / 2 Links für n Knoten.
     *
     * @param nodeCount Anzahl der Knoten
     * @return Erwartete Anzahl der Links für vollständige Vernetzung
     */
    public static int calculateExpectedLinks(int nodeCount) {
        if (nodeCount <= 0) {
            return 0;
        }
        return nodeCount * (nodeCount - 1) / 2;
    }


    // ===== HILFSMETHODEN =====

    /**
     * Validiert die aktuelle vollständig vernetzte Struktur.
     *
     * @return true, wenn die Struktur gültig ist
     */
    protected boolean validateTopology() {
        MirrorNode root = getCurrentStructureRoot();
        if (root instanceof FullyConnectedMirrorNode fcRoot) {
            return fcRoot.isValidStructure();
        }
        return false;
    }

    @Override
    public String toString() {
        int nodeCount = getAllStructureNodes().size();
        int expectedLinks = calculateExpectedLinks(nodeCount);
        boolean isValid = validateTopology();

        return String.format("FullyConnectedTopology{nodes=%d, expectedLinks=%d, valid=%s, substructureId=%d}",
                nodeCount, expectedLinks, isValid, getSubstructureId());
    }
}