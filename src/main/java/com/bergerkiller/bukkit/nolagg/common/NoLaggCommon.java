package com.bergerkiller.bukkit.nolagg.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.permissions.NoPermissionException;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.nolagg.NoLaggComponent;
import com.bergerkiller.bukkit.nolagg.NoLaggComponents;
import com.bergerkiller.bukkit.nolagg.Permission;
import com.bergerkiller.bukkit.nolagg.itembuffer.ItemMap;
import com.bergerkiller.bukkit.nolagg.tnt.TNTHandler;

public class NoLaggCommon extends NoLaggComponent {
	private final Set<String> lastargs = new HashSet<String>();
	private Map<String, List<String>> clearShortcuts = new HashMap<String, List<String>>();

	@Override
	public void onDisable(ConfigurationNode config) {
		this.clearShortcuts.clear();
	}

	@Override
	public void onEnable(ConfigurationNode config) {
		this.onReload(config);
	}

	@Override
	public void onReload(ConfigurationNode config) {
		// clear shortcuts
		this.clearShortcuts.clear();
		config.setHeader("clearShortcuts", "\nDefines all shortcuts for the /lag clear command, more can be added");
		if (!config.contains("clearShortcuts")) {
			ConfigurationNode node = config.getNode("clearShortcuts");
			node.set("enemies", Arrays.asList("monster"));
			node.set("notneutral", Arrays.asList("monster", "item", "tnt", "egg", "arrow"));
		}
		if (!config.contains("clearShortcuts.all")) {
			config.setHeader("clearShortcuts.all", "The entity types removed when using /lag clear all");
			config.set("clearShortcuts.all", Arrays.asList("items", "mobs", "fallingblocks", "tnt", "xporb", "minecart", "boat"));
		}
		if (!config.contains("clearShortcuts.default")) {
			config.setHeader("clearShortcuts.default", "The entity types removed when using /lag clear without arguments");
			config.set("clearShortcuts.default", Arrays.asList("items", "tnt", "xporb"));
		}
		ConfigurationNode shortc = config.getNode("clearShortcuts");
		shortc.setHeader("");
		shortc.addHeader("Several shortcuts you can use for the /nolagg clear(all) command");
		for (String key : shortc.getKeys()) {
			clearShortcuts.put(key.toLowerCase(), shortc.getList(key, String.class));
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, String[] args) throws NoPermissionException {
		if (args.length > 0) {
			boolean all = args[0].equalsIgnoreCase("clearall");
			if (args[0].equalsIgnoreCase("clear") || all) {
				if (sender instanceof Player) {
					Permission.COMMON_CLEAR.handle(sender);
				} else {
					all = true;
				}
				// Get the worlds to work in
				Collection<World> worlds;
				if (all) {
					worlds = Bukkit.getServer().getWorlds();
				} else {
					worlds = Arrays.asList(((Player) sender).getWorld());
				}
				// Read all the requested entity types
				Set<String> types = new HashSet<String>();
				if (args.length == 1) {
					// Default types
					types.addAll(clearShortcuts.get("default"));
				} else {
					// Read the types
					List<String> tmpList;
					ArrayList<String> inputTypes = new ArrayList<String>();
					for (int i = 1; i < args.length; i++) {
						String name = args[i].toLowerCase();
						tmpList = clearShortcuts.get(name);
						if (tmpList != null) {
							inputTypes.addAll(tmpList);
						} else {
							inputTypes.add(name);
						}
					}
					for (String name : inputTypes) {
						if (name.contains("xp") || name.contains("orb")) {
							types.add("experienceorb");
							continue;
						}
						if (name.contains("tnt")) {
							types.add("tnt");
							continue;
						}
						if (name.contains("mob")) {
							types.add("animals");
							types.add("monsters");
							continue;
						}
						if (name.equals("item")) {
							name = "items";
						} else if (name.equals("monster")) {
							name = "monsters";
						} else if (name.equals("animal")) {
							name = "animals";
						} else if (name.equals("fallingblock")) {
							name = "fallingblocks";
						}
						types.add(name);
					}
					if (types.remove("last")) {
						sender.sendMessage(ChatColor.GREEN + "The last-used clear arguments are also used");
						types.addAll(lastargs);
					}
				}
				lastargs.clear();
				lastargs.addAll(types);

				// Remove from TNT component if enabled
				if (NoLaggComponents.TNT.isEnabled() && (types.contains("all") || types.contains("tnt"))) {
					if (all) {
						TNTHandler.clear();
					} else {
						for (World world : worlds) {
							TNTHandler.clear(world);
						}
					}
				}

				// Remove items from the item buffer
				if (NoLaggComponents.ITEMBUFFER.isEnabled()) {
					ItemMap.clear(worlds, types);
				}

				// Entity removal logic
				int remcount = 0; // The amount of removed entities
				boolean monsters = types.contains("monsters");
				boolean animals = types.contains("animals");
				boolean items = types.contains("items");
				boolean fallingblocks = types.contains("fallingblocks");
				boolean remove;
				for (World world : worlds) {
					// Use the types set and clear them
					for (Entity e : world.getEntities()) {
						if (e instanceof Player) {
							continue;
						}
						remove = false;
						if (monsters && EntityUtil.isMonster(e)) {
							remove = true;
						} else if (animals && EntityUtil.isAnimal(e)) {
							remove = true;
						} else if (items && e instanceof Item) {
							remove = true;
						} else if (fallingblocks && e instanceof FallingBlock) {
							remove = true;
						} else if (types.contains(EntityUtil.getName(e))) {
							remove = true;
						}
						if (remove) {
							e.remove();
							remcount++;
						}
					}
				}

				// Final confirmation message
				if (all) {
					sender.sendMessage(ChatColor.YELLOW + "All worlds have been cleared: " + remcount + " entities removed!");
				} else {
					sender.sendMessage(ChatColor.YELLOW + "This world has been cleared: " + remcount + " entities removed!");
				}
			} else if (args[0].equalsIgnoreCase("gc")) {
				Permission.COMMON_GC.handle(sender);
				Runtime.getRuntime().gc();
				sender.sendMessage("Memory garbage collected!");
			} else {
				return false;
			}
			return true;
		}
		return false;
	}
}
