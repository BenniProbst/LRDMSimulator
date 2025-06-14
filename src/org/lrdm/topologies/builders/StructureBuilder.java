
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
    protected Iterator<Mirror> mirrorIterator;

    /**
     * Konstruktor für vollständiges Network.
     * Iterator wird auf den Anfang der Mirror-Liste gesetzt.
     */
    public StructureBuilder(Network network) {
        this.idGenerator = IDGenerator.getInstance();
        this.network = network;
        this.mirrorIterator = network.getMirrors().iterator();
    }

    /**
     * Konstruktor für Substrukturen mit spezifischem Mirror-Iterator.
     * Ermöglicht das Bauen von Substrukturen an beliebigen Stellen.
     */
    public StructureBuilder(Network network, Iterator<Mirror> mirrorIterator) {
        this.idGenerator = IDGenerator.getInstance();
        this.network = network;
        this.mirrorIterator = mirrorIterator;
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
     * Nutzt die strukturspezifischen Validierungen der MirrorNode-Klassen.
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
                MirrorNode newNode = createMirrorNodeFromIterator();
                if (newNode != null) {
                    candidate.addChild(newNode);
                    // Validiere Struktur nach Hinzufügung
                    if (validateStructure(existingRoot)) {
                        added++;
                    } else {
                        // Rückgängig machen wenn Struktur ungültig wird
                        candidate.removeChild(newNode);
                        break;
                    }
                } else {
                    break; // Keine Mirrors mehr verfügbar
                }
            }
        }

        return added;
    }

    /**
     * Entfernt Knoten aus einer bestehenden Struktur.
     * Nutzt die strukturspezifischen Validierungen der MirrorNode-Klassen.
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
            if (node != existingRoot && canRemoveNode(node, existingRoot)) {
                MirrorNode parent = (MirrorNode) node.getParent();
                if (parent != null) {
                    parent.removeChild(node);
                    // Validiere Struktur nach Entfernung
                    if (validateStructure(existingRoot)) {
                        removed++;
                    } else {
                        // Rückgängig machen wenn Struktur ungültig wird
                        parent.addChild(node);
                        break;
                    }
                }
            }
        }

        return removed;
    }

    /**
     * Sammelt alle Mirrors aus einer Substruktur (ohne Parent-Knoten).
     * Essentiell für Substruktur-Operationen.
     *
     * @param root Root der Substruktur
     * @return Set aller Mirrors in der Substruktur
     */
    public Set<Mirror> getAllMirrors(MirrorNode root) {
        Set<Mirror> mirrors = new HashSet<>();
        if (root == null) return mirrors;

        Set<MirrorNode> visited = new HashSet<>();
        Stack<MirrorNode> stack = new Stack<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            MirrorNode current = stack.pop();
            if (visited.contains(current)) continue;
            visited.add(current);

            if (current.getMirror() != null) {
                mirrors.add(current.getMirror());
            }

            // Nur Kinder hinzufügen, keine Parent-Knoten
            for (TreeNode child : current.getChildren()) {
                if (!visited.contains(child)) {
                    stack.push((MirrorNode) child);
                }
            }
        }

        return mirrors;
    }

    /**
     * Erstellt Mirror-Verbindungen basierend auf der Struktur.
     * Verwendet Iterator für Mirror-Zuordnung.
     *
     * @param totalNodes Anzahl zu verwendender Knoten
     * @param simTime Simulationszeit
     * @param props Properties
     * @return Set der erstellten Links
     */
    public final Set<Link> createAndLinkMirrors(int totalNodes, int simTime, Properties props) {
        if (network == null || totalNodes <= 0) return new HashSet<>();

        MirrorNode root = build(totalNodes);
        if (root == null) return new HashSet<>();

        assignMirrorsToNodes(root);
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
     * Verwendet den Iterator für Mirror-Zuweisung.
     *
     * @param root Root der Struktur
     */
    protected final void assignMirrorsToNodes(MirrorNode root) {
        if (root == null) return;

        Stack<MirrorNode> stack = new Stack<>();
        Set<MirrorNode> visited = new HashSet<>();
        stack.push(root);

        while (!stack.isEmpty() && mirrorIterator.hasNext()) {
            MirrorNode current = stack.pop();
            if (visited.contains(current)) continue;
            visited.add(current);

            current.setMirror(mirrorIterator.next());
            addNeighborsToStack(current, stack, visited);
        }
    }

    /**
     * Erstellt einen neuen MirrorNode mit einem Mirror aus dem Iterator.
     * Ersetzt die alte createMirrorNodeFromNetwork-Methode.
     */
    protected MirrorNode createMirrorNodeFromIterator() {
        if (mirrorIterator.hasNext()) {
            Mirror mirror = mirrorIterator.next();
            return new MirrorNode(idGenerator.getNextID(), mirror);
        }
        return null; // Keine Mirrors mehr verfügbar
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

    // Abstrakte/überschreibbare Hilfsmethoden für Strukturspezifika
    protected abstract boolean isLeafInStructure(MirrorNode node);

    /**
     * Gibt an, ob Parent-Links für diese Struktur benötigt werden.
     * Wird für Ring-Strukturen überschrieben.
     */
    protected boolean needsParentLinks() {
        return false;
    }

    protected abstract List<MirrorNode> findInsertionCandidates(MirrorNode root);

    protected abstract List<MirrorNode> findRemovableNodes(MirrorNode root);

    /**
     * Prüft, ob an einen Knoten weitere Knoten hinzugefügt werden können.
     * Nutzt strukturspezifische Validierung.
     */
    protected boolean canAddNodeTo(MirrorNode node) {
        return mirrorIterator.hasNext() && node.canAcceptMoreChildren();
    }

    /**
     * Prüft, ob ein Knoten entfernt werden kann, ohne die Struktur zu zerstören.
     * Nutzt strukturspezifische Validierung.
     */
    protected boolean canRemoveNode(MirrorNode node, MirrorNode structureRoot) {
        return node.canBeRemovedFromStructure(structureRoot);
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