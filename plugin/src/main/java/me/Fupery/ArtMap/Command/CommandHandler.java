package me.Fupery.ArtMap.Command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.logging.Level;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MapView.Scale;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.api.Config.Lang;
import me.Fupery.ArtMap.Event.PlayerOpenMenuEvent;
import me.Fupery.ArtMap.IO.MapArt;
import me.Fupery.ArtMap.Menu.Handler.MenuHandler;
import me.Fupery.ArtMap.Recipe.ArtItem;
import me.Fupery.ArtMap.Recipe.ArtMaterial;
import me.Fupery.ArtMap.Utils.ItemUtils;

public class CommandHandler implements CommandExecutor, TabCompleter {

	private final HashMap<String, AsyncCommand> commands;

	public CommandHandler() {
		commands = new HashMap<>();
		// Commands go here - note that they are run on an async thread

		commands.put("save", new CommandSave());

		commands.put("clear", new CommandClear());

		commands.put("delete", new CommandDelete());

		commands.put("preview", new CommandPreview());

		commands.put("import", new CommandImport());

		commands.put("export", new CommandExport());

		commands.put("test", new CommandTest());

		commands.put("convert", new CommandConvert());

		commands.put("repair", new Repair());

		commands.put("search", new Search());

		commands.put("page", new Page());

		commands.put("palette", new AsyncCommand("artmap.admin", "/art palette", true) {
			@Override
			public void runCommand(CommandSender sender, String[] args, ReturnMessage msg) {
				MapView mapView = Bukkit.getServer().createMap(((Player) sender).getWorld());
				mapView.getRenderers().clear();
				mapView.setScale(Scale.CLOSEST);
				mapView.addRenderer(new MapRenderer() {
					boolean done = false;

					@Override
					public void render(MapView view, MapCanvas canvas, Player player) {
						if (!done) {
							for (int y = 0; y < 128; y++) {
								for (int x = 0; x < 128; x++) {
									if (x < 64) {
										canvas.setPixel(x, y, (byte) (y));
									} else {
										canvas.setPixel(x, y, (byte) (y + 128));
									}
								}
							}
							done = true;
						}
					}
				});
				ItemStack map = new ItemStack(Material.FILLED_MAP, 1);
				MapMeta meta = (MapMeta) map.getItemMeta();
				meta.setMapView(mapView);
				map.setItemMeta(meta);
				((Player) sender).getInventory().setItemInMainHand(map);
			}
		});

		commands.put("give", new AsyncCommand("artmap.admin",
				"/artmap give [player] <easel|canvas|paintbrush|unsaved:<id>|artwork:<title>> [amount]", true) {

			private static final List<String> ITEM_TYPES = Arrays.asList("easel", "canvas", "paintbrush");

			private boolean isItemType(String arg) {
				return arg.equalsIgnoreCase("easel") || arg.equalsIgnoreCase("canvas")
						|| arg.equalsIgnoreCase("paintbrush") || arg.contains(":");
			}

			@Override
			public void runCommand(CommandSender sender, String[] args, ReturnMessage msg) {
				if (args.length < 2) {
					sender.sendMessage(Lang.PREFIX + ChatColor.RED + this.usage);
					return;
				}

				Player target;
				int typeIndex;

				if (isItemType(args[1])) {
					if (!(sender instanceof Player)) {
						sender.sendMessage(Lang.PREFIX + ChatColor.RED + "Console must specify a player.");
						return;
					}
					target = (Player) sender;
					typeIndex = 1;
				} else {
					if (args.length < 3) {
						sender.sendMessage(Lang.PREFIX + ChatColor.RED + this.usage);
						return;
					}
					target = Bukkit.getPlayer(args[1]);
					if (target == null) {
						sender.sendMessage(
								Lang.PREFIX + ChatColor.RED + String.format(Lang.PLAYER_NOT_FOUND.get(), args[1]));
						return;
					}
					typeIndex = 2;
				}

				String type = args[typeIndex].toLowerCase();
				ItemStack item = null;

				if (type.equals("easel")) {
					item = ArtMaterial.EASEL.getItem();
				} else if (type.equals("canvas")) {
					item = ArtMaterial.CANVAS.getItem();
				} else if (type.equals("paintbrush")) {
					item = ArtMaterial.PAINT_BRUSH.getItem();
				} else if (type.startsWith("unsaved:")) {
					String[] parts = type.split(":", 2);
					if (parts.length > 1) {
						try {
							int id = Integer.parseInt(parts[1]);
							if (!ArtMap.instance().getArtDatabase().containsUnsavedArtwork(id)) {
								sender.sendMessage(Lang.PREFIX + ChatColor.RED + "No unsaved artwork with that ID.");
								return;
							}
							item = new ArtItem.InProgressArtworkItem(id, target.getName()).toItemStack();
						} catch (Exception e) {
							sender.sendMessage(Lang.PREFIX + ChatColor.RED + "Error retrieving art! Check logs for details.");
							ArtMap.instance().getLogger().log(Level.SEVERE, "Error retrieving art!", e);
							return;
						}
					}
				} else if (type.startsWith("artwork:")) {
					String[] parts = type.split(":", 2);
					if (parts.length > 1) {
						try {
							Optional<MapArt> art = ArtMap.instance().getArtDatabase().getArtwork(parts[1]);
							if (!art.isPresent()) {
								sender.sendMessage(
										Lang.PREFIX + ChatColor.RED + String.format(Lang.MAP_NOT_FOUND.get(), parts[1]));
								return;
							}
							item = art.get().getMapItem();
						} catch (Exception e) {
							sender.sendMessage(Lang.PREFIX + ChatColor.RED + "Error retrieving art! Check logs for details.");
							ArtMap.instance().getLogger().log(Level.SEVERE, "Error retrieving art!", e);
							return;
						}
					}
				}

				if (item == null) {
					sender.sendMessage(Lang.PREFIX + ChatColor.RED + this.usage);
					return;
				}

				int amount = 1;
				if (args.length > typeIndex + 1) {
					try {
						amount = Math.max(1, Math.min(64, Integer.parseInt(args[typeIndex + 1])));
					} catch (NumberFormatException ignored) {
					}
				}
				item.setAmount(amount);

				ItemStack finalItem = item;
				Player finalTarget = target;
				ArtMap.instance().getScheduler().SYNC.run(() -> ItemUtils.giveItem(finalTarget, finalItem));
				sender.sendMessage(Lang.PREFIX + ChatColor.GREEN + "Given " + amount + "x " + type + " to " + target.getName());
			}

			@Override
			public List<String> tabComplete(CommandSender sender, String[] args) {
				List<String> completions = new ArrayList<>();

				if (args.length == 1) {
					String input = args[0].toLowerCase();
					for (String t : ITEM_TYPES) {
						if (t.startsWith(input)) completions.add(t);
					}
					for (Player p : Bukkit.getOnlinePlayers()) {
						if (p.getName().toLowerCase().startsWith(input)) completions.add(p.getName());
					}
				} else if (args.length == 2) {
					if (Bukkit.getPlayer(args[0]) != null) {
						String input = args[1].toLowerCase();
						for (String t : ITEM_TYPES) {
							if (t.startsWith(input)) completions.add(t);
						}
					}
				}

				return completions;
			}
		});

		// convenience commands
		commands.put("help", new AsyncCommand("artmap.menu", "/art [help]", true) {
			@Override
			public void runCommand(CommandSender sender, String[] args, ReturnMessage msg) {
				if (sender instanceof Player) {
					ArtMap.instance().getScheduler().SYNC.run(() -> {
						if (args.length > 0) {
							Lang.Array.CONSOLE_HELP.send(sender);
							return;
						}
						PlayerOpenMenuEvent event = new PlayerOpenMenuEvent((Player) sender);
						Bukkit.getServer().getPluginManager().callEvent(event);
						MenuHandler menuHandler = ArtMap.instance().getMenuHandler();
						menuHandler.openMenu(((Player) sender), menuHandler.MENU.HELP.get(((Player) sender)));
					});
				} else {
					Lang.Array.CONSOLE_HELP.send(sender);
				}
			}
		});
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command command, String alias, String[] args) {
		if (args.length == 0) return new ArrayList<>();

		String sub = args[0].toLowerCase();
		if (args.length == 1) {
			List<String> subs = new ArrayList<>(commands.keySet());
			subs.removeIf(s -> !s.startsWith(sub));
			return subs;
		}

		AsyncCommand cmd = commands.get(sub);
		if (cmd == null) return new ArrayList<>();

		String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
		return cmd.tabComplete(sender, subArgs);
	}

	@Override
	public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
		// handle quoted arguements since spigot does not
		String[] fixedArgs = fixQuotedArgs(args);

		if (fixedArgs.length > 0) {

			if (commands.containsKey(fixedArgs[0].toLowerCase())) {
				commands.get(fixedArgs[0].toLowerCase()).runPlayerCommand(sender, fixedArgs);
			} else {
				Lang.HELP.send(sender);
			}

		} else {
			commands.get("help").runPlayerCommand(sender, fixedArgs);
		}
		return true;
	}

	/**
	 * Combines "" arguments.
	 * 
	 * @param args The original args.
	 * @return The combined args.
	 */
	public static String[] fixQuotedArgs(String[] args) {
		ArrayList<String> newArgs = new ArrayList<>();
		String combined = null;
		for (String arg : args) {
			// handle quoted single word
			if (arg.startsWith("\"") && arg.endsWith("\"")) {
				newArgs.add(arg.replace("\"", ""));
				continue;
			}

			// start combine
			if (combined == null && arg.contains("\"")) {
				combined = arg.replace("\"", "");
				continue;
			}
			// end combine
			if (combined != null && arg.contains("\"")) {
				combined += " " + arg.replace("\"", "");
				newArgs.add(combined);
				combined = null;
				continue;
			}

			// add to combined if its not null otherwise its a lone arg
			if (combined != null) {
				combined += " " + arg;
			} else {
				newArgs.add(arg);
			}
		}
		return newArgs.toArray(new String[0]);
	}

}