package ryuna.qdeath.yokai;

import net.minecraft.core.BlockPos;

/** A yokai bound to a place: the Kappa's water, the Oni's territory, the Kodama's grove. */
public interface AnchoredYokai {
    void setAnchor(BlockPos pos);
}
