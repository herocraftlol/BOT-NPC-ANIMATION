package com.botnpc.plugin.storage;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.botnpc.plugin.npc.FakeNPC;
import com.botnpc.plugin.npc.NPCManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Sauvegarde/recharge la liste des bots (position, skin) pour qu'ils
 * survivent à un redémarrage du serveur. Les chemins de marche et l'état
 * d'animation en cours (marche/assis/duel) ne sont volontairement PAS
 * restaurés automatiquement : au redémarrage, les bots réapparaissent en
 * position "debout, immobile", et il suffit de relancer l'animation voulue
 * avec /bot path|sit|duel.
 */
public class BotStorage {

    private final Plugin plugin;
    private final File file;

    public BotStorage(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bots.yml");
    }

    public void save(NPCManager manager) {
        YamlConfiguration config = new YamlConfiguration();

        for (FakeNPC npc : manager.all()) {
            String path = "bots." + npc.getId();
            Location loc = npc.getBukkitLocation();
            GameProfile profile = npc.getProfile();
            Property textureProp = profile.getProperties().get("textures").stream().findFirst().orElse(null);

            config.set(path + ".uuid", profile.getId().toString());
            config.set(path + ".display-name", profile.getName());
            config.set(path + ".world", loc.getWorld().getName());
            config.set(path + ".x", loc.getX());
            config.set(path + ".y", loc.getY());
            config.set(path + ".z", loc.getZ());
            config.set(path + ".yaw", loc.getYaw());
            config.set(path + ".pitch", loc.getPitch());

            if (textureProp != null) {
                config.set(path + ".skin-value", textureProp.value());
                config.set(path + ".skin-signature", textureProp.signature());
            }
        }

        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible de sauvegarder bots.yml : " + e.getMessage());
        }
    }

    public void load(NPCManager manager) {
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (!config.contains("bots")) return;

        for (String id : config.getConfigurationSection("bots").getKeys(false)) {
            try {
                String path = "bots." + id;
                String worldName = config.getString(path + ".world");
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("Bot '" + id + "' ignoré : monde '" + worldName + "' introuvable.");
                    continue;
                }

                Location loc = new Location(
                        world,
                        config.getDouble(path + ".x"),
                        config.getDouble(path + ".y"),
                        config.getDouble(path + ".z"),
                        (float) config.getDouble(path + ".yaw"),
                        (float) config.getDouble(path + ".pitch")
                );

                UUID uuid = UUID.fromString(config.getString(path + ".uuid"));
                String displayName = config.getString(path + ".display-name", id);

                GameProfile profile = new GameProfile(uuid, displayName);
                String skinValue = config.getString(path + ".skin-value");
                String skinSignature = config.getString(path + ".skin-signature");
                if (skinValue != null) {
                    if (skinSignature != null) {
                        profile.getProperties().put("textures", new Property("textures", skinValue, skinSignature));
                    } else {
                        profile.getProperties().put("textures", new Property("textures", skinValue));
                    }
                }

                FakeNPC npc = new FakeNPC(id, profile, loc);
                manager.register(npc);
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur au chargement du bot '" + id + "' : " + e.getMessage());
            }
        }
    }
}
