package org.lrdm.topologies.builders;

import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.util.IDGenerator;
import org.lrdm.topologies.base.MirrorNode;
import org.lrdm.topologies.base.TreeNode;

import java.util.*;

/**
 * Abstrakte Basisklasse für alle Structure-Builder.
 * Verallgemeinert TreeBuilder für beliebige Strukturen (Tree, Ring, Star, Line).
 * Zustandslos - jede Methode analysiert/erstellt vollständig neu.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public abstract class StructureBuilder {
    protected IDGenerator idGenerator;
    protected Network network;

    public StructureBuilder(Network network) {
        this.idGenerator = IDGenerator.getInstance();
        this.network = network;
    }

    /**
     * Erstellt eine neue Struktur mit der angegebenen Anzahl von Knoten.
     * Muss von Kindklassen implementiert werden.
     *
     * @param totalNodes Anzahl der zu erstellenden Knoten
     * @return Root/Head-Knoten der erstellten Struktur
     */
    public abstract MirrorNode build(int totalNodes);

    /**
     * Fügt Knoten zu einer bestehenden Struktur hinzu.
     * Standardimplementierung - kann von Kindklassen überschrieben werden.
     *
     * @param existingRoot Bestehende Struktur-Root
     * @param nodesToAdd Anzahl hinzuzufügender Knoten
     * @return Anzahl tatsächlich hinzugefügter Knoten
     */
    public int addNodes(MirrorNode existingRoot, int nodesToAdd) {
        if (existingRoot == null || nodesToAdd <= 0) return 0;

        List<MirrorNode> candidates = findInsertionCandidates(existingRoot);
        int added = 0;

        for (MirrorNode candidate : candidates) {
            if (added >= nodesToAdd) break;
            if (canAddNodeTo(candidate)) {
                MirrorNode newNode = createMirrorNodeFromNetwork();
                candidate.addChild(newNode);
                added++;
            }
        }

        return added;
    }

    /**
     * Entfernt Knoten aus einer bestehenden Struktur.
     * Ring-sichere Implementierung.
     *
     * @param existingRoot Bestehende Struktur-Root
     * @param nodesToRemove Anzahl zu entfernender Knoten
     * @return Anzahl tatsächlich entfernter Knoten
     */
    public int removeNodes(MirrorNode existingRoot, int nodesToRemove) {
        if (existingRoot == null || nodesToRemove <= 0) return 0;

        List<MirrorNode> removableNodes = findRemovableNodes(existingRoot);
        int removed = 0;

        for (MirrorNode node : removableNodes) {
            if (removed >= nodesToRemove) break;
            if (node != existingRoot && canRemoveNode(node)) {
                MirrorNode parent = (MirrorNode) node.getParent();
                if (parent != null) {
                    parent.removeChild(node);
                    removed++;
                }
            }
        }

        return removed;
    }

    /**
     * Erstellt Mirror-Verbindungen basierend auf der Struktur.
     * Verwendet Network für Mirror-Zuordnung.
     *
     * @param totalNodes Anzahl zu verwendender Knoten
     * @param simTime Simulationszeit
     * @param props Properties
     * @return Set der erstellten Links
     */
    public final Set<Link> createAndLinkMirrors(int totalNodes, int simTime, Properties props) {
        if (network == null || totalNodes <= 0) return new HashSet<>();

        List<Mirror> availableMirrors = network.getMirrors();
        if (availableMirrors.size() < totalNodes) {
            throw new IllegalArgumentException("Nicht genügend Mirrors im Network verfügbar");
        }

        MirrorNode root = build(totalNodes);
        if (root == null) return new HashSet<>();

        assignMirrorsToNodes(root, availableMirrors.subList(0, totalNodes));
        return createLinksFromStructure(root, simTime, props);
    }

    /**
     * Validiert die Struktur.
     * Muss von Kindklassen implementiert werden.
     *
     * @param root Root/Head der zu validierenden Struktur
     * @return true wenn Struktur gültig
     */
    public abstract boolean validateStructure(MirrorNode root);

    /**
     * Ordnet Mirrors den MirrorNodes zu - Ring-sichere Stack-basierte Implementierung.
     *
     * @param root Root der Struktur
     * @param mirrors Liste verfügbarer Mirrors
     */
    protected final void assignMirrorsToNodes(MirrorNode root, List<Mirror> mirrors) {
        if (root == null || mirrors.isEmpty()) return;

        Stack<MirrorNode> stack = new Stack<>();
        Set<MirrorNode> visited = new HashSet<>();
        stack.push(root);

        Iterator<Mirror> mirrorIterator = mirrors.iterator();

        while (!stack.isEmpty() && mirrorIterator.hasNext()) {
            MirrorNode current = stack.pop();
            if (visited.contains(current)) continue;
            visited.add(current);

            current.setMirror(mirrorIterator.next());
            addNeighborsToStack(current, stack, visited);
        }
    }

    /**
     * Findet alle "Blätter" der Struktur - Ring-sichere Implementierung.
     */
    protected List<MirrorNode> findLeaves(MirrorNode root) {
        List<MirrorNode> leaves = new ArrayList<>();
        if (root == null) return leaves;

        Set<MirrorNode> visited = new HashSet<>();
        Stack<MirrorNode> stack = new Stack<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            MirrorNode current = stack.pop();
            if (visited.contains(current)) continue;
            visited.add(current);

            if (isLeafInStructure(current)) {
                leaves.add(current);
            }

            addNeighborsToStack(current, stack, visited);
        }

        return leaves;
    }

    /**
     * Zählt alle Knoten in der Struktur - Ring-sichere Implementierung.
     */
    public int countNodes(MirrorNode root) {
        if (root == null) return 0;
        Set<TreeNode> allNodes = root.getAllNodesInStructure();
        return allNodes.size();
    }

    /**
     * Findet die maximale "Tiefe" bis zu einem bekannten Knoten - Ring-sichere Implementierung.
     */
    public int getMaxDepth(MirrorNode root) {
        if (root == null) return -1;

        Set<MirrorNode> visited = new HashSet<>();
        Queue<DepthInfo> queue = new LinkedList<>();
        queue.offer(new DepthInfo(root, 0));

        int maxDepth = 0;

        while (!queue.isEmpty()) {
            DepthInfo current = queue.poll();
            if (visited.contains(current.node)) continue;
            visited.add(current.node);

            maxDepth = Math.max(maxDepth, current.depth);

            for (TreeNode child : current.node.getChildren()) {
                if (!visited.contains(child)) {
                    queue.offer(new DepthInfo((MirrorNode) child, current.depth + 1));
                }
            }
        }

        return maxDepth;
    }

    /**
     * Erstellt Links basierend auf der Struktur - Ring-sichere Stack-Implementierung.
     */
    protected final Set<Link> createLinksFromStructure(MirrorNode root, int simTime, Properties props) {
        Set<Link> links = new HashSet<>();
        if (root == null) return links;

        Stack<MirrorNode> stack = new Stack<>();
        Set<MirrorNode> visited = new HashSet<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            MirrorNode current = stack.pop();
            if (visited.contains(current) || current.getMirror() == null) continue;
            visited.add(current);

            for (TreeNode child : current.getChildren()) {
                MirrorNode mirrorChild = (MirrorNode) child;
                if (mirrorChild.getMirror() != null) {
                    Link link = new Link(
                            idGenerator.getNextID(),
                            current.getMirror(),
                            mirrorChild.getMirror(),
                            simTime,
                            props
                    );
                    links.add(link);
                    current.addLink(link);
                    mirrorChild.addLink(link);
                }

                if (!visited.contains(mirrorChild)) {
                    stack.push(mirrorChild);
                }
            }

            if (needsParentLinks() && current.getParent() != null) {
                MirrorNode parent = (MirrorNode) current.getParent();
                if (!visited.contains(parent) && parent.getMirror() != null) {
                    stack.push(parent);
                }
            }
        }

        return links;
    }

    /**
     * Private Hilfsmethode für das Hinzufügen von Nachbarn zum Stack.
     * Eliminiert Code-Duplikation.
     */
    private void addNeighborsToStack(MirrorNode current, Stack<MirrorNode> stack, Set<MirrorNode> visited) {
        if (current.getParent() != null && !visited.contains(current.getParent())) {
            stack.push((MirrorNode) current.getParent());
        }
        for (TreeNode child : current.getChildren()) {
            if (!visited.contains(child)) {
                stack.push((MirrorNode) child);
            }
        }
    }

    /**
     * Erstellt einen neuen MirrorNode mit einem Mirror aus dem Network.
     */
    protected MirrorNode createMirrorNodeFromNetwork() {
        List<Mirror> availableMirrors = network.getMirrors();
        if (!availableMirrors.isEmpty()) {
            Mirror mirror = availableMirrors.get(0); // Oder andere Auswahllogik
            return new MirrorNode(idGenerator.getNextID(), mirror);
        }
        return new MirrorNode(idGenerator.getNextID());
    }

    // Abstrakte/überschreibbare Hilfsmethoden für Strukturspezifika
    protected boolean isLeafInStructure(MirrorNode node) {
        return node.isLeaf();
    }

    protected boolean needsParentLinks() {
        return false;
    }

    protected List<MirrorNode> findInsertionCandidates(MirrorNode root) {
        return findLeaves(root);
    }

    protected List<MirrorNode> findRemovableNodes(MirrorNode root) {
        return findLeaves(root);
    }

    protected boolean canAddNodeTo(MirrorNode node) {
        return true;
    }

    protected boolean canRemoveNode(MirrorNode node) {
        return node.isLeaf();
    }

    /**
     * Hilfklasse für Tiefenberechnung.
     */
    protected static class DepthInfo {
        final MirrorNode node;
        final int depth;

        DepthInfo(MirrorNode node, int depth) {
            this.node = node;
            this.depth = depth;
        }
    }
}