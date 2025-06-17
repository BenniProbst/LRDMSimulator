package org.lrdm.topologies.node;

import org.lrdm.Mirror;

import java.util.*;

/**
 * Spezialisierte MirrorNode für Stern-Strukturen.
 * Validiert, dass die Struktur ein Stern ist (ein Zentrum mit mehreren Blättern oder Head-Nodes).
 * <p>
 * Eine gültige Stern-Struktur hat folgende Eigenschaften:
 * - Mindestens 3 Knoten (Zentrum + min. 2 Blätter)
 * - Ein Zentrum (Head-Node), das alle anderen Knoten als Kinder hat
 * - Alle anderen Knoten sind direkte Kinder des Zentrums
 * - Zentrum muss Edge-Links für externe Verbindungen haben
 * - Blatt-Knoten sind entweder Terminal-Knoten oder Head-Nodes anderer Strukturen
 * - Keine Zyklen innerhalb der Stern-Struktur
 * <p>
 * Nutzt das Multi-Type-System mit expliziter STAR-Typ-ID und Head-ID-Berücksichtigung
 * für korrekte Koexistenz mit anderen Strukturtypen.
 * <p>
 * Maximierte Wiederverwendung der StructureNode/MirrorNode-Funktionalität:
 * - 85 %+ der Traversierungs- und Validierungslogik wird wiederverwendet
 * - Fokussiert auf stern-spezifische Validierung und Navigation
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class StarMirrorNode extends MirrorNode {

    // ===== KONSTRUKTOREN =====

    /**
     * Erstellt einen neuen StarMirrorNode mit gegebener ID.
     *
     * @param id Eindeutige Identifikationsnummer des Knotens
     */
    public StarMirrorNode(int id) {
        super(id);
    }

    /**
     * Erstellt einen neuen StarMirrorNode mit gegebener ID und zugeordnetem Mirror.
     *
     * @param id Eindeutige Identifikationsnummer des Knotens
     * @param mirror Der zugeordnete Mirror für Link-Management
     */
    public StarMirrorNode(int id, Mirror mirror) {
        super(id, mirror);
    }

    // ===== TYP-SYSTEM INTEGRATION =====

    /**
     * Überschreibt die Typ-Ableitung für korrekte STAR-Identifikation.
     * Kritisch für Multi-Type-System: Ermöglicht korrekte Strukturerkennung
     * und automatische Typ-ID-Zuordnung in allen StructureNode-Methoden.
     *
     * @return StructureType.STAR für eindeutige Stern-Identifikation
     */
    @Override
    public StructureType deriveTypeId() {
        return StructureType.STAR;
    }

    // ===== STRUKTUR-MANAGEMENT =====

    /**
     * Prüft, ob dieser Knoten weitere Kinder akzeptieren kann.
     * <p>
     * Stern-spezifische Logik:
     * - Nur das Zentrum (Head-Node) kann Kinder akzeptieren
     * - Nur wenn die aktuelle Struktur gültig ist
     * - Zentrum hat keine Begrenzung für Kinder (beliebig viele Strahlen)
     * <p>
     * Wiederverwendung:
     * - findHead(typeId) für STAR-spezifische Head-Ermittlung
     * - isHead(typeId) für Zentrum-Erkennung
     * - super.canAcceptMoreChildren() für Basis-Validierung
     *
     * @return true, wenn weitere Kinder akzeptiert werden können
     */
    @Override
    public boolean canAcceptMoreChildren() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);

        if (head == null) {
            return false;
        }

        return super.canAcceptMoreChildren() &&
                isValidStructure() &&
                isHead(typeId);
    }

    /**
     * Prüft, ob dieser Knoten aus der Struktur entfernt werden kann.
     * <p>
     * Stern-spezifische Logik:
     * - Ein Stern muss mindestens 3 Knoten haben (Zentrum + min. 2 Blätter)
     * - Nach Entfernung müssen noch mindestens 3 Knoten übrig bleiben
     * - Nur Blätter (Terminal-Knoten, keine Head-Nodes) können entfernt werden
     * - Zentrum kann nie entfernt werden
     * <p>
     * Wiederverwendung:
     * - findHead(typeId) für STAR-spezifische Head-Ermittlung
     * - getAllNodesInStructure(typeId, head) für Strukturknotenzählung
     * - isTerminal() und isHead() für Knoten-Typ-Erkennung
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

        if (head == null) {
            return false;
        }

        Set<StructureNode> structureNodes = head.getAllNodesInStructure(typeId, head);

        // Ein Stern muss mindestens 3 Knoten haben,
        // nach Entfernung müssen noch mindestens 3 Knoten übrig bleiben
        if (structureNodes.size() < 4) return false;

        // Nur Blätter (Terminal-Knoten, keine Head-Nodes) können entfernt werden
        return super.canBeRemovedFromStructure(structureRoot) &&
                isTerminal() &&
                !isHead(typeId);
    }

    // ===== STRUKTUR-VALIDIERUNG =====

    /**
     * Erweiterte Stern-Struktur-Validierung mit expliziter STAR-Typ-ID.
     * <p>
     * Validiert zusätzlich zu super.isValidStructure():
     * - Alle Knoten sind StarMirrorNode-Instanzen
     * - Mindestens 3 Knoten für einen echten Stern
     * - Genau ein Head-Knoten (Zentrum) vorhanden
     * - Zentrum hat mindestens 2 Kinder
     * - Alle Nicht-Zentrum-Knoten haben das Zentrum als Parent
     * - Keine Zyklen (Stern ist azyklisch)
     * - Zentrum hat Edge-Links für externe Verbindungen
     * - Stern-spezifische Knoten-Validierung
     * <p>
     * Wiederverwendung:
     * - super.isValidStructure() für MirrorNode-Basis-Validierung
     * - hasClosedCycle() für Zyklusprüfung (sollte false sein)
     * - getNumEdgeLinks() für Edge-Link-Validierung
     * - isValidStarNode() für individuelle Knoten-Validierung
     *
     * @param allNodes Menge aller Knoten, die zur Struktur gehören sollen
     * @param typeId Die Typ-ID der gewünschten Struktur (sollte STAR sein)
     * @param head Die Head-Node der gewünschten Struktur
     * @return true, wenn die Stern-Struktur gültig ist
     */
    @Override
    public boolean isValidStructure(Set<StructureNode> allNodes, StructureType typeId, StructureNode head) {
        if (!super.isValidStructure(allNodes, typeId, head)) {
            return false;
        }

        if (allNodes.size() < 3) return false;

        if (typeId != StructureType.STAR) {
            return false;
        }

        Set<StarMirrorNode> starNodes = new HashSet<>();
        StarMirrorNode centerNode = null;

        for (StructureNode node : allNodes) {
            if (!(node instanceof StarMirrorNode starNode)) {
                return false; // Alle Knoten müssen StarMirrorNodes sein
            }

            starNodes.add(starNode);
            if (starNode.isHead(typeId)) {
                if (centerNode != null) return false; // Nur ein Zentrum erlaubt
                centerNode = starNode;
            }
        }

        if (centerNode == null) return false; // Ein Zentrum muss vorhanden sein
        if (centerNode != head) return false; // Zentrum muss der übergebene Head sein

        // Validiere stern-spezifische Eigenschaften
        for (StarMirrorNode starNode : starNodes) {
            if (!isValidStarNode(starNode, centerNode, typeId)) {
                return false;
            }
        }

        // Zentrum muss mindestens 2 Kinder haben
        if (centerNode.getChildren(typeId, head.getId()).size() < 2) return false;

        // Zentrum muss Edge-Links haben (Verbindung nach außen)
        if (centerNode.getNumEdgeLinks() == 0) return false;

        // Stern darf keine Zyklen haben (sollte immer false sein)
        return !hasClosedCycle(allNodes, typeId, head);
    }

    /**
     * Überschreibt isValidStructure() für automatische STAR-Typ-Ermittlung.
     * Wiederverwendung der automatischen Typ- und Head-Ermittlung aus StructureNode.
     *
     * @param allNodes Menge aller Knoten, die zur Struktur gehören sollen
     * @return true, wenn die Stern-Struktur gültig ist
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
     * Nutzt STAR-spezifische Strukturermittlung für automatische Validierung.
     * Konsistenz mit anderen StructureNode-Validierungsmethoden.
     * <p>
     * Wiederverwendung:
     * - findHead() für automatische Head-Ermittlung
     * - getAllNodesInStructure() für automatische Strukturknotenbefüllung
     *
     * @return true, wenn die aktuelle Stern-Struktur gültig ist
     */
    public boolean isValidStructure() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);

        if (head == null) {
            return false;
        }

        Set<StructureNode> allNodes = head.getAllNodesInStructure(typeId, head);
        return isValidStructure(allNodes, typeId, head);
    }

    /**
     * Validiert einen einzelnen Stern-Knoten mit STAR-Typ-ID-Berücksichtigung.
     * <p>
     * Prüft:
     * - Zentrum: Muss mindestens 2 Kinder haben, darf externen Parent haben
     * - Blatt-Knoten: Muss das Zentrum als Parent haben
     * - Blatt-Knoten: Entweder keine Kinder (Terminal) oder Head anderer Struktur
     * - Korrekte Parent-Child-Beziehungen innerhalb der Struktur
     * <p>
     * Wiederverwendung:
     * - getChildren() für strukturspezifische Kind-Zählung
     * - getParent() für Parent-Zugriff
     * - getAllNodesInStructure() für Struktur-Mitgliedschaftsprüfung
     * - isLeaf() und isHead() für Knoten-Typ-Erkennung
     *
     * @param starNode Der zu validierende Stern-Knoten
     * @param centerNode Das Zentrum des Sterns
     * @param typeId Der Strukturtyp (sollte STAR sein)
     * @return true wenn der Knoten gültig ist
     */
    private boolean isValidStarNode(StarMirrorNode starNode, StarMirrorNode centerNode, StructureType typeId) {
        StructureNode parent = starNode.getParent();
        Set<StructureNode> structureNodes = centerNode.getAllNodesInStructure(typeId, centerNode);

        if (starNode == centerNode) {
            // Zentrum: muss mindestens 2 Kinder haben
            if (starNode.getChildren(typeId, centerNode.getId()).size() < 2) return false;

            // Zentrum darf externen Parent haben
            if (parent != null) {
                return !structureNodes.contains(parent); // Zentrum-Parent muss extern sein
            }
        } else {
            // Blatt-Knoten: muss das Zentrum als Parent haben
            if (parent != centerNode) return false;

            // Blatt kann entweder keine Kinder haben oder Head einer anderen Struktur sein
            return starNode.isLeaf() || starNode.isHead(); // Nur Head-Nodes dürfen Kinder haben
        }

        return true;
    }

    // ===== STERN-NAVIGATION =====

    /**
     * Findet das Zentrum des Sterns.
     * Wiederverwendung der StructureNode findHead() Funktion.
     *
     * @return Das Zentrum des Sterns oder null, wenn kein gültiger Stern
     */
    public StarMirrorNode getCenter() {
        StructureNode head = findHead(StructureType.STAR);
        return (head instanceof StarMirrorNode) ? (StarMirrorNode) head : null;
    }

    /**
     * Sammelt alle Blätter des Sterns.
     * <p>
     * Stern-Blätter sind Terminal-Knoten (Endpunkte) der Struktur,
     * die keine Head-Nodes anderer Strukturen sind.
     * <p>
     * Wiederverwendung:
     * - getEndpointsOfStructure() für Terminal-Knoten-Sammlung
     * - isHead() für Head-Node-Filterung
     *
     * @return Liste aller echten Blätter des Sterns
     */
    public List<StarMirrorNode> getLeaves() {
        List<StarMirrorNode> leaves = new ArrayList<>();

        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);

        if (head == null) {
            return leaves;
        }

        // Nutze StructureNode getEndpointsOfStructure() - das sind die Blätter!
        Set<StructureNode> endpoints = head.getEndpointsOfStructure(typeId, head);

        for (StructureNode endpoint : endpoints) {
            // Filtere nur echte Blätter (keine Head-Nodes)
            if (endpoint instanceof StarMirrorNode starNode && !starNode.isHead(typeId)) {
                leaves.add(starNode);
            }
        }

        return leaves;
    }

    /**
     * Sammelt alle Head-Knoten, die Kinder des Zentrums sind.
     * <p>
     * Dies sind Knoten, die sowohl Blätter des Sterns als auch
     * Head-Knoten anderer Strukturen sind.
     * <p>
     * Wiederverwendung:
     * - getAllNodesInStructure() für Struktur-Traversierung
     * - isHead() und getParent() für Head-Node-Erkennung
     *
     * @return Liste aller Kind-Head-Nodes des Sterns
     */
    public List<StarMirrorNode> getChildHeads() {
        List<StarMirrorNode> childHeads = new ArrayList<>();

        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);

        if (head == null) {
            return childHeads;
        }

        Set<StructureNode> allNodes = head.getAllNodesInStructure(typeId, head);

        for (StructureNode node : allNodes) {
            // Head-Nodes mit Parent (außer Zentrum)
            if (node instanceof StarMirrorNode starNode &&
                    starNode.isHead() &&
                    starNode.getParent() != null &&
                    !starNode.isHead(typeId)) { // Nicht das Zentrum selbst
                childHeads.add(starNode);
            }
        }

        return childHeads;
    }

    // ===== CONVENIENCE-METHODEN =====

    /**
     * Prüft, ob dieser Knoten das Zentrum des Sterns ist.
     * Nutzt StructureNode isHead() und isLeaf() direkt.
     *
     * @return true, wenn dieser Knoten das Zentrum ist
     */
    public boolean isCenter() {
        return isHead(StructureType.STAR) && !isLeaf();
    }

    /**
     * Prüft, ob dieser Knoten eine Kind-Head-Node des Sterns ist.
     * Nutzt StructureNode isHead() und getParent() direkt.
     *
     * @return true, wenn dieser Knoten eine Kind-Head-Node ist
     */
    public boolean isChildHead() {
        return isHead() && getParent() != null && !isHead(StructureType.STAR);
    }
}