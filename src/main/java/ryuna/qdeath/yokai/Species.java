package ryuna.qdeath.yokai;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * The thirteen yokai of the expansion. The first four are the original Quantumdeath creatures (ids kept).
 * {@code clues} are the signs the bestiary records before the creature is identified.
 */
public enum Species {
    FALSE_CREEPER("fake_creeper", true, Temper.HOSTILE, false, 0x7fae6a, List.of("hiss", "scorch"), List.of()),
    BIFAUCE("bifauce", true, Temper.HOSTILE, false, 0xa83a3a, List.of("mimic", "backward_breath"), List.of()),
    DAMPER("damper", true, Temper.CONDITIONAL, false, 0x6a6a7a, List.of("mimic_work", "torch_out", "creak"), List.of()),
    SWARM_EYE("swarm_eye", true, Temper.NEUTRAL, true, 0xd8b84a, List.of("watched", "shriek"), List.of(() -> Items.SPIDER_EYE)),
    KAPPA("kappa", false, Temper.NEUTRAL, true, 0x4a8a6a, List.of("bubbles", "wet_prints"), List.of(() -> Items.SEA_PICKLE)),
    ONI("oni", false, Temper.HOSTILE, false, 0xb83a28, List.of("heavy_steps", "impact"), List.of()),
    CHEST("chest_tsukumogami", false, Temper.NEUTRAL, true, 0x9a6a3a, List.of("knocking", "lid"), List.of(() -> Items.OAK_PLANKS, () -> Items.IRON_INGOT)),
    TANUKI("tanuki", false, Temper.MISCHIEVOUS, true, 0x8a6a4a, List.of("drum", "decoy"), List.of(() -> Items.APPLE)),
    TENGU("tengu", false, Temper.NEUTRAL, true, 0xc83030, List.of("gust", "feathers"), List.of(() -> Items.FEATHER)),
    KODAMA("kodama", false, Temper.KIND, false, 0xe0e4d0, List.of("rattle", "lights"), List.of(() -> Items.OAK_SAPLING)),
    NURIKABE("nurikabe", false, Temper.NEUTRAL, false, 0xcfc6b0, List.of("wall", "scrape"), List.of()),
    ITTAN_MOMEN("ittan_momen", false, Temper.HOSTILE, true, 0xf0ece0, List.of("rustle", "cloth_hiss"), List.of(() -> Items.SNOWBALL)),
    BELL("bell_tsukumogami", false, Temper.NEUTRAL, true, 0xd8a83a, List.of("tinkle"), List.of(() -> Items.COPPER_INGOT));

    public enum Temper { HOSTILE, CONDITIONAL, NEUTRAL, MISCHIEVOUS, KIND }

    public final String id;
    /** One of the four original creatures: visible, with their old danger, inside Hell. */
    public final boolean legacy;
    public final Temper temper;
    public final boolean pactable;
    public final int color;
    public final List<String> clues;
    /** Items the bestiary shows (and Prepared Offerings selects) for this creature's offering or ritual. */
    public final List<Supplier<Item>> offerings;
    private @Nullable Supplier<EntityType<?>> type;

    Species(String id, boolean legacy, Temper temper, boolean pactable, int color, List<String> clues, List<Supplier<Item>> offerings) {
        this.id = id;
        this.legacy = legacy;
        this.temper = temper;
        this.pactable = pactable;
        this.color = color;
        this.clues = clues;
        this.offerings = offerings;
    }

    public void bind(Supplier<EntityType<?>> type) { this.type = type; }
    public EntityType<?> type() { return type.get(); }

    /** An ordinary hostile: pursues at full sight, and with partial sight only after the per-encounter roll. */
    public boolean ordinaryHostile() { return temper == Temper.HOSTILE; }
    /** Dangerous creatures wait for the end of the grace period and keep away from occupied beds and fresh respawns. */
    public boolean offensive() { return temper == Temper.HOSTILE || temper == Temper.CONDITIONAL; }

    public String key() { return "yokai.quantumdeath." + id; }

    public static @Nullable Species byId(String id) {
        for (Species s : values()) if (s.id.equals(id)) return s;
        return null;
    }

    public static @Nullable Species of(EntityType<?> type) {
        for (Species s : values()) if (s.type != null && s.type.get() == type) return s;
        return null;
    }
}
