package org.lrdm.topologies.node;

import org.lrdm.Mirror;

import java.util.*;

/**
 * Spezialisierte MirrorNode für Baum-Strukturen.
 * Validiert, dass die Struktur tatsächlich ein azyklischer Baum ist.
 * <p>
 * Eine gültige Baum-Struktur hat folgende Eigenschaften:
 * - Genau eine Root (Head-Node ohne internen Parent)
 * - Keine Zyklen oder geschlossener Pfad
 * - n Knoten haben genau n-1 Kanten (charakteristisch für Bäume)
 * - Alle Knoten außer Root haben genau einen Parent
 * - Root-Node muss Edge-Links für externe Verbindungen haben
 * - Zusammenhängend (alle Knoten über Tree-Pfade erreichbar)
 * <p>
 * Nutzt das Multi-Type-System mit expliziter TREE-Typ-ID und Head-ID-Berücksichtigung
 * für korrekte Koexistenz mit anderen Strukturtypen.
 * <p>
 * Maximierte Wiederverwendung der StructureNode/MirrorNode-Funktionalität:
 * - 85 %+ der Traversierungs- und Validierungslogik wird wiederverwendet
 * - Fokussiert auf baum-spezifische Validierung und Navigation
 * - Erweiterte Baum-Analysefunktionen (Tiefe, Balance, etc.)
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class TreeMirrorNode extends MirrorNode {

    // ===== KONSTRUKTOREN =====

    /**
     * Erstellt einen neuen TreeMirrorNode mit gegebener ID.
     *
     * @param id Eindeutige Identifikationsnummer des Knotens
     */
    public TreeMirrorNode(int id) {
        super(id);
    }

    /**
     * Erstellt einen neuen TreeMirrorNode mit gegebener ID und zugeordnetem Mirror.
     *
     * @param id Eindeutige Identifikationsnummer des Knotens
     * @param mirror Der zugeordnete Mirror für Link-Management
     */
    public TreeMirrorNode(int id, Mirror mirror) {
        super(id, mirror);
    }

    // ===== TYP-SYSTEM INTEGRATION =====

    /**
     * Überschreibt die Typ-Ableitung für die korrekte TREE-Identifikation.
     * Kritisch für Multi-Type-System: Ermöglicht korrekte Strukturerkennung
     * und automatische Typ-ID-Zuordnung in allen StructureNode-Methoden.
     *
     * @return StructureType.TREE für eindeutige Baum-Identifikation
     */
    @Override
    public StructureType deriveTypeId() {
        return StructureType.TREE;
    }

    // ===== STRUKTUR-MANAGEMENT =====

    /**
     * Prüft, ob dieser Knoten weitere Kinder akzeptieren kann.
     * <p>
     * Baum-spezifische Logik:
     * - Jeder Knoten kann beliebig viele Kinder haben (Baum-Eigenschaft)
     * - Nur wenn die aktuelle Struktur gültig bleibt
     * - Keine Zyklen entstehen durch neue Kinder
     * <p>
     * Wiederverwendung:
     * - isValidStructure() für Struktur-Integrität
     * - super.canAcceptMoreChildren() für Basis-Validierung
     *
     * @return true, wenn weitere Kinder akzeptiert werden können
     */
    @Override
    public boolean canAcceptMoreChildren() {
        return super.canAcceptMoreChildren() &&
                isValidStructure();
    }

    /**
     * Prüft, ob dieser Knoten aus der Struktur entfernt werden kann.
     * <p>
     * Baum-spezifische Logik:
     * - Ein Baum muss mindestens 1 Knoten haben (Root)
     * - Nur Blätter können sicher entfernt werden
     * - Entfernung darf keine Teilbäume isolieren
     * <p>
     * Wiederverwendung:
     * - getAllNodesInStructure() für Strukturknotenzählung
     * - isLeaf() für Blatt-Erkennung
     * - super.canBeRemovedFromStructure() für Basis-Validierung
     *
     * @param structureRoot Der Root-Knoten der Struktur
     * @return true, wenn der Knoten sicher entfernt werden kann
     */
    @Override
    public boolean canBeRemovedFromStructure(StructureNode structureRoot) {
        if (structureRoot == null) return false;

        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        if (head == null) head = structureRoot;

        Set<StructureNode> structureNodes = getAllNodesInStructure(typeId, head);

        // Ein Baum muss mindestens 1 Knoten haben
        if (structureNodes.size() < 2) return false;

        // Nur Blätter können sicher entfernt werden
        return super.canBeRemovedFromStructure(structureRoot) &&
                isLeaf();
    }

    // ===== STRUKTUR-VALIDIERUNG =====

    /**
     * Erweiterte Baum-Struktur-Validierung mit expliziter TREE-Typ-ID.
     * <p>
     * Validiert zusätzlich zu super.isValidStructure():
     * - Alle Knoten sind TreeMirrorNode-Instanzen
     * - Genau eine Root (Head-Node ohne internen Parent)
     * - Keine Zyklen (nutzt hasClosedCycle() aus StructureNode)
     * - n Knoten haben genau n-1 Kanten (charakteristische Baum-Eigenschaft)
     * - Root-Node muss Edge-Links für externe Verbindungen haben
     * - Korrekte Parent-Child-Beziehungen (Root darf externen Parent haben)
     * - Baum-spezifische Knoten-Validierung
     * <p>
     * Wiederverwendung:
     * - super.isValidStructure() für MirrorNode-Basis-Validierung
     * - hasClosedCycle() für Zyklusprüfung (sollte false sein)
     * - getNumEdgeLinks() für Edge-Link-Validierung
     * - isValidTreeNode() für individuelle Knoten-Validierung
     *
     * @param allNodes Menge aller Knoten, die zur Struktur gehören sollen
     * @param typeId Die Typ-ID der gewünschten Struktur (sollte TREE sein)
     * @param head Die Head-Node der gewünschten Struktur
     * @return true, wenn die Baum-Struktur gültig ist
     */
    @Override
    public boolean isValidStructure(Set<StructureNode> allNodes, StructureType typeId, StructureNode head) {
        // Zuerst die grundlegende MirrorNode-Struktur-Validierung
        if (!super.isValidStructure(allNodes, typeId, head)) {
            return false;
        }

        if (allNodes.isEmpty()) return false;

        if (allNodes.stream().anyMatch(node -> !node.hasNodeType(StructureType.TREE)) || (allNodes.stream().anyMatch(node -> !node.hasNodeType(typeId)))) {
            return false;
        }

        // Sammle alle Baum-Knoten und finde Root-Knoten
        Set<TreeMirrorNode> treeNodes = new HashSet<>();
        TreeMirrorNode rootNode = null;

        for (StructureNode node : allNodes) {
            if (!(node instanceof TreeMirrorNode treeNode)) {
                return false; // Alle Knoten müssen TreeMirrorNodes sein
            }

            treeNodes.add(treeNode);
            if (treeNode.isHead(typeId)) {
                if (rootNode != null) return false; // Nur eine Root erlaubt
                rootNode = treeNode;
            }
        }

        if (rootNode == null) return false; // Eine Root muss vorhanden sein
        if (rootNode != head) return false; // Root muss der übergebene Head sein

        // Validiere baum-spezifische Eigenschaften
        for (TreeMirrorNode treeNode : treeNodes) {
            if (!isValidTreeNode(treeNode, rootNode, typeId)) {
                return false;
            }
        }

        // Keine Zyklen erlaubt (charakteristisch für Bäume)
        if (hasClosedCycle(allNodes, typeId, head)) {
            return false;
        }

        // n Knoten müssen genau n-1 Kanten haben (Baum-Eigenschaft)
        int expectedEdges = allNodes.size() - 1;
        int actualEdges = countInternalEdges(allNodes, typeId, head);
        if (actualEdges != expectedEdges) {
            return false;
        }

        // Root muss Edge-Links haben (Verbindung nach außen)
        return rootNode.getNumEdgeLinks() > 0;
    }

    /**
     * Überschreibt isValidStructure() für automatische TREE-Typ-Ermittlung.
     * Wiederverwendung der automatischen Typ- und Head-Ermittlung aus StructureNode.
     *
     * @param allNodes Menge aller Knoten, die zur Struktur gehören sollen
     * @return true, wenn die Baum-Struktur gültig ist
     */
    @Override
    public boolean isValidStructure(Set<StructureNode> allNodes) {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);

        if (head == null) {
            return false;
        }

        return isValidStructure(allNodes, typeId, head);
    }

    /**
     * Convenience-Methode für Struktur-Validierung ohne Parameter.
     * <p>
     * Nutzt TREE-spezifische Strukturermittlung für automatische Validierung.
     * Konsistenz mit anderen StructureNode-Validierungsmethoden.
     * <p>
     * Wiederverwendung:
     * - findHead() für automatische Head-Ermittlung
     * - getAllNodesInStructure() für automatische Strukturknotenbefüllung
     *
     * @return true, wenn die aktuelle Baum-Struktur gültig ist
     */
    public boolean isValidStructure() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);

        if (head == null) {
            return false;
        }

        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head);
        return isValidStructure(allNodes, typeId, head);
    }

    /**
     * Validiert einen einzelnen Baum-Knoten mit TREE-Typ-ID-Berücksichtigung.
     * <p>
     * Prüft:
     * - Root-Knoten: Darf externen Parent haben, aber kein interner Parent
     * - Normale Knoten: Müssen genau einen Parent in der Struktur haben
     * - Korrekte Parent-Child-Beziehungen innerhalb der Struktur
     * - Strukturmitgliedschaft der Parent-Child-Beziehungen
     * <p>
     * Wiederverwendung:
     * - getParent() für Parent-Zugriff
     * - getAllNodesInStructure() für Struktur-Mitgliedschaftsprüfung
     *
     * @param treeNode Der zu validierende Baum-Knoten
     * @param rootNode Die Root des Baums
     * @param typeId Der Strukturtyp (sollte TREE sein)
     * @return true wenn der Knoten gültig ist
     */
    private boolean isValidTreeNode(TreeMirrorNode treeNode, TreeMirrorNode rootNode, StructureType typeId) {
        StructureNode parent = treeNode.getParent();
        Set<StructureNode> structureNodes = rootNode.getAllNodesInStructure(typeId, rootNode);

        if (treeNode == rootNode) {
            // Root-Knoten: darf externen Parent haben, aber kein interner Parent
            if (parent != null) {
                return !structureNodes.contains(parent); // Root-Parent muss extern sein
            }
        } else {
            // Normale Knoten: müssen genau einen Parent in der Struktur haben
            if (parent == null) {
                return false; // Knoten müssen verbunden sein
            }
            return structureNodes.contains(parent); // Parent muss in der Struktur sein
        }

        return true;
    }

    /**
     * Zählt die internen Kanten (Links) innerhalb der Baum-Struktur.
     * Hilfsmethode für die n-1 Kanten-Validierung.
     *
     * @param allNodes Alle Knoten der Struktur
     * @param typeId Der Strukturtyp
     * @param head Die Head-Node
     * @return Anzahl der internen Kanten
     */
    private int countInternalEdges(Set<StructureNode> allNodes, StructureType typeId, StructureNode head) {
        Set<LinkPair> internalLinks = new HashSet<>();

        for (StructureNode node : allNodes) {
            Set<StructureNode> children = node.getChildren(typeId, head.getId());
            for (StructureNode child : children) {
                if (allNodes.contains(child)) {
                    // Normalisiere die Link-Richtung (kleinere ID zuerst)
                    int from = Math.min(node.getId(), child.getId());
                    int to = Math.max(node.getId(), child.getId());
                    internalLinks.add(new LinkPair(from, to));
                }
            }
        }

        return internalLinks.size();
    }

    // ===== BAUM-NAVIGATION =====

    /**
     * Findet die Root des Baums.
     * Wiederverwendung der StructureNode findHead() Funktion.
     *
     * @return Die Root des Baums oder null, wenn kein gültiger Baum
     */
    public TreeMirrorNode getTreeRoot() {
        StructureNode head = findHead(StructureType.TREE);
        return (head instanceof TreeMirrorNode) ? (TreeMirrorNode) head : null;
    }

    /**
     * Sammelt alle Blätter des Baums.
     * <p>
     * Baum-Blätter sind exakt die Terminal-Knoten (Endpunkte) der Struktur.
     * <p>
     * Wiederverwendung:
     * - getEndpointsOfStructure() aus StructureNode für Terminal-Knoten-Sammlung
     *
     * @return Liste aller Blätter des Baums
     */
    public List<TreeMirrorNode> getTreeLeaves() {
        List<TreeMirrorNode> leaves = new ArrayList<>();

        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);

        if (head == null) {
            return leaves;
        }

        // Nutze StructureNode getEndpointsOfStructure() - das sind die Blätter!
        Set<StructureNode> endpoints = head.getEndpointsOfStructure(typeId, head);

        for (StructureNode endpoint : endpoints) {
            if (endpoint instanceof TreeMirrorNode treeNode) {
                leaves.add(treeNode);
            }
        }

        return leaves;
    }

    // ===== BAUM-ANALYSE =====

    /**
     * Berechnet die Tiefe dieses Knotens im Baum.
     * <p>
     * Nutzt getPathFromHead() aus StructureNode für konsistente Pfadberechnung.
     * Die Tiefe ist die Anzahl der Kanten von der Root zu diesem Knoten.
     *
     * @return Die Tiefe des Knotens (Root hat Tiefe 0)
     */
    public int getDepthInTree() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);

        if (head == null) {
            return 0; // Isolierter Knoten hat Tiefe 0
        }

        List<StructureNode> pathFromRoot = getPathFromHead(typeId, head);
        return Math.max(0, pathFromRoot.size() - 1); // Pfadlänge - 1 = Tiefe
    }

    /**
     * Berechnet die maximale Tiefe des Baums.
     * <p>
     * Nutzt getAllNodesInStructure() und getDepthInTree() für alle Knoten.
     * Die maximale Tiefe ist die größte Tiefe aller Knoten im Baum.
     *
     * @return Die maximale Tiefe des Baums
     */
    public int getMaxTreeDepth() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);

        if (head == null) {
            return 0;
        }

        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head);
        int maxDepth = 0;

        for (StructureNode node : allNodes) {
            if (node instanceof TreeMirrorNode treeNode) {
                maxDepth = Math.max(maxDepth, treeNode.getDepthInTree());
            }
        }

        return maxDepth;
    }

    /**
     * Zählt die Gesamtanzahl der Knoten im Baum.
     * <p>
     * Nutzt getAllNodesInStructure() aus StructureNode.
     *
     * @return Die Anzahl der Knoten im Baum
     */
    public int getTreeSize() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);

        if (head == null) {
            return 1; // Isolierter Knoten zählt als Größe 1
        }

        return getAllNodesInStructure(typeId, head).size();
    }

    /**
     * Prüft, ob dieser Baum balanciert ist.
     * <p>
     * Ein Baum ist balanciert, wenn sich die Tiefen der Blätter um maximal 1 unterscheiden.
     * <p>
     * Wiederverwendung:
     * - getTreeLeaves() für Blatt-Sammlung
     * - getDepthInTree() für Tiefenberechnung
     *
     * @return true, wenn der Baum balanciert ist
     */
    public boolean isBalanced() {
        List<TreeMirrorNode> leaves = getTreeLeaves();

        if (leaves.size() <= 1) {
            return true; // Ein oder kann Blatt ist immer balanciert
        }

        int minDepth = Integer.MAX_VALUE;
        int maxDepth = Integer.MIN_VALUE;

        for (TreeMirrorNode leaf : leaves) {
            int depth = leaf.getDepthInTree();
            minDepth = Math.min(minDepth, depth);
            maxDepth = Math.max(maxDepth, depth);
        }

        // Balanciert als Tiefenunterschied ≤ 1
        return (maxDepth - minDepth) <= 1;
    }
}