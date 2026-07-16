package com.botnpc.plugin.tasks;

import com.botnpc.plugin.npc.FakeNPC;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fait marcher un bot le long d'un chemin défini en boucle (waypoints posés
 * via /bot path <id> add). À chaque segment, le bot avance pas à pas vers le
 * point suivant, et régulièrement tourne la tête pour "regarder autour de
 * lui" (gauche/droite/haut/bas) sans dévier de sa trajectoire.
 */
public class WalkPathTask extends BukkitRunnable {

    private static final double STEP_DISTANCE = 0.2; // distance parcourue par tick d'animation
    private static final int TICKS_PER_STEP = 4;      // vitesse de marche (plus petit = plus rapide)
    private static final int LOOK_AROUND_EVERY_STEPS = 15;

    private final FakeNPC npc;
    private final List<Location> path;

    private int targetIndex = 0;
    private int stepCounter = 0;
    private boolean lookingAround = false;
    private int lookAroundTicksLeft = 0;

    public WalkPathTask(FakeNPC npc, List<Location> path) {
        this.npc = npc;
        this.path = path;
    }

    @Override
    public void run() {
        if (path.isEmpty()) return;

        if (lookingAround) {
            performLookAround();
            return;
        }

        stepCounter++;
        if (stepCounter % TICKS_PER_STEP != 0) return;

        Location current = npc.getBukkitLocation();
        Location target = path.get(targetIndex);

        double dx = target.getX() - current.getX();
        double dz = target.getZ() - current.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        if (distance < 0.3) {
            // Point atteint : on passe au suivant (boucle sur le chemin)
            targetIndex = (targetIndex + 1) % path.size();

            if (ThreadLocalRandom.current().nextInt(100) < 40) {
                startLookAround();
                return;
            }
            return;
        }

        double ratio = STEP_DISTANCE / distance;
        double newX = current.getX() + dx * ratio;
        double newZ = current.getZ() + dz * ratio;
        double newY = target.getWorld().getHighestBlockYAt((int) Math.round(newX), (int) Math.round(newZ));

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

        npc.moveTo(newX, newY, newZ, yaw, 0f);
        npc.swingArm(); // léger balancement de bras pendant la marche, purement visuel

        if (stepCounter % (TICKS_PER_STEP * LOOK_AROUND_EVERY_STEPS) == 0) {
            startLookAround();
        }
    }

    private void startLookAround() {
        lookingAround = true;
        lookAroundTicksLeft = 20; // ~1 seconde de "regard autour"
    }

    private void performLookAround() {
        lookAroundTicksLeft--;
        Location current = npc.getBukkitLocation();

        float randomYawOffset = ThreadLocalRandom.current().nextInt(-70, 70);
        float randomPitch = ThreadLocalRandom.current().nextInt(-20, 15);

        npc.lookAt(current.getYaw() + randomYawOffset * 0.3f, randomPitch);

        if (lookAroundTicksLeft <= 0) {
            lookingAround = false;
            npc.lookAt(current.getYaw(), 0f); // on remet la tête droite avant de repartir
        }
    }
}
