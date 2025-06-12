package org.lrdm.util;

import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;

import java.util.List;
import java.util.Properties;
import java.util.Set;

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
    public abstract int removeNodesFromTree(MirrorNode root, int nodesToRemove);

    /**
     * Erstellt Mirror-Verbindungen basierend auf der Tree-Struktur.
     *
     * @param mirrors Liste der zu verbindenden Mirrors
     * @param simTime Aktuelle Simulationszeit
     * @param props Simulationseigenschaften
     * @return Set der erstellten Links
     */
    public abstract Set<Link> createAndLinkMirrors(Network n, List<Mirror> mirrors, int simTime, Properties props);

    /**
     * Konvertiert eine TreeNode-Struktur in MirrorNodes mit Mirror-Zuordnungen.
     *
     * @param treeRoot Root des TreeNode-Baums
     * @param mirrors Liste der zu zuordnenden Mirrors
     * @return Root des MirrorNode-Baums
     */
    public MirrorNode convertToMirrorNodes(TreeNode treeRoot, List<Mirror> mirrors) {
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
    public void printTree(MirrorNode node, String prefix) {
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
    public int countNodes(MirrorNode root) {
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
    public int getMaxDepth(MirrorNode root) {
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
    protected int getNextId() {
        return idGenerator.getNextID();
    }

    /**
     * Validiert die Baum-Struktur auf Konsistenz.
     *
     * @param root Root-Knoten
     * @return true wenn der Baum konsistent ist
     */
    public boolean validateTreeStructure(MirrorNode root) {
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
