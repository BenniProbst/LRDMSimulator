package org.lrdm.topologies.builders;

import org.lrdm.Network;
import org.lrdm.Mirror;
import org.lrdm.topologies.node.MirrorNode;
import org.lrdm.util.IDGenerator;

import java.util.Iterator;

/**
 * Abstrakte Basisklasse für alle Structure-Builder.
 * Nutzt die polymorphe isValidStructure()-Methode der MirrorNodes.
 * <p>
 * Vereinfacht durch Nutzung von StructureNode-Funktionen:
 * - addNodes() und removeNodes() verwenden nur noch int-Parameter
 * - Struktur-Navigation über findHead() und getAllNodesInStructure()
 * - Implementierung erfolgt in Kindklassen für strukturspezifische Logik
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public abstract class StructureBuilder {
    protected IDGenerator idGenerator;
    protected Network network;
    protected Iterator<Mirror> mirrorIterator;

    public StructureBuilder(Network network) {
        this.idGenerator = IDGenerator.getInstance();
        this.network = network;
        this.mirrorIterator = network.getMirrors().iterator();
    }

    public StructureBuilder(Network network, Iterator<Mirror> mirrorIterator) {
        this.idGenerator = IDGenerator.getInstance();
        this.network = network;
        this.mirrorIterator = mirrorIterator;
    }

    /**
     * Erstellt eine neue Struktur mit der angegebenen Anzahl von Knoten.
     *
     * @param totalNodes Anzahl der zu erstellenden Knoten
     * @return Root-Knoten der erstellten Struktur
     */
    public abstract MirrorNode build(int totalNodes);

    /**
     * Fügt Knoten zu einer bestehenden Struktur hinzu.
     * Vereinfacht: Verwendet nur noch int-Parameter für nodesToAdd.
     * Die Root-Ermittlung erfolgt über findHead() aus StructureNode.
     *
     * @param nodesToAdd Anzahl der hinzuzufügenden Knoten
     * @return Anzahl der tatsächlich hinzugefügten Knoten
     */
    public abstract int addNodes(int nodesToAdd);

    /**
     * Entfernt Knoten aus einer bestehenden Struktur.
     * Neue Funktion: Verwendet nur noch int-Parameter für nodesToRemove.
     * Die Root-Ermittlung erfolgt über findHead() aus StructureNode.
     *
     * @param nodesToRemove Anzahl der zu entfernenden Knoten
     * @return Anzahl der tatsächlich entfernten Knoten
     */
    public abstract int removeNodes(int nodesToRemove);

    /**
     * Allgemeine Struktur Validierung - delegiert an die strukturspezifische
     * isValidStructure()-Methode der jeweiligen MirrorNode-Implementierung.
     * Nutzt Polymorphismus für saubere Architektur.
     */
    public final boolean validateStructure(MirrorNode root) {
        if (root == null) return false;
        return root.isValidStructure();
    }

    /**
     * Hilfsmethode: Erstellt einen neuen MirrorNode mit Mirror aus dem Iterator.
     * Kann von Kindklassen überschrieben werden, um spezifische MirrorNode-Typen zu erstellen.
     *
     * @return Neuer MirrorNode mit zugeordnetem Mirror oder null, wenn kein Mirror verfügbar ist
     */
    protected MirrorNode getMirrorNodeFromIterator() {
        if (mirrorIterator.hasNext()) {
            Mirror mirror = mirrorIterator.next();
            return new MirrorNode(idGenerator.getNextID(), mirror);
        }
        return null;
    }

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
     * Getter für den Mirror-Iterator.
     *
     * @return Der verwendete Mirror-Iterator
     */
    protected Iterator<Mirror> getMirrorIterator() {
        return mirrorIterator;
    }

    /**
     * Prüft, ob noch weitere Mirrors verfügbar sind.
     *
     * @return true, wenn noch Mirrors im Iterator verfügbar sind
     */
    protected boolean hasMoreMirrors() {
        return mirrorIterator.hasNext();
    }
}