package org.lrdm.topologies.builders;

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

    public LineBuilder() {
        this(2); // Mindestens 2 Knoten für eine Linie
    }

    public LineBuilder(int minLineSize) {
        super();
        this.minLineSize = Math.max(2, minLineSize);
    }

    @Override
    public MirrorNode build(int totalNodes) {
        if (totalNodes < minLineSize) {
            throw new IllegalArgumentException("Linie benötigt mindestens " + minLineSize + " Knoten");
        }

        return buildLine(totalNodes);
    }

    /**
     * Erstellt eine Linie mit der angegebenen Anzahl von Knoten.
     */
    private MirrorNode buildLine(int totalNodes) {
        if (totalNodes < 2) return null;

        // Erstelle ersten Knoten (Head)
        LineMirrorNode head = new LineMirrorNode(idGenerator.getNextID());
        head.setHead(true);

        LineMirrorNode current = head;

        // Erstelle restliche Knoten als Kette
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

        // Für Linien: Füge an den Endpunkten hinzu
        return addNodesToLine(existingRoot, nodesToAdd);
    }

    /**
     * Fügt Knoten zu einer bestehenden Linie hinzu.
     */
    private int addNodesToLine(MirrorNode existingRoot, int nodesToAdd) {
        List<LineMirrorNode> endpoints = findLineEndpoints(existingRoot);
        
        if (endpoints.isEmpty()) return 0;

        int added = 0;
        int endpointIndex = 0;

        while (added < nodesToAdd && endpointIndex < endpoints.size()) {
            LineMirrorNode endpoint = endpoints.get(endpointIndex);
            
            // Füge neuen Knoten am Ende hinzu
            LineMirrorNode newNode = new LineMirrorNode(idGenerator.getNextID());
            endpoint.addChild(newNode);
            added++;
            
            // Wechsle zwischen den Endpunkten für Balance
            endpointIndex = (endpointIndex + 1) % endpoints.size();
        }

        return added;
    }

    /**
     * Findet die Endpunkte der Linie.
     */
    private List<LineMirrorNode> findLineEndpoints(MirrorNode root) {
        List<LineMirrorNode> endpoints = new ArrayList<>();
        
        if (root instanceof LineMirrorNode) {
            LineMirrorNode lineRoot = (LineMirrorNode) root;
            return lineRoot.getEndpoints();
        }

        // Fallback: Suche Terminal-Knoten
        Set<TreeNode> allNodes = root.getAllNodesInStructure();
        for (TreeNode node : allNodes) {
            if (node.isTerminal() && node instanceof LineMirrorNode) {
                endpoints.add((LineMirrorNode) node);
            }
        }
        
        return endpoints;
    }

    @Override
    public int removeNodes(MirrorNode existingRoot, int nodesToRemove) {
        if (existingRoot == null || nodesToRemove <= 0) return 0;

        Set<TreeNode> allNodes = existingRoot.getAllNodesInStructure();
        
        // Linie muss mindestens minLineSize Knoten behalten
        if (allNodes.size() - nodesToRemove < minLineSize) {
            nodesToRemove = allNodes.size() - minLineSize;
        }

        if (nodesToRemove <= 0) return 0;

        // Entferne von den Endpunkten
        List<LineMirrorNode> endpoints = findLineEndpoints(existingRoot);
        int removed = 0;
        int endpointIndex = 0;

        while (removed < nodesToRemove && endpointIndex < endpoints.size()) {
            LineMirrorNode endpoint = endpoints.get(endpointIndex);
            
            // Entferne nur wenn es noch genug Knoten gibt
            if (allNodes.size() - removed > minLineSize) {
                TreeNode parent = endpoint.getParent();
                if (parent != null) {
                    parent.removeChild(endpoint);
                    removed++;
                    
                    // Update der Endpunkte nötig
                    endpoints = findLineEndpoints(existingRoot);
                    endpointIndex = 0; // Starte von vorn
                    continue;
                }
            }
            
            endpointIndex++;
        }

        return removed;
    }

    @Override
    public boolean validateStructure(MirrorNode root) {
        if (root == null) return false;

        if (root instanceof LineMirrorNode) {
            return ((LineMirrorNode) root).isValidLineStructure();
        }

        // Fallback: grundlegende Linien-Validierung
        Set<TreeNode> allNodes = root.getAllNodesInStructure();
        
        if (allNodes.size() < 2) return false;
        
        int terminalCount = 0;
        
        for (TreeNode node : allNodes) {
            int degree = node.getConnectivityDegree();
            
            if (degree == 1) {
                terminalCount++;
            } else if (degree == 2) {
                // Mittlerer Knoten - keine Verzweigungen erlaubt
                if (node.getChildren().size() > 1) return false;
            } else {
                return false; // Ungültiger Konnektivitätsgrad
            }
        }
        
        return terminalCount == 2; // Linie hat genau 2 Endpunkte
    }

    /**
     * Für Linien: Terminal-Knoten sind die Blätter.
     */
    @Override
    protected boolean isLeafInStructure(MirrorNode node) {
        return node.isTerminal();
    }

    /**
     * Linien benötigen keine Parent-Links.
     */
    @Override
    protected boolean needsParentLinks() {
        return false;
    }

    /**
     * Für Linien: nur Endpunkte sind Einfügekandidaten.
     */
    @Override
    protected List<MirrorNode> findInsertionCandidates(MirrorNode root) {
        List<LineMirrorNode> endpoints = findLineEndpoints(root);
        List<MirrorNode> candidates = new ArrayList<>(endpoints);
        return candidates;
    }

    /**
     * Für Linien: nur Endpunkte können entfernt werden.
     */
    @Override
    protected List<MirrorNode> findRemovableNodes(MirrorNode root) {
        List<LineMirrorNode> endpoints = findLineEndpoints(root);
        List<MirrorNode> removable = new ArrayList<>(endpoints);
        return removable;
    }

    @Override
    protected boolean canRemoveNode(MirrorNode node) {
        return node.isTerminal(); // Nur Endpunkte
    }
}