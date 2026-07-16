package com.botnpc.plugin;

import com.botnpc.plugin.commands.BotCommand;
import com.botnpc.plugin.npc.NPCManager;
import com.botnpc.plugin.skin.SkinFetcher;
import com.botnpc.plugin.storage.BotStorage;
import org.bukkit.plugin.java.JavaPlugin;

public class BotNPCPlugin extends JavaPlugin {

    private NPCManager npcManager;
    private BotStorage storage;

    @Override
    public void onEnable() {
        this.npcManager = new NPCManager();
        this.storage = new BotStorage(this);
        SkinFetcher skinFetcher = new SkinFetcher(getLogger());

        // Recharge les bots sauvegardés (position + skin). Les animations
        // (marche/assis/duel) ne sont pas relancées automatiquement.
        storage.load(npcManager);

        getCommand("bot").setExecutor(new BotCommand(this, npcManager, skinFetcher));

        // Montre/cache automatiquement les bots selon la distance des joueurs
        npcManager.startVisibilityLoop(this);

        getLogger().info("BotNPCPlugin activé — " + npcManager.all().size() + " bot(s) rechargé(s).");
    }

    @Override
    public void onDisable() {
        if (npcManager != null) {
            storage.save(npcManager);
            npcManager.stopAllTasks();
            npcManager.stopVisibilityLoop();
            npcManager.removeAll();
        }
    }

    public NPCManager getNpcManager() {
        return npcManager;
    }
}
