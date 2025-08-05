
package org.lrdm.topologies.strategies;

import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.*;
import org.lrdm.topologies.node.MirrorNode;
import org.lrdm.topologies.node.NConnectedMirrorNode;
import org.lrdm.topologies.node.StructureNode;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.min;

/**
 * Eine {@link TopologyStrategy}, die jede Node mit den ersten N Vorgänger-Nodes bidirektional verbindet.
 * <p>
 * **N-Connected-Eigenschaften**:
 * - Jede Node wird mit den ersten N Vorgänger-Nodes vollständig bidirektional verbunden
 * - targetLinksPerNode bestimmt die Anzahl der Vorgänger-Verbindungen
 * - Neuere Nodes sind mit mehr Vorgängern verbunden als ältere
 * - Verwendet {@link NConnectedMirrorNode} für Struktur-Management und -Validierung
 * - Erweitert {@link BuildAsSubstructure} für konsistente StructureBuilder-Integration
 * <p>
 * **Verbindungslogik**:
 * - Node 0: 0 Verbindungen (erste Node)
 * - Node 1: 1 Verbindung zu Node 0
 * - Node 2: min(2, targetLinksPerNode) Verbindungen zu Node 0,1
 * - Node N: min(N, targetLinksPerNode) Verbindungen zu den ersten N Vorgängern
 * <p>
 * **Beispiel mit targetLinksPerNode=3**:
 * - Node 0: []
 * - Node 1: [0]
 * - Node 2: [0,1]
 * - Node 3: [0,1,2]
 * - Node 4: [1,2,3] (nur die letzten 3 Vorgänger)
 * - Node 5: [2,3,4] (nur die letzten 3 Vorgänger)
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class NConnectedTopology extends BuildAsSubstructure {

    // ===== N-CONNECTED-SPEZIFISCHE KONFIGURATION =====
    private int targetLinksPerNode = 2;//start with a circle

    // ===== KONSTRUKTOREN =====

    public NConnectedTopology() {
        super();
    }

    public NConnectedTopology(int targetLinksPerNode) {
        super();
        this.targetLinksPerNode = Math.max(1, targetLinksPerNode);
    }

    // ===== BUILD SUBSTRUCTURE IMPLEMENTATION =====

    /**
     * Baut eine N-Connected-Struktur mit der angegebenen Anzahl von Knoten auf.
     * Jeder Knoten wird mit den ersten N Vorgänger-Knoten bidirektional verbunden.
     * Erstellt sowohl StructureNode-Verbindungen als auch echte Mirror-Links.
     *
     * @param totalNodes Anzahl der zu erstellenden Knoten
     * @param props      Properties der Simulation
     * @return Die Root-Node der erstellten Struktur
     */
    @Override
    protected MirrorNode buildStructure(int totalNodes, Properties props) {
        if (totalNodes <= 0 || !hasNextMirror()) {
            return null;
        }

        // 1. Erstelle alle Knoten
        List<NConnectedMirrorNode> allNodes = new ArrayList<>();
        for (int i = 0; i < totalNodes; i++) {
            NConnectedMirrorNode node = getMirrorNodeFromIterator();
            allNodes.add(node);
            addToStructureNodes(node); // Registriere bei BuildAsSubstructure
            if (node.getMirror().isRoot()) {
                setCurrentStructureRoot(node);
                node.setHead(StructureNode.StructureType.N_CONNECTED, true);
            }
        }

        if (allNodes.isEmpty()) {
            return null;
        }

        // 2. Setze den ersten Knoten als Head und Root
        NConnectedMirrorNode root = allNodes.stream()
                .filter(node -> node.getMirror() != null && node.isRoot())
                .findFirst()
                .orElse(null);

        if (root == null) {
            throw new IllegalStateException("No root node found in FullyConnected structure");
        }


        // 3. Erstelle N-Connected-Verbindungen
        buildNConnectedStructure(allNodes);

        return root;
    }

    /**
     * Erstellt die N-Connected-Struktur: Jede Node wird mit den ersten N Vorgängern verbunden.
     *
     * @param allNodes Liste aller Knoten in chronologischer Reihenfolge
     */
    private void buildNConnectedStructure(List<NConnectedMirrorNode> allNodes) {

        allNodes.sort(Comparator.comparingInt(MirrorNode::getId));
        int overshift = min(targetLinksPerNode, allNodes.size() * 2) - 1;

        for (int i = 1; i < allNodes.size() + overshift; i++) {
            NConnectedMirrorNode currentNode = allNodes.get(i % allNodes.size());

            // Berechne die Anzahl der Vorgänger, mit denen verbunden werden soll
            int connectionsToMake = min(i, overshift);

            // Verbinde mit den letzten connectionsToMake Vorgängern
            int startIndex = Math.max(0, i - connectionsToMake);
            for (int j = startIndex; j < i; j++) {
                NConnectedMirrorNode predecessorNode = allNodes.get(j % allNodes.size());

                // Bidirektionale Verbindung erstellen
                predecessorNode.addChild(currentNode);
            }
        }
    }


    /**
     * Fügt neue Knoten zur bestehenden N-Connected-Struktur hinzu.
     * Neue Knoten werden mit den letzten N bestehenden Knoten bidirektional verbunden.
     *
     * @param nodesToAdd Set der hinzuzufügenden Mirrors
     * @return Tatsächliche Anzahl der hinzugefügten Knoten
     */
    @Override
    protected int addNodesToStructure(Set<Mirror> nodesToAdd) {
        if (nodesToAdd.isEmpty()) {
            return 0;
        }

        List<NConnectedMirrorNode> allNodes = new ArrayList<>(getAllNConnectedNodes()
                .stream()
                .sorted(Comparator.comparingInt(MirrorNode::getId)).toList());

        if (allNodes.isEmpty()) {
            return 0;
        }

        // Sammle zu entfernende Knoten (höchste IDs zuerst, aber nie die Root)
        int addedNodes = 0;

        // Entferne die Knoten mit höchsten IDs
        for (int i = allNodes.size() - 1; i >= 0; i--) {
            NConnectedMirrorNode existingNode = allNodes.get(i);

            // Entferne alle Verbindungen dieses Knotens
            removeNodeAndAllConnections(existingNode);
        }

        setMirrorIterator(nodesToAdd.iterator());

        for(int i = 0; i < nodesToAdd.size(); i++) {
            allNodes.add(getMirrorNodeFromIterator());
            addedNodes++;
        }

        allNodes.sort(Comparator.comparingInt(MirrorNode::getId));
        buildNConnectedStructure(allNodes);

        return addedNodes;
    }


    /**
     * **PLANUNGSEBENE**: Entfernt Knoten aus der N-Connected-Struktur.
     * N-Connected-Entfernung: Bevorzugt Knoten mit höchsten IDs (neueste zuerst).
     * WICHTIG: Nach der Entfernung wird die gesamte Struktur neu verkabelt,
     * um sicherzustellen, dass alle verbleibenden Knoten wieder die gleiche Konnektivität haben.
     * NUR STRUKTURPLANUNG - keine Mirror-Links!
     *
     * @param nodesToRemove Anzahl der zu entfernenden Knoten
     * @return Set der tatsächlich entfernten Knoten
     */
    @Override
    protected Set<MirrorNode> removeNodesFromStructure(int nodesToRemove) {
        if (nodesToRemove <= 0) {
            return new HashSet<>();
        }

        List<NConnectedMirrorNode> allNodes = getAllNConnectedNodes()
                .stream()
                .sorted(Comparator.comparingInt(MirrorNode::getId)).toList();

        if (allNodes.isEmpty()) {
            return new HashSet<>();
        }

        // Berechne tatsächliche Anzahl der zu entfernenden Knoten
        int actualNodesToRemove = Math.min(nodesToRemove, allNodes.size() - 2); // Mindestens 2 Knoten behalten
        if (actualNodesToRemove <= 0) {
            return new HashSet<>();
        }

        // Sammle zu entfernende Knoten (höchste IDs zuerst, aber nie die Root)
        Set<MirrorNode> removedNodes = new HashSet<>();
        List<NConnectedMirrorNode> remainingNodes = new ArrayList<>();

        // Entferne die Knoten mit höchsten IDs
        for (int i = allNodes.size() - 1; i >= 0; i--) {
            NConnectedMirrorNode nodeToRemove = allNodes.get(i);

            // Entferne alle Verbindungen dieses Knotens
            removeNodeAndAllConnections(nodeToRemove);

            if (i < allNodes.size() - actualNodesToRemove) {
                remainingNodes.add(nodeToRemove);
            } else {
                // Entferne aus structureNodes
                removeFromStructureNodes(nodeToRemove);
                removedNodes.add(nodeToRemove);
            }
        }

        buildNConnectedStructure(remainingNodes);

        return removedNodes;
    }

    /**
     * Entfernt einen Knoten und alle Verbindungen aus der N-Connected-Struktur.
     * Die Hilfsmethode für removeNodesFromStructure.
     */
    private void removeNodeAndAllConnections(NConnectedMirrorNode nodeToRemove) {
        StructureNode.StructureType typeId = StructureNode.StructureType.N_CONNECTED;
        int headId = getCurrentStructureRoot().getId();

        // Entferne alle Kind-Verbindungen
        Set<StructureNode> children = new HashSet<>(nodeToRemove.getChildren(typeId, headId));
        for (StructureNode child : children) {
            nodeToRemove.removeChild(child);
        }

        // Entferne eine Parent-Verbindung
        StructureNode parent = nodeToRemove.getParent();
        if (parent != null) {
            parent.removeChild(nodeToRemove);
        }
    }

    // ===== TYPSICHERE HILFSMETHODEN =====

    /**
     * Gibt alle N-Connected-Knoten als typisierte Liste zurück.
     * Erweitert die BuildAsSubstructure-Funktionalität um Typ-Sicherheit.
     *
     * @return Liste aller NConnectedMirrorNodes in der Struktur
     */
    private List<NConnectedMirrorNode> getAllNConnectedNodes() {
        return getAllStructureNodes()
                .stream()
                .filter(node -> node instanceof NConnectedMirrorNode)
                .map(node -> (NConnectedMirrorNode) node)
                .collect(Collectors.toList());
    }

    // ===== TOPOLOGY STRATEGY INTERFACE IMPLEMENTATION =====

    /**
     * Berechnet die erwartete Anzahl der Links im aktuellen Netzwerk.
     * Für N-Connected mit targetLinksPerNode: Summe aller Vorgänger-Verbindungen.
     *
     * @param n Das Netzwerk
     * @return Anzahl der erwarteten Links
     */
    @Override
    public int getNumTargetLinks(Network n) {
        int numMirrors = n.getMirrors().size();
        return calculateNConnectedLinks(numMirrors, targetLinksPerNode);
    }


    /**
     * Berechnet die erwartete Anzahl der Links, wenn die gegebene Aktion ausgeführt wird.
     * Behandelt drei verschiedene Action-Typen:
     * 1. MirrorChange: Berechnet Links für neue Mirror-Anzahl
     * 2. TargetLinkChange: Berechnet Links basierend auf neuen Links pro Mirror
     * 3. TopologyChange: Delegiert an neue Topology-Strategie
     */
    @Override
    public int getPredictedNumTargetLinks(Action a) {
        if (a instanceof MirrorChange mc) {
            return calculateNConnectedLinks(mc.getNewMirrors(), targetLinksPerNode);
        } else if (a instanceof TargetLinkChange tlc) {
            // Verwende aktuelle Mirror-Anzahl mit neuen Links pro Mirror
            int currentMirrors = (network != null) ? network.getMirrors().size() : 0;
            return calculateNConnectedLinks(currentMirrors, tlc.getNewLinksPerMirror());
        } else if (a instanceof TopologyChange tc) {
            // Delegiere an neue Topology-Strategie
            int currentMirrors = (network != null) ? network.getMirrors().size() : 0;
            return tc.getNewTopology().getNumTargetLinks(network); // Fake MirrorChange
        }

        // Fallback: aktuelle Konfiguration
        int currentMirrors = (network != null) ? network.getMirrors().size() : 0;
        return calculateNConnectedLinks(currentMirrors, targetLinksPerNode);
    }


    /**
     * Berechnet die Anzahl der Links für eine N-Connected-Topologie mit Vorgänger-Logik.
     * <p>
     * Formel: Summe von min(i, targetLinksPerNode) für i = 0 bis numMirrors-1
     * - Node 0: 0 Links
     * - Node 1: min(1, N) Links
     * - Node 2: min(2, N) Links
     * - ...
     * - Node k: min(k, N) Links
     *
     * @param numMirrors         Anzahl der Mirrors/Knoten
     * @param targetLinksPerNode Anzahl der Vorgänger-Verbindungen pro Node
     * @return Tatsächliche Anzahl der möglichen Links
     */
    private int calculateNConnectedLinks(int numMirrors, int targetLinksPerNode) {
        if (numMirrors <= 1 || targetLinksPerNode <= 0) {
            return 0;
        }

        int totalLinks = 0;
        for (int i = 1; i < numMirrors; i++) {
            totalLinks += min(i, targetLinksPerNode);
        }

        return totalLinks;
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

    // ===== GETTER UND SETTER =====

    public int getTargetLinksPerNode() {
        return targetLinksPerNode;
    }

    public void setTargetLinksPerNode(int targetLinksPerNode) {
        this.targetLinksPerNode = Math.max(1, targetLinksPerNode);
    }

    // ===== STRING REPRESENTATION =====

    /**
     * Liefert eine detaillierte String-Repräsentation der NConnectedTopology.
     * Zeigt Topologie-Status, Struktur-Informationen und Netzwerk-Metriken.
     * Debugger-freundlich: Vermeidet komplexe Methodenaufrufe.
     *
     * @return Formatierte String-Darstellung der aktuellen Topologie
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("NConnectedTopology{");

        try {
            // Grundlegende Topology-Information
            sb.append("substructureId=").append(getSubstructureId());
            sb.append(", targetLinksPerNode=").append(targetLinksPerNode);

            // Sichere Struktur-Informationen
            MirrorNode root = getCurrentStructureRoot();
            if (root != null) {
                sb.append(", rootId=").append(root.getId());

                // Sichere Node-Zählung
                Set<MirrorNode> allNodes = getAllStructureNodes();
                int nodeCount = allNodes.size();
                sb.append(", nodes=").append(nodeCount);

                // Berechne erwartete Links für N-Connected-Topologie
                int expectedLinks = calculateNConnectedLinks(nodeCount, targetLinksPerNode);
                sb.append(", expectedLinks=").append(expectedLinks);

                // Struktur-Typ-Information
                StructureNode.StructureType structureType = getCurrentStructureType();
                if (structureType != null) {
                    sb.append(", structureType=").append(structureType);
                }

            } else {
                sb.append(", status=NOT_INITIALIZED");
            }

            // Network-Information (falls verfügbar)
            if (network != null) {
                try {
                    sb.append(", networkMirrors=").append(network.getMirrors().size());
                    sb.append(", networkLinks=").append(network.getNumLinks());
                } catch (Exception e) {
                    sb.append(", networkInfo=ERROR");
                }
            }

        } catch (Exception e) {
            sb.append(", ERROR=").append(e.getMessage());
        }

        sb.append("}");
        return sb.toString();
    }

    @Override
    protected MirrorNode createMirrorNodeForMirror(Mirror mirror) {
        int linksPerMirror = network != null ? network.getNumTargetLinksPerMirror() : 2;
        return new NConnectedMirrorNode(mirror.getID(), mirror, linksPerMirror);
    }

    /**
     * Validiert die aktuelle N-Connected-Struktur.
     *
     * @return true, wenn die Struktur gültig ist
     */
    @Override
    protected boolean validateTopology() {
        MirrorNode root = getCurrentStructureRoot();
        if (root instanceof NConnectedMirrorNode nConRoot) {
            return nConRoot.isValidStructure();
        }
        return false;
    }
}