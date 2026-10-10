package ryuna.qdeath.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Render state shared by every Hell creature. Fields a creature does not use simply keep their defaults. */
public class HellRenderState extends LivingEntityRenderState {
    /** Current synced animation id (0 = none) and its 0..1 progress (-1 when idle). */
    public int anim;
    public float animProgress = -1;
    public int affix;
    public int phase = 1;
    /** VIGIL form 1..5. */
    public int form = 1;
    /** 0..1 opacity (VIGIL fading in and out). */
    public float alpha = 1f;
    /** Creature-specific switches: Penitent watched, Heretic inquisitor, VIGIL mask broken, VIGIL frozen. */
    public boolean flag;
    /** VIGIL veiled / pursuing. */
    public boolean flag2;
    /** Creature-specific value: Wrathborn rage. */
    public float value;
    public boolean walking;
    /** True for VIGIL's final form (the boss), whose mask breaks in phase 2. */
    public boolean finalForm;
    /** Limbo dead: detection state (ordinal of ryuna.qdeath.limbo.Mind) and cosmetic variant (Hollow clothes). */
    public int mind;
    public int variant;
    /** Human dead: a stable per-creature number in [0,1) for individual gait (limp, neck tilt, twitch rhythm). */
    public float seed;
    /** Human dead: slim-armed body (picked per skin). */
    public boolean slim;
    /** Bloater: 0..1 swelling (alive: breathing of the belly; corpse: fuse progress). Corpse: lying body. */
    public float swell;
    public boolean corpse;
    /** Limbo dead: pose in a pile of bodies (ryuna.qdeath.limbo.Horde: 0 none, 1 pressing, 2 bearing, 4 climbing). */
    public int mound;
    /** Creature mode of the expansion: the parasite's phase, the Ticker's mode, the Strangler's tongue. */
    public int special;
    /** Strangler: the tongue from the jaw to its end, relative to the creature's position (null when it is not out). */
    public net.minecraft.world.phys.@org.jspecify.annotations.Nullable Vec3 tongueFrom, tongueTo;
}
