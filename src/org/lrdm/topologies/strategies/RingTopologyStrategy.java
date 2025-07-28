package org.lrdm.topologies.strategies;

import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.Action;
import org.lrdm.effectors.MirrorChange;
import org.lrdm.effectors.TargetLinkChange;
import org.lrdm.effectors.TopologyChange;
import org.lrdm.topologies.node.MirrorNode;
import org.lrdm.topologies.node.RingMirrorNode;

import java.util.*;

/**
 * Eine spezialisierte {@link TopologyStrategy}, die Mirrors als Ring-Topologie mit einer
 * geschlossenen Schleife verknüpft. Basiert auf {@link LineTopologyStrategy} mit einem
 * zusätzlichen Link vom letzten zum ersten Knoten.
 * <p>
 * **Ring-Topologie-Eigenschaften**:
 * - Jeder Mirror ist mit genau zwei anderen Mirrors verbunden (Vorgänger und Nachfolger)
 * - Bildet eine geschlossene Schleife ohne Anfang oder Ende
 * - Benötigt mindestens 3 Knoten für einen funktionsfähigen Ring
 * - Anzahl der Links ist gleich der Anzahl der Knoten (n Links für n Knoten)
 * - Verwendet {@link RingMirrorNode} für spezifische Ring-Funktionalität
 * <p>
 * **Planungsebene vs. Ausführungsebene**:
 * - Planungsebene: `removeNodesFromStructure()` - plant strukturelle Ring-Änderungen ohne Zeitbezug
 * - Ausführungsebene: `handleRemoveMirrors()` - führt Mirror-Shutdown innerhalb der Ring-Planungsgrenzen aus
 * - Automatisches Mitwachsen: MirrorNode-Ebene passt sich an StructureNode-Ring-Planung an
 * <p>
 * **Ring = Linie + schließender Link**: Ring ist eine Linie, bei der der letzte Knoten
 * mit dem ersten Knoten verbunden wird (geschlossene Schleife).
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class RingTopologyStrategy extends BuildAsSubstructure {

    // ===== RING-SPEZIFISCHE KONFIGURATION =====
    private int minRingSize = 3;
    private boolean allowRingExpansion = true;

    // ===== KONSTRUKTOREN =====

    public RingTopologyStrategy() {
        super();
    }

    public RingTopologyStrategy(int minRingSize) {
        super();
        this.minRingSize = Math.max(3, minRingSize);
    }

    public RingTopologyStrategy(int minRingSize, boolean allowRingExpansion) {
        super();
        this.minRingSize = Math.max(3, minRingSize);
        this.allowRingExpansion = allowRingExpansion;
    }

    // ===== ÜBERSCHREIBUNG DER BUILD-AS-SUBSTRUCTURE-METHODEN =====

    /**
     * **PLANUNGSEBENE**: Erstellt die Ring-Struktur (Linie + schließender Link).
     * Überschreibt BuildAsSubstructure für Ring-spezifische Logik.
     * NUR STRUKTURPLANUNG - keine Mirror-Links!
     */
    @Override
    protected MirrorNode buildStructure(int totalNodes, Properties props) {
        if (totalNodes < minRingSize || !hasNextMirror()) return null;

        // Erstelle alle Ring-Knoten (wie bei Linie)
        List<RingMirrorNode> ringNodes = new ArrayList<>();
        for (int i = 0; i < totalNodes; i++) {
            if (!hasNextMirror()) break;

            Mirror mirror = getNextMirror();
            assert mirror != null;
            RingMirrorNode ringNode = new RingMirrorNode(mirror.getID(), mirror);
            ringNodes.add(ringNode);
            addToStructureNodes(ringNode);
        }

        if (ringNodes.size() < minRingSize) return null;

        // **NUR STRUKTURPLANUNG**: Erstelle Ring-Struktur (Linie + schließender Link)
        buildRingStructurePlanning(ringNodes);

        // Setze erste Knoten als Head und Root
        RingMirrorNode root = ringNodes.get(0);
        root.setHead(true);
        setCurrentStructureRoot(root);

        return root;
    }

    /**
     * **PLANUNGSEBENE**: Fügt neue Knoten zur Ring-Struktur hinzu.
     * Ring-Erweiterung: Neue Knoten werden an beliebiger Stelle im Ring eingefügt.
     * NUR STRUKTURPLANUNG - keine Mirror-Links!
     */
    @Override
    protected int addNodesToStructure(Set<Mirror> nodesToAdd) {
        if (nodesToAdd.isEmpty() || getCurrentStructureRoot() == null || !allowRingExpansion) {
            return 0;
        }

        RingMirrorNode head = getRingHead();
        if (head == null) return 0;

        List<RingMirrorNode> ringNodes = getAllRingNodes();
        if (ringNodes.isEmpty()) return 0;

        int actuallyAdded = 0;
        int insertionIndex = 0;

        // Ring-Erweiterung: Neue Knoten zwischen bestehenden Knoten einfügen
        while (actuallyAdded < nodesToAdd.size() && insertionIndex < ringNodes.size() && hasNextMirror()) {
            RingMirrorNode currentNode = ringNodes.get(insertionIndex);
            RingMirrorNode nextNode = ringNodes.get((insertionIndex + 1) % ringNodes.size());

            // **NUR STRUKTURPLANUNG**: Erstelle neuen Knoten
            RingMirrorNode newNode = createRingNodeForStructure();
            if (newNode != null) {
                // **NUR STRUKTURPLANUNG**: Füge in Ring zwischen current und next ein
                insertNodeIntoRingStructuralPlanning(currentNode, newNode, nextNode);
                ringNodes.add(insertionIndex + 1, newNode); // Update lokale Liste
                actuallyAdded++;
            }

            insertionIndex = (insertionIndex + 2) % ringNodes.size(); // Skip über neu eingefügten Knoten
        }

        return actuallyAdded;
    }

    /**
     * **PLANUNGSEBENE**: Entfernt Knoten aus der Ring-Struktur.
     * Ring-Entfernung: Entfernt beliebige Knoten (außer Head), Ring bleibt geschlossen.
     */
    @Override
    protected int removeNodesFromStructure(int nodesToRemove) {
        if (nodesToRemove <= 0) return 0;

        List<RingMirrorNode> ringNodes = getAllRingNodes();
        if (ringNodes.size() - nodesToRemove < minRingSize) {
            nodesToRemove = ringNodes.size() - minRingSize;
        }
        if (nodesToRemove <= 0) return 0;

        int actuallyRemoved = 0;

        // Ring-Entfernung: Entferne Nicht-Head-Knoten
        for (int i = 0; i < nodesToRemove && !ringNodes.isEmpty(); i++) {
            RingMirrorNode nodeToRemove = selectNodeForRemoval(ringNodes);
            if (nodeToRemove != null) {
                removeNodeFromRingStructuralPlanning(nodeToRemove);
                ringNodes.remove(nodeToRemove);
                actuallyRemoved++;
            }
        }

        return actuallyRemoved;
    }

    @Override
    protected boolean validateTopology() {
        return isRingIntact();
    }

    /**
     * Factory-Methode für Ring-spezifische MirrorNode-Erstellung.
     * Überschreibt BuildAsSubstructure für die RingMirrorNode-Erstellung.
     *
     * @param mirror Der Mirror, für den ein MirrorNode erstellt werden soll
     * @return Neuer RingMirrorNode
     */
    @Override
    protected MirrorNode createMirrorNodeForMirror(Mirror mirror) {
        return new RingMirrorNode(mirror.getID(), mirror);
    }

    /**
     * **AUSFÜHRUNGSEBENE**: Überschreibt die Mirror-Entfernung für Ring-Erhaltung.
     * Führt Mirror-Shutdown innerhalb der strukturellen Ring-Planungsgrenzen aus.
     * Arbeitet komplementär zu removeNodesFromStructure.
     *
     * @param n Das Netzwerk
     * @param removeMirrors Anzahl zu entfernender Mirrors
     * @param props Properties der Simulation
     * @param simTime Aktuelle Simulationszeit
     * @return Set der entfernten Mirrors
     */
    @Override
    public Set<Mirror> handleRemoveMirrors(Network n, int removeMirrors, Properties props, int simTime) {
        if (removeMirrors <= 0) {
            return new HashSet<>();
        }

        List<RingMirrorNode> ringNodes = getAllRingNodes();
        if (ringNodes.size() - removeMirrors < minRingSize) {
            removeMirrors = ringNodes.size() - minRingSize;
        }
        if (removeMirrors <= 0) {
            return new HashSet<>();
        }

        Set<Mirror> cleanedMirrors = new HashSet<>();
        int actuallyRemoved = 0;

        // Ausführungsebene: Ring-bewusste Mirror-Entfernung
        for (int i = 0; i < removeMirrors && !ringNodes.isEmpty(); i++) {
            RingMirrorNode targetNode = selectNodeForRemoval(ringNodes);
            if (targetNode != null) {
                Mirror targetMirror = targetNode.getMirror();
                if (targetMirror != null) {
                    // Mirror-Shutdown auf Ausführungsebene
                    targetMirror.shutdown(simTime);
                    cleanedMirrors.add(targetMirror);
                    actuallyRemoved++;
                    ringNodes.remove(targetNode);
                }
            }
        }

        // Synchronisiere Plannings- und Ausführungsebene
        removeNodesFromStructure(actuallyRemoved);

        return cleanedMirrors;
    }

    // ===== TOPOLOGY STRATEGY METHODEN =====

    /**
     * Gibt die erwartete Anzahl Links für das Netzwerk gemäß Ring-Topologie zurück.
     * Ring-Topologie: n Links für n Knoten (jeder Knoten hat genau 2 Nachbarn).
     *
     * @param n Das Netzwerk
     * @return Anzahl der erwarteten Links für Ring-Topologie
     */
    @Override
    public int getNumTargetLinks(Network n) {
        if (n == null) return 0;
        return calculateExpectedLinks(n.getNumMirrors());
    }

    /**
     * Berechnet die erwartete Anzahl der Links, wenn die gegebene Aktion ausgeführt wird.
     * Ring-spezifische Implementierung basierend auf den drei Action-Typen.
     * Überschreibt die abstrakte Methode aus TopologyStrategy.
     *
     * @param a Die Action, deren Auswirkungen berechnet werden sollen
     * @return Anzahl der erwarteten Links nach Ausführung der Action
     */
    @Override
    public int getPredictedNumTargetLinks(Action a) {
        if (a instanceof MirrorChange mirrorChange) {
            // MirrorChange: Neue Mirror-Anzahl → neue Link-Anzahl
            int newMirrors = mirrorChange.getNewMirrors();
            return calculateExpectedLinks(newMirrors);
        } else if (a instanceof TargetLinkChange targetLinkChange) {
            // TargetLinkChange: Begrenzt durch Ring-Eigenschaft
            Network network = targetLinkChange.getNetwork();
            int maxPossibleLinks = network.getNumMirrors(); // Ring: n Links für n Knoten
            int requestedTotalLinks = targetLinkChange.getNewLinksPerMirror() * network.getNumMirrors();
            return Math.min(requestedTotalLinks, maxPossibleLinks);
        } else if (a instanceof TopologyChange topologyChange) {
            // TopologyChange: Delegiere an neue Strategie
            return topologyChange.getNewTopology().getPredictedNumTargetLinks(a);
        }

        return 0;
    }

    // ===== RING-SPEZIFISCHE HILFSMETHODEN - NUR PLANUNGSEBENE =====

    /**
     * **NUR PLANUNGSEBENE**: Baut die Ring-Struktur auf (Linie + schließender Link).
     * Erstellt KEINE Mirror-Links - nur StructureNode-Verbindungen!
     */
    private void buildRingStructurePlanning(List<RingMirrorNode> ringNodes) {
        if (ringNodes.size() < minRingSize) return;

        // **SCHRITT 1**: Erstelle Linien-Struktur (wie LineTopologyStrategy)
        for (int i = 0; i < ringNodes.size() - 1; i++) {
            RingMirrorNode current = ringNodes.get(i);
            RingMirrorNode next = ringNodes.get(i + 1);

            // StructureNode-Verbindung (keine Mirror-Links!)
            current.addChild(next);
            next.setParent(current);
        }

        // **SCHRITT 2**: Schließe den Ring (letzter → erster Knoten)
        RingMirrorNode lastNode = ringNodes.get(ringNodes.size() - 1);
        RingMirrorNode firstNode = ringNodes.get(0);

        // Ring-Schließung auf StructureNode-Ebene
        lastNode.addChild(firstNode);
        // NICHT: firstNode.setParent(lastNode) - würde Baum-Struktur verletzen!

        // KEINE Mirror-Link-Erstellung hier! Nur Strukturplanung!
    }

    /**
     * **NUR PLANUNGSEBENE**: Erstellt einen neuen Ring-Knoten mit struktureller Planung.
     */
    private RingMirrorNode createRingNodeForStructure() {
        if (!hasNextMirror()) return null;

        Mirror mirror = getNextMirror();
        assert mirror != null;
        RingMirrorNode ringNode = new RingMirrorNode(mirror.getID(), mirror);
        addToStructureNodes(ringNode);

        return ringNode;
    }

    /**
     * **NUR PLANUNGSEBENE**: Fügt einen Knoten in den Ring zwischen zwei bestehenden Knoten ein.
     * Erstellt KEINE Mirror-Links - nur StructureNode-Verbindungen!
     */
    private void insertNodeIntoRingStructuralPlanning(RingMirrorNode current, RingMirrorNode newNode, RingMirrorNode next) {
        if (current == null || newNode == null || next == null) return;

        // Entferne bestehende Verbindung current → next
        current.removeChild(next);

        // Erstelle neue Verbindungen: current → newNode → next
        current.addChild(newNode);
        newNode.setParent(current);
        newNode.addChild(next);
        next.setParent(newNode);

        // KEINE Mirror-Link-Erstellung hier! Nur Strukturplanung!
    }

    /**
     * Wählt einen Knoten für die Entfernung aus dem Ring aus.
     * Bevorzugt Nicht-Head-Knoten, wenn möglich.
     */
    private RingMirrorNode selectNodeForRemoval(List<RingMirrorNode> ringNodes) {
        if (ringNodes.isEmpty()) return null;

        // Bevorzuge Nicht-Head-Knoten
        for (RingMirrorNode node : ringNodes) {
            if (!node.isHead()) {
                return node;
            }
        }

        // Fallback: Erster verfügbarer Knoten (sollte nicht Head sein, wenn minRingSize > 1)
        return ringNodes.get(0);
    }

    /**
     * **PLANUNGSEBENE**: Entfernt Knoten aus der Ring-Struktur-Planung.
     * Stellt sicher, dass der Ring geschlossen bleibt.
     */
    private void removeNodeFromRingStructuralPlanning(RingMirrorNode nodeToRemove) {
        if (nodeToRemove == null) return;

        // Finde Vorgänger und Nachfolger
        RingMirrorNode predecessor = findRingPredecessor(nodeToRemove);
        RingMirrorNode successor = findRingSuccessor(nodeToRemove);

        if (predecessor != null && successor != null) {
            // Entferne Knoten aus der Kette
            predecessor.removeChild(nodeToRemove);
            nodeToRemove.removeChild(successor);

            // Verbinde Vorgänger direkt mit Nachfolger (Ring bleibt geschlossen)
            predecessor.addChild(successor);
            if (successor.getParent() == nodeToRemove) {
                successor.setParent(predecessor);
            }
        }

        // Entferne Knoten aus der Struktur
        removeFromStructureNodes(nodeToRemove);
    }

    /**
     * Findet den Vorgänger eines Ring-Knotens.
     */
    private RingMirrorNode findRingPredecessor(RingMirrorNode node) {
        if (node == null) return null;
        return (RingMirrorNode) node.getParent();
    }

    /**
     * Findet den Nachfolger eines Ring-Knotens.
     */
    private RingMirrorNode findRingSuccessor(RingMirrorNode node) {
        if (node == null || node.getChildren().isEmpty()) return null;
        return (RingMirrorNode) node.getChildren().iterator().next();
    }

    /**
     * Prüft, ob die Ring-Struktur intakt ist.
     */
    private boolean isRingIntact() {
        List<RingMirrorNode> ringNodes = getAllRingNodes();
        if (ringNodes.size() < minRingSize) return false;

        // Prüfe, ob jeder Knoten genau einen Vorgänger und einen Nachfolger hat
        for (RingMirrorNode node : ringNodes) {
            if (node.getChildren().size() != 1) return false;
            // Parent-Check ist für Ring-Schließung komplexer, da der erste Knoten
            // könnte zwei "Parents" haben (strukturell)
        }

        return true;
    }

    /**
     * Berechnet die erwartete Link-Anzahl für eine gegebene Knotenzahl.
     * Ring-Topologie: n Links für n Knoten (jeder Knoten hat genau 2 Nachbarn).
     */
    private int calculateExpectedLinks(int nodeCount) {
        if (nodeCount < minRingSize) return 0;
        return nodeCount; // Ring: n Links für n Knoten
    }

    // ===== TYPSICHERE HILFSMETHODEN =====

    /**
     * Gibt den Ring-Head zurück.
     */
    private RingMirrorNode getRingHead() {
        MirrorNode root = getCurrentStructureRoot();
        return (root instanceof RingMirrorNode) ? (RingMirrorNode) root : null;
    }

    /**
     * Gibt alle Ring-Knoten als typisierte Liste zurück.
     */
    private List<RingMirrorNode> getAllRingNodes() {
        return getAllStructureNodes().stream()
                .filter(node -> node instanceof RingMirrorNode)
                .map(node -> (RingMirrorNode) node)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public String toString() {
        return "RingTopologyStrategy{" +
                "minRingSize=" + minRingSize +
                ", allowRingExpansion=" + allowRingExpansion +
                ", nodes=" + getAllRingNodes().size() +
                '}';
    }
}