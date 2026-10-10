package ryuna.qdeath.hell;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Delayed server actions for telegraphed attacks and scripted sequences. Not persisted: a restart cancels pending effects. */
public final class HellScheduler {
    private HellScheduler() {}

    private record Task(ServerLevel level, long at, Consumer<ServerLevel> action) {}

    private static final List<Task> TASKS = new ArrayList<>();

    public static void later(ServerLevel level, int delayTicks, Consumer<ServerLevel> action) {
        TASKS.add(new Task(level, level.getGameTime() + Math.max(1, delayTicks), action));
    }

    public static void tick(MinecraftServer server) {
        if (TASKS.isEmpty()) return;
        List<Task> due = new ArrayList<>();
        TASKS.removeIf(t -> {
            if (t.level.getGameTime() < t.at) return false;
            due.add(t);
            return true;
        });
        for (Task t : due) t.action.accept(t.level);
    }

    public static void clear() { TASKS.clear(); }
}
