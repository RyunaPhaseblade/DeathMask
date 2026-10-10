package ryuna.qdeath.client.render;

/** Duck interface on LivingEntityRenderState: whether the creature drawn this frame is a possessed host. */
public interface PossessedRenderState {
    boolean quantumdeath$possessed();
    void quantumdeath$setPossessed(boolean possessed);
}
