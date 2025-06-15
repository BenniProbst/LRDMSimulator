package org.lrdm.topologies.builders;

import org.lrdm.Network;
import org.lrdm.topologies.base.LineMirrorNode;
import org.lrdm.topologies.base.MirrorNode;
import org.lrdm.topologies.base.TreeNode;

import java.util.*;

/**
 * Builder für Linien-Strukturen.
 * Erstellt gerade Linien mit zwei Endpunkten.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public class LineBuilder extends StructureBuilder {

    private final int minLineSize;

    public LineBuilder(Network network) {
        this(network, 2);
    }

    public LineBuilder(Network network, int minLineSize) {
        super(network);
        this.minLineSize = Math.max(2, minLineSize);
    }

    @Override
    public MirrorNode build(int totalNodes) {
        if (totalNodes < minLineSize) {
            throw new IllegalArgumentException("Linie benötigt mindestens " + minLineSize + " Knoten");
        }

        return buildLine(totalNodes);
    }

    private MirrorNode buildLine(int totalNodes) {
        if (totalNodes < 2) return null;

        LineMirrorNode head = new LineMirrorNode(idGenerator.getNextID());
        head.setHead(true);

        LineMirrorNode current = head;

        for (int i = 1; i < totalNodes; i++) {
            LineMirrorNode next = new LineMirrorNode(idGenerator.getNextID());
            current.addChild(next);
            current = next;
        }

        return head;
    }

    @Override
    public int addNodes(MirrorNode existingRoot, int nodesToAdd) {
        if (existingRoot == null || nodesToAdd <= 0) return 0;
        return addNodesToLine(existingRoot, nodesToAdd);
    }

    private int addNodesToLine(MirrorNode existingRoot, int nodesToAdd) {
        List<LineMirrorNode> endpoints = findLineEndpoints(existingRoot);

        if (endpoints.isEmpty()) return 0;

        int added = 0;
        int endpointIndex = 0;

        while (added < nodesToAdd && endpointIndex < endpoints.size()) {
            LineMirrorNode endpoint = endpoints.get(endpointIndex);

            LineMirrorNode newNode = new LineMirrorNode(idGenerator.getNextID());
            endpoint.addChild(newNode);
            added++;

            endpointIndex = (endpointIndex + 1) % endpoints.size();
        }

        return added;
    }

    @Override
    public int removeNodes(MirrorNode existingRoot, int nodesToRemove) {
        RemoveNodesInfo info = validateAndPrepareRemoval(existingRoot, nodesToRemove);
        if (info.adjustedNodesToRemove <= 0) return 0;

        List<LineMirrorNode> endpoints = findLineEndpoints(existingRoot);
        int removed = 0;
        int endpointIndex = 0;

        while (removed < info.adjustedNodesToRemove && endpointIndex < endpoints.size()) {
            LineMirrorNode endpoint = endpoints.get(endpointIndex);

            if (info.allNodes.size() - removed > minLineSize) {
                TreeNode parent = endpoint.getParent();
                if (parent != null) {
                    parent.removeChild(endpoint);
                    removed++;

                    endpoints = findLineEndpoints(existingRoot);
                    endpointIndex = 0;
                    continue;
                }
            }

            endpointIndex++;
        }

        return removed;
    }

    /**
     * Validiert Entfernungsparameter und berechnet angepasste Werte.
     * Eliminiert Code-Duplikation zwischen verschiedenen remove-Methoden.
     */
    private RemoveNodesInfo validateAndPrepareRemoval(MirrorNode existingRoot, int nodesToRemove) {
        if (existingRoot == null || nodesToRemove <= 0) {
            return new RemoveNodesInfo(null, 0);
        }

        Set<TreeNode> allNodes = existingRoot.getAllNodes();

        if (allNodes.size() - nodesToRemove < minLineSize) {
            nodesToRemove = allNodes.size() - minLineSize;
        }

        if (nodesToRemove <= 0) {
            return new RemoveNodesInfo(allNodes, 0);
        }

        return new RemoveNodesInfo(allNodes, nodesToRemove);
    }

    private List<LineMirrorNode> findLineEndpoints(MirrorNode root) {
        List<LineMirrorNode> endpoints = new ArrayList<>();

        if (root instanceof LineMirrorNode) {
            LineMirrorNode lineRoot = (LineMirrorNode) root;
            return lineRoot.getEndpoints();
        }

        Set<TreeNode> allNodes = root.getAllNodes();
        for (TreeNode node : allNodes) {
            if (node.isTerminal() && node instanceof LineMirrorNode) {
                endpoints.add((LineMirrorNode) node);
            }
        }

        return endpoints;
    }

    @Override
    public boolean validateStructure(MirrorNode root) {
        if (root == null) return false;

        if (root instanceof LineMirrorNode) {
            return ((LineMirrorNode) root).isValidLineStructure();
        }

        Set<TreeNode> allNodes = root.getAllNodes();

        if (allNodes.size() < 2) return false;

        int terminalCount = 0;

        for (TreeNode node : allNodes) {
            int degree = node.getConnectivityDegree();

            if (degree == 1) {
                terminalCount++;
            } else if (degree == 2) {
                if (node.getChildren().size() > 1) return false;
            } else {
                return false;
            }
        }

        return terminalCount == 2;
    }

    @Override
    protected boolean isLeafInStructure(MirrorNode node) {
        return node.isTerminal();
    }

    @Override
    protected boolean needsParentLinks() {
        return false;
    }

    @Override
    protected List<MirrorNode> findInsertionCandidates(MirrorNode root) {
        List<MirrorNode> candidates = new ArrayList<>();
        List<LineMirrorNode> endpoints = findLineEndpoints(root);
        candidates.addAll(endpoints);
        return candidates;
    }

    @Override
    protected List<MirrorNode> findRemovableNodes(MirrorNode root) {
        List<MirrorNode> removable = new ArrayList<>();
        List<LineMirrorNode> endpoints = findLineEndpoints(root);
        removable.addAll(endpoints);
        return removable;
    }

    @Override
    protected boolean canRemoveNode(MirrorNode node) {
        return node.isTerminal();
    }

    /**
     * Hilfklasse für Entfernungsvalidierung.
     */
    private static class RemoveNodesInfo {
        final Set<TreeNode> allNodes;
        final int adjustedNodesToRemove;

        RemoveNodesInfo(Set<TreeNode> allNodes, int adjustedNodesToRemove) {
            this.allNodes = allNodes;
            this.adjustedNodesToRemove = adjustedNodesToRemove;
        }
    }
}