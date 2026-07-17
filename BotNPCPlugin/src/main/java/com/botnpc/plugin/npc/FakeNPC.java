package com.botnpc.plugin.npc;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.a;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.entity.decoration.EntityArmorStand;
import net.minecraft.world.entity.player.EnumChatVisibility;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_21_R1.CraftServer;
import org.bukkit.craftbukkit.v1_21_R1.CraftWorld;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Represents a fake player NPC for Spigot 1.21.1.
 * This version has been adapted for Spigot's obfuscated mappings.
 */
public class FakeNPC {

    private final String id;
    private final EntityPlayer ghost;
    private EntityArmorStand seat;

    private final Set<UUID> viewers = new HashSet<>();
    private boolean addedToTabList = false;

    private final List<Location> path = new ArrayList<>();
    private BotState state = BotState.IDLE;

    public FakeNPC(String id, GameProfile profile, Location location) {
        this.id = id;

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        WorldServer level = ((CraftWorld) location.getWorld()).getHandle();

        // Create ClientInformation for EntityPlayer constructor
        ClientInformation clientInfo = new ClientInformation(
            "en_US",           // language
            8,                 // viewDistance
            EnumChatVisibility.b,  // FULL = b in Spigot mappings
            false,             // chatColors
            127,               // modelCustomisation
            net.minecraft.world.entity.EnumMainHand.b,  // LEFT = b in Spigot mappings
            false,             // textFilteringEnabled
            false              // allowsListAlerts
        );

        this.ghost = new EntityPlayer(server, level, profile, clientInfo);
        
        // Set position - EntityPlayer.a(double, double, double) sets position
        this.ghost.a(location.getX(), location.getY(), location.getZ());
        
        // Set rotation - EntityLiving.b(float, float) sets rotation
        this.ghost.b(location.getYaw(), location.getPitch());
        
        // Head rotation is aR field in EntityLiving
        this.ghost.aR = location.getYaw();

        // Set sword in main hand - fY() returns PlayerInventory, a(int, ItemStack) sets item
        this.ghost.fY().a(0, new ItemStack(Items.b));  // IRON_SWORD = b in Items
    }

    public String getId() {
        return id;
    }

    public EntityPlayer getGhost() {
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
        return ghost.fX();  // fX() is getGameProfile in Spigot
    }

    public Location getBukkitLocation() {
        return new Location(
            ghost.cN().getWorld(),  // cN() is getWorld()
            ghost.L, ghost.M, ghost.N,  // L,M,N are x,y,z
            ghost.Y, ghost.Z  // Y,Z are yaw,pitch
        );
    }

    /** Makes the bot visible to this player. */
    public void showTo(Player viewer) {
        EntityPlayer handle = ((org.bukkit.craftbukkit.v1_21_R1.entity.CraftPlayer) viewer).getHandle();

        // 1) Announce profile via Player Info - use the static factory method
        ClientboundPlayerInfoUpdatePacket infoPacket = ClientboundPlayerInfoUpdatePacket.a(List.of(ghost));
        handle.c.a(infoPacket);

        // 2) Send entity spawn
        handle.c.a(new PacketPlayOutSpawnEntity(ghost, null));

        // 3) Send head rotation
        handle.c.a(new PacketPlayOutEntityHeadRotation(ghost, (byte)(ghost.Y * 256 / 360)));
        
        // 4) Send equipment
        sendEquipment(handle);

        viewers.add(viewer.getUniqueId());

        // 5) Skin trick - remove from tab list after delay
        addedToTabList = true;
        Bukkit.getScheduler().runTaskLater(
            Bukkit.getPluginManager().getPlugin("BotNPCPlugin"),
            () -> removeFromTabListFor(viewer),
            40L
        );

        if (seat != null) {
            sendSeatTo(handle);
        }
    }

    private void sendEquipment(EntityPlayer handle) {
        List<Pair<EnumItemSlot, ItemStack>> equipment = new ArrayList<>();
        equipment.add(Pair.of(EnumItemSlot.a, ghost.fY().f()));  // MAINHAND = a, f() is getItem for main hand
        handle.c.a(new PacketPlayOutEntityEquipment(ghost.an(), equipment));  // an() is getId()
    }

    private void removeFromTabListFor(Player viewer) {
        if (!viewer.isOnline() || !viewers.contains(viewer.getUniqueId())) return;
        EntityPlayer handle = ((org.bukkit.craftbukkit.v1_21_R1.entity.CraftPlayer) viewer).getHandle();
        handle.c.a(new ClientboundPlayerInfoRemovePacket(List.of(ghost.fX().getId())));
    }

    /** Hides the bot from this player. */
    public void hideFrom(Player viewer) {
        if (!viewers.contains(viewer.getUniqueId())) return;
        EntityPlayer handle = ((org.bukkit.craftbukkit.v1_21_R1.entity.CraftPlayer) viewer).getHandle();
        handle.c.a(new PacketPlayOutEntityDestroy(ghost.an()));
        if (seat != null) {
            handle.c.a(new PacketPlayOutEntityDestroy(seat.an()));
        }
        handle.c.a(new ClientboundPlayerInfoRemovePacket(List.of(ghost.fX().getId())));
        viewers.remove(viewer.getUniqueId());
    }

    /** Hides the bot from everyone. */
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

    /** Moves the bot to a precise position. */
    public void moveTo(double x, double y, double z, float yaw, float pitch) {
        ghost.a(x, y, z);  // setPos
        ghost.b(yaw, pitch);  // setRotation
        broadcast(new PacketPlayOutEntityTeleport(ghost));
    }

    /** Rotates only the head. */
    public void lookAt(float yaw, float pitch) {
        ghost.aR = yaw;  // head rotation
        ghost.b(yaw, pitch);
        broadcast(new PacketPlayOutEntityHeadRotation(ghost, (byte)(yaw * 256 / 360)));
        broadcast(new PacketPlayOutEntityTeleport(ghost));
    }

    /** Plays the sword swing animation. */
    public void swingArm() {
        broadcast(new PacketPlayOutAnimation(ghost, 0));
    }

    /** Plays the damage animation. */
    public void playHurtAnimation() {
        broadcast(new ClientboundHurtAnimationPacket(ghost));
    }

    public void setCrouching(boolean crouching) {
        syncMetadata();
    }

    private void syncMetadata() {
        broadcast(new PacketPlayOutEntityMetadata(ghost.an(), ghost.ar().c()));
    }

    /** Makes the bot "sit" on a block. */
    public void sitAt(Location blockTopLocation) {
        WorldServer level = ((CraftWorld) blockTopLocation.getWorld()).getHandle();

        this.seat = new EntityArmorStand(level, blockTopLocation.getX(), blockTopLocation.getY() - 0.65, blockTopLocation.getZ());
        seat.n(true);  // setInvisible
        seat.o(false); // setMarker

        ghost.a(blockTopLocation.getX(), blockTopLocation.getY(), blockTopLocation.getZ());
        ghost.a(seat, true);  // startRiding

        for (UUID uuid : viewers) {
            Player viewer = Bukkit.getPlayer(uuid);
            if (viewer != null) {
                EntityPlayer handle = ((org.bukkit.craftbukkit.v1_21_R1.entity.CraftPlayer) viewer).getHandle();
                sendSeatTo(handle);
            }
        }
    }

    private void sendSeatTo(EntityPlayer handle) {
        if (seat == null) return;
        handle.c.a(new PacketPlayOutSpawnEntity(seat, null));
        handle.c.a(new PacketPlayOutMount(seat));
    }

    /** Makes the bot stand up. */
    public void standUp() {
        if (seat == null) return;
        seat.h(ghost);  // remove passenger from vehicle
        for (UUID uuid : viewers) {
            Player viewer = Bukkit.getPlayer(uuid);
            if (viewer != null) {
                EntityPlayer handle = ((org.bukkit.craftbukkit.v1_21_R1.entity.CraftPlayer) viewer).getHandle();
                handle.c.a(new PacketPlayOutEntityDestroy(seat.an()));
            }
        }
        seat = null;
    }

    /** Replaces the skin texture. */
    public void applySkin(String value, String signature) {
        GameProfile profile = ghost.fX();
        profile.getProperties().removeAll("textures");
        if (signature != null) {
            profile.getProperties().put("textures", new Property("textures", value, signature));
        } else {
            profile.getProperties().put("textures", new Property("textures", value));
        }
    }

    private void broadcast(Packet<?> packet) {
        for (UUID uuid : viewers) {
            Player viewer = Bukkit.getPlayer(uuid);
            if (viewer == null || !viewer.isOnline()) continue;
            ((org.bukkit.craftbukkit.v1_21_R1.entity.CraftPlayer) viewer).getHandle().c.a(packet);
        }
    }

    public enum BotState {
        IDLE, WALKING, SITTING, DUELING
    }
}
