package ryuna.qdeath.hell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import ryuna.qdeath.Quantumdeath;

/**
 * Per-player Hell progression, persisted on the player and kept through death.
 * Progress is per player (never per world), so every friend can always catch up by re-fighting a boss.
 *
 * @param depth     deepest layer this player may descend into (1 = Vestibule only, 5 = Zenith unlocked)
 * @param encounters VIGIL encounters witnessed, drives stalker pacing
 * @param pages     bitmask of Pilgrim's Journal pages read
 * @param freed     true once VIGIL has been laid to rest by this player
 * @param visited   bitmask of layers this player has set foot in
 */
public record HellProgress(int depth, int encounters, int pages, boolean freed, int visited) {
    public static final HellProgress START = new HellProgress(1, 0, 0, false, 0);
    public static final Codec<HellProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("depth", 1).forGetter(HellProgress::depth),
            Codec.INT.optionalFieldOf("encounters", 0).forGetter(HellProgress::encounters),
            Codec.INT.optionalFieldOf("pages", 0).forGetter(HellProgress::pages),
            Codec.BOOL.optionalFieldOf("freed", false).forGetter(HellProgress::freed),
            Codec.INT.optionalFieldOf("visited", 0).forGetter(HellProgress::visited)
    ).apply(i, HellProgress::new));

    public static final AttachmentType<HellProgress> TYPE = AttachmentRegistry.create(Quantumdeath.id("hell_progress"),
            b -> b.persistent(CODEC).copyOnDeath().initializer(() -> START));

    public static void init() { /* class load registers the attachment */ }

    public static HellProgress of(ServerPlayer player) { return player.getAttachedOrCreate(TYPE); }

    public static void set(ServerPlayer player, HellProgress progress) { player.setAttached(TYPE, progress); }

    public HellProgress withDepth(int d) { return new HellProgress(Math.max(1, Math.min(5, d)), encounters, pages, freed, visited); }
    public HellProgress withEncounters(int e) { return new HellProgress(depth, e, pages, freed, visited); }
    public HellProgress withPage(int page) { return new HellProgress(depth, encounters, pages | (1 << page), freed, visited); }
    public HellProgress withFreed(boolean f) { return new HellProgress(depth, encounters, pages, f, visited); }
    public HellProgress withVisited(HellLayer layer) { return new HellProgress(depth, encounters, pages, freed, visited | (1 << layer.depth)); }
    public boolean hasVisited(HellLayer layer) { return (visited & (1 << layer.depth)) != 0; }

    public boolean canEnter(HellLayer layer) { return layer.depth <= depth; }

    /** Grants descent past {@code beaten}. Returns true if this was new for the player. */
    public static boolean grantBossCredit(ServerPlayer player, HellLayer beaten) {
        HellProgress progress = of(player);
        if (beaten == HellLayer.ZENITH) {
            if (progress.freed) return false;
            set(player, progress.withFreed(true));
            player.sendSystemMessage(Component.translatable("message.quantumdeath.vigil_freed").withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC));
            return true;
        }
        HellLayer next = beaten.below();
        if (next == null || progress.depth >= next.depth) return false;
        set(player, progress.withDepth(next.depth));
        player.sendSystemMessage(Component.translatable("message.quantumdeath.descent_unlocked", next.title()).withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 0.6f);
        return true;
    }
}
