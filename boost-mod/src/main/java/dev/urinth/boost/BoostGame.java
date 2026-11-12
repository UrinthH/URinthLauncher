package dev.urinth.boost;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The game side of the mod. It owns the {@link BoostRuntime}, reads and writes the settings file, feeds the runtime
 * the clock and the frame rate, and turns what the hooks are given (a particle, an entity, a render state) into the
 * plain numbers the runtime decides on.
 *
 * Nothing that happens here may ever hurt the game: every hook catches everything. If something goes wrong the mod
 * switches itself off for the rest of the session (see {@link BoostRuntime#disable}) and the game carries on as if
 * the mod were not there.
 *
 * Only what is drawn is looked at. Nothing here sends a packet, changes an entity, or changes a game option.
 */
public final class BoostGame {
    static final Logger LOGGER = LoggerFactory.getLogger("urinth_boost");

    /** Bigger than this (in blocks) a mob is never left out: bosses and the like are seen from far away. */
    private static final float SMALL_MOB_WIDTH = 2.0f;
    private static final float SMALL_MOB_HEIGHT = 3.2f;

    private static volatile BoostRuntime runtime;
    private static Path settingsFile;
    private static Bench.Result shownBench;
    private static int loggedLevel = -1;
    private static int shownSecondsLeft = -1;
    private static int[] rankIds = new int[64];
    private static double[] rankDist = new double[64];

    private BoostGame() {}

    public static BoostRuntime runtime() {
        return runtime;
    }

    // ---- start-up and the settings file ----

    static void init() {
        settingsFile = FabricLoader.getInstance().getConfigDir().resolve(BoostConfig.FILE_NAME);
        BoostConfig.Settings settings = BoostConfig.parse(read(settingsFile));
        if (!Files.exists(settingsFile)) write(settingsFile, BoostConfig.DEFAULT_TEXT);
        runtime = new BoostRuntime(settings.mode, settings.targetFps, settings.players);
        LOGGER.info("[URinth Boost] mode {}, aiming for {} fps, other players {}. Hooks: new particles, far "
                + "items/stands/small mobs, name tags, armor of far players, the farthest players in a crowd. "
                + "Type /uboost in the game for the state.", settings.mode.word(), settings.targetFps,
                BoostConfig.playersWord(settings.players));
    }

    /** Writes the current mode and target to the settings file. */
    static void save() {
        BoostRuntime r = runtime;
        if (r != null && settingsFile != null) {
            write(settingsFile, BoostConfig.render(r.mode(), r.target(), r.playersSetting()));
        }
    }

    private static String read(Path file) {
        try {
            return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("[URinth Boost] could not read {}: {}", file, e.toString());
            return null;
        }
    }

    private static void write(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("[URinth Boost] could not write {}: {}", file, e.toString());
        }
    }

    // ---- the clock ----

    static void startTick(Minecraft minecraft) {
        BoostRuntime r = runtime;
        if (r != null) r.newTick();
    }

    static void endTick(Minecraft minecraft) {
        BoostRuntime r = runtime;
        if (r == null) return;
        try {
            boolean inWorld = minecraft.level != null && minecraft.player != null;
            r.tick(System.nanoTime() / 1_000_000L, minecraft.getFps(), inWorld);
            rankPlayers(minecraft, r);

            int level = r.level();
            if (level != loggedLevel) {
                loggedLevel = level;
                LOGGER.info("[URinth Boost] level {} ({} fps, aim {}) {}", BoostLevel.name(level), r.lastFps(), r.target(),
                        r.governorNote());
            }
            showBench(minecraft, r);
        } catch (Throwable error) {
            fail(r, "the clock", error);
        }
    }

    /**
     * Once per tick: when only the nearest players are to be drawn, tells the runtime who the other players are and how
     * far each is from you. Looks only; nothing about any player is changed.
     */
    private static void rankPlayers(Minecraft minecraft, BoostRuntime r) {
        ClientLevel level = minecraft.level;
        LocalPlayer me = minecraft.player;
        if (level == null || me == null) {
            r.clearPlayers();
            return;
        }
        List<? extends Player> players = level.players();
        int size = players.size();
        if (!r.wantsPlayerRanks()) {
            r.setPlayersAround(size - 1);
            r.clearPlayers();
            return;
        }
        if (rankIds.length < size) {
            rankIds = new int[size + 32];
            rankDist = new double[size + 32];
        }
        Entity camera = minecraft.getCameraEntity();
        int count = 0;
        for (int i = 0; i < size; i++) {
            Player other = players.get(i);
            if (other == me || other == camera) continue; // you, and the one the camera is on, are always drawn
            rankIds[count] = other.getId();
            rankDist[count] = me.distanceToSqr(other);
            count++;
        }
        r.updatePlayers(rankIds, rankDist, count);
    }

    private static void showBench(Minecraft minecraft, BoostRuntime r) {
        if (minecraft.player == null) return;
        Bench bench = r.bench();
        if (bench.active()) {
            int left = bench.secondsLeft();
            if (left != shownSecondsLeft) {
                shownSecondsLeft = left;
                minecraft.player.displayClientMessage(Component.literal(BoostText.benchProgress(left, bench.level())), true);
            }
            return;
        }
        shownSecondsLeft = -1;
        Bench.Result result = r.lastBench();
        if (result != null && result != shownBench) {
            shownBench = result;
            for (String line : BoostText.benchResult(result)) {
                minecraft.player.displayClientMessage(Component.literal(line), false);
            }
        }
    }

    // ---- the hooks: game objects in, yes or no out ----

    /** A new particle is about to be added to the game. True: do not add it. */
    public static boolean dropParticle(Particle particle) {
        BoostRuntime r = runtime;
        if (r == null) return false;
        try {
            Player player = Minecraft.getInstance().player;
            if (r.level() == BoostLevel.OFF || player == null) {
                r.stats().call(BoostStats.PARTICLE);
                return false;
            }
            AABB box = particle.getBoundingBox();
            double dx = (box.minX + box.maxX) * 0.5 - player.getX();
            double dy = (box.minY + box.maxY) * 0.5 - player.getEyeY();
            double dz = (box.minZ + box.maxZ) * 0.5 - player.getZ();
            return r.dropParticle(dx * dx + dy * dy + dz * dz);
        } catch (Throwable error) {
            fail(r, "the particle hook", error);
            return false;
        }
    }

    /** An entity that is otherwise about to be drawn this frame. True: leave it out. */
    public static boolean hideEntity(Entity entity, double cameraX, double cameraY, double cameraZ) {
        BoostRuntime r = runtime;
        if (r == null) return false;
        try {
            if (entity instanceof Player) {
                // Only the farthest ones in a crowd are ever left out; you and the camera's player are never listed.
                return r.hidePlayer(entity.getId());
            }
            if (r.level() == BoostLevel.OFF) {
                r.stats().call(BoostStats.ENTITY);
                return false;
            }
            EntityKind kind = kindOf(entity);
            double distSq = kind == EntityKind.KEEP ? 0 : entity.distanceToSqr(cameraX, cameraY, cameraZ);
            return r.hideEntity(kind, distSq);
        } catch (Throwable error) {
            fail(r, "the entity hook", error);
            return false;
        }
    }

    /** A name tag is about to be drawn. True: leave it out. */
    public static boolean hideNameTag(EntityRenderState state) {
        BoostRuntime r = runtime;
        if (r == null) return false;
        try {
            return r.hideNameTag(state.distanceToCameraSq);
        } catch (Throwable error) {
            fail(r, "the name tag hook", error);
            return false;
        }
    }

    /** The armor, held items and so on of a living thing are about to be drawn. True: leave them out. */
    public static boolean hideLayers(EntityRenderState state) {
        BoostRuntime r = runtime;
        if (r == null) return false;
        try {
            // Only people-shaped things (players, zombies, skeletons): a sheep without its wool or a villager without
            // its clothes would look wrong, and those layers are not the expensive ones anyway.
            if (!(state instanceof HumanoidRenderState)) {
                r.stats().call(BoostStats.LAYERS);
                return false;
            }
            return r.hideLayers(state.distanceToCameraSq);
        } catch (Throwable error) {
            fail(r, "the armor hook", error);
            return false;
        }
    }

    static EntityKind kindOf(Entity entity) {
        if (entity instanceof Player) return EntityKind.KEEP;
        if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) return EntityKind.ITEM;
        if (entity instanceof ArmorStand) return EntityKind.ARMOR_STAND;
        if (entity instanceof Mob && entity.getBbWidth() < SMALL_MOB_WIDTH && entity.getBbHeight() < SMALL_MOB_HEIGHT) {
            return EntityKind.MOB;
        }
        return EntityKind.KEEP;
    }

    private static void fail(BoostRuntime r, String where, Throwable error) {
        r.disable(where + " failed: " + error);
        LOGGER.error("[URinth Boost] {} failed, so the mod switched itself off until the game is restarted", where, error);
    }
}
