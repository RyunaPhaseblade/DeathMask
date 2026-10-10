package ryuna.qdeath.yokai;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;
import java.util.UUID;

/**
 * What an amulet holds. {@code token} must match the bond registry for the amulet to act: every store, summon or
 * rebinding changes it, so a copied or superseded amulet can never act twice.
 *
 * @param state  EMPTY, DEPLOYED (bound, companion out in the world), STORED (companion inside) or LOST ("bond lost")
 * @param entity the stored companion (only while STORED; empty otherwise)
 */
public record AmuletData(Optional<UUID> bond, long token, String species, String name, String state, CompoundTag entity, float health, float max) {
    public static final String EMPTY = "empty", DEPLOYED = "deployed", STORED = "stored", LOST = "lost";
    public static final AmuletData NONE = new AmuletData(Optional.empty(), 0, "", "", EMPTY, new CompoundTag(), 0, 0);

    public static final Codec<AmuletData> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.optionalFieldOf("bond").forGetter(AmuletData::bond),
            Codec.LONG.optionalFieldOf("token", 0L).forGetter(AmuletData::token),
            Codec.STRING.optionalFieldOf("species", "").forGetter(AmuletData::species),
            Codec.STRING.optionalFieldOf("name", "").forGetter(AmuletData::name),
            Codec.STRING.optionalFieldOf("state", EMPTY).forGetter(AmuletData::state),
            CompoundTag.CODEC.optionalFieldOf("entity", new CompoundTag()).forGetter(AmuletData::entity),
            Codec.FLOAT.optionalFieldOf("health", 0f).forGetter(AmuletData::health),
            Codec.FLOAT.optionalFieldOf("max", 0f).forGetter(AmuletData::max)
    ).apply(i, AmuletData::new));
    public static final StreamCodec<io.netty.buffer.ByteBuf, AmuletData> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    /** Free for a new pact: empty, or showing a lost bond. */
    public boolean free() { return state.equals(EMPTY) || state.equals(LOST); }

    public static AmuletData lost(String species, String name) {
        return new AmuletData(Optional.empty(), 0, species, name, LOST, new CompoundTag(), 0, 0);
    }
}
