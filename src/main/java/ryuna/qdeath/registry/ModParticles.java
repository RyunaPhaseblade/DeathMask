package ryuna.qdeath.registry;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import ryuna.qdeath.Quantumdeath;

public final class ModParticles {
    private ModParticles() {}

    private static SimpleParticleType reg(String id) {
        return Registry.register(BuiltInRegistries.PARTICLE_TYPE, Quantumdeath.id(id), FabricParticleTypes.simple());
    }

    public static final SimpleParticleType ASH_FLAKE = reg("ash_flake");
    public static final SimpleParticleType SOUL_MOTE = reg("soul_mote");
    public static final SimpleParticleType BILE_DRIP = reg("bile_drip");
    public static final SimpleParticleType TAR_DRIP = reg("tar_drip");
    public static final SimpleParticleType BLOOD_EMBER = reg("blood_ember");
    public static final SimpleParticleType GATE_EMBER = reg("gate_ember");
    public static final SimpleParticleType STATIC = reg("static");
    public static final SimpleParticleType VIGIL_MOTE = reg("vigil_mote");
    /** Limbo: the breath of the living (lures, the Crier's scream) and the glitter at the edge of consecrated light. */
    public static final SimpleParticleType BREATH = reg("breath");
    public static final SimpleParticleType VOTIVE_SPARK = reg("votive_spark");

    public static void init() {}
}
