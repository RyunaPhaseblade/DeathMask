package ryuna.qdeath.jjk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.jjk.entity.TechniqueBody;
import ryuna.qdeath.jjk.entity.TechniqueProjectile;

/** Registered content of the cursed-technique classes: entities, sprite items, cursed ice, the Immobilized effect, tags. */
public final class JjkContent {
    private JjkContent() {}

    private static Item sprite(String id) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Quantumdeath.id(id));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
    }

    // Sprites carried (and drawn) by technique projectiles. Not obtainable.
    public static final Item BLOOD_DISC = sprite("technique_blood_disc");
    public static final Item ICE_SHARD = sprite("technique_ice_shard");
    public static final Item FIRE_BLAST = sprite("technique_fire_blast");
    public static final Item MAGMA_BOMB = sprite("technique_magma_bomb");
    public static final Item EMBER_INSECT = sprite("technique_ember_insect");

    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> entity(String id, EntityType.Builder<T> b) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Quantumdeath.id(id));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, b.build(key));
    }

    public static final EntityType<TechniqueProjectile> PROJECTILE = entity("technique_projectile",
            EntityType.Builder.<TechniqueProjectile>of(TechniqueProjectile::new, MobCategory.MISC).sized(0.4f, 0.4f).clientTrackingRange(8).updateInterval(1));
    public static final EntityType<TechniqueBody> BODY = entity("technique_body",
            EntityType.Builder.<TechniqueBody>of(TechniqueBody::new, MobCategory.MISC).sized(1.6f, 1.6f).clientTrackingRange(12).updateInterval(1).fireImmune());

    /** Temporary cursed ice: never drops, never stays (it melts on a scheduled tick, and randomly if that tick was lost). */
    public static class CursedIce extends HalfTransparentBlock {
        public CursedIce(BlockBehaviour.Properties p) { super(p); }

        @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) { melt(level, pos); }

        @Override protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (!level.getBlockTicks().hasScheduledTick(pos, this)) melt(level, pos);
        }

        public static void melt(ServerLevel level, BlockPos pos) {
            if (!level.getBlockState(pos).is(CURSED_ICE)) return;
            level.removeBlock(pos, false);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    6, 0.3, 0.3, 0.3, 0.05);
            if (level.getRandom().nextInt(3) == 0) level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.35f, 1.6f);
        }
    }

    public static final Block CURSED_ICE = register("cursed_ice");

    private static Block register(String id) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Quantumdeath.id(id));
        return Registry.register(BuiltInRegistries.BLOCK, key, new CursedIce(BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.ICE)
                .strength(0.6f).friction(0.98f).sound(SoundType.GLASS).noOcclusion().randomTicks().noLootTable().pushReaction(PushReaction.IMMOVEABLE)));
    }

    /** Frozen solid (Uraume) or held in a frame (Naoya): cannot move, jump or strike. */
    public static class Immobilized extends MobEffect {
        Immobilized() { super(MobEffectCategory.HARMFUL, 0xbfe8ff); }
        @Override public boolean shouldApplyEffectTickThisTick(int tick, int amp) { return true; }
        @Override public boolean applyEffectTick(ServerLevel level, LivingEntity e, int amp) {
            if (e instanceof Mob mob) mob.getNavigation().stop();
            e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
            return true;
        }
    }

    public static final Holder<MobEffect> IMMOBILIZED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, Quantumdeath.id("immobilized"), new Immobilized()
            .addAttributeModifier(Attributes.MOVEMENT_SPEED, Quantumdeath.id("effect.immobilized.speed"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
            .addAttributeModifier(Attributes.JUMP_STRENGTH, Quantumdeath.id("effect.immobilized.jump"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
            .addAttributeModifier(Attributes.FLYING_SPEED, Quantumdeath.id("effect.immobilized.fly"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
            .addAttributeModifier(Attributes.ATTACK_DAMAGE, Quantumdeath.id("effect.immobilized.attack"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    /** Items that carry cursed energy: Boogie Woogie's Cursed Object Swap may target them when dropped. */
    public static final TagKey<Item> CURSED_OBJECTS = TagKey.create(Registries.ITEM, Quantumdeath.id("cursed_objects"));
    /** Entity types that count as cursed objects for Boogie Woogie (in addition to the mod's own technique bodies). */
    public static final TagKey<EntityType<?>> CURSED_OBJECT_ENTITIES = TagKey.create(Registries.ENTITY_TYPE, Quantumdeath.id("cursed_objects"));
    /** Entities Boogie Woogie can never move (bosses, arena-bound creatures). */
    public static final TagKey<EntityType<?>> SWAP_IMMUNE = TagKey.create(Registries.ENTITY_TYPE, Quantumdeath.id("boogie_woogie_immune"));

    public static void init() {}
}
