package org.lrdm.topologies.strategies;

import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.*;
import org.lrdm.topologies.node.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Eine spezialisierte {@link BuildAsSubstructure}, die Mirrors in einem tiefen-beschränkten Baum
 * mit einer maximalen Tiefe verbindet. Diese Strategie erweitert die standard Baum-Topologie
 * durch eine konfigurierbare Tiefenbeschränkung.
 * <p>
 * **Tiefen-beschränkter Baum-Eigenschaften**:
 * - Baumstruktur mit hierarchischer Organisation (keine Zyklen)
 * - Maximale Tiefe ist konfigurierbar (Standard: 3)
 * - Jeder Knoten kann beliebig viele Kinder haben (keine Verzweigungsbeschränkung)
 * - Bevorzugt gleichmäßige Verteilung mit Depth-First-Wachstum innerhalb der Tiefenbeschränkung
 * - Verwendet {@link DepthLimitTreeTopologyStrategy} für tiefenspezifische Funktionalität
 * <p>
 * **Tiefenmanagement**:
 * - Root-Knoten hat Tiefe 0
 * - Jede Ebene erhöht die Tiefe um 1
 * - Knoten auf maximaler Tiefe können keine Kinder haben
 * - Neue Knoten werden bevorzugt an tieferen, aber nicht maximalen Positionen eingefügt
 * - Gleichmäßige Verteilung: Jeder Knoten kann potentiell unendlich viele Kinder haben
 * <p>
 * **Planungsebene vs. Ausführungsebene**:
 * - Planungsebene: `removeNodesFromStructure()` - plant strukturelle Änderungen ohne Zeitbezug
 * - Ausführungsebene: `handleRemoveMirrors()` - führt Mirror-Shutdown innerhalb der Planungsgrenzen aus
 * - Automatisches Mitwachsen: MirrorNode-Ebene passt sich an StructureNode-Planung an
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class DepthLimitTreeTopologyStrategy extends TreeTopologyStrategy {

    // ===== TIEFENBESCHRÄNKUNGS-KONFIGURATION =====

    /** Maximale Tiefe des Baums (Standard: 3) */
    private int maxDepth = 3;

    /** Ob automatische Tiefenoptimierung aktiviert ist */
    private boolean enableDepthOptimization = true;

    /** Bevorzugte Strategie für Knoteneinfügung */
    private DepthInsertionStrategy insertionStrategy = DepthInsertionStrategy.DEPTH_FIRST;

    // ===== KONSTRUKTOREN =====

    /**
     * Standard-Konstruktor mit Standardwerten.
     * maxDepth=3, enableDepthOptimization=true, insertionStrategy=DEPTH_FIRST
     */
    public DepthLimitTreeTopologyStrategy() {
        super();
    }

    /**
     * Konstruktor mit konfigurierbarer maximaler Tiefe.
     *
     * @param maxDepth Maximale Tiefe des Baums (mindestens 1)
     */
    public DepthLimitTreeTopologyStrategy(int maxDepth) {
        super();
        this.maxDepth = Math.max(1, maxDepth);
    }

    /**
     * Konstruktor mit maximaler Tiefe und Optimierungseinstellung.
     *
     * @param maxDepth Maximale Tiefe des Baums (mindestens 1)
     * @param enableDepthOptimization Ob automatische Tiefenoptimierung aktiviert ist
     */
    public DepthLimitTreeTopologyStrategy(int maxDepth, boolean enableDepthOptimization) {
        super();
        this.maxDepth = Math.max(1, maxDepth);
        this.enableDepthOptimization = enableDepthOptimization;
    }

    /**
     * Vollständiger Konstruktor mit allen Konfigurationsoptionen.
     *
     * @param maxDepth Maximale Tiefe des Baums (mindestens 1)
     * @param enableDepthOptimization Ob automatische Tiefenoptimierung aktiviert ist
     * @param insertionStrategy Strategie für Knoteneinfügung
     */
    public DepthLimitTreeTopologyStrategy(int maxDepth, boolean enableDepthOptimization,
                                          DepthInsertionStrategy insertionStrategy) {
        super();
        this.maxDepth = Math.max(1, maxDepth);
        this.enableDepthOptimization = enableDepthOptimization;
        this.insertionStrategy = insertionStrategy != null ? insertionStrategy : DepthInsertionStrategy.DEPTH_FIRST;
    }

    // ===== ÜBERSCHREIBUNG DER BUILD-AS-SUBSTRUCTURE-METHODEN =====

    /**
     * **PLANUNGSEBENE**: Erstellt die initiale tiefen-beschränkte Baum-Struktur.
     * Überschreibt BuildAsSubstructure für Tiefenbeschränkung.
     * Verwendet DepthLimitedTreeMirrorNode anstatt TreeMirrorNode.
     *
     * @param totalNodes Anzahl der zu erstellenden Knoten
     * @param props      Properties der Simulation
     * @return Die Root-Node der erstellten tiefen-beschränkten Struktur
     */
    @Override
    protected MirrorNode buildStructure(int totalNodes, Properties props) {
        if (totalNodes <= 0 || !hasNextMirror()) {
            return null;
        }

        // Validierung der Eingabe
        int actualNodes = Math.min(totalNodes, countAvailableMirrors());
        actualNodes = Math.max(1, actualNodes); // Mindestens ein Knoten für Root

        // 1. Erstelle Root-Node mit dem ersten Mirror
        Mirror rootMirror = getNextMirror();
        DepthLimitedTreeMirrorNode root = new DepthLimitedTreeMirrorNode(rootMirror.getID(), rootMirror, maxDepth);

        // 2. Registriere Root bei BuildAsSubstructure
        addToStructureNodes(root);
        setCurrentStructureRoot(root);
        root.setHead(true); // Markiere als Head für diese Struktur

        // 3. Sammle verbleibende Mirrors und erstelle DepthLimitedTreeMirrorNodes
        List<DepthLimitedTreeMirrorNode> remainingNodes = new ArrayList<>();
        int createdNodes = 1;

        while (hasNextMirror() && createdNodes < actualNodes) {
            Mirror mirror = getNextMirror();
            DepthLimitedTreeMirrorNode node = new DepthLimitedTreeMirrorNode(mirror.getID(), mirror, maxDepth);
            addToStructureNodes(node);
            remainingNodes.add(node);
            createdNodes++;
        }

        // 4. Baue NUR die StructureNode-basierte Baum-Struktur
        buildDepthLimitedTreeStructureOnly(root, remainingNodes);

        return root;
    }

    /**
     * **PLANUNGSEBENE**: Fügt neue Knoten zur bestehenden tiefen-beschränkten Baum-Struktur hinzu.
     * Überschreibt BuildAsSubstructure für tiefenspezifische Einfügung.
     * Verwendet die konfigurierte DepthInsertionStrategy für optimale Platzierung.
     *
     * @param nodesToAdd Anzahl der hinzuzufügenden Knoten
     * @return Tatsächliche Anzahl der hinzugefügten Knoten
     */
    @Override
    protected int addNodesToStructure(int nodesToAdd) {
        if (nodesToAdd <= 0 || !hasNextMirror() || getCurrentStructureRoot() == null) {
            return 0;
        }

        int actuallyAdded = 0;

        for (int i = 0; i < nodesToAdd && hasNextMirror(); i++) {
            // 1. Finde den besten Einfügepunkt basierend auf Tiefenbeschränkung
            DepthLimitedTreeMirrorNode insertionPoint = findBestInsertionPointInStructure();

            if (insertionPoint == null || !insertionPoint.canAddChildren()) {
                break; // Keine weiteren Einfügungen möglich
            }

            // 2. Erstelle neuen Knoten
            Mirror mirror = getNextMirror();
            DepthLimitedTreeMirrorNode newNode = new DepthLimitedTreeMirrorNode(mirror.getID(), mirror, maxDepth);

            // 3. Registriere bei BuildAsSubstructure
            addToStructureNodes(newNode);

            // 4. Füge in die Struktur ein (nur StructureNode-Ebene)
            insertionPoint.addChild(newNode);
            newNode.setParent(insertionPoint);

            actuallyAdded++;
        }

        return actuallyAdded;
    }

    /**
     * **PLANUNGSEBENE**: Entfernt Knoten aus der tiefen-beschränkten Baum-Struktur.
     * Überschreibt BuildAsSubstructure für tiefenspezifische Entfernung.
     * Entfernt Knoten von den tiefsten Ebenen zuerst (Blätter zuerst).
     *
     * @param nodesToRemove Anzahl der zu entfernenden Knoten
     * @return Tatsächliche Anzahl der entfernten Knoten
     */
    @Override
    protected int removeNodesFromStructure(int nodesToRemove) {
        if (nodesToRemove <= 0 || getCurrentStructureRoot() == null) {
            return 0;
        }

        int actuallyRemoved = 0;

        for (int i = 0; i < nodesToRemove; i++) {
            // 1. Finde den tiefsten Blatt-Knoten
            DepthLimitedTreeMirrorNode nodeToRemove = findDeepestLeafNode();

            if (nodeToRemove == null || nodeToRemove == getCurrentStructureRoot()) {
                break; // Keine entfernbaren Knoten oder nur Root übrig
            }

            // 2. Entferne aus der Struktur (nur StructureNode-Ebene)
            removeNodeFromDepthLimitedTreeStructure(nodeToRemove);
            actuallyRemoved++;
        }

        return actuallyRemoved;
    }

    // ===== TOPOLOGY STRATEGY INTERFACE IMPLEMENTATION =====

    /**
     * Berechnet die Anzahl der Ziel-Links für tiefen-beschränkte Bäume.
     * Für n Knoten sind es n-1 Links (Baum-Eigenschaft).
     *
     * @param n Das Netzwerk
     * @return Anzahl der erwarteten Links
     */
    @Override
    public int getNumTargetLinks(Network n) {
        return Math.max(0, n.getMirrors().size() - 1);
    }

    /**
     * Berechnet die vorhergesagte Anzahl der Links bei einer gegebenen Action.
     *
     * @param a Die Action, deren Auswirkungen berechnet werden sollen
     * @return Anzahl der erwarteten Links nach Ausführung der Action
     */
    @Override
    public int getPredictedNumTargetLinks(Action a) {
        if (a instanceof MirrorChange mc) {
            int predictedMirrors = mc.getNewMirrors();
            return Math.max(0, predictedMirrors - 1);
        }

        if (a instanceof TopologyChange tc) {
            return getNumTargetLinks(tc.getNetwork());
        }

        // Für andere Action-Typen: Behalte aktuelle Anzahl
        return network != null ? getNumTargetLinks(network) : 0;
    }

    // ===== TIEFENBESCHRÄNKUNGS-SPEZIFISCHE HILFSMETHODEN =====

    /**
     * Baut die tiefen-beschränkte Baum-Struktur rekursiv auf - NUR StructureNode-Ebene.
     * Respektiert die maximale Tiefe beim Strukturaufbau.
     *
     * @param root Root-Node der Struktur
     * @param remainingNodes Liste der noch zu verbindenden Knoten
     */
    private void buildDepthLimitedTreeStructureOnly(DepthLimitedTreeMirrorNode root,
                                                    List<DepthLimitedTreeMirrorNode> remainingNodes) {
        if (remainingNodes.isEmpty() || !root.canAddChildren()) {
            return;
        }

        // Berechne optimale Anzahl Kinder für diesen Parent
        int childrenToAdd = calculateOptimalChildrenPerParent(remainingNodes.size(), root.getRemainingDepth());

        for (int i = 0; i < childrenToAdd && !remainingNodes.isEmpty(); i++) {
            DepthLimitedTreeMirrorNode child = remainingNodes.remove(0);

            // NUR strukturelle StructureNode-Verbindung
            root.addChild(child);
            child.setParent(root);

            // Rekursiver Abstieg für weiteren Strukturaufbau
            buildDepthLimitedTreeStructureOnly(child, remainingNodes);
        }
    }

    /**
     * Findet den besten Einfügepunkt für neue Knoten in der bestehenden Struktur.
     * Bevorzugt tiefere Knoten mit weniger Kindern innerhalb der Tiefenbeschränkung.
     *
     * @return Der optimale Knoten für das Hinzufügen oder null
     */
    private DepthLimitedTreeMirrorNode findBestInsertionPointInStructure() {
        if (!(getCurrentStructureRoot() instanceof DepthLimitedTreeMirrorNode root)) {
            return null;
        }

        return root.findBestInsertionPoint(); // Nutzt die Methode aus DepthLimitedTreeMirrorNode
    }

    /**
     * Findet den tiefsten Blatt-Knoten (ohne Kinder) in der Struktur.
     * Bevorzugt Knoten auf den tiefsten Ebenen für die Entfernung.
     *
     * @return Der tiefste Blatt-Knoten oder null
     */
    private DepthLimitedTreeMirrorNode findDeepestLeafNode() {
        if (!(getCurrentStructureRoot() instanceof DepthLimitedTreeMirrorNode root)) {
            return null;
        }

        DepthLimitedTreeMirrorNode deepestLeaf = null;
        int maxDepthFound = -1;

        // Durchsuche alle Struktur-Knoten
        for (MirrorNode node : getAllStructureNodes()) {
            if (node instanceof DepthLimitedTreeMirrorNode depthNode) {
                // Prüfe ob es ein Blatt ist (keine Kinder)
                if (depthNode.getChildren().isEmpty() && depthNode != root) {
                    int nodeDepth = depthNode.getDepthInTree();
                    if (nodeDepth > maxDepthFound) {
                        maxDepthFound = nodeDepth;
                        deepestLeaf = depthNode;
                    }
                }
            }
        }

        return deepestLeaf;
    }

    /**
     * **PLANUNGSEBENE**: Entfernt einen Knoten vollständig aus der tiefen-beschränkten Baum-Struktur.
     * Bereinigt alle Parent-Child-Verbindungen und entfernt aus BuildAsSubstructure-Verwaltung.
     *
     * @param nodeToRemove Der zu entfernende DepthLimitedTreeMirrorNode
     */
    private void removeNodeFromDepthLimitedTreeStructure(DepthLimitedTreeMirrorNode nodeToRemove) {
        // 1. Parent-Kind-Verbindung trennen
        StructureNode parent = nodeToRemove.getParent();  // Das ist ein StructureNode!
        if (parent != null) {
            parent.removeChild(nodeToRemove);
            nodeToRemove.setParent(null);
        }

        // 2. Entferne aus BuildAsSubstructure-Verwaltung
        removeFromStructureNodes(nodeToRemove);
    }

    /**
     * Berechnet die optimale Anzahl von Kindern pro Parent basierend auf verbleibenden Knoten und Tiefe.
     *
     * @param remainingNodes Anzahl verbleibender Knoten
     * @param remainingDepth Verbleibende Tiefe bis Maximum
     * @return Optimale Anzahl Kinder
     */
    private int calculateOptimalChildrenPerParent(int remainingNodes, int remainingDepth) {
        if (remainingDepth <= 0) {
            return 0;
        }

        if (remainingDepth == 1) {
            // Letzte Ebene - alle verbleibenden Knoten
            return remainingNodes;
        }

        // Verteile gleichmäßig über verbleibende Tiefe
        return Math.max(1, remainingNodes / remainingDepth);
    }

    /**
     * Überladung für einfachere Verwendung.
     *
     * @param totalNodes Gesamtanzahl der Knoten
     * @return Optimale Anzahl Kinder pro Parent
     */
    private int calculateOptimalChildrenPerParent(int totalNodes) {
        return Math.max(1, (int) Math.ceil((double) totalNodes / maxDepth));
    }

    /**
     * Zählt die verfügbaren Mirrors im Iterator.
     * Hilfsmethode für Eingabevalidierung.
     *
     * @return Anzahl der verfügbaren Mirrors
     */
    private int countAvailableMirrors() {
        // Einfache Schätzung - da wir den Iterator nicht durchlaufen können ohne ihn zu verbrauchen
        return Integer.MAX_VALUE; // Optimistische Schätzung - wird durch while-Schleife begrenzt
    }

    /**
     * Gibt eine String-Repräsentation der Strategie zurück.
     *
     * @return String-Repräsentation
     */
    @Override
    public String toString() {
        return String.format("DepthLimitTreeTopologyStrategy[maxDepth=%d, strategy=%s, optimization=%s]",
                maxDepth, insertionStrategy, enableDepthOptimization);
    }

    // ===== GETTER UND KONFIGURATION =====

    /**
     * Gibt die maximale Tiefe des Baums zurück.
     *
     * @return Maximale Tiefe
     */
    public int getMaxDepth() {
        return maxDepth;
    }

    /**
     * Setzt die maximale Tiefe des Baums.
     *
     * @param maxDepth Neue maximale Tiefe (mindestens 1)
     */
    public void setMaxDepth(int maxDepth) {
        this.maxDepth = Math.max(1, maxDepth);
    }

    /**
     * Prüft, ob die Tiefenoptimierung aktiviert ist.
     *
     * @return true, wenn Optimierung aktiviert ist
     */
    public boolean isDepthOptimizationEnabled() {
        return enableDepthOptimization;
    }

    /**
     * Setzt die Tiefenoptimierung.
     *
     * @param enableDepthOptimization true für Aktivierung
     */
    public void setDepthOptimizationEnabled(boolean enableDepthOptimization) {
        this.enableDepthOptimization = enableDepthOptimization;
    }

    /**
     * Gibt die aktuelle Einfügungsstrategie zurück.
     *
     * @return Die Einfügungsstrategie
     */
    public DepthInsertionStrategy getInsertionStrategy() {
        return insertionStrategy;
    }

    /**
     * Setzt die Einfügungsstrategie.
     *
     * @param insertionStrategy Neue Einfügungsstrategie
     */
    public void setInsertionStrategy(DepthInsertionStrategy insertionStrategy) {
        this.insertionStrategy = insertionStrategy != null ? insertionStrategy : DepthInsertionStrategy.DEPTH_FIRST;
    }
}

