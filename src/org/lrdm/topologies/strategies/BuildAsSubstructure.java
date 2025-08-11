package org.lrdm.topologies.strategies;

import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.Action;
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
    protected Network network;

    // ===== MIRROR NODE-BASIERTE SUBSTRUKTUR-VERWALTUNG (PRIVATE) =====
    private final Map<MirrorNode, BuildAsSubstructure> nodeToSubstructure = new HashMap<>();
    private final Set<MirrorNode> structureNodes = new HashSet<>();
    private MirrorNode currentStructureRoot;

    // ===== OBSERVER PATTERN (PRIVATE) =====
    private final List<StructureChangeObserver> observers = new ArrayList<>();

    // ===== KONSTRUKTOR =====

    public BuildAsSubstructure() {
        this.substructureId = IDGenerator.getInstance().getNextID();
    }

    public BuildAsSubstructure(IDGenerator idGenerator) {
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
        if (currentStructureRoot == null) {
            throw new IllegalStateException("Current structure root is not initialized yet!");
        }
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
    public StructureNode.StructureType getCurrentStructureType() {
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
    public record SubstructureTuple(MirrorNode node, BuildAsSubstructure substructure) {
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
        resetInternalStateStructureOnly();

        // Also init substructure templates that were added, take those topology templates here
        for(BuildAsSubstructure subStructure : nodeToSubstructure.values()){
            subStructure.initNetwork(n,props);
        }

        int usableMirrorCount = network.getMirrorCursor().getNumUsableMirrors();
        MirrorNode root = buildStructure(usableMirrorCount);
        if (root != null) {
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
        resetInternalStateStructureOnly();

        int usableMirrorCount = Math.toIntExact(n.getMirrors().stream()
                .filter(Mirror::isUsableForNetwork).count());
        MirrorNode root = buildStructure(usableMirrorCount);
        if (root != null) {
            setCurrentStructureRoot(root);
            return buildAndUpdateLinks(root, props, simTime, getCurrentStructureType());
        }

        Set<Link> links = getAllLinksRecursive();
        n.getLinks().addAll(links);

        //clean up extra mirrors on the network in case a system failed to replace the crashed mirror
        Set<Mirror> unusedMirrorToShutdown = new HashSet<>(network.getMirrors());
        for(MirrorNode node:this.getAllStructureNodes()){
            if(node.getMirror()!=null){
                unusedMirrorToShutdown.remove(node.getMirror());
            }
        }
        unusedMirrorToShutdown.forEach(mirror -> mirror.shutdown(simTime));

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
        if (newMirrors <= 0) {
            return;
        }

        initializeInternalState(n);

        // Verwende das offizielle Interface von TopologyStrategy
        Set<Mirror> creatingMirrors = n.getMirrorCursor().createMirrors(newMirrors, simTime);

        // Füge die neuen Knoten zur Struktur hinzu
        int actuallyAdded = addNodesToStructure(creatingMirrors);

        if (actuallyAdded > 0 && getCurrentStructureRoot() != null) {
            // Baue nur die neuen Links auf
            n.getLinks().addAll(buildAndUpdateLinks(getCurrentStructureRoot(), props, simTime, getCurrentStructureType()));
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
     * @return Root-Knoten der erstellten Struktur
     */
    protected abstract MirrorNode buildStructure(int totalNodes);

    /**
     * Fügt Knoten zu einer bestehenden Struktur hinzu.
     * Muss von Subklassen für spezifische Strukturlogik implementiert werden.
     *
     * @param nodesToAdd Mirrors der hinzuzufügenden Knoten
     * @return Anzahl der tatsächlich hinzugefügten Knoten
     */
    protected abstract int addNodesToStructure(Set<Mirror> nodesToAdd);

    /**
     * Entfernt Knoten aus einer bestehenden Struktur.
     * Schaltet Mirror ab, die nicht mehr im Plan vorhanden sind.
     * Kann von Subklassen überschrieben werden.
     *
     * @param totalNodes Mirrors der zu entfernenden Knoten
     * @return Anzahl der tatsächlich entfernten Knoten
     */
    protected Set<MirrorNode> removeNodesFromStructure(int totalNodes){
        // ===== PHASE 1: PLANUNGSEBENE - Structure Nodes nach höchsten IDs entkoppeln =====
        // 1.1. Alle aktuellen Structure Nodes sammeln und nach ID sortieren (höchste zuerst)
        List<MirrorNode> allStructureNodes = getAllStructureNodes()
                .stream()
                .sorted((node1, node2) -> Integer.compare(node2.getId(), node1.getId())) // Absteigende Sortierung
                .toList();

        // 1.2. Zu entfernende Knoten basierend auf höchsten IDs auswählen
        int actualNodesToRemove = Math.min(totalNodes, allStructureNodes.size());
        Set<MirrorNode> outSet = new HashSet<>(allStructureNodes.subList(0, actualNodesToRemove));

        // **NUR STRUKTURPLANUNG**: Entferne bidirektionale StructureNode-Verbindungen
        for (MirrorNode nodeToRemove : outSet) {
            removeNodeFromStructuralPlanning(nodeToRemove,
                    Set.of(StructureNode.StructureType.DEFAULT,StructureNode.StructureType.MIRROR));
        }

        return outSet;
    }


    /**
     * **PLANUNGSEBENE**: Entfernt Knoten aus struktureller Planung.
     * Arbeitet ohne Zeitbezug - nur strukturelle Änderungen.
     * KORRIGIERT: Verwendet die BuildAsSubstructure-API anstatt direkter Collection-Modifikation
     *
     * @param nodeToRemove Der zu entfernende Knoten
     * @param types Identifizierung eines Strukturtypen, der nicht überlagert
     */
    protected void removeNodeFromStructuralPlanning(MirrorNode nodeToRemove, Set<StructureNode.StructureType> types) {
        // **NUR STRUKTURPLANUNG**: Entferne bidirektionale StructureNode-Verbindungen
        Set<StructureNode.StructureType> nodeToRemoveTypes = new HashSet<>(nodeToRemove.getNodeTypes());
        if(!nodeToRemoveTypes.containsAll(types))return;

        for(MirrorNode connectedNode : getAllStructureNodes()){
            Set<StructureNode.StructureType> connectedNodeTypes = new HashSet<>(connectedNode.getNodeTypes());
            if(!connectedNodeTypes.containsAll(types))continue;

            // entweder die zu Entfernende oder die zu prüfende node kann bei Überlagerung ein Subset der anderen node sein
            // Randfall: beide nodes haben keine Überlagerungen über die angefragten types hinaus
            // Randfall2: beide nodes sind Überlagerungen und bereinigen gegenseitig nur die Typeneinträge

            //boolean nodeToRemoveHasConnectedNodeSubset = !nodeToRemoveTypes.containsAll(connectedNodeTypes);
            //boolean connectedNodeHasNodeToRemoveSubset = !connectedNodeTypes.containsAll(nodeToRemoveTypes);

            // Beide nodes oder eine von beiden, haben je ein gemeinsames verbundenes Struktur Subset und können getrennt werden
            Set<StructureNode.StructureType> surplusTypesInConnectedNode = new HashSet<>(connectedNodeTypes);
            surplusTypesInConnectedNode.removeAll(types);
            Set<StructureNode.StructureType> surplusTypesInNodeToRemove = new HashSet<>(nodeToRemoveTypes);
            surplusTypesInNodeToRemove.removeAll(types);

            if(surplusTypesInConnectedNode.stream().anyMatch(type -> !surplusTypesInNodeToRemove.contains(type))
            && surplusTypesInNodeToRemove.stream().anyMatch(type -> !surplusTypesInConnectedNode.contains(type))){
                // Fremde symmetrische Verbindung, daher nur Klassen-Struktur-Typen aus der zu entfernenden node entfernen.
                // Aus dieser Struktur entfernen, aber nicht trennen
                Set<StructureNode.StructureType> deleteSet = new HashSet<>(types);
                deleteSet.remove(StructureNode.StructureType.DEFAULT);
                deleteSet.remove(StructureNode.StructureType.MIRROR);

                nodeToRemoveTypes.removeAll(deleteSet);
            }
            else{
                // Entferne nodeToRemove aus den Kindern von connectedNode
                connectedNode.removeChild(nodeToRemove);
                // Entferne connectedNode aus den Kindern von nodeToRemove
                nodeToRemove.removeChild(connectedNode);

                // Parent-Verbindung nur für nodeToRemove trennen (falls vorhanden)
                if (nodeToRemove.getParent() != null) {
                    nodeToRemove.getParent().removeChild(nodeToRemove);
                    nodeToRemove.setParent(null);
                }
            }

        }
        // 2. Entferne aus BuildAsSubstructure-Verwaltung
        removeFromStructureNodes(nodeToRemove);
    }

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

    /*
     * Verschmilzt die root Node einer unterzuordnenden Struktur mit einer gewünschten host node
     * Setzt für eingehende zu verbindende Struktur/deren nodes zusätzlich den Typeintrag dieser Struktur
     * Ersetzt die hostSubstructureNode mit der Root der externen Struktur
     */
    protected final void connectToStructureNodes(MirrorNode hostSubstructureNode, BuildAsSubstructure buildExtern) {
        if(hostSubstructureNode == null || buildExtern == null)return;
        // Bestimme die extern root aus ihrer externen Struktur
        MirrorNode externRoot = buildExtern.getCurrentStructureRoot();
        if(externRoot == null)return;

        boolean setFirstStructure = false;
        // Ist unsere Struktur Topologie noch leer gehen wir in der ersten Verbindung eine Identität mit der ersten fremden Struktur ein
        if(!getAllStructureNodes().contains(hostSubstructureNode) && getAllStructureNodes().isEmpty()){
            setCurrentStructureRoot(externRoot);
            setFirstStructure = true;
        }
        if(!getAllStructureNodes().contains(hostSubstructureNode)){
            throw new IllegalArgumentException("Host Substructure Node is not part of the current network structure!");
        }

        // get extern all nodes
        Set<MirrorNode> externStructureAllNodes = buildExtern.getAllStructureNodes();
        // Setze den Typen dieser Struktur in die neu anzugliedernde Struktur und deren Nodes ein, die nun an dieser Strukutr teilnimmt
        externStructureAllNodes.forEach(node -> node.addNodeType(getCurrentStructureType()));
        // Update aller Abhängigkeiten der Parents gegenüber der Strukturtypen ihrer Kinder
        externStructureAllNodes.forEach(
            node -> node.updateChildRecordMergeStructureHead(
                Map.of(
                        getCurrentStructureType(),getCurrentStructureRoot().getId(),
                        buildExtern.getCurrentStructureType(),externRoot.getId()
                ),
                    node.getChildren()
            )
        );

        if(setFirstStructure){
            // Setze und ergänze die Strukturtypen in die host node, falls hostSubstructureNode und neue buildExtern root nicht identisch sind
            // damit nun alle notwendigen überlappenden Typen aufgeführt werden
            // externRootNodeStructureTypes.forEach(hostSubstructureNode::addNodeType);

            // Setze die fehlenden Typen der hostSubstructureNode in die externe Root ein
            externRoot.setNodeTypes(hostSubstructureNode.getNodeTypes());

            // Setzte Kinder der external Root mit den Kindern der hostSubstructureNode
            hostSubstructureNode.getChildren().forEach(externRoot::addChild);

            // don't remove root from the external structure since it is still a complete node block

            // Ersetze alle Kinder dieser Struktur von hostSubstructureNode nach externRoot und lösche hostSubstructureNode aus der Strukutur
            structureNodes.stream()
                    .filter(node -> node.getChildren().contains(hostSubstructureNode))
                    .forEach(node -> {
                        node.removeChild(hostSubstructureNode);
                        node.addChild(externRoot);
                    });
            externRoot.setParent(hostSubstructureNode.getParent());
        }
        else{
            hostSubstructureNode.addChild(externRoot);
            hostSubstructureNode.updateChildRecordMergeStructureHead(
                    Map.of(
                            getCurrentStructureType(),getCurrentStructureRoot().getId(),
                            buildExtern.getCurrentStructureType(),externRoot.getId()
                    ),
                    Set.of(externRoot)
            );
        }

        // merge intern build root as root for external structure
        nodeToSubstructure.put(externRoot,buildExtern);
        // add all external nodes also to this structure
        structureNodes.addAll(externStructureAllNodes);
    }

    /*
     * Trennt eine host node nach Eingabe der betroffenen Struktur wieder in die betreffende Struktur root node und der ursprüngliche Host node auf
     * Gibt neu gesetzte und nun separate Wurzel einer eingehenden Struktur aus.
     */
    protected final MirrorNode disconnectFromStructureNodes(MirrorNode hostSubstructureNode, BuildAsSubstructure buildExtern) {
        if(buildExtern.getCurrentStructureRoot() != hostSubstructureNode){
            throw new IllegalArgumentException("Host Substructure Node is not part of the current network structure!");
        }
        if(!nodeToSubstructure.containsValue(buildExtern)){
            throw new IllegalArgumentException("The given buildAsSubstructure is not part of the current network structure!");
        }

        // Analysiere die externe Struktur und lösche alle ihre Struktur nodes aus der ganzheitlichen Struktur, außer die Root node
        List<MirrorNode> externStructureAllNodes = buildExtern.getAllStructureNodes().stream()
                .sorted(Comparator.comparingInt(MirrorNode::getId)).toList();

        // Entferne alle StructureNodes von unserer Struktur, nodes verbleiben nur in der externen Struktur.
        // Entferne aber nur, wenn die node nicht in einer anderen Substruktur unserer Struktur teilnimmt
        externStructureAllNodes.stream()
                .filter(node -> !nodeToSubstructure.containsKey(node))
                .forEach(this::removeFromStructureNodes);

        // Entferne den Strukturtypen dieser Struktur aus der externen Struktur
        externStructureAllNodes.forEach(node -> node.removeNodeType(getCurrentStructureType()));
        // Entferne alle übrigen Kinder aus den nodes der externen Struktur
        StructureNode.StructureType externType = buildExtern.getCurrentStructureType();

        List<Map.Entry<MirrorNode, StructureNode>> toRemove = externStructureAllNodes.stream()
                // baue Paare (parent, child)
                .flatMap(parent -> parent.getChildren().stream()
                        .map(child -> Map.entry(parent, child)))
                // Filter: ChildRecord fehlt ODER hat den externen Typ nicht
                .filter(entry -> {
                    StructureNode parent = entry.getKey();
                    StructureNode child  = entry.getValue();
                    StructureNode.ChildRecord rec = parent.findChildRecordById(child.getId());
                    return rec == null || !rec.hasType(externType);
                })
                .toList();

        // Entfernen in separater Schleife (keine ConcurrentModification), nur nodes die eindeutig extern sind
        toRemove.stream()
                .filter(node -> !nodeToSubstructure.containsKey(node.getKey()))
                .forEach(entry -> entry.getKey().removeChild(entry.getValue()));

        // ChildRecords der nodes in der zu entfernenden Struktur für alle nodes gleichzeitig säubern
        // die root node der entfernten Struktur, steht keine der anderen Substrukturen mehr zur Verfügung, auch hier
        Set<StructureNode> externalStructureSet = new HashSet<>(externStructureAllNodes);
        externStructureAllNodes
                .forEach(node -> node.updateChildRecordRemoveStructureHead(
                        Set.of(getCurrentStructureType()),externalStructureSet)
                );

        return buildExtern.getCurrentStructureRoot();
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
     * **AUSFÜHRUNGSEBENE**: Entfernt Mirrors aus dem Netzwerk mit moderner 3-Phasen-Architektur.
     * <p>
     * **PHASE 1 - PLANUNGSEBENE**: Structure Nodes mit höchsten IDs zuerst entkoppeln
     * **PHASE 2 - LINK-UPDATE**: Links über buildAndUpdateLinks komplett neu aufbauen
     * **PHASE 3 - MIRROR-SHUTDOWN**: Unverbundene Mirrors herunterfahren und sammeln
     * <p>
     * Arbeitet komplementär zu removeNodesFromStructure (Planungsebene).
     * Überschreibt komplexe Cleanup-Logik durch direkte Link-Neuerstellung.
     *
     * @param n             Das Netzwerk
     * @param removeMirrors Anzahl zu entfernender Mirrors
     * @param props         Properties der Simulation
     * @param simTime       Aktuelle Simulationszeit
     */
    @Override
    public void handleRemoveMirrors(Network n, int removeMirrors, Properties props, int simTime) {
        if (removeMirrors <= 0 || getCurrentStructureRoot() == null) {
            return; // Keine Entfernung erforderlich
        }

        initializeInternalState(n);

        // 1.3. Structure Nodes auf Planungsebene entkoppeln (removeNodesFromStructure)
        Set<MirrorNode> removePlanningMirrorNodes = removeNodesFromStructure(removeMirrors);

        Set<Mirror> shutdownMirrors = new HashSet<>();
        removePlanningMirrorNodes.forEach(node -> shutdownMirrors.add(node.getMirror()));

        // ===== PHASE 2: LINK-UPDATE - komplette Link-Neuerstellung =====

        // 2.1. Alle bestehenden Links über buildAndUpdateLinks neu aufbauen
        n.getLinks().addAll(buildAndUpdateLinks(getCurrentStructureRoot(), props, simTime, getCurrentStructureType()));

        // 3.5. Root-Update falls Root-Mirror heruntergefahren wurde
        updateRootAfterMirrorShutdown(shutdownMirrors);
    }

    /**
     * Hilfsmethode: Aktualisiert die Root-Node falls das Root-Mirror heruntergefahren wurde.
     *
     * @param shutdownMirrors Set der heruntergefahrenen Mirrors
     */
    private void updateRootAfterMirrorShutdown(Set<Mirror> shutdownMirrors) {
        if (getCurrentStructureRoot() != null) {
            Mirror rootMirror = getCurrentStructureRoot().getMirror();

            // Prüfen, ob Root-Mirror heruntergefahren wurde
            if (rootMirror != null && shutdownMirrors.contains(rootMirror)) {
                // Neue Root aus verbleibenden Structure Nodes wählen
                Set<MirrorNode> remainingNodes = getAllStructureNodes();
                if (!remainingNodes.isEmpty()) {
                    // Knoten mit niedrigster ID als neue Root wählen
                    MirrorNode newRoot = remainingNodes.stream()
                            .filter(mirrorNode -> mirrorNode.getMirror().isUsableForNetwork())
                            .min(Comparator.comparing(MirrorNode::getId))
                            .orElse(null);
                    setCurrentStructureRoot(newRoot);
                } else {
                    // Keine Knoten mehr vorhanden - Root auf null setzen
                    setCurrentStructureRoot(null);
                }
            }
        }
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
     * Prüft, ob ein Mirror zu einem Knoten in unserer Struktur-Hierarchie gehört.
     */
    private boolean isMirrorInHierarchy(Mirror mirror, Set<MirrorNode> validNodes) {
        return validNodes.stream()
                .anyMatch(node -> node.getMirror() == mirror);
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
     * Initialisiert den internen Zustand für ein Netzwerk.
     */
    public void initializeInternalState(Network n) {
        this.network = n;
    }

    /**
     * Setzt nur die StructureNode-Struktur zurück, nicht die Mirror-Links.
     * Wird verwendet, wenn die Mirror-Links bereits durch TopologyStrategy.restartNetwork() gelöscht wurden.
     */
    public void resetInternalStateStructureOnly() {
        nodeToSubstructure.clear();
        structureNodes.clear();
        currentStructureRoot = null;
    }

    // ===== ABSTRAKTE METHODEN FÜR LINK-ERSTELLUNG =====

    /**
     * Baut die tatsächlichen Links zwischen den Mirrors basierend auf der StructureNode-Struktur auf.
     * Erstellt für jede StructureNode-Verbindung einen entsprechenden Mirror-Link.
     * Validiert die Typkompatibilität zwischen Root-Node und StructureType.
     *
     * @param root Die Root-Node der Struktur
     * @param props Simulation Properties
     * @param simTime Zeitpunkt der Simulation
     * @param structureType Der erwartete StructureType für Validierung
     * @return Set aller erstellten Links
     * @throws IllegalStateException Wenn Root-Node-Typ nicht mit StructureType kompatibel ist
     */

    protected Set<Link> buildAndUpdateLinks(MirrorNode root, Properties props, int simTime, StructureNode.StructureType structureType) {
        // **TYP-KOMPATIBILITÄT VALIDIEREN**: Root-Node-Typ muss mit StructureType kompatibel sein
        validateNodeTypeCompatibility(root, structureType);
        // Sammle alle Knoten in der Struktur (generisch für alle Node-Typen)
        List<MirrorNode> nodeList = root.getAllNodesInStructure(structureType, root)
                .stream()
                .filter(node -> node instanceof MirrorNode)
                .map(node -> (MirrorNode) node)
                .distinct()
                .sorted(Comparator.comparingInt(MirrorNode::getId))
                .toList();

        Set<Link> allLinks = new HashSet<>();
        // Erstelle Links zwischen allen Paaren von Knoten
        for (MirrorNode node1 : nodeList) {
            for (MirrorNode node2 : nodeList) {
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
                        Link link = new Link(IDGenerator.getInstance().getNextID(), node1.getMirror(), node2.getMirror(),
                                simTime, props);
                        node1.getMirror().addLink(link);
                        node2.getMirror().addLink(link);
                        allLinks.add(link);
                    } else {
                        if (node21_connect) {
                            Link link = new Link(IDGenerator.getInstance().getNextID(), node2.getMirror(), node1.getMirror(),
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
                        Set<Link> links1 = node1.getMirror().getJointMirrorLinks(node2.getMirror());
                        for(Link link1:links1){
                            link1.shutdown();
                            allLinks.add(link1);
                        }

                        Set<Link> links2 = node2.getMirror().getJointMirrorLinks(node1.getMirror());
                        for(Link link2:links2){
                            link2.shutdown();
                            allLinks.add(link2);
                        }
                    }
                }
            }
        }

        // ===== PHASE 3: MIRROR-SHUTDOWN - unverbundene Mirrors sammeln und herunterfahren =====

        // 3.1. Mirrors prüfen die entkoppelten Knoten und herunterfahren, um Erkennung der neuen Wurzel zu ermöglichen
        network.getMirrors()
                .stream()
                .filter(mirror -> nodeList.stream().noneMatch(node -> node.getMirror() == mirror))
                .forEach(mirror -> mirror.shutdown(simTime));

        // alle Links müssen immer bekannt sein, um automatisch vom Netzwerk bereinigt zu werden (herunterfahren/crash)
        if(network != null && network.getLinks() != null) {
            network.getLinks().addAll(allLinks);
        }

        // Validiere die erweiterte Struktur
        if(!validateTopology()){
            throw new IllegalStateException("The constructed topology is not valid!");
        }

        return allLinks;
    }

    /**
     * Validiert, ob der gegebene Root-Node-Typ mit dem erwarteten StructureType kompatibel ist.
     * Wirft eine IllegalStateException bei Inkompatibilität.
     *
     * @param root Der Root-MirrorNode
     * @param expectedStructureType Der erwartete StructureType
     * @throws IllegalStateException wenn die Typen nicht kompatibel sind
     */
    private void validateNodeTypeCompatibility(MirrorNode root, StructureNode.StructureType expectedStructureType) {
        // Sammle alle Strukturtypen des Root-Nodes
        Set<StructureNode.StructureType> rootNodeTypes = root.getNodeTypes();

        // Prüfe, ob der erwartete StructureType in den Node-Typen enthalten ist
        if (!rootNodeTypes.contains(expectedStructureType)) {
            String nodeTypesString = rootNodeTypes.stream()
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));

            throw new IllegalStateException(
                    String.format("Node-Typ-Inkompatibilität: Root-Node (ID: %d, Klasse: %s) hat Typen [%s], " +
                                    "aber StructureType %s wurde erwartet",
                            root.getId(),
                            root.getClass().getSimpleName(),
                            nodeTypesString,
                            expectedStructureType.name()));
        }
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
     * Erstellt einen neuen MirrorNode mit Mirror aus dem Iterator.
     * AKTUALISIERT: Fügt den Knoten automatisch zu structureNodes hinzu.
     *
     * @return Neuer MirrorNode mit zugeordnetem Mirror oder null
     */
    protected MirrorNode getMirrorNodeFromIterator() {
        if (network.getMirrorCursor().hasNextMirror()) {
            Mirror mirror = network.getMirrorCursor().getNextMirror();
            MirrorNode node = createMirrorNodeForMirror(mirror);
            if (node != null) {
                node.addNodeType(StructureNode.StructureType.MIRROR);
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
        return new MirrorNode(IDGenerator.getInstance().getNextID(), mirror);
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
