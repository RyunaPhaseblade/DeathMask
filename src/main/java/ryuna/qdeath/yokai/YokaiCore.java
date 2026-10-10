package ryuna.qdeath.yokai;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Shared server state of one yokai: encounters per player (who it has detected, and whether a partial-sight pursuit
 * was rolled for that encounter), its bond when it is a shikigami, ritual progress per player and combat time.
 */
public final class YokaiCore {
    public enum Order { FOLLOW, STAY, DEFEND, PASSIVE }

    /** One encounter with one player: begins at a new detection, ends after ENCOUNTER_TIMEOUT without detecting. */
    public static final class Encounter {
        public long start, lastSeen;
        public boolean chase;
    }

    /** Deterministic ritual progress of one player with this creature. */
    public static final class Ritual {
        public int stage, count;
        public long time;
    }

    public final Species species;
    private final Mob mob;
    private final Map<UUID, Encounter> encounters = new HashMap<>();
    private final Map<UUID, Ritual> rituals = new HashMap<>();

    // Bond (null owner = wild)
    private @Nullable UUID owner;
    private @Nullable UUID bondId;
    private long token;
    private Order order = Order.FOLLOW;
    private long lastCombat = -100000;
    /** Demonstration: hostile behaviour is held back until this game time. */
    private long calmUntil;

    /** Set right before the entity is removed into an amulet (or as a stale copy): objects must not be restored then. */
    public boolean storing;

    private @Nullable ServerPlayer ownerCache;
    private int ownerCacheTick = -1;

    public YokaiCore(Species species, Mob mob) {
        this.species = species;
        this.mob = mob;
    }

    private long now() { return mob.level().getGameTime(); }

    // ------------------------------------------------------------------------------------------------ encounters

    /** The creature notices {@code player}: starts a new encounter (rolling the partial-sight pursuit once) or extends one. */
    public Encounter detect(ServerPlayer player) {
        long t = now();
        Encounter e = encounters.get(player.getUUID());
        if (e == null || t - e.lastSeen > YokaiBalance.Perception.ENCOUNTER_TIMEOUT) {
            e = new Encounter();
            e.start = t;
            e.chase = species.ordinaryHostile() && Perception.view(player, mob) == 1
                    && mob.getRandom().nextFloat() < YokaiBalance.Perception.PARTIAL_PURSUIT_CHANCE;
            encounters.put(player.getUUID(), e);
        }
        e.lastSeen = t;
        return e;
    }

    public @Nullable Encounter encounter(Player player) {
        Encounter e = encounters.get(player.getUUID());
        return e != null && now() - e.lastSeen <= YokaiBalance.Perception.ENCOUNTER_TIMEOUT ? e : null;
    }

    /**
     * Whether this creature's full hostile behaviour applies to {@code player}: complete sight, or partial sight with a
     * successful roll for the current encounter. Never for companions, creative or spectator players, or a calm demo.
     */
    public boolean mayPursue(Player player) {
        if (!(player instanceof ServerPlayer sp) || bonded() || !Perception.targetable(sp) || now() < calmUntil) return false;
        int view = Perception.view(sp, mob);
        if (view >= 2) return true;
        if (view == 1) {
            Encounter e = encounter(player);
            return e != null && e.chase;
        }
        return false;
    }

    public void calm(int ticks) { calmUntil = now() + ticks; }
    public boolean calmed() { return now() < calmUntil; }

    public void tick() {
        if (mob.tickCount % 100 == 0) {
            long t = now();
            encounters.values().removeIf(e -> t - e.lastSeen > YokaiBalance.Perception.ENCOUNTER_TIMEOUT);
        }
    }

    // ------------------------------------------------------------------------------------------------ combat

    public void markCombat() { lastCombat = now(); }
    public boolean inCombat() { return now() - lastCombat < YokaiBalance.Companion.COMBAT_TICKS; }

    // ------------------------------------------------------------------------------------------------ bond

    public boolean bonded() { return owner != null; }
    public @Nullable UUID owner() { return owner; }
    public @Nullable UUID bondId() { return bondId; }
    public long token() { return token; }
    public Order order() { return order; }
    public void order(Order o) { order = o; }

    public void bind(UUID owner, UUID bondId, long token) {
        this.owner = owner;
        this.bondId = bondId;
        this.token = token;
        this.order = Order.FOLLOW;
        this.ownerCache = null;
        mob.setPersistenceRequired();
        mob.setTarget(null);
        encounters.clear();
    }

    public void retoken(long token) { this.token = token; }

    public boolean isOwner(Entity e) { return owner != null && owner.equals(e.getUUID()); }

    /** The owner when online and in the same level (cached for a second: no per-tick player searches). */
    public @Nullable ServerPlayer ownerPlayer() {
        if (owner == null || !(mob.level() instanceof ServerLevel level)) return null;
        if (mob.tickCount - ownerCacheTick >= 20 || ownerCacheTick < 0 || (ownerCache != null && ownerCache.isRemoved())) {
            ownerCacheTick = mob.tickCount;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(owner);
            ownerCache = p != null && p.level() == level ? p : null;
        }
        return ownerCache;
    }

    /** Owner online anywhere (a companion at rest while its owner is away). */
    public boolean ownerOnline() {
        return owner != null && mob.level() instanceof ServerLevel level && level.getServer().getPlayerList().getPlayer(owner) != null;
    }

    // ------------------------------------------------------------------------------------------------ rituals

    public Ritual ritual(Player player) { return rituals.computeIfAbsent(player.getUUID(), u -> new Ritual()); }
    public @Nullable Ritual ritualIfAny(Player player) { return rituals.get(player.getUUID()); }
    public void resetRitual(Player player) { rituals.remove(player.getUUID()); }

    // ------------------------------------------------------------------------------------------------ persistence

    public void save(ValueOutput out) {
        ValueOutput o = out.child("Yokai");
        if (owner != null) {
            o.store("owner", UUIDUtil.CODEC, owner);
            if (bondId != null) o.store("bond", UUIDUtil.CODEC, bondId);
            o.putLong("token", token);
            o.putString("order", order.name());
        }
        o.putLong("last_combat", lastCombat);
        var list = o.childrenList("rituals");
        rituals.forEach((uuid, r) -> {
            ValueOutput e = list.addChild();
            e.store("player", UUIDUtil.CODEC, uuid);
            e.putInt("stage", r.stage);
            e.putInt("count", r.count);
            e.putLong("time", r.time);
        });
    }

    public void load(ValueInput in) {
        ValueInput o = in.childOrEmpty("Yokai");
        owner = o.read("owner", UUIDUtil.CODEC).orElse(null);
        bondId = o.read("bond", UUIDUtil.CODEC).orElse(null);
        token = o.getLongOr("token", 0);
        try { order = Order.valueOf(o.getStringOr("order", "FOLLOW")); } catch (IllegalArgumentException e) { order = Order.FOLLOW; }
        lastCombat = o.getLongOr("last_combat", -100000);
        rituals.clear();
        for (ValueInput e : o.childrenListOrEmpty("rituals")) {
            e.read("player", UUIDUtil.CODEC).ifPresent(uuid -> {
                Ritual r = new Ritual();
                r.stage = e.getIntOr("stage", 0);
                r.count = e.getIntOr("count", 0);
                r.time = e.getLongOr("time", 0);
                rituals.put(uuid, r);
            });
        }
    }

    /** Whether {@code target} is a living thing a companion in DEFEND may engage for its owner. */
    public boolean defendTarget(LivingEntity target) {
        ServerPlayer o = ownerPlayer();
        if (o == null || target == o || !target.isAlive() || isOwner(target)) return false;
        if (target instanceof YokaiCreature y && y.yokai().owner != null && y.yokai().owner.equals(owner)) return false;
        if (target instanceof Player p) return o.canHarmPlayer(p) && o.getLastHurtMob() == p;
        return target instanceof net.minecraft.world.entity.monster.Enemy || o.getLastHurtMob() == target || o.getLastHurtByMob() == target;
    }
}
