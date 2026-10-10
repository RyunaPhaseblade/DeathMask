package ryuna.qdeath.yokai;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.registry.ModSounds;
import ryuna.qdeath.skills.SkillId;
import ryuna.qdeath.skills.SkillManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The bond registry: the single source of truth for every shikigami (owner, species, state and current token),
 * stored on the Overworld. Every transition (pact, store, summon, rebinding) happens on the server thread and changes
 * the token, so there is always exactly one valid amulet and at most one living instance per bond.
 */
public final class Bonds {
    private Bonds() {}

    public static final String DEPLOYED = "deployed", STORED = "stored", DEAD = "dead", LOST = "lost";

    public record Bond(UUID owner, String species, String state, long token, String name, Optional<UUID> entity, float health, float max) {
        static final Codec<Bond> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Bond::owner),
                Codec.STRING.fieldOf("species").forGetter(Bond::species),
                Codec.STRING.fieldOf("state").forGetter(Bond::state),
                Codec.LONG.fieldOf("token").forGetter(Bond::token),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Bond::name),
                UUIDUtil.CODEC.optionalFieldOf("entity").forGetter(Bond::entity),
                Codec.FLOAT.optionalFieldOf("health", 0f).forGetter(Bond::health),
                Codec.FLOAT.optionalFieldOf("max", 0f).forGetter(Bond::max)
        ).apply(i, Bond::new));

        Bond with(String state, long token) { return new Bond(owner, species, state, token, name, entity, health, max); }
        Bond snapshot(float h, float m) { return new Bond(owner, species, state, token, name, entity, h, m); }
        public boolean alive() { return state.equals(DEPLOYED) || state.equals(STORED); }
    }

    public record Registry(Map<UUID, Bond> bonds) {
        static final Codec<Registry> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Bond.CODEC).xmap(Registry::new, Registry::bonds);
    }

    public static final AttachmentType<Registry> TYPE = AttachmentRegistry.create(Quantumdeath.id("bonds"),
            b -> b.persistent(Registry.CODEC).initializer(() -> new Registry(Map.of())));

    public static Map<UUID, Bond> all(MinecraftServer server) { return server.overworld().getAttachedOrCreate(TYPE).bonds(); }

    public static @Nullable Bond get(MinecraftServer server, UUID id) { return all(server).get(id); }

    private static void put(MinecraftServer server, UUID id, Bond bond) {
        Map<UUID, Bond> m = new HashMap<>(all(server));
        m.put(id, bond);
        server.overworld().setAttached(TYPE, new Registry(Map.copyOf(m)));
    }

    public static List<Map.Entry<UUID, Bond>> of(MinecraftServer server, UUID owner) {
        return all(server).entrySet().stream().filter(e -> e.getValue().owner().equals(owner) && e.getValue().alive()).toList();
    }

    private static long newToken(ServerLevel level) {
        long t;
        do t = level.getRandom().nextLong(); while (t == 0);
        return t;
    }

    // ------------------------------------------------------------------------------------------------ amulet item data

    public static AmuletData data(ItemStack amulet) { return amulet.getOrDefault(YokaiContent.AMULET_DATA, AmuletData.NONE); }

    public static void write(ItemStack amulet, AmuletData d) {
        amulet.set(YokaiContent.AMULET_DATA, d);
        amulet.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(d.state()), List.of()));
    }

    private static void say(ServerPlayer p, String key, Object... args) {
        p.sendOverlayMessage(Component.translatable("yokai.quantumdeath.amulet." + key, args).withStyle(ChatFormatting.GRAY));
    }

    // ------------------------------------------------------------------------------------------------ transitions

    /**
     * Completes a pact: needs complete sight, the Pacts node and a free amulet in the inventory, which becomes bound.
     * Returns false (nothing consumed, nothing changed) when any of them is missing.
     */
    public static boolean pact(ServerPlayer player, Mob mob) {
        if (!(mob instanceof YokaiCreature y) || y.yokai().bonded() || !y.species().pactable) return false;
        if (!Perception.canInteract(player, mob) || !SkillManager.has(player, SkillId.BOND_I)) {
            say(player, "need_pacts");
            return false;
        }
        ItemStack amulet = ItemStack.EMPTY;
        for (ItemStack s : player.getInventory()) if (s.is(YokaiContent.AMULET) && data(s).free()) { amulet = s; break; }
        if (amulet.isEmpty()) {
            say(player, "need_amulet");
            return false;
        }
        ServerLevel level = (ServerLevel) mob.level();
        UUID id = UUID.randomUUID();
        long token = newToken(level);
        y.yokai().bind(player.getUUID(), id, token);
        String name = mob.hasCustomName() ? mob.getCustomName().getString() : "";
        put(level.getServer(), id, new Bond(player.getUUID(), y.species().id, DEPLOYED, token, name, Optional.of(mob.getUUID()), mob.getHealth(), mob.getMaxHealth()));
        write(amulet, new AmuletData(Optional.of(id), token, y.species().id, name, AmuletData.DEPLOYED, new CompoundTag(), mob.getHealth(), mob.getMaxHealth()));
        Bestiary.identify(player, y.species());
        level.playSound(null, mob.blockPosition(), ModSounds.YOKAI_PACT, SoundSource.PLAYERS, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.ENCHANT, mob.getX(), mob.getY() + mob.getBbHeight() * 0.6, mob.getZ(), 40, 0.5, 0.6, 0.5, 0.6);
        say(player, "pact", Component.translatable(y.species().key()));
        Companions.push(player);
        return true;
    }

    /** Using an amulet on one's own companion: store it, or (with a free amulet) rebind it, invalidating the old amulet. */
    public static void useOn(ServerPlayer player, ItemStack amulet, Mob mob) {
        if (!(mob instanceof YokaiCreature y) || !y.yokai().isOwner(player)) { say(player, "not_yours"); return; }
        MinecraftServer server = player.level().getServer();
        UUID id = y.yokai().bondId();
        Bond bond = id == null ? null : get(server, id);
        if (bond == null || !bond.state().equals(DEPLOYED)) { say(player, "stale"); return; }
        AmuletData d = data(amulet);
        if (d.free()) {
            long token = newToken((ServerLevel) mob.level());
            y.yokai().retoken(token);
            put(server, id, bond.with(DEPLOYED, token));
            write(amulet, new AmuletData(Optional.of(id), token, bond.species(), bond.name(), AmuletData.DEPLOYED, new CompoundTag(), mob.getHealth(), mob.getMaxHealth()));
            say(player, "rebound");
            return;
        }
        if (d.bond().isEmpty() || !d.bond().get().equals(id) || d.token() != bond.token()) { say(player, "stale"); return; }
        store(player, amulet, mob, y, id, bond);
    }

    private static void store(ServerPlayer player, ItemStack amulet, Mob mob, YokaiCreature y, UUID id, Bond bond) {
        if (player.distanceTo(mob) > YokaiBalance.Companion.STORE_RANGE) { say(player, "too_far"); return; }
        if (mob.isVehicle() || mob.isPassenger()) { say(player, "mounted"); return; }
        if (y.yokai().inCombat()) { say(player, "combat"); return; }
        String busy = y.storeBlocker();
        if (busy != null) { say(player, busy); return; }
        ServerLevel level = (ServerLevel) mob.level();
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        if (!mob.save(out)) { say(player, "stale"); return; }
        CompoundTag tag = out.buildResult();
        long token = newToken(level);
        put(level.getServer(), id, new Bond(bond.owner(), bond.species(), STORED, token, bond.name(), Optional.of(mob.getUUID()), mob.getHealth(), mob.getMaxHealth()));
        write(amulet, new AmuletData(Optional.of(id), token, bond.species(), bond.name(), AmuletData.STORED, tag, mob.getHealth(), mob.getMaxHealth()));
        y.yokai().storing = true;
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(), 30, 0.4, 0.5, 0.4, 0.05);
        level.playSound(null, mob.blockPosition(), ModSounds.AMULET_STORE, SoundSource.PLAYERS, 1.0f, 1.0f);
        mob.discard();
        Companions.push(player);
    }

    /** Summons the stored companion onto the clicked face. A failed summon changes nothing. */
    public static boolean summon(ServerPlayer player, ItemStack amulet, BlockPos clicked, Direction face) {
        AmuletData d = data(amulet);
        ServerLevel level = (ServerLevel) player.level();
        if (!d.state().equals(AmuletData.STORED) || d.bond().isEmpty()) return false;
        Bond bond = get(level.getServer(), d.bond().get());
        if (bond == null || !bond.state().equals(STORED) || bond.token() != d.token() || !bond.owner().equals(player.getUUID())) {
            validate(level, amulet);
            say(player, "stale");
            return false;
        }
        Species species = Species.byId(d.species());
        BlockPos at = level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(face);
        Vec3 pos = Vec3.atBottomCenterOf(at);
        if (species == null || !level.isLoaded(at)) { say(player, "bad_place"); return false; }
        Entity e = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), d.entity()), level, EntitySpawnReason.LOAD,
                ent -> { ent.snapTo(pos.x, pos.y, pos.z, player.getYRot() + 180, 0); return ent; });
        if (!(e instanceof Mob mob) || !(mob instanceof YokaiCreature y) || !level.noCollision(mob) || !y.canStandAt(level, at)
                || level.getEntity(mob.getUUID()) != null) {
            if (e != null) e.discard();
            say(player, "bad_place");
            return false;
        }
        long token = newToken(level);
        y.yokai().retoken(token);
        if (!level.addFreshEntity(mob)) { say(player, "bad_place"); return false; }
        put(level.getServer(), d.bond().get(), bond.with(DEPLOYED, token));
        write(amulet, new AmuletData(d.bond(), token, d.species(), d.name(), AmuletData.DEPLOYED, new CompoundTag(), mob.getHealth(), mob.getMaxHealth()));
        level.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y + 0.6, pos.z, 40, 0.4, 0.6, 0.4, 0.4);
        level.playSound(null, at, ModSounds.AMULET_SUMMON, SoundSource.PLAYERS, 1.0f, 1.0f);
        Companions.push(player);
        return true;
    }

    /** Brings an amulet in line with the registry: a dead or superseded bond leaves it empty, showing "bond lost". */
    public static void validate(ServerLevel level, ItemStack amulet) {
        AmuletData d = data(amulet);
        if (d.bond().isEmpty()) return;
        Bond bond = get(level.getServer(), d.bond().get());
        if (bond == null || !bond.alive() || bond.token() != d.token()) {
            write(amulet, AmuletData.lost(d.species(), d.name()));
            return;
        }
        if (bond.state().equals(DEPLOYED) && d.state().equals(AmuletData.STORED))
            write(amulet, new AmuletData(d.bond(), d.token(), d.species(), d.name(), AmuletData.DEPLOYED, new CompoundTag(), bond.health(), bond.max()));
        else if (bond.state().equals(DEPLOYED) && (bond.health() != d.health() || bond.max() != d.max()))
            write(amulet, new AmuletData(d.bond(), d.token(), d.species(), d.name(), d.state(), d.entity(), bond.health(), bond.max()));
    }

    /** A companion died: the bond is over for good (no resurrection). */
    public static void died(Mob mob) {
        if (!(mob instanceof YokaiCreature y) || y.yokai().bondId() == null || !(mob.level() instanceof ServerLevel level)) return;
        Bond bond = get(level.getServer(), y.yokai().bondId());
        if (bond == null) return;
        put(level.getServer(), y.yokai().bondId(), bond.with(DEAD, 0));
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(bond.owner());
        if (owner != null) {
            owner.sendOverlayMessage(Component.translatable("yokai.quantumdeath.amulet.bond_lost", mob.getDisplayName()).withStyle(ChatFormatting.DARK_RED));
            owner.level().playSound(null, owner.blockPosition(), ModSounds.AMULET_LOST, SoundSource.PLAYERS, 0.8f, 1.0f);
            Companions.push(owner);
        }
    }

    /** Health snapshot of a deployed companion (Resonance panel, amulet tooltip). */
    public static void snapshot(Mob mob) {
        if (!(mob instanceof YokaiCreature y) || y.yokai().bondId() == null || !(mob.level() instanceof ServerLevel level)) return;
        Bond bond = get(level.getServer(), y.yokai().bondId());
        if (bond != null && bond.state().equals(DEPLOYED) && bond.token() == y.yokai().token()
                && (Math.abs(bond.health() - mob.getHealth()) > 0.01f || bond.max() != mob.getMaxHealth()))
            put(level.getServer(), y.yokai().bondId(), bond.snapshot(mob.getHealth(), mob.getMaxHealth()));
    }

    /** A stored amulet was destroyed (burnt, void, despawned): the companion inside is lost permanently. */
    public static void amuletDestroyed(MinecraftServer server, ItemStack amulet) {
        AmuletData d = data(amulet);
        if (!d.state().equals(AmuletData.STORED) || d.bond().isEmpty()) return;
        Bond bond = get(server, d.bond().get());
        if (bond != null && bond.state().equals(STORED) && bond.token() == d.token()) {
            put(server, d.bond().get(), bond.with(LOST, 0));
            Quantumdeath.LOGGER.info("A stored {} was destroyed with its amulet", d.species());
        }
    }

    /** Whether a live companion entity still matches its bond (an outdated copy, e.g. from an old backup, removes itself). */
    public static boolean current(Mob mob) {
        if (!(mob instanceof YokaiCreature y) || y.yokai().bondId() == null || !(mob.level() instanceof ServerLevel level)) return true;
        Bond bond = get(level.getServer(), y.yokai().bondId());
        return bond != null && bond.state().equals(DEPLOYED) && bond.token() == y.yokai().token();
    }
}
