
package org.lrdm.util;

import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;

import java.util.*;

/**
 * Abstrakte Basisklasse für TreeBuilder-Implementierungen.
 * Definiert das Interface für verschiedene Tree-Building-Strategien.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public abstract class TreeBuilder {
    protected IDGenerator idGenerator;

    public TreeBuilder() {
        this.idGenerator = IDGenerator.getInstance();
    }

    /**
     * Erstellt einen neuen Baum mit der angegebenen Anzahl von Knoten.
     *
     * @param totalNodes Gesamtanzahl der zu erstellenden Knoten
     * @param maxDepth Maximale Tiefe des Baums (0 für unbegrenzt)
     * @return Root-Knoten des erstellten Baums
     */
    public abstract MirrorNode buildTree(int totalNodes, int maxDepth);

    /**
     * Fügt Knoten zu einem bestehenden Baum hinzu.
     *
     * @param existingRoot Root-Knoten des bestehenden Baums
     * @param nodesToAdd Anzahl der hinzuzufügenden Knoten
     * @param maxDepth Maximale Tiefe des Baums
     * @return Anzahl der tatsächlich hinzugefügten Knoten
     */
    public abstract int addNodesToExistingTree(MirrorNode existingRoot, int nodesToAdd, int maxDepth);

    /**
     * Entfernt Knoten aus einem bestehenden Baum.
     *
     * @param root Root-Knoten des Baums
     * @param nodesToRemove Anzahl der zu entfernenden Knoten
     * @return Anzahl der tatsächlich entfernten Knoten
     */
    public int removeNodesFromTree(MirrorNode root, int nodesToRemove) {
        if (root == null || nodesToRemove <= 0) return 0;

        List<MirrorNode> leaves = findLeaves(root);
        int removed = 0;

        for (MirrorNode leaf : leaves) {
            if (removed >= nodesToRemove) break;
            if (leaf != root) { // Root nicht entfernen
                MirrorNode parent = (MirrorNode) leaf.getParent();
                if (parent != null) {
                    parent.getChildren().remove(leaf);
                    parent.removeMirrorNode(leaf);
                    removed++;
                }
            }
        }

        return removed;
    }

    /**
     * Erstellt Mirror-Verbindungen basierend auf der Tree-Struktur.
     * Gemeinsame Implementierung für alle TreeBuilder-Subklassen.
     *
     * @param n Network-Objekt für Kontext
     * @param mirrors Liste der zu verbindenden Mirrors
     * @param simTime Aktuelle Simulationszeit
     * @param props Simulationseigenschaften
     * @return Set der erstellten Links
     */
    public final Set<Link> createAndLinkMirrors(Network n, List<Mirror> mirrors, int simTime, Properties props) {
        if (mirrors.isEmpty()) return new HashSet<>();

        // Erstelle eine MirrorNode-Struktur basierend auf der spezifischen Strategie
        MirrorNode root = createTreeStructureWithMirrors(mirrors.size(), getEffectiveMaxDepth(), mirrors);

        if (root == null) return new HashSet<>();

        // Erstelle Links basierend auf der Tree-Struktur
        return createLinksFromTreeStructure(root, simTime, props);
    }

    /**
     * Gibt die effektive maximale Tiefe für diese TreeBuilder-Implementierung zurück.
     * Wird von Subklassen überschrieben.
     *
     * @return Maximale Tiefe (0 für unbegrenzt)
     */
    protected abstract int getEffectiveMaxDepth();

    /**
     * Generische Implementierung der Tree-Link-Erstellung.
     *
     * @param root MirrorNode-Root mit zugeordneten Mirrors
     * @param simTime Simulationszeit
     * @param props Eigenschaften
     * @return Set der erstellten Links
     */
    protected final Set<Link> createLinksFromTreeStructure(MirrorNode root, int simTime, Properties props) {
        Set<Link> links = new HashSet<>();
        if (root == null) return links;

        createLinksRecursive(root, links, simTime, props);
        return links;
    }

    /**
     * Rekursive Hilfsmethode zur Link-Erstellung.
     * Jeder Parent wird mit seinen direkten Kindern verlinkt.
     */
    private void createLinksRecursive(MirrorNode node, Set<Link> links, int simTime, Properties props) {
        if (node.getMirror() == null) return;

        for (TreeNode child : node.getChildren()) {
            MirrorNode mirrorChild = (MirrorNode) child;
            if (mirrorChild.getMirror() != null) {
                // Erstelle Link zwischen Parent und Kind
                Link link = new Link(
                        idGenerator.getNextID(),
                        node.getMirror(),
                        mirrorChild.getMirror(),
                        simTime,
                        props
                );
                links.add(link);

                // Füge Link zu beiden Mirrors hinzu für Konsistenz
                node.getMirror().addLink(link);
                mirrorChild.getMirror().addLink(link);

                // Aktualisiere auch die MirrorNode-internen Strukturen
                node.addLink(link);
                mirrorChild.addLink(link);
            }
            // Rekursiv für alle Kinder
            createLinksRecursive(mirrorChild, links, simTime, props);
        }
    }

    /**
     * Hilfsmethode: Erstellt eine MirrorNode-Struktur und ordnet Mirrors zu.
     *
     * @param totalNodes Anzahl der Knoten
     * @param maxDepth Maximale Tiefe
     * @param mirrors Liste der Mirrors
     * @return Root-MirrorNode mit zugeordneten Mirrors
     */
    protected final MirrorNode createTreeStructureWithMirrors(int totalNodes, int maxDepth, List<Mirror> mirrors) {
        if (mirrors.isEmpty()) return null;

        // Erstelle Tree-Struktur
        MirrorNode root = buildTree(totalNodes, maxDepth);
        if (root == null) return null;

        // Ordne Mirrors zu
        assignMirrorsToNodes(root, mirrors, 0);

        return root;
    }

    /**
     * Ordnet Mirrors den MirrorNodes in Breadth-First-Reihenfolge zu.
     */
    protected final int assignMirrorsToNodes(MirrorNode node, List<Mirror> mirrors, int currentIndex) {
        if (currentIndex < mirrors.size()) {
            node.setMirror(mirrors.get(currentIndex));
            currentIndex++;
        }

        for (TreeNode child : node.getChildren()) {
            currentIndex = assignMirrorsToNodes((MirrorNode) child, mirrors, currentIndex);
            if (currentIndex >= mirrors.size()) break;
        }

        return currentIndex;
    }

    /**
     * Findet alle Blätter des Baums.
     * Gemeinsame Implementierung, die von Subklassen überschrieben werden kann.
     *
     * @param root Root-Knoten
     * @return Liste der Blätter, sortiert nach Tiefe (tiefste zuerst)
     */
    protected List<MirrorNode> findLeaves(MirrorNode root) {
        List<MirrorNode> leaves = new ArrayList<>();
        findLeavesRecursive(root, leaves);

        // Sortiere nach Tiefe (tiefste zuerst) für besseres Entfernen
        leaves.sort((a, b) -> Integer.compare(b.getDepth(), a.getDepth()));

        return leaves;
    }

    /**
     * Rekursive Suche nach Blättern.
     */
    protected final void findLeavesRecursive(MirrorNode node, List<MirrorNode> leaves) {
        if (node.isLeaf()) {
            leaves.add(node);
        } else {
            for (TreeNode child : node.getChildren()) {
                findLeavesRecursive((MirrorNode) child, leaves);
            }
        }
    }

    /**
     * Konvertiert eine TreeNode-Struktur in MirrorNodes mit Mirror-Zuordnungen.
     *
     * @param treeRoot Root des TreeNode-Baums
     * @param mirrors Liste der zu zuordnenden Mirrors
     * @return Root des MirrorNode-Baums
     */
    public final MirrorNode convertToMirrorNodes(TreeNode treeRoot, List<Mirror> mirrors) {
        if (mirrors.isEmpty()) return null;

        MirrorNode mirrorRoot = new MirrorNode(treeRoot.getId(), treeRoot.getDepth());
        convertToMirrorNodesRecursive(treeRoot, mirrorRoot, mirrors, 0);
        return mirrorRoot;
    }

    private int convertToMirrorNodesRecursive(TreeNode treeNode, MirrorNode mirrorNode,
                                              List<Mirror> mirrors, int mirrorIndex) {
        if (mirrorIndex < mirrors.size()) {
            mirrorNode.setMirror(mirrors.get(mirrorIndex));
            mirrorIndex++;
        }

        for (TreeNode child : treeNode.getChildren()) {
            MirrorNode mirrorChild = new MirrorNode(child.getId(), child.getDepth());
            mirrorNode.addChild(mirrorChild);
            mirrorIndex = convertToMirrorNodesRecursive(child, mirrorChild, mirrors, mirrorIndex);
        }

        return mirrorIndex;
    }

    /**
     * Hilfsmethode zur Ausgabe der Baum-Struktur.
     *
     * @param node Ausgangsknoten
     * @param prefix Präfix für die Ausgabe
     */
    public final void printTree(MirrorNode node, String prefix) {
        if (node == null) return;

        System.out.println(prefix + "├── Node " + node.getId() +
                (node.getMirror() != null ? " (Mirror: " + node.getMirror().getID() + ")" : "") +
                " [Links: " + node.getNumTargetLinks() + "]");

        String childPrefix = prefix + "│   ";
        for (int i = 0; i < node.getChildren().size(); i++) {
            MirrorNode child = (MirrorNode) node.getChildren().get(i);
            if (i == node.getChildren().size() - 1) {
                System.out.println(prefix + "└── Node " + child.getId() +
                        (child.getMirror() != null ? " (Mirror: " + child.getMirror().getID() + ")" : "") +
                        " [Links: " + child.getNumTargetLinks() + "]");
                printTree(child, prefix + "    ");
            } else {
                printTree(child, childPrefix);
            }
        }
    }

    /**
     * Berechnet die Gesamtanzahl der Knoten im Baum.
     *
     * @param root Root-Knoten
     * @return Anzahl der Knoten
     */
    public final int countNodes(MirrorNode root) {
        if (root == null) return 0;

        int count = 1;
        for (TreeNode child : root.getChildren()) {
            count += countNodes((MirrorNode) child);
        }
        return count;
    }

    /**
     * Berechnet die maximale Tiefe des Baums.
     *
     * @param root Root-Knoten
     * @return Maximale Tiefe
     */
    public final int getMaxDepth(MirrorNode root) {
        if (root == null) return 0;

        int maxChildDepth = 0;
        for (TreeNode child : root.getChildren()) {
            maxChildDepth = Math.max(maxChildDepth, getMaxDepth((MirrorNode) child));
        }
        return maxChildDepth + 1;
    }

    /**
     * Gibt die nächste verfügbare ID zurück.
     *
     * @return Nächste ID
     */
    protected final int getNextId() {
        return idGenerator.getNextID();
    }

    /**
     * Validiert die Baum-Struktur auf Konsistenz.
     *
     * @param root Root-Knoten
     * @return true wenn der Baum konsistent ist
     */
    public final boolean validateTreeStructure(MirrorNode root) {
        if (root == null) return true;

        // Überprüfe Parent-Child-Beziehungen
        for (TreeNode child : root.getChildren()) {
            if (child.getParent() != root) {
                return false;
            }
            if (!validateTreeStructure((MirrorNode) child)) {
                return false;
            }
        }

        return true;
    }
}