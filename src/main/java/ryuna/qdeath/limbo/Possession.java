package ryuna.qdeath.limbo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.entity.limbo.LimboDead;
import ryuna.qdeath.entity.limbo.MourningParasiteEntity;
import ryuna.qdeath.registry.ModParticles;
import ryuna.qdeath.registry.ModSounds;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Bonds between Mourning Parasites and their hosts. A possessed host keeps its entity, UUID, inventory, profession,
 * trades, name and every saved field: the bond is a persistent attachment on the host (the parasite's UUID and, for a
 * plague parasite, its focus and breach anchor) plus transient attribute modifiers, and the host's own goals and brain
 * are only suspended (its AI step is replaced while the bond lasts, see MobPossessionMixin), never erased. Releasing
 * removes exactly what was added. The dead see a host as one of their own; a bond whose parasite is missing after the
 * load grace is broken, which frees the host. Nothing is ever duplicated or loaded to resolve a bond.
 */
public final class Possession {
    private Possession() {}

    /** The host's half of the bond. {@code focus} is -1 for parasites of the Limbo. */
    public record Link(UUID parasite, Optional<BlockPos> anchor, int focus) {
        static final Codec<Link> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("parasite").forGetter(Link::parasite),
                BlockPos.CODEC.optionalFieldOf("anchor").forGetter(Link::anchor),
                Codec.INT.optionalFieldOf("focus", -1).forGetter(Link::focus)
        ).apply(i, Link::new));
        static final StreamCodec<ByteBuf, Link> STREAM = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Link::parasite, ByteBufCodecs.optional(BlockPos.STREAM_CODEC), Link::anchor, ByteBufCodecs.VAR_INT, Link::focus, Link::new);
    }

    /** Persistent with the host, and synced to clients (the host's twitching, sickly look). */
    public static final AttachmentType<Link> HOST = AttachmentRegistry.create(Quantumdeath.id("possessed"),
            b -> b.persistent(Link.CODEC).syncWith(Link.STREAM, AttachmentSyncPredicate.all()));

    private static final Identifier SPEED = Quantumdeath.id("possession_speed"), DAMAGE = Quantumdeath.id("possession_damage");
    /** Walking speed grows with the square of the speed attribute: +20 % real speed is +9.5 % of the attribute. */
    private static final double SPEED_ATTRIBUTE = Math.sqrt(1 + LimboTuning.HOST_SPEED) - 1;

    /** Transient driving state of each host (rebuilt after a reload; nothing in it needs saving). */
    private static final class Drive {
        @Nullable LivingEntity target;
        @Nullable Vec3 clue;
        int unseen, cooldown, windup, missing, wander;
    }

    private static final Map<UUID, Drive> DRIVES = new HashMap<>();
    public static int possessions, releases;

    public static void init() { /* class load registers the attachment */ }

    public static boolean isPossessed(Entity e) { return e.hasAttached(HOST); }

    public static @Nullable Link link(Entity e) { return e.getAttached(HOST); }

    /**
     * Who a parasite may take: animals that do not live in fire, villagers and spiders (cave spiders too). Never players,
     * tamed or owned creatures, shikigami, creatures of the Nether, bosses, or a host already taken.
     */
    public static boolean eligible(Entity e) {
        if (!(e instanceof Mob m) || !m.isAlive() || m.isNoAi() || isPossessed(m)) return false;
        if (m instanceof TamableAnimal t && t.isTame()) return false;
        if (m instanceof AbstractHorse h && h.isTamed()) return false;
        if (m instanceof OwnableEntity o && o.getOwnerReference() != null) return false;
        if (m instanceof Spider) return true;
        if (m instanceof Villager) return true;
        return m instanceof Animal && !m.fireImmune() && !(m instanceof Hoglin);
    }

    public static void possess(ServerLevel level, Mob host, MourningParasiteEntity parasite) {
        Optional<BlockPos> anchor = parasite.fromPlague() ? Optional.ofNullable(parasite.home()) : Optional.empty();
        host.setAttached(HOST, new Link(parasite.getUUID(), anchor, parasite.plagueFocus()));
        modifiers(host, true);
        host.getNavigation().stop();
        host.setTarget(null);
        DRIVES.remove(host.getUUID());
        possessions++;
        level.playSound(null, host.blockPosition(), ModSounds.PARASITE_POSSESS, SoundSource.HOSTILE, 1.2f, 0.9f);
        level.sendParticles(ModParticles.BILE_DRIP, host.getX(), host.getY() + host.getBbHeight(), host.getZ(), 20, 0.3, 0.2, 0.3, 0.05);
    }

    /** Frees a host: the bond and the added modifiers go; the damage it took stays. */
    public static void release(Mob host) {
        if (!isPossessed(host)) return;
        host.removeAttached(HOST);
        modifiers(host, false);
        host.getNavigation().stop();
        host.setTarget(null);
        DRIVES.remove(host.getUUID());
        releases++;
        if (host.level() instanceof ServerLevel level)
            level.sendParticles(ModParticles.ASH_FLAKE, host.getX(), host.getY() + host.getBbHeight() * 0.7, host.getZ(), 16, 0.3, 0.3, 0.3, 0.02);
    }

    /** Adds (when missing: transient modifiers are not saved, so a reloaded host gets them back here) or removes the bonuses. */
    private static void modifiers(Mob host, boolean on) {
        modifier(host.getAttribute(Attributes.MOVEMENT_SPEED), SPEED, SPEED_ATTRIBUTE, on);
        modifier(host.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE, LimboTuning.HOST_DAMAGE_BONUS, on);
    }

    private static void modifier(@Nullable AttributeInstance a, Identifier id, double amount, boolean on) {
        if (a == null) return;
        if (!on) a.removeModifier(id);
        else if (a.getModifier(id) == null) a.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** Hosts with a real attack (spiders) use it; the others (animals, villagers) strike for HOST_DAMAGE. */
    private static boolean armed(Mob host) {
        AttributeInstance a = host.getAttribute(Attributes.ATTACK_DAMAGE);
        return a != null && a.getBaseValue() > 0;
    }

    /** Navigation modifier for the pursuit: the host's own pace, never faster than HOST_PACE of the sprint (before the bonus). */
    private static double pace(Mob host) {
        AttributeInstance speed = host.getAttribute(Attributes.MOVEMENT_SPEED);
        double base = speed == null ? 0.25 : speed.getBaseValue();
        return Math.min(1.0, LimboTuning.chaseModifier(LimboTuning.HOST_PACE, base));
    }

    /**
     * Replaces the host's AI step while it is possessed (MobPossessionMixin): sensing, a hunt for the living like the
     * dead's own, and the host's navigation and controls (a spider still climbs). Its goals and brain do not run.
     */
    public static void drive(Mob host) {
        if (!(host.level() instanceof ServerLevel level)) return;
        Link link = link(host);
        if (link == null) return;
        Drive d = DRIVES.computeIfAbsent(host.getUUID(), k -> new Drive());
        Entity pe = level.getEntity(link.parasite());
        MourningParasiteEntity parasite = pe instanceof MourningParasiteEntity p && p.isAlive() ? p : null;
        if (parasite == null || !host.getUUID().equals(parasite.hostId())) {
            // The parasite may simply not be loaded yet: wait a little, then the bond is broken.
            if (++d.missing > LimboTuning.LINK_GRACE) release(host);
            controls(host);
            return;
        }
        d.missing = 0;
        modifiers(host, true);
        if (Consecration.at(level, host.position()) != null) {
            parasite.detach(level, true);
            release(host);
            return;
        }
        host.getSensing().tick();
        hunt(level, host, link, parasite, d);
        controls(host);
    }

    private static void controls(Mob host) {
        host.getNavigation().tick();
        host.getMoveControl().tick();
        host.getLookControl().tick();
        host.getJumpControl().tick();
    }

    private static void hunt(ServerLevel level, Mob host, Link link, MourningParasiteEntity parasite, Drive d) {
        if (d.cooldown > 0) d.cooldown--;
        LivingEntity t = d.target;
        if (t != null && (!t.isAlive() || t.level() != level || !LimboSenses.isLife(t) || host.distanceToSqr(t) > 40 * 40)) t = d.target = null;
        // Look for life now and then, and share the horde's view: a dead hunting nearby tells where its quarry is.
        if ((host.tickCount + host.getId()) % 10 == 0) {
            LivingEntity seen = null;
            double best = 16 * 16;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().inflate(16), LimboSenses::isLife)) {
                double dist = host.distanceToSqr(e);
                if (dist < best && e != host && host.getSensing().hasLineOfSight(e)) { best = dist; seen = e; }
            }
            if (seen != null) { t = d.target = seen; d.unseen = 0; }
            else if (t == null) {
                for (LimboDead dead : level.getEntitiesOfClass(LimboDead.class, host.getBoundingBox().inflate(12), x -> x.mind() == Mind.PURSUIT && x.quarry() != null)) {
                    d.clue = dead.quarry().position();
                    break;
                }
            }
        }
        Optional<BlockPos> anchor = link.anchor();
        if (anchor.isPresent() && host.distanceToSqr(Vec3.atBottomCenterOf(anchor.get())) > LimboTuning.PLAGUE_LEASH * LimboTuning.PLAGUE_LEASH) {
            // A plague host keeps to its breach's reach, like the dead that came out of it.
            t = d.target = null;
            d.clue = Vec3.atBottomCenterOf(anchor.get());
        }
        if (t != null) {
            boolean visible = host.getSensing().hasLineOfSight(t);
            if (visible) { d.unseen = 0; d.clue = t.position(); } else if (++d.unseen > LimboTuning.LOSE_SIGHT_TICKS) { d.target = null; return; }
            host.getLookControl().setLookAt(t, 30f, 30f);
            double reach = host.getBbWidth() * 0.5 + t.getBbWidth() * 0.5 + 1.1;
            boolean inReach = host.distanceTo(t) <= reach + 0.4 && visible;
            if (d.windup > 0) {
                if (--d.windup == 0 && inReach && Consecration.at(level, t.position()) == null) strike(level, host, t);
                return;
            }
            if (inReach && d.cooldown == 0) {
                // The announced bite: the parasite clenches, the host jerks forward a moment later.
                d.windup = LimboTuning.HOST_WINDUP;
                parasite.signalStrike();
                host.getNavigation().stop();
                return;
            }
            if ((host.tickCount + host.getId()) % 5 == 0 || host.getNavigation().isDone()) host.getNavigation().moveTo(t, pace(host));
            return;
        }
        if (d.clue != null) {
            if (host.distanceToSqr(d.clue) < 4) d.clue = null;
            else if ((host.tickCount + host.getId()) % 20 == 0 || host.getNavigation().isDone())
                host.getNavigation().moveTo(d.clue.x, d.clue.y, d.clue.z, pace(host) * 0.8);
            return;
        }
        if (--d.wander <= 0 && host.getNavigation().isDone() && host instanceof net.minecraft.world.entity.PathfinderMob pm) {
            d.wander = 80 + host.getRandom().nextInt(120);
            Vec3 dest = LandRandomPos.getPos(pm, 8, 4);
            if (dest != null && Consecration.at(level, dest) == null) host.getNavigation().moveTo(dest.x, dest.y, dest.z, pace(host) * 0.6);
        }
    }

    private static void strike(ServerLevel level, Mob host, LivingEntity t) {
        boolean armed = armed(host);
        d(host).cooldown = armed ? 20 : LimboTuning.HOST_INTERVAL;
        if (armed) host.doHurtTarget(level, t);
        else t.hurtServer(level, level.damageSources().mobAttack(host), LimboTuning.HOST_DAMAGE);
        level.playSound(null, host.blockPosition(), ModSounds.PARASITE_CHITTER, SoundSource.HOSTILE, 1.0f, 0.7f);
    }

    private static Drive d(Mob host) { return DRIVES.computeIfAbsent(host.getUUID(), k -> new Drive()); }

    /** Possessed hosts near a point (commands and the tests). */
    public static int hostsNear(ServerLevel level, Vec3 c, double r) {
        return level.getEntitiesOfClass(Mob.class, new AABB(c, c).inflate(r), Possession::isPossessed).size();
    }

    public static void forget(UUID host) { DRIVES.remove(host); }

    public static void clear() { DRIVES.clear(); possessions = 0; releases = 0; }
}
