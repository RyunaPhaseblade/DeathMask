package ryuna.qdeath.yokai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.entity.yokai.BellTsukumogami;
import ryuna.qdeath.entity.yokai.ChestTsukumogami;
import ryuna.qdeath.registry.ModSounds;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Overworld encounter director, independent of the plague and of the old biome spawn tables (which no longer
 * place yokai in the Overworld). Every 90 s each group of players (within 64 blocks of each other) gets one attempt:
 * 25%, one creature, 24-48 blocks away, out of everyone's immediate view, in a loaded chunk, at most four wild yokai
 * per group. Perception and companions never change any of these numbers.
 */
public final class EncounterDirector {
    private EncounterDirector() {}

    private static long nextCycle;
    private static boolean paused;
    private static final Map<UUID, Long> RESPAWNED = new HashMap<>();
    public static int attempts, spawned, signs, budgetFull;

    public enum Result { SPAWNED, SIGN, ROLL_FAILED, BUDGET, NO_SPOT, ASLEEP }

    private static TagKey<Biome> biomes(Species s) { return TagKey.create(Registries.BIOME, Quantumdeath.id("yokai/" + s.id)); }
    private static final TagKey<Structure> RUINS = TagKey.create(Registries.STRUCTURE, Quantumdeath.id("yokai_ruins"));

    public static void pause(boolean p) { paused = p; }
    public static boolean paused() { return paused; }
    public static void respawned(ServerPlayer player) { RESPAWNED.put(player.getUUID(), player.level().getGameTime()); }
    public static void forget(UUID player) { RESPAWNED.remove(player); }
    public static void reset() { RESPAWNED.clear(); nextCycle = 0; paused = false; attempts = spawned = signs = budgetFull = 0; }

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        YokaiWorld w = YokaiWorld.of(server);
        if (!w.awakened()) return;
        if (now % 20 == 0 && w.graceLeft() > 0 && level.players().stream().anyMatch(p -> !p.isSpectator()))
            YokaiWorld.set(server, w.withGrace(w.graceLeft() - 20));
        if (paused || now < nextCycle) return;
        nextCycle = now + YokaiBalance.Director.INTERVAL;
        for (List<ServerPlayer> group : groups(level)) attempt(level, group, false, null);
    }

    /** Players of the Overworld clustered by GROUP_RADIUS: they share one attempt and one budget. */
    public static List<List<ServerPlayer>> groups(ServerLevel level) {
        List<ServerPlayer> all = new ArrayList<>(level.players().stream().filter(p -> p.isAlive() && !p.isSpectator()).toList());
        List<List<ServerPlayer>> groups = new ArrayList<>();
        double r2 = YokaiBalance.Director.GROUP_RADIUS * YokaiBalance.Director.GROUP_RADIUS;
        while (!all.isEmpty()) {
            List<ServerPlayer> g = new ArrayList<>();
            g.add(all.removeFirst());
            for (int i = 0; i < g.size(); i++) {
                ServerPlayer p = g.get(i);
                for (var it = all.iterator(); it.hasNext(); ) {
                    ServerPlayer q = it.next();
                    if (q.distanceToSqr(p) <= r2) { g.add(q); it.remove(); }
                }
            }
            groups.add(g);
        }
        return groups;
    }

    private record Spot(BlockPos pos, boolean underground, boolean water) {}

    /**
     * One attempt for one group. {@code forced} skips the 25% roll (commands); {@code only} restricts the species.
     */
    public static Result attempt(ServerLevel level, List<ServerPlayer> group, boolean forced, @Nullable Species only) {
        attempts++;
        RandomSource rng = level.getRandom();
        if (!YokaiWorld.of(level.getServer()).awakened()) return Result.ASLEEP;
        boolean grace = YokaiWorld.of(level.getServer()).inGrace();
        if (!forced && rng.nextFloat() >= YokaiBalance.Director.CHANCE) return Result.ROLL_FAILED;
        if (wildNear(level, group) >= YokaiBalance.Director.MAX_WILD) { budgetFull++; return Result.BUDGET; }
        ServerPlayer anchor = group.get(rng.nextInt(group.size()));
        long now = level.getGameTime();
        boolean offensive = !grace && group.stream().noneMatch(p -> now - RESPAWNED.getOrDefault(p.getUUID(), -100000L) < YokaiBalance.Director.RESPAWN_SAFE_TICKS);

        // Objects first: a natural chest or bell in range may stir.
        List<BlockPos> chests = Provenance.candidates(level, anchor.blockPosition(), YokaiBalance.Director.MIN_DISTANCE, YokaiBalance.Director.MAX_DISTANCE, true);
        List<BlockPos> bells = Provenance.candidates(level, anchor.blockPosition(), YokaiBalance.Director.MIN_DISTANCE, YokaiBalance.Director.MAX_DISTANCE, false);
        chests.removeIf(p -> visible(group, Vec3.atCenterOf(p)));
        bells.removeIf(p -> visible(group, Vec3.atCenterOf(p)));

        for (int tries = 0; tries < 12; tries++) {
            Spot spot = pickSpot(level, anchor, group, rng);
            Map<Species, Integer> options = new EnumMap<>(Species.class);
            if (spot != null) {
                for (Species s : habitat(level, spot)) {
                    if (s.offensive() && (!offensive || nearOccupiedBed(level, spot.pos))) continue;
                    options.put(s, weight(s));
                }
            }
            if (!chests.isEmpty()) options.put(Species.CHEST, weight(Species.CHEST));
            if (!bells.isEmpty()) options.put(Species.BELL, weight(Species.BELL));
            if (only != null) options.keySet().retainAll(List.of(only));
            if (options.isEmpty()) continue;
            Species pick = pick(options, rng);
            boolean ok = switch (pick) {
                case CHEST -> ChestTsukumogami.awaken(level, chests.get(rng.nextInt(chests.size())), null) != null;
                case BELL -> BellTsukumogami.awaken(level, bells.get(rng.nextInt(bells.size()))) != null;
                default -> spot != null && spawn(level, pick, spot) != null;
            };
            if (ok) {
                spawned++;
                return Result.SPAWNED;
            }
        }
        if (grace && rng.nextFloat() < YokaiBalance.Director.SIGN_CHANCE) {
            sign(level, anchor, rng);
            return Result.SIGN;
        }
        return Result.NO_SPOT;
    }

    private static int weight(Species s) {
        int[] w = YokaiBalance.Director.WEIGHTS;
        return s.ordinal() < w.length ? Math.max(0, w[s.ordinal()]) : 10;
    }

    private static Species pick(Map<Species, Integer> options, RandomSource rng) {
        int total = options.values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) return options.keySet().iterator().next();
        int r = rng.nextInt(total);
        for (var e : options.entrySet()) if ((r -= e.getValue()) < 0) return e.getKey();
        return options.keySet().iterator().next();
    }

    /** Wild yokai (companions excluded) near any member of the group. */
    public static int wildNear(ServerLevel level, List<ServerPlayer> group) {
        AABB box = null;
        for (ServerPlayer p : group) box = box == null ? p.getBoundingBox() : box.minmax(p.getBoundingBox());
        double r = YokaiBalance.Director.GROUP_RADIUS;
        double r2 = r * r;
        return level.getEntitiesOfClass(Mob.class, box.inflate(r), e -> e instanceof YokaiCreature y && !y.yokai().bonded()
                && group.stream().anyMatch(p -> p.distanceToSqr(e) <= r2)).size();
    }

    private static @Nullable Spot pickSpot(ServerLevel level, ServerPlayer anchor, List<ServerPlayer> group, RandomSource rng) {
        double a = rng.nextDouble() * Math.PI * 2;
        double d = YokaiBalance.Director.MIN_DISTANCE + rng.nextDouble() * (YokaiBalance.Director.MAX_DISTANCE - YokaiBalance.Director.MIN_DISTANCE);
        int x = (int) Math.floor(anchor.getX() + Math.cos(a) * d), z = (int) Math.floor(anchor.getZ() + Math.sin(a) * d);
        if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) return null; // never load chunks
        BlockPos anchorPos = anchor.blockPosition();
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        boolean anchorUnder = !level.canSeeSky(anchorPos) && anchorPos.getY() < level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchorPos.getX(), anchorPos.getZ()) - 6;
        BlockPos pos = null;
        boolean water = false;
        if (anchorUnder) {
            for (int dy = 0; dy <= 10 && pos == null; dy++) {
                for (int sgn : new int[]{-1, 1}) {
                    BlockPos p = new BlockPos(x, anchorPos.getY() + sgn * dy, z);
                    if (p.getY() < surface - 6 && standable(level, p)) { pos = p; break; }
                }
            }
        } else {
            BlockPos top = new BlockPos(x, surface, z);
            BlockState below = level.getBlockState(top.below());
            if (below.getFluidState().is(FluidTags.WATER)) {
                pos = top.below();
                water = true;
            } else if (standable(level, top)) pos = top;
        }
        if (pos == null) return null;
        for (ServerPlayer p : group) if (p.blockPosition().distSqr(pos) < YokaiBalance.Director.MIN_DISTANCE * YokaiBalance.Director.MIN_DISTANCE) return null;
        if (visible(group, Vec3.atCenterOf(pos).add(0, 0.5, 0))) return null;
        return new Spot(pos, anchorUnder, water);
    }

    private static boolean standable(ServerLevel level, BlockPos p) {
        BlockState below = level.getBlockState(p.below());
        return below.isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP) && level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty() && level.getFluidState(p).isEmpty()
                && !below.is(Blocks.LAVA) && !below.is(Blocks.MAGMA_BLOCK);
    }

    /** Inside anyone's immediate field of view, with line of sight. */
    public static boolean visible(List<ServerPlayer> group, Vec3 at) {
        double cos = Math.cos(Math.toRadians(YokaiBalance.Director.VIEW_HALF_ANGLE));
        for (ServerPlayer p : group) {
            Vec3 eye = p.getEyePosition();
            Vec3 to = at.subtract(eye);
            double len = to.length();
            if (len < 1e-3 || to.scale(1 / len).dot(p.getLookAngle()) < cos) continue;
            if (p.level().clip(new ClipContext(eye, at, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }

    private static boolean nearOccupiedBed(ServerLevel level, BlockPos pos) {
        double r2 = YokaiBalance.Director.BED_SAFE_RADIUS * YokaiBalance.Director.BED_SAFE_RADIUS;
        for (ServerPlayer p : level.players())
            if (p.isSleeping() && p.getSleepingPos().map(b -> b.distSqr(pos) <= r2).orElse(false)) return true;
        return false;
    }

    private static List<Species> habitat(ServerLevel level, Spot spot) {
        BlockPos pos = spot.pos;
        Holder<Biome> biome = level.getBiome(pos);
        boolean night = level.isDarkOutside();
        List<Species> out = new ArrayList<>();
        if (spot.water) {
            if (biome.is(biomes(Species.KAPPA)) || !(biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_BEACH)))
                out.add(Species.KAPPA);
            return out;
        }
        if (spot.underground) {
            out.add(Species.DAMPER);
            if (wideCavern(level, pos)) out.add(Species.SWARM_EYE);
            if (pos.getY() > level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - 14) out.add(Species.NURIKABE);
            return out;
        }
        boolean ruins = inStructure(level, pos, RUINS);
        if (night) out.add(Species.FALSE_CREEPER);
        if (biome.is(biomes(Species.BIFAUCE)) || nearPath(level, pos)) out.add(Species.BIFAUCE);
        if (biome.is(biomes(Species.SWARM_EYE)) || ruins) out.add(Species.SWARM_EYE);
        if (biome.is(biomes(Species.ONI)) || ruins) out.add(Species.ONI);
        if (biome.is(biomes(Species.TANUKI)) || inStructure(level, pos, StructureTags.VILLAGE)) out.add(Species.TANUKI);
        if (biome.is(biomes(Species.TENGU))) out.add(Species.TENGU);
        if (biome.is(biomes(Species.KODAMA)) && naturalLeaves(level, pos)) out.add(Species.KODAMA);
        if (nearPath(level, pos)) out.add(Species.NURIKABE);
        if (night && biome.is(biomes(Species.ITTAN_MOMEN))) out.add(Species.ITTAN_MOMEN);
        return out;
    }

    private static boolean inStructure(ServerLevel level, BlockPos pos, TagKey<Structure> tag) {
        return level.structureManager().getStructureWithPieceAt(pos, tag).isValid();
    }

    private static boolean nearPath(ServerLevel level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-4, -2, -4), pos.offset(4, 1, 4)))
            if (level.getBlockState(p).is(Blocks.DIRT_PATH)) return true;
        return false;
    }

    /** Leaves that grew there (player-placed leaves are persistent). */
    private static boolean naturalLeaves(ServerLevel level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-5, 0, -5), pos.offset(5, 8, 5))) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT)) return true;
        }
        return false;
    }

    private static boolean wideCavern(ServerLevel level, BlockPos pos) {
        int air = 0;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-3, 0, -3), pos.offset(3, 5, 3)))
            if (level.getBlockState(p).isAir()) air++;
        return air > 7 * 7 * 6 * 0.7;
    }

    /** Creates a creature of {@code species} standing at {@code spot} (flyers a little above). */
    private static @Nullable Entity spawn(ServerLevel level, Species species, Spot spot) {
        return spawnAt(level, species, Vec3.atBottomCenterOf(spot.pos).add(0, species == Species.SWARM_EYE || species == Species.FALSE_CREEPER || species == Species.ITTAN_MOMEN ? 1.5 : 0, 0),
                EntitySpawnReason.EVENT);
    }

    public static @Nullable Mob spawnAt(ServerLevel level, Species species, Vec3 at, EntitySpawnReason reason) {
        if (!(species.type().create(level, reason) instanceof Mob mob)) return null;
        mob.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360f, 0);
        if (!level.noCollision(mob)) return null;
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), reason, null);
        if (mob instanceof AnchoredYokai anchored) anchored.setAnchor(mob.blockPosition());
        return level.addFreshEntity(mob) ? mob : null;
    }

    /** A grace-period sign: a dangerous creature heard far off, with nobody there. */
    private static void sign(ServerLevel level, ServerPlayer anchor, RandomSource rng) {
        Species[] who = {Species.ONI, Species.BIFAUCE, Species.ITTAN_MOMEN, Species.DAMPER, Species.FALSE_CREEPER};
        Species s = who[rng.nextInt(who.length)];
        SoundEvent sound = switch (s) {
            case ONI -> ModSounds.ONI_STEP;
            case BIFAUCE -> ModSounds.BIFAUCE_BREATH;
            case ITTAN_MOMEN -> ModSounds.ITTAN_RUSTLE;
            case DAMPER -> ModSounds.DAMPER_CREAK;
            default -> ModSounds.FALSE_CREEPER_HISS;
        };
        double a = rng.nextDouble() * Math.PI * 2, d = 26 + rng.nextDouble() * 12;
        Vec3 at = anchor.position().add(Math.cos(a) * d, 0, Math.sin(a) * d);
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, 1.2f, 0.85f + rng.nextFloat() * 0.2f);
        Bestiary.clueNear(level, at, 40, s, s.clues.getFirst());
        signs++;
    }
}
