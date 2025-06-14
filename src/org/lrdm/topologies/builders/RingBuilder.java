package org.lrdm.topologies.builders;

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

    public RingBuilder() {
        this(3); // Mindestens 3 Knoten für einen Ring
    }

    public RingBuilder(int minRingSize) {
        super();
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

        // Für Ringe: Füge in den Ring ein
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
        Iterator<RingMirrorNode> nodeIterator = ringNodes.iterator();

        while (added < nodesToAdd && nodeIterator.hasNext()) {
            RingMirrorNode insertionPoint = nodeIterator.next();
            
            // Erstelle neuen Knoten
            RingMirrorNode newNode = new RingMirrorNode(idGenerator.getNextID());
            
            // Füge zwischen insertionPoint und seinem nächsten Knoten ein
            if (!insertionPoint.getChildren().isEmpty()) {
                TreeNode nextNode = insertionPoint.getChildren().get(0);
                insertionPoint.removeChild(nextNode);
                insertionPoint.addChild(newNode);
                newNode.addChild(nextNode);
                added++;
            }
        }

        return added;
    }

    @Override
    public int removeNodes(MirrorNode existingRoot, int nodesToRemove) {
        if (existingRoot == null || nodesToRemove <= 0) return 0;

        Set<TreeNode> allNodes = existingRoot.getAllNodesInStructure();
        
        // Ring muss mindestens minRingSize Knoten behalten
        if (allNodes.size() - nodesToRemove < minRingSize) {
            nodesToRemove = allNodes.size() - minRingSize;
        }

        if (nodesToRemove <= 0) return 0;

        List<RingMirrorNode> ringNodes = new ArrayList<>();
        for (TreeNode node : allNodes) {
            if (node instanceof RingMirrorNode && !node.isHead()) {
                ringNodes.add((RingMirrorNode) node);
            }
        }

        int removed = 0;
        for (RingMirrorNode node : ringNodes) {
            if (removed >= nodesToRemove) break;
            
            // Verbinde Previous mit Next
            TreeNode prev = node.getParent();
            if (!node.getChildren().isEmpty() && prev != null) {
                TreeNode next = node.getChildren().get(0);
                prev.removeChild(node);
                node.removeChild(next);
                prev.addChild(next);
                removed++;
            }
        }

        return removed;
    }

    @Override
    public boolean validateStructure(MirrorNode root) {
        if (root == null) return false;

        if (root instanceof RingMirrorNode) {
            return ((RingMirrorNode) root).isValidRingStructure();
        }

        // Fallback: grundlegende Ring-Validierung
        Set<TreeNode> allNodes = root.getAllNodesInStructure();
        
        if (allNodes.size() < 3) return false;

        // Jeder Knoten muss genau einen Parent und ein Kind haben
        for (TreeNode node : allNodes) {
            if (node.getConnectivityDegree() != 2 || node.getChildren().size() != 1) {
                return false;
            }
        }

        return true;
    }

    /**
     * In Ringen sind alle Knoten konzeptuell "Blätter" (haben gleichen Grad).
     */
    @Override
    protected boolean isLeafInStructure(MirrorNode node) {
        return true; // Alle Ring-Knoten sind gleichwertig
    }

    /**
     * Ringe benötigen Parent-Links für geschlossene Struktur.
     */
    @Override
    protected boolean needsParentLinks() {
        return true;
    }

    /**
     * Für Ringe: alle Knoten sind Einfügekandidaten.
     */
    @Override
    protected List<MirrorNode> findInsertionCandidates(MirrorNode root) {
        List<MirrorNode> candidates = new ArrayList<>();
        Set<TreeNode> allNodes = root.getAllNodesInStructure();
        
        for (TreeNode node : allNodes) {
            if (node instanceof MirrorNode) {
                candidates.add((MirrorNode) node);
            }
        }
        
        return candidates;
    }

    /**
     * Für Ringe: alle nicht-Head Knoten können entfernt werden.
     */
    @Override
    protected List<MirrorNode> findRemovableNodes(MirrorNode root) {
        List<MirrorNode> removable = new ArrayList<>();
        Set<TreeNode> allNodes = root.getAllNodesInStructure();
        
        for (TreeNode node : allNodes) {
            if (node instanceof MirrorNode && !node.isHead()) {
                removable.add((MirrorNode) node);
            }
        }
        
        return removable;
    }

    @Override
    protected boolean canRemoveNode(MirrorNode node) {
        return !node.isHead(); // Head darf nicht entfernt werden
    }
}