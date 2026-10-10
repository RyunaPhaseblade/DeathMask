package ryuna.qdeath.limbo.plague;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.entity.limbo.LimboDead;
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.limbo.Processions;
import ryuna.qdeath.limbo.plague.PlagueState.Focus;
import ryuna.qdeath.registry.HellEntities;
import ryuna.qdeath.registry.ModBlocks;
import ryuna.qdeath.registry.ModParticles;
import ryuna.qdeath.registry.ModSounds;
import ryuna.qdeath.world.Build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Plague of the Hollows: seepages of the Limbo into the Overworld. Opening a portal allows minor foci; the
 * Toll-Keeper's defeat breaks the first seal and allows major ones. Each focus is local and finite:
 * warning, emergence in budgeted waves from its breach anchor, the hunt for life, the response, containment.
 * Nothing advances where nobody is near, nothing spreads, nobody is converted, and purging the anchor stops new waves
 * (the dead already out stay until killed or dispersed).
 */
public final class PlagueDirector {
    private PlagueDirector() {}

    private record Purge(ResourceKey<net.minecraft.world.level.Level> dim, BlockPos pos, int progress) {}

    private static final Map<UUID, Purge> PURGES = new HashMap<>();
    /** Foci that were being watched last update: an unwatched focus that comes back into view tolls once to show it resumes. */
    private static final Set<Integer> WATCHED = new HashSet<>();

    public static PlagueState state(MinecraftServer server) { return server.overworld().getAttachedOrCreate(PlagueState.TYPE); }

    private static void save(MinecraftServer server, PlagueState s) { server.overworld().setAttached(PlagueState.TYPE, s); }

    public static void init() { /* class load registers the attachment */ }

    // ------------------------------------------------------------------ triggers
    public static void portalCrossed(MinecraftServer server) {
        PlagueState s = state(server);
        if (!s.portalOpened()) save(server, s.withPortalOpened());
    }

    public static void breakFirstSeal(MinecraftServer server) {
        PlagueState s = state(server);
        if (s.sealBroken()) return;
        save(server, s.withPortalOpened().withSealBroken(true));
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            p.sendSystemMessage(Component.translatable("plague.quantumdeath.seal_broken").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
    }

    // ------------------------------------------------------------------ the tick
    public static void tick(MinecraftServer server) {
        tickPurges(server);
        if (server.getTickCount() % 20 != 0) return;
        ServerLevel level = server.overworld();
        PlagueState s = state(server);
        boolean changed = false;
        List<Focus> next = new ArrayList<>(s.foci().size());
        for (Focus f : s.foci()) {
            Focus g = advance(level, f);
            changed |= !g.equals(f);
            next.add(g);
        }
        if (s.portalOpened() && server.getTickCount() % LimboTuning.PLAGUE_CHECK_INTERVAL == 0) {
            for (ServerPlayer p : level.players()) {
                if (p.isCreative() || p.isSpectator() || next.stream().filter(Focus::open).count() >= LimboTuning.PLAGUE_MAX_ACTIVE) continue;
                boolean major = s.sealBroken() && level.getRandom().nextBoolean();
                float chance = s.sealBroken() ? LimboTuning.PLAGUE_MAJOR_CHANCE : LimboTuning.PLAGUE_MINOR_CHANCE;
                if (level.getRandom().nextFloat() >= chance) continue;
                Focus f = open(level, p, major, s.nextId() + next.size());
                if (f != null) { next.add(f); changed = true; }
            }
        }
        if (changed) save(server, trim(new PlagueState(s.portalOpened(), s.sealBroken(), Math.max(s.nextId(), maxId(next) + 1), next)));
    }

    private static int maxId(List<Focus> foci) { return foci.stream().mapToInt(Focus::id).max().orElse(0); }

    /** Contained foci are only kept as a record; the oldest are forgotten after a while. */
    private static PlagueState trim(PlagueState s) {
        List<Focus> keep = new ArrayList<>(s.foci());
        while (keep.stream().filter(f -> !f.open()).count() > 32) keep.remove(keep.stream().filter(f -> !f.open()).findFirst().orElseThrow());
        return s.withFoci(keep);
    }

    private static Focus advance(ServerLevel level, Focus f) {
        if (f.stage() >= PlagueState.EXHAUSTED) return f;
        BlockPos a = f.anchor();
        boolean watched = level.isLoaded(a) && level.getNearestPlayer(a.getX() + 0.5, a.getY(), a.getZ() + 0.5, 96, false) != null;
        if (!watched) { WATCHED.remove(f.id()); return f; } // no waves pile up while nobody is there
        if (WATCHED.add(f.id()) && f.ticks() > 0) toll(level, a, 0.7f); // it resumes where it was, and says so
        if (!level.getBlockState(a).is(ModBlocks.BREACH_ANCHOR)) return f.at(PlagueState.CONTAINED, 0);
        int t = f.ticks() + 20;
        if (f.stage() == PlagueState.WARNING) {
            if (t % 200 == 0) warn(level, a);
            if (t < LimboTuning.PLAGUE_WARNING_TICKS) return f.at(PlagueState.WARNING, t);
            announce(level, a, "plague.quantumdeath.emerging");
            return f.at(PlagueState.ACTIVE, LimboTuning.PLAGUE_WAVE_INTERVAL);
        }
        if (t % 200 == 0) toll(level, a, 0.9f);
        if (t < LimboTuning.PLAGUE_WAVE_INTERVAL) return f.at(PlagueState.ACTIVE, t);
        int size = Math.max(1, (int) Math.ceil(f.reserve() / (double) f.wavesLeft()));
        wave(level, f, size);
        Focus g = f.spent(size);
        if (g.stage() == PlagueState.EXHAUSTED) announce(level, a, "plague.quantumdeath.exhausted");
        return g;
    }

    // ------------------------------------------------------------------ opening a focus
    private static @Nullable Focus open(ServerLevel level, ServerPlayer near, boolean major, int id) {
        BlockPos site = findSite(level, near.blockPosition(), level.getRandom());
        if (site == null) return null;
        return place(level, site, major, id);
    }

    public static @Nullable Focus forceOpen(ServerPlayer player, boolean major, boolean skipWarning) {
        ServerLevel level = player.level().getServer().overworld();
        if (player.level() != level) return null;
        BlockPos site = null;
        for (int i = 0; i < 12 && site == null; i++) site = findSite(level, player.blockPosition(), level.getRandom(), 14, 22, true);
        if (site == null) {
            BlockPos here = player.blockPosition();
            ryuna.qdeath.Quantumdeath.LOGGER.warn("Plague: no site near {} (surface {}, ground {})", here, surface(level, here.getX(), here.getZ()),
                    level.getBlockState(here.below()));
            return null;
        }
        PlagueState s = state(level.getServer());
        Focus f = place(level, site, major, s.nextId());
        if (skipWarning) f = f.at(PlagueState.ACTIVE, LimboTuning.PLAGUE_WAVE_INTERVAL - 40);
        List<Focus> next = new ArrayList<>(s.foci());
        next.add(f);
        save(level.getServer(), new PlagueState(true, s.sealBroken(), s.nextId() + 1, next));
        return f;
    }

    private static Focus place(ServerLevel level, BlockPos site, boolean major, int id) {
        level.setBlockAndUpdate(site, ModBlocks.BREACH_ANCHOR.defaultBlockState());
        RandomSource r = level.getRandom();
        // Signs before the pressure: ash where no ash should be, footprints, and candles left for the dead.
        for (int i = 0; i < 10; i++) {
            BlockPos g = surface(level, site.getX() + r.nextInt(13) - 6, site.getZ() + r.nextInt(13) - 6);
            if (g == null || g.equals(site)) continue;
            BlockState below = level.getBlockState(g.below());
            if (i < 4 && natural(below)) level.setBlockAndUpdate(g.below(), ModBlocks.ASH.defaultBlockState());
            else if (i < 8) level.setBlockAndUpdate(g, ModBlocks.ASH_PRINT.defaultBlockState());
            else level.setBlockAndUpdate(g, ModBlocks.MOURNING_CANDLE.defaultBlockState());
        }
        toll(level, site, 0.6f);
        for (ServerPlayer p : level.players())
            if (p.distanceToSqr(Vec3.atCenterOf(site)) < 160 * 160)
                p.sendOverlayMessage(Component.translatable("plague.quantumdeath.warning").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        int waves = major ? LimboTuning.MAJOR_WAVES : LimboTuning.MINOR_WAVES;
        int reserve = major ? LimboTuning.MAJOR_RESERVE : LimboTuning.MINOR_RESERVE;
        return new Focus(id, site.immutable(), major, PlagueState.WARNING, 0, waves, reserve);
    }

    private static @Nullable BlockPos findSite(ServerLevel level, BlockPos near, RandomSource r) {
        for (int i = 0; i < 10; i++) {
            BlockPos s = findSite(level, near, r, LimboTuning.PLAGUE_MIN_DIST, LimboTuning.PLAGUE_MAX_DIST, false);
            if (s != null) return s;
        }
        return null;
    }

    /** {@code load}: generate the column if needed (commands only; the director never forces chunks). */
    private static @Nullable BlockPos findSite(ServerLevel level, BlockPos near, RandomSource r, int min, int max, boolean load) {
        double a = r.nextDouble() * Math.PI * 2, d = min + r.nextDouble() * (max - min);
        int x = (int) Math.floor(near.getX() + Math.cos(a) * d), z = (int) Math.floor(near.getZ() + Math.sin(a) * d);
        if (load) level.getChunk(x >> 4, z >> 4);
        BlockPos g = surface(level, x, z);
        return g != null && natural(level.getBlockState(g.below())) ? g : null;
    }

    /** First open block above the ground at (x, z), or null when that column is not loaded or is water. */
    private static @Nullable BlockPos surface(ServerLevel level, int x, int z) {
        if (!level.isLoaded(new BlockPos(x, 0, z))) return null;
        BlockPos p = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        return level.getFluidState(p).isEmpty() && level.getFluidState(p.below()).isEmpty() && level.getBlockState(p).canBeReplaced() ? p : null;
    }

    /** Only natural ground: a breach never opens on someone's floor. */
    private static boolean natural(BlockState s) {
        return s.is(BlockTags.SUBSTRATE_OVERWORLD) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW_BLOCK);
    }

    // ------------------------------------------------------------------ waves
    private static void wave(ServerLevel level, Focus f, int size) {
        BlockPos a = f.anchor();
        RandomSource r = level.getRandom();
        level.playSound(null, a, ModSounds.PLAGUE_EMERGE, SoundSource.HOSTILE, 3.0f, 0.9f);
        level.sendParticles(ModParticles.ASH_FLAKE, a.getX() + 0.5, a.getY() + 1, a.getZ() + 0.5, 60, 1.5, 1.0, 1.5, 0.03);
        int initial = f.major() ? LimboTuning.MAJOR_RESERVE : LimboTuning.MINOR_RESERVE;
        Map<Integer, EntityType<? extends LimboDead>> fresh = newMembers(f);
        for (int i = 0; i < size; i++) {
            // Which body of the reserve this is: the new creatures take fixed places in it (they never add to it).
            int index = initial - f.reserve() + i;
            EntityType<? extends LimboDead> type = fresh.containsKey(index) ? fresh.get(index) : member(f.major(), i, r);
            LimboDead d = type.create(level, EntitySpawnReason.EVENT);
            if (d == null) continue;
            BlockPos spot = null;
            for (int tries = 0; tries < 8 && spot == null; tries++) {
                double ang = r.nextDouble() * Math.PI * 2;
                BlockPos g = Processions.ground(level, (int) Math.floor(a.getX() + 0.5 + Math.cos(ang) * 2.5), (int) Math.floor(a.getZ() + 0.5 + Math.sin(ang) * 2.5), a.getY(), 4);
                if (g != null && Processions.clearOfPlayers(level, g, 1.5)) spot = g;
            }
            if (spot == null) spot = a.above(); // out of the anchor itself: always a recognizable entry
            d.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, r.nextFloat() * 360, 0);
            d.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.EVENT, null);
            d.setPlagueOrigin(f.id(), a);
            level.addFreshEntity(d);
            d.emerge(30);
        }
    }

    /**
     * The places of the expansion's creatures in a focus's reserve, the same every time for that focus (so a reload or a
     * pause changes nothing): a minor seepage gets one answer to cover (a Ticker or a thrower; never a parasite or a
     * Strangler); a major incident gets four, at least one Ticker and one thrower, at most one parasite and one Strangler.
     * They replace bodies of the reserve, which never grows.
     */
    public static Map<Integer, EntityType<? extends LimboDead>> newMembers(PlagueState.Focus f) {
        if (!LimboTuning.expansion) return Map.of();
        java.util.Random r = new java.util.Random(f.id() * 7919L + (f.major() ? 1 : 0));
        int reserve = f.major() ? LimboTuning.MAJOR_RESERVE : LimboTuning.MINOR_RESERVE;
        List<EntityType<? extends LimboDead>> types = new ArrayList<>();
        EntityType<? extends LimboDead> thrower = r.nextBoolean() ? HellEntities.BILE_SPITTER : HellEntities.RIB_THROWER;
        if (!f.major()) {
            types.add(r.nextInt(3) == 0 ? HellEntities.BREACH_TICKER : thrower);
        } else {
            types.add(HellEntities.BREACH_TICKER);
            types.add(thrower);
            List<EntityType<? extends LimboDead>> extra = new ArrayList<>(List.of(HellEntities.BREACH_TICKER, HellEntities.BILE_SPITTER, HellEntities.RIB_THROWER,
                    HellEntities.MOURNING_PARASITE, HellEntities.STRANGLER));
            java.util.Collections.shuffle(extra, r);
            types.add(extra.get(0));
            types.add(extra.get(1));
        }
        List<Integer> places = new ArrayList<>();
        for (int i = 1; i < reserve; i++) places.add(i); // the first body out is always a common dead
        java.util.Collections.shuffle(places, r);
        Map<Integer, EntityType<? extends LimboDead>> out = new HashMap<>();
        for (int i = 0; i < types.size(); i++) out.put(places.get(i), types.get(i));
        return out;
    }

    /** Mostly Hollows; minor seepages add a Runaway or a Crawler, major incidents the rest of the bestiary, elites stay rare. */
    private static EntityType<? extends LimboDead> member(boolean major, int index, RandomSource r) {
        if (index == 0 || r.nextFloat() < 0.6f) return HellEntities.HOLLOW;
        if (!major) return r.nextBoolean() ? HellEntities.RUNAWAY : HellEntities.CRAWLER;
        float x = r.nextFloat();
        if (x < 0.25f) return HellEntities.RUNAWAY;
        if (x < 0.45f) return HellEntities.CRAWLER;
        if (x < 0.6f) return HellEntities.CRIER;
        if (x < 0.75f) return HellEntities.MOURNING_HOUND;
        if (x < 0.9f) return HellEntities.SUNDERED;
        if (x < 0.97f) return HellEntities.WALKING_OSSUARY;
        return HellEntities.WAKE_BEARER;
    }

    private static void warn(ServerLevel level, BlockPos a) {
        toll(level, a, 0.6f);
        level.sendParticles(ModParticles.ASH_FLAKE, a.getX() + 0.5, a.getY() + 2, a.getZ() + 0.5, 40, 4, 2, 4, 0.01);
    }

    private static void toll(ServerLevel level, BlockPos a, float pitch) {
        level.playSound(null, a, ModSounds.PLAGUE_TOLL, SoundSource.AMBIENT, 4.0f, pitch);
    }

    private static void announce(ServerLevel level, BlockPos a, String key) {
        for (ServerPlayer p : level.players())
            if (p.distanceToSqr(Vec3.atCenterOf(a)) < 96 * 96) p.sendOverlayMessage(Component.translatable(key).withStyle(ChatFormatting.DARK_RED));
    }

    // ------------------------------------------------------------------ the purge rite
    /** Begins the purge: the player must stay beside the anchor and keep looking at it for the whole rite. */
    public static void startPurge(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (PURGES.containsKey(player.getUUID())) return;
        PURGES.put(player.getUUID(), new Purge(level.dimension(), pos.immutable(), 0));
        level.playSound(null, pos, ModSounds.PLAGUE_PURGE, SoundSource.PLAYERS, 1.5f, 0.8f);
        player.sendOverlayMessage(Component.translatable("plague.quantumdeath.purge.start").withStyle(ChatFormatting.GOLD));
    }

    private static void tickPurges(MinecraftServer server) {
        if (PURGES.isEmpty()) return;
        PURGES.entrySet().removeIf(e -> {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Purge u = e.getValue();
            if (p == null || !p.isAlive() || p.level().dimension() != u.dim) return true;
            ServerLevel level = (ServerLevel) p.level();
            boolean anchored = level.getBlockState(u.pos).is(ModBlocks.BREACH_ANCHOR);
            // Keeping the anchor in view: candles and prints around it must not break the rite, so this is a cone, not a ray.
            Vec3 to = Vec3.atCenterOf(u.pos).subtract(p.getEyePosition());
            boolean looking = p.getViewVector(1f).dot(to.normalize()) > 0.9;
            boolean near = p.distanceToSqr(Vec3.atCenterOf(u.pos)) <= 4.5 * 4.5;
            if (!anchored || !looking || !near) {
                if (anchored) p.sendOverlayMessage(Component.translatable("plague.quantumdeath.purge.interrupted").withStyle(ChatFormatting.RED));
                ryuna.qdeath.Quantumdeath.LOGGER.debug("Purge at {} interrupted: anchored={} looking={} near={}", u.pos, anchored, looking, near);
                return true;
            }
            int progress = u.progress + 1;
            if (progress % 10 == 0) {
                Vec3 c = Vec3.atCenterOf(u.pos);
                level.sendParticles(ModParticles.VOTIVE_SPARK, c.x, c.y + 0.6, c.z, 12, 1.2, 0.8, 1.2, -0.05);
                level.playSound(null, u.pos, ModSounds.PLAGUE_PURGE, SoundSource.PLAYERS, 0.6f, 0.8f + progress / (float) LimboTuning.PURGE_TICKS * 0.6f);
                int bars = progress * 10 / LimboTuning.PURGE_TICKS;
                p.sendOverlayMessage(Component.translatable("plague.quantumdeath.purge.progress", "|".repeat(bars) + ".".repeat(10 - bars)).withStyle(ChatFormatting.GOLD));
            }
            if (progress < LimboTuning.PURGE_TICKS) { e.setValue(new Purge(u.dim, u.pos, progress)); return false; }
            complete(level, p, u.pos);
            return true;
        });
    }

    private static void complete(ServerLevel level, ServerPlayer player, BlockPos pos) {
        level.setBlockAndUpdate(pos, ModBlocks.ASH.defaultBlockState());
        level.playSound(null, pos, ModSounds.PLAGUE_PURGED, SoundSource.PLAYERS, 2.0f, 1.0f);
        level.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 40, 0.6, 1.2, 0.6, 0.05);
        LootTable table = level.getServer().reloadableRegistries().getLootTable(Build.loot("gameplay/breach_purge"));
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos)).create(LootContextParamSets.CHEST);
        table.getRandomItems(params, stack -> level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, stack)));
        MinecraftServer server = level.getServer();
        if (level == server.overworld()) {
            PlagueState s = state(server);
            List<Focus> next = new ArrayList<>();
            for (Focus f : s.foci()) next.add(f.anchor().equals(pos) && f.open() ? f.at(PlagueState.CONTAINED, 0) : f);
            save(server, s.withFoci(next));
        }
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(Vec3.atCenterOf(pos)) > 64 * 64) continue;
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("plague.quantumdeath.purged.sub").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("plague.quantumdeath.purged").withStyle(ChatFormatting.GOLD)));
        }
    }

    /** Removes every open focus (and its anchor). For tests and filming. */
    public static int clear(MinecraftServer server) {
        PlagueState s = state(server);
        int n = 0;
        for (Focus f : s.foci()) if (f.open()) {
            if (server.overworld().getBlockState(f.anchor()).is(ModBlocks.BREACH_ANCHOR)) server.overworld().removeBlock(f.anchor(), false);
            n++;
        }
        save(server, PlagueState.START.withFoci(List.of()).withSealBroken(s.sealBroken()));
        if (s.portalOpened()) save(server, state(server).withPortalOpened());
        return n;
    }

    /** Brings every open focus to the eve of its next wave (tests and filming: a wave every second instead of every 35). */
    public static void hurryWaves(MinecraftServer server) {
        PlagueState s = state(server);
        List<Focus> next = new ArrayList<>();
        for (Focus f : s.foci()) next.add(f.stage() == PlagueState.ACTIVE ? f.at(PlagueState.ACTIVE, LimboTuning.PLAGUE_WAVE_INTERVAL - 20) : f);
        save(server, s.withFoci(next));
    }

    public static void forget(UUID player) { PURGES.remove(player); }

    public static void reset() { PURGES.clear(); WATCHED.clear(); }
}
