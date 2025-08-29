package dev.nighter.teaTeleport.utils;

import dev.nighter.teaTeleport.TeaTeleport;
import dev.nighter.teaTeleport.Scheduler;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class TeleportUtil {
    private final TeaTeleport plugin;
    private final Set<UUID> tpPlayers = ConcurrentHashMap.newKeySet();

    public TeleportUtil(TeaTeleport plugin) {
        this.plugin = plugin;
    }
    
    public CompletableFuture<Boolean> teleportAsync(Player player, Location loc) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        UUID uuid = player.getUniqueId();
        
        if (player.hasPermission("teateleport.bypass")) {
            player.teleportAsync(loc).thenAccept(success -> result.complete(success));
            return result;
        }
        
        if (tpPlayers.contains(uuid)) {
            result.complete(false);
            return result;
        }
        
        AtomicInteger countdown = new AtomicInteger(plugin.getConfig().getInt("teleport-delay"));
        double maxDistance = plugin.getConfig().getDouble("movement-cancel.max-distance");
        boolean cancelOnMove = plugin.getConfig().getBoolean("movement-cancel.enabled");
        
        if (countdown.get() <= 0) {
            result.complete(false);
            return result;
        }
        
        Location originLocation = cancelOnMove ? player.getLocation() : null;
        tpPlayers.add(uuid);
        
        Scheduler.Task[] holder = new Scheduler.Task[1];
        
        holder[0] = Scheduler.runTaskTimerAsync(() -> {
            if (player == null || !player.isOnline()) {
                cleanup(uuid, holder[0]);
                result.complete(false);
                return;
            }
            
            if (cancelOnMove) {
                if (!originLocation.getWorld().equals(player.getWorld()) || originLocation.distance(player.getLocation()) > maxDistance) {
                    cleanup(uuid, holder[0]);
                    plugin.getMessageService().sendMessage(player, "teleport_cancel");
                    result.complete(false);
                    return;
                }
            }
            
            if (countdown.get() > 0) {
                String sec = String.valueOf(countdown.get());
                
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("time", sec);
                plugin.getMessageService().sendMessage(player, "teleport_countdown", placeholders);
                
                countdown.getAndDecrement();
            } else {
                cleanup(uuid, holder[0]);
                
                player.teleportAsync(loc).thenAccept(success -> result.complete(success));
            }
        }, 1L, 20L);
        
        return result;
    }
    
    private void cleanup(UUID uuid, Scheduler.Task task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        tpPlayers.remove(uuid);
    }
}