package ryuna.qdeath.skills;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import ryuna.qdeath.Quantumdeath;

import java.util.Optional;

/** The nine fixed nodes shown in the Quantumdeath skill tree. */
public enum SkillId {
    HEALTH_I(null, Attributes.MAX_HEALTH, 2.0, AttributeModifier.Operation.ADD_VALUE, 0, 0),
    HEALTH_II(HEALTH_I, Attributes.MAX_HEALTH, 4.0, AttributeModifier.Operation.ADD_VALUE, 0, 1),
    HEALTH_III(HEALTH_II, Attributes.MAX_HEALTH, 6.0, AttributeModifier.Operation.ADD_VALUE, 0, 2),
    SPEED_I(null, Attributes.MOVEMENT_SPEED, 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 1, 0),
    SPEED_II(SPEED_I, Attributes.MOVEMENT_SPEED, 0.10, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 1, 1),
    SPEED_III(SPEED_II, Attributes.MOVEMENT_SPEED, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 1, 2),
    STRENGTH_I(null, Attributes.ATTACK_DAMAGE, 1.0, AttributeModifier.Operation.ADD_VALUE, 2, 0),
    STRENGTH_II(STRENGTH_I, Attributes.ATTACK_DAMAGE, 2.0, AttributeModifier.Operation.ADD_VALUE, 2, 1),
    STRENGTH_III(STRENGTH_II, Attributes.ATTACK_DAMAGE, 3.0, AttributeModifier.Operation.ADD_VALUE, 2, 2);

    private final SkillId prerequisite;
    private final net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute;
    private final double value;
    private final AttributeModifier.Operation operation;
    private final int branch;
    private final int tier;

    SkillId(SkillId prerequisite, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value, AttributeModifier.Operation operation, int branch, int tier) {
        this.prerequisite = prerequisite;
        this.attribute = attribute;
        this.value = value;
        this.operation = operation;
        this.branch = branch;
        this.tier = tier;
    }

    public Optional<SkillId> prerequisite() { return Optional.ofNullable(prerequisite); }
    public net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute() { return attribute; }
    public double value() { return value; }
    public AttributeModifier.Operation operation() { return operation; }
    public int branch() { return branch; }
    public int tier() { return tier; }
    public Identifier modifierId() { return Quantumdeath.id("skill_" + name().toLowerCase()); }
    public String translationKey() { return "skill.quantumdeath." + name().toLowerCase(); }
    public String descriptionKey() { return translationKey() + ".description"; }
}
