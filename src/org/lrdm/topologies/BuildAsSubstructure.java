
package org.lrdm.topologies;

import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.Action;
import org.lrdm.topologies.base.*;
import org.lrdm.util.IDGenerator;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

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
    private static final AtomicInteger SUBSTRUCTURE_ID_GENERATOR = new AtomicInteger(1);

    // ===== STRUCTUREBUILDER-INTEGRATION (PROTECTED) =====
    protected IDGenerator idGenerator;
    protected Network network;
    protected Iterator<Mirror> mirrorIterator;

    // ===== MIRRORNODE-BASIERTE SUBSTRUKTUR-VERWALTUNG (PRIVATE) =====
    private final Map<MirrorNode, BuildAsSubstructure> nodeToSubstructure = new HashMap<>();
    private final Set<MirrorNode> structureNodes = new HashSet<>();
    private MirrorNode currentStructureRoot;

    // ===== OBSERVER PATTERN (PRIVATE) =====
    private final List<StructureChangeObserver> observers = new ArrayList<>();

    // ===== KONSTRUKTOR =====

    public BuildAsSubstructure() {
        this.substructureId = SUBSTRUCTURE_ID_GENERATOR.getAndIncrement();
        this.idGenerator = IDGenerator.getInstance();
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
     *
     * @return Neuer MirrorNode mit zugeordnetem Mirror oder null
     */
    protected final MirrorNode getMirrorNodeFromIterator() {
        if (mirrorIterator != null && mirrorIterator.hasNext()) {
            Mirror mirror = mirrorIterator.next();
            MirrorNode node = createMirrorNodeForMirror(mirror);
            if (node != null) {
                node.setMirror(mirror);
                structureNodes.add(node);
            }
            return node;
        }
        return null;
    }

    /**
     * Prüft, ob noch weitere Mirrors verfügbar sind.
     */
    protected final boolean hasMoreMirrors() {
        return mirrorIterator != null && mirrorIterator.hasNext();
    }

    /**
     * Validiert eine Struktur.
     */
    protected final boolean validateStructure(MirrorNode root) {
        if (root == null) return false;
        return root.isValidStructure();
    }

    // ===== REKURSIVE LINK-ERSTELLUNG (PROTECTED) =====

    /**
     * Erstellt und verknüpft alle Links für eine MirrorNode-Struktur rekursiv.
     *
     * @param root Root-Knoten der Struktur
     * @param props Simulation Properties
     * @return Set aller erstellten Links
     */
    protected final Set<Link> buildAndConnectLinks(MirrorNode root, Properties props) {
        if (root == null) return new HashSet<>();

        Set<Link> allLinks = new HashSet<>();
        StructureNode.StructureType typeId = root.deriveTypeId();
        StructureNode head = root.findHead(typeId);

        if (head == null) head = root;

        Set<StructureNode> allNodes = root.getAllNodesInStructure(typeId, head);

        // Rekursiv durch alle Struktur-Knoten gehen
        for (StructureNode node : allNodes) {
            if (node instanceof MirrorNode mirrorNode) {
                allLinks.addAll(createLinksForNode(mirrorNode, typeId, head, props));
            }
        }

        return allLinks;
    }

    /**
     * Erstellt Links für einen einzelnen MirrorNode.
     */
    protected final Set<Link> createLinksForNode(MirrorNode node, StructureNode.StructureType typeId,
                                                 StructureNode head, Properties props) {
        Set<Link> nodeLinks = new HashSet<>();
        Mirror nodeMirror = node.getMirror();

        if (nodeMirror == null) return nodeLinks;

        final int headId = head.getId();

        // Links zu Kindern erstellen
        Set<StructureNode> children = node.getChildren(typeId, headId);
        for (StructureNode child : children) {
            if (child instanceof MirrorNode childMirrorNode) {
                Mirror childMirror = childMirrorNode.getMirror();
                if (childMirror != null) {
                    Link link = new Link(idGenerator.getNextID(), nodeMirror, childMirror, 0, props);
                    nodeMirror.addLink(link);
                    childMirror.addLink(link);
                    nodeLinks.add(link);
                }
            }
        }

        return nodeLinks;
    }

    // ===== INTERNE ZUSTANDSVERWALTUNG (PRIVATE) =====

    private void initializeInternalState(Network n) {
        this.network = n;
        if (n != null) {
            this.mirrorIterator = n.getMirrors().iterator();
        }
    }

    private void resetInternalState() {
        nodeToSubstructure.clear();
        structureNodes.clear();
        setCurrentStructureRoot(null);
    }

    private void setMirrorIterator(Iterator<Mirror> mirrorIterator) {
        this.mirrorIterator = mirrorIterator;
    }

    private void setCurrentStructureRoot(MirrorNode root) {
        MirrorNode oldRoot = this.currentStructureRoot;
        this.currentStructureRoot = root;

        if (root != null) {
            structureNodes.add(root);
        }

        // Observer-Event senden wenn sich der Root geändert hat
        if (oldRoot != root) {
            StructureChangeEvent event = new StructureChangeEvent(oldRoot, root, true);
            notifyStructureChange(event);
        }
    }

    private MirrorNode getCurrentStructureRoot() {
        return currentStructureRoot;
    }

    // ===== OBSERVER PATTERN (PRIVATE) =====

    private void notifyStructureChange(StructureChangeEvent event) {
        handleStructureChange(event);

        for (StructureChangeObserver observer : observers) {
            observer.onStructureChanged(event);
        }
    }

    /**
     * Command Pattern: Verarbeitet spezifische Struktur-Änderungen.
     * Kann von Subklassen überschrieben werden.
     */
    protected void handleStructureChange(StructureChangeEvent event) {
        switch (event.getType()) {
            case NODES_ADDED:
                onNodesAdded(event.getAffectedNodes(), event.getHeadNode());
                break;
            case NODES_REMOVED:
                onNodesRemoved(event.getAffectedNodes(), event.getHeadNode());
                break;
            case STRUCTURE_CONNECTED:
                onStructureConnected(event.getSourceHead(), event.getTargetHead());
                break;
            case HEAD_CHANGED:
                onHeadChanged(event.getOldHead(), event.getNewHead());
                break;
        }
    }

    protected void onNodesAdded(List<StructureNode> addedNodes, StructureNode headNode) {
        for (StructureNode node : addedNodes) {
            if (node instanceof MirrorNode mirrorNode) {
                structureNodes.add(mirrorNode);
            }
        }
    }

    protected void onNodesRemoved(List<StructureNode> removedNodes, StructureNode headNode) {
        for (StructureNode node : removedNodes) {
            if (node instanceof MirrorNode mirrorNode) {
                structureNodes.remove(mirrorNode);
            }
        }
    }

    protected void onStructureConnected(StructureNode sourceHead, StructureNode targetHead) {
        // Standard: Verknüpfe Substrukturen wenn nötig
    }

    protected void onHeadChanged(StructureNode oldHead, StructureNode newHead) {
        // Standard: Update interne Mappings
        if (newHead instanceof MirrorNode newMirrorHead) {
            setCurrentStructureRoot(newMirrorHead);
        }
    }

    // ===== LEGACY-UNTERSTÜTZUNG =====

    /**
     * Legacy-Methode für Substruktur-Initialisierung.
     * Standardmäßig nicht implementiert - kann von Subklassen überschrieben werden.
     */
    public Set<Link> initNetworkSub(Network n, Mirror root, List<Mirror> mirrorsToConnect, int simTime, Properties props) {
        return new HashSet<>();
    }

    // ===== HILFSDATENSTRUKTUREN =====

    /**
     * Einfache Tuple-Klasse.
     */
    public static class Tuple<T, U> {
        private final T first;
        private final U second;

        public Tuple(T first, U second) {
            this.first = first;
            this.second = second;
        }

        public T getFirst() { return first; }
        public U getSecond() { return second; }

        // Backward compatibility
        public T getX() { return first; }
        public U getY() { return second; }
    }

    /**
     * Observer Interface für Struktur-Änderungen.
     */
    public interface StructureChangeObserver {
        void onStructureChanged(StructureChangeEvent event);
    }

    /**
     * Event-Klasse für Struktur-Änderungen.
     */
    public static class StructureChangeEvent {
        public enum Type {
            NODES_ADDED, NODES_REMOVED, STRUCTURE_CONNECTED, HEAD_CHANGED
        }

        private final Type type;
        private final List<StructureNode> affectedNodes;
        private final StructureNode headNode;
        private final StructureNode sourceHead;
        private final StructureNode targetHead;
        private final StructureNode oldHead;
        private final StructureNode newHead;

        public StructureChangeEvent(Type type, List<StructureNode> affectedNodes, StructureNode headNode) {
            this.type = type;
            this.affectedNodes = affectedNodes;
            this.headNode = headNode;
            this.sourceHead = null;
            this.targetHead = null;
            this.oldHead = null;
            this.newHead = null;
        }

        public StructureChangeEvent(StructureNode sourceHead, StructureNode targetHead) {
            this.type = Type.STRUCTURE_CONNECTED;
            this.sourceHead = sourceHead;
            this.targetHead = targetHead;
            this.affectedNodes = null;
            this.headNode = null;
            this.oldHead = null;
            this.newHead = null;
        }

        public StructureChangeEvent(StructureNode oldHead, StructureNode newHead, boolean headChange) {
            this.type = Type.HEAD_CHANGED;
            this.oldHead = oldHead;
            this.newHead = newHead;
            this.affectedNodes = null;
            this.headNode = null;
            this.sourceHead = null;
            this.targetHead = null;
        }

        // Getters
        public Type getType() { return type; }
        public List<StructureNode> getAffectedNodes() { return affectedNodes; }
        public StructureNode getHeadNode() { return headNode; }
        public StructureNode getSourceHead() { return sourceHead; }
        public StructureNode getTargetHead() { return targetHead; }
        public StructureNode getOldHead() { return oldHead; }
        public StructureNode getNewHead() { return newHead; }
    }
}