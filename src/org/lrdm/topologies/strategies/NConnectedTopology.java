
package org.lrdm.topologies.strategies;

import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.*;
import org.lrdm.topologies.node.MirrorNode;
import org.lrdm.topologies.node.StructureNode;
import org.lrdm.util.IDGenerator;

import java.util.*;

/**
 * A {@link TopologyStrategy} which connects each mirror with exactly n other mirrors.
 * If n is the number of all mirrors - 1 this strategy equals the {@link FullyConnectedTopology}.
 * <p>
 * Verwendet {@link MirrorNode} für Struktur-Management und erweitert {@link BuildAsSubstructure}
 * für konsistente StructureBuilder-Integration.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class NConnectedTopology extends BuildAsSubstructure {

    // ===== BUILD SUBSTRUCTURE IMPLEMENTATION =====

    /**
     * Baut eine N-Connected-Struktur mit der angegebenen Anzahl von Knoten auf.
     * Jeder Knoten wird mit genau N anderen Knoten verbunden (basierend auf numTargetLinksPerMirror).
     *
     * @param totalNodes Anzahl der zu erstellenden Knoten
     * @param simTime Aktuelle Simulationszeit für Link-Erstellung
     * @return Die Root-Node der erstellten Struktur
     */
    @Override
    protected MirrorNode buildStructure(int totalNodes, int simTime) {
        if (totalNodes <= 0) {
            return null;
        }

        List<MirrorNode> nodes = new ArrayList<>();

        // Erstelle MirrorNodes - Verwende das saubere Interface
        for (int i = 0; i < totalNodes && hasNextMirror(); i++) {
            Mirror mirror = getNextMirror();
            assert mirror != null;
            MirrorNode node = new MirrorNode(mirror.getID(), mirror);
            nodes.add(node);
            addToStructureNodes(node);
        }

        if (nodes.isEmpty()) {
            return null;
        }

        // Setze den ersten Knoten als Head und Root
        MirrorNode root = nodes.get(0);
        root.setHead(true);
        setCurrentStructureRoot(root);

        // Verknüpfe die Knoten basierend auf N-Connected-Logik
        buildNConnectedStructure(nodes);

        return root;
    }

    /**
     * Erstellt die N-Connected-Struktur zwischen den Knoten.
     * Jeder Knoten wird mit bis zu N anderen Knoten verbunden.
     */
    private void buildNConnectedStructure(List<MirrorNode> nodes) {
        if (network == null) {
            return;
        }

        int linksPerMirror = network.getNumTargetLinksPerMirror();

        for (int i = 0; i < nodes.size(); i++) {
            MirrorNode sourceNode = nodes.get(i);
            int connectionsAdded = 0;

            // Verbinde mit den nächsten verfügbaren Knoten
            for (int j = 0; j < nodes.size() && connectionsAdded < linksPerMirror; j++) {
                if (i == j) continue; // Keine Selbstverbindung

                MirrorNode targetNode = nodes.get(j);

                // Prüfe, ob bereits verbunden (über getChildren)
                if (!isNodeConnectedTo(sourceNode, targetNode)) {
                    // Füge bidirektionale StructureNode-Verbindung hinzu
                    sourceNode.addChild(targetNode);
                    targetNode.addChild(sourceNode);
                    connectionsAdded++;
                }
            }
        }
    }

    /**
     * Prüft, ob zwei MirrorNodes bereits miteinander verbunden sind.
     * Überprüft die Children-Beziehungen in beide Richtungen.
     */
    private boolean isNodeConnectedTo(MirrorNode node1, MirrorNode node2) {
        // Prüfe, ob node2 in den Children von node1 ist
        for (StructureNode child : node1.getChildren()) {
            if (child == node2) {
                return true;
            }
        }

        // Prüfe umgekehrt (für Sicherheit bei bidirektionalen Verbindungen)
        for (StructureNode child : node2.getChildren()) {
            if (child == node1) {
                return true;
            }
        }

        return false;
    }

    /**
     * Fügt neue Knoten zur bestehenden N-Connected-Struktur hinzu.
     * Neue Knoten werden mit bestehenden Knoten basierend auf der N-Connected-Logik verbunden.
     *
     * @param nodesToAdd Anzahl der hinzuzufügenden Knoten
     * @return Tatsächliche Anzahl der hinzugefügten Knoten
     */
    @Override
    protected int addNodesToStructure(int nodesToAdd) {
        if (nodesToAdd <= 0 || !hasNextMirror()) {
            return 0;
        }

        List<MirrorNode> newNodes = new ArrayList<>();
        List<MirrorNode> existingNodes = new ArrayList<>(getAllStructureNodes());

        // Erstelle neue Knoten
        int actualAdded = 0;
        for (int i = 0; i < nodesToAdd && hasNextMirror(); i++) {
            Mirror mirror = getNextMirror();
            if (mirror != null) {
                MirrorNode newNode = new MirrorNode(mirror.getID(), mirror);
                newNodes.add(newNode);
                addToStructureNodes(newNode);
                actualAdded++;
            }
        }

        if (newNodes.isEmpty()) {
            return 0;
        }

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
            if (getNodeConnectionCount(existingNode) < linksPerMirror) {
                // Füge bidirektionale Verbindung hinzu
                newNode.addChild(existingNode);
                existingNode.addChild(newNode);
                connectionsAdded++;
            }
        }
    }

    /**
     * Zählt die aktuellen Verbindungen eines Knotens.
     */
    private int getNodeConnectionCount(MirrorNode node) {
        return node.getChildren().size();
    }

    /**
     * Entfernt Knoten aus einer bestehenden N-Connected-Struktur.
     * Bevorzugt Knoten mit weniger Verbindungen für die Entfernung.
     *
     * @param nodesToRemove Anzahl der zu entfernenden Knoten
     * @return Anzahl der tatsächlich entfernten Knoten
     */
    @Override
    protected int removeNodesFromStructure(int nodesToRemove) {
        if (nodesToRemove <= 0) {
            return 0;
        }

        // Sammle Kandidaten für Entfernung (außer Root)
        List<MirrorNode> candidatesForRemoval = new ArrayList<>();

        for (MirrorNode node : getAllStructureNodes()) {
            if (node != getCurrentStructureRoot()) {
                candidatesForRemoval.add(node);
            }
        }

        if (candidatesForRemoval.isEmpty()) {
            return 0;
        }

        // Sortiere nach Verbindungsanzahl (weniger Verbindungen zuerst)
        candidatesForRemoval.sort((node1, node2) -> {
            int connections1 = getNodeConnectionCount(node1);
            int connections2 = getNodeConnectionCount(node2);

            if (connections1 != connections2) {
                return Integer.compare(connections1, connections2);
            }

            // Bei gleicher Verbindungsanzahl: höhere ID zuerst
            return Integer.compare(node2.getId(), node1.getId());
        });

        // Entferne die gewünschte Anzahl
        int actualRemovalCount = Math.min(nodesToRemove, candidatesForRemoval.size());
        List<MirrorNode> nodesToRemoveList = new ArrayList<>();

        for (int i = 0; i < actualRemovalCount; i++) {
            MirrorNode nodeToRemove = candidatesForRemoval.get(i);
            removeNodeFromNConnectedStructure(nodeToRemove);
            nodesToRemoveList.add(nodeToRemove);
        }

        // Bereinige die StructureNode-Verwaltung
        cleanupStructureNodes(nodesToRemoveList);

        return actualRemovalCount;
    }

    /**
     * Entfernt einen Knoten vollständig aus der N-Connected-Struktur.
     * Bereinigt alle bidirektionalen Verbindungen.
     */
    private void removeNodeFromNConnectedStructure(MirrorNode nodeToRemove) {
        // Sammle alle verbundenen Knoten
        Set<MirrorNode> connectedNodes = new HashSet<>();
        for (StructureNode child : nodeToRemove.getChildren()) {
            if (child instanceof MirrorNode mirrorChild) {
                connectedNodes.add(mirrorChild);
            }
        }

        // Entferne bidirektionale Verbindungen
        for (MirrorNode connectedNode : connectedNodes) {
            connectedNode.removeChild(nodeToRemove);
            nodeToRemove.removeChild(connectedNode);
        }
    }

    /**
     * Baut die tatsächlichen Links zwischen den Mirrors basierend auf der StructureNode-Struktur auf.
     *
     * @param root Die Root-Node der Struktur
     * @param props Simulation Properties
     * @return Set aller erstellten Links
     */
    @Override
    protected Set<Link> buildAndConnectLinks(MirrorNode root, Properties props) {
        Set<Link> links = new HashSet<>();
        Set<String> processedConnections = new HashSet<>();

        for (MirrorNode node : getAllStructureNodes()) {
            Mirror sourceMirror = node.getMirror();
            if (sourceMirror == null) continue;

            for (StructureNode child : node.getChildren()) {
                if (child instanceof MirrorNode childNode) {
                    Mirror targetMirror = childNode.getMirror();
                    if (targetMirror == null) continue;

                    // Verhindere doppelte Links (bidirektional)
                    String connectionKey = createConnectionKey(sourceMirror, targetMirror);
                    if (processedConnections.contains(connectionKey)) {
                        continue;
                    }

                    // Erstelle Link, nur wenn noch nicht vorhanden
                    if (!sourceMirror.isLinkedWith(targetMirror)) {
                        Link link = new Link(
                                IDGenerator.getInstance().getNextID(),
                                sourceMirror,
                                targetMirror,
                                0,
                                props
                        );

                        sourceMirror.addLink(link);
                        targetMirror.addLink(link);
                        links.add(link);

                        processedConnections.add(connectionKey);
                    }
                }
            }
        }

        return links;
    }

    /**
     * Erstellt einen eindeutigen Schlüssel für eine Verbindung zwischen zwei Mirrors.
     */
    private String createConnectionKey(Mirror m1, Mirror m2) {
        int id1 = m1.getID();
        int id2 = m2.getID();
        return (id1 < id2) ? id1 + "-" + id2 : id2 + "-" + id1;
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

        MirrorNode root = buildStructure(n.getNumMirrors(), 0);

        if (root == null) {
            return new HashSet<>();
        }

        return buildAndConnectLinks(root, props);
    }


    /**
     * Startet das Netzwerk komplett neu mit der aktuellen Topologie.
     *
     * @param n Das Netzwerk
     * @param props Simulation Properties
     * @param simTime Aktuelle Simulationszeit
     */
    @Override
    public void restartNetwork(Network n, Properties props, int simTime) {
        // Standard-Restart-Verhalten
        super.restartNetwork(n, props, simTime);

        // Erstelle N-Connected-Struktur neu
        Set<Link> newLinks = initNetwork(n, props);
        n.getLinks().addAll(newLinks);
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
        if (newMirrors <= 0) {
            return;
        }

        // Erstelle und füge neue Mirrors hinzu
        List<Mirror> addedMirrors = createMirrors(newMirrors, simTime, props);
        n.getMirrors().addAll(addedMirrors);

        // Initialisiere den internen Zustand, falls nötig
        if (getCurrentStructureRoot() == null) {
            initializeInternalState(n);
            MirrorNode root = buildStructure(n.getNumMirrors(), simTime);
            if (root != null) {
                Set<Link> newLinks = buildAndConnectLinks(root, props);
                n.getLinks().addAll(newLinks);
            }
        } else {
            // Füge zu bestehender Struktur hinzu
            setMirrorIterator(addedMirrors.iterator());
            int actualAdded = addNodesToStructure(newMirrors);

            if (actualAdded > 0) {
                // Erstelle Links für neue Verbindungen
                Set<Link> newLinks = buildAndConnectLinks(getCurrentStructureRoot(), props);
                n.getLinks().addAll(newLinks);
            }
        }
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
     * Entfernt die angegebene Anzahl von Mirrors aus dem Netzwerk.
     * Erweitert die BuildAsSubstructure-Implementierung um N-Connected-spezifische Logik.
     *
     * @param n Das Netzwerk
     * @param removeMirrors Anzahl der zu entfernenden Mirrors
     * @param props Simulation Properties
     * @param simTime Aktuelle Simulationszeit
     */
    @Override
    public void handleRemoveMirrors(Network n, int removeMirrors, Properties props, int simTime) {
        if (removeMirrors <= 0) {
            return;
        }

        // Verwende die BuildAsSubstructure-Implementierung, die bereits:
        // 1. Mirror.shutdown() aufruft
        // 2. StructureNode-Beziehungen bereinigt
        // 3. Observer benachrichtigt
        super.handleRemoveMirrors(n, removeMirrors, props, simTime);

        // Falls N-Connected-spezifische Nachbearbeitung nötig wäre:
        // rebalanceConnections();
    }

    /**
     * Returns the number of links expected for the overall network according to this strategy.
     * If the number of mirrors is less than twice the number of links per mirror, we compute this like for the fully connected topology.
     * For a fully connected network this can be computed as (n * (n -1)) / 2, where n is the number of mirrors.
     * Else, the number of links can be simply computed by multiplying the number of links per mirror with the number of mirrors.
     *
     * @param n the {@link Network}
     * @return the number of links the network is expected to have
     */
    @Override
    public int getNumTargetLinks(Network n) {
        int numMirrors = n.getNumMirrors();
        int linksPerMirror = n.getNumTargetLinksPerMirror();

        if (numMirrors <= 2 * linksPerMirror) {
            // Bei wenigen Mirrors: vollständige Vernetzung
            return (numMirrors * (numMirrors - 1)) / 2;
        } else {
            // Bei vielen Mirrors: N Links pro Mirror (geteilt durch 2 wegen bidirektionaler Links)
            return (numMirrors * linksPerMirror) / 2;
        }
    }

    /**
     * Berechnet die erwartete Anzahl der Links, wenn die gegebene Aktion ausgeführt wird.
     *
     * @param a Die Action, deren Auswirkungen berechnet werden sollen
     * @return Anzahl der erwarteten Links nach Ausführung der Action
     */
    @Override
    public int getPredictedNumTargetLinks(Action a) {
        int m = a.getNetwork().getNumMirrors();
        int lpm = a.getNetwork().getNumTargetLinksPerMirror();

        if (a instanceof MirrorChange mc) {
            m += mc.getNewMirrors();
        } else if (a instanceof TargetLinkChange tlc) {
            lpm += tlc.getNewLinksPerMirror();
        } else if (a instanceof TopologyChange tc) {
            // KORREKTUR: getNewTopology() statt getNewStrategy()
            return tc.getNewTopology().getPredictedNumTargetLinks(a);
        }

        if (m <= 2 * lpm) {
            return (m * (m - 1)) / 2;
        } else {
            return (m * lpm) / 2;
        }
    }

    @Override
    public String toString() {
        return "NConnectedTopology";
    }
}