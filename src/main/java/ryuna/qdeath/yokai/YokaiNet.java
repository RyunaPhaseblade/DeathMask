package ryuna.qdeath.yokai;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import ryuna.qdeath.Quantumdeath;
import ryuna.qdeath.skills.SkillId;
import ryuna.qdeath.skills.SkillManager;

import java.util.List;
import java.util.Map;

/**
 * Yokai packets. The client only ever asks (an order, a quick selection, "note read"); the server decides and sends
 * back what the player is entitled to know: their own perception, their bestiary, their companions and the
 * observations of a tracking eye.
 */
public final class YokaiNet {
    private YokaiNet() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String id) { return new CustomPacketPayload.Type<>(Quantumdeath.id(id)); }

    // ---------------------------------------------------------------- server -> client

    /** The player's own perception and book data. {@code nodes} is a bitmask of the nine new general nodes. */
    public record Self(int perception, boolean override, boolean awakened, boolean noteUnread, long day, int nodes, Map<String, Integer> bestiary)
            implements CustomPacketPayload {
        public static final Type<Self> TYPE = typeOf("yokai_self");
        static final Codec<Self> C = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("p").forGetter(Self::perception), Codec.BOOL.fieldOf("o").forGetter(Self::override),
                Codec.BOOL.fieldOf("a").forGetter(Self::awakened), Codec.BOOL.fieldOf("n").forGetter(Self::noteUnread),
                Codec.LONG.fieldOf("d").forGetter(Self::day), Codec.INT.fieldOf("k").forGetter(Self::nodes),
                Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("b").forGetter(Self::bestiary)).apply(i, Self::new));
        public static final StreamCodec<io.netty.buffer.ByteBuf, Self> CODEC = ByteBufCodecs.fromCodec(C);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** One companion line of the Resonance panel and the book. {@code entity} is -1 when it is not loaded near the owner. */
    public record Status(int entity, String species, String name, float health, float max, String order, String state) {
        static final Codec<Status> C = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("e").forGetter(Status::entity), Codec.STRING.fieldOf("s").forGetter(Status::species),
                Codec.STRING.fieldOf("n").forGetter(Status::name), Codec.FLOAT.fieldOf("h").forGetter(Status::health),
                Codec.FLOAT.fieldOf("m").forGetter(Status::max), Codec.STRING.fieldOf("o").forGetter(Status::order),
                Codec.STRING.fieldOf("t").forGetter(Status::state)).apply(i, Status::new));
    }

    public record Companions(List<Status> list) implements CustomPacketPayload {
        public static final Type<Companions> TYPE = typeOf("yokai_companions");
        public static final StreamCodec<io.netty.buffer.ByteBuf, Companions> CODEC = ByteBufCodecs.fromCodec(
                Status.C.listOf().xmap(Companions::new, Companions::list));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * What a tracking eye last observed. {@code observedAt} is the server game time of the observation (the client
     * shows its age); {@code status} is a lang key suffix: observing, searching, lost, offline, dimension, unloaded.
     */
    public record Tracking(boolean active, String target, int x, int y, int z, String dimension, long observedAt, long now, String status)
            implements CustomPacketPayload {
        public static final Type<Tracking> TYPE = typeOf("yokai_tracking");
        static final Codec<Tracking> C = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("a").forGetter(Tracking::active), Codec.STRING.fieldOf("t").forGetter(Tracking::target),
                Codec.INT.fieldOf("x").forGetter(Tracking::x), Codec.INT.fieldOf("y").forGetter(Tracking::y), Codec.INT.fieldOf("z").forGetter(Tracking::z),
                Codec.STRING.fieldOf("d").forGetter(Tracking::dimension), Codec.LONG.fieldOf("o").forGetter(Tracking::observedAt),
                Codec.LONG.fieldOf("w").forGetter(Tracking::now), Codec.STRING.fieldOf("s").forGetter(Tracking::status)).apply(i, Tracking::new));
        public static final StreamCodec<io.netty.buffer.ByteBuf, Tracking> CODEC = ByteBufCodecs.fromCodec(C);
        public static final Tracking NONE = new Tracking(false, "", 0, 0, 0, "", 0, 0, "");
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Opens the order screen of one's own companion. {@code targets}: players the eye can start tracking right now. */
    /** {@code refresh}: an update after an order, applied only to a screen still open for that companion. */
    public record OpenCompanion(int entity, String species, String name, float health, float max, String order, boolean orders,
                                boolean tracking, List<String> targets, boolean refresh) implements CustomPacketPayload {
        public static final Type<OpenCompanion> TYPE = typeOf("yokai_open_companion");
        static final Codec<OpenCompanion> C = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("e").forGetter(OpenCompanion::entity), Codec.STRING.fieldOf("s").forGetter(OpenCompanion::species),
                Codec.STRING.fieldOf("n").forGetter(OpenCompanion::name), Codec.FLOAT.fieldOf("h").forGetter(OpenCompanion::health),
                Codec.FLOAT.fieldOf("m").forGetter(OpenCompanion::max), Codec.STRING.fieldOf("o").forGetter(OpenCompanion::order),
                Codec.BOOL.fieldOf("r").forGetter(OpenCompanion::orders), Codec.BOOL.fieldOf("k").forGetter(OpenCompanion::tracking),
                Codec.STRING.listOf().fieldOf("t").forGetter(OpenCompanion::targets), Codec.BOOL.fieldOf("u").forGetter(OpenCompanion::refresh))
                .apply(i, OpenCompanion::new));
        public static final StreamCodec<io.netty.buffer.ByteBuf, OpenCompanion> CODEC = ByteBufCodecs.fromCodec(C);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ---------------------------------------------------------------- client -> server

    /** An order for one's own companion: follow, stay, defend, passive, special, ride, storage, track (arg = player name), untrack. */
    public record Order(int entity, String action, String arg) implements CustomPacketPayload {
        public static final Type<Order> TYPE = typeOf("yokai_order");
        static final Codec<Order> C = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("e").forGetter(Order::entity), Codec.STRING.fieldOf("a").forGetter(Order::action),
                Codec.STRING.fieldOf("g").forGetter(Order::arg)).apply(i, Order::new));
        public static final StreamCodec<io.netty.buffer.ByteBuf, Order> CODEC = ByteBufCodecs.fromCodec(C);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Prepared Offerings: bring this item (registry id) from the inventory to the hand. */
    public record QuickSelect(String item) implements CustomPacketPayload {
        public static final Type<QuickSelect> TYPE = typeOf("yokai_quick_select");
        public static final StreamCodec<io.netty.buffer.ByteBuf, QuickSelect> CODEC = ByteBufCodecs.fromCodec(Codec.STRING.xmap(QuickSelect::new, QuickSelect::item));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record NoteRead() implements CustomPacketPayload {
        public static final Type<NoteRead> TYPE = typeOf("yokai_note_read");
        public static final StreamCodec<io.netty.buffer.ByteBuf, NoteRead> CODEC = StreamCodec.unit(new NoteRead());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Self.TYPE, Self.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Companions.TYPE, Companions.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Tracking.TYPE, Tracking.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenCompanion.TYPE, OpenCompanion.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Order.TYPE, Order.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(QuickSelect.TYPE, QuickSelect.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(NoteRead.TYPE, NoteRead.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Order.TYPE, (p, ctx) -> ryuna.qdeath.yokai.Companions.order(ctx.player(), p.entity(), p.action(), p.arg()));
        ServerPlayNetworking.registerGlobalReceiver(QuickSelect.TYPE, (p, ctx) -> Offerings.quickSelect(ctx.player(), p.item()));
        ServerPlayNetworking.registerGlobalReceiver(NoteRead.TYPE, (p, ctx) -> {
            YokaiPlayer.set(ctx.player(), YokaiPlayer.of(ctx.player()).withNote(false));
            syncSelf(ctx.player());
        });
    }

    static boolean real(ServerPlayer player) { return !(player instanceof FakePlayer) && player.connection != null; }

    public static void syncSelf(ServerPlayer player) {
        if (!real(player)) return;
        YokaiPlayer d = YokaiPlayer.of(player);
        YokaiWorld w = YokaiWorld.of(player.level().getServer());
        int nodes = 0;
        for (SkillId s : SkillId.values()) if (!s.legacy() && SkillManager.has(player, s)) nodes |= 1 << (s.ordinal() - SkillId.PERCEPTION_I.ordinal());
        ServerPlayNetworking.send(player, new Self(Perception.level(player), d.perceptionOverride() >= 0, w.awakened(), d.noteUnread(), w.day(), nodes, d.bestiary()));
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (real(player)) ServerPlayNetworking.send(player, payload);
    }
}
