package com.botnpc.plugin.tasks;

import com.botnpc.plugin.npc.FakeNPC;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Assoit le bot sur le bloc situé sous la position donnée, tête levée vers
 * le ciel, avec de petits mouvements de tête occasionnels pour un rendu
 * plus naturel (pas une statue figée).
 */
public class SitTask extends BukkitRunnable {

    private final FakeNPC npc;
    private final Location blockTop;

    public SitTask(FakeNPC npc, Location blockTop) {
        this.npc = npc;
        this.blockTop = blockTop;
    }

    @Override
    public void run() {
        // Ce bloc ne s'exécute qu'une fois pour l'installation, puis se
        // contente de petites variations de regard. On distingue les deux
        // phases avec un simple compteur statique interne.
        if (!installed) {
            npc.sitAt(blockTop);
            installed = true;
            npc.lookAt(blockTop.getYaw(), -75f); // tête levée vers le ciel
            return;
        }

        if (ThreadLocalRandom.current().nextInt(100) < 15) {
            float yawWobble = ThreadLocalRandom.current().nextInt(-15, 15);
            float pitchWobble = ThreadLocalRandom.current().nextInt(-85, -60);
            npc.lookAt(blockTop.getYaw() + yawWobble, pitchWobble);
        }
    }

    private boolean installed = false;
}
