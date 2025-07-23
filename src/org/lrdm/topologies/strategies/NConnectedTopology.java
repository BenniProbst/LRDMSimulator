
package org.lrdm.topologies.strategies;

import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.*;
import org.lrdm.topologies.node.*;

import java.util.*;

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
     * Erstellt die N-Connected-Struktur zwischen den Knoten mit echten Mirror-Links.
     * Jeder Knoten wird mit bis zu N anderen Knoten verbunden.
     * Erstellt sowohl StructureNode-Verbindungen als auch echte Mirror-Links.
     *
     * @param nodes Liste der NConnectedMirrorNodes
     * @param simTime Aktuelle Simulationszeit für Link-Erstellung
     * @param props Properties der Simulation
     * @return Set aller erstellten Links
     */
    private Set<Link> buildNConnectedStructureWithLinks(List<NConnectedMirrorNode> nodes, int simTime, Properties props) {
        if (network == null) {
            return new HashSet<>();
        }

        int linksPerMirror = network.getNumTargetLinksPerMirror();
        Set<Link> createdLinks = new HashSet<>();

        for (int i = 0; i < nodes.size(); i++) {
            NConnectedMirrorNode sourceNode = nodes.get(i);
            Mirror sourceMirror = sourceNode.getMirror();
            int connectionsAdded = 0;

            // Verbinde mit den nächsten verfügbaren Knoten
            for (int j = 0; j < nodes.size() && connectionsAdded < linksPerMirror; j++) {
                if (i == j) continue; // Keine Selbstverbindung

                NConnectedMirrorNode targetNode = nodes.get(j);
                Mirror targetMirror = targetNode.getMirror();

                // Prüfe, ob bereits verbunden (über Mirror-Links)
                if (sourceMirror != null && targetMirror != null &&
                        !isAlreadyConnected(sourceMirror, targetMirror)) {

                    // Erstelle echten Mirror-Link mit korrektem Konstruktor
                    Link link = new Link(
                            idGenerator != null ? idGenerator.getNextID() : (int)(Math.random() * 100000),
                            sourceMirror,
                            targetMirror,
                            simTime,
                            props
                    );

                    // Füge Link zu beiden Mirrors hinzu
                    sourceMirror.addLink(link);
                    targetMirror.addLink(link);

                    // Füge bidirektionale StructureNode-Verbindung hinzu
                    sourceNode.addChild(targetNode);
                    targetNode.addChild(sourceNode);

                    createdLinks.add(link);
                    connectionsAdded++;
                }
            }
        }

        return createdLinks;
    }

    /**
     * Prüft, ob zwei Mirrors bereits über einen Link verbunden sind.
     * Verwendet die verfügbaren Link-Methoden getSource() und getTarget().
     *
     * @param mirror1 Erster Mirror
     * @param mirror2 Zweiter Mirror
     * @return true, wenn bereits verbunden
     */
    private boolean isAlreadyConnected(Mirror mirror1, Mirror mirror2) {
        return mirror1.getLinks().stream()
                .anyMatch(link ->
                        (link.getSource() == mirror2) || (link.getTarget() == mirror2)
                );
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

    @Override
    protected boolean validateTopology() {
        return false;
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
            MirrorNode root = buildStructure(n.getNumMirrors(), props);
            if (root != null) {
                Set<Link> newLinks = buildAndUpdateLinks(root, props, 0, StructureNode.StructureType.N_CONNECTED);
                n.getLinks().addAll(newLinks);
            }
        } else {
            // Füge zu bestehender Struktur hinzu
            setMirrorIterator(addedMirrors.iterator());
            int actualAdded = addNodesToStructure(newMirrors);

            if (actualAdded > 0) {
                // Erstelle Links für neue Verbindungen
                Set<Link> newLinks = buildAndUpdateLinks(getCurrentStructureRoot(), props, simTime, StructureNode.StructureType.N_CONNECTED);
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
     * @return Set der entfernten Mirrors
     */
    @Override
    public Set<Mirror> handleRemoveMirrors(Network n, int removeMirrors, Properties props, int simTime) {
        if (removeMirrors <= 0) {
            return new HashSet<>();
        }

        // Verwende die BuildAsSubstructure-Implementierung, die bereits:
        // 1. Mirror.shutdown() aufruft
        // 2. StructureNode-Beziehungen bereinigt
        // 3. Observer benachrichtigt
        // 4. Set<Mirror> zurückgibt

        // Falls N-Connected-spezifische Nachbearbeitung nötig wäre:
        // rebalanceConnections();

        return super.handleRemoveMirrors(n, removeMirrors, props, simTime);
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
     * Berücksichtigt die drei Action-Typen und die N-Connected-Grenzfälle.
     *
     * @param a Die Action, deren Auswirkungen berechnet werden sollen
     * @return Anzahl der erwarteten Links nach Ausführung der Action
     */
    @Override
    public int getPredictedNumTargetLinks(Action a) {
        Network network = a.getNetwork();
        int currentMirrors = network.getNumMirrors();
        int currentLinksPerMirror = network.getNumTargetLinksPerMirror();

        // Neue Werte nach Action berechnen
        int newMirrors = currentMirrors;
        int newLinksPerMirror = currentLinksPerMirror;

        if (a instanceof MirrorChange mc) {
            // MirrorChange: getNewMirrors() ist die NEUE Gesamtanzahl, nicht die Differenz
            newMirrors = mc.getNewMirrors();
        } else if (a instanceof TargetLinkChange tlc) {
            // TargetLinkChange: getNewLinksPerMirror() ist der NEUE Wert pro Mirror
            newLinksPerMirror = tlc.getNewLinksPerMirror();
        } else if (a instanceof TopologyChange tc) {
            // TopologyChange: Delegiere an die neue Topologie-Strategie
            return tc.getNewTopology().getPredictedNumTargetLinks(a);
        }

        // Berechne N-Connected Links mit den neuen Werten
        return calculateNConnectedLinks(newMirrors, newLinksPerMirror);
    }

    /**
     * Berechnet die Anzahl der Links für eine N-Connected-Topologie.
     * Berücksichtigt den Grenzfall, dass nicht genügend Mirrors für die gewünschten Links vorhanden sind.
     *
     * @param numMirrors Anzahl der Mirrors
     * @param linksPerMirror Gewünschte Links pro Mirror
     * @return Tatsächliche Anzahl der möglichen Links
     */
    private int calculateNConnectedLinks(int numMirrors, int linksPerMirror) {
        if (numMirrors <= 0 || linksPerMirror <= 0) {
            return 0;
        }

        // Maximale mögliche Links in einem vollständig vernetzten Graph: n*(n-1)/2
        int maxPossibleLinks = (numMirrors * (numMirrors - 1)) / 2;

        // Gewünschte Links basierend auf linksPerMirror: (n * linksPerMirror) / 2
        // Division durch 2, da jeder Link zwei Mirrors verbindet
        int desiredLinks = (numMirrors * linksPerMirror) / 2;

        // Rückgabe des Minimums: Was ist realistisch erreichbar?
        return Math.min(desiredLinks, maxPossibleLinks);
    }

    @Override
    public String toString() {
        return "NConnectedTopology";
    }
}