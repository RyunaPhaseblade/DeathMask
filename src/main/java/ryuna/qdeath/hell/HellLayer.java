package ryuna.qdeath.hell;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.Quantumdeath;

/** The five descending circles of Hell. Depth 1 replaces the vanilla Nether as the portal destination. */
public enum HellLayer {
    VESTIBULE(1, "vestibule", ChatFormatting.GRAY),
    GORGE(2, "gorge", ChatFormatting.RED),
    MIRE(3, "mire", ChatFormatting.DARK_AQUA),
    DIS(4, "dis", ChatFormatting.GOLD),
    ZENITH(5, "zenith", ChatFormatting.WHITE);

    public final int depth;
    public final String id;
    public final ChatFormatting color;
    public final ResourceKey<Level> level;
    /** Boss arenas of this layer; the Pilgrim's Compass and /deathmask locate use it. */
    public final TagKey<Structure> arenaTag;

    HellLayer(int depth, String id, ChatFormatting color) {
        this.depth = depth;
        this.id = id;
        this.color = color;
        this.level = ResourceKey.create(Registries.DIMENSION, Quantumdeath.id(id));
        this.arenaTag = TagKey.create(Registries.STRUCTURE, Quantumdeath.id("arena/" + id));
    }

    public static @Nullable HellLayer of(ResourceKey<Level> dimension) {
        for (HellLayer layer : values()) if (layer.level == dimension) return layer;
        return null;
    }

    public static @Nullable HellLayer of(Level level) { return of(level.dimension()); }

    public static @Nullable HellLayer byDepth(int depth) {
        return depth >= 1 && depth <= values().length ? values()[depth - 1] : null;
    }

    public @Nullable HellLayer below() { return byDepth(depth + 1); }
    public @Nullable HellLayer above() { return byDepth(depth - 1); }

    public Component title() {
        return Component.translatable("layer.quantumdeath." + id).withStyle(color);
    }

    public Component subtitle() {
        return Component.translatable("layer.quantumdeath." + id + ".subtitle").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
    }
}
