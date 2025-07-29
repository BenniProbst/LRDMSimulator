
package org.lrdm.topologies.strategies;

import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.*;
import org.lrdm.topologies.node.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A {@link TopologyStrategy} which connects each mirror with exactly n other mirrors.
 * If n is the number of all mirrors - 1 this strategy equals the {@link FullyConnectedTopology}.
 * <p>
 * Verwendet {@link MirrorNode} für Struktur-Management und erweitert {@link BuildAsSubstructure}
 * für konsistente StructureBuilder-Integration.
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class NConnectedTopology extends BuildAsSubstructure {

    // ===== BUILD SUBSTRUCTURE IMPLEMENTATION =====

    /**
     * Baut eine N-Connected-Struktur mit der angegebenen Anzahl von Knoten auf.
     * Erstellt NConnectedMirrorNodes, weist Mirrors zu, baut StructureNode-Verbindungen
     * und erstellt die echten Mirror-Links zur simTime.
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

        int linksPerMirror = network.getNumTargetLinksPerMirror();
        List<NConnectedMirrorNode> nodes = new ArrayList<>();

        // 1. Erstelle NConnectedMirrorNodes mit Mirror-Zuordnung
        for (int i = 0; i < totalNodes && hasNextMirror(); i++) {
            Mirror mirror = getNextMirror();
            if (mirror != null) {
                NConnectedMirrorNode node = new NConnectedMirrorNode(mirror.getID(), mirror, linksPerMirror);
                nodes.add(node);
                addToStructureNodes(node); // Registriere bei BuildAsSubstructure
            }
        }

        if (nodes.isEmpty()) {
            return null;
        }

        // 2. Setze den ersten Knoten als Head und Root
        NConnectedMirrorNode root = nodes.get(0);
        root.setHead(true);
        setCurrentStructureRoot(root);

        return root;
    }

    /**
     * Fügt neue Knoten zur bestehenden N-Connected-Struktur hinzu.
     * Neue Knoten werden mit bestehenden Knoten basierend auf der N-Connected-Logik verbunden.
     *
     * @param nodesToAdd Anzahl der hinzuzufügenden Knoten
     * @return Tatsächliche Anzahl der hinzugefügten Knoten
     */
    @Override
    protected int addNodesToStructure(Set<Mirror> nodesToAdd) {
        if (nodesToAdd.isEmpty() || !hasNextMirror()) {
            return 0;
        }

        List<MirrorNode> newNodes = new ArrayList<>();
        List<MirrorNode> existingNodes = new ArrayList<>(getAllStructureNodes());

        List<Mirror> tmpMirrorIterate = new ArrayList<>(nodesToAdd);
        setMirrorIterator(tmpMirrorIterate.iterator());
        // Erstelle neue Knoten
        int actualAdded = 0;
        for (int i = 0; i < nodesToAdd.size() && hasNextMirror(); i++) {
            NConnectedMirrorNode newNode = getMirrorNodeFromIterator();
            newNodes.add(newNode);
            addToStructureNodes(newNode);
            actualAdded++;
        }
        setMirrorIterator(network.getMirrors().iterator());

        // Verbinde neue Knoten mit bestehenden Knoten
        if (network != null) {
            int linksPerMirror = network.getNumTargetLinksPerMirror();

            for (MirrorNode newNode : newNodes) {
                connectNodeToExistingNodes(newNode, existingNodes, linksPerMirror);
            }
        }

        return actualAdded;
    }

    /**
     * Verbindet einen neuen Knoten mit bestehenden Knoten basierend auf N-Connected-Logik.
     */
    private void connectNodeToExistingNodes(MirrorNode newNode, List<MirrorNode> existingNodes, int linksPerMirror) {
        int connectionsAdded = 0;

        for (MirrorNode existingNode : existingNodes) {
            if (connectionsAdded >= linksPerMirror) {
                break;
            }

            // Prüfe, ob der bestehende Knoten noch Kapazität hat
            if (existingNode.getChildren().size() < linksPerMirror) {
                // Füge bidirektionale Verbindung hinzu
                newNode.addChild(existingNode);
                existingNode.addChild(newNode);
                connectionsAdded++;
            }
        }
    }

    /**
     * **PLANUNGSEBENE**: Entfernt Knoten aus der N-Connected-Struktur.
     * N-Connected-Entfernung: Bevorzugt Knoten mit wenigen Verbindungen (minimale Störung).
     * Bei gleicher Verbindungsanzahl werden Knoten mit höheren IDs bevorzugt entfernt.
     * NUR STRUKTURPLANUNG - keine Mirror-Links!
     *
     * @param nodesToRemove Anzahl der zu entfernenden Knoten
     * @return Anzahl der tatsächlich entfernten Knoten
     */
    @Override
    protected Set<MirrorNode> removeNodesFromStructure(int nodesToRemove) {
        if (nodesToRemove <= 0 || getCurrentStructureRoot() == null) {
            return new HashSet<>();
        }

        List<MirrorNode> allNodes = getAllNConnectedNodes();

        // Sammle Kandidaten für Entfernung (außer Root)
        List<MirrorNode> candidatesForRemoval = allNodes.stream()
                .filter(node -> node != getCurrentStructureRoot()) // Root nie entfernen
                .filter(node -> !node.isHead()) // Head-Knoten bevorzugt beibehalten
                .collect(Collectors.toList());

        // Fallback: Wenn keine Nicht-Head-Kandidaten verfügbar sind, verwenden alle Nicht-Root-Knoten
        if (candidatesForRemoval.isEmpty()) {
            candidatesForRemoval = allNodes.stream()
                    .filter(node -> node != getCurrentStructureRoot())
                    .collect(Collectors.toList());
        }

        // Begrenze auf verfügbare Anzahl
        int actualRemovalCount = Math.min(nodesToRemove, candidatesForRemoval.size());
        if (actualRemovalCount == 0) return new HashSet<>();

        // **N-CONNECTED-SPEZIFISCH**: Sortiere nach Verbindungsanzahl (weniger Verbindungen zuerst)
        // Bei gleicher Verbindungsanzahl: höhere ID zuerst (deterministische Auswahl)
        candidatesForRemoval.sort((node1, node2) -> {
            int connections1 = node1.getChildren().size();
            int connections2 = node2.getChildren().size();

            if (connections1 != connections2) {
                return Integer.compare(connections1, connections2); // Weniger Verbindungen zuerst
            }

            // Bei gleicher Verbindungsanzahl: höhere ID zuerst
            return Integer.compare(node2.getId(), node1.getId());
        });

        // **NUR PLANUNGSEBENE**: Entferne die ersten N Kandidaten
        Set<MirrorNode> removedNodes = new HashSet<>();

        for (int i = 0; i < actualRemovalCount; i++) {
            MirrorNode nodeToRemove = candidatesForRemoval.get(i);

            // 1. **NUR STRUKTURPLANUNG**: Entferne alle StructureNode-Verbindungen
            removeNodeFromStructuralPlanning(nodeToRemove,
                    Set.of(StructureNode.StructureType.DEFAULT,StructureNode.StructureType.MIRROR,StructureNode.StructureType.N_CONNECTED));

            removedNodes.add(nodeToRemove);
        }

        return removedNodes;
    }

// ===== TYPSICHERE HILFSMETHODEN =====

    /**
     * Gibt alle N-Connected-Knoten als typisierte Liste zurück.
     * Erweitert die BuildAsSubstructure-Funktionalität um Typ-Sicherheit.
     *
     * @return Liste aller MirrorNodes in der N-Connected-Struktur
     */
    private List<MirrorNode> getAllNConnectedNodes() {
        return new ArrayList<>(getAllStructureNodes());
    }

    // ===== TOPOLOGY STRATEGY INTERFACE IMPLEMENTATION =====

    /**
     * Initializes the network by connecting mirrors according to N-Connected topology.
     *
     * @param n the {@link Network}
     * @param props {@link Properties} of the simulation
     * @return {@link Set} of all {@link Link}s created
     */
    @Override
    public Set<Link> initNetwork(Network n, Properties props) {
        // Initialisiere den internen Zustand
        initializeInternalState(n);

        MirrorNode root = buildStructure(n.getNumMirrors(), props);

        if (root == null) {
            return new HashSet<>();
        }

        return buildAndUpdateLinks(root, props, 0, StructureNode.StructureType.N_CONNECTED);
    }


    /**
     * Startet das Netzwerk komplett neu mit der aktuellen Topologie.
     *
     * @param n       Das Netzwerk
     * @param props   Simulation Properties
     * @param simTime Aktuelle Simulationszeit
     * @return {@link Set} of all {@link Link}s created
     */
    @Override
    public Set<Link> restartNetwork(Network n, Properties props, int simTime) {
        // Initialisiere den internen Zustand
        initializeInternalState(n);

        MirrorNode root = buildStructure(n.getNumMirrors(), props);

        if (root == null) {
            return new HashSet<>();
        }

        return buildAndUpdateLinks(root, props, simTime, StructureNode.StructureType.N_CONNECTED);
    }

    /**
     * Eine Hilfsmethode für die Initialisierung des internen Zustands.
     * Verwendet die korrekte Methode aus BuildAsSubstructure.
     */
    private void initializeInternalState(Network n) {
        this.network = n;
        this.mirrorIterator = n.getMirrors().iterator();
    }

    /**
     * Returns the number of links expected for the overall network according to this strategy.
     * If the number of mirrors is less than twice the number of links per mirror, we compute this like for the fully connected topology.
     * For a fully connected network this can be computed as (n * (n -1)) / 2, where n is the number of mirrors.
     * Else, the number of links can be simply computed by multiplying the number of links per mirror with the number of mirrors.
     * Berechnet die erwartete Anzahl der Links im aktuellen Netzwerk.
     *
     * @param n Das Netzwerk
     * @return Anzahl der erwarteten Links
     */
    @Override
    public int getNumTargetLinks(Network n) {
        return calculateNConnectedLinks(n.getNumMirrors(), n.getNumTargetLinksPerMirror());
    }

    /**
     * Berechnet die erwartete Anzahl der Links, wenn die gegebene Aktion ausgeführt wird.
     * N-Connected-spezifische Implementierung basierend auf den drei Action-Typen.
     * Überschreibt die abstrakte Methode aus TopologyStrategy.
     */
    @Override
    public int getPredictedNumTargetLinks(Action a) {
        if (a == null) {
            return network != null ? getNumTargetLinks(network) : 0;
        }

        // 1. MirrorChange: Anzahl der Mirrors ändert sich
        if (a instanceof MirrorChange mirrorChange) {
            int newMirrorCount = mirrorChange.getNewMirrors();
            int linksPerMirror = network != null ? network.getNumTargetLinksPerMirror() : 2;
            return calculateNConnectedLinks(newMirrorCount, linksPerMirror);
        }

        // 2. TargetLinkChange: Links pro Mirror ändern sich
        if (a instanceof TargetLinkChange targetLinkChange) {
            Network targetNetwork = targetLinkChange.getNetwork();
            int currentMirrors = targetNetwork.getNumMirrors();
            int newLinksPerMirror = targetLinkChange.getNewLinksPerMirror();
            return calculateNConnectedLinks(currentMirrors, newLinksPerMirror);
        }

        // 3. TopologyChange: Komplette Topologie-Rekonstruktion
        if (a instanceof TopologyChange topologyChange) {
            TopologyStrategy newTopology = topologyChange.getNewTopology();
            if (network != null) {
                return newTopology.getNumTargetLinks(network);
            }
            return 0;
        }

        // Fallback: Aktueller Zustand beibehält
        return network != null ? getNumTargetLinks(network) : 0;
    }

    /**
     * Berechnet die Anzahl der Links für eine N-Connected-Topologie mit Graphentheorie.
     * <p>
     * Zwei Grenzfälle:
     * 1. Wenn n < 2*linksPerMirror: Nicht genug Knoten → Vollständiger Graph = n*(n-1)/2
     * 2. Wenn n >= 2*linksPerMirror: Regulärer Graph möglich → n*linksPerMirror/2
     *
     * @param numMirrors Anzahl der Mirrors/Knoten
     * @param linksPerMirror Gewünschte Links pro Mirror/Knotengrad
     * @return Tatsächliche Anzahl der möglichen Links
     */
    private int calculateNConnectedLinks(int numMirrors, int linksPerMirror) {
        if (numMirrors <= 1) {
            return 0; // Keine Links möglich
        }

        // Grenzfall: Nicht genug Knoten für gewünschten Grad
        // → Fallback auf vollständigen Graphen
        if (numMirrors < 2 * linksPerMirror) {
            return numMirrors * (numMirrors - 1) / 2;
        }

        // Regulärer Fall: n-regulärer Graph ist möglich.
        // Jeder Knoten hat genau linksPerMirror Verbindungen
        // Gesamtzahl Links = (Anzahl Knoten × Grad) / 2
        return (numMirrors * linksPerMirror) / 2;
    }

    /**
     * Erstellt einen neuen MirrorNode mit Mirror aus dem Iterator.
     * AKTUALISIERT: Fügt den Knoten automatisch zu structureNodes hinzu.
     *
     * @return Neuer MirrorNode mit zugeordnetem Mirror oder null
     */
    @Override
    protected NConnectedMirrorNode getMirrorNodeFromIterator() {
        if (mirrorIterator != null && mirrorIterator.hasNext()) {
            NConnectedMirrorNode node = (NConnectedMirrorNode) super.getMirrorNodeFromIterator();
            node.addNodeType(StructureNode.StructureType.N_CONNECTED);
            return node;
        }
        return null;
    }

    @Override
    public String toString() {
        int currentNodes = getAllStructureNodes().size();
        int currentLinks = network != null ? network.getLinks().size() : 0;
        int targetLinks = network != null ? getNumTargetLinks(network) : 0;
        int linksPerMirror = network != null ? network.getNumTargetLinksPerMirror() : 0;

        String topologyType = determineTopologyType(currentNodes, linksPerMirror);
        boolean isRegular = isRegularGraphPossible(currentNodes, linksPerMirror);
        double efficiency = calculateLinkEfficiency(currentLinks, currentNodes);

        return String.format("NConnectedTopology{type=%s, nodes=%d, links=%d/%d, linksPerMirror=%d, " +
                        "regularGraph=%s, efficiency=%.1f%%, density=%.3f}",
                topologyType,
                currentNodes,
                currentLinks, targetLinks,
                linksPerMirror,
                isRegular,
                efficiency,
                calculateGraphDensity(currentLinks, currentNodes));
    }

    /**
     * Bestimmt den effektiven Topologie-Typ basierend auf der aktuellen Konfiguration.
     */
    private String determineTopologyType(int nodes, int linksPerMirror) {
        if (nodes <= 1) return "Empty";
        if (linksPerMirror >= nodes - 1) return "FullyConnected";
        if (nodes < 2 * linksPerMirror) return "PartiallyConnected";
        return "N-Regular";
    }

    /**
     * Prüft, ob ein regulärer Graph mit der aktuellen Konfiguration möglich ist.
     */
    private boolean isRegularGraphPossible(int nodes, int linksPerMirror) {
        return nodes >= 2 * linksPerMirror && nodes > 1;
    }

    /**
     * Berechnet die Link-Effizienz (aktuelle Links / maximal mögliche Links).
     */
    private double calculateLinkEfficiency(int currentLinks, int nodes) {
        if (nodes <= 1) return 0.0;
        int maxPossibleLinks = nodes * (nodes - 1) / 2;
        return maxPossibleLinks > 0 ? (currentLinks * 100.0) / maxPossibleLinks : 0.0;
    }

    /**
     * Berechnet die Graphendichte (2 * Links) / (Knoten * (Knoten-1)).
     */
    private double calculateGraphDensity(int links, int nodes) {
        if (nodes <= 1) return 0.0;
        return (2.0 * links) / (nodes * (nodes - 1));
    }

    @Override
    protected MirrorNode createMirrorNodeForMirror(Mirror mirror) {
        int linksPerMirror = network != null ? network.getNumTargetLinksPerMirror() : 2;
        return new NConnectedMirrorNode(mirror.getID(), mirror, linksPerMirror);
    }

    @Override
    protected boolean validateTopology() {
        if (network == null || getCurrentStructureRoot() == null) return false;

        int expectedLinks = getNumTargetLinks(network);
        int actualLinks = network.getLinks().size();
        int linksPerMirror = network.getNumTargetLinksPerMirror();

        // Prüfe, ob jeder Knoten die erwartete Anzahl Verbindungen hat
        for (MirrorNode node : getAllStructureNodes()) {
            int nodeConnections = node.getChildren().size();
            int expectedConnections = Math.min(linksPerMirror, getAllStructureNodes().size() - 1);

            if (nodeConnections != expectedConnections) {
                return false;
            }
        }

        return Math.abs(actualLinks - expectedLinks) <= 1; // Toleranz für Rundungsfehler
    }
}