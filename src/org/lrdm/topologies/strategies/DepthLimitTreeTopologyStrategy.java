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
        if (totalNodes <= 0) return null;

        // Erstelle Root-Node als BalancedTreeMirrorNode
        // 1. Erstelle Root-Node mit dem ersten Mirror
        DepthLimitedTreeMirrorNode root = getNodeFromIterator();
        if (root == null) return null;
        root.setMaxDepth(maxDepth);

        // 2. Registriere Root bei BuildAsSubstructure
        setCurrentStructureRoot(root);

        // 3. Sammle verbleibende Mirrors und erstelle DepthLimitedTreeMirrorNodes
        List<DepthLimitedTreeMirrorNode> remainingNodes = new ArrayList<>();

        for (int i=1; i<totalNodes&&hasNextMirror(); i++) {
            DepthLimitedTreeMirrorNode node = getNodeFromIterator();
            remainingNodes.add(node);
        }

        // 4. Baue NUR die StructureNode-basierte Baum-Struktur
        root.setHead(StructureNode.StructureType.DEPTH_LIMIT_TREE,true); // Markiere als Head für diese Struktur
        buildDepthLimitedTreeStructureOnly(root, remainingNodes);

        return root;
    }

    /**
     * Erstellt einen neuen BalancedTreeMirrorNode aus dem Mirror-Iterator.
     *
     * @return Neuer BalancedTreeMirrorNode oder null, wenn keine Mirrors verfügbar sind
     */
    protected DepthLimitedTreeMirrorNode getNodeFromIterator() {
        if (mirrorIterator != null && mirrorIterator.hasNext()) {
            Mirror mirror = getNextMirror();
            MirrorNode node = createMirrorNodeForMirror(mirror);

            if (node != null) {
                node.addNodeType(StructureNode.StructureType.MIRROR);
                node.addNodeType(StructureNode.StructureType.TREE);
                node.addNodeType(StructureNode.StructureType.DEPTH_LIMIT_TREE);
                node.setMirror(mirror);
                addToStructureNodes(node); // Aktiv hinzufügen
                ((DepthLimitedTreeMirrorNode) node).setMaxDepth(maxDepth);
            }
            return (DepthLimitedTreeMirrorNode) node;
        }
        return null;
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
        return new DepthLimitedTreeMirrorNode(mirror.getID(), mirror, maxDepth);
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
    protected int addNodesToStructure(Set<Mirror> nodesToAdd) {
        if (nodesToAdd.isEmpty() || getCurrentStructureRoot() == null) {
            return 0;
        }

        int actuallyAdded = 0;

        for (int i = 0; i < nodesToAdd.size() && hasNextMirror(); i++) {
            // 1. Finde den besten Einfügepunkt basierend auf Tiefenbeschränkung
            DepthLimitedTreeMirrorNode insertionPoint = findBestInsertionPointInStructure();

            if (insertionPoint == null || !insertionPoint.canAddChildren()) {
                break; // Keine weiteren Einfügungen möglich
            }

            // 2. Erstelle neuen Knoten
            DepthLimitedTreeMirrorNode newNode = getNodeFromIterator();

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
    protected Set<MirrorNode> removeNodesFromStructure(int nodesToRemove) {
        if (nodesToRemove <= 0 || getCurrentStructureRoot() == null) {
            return new HashSet<>();
        }

        Set<MirrorNode> removedNodes = new HashSet<>();

        for (int i = 0; i < nodesToRemove; i++) {
            // 1. Finde den tiefsten Blatt-Knoten
            DepthLimitedTreeMirrorNode nodeToRemove = findOptimalNodeForRemoval();

            if (nodeToRemove == null || nodeToRemove == getCurrentStructureRoot()) {
                break; // Keine entfernbaren Knoten oder nur Root übrig
            }
            // 2. Entferne aus der Struktur (nur StructureNode-Ebene)
            removeNodeFromStructuralPlanning(nodeToRemove,
                    Set.of(StructureNode.StructureType.DEFAULT,
                            StructureNode.StructureType.MIRROR,
                            StructureNode.StructureType.TREE,
                            StructureNode.StructureType.DEPTH_LIMIT_TREE));
            removedNodes.add(nodeToRemove);
        }

        return removedNodes;
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

        if (a instanceof TargetLinkChange tac) {
            return tac.getNewLinksPerMirror();
        }

        if (a instanceof TopologyChange tc) {
            return getNumTargetLinks(tc.getNetwork());
        }

        // Für andere Action-Typen: Behalte aktuelle Anzahl
        return network != null ? getNumTargetLinks(network) : 0;
    }

    // ===== TIEFENBESCHRÄNKUNGS-SPEZIFISCHE HILFSMETHODEN =====


    /**
     * Baut die tiefen-beschränkte Baum-Struktur iterativ auf - NUR StructureNode-Ebene.
     * Respektiert die maximale Tiefe beim Strukturaufbau.
     *
     * **Algorithmus:**
     * 1. Bestimme das Minimum der Kinderanzahl aller Knoten im Baum
     * 2. Finde die tiefste Node mit dieser minimalen Kinderanzahl
     * 3. Füge ein Kind zu dieser Node hinzu
     * 4. Wiederhole bis alle Knoten verteilt sind oder maximale Tiefe erreicht
     *
     * @param root Root-Node der Struktur
     * @param remainingNodes Liste der noch zu verbindenden Knoten
     */
    private void buildDepthLimitedTreeStructureOnly(DepthLimitedTreeMirrorNode root,
                                                    List<DepthLimitedTreeMirrorNode> remainingNodes) {
        if (remainingNodes.isEmpty() || !root.canAddChildren()) {
            return;
        }

        // Iterativer Aufbau: Füge Knoten einzeln hinzu
        while (!remainingNodes.isEmpty()) {
            // 1. Finde die optimale Einfügeposition
            DepthLimitedTreeMirrorNode targetParent = findOptimalInsertionParent(root);

            if (targetParent == null || !targetParent.canAddChildren()) {
                // Keine weiteren Einfügungen möglich - maximale Tiefe erreicht
                break;
            }

            // 2. Entferne den nächsten Knoten aus der Liste
            DepthLimitedTreeMirrorNode child = remainingNodes.remove(0);

            // 3. NUR strukturelle StructureNode-Verbindung
            targetParent.addChild(child);
        }
    }

    /**
     * Findet die optimale Einfügeposition basierend auf dem beschriebenen Algorithmus:
     * 1. Sammle nur Knoten, die tatsächlich noch Kinder aufnehmen können
     * 2. Bestimme das Minimum der Kinderanzahl unter diesen gültigen Kandidaten
     * 3. Unter allen Knoten mit minimaler Kinderanzahl, wähle den tiefsten
     * 4. Bei Gleichstand in Tiefe, wähle den ersten gefundenen
     *
     * @param root Die Root-Node des Baums
     * @return Die optimale Parent-Node für das nächste Kind oder null
     */
    private DepthLimitedTreeMirrorNode findOptimalInsertionParent(DepthLimitedTreeMirrorNode root) {
        // 1. Sammle NUR Knoten, die noch Kinder haben können (filtert maximale Tiefe aus)
        List<DepthLimitedTreeMirrorNode> candidateParents = new ArrayList<>();
        collectValidCandidateParents(root, candidateParents);

        if (candidateParents.isEmpty()) {
            return null;
        }

        // 2. Bestimme die minimale Anzahl Kinder unter allen GÜLTIGEN Kandidaten
        int minChildren = candidateParents.stream()
                .mapToInt(node -> node.getChildren().size())
                .min()
                .orElse(Integer.MAX_VALUE);

        // 3. Filtere Knoten mit minimaler Kinderanzahl
        List<DepthLimitedTreeMirrorNode> nodesWithMinChildren = candidateParents.stream()
                .filter(node -> node.getChildren().size() == minChildren)
                .toList();

        // 4. Finde den tiefsten unter diesen Knoten
        return nodesWithMinChildren.stream()
                .max(Comparator.comparingInt(DepthLimitedTreeMirrorNode::getDepthInTree))
                .orElse(null);
    }

    /**
     * Sammelt rekursiv NUR Knoten, die tatsächlich noch Kinder aufnehmen können.
     * WICHTIG: Filtert Knoten in maximaler Tiefe bereits hier aus!
     *
     * @param current Der aktuelle Knoten
     * @param candidates Liste der Kandidaten (wird befüllt)
     */
    private void collectValidCandidateParents(DepthLimitedTreeMirrorNode current,
                                              List<DepthLimitedTreeMirrorNode> candidates) {
        // WICHTIG: Prüfe ZUERST, ob dieser Knoten noch Kinder aufnehmen kann
        // Dies filtert Knoten in maximaler Tiefe automatisch aus
        if (current.canAddChildren()) {
            candidates.add(current);
        }

        // Rekursiv alle Kinder durchsuchen
        for (StructureNode child : current.getChildren()) {
            if (child instanceof DepthLimitedTreeMirrorNode depthChild) {
                collectValidCandidateParents(depthChild, candidates);
            }
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
     * Findet den optimalen Knoten für die Entfernung basierend auf umgekehrter Logik zu buildDepthLimitedTreeStructureOnly.
     *
     * **Algorithmus (umgekehrt zum Aufbau)**:
     * 1. Bestimme das Maximum der Kinderanzahl aller Knoten im Baum
     * 2. Finde die tiefste Node mit dieser maximalen Kinderanzahl
     * 3. Bei Gleichstand: Finde die flachste Node mit der minimalen Kinderanzahl
     * 4. Entferne ein Kind von dieser Node
     *
     * Dies sorgt dafür, dass die Gleichverteilung erhalten bleibt: Knoten mit vielen Kindern
     * werden zuerst "entlastet", bevor Knoten mit wenigen Kindern betroffen werden.
     *
     * @return Der optimale Knoten für die Entfernung oder null
     */
    private DepthLimitedTreeMirrorNode findOptimalNodeForRemoval() {
        if (!(getCurrentStructureRoot() instanceof DepthLimitedTreeMirrorNode root)) {
            return null;
        }

        // 1. Sammle alle entfernbaren Knoten (nicht die Root, da diese erhalten bleiben muss)
        List<DepthLimitedTreeMirrorNode> removableCandidates = new ArrayList<>();
        collectRemovableCandidates(root, removableCandidates);

        if (removableCandidates.isEmpty()) {
            return null;
        }

        // 2. Bestimme die MAXIMALE Anzahl Kinder unter allen entfernbaren Kandidaten
        int maxChildren = removableCandidates.stream()
                .mapToInt(node -> node.getChildren().size())
                .max()
                .orElse(0);

        // 3. Filtere Knoten mit maximaler Kinderanzahl
        List<DepthLimitedTreeMirrorNode> nodesWithMaxChildren = removableCandidates.stream()
                .filter(node -> node.getChildren().size() == maxChildren)
                .toList();

        if (!nodesWithMaxChildren.isEmpty()) {
            // 4a. Unter den Knoten mit maximaler Kinderanzahl: Wähle den TIEFSTEN
            return nodesWithMaxChildren.stream()
                    .max(Comparator.comparingInt(DepthLimitedTreeMirrorNode::getDepthInTree))
                    .orElse(null);
        }

        // 4b. Fallback: Bei Gleichverteilung, wähle den FLACHSTEN mit MINIMALER Kinderanzahl
        int minChildren = removableCandidates.stream()
                .mapToInt(node -> node.getChildren().size())
                .min()
                .orElse(Integer.MAX_VALUE);

        return removableCandidates.stream()
                .filter(node -> node.getChildren().size() == minChildren)
                .min(Comparator.comparingInt(DepthLimitedTreeMirrorNode::getDepthInTree))
                .orElse(null);
    }

    /**
     * Sammelt rekursiv alle Knoten, die entfernt werden können.
     * Schließt die Root-Node aus, da diese erhalten bleiben muss.
     *
     * @param current Der aktuelle Knoten
     * @param candidates Liste der Kandidaten (wird befüllt)
     */
    private void collectRemovableCandidates(DepthLimitedTreeMirrorNode current,
                                            List<DepthLimitedTreeMirrorNode> candidates) {
        // Alle Knoten außer Root sind entfernbar
        if (current != getCurrentStructureRoot()) {
            candidates.add(current);
        }

        // Rekursiv alle Kinder durchsuchen
        for (StructureNode child : current.getChildren()) {
            if (child instanceof DepthLimitedTreeMirrorNode depthChild) {
                collectRemovableCandidates(depthChild, candidates);
            }
        }
    }

    /**
     * Format-Template für die String-Repräsentation der Strategie.
     */
    private static final String TO_STRING_FORMAT = "DepthLimitTreeTopologyStrategy[maxDepth=%d, strategy=%s, optimization=%b]";

    /**
     * Gibt eine String-Repräsentation der Strategie zurück.
     *
     * @return String-Repräsentation
     */
    @Override
    public String toString() {
        return String.format(TO_STRING_FORMAT, maxDepth, insertionStrategy, enableDepthOptimization);
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

