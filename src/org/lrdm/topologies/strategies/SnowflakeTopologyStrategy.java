
package org.lrdm.topologies.strategies;

import org.graphstream.ui.swing.util.AttributeUtils;
import org.junit.jupiter.params.aggregator.ArgumentAccessException;
import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.*;
import org.lrdm.topologies.node.*;
import org.lrdm.util.IDGenerator;

import java.util.*;

/**
 * Schneeflocken-Topologie-Strategie mit hierarchischer Recursions-Architektur.
 * <p>
 * REKURSIVE 3-EBENEN-STRUKTUR:
 * - Ebene 1: SnowflakeTopologyStrategy (Kern-Verwaltung)
 * - Ebene 2: RingTopologyStrategy (Kern-Ringe) + LineTopologyStrategy (externe Verbindungen)
 * - Ebene 3: RingTopologyStrategy + StarTopologyStrategy (terminale Anhänge)
 * <p>
 * NUTZT BuildAsSubstructure-VERERBUNG:
 * - nodeToSubstructure aus Basisklasse für alle Substruktur-Zuordnungen
 * - structureNodes aus Basisklasse für alle Knoten-Verwaltung
 * - Keine redundanten Maps oder Listen nötig
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class SnowflakeTopologyStrategy extends BuildAsSubstructure {

    // ===== SCHNEEFLOCKEN-PARAMETER =====
    private static final int RING_BRIDGE_STEP_ON_RING = 3;
    private static final int RING_BRIDGE_OFFSET = 1;
    private static final int RING_BRIDGE_MIRROR_NUM_HEIGHT = 2;
    private static final int MAX_RING_LAYERS = 3;
    private static final int MINIMAL_RING_MIRROR_COUNT = 3;
    private static final int EXTERN_STAR_MAX_TREE_DEPTH = 2;
    private static final int BRIDGE_TO_EXTERN_STAR_DISTANCE = 1;
    private static final double EXTERN_STAR_RATIO = 0.4;

    // ===== SCHNEEFLOCKEN-SPEZIFISCHE VERWALTUNG =====
    private int currentRingLayer = 0;
    private final List<MirrorNode> bridgeNodes = new ArrayList<>();

    // ===== KONSTRUKTOREN =====

    public SnowflakeTopologyStrategy() {
        super();
    }

    // ===== KERN-STRUKTURAUFBAU =====

    /**
     * Erstellt die Schneeflocken-Struktur mit verschachtelten Ringen und externen Anhängen.
     * NUTZT BuildAsSubstructure.nodeToSubstructure für alle Substruktur-Zuordnungen.
     */
    @Override
    protected MirrorNode buildStructure(int totalNodes, int simTime, Properties props) {
        if (totalNodes < getMinimumRequiredMirrors()) {
            throw new ArgumentAccessException(
                    "Snowflake benötigt mindestens " + getMinimumRequiredMirrors() + " Knoten"
            );
        }

        // 1. Berechne Mirror-Verteilung
        AttributeUtils.Tuple<Integer, Integer> distribution = snowflakeDistribution(totalNodes);
        int mirrorsForRings = distribution.x;
        int mirrorsForExternal = distribution.y;

        // 2. Erstelle eine Kern-Ring-Struktur
        MirrorNode rootNode = buildCoreRingStructure(mirrorsForRings, simTime, props);

        // 3. Erstelle externe Substrukturen
        attachExternalSubstructures(mirrorsForExternal, simTime, props);

        return rootNode;
    }

    /**
     * Erstellt die Kern-Ring-Struktur mit Bridge-Verbindungen.
     * Nutzt BuildAsSubstructure.buildStructure() für konsistente Substruktur-Erstellung.
     */
    private MirrorNode buildCoreRingStructure(int totalMirrors, int simTime, Properties props) {
        int mirrorsPerRing = Math.max(MINIMAL_RING_MIRROR_COUNT, totalMirrors / MAX_RING_LAYERS);
        int remainingMirrors = totalMirrors;
        MirrorNode snowflakeRoot = null;
        MirrorNode previousRingRoot = null;

        // Erstelle Ring-Schichten von innen nach außen
        for (currentRingLayer = 0; currentRingLayer < MAX_RING_LAYERS && remainingMirrors >= MINIMAL_RING_MIRROR_COUNT; currentRingLayer++) {
            int mirrorsForThisRing = Math.min(mirrorsPerRing, remainingMirrors);

            // Erstelle eine Ring-Substruktur direkt mit BuildAsSubstructure-Pattern
            RingTopologyStrategy ringStrategy = new RingTopologyStrategy(MINIMAL_RING_MIRROR_COUNT);

            // Setze verfügbare Mirrors für Ring-Strategie
            List<Mirror> ringMirrors = new ArrayList<>();
            for (int i = 0; i < mirrorsForThisRing && mirrorIterator.hasNext(); i++) {
                ringMirrors.add(mirrorIterator.next());
            }

            // Initialisiere Ring-Strategy mit verfügbaren Mirrors
            ringStrategy.network = this.network;
            ringStrategy.mirrorIterator = ringMirrors.iterator();

            // Nutze buildStructure() aus BuildAsSubstructure
            MirrorNode ringRoot = ringStrategy.buildStructure(mirrorsForThisRing, simTime, props);

            // Registriere alle Ring-Knoten in der Hauptstruktur
            for (MirrorNode ringNode : ringStrategy.getAllStructureNodes()) {
                this.addToStructureNodes(ringNode);
                this.setSubstructureForNode(ringNode, ringStrategy);
            }

            // Setze Root für innerste Ring-Schicht
            if (currentRingLayer == 0) {
                snowflakeRoot = ringRoot;
                this.setCurrentStructureRoot(snowflakeRoot);
            }

            // Erstelle Bridge-Verbindungen zur vorherigen Ring-Schicht
            if (previousRingRoot != null) {
                createBridgeConnectionBetweenRings(previousRingRoot, ringRoot, simTime, props);
            }

            previousRingRoot = ringRoot;
            remainingMirrors -= mirrorsForThisRing;
        }

        return snowflakeRoot;
    }


    /**
     * Erstellt eine Bridge-Verbindung zwischen zwei Ring-Schichten.
     */
    private void createBridgeConnectionBetweenRings(MirrorNode innerRing, MirrorNode outerRing,
                                                    int simTime, Properties props) {
        // Finde geeignete Bridge-Knoten basierend auf RING_BRIDGE_STEP_ON_RING
        MirrorNode fromNode = findBridgeNodeInRing(innerRing, 0);
        MirrorNode toNode = findBridgeNodeInRing(outerRing, RING_BRIDGE_OFFSET);

        if (fromNode != null && toNode != null) {
            createConnectionLink(fromNode, toNode, simTime, props);
            bridgeNodes.add(fromNode);
            bridgeNodes.add(toNode);
        }
    }

    /**
     * Findet einen geeigneten Bridge-Knoten in einem Ring.
     */
    private MirrorNode findBridgeNodeInRing(MirrorNode ringRoot, int offset) {
        // Vereinfachte Bridge-Knoten-Findung - in echter Implementierung
        // würde hier die Ring-Struktur traversiert
        return ringRoot; // Für Einfachheit: Root als Bridge-Punkt
    }
    /**
     * Erstellt externe Substrukturen an Bridge-Verbindungen.
     * Nutzt BuildAsSubstructure.buildStructure() für konsistente Substruktur-Erstellung.
     */
    private void attachExternalSubstructures(int mirrorsForExternal, int simTime, Properties props) {
        if (mirrorsForExternal <= 0 || bridgeNodes.isEmpty()) return;

        int mirrorsPerBridge = Math.max(2, mirrorsForExternal / bridgeNodes.size());
        int remainingMirrors = mirrorsForExternal;

        for (MirrorNode bridgeNode : bridgeNodes) {
            if (remainingMirrors <= 0) break;

            int mirrorsForThisBridge = Math.min(mirrorsPerBridge, remainingMirrors);

            // Erstelle Line-Substruktur direkt mit BuildAsSubstructure-Pattern
            LineTopologyStrategy lineStrategy = new LineTopologyStrategy(2, true);

            // Setze verfügbare Mirrors für Line-Strategie
            List<Mirror> lineMirrors = new ArrayList<>();
            for (int i = 0; i < mirrorsForThisBridge && mirrorIterator.hasNext(); i++) {
                lineMirrors.add(mirrorIterator.next());
            }

            // Initialisiere Line-Strategy mit verfügbaren Mirrors
            lineStrategy.network = this.network;
            lineStrategy.mirrorIterator = lineMirrors.iterator();

            // Nutze buildStructure() aus BuildAsSubstructure
            MirrorNode lineRoot = lineStrategy.buildStructure(mirrorsForThisBridge, simTime, props);

            // Registriere Line-Knoten in der Hauptstruktur
            for (MirrorNode lineNode : lineStrategy.getAllStructureNodes()) {
                this.addToStructureNodes(lineNode);
                this.setSubstructureForNode(lineNode, lineStrategy);
            }

            // Verbinde Line-Struktur mit Bridge-Knoten
            if (lineRoot != null) {
                createConnectionLink(bridgeNode, lineRoot, simTime, props);
            }

            remainingMirrors -= mirrorsForThisBridge;
        }
    }


    /**
     * Berechnet die Verteilung der Mirrors auf Kern-Ringe vs. externe Strukturen.
     */
    private AttributeUtils.Tuple<Integer, Integer> snowflakeDistribution(int totalNodes) {
        int externalMirrors = (int) Math.round(totalNodes * EXTERN_STAR_RATIO);
        int ringMirrors = totalNodes - externalMirrors;

        // Stelle sicher, dass genügend Mirrors für die Grundstruktur vorhanden sind
        int minRingMirrors = MINIMAL_RING_MIRROR_COUNT * MAX_RING_LAYERS;
        if (ringMirrors < minRingMirrors) {
            ringMirrors = minRingMirrors;
            externalMirrors = Math.max(0, totalNodes - ringMirrors);
        }

        return new AttributeUtils.Tuple<>(ringMirrors, externalMirrors);
    }

    /**
     * Erstellt eine Link-Verbindung zwischen zwei Knoten.
     */
    private void createConnectionLink(MirrorNode from, MirrorNode to, int simTime, Properties props) {
        if (from == null || to == null) return;

        Mirror fromMirror = from.getMirror();
        Mirror toMirror = to.getMirror();

        if (fromMirror != null && toMirror != null) {
            Link link = new Link(IDGenerator.getInstance().getNextID(), fromMirror, toMirror, simTime, props);
            fromMirror.addLink(link);
            toMirror.addLink(link);

            // Füge auch StructureNode-Verbindung hinzu
            from.addChild(to, Set.of(StructureNode.StructureType.MIRROR),
                    Map.of(StructureNode.StructureType.MIRROR, this.getSubstructureId()));
        }
    }

    /**
     * Berechnet die minimale Anzahl von Mirrors für eine funktionsfähige Schneeflocke.
     */
    private int getMinimumRequiredMirrors() {
        return MINIMAL_RING_MIRROR_COUNT * MAX_RING_LAYERS + 2; // Minimale Ringe + externe Strukturen
    }

// ===== ÜBERSCHREIBUNG DER BUILD-AS-SUBSTRUCTURE-METHODEN =====

    /**
     * Fügt neue Knoten zur Schneeflocken-Struktur hinzu.
     * Nutzt die registrierten Substrukturen für gezielte Erweiterungen.
     */
    @Override
    protected int addNodesToStructure(int nodesToAdd) {
        if (nodesToAdd <= 0) return 0;

        int addedNodes = 0;

        // Erweitere zuerst die Kern-Ringe über die Substruktur-Registry
        for (MirrorNode node : new ArrayList<>(this.getAllStructureNodes())) {
            // KORREKT: Direkter Zugriff auf nodeToSubstructure Map über protected Methoden
            if (getNodeToSubstructureMapping().containsKey(node)) {
                BuildAsSubstructure substructure = findSubstructureForNode(node);

                if (substructure instanceof RingTopologyStrategy && addedNodes < nodesToAdd) {
                    int ringAdditions = Math.min(nodesToAdd - addedNodes, 2);
                    addedNodes += substructure.addNodesToStructure(ringAdditions);

                    // Registriere neue Knoten in der Hauptstruktur
                    for (MirrorNode newNode : substructure.getAllStructureNodes()) {
                        if (!this.getAllStructureNodes().contains(newNode)) {
                            this.addToStructureNodes(newNode);
                            this.setSubstructureForNode(newNode, substructure);
                        }
                    }
                }
            }
        }

        // Erweitere dann die externen Strukturen
        if (addedNodes < nodesToAdd) {
            for (MirrorNode node : new ArrayList<>(this.getAllStructureNodes())) {
                if (getNodeToSubstructureMapping().containsKey(node)) {
                    BuildAsSubstructure substructure = findSubstructureForNode(node);

                    if (substructure instanceof LineTopologyStrategy && addedNodes < nodesToAdd) {
                        int lineAdditions = Math.min(nodesToAdd - addedNodes, 3);
                        addedNodes += substructure.addNodesToStructure(lineAdditions);

                        // Registriere neue Knoten in der Hauptstruktur
                        for (MirrorNode newNode : substructure.getAllStructureNodes()) {
                            if (!this.getAllStructureNodes().contains(newNode)) {
                                this.addToStructureNodes(newNode);
                                this.setSubstructureForNode(newNode, substructure);
                            }
                        }
                    }
                }
            }
        }

        return addedNodes;
    }



    /**
     * Entfernt Knoten aus der Schneeflocken-Struktur.
     * Entfernt zuerst aus externen Strukturen, dann aus Kern-Ringen.
     */
    @Override
    protected int removeNodesFromStructure(int nodesToRemove) {
        if (nodesToRemove <= 0) return 0;

        int removedNodes = 0;

        // Entferne zuerst aus externen Strukturen
        for (MirrorNode node : new ArrayList<>(this.getAllStructureNodes())) {
            if (getNodeToSubstructureMapping().containsKey(node)) {
                BuildAsSubstructure substructure = findSubstructureForNode(node);

                if (substructure instanceof LineTopologyStrategy && removedNodes < nodesToRemove) {
                    int lineRemovals = Math.min(nodesToRemove - removedNodes, 2);
                    removedNodes += substructure.removeNodesFromStructure(lineRemovals);

                    // Entferne gelöschte Knoten aus der Hauptstruktur
                    this.getAllStructureNodes().removeIf(n -> !substructure.getAllStructureNodes().contains(n));
                }
            }
        }

        // Entferne dann aus Kern-Ringen (falls nötig)
        if (removedNodes < nodesToRemove) {
            for (MirrorNode node : new ArrayList<>(this.getAllStructureNodes())) {
                if (getNodeToSubstructureMapping().containsKey(node)) {
                    BuildAsSubstructure substructure = findSubstructureForNode(node);

                    if (substructure instanceof RingTopologyStrategy && removedNodes < nodesToRemove) {
                        int ringRemovals = Math.min(nodesToRemove - removedNodes, 1);
                        removedNodes += substructure.removeNodesFromStructure(ringRemovals);

                        // Entferne gelöschte Knoten aus der Hauptstruktur
                        this.getAllStructureNodes().removeIf(n -> !substructure.getAllStructureNodes().contains(n));
                    }
                }
            }
        }

        return removedNodes;
    }

    /**
     * Erstellt die tatsächlichen Links zwischen allen Substrukturen.
     */
    @Override
    protected Set<Link> buildAndConnectLinks(MirrorNode root, Properties props) {
        Set<Link> allLinks = new HashSet<>();

        // Sammle Links von allen Substrukturen
        for (MirrorNode node : this.getAllStructureNodes()) {
            BuildAsSubstructure substructure = this.findSubstructureForNode(node);
            if (substructure != null) {
                Set<Link> substructureLinks = substructure.buildAndConnectLinks(node, props);
                allLinks.addAll(substructureLinks);
            }
        }

        return allLinks;
    }

// ===== TOPOLOGY STRATEGY INTERFACE IMPLEMENTATION =====

    /**
     * Initialisiert das Netzwerk mit Schneeflocken-Topologie.
     */
    @Override
    public Set<Link> initNetwork(Network n, Properties props) {
        this.network = n;
        this.mirrorIterator = n.getMirrors().iterator();

        int totalMirrors = n.getNumMirrors();
        if (totalMirrors < getMinimumRequiredMirrors()) {
            throw new ArgumentAccessException(
                    "Snowflake benötigt mindestens " + getMinimumRequiredMirrors() + " Mirrors"
            );
        }

        // Erstelle Struktur und Links
        MirrorNode root = buildStructure(totalMirrors, 0, props);
        this.setCurrentStructureRoot(root);

        return buildAndConnectLinks(root, props);
    }

    /**
     * Fügt neue Mirrors zur Schneeflocken-Struktur hinzu.
     */
    @Override
    public void handleAddNewMirrors(Network n, int newMirrors, Properties props, int simTime) {
        this.network = n;
        List<Mirror> availableMirrors = n.getMirrors().subList(
                n.getNumMirrors() - newMirrors, n.getNumMirrors()
        );
        this.mirrorIterator = availableMirrors.iterator();

        addNodesToStructure(newMirrors);
    }

    /**
     * Berechnet die erwartete Anzahl von Links für die Schneeflocken-Topologie.
     */
    @Override
    public int getNumTargetLinks(Network n) {
        int totalMirrors = n.getNumMirrors();
        if (totalMirrors < getMinimumRequiredMirrors()) return 0;

        AttributeUtils.Tuple<Integer, Integer> distribution = snowflakeDistribution(totalMirrors);
        int ringMirrors = distribution.x;
        int externalMirrors = distribution.y;

        // Ring-Links: n Links pro Ring
        int ringLinks = ringMirrors;

        // Bridge-Links zwischen Ringen
        int bridgeLinks = MAX_RING_LAYERS - 1;

        // External Line-Links: (n-1) Links pro Line
        int externalLinks = Math.max(0, externalMirrors - bridgeNodes.size());

        // Bridge-zu-External-Links
        int bridgeToExternalLinks = Math.min(bridgeNodes.size(), externalMirrors > 0 ? 1 : 0);

        return ringLinks + bridgeLinks + externalLinks + bridgeToExternalLinks;
    }

    /**
     * Berechnet die vorhergesagte Anzahl von links nach einer Aktion.
     */
    @Override
    public int getPredictedNumTargetLinks(Action a) {
        if (a instanceof MirrorChange) {
            MirrorChange mc = (MirrorChange) a;
            int predictedMirrors = network.getNumMirrors() + mc.getNewMirrors();
            return calculateLinksForMirrorCount(predictedMirrors);
        }
        return getNumTargetLinks(network);
    }

    /**
     * Eine Hilfsmethode zur Berechnung der Links für eine gegebene Mirror-Anzahl.
     */
    private int calculateLinksForMirrorCount(int mirrorCount) {
        if (mirrorCount < getMinimumRequiredMirrors()) return 0;

        AttributeUtils.Tuple<Integer, Integer> distribution = snowflakeDistribution(mirrorCount);
        int ringMirrors = distribution.x;
        int externalMirrors = distribution.y;

        return ringMirrors + (MAX_RING_LAYERS - 1) + Math.max(0, externalMirrors - 1);
    }

    @Override
    public String toString() {
        return "SnowflakeTopologyStrategy{" +
                "maxRingLayers=" + MAX_RING_LAYERS +
                ", minRingMirrors=" + MINIMAL_RING_MIRROR_COUNT +
                ", externalRatio=" + EXTERN_STAR_RATIO +
                ", substructureId=" + getSubstructureId() +
                "}";
    }
}