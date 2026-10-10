package ryuna.qdeath.jjk.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import ryuna.qdeath.jjk.JjkContent;

/**
 * An entity that holds cursed energy and can therefore take part in Boogie Woogie's Cursed Object Swap. Implement it
 * on a new entity, or add its type to {@code #quantumdeath:cursed_objects}, or tag an item {@code #quantumdeath:cursed_objects}
 * so that the dropped item counts.
 */
public interface CursedObject {
    static boolean is(Entity e) {
        if (e instanceof CursedObject) return true;
        if (e.is(JjkContent.CURSED_OBJECT_ENTITIES)) return true;
        return e instanceof ItemEntity item && item.getItem().is(JjkContent.CURSED_OBJECTS);
    }
}
