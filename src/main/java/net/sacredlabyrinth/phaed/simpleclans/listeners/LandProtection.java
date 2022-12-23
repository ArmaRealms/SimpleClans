package net.sacredlabyrinth.phaed.simpleclans.listeners;

import net.sacredlabyrinth.phaed.simpleclans.ChatBlock;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.hooks.protection.Land;
import net.sacredlabyrinth.phaed.simpleclans.hooks.protection.ProtectionProvider;
import net.sacredlabyrinth.phaed.simpleclans.managers.ClanManager;
import net.sacredlabyrinth.phaed.simpleclans.managers.ProtectionManager;
import net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.BiConsumer;

import static net.sacredlabyrinth.phaed.simpleclans.managers.ProtectionManager.Action.*;
import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.*;

public class LandProtection implements Listener {

    private final SimpleClans plugin;
    private final ProtectionManager protectionManager;
    private final ClanManager clanManager;
    private final SettingsManager settingsManager;
    private final EventPriority priority;

    public LandProtection(@NotNull SimpleClans plugin) {
        this.plugin = plugin;
        protectionManager = plugin.getProtectionManager();
        clanManager = plugin.getClanManager();
        settingsManager = plugin.getSettingsManager();
        priority = EventPriority.valueOf(settingsManager.getString(WAR_LISTENERS_PRIORITY));
    }

    public void registerListeners() {
        registerListener(BlockBreakEvent.class, (event, cancel) -> {
            Block block = event.getBlock();
            if (settingsManager.getIgnoredList(BREAK).contains(block.getType().name())) {
                return;
            }
            if (protectionManager.can(BREAK, block.getLocation(), event.getPlayer())) {
                event.setCancelled(cancel);
            }
        });
        registerListener(BlockPlaceEvent.class, (event, cancel) -> {
            Block block = event.getBlock();
            if (settingsManager.getIgnoredList(PLACE).contains(block.getType().name())) {
                return;
            }
            if (protectionManager.can(PLACE, block.getLocation(), event.getPlayer())) {
                event.setCancelled(cancel);
            }
        });
        registerListener(PlayerBucketEmptyEvent.class, (event, cancel) -> {
            Block block = event.getBlockClicked();
            if (settingsManager.getIgnoredList(PLACE).contains(block.getType().name())) {
                return;
            }
            if (protectionManager.can(PLACE, block.getLocation(), event.getPlayer())) {
                event.setCancelled(cancel);
            }
        });
        registerListener(EntityDamageByEntityEvent.class, (event, cancel) -> {
            Player attacker = ((Player) event.getDamager());
            Player victim = event.getEntity() instanceof Player ? ((Player) event.getEntity()) : null;
            if (protectionManager.can(DAMAGE, event.getEntity().getLocation(), attacker, victim)) {
                event.setCancelled(cancel);
            }
        });
        registerListener(PlayerInteractEntityEvent.class, (event, cancel) -> {
            if (protectionManager.can(INTERACT_ENTITY, event.getRightClicked().getLocation(), event.getPlayer())) {
                event.setCancelled(cancel);
            }
        });
        registerListener(InventoryOpenEvent.class, (event, cancel) -> {
            InventoryHolder holder = event.getInventory().getHolder();
            Location location = getLocation(holder);
            if (holder instanceof Entity && holder == event.getPlayer()) return;
            if (location == null) return;

            if (protectionManager.can(CONTAINER, location, ((Player) event.getPlayer()))) {
                event.setCancelled(cancel);
            }
        });
        registerListener(PlayerInteractEvent.class, (event, cancel) -> {
            if (event.getClickedBlock() == null) {
                return;
            }
            ProtectionManager.Action action = getInteractEventAction(event);
            if (protectionManager.can(action, event.getClickedBlock().getLocation(), event.getPlayer())) {
                event.setUseInteractedBlock(cancel ? Result.DENY : Result.ALLOW);
                event.setUseItemInHand(cancel ? Result.DENY : Result.ALLOW);
                event.setCancelled(cancel);
            }
        });
        registerListener(PlayerBedEnterEvent.class, (event, cancel) -> {
            if (protectionManager.can(INTERACT, event.getBed().getLocation(), event.getPlayer())) {
                event.setUseBed(cancel ? Result.DENY : Result.ALLOW);
                event.setCancelled(cancel);
            }
        });
    }

    private <T extends Event> void registerListener(Class<T> clazz, BiConsumer<T, Boolean> listener) {
        Bukkit.getPluginManager().registerEvent(clazz, this, EventPriority.LOWEST, (l, event) -> {
            if (clazz.isInstance(event)) {
                listener.accept(clazz.cast(event), true);
            }
        }, plugin, false);
        Bukkit.getPluginManager().registerEvent(clazz, this, priority, (l, event) -> {
            if (clazz.isInstance(event)) {
                listener.accept(clazz.cast(event), false);
            }
        }, plugin, false);
    }

    public void registerCreateLandEvent(ProtectionProvider provider, @Nullable Class<? extends Event> createLandEvent) {
        if (createLandEvent == null) return;
        Bukkit.getPluginManager().registerEvent(createLandEvent, this, EventPriority.NORMAL, (listener, event) -> {
            if (!createLandEvent.isInstance(event)) {
                return;
            }
            Player player = provider.getPlayer(event);
            if (player == null) return;
            Clan clan = clanManager.getClanByPlayerUniqueId(player.getUniqueId());
            if ((clan == null || !clan.isLeader(player)) && settingsManager.is(LAND_CREATION_ONLY_LEADERS)) {
                cancelWithMessage(player, event, "only.leaders.can.create.lands");
                return;
            }
            if (settingsManager.is(LAND_CREATION_ONLY_ONE_PER_CLAN)) {
                if (clan == null) {
                    cancelWithMessage(player, event, "only.clan.members.can.create.lands");
                    return;
                }
                for (ClanPlayer member : clan.getMembers()) {
                    Set<Land> lands = protectionManager.getLandsOf(Bukkit.getOfflinePlayer(member.getUniqueId()), player.getWorld());
                    if (lands.size() > 0) {
                        cancelWithMessage(player, event, "only.one.land.per.clan");
                        return;
                    }
                }
            }
        }, plugin, true);
    }

    private void cancelWithMessage(Player player, Event event, String messageKey) {
        if (!(event instanceof Cancellable)) {
            return;
        }
        ChatBlock.sendMessageKey(player, messageKey);
        ((Cancellable) event).setCancelled(true);
    }

    private @Nullable Location getLocation(@Nullable InventoryHolder holder) {
        if (holder instanceof BlockState) {
            return ((BlockState) holder).getLocation();
        }
        if (holder instanceof DoubleChest) {
            return ((DoubleChest) holder).getLocation();
        }
        return null;
    }

    @NotNull
    private ProtectionManager.Action getInteractEventAction(PlayerInteractEvent event) {
        ProtectionManager.Action action;
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock != null && clickedBlock.getState() instanceof InventoryHolder) {
            action = CONTAINER;
        } else {
            action = INTERACT;
        }
        return action;
    }
}
