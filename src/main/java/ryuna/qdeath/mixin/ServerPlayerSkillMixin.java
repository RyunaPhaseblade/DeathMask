package ryuna.qdeath.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ryuna.qdeath.skills.SkillProgress;
import ryuna.qdeath.skills.SkillProgressHolder;
import ryuna.qdeath.skills.SkillLevelTracker;
import ryuna.qdeath.intro.PlayerIntroHolder;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerSkillMixin implements SkillProgressHolder, SkillLevelTracker, PlayerIntroHolder {
    @Unique private final SkillProgress quantumdeath$progress = new SkillProgress();
    @Unique private int quantumdeath$lastObservedLevel = -1;
    @Unique private boolean quantumdeath$receivedNotes;
    @Override public SkillProgress quantumdeath$getSkillProgress() { return quantumdeath$progress; }
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void quantumdeath$save(ValueOutput output, CallbackInfo ci) {
        quantumdeath$progress.writeTo(output.child("QuantumdeathSkills"));
        output.putBoolean("QuantumdeathReceivedNotes", quantumdeath$receivedNotes);
    }
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void quantumdeath$load(ValueInput input, CallbackInfo ci) {
        input.child("QuantumdeathSkills").ifPresent(quantumdeath$progress::readFrom);
        quantumdeath$receivedNotes = input.getBooleanOr("QuantumdeathReceivedNotes", false);
    }
    @Override public boolean quantumdeath$hasReceivedNotes() { return quantumdeath$receivedNotes; }
    @Override public void quantumdeath$setReceivedNotes(boolean received) { quantumdeath$receivedNotes = received; }
    @Override public boolean quantumdeath$updateLevelTracking(int currentLevel) {
        if (quantumdeath$lastObservedLevel < 0) { quantumdeath$lastObservedLevel = currentLevel; return false; }
        boolean increased = currentLevel > quantumdeath$lastObservedLevel;
        if (increased) quantumdeath$progress.addPoints(currentLevel - quantumdeath$lastObservedLevel);
        quantumdeath$lastObservedLevel = currentLevel;
        return increased;
    }
}
