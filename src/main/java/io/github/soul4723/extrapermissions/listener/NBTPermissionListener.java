package io.github.soul4723.extrapermissions.listener;

import io.github.soul4723.extrapermissions.util.PermissionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

public class NBTPermissionListener implements Listener {
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getClickedBlock() != null && !PermissionManager.hasPermission(player, "minecraft.nbt.query.block")) {
            event.setCancelled(true);
            player.sendMessage("§cYou don't have permission to query block NBT data!");
            return;
        }
        
        ItemStack item = event.getItem();
        if (item != null && !PermissionManager.hasPermission(player, "minecraft.nbt.query.item")) {
            event.setCancelled(true);
            player.sendMessage("§cYou don't have permission to query item NBT data!");
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!PermissionManager.hasPermission(player, "minecraft.nbt.modify.block")) {
            event.setCancelled(true);
            player.sendMessage("§cYou don't have permission to modify block NBT data!");
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!PermissionManager.hasPermission(player, "minecraft.nbt.modify.block")) {
            ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
            if (item.hasItemMeta()) {
                event.setCancelled(true);
                player.sendMessage("§cYou don't have permission to place blocks with NBT data!");
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        
        Player player = (Player) event.getDamager();
        if (!PermissionManager.hasPermission(player, "minecraft.nbt.modify.entity")) {
            event.setCancelled(true);
            player.sendMessage("§cYou don't have permission to modify entity NBT data!");
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!PermissionManager.hasPermission(player, "minecraft.nbt.load.block")) {
            event.setCancelled(true);
            player.sendMessage("§cYou don't have permission to load block NBT data during teleportation!");
            return;
        }
        
        if (!PermissionManager.hasPermission(player, "minecraft.nbt.load.entity")) {
            event.setCancelled(true);
            player.sendMessage("§cYou don't have permission to load entity NBT data during teleportation!");
        }
    }
}