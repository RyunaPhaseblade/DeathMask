package ryuna.qdeath.yokai;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import ryuna.qdeath.registry.ModSounds;
import ryuna.qdeath.skills.SkillId;
import ryuna.qdeath.skills.SkillManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shikigami control: the order screen, server-validated orders (follow and stay with Pacts; defend, passive and the
 * special actions with Orders), and the Resonance panel with its low-health warning.
 */
public final class Companions {
    private Companions() {}

    /** Companions already warned about (below the low-health threshold); cleared when they recover. */
    private static final Set<UUID> WARNED = new HashSet<>();

    /** Owner right-clicked their companion with an empty hand. */
    public static void open(ServerPlayer player, Mob mob) { open(player, mob, false); }

    private static void open(ServerPlayer player, Mob mob, boolean refresh) {
        if (!(mob instanceof YokaiCreature y) || !y.yokai().isOwner(player)) return;
        boolean orders = SkillManager.has(player, SkillId.BOND_II);
        List<String> targets = new ArrayList<>();
        boolean tracking = false;
        if (mob instanceof Tracker eye) {
            tracking = eye.tracking();
            if (orders) targets.addAll(eye.trackable());
        }
        String name = mob.hasCustomName() ? mob.getCustomName().getString() : "";
        YokaiNet.send(player, new YokaiNet.OpenCompanion(mob.getId(), y.species().id, name, mob.getHealth(), mob.getMaxHealth(),
                y.yokai().order().name().toLowerCase(), orders, tracking, targets, refresh));
    }

    /** An order from the screen. Everything is checked again here. */
    public static void order(ServerPlayer player, int entityId, String action, String arg) {
        Entity e = player.level().getEntity(entityId);
        if (!(e instanceof Mob mob) || !(mob instanceof YokaiCreature y) || !y.yokai().isOwner(player) || player.distanceTo(mob) > 48) return;
        if (!SkillManager.has(player, SkillId.BOND_I)) return;
        boolean orders = SkillManager.has(player, SkillId.BOND_II);
        YokaiCore core = y.yokai();
        switch (action) {
            case "follow" -> core.order(YokaiCore.Order.FOLLOW);
            case "stay" -> core.order(YokaiCore.Order.STAY);
            case "defend" -> { if (orders) core.order(YokaiCore.Order.DEFEND); }
            case "passive" -> { if (orders) core.order(YokaiCore.Order.PASSIVE); }
            case "special" -> { if (orders && mob instanceof Special s) s.special(player); }
            case "ride" -> { if (mob instanceof Mount m) m.mount(player); }
            case "storage" -> { if (mob instanceof Storage s) s.openStorage(player); }
            case "track" -> { if (orders && mob instanceof Tracker t) t.track(player, arg); }
            case "untrack" -> { if (mob instanceof Tracker t) t.untrack(player); }
            default -> { return; }
        }
        if (!action.equals("ride") && !action.equals("storage")) {
            mob.level().playSound(null, mob.blockPosition(), ModSounds.YOKAI_ORDER, SoundSource.NEUTRAL, 0.6f, 1.0f);
            open(player, mob, true);
        }
        push(player);
    }

    // ------------------------------------------------------------------------------------------------ status

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % YokaiBalance.Companion.STATUS_INTERVAL != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) push(p);
    }

    /** Sends the owner the state of every companion (Resonance panel and book), and warns once under 25% health. */
    public static void push(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        List<YokaiNet.Status> list = new ArrayList<>();
        boolean resonance = SkillManager.has(player, SkillId.BOND_III);
        for (Map.Entry<UUID, Bonds.Bond> e : Bonds.of(server, player.getUUID())) {
            Bonds.Bond b = e.getValue();
            Entity ent = b.state().equals(Bonds.DEPLOYED) && b.entity().isPresent() ? ((ServerLevel) player.level()).getEntity(b.entity().get()) : null;
            String order = ent instanceof YokaiCreature y ? y.yokai().order().name().toLowerCase() : "";
            float h = ent instanceof Mob m ? m.getHealth() : b.health(), max = ent instanceof Mob m ? m.getMaxHealth() : b.max();
            String name = ent instanceof Mob m && m.hasCustomName() ? m.getCustomName().getString() : b.name();
            list.add(new YokaiNet.Status(ent != null ? ent.getId() : -1, b.species(), name, h, max, order, b.state()));
            if (ent instanceof Mob m && resonance && max > 0) {
                boolean low = h / max < YokaiBalance.Companion.LOW_HEALTH;
                if (low && WARNED.add(m.getUUID())) {
                    player.sendOverlayMessage(Component.translatable("yokai.quantumdeath.resonance.low", m.getDisplayName()).withStyle(ChatFormatting.RED));
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                            net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.RESONANCE_WARNING), SoundSource.PLAYERS,
                            player.getX(), player.getY(), player.getZ(), 0.8f, 1.0f, 0));
                } else if (!low) WARNED.remove(m.getUUID());
            }
        }
        YokaiNet.send(player, new YokaiNet.Companions(list));
    }

    // ------------------------------------------------------------------------------------------------ capabilities

    /** Companions with a special action under the Orders node (Tanuki: distract; Tengu: gust). */
    public interface Special { void special(ServerPlayer owner); }
    /** Mountable companions (Ittan-momen). */
    public interface Mount { void mount(ServerPlayer owner); }
    /** Companions with an inventory (chest tsukumogami). */
    public interface Storage { void openStorage(ServerPlayer owner); }
    /** The Swarm Eye's physical tracking. */
    public interface Tracker {
        boolean tracking();
        List<String> trackable();
        void track(ServerPlayer owner, String targetName);
        void untrack(ServerPlayer owner);
    }
}
