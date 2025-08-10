package org.lrdm.topologies.strategies;

import org.lrdm.topologies.node.*;
import org.lrdm.Mirror;
import org.lrdm.Network;

import java.util.*;

/**
 * Eine spezialisierte {@link TreeTopologyStrategy}, die Mirrors als balancierten Baum mit einer
 * einzelnen Root verknüpft. Jeder Mirror hat maximal {@link Network#getNumTargetLinksPerMirror()} Kinder.
 * <p>
 * **Balance-Eigenschaften**:
 * - Erweitert TreeTopologyStrategy um Balance-Optimierung
 * - Breadth-First-Aufbau für gleichmäßige Tiefenverteilung
 * - Konfigurierbare maximale Abweichung der Balance
 * - Verwendet {@link BalancedTreeMirrorNode} für Balance-spezifische Funktionalität
 * <p>
 * **Wiederverwendung von TreeTopologyStrategy**:
 * - Alle TopologyStrategy-Interface-Methoden werden vererbt
 * - Grundlegende Baum-Logik wird wiederverwendet
 * - Nur Balance-spezifische Methoden werden überschrieben
 * <p>
 * **Planungsebene vs. Ausführungsebene**:
 * - Planungsebene: `removeNodesFromStructure()` - plant strukturelle Änderungen ohne Zeitbezug
 * - Ausführungsebene: `handleRemoveMirrors()` - führt Mirror-Shutdown innerhalb der Planungsgrenzen aus
 * - Automatisches Mitwachsen: MirrorNode-Ebene passt sich an StructureNode-Planung an
 * <p>
 * **Unbegrenztes Baumwachstum**: Im Gegensatz zu statischen Strukturen (Quadrate, Ringe)
 * können Bäume dynamisch in alle Richtungen wachsen ohne strukturelle Limitierungen.
 *
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 */
public class BalancedTreeTopologyStrategy extends TreeTopologyStrategy {

    // ===== BALANCE-SPEZIFISCHE KONFIGURATION =====

    private double maxAllowedBalanceDeviation = 1.0;

    // ===== KONSTRUKTOREN =====

    public BalancedTreeTopologyStrategy() {
        super();
    }

    public BalancedTreeTopologyStrategy(double maxAllowedBalanceDeviation) {
        super();
        this.maxAllowedBalanceDeviation = Math.max(0.1, maxAllowedBalanceDeviation);
    }

    // ===== ÜBERSCHREIBUNG DER BUILD-AS-SUBSTRUCTURE-METHODEN =====

    /**
     * **PLANUNGSEBENE**: Erstellt die balancierte Baum-Struktur mit optimaler Verteilung.
     * Überschreibt TreeTopologyStrategy für Balance-spezifische Logik.
     * Ermöglicht unbegrenztes Baumwachstum ohne strukturelle Einschränkungen.
     *
     * @param totalNodes Anzahl der zu erstellenden Knoten
     * @return Die Root-Node der erstellten balancierten Baum-Struktur
     */
    @Override
    protected MirrorNode buildStructure(int totalNodes) {
        if (totalNodes <= 0) return null;

        // Erstelle Root-Node als BalancedTreeMirrorNode
        BalancedTreeMirrorNode root = getNodeFromIterator();
        if (root == null) return null;

        setCurrentStructureRoot(root);
        List<BalancedTreeMirrorNode> remainingNodes = new ArrayList<>();

        // Erstelle alle weiteren Knoten
        for (int i = 1; i < totalNodes; i++) {
            BalancedTreeMirrorNode node = getNodeFromIterator();
            if (node != null) {
                remainingNodes.add(node);
            }
        }

        // Baue balancierte Struktur mit Breadth-First-Ansatz
        root.setHead(StructureNode.StructureType.BALANCED_TREE, true);
        buildBalancedTreeStructureOnly(root, remainingNodes);

        return root;
    }

    /**
     * **PLANUNGSEBENE**: Fügt neue Knoten zur balancierten Struktur hinzu.
     * Überschreibt TreeTopologyStrategy für Balance-spezifische Einfügung.
     * Verwendet Breadth-First-Einfügung für optimale Balance-Erhaltung.
     *
     * @param nodesToAdd Set der hinzuzufügenden Mirrors
     * @return Tatsächliche Anzahl der hinzugefügten Knoten
     */
    @Override
    protected int addNodesToStructure(Set<Mirror> nodesToAdd) {
        if (nodesToAdd == null || nodesToAdd.isEmpty()) return 0;

        MirrorNode currentRoot = getCurrentStructureRoot();
        if (!(currentRoot instanceof BalancedTreeMirrorNode)) {
            return 0;
        }

        int addedCount = 0;

        // Sammle alle verfügbaren Einfüge-Punkte (Balance-optimiert)
        List<BalancedTreeMirrorNode> insertionCandidates = findBalancedInsertionCandidates((BalancedTreeMirrorNode) currentRoot);

        for (int i = 0; i < nodesToAdd.size(); i++) {
            if (insertionCandidates.isEmpty()) break;

            // Erstelle neuen BalancedTreeMirrorNode
            BalancedTreeMirrorNode newNode = getNodeFromIterator();

            // Finde besten Einfüge-Punkt basierend auf Balance
            BalancedTreeMirrorNode bestParent = insertionCandidates.get(0);

            // Verbinde auf StructureNode-Ebene
            bestParent.addChild(newNode);

            // Füge zu strukturNodes hinzu
            addToStructureNodes(newNode);
            addedCount++;

            // Aktualisiere Kandidatenliste
            insertionCandidates = findBalancedInsertionCandidates((BalancedTreeMirrorNode) currentRoot);
        }

        return addedCount;
    }


    /**
     * **PLANUNGSEBENE**: Entfernt Knoten aus der balancierten Baum-Struktur.
     * Überschreibt TreeTopologyStrategy für Balance-spezifische Entfernung.
     * Verwendet dynamische Kandidaten-Aktualisierung für optimale Balance-Erhaltung.
     *
     * @param nodesToRemove Anzahl der zu entfernenden Knoten
     * @return Set der tatsächlich entfernten Knoten
     */
    @Override
    protected Set<MirrorNode> removeNodesFromStructure(int nodesToRemove) {
        Set<MirrorNode> removedNodes = new HashSet<>();
        if (nodesToRemove <= 0) return removedNodes;

        MirrorNode currentRoot = getCurrentStructureRoot();
        if (!(currentRoot instanceof BalancedTreeMirrorNode balancedRoot)) {
            return removedNodes;
        }

        StructureNode.StructureType typeId = currentRoot.deriveTypeId();

        // Dynamische Entfernung mit Kandidaten-Aktualisierung
        for (int i = 0; i < nodesToRemove; i++) {
            // Aktualisierte Kandidatenliste nach jeder Entfernung
            List<BalancedTreeMirrorNode> candidates = findBalancedRemovalCandidates(balancedRoot);

            // *** HINZUFÜGEN: Fallback wenn keine Balance-optimierten Kandidaten verfügbar ***
            if (candidates.isEmpty()) {
                candidates = findFallbackRemovalCandidates(balancedRoot);
            }

            if (candidates.isEmpty()) {
                break; // Keine entfernbaren Knoten mehr
            }

            BalancedTreeMirrorNode nodeToRemove = candidates.get(0); // Bester Kandidat

            // *** KRITISCH: Alle strukturellen Änderungen direkt in Hauptfunktion! ***

            // 1. Sammle Kinder vor Entfernung
            Set<StructureNode> children = new HashSet<>(nodeToRemove.getChildren(typeId));
            StructureNode parent = nodeToRemove.getParent();

            // 2. Entferne aus Parent
            if (parent != null) {
                parent.removeChild(nodeToRemove, Set.of(typeId));
            }

            // 3. Balance-optimierte Redistribution der Kinder
            if (parent instanceof BalancedTreeMirrorNode balancedParent && !children.isEmpty()) {
                redistributeChildrenForOptimalBalance(children, balancedParent, balancedRoot, typeId);
            }

            // *** KRITISCH: Aus BuildAsSubstructure structureNodes entfernen! ***
            removeFromStructureNodes(nodeToRemove);

            removedNodes.add(nodeToRemove);

            // *** KRITISCH: Balance-Map nach jeder strukturellen Änderung aktualisieren! ***
            balancedRoot.updateBalanceMap();
        }

        return removedNodes;
    }

    // ===== BALANCE-SPEZIFISCHE HILFSMETHODEN =====

    /**
     * Baut die balancierte Baum-Struktur mit Breadth-First-Ansatz auf.
     * NUR StructureNode-Ebene - keine Mirror-Links!
     *
     * @param root           Root-Node der Struktur
     * @param remainingNodes Liste der noch zu verbindenden Knoten
     */
    private void buildBalancedTreeStructureOnly(BalancedTreeMirrorNode root, List<BalancedTreeMirrorNode> remainingNodes) {
        if (remainingNodes.isEmpty()) return;

        int iterations = 0;
        int maxIterations = remainingNodes.size() * 2; // Sicherheitslimit

        StructureNode.StructureType typeId = root.deriveTypeId();
        List<BalancedTreeMirrorNode> queue = new LinkedList<>();
        queue.add(root);

        Iterator<BalancedTreeMirrorNode> nodeIterator = remainingNodes.iterator();

        // Breadth-First-Aufbau für optimale Balance
        while (!queue.isEmpty() && nodeIterator.hasNext() && iterations < maxIterations) {
            iterations++;
            queue.sort(this::compareInsertionCandidates);
            BalancedTreeMirrorNode current = queue.get(0);
            queue.remove(current);

            // Füge Kinder bis zur Kapazität hinzu
            int currentChildren = current.getChildren(typeId).size();
            int maxChildren = Math.max(0, network.getNumTargetLinksPerMirror() - currentChildren);

            for (int i = 0; i < maxChildren && nodeIterator.hasNext(); i++) {
                BalancedTreeMirrorNode child = nodeIterator.next();
                current.addChild(child);
                addToStructureNodes(child); // Aktiv zu BuildAsSubstructure hinzufügen
                queue.add(child); // Für nächste Ebene
            }
        }
    }


    /**
     * Findet Balance-optimierte Einfüge-Punkte in der bestehenden Struktur.
     * Berücksichtigt targetLinksPerNode und maxAllowedBalanceDeviation für strikte Balance-Einhaltung.
     * Sammelt nur Kandidaten, die diese Balance-Grenzen respektieren.
     * <p>
     * WICHTIG: Umgeht isValidStructure-Prüfung in canAcceptMoreChildren(),
     * da auf Planungsebene noch keine Mirror-Zuordnung existiert.
     *
     * @param root Root der Struktur
     * @return Sortierte Liste der besten Balance-konformen Einfüge-Punkte
     */
    private List<BalancedTreeMirrorNode> findBalancedInsertionCandidates(BalancedTreeMirrorNode root) {
        List<BalancedTreeMirrorNode> candidates = new ArrayList<>();
        StructureNode.StructureType typeId = root.deriveTypeId();
        Set<StructureNode> allNodes = root.getAllNodesInStructure(typeId, root);

        // Berechne aktuelle Baum-Balance über Node-Methode
        double currentTreeBalance = root.calculateTreeBalance();

        for (StructureNode node : allNodes) {
            if (node instanceof BalancedTreeMirrorNode balancedNode) {

                // 1. PLANUNGSEBENE: Direkte Kinder-Zählung ohne isValidStructure-Prüfung
                int currentChildren = balancedNode.getChildren(typeId).size();

                // 2. Target-Links-Prüfung: Respektiert der Knoten die targetLinksPerNode-Grenze?
                if (currentChildren >= network.getNumTargetLinksPerMirror()) {
                    // Knoten hat bereits das Target erreicht oder überschritten
                    continue;
                }

                // 3. Strukturelle Basis-Prüfung: Kann der Knoten theoretisch mehr Kinder haben?
                // Umgeht canAcceptMoreChildren() wegen isValidStructure-Problem
                if (currentChildren >= balancedNode.getMaxChildren()) {
                    // Knoten hat bereits die absolute maximale Anzahl erreicht
                    continue;
                }

                // 4. Balance-Deviation-Prüfung: Würde die Einfügung die erlaubte Abweichung überschreiten?
                if (!wouldInsertionRespectBalanceDeviation(balancedNode, root, typeId, currentTreeBalance)) {
                    continue;
                }

                // 5. Balance-Impact-Prüfung: Würde die Einfügung die Gesamtbalance verbessern oder neutral bleiben?
                double insertionImpact = calculateInsertionBalanceImpact(balancedNode, root, typeId);
                if (insertionImpact > maxAllowedBalanceDeviation) {
                    // Einfügung würde Balance zu stark verschlechtern
                    continue;
                }

                // Knoten ist ein gültiger Balance-konformer Kandidat
                candidates.add(balancedNode);
            }
        }

        // Sortiere nach Balance-Optimierung (beste Balance-Verbesserung zuerst)
        candidates.sort(this::compareInsertionCandidates);
        return candidates;
    }


    /**
     * Prüft, ob die Einfügung eines Kindes an diesem Knoten die erlaubte Balance-Abweichung respektiert.
     * Simuliert die Einfügung und prüft sowohl lokale als auch globale Balance-Auswirkungen.
     *
     * @param candidate          Der Kandidat für die Einfügung
     * @param root               Root der Struktur
     * @param typeId             Struktur-Typ-ID
     * @param currentTreeBalance Aktuelle Baum-Balance
     * @return true, wenn die Balance-Abweichung respektiert wird
     */
    private boolean wouldInsertionRespectBalanceDeviation(BalancedTreeMirrorNode candidate,
                                                          BalancedTreeMirrorNode root,
                                                          StructureNode.StructureType typeId,
                                                          double currentTreeBalance) {
        int currentChildren = candidate.getChildren(typeId).size();
        int childrenAfterInsertion = currentChildren + 1;

        // 1. Lokale Balance-Abweichung prüfen
        double localDeviation = Math.abs(childrenAfterInsertion - network.getNumTargetLinksPerMirror()) / (double) network.getNumTargetLinksPerMirror();
        if (localDeviation > maxAllowedBalanceDeviation) {
            return false;
        }

        // 2. Globale Balance-Auswirkung simulieren (vereinfacht)
        double simulatedTreeBalance = simulateInsertionBalance(candidate, root, typeId, currentTreeBalance);
        double balanceChange = Math.abs(simulatedTreeBalance - currentTreeBalance);

        // Prüfe, ob die globale Balance-Änderung akzeptabel ist
        return balanceChange <= maxAllowedBalanceDeviation;
    }

    /**
     * Simuliert die Balance-Auswirkung einer Einfügung an einem Kandidaten.
     *
     * @param candidate      Der Einfüge-Kandidat
     * @param root           Root der Struktur
     * @param typeId         Struktur-Typ-ID
     * @param currentBalance Aktuelle Baum-Balance (von root.calculateTreeBalance())
     * @return Simulierte Balance nach der Einfügung
     */
    private double simulateInsertionBalance(BalancedTreeMirrorNode candidate,
                                            BalancedTreeMirrorNode root,
                                            StructureNode.StructureType typeId,
                                            double currentBalance) {
        try {
            // Einfache Simulation: Berechne Balance-Änderung basierend auf lokaler Abweichung
            int currentChildren = candidate.getChildren(typeId).size();
            int childrenAfterInsertion = currentChildren + 1;

            // Berechne lokale Abweichungsänderung
            double currentLocalDeviation = Math.abs(currentChildren - network.getNumTargetLinksPerMirror()) / (double) network.getNumTargetLinksPerMirror();
            double newLocalDeviation = Math.abs(childrenAfterInsertion - network.getNumTargetLinksPerMirror()) / (double) network.getNumTargetLinksPerMirror();
            double localDeviationChange = newLocalDeviation - currentLocalDeviation;

            // Schätze globale Auswirkung (vereinfacht)
            Set<StructureNode> allNodes = root.getAllNodesInStructure(typeId, root);
            double weightFactor = 1.0 / allNodes.size(); // Gewichtung basierend auf Knotenzahl

            return currentBalance + (localDeviationChange * weightFactor);

        } catch (Exception e) {
            // Bei Fehlern: Konservative Schätzung (keine Verschlechterung)
            return currentBalance;
        }
    }

    /**
     * Berechnet die Balance-Auswirkung einer Einfügung an einem Kandidaten.
     * Niedrigere Werte bedeuten bessere Kandidaten (weniger negative Auswirkung auf Balance).
     *
     * @param candidate Der Einfüge-Kandidat
     * @param root      Root der Struktur
     * @param typeId    Struktur-Typ-ID
     * @return Balance-Impact-Wert (niedrigere Werte = bessere Kandidaten)
     */
    private double calculateInsertionBalanceImpact(BalancedTreeMirrorNode candidate,
                                                   BalancedTreeMirrorNode root,
                                                   StructureNode.StructureType typeId) {
        try {
            int currentChildren = candidate.getChildren(typeId).size();
            int childrenAfterInsertion = currentChildren + 1;

            // 1. Berechne lokale Balance-Verbesserung/Verschlechterung
            double currentDeviation = Math.abs(currentChildren - network.getNumTargetLinksPerMirror());
            double newDeviation = Math.abs(childrenAfterInsertion - network.getNumTargetLinksPerMirror());
            double localImpact = newDeviation - currentDeviation;

            // 2. Berücksichtige Tiefe (flachere Einfügungen sind besser für Balance)
            int depth = candidate.getDepthInTree();
            double depthPenalty = depth * 0.1; // Kleine Strafe für tiefere Einfügungen

            // 3. Berücksichtige Nähe zum Optimal-Wert
            double optimalityBonus = 0.0;
            if (childrenAfterInsertion == network.getNumTargetLinksPerMirror()) {
                optimalityBonus = -0.5; // Bonus für Erreichen des Optimal-Werts
            } else if (currentChildren < network.getNumTargetLinksPerMirror() && childrenAfterInsertion <= network.getNumTargetLinksPerMirror()) {
                optimalityBonus = -0.2; // Bonus für Annäherung an Optimal-Wert
            }

            return localImpact + depthPenalty + optimalityBonus;

        } catch (Exception e) {
            // Bei Fehlern: hoher Impact (schlechter Kandidat)
            return Double.MAX_VALUE;
        }
    }

    /**
     * Vergleicht zwei Einfügekandidaten basierend auf Balance-Kriterien.
     * Bevorzugt Kandidaten, die die Balance am besten respektieren und verbessern.
     *
     * @param candidate1 Erster Kandidat
     * @param candidate2 Zweiter Kandidat
     * @return Vergleichsergebnis (negative Werte = candidate1 ist besser)
     */
    private int compareInsertionCandidates(BalancedTreeMirrorNode candidate1, BalancedTreeMirrorNode candidate2) {
        StructureNode.StructureType typeId = candidate1.deriveTypeId();

        // 1. Priorität: Balance-Impact (niedrigerer Impact = besserer Kandidat)
        BalancedTreeMirrorNode root = (BalancedTreeMirrorNode) getCurrentStructureRoot();
        if (root != null) {
            double impact1 = calculateInsertionBalanceImpact(candidate1, root, typeId);
            double impact2 = calculateInsertionBalanceImpact(candidate2, root, typeId);

            int impactCompare = Double.compare(impact1, impact2);
            if (impactCompare != 0) {
                return impactCompare;
            }
        }

        // 2. Priorität: Nähe zum Target (näher am targetLinksPerNode = besser)
        int children1 = candidate1.getChildren(typeId).size();
        int children2 = candidate2.getChildren(typeId).size();

        double targetDistance1 = Math.abs(children1 - network.getNumTargetLinksPerMirror());
        double targetDistance2 = Math.abs(children2 - network.getNumTargetLinksPerMirror());

        int targetCompare = Double.compare(targetDistance1, targetDistance2);
        if (targetCompare != 0) {
            return targetCompare;
        }

        // 3. Priorität: Anzahl Kinder (weniger Kinder = besserer Kandidat für ausgeglichenes Wachstum)
        int childrenCompare = Integer.compare(children1, children2);
        if (childrenCompare != 0) {
            return childrenCompare;
        }

        // 4. Priorität: Tiefe (flachere Knoten bevorzugt für bessere Balance)
        int depth1 = candidate1.getDepthInTree();
        int depth2 = candidate2.getDepthInTree();

        int depthCompare = Integer.compare(depth1, depth2);
        if (depthCompare != 0) {
            return depthCompare;
        }

        // 5. Priorität: Node-ID (deterministische Sortierung)
        return Integer.compare(candidate1.getId(), candidate2.getId());
    }

    /**
     * Findet Balance-optimierte Entfernungskandidaten.
     * Sammelt alle entfernbaren Knoten und sortiert sie nach Balance-Impact.
     *
     * @param root Root der Struktur
     * @return Sortierte Liste der besten Entfernungskandidaten
     */
    private List<BalancedTreeMirrorNode> findBalancedRemovalCandidates(BalancedTreeMirrorNode root) {
        List<BalancedTreeMirrorNode> candidates = new ArrayList<>();
        StructureNode.StructureType typeId = root.deriveTypeId();
        Set<StructureNode> allNodes = root.getAllNodesInStructure(typeId, root);

        for (StructureNode node : allNodes) {
            if (node != root && node instanceof BalancedTreeMirrorNode balancedNode) {
                if (balancedNode.canBeRemovedFromStructure(root)) {
                    candidates.add(balancedNode);
                }
            }
        }

        // Sortiere nach Balance-Impact (minimaler Impact zuerst)
        candidates.sort(this::compareRemovalCandidates);
        return candidates;
    }

    /**
     * Fallback-Methode für Entfernungskandidaten wenn keine Balance-optimierten Kandidaten verfügbar sind.
     * Verwendet vereinfachte Kriterien: Blätter werden bevorzugt.
     *
     * @param root Root der Struktur
     * @return Liste der Fallback-Entfernungskandidaten
     */
    private List<BalancedTreeMirrorNode> findFallbackRemovalCandidates(BalancedTreeMirrorNode root) {
        List<BalancedTreeMirrorNode> candidates = new ArrayList<>();
        StructureNode.StructureType typeId = root.deriveTypeId();
        Set<StructureNode> allNodes = root.getAllNodesInStructure(typeId, root);

        for (StructureNode node : allNodes) {
            if (node != root && node instanceof BalancedTreeMirrorNode balancedNode) {
                // Weniger strenge Kriterien als Balance-optimierte Variante
                if (!balancedNode.isHead(typeId) && balancedNode.getParent() != null) {
                    candidates.add(balancedNode);
                }
            }
        }

        // Einfache Sortierung: Blätter zuerst, dann nach ID
        candidates.sort((a, b) -> {
            int childrenA = a.getChildren(typeId).size();
            int childrenB = b.getChildren(typeId).size();
            if (childrenA != childrenB) {
                return Integer.compare(childrenA, childrenB);
            }
            return Integer.compare(b.getId(), a.getId()); // Höhere IDs zuerst
        });

        return candidates;
    }

    /**
     * Redistributes children for optimal balance after node removal.
     * Verteilt Kinder eines entfernten Knotens optimal auf die verbleibende Struktur.
     *
     * @param children  Set der zu redistributionierenden Kinder
     * @param newParent Der neue Parent-Knoten
     * @param root      Root der Struktur
     * @param typeId    Struktur-Typ-ID
     */
    private void redistributeChildrenForOptimalBalance(Set<StructureNode> children,
                                                       BalancedTreeMirrorNode newParent,
                                                       BalancedTreeMirrorNode root,
                                                       StructureNode.StructureType typeId) {
        if (children.isEmpty()) return;

        // Direkte Verbindung mit dem neuen Parent als Startpunkt
        List<StructureNode> childrenList = new ArrayList<>(children);

        for (StructureNode child : childrenList) {
            // Prüfe ob newParent noch Kapazität hat
            if (newParent.getChildren(typeId).size() < network.getNumTargetLinksPerMirror()) {
                newParent.addChild(child, Set.of(typeId), Map.of(typeId, newParent.getId()));
            } else {
                // Finde alternativen Balance-optimierten Parent
                List<BalancedTreeMirrorNode> alternativeCandidates = findBalancedInsertionCandidates(root);

                boolean childPlaced = false;
                for (BalancedTreeMirrorNode candidate : alternativeCandidates) {
                    if (candidate.getChildren(typeId).size() < network.getNumTargetLinksPerMirror()) {
                        candidate.addChild(child, Set.of(typeId), Map.of(typeId, candidate.getId()));
                        childPlaced = true;
                        break;
                    }
                }

                // Fallback: Verbinde mit newParent auch wenn über Kapazität
                if (!childPlaced) {
                    newParent.addChild(child, Set.of(typeId), Map.of(typeId, newParent.getId()));
                }
            }
        }
    }

    /**
     * Berechnet den Balance-Impact beim Hinzufügen eines Kindes zu einem Kandidaten.
     * Simuliert die Änderung und bewertet den Impact auf die Gesamtbalance.
     *
     * @param candidate Der Kandidat für die Impact-Berechnung
     * @return Balance-Impact-Wert (niedrigere Werte = besserer Impact)
     */
    private double calculateInsertionBalanceImpact(BalancedTreeMirrorNode candidate) {
        // Aktuelle Balance des gesamten Baums
        BalancedTreeMirrorNode root = (BalancedTreeMirrorNode) candidate.findHead(candidate.deriveTypeId());
        if (root == null) return Double.MAX_VALUE;

        // Simuliere das Hinzufügen eines Kindes
        int currentChildren = candidate.getChildren().size();
        double predictedDeviation = Math.abs(currentChildren + 1 - network.getNumTargetLinksPerMirror());

        // Berücksichtige auch die Tiefe für Balance-Optimierung
        int depth = candidate.getDepthInTree();
        double depthPenalty = depth * 0.1; // Tiefere Knoten erhalten kleine Strafe

        return predictedDeviation + depthPenalty;
    }

    /**
     * Vergleicht Entfernungskandidaten für Balance-Optimierung.
     * Bevorzugt Knoten, deren Entfernung die Balance am wenigsten beeinträchtigt.
     *
     * @param candidate1 Erster Kandidat
     * @param candidate2 Zweiter Kandidat
     * @return Vergleichsergebnis für Sortierung
     */
    private int compareRemovalCandidates(BalancedTreeMirrorNode candidate1, BalancedTreeMirrorNode candidate2) {
        StructureNode.StructureType typeId = candidate1.deriveTypeId();

        // 1. Priorität: Bevorzuge Blätter (weniger Kinder)
        int children1 = candidate1.getChildren(typeId).size();
        int children2 = candidate2.getChildren(typeId).size();
        int childrenCompare = Integer.compare(children1, children2);
        if (childrenCompare != 0) return childrenCompare;

        // 2. Priorität: Bei gleicher Kinderanzahl - bevorzuge tiefere Knoten
        int depth1 = candidate1.getDepthInTree();
        int depth2 = candidate2.getDepthInTree();
        int depthCompare = Integer.compare(depth2, depth1); // Umgekehrte Reihenfolge
        if (depthCompare != 0) return depthCompare;

        // 3. Priorität: Balance-Impact-Berechnung
        try {
            double removalImpact1 = calculateRemovalBalanceImpact(candidate1);
            double removalImpact2 = calculateRemovalBalanceImpact(candidate2);

            int impactCompare = Double.compare(removalImpact1, removalImpact2);
            if (impactCompare != 0) return impactCompare;
        } catch (Exception e) {
            // Fallback bei Berechnungsfehlern
        }

        // 4. Fallback: Bei gleicher Tiefe - bevorzuge höhere IDs (deterministische Auswahl)
        return Integer.compare(candidate2.getId(), candidate1.getId());
    }

    /**
     * Berechnet den Balance-Impact bei der Entfernung eines Knotens.
     * Berücksichtigt die Anzahl der Kinder und deren Redistribution.
     *
     * @param candidate Der Kandidat für die Impact-Berechnung
     * @return Balance-Impact-Wert (niedrigere Werte = besserer Impact)
     */
    private double calculateRemovalBalanceImpact(BalancedTreeMirrorNode candidate) {
        StructureNode.StructureType typeId = candidate.deriveTypeId();

        // Impact basierend auf Kinderanzahl
        int childrenCount = candidate.getChildren(typeId).size();
        double childrenImpact = childrenCount * 0.5; // Jedes Kind erhöht den Impact

        // Impact basierend auf Parent-Kapazität
        StructureNode parent = candidate.getParent();
        double parentImpact = 0.0;

        if (parent instanceof BalancedTreeMirrorNode balancedParent) {
            int parentChildren = balancedParent.getChildren(typeId).size();
            int parentCapacity = Math.max(0, network.getNumTargetLinksPerMirror() - parentChildren);

            // Höherer Impact wenn Parent keine Kapazität für Kinder-Redistribution hat
            if (childrenCount > parentCapacity) {
                parentImpact = (childrenCount - parentCapacity) * 1.0;
            }
        }

        return childrenImpact + parentImpact;
    }

    /**
     * Erstellt einen neuen BalancedTreeMirrorNode aus dem Mirror-Iterator.
     * Factory-Methode für typsichere Node-Erstellung.
     *
     * @return Neuer BalancedTreeMirrorNode oder null, wenn keine Mirrors verfügbar sind
     */
    protected BalancedTreeMirrorNode getNodeFromIterator() {
        if (network.getMirrorCursor().hasNextMirror()) {
            Mirror mirror = network.getMirrorCursor().getNextMirror();
            MirrorNode node = createMirrorNodeForMirror(mirror);

            if (node != null) {
                node.addNodeType(StructureNode.StructureType.MIRROR);
                node.addNodeType(StructureNode.StructureType.TREE);
                node.addNodeType(StructureNode.StructureType.BALANCED_TREE);
                node.setMirror(mirror);
            }
            return (BalancedTreeMirrorNode) node;
        }
        return null;
    }

    /**
     * Factory-Methode für baum-spezifische MirrorNode-Erstellung.
     * Überschreibt BuildAsSubstructure für die TreeMirrorNode-Erstellung.
     *
     * @param mirror Der Mirror, für den ein MirrorNode erstellt werden soll
     * @return Neuer TreeMirrorNode
     */
    @Override
    protected MirrorNode createMirrorNodeForMirror(Mirror mirror) {
        return new BalancedTreeMirrorNode(mirror.getID(), mirror, network.getNumTargetLinksPerMirror(), maxAllowedBalanceDeviation);
    }

    // ===== GETTER UND SETTER =====

    public double getMaxAllowedBalanceDeviation() {
        return maxAllowedBalanceDeviation;
    }

    public void setMaxAllowedBalanceDeviation(double maxAllowedBalanceDeviation) {
        this.maxAllowedBalanceDeviation = Math.max(0.1, maxAllowedBalanceDeviation);
    }

    @Override
    public String toString() {
        return String.format("BalancedTreeTopologyStrategy[targetLinks=%d, maxDev=%.2f]",
                network.getNumTargetLinksPerMirror(), maxAllowedBalanceDeviation);
    }
}