package io.github.soul4723.extrapermissions.listener;

import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

public class NBTPermissionListener implements Listener {
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        // Check NBT query permissions for blocks
        if (event.getClickedBlock() != null) {
            if (!PermissionManager.hasPermission(player, "minecraft.nbt.query.block")) {
                // Player cannot query NBT data of blocks
                event.setCancelled(true);
                player.sendMessage("§cYou don't have permission to query block NBT data!");
                return;
            }
        }
        
        // Check NBT query permissions for items in hand
        ItemStack item = event.getItem();
        if (item != null) {
            if (!PermissionManager.hasPermission(player, "minecraft.nbt.query.item")) {
                // Player cannot query NBT data of items
                event.setCancelled(true);
                player.sendMessage("§cYou don't have permission to query item NBT data!");
                return;
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        // Check if player can modify NBT data when breaking blocks
        if (!PermissionManager.hasPermission(player, "minecraft.nbt.modify.block")) {
            // Prevent breaking blocks that might have NBT data
            event.setCancelled(true);
            player.sendMessage("§cYou don't have permission to modify block NBT data!");
            return;
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        // Check NBT modification permissions for placed blocks
        if (!PermissionManager.hasPermission(player, "minecraft.nbt.modify.block")) {
            // Prevent placing blocks with custom NBT if no permission
            ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
            if (item != null && item.hasItemMeta()) {
                // Block has custom NBT data
                event.setCancelled(true);
                player.sendMessage("§cYou don't have permission to place blocks with NBT data!");
                return;
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getDamager();
        
        // Check NBT modification permissions for entities
        if (!PermissionManager.hasPermission(player, "minecraft.nbt.modify.entity")) {
            // Player cannot modify entity NBT through damage
            event.setCancelled(true);
            player.sendMessage("§cYou don't have permission to modify entity NBT data!");
            return;
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent event) {
        if (!(event.getTarget() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getTarget();
        
        // Check NBT query permissions for entities targeting player
        if (!PermissionManager.hasPermission(player, "minecraft.nbt.query.entity")) {
            // Player cannot query entity NBT data
            return;
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        // Check NBT loading permissions during teleportation
        if (!PermissionManager.hasPermission(player, "minecraft.nbt.load.block")) {
            // Prevent teleportation that would load chunk NBT without permission
            event.setCancelled(true);
            player.sendMessage("§cYou don't have permission to load block NBT data during teleportation!");
            return;
        }
        
        if (!PermissionManager.hasPermission(player, "minecraft.nbt.load.entity")) {
            // Prevent teleportation that would load entity NBT without permission
            event.setCancelled(true);
            player.sendMessage("§cYou don't have permission to load entity NBT data during teleportation!");
            return;
        }
    }
}