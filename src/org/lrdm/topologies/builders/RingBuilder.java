
package org.lrdm.topologies.builders;

import org.lrdm.Network;
import org.lrdm.topologies.base.MirrorNode;
import org.lrdm.topologies.base.RingMirrorNode;
import org.lrdm.topologies.base.TreeNode;

import java.util.*;

/**
 * Builder für Ring-Strukturen.
 * Erstellt geschlossene Ringe mit mindestens 3 Knoten.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public class RingBuilder extends StructureBuilder {

    private final int minRingSize;

    public RingBuilder(Network network) {
        this(network, 3);
    }

    public RingBuilder(Network network, int minRingSize) {
        super(network);
        this.minRingSize = Math.max(3, minRingSize);
    }

    @Override
    public MirrorNode build(int totalNodes) {
        if (totalNodes < minRingSize) {
            throw new IllegalArgumentException("Ring benötigt mindestens " + minRingSize + " Knoten");
        }

        return buildRing(totalNodes);
    }

    /**
     * Erstellt einen Ring mit der angegebenen Anzahl von Knoten.
     */
    private MirrorNode buildRing(int totalNodes) {
        if (totalNodes < 3) return null;

        // Erstelle alle Knoten
        List<RingMirrorNode> nodes = new ArrayList<>();
        for (int i = 0; i < totalNodes; i++) {
            nodes.add(new RingMirrorNode(idGenerator.getNextID()));
        }

        // Verbinde zum Ring
        for (int i = 0; i < totalNodes; i++) {
            RingMirrorNode current = nodes.get(i);
            RingMirrorNode next = nodes.get((i + 1) % totalNodes);
            current.addChild(next);
        }

        // Setze ersten Knoten als Head
        RingMirrorNode head = nodes.get(0);
        head.setHead(true);

        return head;
    }

    @Override
    public int addNodes(MirrorNode existingRoot, int nodesToAdd) {
        if (existingRoot == null || nodesToAdd <= 0) return 0;
        return addNodesToRing(existingRoot, nodesToAdd);
    }

    /**
     * Fügt Knoten in einen bestehenden Ring ein.
     */
    private int addNodesToRing(MirrorNode existingRoot, int nodesToAdd) {
        Set<TreeNode> allNodes = existingRoot.getAllNodesInStructure();
        List<RingMirrorNode> ringNodes = new ArrayList<>();

        // Sammle alle Ring-Knoten
        for (TreeNode node : allNodes) {
            if (node instanceof RingMirrorNode) {
                ringNodes.add((RingMirrorNode) node);
            }
        }

        if (ringNodes.isEmpty()) return 0;

        int added = 0;
        int nodeIndex = 0;

        while (added < nodesToAdd && nodeIndex < ringNodes.size()) {
            RingMirrorNode current = ringNodes.get(nodeIndex);
            RingMirrorNode next = current.getNextInRing();

            if (next != null) {
                // Erstelle neuen Knoten zwischen current und next
                RingMirrorNode newNode = new RingMirrorNode(idGenerator.getNextID());

                // Entferne alte Verbindung
                current.removeChild(next);

                // Füge neue Verbindungen hinzu
                current.addChild(newNode);
                newNode.addChild(next);

                added++;

                // Aktualisiere Ring-Liste für nächste Iteration
                ringNodes.add(nodeIndex + 1, newNode);
            }

            nodeIndex++;
        }

        return added;
    }

    @Override
    public boolean validateStructure(MirrorNode root) {
        if (root == null) return false;

        if (root instanceof RingMirrorNode) {
            return ((RingMirrorNode) root).isValidStructure();
        }

        Set<TreeNode> allNodes = root.getAllNodesInStructure();

        if (allNodes.size() < 3) return false;

        // Jeder Knoten muss Konnektivitätsgrad 2 haben
        for (TreeNode node : allNodes) {
            if (node.getConnectivityDegree() != 2 || node.getChildren().size() != 1) {
                return false;
            }
        }

        return hasClosedCycle(allNodes);
    }

    /**
     * Prüft, ob die Knoten einen geschlossenen Zyklus bilden.
     */
    private boolean hasClosedCycle(Set<TreeNode> nodes) {
        if (nodes.isEmpty()) return false;

        TreeNode start = nodes.iterator().next();
        TreeNode current = start;
        Set<TreeNode> visitedInCycle = new HashSet<>();

        do {
            if (visitedInCycle.contains(current)) {
                return visitedInCycle.size() == nodes.size();
            }
            visitedInCycle.add(current);

            if (current.getChildren().size() != 1) return false;
            current = current.getChildren().get(0);

        } while (current != start && visitedInCycle.size() <= nodes.size());

        return current == start && visitedInCycle.size() == nodes.size();
    }

    @Override
    protected boolean isLeafInStructure(MirrorNode node) {
        // In einem Ring gibt es keine "Blätter" im traditionellen Sinne
        return false;
    }

    @Override
    protected boolean needsParentLinks() {
        return true; // Ringe benötigen bidirektionale Traversierung
    }

    @Override
    protected List<MirrorNode> findInsertionCandidates(MirrorNode root) {
        // In einem Ring kann zwischen jedem Knotenpaar eingefügt werden
        List<MirrorNode> candidates = new ArrayList<>();
        Set<TreeNode> allNodes = root.getAllNodesInStructure();

        for (TreeNode node : allNodes) {
            if (node instanceof MirrorNode) {
                candidates.add((MirrorNode) node);
            }
        }

        return candidates;
    }

    @Override
    protected List<MirrorNode> findRemovableNodes(MirrorNode root) {
        // In einem Ring können alle Knoten entfernt werden (außer dem Root)
        List<MirrorNode> removable = new ArrayList<>();
        Set<TreeNode> allNodes = root.getAllNodesInStructure();

        for (TreeNode node : allNodes) {
            if (node instanceof MirrorNode && node != root) {
                removable.add((MirrorNode) node);
            }
        }

        return removable;
    }

    @Override
    protected boolean canRemoveNode(MirrorNode node) {
        // Ring muss mindestens minRingSize Knoten behalten
        Set<TreeNode> allNodes = node.getAllNodesInStructure();
        return allNodes.size() > minRingSize;
    }
}