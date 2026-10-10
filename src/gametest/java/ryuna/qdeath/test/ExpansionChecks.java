package ryuna.qdeath.test;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.block.HellBlockTypes.VotiveLantern;
import ryuna.qdeath.entity.limbo.BreachTickerEntity;
import ryuna.qdeath.entity.limbo.LimboDead;
import ryuna.qdeath.entity.limbo.MourningParasiteEntity;
import ryuna.qdeath.entity.limbo.StranglerEntity;
import ryuna.qdeath.hell.HellLayer;
import ryuna.qdeath.limbo.Grips;
import ryuna.qdeath.limbo.HordePaths;
import ryuna.qdeath.limbo.LimboTuning;
import ryuna.qdeath.limbo.Mind;
import ryuna.qdeath.limbo.Possession;
import ryuna.qdeath.limbo.Processions;
import ryuna.qdeath.limbo.Provenance;
import ryuna.qdeath.limbo.Stimuli;
import ryuna.qdeath.limbo.plague.PlagueDirector;
import ryuna.qdeath.limbo.plague.PlagueState;
import ryuna.qdeath.registry.HellEntities;
import ryuna.qdeath.registry.ModBlocks;
import ryuna.qdeath.yokai.YokaiContent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static ryuna.qdeath.test.LimboExpansionTest.get;
import static ryuna.qdeath.test.LimboExpansionTest.limbo;
import static ryuna.qdeath.test.LimboExpansionTest.log;
import static ryuna.qdeath.test.LimboExpansionTest.player;

/** The functional acceptance matrix of the expansion; the fights (Ticker, tongue, throwers, senses, routes, horde) are in ExpansionCombat. */
final class ExpansionChecks {
    private final LimboExpansionTest t;
    /** State carried to the persistence phase (set by the earlier phases). */
    static UUID keptVillager, keptParasite, keptPig;
    static BlockPos keptMark;
    static int keptFocus = -1;
    static BlockPos owHome;
    static final List<UUID> keptPlagueDead = new ArrayList<>();

    ExpansionChecks(LimboExpansionTest t) { this.t = t; }

    private static ServerLevel ow(MinecraftServer s) { return s.overworld(); }

    private <T extends Entity> UUID owSpawn(EntityType<T> type, BlockPos at, float yaw, java.util.function.Consumer<T> setup) {
        return t.spawnIn(ExpansionChecks::ow, type, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, setup);
    }

    /** Standable ground near (x, z) of the Overworld test area (the chunk is loaded first). */
    private BlockPos owGround(int x, int z) {
        return t.server(s -> {
            ServerLevel l = ow(s);
            l.getChunk(x >> 4, z >> 4);
            BlockPos g = Processions.ground(l, x, z, player(s).getBlockY(), 12);
            return g != null ? g : new BlockPos(x, player(s).getBlockY(), z);
        });
    }

    private boolean possessed(UUID id) { return t.server(s -> get(s, id) instanceof Mob m && Possession.isPossessed(m)); }

    // ================================================================== possession (Overworld)
    void possession() {
        BlockPos home = t.server(s -> player(s).blockPosition());
        owHome = home;
        for (EntityType<? extends Mob> type : List.<EntityType<? extends Mob>>of(EntityTypes.PIG, EntityTypes.VILLAGER, EntityTypes.SPIDER, EntityTypes.CAVE_SPIDER))
            hostCase(type, home.offset(0, 0, 26));
        excluded(home.offset(0, 0, 26));
        interruptFixation(home.offset(0, 0, 26));
        hostDeath(home.offset(0, 0, 26));
        consecration(home.offset(0, 0, 26));
        t.tp(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
    }

    private void clearOw(BlockPos c) {
        t.run(s -> ow(s).getEntitiesOfClass(Entity.class, new AABB(c).inflate(30, 12, 30), e -> !(e instanceof ServerPlayer) && !keptPlagueDead.contains(e.getUUID())).forEach(Entity::discard));
        t.waitTicks(3);
    }

    /** What makes a host itself: identity, name, health, and (villagers) profession, level, offers and inventory. */
    private String identity(MinecraftServer s, UUID id) {
        if (!(get(s, id) instanceof Mob m)) return "gone";
        StringBuilder b = new StringBuilder(m.getUUID() + " name=" + m.getCustomName() + " hp=" + m.getHealth());
        if (m instanceof Villager v) {
            b.append(" prof=").append(v.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath()).orElse("?"))
                    .append(" lvl=").append(v.getVillagerData().level()).append(" offers=").append(v.getOffers().size())
                    .append(" inv=").append(v.getInventory().getItem(0).getCount()).append("x").append(BuiltInRegistries.ITEM.getKey(v.getInventory().getItem(0).getItem()));
        }
        return b.toString();
    }

    private void hostCase(EntityType<? extends Mob> type, BlockPos lane) {
        String name = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
        clearOw(lane);
        t.tp(lane.getX() + 0.5, lane.getY() + 8, lane.getZ() - 6.5);
        BlockPos hp = owGround(lane.getX(), lane.getZ());
        UUID host = owSpawn(type, hp, 180, m -> {
            m.setCustomName(Component.literal("Host " + name));
            if (m instanceof Villager v) {
                v.setVillagerData(v.getVillagerData().withProfession(v.level().registryAccess(), VillagerProfession.LIBRARIAN).withLevel(3));
                v.getInventory().addItem(new ItemStack(Items.BREAD, 5));
            }
        });
        t.waitTicks(5);
        t.run(s -> { if (get(s, host) instanceof Villager v) v.getOffers(); }); // offers exist before the parasite comes
        String before = t.server(s -> identity(s, host));
        UUID par = owSpawn(HellEntities.MOURNING_PARASITE, owGround(lane.getX(), lane.getZ() - 7), 0, null);
        int took = t.until(s -> get(s, host) instanceof Mob m && Possession.isPossessed(m), 900);
        t.soft(took >= 0, "possession: a " + name + " is taken by a parasite (after " + took + " ticks)");
        if (took < 0) { clearOw(lane); return; }
        boolean linked = t.server(s -> get(s, par) instanceof MourningParasiteEntity p && p.phase() == MourningParasiteEntity.POSSESSING
                && host.equals(p.hostId()) && Possession.link(get(s, host)).parasite().equals(par));
        t.soft(linked, "possession: " + name + " - the bond is saved by UUID on both sides");
        boolean mods = t.server(s -> get(s, host) instanceof Mob m && m.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Quantumdeath.id("possession_speed")) != null
                && (m.getAttribute(Attributes.ATTACK_DAMAGE) == null || m.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(Quantumdeath.id("possession_damage")) != null));
        t.soft(mods, "possession: " + name + " - temporary speed (and damage) modifiers are on");
        String during = t.server(s -> identity(s, host));
        t.soft(during.replaceAll(" hp=[0-9.]+", "").equals(before.replaceAll(" hp=[0-9.]+", "")), "possession: " + name + " keeps its identity and data while possessed (" + during + ")");
        if (type == EntityTypes.VILLAGER) {
            InteractionResult r = t.server(s -> UseEntityCallback.EVENT.invoker().interact(player(s), ow(s), InteractionHand.MAIN_HAND, get(s, host), null));
            t.soft(r == InteractionResult.FAIL, "possession: a possessed villager refuses trade and interaction (" + r + ")");
        }
        // It hunts the living for the horde: a cow next to it (wherever it went) gets attacked.
        BlockPos at = t.server(s -> get(s, host).blockPosition());
        UUID cow = owSpawn(EntityTypes.COW, owGround(at.getX() + 3, at.getZ()), 0, null);
        int bit = t.until(s -> get(s, cow) instanceof LivingEntity c && c.getHealth() < c.getMaxHealth(), 400);
        t.soft(bit >= 0, "possession: the possessed " + name + " hunts and strikes the living (" + bit + " ticks)");
        t.run(s -> { if (get(s, cow) != null) get(s, cow).discard(); });
        // Killing the parasite frees it at once, keeping the damage it took.
        float hpBefore = t.server(s -> get(s, host) instanceof Mob m ? m.getHealth() : -1f);
        t.run(s -> { if (get(s, host) instanceof Mob m) m.setHealth(Math.max(1, m.getHealth() - 2)); });
        float hurt = t.server(s -> get(s, host) instanceof Mob m ? m.getHealth() : -1f);
        t.run(s -> { if (get(s, par) instanceof MourningParasiteEntity p) p.kill(ow(s)); });
        t.waitTicks(2);
        boolean freed = t.server(s -> get(s, host) instanceof Mob m && !Possession.isPossessed(m)
                && m.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Quantumdeath.id("possession_speed")) == null);
        float after = t.server(s -> get(s, host) instanceof Mob m ? m.getHealth() : -1f);
        t.soft(freed, "possession: killing the parasite frees the " + name + " at once, modifiers removed");
        t.soft(Math.abs(after - hurt) < 0.01, "possession: the freed " + name + " keeps the damage it took (" + hpBefore + " -> " + hurt + " -> " + after + ")");
        String released = t.server(s -> identity(s, host));
        t.soft(released.replaceAll(" hp=[0-9.]+", "").equals(before.replaceAll(" hp=[0-9.]+", "")), "possession: " + name + " - identity, data and inventory intact after release (" + released + ")");
        int copies = t.server(s -> ow(s).getEntitiesOfClass(Mob.class, new AABB(lane).inflate(30, 12, 30), m -> m.getType() == type).size());
        int parasites = t.server(s -> ow(s).getEntitiesOfClass(MourningParasiteEntity.class, new AABB(lane).inflate(30, 12, 30), Entity::isAlive).size());
        t.soft(copies == 1 && parasites == 0, "possession: no duplicate " + name + " and no parasite left (" + copies + ", " + parasites + ")");
        clearOw(lane);
    }

    /** Never pets, owned creatures, shikigami, fire-dwellers or bosses. */
    private void excluded(BlockPos lane) {
        clearOw(lane);
        t.tp(lane.getX() + 0.5, lane.getY() + 8, lane.getZ() - 6.5);
        List<UUID> pets = new ArrayList<>();
        pets.add(owSpawn(EntityTypes.WOLF, owGround(lane.getX() - 2, lane.getZ()), 0, w -> w.tame(player(w.level().getServer()))));
        pets.add(owSpawn(EntityTypes.CAT, owGround(lane.getX() + 2, lane.getZ()), 0, c -> c.tame(player(c.level().getServer()))));
        pets.add(owSpawn(EntityTypes.HORSE, owGround(lane.getX(), lane.getZ() + 2), 0, h -> h.setTamed(true)));
        pets.add(owSpawn(EntityTypes.STRIDER, owGround(lane.getX(), lane.getZ() - 2), 0, null));
        pets.add(owSpawn(YokaiContent.KAPPA, owGround(lane.getX() + 3, lane.getZ() + 3), 0, null));
        owSpawn(HellEntities.MOURNING_PARASITE, owGround(lane.getX(), lane.getZ() - 6), 0, null);
        t.waitTicks(200);
        long taken = pets.stream().filter(this::possessed).count();
        boolean bossEligible = t.server(s -> Possession.eligible(HellEntities.TOLL_KEEPER.create(ow(s), EntitySpawnReason.COMMAND))
                || Possession.eligible(EntityTypes.HOGLIN.create(ow(s), EntitySpawnReason.COMMAND)) || Possession.eligible(player(s)));
        t.soft(taken == 0 && !bossEligible, "possession: pets, horses, fire-dwellers, yokai, bosses and players are never taken (" + taken + " taken)");
        clearOw(lane);
    }

    private void interruptFixation(BlockPos lane) {
        clearOw(lane);
        t.tp(lane.getX() + 0.5, lane.getY() + 8, lane.getZ() - 6.5);
        UUID pig = owSpawn(EntityTypes.PIG, owGround(lane.getX(), lane.getZ()), 0, null);
        UUID par = owSpawn(HellEntities.MOURNING_PARASITE, owGround(lane.getX(), lane.getZ() - 5), 0, null);
        int fixing = t.until(s -> get(s, par) instanceof MourningParasiteEntity p && p.phase() == MourningParasiteEntity.FIXING, 900);
        t.run(s -> { if (get(s, par) instanceof MourningParasiteEntity p) p.hurtServer(ow(s), ow(s).damageSources().playerAttack(player(s)), 1f); });
        t.waitTicks(2);
        boolean shaken = t.server(s -> get(s, par) instanceof MourningParasiteEntity p && p.phase() == MourningParasiteEntity.STUNNED);
        t.waitTicks(35);
        t.soft(fixing >= 0 && shaken && !possessed(pig), "possession: a hit while it sinks in its tendrils shakes it off (fixing " + fixing + ", stunned " + shaken + ")");
        clearOw(lane);
    }

    private void hostDeath(BlockPos lane) {
        clearOw(lane);
        t.tp(lane.getX() + 0.5, lane.getY() + 8, lane.getZ() - 6.5);
        UUID spider = owSpawn(EntityTypes.SPIDER, owGround(lane.getX(), lane.getZ()), 0, null);
        UUID par = owSpawn(HellEntities.MOURNING_PARASITE, owGround(lane.getX(), lane.getZ() - 6), 0, null);
        int took = t.until(s -> get(s, spider) instanceof Mob m && Possession.isPossessed(m), 500);
        t.run(s -> { if (get(s, spider) instanceof Mob m) m.kill(ow(s)); });
        t.waitTicks(3);
        boolean dropped = t.server(s -> get(s, par) instanceof MourningParasiteEntity p && p.isAlive() && p.phase() == MourningParasiteEntity.STUNNED && !p.attached());
        BlockPos at = t.server(s -> get(s, par) != null ? get(s, par).blockPosition() : BlockPos.ZERO);
        UUID pig = owSpawn(EntityTypes.PIG, owGround(at.getX() + 4, at.getZ()), 0, null);
        t.waitTicks(130);
        boolean waited = !possessed(pig);
        int again = t.until(s -> get(s, pig) instanceof Mob m && Possession.isPossessed(m), 900);
        t.soft(took >= 0 && dropped, "possession: when the host dies the parasite drops off alive and stunned");
        t.soft(waited && again >= 0, "possession: it waits about eight seconds before taking another host (waited " + waited + ", then " + again + " ticks)");
        clearOw(lane);
    }

    private void consecration(BlockPos lane) {
        clearOw(lane);
        t.tp(lane.getX() + 0.5, lane.getY() + 8, lane.getZ() - 6.5);
        UUID pig = owSpawn(EntityTypes.PIG, owGround(lane.getX(), lane.getZ()), 0, null);
        UUID par = owSpawn(HellEntities.MOURNING_PARASITE, owGround(lane.getX(), lane.getZ() - 5), 0, null);
        int took = t.until(s -> get(s, pig) instanceof Mob m && Possession.isPossessed(m), 900);
        BlockPos lamp = t.server(s -> get(s, pig).blockPosition().offset(2, 0, 0));
        t.run(s -> {
            ow(s).setBlockAndUpdate(lamp, ModBlocks.VOTIVE_LANTERN.defaultBlockState());
            VotiveLantern.consecrate(ow(s), lamp, ow(s).getBlockState(lamp), LimboTuning.MAX_FUEL);
        });
        int freed = t.until(s -> get(s, pig) instanceof Mob m && !Possession.isPossessed(m), 60);
        t.soft(took >= 0 && freed >= 0, "consecration: a possessed host that enters consecrated light is freed (" + freed + " ticks)");
        // Inside the light no new possession happens.
        BlockPos pen = lamp.offset(-2, 0, 2);
        t.run(s -> {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                if (dx != 0 || dz != 0) ow(s).setBlockAndUpdate(pen.offset(dx, 0, dz), Blocks.OAK_FENCE.defaultBlockState());
        });
        UUID pig2 = owSpawn(EntityTypes.PIG, pen, 0, null);
        t.run(s -> { if (get(s, par) instanceof Entity p) p.teleportTo(lamp.getX() + 9.5, lamp.getY(), lamp.getZ() + 0.5); });
        t.waitTicks(240);
        t.soft(!possessed(pig2), "consecration: no possession inside consecrated light");
        t.run(s -> {
            ow(s).removeBlock(lamp, false);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) ow(s).removeBlock(pen.offset(dx, 0, dz), false);
        });
        clearOw(lane);
    }

    // ================================================================== alliance (Limbo)
    void alliance() {
        BlockPos a = t.stage;
        t.clearStage();
        UUID pig = t.spawn(EntityTypes.PIG, a.getX() + 0.5, a.getY(), a.getZ() + 6.5, 180, m -> {
            m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(400);
            m.setHealth(400);
        });
        UUID dead = t.spawn(HellEntities.HOLLOW, a.getX() + 0.5, a.getY(), a.getZ() + 1.5, 0, null);
        t.view(a.getX() + 8.5, a.getY() + 3, a.getZ() + 4, 90, 15);
        t.run(s -> { if (get(s, dead) instanceof LimboDead d && get(s, pig) instanceof Mob m) d.engage(m); });
        t.waitTicks(10);
        UUID par = t.spawn(HellEntities.MOURNING_PARASITE, a.getX() + 4.5, a.getY(), a.getZ() + 6.5, 270, null);
        int took = t.until(s -> get(s, pig) instanceof Mob m && Possession.isPossessed(m), 900);
        t.soft(took >= 0, "alliance: the pig the dead hunt is possessed (" + took + ")");
        int dropped = t.until(s -> get(s, dead) instanceof LimboDead d && d.quarry() != get(s, pig), 80);
        t.soft(dropped >= 0, "alliance: the dead abandon a host once it is possessed (" + dropped + " ticks)");
        // It never takes it back, even struck by it (no retaliation).
        t.run(s -> { if (get(s, dead) instanceof LimboDead d && get(s, pig) instanceof Mob m) d.hurtServer(limbo(s), limbo(s).damageSources().mobAttack(m), 1f); });
        boolean ignored = true;
        for (int i = 0; i < 10; i++) {
            t.waitTicks(10);
            ignored &= t.server(s -> get(s, dead) instanceof LimboDead d && d.quarry() != get(s, pig));
        }
        t.soft(ignored, "alliance: the dead never re-target a possessed host, not even when it hits them");
        t.run(s -> { if (get(s, par) instanceof MourningParasiteEntity p) p.kill(limbo(s)); });
        t.run(s -> { if (get(s, dead) instanceof LimboDead d && get(s, pig) instanceof Mob m) d.teleportTo(m.getX(), m.getY(), m.getZ() - 4); });
        int back = t.until(s -> get(s, dead) instanceof LimboDead d && d.quarry() == get(s, pig), 200);
        t.soft(back >= 0, "alliance: once freed, the host is recognized as life again (" + back + " ticks)");
        t.clearStage();
    }

    // ================================================================== provenance (Limbo)
    void provenance() {
        BlockPos a = t.stage;
        t.clearStage();
        t.creative();
        // A player places a block with the real client: it is marked; the block it was placed against (set by command) is not.
        t.box(a.offset(0, 0, 2), a.offset(0, 2, 2), Blocks.STONE.defaultBlockState(), false);
        t.tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
        t.cmd("item replace entity @p weapon.mainhand with minecraft:cobblestone 16");
        t.ctx.getInput().lookAt(0, 0);
        t.waitTicks(5);
        t.ctx.getInput().pressKey(o -> o.keyUse);
        t.waitTicks(5);
        BlockPos placed = a.offset(0, 1, 1);
        boolean marked = t.server(s -> limbo(s).getBlockState(placed).is(Blocks.COBBLESTONE) && Provenance.isPlayerPlaced(limbo(s), placed));
        boolean against = t.server(s -> !Provenance.isPlayerPlaced(limbo(s), a.offset(0, 1, 2)));
        t.soft(marked && against, "provenance: a block placed by the player is marked; one set by command stays unknown");
        keptMark = placed;
        // A door: both halves marked; opening it keeps the mark; breaking it removes it.
        t.cmd("item replace entity @p weapon.mainhand with minecraft:oak_door");
        t.ctx.getInput().lookAt(90, 42);
        t.waitTicks(5);
        t.ctx.getInput().pressKey(o -> o.keyUse);
        t.waitTicks(5);
        BlockPos door = t.server(s -> {
            for (BlockPos p : BlockPos.betweenClosed(a.offset(-4, 0, -2), a.offset(0, 0, 2)))
                if (limbo(s).getBlockState(p).is(Blocks.OAK_DOOR)) return p.immutable();
            return null;
        });
        boolean both = door != null && t.server(s -> Provenance.isPlayerPlaced(limbo(s), door) && Provenance.isPlayerPlaced(limbo(s), door.above()));
        t.soft(both, "provenance: both halves of a placed door are marked (" + door + ")");
        if (door != null) {
            t.cmd("item replace entity @p weapon.mainhand with minecraft:air");
            t.ctx.getInput().pressKey(o -> o.keyUse); // opens it
            t.waitTicks(5);
            boolean open = t.server(s -> limbo(s).getBlockState(door).getValue(net.minecraft.world.level.block.DoorBlock.OPEN));
            boolean kept = t.server(s -> Provenance.isPlayerPlaced(limbo(s), door) && Provenance.isPlayerPlaced(limbo(s), door.above()));
            t.soft(open && kept, "provenance: opening the door keeps its mark (open " + open + ")");
            t.run(s -> limbo(s).destroyBlock(door, false));
            t.waitTicks(3);
            boolean gone = t.server(s -> !Provenance.isPlayerPlaced(limbo(s), door) && !Provenance.isPlayerPlaced(limbo(s), door.above()));
            t.soft(gone, "provenance: breaking the door removes the mark of both halves");
        }
        // A piston push: the moved block is unknown at its new place, and the old place lost its mark.
        BlockPos stone = a.offset(4, 0, -4);
        t.set(stone, Blocks.STONE.defaultBlockState(), true);
        t.set(stone.west(), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, net.minecraft.core.Direction.EAST), false);
        t.set(stone.west(2), Blocks.REDSTONE_BLOCK.defaultBlockState(), false);
        t.waitTicks(10);
        boolean pushed = t.server(s -> limbo(s).getBlockState(stone.east()).is(Blocks.STONE) && !Provenance.isPlayerPlaced(limbo(s), stone.east())
                && !Provenance.isPlayerPlaced(limbo(s), stone));
        t.soft(pushed, "provenance: a block moved by a piston is unknown where it lands, and its old place is no longer marked");
        // The operator command marks old builds, never loading a chunk.
        t.box(a.offset(-6, 0, 4), a.offset(-4, 2, 6), Blocks.STONE_BRICKS.defaultBlockState(), false);
        t.cmd("deathmask limbo provenance mark " + (a.getX() - 6) + " " + a.getY() + " " + (a.getZ() + 4) + " " + (a.getX() - 4) + " " + (a.getY() + 2) + " " + (a.getZ() + 6));
        t.waitTicks(3);
        int marks = t.server(s -> { int n = 0; for (BlockPos p : BlockPos.betweenClosed(a.offset(-6, 0, 4), a.offset(-4, 2, 6))) if (Provenance.isPlayerPlaced(limbo(s), p)) n++; return n; });
        t.soft(marks == 27, "provenance: /deathmask limbo provenance mark marks an old build (" + marks + "/27)");
        int far = 30_000;
        boolean unloadedBefore = t.server(s -> limbo(s).getChunkSource().getChunkNow(far >> 4, far >> 4) == null);
        t.cmd("deathmask limbo provenance mark " + far + " 60 " + far + " " + (far + 3) + " 61 " + (far + 3));
        t.waitTicks(3);
        boolean stillUnloaded = t.server(s -> limbo(s).getChunkSource().getChunkNow(far >> 4, far >> 4) == null);
        t.soft(unloadedBefore && stillUnloaded, "provenance: marking an area in unloaded chunks loads nothing (skipped)");
        t.clearStage();
    }

    // ================================================================== plague (Overworld)
    void plague() {
        t.cmd("deathmask plague clear");
        t.cmd("deathmask plague seal broken");
        t.cmd("deathmask plague trigger major now");
        t.waitTicks(5);
        PlagueState.Focus f = t.server(s -> PlagueDirector.state(s).foci().stream().filter(PlagueState.Focus::open).reduce((a, b) -> b).orElse(null));
        if (f == null) { t.soft(false, "plague: a major focus opens"); return; }
        keptFocus = f.id();
        for (int i = 0; i < 8; i++) {
            t.run(PlagueDirector::hurryWaves);
            t.waitTicks(45);
        }
        BlockPos anchor = f.anchor();
        List<String> types = t.server(s -> ow(s).getEntitiesOfClass(LimboDead.class, new AABB(anchor).inflate(80), d -> d.plagueFocus() == f.id())
                .stream().map(d -> BuiltInRegistries.ENTITY_TYPE.getKey(d.getType()).getPath()).toList());
        Map<String, Integer> counts = new HashMap<>();
        for (String x : types) counts.merge(x, 1, Integer::sum);
        int fresh = counts.getOrDefault("breach_ticker", 0) + counts.getOrDefault("bile_spitter", 0) + counts.getOrDefault("rib_thrower", 0)
                + counts.getOrDefault("mourning_parasite", 0) + counts.getOrDefault("strangler", 0);
        PlagueState.Focus g = t.server(s -> PlagueDirector.state(s).foci().stream().filter(x -> x.id() == f.id()).findFirst().orElse(null));
        log("plague major focus {}: released {} dead {}", f.id(), types.size(), counts);
        t.soft(types.size() == LimboTuning.MAJOR_RESERVE && g != null && g.reserve() == 0,
                "plague: the new creatures come out of the existing reserve, which never grows (" + types.size() + "/" + LimboTuning.MAJOR_RESERVE + ")");
        t.soft(fresh == 4 && counts.getOrDefault("breach_ticker", 0) >= 1 && counts.getOrDefault("bile_spitter", 0) + counts.getOrDefault("rib_thrower", 0) >= 1
                && counts.getOrDefault("mourning_parasite", 0) <= 1 && counts.getOrDefault("strangler", 0) <= 1,
                "plague: a major reserve holds four new members, at least a Ticker and a thrower, at most one parasite and one Strangler (" + counts + ")");
        boolean marked = t.server(s -> ow(s).getEntitiesOfClass(LimboDead.class, new AABB(anchor).inflate(80), d -> d.plagueFocus() == f.id())
                .stream().allMatch(d -> d.fromPlague() && anchor.equals(d.home()) && d.expanded()));
        t.soft(marked, "plague: every dead of the focus carries its origin and its anchor (and the expansion's senses)");
        keptPlagueDead.clear();
        keptPlagueDead.addAll(t.server(s -> ow(s).getEntitiesOfClass(LimboDead.class, new AABB(anchor).inflate(80), d -> d.plagueFocus() == f.id())
                .stream().limit(5).peek(d -> { d.setPersistenceRequired(); d.setNoAi(true); }).map(Entity::getUUID).toList()));
        // (kept for the restart in the persistence phase: no despawn, no hunting in the scenes between, spared by clearOw)
        // Minor and major places, across many foci (deterministic per focus).
        boolean minorOk = true, majorOk = true;
        for (int id = 1; id <= 60; id++) {
            var minor = PlagueDirector.newMembers(new PlagueState.Focus(id, BlockPos.ZERO, false, 1, 0, 3, 9));
            var major = PlagueDirector.newMembers(new PlagueState.Focus(id, BlockPos.ZERO, true, 1, 0, 5, 20));
            minorOk &= minor.size() == 1 && minor.values().stream().noneMatch(x -> x == HellEntities.MOURNING_PARASITE || x == HellEntities.STRANGLER) && !minor.containsKey(0);
            long tickers = major.values().stream().filter(x -> x == HellEntities.BREACH_TICKER).count();
            long throwers = major.values().stream().filter(x -> x == HellEntities.BILE_SPITTER || x == HellEntities.RIB_THROWER).count();
            majorOk &= major.size() == 4 && tickers >= 1 && throwers >= 1 && major.values().stream().filter(x -> x == HellEntities.MOURNING_PARASITE).count() <= 1
                    && major.values().stream().filter(x -> x == HellEntities.STRANGLER).count() <= 1 && major.equals(PlagueDirector.newMembers(new PlagueState.Focus(id, BlockPos.ZERO, true, 1, 0, 5, 20)));
        }
        t.soft(minorOk, "plague: a minor reserve holds exactly one new answer to cover, never a parasite or a Strangler (60 foci)");
        t.soft(majorOk, "plague: major reserves follow the rule and are the same for the same focus (60 foci)");
        // Purging: the real rite, the player beside the anchor looking at it; then no wave ever comes.
        t.run(s -> ow(s).getEntitiesOfClass(LimboDead.class, new AABB(anchor).inflate(80), d -> d.plagueFocus() == f.id() && !keptPlagueDead.contains(d.getUUID()))
                .forEach(Entity::discard));
        t.tp(anchor.getX() + 2.5, anchor.getY(), anchor.getZ() + 0.5);
        t.ctx.getInput().lookAt(90, 30);
        t.waitTicks(5);
        t.run(s -> PlagueDirector.startPurge(ow(s), player(s), anchor));
        int purged = t.until(s -> PlagueDirector.state(s).foci().stream().anyMatch(x -> x.id() == f.id() && x.stage() == PlagueState.CONTAINED), 200);
        t.run(PlagueDirector::hurryWaves);
        t.waitTicks(60);
        int after = t.server(s -> ow(s).getEntitiesOfClass(LimboDead.class, new AABB(anchor).inflate(80), d -> d.plagueFocus() == f.id()).size());
        t.soft(purged >= 0 && after == keptPlagueDead.size(), "plague: purging the anchor stops new waves (" + purged + " ticks, " + after + " left)");
    }

    // ================================================================== composition
    void composition() {
        RandomSource r = RandomSource.create(777);
        boolean packs = true, processions = true;
        int bodies = 0, specials = 0;
        for (Processions.Kind kind : Processions.Kind.values()) for (int size = 8; size <= 48; size++) {
            if (size > 16 && size < 24) continue;
            boolean proc = size >= 24;
            for (int k = 0; k < 40; k++) {
                List<EntityType<? extends LimboDead>> g = Processions.composition(kind, size, r);
                Map<EntityType<?>, Long> c = new HashMap<>();
                for (EntityType<?> e : g) c.merge(e, 1L, Long::sum);
                long answers = c.getOrDefault(HellEntities.BREACH_TICKER, 0L) + c.getOrDefault(HellEntities.STRANGLER, 0L)
                        + c.getOrDefault(HellEntities.BILE_SPITTER, 0L) + c.getOrDefault(HellEntities.RIB_THROWER, 0L);
                long shooters = c.getOrDefault(HellEntities.BILE_SPITTER, 0L) + c.getOrDefault(HellEntities.RIB_THROWER, 0L);
                boolean ok = g.size() == size && g.getFirst() == HellEntities.HOLLOW && c.getOrDefault(HellEntities.FOREMAN, 0L) <= 1;
                if (!proc) ok &= answers >= 1 && c.getOrDefault(HellEntities.STRANGLER, 0L) <= 1 && c.getOrDefault(HellEntities.MOURNING_PARASITE, 0L) <= 1
                        && c.getOrDefault(HellEntities.BREACH_TICKER, 0L) <= 2 && shooters <= 2;
                else ok &= answers >= 2 && c.getOrDefault(HellEntities.STRANGLER, 0L) <= 2 && c.getOrDefault(HellEntities.MOURNING_PARASITE, 0L) <= 2
                        && c.getOrDefault(HellEntities.BREACH_TICKER, 0L) <= 3 && shooters <= 4;
                if (proc) processions &= ok; else packs &= ok;
                bodies += g.size();
                specials += (int) g.stream().filter(e -> e != HellEntities.HOLLOW).count();
            }
        }
        double share = specials / (double) bodies;
        log("composition: {} bodies, {} specialists ({}%)", bodies, specials, String.format(java.util.Locale.ROOT, "%.1f", share * 100));
        t.soft(packs, "composition: every pack of 8-16 has an answer to cover or height and respects the caps (1 Strangler, 1 parasite, 2 Tickers, 2 throwers, 1 Foreman)");
        t.soft(processions, "composition: every procession of 24-48 has two answers and respects the caps (2, 2, 3, 4, 1 Foreman)");
        t.soft(share > 0.27 && share < 0.33, "composition: specialists are about 30 % of the groups (" + String.format(java.util.Locale.ROOT, "%.1f", share * 100) + " %)");
    }

    // ================================================================== regressions
    void regression() {
        BlockPos home = t.server(s -> player(s).blockPosition());
        t.survival(true);
        BlockPos z = owGround(home.getX() + 8, home.getZ());
        UUID zombie = owSpawn(EntityTypes.ZOMBIE, z, 270, null);
        int targets = t.until(s -> get(s, zombie) instanceof Zombie zz && zz.getTarget() == player(s), 200);
        boolean plain = t.server(s -> get(s, zombie) instanceof Zombie zz && !Possession.eligible(zz));
        t.soft(targets >= 0 && plain, "regression: a vanilla zombie hunts as always and is untouched by the expansion (" + targets + ")");
        t.run(s -> { if (get(s, zombie) != null) get(s, zombie).discard(); });
        // A dead out of the Limbo and not of the plague keeps the old ways: no stimuli.
        UUID dead = owSpawn(HellEntities.HOLLOW, owGround(home.getX() - 8, home.getZ()), 90, null);
        t.waitTicks(10);
        boolean old = t.server(s -> get(s, dead) instanceof LimboDead d && !d.expanded());
        t.run(s -> Stimuli.publish(ow(s), get(s, dead).position().add(3, 0, 0), Stimuli.Kind.BREAK));
        t.waitTicks(5);
        boolean deaf = t.server(s -> get(s, dead) instanceof LimboDead d && d.mind() != Mind.SEARCH && d.lastStimulus() == null);
        t.soft(old && deaf, "regression: a dead outside the Limbo that is not of the plague keeps the old behaviour (no stimuli)");
        t.run(s -> { if (get(s, dead) != null) get(s, dead).discard(); });
        t.creative();
    }

    // ================================================================== persistence (restart and unload)
    void persistence() {
        limboRestart();
        // A possessed villager and pig in the Overworld.
        BlockPos home = owHome != null ? owHome : t.server(s -> new BlockPos(0, ow(s).getMinY() + 4, 0));
        t.cmd("execute in minecraft:overworld run tp @p " + home.getX() + " " + (home.getY() + 2) + " " + home.getZ());
        t.waitTicks(40);
        BlockPos lane = home.offset(0, 0, 20);
        clearOw(lane);
        keptVillager = owSpawn(EntityTypes.VILLAGER, owGround(lane.getX(), lane.getZ()), 0, m -> {
            m.setCustomName(Component.literal("Kept"));
            m.setVillagerData(m.getVillagerData().withProfession(m.level().registryAccess(), VillagerProfession.LIBRARIAN).withLevel(2));
            m.getInventory().addItem(new ItemStack(Items.EMERALD, 3));
        });
        // The pig far enough from the villager that, once possessed, it does not hunt the villager before its parasite comes.
        keptPig = owSpawn(EntityTypes.PIG, owGround(lane.getX() + 24, lane.getZ()), 0, null);
        keptParasite = owSpawn(HellEntities.MOURNING_PARASITE, owGround(lane.getX(), lane.getZ() - 6), 0, null);
        UUID par2 = owSpawn(HellEntities.MOURNING_PARASITE, owGround(lane.getX() + 24, lane.getZ() - 6), 0, null);
        int took = t.until(s -> possessed(keptVillager) && possessed(keptPig), 600);
        t.run(s -> { if (get(s, keptVillager) instanceof Villager v) v.getOffers(); });
        t.waitTicks(5);
        String before = t.server(s -> identity(s, keptVillager));
        t.soft(took >= 0, "persistence: two hosts possessed before the restart");
        t.restart();
        t.cmd("execute in minecraft:overworld run tp @p " + home.getX() + " " + (home.getY() + 2) + " " + home.getZ());
        t.waitTicks(LimboTuning.LINK_GRACE + 60);
        String after = t.server(s -> identity(s, keptVillager));
        int villagers = t.server(s -> ow(s).getEntitiesOfClass(Villager.class, new AABB(lane).inflate(40)).size());
        int parasites = t.server(s -> ow(s).getEntitiesOfClass(MourningParasiteEntity.class, new AABB(lane).inflate(40)).size());
        boolean bonded = t.server(s -> get(s, keptVillager) instanceof Villager v && Possession.isPossessed(v)
                && get(s, keptParasite) instanceof MourningParasiteEntity p && p.phase() == MourningParasiteEntity.POSSESSING && keptVillager.equals(p.hostId()));
        t.soft(bonded, "persistence: after a restart the bond holds (host and parasite found by UUID, no break)");
        t.soft(villagers == 1 && parasites == 2, "persistence: no duplicated villager or parasite after the restart (" + villagers + ", " + parasites + ")");
        t.soft(after.replaceAll(" hp=[0-9.]+", "").equals(before.replaceAll(" hp=[0-9.]+", "")), "persistence: the host's UUID, name, profession, trades and inventory survive the restart (" + after + ")");
        // Unloading: the area is left far behind and comes back.
        t.cmd("execute in minecraft:overworld run tp @p " + (home.getX() + 600) + " " + (home.getY() + 40) + " " + home.getZ());
        t.waitTicks(200);
        boolean unloaded = t.server(s -> get(s, keptVillager) == null);
        t.cmd("execute in minecraft:overworld run tp @p " + home.getX() + " " + (home.getY() + 2) + " " + home.getZ());
        t.waitTicks(LimboTuning.LINK_GRACE + 60);
        boolean back = t.server(s -> get(s, keptVillager) instanceof Villager v && Possession.isPossessed(v) && get(s, keptParasite) instanceof MourningParasiteEntity p && p.attached());
        int villagers2 = t.server(s -> ow(s).getEntitiesOfClass(Villager.class, new AABB(lane).inflate(40)).size());
        t.soft(unloaded && back && villagers2 == 1, "persistence: unloading and reloading the area keeps one bond, no copies (unloaded " + unloaded + ")");
        // The provenance of the Limbo and the plague's marks were saved too.
        if (keptMark != null) {
            boolean mark = t.server(s -> { limbo(s).getChunk(keptMark.getX() >> 4, keptMark.getZ() >> 4); return Provenance.isPlayerPlaced(limbo(s), keptMark); });
            t.soft(mark, "persistence: a block's provenance survives the restart");
        }
        if (!keptPlagueDead.isEmpty()) {
            t.waitTicks(20);
            long plague = keptPlagueDead.stream().filter(id -> t.server(s -> get(s, id) instanceof LimboDead d && d.fromPlague() && d.plagueFocus() == keptFocus)).count();
            t.soft(plague > 0, "persistence: the plague's dead keep their origin after a restart (" + plague + "/" + keptPlagueDead.size() + " loaded)");
        }
        clearOw(lane);
        t.cmd("deathmask tp vestibule");
        t.waitTicks(60);
        t.cmd("tp @p " + (t.stage.getX() + 0.5) + " " + t.stage.getY() + " " + (t.stage.getZ() + 0.5));
        settleAfterTp();
    }

    private void settleAfterTp() { TestUtil.settle(t.ctx, t.sp, 30); }

    /**
     * The world is saved and closed while a Ticker counts on a marked wall (a pig inside is what it hunts) and a
     * Strangler holds the player: on load nothing resumes - no burst, no grip, no Ticker hanging in the air.
     */
    private void limboRestart() {
        BlockPos a = t.stage;
        t.clearStage();
        t.survival(true);
        BlockPos r = a.offset(10, 0, 0);
        var wall = Blocks.COBBLESTONE.defaultBlockState();
        t.box(r.offset(-2, 0, -2), r.offset(2, 3, 2), wall, true);
        t.box(r.offset(-1, 0, -1), r.offset(1, 2, 1), Blocks.AIR.defaultBlockState(), false);
        UUID pig = t.spawn(EntityTypes.PIG, r.getX() + 0.5, r.getY(), r.getZ() + 0.5, 0, m -> m.setNoAi(true));
        UUID tk = t.spawn(HellEntities.BREACH_TICKER, r.getX() + 4.5, r.getY(), r.getZ() + 0.5, 90, null);
        t.run(s -> { if (get(s, tk) instanceof LimboDead d && get(s, pig) instanceof LivingEntity l) d.engage(l); });
        t.tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
        UUID st = t.spawn(HellEntities.STRANGLER, a.getX() + 0.5, a.getY(), a.getZ() + 10.5, 180, null);
        t.engage(st);
        // A marked block out of the stage (stage resets clear marks with the blocks): its provenance must survive.
        keptMark = a.offset(0, 0, 30);
        t.set(keptMark, Blocks.COBBLESTONE.defaultBlockState(), true);
        int both = t.until(s -> get(s, tk) instanceof BreachTickerEntity b && b.mode() == BreachTickerEntity.ON_BARRIER
                && get(s, st) instanceof StranglerEntity x && x.tongue() == StranglerEntity.HOLDING, 300);
        boolean held = t.server(s -> Grips.held(player(s).getUUID()));
        log("restart in the Limbo: Ticker counting and tongue holding after {} ticks (held {})", both, held);
        t.restart(10);
        // Right after the load: no grip, the Ticker free and on the ground.
        String ticker = t.server(s -> get(s, tk) instanceof BreachTickerEntity b ? "mode " + b.mode() + (b.isNoGravity() ? " floating" : "") : "missing");
        boolean free = t.server(s -> !Grips.held(player(s).getUUID()) && get(s, st) instanceof StranglerEntity x && x.tongue() != StranglerEntity.HOLDING
                && player(s).getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Quantumdeath.id("tongue_drag")) == null);
        // A resumed count (60 ticks) would have burst by now; a new one cannot have (load grace, clinging, 60 ticks).
        t.waitTicks(55);
        int missing = t.server(s -> {
            int n = 0;
            for (BlockPos p : BlockPos.betweenClosed(r.offset(-2, 0, -2), r.offset(2, 3, 2)))
                if (Math.max(Math.abs(p.getX() - r.getX()), Math.abs(p.getZ() - r.getZ())) == 2 || p.getY() - r.getY() == 3)
                    if (!limbo(s).getBlockState(p).is(Blocks.COBBLESTONE)) n++;
            return n;
        });
        int copies = t.server(s -> limbo(s).getEntitiesOfClass(BreachTickerEntity.class, new AABB(a).inflate(40)).size()
                + limbo(s).getEntitiesOfClass(StranglerEntity.class, new AABB(a).inflate(40)).size());
        t.soft(both >= 0, "persistence: saved with a Ticker counting on a wall and a tongue holding the player (" + both + ")");
        t.soft(missing == 0 && ticker.equals("mode " + BreachTickerEntity.FREE), "persistence: a Ticker saved while counting does not burst on load and lands free (" + ticker + ", " + missing + " wall blocks missing)");
        t.soft(free, "persistence: a grip does not survive the restart (no hold, no drag left on the player)");
        t.soft(copies == 2, "persistence: no copies of the Ticker or the Strangler after the restart (" + copies + ")");
        t.clearStage();
        t.creative();
    }

    // ================================================================== performance
    /**
     * The same hordes (50, 100, 120 dead with the old mix of specialists) hunting the player on the same stage, measured
     * with the expansion off (the baseline) and on, alternately, twice each; p95 of the server tick over ~30 s.
     */
    void performance() {
        BlockPos a = t.stage;
        t.survival(true);
        horde(a, 50, true); // warm-up (JIT, chunk caches): not counted
        for (int n : new int[]{50, 100, 120}) {
            double[] base = new double[3], exp = new double[3];
            for (int rep = 0; rep < 3; rep++) {
                base[rep] = horde(a, n, false);
                exp[rep] = horde(a, n, true);
            }
            double bm = median(base), em = median(exp);
            log("perf horde {}: p95 baseline {} / {} / {} ms, expansion {} / {} / {} ms; median {} vs {} ms ({}%)", n,
                    r2(base[0]), r2(base[1]), r2(base[2]), r2(exp[0]), r2(exp[1]), r2(exp[2]), r2(bm), r2(em), r2((em / bm - 1) * 100));
            t.soft(em <= bm * 1.2, "performance: horde of " + n + " - median p95 server tick with the expansion within 20 % of the baseline ("
                    + r2(em) + " vs " + r2(bm) + " ms)");
        }
        LimboTuning.expansion = true;
        t.creative();
        t.clearStage();
    }

    private static double median(double[] v) {
        double[] c = v.clone();
        java.util.Arrays.sort(c);
        return c[c.length / 2];
    }

    private static String r2(double v) { return String.format(java.util.Locale.ROOT, "%.2f", v); }

    /** p95 of the server tick (ms) for a horde of {@code n} hunting the player on the stage. */
    private double horde(BlockPos a, int n, boolean expansion) {
        t.clearStage();
        LimboTuning.expansion = expansion;
        t.tp(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
        List<EntityType<? extends LimboDead>> mix = List.of(HellEntities.STALKER, HellEntities.RUNT, HellEntities.RUNAWAY, HellEntities.CRIER, HellEntities.BLOATER);
        List<EntityType<? extends LimboDead>> specialists = List.of(HellEntities.STRANGLER, HellEntities.BILE_SPITTER, HellEntities.RIB_THROWER, HellEntities.MOURNING_PARASITE);
        for (int i = 0; i < n; i++) {
            double ang = i * 2 * Math.PI / n, r = 12 + (i % 5) * 2;
            EntityType<? extends LimboDead> type = i % 7 == 3 ? mix.get(i % mix.size()) : HellEntities.HOLLOW;
            // The expanded horde carries its 30 % of new specialists (Tickers left out: they burst on the player and the count would drop).
            if (expansion && i % 10 % 3 == 2) type = specialists.get(i / 10 % specialists.size());
            UUID id = t.spawn(type, a.getX() + 0.5 + Math.cos(ang) * r, a.getY(), a.getZ() + 0.5 + Math.sin(ang) * r, 0, null);
            t.engage(id);
        }
        t.waitTicks(80);
        List<Double> ms = new ArrayList<>();
        for (int k = 0; k < 16; k++) {
            double ang = k * 0.9;
            t.tp(a.getX() + 0.5 + Math.cos(ang) * 5, a.getY(), a.getZ() + 0.5 + Math.sin(ang) * 5);
            t.waitTicks(37);
            long[] times = t.server(MinecraftServer::getTickTimesNanos).clone();
            for (long v : times) if (v > 0) ms.add(v / 1e6);
        }
        double[] arr = ms.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double p95 = arr.length == 0 ? 0 : arr[(int) Math.min(arr.length - 1, Math.floor(arr.length * 0.95))];
        log("perf sample: horde {} expansion {}: p95 {} ms over {} samples", n, expansion, r2(p95), arr.length);
        return p95;
    }

    // ================================================================== dedicated server and a second player
    void dedicated() {
        // Fabric gives the dedicated server ten seconds to start: let the machine settle after the last world closed
        // (the hordes of the performance phase take a while to save) before asking for it.
        System.gc();
        t.ctx.waitTicks(200);
        Properties props = new Properties();
        props.setProperty("online-mode", "false");
        props.setProperty("spawn-monsters", "false");
        // Room for a second real client (Fabric's test server takes one by default) without a whitelist to manage.
        props.setProperty("max-players", "4");
        props.setProperty("white-list", "false");
        try (TestDedicatedServerContext server = t.ctx.worldBuilder().createServer(props)) {
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamerule spawn_monsters false");
            UUID st2 = null, me = null;
            boolean heldBefore = false;
            try (var connection = server.connect()) {
                connection.waitForChunksRender(200);
                String name = server.computeOnServer(s -> player(s).getGameProfile().name());
                server.runCommand("op " + name);
                // The remote player joins in survival: protected from the start (a possessed pig hunts it before the tongue tests).
                server.runCommand("effect give " + name + " minecraft:resistance infinite 255 true");
                server.runCommand("effect give " + name + " minecraft:saturation infinite 0 true");
                server.runCommand("deathmask vigil pause");
                server.runCommand("deathmask limbo processions off");
                server.runCommand("execute as " + name + " at " + name + " run deathmask tp vestibule");
                t.waitTicks(60);
                connection.waitForChunksRender(200);
                BlockPos p = server.computeOnServer(s -> player(s).blockPosition());
                server.runOnServer(s -> {
                    ServerLevel l = limbo(s);
                    for (int x = -12; x <= 12; x++) for (int zz = -12; zz <= 12; zz++) {
                        l.setBlockAndUpdate(p.offset(x, -1, zz), ModBlocks.ASHEN_BRICKS.defaultBlockState());
                        for (int y = 0; y < 5; y++) l.setBlockAndUpdate(p.offset(x, y, zz), Blocks.AIR.defaultBlockState());
                    }
                });
                t.waitTicks(10);
                // Every new creature reaches the client with its type (renderers and synced data are exercised by drawing them).
                List<EntityType<?>> types = List.of(HellEntities.MOURNING_PARASITE, HellEntities.BREACH_TICKER, HellEntities.STRANGLER, HellEntities.BILE_SPITTER, HellEntities.RIB_THROWER);
                server.runOnServer(s -> {
                    int i = 0;
                    for (EntityType<?> type : types) {
                        Entity e = type.create(limbo(s), EntitySpawnReason.COMMAND);
                        e.snapTo(p.getX() + 0.5 - 6 + i * 3, p.getY(), p.getZ() + 6.5, 180, 0);
                        if (e instanceof Mob m) { m.setNoAi(true); m.setPersistenceRequired(); }
                        limbo(s).addFreshEntity(e);
                        i++;
                    }
                });
                t.waitTicks(40);
                long seen = t.ctx.computeOnClient(mc -> types.stream().filter(type -> !mc.level.getEntities(type, mc.player.getBoundingBox().inflate(20), e -> true).isEmpty()).count());
                t.ctx.getInput().lookAt(0, 10);
                t.waitTicks(10);
                t.ctx.takeScreenshot("exp_dedicated_bestiary");
                t.soft(seen == types.size(), "dedicated server: every new creature is sent to and drawn by a remote client (" + seen + "/" + types.size() + ")");
                // Possession over the network: the client sees the host's bond.
                UUID pig = server.computeOnServer(s -> {
                    Mob m = EntityTypes.PIG.create(limbo(s), EntitySpawnReason.COMMAND);
                    m.snapTo(p.getX() + 6.5, p.getY(), p.getZ() - 4.5, 0, 0);
                    limbo(s).addFreshEntity(m);
                    Mob par = HellEntities.MOURNING_PARASITE.create(limbo(s), EntitySpawnReason.COMMAND);
                    par.snapTo(p.getX() + 6.5, p.getY(), p.getZ() + 1.5, 180, 0);
                    limbo(s).addFreshEntity(par);
                    return m.getUUID();
                });
                int taken = -1;
                for (int k = 0; k < 250 && taken < 0; k++) {
                    t.waitTicks(2);
                    if (server.computeOnServer(s -> get(s, pig) instanceof Mob m && Possession.isPossessed(m))) taken = k;
                }
                t.waitTicks(10);
                boolean clientSees = t.ctx.computeOnClient(mc -> mc.level.getEntities(EntityTypes.PIG, mc.player.getBoundingBox().inflate(20), Possession::isPossessed).size() == 1);
                t.soft(taken >= 0 && clientSees, "dedicated server: a possession is synced to the remote client (" + taken + ", client " + clientSees + ")");
                // A real client player held by a tongue is dragged toward the Strangler by the network velocity.
                server.runCommand("gamemode survival " + name);
                server.runOnServer(s -> limbo(s).getEntitiesOfClass(Entity.class, new AABB(p).inflate(20), m -> !(m instanceof ServerPlayer)).forEach(Entity::discard));
                UUID st = server.computeOnServer(s -> {
                    StranglerEntity m = HellEntities.STRANGLER.create(limbo(s), EntitySpawnReason.COMMAND);
                    m.snapTo(p.getX() + 0.5, p.getY(), p.getZ() + 10.5, 180, 0);
                    limbo(s).addFreshEntity(m);
                    m.engage(player(s));
                    return m.getUUID();
                });
                int held = -1;
                for (int k = 0; k < 200 && held < 0; k++) {
                    t.waitTicks(2);
                    if (server.computeOnServer(s -> get(s, st) instanceof StranglerEntity m && m.tongue() == StranglerEntity.HOLDING)) held = k;
                }
                double d0 = t.ctx.computeOnClient(mc -> mc.player.position().distanceTo(new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 10.5)));
                String where = server.computeOnServer(s -> "player " + player(s).position().subtract(Vec3.atLowerCornerOf(p)) + " creative " + player(s).isCreative()
                        + (get(s, st) instanceof StranglerEntity m ? " strangler " + m.position().subtract(Vec3.atLowerCornerOf(p)) + " tongue " + m.tongue() + " " + m.debugState() : " no strangler"));
                log("dedicated drag: held after {}, {}", held, where);
                t.waitTicks(40);
                double d1 = t.ctx.computeOnClient(mc -> mc.player.position().distanceTo(new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 10.5)));
                t.soft(held >= 0 && d0 - d1 > 2.0, "dedicated server: the tongue drags the remote player toward the Strangler (" + r2(d0) + " -> " + r2(d1) + ")");
                // A second player (a server-side FakePlayer in the world): its own Strangler, the rescue, a single grip each.
                multiplayer(server, p, st);
                // A second real client, a whole game of its own, joins over the network.
                twoClients(server, p, name);
                // Disconnecting while held: the grip ends and leaves nothing on the player. The player back in the middle,
                // nothing else around, no immunity left from the grips before.
                server.runOnServer(s -> limbo(s).getEntitiesOfClass(Entity.class, new AABB(p).inflate(30), m -> !(m instanceof ServerPlayer)).forEach(Entity::discard));
                server.runCommand("execute in quantumdeath:vestibule run tp " + name + " " + (p.getX() + 0.5) + " " + p.getY() + " " + (p.getZ() + 0.5));
                t.waitTicks(LimboTuning.TONGUE_IMMUNITY + 10);
                st2 = server.computeOnServer(s -> {
                    StranglerEntity m = HellEntities.STRANGLER.create(limbo(s), EntitySpawnReason.COMMAND);
                    m.snapTo(p.getX() + 0.5, p.getY(), p.getZ() + 9.5, 180, 0);
                    limbo(s).addFreshEntity(m);
                    m.engage(player(s));
                    return m.getUUID();
                });
                UUID captor = st2;
                for (int k = 0; k < 300; k++) {
                    t.waitTicks(2);
                    if (server.computeOnServer(s -> get(s, captor) instanceof StranglerEntity m && m.tongue() == StranglerEntity.HOLDING)) break;
                }
                me = server.computeOnServer(s -> player(s).getUUID());
                UUID victim = me;
                heldBefore = server.computeOnServer(s -> Grips.held(victim));
                String state = server.computeOnServer(s -> "player " + player(s).position().subtract(Vec3.atLowerCornerOf(p)) + " alive " + player(s).isAlive() + " canGrab " + Grips.canGrab(player(s))
                        + (get(s, captor) instanceof StranglerEntity m ? " strangler " + m.position().subtract(Vec3.atLowerCornerOf(p)) + " tongue " + m.tongue() + " " + m.debugState() : " no strangler"));
                log("dedicated disconnect: {}", state);
            } // the connection closes here: the client leaves the server
            // The server handles the disconnection on its own thread: poll it (up to 10 s) rather than guess.
            UUID captor = st2, victim = me;
            boolean released = false;
            for (int k = 0; k < 100 && !released && captor != null; k++) {
                t.waitTicks(2);
                // With nobody left there the Limbo may stop ticking or unload the Strangler: what matters is that no grip is
                // registered and the player is gone (a Strangler that ticks again lets go of a removed victim at once).
                released = server.computeOnServer(s -> !Grips.held(victim) && get(s, victim) == null
                        && (!(get(s, captor) instanceof StranglerEntity m) || m.tongue() != StranglerEntity.HOLDING || m.tongueEnd(1f) == null));
            }
            t.soft(heldBefore && released, "dedicated server: a disconnection ends the grip (held " + heldBefore + ", released " + released + ")");
        } catch (Exception e) {
            Quantumdeath.LOGGER.error("EXPAND dedicated phase error", e);
            t.soft(false, "dedicated server phase ran without errors (" + e + ")");
        }
    }

    // ================================================================== two real clients
    private static final String ALLY = "Ally";

    private static ServerPlayer ally(MinecraftServer s) { return s.getPlayerList().getPlayerByName(ALLY); }

    /**
     * Launches a second Minecraft client (the same game and mods as this one, without the test runner, its own game
     * directory) that joins the dedicated server by itself, and checks with it what needs two real players: a tongue
     * dragging a remote player whose own client moves it, two real players held at once by their own Strangler, the
     * rescue of one by the other, and the grip ending when that client goes away. The process is always closed.
     */
    private void twoClients(TestDedicatedServerContext server, BlockPos p, String name) {
        Process proc = null;
        try {
            int port = server.computeOnServer(MinecraftServer::getPort);
            // The test client runs in <project>/build/run/clientGameTest (its launch properties are consumed at start-up).
            java.nio.file.Path project = java.nio.file.Path.of("").toAbsolutePath().getParent().getParent().getParent();
            java.nio.file.Path cfg = project.resolve(".gradle/loom-cache/launch.cfg");
            java.nio.file.Path dir = project.resolve("build/run/secondClient");
            java.nio.file.Files.createDirectories(dir);
            // A quiet client: no first-run screens, no pause on lost focus, little to draw, no sound.
            java.nio.file.Files.writeString(dir.resolve("options.txt"), String.join("\n",
                    "onboardAccessibility:false", "pauseOnLostFocus:false", "renderDistance:4", "simulationDistance:5", "maxFps:30",
                    "soundCategory_master:0.0", "joinedFirstServer:true", "skipMultiplayerWarning:true", "tutorialStep:none") + "\n");
            List<String> cmd = List.of(ProcessHandle.current().info().command().orElse("java"),
                    "-Dfabric.dli.config=" + cfg, "-Dfabric.dli.env=client", "-Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient",
                    "@" + project.resolve("build/loom-cache/argFiles/runClientGameTest"), "-Xmx2G", "-XX:StackShadowPages=32",
                    "--sun-misc-unsafe-memory-access=allow", "--enable-native-access=ALL-UNNAMED", "-Dfile.encoding=UTF-8",
                    "net.fabricmc.devlaunchinjector.Main", "--username", ALLY, "--gameDir", dir.toString(), "--quickPlayMultiplayer", "localhost:" + port);
            ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).redirectOutput(dir.resolve("client.log").toFile());
            pb.environment().remove("JAVA_TOOL_OPTIONS");
            proc = pb.start();
            int joined = -1;
            for (int k = 0; k < 180 && joined < 0 && proc.isAlive(); k++) {
                t.waitTicks(20);
                if (server.computeOnServer(s -> ally(s) != null)) joined = k;
            }
            log("two clients: the second client joined after {} s (alive {})", joined, proc.isAlive());
            t.soft(joined >= 0, "two clients: a second real client launches and joins the dedicated server (" + joined + " s)");
            if (joined < 0) return;
            t.waitTicks(100); // its chunks and its first ticks
            server.runOnServer(s -> limbo(s).getEntitiesOfClass(Entity.class, new AABB(p).inflate(30), m -> !(m instanceof ServerPlayer)).forEach(Entity::discard));
            for (String who : List.of(name, ALLY)) {
                server.runCommand("effect give " + who + " minecraft:resistance infinite 255 true");
                server.runCommand("effect give " + who + " minecraft:saturation infinite 0 true");
                server.runCommand("gamemode survival " + who);
            }
            server.runCommand("execute in quantumdeath:vestibule run tp " + name + " " + (p.getX() + 0.5) + " " + p.getY() + " " + (p.getZ() + 0.5));
            server.runCommand("execute in quantumdeath:vestibule run tp " + ALLY + " " + (p.getX() + 6.5) + " " + p.getY() + " " + (p.getZ() + 0.5));
            t.waitTicks(LimboTuning.TONGUE_IMMUNITY + 40); // the second client loads the Limbo; the first loses its immunity
            boolean there = server.computeOnServer(s -> ally(s) != null && ally(s).level() == limbo(s) && ally(s).distanceToSqr(Vec3.atBottomCenterOf(p)) < 100);
            t.soft(there, "two clients: the second real player stands in the Limbo beside the first");
            // A Strangler for each: both real players held at once, one grip each; the second one is dragged by its own client.
            UUID[] st = server.computeOnServer(s -> {
                UUID[] ids = new UUID[2];
                ServerPlayer[] victims = {player(s), ally(s)};
                double[] xs = {0.5, 6.5};
                for (int i = 0; i < 2; i++) {
                    StranglerEntity m = HellEntities.STRANGLER.create(limbo(s), EntitySpawnReason.COMMAND);
                    m.snapTo(p.getX() + xs[i], p.getY(), p.getZ() + 10.5, 180, 0);
                    limbo(s).addFreshEntity(m);
                    m.engage(victims[i]);
                    ids[i] = m.getUUID();
                }
                return ids;
            });
            int both = -1;
            for (int k = 0; k < 200 && both < 0; k++) {
                t.waitTicks(2);
                if (server.computeOnServer(s -> ally(s) != null && Grips.held(player(s)) && Grips.held(ally(s)))) both = k;
            }
            boolean ownGrip = server.computeOnServer(s -> get(s, st[0]) instanceof StranglerEntity a && get(s, st[1]) instanceof StranglerEntity b
                    && a.tongue() == StranglerEntity.HOLDING && b.tongue() == StranglerEntity.HOLDING);
            t.soft(both >= 0 && ownGrip, "two clients: two real players are held at once, each by its own Strangler (" + both + ")");
            double a0 = server.computeOnServer(s -> ally(s).position().distanceTo(get(s, st[1]).position()));
            t.waitTicks(30);
            double a1 = server.computeOnServer(s -> ally(s).position().distanceTo(get(s, st[1]).position()));
            t.soft(a0 - a1 > 1.0, "two clients: the second client's player is dragged toward its Strangler (" + r2(a0) + " -> " + r2(a1) + ")");
            // The first real player cuts the tongue holding the second (the attack of a real player on a length of it).
            boolean rescued = server.computeOnServer(s -> {
                if (!(get(s, st[1]) instanceof StranglerEntity m) || m.tongue() != StranglerEntity.HOLDING) return false;
                var seg = limbo(s).getEntitiesOfClass(ryuna.qdeath.entity.limbo.TongueSegment.class, m.getBoundingBox().inflate(20), x -> x.owner() == m)
                        .stream().findFirst().orElse(null);
                if (seg == null) return false;
                player(s).attack(seg);
                return m.tongue() != StranglerEntity.HOLDING && !Grips.held(ally(s));
            });
            t.soft(rescued, "two clients: one real player cuts the tongue holding the other (rescue)");
            // Held again, then its client goes away: the grip ends with it.
            server.runOnServer(s -> limbo(s).getEntitiesOfClass(StranglerEntity.class, new AABB(p).inflate(30)).forEach(Entity::discard));
            t.waitTicks(LimboTuning.TONGUE_IMMUNITY + 10);
            UUID allyId = server.computeOnServer(s -> ally(s).getUUID());
            server.runOnServer(s -> {
                StranglerEntity m = HellEntities.STRANGLER.create(limbo(s), EntitySpawnReason.COMMAND);
                m.snapTo(p.getX() + 6.5, p.getY(), p.getZ() + 10.5, 180, 0);
                limbo(s).addFreshEntity(m);
                m.engage(ally(s));
            });
            int held = -1;
            for (int k = 0; k < 200 && held < 0; k++) {
                t.waitTicks(2);
                if (server.computeOnServer(s -> Grips.held(allyId))) held = k;
            }
            proc.destroy();
            proc.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            boolean released = false;
            for (int k = 0; k < 100 && !released; k++) {
                t.waitTicks(2);
                released = server.computeOnServer(s -> !Grips.held(allyId) && ally(s) == null);
            }
            t.soft(held >= 0 && released, "two clients: when the second client quits while held, the grip ends (held " + held + ", released " + released + ")");
        } catch (Exception e) {
            Quantumdeath.LOGGER.error("EXPAND two clients error", e);
            t.soft(false, "two clients ran without errors (" + e + ")");
        } finally {
            if (proc != null && proc.isAlive()) {
                proc.destroyForcibly();
                try { proc.waitFor(30, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            }
        }
    }

    private void multiplayer(TestDedicatedServerContext server, BlockPos p, UUID firstStrangler) {
        UUID friend = server.computeOnServer(s -> {
            ServerLevel l = limbo(s);
            FakePlayer fp = FakePlayer.get(l, new GameProfile(UUID.fromString("0f0f0f0f-0000-4000-8000-000000000042"), "Second"));
            fp.snapTo(p.getX() + 6.5, p.getY(), p.getZ() + 0.5, 0, 0);
            fp.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            l.addNewPlayer(fp);
            return fp.getUUID();
        });
        boolean inWorld = server.computeOnServer(s -> limbo(s).getEntity(friend) instanceof FakePlayer fp && Processions.class != null && ryuna.qdeath.limbo.LimboSenses.isLife(fp));
        log("multiplayer: second player in the world {}", inWorld);
        // The real player is still held by the first Strangler (or is grabbed again): the friend cuts the tongue.
        for (int k = 0; k < 300; k++) {
            if (server.computeOnServer(s -> get(s, firstStrangler) instanceof StranglerEntity m && m.tongue() == StranglerEntity.HOLDING)) break;
            t.waitTicks(2);
        }
        boolean rescued = server.computeOnServer(s -> {
            if (!(get(s, firstStrangler) instanceof StranglerEntity m) || m.tongue() != StranglerEntity.HOLDING) return false;
            var seg = limbo(s).getEntitiesOfClass(ryuna.qdeath.entity.limbo.TongueSegment.class, m.getBoundingBox().inflate(20)).stream().findFirst().orElse(null);
            if (seg == null || !(limbo(s).getEntity(friend) instanceof FakePlayer fp)) return false;
            fp.attack(seg);
            return m.tongue() != StranglerEntity.HOLDING && !Grips.held(player(s));
        });
        t.soft(rescued, "multiplayer: a second player cuts the tongue holding the first (rescue)");
        // Two Stranglers, two players: each holds its own victim, never the same one twice. Fresh ones for both, once
        // the rescued player is no longer immune, so the two grips come together.
        server.runOnServer(s -> limbo(s).getEntitiesOfClass(StranglerEntity.class, new AABB(p).inflate(30)).forEach(Entity::discard));
        t.waitTicks(LimboTuning.TONGUE_IMMUNITY + 10);
        server.runOnServer(s -> {
            StranglerEntity m = HellEntities.STRANGLER.create(limbo(s), EntitySpawnReason.COMMAND);
            m.snapTo(p.getX() + 0.5, p.getY(), p.getZ() + 10.5, 180, 0);
            limbo(s).addFreshEntity(m);
            m.engage(player(s));
        });
        UUID other = server.computeOnServer(s -> {
            StranglerEntity m = HellEntities.STRANGLER.create(limbo(s), EntitySpawnReason.COMMAND);
            m.snapTo(p.getX() + 6.5, p.getY(), p.getZ() + 10.5, 180, 0);
            limbo(s).addFreshEntity(m);
            if (limbo(s).getEntity(friend) instanceof FakePlayer fp) m.engage(fp);
            return m.getUUID();
        });
        int both = -1;
        for (int k = 0; k < 400 && both < 0; k++) {
            t.waitTicks(2);
            if (server.computeOnServer(s -> Grips.held(friend) && Grips.held(player(s)))) both = k;
        }
        boolean distinct = server.computeOnServer(s -> {
            List<StranglerEntity> holding = limbo(s).getEntitiesOfClass(StranglerEntity.class, new AABB(p).inflate(30), m -> m.tongue() == StranglerEntity.HOLDING);
            return holding.size() <= 2;
        });
        t.soft(both >= 0 && distinct, "multiplayer: two players are held by their own Strangler, one grip each (" + both + ")");
        // Possession stays single: two parasites, one pig.
        server.runOnServer(s -> limbo(s).getEntitiesOfClass(Entity.class, new AABB(p).inflate(30), m -> !(m instanceof ServerPlayer)).forEach(Entity::discard));
        UUID pig = server.computeOnServer(s -> {
            Mob m = EntityTypes.PIG.create(limbo(s), EntitySpawnReason.COMMAND);
            m.snapTo(p.getX() - 5.5, p.getY(), p.getZ() + 0.5, 0, 0);
            limbo(s).addFreshEntity(m);
            for (int i = 0; i < 2; i++) {
                Mob par = HellEntities.MOURNING_PARASITE.create(limbo(s), EntitySpawnReason.COMMAND);
                par.snapTo(p.getX() - 5.5 + (i == 0 ? -6 : 6), p.getY(), p.getZ() + 0.5, 0, 0);
                limbo(s).addFreshEntity(par);
            }
            return m.getUUID();
        });
        for (int k = 0; k < 450; k++) {
            t.waitTicks(2);
            if (server.computeOnServer(s -> get(s, pig) instanceof Mob m && Possession.isPossessed(m))) break;
        }
        t.waitTicks(60);
        long bonded = server.computeOnServer(s -> limbo(s).getEntitiesOfClass(MourningParasiteEntity.class, new AABB(p).inflate(30), m -> m.phase() == MourningParasiteEntity.POSSESSING).size());
        boolean single = server.computeOnServer(s -> get(s, pig) instanceof Mob m && Possession.isPossessed(m));
        t.soft(single && bonded == 1, "multiplayer: one host, one parasite: the second parasite never takes the same host (" + bonded + ")");
        server.runOnServer(s -> { if (limbo(s).getEntity(friend) instanceof FakePlayer fp) limbo(s).removePlayerImmediately(fp, Entity.RemovalReason.DISCARDED); });
    }

    // ================================================================== the fights
    // ================================================================== the demo commands (Limbo)
    /** Every /deathmask limbo demo builds what it says and the scene plays out by itself, facing south on the stage. */
    void demo() {
        BlockPos a = t.stage;
        // The bestiary: each new spawn egg, used with the real client on the floor ahead, brings its creature.
        demoStage(a);
        t.creative();
        StringBuilder eggs = new StringBuilder();
        boolean allEggs = true;
        for (String id : List.of("mourning_parasite", "breach_ticker", "strangler", "bile_spitter", "rib_thrower")) {
            t.run(s -> limbo(s).getEntitiesOfClass(LimboDead.class, new AABB(a).inflate(24, 16, 24)).forEach(Entity::discard));
            t.cmd("item replace entity @p weapon.mainhand with quantumdeath:" + id + "_spawn_egg");
            t.ctx.getInput().lookAt(a.offset(0, -1, 3));
            t.waitTicks(3);
            t.ctx.getInput().pressKey(o -> o.keyUse);
            t.waitTicks(5);
            boolean ok = t.server(s -> limbo(s).getEntitiesOfClass(LimboDead.class, new AABB(a).inflate(8),
                    d -> BuiltInRegistries.ENTITY_TYPE.getKey(d.getType()).getPath().equals(id)).size() == 1);
            allEggs &= ok;
            eggs.append(id).append(ok ? " ok " : " missing ");
        }
        t.cmd("item replace entity @p weapon.mainhand with minecraft:air");
        t.soft(allEggs, "demo: each new spawn egg, used by the player, brings its creature (" + eggs.toString().trim() + ")");
        t.cmd("gamerule mob_griefing true");
        t.survival(true);
        AABB area = new AABB(a).inflate(24, 16, 24);
        // possession: a pig, a villager and a spider ahead, a parasite behind them; one of them is taken.
        demoStage(a);
        t.cmd("deathmask limbo demo possession");
        boolean spawned = t.server(s -> !limbo(s).getEntitiesOfClass(net.minecraft.world.entity.animal.pig.Pig.class, area).isEmpty()
                && !limbo(s).getEntitiesOfClass(Villager.class, area).isEmpty() && !limbo(s).getEntitiesOfClass(net.minecraft.world.entity.monster.spider.Spider.class, area).isEmpty()
                && limbo(s).getEntitiesOfClass(MourningParasiteEntity.class, area).size() == 1);
        int taken = t.until(s -> limbo(s).getEntitiesOfClass(Mob.class, area, Possession::isPossessed).size() == 1, 900);
        t.soft(spawned && taken >= 0, "demo: possession - a pig, a villager, a spider and a parasite; one host is taken (" + taken + " ticks)");
        // breach: a marked shelter (5 x 4 x 5, 73 blocks) around the player and a Ticker outside, which opens it.
        demoStage(a);
        t.cmd("deathmask limbo demo breach");
        int wall = t.server(s -> {
            int n = 0;
            for (BlockPos p : BlockPos.betweenClosed(a.offset(-2, 0, -2), a.offset(2, 3, 2)))
                if (limbo(s).getBlockState(p).is(Blocks.COBBLESTONE) && Provenance.isPlayerPlaced(limbo(s), p)) n++;
            return n;
        });
        int opened = t.until(s -> {
            int n = 0;
            for (BlockPos p : BlockPos.betweenClosed(a.offset(-2, 0, -2), a.offset(2, 3, 2))) if (limbo(s).getBlockState(p).is(Blocks.COBBLESTONE)) n++;
            return n < 73;
        }, 500);
        t.soft(wall == 73 && opened >= 0, "demo: breach - a marked shelter of 73 blocks around the player (" + wall + ") that the Ticker opens (" + opened + " ticks)");
        // the single creatures: each comes and does its thing.
        demoStage(a);
        t.cmd("deathmask limbo demo tongue");
        int tongue = t.until(s -> limbo(s).getEntitiesOfClass(StranglerEntity.class, area, m -> m.tongue() == StranglerEntity.HOLDING).size() == 1, 400);
        t.soft(tongue >= 0, "demo: tongue - the Strangler grabs the player (" + tongue + " ticks)");
        demoStage(a);
        t.cmd("deathmask limbo demo bile");
        int bile = t.until(s -> !limbo(s).getEntitiesOfClass(ryuna.qdeath.entity.projectile.BileBolus.class, area).isEmpty(), 400);
        t.soft(bile >= 0, "demo: bile - the Bile Spitter throws (" + bile + " ticks)");
        demoStage(a);
        t.cmd("deathmask limbo demo ribs");
        int ribs = t.until(s -> !limbo(s).getEntitiesOfClass(ryuna.qdeath.entity.projectile.RibShard.class, area).isEmpty(), 400);
        t.soft(ribs >= 0, "demo: ribs - the Rib Thrower throws (" + ribs + " ticks)");
        demoStage(a);
        t.cmd("deathmask limbo demo ticker");
        int leap = t.until(s -> limbo(s).getEntitiesOfClass(BreachTickerEntity.class, area, b -> b.mode() == BreachTickerEntity.ON_VICTIM).size() == 1, 400);
        t.soft(leap >= 0, "demo: ticker - the Ticker leaps onto the player (" + leap + " ticks)");
        // mound: a marked tower of 4 under the player and enough dead around it, who pile up.
        demoStage(a);
        t.cmd("deathmask limbo demo mound 4");
        int tower = t.server(s -> {
            int n = 0;
            for (BlockPos p : BlockPos.betweenClosed(a.offset(-1, 0, -1), a.offset(1, 3, 1)))
                if (limbo(s).getBlockState(p).is(Blocks.COBBLESTONE) && Provenance.isPlayerPlaced(limbo(s), p)) n++;
            return n;
        });
        double top = t.server(s -> player(s).getY() - a.getY());
        int dead = t.server(s -> limbo(s).getEntitiesOfClass(LimboDead.class, area).size());
        int formed = t.until(s -> !limbo(s).getEntitiesOfClass(LimboDead.class, area, d -> d.getY() >= a.getY() + 3.95).isEmpty(), 600);
        t.soft(tower == 36 && Math.abs(top - 4) < 0.1 && dead == ryuna.qdeath.limbo.Horde.crowdFor(4) && formed >= 0,
                "demo: mound - a marked tower of 36 blocks (" + tower + "), the player on top (" + top + "), " + dead + " dead who climb each other onto it (" + formed + " ticks)");
        t.creative();
        t.clearStage();
    }

    // ================================================================== natural appearance (Limbo)
    private static final Set<String> NEW_TYPES = Set.of("mourning_parasite", "breach_ticker", "strangler", "bile_spitter", "rib_thrower");
    private static final Set<String> ANSWERS = Set.of("breach_ticker", "strangler", "bile_spitter", "rib_thrower");

    /** The dead loaded around the player now, by UUID, with their type's path. */
    private Map<UUID, String> deadNear(int r) {
        return t.server(s -> {
            Map<UUID, String> m = new HashMap<>();
            for (LimboDead d : limbo(s).getEntitiesOfClass(LimboDead.class, player(s).getBoundingBox().inflate(r, 64, r)))
                m.put(d.getUUID(), BuiltInRegistries.ENTITY_TYPE.getKey(d.getType()).getPath());
            return m;
        });
    }

    /** What came since {@code before}: type -> count. */
    private Map<String, Integer> newcomers(Map<UUID, String> before, int r) {
        Map<String, Integer> out = new java.util.TreeMap<>();
        deadNear(r).forEach((id, type) -> { if (!before.containsKey(id)) out.merge(type, 1, Integer::sum); });
        return out;
    }

    /**
     * The new creatures turn up by the game's own means, not by command: the biomes' natural spawning (gamerule
     * spawn_monsters) and the Limbo director's packs and processions, each with its answers to cover and height.
     */
    void natural() {
        BlockPos a = t.stage;
        t.clearStage();
        t.survival(true);
        t.run(s -> limbo(s).getEntitiesOfClass(LimboDead.class, player(s).getBoundingBox().inflate(128, 64, 128)).forEach(Entity::discard));
        // The biomes' natural spawning, for up to two minutes.
        Map<UUID, String> before = deadNear(128);
        t.cmd("gamerule spawn_mobs true");
        t.cmd("gamerule spawn_monsters true");
        Map<String, Integer> spawned = Map.of();
        for (int k = 0; k < 24; k++) {
            t.waitTicks(100);
            spawned = newcomers(before, 128);
            if (spawned.keySet().stream().filter(NEW_TYPES::contains).count() >= 2) break;
        }
        t.cmd("gamerule spawn_monsters false");
        t.cmd("gamerule spawn_mobs false");
        int total = spawned.values().stream().mapToInt(Integer::intValue).sum();
        long fresh = spawned.entrySet().stream().filter(e -> NEW_TYPES.contains(e.getKey())).mapToInt(Map.Entry::getValue).sum();
        log("natural spawning: {} dead appeared on their own {}", total, spawned);
        t.soft(total > 0 && fresh > 0, "natural: the biomes spawn the new creatures by themselves among the others (" + fresh + " of " + total + ")");
        t.run(s -> limbo(s).getEntitiesOfClass(LimboDead.class, player(s).getBoundingBox().inflate(128, 64, 128)).forEach(Entity::discard));
        // The director, as in play (processions on, the player in survival): its packs (8-16) bring at least one answer
        // to cover or height, its processions (24-48) at least two. Groups are told apart by their size.
        t.cmd("deathmask limbo processions on");
        boolean packsOk = true, procOk = true;
        int packsSeen = 0, procSeen = 0;
        StringBuilder groups = new StringBuilder();
        for (int i = 0; i < 6 && (packsSeen < 2 || procSeen < 1); i++) {
            boolean forceProcession = procSeen == 0 && i >= 2;
            Map<UUID, String> pre = deadNear(96);
            t.run(s -> ryuna.qdeath.limbo.LimboDirector.hurry(player(s), forceProcession));
            Map<String, Integer> group = Map.of();
            for (int k = 0; k < 30 && group.isEmpty(); k++) { t.waitTicks(10); group = newcomers(pre, 96); }
            t.waitTicks(5);
            group = newcomers(pre, 96);
            int size = group.values().stream().mapToInt(Integer::intValue).sum();
            int answers = group.entrySet().stream().filter(e -> ANSWERS.contains(e.getKey())).mapToInt(Map.Entry::getValue).sum();
            if (size >= 17) { procSeen++; procOk &= answers >= 2; }
            else if (size > 0) { packsSeen++; packsOk &= size >= 6 && answers >= 1; }
            groups.append(size).append(':').append(group).append(' ');
            t.run(s -> limbo(s).getEntitiesOfClass(LimboDead.class, player(s).getBoundingBox().inflate(128, 64, 128)).forEach(Entity::discard));
        }
        log("director groups: {}", groups);
        t.soft(packsSeen >= 2 && packsOk, "natural: the director's packs carry at least one answer to cover or height (" + packsSeen + " packs)");
        t.soft(procSeen >= 1 && procOk, "natural: the director's processions carry at least two answers (" + procSeen + " processions)");
        t.cmd("deathmask limbo processions off");
        t.run(s -> limbo(s).getEntitiesOfClass(LimboDead.class, player(s).getBoundingBox().inflate(128, 64, 128)).forEach(Entity::discard));
        t.creative();
        t.clearStage();
    }

    /** A clean stage, the player in the middle facing south, healed. */
    private void demoStage(BlockPos a) {
        t.clearStage();
        t.cmd("tp @p " + (a.getX() + 0.5) + " " + a.getY() + " " + (a.getZ() + 0.5) + " 0 0");
        t.run(s -> player(s).setHealth(player(s).getMaxHealth()));
        t.waitTicks(5);
    }

    void ticker() { new ExpansionCombat(t).ticker(); }
    void tongue() { new ExpansionCombat(t).tongue(); }
    void shooters() { new ExpansionCombat(t).shooters(); }
    void senses() { new ExpansionCombat(t).senses(); }
    void navigation() { new ExpansionCombat(t).navigation(); }
    void horde() { new ExpansionCombat(t).horde(); }
}
