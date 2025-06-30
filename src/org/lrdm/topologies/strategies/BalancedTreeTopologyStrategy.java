
package org.lrdm.topologies.strategies;

import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.*;
import org.lrdm.topologies.node.*;
import org.lrdm.util.IDGenerator;

import java.util.*;

/**
 * Eine {@link TopologyStrategy}, die Mirrors als balancierten Baum mit einer
 * einzelnen Root verknüpft. Jeder Mirror hat maximal {@link Network#getNumTargetLinksPerMirror()} Kinder.
 * Die Strategie zielt darauf ab, eine Baumstruktur zu erstellen, bei der jeder Zweig
 * die gleiche Anzahl von Vorgängern hat (soweit möglich).
 * <p>
 * Verwendet {@link BalancedTreeMirrorNode} für Struktur-Management mit Balance-Optimierung
 * und erweitert {@link BuildAsSubstructure} für konsistente StructureBuilder-Integration.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class BalancedTreeTopologyStrategy extends BuildAsSubstructure {

    // ===== ZUSTANDSVARIABLEN FÜR SIM TIME UND PROPS =====
    private int currentSimTime = 0;
    private Properties currentProps;

    // ===== BUILD SUBSTRUCTURE IMPLEMENTATION =====

    /**
     * Baut eine balancierte Baum-Struktur mit der angegebenen Anzahl von Knoten auf.
     * Erstellt eine hierarchische Struktur mit optimaler Balance und n-1 Links.
     * Verwendet einen Breadth-First-Ansatz für optimale Balancierung.
     *
     * @param totalNodes Anzahl der zu erstellenden Knoten
     * @param simTime Aktuelle Simulationszeit für Link-Erstellung
     * @param props Properties der Simulation
     * @return Die Root-Node der erstellten balancierten Struktur
     */
    @Override
    protected MirrorNode buildStructure(int totalNodes, int simTime, Properties props) {
        this.currentSimTime = simTime;
        this.currentProps = props;

        if (totalNodes <= 0 || !mirrorIterator.hasNext()) return null;

        // Bestimme Links pro Node basierend auf Network-Einstellungen
        int linksPerNode = (network != null) ? network.getNumTargetLinksPerMirror() : 2;

        // Erstelle Root-Node mit erstem Mirror
        Mirror rootMirror = mirrorIterator.next();
        BalancedTreeMirrorNode root = new BalancedTreeMirrorNode(
                idGenerator.getNextID(), rootMirror, linksPerNode);

        // Setze Root als Head - nur der Head gibt den Strukturtyp vor
        root.setHead(true);

        // Erstelle balancierte Baum-Struktur rekursiv
        buildBalancedTreeRecursive(root, totalNodes - 1, linksPerNode, simTime, props);

        return root;
    }

    /**
     * Baut die balancierte Baum-Struktur rekursiv mit Stack-basierter Navigation auf.
     * Verwendet einen Breadth-First-Ansatz für optimale Balance-Verteilung.
     *
     * @param root Root-Node der Struktur
     * @param remainingNodes Anzahl noch zu erstellender Knoten
     * @param linksPerNode Maximale Anzahl Kinder pro Knoten
     * @param simTime Aktuelle Simulationszeit
     * @param props Properties der Simulation
     */
    private void buildBalancedTreeRecursive(BalancedTreeMirrorNode root, int remainingNodes,
                                            int linksPerNode, int simTime, Properties props) {
        if (remainingNodes <= 0 || !mirrorIterator.hasNext()) return;

        // Stack für Parent-Knoten (Breadth-First mit Stack-Simulation)
        Stack<BalancedTreeMirrorNode> parentStack = new Stack<>();
        parentStack.push(root);

        while (remainingNodes > 0 && !parentStack.isEmpty() && mirrorIterator.hasNext()) {
            BalancedTreeMirrorNode currentParent = parentStack.pop();

            // Berechne optimale Kindanzahl für diesen Parent
            int childrenToAdd = Math.min(
                    calculateOptimalChildrenForParent(currentParent, remainingNodes, linksPerNode),
                    remainingNodes
            );

            // Füge Kinder zu diesem Parent hinzu
            for (int i = 0; i < childrenToAdd && mirrorIterator.hasNext(); i++) {
                Mirror childMirror = mirrorIterator.next();
                BalancedTreeMirrorNode child = new BalancedTreeMirrorNode(
                        idGenerator.getNextID(), childMirror, linksPerNode);

                // Erstelle StructureNode-Verbindung (nur Head hat Typ-Kennzeichnung)
                currentParent.addChild(child);
                child.setParent(currentParent);

                // Erstelle echten Mirror-Link
                createMirrorLink(currentParent, child, simTime, props);

                // Füge Kind für nächste Ebene zu Stack hinzu
                parentStack.push(child);
                remainingNodes--;
            }
        }
    }

    /**
     * Berechnet die optimale Anzahl Kinder für einen Parent-Knoten zur Balance-Erhaltung.
     *
     * @param parent Der Parent-Knoten
     * @param remainingNodes Anzahl noch zu verteilender Knoten
     * @param maxLinksPerNode Maximale Links pro Knoten
     * @return Optimale Anzahl Kinder für diesen Parent
     */
    private int calculateOptimalChildrenForParent(BalancedTreeMirrorNode parent,
                                                  int remainingNodes, int maxLinksPerNode) {
        if (remainingNodes <= 0) return 0;

        // Verwende die Balance-Logik der BalancedTreeMirrorNode falls verfügbar
        if (parent != null) {
            return Math.min(remainingNodes, maxLinksPerNode);
        }

        // Fallback: Gleichmäßige Verteilung
        return Math.min(maxLinksPerNode, Math.min(remainingNodes, 2));
    }

    /**
     * Erstellt einen Mirror-Link zwischen Parent und Child.
     */
    private void createMirrorLink(BalancedTreeMirrorNode parent, BalancedTreeMirrorNode child,
                                  int simTime, Properties props) {
        Link link = new Link(idGenerator.getNextID(),
                parent.getMirror(), child.getMirror(), simTime, props);
        parent.getMirror().addLink(link);
        child.getMirror().addLink(link);
    }

    /**
     * Fügt neue Knoten zur bestehenden balancierten Baum-Struktur hinzu.
     * Neue Knoten werden an den optimalen Stellen eingefügt, um die Balance zu bewahren.
     *
     * @param nodesToAdd Anzahl der hinzuzufügenden Knoten
     * @return Tatsächliche Anzahl der hinzugefügten Knoten
     */
    @Override
    protected int addNodesToStructure(int nodesToAdd) {
        if (nodesToAdd <= 0 || getCurrentStructureRoot() == null) return 0;

        BalancedTreeMirrorNode root = (BalancedTreeMirrorNode) getCurrentStructureRoot();
        int actuallyAdded = 0;

        while (actuallyAdded < nodesToAdd && mirrorIterator.hasNext()) {
            // Finde besten einfüge Punkt mit rekursiver Navigation
            BalancedTreeMirrorNode bestParent = findBestInsertionParentRecursive(root);
            if (bestParent == null) break;

            // Erstelle neuen Knoten
            Mirror mirror = mirrorIterator.next();
            BalancedTreeMirrorNode newNode = new BalancedTreeMirrorNode(
                    idGenerator.getNextID(), mirror, root.getTargetLinksPerNode());

            // Füge zur Struktur hinzu (ohne Typ-Angabe bei Kindern)
            bestParent.addChild(newNode);
            newNode.setParent(bestParent);

            // Erstelle Mirror-Link
            createMirrorLink(bestParent, newNode, currentSimTime, currentProps);

            actuallyAdded++;
        }

        return actuallyAdded;
    }

    /**
     * Findet den besten Parent für das Einfügen eines neuen Knotens mit rekursiver Navigation.
     * Verwendet Stack-basierte Traversierung zur Balance-Erhaltung.
     */
    private BalancedTreeMirrorNode findBestInsertionParentRecursive(BalancedTreeMirrorNode root) {
        Stack<BalancedTreeMirrorNode> candidateStack = new Stack<>();
        BalancedTreeMirrorNode bestCandidate = null;
        int lowestDepth = Integer.MAX_VALUE;
        int fewestChildren = Integer.MAX_VALUE;

        // Stack-basierte Traversierung aller Knoten
        candidateStack.push(root);

        while (!candidateStack.isEmpty()) {
            BalancedTreeMirrorNode current = candidateStack.pop();

            // Prüfe, ob dieser Knoten weitere Kinder akzeptieren kann
            if (current.canAcceptMoreChildren()) {
                int currentDepth = calculateNodeDepth(current);
                int currentChildren = current.getChildren().size();

                // Bevorzuge niedrigere Tiefe, dann weniger Kinder
                if (currentDepth < lowestDepth ||
                        (currentDepth == lowestDepth && currentChildren < fewestChildren)) {
                    bestCandidate = current;
                    lowestDepth = currentDepth;
                    fewestChildren = currentChildren;
                }
            }

            // Füge Kinder zum Stack für weitere Exploration hinzu
            for (StructureNode child : current.getChildren()) {
                if (child instanceof BalancedTreeMirrorNode balancedChild) {
                    candidateStack.push(balancedChild);
                }
            }
        }

        return bestCandidate;
    }

    /**
     * Berechnet die Tiefe eines Knotens durch rekursive Parent-Navigation.
     */
    private int calculateNodeDepth(StructureNode node) {
        int depth = 0;
        StructureNode current = node;

        while (current.getParent() != null) {
            depth++;
            current = current.getParent();
        }

        return depth;
    }

    /**
     * Entfernt Knoten aus einer bestehenden balancierten Baum-Struktur.
     * Bevorzugt Blatt-Knoten für die Entfernung, um die Balance zu erhalten.
     *
     * @param nodesToRemove Anzahl der zu entfernenden Knoten
     * @return Anzahl der tatsächlich entfernten Knoten
     */
    @Override
    protected int removeNodesFromStructure(int nodesToRemove) {
        if (nodesToRemove <= 0 || getCurrentStructureRoot() == null) return 0;

        BalancedTreeMirrorNode root = (BalancedTreeMirrorNode) getCurrentStructureRoot();
        int actuallyRemoved = 0;

        while (actuallyRemoved < nodesToRemove) {
            // Finde Blatt-Knoten mit höchster Tiefe (außer Root)
            BalancedTreeMirrorNode leafToRemove = findDeepestLeafNodeRecursive(root);
            if (leafToRemove == null || leafToRemove == root) break;

            removeNodeFromBalancedTreeStructure(leafToRemove);
            actuallyRemoved++;
        }

        return actuallyRemoved;
    }

    /**
     * Findet den tiefsten Blatt-Knoten mit rekursiver Stack-Navigation.
     */
    private BalancedTreeMirrorNode findDeepestLeafNodeRecursive(BalancedTreeMirrorNode root) {
        Stack<BalancedTreeMirrorNode> nodeStack = new Stack<>();
        BalancedTreeMirrorNode deepestLeaf = null;
        int maxDepth = -1;

        nodeStack.push(root);

        while (!nodeStack.isEmpty()) {
            BalancedTreeMirrorNode current = nodeStack.pop();

            // Prüfe, ob dies ein Blatt-Knoten ist (keine Kinder)
            if (current.getChildren().isEmpty() && current != root) {
                int currentDepth = calculateNodeDepth(current);
                if (currentDepth > maxDepth) {
                    deepestLeaf = current;
                    maxDepth = currentDepth;
                }
            }

            // Füge Kinder zum Stack hinzu
            for (StructureNode child : current.getChildren()) {
                if (child instanceof BalancedTreeMirrorNode balancedChild) {
                    nodeStack.push(balancedChild);
                }
            }
        }

        return deepestLeaf;
    }

    /**
     * Entfernt einen Knoten vollständig aus der balancierten Baum-Struktur.
     * Bereinigt die Parent-Child-Beziehungen und Mirror-Links.
     */
    private void removeNodeFromBalancedTreeStructure(BalancedTreeMirrorNode nodeToRemove) {
        // Entferne Mirror-Links durch Mirror-Shutdown
        Mirror mirror = nodeToRemove.getMirror();
        if (mirror != null) {
            mirror.shutdown(currentSimTime);
        }

        // Bereinige StructureNode-Beziehungen
        StructureNode parent = nodeToRemove.getParent();
        if (parent != null) {
            parent.removeChild(nodeToRemove);
        }
    }

    /**
     * Sammelt alle Links aus der Struktur durch rekursive Navigation.
     * Durchläuft alle Knoten und sammelt deren Mirror-Links.
     */
    private Set<Link> collectAllLinksFromStructureRecursive(MirrorNode root) {
        Set<Link> allLinks = new HashSet<>();
        Stack<MirrorNode> nodeStack = new Stack<>();
        Set<MirrorNode> visited = new HashSet<>();

        nodeStack.push(root);

        while (!nodeStack.isEmpty()) {
            MirrorNode current = nodeStack.pop();
            if (visited.contains(current)) continue;
            visited.add(current);

            // Sammle Links von diesem Knoten
            if (current.getMirror() != null) {
                allLinks.addAll(current.getMirror().getLinks());
            }

            // Füge Kinder zum Stack hinzu
            for (StructureNode child : current.getChildren()) {
                if (child instanceof MirrorNode mirrorChild) {
                    nodeStack.push(mirrorChild);
                }
            }
        }

        return allLinks;
    }

    /**
     * Baut die tatsächlichen Links zwischen den Mirrors basierend auf der StructureNode-Struktur auf.
     * Erstellt für jede Parent-Child-Beziehung einen entsprechenden Mirror-Link.
     *
     * @param root Die Root-Node der Struktur
     * @param props Simulation Properties
     * @return Set aller erstellten Links
     */
    @Override
    protected Set<Link> buildAndConnectLinks(MirrorNode root, Properties props) {
        Set<Link> links = new HashSet<>();
        Stack<MirrorNode> nodeStack = new Stack<>();
        Set<MirrorNode> visited = new HashSet<>();

        nodeStack.push(root);

        while (!nodeStack.isEmpty()) {
            MirrorNode current = nodeStack.pop();
            if (visited.contains(current)) continue;
            visited.add(current);

            // Erstelle Links zu allen Kindern
            for (StructureNode child : current.getChildren()) {
                if (child instanceof BalancedTreeMirrorNode balancedChild &&
                        !visited.contains(balancedChild)) {

                    // Erstelle Mirror-Link falls noch nicht vorhanden
                    if (!isAlreadyLinked(current, balancedChild)) {
                        Link link = new Link(idGenerator.getNextID(),
                                current.getMirror(), balancedChild.getMirror(),
                                currentSimTime, currentProps);

                        current.getMirror().addLink(link);
                        balancedChild.getMirror().addLink(link);
                        links.add(link);
                    }

                    nodeStack.push(balancedChild);
                }
            }
        }

        return links;
    }

    /**
     * Prüft, ob zwei MirrorNodes bereits über Mirror-Links verbunden sind.
     */
    private boolean isAlreadyLinked(MirrorNode node1, MirrorNode node2) {
        if (node1.getMirror() == null || node2.getMirror() == null) return false;

        for (Link link : node1.getMirror().getLinks()) {
            Mirror source = link.getSource();
            Mirror target = link.getTarget();

            if ((source == node1.getMirror() && target == node2.getMirror()) ||
                    (source == node2.getMirror() && target == node1.getMirror())) {
                return true;
            }
        }

        return false;
    }

    // ===== TOPOLOGY STRATEGY INTERFACE IMPLEMENTATION =====

    /**
     * Initialisiert das Netzwerk mit der angegebenen Anzahl von Mirrors als balancierter Baum.
     *
     * @param n Das zu initialisierende {@link Network}
     * @param props {@link Properties} der Simulation
     * @return Set aller erstellten {@link Link}s
     */
    @Override
    public Set<Link> initNetwork(Network n, Properties props) {
        // Initialisiere Builder-Funktionalität
        this.network = n;
        this.idGenerator = IDGenerator.getInstance();
        this.mirrorIterator = n.getMirrors().iterator();
        this.currentProps = props;

        int totalMirrors = n.getNumMirrors();
        MirrorNode root = buildStructure(totalMirrors, 0, props);

        if (root != null) {
            setCurrentStructureRoot(root);
            return collectAllLinksFromStructureRecursive(root);
        }

        return new HashSet<>();
    }

    /**
     * Startet das Netzwerk neu und erstellt die balancierten Baum-Verbindungen erneut.
     *
     * @param n Das {@link Network}
     * @param props {@link Properties} der Simulation
     * @param simTime Aktuelle Simulationszeit
     */
    @Override
    public void restartNetwork(Network n, Properties props, int simTime) {
        super.restartNetwork(n, props, simTime);
        this.currentSimTime = simTime;
        this.currentProps = props;

        if (getCurrentStructureRoot() != null) {
            Set<Link> newLinks = buildAndConnectLinks(getCurrentStructureRoot(), props);
            n.getLinks().addAll(newLinks);
        }
    }

    /**
     * Fügt neue Mirrors zum Netzwerk hinzu und verbindet sie gemäß der balancierten Baum-Topologie.
     * Verwendet die geerbte createMirrors-Funktionalität für simTime und Props.
     *
     * @param n Das {@link Network}
     * @param newMirrors Anzahl der hinzuzufügenden Mirrors
     * @param props {@link Properties} der Simulation
     * @param simTime Aktuelle Simulationszeit
     */
    @Override
    public void handleAddNewMirrors(Network n, int newMirrors, Properties props, int simTime) {
        this.currentSimTime = simTime;
        this.currentProps = props;

        // Verwende TopologyStrategy.createMirrors für korrekte simTime/Props-Behandlung
        List<Mirror> addedMirrors = createMirrors(newMirrors, simTime, props);
        n.getMirrors().addAll(addedMirrors);

        // Erstelle neuen Iterator mit hinzugefügten Mirrors
        this.mirrorIterator = addedMirrors.iterator();

        // Füge zur bestehenden Struktur hinzu
        int actuallyAdded = addNodesToStructure(newMirrors);

        // Aktualisiere Network-Links
        if (getCurrentStructureRoot() != null) {
            Set<Link> allLinks = collectAllLinksFromStructureRecursive(getCurrentStructureRoot());
            n.getLinks().clear();
            n.getLinks().addAll(allLinks);
        }
    }

    /**
     * Entfernt Mirrors aus dem Netzwerk unter Beibehaltung der balancierten Baum-Struktur.
     *
     * @param n Das {@link Network}
     * @param removeMirrors Anzahl der zu entfernenden Mirrors
     * @param props {@link Properties} der Simulation
     * @param simTime Aktuelle Simulationszeit
     */
    @Override
    public void handleRemoveMirrors(Network n, int removeMirrors, Properties props, int simTime) {
        this.currentSimTime = simTime;
        this.currentProps = props;

        int actuallyRemoved = removeNodesFromStructure(removeMirrors);

        // Aktualisiere Network-Links
        if (getCurrentStructureRoot() != null) {
            Set<Link> allLinks = collectAllLinksFromStructureRecursive(getCurrentStructureRoot());
            n.getLinks().clear();
            n.getLinks().addAll(allLinks);
        }
    }

    /**
     * Gibt die Anzahl der Ziel-Links für das Netzwerk zurück.
     * Für einen Baum ist dies immer N-1 (N = Anzahl Mirrors).
     *
     * @param n Das {@link Network}
     * @return Anzahl der Ziel-Links (N-1)
     */
    @Override
    public int getNumTargetLinks(Network n) {
        return Math.max(0, n.getNumMirrors() - 1);
    }

    /**
     * Gibt die vorhergesagte Anzahl der Ziel-Links zurück, wenn die Aktion ausgeführt wird.
     *
     * @param a Die {@link Action}, die möglicherweise ausgeführt wird
     * @return Vorhergesagte Anzahl der Ziel-Links
     */
    @Override
    public int getPredictedNumTargetLinks(Action a) {
        int mirrors = a.getNetwork().getNumMirrors();
        if (a instanceof MirrorChange mc) {
            mirrors += mc.getNewMirrors();
        }
        return Math.max(0, mirrors - 1);
    }

    @Override
    public String toString() {
        return "BalancedTreeTopology{substructureId=" + getSubstructureId() + "}";
    }
}