package dev.nglmercer.tiktokminecraft.actions;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;

/**
 * Resolves a configured vanilla block material.
 *
 * <p>{@code Material.isBlock()} and {@code isAir()} need the server
 * registry, which only exists inside a running server: unit tests and
 * offline tools have none. This helper always rejects malformed and
 * unknown names, enforces block-ness while the registry is available,
 * and otherwise returns the matched material so the check happens at
 * run time instead. Every caller resolves through this same helper on
 * the server thread too, so nothing unenforced ever reaches the
 * world.</p>
 */
public final class BlockMaterials {

    private BlockMaterials() {
    }

    /**
     * @param allowAir whether {@code minecraft:air} (cleaning) is accepted
     * @param allowFluids whether water and lava are accepted
     */
    public static Material resolve(String name, String param,
            boolean allowAir, boolean allowFluids) throws ActionException {
        NamespacedKey key = ParticleAction.key(name);
        if (key == null || !"minecraft".equals(key.getNamespace())) {
            throw new ActionException(
                    "Invalid vanilla block material for '" + param + "': " + name);
        }
        Material material = Material.matchMaterial(key.getKey());
        if (material == null) {
            throw new ActionException(
                    "Unknown block material for '" + param + "': " + name);
        }
        if (!registryAvailable(material)) {
            return material;
        }
        if (!material.isBlock()) {
            throw new ActionException("Material is not a placeable block for '"
                    + param + "': " + name);
        }
        if (!allowAir && material.isAir()) {
            throw new ActionException("Material must not be air for '"
                    + param + "': " + name);
        }
        if (!allowFluids
                && (material == Material.WATER || material == Material.LAVA)) {
            throw new ActionException("Material is not a placeable solid block for '"
                    + param + "': " + name);
        }
        return material;
    }

    /**
     * Probes whether the server registry answers. The first probe in a
     * registry-less JVM fails while {@code Registry} initializes
     * ({@code ExceptionInInitializerError}); later probes fail with
     * {@code NoClassDefFoundError}. Both, plus the holder's own
     * {@code IllegalStateException}, mean the same thing: enforce at run
     * time instead.
     */
    private static boolean registryAvailable(Material material) {
        try {
            material.isBlock();
            return true;
        } catch (IllegalStateException | ExceptionInInitializerError
                | NoClassDefFoundError missing) {
            return false;
        }
    }
}
