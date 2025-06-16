
package org.lrdm.topologies;

import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.Action;
import org.lrdm.topologies.base.MirrorNode;
import org.lrdm.topologies.base.StructureNode;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstrakte Basisklasse für hierarchische Substruktur-basierte TopologyStrategy.
 * Nutzt Observer/Command Pattern zur Reaktion auf StructureBuilder-Änderungen.
 *
 * Jede Substruktur hat eine eindeutige ID und kann an Mirror-Ports weitere
 * Substrukturen anbinden. Die Struktur-Logik bleibt in MirrorNodes/StructureBuilder.
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public abstract class BuildAsSubstructure extends TopologyStrategy {

    // ===== EINDEUTIGE SUBSTRUKTUR-ID =====
    private final int substructureId;
    private static final AtomicInteger SUBSTRUCTURE_ID_GENERATOR = new AtomicInteger(1);

    // ===== PORT-BASIERTE SUBSTRUKTUR-VERWALTUNG =====
    private Map<Mirror, BuildAsSubstructure> mirrorToSubstructure = new HashMap<>();
    private Set<Mirror> structureMirrors = new HashSet<>();

    // ===== OBSERVER PATTERN - STRUCTURE CHANGE EVENTS =====
    private List<StructureChangeObserver> observers = new ArrayList<>();

    // ===== KONSTRUKTOR =====

    public BuildAsSubstructure() {
        this.substructureId = SUBSTRUCTURE_ID_GENERATOR.getAndIncrement();
    }

    public int getSubstructureId() {
        return substructureId;
    }

    // ===== PORT-VERWALTUNG (VEREINFACHT) =====

    /**
     * Registriert eine Substruktur an einem Mirror-Port (Head Node).
     */
    public void attachSubstructure(Mirror headMirror, BuildAsSubstructure substructure) {
        mirrorToSubstructure.put(headMirror, substructure);
        structureMirrors.add(headMirror);
        substructure.structureMirrors.add(headMirror);
    }

    /**
     * Gibt die an einem Mirror-Port registrierte Substruktur zurück.
     */
    public BuildAsSubstructure getAttachedSubstructure(Mirror headMirror) {
        return mirrorToSubstructure.get(headMirror);
    }

    /**
     * Entfernt eine Substruktur von einem Mirror-Port.
     */
    public boolean detachSubstructure(Mirror headMirror) {
        BuildAsSubstructure removed = mirrorToSubstructure.remove(headMirror);
        if (removed != null) {
            structureMirrors.remove(headMirror);
            removed.structureMirrors.remove(headMirror);
            return true;
        }
        return false;
    }

    public Set<Mirror> getMirrors() {
        return new HashSet<>(structureMirrors);
    }

    // ===== OBSERVER PATTERN FÜR STRUCTURE BUILDER EVENTS =====

    public void addStructureChangeObserver(StructureChangeObserver observer) {
        observers.add(observer);
    }

    public void removeStructureChangeObserver(StructureChangeObserver observer) {
        observers.remove(observer);
    }

    /**
     * Notify-Methode für StructureBuilder-Änderungen.
     * Wird von StructureBuilder aufgerufen, wenn Strukturen geändert werden.
     */
    public void notifyStructureChange(StructureChangeEvent event) {
        // 1. Verarbeite die Änderung intern
        handleStructureChange(event);

        // 2. Informiere alle Observer
        for (StructureChangeObserver observer : observers) {
            observer.onStructureChanged(event);
        }
    }

    /**
     * Command Pattern: Verarbeitet spezifische Struktur-Änderungen.
     * Wird von Subklassen überschrieben für strukturspezifische Logik.
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

    // ===== COMMAND PATTERN - STRUKTUR-ÄNDERUNGS-BEHANDLUNG =====

    /**
     * Wird aufgerufen, wenn neue Nodes zu einer Struktur hinzugefügt wurden.
     */
    protected void onNodesAdded(List<StructureNode> addedNodes, StructureNode headNode) {
        // Standard: Füge entsprechende Mirrors hinzu
        for (StructureNode node : addedNodes) {
            if (node instanceof MirrorNode mirrorNode && mirrorNode.getMirror() != null) {
                structureMirrors.add(mirrorNode.getMirror());
            }
        }
    }

    /**
     * Wird aufgerufen, wenn Nodes aus einer Struktur entfernt wurden.
     */
    protected void onNodesRemoved(List<StructureNode> removedNodes, StructureNode headNode) {
        // Standard: Entferne entsprechende Mirrors
        for (StructureNode node : removedNodes) {
            if (node instanceof MirrorNode mirrorNode && mirrorNode.getMirror() != null) {
                structureMirrors.remove(mirrorNode.getMirror());
            }
        }
    }

    /**
     * Wird aufgerufen, wenn zwei Strukturen verbunden wurden.
     */
    protected void onStructureConnected(StructureNode sourceHead, StructureNode targetHead) {
        // Standard: Verknüpfe Substrukturen wenn nötig
    }

    /**
     * Wird aufgerufen, wenn sich der Head einer Struktur geändert hat.
     */
    protected void onHeadChanged(StructureNode oldHead, StructureNode newHead) {
        // Standard: Update interne Mappings
    }

    // ===== BESTEHENDE METHODEN (MIT EIGENER TUPLE-KLASSE) =====

    public Set<Mirror> shutdownSubstructures(int simTime) {
        Set<Mirror> removed = new HashSet<>(structureMirrors);
        for (BuildAsSubstructure substructure : mirrorToSubstructure.values()) {
            removed.addAll(substructure.shutdownSubstructures(simTime));
        }
        for (Mirror mirror : structureMirrors) {
            mirror.shutdown(simTime);
        }
        return removed;
    }

    public Tuple<Set<Mirror>, Set<Link>> gatherAllSubstructures() {
        Set<Link> links = new HashSet<>();
        Set<Mirror> mirrors = new HashSet<>();

        for (BuildAsSubstructure substructure : mirrorToSubstructure.values()) {
            Tuple<Set<Mirror>, Set<Link>> tuple = substructure.gatherAllSubstructures();
            mirrors.addAll(tuple.getFirst());
            links.addAll(tuple.getSecond());
        }

        for (Mirror mirror : structureMirrors) {
            links.addAll(mirror.getLinks());
            mirrors.add(mirror);
        }

        return new Tuple<>(mirrors, links);
    }

    public Set<Mirror> getEdgeMirrors() {
        Set<Mirror> edgeMirrors = new HashSet<>();
        Tuple<Set<Mirror>, Set<Link>> gathered = gatherAllSubstructures();
        Set<Mirror> allMirrors = gathered.getFirst();

        for (Mirror mirror : allMirrors) {
            if (mirror.getLinks().stream().anyMatch(link ->
                    !allMirrors.contains(link.getSource()) || !allMirrors.contains(link.getTarget()))) {
                edgeMirrors.add(mirror);
            }
        }
        return edgeMirrors;
    }

    // ===== TOPOLOGYSTRATEGY IMPLEMENTIERUNG =====

    /**
     * Standard-Implementierung für TopologyStrategy-Methoden.
     * Kann von Subklassen überschrieben werden für spezifische Logik.
     */
    @Override
    public void restartNetwork(Network n, Properties props, int simTime) {
        super.restartNetwork(n, props, simTime);

        // Restart nur interne Links, Edge-Links bleiben bestehen
        Tuple<Set<Mirror>, Set<Link>> gathered = gatherAllSubstructures();
        Set<Mirror> allMirrors = gathered.getFirst();
        Set<Link> allLinks = gathered.getSecond();
        Set<Mirror> edgeMirrors = getEdgeMirrors();

        // Interne Links entfernen
        for (Link link : allLinks) {
            if (allMirrors.contains(link.getSource()) && allMirrors.contains(link.getTarget())) {
                link.shutdown();
            }
        }

        // Interne Mirror-Links säubern (externe bleiben)
        for (Mirror mirror : allMirrors) {
            if (!edgeMirrors.contains(mirror)) {
                mirror.shutdown(simTime);
            }
            mirror.getLinks().removeIf(link ->
                    allMirrors.contains(link.getSource()) && allMirrors.contains(link.getTarget()));
        }

        // Netzwerk neu initialisieren über initNetwork
        n.getLinks().addAll(initNetwork(n, props));
    }

    // ===== ABSTRAKTE METHODEN =====

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

        // Konstruktoren für verschiedene Event-Typen
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