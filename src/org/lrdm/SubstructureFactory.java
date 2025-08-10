package org.lrdm;

// Java
import org.lrdm.topologies.node.StructureNode;
import org.lrdm.topologies.strategies.BuildAsSubstructure;

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