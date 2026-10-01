package ryuna.qdeath.intro;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.List;

/** Creates the vanilla written book awarded when a player first joins. */
public final class InitialNotes {
    private InitialNotes() { }

    public static ItemStack create() {
        ItemStack notes = new ItemStack(Items.WRITTEN_BOOK);
        notes.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("Notas"),
                "Desconocido",
                0,
                List.of(
                        Filterable.passThrough(Component.empty()
                                .append(Component.literal("✦  PUNTO DE PARTIDA  ✦\n\n").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD))
                                .append(Component.literal("──────────────────").withStyle(ChatFormatting.DARK_GRAY))
                                .append(Component.literal("\n\nUna nota encontrada al despertar.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC))),
                        Filterable.passThrough(Component.empty()
                                .append(Component.literal("Nada. No recuerdo nada de antes de llegar aquí.\n\n").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal("Este mundo parece Minecraft, pero la opción de desconectarse ha desaparecido.\n\n").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal("El miedo es real. Por ahora, mi única opción es sobrevivir.\n\n").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal("Tengo que reunir los materiales y ensamblar el diseño de la nota que encontré al despertar.\n\n").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal("Que sea lo que tenga que ser.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)))
                ),
                false
        ));
        return notes;
    }
}
