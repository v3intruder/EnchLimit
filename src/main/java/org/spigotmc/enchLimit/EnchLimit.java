package org.spigotmc.enchLimit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

public final class EnchLimit extends JavaPlugin implements Listener, CommandExecutor {

    // A map to store which enchantment a player is currently configuring in the GUI.
    private final Map<UUID, Enchantment> playerEnchantmentMap = new HashMap<>();

    // Inventory titles to identify our custom GUIs.
    private static final String MAIN_GUI_TITLE = ChatColor.DARK_BLUE + "Enchantment Limits";
    private static final String LEVEL_GUI_TITLE = ChatColor.DARK_BLUE + "Select Level for ";

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("enchlimit")).setExecutor(this);
        saveDefaultConfig();
        getLogger().info("Enchlimit has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Enchlimit has been disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player && !sender.hasPermission("enchlimit.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        // Handle the new '/enchlimit gui' command.
        if (args.length == 1 && args[0].equalsIgnoreCase("gui")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                player.openInventory(createEnchantmentSelectionGUI());
                return true;
            } else {
                sender.sendMessage(ChatColor.RED + "This command can only be used by a player.");
                return true;
            }
        }

        // Retain the old command line functionality for backward compatibility.
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /enchlimit <enchantment> <level>");
            sender.sendMessage(ChatColor.YELLOW + "Or: /enchlimit gui");
            return true;
        }

        Enchantment enchantment = getEnchantmentByName(args[0]);
        if (enchantment == null) {
            sender.sendMessage(ChatColor.RED + "Invalid enchantment name.");
            return true;
        }

        int limit;
        try {
            limit = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid level. Must be a number.");
            return true;
        }

        getConfig().set("enchantment-limits." + enchantment.getKey().getKey(), limit);
        saveConfig();

        sender.sendMessage(ChatColor.GREEN + "Enchantment limit for " + args[0] + " set to " + limit + ".");

        return true;
    }

    // --- New GUI Creation Methods ---

    private Inventory createEnchantmentSelectionGUI() {
        // Create an inventory with a size that fits all enchantments.
        // There are currently 36 enchantments in Minecraft, so a 54-slot inventory is a safe size.
        Inventory gui = Bukkit.createInventory(null, 54, MAIN_GUI_TITLE);

        // Get all registered enchantments.
        for (Enchantment enchantment : Enchantment.values()) {
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();

            // Add the enchantment to the book's meta.
            meta.addStoredEnchant(enchantment, 1, false);
            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + formatEnchantmentName(enchantment));

            // Add lore to show the current limit.
            List<String> lore = new ArrayList<>();
            int currentLimit = getConfig().getInt("enchantment-limits." + enchantment.getKey().getKey(), -1);
            if (currentLimit == -1) {
                lore.add(ChatColor.GRAY + "Current Limit: " + ChatColor.RED + "None");
            } else {
                lore.add(ChatColor.GRAY + "Current Limit: " + ChatColor.GREEN + currentLimit);
            }
            meta.setLore(lore);

            book.setItemMeta(meta);
            gui.addItem(book);
        }
        return gui;
    }

    private Inventory createLevelSelectionGUI(Player player, Enchantment enchantment) {
        int maxLevel = enchantment.getMaxLevel();
        Inventory gui = Bukkit.createInventory(player, 9, LEVEL_GUI_TITLE + formatEnchantmentName(enchantment));

        // Add option to set limit to 0 (remove enchantment).
        gui.setItem(0, createLevelItem(enchantment, 0));

        // Create an item for each level from 1 to maxLevel.
        for (int i = 1; i <= maxLevel; i++) {
            if (i < 9) { // Only add up to 8 levels in this small GUI
                gui.setItem(i, createLevelItem(enchantment, i));
            }
        }

        // Store the enchantment the player is currently configuring.
        playerEnchantmentMap.put(player.getUniqueId(), enchantment);

        return gui;
    }

    private ItemStack createLevelItem(Enchantment enchantment, int level) {
        Material material = Material.LIME_STAINED_GLASS_PANE;
        String displayName = ChatColor.GREEN + formatEnchantmentName(enchantment) + " " + toRoman(level);

        // Use a different item/color for level 0.
        if (level == 0) {
            material = Material.RED_STAINED_GLASS_PANE;
            displayName = ChatColor.RED + "Remove Enchantment";
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Set limit to " + level);
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }

    // --- New Event Handler for GUI Clicks ---

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;

        String title = event.getView().getTitle();

        // Handle clicks in the main enchantment selection GUI.
        if (title.equals(MAIN_GUI_TITLE)) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() != Material.ENCHANTED_BOOK) return;

            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) clickedItem.getItemMeta();
            if (meta == null || meta.getStoredEnchants().isEmpty()) return;

            Enchantment enchantment = (Enchantment) meta.getStoredEnchants().keySet().toArray()[0];

            Player player = (Player) event.getWhoClicked();
            playerEnchantmentMap.put(player.getUniqueId(), enchantment);
            player.openInventory(createLevelSelectionGUI(player, enchantment));

        }
        // Handle clicks in the level selection GUI.
        else if (title.startsWith(LEVEL_GUI_TITLE)) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            Player player = (Player) event.getWhoClicked();
            Enchantment enchantment = playerEnchantmentMap.get(player.getUniqueId());
            if (enchantment == null) return;

            // --- FIX ---
            // Get the level directly from the lore instead of parsing the display name.
            List<String> lore = Objects.requireNonNull(clickedItem.getItemMeta()).getLore();
            if (lore == null || lore.isEmpty()) return;

            String loreLine = ChatColor.stripColor(lore.get(0));
            // Extract the number from the lore string "Set limit to X"
            int level = Integer.parseInt(loreLine.substring(loreLine.lastIndexOf(" ") + 1));
            // --- END FIX ---

            getConfig().set("enchantment-limits." + enchantment.getKey().getKey(), level);
            saveConfig();

            player.sendMessage(ChatColor.GREEN + "Enchantment limit for " + formatEnchantmentName(enchantment) + " set to " + level + ".");
            player.closeInventory();
            playerEnchantmentMap.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // When a player joins, check their entire inventory.
        checkPlayerInventory(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // When a player closes an inventory (chest, crafting table, etc.), check their inventory.
        if (event.getPlayer() instanceof Player) {
            checkPlayerInventory((Player) event.getPlayer());
        }
    }

    /**
     * Helper method to iterate through and check every item in a player's inventory.
     * @param player The player whose inventory to check.
     */
    private void checkPlayerInventory(Player player) {
        PlayerInventory inventory = player.getInventory();

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null) {
                capEnchantments(item, player);
            }
        }

        for (ItemStack armor : inventory.getArmorContents()) {
            if (armor != null) {
                capEnchantments(armor, player);
            }
        }

        if (inventory.getItemInOffHand() != null) {
            capEnchantments(inventory.getItemInOffHand(), player);
        }
    }

    /**
     * Helper method to cap enchantments on a single ItemStack based on the config.
     * @param item The ItemStack to check.
     * @param player The player to message if changes are made.
     */
    private void capEnchantments(ItemStack item, Player player) {
        boolean changed = false;

        // First, check for the Sharpness on Axe rule.
        if (isAxe(item.getType()) && item.getEnchantments().containsKey(Enchantment.SHARPNESS)) {
            item.removeEnchantment(Enchantment.SHARPNESS);
            player.sendMessage(ChatColor.RED + "You cannot have Sharpness on an axe!");
            changed = true;
        }

        // Then, check for all other configured limits.
        for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
            Enchantment enchantment = entry.getKey();
            int currentLevel = entry.getValue();

            int limit = getConfig().getInt("enchantment-limits." + enchantment.getKey().getKey(), -1);

            if (limit != -1 && currentLevel > limit) {
                item.removeEnchantment(enchantment);
                if (limit > 0) {
                    item.addEnchantment(enchantment, limit);
                }

                changed = true;
                player.sendMessage(ChatColor.YELLOW + "Enchantment " + formatEnchantmentName(enchantment) + " was capped at level " + limit + ".");
            }
        }

        if (changed) {
            player.updateInventory();
        }
    }

    /**
     * Helper method to get an Enchantment object from its string name.
     * @param name The name of the enchantment (e.g., "protection").
     * @return The Enchantment object, or null if not found.
     */
    private Enchantment getEnchantmentByName(String name) {
        for (Enchantment enchantment : Enchantment.values()) {
            if (enchantment.getKey().getKey().equalsIgnoreCase(name) || enchantment.getName().equalsIgnoreCase(name)) {
                return enchantment;
            }
        }
        return null;
    }

    /**
     * Helper method to format enchantment names.
     * @param enchantment The enchantment to format.
     * @return A user-friendly string (e.g., "fire_aspect" becomes "Fire Aspect").
     */
    private String formatEnchantmentName(Enchantment enchantment) {
        String name = enchantment.getKey().getKey();
        name = name.replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder formattedName = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                formattedName.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return formattedName.toString().trim();
    }

    /**
     * Helper method to check if a Material is an axe.
     * @param material The material to check.
     * @return true if the material is an axe, false otherwise.
     */
    private boolean isAxe(Material material) {
        return material.toString().endsWith("_AXE");
    }

    // --- Roman Numeral Conversion (for GUI display) ---
    private String toRoman(int num) {
        if (num == 0) return "0";
        // The previous Roman numeral array only went up to X.
        String[] roman = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV"};
        if (num > roman.length - 1) {
            return String.valueOf(num);
        }
        return roman[num];
    }

    private int toLevel(String name) {
        // This is a more robust way to get the level from the display name, handling single digits and roman numerals correctly.
        String[] parts = name.split(" ");
        String levelString = parts[parts.length - 1];

        // Check if the string is a number first
        try {
            return Integer.parseInt(levelString);
        } catch (NumberFormatException e) {
            // It's a Roman numeral, so proceed with the switch statement.
        }

        switch(levelString) {
            case "I": return 1;
            case "II": return 2;
            case "III": return 3;
            case "IV": return 4;
            case "V": return 5;
            case "VI": return 6;
            case "VII": return 7;
            case "VIII": return 8;
            case "IX": return 9;
            case "X": return 10;
            case "XI": return 11;
            case "XII": return 12;
            case "XIII": return 13;
            case "XIV": return 14;
            case "XV": return 15;
            default: return 0;
        }
    }
}