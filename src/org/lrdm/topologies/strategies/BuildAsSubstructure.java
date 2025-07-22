package org.lrdm.topologies.strategies;

import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.Action;
import org.lrdm.topologies.node.FullyConnectedMirrorNode;
import org.lrdm.topologies.node.MirrorNode;
import org.lrdm.topologies.node.StructureNode;
import org.lrdm.util.IDGenerator;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Abstrakte Basisklasse für hierarchische Substruktur-basierte TopologyStrategy.
 * <p>
 * **Zweck**: Erfüllung des TopologyStrategy-Contracts mit integrierter StructureBuilder-Funktionalität.
 * Die 5 essenziellen TopologyStrategy-Methoden sind die einzige öffentliche API.
 * <p>
 * **Interne StructureBuilder-Integration**:
 * - Verwaltet MirrorNode-basierte Strukturen
 * - Verwendet mirrorIterator für verfügbare, noch nicht zugeordnete Mirrors
 * - Observer Pattern für Struktur-Änderungen
 * - Rekursive Link-Erstellung mit Mirror-Zuordnung
 * <p>
 * **Public API** (nur TopologyStrategy-Methoden):
 * - initNetwork(), restartNetwork(), handleAddNewMirrors()
 * - getNumTargetLinks(), getPredictedNumTargetLinks()
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public abstract class BuildAsSubstructure extends TopologyStrategy {

    // ===== EINDEUTIGE SUBSTRUKTUR-ID =====
    private final int substructureId;

    // ===== STRUCTURE BUILDER-INTEGRATION (PROTECTED) =====
    protected IDGenerator idGenerator;
    protected Network network;
    protected Iterator<Mirror> mirrorIterator;

    // ===== MIRROR NODE-BASIERTE SUBSTRUKTUR-VERWALTUNG (PRIVATE) =====
    private final Map<MirrorNode, BuildAsSubstructure> nodeToSubstructure = new HashMap<>();
    private final Set<MirrorNode> structureNodes = new HashSet<>();
    private MirrorNode currentStructureRoot;

    // ===== OBSERVER PATTERN (PRIVATE) =====
    private final List<StructureChangeObserver> observers = new ArrayList<>();

    // ===== KONSTRUKTOR =====

    public BuildAsSubstructure() {
        this.idGenerator = IDGenerator.getInstance();
        this.substructureId = idGenerator.getNextID();
    }

    public BuildAsSubstructure(IDGenerator idGenerator) {
        this.idGenerator = idGenerator;
        this.substructureId = idGenerator.getNextID();
    }

    // ===== NEUE GETTER UND SETTER (PROTECTED) =====

    /**
     * Gibt die eindeutige Substruktur-ID zurück.
     *
     * @return Die eindeutige ID dieser Substruktur
     */
    public final int getSubstructureId() {
        return substructureId;
    }

    /**
     * Gibt die Root-Node der aktuellen Struktur zurück.
     *
     * @return Die Root-Node der Struktur oder null, wenn noch nicht initialisiert
     */
    public final MirrorNode getCurrentStructureRoot() {
        return currentStructureRoot;
    }

    /**
     * Setzt die Root-Node der aktuellen Struktur (für Builder).
     * PROTECTED - nur für Builder und interne Nutzung.
     *
     * @param root Die neue Root-Node
     */
    protected final void setCurrentStructureRoot(MirrorNode root) {
        this.currentStructureRoot = root;
        if (root != null) {
            addToStructureNodes(root);
        }
    }

    /**
     * Gibt den Strukturtyp der aktuellen Substruktur zurück.
     * Ermittelt den Typ über die Root-Node.
     *
     * @return Der StructureType der aktuellen Substruktur oder null, wenn nicht, initialisiert
     */
    public final StructureNode.StructureType getCurrentStructureType() {
        if (currentStructureRoot == null) {
            return null;
        }
        return currentStructureRoot.deriveTypeId();
    }

    /**
     * Gibt alle aktuell verwalteten MirrorNodes der Struktur zurück.
     * Diese Collection wird aktiv gepflegt und ermöglicht die Auswahl spezifischer Knoten
     * für den Anbau weiterer Substrukturen.
     * <p>
     * REKURSIV: Berücksichtigt auch alle Knoten der untergeordneten Substrukturen.
     *
     * @return Unveränderliche Kopie aller MirrorNodes in der Struktur und aller Substrukturen
     */
    public final Set<MirrorNode> getAllStructureNodes() {
        return Set.copyOf(getAllStructureNodesRecursive(new HashSet<>()));
    }

    /**
     * Sammelt rekursiv alle MirrorNodes aus dieser Struktur und allen Substrukturen.
     * Verhindert zirkuläre Referenzen durch visited-Set.
     *
     * @param visited Set zur Vermeidung zirkulärer Referenzen
     * @return Alle MirrorNodes in der Struktur-Hierarchie
     */
    private Set<MirrorNode> getAllStructureNodesRecursive(Set<BuildAsSubstructure> visited) {
        if (visited.contains(this)) {
            return Set.of(); // Zirkuläre Referenz erkannt
        }

        visited.add(this);
        Set<MirrorNode> allNodes = new HashSet<>(structureNodes);

        // Rekursiv alle Knoten der Substrukturen sammeln
        for (BuildAsSubstructure substructure : nodeToSubstructure.values()) {
            if (substructure != this) {
                allNodes.addAll(substructure.getAllStructureNodesRecursive(visited));
            }
        }

        return allNodes;
    }

    /**
     * Gibt die Zuordnung von MirrorNode zu BuildAsSubstructure zurück.
     * PROTECTED - ermöglicht Subklassen den Zugriff auf Substruktur-Zuordnungen.
     *
     * @return Unveränderliche Kopie der Node-zu-Substruktur-Zuordnung
     */
    protected final Map<MirrorNode, BuildAsSubstructure> getNodeToSubstructureMapping() {
        return Map.copyOf(nodeToSubstructure);
    }

    /**
     * Findet die Substruktur, zu der ein bestimmter MirrorNode gehört.
     * PROTECTED - ermöglicht Subklassen die Identifikation von Substrukturen.
     *
     * @param node Der MirrorNode
     * @return Die zugehörige BuildAsSubstructure oder null, wenn nicht gefunden
     */
    protected final BuildAsSubstructure findSubstructureForNode(MirrorNode node) {
        return nodeToSubstructure.get(node);
    }

    /**
     * Gibt alle Substruktur-Tupel zurück (MirrorNode, BuildAsSubstructure).
     * PROTECTED - ermöglicht Iteration über alle Substruktur-Zuordnungen.
     *
     * @return Liste aller Substruktur-Tupel
     */
    protected final List<SubstructureTuple> getAllSubstructureTuples() {
        return nodeToSubstructure.entrySet().stream()
                .map(entry -> new SubstructureTuple(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * Tupel-Klasse für MirrorNode-BuildAsSubstructure-Zuordnungen.
     * Ermöglicht die typsichere Rückgabe von Zuordnungspaaren.
     */
    public static record SubstructureTuple(MirrorNode node, BuildAsSubstructure substructure) {
        public SubstructureTuple {
            Objects.requireNonNull(node, "MirrorNode darf nicht null sein");
            Objects.requireNonNull(substructure, "BuildAsSubstructure darf nicht null sein");
        }

        /**
         * Gibt die ID des MirrorNodes zurück.
         */
        public int getNodeId() {
            return node.getId();
        }

        /**
         * Gibt die Substruktur-ID zurück.
         */
        public int getSubstructureId() {
            return substructure.getSubstructureId();
        }

        /**
         * Prüft, ob dieser Knoten zu einer bestimmten Substruktur-ID gehört.
         */
        public boolean belongsToSubstructure(int substructureId) {
            return this.substructure.getSubstructureId() == substructureId;
        }
    }

    /**
     * Setzt eine Substruktur-Zuordnung.
     * PROTECTED - ermöglicht Builder die Registrierung von Substruktur-Zuordnungen.
     *
     * @param node Die MirrorNode
     * @param substructure Die zugehörige BuildAsSubstructure
     */
    protected final void setSubstructureForNode(MirrorNode node, BuildAsSubstructure substructure) {
        if (node != null && substructure != null) {
            nodeToSubstructure.put(node, substructure);
        }
    }

    /**
     * Entfernt eine Substruktur-Zuordnung.
     * ERWEITERT: Bereinigt auch rekursiv alle Substrukturen des entfernten Knotens.
     *
     * @param node Die MirrorNode
     */
    protected final void removeSubstructureForNode(MirrorNode node) {
        BuildAsSubstructure removedSubstructure = nodeToSubstructure.remove(node);

        if (removedSubstructure != null && removedSubstructure != this) {
            // Alle Knoten der entfernten Substruktur ebenfalls aus der Zuordnung entfernen
            Set<MirrorNode> substructureNodes = removedSubstructure.getAllStructureNodes();
            for (MirrorNode subNode : substructureNodes) {
                nodeToSubstructure.remove(subNode);
            }
        }
    }


    /**
     * Gibt alle verfügbaren Endpunkte (Terminal-Knoten) zurück, an die neue Substrukturen
     * angebaut werden können.
     * REKURSIV: Berücksichtigt auch Terminal-Knoten aus Substrukturen.
     *
     * @return Set aller Terminal-MirrorNodes, die für Substruktur-Erweiterungen geeignet sind
     */
    public final Set<MirrorNode> getAvailableConnectionPoints() {
        Set<MirrorNode> connectionPoints = new HashSet<>();

        // Lokale Connection Points sammeln
        if (currentStructureRoot != null) {
            StructureNode.StructureType typeId = currentStructureRoot.deriveTypeId();
            int headId = currentStructureRoot.getId();

            connectionPoints.addAll(structureNodes.stream()
                    .filter(node -> node.canAcceptMoreChildren(typeId, headId))
                    .filter(node -> node.isTerminal(typeId, headId) || node.isHead(typeId))
                    .collect(Collectors.toSet()));
        }

        // Rekursiv Connection Points aus Substrukturen sammeln
        for (BuildAsSubstructure substructure : nodeToSubstructure.values()) {
            if (substructure != this) {
                connectionPoints.addAll(substructure.getAvailableConnectionPoints());
            }
        }

        return connectionPoints;
    }

    /**
     * Sucht einen spezifischen MirrorNode anhand seiner ID.
     * REKURSIV: Sucht auch in allen Substrukturen.
     *
     * @param nodeId Die ID des gesuchten Knotens
     * @return Der MirrorNode mit der angegebenen ID oder null, wenn nicht gefunden
     */
    public final MirrorNode findStructureNodeById(int nodeId) {
        // Lokale Suche
        MirrorNode found = structureNodes.stream()
                .filter(node -> node.getId() == nodeId)
                .findFirst()
                .orElse(null);

        if (found != null) {
            return found;
        }

        // Rekursive Suche in Substrukturen
        for (BuildAsSubstructure substructure : nodeToSubstructure.values()) {
            if (substructure != this) {
                found = substructure.findStructureNodeById(nodeId);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    // ===== TOPOLOGY STRATEGY CONTRACT (PUBLIC API) =====

    /**
     * Initialisiert das Netzwerk durch Aufbau der strukturspezifischen Topologie.
     * Erstellt eine MirrorNode-Struktur und verknüpft alle Links.
     *
     * @param n Das Netzwerk
     * @param props Simulation Properties
     * @return Set aller erstellten Links
     */
    @Override
    public Set<Link> initNetwork(Network n, Properties props) {
        initializeInternalState(n);

        MirrorNode root = buildStructure(n.getNumMirrors(), props);
        if (root != null) {
            setCurrentStructureRoot(root);
            return buildAndUpdateLinks(root, props, 0, getCurrentStructureType());
        }

        return getAllLinksRecursive();
    }

        /**
         * Sammelt rekursiv alle Links aus dieser Struktur und allen Substrukturen.
         * Verwendet einen Stack-basierten Ansatz zur Traversierung der Struktur-Hierarchie.
         * Verhindert zirkuläre Referenzen durch visited-Set.
         *
         * @return Set aller Links in der Struktur-Hierarchie
         */
    protected final Set<Link> getAllLinksRecursive() {
        Set<Link> allLinks = new HashSet<>();
        Set<BuildAsSubstructure> visitedSubstructures = new HashSet<>();
        Stack<BuildAsSubstructure> substructureStack = new Stack<>();

        // Start mit dieser Substruktur
        substructureStack.push(this);

        while (!substructureStack.isEmpty()) {
            BuildAsSubstructure currentSubstructure = substructureStack.pop();

            // Zirkuläre Referenzen vermeiden
            if (visitedSubstructures.contains(currentSubstructure)) {
                continue;
            }

            visitedSubstructures.add(currentSubstructure);

            // Links aus allen StructureNodes der aktuellen Substruktur sammeln
            for (MirrorNode node : currentSubstructure.structureNodes) {
                Mirror mirror = node.getMirror();
                if (mirror != null) {
                    allLinks.addAll(mirror.getLinks());
                }
            }

            // Alle Substrukturen auf den Stack legen
            for (BuildAsSubstructure substructure : currentSubstructure.nodeToSubstructure.values()) {
                if (substructure != currentSubstructure && !visitedSubstructures.contains(substructure)) {
                    substructureStack.push(substructure);
                }
            }
        }

        return allLinks;
    }

    /**
     * Startet das Netzwerk komplett neu mit der aktuellen Topologie.
     * Löscht alle bestehenden Verbindungen und baut sie neu auf.
     *
     * @param n       Das Netzwerk
     * @param props   Simulation Properties
     * @param simTime Aktuelle Simulationszeit
     * @return created links
     */
    public Set<Link> restartNetwork(Network n, Properties props, int simTime) {

        super.restartNetwork(n, props, simTime);

        // Komplett neu initialisieren
        initializeInternalState(n);

        MirrorNode root = buildStructure(n.getNumMirrors(), props);
        if (root != null) {
            setCurrentStructureRoot(root);
            Set<Link> links = buildAndUpdateLinks(root, props, simTime, getCurrentStructureType());
            n.getLinks().addAll(links);
            return links;
        }

        Set<Link> links = getAllLinksRecursive();
        n.getLinks().addAll(links);
        return links;
    }

    /**
     * Fügt neue Mirrors zum Netzwerk hinzu und integriert sie in die bestehende Struktur.
     * Verwendet die strukturspezifische addNodes-Logik.
     *
     * @param n Das Netzwerk
     * @param newMirrors Anzahl hinzuzufügender Mirrors
     * @param props Simulation Properties
     * @param simTime Aktuelle Simulationszeit
     */
    public void handleAddNewMirrors(Network n, int newMirrors, Properties props, int simTime) {
        // Verwende das offizielle Interface von TopologyStrategy
        List<Mirror> addedMirrors = createMirrors(newMirrors, simTime, props);
        n.getMirrors().addAll(addedMirrors);

        // Setze Iterator für die neuen Mirrors - BuildAsSubstructure erwartet diesen
        // Iterator für neue Mirrors setzen
        setMirrorIterator(addedMirrors.iterator());

        // Füge die neuen Knoten zur Struktur hinzu
        int actuallyAdded = addNodesToStructure(newMirrors);

        if (actuallyAdded > 0 && getCurrentStructureRoot() != null) {
            // Baue nur die neuen Links auf
            Set<Link> newLinks = buildAndUpdateLinks(getCurrentStructureRoot(), props, 0, getCurrentStructureType());
            n.getLinks().addAll(newLinks);
        }
    }

    /**
     * Gibt die erwartete Anzahl Links für das Netzwerk gemäß dieser Topologie zurück.
     * Muss von Subklassen implementiert werden.
     *
     * @param n Das Netzwerk
     * @return Erwartete Anzahl Links
     */
    @Override
    public abstract int getNumTargetLinks(Network n);

    /**
     * Gibt die vorhergesagte Anzahl von Links zurück, falls die Action ausgeführt wird.
     * Muss von Subklassen implementiert werden.
     *
     * @param a Die potenziell auszuführende Action
     * @return Vorhergesagte Anzahl Links
     */
    @Override
    public abstract int getPredictedNumTargetLinks(Action a);

    // ===== ABSTRAKTE Strukturbuilder-METHODEN (PROTECTED) =====

    /**
     * Erstellt eine neue Struktur mit der angegebenen Anzahl von Knoten.
     * Muss von Subklassen für spezifische Strukturtypen implementiert werden.
     * Ist eine zeitlose Planungsklasse und ändert sich bei einem Aufruf!
     * Wird erst durch einen Simulationszeitpunkt durch das Bauen von Links aktiviert.
     *
     * @param totalNodes Anzahl der zu erstellenden Knoten
     * @param props      Properties der Simulation
     * @return Root-Knoten der erstellten Struktur
     */
    protected abstract MirrorNode buildStructure(int totalNodes, Properties props);

    /**
     * Fügt Knoten zu einer bestehenden Struktur hinzu.
     * Muss von Subklassen für spezifische Strukturlogik implementiert werden.
     *
     * @param nodesToAdd Anzahl der hinzuzufügenden Knoten
     * @return Anzahl der tatsächlich hinzugefügten Knoten
     */
    protected abstract int addNodesToStructure(int nodesToAdd);

    /**
     * Entfernt Knoten aus einer bestehenden Struktur.
     * Kann von Subklassen überschrieben werden.
     *
     * @param nodesToRemove Anzahl der zu entfernenden Knoten
     * @return Anzahl der tatsächlich entfernten Knoten
     */
    protected abstract int removeNodesFromStructure(int nodesToRemove);



    // ===== INTERNE STRUCTURE BUILDER-HILFSMETHODEN (PROTECTED) =====

    /**
     * Validiert die aktuelle vollständig vernetzte Struktur.
     *
     * @return true, wenn die Struktur gültig ist
     */
    protected abstract boolean validateTopology();

    /**
     * Fügt einen MirrorNode zu den verwalteten Struktur-Knoten hinzu.
     * Stellt sicher, dass structureNodes immer aktuell ist.
     *
     * @param node Der hinzuzufügende MirrorNode
     */
    protected final void addToStructureNodes(MirrorNode node) {
        if (node != null) {
            structureNodes.add(node);
            nodeToSubstructure.put(node, this);
        }
    }

    /**
     * Entfernt einen MirrorNode aus den verwalteten Struktur-Knoten.
     * Stellt sicher, dass structureNodes immer aktuell ist.
     *
     * @param node Der zu entfernende MirrorNode
     */
    protected final void removeFromStructureNodes(MirrorNode node) {
        if (node != null) {
            structureNodes.remove(node);
            nodeToSubstructure.remove(node);
        }
    }


    /**
     * Erweiterte handleRemoveMirrors-Implementierung für BuildAsSubstructure.
     * WICHTIG: Ruft NICHT super.handleRemoveMirrors() auf, da dies die Mirrors ausschalten würde.
     * Stattdessen werden nur die Links entfernt und die Topologie neu strukturiert.
     * Die Mirrors bleiben am Leben und werden nur neu verkabelt.
     *
     * @param n Das Netzwerk
     * @param removeMirrors Anzahl der zu entfernenden Mirrors
     * @param props Simulation Properties
     * @param simTime Aktuelle Simulationszeit
     * @return Set von bereinigten Mirrors für Neuverteilung
     */
    @Override
    public Set<Mirror> handleRemoveMirrors(Network n, int removeMirrors, Properties props, int simTime) {
        if (removeMirrors <= 0) return new HashSet<>();

        // 1. Identifiziere die Mirrors mit den höchsten IDs (wie in der Standard-Implementierung)
        List<Mirror> sortedMirrors = n.getMirrorsSortedById();
        Set<Mirror> mirrorsToRemove = new LinkedHashSet<>();

        for (int i = 0; i < removeMirrors && i < sortedMirrors.size(); i++) {
            Mirror mirrorToRemove = sortedMirrors.get(sortedMirrors.size() - 1 - i);
            mirrorsToRemove.add(mirrorToRemove);
        }

        // 2. Sammle die entsprechenden MirrorNodes vor der Strukturänderung
        List<MirrorNode> nodesToRemove = new ArrayList<>();
        for (Mirror mirror : mirrorsToRemove) {
            MirrorNode node = findMirrorNodeForMirror(mirror);
            if (node != null) {
                nodesToRemove.add(node);
            }
        }

        // 3. Entferne alle Links der betroffenen Mirrors (ohne sie auszuschalten)
        Set<Link> linksToRemove = new HashSet<>();
        for (Mirror mirror : mirrorsToRemove) {
            linksToRemove.addAll(new ArrayList<>(mirror.getLinks())); // Kopie für sichere Iteration
        }

        for (Link link : linksToRemove) {
            // Entferne Link von beiden Mirrors
            link.getSource().removeLink(link);
            link.getTarget().removeLink(link);
            // Entferne Link vom Netzwerk
            n.getLinks().remove(link);
        }

        // 4. Entferne die Mirrors vom Netzwerk (aber schalte sie NICHT aus)
        for (Mirror mirror : mirrorsToRemove) {
            n.getMirrors().remove(mirror);
        }

        // 5. StructureNode-Verwaltung bereinigen

        return cleanupStructureNodes(nodesToRemove);
    }

    /**
     * Hilfsmethode zur Suche des MirrorNodes für einen Mirror.
     *
     * @param mirror Der zu suchende Mirror
     * @return Der zugehörige MirrorNode oder null
     */
    private MirrorNode findMirrorNodeForMirror(Mirror mirror) {
        return getAllStructureNodes().stream()
                .filter(node -> node.getMirror() == mirror)
                .findFirst()
                .orElse(null);
    }

    /**
     * Bereinigt Links nur innerhalb der eigenen Struktur-Hierarchie.
     * Verwendet MirrorNode-Interfaces und bereinigt auch das Network.
     *
     * @param nodeToRemove Der zu entfernende Knoten
     * @param validNodes Alle gültigen Knoten der Struktur-Hierarchie
     */
    private void cleanupLinksInHierarchy(MirrorNode nodeToRemove, Set<MirrorNode> validNodes) {
        if (nodeToRemove.getMirror() == null) return;

        // Alle implementierten Links des Knotens sammeln
        Set<Link> implementedLinks = new HashSet<>(nodeToRemove.getImplementedLinks());

        for (Link link : implementedLinks) {
            Mirror otherMirror = getOtherMirror(link, nodeToRemove.getMirror());
            if (otherMirror != null) {
                // Prüfen, ob der andere Mirror zu einem Knoten in unserer Hierarchie gehört
                MirrorNode otherNode = findNodeForMirror(otherMirror, validNodes);
                if (otherNode != null) {
                    // Link aus beiden MirrorNodes entfernen (über MirrorNode-Interface)
                    nodeToRemove.removeLink(link);
                    otherNode.removeLink(link);

                    // Link aus dem Network entfernen
                    if (network != null) {
                        network.getLinks().remove(link);
                    }
                }
            }
        }
    }

    /**
     * Findet den MirrorNode für einen gegebenen Mirror innerhalb der gültigen Knoten.
     *
     * @param mirror Der zu suchende Mirror
     * @param validNodes Set der gültigen Knoten
     * @return Der zugehörige MirrorNode oder null
     */
    private MirrorNode findNodeForMirror(Mirror mirror, Set<MirrorNode> validNodes) {
        return validNodes.stream()
                .filter(node -> node.getMirror() == mirror)
                .findFirst()
                .orElse(null);
    }


    /**
     * Ermittelt den anderen Mirror eines Links.
     *
     * @param link Der Link
     * @param currentMirror Der aktuelle Mirror
     * @return Der andere Mirror des Links
     */
    private Mirror getOtherMirror(Link link, Mirror currentMirror) {
        if (link.getSource() == currentMirror) {
            return link.getTarget();
        } else if (link.getTarget() == currentMirror) {
            return link.getSource();
        }
        return null;
    }

    /**
     * Prüft, ob ein Mirror zu einem Knoten in unserer Struktur-Hierarchie gehört.
     */
    private boolean isMirrorInHierarchy(Mirror mirror, Set<MirrorNode> validNodes) {
        return validNodes.stream()
                .anyMatch(node -> node.getMirror() == mirror);
    }

    /**
     * Bereinigt Parent-Child-Beziehungen für einen entfernten Knoten.
     *
     * @param nodeToRemove Der zu entfernende Knoten
     */
    private void cleanupNodeRelationships(MirrorNode nodeToRemove) {
        // Parent-Verbindung bereinigen
        if (nodeToRemove.getParent() != null) {
            nodeToRemove.getParent().removeChild(nodeToRemove);
        }

        // Kinder-Verbindungen bereinigen
        List<StructureNode> children = new ArrayList<>(nodeToRemove.getChildren());
        for (StructureNode child : children) {
            nodeToRemove.removeChild(child);

            // Kinder an Parent weiterreichen oder als neue Roots behandeln
            if (nodeToRemove.getParent() != null) {
                nodeToRemove.getParent().addChild(child);
            }
        }
    }

    /**
     * Aktualisiert die Root-Node, falls die aktuelle Root entfernt wurde.
     *
     * @param nodeToRemove Der zu entfernende Knoten
     */
    private void updateRootIfNecessary(MirrorNode nodeToRemove) {
        if (getCurrentStructureRoot() == nodeToRemove) {
            // Finde neue Root aus verbleibenden Knoten
            MirrorNode newRoot = structureNodes.isEmpty() ? null : structureNodes.iterator().next();
            setCurrentStructureRoot(newRoot);
        }
    }

    /**
     * Findet eine neue Root-Node aus den verbleibenden StructureNodes.
     * PRIVATE - interne Strukturverwaltung.
     *
     * @return Neue Root-Node oder null, wenn keine Knoten mehr vorhanden
     */
    private MirrorNode findNewRoot() {
        if (structureNodes.isEmpty()) {
            return null;
        }

        // Strategie: Wähle den Knoten mit den wenigsten Parents
        return structureNodes.stream()
                .min(Comparator.comparingInt(node ->
                        node.getParent() != null ? 1 : 0))
                .orElse(null);
    }

    /**
     * Registriert einen Observer für Strukturänderungen.
     * PUBLIC - ermöglicht externe Observer-Registrierung.
     *
     * @param observer Der zu registrierende Observer
     */
    public final void addStructureChangeObserver(StructureChangeObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
        }
    }

    /**
     * Entfernt einen Observer für Strukturänderungen.
     * PUBLIC - ermöglicht externe Observer-Deregistrierung.
     *
     * @param observer Der zu entfernende Observer
     */
    public final void removeStructureChangeObserver(StructureChangeObserver observer) {
        observers.remove(observer);
    }


    /**
     * Setzt den Iterator für verfügbare Mirrors.
     */
    protected final void setMirrorIterator(Iterator<Mirror> mirrorIterator) {
        this.mirrorIterator = mirrorIterator;
    }

    /**
     * Initialisiert den internen Zustand für ein Netzwerk.
     */
    private void initializeInternalState(Network n) {
        this.network = n;
        this.mirrorIterator = n.getMirrors().iterator();
    }

    /**
     * Setzt nur die StructureNode-Struktur zurück, nicht die Mirror-Links.
     * Wird verwendet, wenn die Mirror-Links bereits durch TopologyStrategy.restartNetwork() gelöscht wurden.
     */
    protected void resetInternalStateStructureOnly() {
        nodeToSubstructure.clear();
        structureNodes.clear();
        currentStructureRoot = null;
    }

    // ===== ABSTRAKTE METHODEN FÜR LINK-ERSTELLUNG =====

    /**
     * Baut die tatsächlichen Links zwischen den Mirrors basierend auf der StructureNode-Struktur auf.
     * Erstellt für jede StructureNode-Verbindung einen entsprechenden Mirror-Link.
     *
     * @param root    Die Root-Node der Struktur
     * @param props   Simulation Properties
     * @param simTime Zeitpunkt der Simulation
     * @return Set aller erstellten Links
     */
    protected Set<Link> buildAndUpdateLinks(MirrorNode root, Properties props, int simTime, StructureNode.StructureType structureType) {
        Set<Link> allLinks = new HashSet<>();

        if (!(root instanceof FullyConnectedMirrorNode fcRoot)) {
            return allLinks;
        }

        // Sammle alle Knoten in der Struktur
        List<FullyConnectedMirrorNode> nodeList = fcRoot.getAllNodesInStructure(structureType, fcRoot)
                .stream()
                .filter(node -> node instanceof FullyConnectedMirrorNode)
                .map(node -> (FullyConnectedMirrorNode) node).distinct().toList();

        // Erstelle Links zwischen allen Paaren von Knoten
        for (int i = 0; i < nodeList.size(); i++) {
            FullyConnectedMirrorNode node1 = nodeList.get(i);

            for (FullyConnectedMirrorNode node2 : nodeList) {
                //self connect is forbidden
                if (node1.equals(node2)) continue;

                boolean node12_connect = node1.getChildren().contains(node2);
                boolean node21_connect = node2.getChildren().contains(node1);

                if ((node1.getMirror().isAlreadyConnected(node2.getMirror()) && !node2.getMirror().isAlreadyConnected(node1.getMirror())) ||
                        (!node1.getMirror().isAlreadyConnected(node2.getMirror()) && node2.getMirror().isAlreadyConnected(node1.getMirror()))) {
                    throw new IllegalStateException("Link-Verbindung zwischen Knoten " + node1.getId() + " und " + node2.getId() + " ist bereits vorhanden, aber es gibt ein asymmetrisches Verbindungsproblem!");
                }

                // Prüfe, ob die Mirrors bereits verbunden sind
                if (!node1.getMirror().isAlreadyConnected(node2.getMirror()) && !node2.getMirror().isAlreadyConnected(node1.getMirror())) {
                    //Mirror nicht verbunden, sollte er per Plan verbunden sein → Link erstellen
                    if (node12_connect) {
                        Link link = new Link(idGenerator.getNextID(), node1.getMirror(), node2.getMirror(),
                                simTime, props);
                        node1.getMirror().addLink(link);
                        node2.getMirror().addLink(link);
                        allLinks.add(link);
                    } else {
                        if (node21_connect) {
                            Link link = new Link(idGenerator.getNextID(), node2.getMirror(), node1.getMirror(),
                                    simTime, props);
                            node2.getMirror().addLink(link);
                            node1.getMirror().addLink(link);
                            allLinks.add(link);
                        }
                    }
                } else {
                    //Mirror verbunden, solle er nicht verbunden sein → Link löschen
                    if (!node12_connect && !node21_connect) {
                        //sollte überhaupt nicht verbunden sein → Verbindung löschen

                        Set<Link> links1 = node1.getMirror().getLinksTo(node2.getMirror());
                        for(Link link1:links1){
                            link1.shutdown();
                        }

                        Set<Link> links2 = node2.getMirror().getLinksTo(node1.getMirror());
                        for(Link link2:links2){
                            link2.shutdown();
                        }
                    }
                }
            }
        }

        return allLinks;
    }

    // ===== OBSERVER PATTERN INTERFACES =====

    /**
     * Interface für Observer von Struktur-Änderungen.
     */
    public interface StructureChangeObserver {
        void onNodesAdded(List<StructureNode> addedNodes, StructureNode headNode);
        void onNodesRemoved(List<StructureNode> removedNodes, StructureNode headNode);
        void onLinksCreated(Set<Link> newLinks, StructureNode headNode);
        void onLinksRemoved(Set<Link> removedLinks, StructureNode headNode);
        void onStructureChanged(BuildAsSubstructure structure);
    }


    /**
     * Bereinigt die StructureNode-Verwaltung für entfernte Knoten.
     * REKURSIV: Berücksichtigt Substrukturen und prüft, ob Links/Mirrors nur innerhalb
     * der eigenen Struktur-Hierarchie aufgelöst werden dürfen.
     *
     * @param nodesToRemove Liste der zu entfernenden MirrorNodes
     * @return Set von Mirrors, die bereinigt wurden und für Neuverteilung verfügbar sind
     */
    Set<Mirror> cleanupStructureNodes(List<MirrorNode> nodesToRemove) {
        Set<MirrorNode> allValidNodes = getAllStructureNodesRecursive(new HashSet<>());
        Set<Mirror> cleanedMirrors = new HashSet<>();

        for (MirrorNode nodeToRemove : nodesToRemove) {
            // Nur bereinigen, wenn der Knoten tatsächlich zu dieser Struktur-Hierarchie gehört
            if (allValidNodes.contains(nodeToRemove)) {
                Mirror cleanedMirror = cleanupNodeInHierarchy(nodeToRemove, allValidNodes);
                if (cleanedMirror != null) {
                    cleanedMirrors.add(cleanedMirror);
                }
            }
        }

        return cleanedMirrors;
    }

    /**
     * Bereinigt einen einzelnen Knoten innerhalb der Struktur-Hierarchie.
     *
     * @param nodeToRemove Der zu entfernende Knoten
     * @param validNodes Alle gültigen Knoten der Struktur-Hierarchie
     * @return Der bereinigte Mirror oder null, wenn kein Mirror zugeordnet war
     */
    private Mirror cleanupNodeInHierarchy(MirrorNode nodeToRemove, Set<MirrorNode> validNodes) {
        Mirror mirror = nodeToRemove.getMirror();

        // 1. Aus lokalen structureNodes entfernen
        structureNodes.remove(nodeToRemove);

        // 2. Substruktur-Zuordnung bereinigen
        BuildAsSubstructure owningSubstructure = nodeToSubstructure.get(nodeToRemove);
        if (owningSubstructure != null) {
            owningSubstructure.removeSubstructureForNode(nodeToRemove);
        }

        // 3. Links innerhalb der Hierarchie bereinigen (über MirrorNode-Interface)
        cleanupLinksInHierarchy(nodeToRemove, validNodes);

        // 4. Node-Beziehungen bereinigen
        cleanupNodeRelationships(nodeToRemove);

        // 5. Root-Update falls nötig
        updateRootIfNecessary(nodeToRemove);

        // 6. Mirror für Neuverteilung vorbereiten
        if (mirror != null) {
            mirror.setRoot(false); // Root-Flag zurücksetzen
            return mirror;
        }

        return null;
    }

    /**
     * Fügt einen Observer hinzu.
     */
    public final void addObserver(StructureChangeObserver observer) {
        if (observer != null) {
            observers.add(observer);
        }
    }

    /**
     * Entfernt einen Observer.
     */
    public final void removeObserver(StructureChangeObserver observer) {
        observers.remove(observer);
    }

    /**
     * Benachrichtigt Observer über hinzugefügte Knoten.
     */
    protected final void notifyNodesAdded(List<StructureNode> addedNodes, StructureNode headNode) {
        for (StructureChangeObserver observer : observers) {
            observer.onNodesAdded(addedNodes, headNode);
        }

        // Automatische structureNodes-Aktualisierung
        for (StructureNode node : addedNodes) {
            if (node instanceof MirrorNode mirrorNode) {
                addToStructureNodes(mirrorNode);
            }
        }
    }

    /**
     * Benachrichtigt Observer über entfernte Knoten.
     */
    protected final void notifyNodesRemoved(List<StructureNode> removedNodes, StructureNode headNode) {
        for (StructureChangeObserver observer : observers) {
            observer.onNodesRemoved(removedNodes, headNode);
        }

        // Automatische structureNodes-Aktualisierung
        for (StructureNode node : removedNodes) {
            if (node instanceof MirrorNode mirrorNode) {
                removeFromStructureNodes(mirrorNode);
            }
        }
    }

    /**
     * Benachrichtigt Observer über erstellte Links.
     */
    protected final void notifyLinksCreated(Set<Link> newLinks, StructureNode headNode) {
        for (StructureChangeObserver observer : observers) {
            observer.onLinksCreated(newLinks, headNode);
        }
    }

    /**
     * Benachrichtigt Observer über entfernte Links.
     */
    protected final void notifyLinksRemoved(Set<Link> removedLinks, StructureNode headNode) {
        for (StructureChangeObserver observer : observers) {
            observer.onLinksRemoved(removedLinks, headNode);
        }
    }

    /**
     * Benachrichtigt alle Observer über Strukturänderungen.
     * PROTECTED - für Subklassen-Zugriff verfügbar.
     */
    protected final void notifyStructureChanged() {
        // Einfache Implementation ohne Observer Pattern - falls nicht benötigt
        // kann diese Methode leer bleiben oder entfernt werden
        for (StructureChangeObserver observer : observers) {
            observer.onStructureChanged(this);
        }
    }


    // ===== HILFSMETHODEN =====

    /**
     * Erstellt neue Mirror-Instanzen.
     */
    protected final List<Mirror> createMirrors(int count, int simTime, Properties props) {
        List<Mirror> mirrors = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Mirror mirror = new Mirror(idGenerator.getNextID(), simTime, props);
            mirrors.add(mirror);
        }
        return mirrors;
    }

    /**
     * Gibt den nächsten verfügbaren Mirror aus dem Iterator zurück.
     * Vereinfacht die Interface-Trennung zwischen BuildAsSubstructure und Topology-Implementierungen.
     *
     * @return Der nächste verfügbare Mirror oder null, wenn kein Mirror verfügbar ist
     */
    protected final Mirror getNextMirror() {
        if (mirrorIterator != null && mirrorIterator.hasNext()) {
            while (mirrorIterator.hasNext()) {
                Mirror mirror = mirrorIterator.next();
                if(mirror.isUsableForNetwork())return mirror;
            }
        }
        return null;
    }

    /**
     * Prüft, ob weitere Mirrors im Iterator verfügbar sind.
     *
     * @return true, wenn weitere Mirrors verfügbar sind
     */
    protected final boolean hasNextMirror() {
        return mirrorIterator != null && mirrorIterator.hasNext();
    }

    /**
     * Erstellt einen neuen MirrorNode mit Mirror aus dem Iterator.
     * AKTUALISIERT: Fügt den Knoten automatisch zu structureNodes hinzu.
     *
     * @return Neuer MirrorNode mit zugeordnetem Mirror oder null
     */
    protected final MirrorNode getMirrorNodeFromIterator() {
        if (mirrorIterator != null && mirrorIterator.hasNext()) {
            Mirror mirror = mirrorIterator.next();
            MirrorNode node = createMirrorNodeForMirror(mirror);
            if (node != null) {
                node.setMirror(mirror);
                addToStructureNodes(node); // Aktiv hinzufügen
            }
            return node;
        }
        return null;
    }

    /**
     * Factory-Methode für strukturspezifische MirrorNode-Erstellung.
     * Kann von Subklassen überschrieben werden.
     *
     * @param mirror Der Mirror, für den ein MirrorNode erstellt werden soll
     * @return Neuer strukturspezifischer MirrorNode
     */
    protected MirrorNode createMirrorNodeForMirror(Mirror mirror) {
        return new MirrorNode(idGenerator.getNextID(), mirror);
    }

    /**
     * Gibt eine detaillierte String-Repräsentation der BuildAsSubstructure zurück.
     * Enthält Informationen über Knotenzahl, erwartete Links, Struktur-Validität
     * und Substruktur-Details.
     *
     * @return String-Repräsentation mit Klassenname, Knotenzahl, erwarteten Links und Validitätsstatus
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        // Grundlegende Klasseninformationen
        sb.append(this.getClass().getSimpleName());
        sb.append(" [");

        // Struktur-Informationen
        sb.append("substructureId=").append(substructureId);
        sb.append(", nodeCount=").append(structureNodes.size());

        // Netzwerk-Informationen falls verfügbar
        if (network != null) {
            sb.append(", expectedLinks=").append(getNumTargetLinks(network));
        }

        // Validitätsstatus
        sb.append(", isValid=").append(validateTopology());

        // Root-Informationen
        if (currentStructureRoot != null) {
            sb.append(", rootId=").append(currentStructureRoot.getId());
            sb.append(", structureType=").append(getCurrentStructureType());
        }

        // Substruktur-Informationen
        if (!nodeToSubstructure.isEmpty()) {
            sb.append(", substructures=").append(nodeToSubstructure.size());
        }

        sb.append("]");

        return sb.toString();
    }
}
