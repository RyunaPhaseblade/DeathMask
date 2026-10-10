package ryuna.qdeath.registry;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import ryuna.qdeath.Quantumdeath;

/** Every sound is original audio synthesized by tools/gen_sounds.py (see sounds.json). */
public final class ModSounds {
    private ModSounds() {}

    private static SoundEvent reg(String id) {
        return Registry.register(BuiltInRegistries.SOUND_EVENT, Quantumdeath.id(id), SoundEvent.createVariableRangeEvent(Quantumdeath.id(id)));
    }

    private static Holder<SoundEvent> regHolder(String id) {
        return Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, Quantumdeath.id(id), SoundEvent.createVariableRangeEvent(Quantumdeath.id(id)));
    }

    // Layer ambience and music (referenced from biome JSON)
    public static final SoundEvent AMBIENT_VESTIBULE = reg("ambient.vestibule.loop");
    public static final SoundEvent AMBIENT_VESTIBULE_ADD = reg("ambient.vestibule.additions");
    public static final SoundEvent AMBIENT_GORGE = reg("ambient.gorge.loop");
    public static final SoundEvent AMBIENT_GORGE_ADD = reg("ambient.gorge.additions");
    public static final SoundEvent AMBIENT_MIRE = reg("ambient.mire.loop");
    public static final SoundEvent AMBIENT_MIRE_ADD = reg("ambient.mire.additions");
    public static final SoundEvent AMBIENT_DIS = reg("ambient.dis.loop");
    public static final SoundEvent AMBIENT_DIS_ADD = reg("ambient.dis.additions");
    public static final SoundEvent AMBIENT_ZENITH = reg("ambient.zenith.loop");
    public static final SoundEvent AMBIENT_ZENITH_ADD = reg("ambient.zenith.additions");
    public static final Holder<SoundEvent> MUSIC_VESTIBULE = regHolder("music.vestibule");
    public static final Holder<SoundEvent> MUSIC_GORGE = regHolder("music.gorge");
    public static final Holder<SoundEvent> MUSIC_MIRE = regHolder("music.mire");
    public static final Holder<SoundEvent> MUSIC_DIS = regHolder("music.dis");
    public static final Holder<SoundEvent> MUSIC_ZENITH = regHolder("music.zenith");
    public static final Holder<SoundEvent> MUSIC_BOSS = regHolder("music.boss");
    public static final Holder<SoundEvent> MUSIC_VIGIL = regHolder("music.vigil");

    // VIGIL
    public static final SoundEvent VIGIL_PRESENCE = reg("vigil.presence");
    public static final SoundEvent VIGIL_TOLL = reg("vigil.toll");
    public static final SoundEvent VIGIL_WHISPER = reg("vigil.whisper");
    public static final SoundEvent VIGIL_STEP = reg("vigil.step");
    public static final SoundEvent VIGIL_BREATH = reg("vigil.breath");
    public static final SoundEvent VIGIL_VANISH = reg("vigil.vanish");
    public static final SoundEvent VIGIL_PURSUIT = reg("vigil.pursuit");
    public static final SoundEvent VIGIL_TOUCH = reg("vigil.touch");
    public static final SoundEvent VIGIL_MASK_BREAK = reg("vigil.mask_break");
    public static final SoundEvent VIGIL_ROAR = reg("vigil.roar");
    public static final SoundEvent VIGIL_DEATH = reg("vigil.death");

    // Monsters
    public static final SoundEvent HOLLOW_AMBIENT = reg("hollow.ambient");
    public static final SoundEvent HOLLOW_HURT = reg("hollow.hurt");
    public static final SoundEvent HOLLOW_DEATH = reg("hollow.death");
    public static final SoundEvent KNIGHT_AMBIENT = reg("cinder_knight.ambient");
    public static final SoundEvent KNIGHT_SWING = reg("cinder_knight.swing");
    public static final SoundEvent GLUTTON_AMBIENT = reg("glutton.ambient");
    public static final SoundEvent GLUTTON_HURT = reg("glutton.hurt");
    public static final SoundEvent GLUTTON_VOMIT = reg("glutton.vomit");
    public static final SoundEvent SULLEN_AMBIENT = reg("sullen.ambient");
    public static final SoundEvent SULLEN_GRAB = reg("sullen.grab");
    public static final SoundEvent WRATHBORN_ROAR = reg("wrathborn.roar");
    public static final SoundEvent WRATHBORN_HURT = reg("wrathborn.hurt");
    public static final SoundEvent HERETIC_CHANT = reg("heretic.chant");
    public static final SoundEvent HERETIC_HURT = reg("heretic.hurt");
    public static final SoundEvent FURY_SHRIEK = reg("fury.shriek");
    public static final SoundEvent PENITENT_CRACK = reg("penitent.crack");
    public static final SoundEvent ECHO_WHISPER = reg("echo.whisper");
    public static final SoundEvent MONSTER_DEATH = reg("monster.death");

    // The dead of the Limbo: every signal that matters has its own sound
    public static final SoundEvent HOLLOW_BREATH = reg("hollow.breath");
    public static final SoundEvent HOLLOW_CALL = reg("hollow.call");
    public static final SoundEvent HOLLOW_SHAMBLE = reg("hollow.shamble");
    public static final SoundEvent RUNAWAY_PANT = reg("runaway.pant");
    public static final SoundEvent RUNAWAY_CHARGE = reg("runaway.charge");
    public static final SoundEvent CRIER_INHALE = reg("crier.inhale");
    public static final SoundEvent CRIER_SCREAM = reg("crier.scream");
    public static final SoundEvent CRAWLER_SCRAPE = reg("crawler.scrape");
    public static final SoundEvent CRAWLER_LUNGE = reg("crawler.lunge");
    // The human dead of the refined Limbo: organic voices (gen_sounds.py, voice synthesis), mixed by LimboVoices
    public static final SoundEvent DEAD_GROAN = reg("dead.groan");
    public static final SoundEvent DEAD_CHASE = reg("dead.chase");
    public static final SoundEvent DEAD_ATTACK = reg("dead.attack");
    public static final SoundEvent DEAD_HURT = reg("dead.hurt");
    public static final SoundEvent DEAD_DEATH = reg("dead.death");
    public static final SoundEvent DEAD_HORDE = reg("dead.horde");
    public static final SoundEvent DEAD_STEP = reg("dead.step");
    public static final SoundEvent STALKER_HISS = reg("stalker.hiss");
    public static final SoundEvent STALKER_SCRAPE = reg("stalker.scrape");
    public static final SoundEvent CRIER_THROAT = reg("crier.throat");
    public static final SoundEvent FOREMAN_SIGNAL = reg("foreman.signal");
    public static final SoundEvent FOREMAN_AMBIENT = reg("foreman.ambient");
    public static final SoundEvent LANKY_GROAN = reg("lanky.groan");
    public static final SoundEvent LANKY_SWING = reg("lanky.swing");
    public static final SoundEvent BLOATER_GURGLE = reg("bloater.gurgle");
    public static final SoundEvent BLOATER_SWELL = reg("bloater.swell");
    public static final SoundEvent BLOATER_BURST = reg("bloater.burst");
    public static final SoundEvent BREAKER_GRUNT = reg("breaker.grunt");
    public static final SoundEvent BREAKER_ROAR = reg("breaker.roar");
    public static final SoundEvent BREAKER_SLAM = reg("breaker.slam");
    public static final SoundEvent RUNT_SHRIEK = reg("runt.shriek");
    public static final SoundEvent RUNT_CHIRP = reg("runt.chirp");
    public static final SoundEvent RUNAWAY_SCREAM = reg("runaway.scream");
    public static final SoundEvent CRAWLER_HISS = reg("crawler.hiss");
    // The expansion: parasites, siege and climbing hordes
    public static final SoundEvent PARASITE_CHITTER = reg("parasite.chitter");
    public static final SoundEvent PARASITE_LEAP = reg("parasite.leap");
    public static final SoundEvent PARASITE_LATCH = reg("parasite.latch");
    public static final SoundEvent PARASITE_POSSESS = reg("parasite.possess");
    public static final SoundEvent TICKER_PULSE = reg("ticker.pulse");
    public static final SoundEvent TICKER_HISS = reg("ticker.hiss");
    public static final SoundEvent TICKER_ATTACH = reg("ticker.attach");
    public static final SoundEvent TICKER_BREACH = reg("ticker.breach");
    public static final SoundEvent TICKER_BURST = reg("ticker.burst");
    public static final SoundEvent STRANGLER_GURGLE = reg("strangler.gurgle");
    public static final SoundEvent STRANGLER_LASH = reg("strangler.lash");
    public static final SoundEvent STRANGLER_REEL = reg("strangler.reel");
    public static final SoundEvent STRANGLER_SEVER = reg("strangler.sever");
    public static final SoundEvent SPITTER_RETCH = reg("bile_spitter.retch");
    public static final SoundEvent SPITTER_SPIT = reg("bile_spitter.spit");
    public static final SoundEvent SPITTER_SPLAT = reg("bile_spitter.splat");
    public static final SoundEvent RIBS_CRACK = reg("rib_thrower.crack");
    public static final SoundEvent RIBS_THROW = reg("rib_thrower.throw");
    public static final SoundEvent RIBS_IMPACT = reg("rib_thrower.impact");
    public static final SoundEvent MOUND_GATHER = reg("mound.gather");
    public static final SoundEvent MOUND_CLIMB = reg("mound.climb");
    public static final SoundEvent DEAD_CLAW = reg("dead.claw");
    public static final SoundEvent SUNDERED_SPLIT = reg("sundered.split");
    public static final SoundEvent SUNDERED_AMBIENT = reg("sundered.ambient");
    public static final SoundEvent OSSUARY_RATTLE = reg("walking_ossuary.rattle");
    public static final SoundEvent OSSUARY_SLAM = reg("walking_ossuary.slam");
    public static final SoundEvent HOUND_SNIFF = reg("mourning_hound.sniff");
    public static final SoundEvent HOUND_CHATTER = reg("mourning_hound.chatter");
    public static final SoundEvent BEARER_DIRGE = reg("wake_bearer.dirge");
    public static final SoundEvent BEARER_GATHER = reg("wake_bearer.gather");
    public static final SoundEvent BEARER_SLAM = reg("wake_bearer.slam");

    // Bosses
    public static final SoundEvent BOSS_RISE = reg("boss.rise");
    public static final SoundEvent BOSS_ROAR = reg("boss.roar");
    public static final SoundEvent BOSS_SLAM = reg("boss.slam");
    public static final SoundEvent BOSS_DEFEATED = reg("boss.defeated");
    public static final SoundEvent TOLL_KEEPER_LANTERN = reg("toll_keeper.lantern");
    public static final SoundEvent MATRIARCH_INHALE = reg("matriarch.inhale");
    public static final SoundEvent STYXMAW_EMERGE = reg("styxmaw.emerge");
    public static final SoundEvent HERESIARCH_CHANT = reg("heresiarch.chant");

    // World
    public static final SoundEvent GATE_HUM = reg("gate.hum");
    public static final SoundEvent GATE_DENIED = reg("gate.denied");
    public static final SoundEvent SHRINE_FORMS = reg("shrine.forms");
    public static final SoundEvent MAW_BITE = reg("maw.bite");
    public static final SoundEvent TREE_WAIL = reg("tree.wail");
    public static final SoundEvent STATIC_BURST = reg("static.burst");
    public static final SoundEvent CHOIR_BELL = reg("choir.bell");
    public static final SoundEvent SEAL_BREAK = reg("seal.break");
    public static final SoundEvent ALTAR_SUMMON = reg("altar.summon");
    public static final SoundEvent MOURNING_BELL = reg("item.mourning_bell");
    public static final SoundEvent MIRROR_GAZE = reg("item.mirror_gaze");
    public static final SoundEvent HOOK_FIRE = reg("item.hook_fire");

    // Limbo world: distant bells, votive consecration, siege, graves, lures and the plague of the Hollows
    public static final SoundEvent LIMBO_BELL_FAR = reg("limbo.bell_far");
    public static final SoundEvent LANTERN_CONSECRATE = reg("lantern.consecrate");
    public static final SoundEvent LANTERN_GUTTER = reg("lantern.gutter");
    public static final SoundEvent LANTERN_EXTINGUISH = reg("lantern.extinguish");
    public static final SoundEvent SIEGE_STRAIN = reg("siege.strain");
    public static final SoundEvent GRAVE_STIR = reg("grave.stir");
    public static final SoundEvent LURE_BREATH = reg("lure.breath");
    public static final SoundEvent PLAGUE_TOLL = reg("plague.toll");
    public static final SoundEvent PLAGUE_EMERGE = reg("plague.emerge");
    public static final SoundEvent PLAGUE_PURGE = reg("plague.purge");
    public static final SoundEvent PLAGUE_PURGED = reg("plague.purged");

    // Cursed techniques (jjk): original audio from tools/gen_jjk.py
    public static final SoundEvent JJK_LEVEL_UP = reg("jjk.level_up");
    public static final SoundEvent JJK_UNLOCK = reg("jjk.unlock");
    public static final SoundEvent JJK_BLOOD_SHOT = reg("jjk.blood_shot");
    public static final SoundEvent JJK_BLOOD_CONVERGE = reg("jjk.blood_converge");
    public static final SoundEvent JJK_PIERCING_BLOOD = reg("jjk.piercing_blood");
    public static final SoundEvent JJK_SUPERNOVA = reg("jjk.supernova");
    public static final SoundEvent JJK_ICE_FORM = reg("jjk.ice_form");
    public static final SoundEvent JJK_FROST_CALM = reg("jjk.frost_calm");
    public static final SoundEvent JJK_EMBER_BUZZ = reg("jjk.ember_buzz");
    public static final SoundEvent JJK_VOLCANO_RUMBLE = reg("jjk.volcano_rumble");
    public static final SoundEvent JJK_ERUPTION = reg("jjk.eruption");
    public static final SoundEvent JJK_METEOR_FALL = reg("jjk.meteor_fall");
    public static final SoundEvent JJK_METEOR_IMPACT = reg("jjk.meteor_impact");
    public static final SoundEvent JJK_PROJECTION_FRAME = reg("jjk.projection_frame");
    public static final SoundEvent JJK_FRAME_FREEZE = reg("jjk.frame_freeze");
    public static final SoundEvent JJK_CLAP = reg("jjk.clap");
    public static final SoundEvent JJK_SWAP = reg("jjk.swap");

    // The yokai awakening (tools/gen_yokai.py). Subtitles describe what is perceptible, never who.
    public static final SoundEvent YOKAI_AWAKENING = reg("yokai.awakening");
    public static final SoundEvent YOKAI_SKILL = reg("yokai.skill");
    public static final SoundEvent YOKAI_RETREAT = reg("yokai.retreat");
    public static final SoundEvent YOKAI_PACT = reg("yokai.pact");
    public static final SoundEvent YOKAI_ORDER = reg("yokai.order");
    public static final SoundEvent RESONANCE_WARNING = reg("yokai.resonance_warning");
    public static final SoundEvent YOKAI_HURT = reg("yokai.hurt");
    public static final SoundEvent YOKAI_DEATH = reg("yokai.death");
    public static final SoundEvent ONI_AMBIENT = reg("oni.ambient");
    public static final SoundEvent AMULET_STORE = reg("amulet.store");
    public static final SoundEvent AMULET_SUMMON = reg("amulet.summon");
    public static final SoundEvent AMULET_LOST = reg("amulet.lost");
    public static final SoundEvent FALSE_CREEPER_HISS = reg("false_creeper.hiss");
    public static final SoundEvent FALSE_CREEPER_BURST = reg("false_creeper.burst");
    public static final SoundEvent BIFAUCE_BREATH = reg("bifauce.breath");
    public static final SoundEvent BIFAUCE_OPEN = reg("bifauce.open");
    public static final SoundEvent BIFAUCE_CHARGE = reg("bifauce.charge");
    public static final SoundEvent DAMPER_CREAK = reg("damper.creak");
    public static final SoundEvent DAMPER_STRIKE = reg("damper.strike");
    public static final SoundEvent EYE_GAZE = reg("swarm_eye.gaze");
    public static final SoundEvent EYE_SHRIEK = reg("swarm_eye.shriek");
    public static final SoundEvent EYE_SHOT = reg("swarm_eye.shot");
    public static final SoundEvent KAPPA_BUBBLES = reg("kappa.bubbles");
    public static final SoundEvent KAPPA_BITE = reg("kappa.bite");
    public static final SoundEvent KAPPA_AMBIENT = reg("kappa.ambient");
    public static final SoundEvent KAPPA_SPLASH = reg("kappa.splash");
    public static final SoundEvent ONI_STEP = reg("oni.step");
    public static final SoundEvent ONI_WINDUP = reg("oni.windup");
    public static final SoundEvent ONI_SLAM = reg("oni.slam");
    public static final SoundEvent CHEST_KNOCK = reg("chest_tsukumogami.knock");
    public static final SoundEvent CHEST_LID = reg("chest_tsukumogami.lid");
    public static final SoundEvent CHEST_BITE = reg("chest_tsukumogami.bite");
    public static final SoundEvent TANUKI_DRUM = reg("tanuki.drum");
    public static final SoundEvent TANUKI_PUFF = reg("tanuki.puff");
    public static final SoundEvent TANUKI_CHITTER = reg("tanuki.chitter");
    public static final SoundEvent TENGU_WINGS = reg("tengu.wings");
    public static final SoundEvent TENGU_WINDUP = reg("tengu.windup");
    public static final SoundEvent TENGU_GUST = reg("tengu.gust");
    public static final SoundEvent KODAMA_RATTLE = reg("kodama.rattle");
    public static final SoundEvent NURIKABE_SCRAPE = reg("nurikabe.scrape");
    public static final SoundEvent NURIKABE_SHIFT = reg("nurikabe.shift");
    public static final SoundEvent ITTAN_RUSTLE = reg("ittan_momen.rustle");
    public static final SoundEvent ITTAN_WRAP = reg("ittan_momen.wrap");
    public static final SoundEvent BELL_TINKLE = reg("bell_tsukumogami.tinkle");
    public static final SoundEvent BELL_WARN = reg("bell_tsukumogami.warn");

    public static void init() {}
}
