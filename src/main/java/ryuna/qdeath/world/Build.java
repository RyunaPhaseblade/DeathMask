package ryuna.qdeath.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultConfig;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;
import ryuna.qdeath.Quantumdeath;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Block writer for structure builders: coordinates are relative to the structure origin and clipped to the current chunk. */
public final class Build {
    public final WorldGenLevel level;
    public final BoundingBox box;
    public final BlockPos origin;
    public final long seed;
    public final RandomSource rand;

    public Build(WorldGenLevel level, BoundingBox box, BlockPos origin, long seed) {
        this.level = level;
        this.box = box;
        this.origin = origin;
        this.seed = seed;
        this.rand = RandomSource.create(seed);
    }

    public BlockPos at(int x, int y, int z) { return origin.offset(x, y, z); }
    public boolean inside(int x, int y, int z) { return box.isInside(at(x, y, z)); }

    public void set(int x, int y, int z, BlockState s) {
        BlockPos p = at(x, y, z);
        if (box.isInside(p)) level.setBlock(p, s, 2);
    }

    /** Schedules a block tick (lanterns consecrated at generation register themselves when it fires). */
    public void tick(int x, int y, int z, net.minecraft.world.level.block.Block block, int delay) {
        BlockPos p = at(x, y, z);
        if (box.isInside(p)) level.scheduleTick(p, block, delay);
    }

    public BlockState get(int x, int y, int z) {
        BlockPos p = at(x, y, z);
        return box.isInside(p) ? level.getBlockState(p) : Blocks.AIR.defaultBlockState();
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, s);
    }

    /** Hollow box: walls/floor/ceiling of {@code wall}, inside air. */
    public void room(int x1, int y1, int z1, int x2, int y2, int z2, BlockState wall) {
        fill(x1, y1, z1, x2, y2, z2, wall);
        fill(x1 + 1, y1 + 1, z1 + 1, x2 - 1, y2 - 1, z2 - 1, Blocks.AIR.defaultBlockState());
    }

    /** Deterministic per-position noise in [0,1), independent of build order. */
    public double noise(int x, int y, int z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        h ^= h >>> 33; h *= 0xff51afd7ed558ccdL; h ^= h >>> 33; h *= 0xc4ceb9fe1a85ec53L; h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Carves an ellipsoid (with a ragged edge) to air. Blocks below {@code floorY} inside it become {@code below}. */
    public void carve(int rx, int ry, int rz, int cy, int floorY, BlockState below) {
        for (int x = -rx; x <= rx; x++) for (int z = -rz; z <= rz; z++) {
            if (!box.isInside(at(x, 0, z).atY(box.minY()))) continue;
            for (int y = -ry; y <= ry; y++) {
                double d = (double) (x * x) / (rx * rx) + (double) (y * y) / (ry * ry) + (double) (z * z) / (rz * rz);
                if (d > 1.0 - noise(x, cy + y, z) * 0.12) continue;
                int wy = cy + y;
                set(x, wy, z, wy < floorY ? below : Blocks.AIR.defaultBlockState());
            }
        }
    }

    public void disc(int cx, int cz, int y, double r, BlockState s) {
        int ir = (int) Math.ceil(r);
        for (int x = -ir; x <= ir; x++) for (int z = -ir; z <= ir; z++) if (x * x + z * z <= r * r) set(cx + x, y, cz + z, s);
    }

    public void ring(int cx, int cz, int y, double rIn, double rOut, BlockState s) {
        int ir = (int) Math.ceil(rOut);
        for (int x = -ir; x <= ir; x++) for (int z = -ir; z <= ir; z++) {
            double d = x * x + z * z;
            if (d <= rOut * rOut && d >= rIn * rIn) set(cx + x, y, cz + z, s);
        }
    }

    public void pillar(int x, int z, int y1, int y2, BlockState s) { for (int y = y1; y <= y2; y++) set(x, y, z, s); }

    /** Column of {@code s} from y down until it meets solid ground (max {@code depth}). */
    public void foundation(int x, int y, int z, int depth, BlockState s) {
        for (int i = 0; i < depth; i++) {
            BlockPos p = at(x, y - i, z);
            if (!box.isInside(p)) continue;
            BlockState cur = level.getBlockState(p);
            if (cur.isSolid() && !cur.is(Blocks.LAVA)) return;
            level.setBlock(p, s, 2);
        }
    }

    public void chest(int x, int y, int z, Direction facing, String lootTable) {
        BlockPos p = at(x, y, z);
        if (!box.isInside(p)) return;
        level.setBlock(p, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), 2);
        RandomizableContainer.setBlockEntityLootTable(level, rand, p, loot(lootTable));
    }

    public void barrel(int x, int y, int z, String lootTable) {
        BlockPos p = at(x, y, z);
        if (!box.isInside(p)) return;
        level.setBlock(p, Blocks.BARREL.defaultBlockState(), 2);
        RandomizableContainer.setBlockEntityLootTable(level, rand, p, loot(lootTable));
    }

    public static ResourceKey<LootTable> loot(String path) {
        return ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, Quantumdeath.id(path));
    }

    public void spawner(int x, int y, int z, EntityType<?> type) {
        BlockPos p = at(x, y, z);
        if (!box.isInside(p)) return;
        level.setBlock(p, Blocks.SPAWNER.defaultBlockState(), 2);
        if (level.getBlockEntity(p) instanceof SpawnerBlockEntity spawner) spawner.setEntityId(type, rand);
    }

    public void vault(int x, int y, int z, Direction facing, String lootTable, ItemStack key) {
        BlockPos p = at(x, y, z);
        if (!box.isInside(p)) return;
        level.setBlock(p, Blocks.VAULT.defaultBlockState().setValue(net.minecraft.world.level.block.VaultBlock.FACING, facing), 2);
        if (level.getBlockEntity(p) instanceof VaultBlockEntity vault)
            vault.setConfig(new VaultConfig(loot(lootTable), 4.0, 4.5, key, Optional.empty()));
    }

    public void lectern(int x, int y, int z, Direction facing, ItemStack book) {
        BlockPos p = at(x, y, z);
        if (!box.isInside(p)) return;
        level.setBlock(p, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, facing).setValue(LecternBlock.HAS_BOOK, true), 2);
        if (level.getBlockEntity(p) instanceof LecternBlockEntity lectern) lectern.setBook(book);
    }

    /** Wall sign hanging on the block behind it (facing away from that block). Lines are translation keys. */
    public void sign(int x, int y, int z, Direction facing, String... keys) {
        BlockPos p = at(x, y, z);
        if (!box.isInside(p)) return;
        level.setBlock(p, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing), 2);
        if (level.getBlockEntity(p) instanceof SignBlockEntity sign) {
            List<Component> lines = new ArrayList<>();
            for (int i = 0; i < 4; i++) lines.add(i < keys.length ? Component.translatable(keys[i]) : Component.empty());
            // setText() needs a live level; during worldgen apply the text as a component instead.
            sign.applyComponents(net.minecraft.core.component.DataComponentMap.EMPTY, net.minecraft.core.component.DataComponentPatch.builder()
                    .set(DataComponents.SIGN_TEXT_FRONT, new SignText(lines, lines, DyeColor.LIGHT_GRAY, true)).build());
        }
    }

    /** Spawns a persistent mob once (only in the chunk containing the spot). */
    public <T extends Mob> void mob(int x, int y, int z, EntityType<T> type, Consumer<T> setup) {
        BlockPos p = at(x, y, z);
        if (!box.isInside(p)) return;
        T mob = type.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (mob == null) return;
        mob.setPersistenceRequired();
        mob.snapTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, rand.nextFloat() * 360, 0);
        setup.accept(mob);
        level.addFreshEntityWithPassengers(mob);
    }

    /** A written book whose pages are translation keys {@code book.quantumdeath.<id>.<n>}. */
    public static ItemStack book(String id, String title, int pages) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        List<Filterable<Component>> content = new ArrayList<>();
        for (int i = 0; i < pages; i++) content.add(Filterable.passThrough(Component.translatable("book.quantumdeath." + id + "." + i)));
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(title), "?", 0, content, true));
        return book;
    }
}
