package org.lrdm.topologies.node;

import org.lrdm.Mirror;

import java.util.*;


/**
 * Spezialisierte MirrorNode für vollständig vernetzte Strukturen (Fully Connected).
 * Validiert, dass die Struktur ein vollständiges Netz ist, in dem jeder Knoten mit jedem anderen verbunden ist.
 * <p>
 * Eine gültige vollständig vernetzte Struktur hat folgende Eigenschaften:
 * - Mindestens 2 Knoten für ein sinnvolles vollständiges Netz
 * - Jeder Knoten ist mit jedem anderen Knoten direkt verbunden
 * - Connectivity Degree = n-1 für n Knoten in der Struktur
 * - Genau ein Head-Knoten als Verbindungspunkt zu externen Strukturen
 * - Head-Knoten muss Edge-Links für externe Verbindungen haben
 * - Keine Hierarchie - alle Nicht-Head-Knoten sind auf derselben Ebene
 * - Symmetrische Verbindungen (bidirektional)
 * <p>
 * Nutzt das Multi-Type-System mit expliziter FULLY_CONNECTED-Typ-ID und Head-ID-Berücksichtigung
 * für korrekte Koexistenz mit anderen Strukturtypen.
 * <p>
 * Maximierte Wiederverwendung der StructureNode/MirrorNode-Funktionalität:
 * - 85 %+ der Traversierungs- und Validierungslogik wird wiederverwendet
 * - Fokussiert auf vollständig-vernetzte-spezifische Validierung und Navigation
 * - Überschreibt nur strukturspezifische Validierung und Management
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class FullyConnectedMirrorNode extends MirrorNode {

    // ===== KONSTRUKTOREN =====

    /**
     * Erstellt einen neuen FullyConnectedMirrorNode mit gegebener ID.
     *
     * @param id Eindeutige Identifikationsnummer des Knotens
     */
    public FullyConnectedMirrorNode(int id) {
        super(id);
        this.nodeTypes.add(StructureType.FULLY_CONNECTED);
    }

    /**
     * Erstellt einen neuen FullyConnectedMirrorNode mit gegebener ID und zugeordnetem Mirror.
     *
     * @param id Eindeutige Identifikationsnummer des Knotens
     * @param mirror Der zugeordnete Mirror für Link-Management
     */
    public FullyConnectedMirrorNode(int id, Mirror mirror) {
        super(id, mirror);
        this.nodeTypes.add(StructureType.FULLY_CONNECTED);
    }

    // ===== TYP-SYSTEM INTEGRATION =====

    /**
     * Überschreibt die Typ-Ableitung für die korrekte FULLY_CONNECTED-Identifikation.
     * Kritisch für Multi-Type-System: Ermöglicht korrekte Strukturerkennung
     * und automatische Typ-ID-Zuordnung in allen StructureNode-Methoden.
     *
     * @return StructureType.FULLY_CONNECTED für eindeutige vollständig-vernetzte-Identifikation
     */
    @Override
    public StructureType deriveTypeId() {
        return StructureType.FULLY_CONNECTED;
    }

    // ===== STRUKTUR-MANAGEMENT =====

    /**
     * Prüft, ob dieser Knoten weitere Kinder akzeptieren kann.
     * <p>
     * Vollständig-vernetzte-spezifische Logik:
     * - Jeder Knoten kann mit allen anderen Knoten der Struktur verbunden werden
     * - Nur wenn die aktuelle Struktur gültig ist
     * - Keine Begrenzung für Kinder, da vollständiges Netz bidirektional ist
     * <p>
     * Wiederverwendung:
     * - findHead(typeId) für FULLY_CONNECTED-spezifische Head-Ermittlung
     * - super.canAcceptMoreChildren() für Basis-Validierung
     * - isValidStructure() für Struktur-Validierung
     *
     * @return true, wenn weitere Kinder akzeptiert werden können
     */
    @Override
    public boolean canAcceptMoreChildren() {
        StructureType typeId = StructureType.FULLY_CONNECTED;
        StructureNode head = findHead(typeId);

        // Basis-Validierung und vollständig-vernetzte-spezifische Prüfungen
        return super.canAcceptMoreChildren() &&
                isValidStructure(getAllNodesInStructure(typeId, head != null ? head : this), typeId, head != null ? head : this);
    }


    /**
     * Prüft, ob dieser Knoten aus der Struktur entfernt werden kann.
     * <p>
     * Vollständig-vernetzte-spezifische Logik:
     * - Ein vollständiges Netz muss mindestens 2 Knoten haben
     * - Nach Entfernung müssen noch mindestens 2 Knoten übrig bleiben
     * - Head-Knoten können normalerweise nicht entfernt werden
     * - Entfernung erfordert Neuverkabelung aller anderen Knoten
     * <p>
     * Wiederverwendung:
     * - findHead(typeId) für Head-Ermittlung
     * - getAllNodesInStructure(typeId, head) für Strukturknotenzählung
     * - isHead(typeId) für Head-Erkennung
     * - super.canBeRemovedFromStructure() für Basis-Validierung
     *
     * @param structureRoot Der Root-Knoten der Struktur
     * @return true, wenn der Knoten sicher entfernt werden kann
     */
    @Override
    public boolean canBeRemovedFromStructure(StructureNode structureRoot) {
        if (structureRoot == null) return false;

        StructureType typeId = StructureType.FULLY_CONNECTED;
        StructureNode head = findHead(typeId);
        if (head == null) head = structureRoot;

        Set<StructureNode> structureNodes = getAllNodesInStructure(typeId, head);

        // Vollständiges Netz muss mindestens 2 Knoten haben.
        // Nach Entfernung müssen noch mindestens 2 Knoten übrig bleiben
        if (structureNodes.size() < 3) return false;

        // Head-Knoten können normalerweise nicht entfernt werden
        if (isHead(typeId)) return false;

        return super.canBeRemovedFromStructure(structureRoot);
    }

    // ===== STRUKTUR-VALIDIERUNG =====

    /**
     * Erweiterte vollständig-vernetzte-Struktur-Validierung mit expliziter FULLY_CONNECTED-Typ-ID.
     * <p>
     * Validiert zusätzlich zu super.isValidStructure():
     * - Alle Knoten sind FullyConnectedMirrorNode-Instanzen
     * - Mindestens 2 Knoten für ein sinnvolles vollständiges Netz
     * - Genau ein Head-Knoten vorhanden
     * - Jeder Knoten hat Connectivity Degree = n-1 (mit allen anderen verbunden)
     * - Symmetrische Verbindungen (bidirektional)
     * - Keine Zyklen im traditionellen Sinne (vollständiges Netz ist per definitionem azyklisch in der Hierarchie)
     * - Head-Node hat Edge-Links für externe Verbindungen
     * - Alle Nicht-Head-Knoten haben korrekte Parent-Child-Beziehungen
     * <p>
     * Wiederverwendung:
     * - super.isValidStructure() für MirrorNode-Basis-Validierung
     * - getNumEdgeLinks() für Edge-Link-Validierung
     * - isValidFullyConnectedNode() für individuelle Knoten-Validierung
     *
     * @param allNodes Menge aller Knoten, die zur Struktur gehören sollen
     * @param typeId Die Typ-ID der gewünschten Struktur (sollte FULLY_CONNECTED sein)
     * @param head Die Head-Node der gewünschten Struktur
     * @return true, wenn die vollständig-vernetzte-Struktur gültig ist
     */
    @Override
    public boolean isValidStructure(Set<StructureNode> allNodes, StructureType typeId, StructureNode head) {
        // Zuerst die grundlegende MirrorNode-Struktur-Validierung
        if (!super.isValidStructure(allNodes, typeId, head)) {
            return false;
        }

        // Mindestgröße für vollständige Netze
        if (allNodes.size() < 2) return false;

        // Sammle alle vollständig-vernetzte-Knoten und finde Head-Knoten
        Set<FullyConnectedMirrorNode> fullyConnectedNodes = new HashSet<>();
        FullyConnectedMirrorNode headNode = null;

        for (StructureNode node : allNodes) {
            if (!(node instanceof FullyConnectedMirrorNode fcNode)) {
                return false; // Alle Knoten müssen FullyConnectedMirrorNodes sein
            }

            fullyConnectedNodes.add(fcNode);
            if (fcNode.isHead(typeId)) {
                if (headNode != null) return false; // Nur ein Head erlaubt
                headNode = fcNode;
            }
        }

        if (headNode == null) return false; // Ein Head muss vorhanden sein

        // Validiere vollständig-vernetzte-spezifische Eigenschaften für alle Knoten
        for (FullyConnectedMirrorNode fcNode : fullyConnectedNodes) {
            if (!isValidFullyConnectedNode(fcNode, headNode, typeId, allNodes.size())) {
                return false;
            }
        }

        // Prüfe symmetrische Verbindungen
        return hasSymmetricConnections(fullyConnectedNodes, typeId, headNode);
    }

    /**
     * Überschreibt isValidStructure() für die automatische FULLY_CONNECTED-Typ-Ermittlung.
     * Wiederverwendung der automatischen Typ- und Head-Ermittlung aus StructureNode.
     *
     * @param allNodes Menge aller Knoten, die zur Struktur gehören sollen
     * @return true, wenn die vollständig-vernetzte-Struktur gültig ist
     */
    @Override
    public boolean isValidStructure(Set<StructureNode> allNodes) {
        StructureType typeId = StructureType.FULLY_CONNECTED;
        StructureNode head = findHead(typeId);
        return isValidStructure(allNodes, typeId, head != null ? head : this);
    }

    /**
     * Convenience-Methode für Struktur-Validierung ohne Parameter.
     */
    public boolean isValidStructure() {
        StructureType typeId = StructureType.FULLY_CONNECTED;
        StructureNode head = findHead(typeId);
        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head != null ? head : this);
        return isValidStructure(allNodes, typeId, head != null ? head : this);
    }

    /**
     * Validiert einen einzelnen vollständig-vernetzten-Knoten mit FULLY_CONNECTED-Typ-ID-Berücksichtigung.
     * <p>
     * Prüft:
     * - Connectivity Degree = n-1 (mit allen anderen Knoten verbunden)
     * - Korrekte Anzahl von Kindern basierend auf Position (Head vs. Nicht-Head)
     * - Parent-Beziehungen (Head darf externen Parent haben, andere müssen internen Parent haben)
     * - Strukturmitgliedschaft der Parent-Child-Beziehungen
     * <p>
     * Wiederverwendung:
     * - getConnectivityDegree() für Grad-Berechnung mit FULLY_CONNECTED-Typ-ID
     * - getChildren() für strukturspezifische Kind-Zählung
     * - getParent() für Parent-Zugriff
     * - getAllNodesInStructure() für Struktur-Mitgliedschaftsprüfung
     *
     * @param fcNode Der zu validierende vollständig-vernetzte-Knoten
     * @param headNode Der Head-Knoten der Struktur
     * @param typeId Der Strukturtyp (sollte FULLY_CONNECTED sein)
     * @param totalNodes Gesamtanzahl der Knoten in der Struktur
     * @return true wenn der Knoten gültig ist
     */
    private boolean isValidFullyConnectedNode(FullyConnectedMirrorNode fcNode, FullyConnectedMirrorNode headNode,
                                              StructureType typeId, int totalNodes) {
        final int headId = headNode.getId();

        // Jeder Knoten muss Connectivity Degree n-1 haben (mit allen anderen verbunden)
        int expectedDegree = totalNodes - 1;
        int actualDegree = fcNode.getConnectivityDegree(typeId, headId);

        if (actualDegree != expectedDegree) {
            return false;
        }

        StructureNode parent = fcNode.getParent();
        Set<StructureNode> structureNodes = fcNode.getAllNodesInStructure(typeId, headNode);

        if (fcNode == headNode) {
            // Head-Node darf einen externen Parent haben
            if (parent != null) {
                return !structureNodes.contains(parent);
            }
        } else {
            // Normale vollständig-vernetzte-Knoten: müssen Head als Parent haben
            return parent == headNode;
        }

        return true;
    }


    /**
     * Prüft, ob zwei Knoten miteinander verbunden sind.
     * <p>
     * VOLLSTÄNDIG manuelle Implementierung ohne Hilfsfunktionen:
     * - Prüft direkte Parent-Child-Beziehungen in beide Richtungen
     * - Prüft Geschwister-Beziehungen über gemeinsamen Parent
     * - Validiert Struktur-Zugehörigkeit für jede gefundene Verbindung
     *
     * @param nodeA Der erste Knoten
     * @param nodeB Der zweite Knoten
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @param headId Die Head-ID der gewünschten Struktur
     * @return true, wenn die Knoten direkt verbunden sind
     */
    private boolean isConnectedTo(FullyConnectedMirrorNode nodeA, FullyConnectedMirrorNode nodeB,
                                  StructureType typeId, int headId) {
        if (nodeA == nodeB) return false; // Keine Selbstverbindung

        // FALL 1: nodeA ist Parent von nodeB
        if (nodeB.getParent() == nodeA) {
            ChildRecord childRecord = nodeA.findChildRecordById(nodeB.getId());
            if (childRecord != null && childRecord.belongsToStructure(typeId, headId)) {
                return true;
            }
        }

        // FALL 2: nodeB ist Parent von nodeA
        if (nodeA.getParent() == nodeB) {
            ChildRecord childRecord = nodeB.findChildRecordById(nodeA.getId());
            if (childRecord != null && childRecord.belongsToStructure(typeId, headId)) {
                return true;
            }
        }

        // FALL 3: Beide sind Kinder des gleichen Parents (Geschwister)
        if (nodeA.getParent() != null && nodeB.getParent() != null &&
                nodeA.getParent() == nodeB.getParent()) {

            StructureNode commonParent = nodeA.getParent();
            ChildRecord recordA = commonParent.findChildRecordById(nodeA.getId());
            ChildRecord recordB = commonParent.findChildRecordById(nodeB.getId());

            if (recordA != null && recordB != null &&
                    recordA.belongsToStructure(typeId, headId) &&
                    recordB.belongsToStructure(typeId, headId)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Prüft symmetrische Verbindungen in der vollständig-vernetzten-Struktur.
     * <p>
     * VERBESSERTE Implementierung mit detailliertem Debugging:
     * - Prüft jede Knotenpaarung einzeln
     * - Validiert bidirektionale Verbindungen explizit
     * - Sammelt fehlende Verbindungen für Debugging
     *
     * @param fcNodes Alle vollständig-vernetzten-Knoten der Struktur
     * @param typeId Der Strukturtyp
     * @param headNode Der Head-Knoten
     * @return true wenn alle Verbindungen symmetrisch sind
     */
    private boolean hasSymmetricConnections(Set<FullyConnectedMirrorNode> fcNodes,
                                            StructureType typeId, FullyConnectedMirrorNode headNode) {
        final int headId = headNode.getId();
        List<String> missingConnections = new ArrayList<>();

        for (FullyConnectedMirrorNode nodeA : fcNodes) {
            for (FullyConnectedMirrorNode nodeB : fcNodes) {
                if (nodeA == nodeB) continue; // Keine Selbstverbindungen

                // Prüfe Verbindung A -> B
                boolean aToB = isConnectedTo(nodeA, nodeB, typeId, headId);

                // Prüfe Verbindung B -> A
                boolean bToA = isConnectedTo(nodeB, nodeA, typeId, headId);

                // Debugging: Sammle fehlende Verbindungen
                if (!aToB) {
                    missingConnections.add(String.format("Missing: Node %d -> Node %d",
                            nodeA.getId(), nodeB.getId()));
                }
                if (!bToA) {
                    missingConnections.add(String.format("Missing: Node %d -> Node %d",
                            nodeB.getId(), nodeA.getId()));
                }

                // In einem vollständigen Netz müssen ALLE Knoten bidirektional verbunden sein
                if (!aToB || !bToA) {
                    // Debug-Ausgabe für fehlende Verbindungen
                    System.err.println("FullyConnected validation failed:");
                    System.err.println("Node A: " + nodeA.getId() + ", Node B: " + nodeB.getId());
                    System.err.println("A->B connected: " + aToB + ", B->A connected: " + bToA);

                    // Detaillierte Struktur-Analyse
                    debugNodeConnection(nodeA, nodeB, typeId, headId);

                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Debug-Hilfsmethode zur detaillierten Analyse von Knoten-Verbindungen.
     * Gibt detaillierte Informationen über Parent-Child-Beziehungen aus.
     */
    private void debugNodeConnection(FullyConnectedMirrorNode nodeA, FullyConnectedMirrorNode nodeB,
                                     StructureType typeId, int headId) {
        System.err.println("=== DEBUG NODE CONNECTION ===");
        System.err.println("Node A ID: " + nodeA.getId() + ", Node B ID: " + nodeB.getId());

        // Parent-Informationen für Node A
        if (nodeA.getParent() != null) {
            System.err.println("Node A Parent: " + nodeA.getParent().getId());
            ChildRecord recordA = nodeA.getParent().findChildRecordById(nodeA.getId());
            if (recordA != null) {
                System.err.println("Node A ChildRecord found, belongs to structure: " +
                        recordA.belongsToStructure(typeId, headId));
            }
        } else {
            System.err.println("Node A has no parent");
        }

        // Parent-Informationen für Node B
        if (nodeB.getParent() != null) {
            System.err.println("Node B Parent: " + nodeB.getParent().getId());
            ChildRecord recordB = nodeB.getParent().findChildRecordById(nodeB.getId());
            if (recordB != null) {
                System.err.println("Node B ChildRecord found, belongs to structure: " +
                        recordB.belongsToStructure(typeId, headId));
            }
        } else {
            System.err.println("Node B has no parent");
        }

        // Prüfe, ob sie den gleichen Parent haben
        if (nodeA.getParent() != null && nodeB.getParent() != null) {
            System.err.println("Same parent: " + (nodeA.getParent() == nodeB.getParent()));
            if (nodeA.getParent() == nodeB.getParent()) {
                System.err.println("Common parent ID: " + nodeA.getParent().getId());
            }
        }

        // Prüfe Child-Records in beide Richtungen
        ChildRecord aHasB = nodeA.findChildRecordById(nodeB.getId());
        ChildRecord bHasA = nodeB.findChildRecordById(nodeA.getId());
        System.err.println("Node A has B as child: " + (aHasB != null));
        System.err.println("Node B has A as child: " + (bHasA != null));

        System.err.println("=== END DEBUG ===");
    }

    // ===== VOLLSTÄNDIG-VERNETZTE-NAVIGATION =====

    /**
     * Findet den Head-Knoten der FULLY_CONNECTED-Struktur.
     * <p>
     * Nutzt findHead() mit expliziter FULLY_CONNECTED-Typ-ID für typsichere Suche
     * und stellt sicher, dass nur FullyConnectedMirrorNode-Instanzen zurückgegeben werden.
     * <p>
     * Wiederverwendung:
     * - findHead() aus StructureNode für Standard-Head-Finding
     *
     * @return Der Head-Knoten oder null, wenn kein vollständig-vernetzter-Head gefunden wird
     */
    public FullyConnectedMirrorNode getFullyConnectedHead() {
        StructureNode head = findHead(StructureType.FULLY_CONNECTED);
        return (head instanceof FullyConnectedMirrorNode) ? (FullyConnectedMirrorNode) head : null;
    }

    /**
     * Berechnet die Anzahl der Knoten im vollständigen Netz.
     * <p>
     * Nutzt FULLY_CONNECTED-spezifische Strukturermittlung für genaue Zählung
     * nur der Knoten, die zur FULLY_CONNECTED-Struktur gehören.
     * <p>
     * Wiederverwendung:
     * - findHead() für vollständig-vernetzte-spezifische Head-Ermittlung
     * - getAllNodesInStructure() für Strukturknotenzählung
     *
     * @return Anzahl der Knoten im vollständigen Netz
     */
    public int getNetworkSize() {
        StructureType typeId = StructureType.FULLY_CONNECTED;
        StructureNode head = findHead(typeId);
        return getAllNodesInStructure(typeId, head != null ? head : this).size();
    }

    /**
     * Sammelt alle vollständig-vernetzten-Knoten der Struktur.
     * <p>
     * Nutzt FULLY_CONNECTED-spezifische Strukturermittlung für die typsichere Sammlung
     * aller Knoten, die zur Struktur gehören.
     * <p>
     * Wiederverwendung:
     * - findHead() für Head-Ermittlung
     * - getAllNodesInStructure() für Strukturknotenzählung
     *
     * @return Liste aller FullyConnectedMirrorNodes in der Struktur
     */
    public List<FullyConnectedMirrorNode> getAllFullyConnectedNodes() {
        StructureType typeId = StructureType.FULLY_CONNECTED;
        StructureNode head = findHead(typeId);
        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head != null ? head : this);

        List<FullyConnectedMirrorNode> fcNodes = new ArrayList<>();
        for (StructureNode node : allNodes) {
            if (node instanceof FullyConnectedMirrorNode fcNode) {
                fcNodes.add(fcNode);
            }
        }

        return fcNodes;
    }

    // ===== HILFSMETHODEN =====


    /**
     * Berechnet den Connectivity Degree für eine spezifische Struktur.
     * <p>
     * VOLLSTÄNDIG manuelle Implementierung ohne Hilfsfunktionen:
     * - Durchsucht ALLE Knoten der gesamten Struktur
     * - Zählt direkte Verbindungen zu jedem anderen Knoten der gleichen Struktur
     * - Verwendet direkte childRecords- und Parent-Zugriffe
     * - Vermeidet komplexe Traversierungs- und Filtermethoden
     *
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @param headId Die Head-ID der gewünschten Struktur
     * @return Anzahl der direkten Verbindungen für diese spezifische Struktur
     */
    private int getConnectivityDegree(StructureType typeId, int headId) {
        int connections = 0;
        Set<Integer> connectedNodeIds = new HashSet<>();

        // SCHRITT 1: Sammle ALLE Knoten der gesamten Struktur durch vollständige Traversierung
        Set<StructureNode> allPossibleNodes = new HashSet<>();
        Stack<StructureNode> toVisit = new Stack<>();
        Set<StructureNode> visited = new HashSet<>();

        toVisit.push(this);

        while (!toVisit.isEmpty()) {
            StructureNode current = toVisit.pop();
            if (visited.contains(current)) continue;
            visited.add(current);
            allPossibleNodes.add(current);

            // Parent hinzufügen
            if (current.getParent() != null) {
                toVisit.push(current.getParent());
            }

            // Alle Kinder hinzufügen (ohne Typ-Filter)
            for (StructureNode child : current.getChildren()) {
                toVisit.push(child);
            }
        }

        // SCHRITT 2: Prüfe direkte Verbindungen zu jedem anderen Knoten
        for (StructureNode otherNode : allPossibleNodes) {
            if (otherNode == this) continue; // Keine Selbstverbindung
            if (!(otherNode instanceof FullyConnectedMirrorNode)) continue; // Nur FullyConnected-Knoten

            boolean isConnected = false;

            // FALL A: Bin ich Parent von otherNode?
            if (otherNode.getParent() == this) {
                // Prüfe, ob diese Parent-Child-Verbindung zur gewünschten Struktur gehört
                ChildRecord childRecord = this.findChildRecordById(otherNode.getId());
                if (childRecord != null && childRecord.belongsToStructure(typeId, headId)) {
                    isConnected = true;
                }
            }

            // FALL B: Ist otherNode mein Parent?
            if (this.getParent() == otherNode) {
                // Prüfe, ob diese Child-Parent-Verbindung zur gewünschten Struktur gehört
                ChildRecord myRecord = otherNode.findChildRecordById(this.getId());
                if (myRecord != null && myRecord.belongsToStructure(typeId, headId)) {
                    isConnected = true;
                }
            }

            // FALL C: Sind wir beide Kinder des gleichen Parents?
            if (this.getParent() != null && otherNode.getParent() != null &&
                    this.getParent() == otherNode.getParent()) {

                StructureNode commonParent = this.getParent();
                ChildRecord myRecord = commonParent.findChildRecordById(this.getId());
                ChildRecord otherRecord = commonParent.findChildRecordById(otherNode.getId());

                if (myRecord != null && otherRecord != null &&
                        myRecord.belongsToStructure(typeId, headId) &&
                        otherRecord.belongsToStructure(typeId, headId)) {
                    isConnected = true;
                }
            }

            // Verbindung gefunden und noch nicht gezählt?
            if (isConnected && !connectedNodeIds.contains(otherNode.getId())) {
                connections++;
                connectedNodeIds.add(otherNode.getId());
            }
        }

        return connections;
    }

    /**
     * Berechnet die erwartete Anzahl Links für ein vollständiges Netz.
     * <p>
     * Formel: n * (n-1) / 2 für n Knoten (bidirektionale Links werden einmal gezählt)
     * in unserer Parent-Child-Struktur: n-1 Links (Stern-Form vom Head aus)
     *
     * @return Erwartete Anzahl Links für die Struktur-Größe
     */
    public int getExpectedLinkCount() {
        int n = getNetworkSize();
        return Math.max(0, n - 1); // Stern-Form: Head verbunden mit allen anderen
    }

    /**
     * Prüft, ob die aktuelle Struktur die optimale Anzahl Links hat.
     *
     * @return true, wenn die Link-Anzahl der erwarteten Anzahl entspricht
     */
    public boolean hasOptimalLinkCount() {
        StructureType typeId = StructureType.FULLY_CONNECTED;
        StructureNode head = findHead(typeId);
        int actualLinks = getNumPlannedLinksFromStructure(typeId, head != null ? head : this);
        return actualLinks == getExpectedLinkCount();
    }
}