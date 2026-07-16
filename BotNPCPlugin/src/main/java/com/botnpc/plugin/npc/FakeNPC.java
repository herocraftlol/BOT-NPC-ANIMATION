package com.botnpc.plugin.npc;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Représente un faux joueur.
 *
 * Astuce technique : on instancie un vrai objet {@link ServerPlayer} côté NMS
 * (ce qui nous donne gratuitement un id d'entité unique, la gestion de la
 * position/rotation, l'inventaire, etc.) mais on NE L'AJOUTE JAMAIS au monde
 * (jamais de level.addFreshEntity, jamais dans la PlayerList). Il ne sert
 * qu'à générer les bons paquets, qu'on envoie nous-mêmes aux joueurs qui
 * doivent le voir. Le serveur ne le simule donc jamais (pas de physique, pas
 * d'IA, pas de tick) : c'est nous qui pilotons tout, paquet par paquet.
 */
public class FakeNPC {

    private final String id; // identifiant interne (nom donné par la commande)
    private final ServerPlayer ghost; // entité "fantôme" jamais ajoutée au monde
    private ArmorStand seat; // entité fantôme utilisée uniquement pour la position assise

    private final Set<UUID> viewers = new HashSet<>();
    private boolean addedToTabList = false;

    private final List<Location> path = new ArrayList<>();
    private BotState state = BotState.IDLE;

    public FakeNPC(String id, GameProfile profile, Location location) {
        this.id = id;

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) location.getWorld()).getHandle();

        this.ghost = new ServerPlayer(server, level, profile);
        this.ghost.setPos(location.getX(), location.getY(), location.getZ());
        this.ghost.setYRot(location.getYaw());
        this.ghost.setXRot(location.getPitch());
        this.ghost.yHeadRot = location.getYaw();

        // Épée en main par défaut, utile pour les duels, retirable si inutile
        this.ghost.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
    }

    public String getId() {
        return id;
    }

    public ServerPlayer getGhost() {
        return ghost;
    }

    public BotState getState() {
        return state;
    }

    public void setState(BotState state) {
        this.state = state;
    }

    public List<Location> getPath() {
        return path;
    }

    public GameProfile getProfile() {
        return ghost.getGameProfile();
    }

    public Location getBukkitLocation() {
        return new Location(
                ((ServerLevel) ghost.level()).getWorld(),
                ghost.getX(), ghost.getY(), ghost.getZ(),
                ghost.getYRot(), ghost.getXRot()
        );
    }

    // ------------------------------------------------------------------
    // Apparition / disparition
    // ------------------------------------------------------------------

    /** Rend le bot visible pour ce joueur. */
    public void showTo(Player viewer) {
        ServerPlayer handle = ((org.bukkit.craftbukkit.entity.CraftPlayer) viewer).getHandle();

        // 1) On doit d'abord annoncer le profil (avec le skin) via Player Info,
        //    sinon le client ne sait pas quelle texture appliquer.
        ClientboundPlayerInfoUpdatePacket infoPacket = new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(
                        ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY
                ),
                List.of(ghost)
        );
        handle.connection.send(infoPacket);

        // 2) On envoie l'entité elle-même.
        handle.connection.send(new ClientboundAddEntityPacket(ghost));
        handle.connection.send(new ClientboundRotateHeadPacket(ghost, (byte) (ghost.getYRot() * 256 / 360)));
        sendEquipment(handle);

        viewers.add(viewer.getUniqueId());

        // 3) Petite astuce nécessaire sur beaucoup de versions : le skin ne
        //    s'affiche correctement que si le profil reste un court instant
        //    dans la tab list. On le retire automatiquement peu après.
        addedToTabList = true;
        Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugin("BotNPCPlugin"),
                () -> removeFromTabListFor(viewer),
                40L // 2 secondes
        );

        if (seat != null) {
            sendSeatTo(handle);
        }
    }

    private void sendEquipment(ServerPlayer handle) {
        List<com.mojang.datafixers.util.Pair<EquipmentSlot, ItemStack>> equipment = new ArrayList<>();
        equipment.add(com.mojang.datafixers.util.Pair.of(EquipmentSlot.MAINHAND, ghost.getMainHandItem()));
        handle.connection.send(new ClientboundSetEquipmentPacket(ghost.getId(), equipment));
    }

    private void removeFromTabListFor(Player viewer) {
        if (!viewer.isOnline() || !viewers.contains(viewer.getUniqueId())) return;
        ServerPlayer handle = ((org.bukkit.craftbukkit.entity.CraftPlayer) viewer).getHandle();
        handle.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(ghost.getUUID())));
    }

    /** Cache le bot pour ce joueur. */
    public void hideFrom(Player viewer) {
        if (!viewers.contains(viewer.getUniqueId())) return;
        ServerPlayer handle = ((org.bukkit.craftbukkit.entity.CraftPlayer) viewer).getHandle();
        handle.connection.send(new ClientboundRemoveEntitiesPacket(ghost.getId()));
        if (seat != null) {
            handle.connection.send(new ClientboundRemoveEntitiesPacket(seat.getId()));
        }
        handle.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(ghost.getUUID())));
        viewers.remove(viewer.getUniqueId());
    }

    /** Cache le bot pour tout le monde, à appeler avant suppression définitive. */
    public void despawn() {
        for (UUID uuid : new HashSet<>(viewers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) hideFrom(p);
        }
        viewers.clear();
    }

    public void refreshViewersInRadius(Location center, double radius) {
        for (Player p : center.getWorld().getPlayers()) {
            boolean inRange = p.getLocation().distanceSquared(center) <= radius * radius;
            boolean currentlyShown = viewers.contains(p.getUniqueId());
            if (inRange && !currentlyShown) {
                showTo(p);
            } else if (!inRange && currentlyShown) {
                hideFrom(p);
            }
        }
    }

    // ------------------------------------------------------------------
    // Mouvement / rotation
    // ------------------------------------------------------------------

    /** Déplace le bot à une position précise (téléportation "propre", utilisée à chaque étape d'animation). */
    public void moveTo(double x, double y, double z, float yaw, float pitch) {
        ghost.setPos(x, y, z);
        ghost.setYRot(yaw);
        ghost.setXRot(pitch);
        broadcast(new ClientboundTeleportEntityPacket(ghost));
    }

    /** Fait tourner uniquement la tête (regarder autour sans bouger le corps). */
    public void lookAt(float yaw, float pitch) {
        ghost.yHeadRot = yaw;
        ghost.setXRot(pitch);
        broadcast(new ClientboundRotateHeadPacket(ghost, (byte) (yaw * 256 / 360)));
        // On renvoie aussi une téléportation pour synchroniser le pitch (inclinaison verticale)
        broadcast(new ClientboundTeleportEntityPacket(ghost));
    }

    /** Joue l'animation de "coup d'épée" / balancement de bras. */
    public void swingArm() {
        broadcast(new ClientboundAnimatePacket(ghost, 0)); // 0 = SWING_MAIN_HAND
    }

    /** Joue l'animation de "dégâts reçus". */
    public void playHurtAnimation() {
        broadcast(new ClientboundAnimatePacket(ghost, 2)); // 2 = HURT
    }

    public void setCrouching(boolean crouching) {
        ghost.setShiftKeyDown(crouching);
        syncMetadata();
    }

    private void syncMetadata() {
        broadcast(new ClientboundSetEntityDataPacket(ghost.getId(), ghost.getEntityData().packDirty()));
    }

    // ------------------------------------------------------------------
    // Position assise (sur un bloc)
    // ------------------------------------------------------------------

    /**
     * Fait "asseoir" le bot sur le bloc situé sous la position donnée, en
     * le montant sur une fausse ArmorStand invisible (technique standard
     * utilisée par la plupart des plugins de siège).
     */
    public void sitAt(Location blockTopLocation) {
        ServerLevel level = ((CraftWorld) blockTopLocation.getWorld()).getHandle();

        this.seat = new ArmorStand(level, blockTopLocation.getX(), blockTopLocation.getY() - 0.65, blockTopLocation.getZ());
        seat.setInvisible(true);
        seat.setMarker(false);
        seat.setNoGravity(true);
        seat.setSilent(true);
        seat.setSmall(true);

        ghost.setPos(blockTopLocation.getX(), blockTopLocation.getY(), blockTopLocation.getZ());
        ghost.startRiding(seat, true);

        for (UUID uuid : viewers) {
            Player viewer = Bukkit.getPlayer(uuid);
            if (viewer != null) {
                ServerPlayer handle = ((org.bukkit.craftbukkit.entity.CraftPlayer) viewer).getHandle();
                sendSeatTo(handle);
            }
        }
    }

    private void sendSeatTo(ServerPlayer handle) {
        if (seat == null) return;
        handle.connection.send(new ClientboundAddEntityPacket(seat));
        handle.connection.send(new ClientboundSetPassengersPacket(seat));
    }

    /** Fait se relever le bot (retire le siège invisible). */
    public void standUp() {
        if (seat == null) return;
        ghost.stopRiding();
        for (UUID uuid : viewers) {
            Player viewer = Bukkit.getPlayer(uuid);
            if (viewer != null) {
                ServerPlayer handle = ((org.bukkit.craftbukkit.entity.CraftPlayer) viewer).getHandle();
                handle.connection.send(new ClientboundRemoveEntitiesPacket(seat.getId()));
            }
        }
        seat = null;
    }

    // ------------------------------------------------------------------
    // Skin
    // ------------------------------------------------------------------

    /** Remplace la texture de skin. Nécessite un respawn (hide + show) pour être visible. */
    public void applySkin(String value, String signature) {
        GameProfile profile = ghost.getGameProfile();
        profile.getProperties().removeAll("textures");
        if (signature != null) {
            profile.getProperties().put("textures", new Property("textures", value, signature));
        } else {
            profile.getProperties().put("textures", new Property("textures", value));
        }
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    private void broadcast(Packet<?> packet) {
        for (UUID uuid : viewers) {
            Player viewer = Bukkit.getPlayer(uuid);
            if (viewer == null || !viewer.isOnline()) continue;
            ((org.bukkit.craftbukkit.entity.CraftPlayer) viewer).getHandle().connection.send(packet);
        }
    }

    public enum BotState {
        IDLE, WALKING, SITTING, DUELING
    }
}
