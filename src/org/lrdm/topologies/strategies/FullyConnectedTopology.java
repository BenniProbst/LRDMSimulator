package org.lrdm.topologies.strategies;

import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.*;
import org.lrdm.topologies.node.FullyConnectedMirrorNode;
import org.lrdm.topologies.node.MirrorNode;
import org.lrdm.topologies.node.StructureNode;

import java.util.*;
import java.util.stream.Collectors;

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
     * @return Die Root-Node der erstellten Struktur
     */
    @Override
    protected MirrorNode buildStructure(int totalNodes) {
        if (totalNodes <= 0 || network == null) {
            return null;
        }

        List<FullyConnectedMirrorNode> nodes = new ArrayList<>();

        // 1. Erstelle FullyConnectedMirrorNodes mit Mirror-Zuordnung
        for (int i = 0; i < totalNodes; i++) {
            FullyConnectedMirrorNode node = getMirrorNodeFromIterator();
            nodes.add(node);
            addToStructureNodes(node); // Registriere bei BuildAsSubstructure
            if(node.getMirror().isRoot()) {
                setCurrentStructureRoot(node);
                node.setHead(StructureNode.StructureType.FULLY_CONNECTED,true);
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
        for (FullyConnectedMirrorNode sourceNode:nodes) {
            for (FullyConnectedMirrorNode targetNode:nodes) {
                if (sourceNode == targetNode) continue;
                // Füge bidirektionale StructureNode-Verbindung hinzu
                sourceNode.addChild(targetNode);
                targetNode.addChild(sourceNode);
                if(sourceNode!=root)sourceNode.setParent(root);
                if(targetNode!=root)targetNode.setParent(root);
            }
        }

        root.setParent(null);

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
    protected int addNodesToStructure(Set<Mirror> nodesToAdd) {
        if (nodesToAdd.isEmpty() || getCurrentStructureRoot() == null) {
            return 0;
        }

        List<FullyConnectedMirrorNode> existingNodes = getAllStructureNodes().stream()
                .filter(node -> node instanceof FullyConnectedMirrorNode)
                .map(node -> (FullyConnectedMirrorNode) node)
                .toList();

        List<FullyConnectedMirrorNode> newNodes = new ArrayList<>();
        int actuallyAdded = 0;

        List<Mirror> tmpMirrorIterate = new ArrayList<>(nodesToAdd);
        setMirrorIterator(tmpMirrorIterate.iterator());
        // Erstelle neue FullyConnectedMirrorNodes - Verwende das saubere Interface
        for (int i = 0; i < nodesToAdd.size(); i++) {
            FullyConnectedMirrorNode newNode = getMirrorNodeFromIterator();
            newNodes.add(newNode);
            addToStructureNodes(newNode);
            actuallyAdded++;
        }

        FullyConnectedMirrorNode root = existingNodes.stream()
                .filter(node -> node.getMirror() != null && node.isRoot())
                .findFirst()
                .orElse(null);

        if (root == null) {
            throw new IllegalStateException("No root node found in FullyConnected structure");
        }

        // Verbinde jeden neuen Knoten mit allen bestehenden Knoten
        for (FullyConnectedMirrorNode newNode : newNodes) {
            for (FullyConnectedMirrorNode existingNode : existingNodes) {
                // Bidirektionale StructureNode-Verbindungen
                existingNode.addChild(newNode);
                newNode.addChild(existingNode);
                if(newNode!=root)newNode.setParent(root);
                if(existingNode!=root)existingNode.setParent(root);
            }

            // Verbinde neue Knoten auch untereinander
            for (FullyConnectedMirrorNode otherNewNode : newNodes) {
                if (!newNode.equals(otherNewNode)) {
                    newNode.addChild(otherNewNode);
                    otherNewNode.addChild(newNode);
                    if(newNode!=root)newNode.setParent(root);
                    if(otherNewNode!=root)otherNewNode.setParent(root);
                }
            }
        }

        root.setParent(null);

        return actuallyAdded;
    }


    /**
     * **PLANUNGSEBENE**: Entfernt Knoten aus der vollständig vernetzten Struktur.
     * Fully-Connected-Entfernung: Entfernt bevorzugt Knoten mit höchsten IDs (deterministische Auswahl).
     * Da alle Nicht-Root-Knoten gleichwertig sind, werden Knoten nach ID-Priorität entfernt.
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

        List<FullyConnectedMirrorNode> fullyConnectedNodes = getAllFullyConnectedNodes();

        // Sammle alle Knoten außer Root für die Entfernung
        List<FullyConnectedMirrorNode> candidatesForRemoval = fullyConnectedNodes.stream()
                .filter(node -> node != getCurrentStructureRoot()) // Root nie entfernen
                .filter(node -> !node.isHead()) // Head-Knoten bevorzugt beibehalten
                .collect(Collectors.toList());

        // Fallback: Wenn keine Nicht-Head-Kandidaten verfügbar, verwende alle Nicht-Root-Knoten
        if (candidatesForRemoval.isEmpty()) {
            candidatesForRemoval = fullyConnectedNodes.stream()
                    .filter(node -> node != getCurrentStructureRoot())
                    .collect(Collectors.toList());
        }
        candidatesForRemoval = candidatesForRemoval.stream()
                .limit(nodesToRemove)
                .collect(Collectors.toList());

        // Begrenze auf verfügbare Anzahl
        int actualRemovalCount = Math.min(nodesToRemove, candidatesForRemoval.size());
        if (actualRemovalCount == 0) return new HashSet<>();

        // **FULLY-CONNECTED-SPEZIFISCH**: Sortiere nach ID (höchste zuerst) für deterministische Entfernung
        candidatesForRemoval.sort((node1, node2) -> Integer.compare(node2.getId(), node1.getId()));

        Set<MirrorNode> removedNodes = new HashSet<>();
        FullyConnectedMirrorNode root = (FullyConnectedMirrorNode) getCurrentStructureRoot();

        // **NUR PLANUNGSEBENE**: Entferne die ersten N Kandidaten
        for (FullyConnectedMirrorNode nodeToRemove : candidatesForRemoval) {
            for (StructureNode existingNode : nodeToRemove.getChildren(StructureNode.StructureType.FULLY_CONNECTED,root.getId())) {
                existingNode.removeChild(nodeToRemove);
            }
            for(StructureNode childNode:nodeToRemove.getChildren(StructureNode.StructureType.FULLY_CONNECTED,root.getId())) {
                nodeToRemove.removeChild(childNode);
            }

            removeFromStructureNodes(nodeToRemove);
            removedNodes.add(nodeToRemove);
        }

        return removedNodes;
    }

// ===== TYPSICHERE HILFSMETHODEN =====

    /**
     * Gibt alle Fully-Connected-Knoten als typisierte Liste zurück.
     * Erweitert die BuildAsSubstructure-Funktionalität um Typ-Sicherheit.
     *
     * @return Liste aller FullyConnectedMirrorNodes in der Struktur
     */
    private List<FullyConnectedMirrorNode> getAllFullyConnectedNodes() {
        return getAllStructureNodes().stream()
                .filter(node -> node instanceof FullyConnectedMirrorNode)
                .map(node -> (FullyConnectedMirrorNode) node)
                .collect(Collectors.toList());
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

    /**
     * Factory-Methode für baum-spezifische MirrorNode-Erstellung.
     * Überschreibt BuildAsSubstructure für die TreeMirrorNode-Erstellung.
     *
     * @param mirror Der Mirror, für den ein MirrorNode erstellt werden soll
     * @return Neuer TreeMirrorNode
     */
    @Override
    protected MirrorNode createMirrorNodeForMirror(Mirror mirror) {
        return new FullyConnectedMirrorNode(mirror.getID(), mirror);
    }

    /**
     * Erstellt einen neuen MirrorNode mit Mirror aus dem Iterator.
     * AKTUALISIERT: Fügt den Knoten automatisch zu structureNodes hinzu.
     *
     * @return Neuer MirrorNode mit zugeordnetem Mirror oder null
     */
    @Override
    protected FullyConnectedMirrorNode getMirrorNodeFromIterator() {
        if (mirrorIterator != null && mirrorIterator.hasNext()) {
            FullyConnectedMirrorNode node = (FullyConnectedMirrorNode) super.getMirrorNodeFromIterator();
            node.addNodeType(StructureNode.StructureType.FULLY_CONNECTED);
            return node;
        }
        return null;
    }

    /**
     * Liefert eine detaillierte String-Repräsentation der FullyConnectedTopology.
     * Zeigt Topologie-Status, Struktur-Informationen und Netzwerk-Metriken.
     * Debugger-freundlich: Vermeidet komplexe Methodenaufrufe, die Breakpoint-Probleme verursachen könnten.
     *
     * @return Formatierte String-Darstellung der aktuellen Topologie
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("FullyConnectedTopology{");

        try {
            // Grundlegende Topology-Information
            sb.append("substructureId=").append(getSubstructureId());

            // Sichere Struktur-Informationen
            MirrorNode root = getCurrentStructureRoot();
            if (root != null) {
                sb.append(", rootId=").append(root.getId());

                // Sichere Node-Zählung
                Set<MirrorNode> allNodes = getAllStructureNodes();
                int nodeCount = allNodes.size();
                sb.append(", nodes=").append(nodeCount);

                // Berechne erwartete Links für vollständig vernetzte Topologie
                int expectedLinks = calculateExpectedLinks(nodeCount);
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

    /**
     * Berechnet die erwartete Link-Anzahl für eine gegebene Knotenzahl.
     * Für vollständig vernetzte Topologie: n*(n-1)/2 Links für n Knoten.
     * Hilfsmethode für toString() - statisch um Debugger-Probleme zu vermeiden.
     *
     * @param nodeCount Anzahl der Knoten
     * @return Erwartete Anzahl der Links für vollständig vernetzte Topologie
     */
    private static int calculateExpectedLinks(int nodeCount) {
        if (nodeCount <= 1) {
            return 0;
        }
        return (nodeCount * (nodeCount - 1)) / 2;
    }

}