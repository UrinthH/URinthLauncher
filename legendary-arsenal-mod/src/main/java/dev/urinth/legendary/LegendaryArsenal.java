package dev.urinth.legendary;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Legendary Arsenal: the whole mod is one built-in resource pack that Fabric API offers in Options > Resource Packs and switches on by
 * default. The pack gives every sword, axe and mace a detailed 3D model that is used ONLY in the player's own first-person hands (the
 * item definitions select on display_context); every other view of the item falls back to the normal vanilla model.
 *
 * There is no code that touches rendering, no mixin and no file the mod writes, so the only thing that can go wrong is the pack itself,
 * and the player can switch that off in the Resource Packs screen.
 */
public final class LegendaryArsenal implements ClientModInitializer {
    public static final String MOD_ID = "legendary_arsenal";
    /** The folder under resourcepacks/ inside the jar. */
    public static final String PACK_NAME = "legendary_arsenal";

    private static final Logger LOG = LoggerFactory.getLogger("LegendaryArsenal");

    @Override
    public void onInitializeClient() {
        try {
            FabricLoader.getInstance().getModContainer(MOD_ID).ifPresent(mod -> {
                boolean ok = ResourceManagerHelper.registerBuiltinResourcePack(
                    ResourceLocation.fromNamespaceAndPath(MOD_ID, PACK_NAME), mod,
                    Component.literal("Legendary Arsenal"), ResourcePackActivationType.DEFAULT_ENABLED);
                LOG.info("[Legendary Arsenal] built-in resource pack {}", ok ? "registered" : "was NOT found inside the mod");
            });
        } catch (Throwable t) {
            LOG.warn("[Legendary Arsenal] could not register the resource pack", t);
        }
    }
}
