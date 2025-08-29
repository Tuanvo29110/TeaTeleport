package dev.nighter.teaTeleport.utils;

import dev.nighter.teaTeleport.TeaTeleport;
import dev.nighter.teaTeleport.Scheduler;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class TeleportUtil {
    private final Main plugin;
    private final Set<UUID> tpPlayers = ConcurrentHashMap.newKeySet();

    public TeleportUtil(TeaTeleport plugin) {
        this.plugin = plugin;
    }
    
    public void teleportAsync(Player player, Location loc) {
        UUID uuid = player.getUniqueId();
        
        if (tpPlayers.containsKey(uuid)) {
            return;
        }
        
        AtomicInteger countdown = new AtomicInteger(plugin.getConfig().getInt("teleport-delay"));
        double maxDistance = plugin.getConfig().getDouble("movement-cancel.max-distance");
        boolean cancelOnMove = plugin.getConfig().getBoolean("movement-cancel.enabled");
        
        if (countdown.get() <= 0) {
            return;
        }
        
        Location originLocation = cancelOnMove ? player.getLocation() : null;
        
        tpPlayers.put(uuid);
        
        Scheduler.Task[] holder = new Scheduler.Task[1];
        
        Scheduler.Task task = Scheduler.runTaskTimerAsync(() -> {
            if (player == null || !player.isOnline) {
                cleanup(uuid, holder[0]);
                return;
            }
            
            if (cancelOnMove) {
                if (!originLocation.getWorld().equals(player.getWorld()) || originLocation.distance(player.getLocation()) > maxDistance) {
                    cleanup(uuid, holder[0]);
                    
                    plugin.getMessageService().sendMessage(player, "teleport_cancel");
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
                
                player.teleportAsync(loc);
            }
        }, 1L, 20L);
    }
    
    private void cleanup(UUID uuid, Scheduler.Task task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        tpPlayers.remove(uuid);
    }
}