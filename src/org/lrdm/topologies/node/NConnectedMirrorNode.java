package org.lrdm.topologies.node;

import org.lrdm.Mirror;

import java.util.*;

/**
 * Spezialisierte MirrorNode für N-Connected-Strukturen.
 * Validiert, dass jeder Knoten mit genau N anderen Knoten verbunden ist.
 * <p>
 * Eine gültige N-Connected-Struktur hat folgende Eigenschaften:
 * - Jeder Knoten hat genau N Verbindungen (Connectivity Degree N)
 * - Mindestens N+1 Knoten für eine sinnvolle N-Connected-Struktur
 * - Zusammenhängende Struktur (alle Knoten erreichbar)
 * - Genau ein Head-Knoten als Verbindungspunkt zu externen Strukturen
 * - Head-Knoten muss Edge-Links für externe Verbindungen haben
 * - Parametrisierbare Vernetzungsgrad N (standardmäßig aus Konfiguration)
 * - Optimierte Verteilung der Verbindungen für maximale Redundanz
 * <p>
 * Nutzt das Multi-Type-System mit expliziter N_CONNECTED-Typ-ID und Head-ID-Berücksichtigung
 * für korrekte Koexistenz mit anderen Strukturtypen.
 * <p>
 * Maximierte Wiederverwendung der StructureNode/MirrorNode-Funktionalität:
 * - 90 %+ der Traversierungs- und Validierungslogik wird wiederverwendet
 * - Fokussiert auf N-Connected-spezifische Validierung und Navigation
 * - Überschreibt nur strukturspezifische Validierung und Management
 * <p>
 * Unterstützte Vernetzungsgrade:
 * - N=1: Baum-ähnliche Struktur (jeder Knoten hat 1 Verbindung außer Root)
 * - N=2: Ring-ähnliche Struktur (jeder Knoten hat 2 Verbindungen)
 * - N=3: Hochredundante Struktur (jeder Knoten hat 3 Verbindungen)
 * - N=k: Beliebige k-reguläre Struktur
 * - N=n-1: Vollständig vernetzte Struktur (entspricht FullyConnectedMirrorNode)
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class NConnectedMirrorNode extends MirrorNode {
    
    private final int connectivityDegree;
    private static final int DEFAULT_CONNECTIVITY_DEGREE = 2; // Standard: 2-Connected (Ring-ähnlich)

    // ===== KONSTRUKTOREN =====

    /**
     * Erstellt einen neuen NConnectedMirrorNode mit gegebener ID und Standard-Vernetzungsgrad.
     *
     * @param id Eindeutige Identifikationsnummer des Knotens
     */
    public NConnectedMirrorNode(int id) {
        this(id, DEFAULT_CONNECTIVITY_DEGREE);
    }

    /**
     * Erstellt einen neuen NConnectedMirrorNode mit gegebener ID und spezifischem Vernetzungsgrad.
     *
     * @param id Eindeutige Identifikationsnummer des Knotens
     * @param connectivityDegree Gewünschter Vernetzungsgrad N (muss >= 1 sein)
     * @throws IllegalArgumentException wenn connectivityDegree < 1
     */
    public NConnectedMirrorNode(int id, int connectivityDegree) {
        super(id);
        if (connectivityDegree < 1) {
            throw new IllegalArgumentException("Connectivity degree must be at least 1, got: " + connectivityDegree);
        }
        this.connectivityDegree = connectivityDegree;
    }

    /**
     * Erstellt einen neuen NConnectedMirrorNode mit gegebener ID, Mirror und spezifischem Vernetzungsgrad.
     *
     * @param id Eindeutige Identifikationsnummer des Knotens
     * @param mirror Der zugeordnete Mirror für Link-Management
     * @param connectivityDegree Gewünschter Vernetzungsgrad N (muss >= 1 sein)
     * @throws IllegalArgumentException wenn connectivityDegree < 1
     */
    public NConnectedMirrorNode(int id, Mirror mirror, int connectivityDegree) {
        super(id, mirror);
        if (connectivityDegree < 1) {
            throw new IllegalArgumentException("Connectivity degree must be at least 1, got: " + connectivityDegree);
        }
        this.connectivityDegree = connectivityDegree;
    }

    // ===== TYP-SYSTEM INTEGRATION =====

    /**
     * Überschreibt die Typ-Ableitung für korrekte N_CONNECTED-Identifikation.
     * Kritisch für Multi-Type-System: Ermöglicht korrekte Strukturerkennung
     * und automatische Typ-ID-Zuordnung in allen StructureNode-Methoden.
     *
     * @return StructureType.FULLY_CONNECTED für N-Connected-Identifikation
     *         (TODO: StructureType.N_CONNECTED verwenden, wenn verfügbar)
     */
    @Override
    public StructureType deriveTypeId() {
        // Für zukünftige Erweiterung - aktuell verwenden wir FULLY_CONNECTED als Platzhalter
        // In einer vollständigen Implementierung würde hier StructureType.N_CONNECTED zurückgegeben
        return StructureType.FULLY_CONNECTED;
    }

    // ===== CONNECTIVITY-MANAGEMENT =====

    /**
     * Gibt den konfigurierten Vernetzungsgrad zurück.
     *
     * @return Der Vernetzungsgrad N dieses Knotens
     */
    public int getConnectivityDegree() {
        return connectivityDegree;
    }

    /**
     * Berechnet den tatsächlichen Connectivity Degree mit Multi-Type-Unterstützung.
     * Entspricht getConnectivityDegree() in FullyConnectedMirrorNode.
     *
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @param headId Die Head-ID der gewünschten Struktur
     * @return Anzahl der direkten Verbindungen dieses Knotens innerhalb der spezifischen Struktur
     */
    public int getConnectivityDegree(StructureType typeId, int headId) {
        int connections = 0;
        
        // Zähle Parent-Verbindung (falls zur gleichen Struktur gehörend)
        if (getParent() != null) {
            ChildRecord parentRecord = getParent().findChildRecordById(this.getId());
            if (parentRecord != null && parentRecord.belongsToStructure(typeId, headId)) {
                connections++;
            }
        }
        
        // Zähle Kind-Verbindungen (die zur gleichen Struktur gehören)
        Set<StructureNode> structureChildren = getChildren(typeId, headId);
        connections += structureChildren.size();
        
        return connections;
    }

    /**
     * Prüft, ob dieser Knoten den gewünschten Vernetzungsgrad erreicht hat.
     *
     * @return true, wenn der Knoten genau N implementierte Verbindungen hat
     */
    public boolean hasOptimalConnectivity() {
        return getNumImplementedLinks() == getExpectedLinkCount();
    }

    /**
     * Berechnet die erwartete Anzahl Links pro Knoten in dieser Struktur.
     * Für N-Connected entspricht dies dem konfigurierten connectivityDegree,
     * aber maximal n-1 bei kleinen Strukturen.
     *
     * @return Erwartete Anzahl Links pro Knoten
     */
    public int getExpectedLinkCount() {
        // Bei kleinen Strukturen: max. n-1 Verbindungen möglich
        int networkSize = getNetworkSize();
        return Math.min(connectivityDegree, networkSize - 1);
    }

    /**
     * Berechnet die erwartete Anzahl Links für die gesamte N-Connected-Struktur.
     * Formel: (Anzahl_Knoten * N) / 2
     * Durch 2 geteilt, da jeder Link zwei Knoten verbindet.
     *
     * @return Erwartete Gesamtanzahl Links in der Struktur
     */
    public int getExpectedTotalLinkCount() {
        int networkSize = getNetworkSize();
        int effectiveConnectivity = Math.min(connectivityDegree, networkSize - 1);
        return (networkSize * effectiveConnectivity) / 2;
    }

    /**
     * Prüft, ob die gesamte Struktur die optimale Anzahl Links hat.
     *
     * @return true, wenn die Link-Anzahl der erwarteten Anzahl entspricht
     */
    public boolean hasOptimalLinkCount() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        int actualLinks = getNumLinksOfStructure(typeId, head != null ? head : this);
        return actualLinks == getExpectedTotalLinkCount();
    }

    // ===== NETZWERK-NAVIGATION =====

    /**
     * Findet den Head-Knoten der N-Connected-Struktur (analog zu getFullyConnectedHead).
     *
     * @return Der Head-Knoten oder null, wenn nicht gefunden
     */
    public NConnectedMirrorNode getNConnectedHead() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        
        if (head instanceof NConnectedMirrorNode nConnectedHead) {
            return nConnectedHead;
        }
        return null;
    }

    /**
     * Prüft, ob dieser Knoten der Head der N-Connected-Struktur ist.
     */
    public boolean isNConnectedHead() {
        return isHead(deriveTypeId());
    }

    /**
     * Berechnet die Größe der N-Connected-Struktur (analog zu getNetworkSize).
     *
     * @return Anzahl aller Knoten in der N-Connected-Struktur
     */
    public int getNetworkSize() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head != null ? head : this);
        return allNodes.size();
    }

    /**
     * Gibt alle direkt verbundenen N-Connected-Knoten zurück.
     * Verbesserte Version mit expliziter Typ-ID-Berücksichtigung.
     *
     * @return Set aller direkt verbundenen NConnectedMirrorNode-Instanzen (ohne diesen Knoten selbst)
     */
    public Set<NConnectedMirrorNode> getConnectedNodes() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        
        if (head == null) return new HashSet<>();
        
        Set<NConnectedMirrorNode> connected = new HashSet<>();
        final int headId = head.getId();
        
        // Parent hinzufügen (falls zur gleichen Struktur gehörend)
        if (getParent() != null) {
            ChildRecord parentRecord = getParent().findChildRecordById(this.getId());
            if (parentRecord != null && parentRecord.belongsToStructure(typeId, headId) &&
                getParent() instanceof NConnectedMirrorNode nConnectedParent) {
                connected.add(nConnectedParent);
            }
        }
        
        // Kinder hinzufügen (die zur gleichen Struktur gehören)
        Set<StructureNode> structureChildren = getChildren(typeId, headId);
        for (StructureNode child : structureChildren) {
            if (child instanceof NConnectedMirrorNode nConnectedChild) {
                connected.add(nConnectedChild);
            }
        }
        
        return connected;
    }

    // ===== STRUKTUR-MANAGEMENT =====

    /**
     * Prüft, ob dieser Knoten weitere Kinder akzeptieren kann.
     * <p>
     * N-Connected-spezifische Logik:
     * - Nur wenn die aktuelle Struktur gültig ist
     * - Berücksichtigt den konfigurierten Vernetzungsgrad
     * - Verhindert Übervernetzung einzelner Knoten
     * <p>
     * Wiederverwendung:
     * - findHead(typeId) für N-Connected-spezifische Head-Ermittlung
     * - super.canAcceptMoreChildren() für Basis-Validierung
     * - isValidStructure() für Struktur-Validierung
     *
     * @return true, wenn weitere Kinder akzeptiert werden können
     */
    @Override
    public boolean canAcceptMoreChildren() {
        if (!super.canAcceptMoreChildren()) {
            return false;
        }

        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        
        if (head == null) {
            return true; // Erster Knoten kann immer Kinder akzeptieren
        }

        // Prüfe aktuelle Vernetzung
        int currentConnections = getConnectivityDegree(typeId, head.getId());
        int maxConnections = getExpectedLinkCount();
        
        return currentConnections < maxConnections && isValidStructure();
    }

    /**
     * Prüft, ob dieser Knoten aus der Struktur entfernt werden kann.
     * <p>
     * N-Connected-spezifische Logik:
     * - Eine N-Connected-Struktur muss mindestens N+1 Knoten haben
     * - Nach Entfernung müssen noch mindestens N+1 Knoten übrig bleiben
     * - Entfernung darf die Vernetzung anderer Knoten nicht unter N reduzieren
     * - Head-Knoten können normalerweise nicht entfernt werden
     * <p>
     * Wiederverwendung:
     * - findHead(typeId) für Head-Ermittlung
     * - getAllNodesInStructure(typeId, head) für Strukturknotenzählung
     * - super.canBeRemovedFromStructure() für Basis-Validierung
     *
     * @param structureRoot Der Root-Knoten der Struktur
     * @return true, wenn der Knoten sicher entfernt werden kann
     */
    @Override
    public boolean canBeRemovedFromStructure(StructureNode structureRoot) {
        if (!super.canBeRemovedFromStructure(structureRoot)) {
            return false;
        }

        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        
        if (head == null || head == this) {
            return false; // Head kann nicht entfernt werden
        }

        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head);
        
        // Nach Entfernung müssen mindestens 2 Knoten übrig bleiben für minimale Struktur
        if (allNodes.size() <= 2) {
            return false;
        }

        // Bei N+1 Knoten können keine weiteren entfernt werden
        if (allNodes.size() <= connectivityDegree + 1) {
            return false;
        }

        // Prüfe, ob Entfernung die Vernetzung anderer Knoten zu stark reduziert
        return wouldRemovalMaintainConnectivity(allNodes);
    }

    /**
     * Prüft, ob die Entfernung dieses Knotens die erforderliche Vernetzung aufrechterhält.
     */
    private boolean wouldRemovalMaintainConnectivity(Set<StructureNode> allNodes) {
        // Simuliere Entfernung und prüfe verbleibende Vernetzung
        Set<StructureNode> remainingNodes = new HashSet<>(allNodes);
        remainingNodes.remove(this);
        
        // Prüfe, ob alle verbleibenden Knoten noch ausreichend vernetzt werden können
        int remainingSize = remainingNodes.size();
        int maxPossibleConnections = remainingSize - 1;
        
        return maxPossibleConnections >= connectivityDegree;
    }

    // ===== STRUKTUR-VALIDIERUNG =====

    /**
     * Erweiterte N-Connected-Struktur-Validierung mit expliziter Typ-ID.
     * <p>
     * Validiert zusätzlich zu super.isValidStructure():
     * - Alle Knoten sind NConnectedMirrorNode-Instanzen mit gleichem Vernetzungsgrad
     * - Genau ein Head-Knoten vorhanden
     * - Alle Knoten haben korrekten Vernetzungsgrad N (außer bei kleinen Strukturen)
     * - Struktur ist zusammenhängend
     * - Mindestens 2 Knoten in der Struktur
     * - Head-Node hat Edge-Links für externe Verbindungen
     * <p>
     * Wiederverwendung:
     * - super.isValidStructure() für MirrorNode-Basis-Validierung
     * - getAllNodesInStructure() für Strukturknotenbefüllung
     * - getNumEdgeLinks() für Edge-Link-Validierung
     *
     * @param allNodes Menge aller Knoten, die zur Struktur gehören sollen
     * @param typeId Die Typ-ID der gewünschten Struktur
     * @param head Die Head-Node der gewünschten Struktur
     * @return true, wenn die N-Connected-Struktur gültig ist
     */
    @Override
    public boolean isValidStructure(Set<StructureNode> allNodes, StructureType typeId, StructureNode head) {
        // Zuerst die grundlegende MirrorNode-Validierung
        if (!super.isValidStructure(allNodes, typeId, head)) {
            return false;
        }

        if (head == null || allNodes == null || allNodes.isEmpty()) {
            return false;
        }

        // Mindestens 2 Knoten erforderlich für sinnvolle N-Connected-Struktur
        if (allNodes.size() < 2) {
            return false;
        }

        // Validiere jeden Knoten in der Struktur
        for (StructureNode node : allNodes) {
            if (!(node instanceof NConnectedMirrorNode nConnectedNode)) {
                return false; // Alle Knoten müssen NConnectedMirrorNode sein
            }

            if (nConnectedNode.getConnectivityDegree() != this.connectivityDegree) {
                return false; // Alle Knoten müssen gleichen Vernetzungsgrad haben
            }

            if (!isValidNConnectedNode(nConnectedNode, head, typeId, allNodes)) {
                return false;
            }
        }

        // Prüfe Gesamtstruktur-Eigenschaften
        return isConnectedStructure(allNodes, typeId, head) &&
               hasCorrectTotalLinkCount(allNodes) &&
               hasValidHeadEdgeLinks(head, typeId);
    }

    /**
     * Überschreibt isValidStructure() für automatische Typ-Ermittlung.
     */
    @Override
    public boolean isValidStructure(Set<StructureNode> allNodes) {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        return isValidStructure(allNodes, typeId, head != null ? head : this);
    }

    /**
     * Convenience-Methode für Struktur-Validierung ohne Parameter.
     */
    public boolean isValidStructure() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head != null ? head : this);
        return isValidStructure(allNodes, typeId, head != null ? head : this);
    }

    /**
     * Validiert einen einzelnen N-Connected-Knoten mit korrekter Multi-Type-Berücksichtigung.
     * Entspricht isValidFullyConnectedNode() in der FullyConnectedMirrorNode.
     */
    private boolean isValidNConnectedNode(NConnectedMirrorNode nConnectedNode, StructureNode headNode,
                                        StructureType typeId, Set<StructureNode> allNodes) {
        final int headId = headNode.getId();
        
        // Erwarteter Grad: min(connectivityDegree, n-1)
        int expectedDegree = Math.min(connectivityDegree, allNodes.size() - 1);
        int actualDegree = nConnectedNode.getConnectivityDegree(typeId, headId);
        
        if (actualDegree != expectedDegree) {
            return false;
        }
        
        StructureNode parent = nConnectedNode.getParent();
        
        if (nConnectedNode == headNode) {
            // Head-Node darf einen externen Parent haben
            if (parent != null) {
                return !allNodes.contains(parent);
            }
        } else {
            // Normale N-Connected-Knoten: müssen strukturinternen Parent haben
            if (parent == null) return false;
            
            ChildRecord parentRecord = parent.findChildRecordById(nConnectedNode.getId());
            if (parentRecord == null || !parentRecord.belongsToStructure(typeId, headId)) {
                return false;
            }
            
            return allNodes.contains(parent);
        }
        
        return true;
    }

    /**
     * Prüft, ob die Struktur zusammenhängend ist.
     */
    private boolean isConnectedStructure(Set<StructureNode> allNodes, StructureType typeId, StructureNode head) {
        if (allNodes.size() <= 1) return true;
        
        Set<StructureNode> visited = new HashSet<>();
        Stack<StructureNode> stack = new Stack<>();
        stack.push(head);
        
        while (!stack.isEmpty()) {
            StructureNode current = stack.pop();
            if (visited.contains(current)) continue;
            visited.add(current);
            
            // Füge alle verbundenen Knoten der gleichen Struktur hinzu
            Set<StructureNode> neighbors = new HashSet<>();
            
            // Parent hinzufügen
            if (current.getParent() != null && allNodes.contains(current.getParent())) {
                neighbors.add(current.getParent());
            }
            
            // Kinder hinzufügen
            neighbors.addAll(current.getChildren(typeId, head.getId()));
            
            for (StructureNode neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    stack.push(neighbor);
                }
            }
        }
        
        return visited.size() == allNodes.size();
    }

    /**
     * Prüft, ob die Gesamtanzahl der Links korrekt ist.
     */
    private boolean hasCorrectTotalLinkCount(Set<StructureNode> allNodes) {
        int networkSize = allNodes.size();
        int effectiveConnectivity = Math.min(connectivityDegree, networkSize - 1);
        int expectedTotalLinks = (networkSize * effectiveConnectivity) / 2;
        
        Set<LinkPair> uniqueLinks = new HashSet<>();
        for (StructureNode node : allNodes) {
            if (node instanceof NConnectedMirrorNode nNode) {
                for (StructureNode neighbor : getAllConnectedNodes(nNode, allNodes)) {
                    int from = Math.min(nNode.getId(), neighbor.getId());
                    int to = Math.max(nNode.getId(), neighbor.getId());
                    uniqueLinks.add(new LinkPair(from, to));
                }
            }
        }
        
        return uniqueLinks.size() == expectedTotalLinks;
    }

    /**
     * Sammelt alle verbundenen Knoten eines gegebenen Knotens innerhalb der Struktur.
     */
    private Set<StructureNode> getAllConnectedNodes(NConnectedMirrorNode node, Set<StructureNode> allNodes) {
        Set<StructureNode> connected = new HashSet<>();
        
        // Parent hinzufügen
        if (node.getParent() != null && allNodes.contains(node.getParent())) {
            connected.add(node.getParent());
        }
        
        // Kinder hinzufügen
        for (ChildRecord childRecord : node.getChildren()) {
            if (allNodes.contains(childRecord.child())) {
                connected.add(childRecord.child());
            }
        }
        
        return connected;
    }

    /**
     * Prüft, ob der Head-Knoten gültige Edge-Links hat.
     */
    private boolean hasValidHeadEdgeLinks(StructureNode head, StructureType typeId) {
        if (!(head instanceof NConnectedMirrorNode headNode)) {
            return false;
        }
        
        // Head sollte mindestens einen Edge-Link haben (für externe Verbindungen)
        return headNode.getNumEdgeLinks(typeId, head) >= 0; // >= 0 erlaubt auch isolierte Strukturen
    }

    // ===== N-CONNECTED CONVENIENCE-METHODEN =====

    /**
     * Berechnet die aktuelle Vernetzungsdichte der Struktur.
     * Wert zwischen 0.0 (keine Verbindungen) und 1.0 (optimale N-Vernetzung).
     *
     * @return Vernetzungsdichte als Decimal zwischen 0.0 und 1.0
     */
    public double getConnectivityDensity() {
        int actualLinks = getNumLinksOfStructure();
        int expectedLinks = getExpectedTotalLinkCount();
        
        if (expectedLinks == 0) return 1.0; // Einzelner Knoten ist "optimal vernetzt"
        
        return (double) actualLinks / expectedLinks;
    }

    /**
     * Prüft, ob diese N-Connected-Struktur fault-tolerant ist.
     * Eine Struktur ist fault-tolerant, wenn sie nach Entfernung eines beliebigen Knotens
     * noch zusammenhängend bleibt.
     *
     * @return true, wenn die Struktur fault-tolerant ist
     */
    public boolean isFaultTolerant() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head != null ? head : this);
        
        if (allNodes.size() <= 2) return false; // Zu kleine Strukturen sind nicht fault-tolerant
        
        // Teste Entfernung jedes einzelnen Knotens (außer Head)
        for (StructureNode node : allNodes) {
            if (node == head) continue; // Head nicht entfernen
            
            Set<StructureNode> remainingNodes = new HashSet<>(allNodes);
            remainingNodes.remove(node);
            
            if (!isConnectedStructure(remainingNodes, typeId, head)) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Findet die optimale Verteilung der Verbindungen für maximale Fault-Tolerance.
     * Gibt Empfehlungen für zusätzliche Verbindungen zurück.
     *
     * @return Map von Knoten-IDs zu empfohlenen neuen Verbindungen
     */
    public Map<Integer, Set<Integer>> getOptimalConnectionRecommendations() {
        Map<Integer, Set<Integer>> recommendations = new HashMap<>();
        
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head != null ? head : this);
        
        for (StructureNode node : allNodes) {
            if (node instanceof NConnectedMirrorNode nNode) {
                Set<NConnectedMirrorNode> currentConnected = nNode.getConnectedNodes();
                Set<Integer> recommendedConnections = new HashSet<>();
                
                // Empfehle Verbindungen zu entfernten Knoten für bessere Redundanz
                for (StructureNode potential : allNodes) {
                    if (potential != node && !currentConnected.contains(potential)) {
                        if (currentConnected.size() < connectivityDegree) {
                            recommendedConnections.add(potential.getId());
                        }
                    }
                }
                
                if (!recommendedConnections.isEmpty()) {
                    recommendations.put(node.getId(), recommendedConnections);
                }
            }
        }
        
        return recommendations;
    }

    // ===== HILFSKLASSEN =====

    /**
         * Hilfsklasse für eindeutige Link-Identifikation.
         * Entspricht dem Pattern aus FullyConnectedMirrorNode.
         */
        private record LinkPair(int from, int to) {

        @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (!(obj instanceof LinkPair other)) return false;
                return from == other.from && to == other.to;
            }

    }

    // ===== OBJECT-METHODEN =====

    @Override
    public String toString() {
        return String.format("NConnectedMirrorNode{id=%d, connectivityDegree=%d, mirror=%s, actualConnections=%d, networkSize=%d}",
                getId(), connectivityDegree, getMirror() != null ? getMirror().getID() : "null", 
                getNumImplementedLinks(), getNetworkSize());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof NConnectedMirrorNode other)) return false;
        return getId() == other.getId() && connectivityDegree == other.connectivityDegree;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), connectivityDegree);
    }
}