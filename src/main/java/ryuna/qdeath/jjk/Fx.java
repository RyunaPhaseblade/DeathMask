package ryuna.qdeath.jjk;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Particle shapes shared by the techniques. Counts are deliberately small: these are sent to every nearby client. */
public final class Fx {
    private Fx() {}

    public static final DustParticleOptions BLOOD = new DustParticleOptions(0x9a0a0a, 1.3f);
    public static final DustParticleOptions BLOOD_DARK = new DustParticleOptions(0x4f0303, 1.6f);
    public static final DustParticleOptions BLOOD_SMALL = new DustParticleOptions(0x8a0a0a, 0.7f);
    public static final DustParticleOptions BLOOD_BRIGHT = new DustParticleOptions(0xe0201a, 0.9f);
    public static final DustParticleOptions FROST = new DustParticleOptions(0xcff4ff, 1.2f);
    public static final DustParticleOptions FRAME = new DustParticleOptions(0xf4f1e8, 0.8f);
    public static final DustParticleOptions MAGMA = new DustParticleOptions(0xff6a1a, 1.5f);

    public static void at(ServerLevel l, ParticleOptions p, Vec3 v, int count, double spread, double speed) {
        l.sendParticles(p, v.x, v.y, v.z, count, spread, spread, spread, speed);
    }

    /** Visible from far away (ignores the client's particle distance limit). */
    public static void far(ServerLevel l, ParticleOptions p, Vec3 v, int count, double spread, double speed) {
        l.sendParticles(p, true, true, v.x, v.y, v.z, count, spread, spread, spread, speed, net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket.RandomizationType.DEFAULT);
    }

    /** One particle moving with velocity {@code vel} (count 0 makes the offsets a direction). */
    public static void moving(ServerLevel l, ParticleOptions p, Vec3 at, Vec3 vel) {
        l.sendParticles(p, at.x, at.y, at.z, 0, vel.x, vel.y, vel.z, 1.0);
    }

    public static void line(ServerLevel l, ParticleOptions p, Vec3 a, Vec3 b, double step) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1e-3) return;
        int n = (int) Math.min(200, len / step);
        for (int i = 0; i <= n; i++) {
            Vec3 v = a.add(d.scale(i / (double) Math.max(1, n)));
            l.sendParticles(p, v.x, v.y, v.z, 1, 0, 0, 0, 0);
        }
    }

    /** A horizontal ring; {@code outward} > 0 makes it expand. */
    public static void ring(ServerLevel l, ParticleOptions p, Vec3 c, double radius, int count, double outward) {
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count;
            Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 at = c.add(dir.scale(radius));
            if (outward > 0) moving(l, p, at, dir.scale(outward));
            else l.sendParticles(p, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }

    /** A burst flying out in every direction. */
    public static void burst(ServerLevel l, ParticleOptions p, Vec3 c, int count, double speed) {
        var r = l.getRandom();
        for (int i = 0; i < count; i++) {
            Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
            moving(l, p, c, dir.scale(speed * (0.5 + r.nextDouble())));
        }
    }
}
