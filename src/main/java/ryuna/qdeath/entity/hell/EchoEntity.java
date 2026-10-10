package ryuna.qdeath.entity.hell;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ryuna.qdeath.registry.ModSounds;

/**
 * Something in the Zenith wears your shape. It walks when you walk and stops when you stop,
 * mirrored across the space between you, and closes the distance a little every time.
 */
public class EchoEntity extends HellMonster {
    private Vec3 lastPlayerPos = Vec3.ZERO;

    public EchoEntity(EntityType<? extends EchoEntity> type, Level level) { super(type, level); xpReward = 8; }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 24).add(Attributes.ATTACK_DAMAGE, 7)
                .add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 32);
    }

    @Override protected void registerGoals() {}

    @Override protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        Player player = level.getNearestPlayer(this, 32);
        if (player == null || player.isCreative() || player.isSpectator()) { lastPlayerPos = Vec3.ZERO; return; }
        setTarget(player);
        Vec3 now = player.position();
        if (lastPlayerPos == Vec3.ZERO) { lastPlayerPos = now; return; }
        Vec3 moved = now.subtract(lastPlayerPos);
        lastPlayerPos = now;
        getLookControl().setLookAt(player, 30, 30);
        double dist = distanceTo(player);
        if (dist < 2.0 && player.hurtTime == 0) {
            doHurtTarget(level, player);
            playAnim(1, 10);
            return;
        }
        if (moved.horizontalDistanceSqr() < 1.0E-4) { setDeltaMovement(0, getDeltaMovement().y, 0); return; }
        // Mirror the step around the line between us, plus a small lean toward the player.
        Vec3 axis = position().subtract(now).multiply(1, 0, 1).normalize();
        Vec3 mirrored = moved.subtract(axis.scale(2 * moved.dot(axis))).multiply(1, 0, 1);
        Vec3 lean = now.subtract(position()).multiply(1, 0, 1).normalize().scale(0.035);
        Vec3 step = mirrored.add(lean);
        setDeltaMovement(step.x, getDeltaMovement().y, step.z);
        if (horizontalCollision && onGround()) jumpFromGround();
        if (random.nextInt(200) == 0) playSound(ModSounds.ECHO_WHISPER, 0.8f, 1.0f);
    }

    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ModSounds.ECHO_WHISPER; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.VIGIL_VANISH; }
}
