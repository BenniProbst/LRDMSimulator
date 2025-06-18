package org.lrdm.topologies.strategies;

import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.Action;
import org.lrdm.topologies.node.MirrorNode;
import org.lrdm.topologies.node.StructureNode;
import org.lrdm.util.IDGenerator;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Abstrakte Basisklasse für hierarchische Substruktur-basierte TopologyStrategy.
 * <p>
 * **Zweck**: Erfüllung des TopologyStrategy-Contracts mit integrierter StructureBuilder-Funktionalität.
 * Die 5 essenziellen TopologyStrategy-Methoden sind die einzige öffentliche API.
 * <p>
 * **Interne StructureBuilder-Integration**:
 * - Verwaltet MirrorNode-basierte Strukturen
 * - Verwendet mirrorIterator für verfügbare, noch nicht zugeordnete Mirrors
 * - Observer Pattern für Struktur-Änderungen
 * - Rekursive Link-Erstellung mit Mirror-Zuordnung
 * <p>
 * **Public API** (nur TopologyStrategy-Methoden):
 * - initNetwork(), restartNetwork(), handleAddNewMirrors()
 * - getNumTargetLinks(), getPredictedNumTargetLinks()
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public abstract class BuildAsSubstructure extends TopologyStrategy {

    // ===== EINDEUTIGE SUBSTRUKTUR-ID =====
    private final int substructureId;

    // ===== STRUCTURE BUILDER-INTEGRATION (PROTECTED) =====
    protected IDGenerator idGenerator;
    protected Network network;
    protected Iterator<Mirror> mirrorIterator;

    // ===== MIRROR NODE-BASIERTE SUBSTRUKTUR-VERWALTUNG (PRIVATE) =====
    private final Map<MirrorNode, BuildAsSubstructure> nodeToSubstructure = new HashMap<>();
    private final Set<MirrorNode> structureNodes = new HashSet<>();
    private MirrorNode currentStructureRoot; // NICHT FINAL - Builder-kompatibel

    // ===== OBSERVER PATTERN (PRIVATE) =====
    private final List<StructureChangeObserver> observers = new ArrayList<>();

    // ===== KONSTRUKTOR =====

    public BuildAsSubstructure() {
        this.idGenerator = IDGenerator.getInstance();
        this.substructureId = idGenerator.getNextID();
    }

    public BuildAsSubstructure(IDGenerator idGenerator) {
        this.idGenerator = idGenerator;
        this.substructureId = idGenerator.getNextID();
    }

    // ===== NEUE GETTER UND SETTER (PROTECTED) =====

    /**
     * Gibt die eindeutige Substruktur-ID zurück.
     *
     * @return Die eindeutige ID dieser Substruktur
     */
    public final int getSubstructureId() {
        return substructureId;
    }

    /**
     * Gibt die Root-Node der aktuellen Struktur zurück.
     *
     * @return Die Root-Node der Struktur oder null, wenn noch nicht initialisiert
     */
    public final MirrorNode getCurrentStructureRoot() {
        return currentStructureRoot;
    }

    /**
     * Setzt die Root-Node der aktuellen Struktur (für Builder).
     * PROTECTED - nur für Builder und interne Nutzung.
     *
     * @param root Die neue Root-Node
     */
    protected final void setCurrentStructureRoot(MirrorNode root) {
        this.currentStructureRoot = root;
        if (root != null) {
            addToStructureNodes(root);
        }
    }

    /**
     * Gibt den Strukturtyp der aktuellen Substruktur zurück.
     * Ermittelt den Typ über die Root-Node.
     *
     * @return Der StructureType der aktuellen Substruktur oder null, wenn nicht, initialisiert
     */
    public final StructureNode.StructureType getCurrentStructureType() {
        if (currentStructureRoot == null) {
            return null;
        }
        return currentStructureRoot.deriveTypeId();
    }

    /**
     * Gibt alle aktuell verwalteten MirrorNodes der Struktur zurück.
     * Diese Collection wird aktiv gepflegt und ermöglicht die Auswahl spezifischer Knoten
     * für den Anbau weiterer Substrukturen.
     *
     * @return Unveränderliche Kopie aller MirrorNodes in der Struktur
     */
    public final Set<MirrorNode> getAllStructureNodes() {
        return Set.copyOf(structureNodes);
    }

    /**
     * Gibt die Zuordnung von MirrorNode zu BuildAsSubstructure zurück.
     * PROTECTED - ermöglicht Subklassen den Zugriff auf Substruktur-Zuordnungen.
     *
     * @return Unveränderliche Kopie der Node-zu-Substruktur-Zuordnung
     */
    protected final Map<MirrorNode, BuildAsSubstructure> getNodeToSubstructureMapping() {
        return Map.copyOf(nodeToSubstructure);
    }

    /**
     * Findet die Substruktur, zu der ein bestimmter MirrorNode gehört.
     * PROTECTED - ermöglicht Subklassen die Identifikation von Substrukturen.
     *
     * @param node Der MirrorNode
     * @return Die zugehörige BuildAsSubstructure oder null, wenn nicht gefunden
     */
    protected final BuildAsSubstructure findSubstructureForNode(MirrorNode node) {
        return nodeToSubstructure.get(node);
    }

    /**
     * Gibt alle Substruktur-Tupel zurück (MirrorNode, BuildAsSubstructure).
     * PROTECTED - ermöglicht Iteration über alle Substruktur-Zuordnungen.
     *
     * @return Liste aller Substruktur-Tupel
     */
    protected final List<SubstructureTuple> getAllSubstructureTuples() {
        return nodeToSubstructure.entrySet().stream()
                .map(entry -> new SubstructureTuple(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * Tupel-Klasse für MirrorNode-BuildAsSubstructure-Zuordnungen.
     * Ermöglicht die typsichere Rückgabe von Zuordnungspaaren.
     */
    public static record SubstructureTuple(MirrorNode node, BuildAsSubstructure substructure) {
        public SubstructureTuple {
            Objects.requireNonNull(node, "MirrorNode darf nicht null sein");
            Objects.requireNonNull(substructure, "BuildAsSubstructure darf nicht null sein");
        }

        /**
         * Gibt die ID des MirrorNodes zurück.
         */
        public int getNodeId() {
            return node.getId();
        }

        /**
         * Gibt die Substruktur-ID zurück.
         */
        public int getSubstructureId() {
            return substructure.getSubstructureId();
        }

        /**
         * Prüft, ob dieser Knoten zu einer bestimmten Substruktur-ID gehört.
         */
        public boolean belongsToSubstructure(int substructureId) {
            return this.substructure.getSubstructureId() == substructureId;
        }
    }

    /**
     * Setzt eine Substruktur-Zuordnung.
     * PROTECTED - ermöglicht Builder die Registrierung von Substruktur-Zuordnungen.
     *
     * @param node Die MirrorNode
     * @param substructure Die zugehörige BuildAsSubstructure
     */
    protected final void setSubstructureForNode(MirrorNode node, BuildAsSubstructure substructure) {
        if (node != null && substructure != null) {
            nodeToSubstructure.put(node, substructure);
        }
    }

    /**
     * Entfernt eine Substruktur-Zuordnung.
     * PROTECTED - ermöglicht Builder die Deregistrierung von Substruktur-Zuordnungen.
     *
     * @param node Die MirrorNode
     */
    protected final void removeSubstructureForNode(MirrorNode node) {
        nodeToSubstructure.remove(node);
    }

    /**
     * Gibt alle verfügbaren Endpunkte (Terminal-Knoten) zurück, an die neue Substrukturen
     * angebaut werden können.
     *
     * @return Set aller Terminal-MirrorNodes, die für Substruktur-Erweiterungen geeignet sind
     */
    public final Set<MirrorNode> getAvailableConnectionPoints() {
        if (currentStructureRoot == null) {
            return Set.of();
        }

        StructureNode.StructureType typeId = currentStructureRoot.deriveTypeId();
        int headId = currentStructureRoot.getId();

        return structureNodes.stream()
                .filter(node -> node.canAcceptMoreChildren(typeId, headId))
                .filter(node -> node.isTerminal(typeId, headId) || node.isHead(typeId))
                .collect(Collectors.toSet());
    }

    /**
     * Sucht einen spezifischen MirrorNode anhand seiner ID.
     *
     * @param nodeId Die ID des gesuchten Knotens
     * @return Der MirrorNode mit der angegebenen ID oder null, wenn nicht gefunden
     */
    public final MirrorNode findStructureNodeById(int nodeId) {
        return structureNodes.stream()
                .filter(node -> node.getId() == nodeId)
                .findFirst()
                .orElse(null);
    }

    // ===== TOPOLOGY STRATEGY CONTRACT (PUBLIC API) =====

    /**
     * Initialisiert das Netzwerk durch Aufbau der strukturspezifischen Topologie.
     * Erstellt eine MirrorNode-Struktur und verknüpft alle Links.
     *
     * @param n Das Netzwerk
     * @param props Simulation Properties
     * @return Set aller erstellten Links
     */
    @Override
    public Set<Link> initNetwork(Network n, Properties props) {
        initializeInternalState(n);

        MirrorNode root = buildStructure(n.getNumMirrors(), props);
        if (root != null) {
            setCurrentStructureRoot(root);
            return buildAndConnectLinks(root, props);
        }

        return new HashSet<>();
    }

    /**
     * Startet das Netzwerk komplett neu mit der aktuellen Topologie.
     * Löscht alle bestehenden Verbindungen und baut sie neu auf.
     *
     * @param n Das Netzwerk
     * @param props Simulation Properties
     * @param simTime Aktuelle Simulationszeit
     */
    @Override
    public void restartNetwork(Network n, Properties props, int simTime) {
        super.restartNetwork(n, props, simTime);

        // Alle internen Strukturen zurücksetzen
        resetInternalState();

        // Komplett neu initialisieren
        Set<Link> newLinks = initNetwork(n, props);
        n.getLinks().addAll(newLinks);
    }

    /**
     * Fügt neue Mirrors zum Netzwerk hinzu und integriert sie in die bestehende Struktur.
     * Verwendet die strukturspezifische addNodes-Logik.
     *
     * @param n Das Netzwerk
     * @param newMirrors Anzahl hinzuzufügender Mirrors
     * @param props Simulation Properties
     * @param simTime Aktuelle Simulationszeit
     */
    @Override
    public void handleAddNewMirrors(Network n, int newMirrors, Properties props, int simTime) {
        // Neue Mirrors erstellen
        List<Mirror> addedMirrors = createMirrors(newMirrors, simTime, props);
        n.getMirrors().addAll(addedMirrors);

        // Iterator für neue Mirrors setzen
        setMirrorIterator(addedMirrors.iterator());

        // Zu bestehender Struktur hinzufügen
        int actuallyAdded = addNodesToStructure(newMirrors);

        if (actuallyAdded > 0) {
            // Links für die gesamte Struktur neu aufbauen
            MirrorNode root = getCurrentStructureRoot();
            if (root != null) {
                Set<Link> newLinks = buildAndConnectLinks(root, props);
                n.getLinks().addAll(newLinks);
            }
        } else {
            // Fallback: Komplett neu aufbauen
            restartNetwork(n, props, simTime);
        }
    }

    /**
     * Gibt die erwartete Anzahl Links für das Netzwerk gemäß dieser Topologie zurück.
     * Muss von Subklassen implementiert werden.
     *
     * @param n Das Netzwerk
     * @return Erwartete Anzahl Links
     */
    @Override
    public abstract int getNumTargetLinks(Network n);

    /**
     * Gibt die vorhergesagte Anzahl von Links zurück, falls die Action ausgeführt wird.
     * Muss von Subklassen implementiert werden.
     *
     * @param a Die potenziell auszuführende Action
     * @return Vorhergesagte Anzahl Links
     */
    @Override
    public abstract int getPredictedNumTargetLinks(Action a);

    // ===== ABSTRAKTE Strukturbuilder-METHODEN (PROTECTED) =====

    /**
     * Erstellt eine neue Struktur mit der angegebenen Anzahl von Knoten.
     * Backwards-Kompatibilität: Ruft buildStructure(totalNodes, 0) auf.
     *
     * @param totalNodes Anzahl der zu erstellenden Knoten
     * @param props
     * @return Root-Knoten der erstellten Struktur
     */
    protected final MirrorNode buildStructure(int totalNodes, Properties props) {
        return buildStructure(totalNodes, 0, props);
    }

    /**
     * Erstellt eine neue Struktur mit der angegebenen Anzahl von Knoten.
     * Muss von Subklassen für spezifische Strukturtypen implementiert werden.
     *
     * @param totalNodes Anzahl der zu erstellenden Knoten
     * @param simTime    Aktuelle Simulationszeit für Link-Erstellung
     * @param props
     * @return Root-Knoten der erstellten Struktur
     */
    protected abstract MirrorNode buildStructure(int totalNodes, int simTime, Properties props);

    /**
     * Fügt Knoten zu einer bestehenden Struktur hinzu.
     * Muss von Subklassen für spezifische Strukturlogik implementiert werden.
     *
     * @param nodesToAdd Anzahl der hinzuzufügenden Knoten
     * @return Anzahl der tatsächlich hinzugefügten Knoten
     */
    protected abstract int addNodesToStructure(int nodesToAdd);

    /**
     * Entfernt Knoten aus einer bestehenden Struktur.
     * Kann von Subklassen überschrieben werden.
     *
     * @param nodesToRemove Anzahl der zu entfernenden Knoten
     * @return Anzahl der tatsächlich entfernten Knoten
     */
    protected abstract int removeNodesFromStructure(int nodesToRemove);

    // ===== INTERNE STRUCTURE BUILDER-HILFSMETHODEN (PROTECTED) =====

    /**
     * Fügt einen MirrorNode zu den verwalteten Struktur-Knoten hinzu.
     * Stellt sicher, dass structureNodes immer aktuell ist.
     *
     * @param node Der hinzuzufügende MirrorNode
     */
    protected final void addToStructureNodes(MirrorNode node) {
        if (node != null) {
            structureNodes.add(node);
            nodeToSubstructure.put(node, this);
        }
    }

    /**
     * Entfernt einen MirrorNode aus den verwalteten Struktur-Knoten.
     * Stellt sicher, dass structureNodes immer aktuell ist.
     *
     * @param node Der zu entfernende MirrorNode
     */
    protected final void removeFromStructureNodes(MirrorNode node) {
        if (node != null) {
            structureNodes.remove(node);
            nodeToSubstructure.remove(node);
        }
    }


    /**
     * Erweiterte handleRemoveMirrors-Implementierung für BuildAsSubstructure.
     * Ergänzt die Standard-TopologyStrategy-Funktionalität um StructureNode-Management.
     *
     * @param n Das Netzwerk
     * @param removeMirrors Anzahl der zu entfernenden Mirrors
     * @param props Simulation Properties
     * @param simTime Aktuelle Simulationszeit
     */
    @Override
    public void handleRemoveMirrors(Network n, int removeMirrors, Properties props, int simTime) {
        // 1. Sammle die MirrorNodes, die entfernt werden sollen (vor dem Mirror-Shutdown)
        List<MirrorNode> nodesToRemove = new ArrayList<>();
        List<Mirror> sortedMirrors = n.getMirrorsSortedById();

        for (int i = 0; i < removeMirrors && i < sortedMirrors.size(); i++) {
            Mirror mirrorToRemove = sortedMirrors.get(sortedMirrors.size() - 1 - i);
            MirrorNode nodeToRemove = findMirrorNodeForMirror(mirrorToRemove);
            if (nodeToRemove != null) {
                nodesToRemove.add(nodeToRemove);
            }
        }

        // 2. Standard-Mirror-Shutdown (behandelt Links automatisch)
        super.handleRemoveMirrors(n, removeMirrors, props, simTime);

        // 3. StructureNode-Verwaltung bereinigen - verwendet die bestehende Methode
        cleanupStructureNodes(nodesToRemove);
    }

    /**
     * Hilfsmethode: Findet den MirrorNode für einen gegebenen Mirror.
     *
     * @param mirror Der Mirror, für den der MirrorNode gesucht wird
     * @return Der zugehörige MirrorNode oder null, wenn nicht gefunden
     */
    private MirrorNode findMirrorNodeForMirror(Mirror mirror) {
        if (mirror == null) return null;

        for (MirrorNode node : structureNodes) {
            if (node.getMirror() != null && node.getMirror().getID() == mirror.getID()) {
                return node;
            }
        }
        return null;
    }

    /**
     * Bereinigt Parent-Child-Beziehungen für einen entfernten Knoten.
     *
     * @param nodeToRemove Der zu entfernende Knoten
     */
    private void cleanupNodeRelationships(MirrorNode nodeToRemove) {
        // Parent-Verbindung bereinigen
        if (nodeToRemove.getParent() != null) {
            nodeToRemove.getParent().removeChild(nodeToRemove);
        }

        // Kinder-Verbindungen bereinigen
        List<StructureNode> children = new ArrayList<>(nodeToRemove.getChildren());
        for (StructureNode child : children) {
            nodeToRemove.removeChild(child);

            // Kinder an Parent weiterreichen oder als neue Roots behandeln
            if (nodeToRemove.getParent() != null) {
                nodeToRemove.getParent().addChild(child);
            }
        }
    }

    /**
     * Aktualisiert die Root-Node, falls die aktuelle Root entfernt wurde.
     *
     * @param nodeToRemove Der zu entfernende Knoten
     */
    private void updateRootIfNecessary(MirrorNode nodeToRemove) {
        if (getCurrentStructureRoot() == nodeToRemove) {
            // Finde neue Root aus verbleibenden Knoten
            MirrorNode newRoot = structureNodes.isEmpty() ? null : structureNodes.iterator().next();
            setCurrentStructureRoot(newRoot);
        }
    }

    /**
     * Findet eine neue Root-Node aus den verbleibenden StructureNodes.
     * PRIVATE - interne Strukturverwaltung.
     *
     * @return Neue Root-Node oder null, wenn keine Knoten mehr vorhanden
     */
    private MirrorNode findNewRoot() {
        if (structureNodes.isEmpty()) {
            return null;
        }

        // Strategie: Wähle den Knoten mit den wenigsten Parents
        return structureNodes.stream()
                .min(Comparator.comparingInt(node ->
                        node.getParent() != null ? 1 : 0))
                .orElse(null);
    }

    /**
     * Registriert einen Observer für Strukturänderungen.
     * PUBLIC - ermöglicht externe Observer-Registrierung.
     *
     * @param observer Der zu registrierende Observer
     */
    public final void addStructureChangeObserver(StructureChangeObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
        }
    }

    /**
     * Entfernt einen Observer für Strukturänderungen.
     * PUBLIC - ermöglicht externe Observer-Deregistrierung.
     *
     * @param observer Der zu entfernende Observer
     */
    public final void removeStructureChangeObserver(StructureChangeObserver observer) {
        observers.remove(observer);
    }


    /**
     * Setzt den Iterator für verfügbare Mirrors.
     */
    protected final void setMirrorIterator(Iterator<Mirror> mirrorIterator) {
        this.mirrorIterator = mirrorIterator;
    }

    /**
     * Initialisiert den internen Zustand für ein Netzwerk.
     */
    private void initializeInternalState(Network n) {
        this.network = n;
        this.mirrorIterator = n.getMirrors().iterator();
    }

    /**
     * Setzt nur die StructureNode-Struktur zurück, nicht die Mirror-Links.
     * Wird verwendet, wenn die Mirror-Links bereits durch TopologyStrategy.restartNetwork() gelöscht wurden.
     */
    protected void resetInternalStateStructureOnly() {
        nodeToSubstructure.clear();
        structureNodes.clear();
        currentStructureRoot = null;
    }


    /**
     * Vollständiger Reset des internen Zustands UND aller Mirror-Links.
     * Muss vor dem Neuaufbau der Struktur aufgerufen werden.
     */
    protected void resetInternalState() {
        // 1. ERST alle Mirror-Links aus allen MirrorNodes entfernen
        for (MirrorNode node : structureNodes) {
            Mirror mirror = node.getMirror();
            if (mirror != null) {
                // Alle Links des Mirrors löschen
                mirror.getLinks().clear();
            }
        }

        // 2. DANN die StructureNode-Struktur zurücksetzen
        nodeToSubstructure.clear();
        structureNodes.clear();
        currentStructureRoot = null;
    }

    // ===== ABSTRAKTE METHODEN FÜR LINK-ERSTELLUNG =====

    /**
     * Erstellt und verbindet alle Links für eine Struktur.
     * Muss von Subklassen implementiert werden.
     *
     * @param root Die Root-Node der Struktur
     * @param props Simulation Properties
     * @return Set aller erstellten Links
     */
    protected abstract Set<Link> buildAndConnectLinks(MirrorNode root, Properties props);

    // ===== OBSERVER PATTERN INTERFACES =====

    /**
     * Interface für Observer von Struktur-Änderungen.
     */
    public interface StructureChangeObserver {
        void onNodesAdded(List<StructureNode> addedNodes, StructureNode headNode);
        void onNodesRemoved(List<StructureNode> removedNodes, StructureNode headNode);
        void onLinksCreated(Set<Link> newLinks, StructureNode headNode);
        void onLinksRemoved(Set<Link> removedLinks, StructureNode headNode);
        void onStructureChanged(BuildAsSubstructure structure);
    }

    /**
     * Bereinigt die StructureNode-Verwaltung für entfernte Knoten.
     */
    void cleanupStructureNodes(List<MirrorNode> nodesToRemove) {
        for (MirrorNode nodeToRemove : nodesToRemove) {
            structureNodes.remove(nodeToRemove);
            removeSubstructureForNode(nodeToRemove);
            cleanupNodeRelationships(nodeToRemove);
            updateRootIfNecessary(nodeToRemove);
        }
        // Observer-Benachrichtigung entfernt
    }

    /**
     * Fügt einen Observer hinzu.
     */
    public final void addObserver(StructureChangeObserver observer) {
        if (observer != null) {
            observers.add(observer);
        }
    }

    /**
     * Entfernt einen Observer.
     */
    public final void removeObserver(StructureChangeObserver observer) {
        observers.remove(observer);
    }

    /**
     * Benachrichtigt Observer über hinzugefügte Knoten.
     */
    protected final void notifyNodesAdded(List<StructureNode> addedNodes, StructureNode headNode) {
        for (StructureChangeObserver observer : observers) {
            observer.onNodesAdded(addedNodes, headNode);
        }

        // Automatische structureNodes-Aktualisierung
        for (StructureNode node : addedNodes) {
            if (node instanceof MirrorNode mirrorNode) {
                addToStructureNodes(mirrorNode);
            }
        }
    }

    /**
     * Benachrichtigt Observer über entfernte Knoten.
     */
    protected final void notifyNodesRemoved(List<StructureNode> removedNodes, StructureNode headNode) {
        for (StructureChangeObserver observer : observers) {
            observer.onNodesRemoved(removedNodes, headNode);
        }

        // Automatische structureNodes-Aktualisierung
        for (StructureNode node : removedNodes) {
            if (node instanceof MirrorNode mirrorNode) {
                removeFromStructureNodes(mirrorNode);
            }
        }
    }

    /**
     * Benachrichtigt Observer über erstellte Links.
     */
    protected final void notifyLinksCreated(Set<Link> newLinks, StructureNode headNode) {
        for (StructureChangeObserver observer : observers) {
            observer.onLinksCreated(newLinks, headNode);
        }
    }

    /**
     * Benachrichtigt Observer über entfernte Links.
     */
    protected final void notifyLinksRemoved(Set<Link> removedLinks, StructureNode headNode) {
        for (StructureChangeObserver observer : observers) {
            observer.onLinksRemoved(removedLinks, headNode);
        }
    }

    /**
     * Benachrichtigt alle Observer über Strukturänderungen.
     * PROTECTED - für Subklassen-Zugriff verfügbar.
     */
    protected final void notifyStructureChanged() {
        // Einfache Implementation ohne Observer Pattern - falls nicht benötigt
        // kann diese Methode leer bleiben oder entfernt werden
        for (StructureChangeObserver observer : observers) {
            observer.onStructureChanged(this);
        }
    }


    // ===== HILFSMETHODEN =====

    /**
     * Erstellt neue Mirror-Instanzen.
     */
    protected final List<Mirror> createMirrors(int count, int simTime, Properties props) {
        List<Mirror> mirrors = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Mirror mirror = new Mirror(idGenerator.getNextID(), simTime, props);
            mirrors.add(mirror);
        }
        return mirrors;
    }

    /**
     * Gibt den nächsten verfügbaren Mirror aus dem Iterator zurück.
     * Vereinfacht die Interface-Trennung zwischen BuildAsSubstructure und Topology-Implementierungen.
     *
     * @return Der nächste verfügbare Mirror oder null, wenn kein Mirror verfügbar ist
     */
    protected final Mirror getNextMirror() {
        if (mirrorIterator != null && mirrorIterator.hasNext()) {
            return mirrorIterator.next();
        }
        return null;
    }

    /**
     * Prüft, ob weitere Mirrors im Iterator verfügbar sind.
     *
     * @return true, wenn weitere Mirrors verfügbar sind
     */
    protected final boolean hasNextMirror() {
        return mirrorIterator != null && mirrorIterator.hasNext();
    }

    /**
     * Erstellt einen neuen MirrorNode mit Mirror aus dem Iterator.
     * AKTUALISIERT: Fügt den Knoten automatisch zu structureNodes hinzu.
     *
     * @return Neuer MirrorNode mit zugeordnetem Mirror oder null
     */
    protected final MirrorNode getMirrorNodeFromIterator() {
        if (mirrorIterator != null && mirrorIterator.hasNext()) {
            Mirror mirror = mirrorIterator.next();
            MirrorNode node = createMirrorNodeForMirror(mirror);
            if (node != null) {
                node.setMirror(mirror);
                addToStructureNodes(node); // Aktiv hinzufügen
            }
            return node;
        }
        return null;
    }

    /**
     * Factory-Methode für strukturspezifische MirrorNode-Erstellung.
     * Kann von Subklassen überschrieben werden.
     *
     * @param mirror Der Mirror, für den ein MirrorNode erstellt werden soll
     * @return Neuer strukturspezifischer MirrorNode
     */
    protected MirrorNode createMirrorNodeForMirror(Mirror mirror) {
        return new MirrorNode(idGenerator.getNextID(), mirror);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "{substructureId=" + substructureId +
                ", structureNodes=" + structureNodes.size() +
                ", currentStructureRoot=" +
                (currentStructureRoot != null ? currentStructureRoot.getId() : "null") + "}";
    }
}
