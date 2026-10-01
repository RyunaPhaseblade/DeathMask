package ryuna.qdeath.skills;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import ryuna.qdeath.Quantumdeath;

import java.util.EnumSet;

public final class SkillPackets {
    private SkillPackets() { }
    public record UnlockRequest(String skill) implements CustomPacketPayload {
        public static final Type<UnlockRequest> TYPE = new Type<>(Quantumdeath.id("unlock_skill"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UnlockRequest> CODEC = CustomPacketPayload.codec(UnlockRequest::write, UnlockRequest::read);
        private static UnlockRequest read(RegistryFriendlyByteBuf buf) { return new UnlockRequest(buf.readUtf(64)); }
        private void write(RegistryFriendlyByteBuf buf) { buf.writeUtf(skill, 64); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Sync(int points, EnumSet<SkillId> unlocked) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(Quantumdeath.id("skills_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = CustomPacketPayload.codec(Sync::write, Sync::read);
        private static Sync read(RegistryFriendlyByteBuf buf) {
            var skills = EnumSet.noneOf(SkillId.class);
            int count = buf.readVarInt();
            for (int i = 0; i < count; i++) {
                int ordinal = buf.readVarInt();
                if (ordinal >= 0 && ordinal < SkillId.values().length) skills.add(SkillId.values()[ordinal]);
            }
            return new Sync(buf.readVarInt(), skills);
        }
        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(unlocked.size());
            unlocked.forEach(skill -> buf.writeVarInt(skill.ordinal()));
            buf.writeVarInt(points);
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    /** Server-authorized request for the client to display the local skill-tree screen. */
    public record OpenSkills() implements CustomPacketPayload {
        public static final Type<OpenSkills> TYPE = new Type<>(Quantumdeath.id("open_skills"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenSkills> CODEC = StreamCodec.unit(new OpenSkills());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
