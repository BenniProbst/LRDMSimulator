
package org.lrdm.util;

import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.topologies.BalancedTreeTopologyStrategy;
import java.util.*;

/**
 * TreeBuilder-Implementation für balancierte Bäume (Breadth-First-Ansatz).
 * Ohne Tiefenbeschränkung, aber mit balanciertem Einfügen und Löschen.
 * Nutzt die BalancedTreeTopologyStrategy direkt.
 * 
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public class TreeBuilderBalanced extends TreeBuilder {
    private BalancedTreeTopologyStrategy topologyStrategy;
    private int targetLinksPerNode;
    
    public TreeBuilderBalanced() {
        this(2); // Standard: Binärbaum
    }
    
    public TreeBuilderBalanced(int targetLinksPerNode) {
        super();
        this.topologyStrategy = new BalancedTreeTopologyStrategy();
        this.targetLinksPerNode = targetLinksPerNode;
    }
    
    @Override
    public MirrorNode buildTree(int totalNodes, int maxDepth) {
        if (totalNodes <= 0) return null;
        
        MirrorNode root = new MirrorNode(getNextId(), 0);
        if (totalNodes == 1) return root;
        
        buildBalanced(root, totalNodes - 1); // -1 weil root bereits erstellt
        return root;
    }
    
    /**
     * Erstellt einen balancierten Baum mit Breadth-First-Ansatz.
     * 
     * @param root Root-Knoten
     * @param remainingNodes Verbleibende zu erstellende Knoten
     */
    private void buildBalanced(MirrorNode root, int remainingNodes) {
        if (remainingNodes <= 0) return;
        
        Queue<MirrorNode> queue = new LinkedList<>();
        queue.offer(root);
        int nodesAdded = 0;
        
        while (!queue.isEmpty() && nodesAdded < remainingNodes) {
            MirrorNode current = queue.poll();
            
            // Berechne optimale Anzahl Kinder für Balance
            int childrenToAdd = calculateOptimalChildren(current, remainingNodes - nodesAdded, queue.size());
            
            for (int i = 0; i < childrenToAdd && nodesAdded < remainingNodes; i++) {
                MirrorNode child = new MirrorNode(getNextId(), current.getDepth() + 1);
                current.addChild(child);
                queue.offer(child);
                nodesAdded++;
            }
        }
    }
    
    /**
     * Berechnet die optimale Anzahl von Kindern für einen Knoten.
     * 
     * @param node Aktueller Knoten
     * @param remainingNodes Verbleibende Knoten
     * @param queueSize Aktuelle Größe der Queue
     * @return Optimale Anzahl Kinder
     */
    private int calculateOptimalChildren(MirrorNode node, int remainingNodes, int queueSize) {
        if (remainingNodes <= 0) return 0;
        
        // Standardmäßig targetLinksPerNode Kinder
        int baseChildren = Math.min(targetLinksPerNode, remainingNodes);
        
        // Anpassung für bessere Balance
        if (queueSize > 0) {
            int avgChildren = (int) Math.ceil((double) remainingNodes / (queueSize + 1));
            baseChildren = Math.min(baseChildren, avgChildren);
        }
        
        return Math.max(1, baseChildren);
    }
    
    @Override
    public int addNodesToExistingTree(MirrorNode existingRoot, int nodesToAdd, int maxDepth) {
        if (existingRoot == null || nodesToAdd <= 0) return 0;
        
        return addNodesToExistingTreeBalanced(existingRoot, nodesToAdd);
    }
    
    /**
     * Fügt Knoten balanciert zu einem bestehenden Baum hinzu.
     * 
     * @param existingRoot Root des bestehenden Baums
     * @param nodesToAdd Anzahl hinzuzufügender Knoten
     * @return Anzahl tatsächlich hinzugefügter Knoten
     */
    private int addNodesToExistingTreeBalanced(MirrorNode existingRoot, int nodesToAdd) {
        List<MirrorNode> candidates = findBalancedInsertionCandidates(existingRoot);
        int added = 0;
        
        // Breadth-First-Einfügung für Balance
        while (added < nodesToAdd && !candidates.isEmpty()) {
            // Wähle den besten Kandidaten (niedrigste Tiefe, wenigste Kinder)
            MirrorNode bestCandidate = selectBestBalancedParent(candidates);
            
            if (bestCandidate != null && bestCandidate.getChildren().size() < targetLinksPerNode) {
                MirrorNode newChild = new MirrorNode(getNextId(), bestCandidate.getDepth() + 1);
                bestCandidate.addChild(newChild);
                added++;
                
                // Füge neues Kind zur Kandidatenliste hinzu
                candidates.add(newChild);
                
                // Entferne Kandidat wenn er voll ist
                if (bestCandidate.getChildren().size() >= targetLinksPerNode) {
                    candidates.remove(bestCandidate);
                }
            } else {
                break; // Keine weiteren Einfügepunkte verfügbar
            }
        }
        
        return added;
    }
    
    /**
     * Findet alle möglichen Einfügepunkte und bewertet sie nach Balance-Kriterien.
     * 
     * @param root Root-Knoten
     * @return Liste sortierter Einfügekandidaten
     */
    private List<MirrorNode> findBalancedInsertionCandidates(MirrorNode root) {
        List<MirrorNode> candidates = new ArrayList<>();
        findCandidatesRecursive(root, candidates);
        
        // Filtere nur Knoten, die noch Platz für Kinder haben
        candidates = candidates.stream()
                .filter(node -> node.getChildren().size() < targetLinksPerNode)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        
        // Sortiere nach Balance-Kriterien: niedrigere Tiefe zuerst, dann weniger Kinder
        candidates.sort((a, b) -> {
            int depthDiff = Integer.compare(a.getDepth(), b.getDepth());
            if (depthDiff != 0) return depthDiff;
            return Integer.compare(a.getChildren().size(), b.getChildren().size());
        });
        
        return candidates;
    }
    
    /**
     * Rekursive Suche nach Einfügekandidaten.
     */
    private void findCandidatesRecursive(MirrorNode node, List<MirrorNode> candidates) {
        candidates.add(node);
        for (TreeNode child : node.getChildren()) {
            findCandidatesRecursive((MirrorNode) child, candidates);
        }
    }
    
    /**
     * Wählt den besten Einfügepunkt basierend auf Balance-Kriterien.
     * 
     * @param candidates Liste der Kandidaten
     * @return Bester Kandidat
     */
    private MirrorNode selectBestBalancedParent(List<MirrorNode> candidates) {
        if (candidates.isEmpty()) return null;
        
        // Der erste Kandidat ist bereits der beste (aufgrund der Sortierung)
        return candidates.get(0);
    }
    
    @Override
    public int removeNodesFromTree(MirrorNode root, int nodesToRemove) {
        if (root == null || nodesToRemove <= 0) return 0;
        
        return removeNodesBalanced(root, nodesToRemove);
    }
    
    /**
     * Entfernt Knoten balanciert aus dem Baum.
     * Entfernt von den tiefsten Ebenen her, um Balance zu erhalten.
     * 
     * @param root Root-Knoten
     * @param nodesToRemove Anzahl zu entfernender Knoten
     * @return Anzahl tatsächlich entfernter Knoten
     */
    private int removeNodesBalanced(MirrorNode root, int nodesToRemove) {
        List<MirrorNode> removalCandidates = findBalancedRemovalCandidates(root);
        int removed = 0;
        
        for (MirrorNode candidate : removalCandidates) {
            if (removed >= nodesToRemove) break;
            if (candidate != root) { // Root nicht entfernen
                MirrorNode parent = (MirrorNode) candidate.getParent();
                if (parent != null) {
                    parent.getChildren().remove(candidate);
                    parent.removeMirrorNode(candidate);
                    
                    // Entferne alle Links des entfernten Knotens
                    for (Link link : candidate.getAllLinks()) {
                        candidate.removeLink(link);
                    }
                    
                    removed++;
                }
            }
        }
        
        return removed;
    }
    
    /**
     * Findet Kandidaten für balancierte Entfernung.
     * Priorisiert Blätter auf tieferen Ebenen.
     * 
     * @param root Root-Knoten
     * @return Liste sortierter Entfernungskandidaten
     */
    private List<MirrorNode> findBalancedRemovalCandidates(MirrorNode root) {
        List<MirrorNode> candidates = new ArrayList<>();
        findRemovalCandidatesRecursive(root, candidates);
        
        // Sortiere nach Tiefe (tiefste zuerst) und bevorzuge Blätter
        candidates.sort((a, b) -> {
            // Blätter zuerst
            boolean aIsLeaf = a.isLeaf();
            boolean bIsLeaf = b.isLeaf();
            if (aIsLeaf != bIsLeaf) {
                return aIsLeaf ? -1 : 1;
            }
            
            // Dann tiefste zuerst
            int depthDiff = Integer.compare(b.getDepth(), a.getDepth());
            if (depthDiff != 0) return depthDiff;
            
            // Dann Knoten mit mehr Kindern zuerst (um Balance zu verbessern)
            return Integer.compare(b.getChildren().size(), a.getChildren().size());
        });
        
        return candidates;
    }
    
    /**
     * Rekursive Suche nach Entfernungskandidaten.
     */
    private void findRemovalCandidatesRecursive(MirrorNode node, List<MirrorNode> candidates) {
        for (TreeNode child : node.getChildren()) {
            findRemovalCandidatesRecursive((MirrorNode) child, candidates);
        }
        candidates.add(node); // Füge den Knoten nach seinen Kindern hinzu
    }
    
    @Override
    public Set<Link> createAndLinkMirrors(Network n, List<Mirror> mirrors, int simTime, Properties props) {
        if (mirrors.isEmpty()) return new HashSet<>();
        
        // Direkte Nutzung der BalancedTreeTopologyStrategy
        Mirror root = mirrors.get(0);
        List<Mirror> remainingMirrors = new ArrayList<>(mirrors.subList(1, mirrors.size()));
        
        return topologyStrategy.initNetworkSub(n, root, remainingMirrors, simTime, props);
    }
    
    /**
     * Berechnet die Balance-Metrik für den Baum.
     * 
     * @param root Root-Knoten
     * @return Balance-Wert (niedrigere Werte = bessere Balance)
     */
    public double calculateTreeBalance(MirrorNode root) {
        if (root == null) return 0.0;
        
        Map<Integer, Integer> depthCounts = new HashMap<>();
        calculateDepthCounts(root, depthCounts);
        
        // Berechne Standardabweichung der Knotenzahlen pro Ebene
        double mean = depthCounts.values().stream().mapToInt(i -> i).average().orElse(0.0);
        double variance = depthCounts.values().stream()
                .mapToDouble(count -> Math.pow(count - mean, 2))
                .average().orElse(0.0);
        
        return Math.sqrt(variance);
    }
    
    /**
     * Zählt Knoten pro Tiefenebene.
     */
    private void calculateDepthCounts(MirrorNode node, Map<Integer, Integer> depthCounts) {
        depthCounts.merge(node.getDepth(), 1, Integer::sum);
        for (TreeNode child : node.getChildren()) {
            calculateDepthCounts((MirrorNode) child, depthCounts);
        }
    }
    
    /**
     * Getter für die Anzahl der Target-Links pro Knoten.
     */
    public int getTargetLinksPerNode() {
        return targetLinksPerNode;
    }
    
    /**
     * Setter für die Anzahl der Target-Links pro Knoten.
     */
    public void setTargetLinksPerNode(int targetLinksPerNode) {
        this.targetLinksPerNode = targetLinksPerNode;
    }
    
    /**
     * Gibt die verwendete BalancedTreeTopologyStrategy zurück.
     */
    public BalancedTreeTopologyStrategy getTopologyStrategy() {
        return topologyStrategy;
    }
}