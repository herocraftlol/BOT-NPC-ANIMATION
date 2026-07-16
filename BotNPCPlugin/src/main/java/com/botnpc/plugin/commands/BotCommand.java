package com.botnpc.plugin.commands;

import com.mojang.authlib.GameProfile;
import com.botnpc.plugin.npc.FakeNPC;
import com.botnpc.plugin.npc.NPCManager;
import com.botnpc.plugin.skin.SkinData;
import com.botnpc.plugin.skin.SkinFetcher;
import com.botnpc.plugin.tasks.DuelTask;
import com.botnpc.plugin.tasks.SitTask;
import com.botnpc.plugin.tasks.WalkPathTask;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public class BotCommand implements CommandExecutor {

    private final Plugin plugin;
    private final NPCManager manager;
    private final SkinFetcher skinFetcher;

    public BotCommand(Plugin plugin, NPCManager manager, SkinFetcher skinFetcher) {
        this.plugin = plugin;
        this.manager = manager;
        this.skinFetcher = skinFetcher;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande doit être exécutée en jeu.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> handleCreate(player, args);
            case "remove", "delete" -> handleRemove(player, args);
            case "list" -> handleList(player);
            case "skin" -> handleSkin(player, args);
            case "tp" -> handleTp(player, args);
            case "path" -> handlePath(player, args);
            case "sit" -> handleSit(player, args);
            case "duel" -> handleDuel(player, args);
            case "stop" -> handleStop(player, args);
            default -> sendHelp(player);
        }
        return true;
    }

    // ------------------------------------------------------------------

    private void sendHelp(Player p) {
        p.sendMessage(ChatColor.GOLD + "=== BotNPC — Commandes ===");
        p.sendMessage(ChatColor.YELLOW + "/bot create <id> <pseudo|url_image>" + ChatColor.GRAY + " - crée un bot à ta position");
        p.sendMessage(ChatColor.YELLOW + "/bot skin <id> <pseudo|url_image>" + ChatColor.GRAY + " - change le skin d'un bot");
        p.sendMessage(ChatColor.YELLOW + "/bot remove <id>" + ChatColor.GRAY + " - supprime un bot");
        p.sendMessage(ChatColor.YELLOW + "/bot list" + ChatColor.GRAY + " - liste les bots");
        p.sendMessage(ChatColor.YELLOW + "/bot tp <id>" + ChatColor.GRAY + " - téléporte un bot à ta position");
        p.sendMessage(ChatColor.YELLOW + "/bot path <id> add" + ChatColor.GRAY + " - ajoute ta position au chemin du bot");
        p.sendMessage(ChatColor.YELLOW + "/bot path <id> clear" + ChatColor.GRAY + " - vide le chemin du bot");
        p.sendMessage(ChatColor.YELLOW + "/bot path <id> start" + ChatColor.GRAY + " - le bot marche sur son chemin en regardant autour");
        p.sendMessage(ChatColor.YELLOW + "/bot sit <id>" + ChatColor.GRAY + " - assoit le bot sur le bloc sous toi, regard au ciel");
        p.sendMessage(ChatColor.YELLOW + "/bot duel <id1> <id2>" + ChatColor.GRAY + " - lance un duel à l'épée entre deux bots");
        p.sendMessage(ChatColor.YELLOW + "/bot stop <id>" + ChatColor.GRAY + " - arrête l'animation en cours d'un bot");
    }

    private void handleCreate(Player p, String[] args) {
        if (args.length < 3) {
            p.sendMessage(ChatColor.RED + "Usage: /bot create <id> <pseudo|url_image>");
            return;
        }
        String id = args[1];
        String skinInput = args[2];

        if (manager.exists(id)) {
            p.sendMessage(ChatColor.RED + "Un bot avec l'id '" + id + "' existe déjà.");
            return;
        }

        p.sendMessage(ChatColor.GRAY + "Récupération du skin en cours...");

        skinFetcher.resolve(skinInput).whenComplete((skinData, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) {
                p.sendMessage(ChatColor.RED + "Échec de récupération du skin : " + rootMessage(error));
                return;
            }
            createBotWithSkin(p, id, skinInput, skinData);
        }));
    }

    private void createBotWithSkin(Player p, String id, String displayNameSeed, SkinData skinData) {
        Location loc = p.getLocation();
        UUID uuid = UUID.randomUUID();
        String displayName = sanitizeDisplayName(id);

        GameProfile profile = new GameProfile(uuid, displayName);
        if (skinData.hasSignature()) {
            profile.getProperties().put("textures",
                    new com.mojang.authlib.properties.Property("textures", skinData.getValue(), skinData.getSignature()));
        } else {
            profile.getProperties().put("textures",
                    new com.mojang.authlib.properties.Property("textures", skinData.getValue()));
        }

        FakeNPC npc = new FakeNPC(id, profile, loc);
        manager.register(npc);
        npc.refreshViewersInRadius(loc, 48.0);

        p.sendMessage(ChatColor.GREEN + "Bot '" + id + "' créé avec succès.");
    }

    private String sanitizeDisplayName(String id) {
        // Les faux profils doivent avoir un nom valide (16 caractères max, sans espace)
        String cleaned = id.replaceAll("[^a-zA-Z0-9_]", "");
        if (cleaned.isEmpty()) cleaned = "Bot";
        return cleaned.length() > 16 ? cleaned.substring(0, 16) : cleaned;
    }

    private void handleRemove(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage(ChatColor.RED + "Usage: /bot remove <id>");
            return;
        }
        if (!manager.exists(args[1])) {
            p.sendMessage(ChatColor.RED + "Bot introuvable.");
            return;
        }
        manager.remove(args[1]);
        p.sendMessage(ChatColor.GREEN + "Bot '" + args[1] + "' supprimé.");
    }

    private void handleList(Player p) {
        if (manager.all().isEmpty()) {
            p.sendMessage(ChatColor.GRAY + "Aucun bot créé.");
            return;
        }
        p.sendMessage(ChatColor.GOLD + "Bots (" + manager.all().size() + ") :");
        for (FakeNPC npc : manager.all()) {
            p.sendMessage(ChatColor.YELLOW + " - " + npc.getId() + ChatColor.GRAY + " [" + npc.getState() + "]");
        }
    }

    private void handleSkin(Player p, String[] args) {
        if (args.length < 3) {
            p.sendMessage(ChatColor.RED + "Usage: /bot skin <id> <pseudo|url_image>");
            return;
        }
        FakeNPC npc = manager.get(args[1]);
        if (npc == null) {
            p.sendMessage(ChatColor.RED + "Bot introuvable.");
            return;
        }

        p.sendMessage(ChatColor.GRAY + "Récupération du skin en cours...");
        skinFetcher.resolve(args[2]).whenComplete((skinData, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) {
                p.sendMessage(ChatColor.RED + "Échec de récupération du skin : " + rootMessage(error));
                return;
            }
            npc.applySkin(skinData.getValue(), skinData.getSignature());
            // On force le réaffichage pour que le nouveau skin soit pris en compte par les clients
            Location loc = npc.getBukkitLocation();
            npc.despawn();
            npc.refreshViewersInRadius(loc, 48.0);
            p.sendMessage(ChatColor.GREEN + "Skin du bot '" + npc.getId() + "' mis à jour.");
        }));
    }

    private void handleTp(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage(ChatColor.RED + "Usage: /bot tp <id>");
            return;
        }
        FakeNPC npc = manager.get(args[1]);
        if (npc == null) {
            p.sendMessage(ChatColor.RED + "Bot introuvable.");
            return;
        }
        manager.stopTask(npc.getId());
        npc.setState(FakeNPC.BotState.IDLE);
        Location loc = p.getLocation();
        npc.moveTo(loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), 0f);
        p.sendMessage(ChatColor.GREEN + "Bot '" + npc.getId() + "' téléporté.");
    }

    private void handlePath(Player p, String[] args) {
        if (args.length < 3) {
            p.sendMessage(ChatColor.RED + "Usage: /bot path <id> <add|clear|start>");
            return;
        }
        FakeNPC npc = manager.get(args[1]);
        if (npc == null) {
            p.sendMessage(ChatColor.RED + "Bot introuvable.");
            return;
        }

        switch (args[2].toLowerCase()) {
            case "add" -> {
                npc.getPath().add(p.getLocation());
                p.sendMessage(ChatColor.GREEN + "Point ajouté au chemin de '" + npc.getId()
                        + "' (" + npc.getPath().size() + " points).");
            }
            case "clear" -> {
                npc.getPath().clear();
                p.sendMessage(ChatColor.GREEN + "Chemin de '" + npc.getId() + "' vidé.");
            }
            case "start" -> {
                if (npc.getPath().size() < 2) {
                    p.sendMessage(ChatColor.RED + "Il faut au moins 2 points dans le chemin (utilise /bot path "
                            + npc.getId() + " add plusieurs fois).");
                    return;
                }
                npc.standUp();
                npc.setState(FakeNPC.BotState.WALKING);
                WalkPathTask task = new WalkPathTask(npc, npc.getPath());
                manager.setTask(npc.getId(), task.runTaskTimer(plugin, 0L, 1L));
                p.sendMessage(ChatColor.GREEN + "Bot '" + npc.getId() + "' démarre sa marche sur le chemin.");
            }
            default -> p.sendMessage(ChatColor.RED + "Usage: /bot path <id> <add|clear|start>");
        }
    }

    private void handleSit(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage(ChatColor.RED + "Usage: /bot sit <id>");
            return;
        }
        FakeNPC npc = manager.get(args[1]);
        if (npc == null) {
            p.sendMessage(ChatColor.RED + "Bot introuvable.");
            return;
        }

        manager.stopTask(npc.getId());
        npc.setState(FakeNPC.BotState.SITTING);

        Location blockTop = p.getLocation().getBlock().getLocation().add(0.5, 1.0, 0.5);
        blockTop.setYaw(p.getLocation().getYaw());

        SitTask task = new SitTask(npc, blockTop);
        manager.setTask(npc.getId(), task.runTaskTimer(plugin, 0L, 5L));
        p.sendMessage(ChatColor.GREEN + "Bot '" + npc.getId() + "' s'assoit et regarde le ciel.");
    }

    private void handleDuel(Player p, String[] args) {
        if (args.length < 3) {
            p.sendMessage(ChatColor.RED + "Usage: /bot duel <id1> <id2>");
            return;
        }
        FakeNPC npc1 = manager.get(args[1]);
        FakeNPC npc2 = manager.get(args[2]);
        if (npc1 == null || npc2 == null) {
            p.sendMessage(ChatColor.RED + "Un des deux bots est introuvable.");
            return;
        }
        if (npc1 == npc2) {
            p.sendMessage(ChatColor.RED + "Il faut deux bots différents.");
            return;
        }

        manager.stopTask(npc1.getId());
        manager.stopTask(npc2.getId());
        npc1.standUp();
        npc2.standUp();
        npc1.setState(FakeNPC.BotState.DUELING);
        npc2.setState(FakeNPC.BotState.DUELING);

        DuelTask task = new DuelTask(npc1, npc2);
        var handle = task.runTaskTimer(plugin, 0L, 1L);
        // On enregistre la même tâche sous les deux id : /bot stop marche sur l'un OU l'autre
        // (annuler deux fois la même BukkitTask ne pose aucun problème).
        manager.setTask(npc1.getId(), handle);
        manager.setTask(npc2.getId(), handle);

        p.sendMessage(ChatColor.GREEN + "Duel lancé entre '" + npc1.getId() + "' et '" + npc2.getId() + "'.");
    }

    private void handleStop(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage(ChatColor.RED + "Usage: /bot stop <id>");
            return;
        }
        FakeNPC npc = manager.get(args[1]);
        if (npc == null) {
            p.sendMessage(ChatColor.RED + "Bot introuvable.");
            return;
        }
        manager.stopTask(npc.getId());
        npc.standUp();
        npc.setState(FakeNPC.BotState.IDLE);
        p.sendMessage(ChatColor.GREEN + "Animation du bot '" + npc.getId() + "' arrêtée.");
    }

    private String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() != null ? cause.getMessage() : cause.toString();
    }
}
