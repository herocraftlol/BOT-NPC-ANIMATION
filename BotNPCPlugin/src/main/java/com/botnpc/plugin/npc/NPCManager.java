package com.botnpc.plugin.npc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NPCManager {

    private final Map<String, FakeNPC> bots = new LinkedHashMap<>();
    private final Map<String, BukkitTask> runningTasks = new ConcurrentHashMap<>();
    private BukkitTask visibilityTask;

    public void register(FakeNPC npc) {
        bots.put(npc.getId().toLowerCase(), npc);
    }

    public FakeNPC get(String id) {
        return bots.get(id.toLowerCase());
    }

    public boolean exists(String id) {
        return bots.containsKey(id.toLowerCase());
    }

    public Collection<FakeNPC> all() {
        return bots.values();
    }

    public void remove(String id) {
        FakeNPC npc = bots.remove(id.toLowerCase());
        if (npc != null) {
            stopTask(npc.getId());
            npc.despawn();
        }
    }

    public void removeAll() {
        for (String id : new java.util.HashSet<>(bots.keySet())) {
            remove(id);
        }
    }

    // ------------------------------------------------------------------
    // Gestion des tâches d'animation (une seule active à la fois par bot)
    // ------------------------------------------------------------------

    public void setTask(String botId, BukkitTask task) {
        stopTask(botId);
        runningTasks.put(botId.toLowerCase(), task);
    }

    public void stopTask(String botId) {
        BukkitTask task = runningTasks.remove(botId.toLowerCase());
        if (task != null) {
            task.cancel();
        }
    }

    public void stopAllTasks() {
        runningTasks.values().forEach(BukkitTask::cancel);
        runningTasks.clear();
    }

    // ------------------------------------------------------------------
    // Visibilité automatique (montre/cache les bots selon la distance des joueurs)
    // ------------------------------------------------------------------

    public void startVisibilityLoop(org.bukkit.plugin.Plugin plugin) {
        visibilityTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (FakeNPC npc : bots.values()) {
                Location loc = npc.getBukkitLocation();
                if (loc.getWorld() == null) continue;
                npc.refreshViewersInRadius(loc, 48.0);
            }
        }, 20L, 20L);
    }

    public void stopVisibilityLoop() {
        if (visibilityTask != null) visibilityTask.cancel();
    }
}
