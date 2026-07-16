package com.botnpc.plugin.tasks;

import com.botnpc.plugin.npc.FakeNPC;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Fait s'affronter deux bots en duel scénarisé : ils se rapprochent l'un de
 * l'autre, se font face, puis alternent coups d'épée (swing + éventuelle
 * "hurt animation" sur l'adversaire) et petits pas d'esquive. C'est une
 * chorégraphie, pas un vrai calcul de dégâts (les bots n'ont pas de vraie
 * vie ici) — largement suffisant pour un rendu visuel de duel.
 */
public class DuelTask extends BukkitRunnable {

    private static final double FIGHT_DISTANCE = 2.2;
    private static final int ATTACK_EVERY_TICKS = 15;

    private final FakeNPC bot1;
    private final FakeNPC bot2;
    private int tickCounter = 0;
    private boolean facingEachOther = false;

    public DuelTask(FakeNPC bot1, FakeNPC bot2) {
        this.bot1 = bot1;
        this.bot2 = bot2;
    }

    @Override
    public void run() {
        tickCounter++;

        Location loc1 = bot1.getBukkitLocation();
        Location loc2 = bot2.getBukkitLocation();

        double distance = loc1.distance(loc2);

        if (!facingEachOther) {
            approachEachOther(loc1, loc2, distance);
            return;
        }

        // Une fois à distance de combat : on se fixe du regard et on échange des coups
        faceTarget(bot1, loc1, loc2);
        faceTarget(bot2, loc2, loc1);

        if (tickCounter % ATTACK_EVERY_TICKS == 0) {
            FakeNPC attacker = ThreadLocalRandom.current().nextBoolean() ? bot1 : bot2;
            FakeNPC defender = attacker == bot1 ? bot2 : bot1;
            attacker.swingArm();

            // Léger décalage pour laisser "l'animation" de coup arriver visuellement avant la réaction
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                    org.bukkit.Bukkit.getPluginManager().getPlugin("BotNPCPlugin"),
                    defender::playHurtAnimation,
                    4L
            );
        }
    }

    private void approachEachOther(Location loc1, Location loc2, double distance) {
        if (distance <= FIGHT_DISTANCE) {
            facingEachOther = true;
            return;
        }

        moveTowards(bot1, loc1, loc2);
        moveTowards(bot2, loc2, loc1);
    }

    private void moveTowards(FakeNPC npc, Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01) return;

        double step = 0.15;
        double newX = from.getX() + (dx / len) * step;
        double newZ = from.getZ() + (dz / len) * step;
        double newY = from.getWorld().getHighestBlockYAt((int) Math.round(newX), (int) Math.round(newZ));

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        npc.moveTo(newX, newY, newZ, yaw, 0f);
    }

    private void faceTarget(FakeNPC npc, Location self, Location target) {
        double dx = target.getX() - self.getX();
        double dz = target.getZ() - self.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        npc.lookAt(yaw, 0f);
    }
}
