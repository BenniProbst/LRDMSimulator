package org.lrdm.topologies.builders;

import org.lrdm.topologies.node.MirrorNode;
import org.lrdm.topologies.node.StarMirrorNode;
import org.lrdm.topologies.node.StructureNode;

import java.util.*;

/**
 * Builder für Stern-Strukturen.
 * Erstellt Sterne mit einem Zentrum und mehreren Blättern.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public class StarBuilder extends StructureBuilder {

    private final int minStarSize;

    public StarBuilder() {
        this(3); // Mindestens 3 Knoten für einen Stern (Zentrum + 2 Blätter)
    }

    public StarBuilder(int minStarSize) {
        super();
        this.minStarSize = Math.max(3, minStarSize);
    }

    @Override
    public MirrorNode build(int totalNodes) {
        if (totalNodes < minStarSize) {
            throw new IllegalArgumentException("Stern benötigt mindestens " + minStarSize + " Knoten");
        }

        return buildStar(totalNodes);
    }

    /**
     * Erstellt einen Stern mit der angegebenen Anzahl von Knoten.
     */
    private MirrorNode buildStar(int totalNodes) {
        if (totalNodes < 3) return null;

        // Erstelle Zentrum
        StarMirrorNode center = new StarMirrorNode(idGenerator.getNextID());
        center.setHead(true);

        // Erstelle Blätter
        for (int i = 1; i < totalNodes; i++) {
            StarMirrorNode leaf = new StarMirrorNode(idGenerator.getNextID());
            center.addChild(leaf);
        }

        return center;
    }

    @Override
    public int addNodes(MirrorNode existingRoot, int nodesToAdd) {
        if (existingRoot == null || nodesToAdd <= 0) return 0;

        // Für Sterne: Füge neue Blätter zum Zentrum hinzu
        StarMirrorNode center = findCenter(existingRoot);
        if (center == null) return 0;

        int added = 0;
        for (int i = 0; i < nodesToAdd; i++) {
            StarMirrorNode newLeaf = new StarMirrorNode(idGenerator.getNextID());
            center.addChild(newLeaf);
            added++;
        }

        return added;
    }

    /**
     * Findet das Zentrum des Sterns.
     */
    private StarMirrorNode findCenter(MirrorNode root) {
        if (root instanceof StarMirrorNode) {
            StarMirrorNode starRoot = (StarMirrorNode) root;
            return starRoot.getCenter();
        }

        // Fallback: Suche Knoten ohne Parent mit Kindern
        Set<StructureNode> allNodes = root.getAllNodes();
        for (StructureNode node : allNodes) {
            if (node.isRoot() && !node.isLeaf() && node instanceof StarMirrorNode) {
                return (StarMirrorNode) node;
            }
        }

        return null;
    }

    @Override
    public int removeNodes(MirrorNode existingRoot, int nodesToRemove) {
        if (existingRoot == null || nodesToRemove <= 0) return 0;

        Set<StructureNode> allNodes = existingRoot.getAllNodes();
        
        // Stern muss mindestens minStarSize Knoten behalten
        if (allNodes.size() - nodesToRemove < minStarSize) {
            nodesToRemove = allNodes.size() - minStarSize;
        }

        if (nodesToRemove <= 0) return 0;

        // Entferne nur Blätter, nicht das Zentrum
        List<StarMirrorNode> leaves = findStarLeaves(existingRoot);
        int removed = 0;

        for (StarMirrorNode leaf : leaves) {
            if (removed >= nodesToRemove) break;
            
            StructureNode parent = leaf.getParent();
            if (parent != null) {
                parent.removeChild(leaf);
                removed++;
            }
        }

        return removed;
    }

    /**
     * Findet alle Stern-Blätter.
     */
    private List<StarMirrorNode> findStarLeaves(MirrorNode root) {
        List<StarMirrorNode> leaves = new ArrayList<>();
        Set<StructureNode> allNodes = root.getAllNodes();
        
        for (StructureNode node : allNodes) {
            if (node instanceof StarMirrorNode) {
                StarMirrorNode starNode = (StarMirrorNode) node;
                if (starNode.isStarLeaf()) {
                    leaves.add(starNode);
                }
            }
        }
        
        return leaves;
    }

    @Override
    public boolean validateStructure(MirrorNode root) {
        if (root == null) return false;

        if (root instanceof StarMirrorNode) {
            return ((StarMirrorNode) root).isValidStarStructure();
        }

        // Fallback: grundlegende Stern-Validierung
        Set<StructureNode> allNodes = root.getAllNodes();
        
        if (allNodes.size() < 3) return false;

        StructureNode center = null;
        int leafCount = 0;
        
        for (StructureNode node : allNodes) {
            if (node.isRoot() && !node.isLeaf()) {
                if (center != null) return false; // Nur ein Zentrum erlaubt
                center = node;
            } else if (node.isTerminal() && !node.isRoot()) {
                leafCount++;
            } else {
                return false; // Ungültige Knotenkonfiguration
            }
        }
        
        return center != null && leafCount == allNodes.size() - 1;
    }

    /**
     * Für Sterne: nur echte Blätter (Terminal-Knoten ohne Parent = Root).
     */
    @Override
    protected boolean isLeafInStructure(MirrorNode node) {
        return node.isTerminal() && !node.isRoot();
    }

    /**
     * Sterne benötigen keine Parent-Links.
     */
    @Override
    protected boolean needsParentLinks() {
        return false;
    }

    /**
     * Für Sterne: nur das Zentrum ist Einfügekandidat.
     */
    @Override
    protected List<MirrorNode> findInsertionCandidates(MirrorNode root) {
        List<MirrorNode> candidates = new ArrayList<>();
        StarMirrorNode center = findCenter(root);
        if (center != null) {
            candidates.add(center);
        }
        return candidates;
    }

    /**
     * Für Sterne: nur Blätter können entfernt werden.
     */
    @Override
    protected List<MirrorNode> findRemovableNodes(MirrorNode root) {
        List<MirrorNode> removable = new ArrayList<>();
        List<StarMirrorNode> leaves = findStarLeaves(root);
        removable.addAll(leaves);
        return removable;
    }

    @Override
    protected boolean canRemoveNode(MirrorNode node) {
        return node.isTerminal() && !node.isRoot(); // Nur Stern-Blätter
    }
}