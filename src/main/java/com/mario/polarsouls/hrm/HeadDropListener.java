package com.mario.polarsouls.hrm;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;

import com.mario.polarsouls.PolarSouls;
import com.mario.polarsouls.database.DatabaseManager;
import com.mario.polarsouls.model.PlayerData;

public class HeadDropListener implements Listener {

    private static final String PERM_BYPASS = "PolarSouls.bypass";

    private final PolarSouls plugin;
    private final DatabaseManager db;

    public HeadDropListener(PolarSouls plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (player.hasPermission(PERM_BYPASS)) return;

        World world = player.getWorld();
        Location deathLoc = player.getLocation();
        if (deathLoc == null) return;

        if (plugin.isHrmDeathLocationMsg()) {
            player.sendMessage(ChatColor.GRAY.toString() + ChatColor.ITALIC
                    + "You died at " + deathLoc.getBlockX() + ", "
                    + deathLoc.getBlockY() + ", " + deathLoc.getBlockZ()
                    + " in " + world.getName());
        }

        // only drop head if really dead (work pls)
        if (plugin.isHrmDropHeads()) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                PlayerData data = db.getPlayer(player.getUniqueId());
                if (data == null) {
                    plugin.debug("Skipping head drop for " + player.getName() + " (no data).");
                    return;
                }
                if (!data.isDead()) {
                    plugin.debug("Skipping head drop for " + player.getName() + " (not dead).");
                    return;
                }
                if (data.isInGracePeriod(plugin.getGracePeriodMillis())) {
                    plugin.debug("Skipping head drop for " + player.getName() + " (grace period).");
                    return;
                }
                // Drop head on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    ItemStack head = createPlayerHead(player);
                    world.dropItemNaturally(deathLoc, head);
                    plugin.debug("Dropped " + player.getName() + "'s head at "
                            + deathLoc.getBlockX() + ", " + deathLoc.getBlockY()
                            + ", " + deathLoc.getBlockZ());
                });
            }, 10L); // 0.5s delay because idfk it feels good
        }
    }

    public static ItemStack createPlayerHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName(ChatColor.YELLOW + player.getName() + "'s Head");
            meta.setLore(List.of(
                    ChatColor.DARK_RED.toString() + ChatColor.ITALIC + "A fallen player's head",
                    ChatColor.GRAY + "Place on a revival structure to revive"));
            head.setItemMeta(meta);
        }
        return head;
    }

    /**
     * Removes all player head items belonging to the specified player from:
     * dropped item entities, player inventories (including armor/offhand),
     * and item frames across all worlds. Must be called on the main server thread.
     */
    public static void removeDroppedHeads(UUID ownerUuid) {
        for (World world : Bukkit.getWorlds()) {
            // Remove dropped item entities
            for (Item itemEntity : world.getEntitiesByClass(Item.class)) {
                if (isOwnedHead(itemEntity.getItemStack(), ownerUuid)) {
                    itemEntity.remove();
                }
            }

            // Remove from item frames
            for (ItemFrame frame : world.getEntitiesByClass(ItemFrame.class)) {
                if (isOwnedHead(frame.getItem(), ownerUuid)) {
                    frame.setItem(null);
                }
            }
        }

        // Remove from all online player inventories (main, armor, offhand)
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerInventory inv = player.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                if (isOwnedHead(inv.getItem(i), ownerUuid)) {
                    inv.setItem(i, null);
                }
            }
        }
    }

    private static boolean isOwnedHead(ItemStack stack, UUID ownerUuid) {
        if (stack == null || stack.getType() != Material.PLAYER_HEAD) return false;
        if (!(stack.getItemMeta() instanceof SkullMeta skullMeta)) return false;
        OfflinePlayer skullOwner = skullMeta.getOwningPlayer();
        return skullOwner != null && skullOwner.getUniqueId().equals(ownerUuid);
    }
}
