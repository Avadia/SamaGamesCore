package net.samagames.core.commands;

import net.samagames.core.APIPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/**
 * This file is a part of the SamaGames project
 * This code is absolutely confidential.
 * Created by Thog
 * (C) Copyright Elydra Network 2015
 * All rights reserved.
 */
public class CommandBukkitdebug extends AbstractCommand {

	public CommandBukkitdebug(APIPlugin plugin) {
		super(plugin);
	}

	@Override
	public boolean onCommand(CommandSender sender, String label, String[] arguments) {
		if (!hasPermission(sender, "api.servers.debug"))
			return true;

		plugin.getDebugListener().toggle(sender);
		sender.sendMessage(ChatColor.GOLD + "Bukkit Debug toggled.");

		return true;
	}
}
