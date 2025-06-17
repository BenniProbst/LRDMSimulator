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
 * Kombiniert Observer/Command Pattern mit StructureBuilder-Funktionalität.
 *
 * Integriert die bewährte StructureBuilder-API und erweitert sie um:
 * - MirrorNode-basierte Substruktur-Verwaltung
 * - Observer Pattern für Struktur-Änderungen
 * - Hierarchische Substruktur-Anbindung an Head-Nodes
 * - Rekursive Link-Erstellung mit Mirror-Zuordnung
 *
 * Ersetzt die veralteten Builder-Klassen (LineBuilder, RingBuilder, etc.) und
 * bietet eine einheitliche API für strukturbasierte Netzwerktopologien.
 *
 * Der mirrorIterator verwaltet verfügbare, noch nicht eingeschaltete Mirrors aus dem Netzwerk.
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public abstract class BuildAsSubstructure extends TopologyStrategy {

    // ===== EINDEUTIGE SUBSTRUKTUR-ID =====
    private final int substructureId;
    private static final AtomicInteger SUBSTRUCTURE_ID_GENERATOR = new AtomicInteger(1);

    // ===== STRUCTUREBUILDER-INTEGRATION =====
    protected IDGenerator idGenerator;
    protected Network network;
    protected Iterator<Mirror> mirrorIterator;

    // ===== MIRRORNODE-BASIERTE SUBSTRUKTUR-VERWALTUNG =====
    private final Map<MirrorNode, BuildAsSubstructure> nodeToSubstructure = new HashMap<>();
    private final Set<MirrorNode> structureNodes = new HashSet<>();

    // ===== STRUCTURE-ROOT TRACKING =====
    private MirrorNode currentStructureRoot;

    // ===== OBSERVER PATTERN - STRUCTURE CHANGE EVENTS =====
    private final List<StructureChangeObserver> observers = new ArrayList<>();

    // ===== KONSTRUKTOREN =====

    public BuildAsSubstructure() {
        this.substructureId = SUBSTRUCTURE_ID_GENERATOR.getAndIncrement();
        this.idGenerator = IDGenerator.getInstance();
    }

    public BuildAsSubstructure(Network network) {
        this();
        this.network = network;
        if (network != null) {
            this.mirrorIterator = network.getMirrors().iterator();
        }
    }

    public BuildAsSubstructure(Network network, Iterator<Mirror> mirrorIterator) {
        this();
        this.network = network;
        this.mirrorIterator = mirrorIterator;
    }

    public int getSubstructureId() {
        return substructureId;
    }

    // ===== STRUCTUREBUILDER-API (INTEGRIERT) =====

    /**
     * Erstellt eine neue Struktur mit der angegebenen Anzahl von Knoten.
     * Kernmethode der StructureBuilder-API, muss von Subklassen implementiert werden.
     *
     * @param totalNodes Anzahl der zu erstellenden Knoten
     * @return Root-Knoten der erstellten Struktur
     */
    public abstract MirrorNode build(int totalNodes);

    /**
     * Fügt Knoten zu einer bestehenden Struktur hinzu.
     * Vereinfacht: Verwendet automatische Root-Ermittlung über findHead().
     *
     * @param nodesToAdd Anzahl der hinzuzufügenden Knoten
     * @return Anzahl der tatsächlich hinzugefügten Knoten
     */
    public abstract int addNodes(int nodesToAdd);

    /**
     * Entfernt Knoten aus einer bestehenden Struktur.
     * Verwendet automatische Root-Ermittlung über findHead().
     *
     * @param nodesToRemove Anzahl der zu entfernenden Knoten
     * @return Anzahl der tatsächlich entfernten Knoten
     */
    public abstract int removeNodes(int nodesToRemove);

    /**
     * Allgemeine Struktur-Validierung - delegiert an die strukturspezifische
     * isValidStructure()-Methode der jeweiligen MirrorNode-Implementierung.
     * Nutzt Polymorphismus für saubere Architektur.
     *
     * @param root Der Root-Knoten der zu validierenden Struktur
     * @return true, wenn die Struktur gültig ist
     */
    public final boolean validateStructure(MirrorNode root) {
        if (root == null) return false;
        return root.isValidStructure();
    }

    /**
     * Hilfsmethode: Erstellt einen neuen MirrorNode mit Mirror aus dem Iterator.
     * Kann von Subklassen überschrieben werden für spezifische MirrorNode-Typen.
     *
     * @return Neuer MirrorNode mit zugeordnetem Mirror oder null, wenn kein Mirror verfügbar
     */
    protected MirrorNode getMirrorNodeFromIterator() {
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
     * Factory-Methode für strukturspezifische MirrorNode-Erstellung.
     * Muss von Subklassen überschrieben werden für spezifische MirrorNode-Typen.
     *
     * @param mirror Der Mirror, für den ein MirrorNode erstellt werden soll
     * @return Neuer strukturspezifischer MirrorNode
     */
    protected MirrorNode createMirrorNodeForMirror(Mirror mirror) {
        return new MirrorNode(idGenerator.getNextID(), mirror);
    }

    // ===== STRUCTUREBUILDER-HELPER METHODS =====

    /**
     * Getter für den ID-Generator.
     *
     * @return Der verwendete ID-Generator
     */
    protected IDGenerator getIdGenerator() {
        return idGenerator;
    }

    /**
     * Getter für das Netzwerk.
     *
     * @return Das verwendete Netzwerk
     */
    protected Network getNetwork() {
        return network;
    }

    /**
     * Setzt das Netzwerk und aktualisiert den Mirror-Iterator.
     *
     * @param network Das neue Netzwerk
     */
    public void setNetwork(Network network) {
        this.network = network;
        if (network != null) {
            this.mirrorIterator = network.getMirrors().iterator();
        }
    }

    /**
     * Getter für den Mirror-Iterator.
     *
     * @return Der verwendete Mirror-Iterator
     */
    protected Iterator<Mirror> getMirrorIterator() {
        return mirrorIterator;
    }

    /**
     * Setzt den Mirror-Iterator.
     * Nützlich für die Verarbeitung spezifischer Mirror-Subsets.
     *
     * @param mirrorIterator Der neue Mirror-Iterator
     */
    public void setMirrorIterator(Iterator<Mirror> mirrorIterator) {
        this.mirrorIterator = mirrorIterator;
    }

    /**
     * Prüft, ob noch weitere Mirrors verfügbar sind.
     *
     * @return true, wenn noch Mirrors im Iterator verfügbar sind
     */
    protected boolean hasMoreMirrors() {
        return mirrorIterator != null && mirrorIterator.hasNext();
    }

    // ===== REKURSIVE LINK-ERSTELLUNG =====

    /**
     * Erstellt und verknüpft alle Links für eine MirrorNode-Struktur rekursiv.
     * Diese Methode baut die tatsächlichen Mirror-Links basierend auf der
     * MirrorNode-Struktur auf und verknüpft sie mit den zugeordneten Mirrors.
     *
     * @param root Root-Knoten der Struktur
     * @param props Simulation Properties
     * @return Set aller erstellten Links
     */
    public Set<Link> buildAndConnectLinks(MirrorNode root, Properties props) {
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
     * Erstellt Links für einen einzelnen MirrorNode basierend auf seinen
     * strukturellen Beziehungen (Parent/Children).
     *
     * @param node Der MirrorNode für den Links erstellt werden sollen
     * @param typeId Der Strukturtyp
     * @param head Der Head-Knoten der Struktur
     * @param props Simulation Properties
     * @return Set der erstellten Links für diesen Knoten
     */
    protected Set<Link> createLinksForNode(MirrorNode node, StructureNode.StructureType typeId,
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

    // ===== SUBSTRUKTUR-VERWALTUNG (MIRRORNODE-BASIERT) =====

    /**
     * Registriert eine Substruktur an einem MirrorNode-Port (Head Node).
     *
     * @param headNode Der Head-MirrorNode als Verbindungspunkt
     * @param substructure Die anzubindende Substruktur
     */
    public void attachSubstructure(MirrorNode headNode, BuildAsSubstructure substructure) {
        nodeToSubstructure.put(headNode, substructure);
        structureNodes.add(headNode);
        substructure.structureNodes.add(headNode);

        // Observer-Event senden
        StructureChangeEvent event = new StructureChangeEvent(headNode, substructure.getCurrentStructureRoot());
        notifyStructureChange(event);
    }

    /**
     * Gibt die an einem MirrorNode-Port registrierte Substruktur zurück.
     *
     * @param headNode Der Head-MirrorNode
     * @return Die registrierte Substruktur oder null
     */
    public BuildAsSubstructure getAttachedSubstructure(MirrorNode headNode) {
        return nodeToSubstructure.get(headNode);
    }

    /**
     * Entfernt eine Substruktur von einem MirrorNode-Port.
     *
     * @param headNode Der Head-MirrorNode
     * @return true, wenn eine Substruktur entfernt wurde
     */
    public boolean detachSubstructure(MirrorNode headNode) {
        BuildAsSubstructure removed = nodeToSubstructure.remove(headNode);
        if (removed != null) {
            structureNodes.remove(headNode);
            removed.structureNodes.remove(headNode);

            // Observer-Event senden
            StructureChangeEvent event = new StructureChangeEvent(
                    StructureChangeEvent.Type.STRUCTURE_CONNECTED,
                    Arrays.asList(headNode), headNode
            );
            notifyStructureChange(event);
            return true;
        }
        return false;
    }

    /**
     * Gibt alle verwalteten MirrorNodes zurück.
     *
     * @return Set aller MirrorNodes in dieser Substruktur
     */
    public Set<MirrorNode> getMirrorNodes() {
        return new HashSet<>(structureNodes);
    }

    /**
     * Gibt alle Mirrors aus den verwalteten MirrorNodes zurück.
     *
     * @return Set aller Mirrors
     */
    public Set<Mirror> getMirrors() {
        return structureNodes.stream()
                .map(MirrorNode::getMirror)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
    }

    // ===== STRUCTURE-ROOT MANAGEMENT =====

    /**
     * Setzt den aktuellen Struktur-Root.
     *
     * @param root Der neue Root-Knoten
     */
    protected void setCurrentStructureRoot(MirrorNode root) {
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

    /**
     * Gibt den aktuellen Struktur-Root zurück.
     *
     * @return Der aktuelle Root-Knoten oder null
     */
    public MirrorNode getCurrentStructureRoot() {
        return currentStructureRoot;
    }

    /**
     * Findet den Root einer bestehenden Struktur.
     * Verwendet die Head-Finding-Logik der MirrorNodes.
     *
     * @return Der gefundene Root-Knoten oder null
     */
    protected MirrorNode findExistingStructureRoot() {
        if (currentStructureRoot != null) {
            return currentStructureRoot;
        }

        // Suche Head-Knoten in bestehenden Strukturen
        return structureNodes.stream()
                .filter(node -> node.isHead(node.deriveTypeId()))
                .findFirst()
                .orElse(null);
    }

    // ===== OBSERVER PATTERN FÜR STRUCTURE CHANGE EVENTS =====

    public void addStructureChangeObserver(StructureChangeObserver observer) {
        observers.add(observer);
    }

    public void removeStructureChangeObserver(StructureChangeObserver observer) {
        observers.remove(observer);
    }

    /**
     * Notify-Methode für StructureBuilder-Änderungen.
     * Wird bei Struktur-Änderungen aufgerufen.
     *
     * @param event Das Struktur-Änderungs-Event
     */
    public void notifyStructureChange(StructureChangeEvent event) {
        handleStructureChange(event);

        for (StructureChangeObserver observer : observers) {
            observer.onStructureChanged(event);
        }
    }

    /**
     * Command Pattern: Verarbeitet spezifische Struktur-Änderungen.
     * Kann von Subklassen überschrieben werden für strukturspezifische Logik.
     *
     * @param event Das zu verarbeitende Event
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

    // ===== TOPOLOGYSTRATEGY IMPLEMENTIERUNG =====

    @Override
    public Set<Link> initNetwork(Network n, Properties props) {
        setNetwork(n);
        setMirrorIterator(n.getMirrors().iterator());

        MirrorNode root = build(n.getNumMirrors());
        setCurrentStructureRoot(root);

        if (root != null) {
            return buildAndConnectLinks(root, props);
        }

        return new HashSet<>();
    }

    @Override
    public void handleAddNewMirrors(Network n, int newMirrors, Properties props, int simTime) {
        // Erstelle neue Mirrors
        List<Mirror> addedMirrors = createMirrors(newMirrors, simTime, props);
        n.getMirrors().addAll(addedMirrors);

        // Setze Iterator für neue Mirrors
        setMirrorIterator(addedMirrors.iterator());

        // Füge zu bestehender Struktur hinzu
        int actuallyAdded = addNodes(newMirrors);

        if (actuallyAdded > 0) {
            // Rebuild Links für die gesamte Struktur
            MirrorNode root = getCurrentStructureRoot();
            if (root != null) {
                Set<Link> newLinks = buildAndConnectLinks(root, props);
                n.getLinks().addAll(newLinks);
            }
        } else {
            // Fallback: Komplett neu aufbauen wenn Hinzufügen fehlschlägt
            restartNetwork(n, props, simTime);
        }
    }

    @Override
    public void restartNetwork(Network n, Properties props, int simTime) {
        super.restartNetwork(n, props, simTime);

        // Alle Substrukturen zurücksetzen
        nodeToSubstructure.clear();
        structureNodes.clear();
        setCurrentStructureRoot(null);

        // Komplett neu initialisieren
        Set<Link> newLinks = initNetwork(n, props);
        n.getLinks().addAll(newLinks);
    }

    // ===== LEGACY-UNTERSTÜTZUNG UND SUBSTRUKTUREN =====

    public Set<MirrorNode> shutdownSubstructureNodes(int simTime) {
        Set<MirrorNode> removed = new HashSet<>(structureNodes);

        // Rekursiv alle angehängten Substrukturen herunterfahren
        for (BuildAsSubstructure substructure : nodeToSubstructure.values()) {
            removed.addAll(substructure.shutdownSubstructureNodes(simTime));
        }

        // Eigene Nodes herunterfahren
        for (MirrorNode node : structureNodes) {
            if (node.getMirror() != null) {
                node.getMirror().shutdown(simTime);
            }
        }

        return removed;
    }

    public Tuple<Set<Mirror>, Set<Link>> gatherAllSubstructures() {
        Set<Link> links = new HashSet<>();
        Set<Mirror> mirrors = new HashSet<>();

        // Rekursiv alle angehängten Substrukturen sammeln
        for (BuildAsSubstructure substructure : nodeToSubstructure.values()) {
            Tuple<Set<Mirror>, Set<Link>> tuple = substructure.gatherAllSubstructures();
            mirrors.addAll(tuple.getFirst());
            links.addAll(tuple.getSecond());
        }

        // Eigene Mirrors und Links sammeln
        for (MirrorNode node : structureNodes) {
            Mirror mirror = node.getMirror();
            if (mirror != null) {
                links.addAll(mirror.getLinks());
                mirrors.add(mirror);
            }
        }

        return new Tuple<>(mirrors, links);
    }

    // ===== ABSTRAKTE METHODEN =====

    /**
     * Legacy-Methode für Substruktur-Initialisierung.
     * Kann von Subklassen implementiert werden für spezifische Legacy-Unterstützung.
     */
    public abstract Set<Link> initNetworkSub(Network n, Mirror root, List<Mirror> mirrorsToConnect, int simTime, Properties props);

    // ===== HILFSDATENSTRUKTUREN =====

    /**
     * Einfache Tuple-Klasse als Ersatz für AttributeUtils.Tuple.
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

        // Backward compatibility für existierenden Code
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