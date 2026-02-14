package com.mario.polarsouls.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.mario.polarsouls.PolarSouls;
import com.mario.polarsouls.util.MessageUtil;

// pings the db on main server for spectators who've been revived externally and then restores em to survival

public class MainReviveCheckTask extends BukkitRunnable {

    private static final String PERM_BYPASS = "PolarSouls.bypass";

    private final PolarSouls plugin;

    public MainReviveCheckTask(PolarSouls plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // Batch query all dead player UUIDs from database (optimization: single query instead of N queries)
        Set<UUID> deadPlayerUUIDs = plugin.getDatabaseManager().getDeadPlayerUUIDs();

        List<UUID> spectators = collectSpectators();
        if (spectators.isEmpty()) return;

        plugin.debug("Main revive check: scanning " + spectators.size() + " spectator(s)...");

        List<UUID> revived = findRevivedPlayers(spectators, deadPlayerUUIDs);

        if (!revived.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> restoreAll(revived));
        }
    }

    private List<UUID> collectSpectators() {
        List<UUID> spectatorUuids = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR
                    && !player.hasPermission(PERM_BYPASS)) {
                spectatorUuids.add(player.getUniqueId());
            }
        }
        return spectatorUuids;
    }

    private List<UUID> findRevivedPlayers(List<UUID> spectators, Set<UUID> deadPlayerUUIDs) {
        List<UUID> revived = new ArrayList<>();
        for (UUID uuid : spectators) {
            // If spectator is NOT in the dead players set, they've been revived
            if (!deadPlayerUUIDs.contains(uuid)) {
                revived.add(uuid);
                plugin.debug("Spectator " + uuid + " is no longer dead in DB, restoring...");
            }
        }
        return revived;
    }

    private void restoreAll(List<UUID> uuids) {
        for (UUID uuid : uuids) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.setGameMode(GameMode.SURVIVAL);
                player.sendMessage(MessageUtil.get("revive-success"));
                plugin.getLogger().log(Level.INFO,
                        "Restored {0} from spectator to survival (revived in DB).",
                        player.getName());
            }
        }
    }
}
