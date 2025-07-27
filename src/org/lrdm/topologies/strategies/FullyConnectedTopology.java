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
                newNode.addChild(existingNode);
                existingNode.addChild(newNode);
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
    protected int removeNodesFromStructure(int nodesToRemove) {
        if (nodesToRemove <= 0) return 0;

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

        // Begrenze auf verfügbare Anzahl
        int actualRemovalCount = Math.min(nodesToRemove, candidatesForRemoval.size());
        if (actualRemovalCount == 0) return 0;

        // **FULLY-CONNECTED-SPEZIFISCH**: Sortiere nach ID (höchste zuerst) für deterministische Entfernung
        candidatesForRemoval.sort((node1, node2) -> Integer.compare(node2.getId(), node1.getId()));

        int actuallyRemoved = 0;

        // **NUR PLANUNGSEBENE**: Entferne die ersten N Kandidaten
        for (int i = 0; i < actualRemovalCount; i++) {
            FullyConnectedMirrorNode nodeToRemove = candidatesForRemoval.get(i);

            // 1. **NUR STRUKTURPLANUNG**: Entferne alle StructureNode-Verbindungen
            removeNodeFromFullyConnectedStructuralPlanning(nodeToRemove);

            // 2. Entferne aus BuildAsSubstructure-Verwaltung
            removeFromStructureNodes(nodeToRemove);

            actuallyRemoved++;
        }

        return actuallyRemoved;
    }

    /**
     * **NUR PLANUNGSEBENE**: Entfernt einen Knoten vollständig aus der vollständig vernetzten Struktur.
     * Bereinigt alle bidirektionalen StructureNode-Verbindungen zu anderen Knoten.
     * Arbeitet ohne Zeitbezug - nur strukturelle Fully-Connected-Änderungen.
     *
     * @param nodeToRemove Der zu entfernende FullyConnectedMirrorNode
     */
    private void removeNodeFromFullyConnectedStructuralPlanning(FullyConnectedMirrorNode nodeToRemove) {
        if (nodeToRemove == null) return;

        // Sammle alle verbundenen Knoten vor der Trennung
        Set<StructureNode> connectedNodes = nodeToRemove.getAllNodesInStructure(StructureNode.StructureType.FULLY_CONNECTED,getCurrentStructureRoot());

        // **NUR STRUKTURPLANUNG**: Entferne bidirektionale StructureNode-Verbindungen
        for (StructureNode connectedNode : connectedNodes) {
            // Entferne nodeToRemove aus den Kindern von connectedNode
            connectedNode.removeChild(nodeToRemove);
            // Entferne connectedNode aus den Kindern von nodeToRemove
            nodeToRemove.removeChild(connectedNode);
        }

        // Parent-Verbindung trennen (falls vorhanden)
        if (nodeToRemove.getParent() != null) {
            nodeToRemove.getParent().removeChild(nodeToRemove);
            nodeToRemove.setParent(null);
        }

        // KEINE Mirror-Link-Bereinigung hier! Nur Strukturplanung!
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

    /**
     * Entfernt einen Knoten vollständig aus der vollständig vernetzten Struktur.
     * Bereinigt alle bidirektionalen Verbindungen zu anderen Knoten.
     *
     * @param nodeToRemove Der zu entfernende FullyConnectedMirrorNode
     */
    private void removeNodeFromFullyConnectedStructure(FullyConnectedMirrorNode nodeToRemove) {
        // Sammle alle verbundenen Knoten
        Set<StructureNode> connectedNodes = nodeToRemove.getAllNodesInStructure(StructureNode.StructureType.FULLY_CONNECTED,getCurrentStructureRoot());

        // Entferne bidirektionale Verbindungen
        for (StructureNode connectedNode : connectedNodes) {
            // Entferne nodeToRemove aus den Kindern von connectedNode
            connectedNode.removeChild(nodeToRemove);
            // Entferne connectedNode aus den Kindern von nodeToRemove
            nodeToRemove.removeChild(connectedNode);
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

        // ===== GRUNDLEGENDE INFORMATIONEN =====
        sb.append("type=FULLY_CONNECTED");

        // Netzwerk-Status
        if (network != null) {
            sb.append(", network=").append(network.getClass().getSimpleName());
            sb.append(", mirrors=").append(network.getNumMirrors());
            sb.append(", targetLinks=").append(network.getNumTargetLinks());
            sb.append(", linksPerMirror=").append(network.getNumTargetLinksPerMirror());
        } else {
            sb.append(", network=null");
        }

        // ===== STRUKTUR-INFORMATIONEN =====
        MirrorNode root = getCurrentStructureRoot();
        if (root != null) {
            sb.append(", structureRoot=").append(root.getId());
            sb.append(", rootType=").append(root.getClass().getSimpleName());

            // Head-Status
            if (root.isHead()) {
                sb.append(", head=true");
            }
        } else {
            sb.append(", structureRoot=null");
        }

        // Anzahl der verwalteten StructureNodes
        Collection<MirrorNode> allNodes = getAllStructureNodes();
        if (allNodes != null) {
            sb.append(", structureNodes=").append(allNodes.size());

            // Typ-Verteilung der Knoten
            long fullyConnectedNodes = allNodes.stream()
                    .filter(node -> node instanceof FullyConnectedMirrorNode)
                    .count();
            sb.append(", fullyConnectedNodes=").append(fullyConnectedNodes);
        } else {
            sb.append(", structureNodes=0");
        }

        // ===== TOPOLOGIE-SPEZIFISCHE METRIKEN =====
        if (network != null) {
            int mirrors = network.getNumMirrors();

            // Theoretische vs. tatsächliche Links
            int theoreticalLinks = calculateExpectedLinks(mirrors);
            int actualTargetLinks = network.getNumTargetLinks();
            sb.append(", theoretical=").append(theoreticalLinks);
            sb.append(", actual=").append(actualTargetLinks);

            // Link-Effizienz
            if (theoreticalLinks > 0) {
                double efficiency = (double) actualTargetLinks / theoreticalLinks * 100.0;
                sb.append(", efficiency=").append(String.format("%.1f%%", efficiency));
            }

            // Konnektivitäts-Grad
            if (mirrors > 1) {
                int connectivityDegree = mirrors - 1; // Fully connected = jeder mit jedem anderen
                sb.append(", degree=").append(connectivityDegree);

                // Graph-Dichte (für vollständigen Graph immer 1.0)
                sb.append(", density=1.0");
            }
        }

        // ===== ZUSTANDSINFORMATIONEN =====

        // Mirror-Iterator Status
        if (mirrorIterator != null) {
            sb.append(", mirrorIterator=active");
        } else {
            sb.append(", mirrorIterator=null");
        }

        // Validierung (einfacher Check ohne komplexe Aufrufe)
        boolean isValid = (root != null && allNodes != null && !allNodes.isEmpty());
        sb.append(", valid=").append(isValid);

        // ===== PERFORMANCE-INFORMATIONEN =====
        if (network != null && network.getNumMirrors() > 0) {
            int nodes = network.getNumMirrors();

            // Komplexitäts-Kategorisierung
            String complexity;
            if (nodes <= 10) {
                complexity = "SMALL";
            } else if (nodes <= 50) {
                complexity = "MEDIUM";
            } else if (nodes <= 200) {
                complexity = "LARGE";
            } else {
                complexity = "VERY_LARGE";
            }
            sb.append(", complexity=").append(complexity);

            // Skalierung-Warnung für sehr große Netzwerke
            if (nodes > 100) {
                sb.append(", warning=HIGH_LINK_COUNT");
            }
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
        if (nodeCount <= 1) return 0;
        return nodeCount * (nodeCount - 1) / 2;
    }

}