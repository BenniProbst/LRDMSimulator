package org.lrdm.topologies.node;

import java.util.*;
import java.util.stream.Collectors;

/**
 * # StructureNode
 * <p>
 * A structural graph node that supports:
 * <ul>
 *   <li><b>Parent–child relations</b> (directed edges)</li>
 *   <li><b>Multi-type membership</b> (a node can belong to multiple structural types)</li>
 *   <li><b>Head per type</b> (a node may act as the head/root for one or more structure types)</li>
 *   <li><b>Head-ID scoping</b> (multiple disjoint structures of the same type are distinguished via head IDs)</li>
 *   <li><b>Ring-safe traversals</b> (all traversals guard against cycles via explicit visited tracking)</li>
 * </ul>
 *
 * <p><b>Multi-structure model.</b> A single parent–child edge is annotated with:
 * <ul>
 *   <li>the set of {@link StructureType}s for which it exists, and</li>
 *   <li>for each of those types, a <em>head ID</em> indicating which independent structure instance it belongs to.</li>
 * </ul>
 * This allows several structures (even of the same {@link StructureType}) to co-exist on the same node set without
 * bleeding into each other during typed traversals.
 *
 * <p><b>Immutability & copies.</b> Public getters on composite state return defensive copies to protect invariants.
 *
 * <p><b>Traversal guarantees.</b> All traversals (DFS/BFS/stack-based) are cycle-safe and avoid recursion to prevent
 * stack overflow in large graphs.
 *
 * @author Benjamin-Elias Probst <benjamineliasprobst@gmail.com>
 * @since 1.0
 */
public class StructureNode {

    /** Unique node identifier. */
    private final int id;

    /** Parent pointer (not typed; membership is validated via child records). */
    private StructureNode parent;

    /**
     * Child edges. Each record store:
     * <ul>
     *   <li>the child node reference,</li>
     *   <li>the set of structure types this edge belongs to,</li>
     *   <li>and a mapping type → headId defining the structure instance.</li>
     * </ul>
     */
    private final Set<ChildRecord> children;

    /**
     * Head status per structure type. If a node is a head for a given {@link StructureType},
     * traversals treat that node as a boundary (for typed, head-scoped traversals).
     */
    private final Map<StructureType, Boolean> headStatus;

    /**
     * The set of structure types this node itself represents. This is used by automatic type derivation
     * and convenience checks (e.g., {@link #hasNodeType(StructureType)}).
     */
    protected Set<StructureType> nodeTypes;

    /** Upper bound for direct children (across all types). */
    private int maxChildren = Integer.MAX_VALUE;

    // --------------------------------------------------------------------------------------------
    // Types
    // --------------------------------------------------------------------------------------------

    /**
     * Canonical structure types supported by the simulator. You can extend the set via
     * {@link #nodeTypes} or by subclassing and overriding {@link #deriveTypeId()}.
     */
    public enum StructureType {
        DEFAULT(0),
        MIRROR(1),
        TREE(2),
        LINE(3),
        STAR(4),
        FULLY_CONNECTED(5),
        N_CONNECTED(6),
        BALANCED_TREE(7),
        DEPTH_LIMIT_TREE(8),
        SNOWFLAKE(9);

        private final int id;

        StructureType(int id) {
            this.id = id;
        }

        /**
         * @return integer identifier for stable, compact storage or logging
         */
        public int getId() {
            return id;
        }
    }

    /**
     * Child-edge metadata: the child node plus per-edge annotations for multi-structure awareness.
     * The constructor stores defensive copies to keep this record immutable.
     *
     * @param child   the child node
     * @param typeIds structure types this edge participates in
     * @param headIds for each type, the head-id that scopes the structure instance
     */
    public record ChildRecord(StructureNode child,
                              Set<StructureType> typeIds,
                              Map<StructureType, Integer> headIds) {

        public ChildRecord {
            typeIds = new HashSet<>(typeIds);
            headIds = new HashMap<>(headIds);
        }

        /**
         * @return the referenced child node
         */
        public StructureNode getChild() {
            return child;
        }

        /**
         * @param typeId structure type to test
         * @return {@code true} if this edge belongs to the given structure type
         */
        public boolean hasType(StructureType typeId) {
            return typeIds.contains(typeId);
        }

        /**
         * @param typeId structure type
         * @param headId structure head-id
         * @return {@code true} if this edge belongs to the structure instance (type + headId)
         */
        public boolean belongsToStructure(StructureType typeId, int headId) {
            return typeIds.contains(typeId) && headIds.containsKey(typeId) && headIds.get(typeId) == headId;
        }

        /**
         * @param typeId structure type
         * @return the head-id for this type, or {@code null} if none
         */
        public Integer getHeadId(StructureType typeId) {
            return headIds.get(typeId);
        }

        /**
         * @return defensive copy of the type set
         */
        public Set<StructureType> getTypeIds() {
            return new HashSet<>(typeIds);
        }

        /**
         * @return defensive copy of a type → head-id map
         */
        public Map<StructureType, Integer> getHeadIds() {
            return new HashMap<>(headIds);
        }
    }

    /**
     * Directed link expressed as a pair of node IDs to avoid string concatenation in sets/maps.
     *
     * @param from source-node id
     * @param to   target-node id
     */
    public record LinkPair(int from, int to) {

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof LinkPair other)) return false;
            return from == other.from && to == other.to;
        }
    }

    // --------------------------------------------------------------------------------------------
    // Construction
    // --------------------------------------------------------------------------------------------

    /**
     * Creates a new node with the given id. Initializes empty children/head-status and adds {@link StructureType#DEFAULT}
     * to this node's {@link #nodeTypes}.
     *
     * @param id unique node id
     */
    public StructureNode(int id) {
        this.id = id;
        this.parent = null;
        this.children = new HashSet<>();
        this.headStatus = new HashMap<>();
        this.nodeTypes = new HashSet<>();
        this.nodeTypes.add(StructureType.DEFAULT);
    }

    /**
     * Creates a new node with the given id and a maximum number of direct children.
     *
     * @param id          unique node id
     * @param maxChildren maximum number of children (clamped to {@code >= 0})
     */
    public StructureNode(int id, int maxChildren) {
        this(id);
        this.maxChildren = Math.max(0, maxChildren);
    }

    // --------------------------------------------------------------------------------------------
    // Type derivation & node type management
    // --------------------------------------------------------------------------------------------

    /**
     * Derives the primary type of this node. Subclasses may override to report a specific default type.
     *
     * @return the derived (primary) {@link StructureType}
     */
    protected StructureType deriveTypeId() {
        return StructureType.DEFAULT;
    }

    /**
     * @return defensive copy of the set of types this node represents
     */
    public Set<StructureType> getNodeTypes() {
        return new HashSet<>(nodeTypes);
    }

    /**
     * Replaces the set of types this node represents.
     *
     * @param nodeTypes new type set; must not be {@code null}
     * @throws IllegalArgumentException if {@code nodeTypes} is {@code null}
     */
    public void setNodeTypes(Set<StructureType> nodeTypes) {
        if (nodeTypes == null) {
            throw new IllegalArgumentException("nodeTypes must not be null");
        }
        this.nodeTypes.clear();
        this.nodeTypes.addAll(nodeTypes);
    }

    /**
     * Adds a structure type to this node.
     *
     * @param nodeType type to add; must not be {@code null}
     * @throws IllegalArgumentException if {@code nodeType} is {@code null}
     */
    public void addNodeType(StructureType nodeType) {
        if (nodeType == null) {
            throw new IllegalArgumentException("nodeType must not be null");
        }
        this.nodeTypes.add(nodeType);
    }

    /**
     * Removes a structure type from this node (no-op if not present).
     *
     * @param nodeType type to remove (nullable)
     */
    public void removeNodeType(StructureType nodeType) {
        this.nodeTypes.remove(nodeType);
    }

    /**
     * @param nodeType type to test
     * @return {@code true} if this node represents the given type
     */
    public boolean hasNodeType(StructureType nodeType) {
        return nodeTypes.contains(nodeType);
    }

    // --------------------------------------------------------------------------------------------
    // Typed traversal (multi-structure aware)
    // --------------------------------------------------------------------------------------------

    /**
     * Collects all nodes reachable within the same structure instance (typed and head-scoped), starting from {@code this}.
     * The traversal:
     * <ul>
     *   <li>follows only edges whose {@code typeId} and {@code headId} match,</li>
     *   <li>is ring-safe, and</li>
     *   <li>does not traverse beyond head nodes of the <em>same</em> type+headId (heads define boundaries).</li>
     * </ul>
     *
     * @param typeId structure type to traverse
     * @param head   head node that defines the structure instance (its id is used as head-id)
     * @return the set of nodes in that structure instance; if {@code head} is {@code null}, returns {@code Set.of(this)}
     */
    public Set<StructureNode> getAllNodesInStructure(StructureType typeId, StructureNode head) {
        if (head == null) return Set.of(this);

        int headId = head.getId();
        Set<StructureNode> result = new HashSet<>();
        Stack<StructureNode> stack = new Stack<>();
        stack.push(this);

        while (!stack.isEmpty()) {
            StructureNode current = stack.pop();

            if (result.contains(current)) continue; // cycle guard
            result.add(current);

            // If the current is a foreign head of the same type or does not represent the type → stop exploring here.
            if ((current.isHead(typeId) && headId != current.getId()) || (!current.getNodeTypes().contains(typeId))) {
                continue;
            }

            // Traverse children that belong to the same typed, head-scoped structure
            Set<StructureNode> structureChildren = current.getChildren(typeId, headId);
            stack.addAll(structureChildren);

            // Do not traverse past the head of this structure instance
            if (current.isHead(typeId) && headId == current.getId()) {
                continue;
            }

            // Traverse to parent if the parent→current edge belongs to this structure instance
            if (current.parent != null) {
                ChildRecord parentRecord = current.parent.findChildRecordById(current.getId());
                if (parentRecord != null && parentRecord.belongsToStructure(typeId, headId)) {
                    stack.push(current.parent);
                }
            }
        }

        return result;
    }

    /**
     * Collects all terminal nodes (degree==1 within the typed, head-scoped structure).
     *
     * @param typeId structure type
     * @param head   head that identifies the structure instance
     * @return set of endpoints
     */
    public Set<StructureNode> getEndpointsOfStructure(StructureType typeId, StructureNode head) {
        if (head == null) return Set.of(this);

        final int headId = head.getId();
        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head);
        Set<StructureNode> endpoints = new HashSet<>();

        for (StructureNode node : allNodes) {
            if (isEndpoint(node, typeId, headId)) {
                endpoints.add(node);
            }
        }
        return endpoints;
    }

    // --------------------------------------------------------------------------------------------
    // Cycle detection
    // --------------------------------------------------------------------------------------------

    /**
     * Detects whether a closed cycle exists within a given typed, head-scoped structure.
     * Uses a stack-based DFS with explicit recursion stacks for ring safety.
     *
     * @param allNodes nodes of the structure instance
     * @param typeId   structure type
     * @param head     head that identifies the structure instance
     * @return {@code true} if a cycle is found
     */
    public boolean hasClosedCycle(Set<StructureNode> allNodes, StructureType typeId, StructureNode head) {
        if (allNodes == null || allNodes.isEmpty() || head == null) return false;

        final int headId = head.getId();
        Set<StructureNode> visited = new HashSet<>();
        Set<StructureNode> globalRecursionStack = new HashSet<>();

        for (StructureNode startNode : allNodes) {
            if (!visited.contains(startNode)) {
                if (hasCycleDFS(startNode, visited, globalRecursionStack, typeId, headId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Stack-based DFS for cycle detection with type+head scoping.
     *
     * @param startNode start node
     * @param visited   global visited set
     * @param globalRecursionStack nodes in current recursion globally
     * @param typeId    structure type
     * @param headId    head-id for the structure instance
     * @return {@code true} if a cycle is detected
     */
    private boolean hasCycleDFS(StructureNode startNode,
                                Set<StructureNode> visited,
                                Set<StructureNode> globalRecursionStack,
                                StructureType typeId,
                                int headId) {

        record StackEntry(StructureNode node, boolean isProcessing) {}

        Stack<StackEntry> stack = new Stack<>();
        Set<StructureNode> localRecursionStack = new HashSet<>();
        stack.push(new StackEntry(startNode, true));

        while (!stack.isEmpty()) {
            StackEntry entry = stack.pop();
            StructureNode node = entry.node;
            boolean isProcessing = entry.isProcessing;

            if (isProcessing) {
                if (localRecursionStack.contains(node) || globalRecursionStack.contains(node)) {
                    return true;
                }
                if (visited.contains(node)) {
                    continue;
                }

                visited.add(node);
                localRecursionStack.add(node);
                globalRecursionStack.add(node);

                // schedule post-order
                stack.push(new StackEntry(node, false));

                // follow typed, head-scoped children
                Set<StructureNode> children = node.getChildren(typeId, headId);
                for (StructureNode child : children) {
                    stack.push(new StackEntry(child, true));
                }
            } else {
                localRecursionStack.remove(node);
                globalRecursionStack.remove(node);
            }
        }
        return false;
    }

    /**
     * Utility to test whether a given set of nodes forms a single <em>closed ring</em>
     * (each node has exactly one child and the ring covers all nodes).
     *
     * @param nodes candidate nodes
     * @return {@code true} if nodes form one closed cycle; {@code false} otherwise
     */
    public static boolean hasClosedCycle(Set<StructureNode> nodes) {
        if (nodes.isEmpty()) return false;

        for (StructureNode node : nodes) {
            if (node.getChildren().size() != 1) {
                return false;
            }
        }

        StructureNode start = nodes.iterator().next();
        Set<StructureNode> visitedInCycle = new HashSet<>();
        StructureNode current = start;

        do {
            if (visitedInCycle.contains(current)) {
                return current == start && visitedInCycle.size() == nodes.size();
            }
            visitedInCycle.add(current);

            Set<StructureNode> children = current.getChildren();
            if (children.size() != 1) return false;

            StructureNode child = children.iterator().next();
            if (!nodes.contains(child)) return false;

            current = child;
        } while (!visitedInCycle.contains(current));

        return current == start && visitedInCycle.size() == nodes.size();
    }

    // --------------------------------------------------------------------------------------------
    // Children management
    // --------------------------------------------------------------------------------------------

    /**
     * Adds (or merges) a typed, head-scoped child edge. If the child already exists (by id), the
     * type set and head-id map are merged; otherwise a new edge is inserted (subject to {@link #maxChildren}).
     *
     * @param child   child node; must not be {@code null}
     * @param typeIds structure types; must not be {@code null} or empty
     * @param headIds map type → head-id; must provide a head-id for each type in {@code typeIds}
     * @throws IllegalArgumentException if required, head-ids are missing
     */
    public void addChild(StructureNode child, Set<StructureType> typeIds, Map<StructureType, Integer> headIds) {
        if (child == null || typeIds == null || typeIds.isEmpty()) {
            return;
        }

        // auto-derive a child's primary type and add it to the edge types
        Set<StructureType> finalTypeIds = new HashSet<>(typeIds);
        StructureType derivedTypeId = child.deriveTypeId();
        finalTypeIds.add(derivedTypeId);

        // validate head-ids coverage
        Map<StructureType, Integer> finalHeadIds = new HashMap<>(headIds);
        for (StructureType typeId : finalTypeIds) {
            if (!finalHeadIds.containsKey(typeId)) {
                throw new IllegalArgumentException("Missing head-id for type: " + typeId);
            }
        }

        // merge or insert
        ChildRecord existingRecord = findChildRecordById(child.getId());
        if (existingRecord != null) {
            Set<StructureType> mergedTypes = new HashSet<>(existingRecord.typeIds());
            mergedTypes.addAll(finalTypeIds);

            Map<StructureType, Integer> mergedHeadIds = new HashMap<>(existingRecord.headIds());
            mergedHeadIds.putAll(finalHeadIds);

            children.remove(existingRecord);
            children.add(new ChildRecord(child, mergedTypes, mergedHeadIds));
            child.setParent(this);
        } else if (children.size() < maxChildren) {
            children.add(new ChildRecord(child, finalTypeIds, finalHeadIds));
            child.setParent(this);
        }
    }

    /**
     * Convenience overload: adds a child using its derived primary type and the nearest head for that type.
     * If no head is found, this node becomes the head for the new edge.
     *
     * @param child child node (ignored if {@code null})
     */
    public void addChild(StructureNode child) {
        if (child == null) return;

        StructureType derivedTypeId = child.deriveTypeId();
        StructureNode head = findHead(derivedTypeId);
        int headId = (head != null) ? head.getId() : this.getId();

        addChild(child, Set.of(derivedTypeId), Map.of(derivedTypeId, headId));
    }

    /**
     * Removes a child <em>only</em> for the specified types (the edge may remain if it still belongs to other types).
     *
     * @param child   child node (nullable)
     * @param typeIds types to strip from the child edge (nullable)
     */
    public void removeChild(StructureNode child, Set<StructureType> typeIds) {
        if (child == null || typeIds == null) return;

        ChildRecord existingRecord = findChildRecordById(child.getId());
        if (existingRecord == null) return;

        Set<StructureType> remainingTypes = new HashSet<>(existingRecord.typeIds());
        remainingTypes.removeAll(typeIds);

        Map<StructureType, Integer> remainingHeadIds = new HashMap<>(existingRecord.headIds());
        typeIds.forEach(remainingHeadIds::remove);

        if (remainingTypes.isEmpty()) {
            children.remove(existingRecord);
            if (child.getParent() == this) {
                child.setParent(null);
            }
        } else {
            children.remove(existingRecord);
            children.add(new ChildRecord(child, remainingTypes, remainingHeadIds));
        }
    }

    /**
     * Removes a child edge entirely (across all types) and clears the child's parent if present.
     *
     * @param child child node (ignored if {@code null})
     */
    public void removeChild(StructureNode child) {
        if (child != null) {
            children.removeIf(record -> record.child().getId() == child.getId());
            if (child.getParent() == this) {
                child.setParent(null);
            }
        }
    }

    /**
     * Finds the child record for a given child-id.
     *
     * @param childId child node id
     * @return the record or {@code null} if not present
     */
    public ChildRecord findChildRecordById(int childId) {
        return children.stream()
                .filter(record -> record.child().getId() == childId)
                .findFirst()
                .orElse(null);
    }

    // --------------------------------------------------------------------------------------------
    // Child accessors
    // --------------------------------------------------------------------------------------------

    /**
     * @return set of all direct child nodes (across all types)
     */
    public Set<StructureNode> getChildren() {
        Set<StructureNode> allChildren = new HashSet<>();
        for (ChildRecord record : children) {
            allChildren.add(record.child());
        }
        return allChildren;
    }

    /**
     * Returns direct children for a given structure type (ignores head scoping).
     *
     * @param typeId structure type
     * @return set of children that have this type on the edge
     */
    public Set<StructureNode> getChildren(StructureType typeId) {
        Set<StructureNode> typeChildren = new HashSet<>();
        for (ChildRecord record : children) {
            if (record.hasType(typeId)) {
                typeChildren.add(record.child());
            }
        }
        return typeChildren;
    }

    /**
     * Returns direct children that belong to the specific structure instance (type + headId).
     *
     * @param typeId structure type
     * @param headId head-id identifying the instance
     * @return set of typed, head-scoped children
     */
    public Set<StructureNode> getChildren(StructureType typeId, int headId) {
        Set<StructureNode> structureChildren = new HashSet<>();
        for (ChildRecord record : children) {
            if (record.belongsToStructure(typeId, headId)) {
                structureChildren.add(record.child());
            }
        }
        return structureChildren;
    }

    /**
     * Bulk-merge: add/overwrite head-ids for the specified children (union on types).
     *
     * @param headIds        new head-ids to merge (type → headId)
     * @param childrenNodes  affected children
     */
    public void updateChildRecordMergeStructureHead(Map<StructureType, Integer> headIds, Set<StructureNode> childrenNodes) {
        HashMap<StructureType, Integer> copyHeadIds = new HashMap<>(headIds);
        HashSet<ChildRecord> copyChildRecordsForUpdate = new HashSet<>();
        HashSet<ChildRecord> removeChildRecord = new HashSet<>();

        for (ChildRecord c : children) {
            if (!childrenNodes.contains(c.child())) {
                continue;
            }
            HashMap<StructureType, Integer> copyHeadIdsLocal = new HashMap<>(c.getHeadIds());
            copyHeadIdsLocal.putAll(copyHeadIds);
            ChildRecord newChildRecord = new ChildRecord(
                    c.getChild(),
                    c.getChild().getNodeTypes(),
                    copyHeadIdsLocal);
            copyChildRecordsForUpdate.add(newChildRecord);
            removeChildRecord.add(c);
        }
        children.removeAll(removeChildRecord);
        children.addAll(copyChildRecordsForUpdate);
    }

    /**
     * Bulk-remove: drop the provided types (and their head-ids) from selected children.
     *
     * @param typeIds        types to remove
     * @param childrenNodes  affected children
     */
    public void updateChildRecordRemoveStructureHead(Set<StructureType> typeIds, Set<StructureNode> childrenNodes) {
        HashSet<ChildRecord> copyChildRecordsForUpdate = new HashSet<>();
        HashSet<ChildRecord> removeChildRecord = new HashSet<>();

        for (ChildRecord c : children) {
            if (!childrenNodes.contains(c.child())) {
                continue;
            }
            HashSet<StructureType> copyTypeIdsLocal = new HashSet<>(c.getTypeIds());
            copyTypeIdsLocal.removeAll(typeIds);

            HashMap<StructureType, Integer> copyHeadIdsLocal = new HashMap<>(c.getHeadIds());
            copyHeadIdsLocal.keySet().removeAll(typeIds);

            ChildRecord newChildRecord = new ChildRecord(
                    c.getChild(),
                    copyTypeIdsLocal,
                    copyHeadIdsLocal);
            copyChildRecordsForUpdate.add(newChildRecord);
            removeChildRecord.add(c);
        }
        children.removeAll(removeChildRecord);
        children.addAll(copyChildRecordsForUpdate);
    }

    // --------------------------------------------------------------------------------------------
    // Head discovery
    // --------------------------------------------------------------------------------------------

    /**
     * Finds the nearest head node for the given type by walking upwards (parents only).
     * Returns {@code null} if none is found. This method is ring-safe.
     *
     * @param typeId structure type
     * @return nearest head for the type, or {@code null}
     */
    public StructureNode findHead(StructureType typeId) {
        if (typeId == null) {
            return null;
        }

        Set<StructureNode> visited = new HashSet<>();
        Stack<StructureNode> stack = new Stack<>();
        stack.push(this);

        while (!stack.isEmpty()) {
            StructureNode current = stack.pop();

            if (visited.contains(current)) {
                continue;
            }
            visited.add(current);

            if (current.isHead(typeId)) {
                return current;
            }
            if (current.getParent() != null && !visited.contains(current.getParent())) {
                stack.push(current.getParent());
            }
        }
        return null;
    }

    // --------------------------------------------------------------------------------------------
    // Endpoint detection
    // --------------------------------------------------------------------------------------------

    /**
     * Checks whether {@code node} is an endpoint (degree==1) within the typed, head-scoped structure.
     *
     * @param node   node to test
     * @param typeId structure type
     * @param headId structure head-id
     * @return {@code true} if exactly one incident edge of this structure instance exists
     */
    public boolean isEndpoint(StructureNode node, StructureType typeId, int headId) {
        int connections = 0;

        if (node.parent != null) {
            ChildRecord parentRecord = node.parent.findChildRecordById(node.getId());
            if (parentRecord != null && parentRecord.belongsToStructure(typeId, headId)) {
                connections++;
            }
        }
        connections += node.getChildren(typeId, headId).size();
        return connections == 1;
    }

    // --------------------------------------------------------------------------------------------
    // Full traversal (untyped, legacy)
    // --------------------------------------------------------------------------------------------

    /**
     * Returns all nodes reachable from this node by following parent and all children (ignores types/heads).
     * Cycle-safe, non-recursive.
     *
     * @return reachable node set
     */
    public Set<StructureNode> getAllNodes() {
        Set<StructureNode> result = new HashSet<>();
        Stack<StructureNode> stack = new Stack<>();
        stack.push(this);

        while (!stack.isEmpty()) {
            StructureNode current = stack.pop();

            if (result.contains(current)) continue;
            result.add(current);

            if (current.parent != null) {
                stack.push(current.parent);
            }
            current.getChildren().forEach(stack::push);
        }
        return result;
    }

    // --------------------------------------------------------------------------------------------
    // Paths (multi-path discovery)
    // --------------------------------------------------------------------------------------------

    /**
     * Finds all directed paths from {@code head} to {@code this} constrained to {@code typeId}.
     * Uses a Dijkstra-like best-first exploration (on path length; then sum of node IDs) and avoids loops.
     *
     * <p><b>Ordering:</b> paths are sorted by (1) length (shortest first), then (2) sum of node IDs.</p>
     *
     * @param typeId structure type
     * @param head   head node
     * @return sorted list of all found paths; {@code List.of(List.of(this))} if {@code head == this}
     */
    public List<List<StructureNode>> getPathFromHeadMulti(StructureType typeId, StructureNode head) {
        if (head == null || typeId == null) {
            return List.of();
        }
        if (head.equals(this)) {
            return List.of(List.of(this));
        }

        List<List<StructureNode>> allPaths = new ArrayList<>();
        findAllPathsDijkstra(head, this, typeId, allPaths);

        allPaths.sort((path1, path2) -> {
            int lengthCompare = Integer.compare(path1.size(), path2.size());
            if (lengthCompare != 0) return lengthCompare;

            long sum1 = path1.stream().mapToLong(StructureNode::getId).sum();
            long sum2 = path2.stream().mapToLong(StructureNode::getId).sum();
            return Long.compare(sum1, sum2);
        });
        return allPaths;
    }

    /**
     * Convenience overload using {@link #deriveTypeId()} and {@link #findHead(StructureType)}.
     *
     * @return sorted list of all paths from the derived head to {@code this}; if no head exists, returns {@code List.of(List.of(this))}
     */
    public List<List<StructureNode>> getPathFromHeadMulti() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);

        if (head == null) {
            return List.of(List.of(this));
        }
        return getPathFromHeadMulti(typeId, head);
    }

    /**
     * Best-first search (Dijkstra-like) that lists all simple paths (no repeated nodes) from {@code start} to {@code target}
     * along edges that belong to {@code typeId}.
     *
     * @param start    start node (head)
     * @param target   target node
     * @param typeId   structure type filter
     * @param allPaths accumulator for discovered paths
     */
    private void findAllPathsDijkstra(StructureNode start,
                                      StructureNode target,
                                      StructureType typeId,
                                      List<List<StructureNode>> allPaths) {

        Map<StructureNode, List<List<StructureNode>>> nodeToAllPaths = new HashMap<>();

        PriorityQueue<List<StructureNode>> queue = new PriorityQueue<>((path1, path2) -> {
            int lengthCompare = Integer.compare(path1.size(), path2.size());
            if (lengthCompare != 0) return lengthCompare;

            long sum1 = path1.stream().mapToLong(StructureNode::getId).sum();
            long sum2 = path2.stream().mapToLong(StructureNode::getId).sum();
            return Long.compare(sum1, sum2);
        });

        List<StructureNode> initialPath = new ArrayList<>();
        initialPath.add(start);
        queue.offer(initialPath);

        Set<List<StructureNode>> exploredPaths = new HashSet<>();

        while (!queue.isEmpty()) {
            List<StructureNode> currentPath = queue.poll();
            StructureNode currentNode = currentPath.get(currentPath.size() - 1);

            if (exploredPaths.contains(currentPath)) {
                continue;
            }
            exploredPaths.add(new ArrayList<>(currentPath));

            if (currentNode.equals(target)) {
                allPaths.add(new ArrayList<>(currentPath));
                continue;
            }

            Set<StructureNode> neighbors = getNeighborsForTypeId(currentNode, typeId);
            for (StructureNode neighbor : neighbors) {
                if (currentPath.contains(neighbor)) {
                    continue; // avoid loops
                }
                List<StructureNode> newPath = new ArrayList<>(currentPath);
                newPath.add(neighbor);
                if (!exploredPaths.contains(newPath)) {
                    queue.offer(newPath);
                }
            }
        }
    }

    /**
     * Returns typed neighbors (children with matching {@code typeId} plus the parent if it supports {@code typeId}).
     * This defines the directed adjacency used by path discovery.
     *
     * @param node   node whose neighbors are requested
     * @param typeId structure type
     * @return neighbor set (typed)
     */
    private Set<StructureNode> getNeighborsForTypeId(StructureNode node, StructureType typeId) {
        Set<StructureNode> neighbors = new HashSet<>(node.getChildren(typeId));
        StructureNode parent = node.getParent();
        if (parent != null && parent.hasNodeType(typeId)) {
            neighbors.add(parent);
        }
        return neighbors;
    }

    /**
     * Returns the <em>shortest</em> (by length) path from head to {@code this} for the derived type.
     *
     * @return shortest path (possibly empty if none exists)
     */
    public List<StructureNode> getPathFromHead() {
        List<List<StructureNode>> allPaths = getPathFromHeadMulti();
        return !allPaths.isEmpty() ? allPaths.get(0) : new ArrayList<>();
    }

    /**
     * Returns the <em>shortest</em> (by length) path for a given type/head.
     *
     * @param typeId structure type
     * @param head   head node
     * @return shortest path (possibly empty if none exists)
     */
    public List<StructureNode> getPathFromHead(StructureType typeId, StructureNode head) {
        List<List<StructureNode>> allPaths = getPathFromHeadMulti(typeId, head);
        return !allPaths.isEmpty() ? allPaths.get(0) : new ArrayList<>();
    }

    // --------------------------------------------------------------------------------------------
    // Link counting
    // --------------------------------------------------------------------------------------------

    /**
     * Estimates the number of planned links in a typed, head-scoped structure.
     * Default heuristic:
     * <ul>
     *   <li>Tree-like structures → {@code n - 1}</li>
     *   <li>Rings → may need override (default remains {@code n - 1})</li>
     * </ul>
     *
     * @param typeId structure type
     * @param head   head node
     * @return estimated link count (defaults to {@code n - 1})
     */
    public int getNumPlannedLinksFromStructure(StructureType typeId, StructureNode head) {
        if (head == null) return 0;

        Set<StructureNode> allNodes = getAllNodesInStructure(typeId, head);
        if (allNodes.size() <= 1) return 0;

        return allNodes.size() - 1;
    }

    /**
     * Convenience overload using {@link #deriveTypeId()} and {@link #findHead(StructureType)}.
     *
     * @return estimated link count for the derived structure
     */
    public int getNumPlannedLinksFromStructure() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        return getNumPlannedLinksFromStructure(typeId, head != null ? head : this);
    }

    /**
     * @return the number of direct connections, i.e. {@code (parent != null ? 1 : 0) + children.size()}
     */
    public int getNumDirectLinksFromStructure() {
        int linkCount = 0;
        if (parent != null) linkCount++;
        linkCount += children.size();
        return linkCount;
    }

    // --------------------------------------------------------------------------------------------
    // Descendants
    // --------------------------------------------------------------------------------------------

    /**
     * Counts all descendants (children, grandchildren, …) ignoring types (full structure).
     * Cycle-safe, non-recursive.
     *
     * @return number of descendants
     */
    public int getDescendantCount() {
        Set<StructureNode> descendants = new HashSet<>();
        Stack<StructureNode> stack = new Stack<>();

        stack.addAll(getChildren());

        while (!stack.isEmpty()) {
            StructureNode current = stack.pop();
            if (descendants.contains(current)) continue;

            descendants.add(current);
            stack.addAll(current.getChildren());
        }
        return descendants.size();
    }

    // --------------------------------------------------------------------------------------------
    // Node search (untyped, full)
    // --------------------------------------------------------------------------------------------

    /**
     * Searches the entire reachable component (untyped) for a node id.
     *
     * @param nodeId id to search
     * @return the matching node or {@code null}
     */
    public StructureNode findNodeById(int nodeId) {
        Set<StructureNode> allNodes = getAllNodes();
        for (StructureNode node : allNodes) {
            if (node.getId() == nodeId) {
                return node;
            }
        }
        return null;
    }

    // --------------------------------------------------------------------------------------------
    // Structure integrity & validation
    // --------------------------------------------------------------------------------------------

    /**
     * Checks whether this node can be removed from the structure rooted at {@code structureRoot}.
     * Current policy:
     * <ul>
     *   <li>node must be part of the structure</li>
     *   <li>node must be a leaf (to avoid fragmentation)</li>
     * </ul>
     *
     * @param structureRoot structure root (must not be {@code null})
     * @return {@code true} if safe to remove
     */
    public boolean canBeRemovedFromStructure(StructureNode structureRoot) {
        if (structureRoot == null) return false;

        Set<StructureNode> structureNodes = structureRoot.getAllNodesInStructure();

        if (!structureNodes.contains(this)) {
            return false;
        }
        return this.isLeaf();
    }

    /**
     * Basic validation: checks connectedness of {@code allNodes} (untyped).
     *
     * @param allNodes nodes to validate
     * @return {@code true} if connected (or single-node); {@code false} if empty or disconnected
     */
    public boolean isValidStructure(Set<StructureNode> allNodes) {
        if (allNodes == null || allNodes.isEmpty()) return false;
        if (allNodes.size() == 1) return true;
        return isConnectedStructure(allNodes);
    }

    /**
     * BFS-based connectedness check (untyped).
     *
     * @param allNodes nodes to test
     * @return {@code true} if all nodes are reachable from an arbitrary start
     */
    private boolean isConnectedStructure(Set<StructureNode> allNodes) {
        if (allNodes.isEmpty()) return false;

        StructureNode startNode = allNodes.iterator().next();
        Set<StructureNode> visited = new HashSet<>();
        Queue<StructureNode> queue = new LinkedList<>();

        queue.offer(startNode);
        visited.add(startNode);

        while (!queue.isEmpty()) {
            StructureNode current = queue.poll();

            List<StructureNode> neighbors = new ArrayList<>();
            if (current.parent != null) {
                neighbors.add(current.parent);
            }
            neighbors.addAll(current.getChildren());

            for (StructureNode neighbor : neighbors) {
                if (allNodes.contains(neighbor) && !visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.offer(neighbor);
                }
            }
        }
        return visited.size() == allNodes.size();
    }

    // --------------------------------------------------------------------------------------------
    // Head status
    // --------------------------------------------------------------------------------------------

    /**
     * Sets or clears head status for a given type.
     *
     * @param typeId type
     * @param isHead {@code true} to mark as head, {@code false} to clear
     */
    public void setHead(StructureType typeId, boolean isHead) {
        if (isHead) {
            headStatus.put(typeId, true);
        } else {
            headStatus.remove(typeId);
        }
    }

    /**
     * Convenience: sets/clears head status across all {@link #nodeTypes}.
     *
     * @param isHead {@code true} to mark as head for all types, else clear
     */
    public void setHead(boolean isHead) {
        for (StructureType typeId : nodeTypes) {
            setHead(typeId, isHead);
        }
    }

    /**
     * @param typeId type to test
     * @return {@code true} if this node is head for that type
     */
    public boolean isHead(StructureType typeId) {
        return headStatus.getOrDefault(typeId, false);
    }

    /**
     * @return {@code true} if this node is head for at least one type
     */
    public boolean isHead() {
        return headStatus.values().stream().anyMatch(Boolean::booleanValue);
    }

    // --------------------------------------------------------------------------------------------
    // Legacy conveniences (DEFAULT-type fallbacks)
    // --------------------------------------------------------------------------------------------

    /**
     * Collects all nodes across <em>all</em> discovered structures this node participates in, by:
     * <ol>
     *   <li>collecting heads for each known type,</li>
     *   <li>and uniting {@link #getAllNodesInStructure(StructureType, StructureNode)} for each (type, head-id) pair.</li>
     * </ol>
     *
     * @return union of nodes across discovered structures
     */
    public Set<StructureNode> getAllNodesInStructure() {
        Set<StructureType> defaultTypes = new HashSet<>(headStatus.keySet());

        Map<AbstractMap.SimpleEntry<StructureType, Integer>, StructureNode> heads = defaultTypes.stream()
                .flatMap(type -> {
                    List<StructureNode> headsForType = new ArrayList<>();
                    StructureNode mainHead = findHead(type);
                    if (mainHead != null) {
                        headsForType.add(mainHead);
                    }
                    return headsForType.stream()
                            .map(head -> Map.entry(
                                    new AbstractMap.SimpleEntry<>(type, head.getId()),
                                    head));
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return heads.entrySet().stream()
                .flatMap(entry -> getAllNodesInStructure(entry.getKey().getKey(), entry.getValue()).stream())
                .collect(Collectors.toSet());
    }

    /**
     * Finds the head of the DEFAULT structure if possible; otherwise falls back to the first root found.
     *
     * @return default head or a root node, otherwise {@code null}
     */
    public StructureNode findHead() {
        StructureNode head = findHead(StructureType.DEFAULT);
        if (head != null) return head;

        Set<StructureNode> visited = new HashSet<>();
        Stack<StructureNode> stack = new Stack<>();
        stack.push(this);

        while (!stack.isEmpty()) {
            StructureNode current = stack.pop();
            if (visited.contains(current)) continue;
            visited.add(current);

            if (current.isRoot()) return current;

            if (current.parent != null && !visited.contains(current.parent)) {
                stack.push(current.parent);
            }
            for (StructureNode child : current.getChildren()) {
                if (!visited.contains(child)) {
                    stack.push(child);
                }
            }
        }
        return null;
    }

    /**
     * @return endpoints for the DEFAULT structure (falls back to this node as head if none found)
     */
    public Set<StructureNode> getEndpointsOfStructure() {
        StructureType defaultType = StructureType.DEFAULT;
        StructureNode head = findHead(defaultType);
        if (head == null) head = this;
        return getEndpointsOfStructure(defaultType, head);
    }

    /**
     * @return {@code true} if this node is an endpoint (degree==1) in the DEFAULT structure
     */
    public boolean isEndpoint() {
        StructureType defaultType = StructureType.DEFAULT;
        StructureNode head = findHead(defaultType);
        int headId = (head != null) ? head.getId() : this.getId();
        return isEndpoint(this, defaultType, headId);
    }

    // --------------------------------------------------------------------------------------------
    // Node classification helpers
    // --------------------------------------------------------------------------------------------

    /**
     * @param typeId type
     * @param headId head-id
     * @return {@code true} if this node has no children within that typed, head-scoped structure
     */
    public boolean isLeaf(StructureType typeId, int headId) {
        return getChildren(typeId, headId).isEmpty();
    }

    /**
     * Uses derived type/head to determine leaf status; fallback is global child emptiness.
     *
     * @return {@code true} if leaf under the derived structure
     */
    public boolean isLeaf() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        if (head == null) {
            return children.isEmpty();
        }
        return isLeaf(typeId, head.getId());
    }

    /**
     * @param typeId type
     * @param headId head-id
     * @return {@code true} if endpoint (degree==1) in that structure
     */
    public boolean isTerminal(StructureType typeId, int headId) {
        return isEndpoint(this, typeId, headId);
    }

    /**
     * Uses derived type/head to determine endpoint status; fallback counts global degree.
     *
     * @return {@code true} if terminal under the derived structure
     */
    public boolean isTerminal() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        if (head == null) {
            return (parent != null ? 1 : 0) + children.size() == 1;
        }
        return isTerminal(typeId, head.getId());
    }

    /**
     * @param typeId type
     * @return {@code true} if this node is a head and has no parent within the structure
     */
    public boolean isRoot(StructureType typeId) {
        return isHead(typeId) && (parent == null || !hasParentInStructure(typeId));
    }

    /**
     * Derived-type root check.
     *
     * @return {@code true} if root for the derived type
     */
    public boolean isRoot() {
        StructureType typeId = deriveTypeId();
        return isRoot(typeId);
    }

    /**
     * @param typeId type
     * @return {@code true} if this node has a parent edge that belongs to the (type, headId)-scoped structure
     */
    private boolean hasParentInStructure(StructureType typeId) {
        if (parent == null) return false;

        StructureNode head = findHead(typeId);
        if (head == null) return parent != null;

        Set<StructureNode> parentChildren = parent.getChildren(typeId, head.getId());
        return parentChildren.contains(this);
    }

    /**
     * @param typeId type
     * @param headId head-id
     * @return {@code true} if the number of typed, head-scoped children is below {@link #maxChildren}
     */
    public boolean canAcceptMoreChildren(StructureType typeId, int headId) {
        Set<StructureNode> currentChildren = getChildren(typeId, headId);
        return currentChildren.size() < maxChildren;
    }

    /**
     * Derived-type check for {@link #canAcceptMoreChildren(StructureType, int)}.
     *
     * @return {@code true} if more children are allowed
     */
    public boolean canAcceptMoreChildren() {
        StructureType typeId = deriveTypeId();
        StructureNode head = findHead(typeId);
        if (head == null) {
            return children.size() < maxChildren;
        }
        return canAcceptMoreChildren(typeId, head.getId());
    }

    /**
     * @return (parent connection ? 1: 0) + child count
     */
    public int getConnectivityDegree() {
        return (parent != null ? 1 : 0) + children.size();
    }

    // --------------------------------------------------------------------------------------------
    // Getters / setters
    // --------------------------------------------------------------------------------------------

    /**
     * @return node id
     */
    public int getId() {
        return id;
    }

    /**
     * @return parent node (untyped), or {@code null}
     */
    public StructureNode getParent() {
        return parent;
    }

    /**
     * Sets the parent pointer directly. Use with care—normally edges should be created via {@link #addChild(StructureNode, Set, Map)}
     * or {@link #addChild(StructureNode)} to keep child metadata consistent.
     *
     * @param parent new parent (nullable)
     */
    public void setParent(StructureNode parent) {
        this.parent = parent;
    }

    /**
     * @return maximum number of children
     */
    public int getMaxChildren() {
        return maxChildren;
    }

    /**
     * Sets the maximum number of children; values below 0 are clamped to 0.
     *
     * @param maxChildren limit (≥ 0)
     */
    public void setMaxChildren(int maxChildren) {
        this.maxChildren = Math.max(0, maxChildren);
    }

    // --------------------------------------------------------------------------------------------
    // Object overrides
    // --------------------------------------------------------------------------------------------

    /**
     * Nodes are equal if their ids are equal.
     *
     * @param obj another object
     * @return {@code true} if ids match
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof StructureNode other)) return false;
        return id == other.id;
    }

    /**
     * Hash-code based on the node id.
     *
     * @return hash
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * @return debug-friendly string with id, types, head-status and child count
     */
    @Override
    public String toString() {
        return String.format("StructureNode{id=%d, types=%s, heads=%s, children=%d}",
                id, nodeTypes, headStatus, children.size());
    }
}
