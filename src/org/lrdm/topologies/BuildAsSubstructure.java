package org.lrdm.topologies;

import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.Action;
import org.lrdm.topologies.base.MirrorNode;
import org.lrdm.topologies.base.StructureNode;
import org.lrdm.util.IDGenerator;

import java.lang.reflect.Field;
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
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public abstract class BuildAsSubstructure extends TopologyStrategy {

    // ===== EINDEUTIGE SUBSTRUKTUR-ID =====
    private final int substructureId;

    // ===== STRUCTUREBUILDER-INTEGRATION (PROTECTED) =====
    protected IDGenerator idGenerator;
    protected Network network;
    protected Iterator<Mirror> mirrorIterator;

    // ===== MIRRORNODE-BASIERTE SUBSTRUKTUR-VERWALTUNG (PRIVATE) =====
    private final Map<MirrorNode, BuildAsSubstructure> nodeToSubstructure = new HashMap<>();
    private final Set<MirrorNode> structureNodes = new HashSet<>();
    private final MirrorNode currentStructureRoot; // FINAL - ändert sich nicht nach Initialisierung

    // ===== OBSERVER PATTERN (PRIVATE) =====
    private final List<StructureChangeObserver> observers = new ArrayList<>();

    // ===== KONSTRUKTOR =====

    public BuildAsSubstructure() {
        this.idGenerator = IDGenerator.getInstance();
        this.substructureId = idGenerator.getNextID();
        this.currentStructureRoot = null; // Wird in initNetwork() gesetzt
    }

    public BuildAsSubstructure(IDGenerator idGenerator) {
        this.idGenerator = idGenerator;
        this.substructureId = idGenerator.getNextID();
        this.currentStructureRoot = null; // Wird in initNetwork() gesetzt
    }

    // ===== NEUE GETTER-METHODEN =====

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
     * Diese ist final und ändert sich nach der Initialisierung nicht mehr.
     *
     * @return Die Root-Node der Struktur oder null, wenn noch nicht initialisiert
     */
    public final MirrorNode getCurrentStructureRoot() {
        return currentStructureRoot;
    }

    /**
     * Gibt den Strukturtyp der aktuellen Substruktur zurück.
     * Ermittelt den Typ über die Root-Node.
     *
     * @return Der StructureType der aktuellen Substruktur oder null, wenn nicht initialisiert
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
     * für das Anbau weiterer Substrukturen.
     *
     * @return Unveränderliche Kopie aller MirrorNodes in der Struktur
     */
    public final Set<MirrorNode> getAllStructureNodes() {
        return Set.copyOf(structureNodes);
    }

    /**
     * Gibt alle verfügbaren Endpunkte (Terminal-Knoten) zurück, an die neue Substrukturen
     * angebaut werden können.
     *
     * @return Set aller Terminal-MirrorNodes, die für Substruktur-Erweiterung geeignet sind
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

    // ===== TOPOLOGYSTRATEGY CONTRACT (PUBLIC API) =====

    /**
     * Initialisiert das Netzwerk durch Aufbau der strukturspezifischen Topologie.
     * Erstellt MirrorNode-Struktur und verknüpft alle Links.
     *
     * @param n Das Netzwerk
     * @param props Simulation Properties
     * @return Set aller erstellten Links
     */
    @Override
    public final Set<Link> initNetwork(Network n, Properties props) {
        initializeInternalState(n);

        MirrorNode root = buildStructure(n.getNumMirrors());
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
    public final void restartNetwork(Network n, Properties props, int simTime) {
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
    public final void handleAddNewMirrors(Network n, int newMirrors, Properties props, int simTime) {
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
     * Gibt die vorhergesagte Anzahl Links zurück, falls die Action ausgeführt würde.
     * Muss von Subklassen implementiert werden.
     *
     * @param a Die potentiell auszuführende Action
     * @return Vorhergesagte Anzahl Links
     */
    @Override
    public abstract int getPredictedNumTargetLinks(Action a);

    // ===== ABSTRAKTE STRUKTURBUILDER-METHODEN (PROTECTED) =====

    /**
     * Erstellt eine neue Struktur mit der angegebenen Anzahl von Knoten.
     * Muss von Subklassen für spezifische Strukturtypen implementiert werden.
     *
     * @param totalNodes Anzahl der zu erstellenden Knoten
     * @return Root-Knoten der erstellten Struktur
     */
    protected abstract MirrorNode buildStructure(int totalNodes);

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
    protected int removeNodesFromStructure(int nodesToRemove) {
        // Standard-Implementierung: Nicht unterstützt
        return 0;
    }

    /**
     * Factory-Methode für strukturspezifische MirrorNode-Erstellung.
     * Kann von Subklassen überschrieben werden.
     *
     * @param mirror Der Mirror für den ein MirrorNode erstellt werden soll
     * @return Neuer strukturspezifischer MirrorNode
     */
    protected MirrorNode createMirrorNodeForMirror(Mirror mirror) {
        return new MirrorNode(idGenerator.getNextID(), mirror);
    }

    // ===== INTERNE STRUCTUREBUILDER-HILFSMETHODEN (PROTECTED) =====

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
     * Setzt die currentStructureRoot (einmalig, da final).
     */
    private void setCurrentStructureRoot(MirrorNode root) {
        if (this.currentStructureRoot == null && root != null) {
            try {
                Field field = BuildAsSubstructure.class.getDeclaredField("currentStructureRoot");
                field.setAccessible(true);
                field.set(this, root);
                addToStructureNodes(root);
            } catch (Exception e) {
                throw new RuntimeException("Fehler beim Setzen der currentStructureRoot", e);
            }
        }
    }

    /**
     * Setzt den internen Zustand zurück (außer currentStructureRoot, da final).
     */
    private void resetInternalState() {
        nodeToSubstructure.clear();
        structureNodes.clear();
        // currentStructureRoot bleibt final und unverändert
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

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "{substructureId=" + substructureId +
                ", structureNodes=" + structureNodes.size() +
                ", currentStructureRoot=" +
                (currentStructureRoot != null ? currentStructureRoot.getId() : "null") + "}";
    }
}