package ryuna.qdeath.skills;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import ryuna.qdeath.Quantumdeath;

import java.util.ArrayList;
import java.util.List;

public final class SkillPackets {
    private SkillPackets() { }
    /** Client request to learn a node, by stable id. The server checks points and prerequisites. */
    public record UnlockRequest(String skill) implements CustomPacketPayload {
        public static final Type<UnlockRequest> TYPE = new Type<>(Quantumdeath.id("unlock_skill"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UnlockRequest> CODEC = CustomPacketPayload.codec(UnlockRequest::write, UnlockRequest::read);
        private static UnlockRequest read(RegistryFriendlyByteBuf buf) { return new UnlockRequest(buf.readUtf(64)); }
        private void write(RegistryFriendlyByteBuf buf) { buf.writeUtf(skill, 64); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    /** General points, unlocked ids and the server's cost of every node (in {@link SkillId} order). */
    public record Sync(int points, List<String> unlocked, int[] costs) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(Quantumdeath.id("skills_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = CustomPacketPayload.codec(Sync::write, Sync::read);
        private static Sync read(RegistryFriendlyByteBuf buf) {
            int points = buf.readVarInt();
            int count = Math.min(64, buf.readVarInt());
            List<String> ids = new ArrayList<>(count);
            for (int i = 0; i < count; i++) ids.add(buf.readUtf(64));
            return new Sync(points, ids, buf.readVarIntArray());
        }
        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(points);
            buf.writeVarInt(unlocked.size());
            unlocked.forEach(id -> buf.writeUtf(id, 64));
            buf.writeVarIntArray(costs);
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
