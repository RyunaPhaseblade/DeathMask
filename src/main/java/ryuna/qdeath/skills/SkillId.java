package ryuna.qdeath.skills;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.yokai.YokaiBalance;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The general skill tree of the Wisdom of the Void: six branches of three nodes. Saved by {@link #id} (stable text),
 * never by position; {@link #LEGACY_ORDER} maps the nine ordinals written by older versions.
 */
public enum SkillId {
    HEALTH_I("health_i", 0, 0, Attributes.MAX_HEALTH, 2.0, AttributeModifier.Operation.ADD_VALUE),
    HEALTH_II("health_ii", 0, 1, Attributes.MAX_HEALTH, 4.0, AttributeModifier.Operation.ADD_VALUE),
    HEALTH_III("health_iii", 0, 2, Attributes.MAX_HEALTH, 6.0, AttributeModifier.Operation.ADD_VALUE),
    SPEED_I("speed_i", 1, 0, Attributes.MOVEMENT_SPEED, 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
    SPEED_II("speed_ii", 1, 1, Attributes.MOVEMENT_SPEED, 0.10, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
    SPEED_III("speed_iii", 1, 2, Attributes.MOVEMENT_SPEED, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
    STRENGTH_I("strength_i", 2, 0, Attributes.ATTACK_DAMAGE, 1.0, AttributeModifier.Operation.ADD_VALUE),
    STRENGTH_II("strength_ii", 2, 1, Attributes.ATTACK_DAMAGE, 2.0, AttributeModifier.Operation.ADD_VALUE),
    STRENGTH_III("strength_iii", 2, 2, Attributes.ATTACK_DAMAGE, 3.0, AttributeModifier.Operation.ADD_VALUE),
    /** Omen: partial perception (shadows and particles). */
    PERCEPTION_I("perception_i", 3, 0, null, 0, null),
    /** Cursed Sight: complete perception. */
    PERCEPTION_II("perception_ii", 3, 1, null, 0, null),
    /** Reading Intent: a discreet mark over visible attack preparations. */
    PERCEPTION_III("perception_iii", 3, 2, null, 0, null),
    /** Pacts: the rituals. Also needs Cursed Sight. */
    BOND_I("bond_i", 4, 0, null, 0, null),
    /** Orders: defend, passive, special actions and tracking. */
    BOND_II("bond_ii", 4, 1, null, 0, null),
    /** Resonance: companion status panel and a warning under 25% health. */
    BOND_III("bond_iii", 4, 2, null, 0, null),
    /** Interpretation: the book explains the signs already witnessed. */
    SPIRIT_I("spirit_i", 5, 0, null, 0, null),
    /** Prepared Offerings: recipes and quick selection of offerings from the book. */
    SPIRIT_II("spirit_ii", 5, 1, null, 0, null),
    /** Retreat: brief speed after a yokai hurts you. */
    SPIRIT_III("spirit_iii", 5, 2, null, 0, null);

    /** The nine nodes of the original tree, in their original ordinal order. Never reorder. */
    public static final List<SkillId> LEGACY_ORDER = List.of(HEALTH_I, HEALTH_II, HEALTH_III, SPEED_I, SPEED_II, SPEED_III, STRENGTH_I, STRENGTH_II, STRENGTH_III);
    public static final int BRANCHES = 6;

    public final String id;
    private final int branch, tier;
    private final @Nullable Holder<Attribute> attribute;
    private final double value;
    private final AttributeModifier.@Nullable Operation operation;

    SkillId(String id, int branch, int tier, @Nullable Holder<Attribute> attribute, double value, AttributeModifier.@Nullable Operation operation) {
        this.id = id;
        this.branch = branch;
        this.tier = tier;
        this.attribute = attribute;
        this.value = value;
        this.operation = operation;
    }

    public static Optional<SkillId> byId(String id) {
        for (SkillId s : values()) if (s.id.equals(id)) return Optional.of(s);
        return Optional.empty();
    }

    /** The previous node of the branch, plus Cursed Sight for Pacts. */
    public List<SkillId> prerequisites() {
        List<SkillId> out = new ArrayList<>(2);
        if (tier > 0) out.add(values()[ordinal() - 1]);
        if (this == BOND_I) out.add(PERCEPTION_II);
        return out;
    }

    /** First prerequisite (the previous node of the branch), kept for older callers. */
    public Optional<SkillId> prerequisite() { return prerequisites().stream().findFirst(); }

    /** General points this node costs (server value; the client receives it with the sync). */
    public int cost() {
        return branch < 3 ? YokaiBalance.Skills.LEGACY_COST : switch (tier) {
            case 0 -> YokaiBalance.Skills.TIER_1_COST;
            case 1 -> YokaiBalance.Skills.TIER_2_COST;
            default -> YokaiBalance.Skills.TIER_3_COST;
        };
    }

    public boolean legacy() { return branch < 3; }
    public @Nullable Holder<Attribute> attribute() { return attribute; }
    public double value() { return value; }
    public AttributeModifier.@Nullable Operation operation() { return operation; }
    public int branch() { return branch; }
    public int tier() { return tier; }
    public Identifier modifierId() { return Quantumdeath.id("skill_" + id); }
    public String translationKey() { return "skill.quantumdeath." + id; }
    public String descriptionKey() { return translationKey() + ".description"; }
}
