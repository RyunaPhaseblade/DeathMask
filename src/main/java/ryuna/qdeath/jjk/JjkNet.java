package ryuna.qdeath.jjk;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import ryuna.qdeath.Quantumdeath;

import java.util.ArrayList;
import java.util.List;

/**
 * Cursed-technique packets. The client only ever sends intentions (keys, a class choice, an unlock request); the
 * server validates everything and answers with the authoritative progress and state.
 */
public final class JjkNet {
    private JjkNet() {}

    // ---------------------------------------------------------------- client -> server
    public static final byte PRESS = 0, RELEASE = 1, SECONDARY = 2, NEXT = 3, PREV = 4;

    public record Input(byte action) implements CustomPacketPayload {
        public static final Type<Input> TYPE = new Type<>(Quantumdeath.id("jjk_input"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Input> CODEC = CustomPacketPayload.codec((p, b) -> b.writeByte(p.action), b -> new Input(b.readByte()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Make {@code skill} the selected technique. */
    public record Select(String skill) implements CustomPacketPayload {
        public static final Type<Select> TYPE = new Type<>(Quantumdeath.id("jjk_select"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Select> CODEC = CustomPacketPayload.codec((p, b) -> b.writeUtf(p.skill, 96), b -> new Select(b.readUtf(96)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Unlock(String skill) implements CustomPacketPayload {
        public static final Type<Unlock> TYPE = new Type<>(Quantumdeath.id("jjk_unlock"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Unlock> CODEC = CustomPacketPayload.codec((p, b) -> b.writeUtf(p.skill, 96), b -> new Unlock(b.readUtf(96)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Choose a class; an empty id renounces the current one (its progress is kept). */
    public record ChooseClass(String classId) implements CustomPacketPayload {
        public static final Type<ChooseClass> TYPE = new Type<>(Quantumdeath.id("jjk_choose_class"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ChooseClass> CODEC = CustomPacketPayload.codec((p, b) -> b.writeUtf(p.classId, 64), b -> new ChooseClass(b.readUtf(64)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ---------------------------------------------------------------- server -> client
    public record Progress(String classId, int level, int xp, int xpNext, int tp, List<String> unlocked, String selected) implements CustomPacketPayload {
        public static final Type<Progress> TYPE = new Type<>(Quantumdeath.id("jjk_progress"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Progress> CODEC = CustomPacketPayload.codec(Progress::write, Progress::read);
        private void write(RegistryFriendlyByteBuf b) {
            b.writeUtf(classId, 64);
            b.writeVarInt(level); b.writeVarInt(xp); b.writeVarInt(xpNext); b.writeVarInt(tp);
            b.writeVarInt(unlocked.size());
            unlocked.forEach(s -> b.writeUtf(s, 96));
            b.writeUtf(selected, 96);
        }
        private static Progress read(RegistryFriendlyByteBuf b) {
            String c = b.readUtf(64);
            int level = b.readVarInt(), xp = b.readVarInt(), next = b.readVarInt(), tp = b.readVarInt();
            int n = Math.min(256, b.readVarInt());
            List<String> u = new ArrayList<>(n);
            for (int i = 0; i < n; i++) u.add(b.readUtf(96));
            return new Progress(c, level, xp, next, tp, u, b.readUtf(96));
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Cooldown(String skill, int left, int total) {}

    /** A labelled HUD readout: {@code key} is a lang key taking {@code arg}; {@code fill} < 0 hides the bar. */
    public record Indicator(String key, String arg, float fill, int color) {}

    public record State(float energy, float energyMax, float resource, float resourceMax, float charge,
                        List<Cooldown> cooldowns, List<Indicator> indicators) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(Quantumdeath.id("jjk_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = CustomPacketPayload.codec(State::write, State::read);
        private void write(RegistryFriendlyByteBuf b) {
            b.writeFloat(energy); b.writeFloat(energyMax); b.writeFloat(resource); b.writeFloat(resourceMax); b.writeFloat(charge);
            b.writeVarInt(cooldowns.size());
            for (Cooldown c : cooldowns) { b.writeUtf(c.skill, 96); b.writeVarInt(c.left); b.writeVarInt(c.total); }
            b.writeVarInt(indicators.size());
            for (Indicator i : indicators) { b.writeUtf(i.key, 96); b.writeUtf(i.arg, 64); b.writeFloat(i.fill); b.writeInt(i.color); }
        }
        private static State read(RegistryFriendlyByteBuf b) {
            float e = b.readFloat(), em = b.readFloat(), r = b.readFloat(), rm = b.readFloat(), ch = b.readFloat();
            int n = Math.min(64, b.readVarInt());
            List<Cooldown> cds = new ArrayList<>(n);
            for (int k = 0; k < n; k++) cds.add(new Cooldown(b.readUtf(96), b.readVarInt(), b.readVarInt()));
            int m = Math.min(16, b.readVarInt());
            List<Indicator> ind = new ArrayList<>(m);
            for (int k = 0; k < m; k++) ind.add(new Indicator(b.readUtf(96), b.readUtf(64), b.readFloat(), b.readInt()));
            return new State(e, em, r, rm, ch, cds, ind);
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** One-shot screen feedback: a coloured flash ({@code color} ARGB) lasting {@code ticks}. */
    public record Flash(int color, int ticks) implements CustomPacketPayload {
        public static final Type<Flash> TYPE = new Type<>(Quantumdeath.id("jjk_flash"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Flash> CODEC = CustomPacketPayload.codec((p, b) -> { b.writeInt(p.color); b.writeVarInt(p.ticks); },
                b -> new Flash(b.readInt(), b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Input.TYPE, Input.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Select.TYPE, Select.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Unlock.TYPE, Unlock.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ChooseClass.TYPE, ChooseClass.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Progress.TYPE, Progress.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Flash.TYPE, Flash.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Input.TYPE, (p, ctx) -> Techniques.input(ctx.player(), p.action()));
        ServerPlayNetworking.registerGlobalReceiver(Select.TYPE, (p, ctx) -> Techniques.select(ctx.player(), p.skill()));
        ServerPlayNetworking.registerGlobalReceiver(Unlock.TYPE, (p, ctx) -> Techniques.requestUnlock(ctx.player(), p.skill()));
        ServerPlayNetworking.registerGlobalReceiver(ChooseClass.TYPE, (p, ctx) -> Techniques.requestClass(ctx.player(), p.classId()));
    }
}
