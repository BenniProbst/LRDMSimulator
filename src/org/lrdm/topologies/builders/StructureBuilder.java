package org.lrdm.topologies.builders;

import org.lrdm.Network;
import org.lrdm.topologies.base.MirrorNode;
import org.lrdm.Mirror;
import org.lrdm.util.IDGenerator;
import java.util.*;

/**
 * Abstrakte Basisklasse für alle Structure-Builder.
 * Nutzt die polymorphe isValidStructure()-Methode der MirrorNodes.
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

    public abstract MirrorNode build(int totalNodes);

    /**
     * Allgemeine Strukturvalidierung - delegiert an die strukturspezifische
     * isValidStructure()-Methode der jeweiligen MirrorNode-Implementierung.
     * Nutzt Polymorphismus für saubere Architektur.
     */
    public final boolean validateStructure(MirrorNode root) {
        if (root == null) return false;
        return root.isValidStructure();
    }

    // Rest der StructureBuilder-Implementierung...
    public int addNodes(MirrorNode existingRoot, int nodesToAdd) {
        if (existingRoot == null || nodesToAdd <= 0) return 0;

        List<MirrorNode> candidates = findInsertionCandidates(existingRoot);
        int added = 0;

        for (MirrorNode candidate : candidates) {
            if (added >= nodesToAdd) break;
            if (canAddNodeTo(candidate)) {
                MirrorNode newNode = getMirrorNodeFromIterator();
                if (newNode != null) {
                    candidate.addChild(newNode);
                    // Nutze die polymorphe Validierung
                    if (existingRoot.isValidStructure()) {
                        added++;
                    } else {
                        candidate.removeChild(newNode);
                        break;
                    }
                } else {
                    break;
                }
            }
        }

        return added;
    }

    protected abstract List<MirrorNode> findInsertionCandidates(MirrorNode root);
    protected abstract boolean canAddNodeTo(MirrorNode node);

    protected MirrorNode getMirrorNodeFromIterator() {
        if (mirrorIterator.hasNext()) {
            Mirror mirror = mirrorIterator.next();
            // Diese Methode muss von Kindklassen überschrieben werden,
            // um den richtigen MirrorNode-Typ zu erstellen
            return new MirrorNode(idGenerator.getNextID(), mirror);
        }
        return null;
    }
}