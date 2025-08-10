package org.lrdm;

// Java
import org.lrdm.topologies.node.StructureNode;
import org.lrdm.topologies.strategies.BuildAsSubstructure;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Factory for creating {@link BuildAsSubstructure}s.
 * Allows registering and cycle through different {@link BuildAsSubstructure}s.
 */

public final class SubstructureFactory {
    private final Map<StructureNode.StructureType, Supplier<BuildAsSubstructure>> registry =
            new EnumMap<>(StructureNode.StructureType.class);

    private final List<BuildAsSubstructure> created = new ArrayList<>();
    private final Map<StructureNode.StructureType, List<BuildAsSubstructure>> createdByType =
            new EnumMap<>(StructureNode.StructureType.class);

    public SubstructureFactory register(StructureNode.StructureType type,
                                        Supplier<BuildAsSubstructure> supplier) {
        registry.put(type, supplier);
        return this;
    }

    public SubstructureFactory registerAll(Map<StructureNode.StructureType, Supplier<BuildAsSubstructure>> map) {
        registry.putAll(map);
        return this;
    }

    public static SubstructureFactory fromSuppliers(
            Map<StructureNode.StructureType, Supplier<BuildAsSubstructure>> map) {
        return new SubstructureFactory().registerAll(map);
    }

    // Erzeugt, initialisiert (Network setzen + Zustand leeren) und speichert die Instanz
    public BuildAsSubstructure createAndInit(StructureNode.StructureType type, Network n) {
        Supplier<BuildAsSubstructure> s = registry.get(type);
        if (s == null) {
            throw new IllegalArgumentException("Kein Supplier registriert für: " + type);
        }
        BuildAsSubstructure instance = s.get();

        // Network-Kontext setzen und Strukturzustand leeren
        instance.initializeInternalState(n);
        instance.resetInternalStateStructureOnly();

        // Speicherung
        created.add(instance);
        createdByType.computeIfAbsent(type, k -> new ArrayList<>()).add(instance);

        return instance;
    }

    // Zyklische Auswahl: index modulo Rotation → erzeugen, initialisieren, speichern
    public BuildAsSubstructure createCycledAndInit(int index,
                                                   List<StructureNode.StructureType> rotation,
                                                   Network n) {
        if (rotation == null || rotation.isEmpty()) {
            throw new IllegalArgumentException("Rotation darf nicht leer sein");
        }
        int pos = Math.floorMod(index, rotation.size());
        return createAndInit(rotation.get(pos), n);
    }

    // Zugriff auf erzeugte Instanzen (read-only)
    public List<BuildAsSubstructure> getCreated() {
        return List.copyOf(created);
    }

    public List<BuildAsSubstructure> getCreated(StructureNode.StructureType type) {
        return List.copyOf(createdByType.getOrDefault(type, List.of()));
    }

    public BuildAsSubstructure create(StructureNode.StructureType type) {
        Supplier<BuildAsSubstructure> s = registry.get(type);
        if (s == null) {
            throw new IllegalArgumentException("Kein Supplier für: " + type);
        }
        return s.get();
    }

    // Zyklische Auswahl: index wird modulo der Rotation genommen
    public BuildAsSubstructure createCycled(int index, List<StructureNode.StructureType> rotation) {
        if (rotation == null || rotation.isEmpty()) {
            throw new IllegalArgumentException("Rotation darf nicht leer sein");
        }
        int pos = Math.floorMod(index, rotation.size());
        return create(rotation.get(pos));
    }
}