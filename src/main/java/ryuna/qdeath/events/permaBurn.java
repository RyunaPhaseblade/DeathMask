package ryuna.qdeath.events;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EquipmentSlot;
import ryuna.qdeath.effects.modEffects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class permaBurn {
    
    private static final Map<UUID, Integer> playerNetherTicks = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                UUID playerId = player.getUUID();

                if (!player.isAlive()) {
                    playerNetherTicks.remove(playerId);
                    continue;
                }

                if (player.level().dimension() == Level.NETHER) {
                    
                    // Comprobar inmunidades
                    boolean hasAntidote = player.hasEffect(modEffects.ANTI_PERMA_BURN);
                    boolean hasFullNetherite = isWearingFullNetherite(player);

                    if (hasAntidote || hasFullNetherite) {
                        // Si está protegido, eliminamos su registro de tiempo. 
                        // Así, cuando pierda la protección, volverá a tener los 5 segundos iniciales de gracia.
                        playerNetherTicks.remove(playerId);
                        continue; // Saltamos a la siguiente persona sin hacerle daño
                    }

                    int ticks = playerNetherTicks.getOrDefault(playerId, 0);
                    ticks++;
                    playerNetherTicks.put(playerId, ticks);

                    applyNetherDamage(player, ticks);
                    
                } else {
                    if (playerNetherTicks.containsKey(playerId)) {
                        playerNetherTicks.remove(playerId);
                    }
                }
            }
        });
    }

    // Método para comprobar la armadura completa
    private static boolean isWearingFullNetherite(ServerPlayer player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(Items.NETHERITE_HELMET)
            && player.getItemBySlot(EquipmentSlot.CHEST).is(Items.NETHERITE_CHESTPLATE)
            && player.getItemBySlot(EquipmentSlot.LEGS).is(Items.NETHERITE_LEGGINGS)
            && player.getItemBySlot(EquipmentSlot.FEET).is(Items.NETHERITE_BOOTS);
    }


    private static void applyNetherDamage(ServerPlayer player, int ticks) {
        if (ticks <= 100) {
            if (ticks % 20 == 0) {
                player.hurtServer((ServerLevel) player.level(), player.damageSources().magic(), 2.0F);
            }
        } 
        else if (ticks <= 200) {
            if ((ticks - 100) % 15 == 0) {
                player.hurtServer((ServerLevel) player.level(), player.damageSources().magic(), 5.0F);
            }
        }  
        else if (ticks <= 300) {
            if ((ticks - 200) % 10 == 0) {
                player.hurtServer((ServerLevel) player.level(), player.damageSources().magic(), 10.0F);
            }
        } 
        else {
            if ((ticks - 300) % 10 == 0) {
                player.hurtServer((ServerLevel) player.level(), player.damageSources().magic(), 20.0F);
            }
        }
    }
}

