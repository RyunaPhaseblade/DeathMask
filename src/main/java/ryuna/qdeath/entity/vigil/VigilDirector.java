package ryuna.qdeath.entity.vigil;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.hell.HellLayer;
import ryuna.qdeath.hell.HellProgress;
import ryuna.qdeath.registry.HellEntities;
import ryuna.qdeath.registry.HellItems;
import ryuna.qdeath.registry.ModBlocks;
import ryuna.qdeath.registry.ModEffects;
import ryuna.qdeath.registry.ModSounds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Paces VIGIL's presence for each player in Hell. Dread builds slowly; events escalate from sounds and traces
 * to distant sightings and, after the player has seen enough, pursuits. Nothing ever appears suddenly in view.
 */
public final class VigilDirector {
    private VigilDirector() {}

    public enum Event { SOUND, TRACES, REMAINS, SNUFF, SIGHTING, STALK, PURSUIT }

    private static final class State {
        float tension;
        int cooldown = 20 * 90; // first minute and a half in Hell is quiet
        long lastPursuit = -100000;
        @Nullable UUID active;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    public static boolean paused;

    public static void tick(MinecraftServer server) {
        if (paused || server.getTickCount() % 20 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            HellLayer layer = HellLayer.of(player.level());
            if (layer == null || player.isSpectator() || player.isCreative() || !player.isAlive()) continue;
            HellProgress progress = HellProgress.of(player);
            if (progress.freed()) continue; // after the Zenith, Hell is silent for you
            State s = STATES.computeIfAbsent(player.getUUID(), k -> new State());
            if (s.active != null && player.level() instanceof ServerLevel lvl && lvl.getEntity(s.active) instanceof VigilEntity v && v.isAlive()) continue;
            s.active = null;
            if (s.cooldown > 0) { s.cooldown -= 20; continue; }
            ServerLevel level = (ServerLevel) player.level();
            if (VigilEntity.inSanctuary(level, player.blockPosition())) continue;
            float gain = 0.6f + 0.25f * layer.depth;
            if (level.getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(32), p -> p != player).isEmpty()) gain *= 1.4f;
            if (player.hasEffect(ModEffects.VIGILS_MARK)) gain *= 2f;
            if (wearsPilgrim(player)) gain *= 0.6f;
            if (level.getMaxLocalRawBrightness(player.blockPosition()) < 5) gain *= 1.2f;
            s.tension += gain;
            if (s.tension < 60) continue;
            Event event = choose(level.getRandom(), layer, progress.encounters(), level.getGameTime() - s.lastPursuit > 20 * 60 * 8);
            if (trigger(player, event, layer)) {
                s.tension = 0;
                int base = switch (layer) { case VESTIBULE -> 20 * 150; case GORGE -> 20 * 120; case MIRE -> 20 * 100; case DIS -> 20 * 80; case ZENITH -> 20 * 50; };
                s.cooldown = base + level.getRandom().nextInt(base);
                if (event == Event.PURSUIT) s.lastPursuit = level.getGameTime();
                if (event == Event.SIGHTING || event == Event.STALK || event == Event.PURSUIT)
                    HellProgress.set(player, progress.withEncounters(progress.encounters() + 1));
            }
        }
    }

    private static boolean wearsPilgrim(ServerPlayer p) {
        return p.getItemBySlot(EquipmentSlot.HEAD).is(HellItems.PILGRIM_HOOD) && p.getItemBySlot(EquipmentSlot.CHEST).is(HellItems.PILGRIM_ROBE)
                && p.getItemBySlot(EquipmentSlot.LEGS).is(HellItems.PILGRIM_LEGGINGS) && p.getItemBySlot(EquipmentSlot.FEET).is(HellItems.PILGRIM_BOOTS);
    }

    private static Event choose(RandomSource random, HellLayer layer, int encounters, boolean pursuitReady) {
        // The first encounters only foreshadow; sightings follow; the hunt starts once the player knows what it is.
        if (encounters == 0) return random.nextInt(3) == 0 ? Event.SIGHTING : (random.nextBoolean() ? Event.TRACES : Event.SOUND);
        int pursuitWeight = pursuitReady && encounters >= (layer == HellLayer.VESTIBULE ? 3 : 2) ? 2 + layer.depth : 0;
        int stalkWeight = layer.depth >= 2 ? 3 : 0;
        int[] weights = {3, 3, 2, 2, 4, stalkWeight, pursuitWeight};
        int total = 0;
        for (int w : weights) total += w;
        int roll = random.nextInt(total);
        for (int i = 0; i < weights.length; i++) { roll -= weights[i]; if (roll < 0) return Event.values()[i]; }
        return Event.SOUND;
    }

    /** Runs an event for {@code player}. Returns false if it could not happen here (no spot found). */
    public static boolean trigger(ServerPlayer player, Event event, HellLayer layer) {
        ServerLevel level = (ServerLevel) player.level();
        RandomSource r = level.getRandom();
        return switch (event) {
            case SOUND -> {
                SoundEvent sound = switch (r.nextInt(4)) { case 0 -> ModSounds.VIGIL_TOLL; case 1 -> ModSounds.VIGIL_WHISPER; case 2 -> ModSounds.VIGIL_BREATH; default -> ModSounds.VIGIL_STEP; };
                Vec3 behind = player.position().subtract(player.getViewVector(1f).multiply(1, 0, 1).normalize().scale(10 + r.nextInt(10)));
                int repeats = sound == ModSounds.VIGIL_STEP ? 4 : 1;
                for (int i = 0; i < repeats; i++) {
                    int delay = i * 14;
                    ryuna.qdeath.hell.HellScheduler.later(level, delay + 1, l -> sendSoundTo(player, sound, behind, 1.2f, 0.9f));
                }
                yield true;
            }
            case TRACES -> placePrints(level, player, r);
            case REMAINS -> placeRemains(level, player, r);
            case SNUFF -> snuffLights(level, player);
            case SIGHTING -> spawnVigil(player, VigilEntity.Mode.WATCH, layer, sightMin(layer), sightMin(layer) * 3 / 2, true);
            case STALK -> spawnVigil(player, VigilEntity.Mode.STALK, layer, 26, 40, false);
            case PURSUIT -> startPursuit(player, layer);
        };
    }

    /** Sightings happen at the edge of what the fog of each layer lets you see. */
    private static int sightMin(HellLayer layer) {
        return switch (layer) { case VESTIBULE -> 40; case GORGE -> 32; case MIRE -> 22; case DIS -> 42; case ZENITH -> 48; };
    }

    private static void sendSoundTo(ServerPlayer player, SoundEvent sound, Vec3 at, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(Holder.direct(sound), SoundSource.HOSTILE, at.x, at.y, at.z, volume, pitch, player.getRandom().nextLong()));
    }

    /** A line of ash footprints coming from the dark toward where the player stands. */
    private static boolean placePrints(ServerLevel level, ServerPlayer player, RandomSource r) {
        // Try a few headings until one has enough floor for the trail to read as a trail.
        for (int tries = 0; tries < 8; tries++) {
            float angle = r.nextFloat() * Mth.TWO_PI;
            Vec3 dir = new Vec3(Mth.cos(angle), 0, Mth.sin(angle));
            List<BlockPos> prints = new ArrayList<>();
            for (int i = 0; i < 14; i++) {
                double d = 30 - i * 1.6;
                Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(i % 2 == 0 ? 0.4 : -0.4);
                BlockPos p = ground(level, BlockPos.containing(player.position().add(dir.scale(d)).add(side)));
                if (p != null && level.getBlockState(p).isAir()) prints.add(p);
            }
            if (prints.size() <= 4) continue;
            for (BlockPos p : prints) level.setBlockAndUpdate(p, ModBlocks.ASH_PRINT.defaultBlockState());
            return true;
        }
        return false;
    }

    /** A ring of mourning candles around the remains of someone who came before. */
    private static boolean placeRemains(ServerLevel level, ServerPlayer player, RandomSource r) {
        // Somewhere behind the player, on the first patch of floor that will hold it.
        Vec3 back = player.getViewVector(1f).multiply(1, 0, 1).normalize().scale(-1);
        BlockPos center = null;
        for (int tries = 0; tries < 12 && center == null; tries++) {
            Vec3 dir = back.yRot((r.nextFloat() - 0.5f) * Mth.HALF_PI);
            center = ground(level, BlockPos.containing(player.position().add(dir.scale(10 + r.nextInt(16)))));
        }
        if (center == null) return false;
        level.setBlockAndUpdate(center, ModBlocks.PILGRIM_REMAINS.defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(r)));
        for (int i = 0; i < 6; i++) {
            double a = i * Mth.TWO_PI / 6;
            BlockPos c = ground(level, center.offset((int) Math.round(Math.cos(a) * 2.5), 0, (int) Math.round(Math.sin(a) * 2.5)));
            if (c != null && level.getBlockState(c).isAir()) level.setBlockAndUpdate(c, ModBlocks.MOURNING_CANDLE.defaultBlockState());
        }
        return true;
    }

    /** Lights near the player gutter out one after another. */
    private static boolean snuffLights(ServerLevel level, ServerPlayer player) {
        int n = 0;
        BlockPos c = player.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-12, -4, -12), c.offset(12, 4, 12))) {
            BlockState s = level.getBlockState(p);
            if (s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.SOUL_TORCH) || s.is(Blocks.SOUL_WALL_TORCH)) {
                BlockPos at = p.immutable();
                ryuna.qdeath.hell.HellScheduler.later(level, 10 + n * 8, l -> {
                    if (l.getBlockState(at).isAir()) return;
                    l.removeBlock(at, false);
                    l.sendParticles(ParticleTypes.SMOKE, at.getX() + 0.5, at.getY() + 0.6, at.getZ() + 0.5, 6, 0.1, 0.1, 0.1, 0.01);
                    l.playSound(null, at, net.minecraft.sounds.SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 1.0f, 0.6f);
                });
                if (++n >= 8) break;
            }
        }
        if (n == 0) return false;
        ryuna.qdeath.hell.HellScheduler.later(level, 20 + n * 8, l -> sendSoundTo(player, ModSounds.VIGIL_BREATH, player.position().add(player.getViewVector(1f).scale(-6)), 0.8f, 1f));
        return true;
    }

    private static boolean startPursuit(ServerPlayer player, HellLayer layer) {
        ServerLevel level = (ServerLevel) player.level();
        // Warning first: the world goes quiet, a bell tolls three times far away. Then it comes from out of sight.
        player.connection.send(new ClientboundStopSoundPacket(null, SoundSource.MUSIC));
        player.connection.send(new ClientboundStopSoundPacket(null, SoundSource.AMBIENT));
        sendSoundTo(player, ModSounds.VIGIL_PURSUIT, player.position().add(0, 20, 0), 2f, 1f);
        player.sendOverlayMessage(Component.translatable("message.quantumdeath.pursuit_warning." + layer.id).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        ryuna.qdeath.hell.HellScheduler.later(level, 120, l -> {
            if (player.level() == l && player.isAlive()) spawnVigil(player, VigilEntity.Mode.PURSUE, layer, 34, 46, false);
        });
        return true;
    }

    /**
     * Spawns a VIGIL manifestation at {@code min..max} blocks. Sightings appear inside the player's field of view
     * (far away, standing still); every other manifestation appears out of view.
     */
    public static boolean spawnVigil(ServerPlayer player, VigilEntity.Mode mode, HellLayer layer, int min, int max, boolean inView) {
        ServerLevel level = (ServerLevel) player.level();
        RandomSource r = level.getRandom();
        Vec3 look = player.getViewVector(1f).multiply(1, 0, 1).normalize();
        for (int attempt = 0; attempt < 40; attempt++) {
            float spread = inView ? (r.nextFloat() - 0.5f) * 1.6f : Mth.PI + (r.nextFloat() - 0.5f) * 2.4f;
            Vec3 dir = look.yRot(spread);
            double dist = min + r.nextDouble() * (max - min);
            BlockPos pos = ground(level, BlockPos.containing(player.position().add(dir.scale(dist))));
            if (pos == null || VigilEntity.inSanctuary(level, pos)) continue;
            Vec3 eye = Vec3.atBottomCenterOf(pos).add(0, 2.4, 0);
            boolean visible = level.clip(new ClipContext(player.getEyePosition(), eye, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
            if (inView != visible) continue;
            VigilEntity vigil = HellEntities.VIGIL.create(level, EntitySpawnReason.EVENT);
            if (vigil == null) return false;
            vigil.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            vigil.manifest(player, mode, layer.depth);
            level.addFreshEntity(vigil);
            STATES.computeIfAbsent(player.getUUID(), k -> new State()).active = vigil.getUUID();
            if (mode == VigilEntity.Mode.WATCH) sendSoundTo(player, ModSounds.VIGIL_PRESENCE, vigil.position(), 3.0f, 1.0f);
            return true;
        }
        return false;
    }

    /** Finds a floor (air above solid) near {@code around}, searching up and down. */
    private static @Nullable BlockPos ground(ServerLevel level, BlockPos around) {
        for (int dy = 0; dy <= 12; dy++) {
            for (int sign : new int[]{1, -1}) {
                BlockPos p = around.above(dy * sign);
                if (!level.isLoaded(p)) return null;
                if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && level.getBlockState(p.above(2)).isAir()
                        && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP) && level.getFluidState(p.below()).isEmpty()) return p;
            }
        }
        return null;
    }

    public static void forget(UUID player) { STATES.remove(player); }

    /** Something told VIGIL where the player is (a Swarm Eye's gaze, for example). */
    public static void addTension(ServerPlayer player, float amount) {
        STATES.computeIfAbsent(player.getUUID(), k -> new State()).tension += amount;
    }

    /** For commands: makes the next director tick fire an event immediately. */
    public static void provoke(ServerPlayer player) {
        State s = STATES.computeIfAbsent(player.getUUID(), k -> new State());
        s.cooldown = 0;
        s.tension = 1000;
    }
}
