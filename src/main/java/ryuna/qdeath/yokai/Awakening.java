package ryuna.qdeath.yokai;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.registry.ModSounds;

import java.util.Optional;

/**
 * The world awakening: the first obsidian portal that forms in the Overworld wakes the yokai everywhere, for good.
 * The only text is the normal advancement "What have you done?" (no title, no custom announcement); everyone online
 * hears one eerie sound, and everyone gets a note in the book.
 */
public final class Awakening {
    private Awakening() {}

    public static final Identifier ADVANCEMENT = Quantumdeath.id("what_have_you_done");
    public static final String CRITERION = "awakened";

    /** The player whose flint and steel or fire charge is being used right now (set around the item use by mixins). */
    private static final ThreadLocal<ServerPlayer> IGNITER = new ThreadLocal<>();

    public static void igniting(@Nullable ServerPlayer player) {
        if (player == null) IGNITER.remove();
        else IGNITER.set(player);
    }

    /** An obsidian portal has just been created by its frame being lit. Lighting fire without forming a portal never gets here. */
    public static void portalFormed(LevelAccessor accessor, BlockPos at) {
        if (!(accessor instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        ServerPlayer igniter = IGNITER.get();
        if (igniter != null && (igniter.level() != level || igniter.blockPosition().distSqr(at) > 64 * 64)) igniter = null;
        awaken(level.getServer(), igniter);
    }

    /** Wakes the world. {@code responsible} may be null (lava, fire spread, a dispenser): the advancement then waits for the first traveller. */
    public static boolean awaken(MinecraftServer server, @Nullable ServerPlayer responsible) {
        YokaiWorld w = YokaiWorld.of(server);
        if (w.awakened()) return false;
        long day = server.overworld().getGameTime() / 24000L;
        YokaiWorld.set(server, new YokaiWorld(true, day, YokaiBalance.World.GRACE_TICKS, responsible == null, Optional.empty()));
        Quantumdeath.LOGGER.info("The yokai have awakened (cause: {})", responsible == null ? "unidentified" : responsible.getGameProfile().name());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.YOKAI_AWAKENING), SoundSource.AMBIENT,
                    p.getX(), p.getY(), p.getZ(), 1.0f, 1.0f, p.getRandom().nextLong()));
            note(p);
        }
        if (responsible != null) award(responsible);
        return true;
    }

    /** A player is about to go through an obsidian portal from the Overworld. In an older world this is the awakening. */
    public static void portalCrossed(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        YokaiWorld w = YokaiWorld.of(server);
        if (!w.awakened()) awaken(server, player);
        else if (w.pendingAdvancement()) award(player);
    }

    private static void award(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        AdvancementHolder holder = server.getAdvancements().get(ADVANCEMENT);
        if (holder != null) player.getAdvancements().award(holder, CRITERION);
        YokaiWorld.set(server, YokaiWorld.of(server).awarded(player.getUUID()));
    }

    /** A player joins: whoever was not there when it happened finds the note in the book. */
    public static void join(ServerPlayer player) {
        if (YokaiWorld.of(player.level().getServer()).awakened() && !YokaiPlayer.of(player).noted()) note(player);
    }

    private static void note(ServerPlayer player) {
        YokaiPlayer.set(player, YokaiPlayer.of(player).withNote(true));
        YokaiNet.syncSelf(player);
    }

    /** Demonstration only: puts the world back to sleep (the advancement already granted is kept). */
    public static void sleep(MinecraftServer server) {
        YokaiWorld.set(server, YokaiWorld.START);
    }

    public static boolean awake(MinecraftServer server) { return YokaiWorld.of(server).awakened(); }
}
