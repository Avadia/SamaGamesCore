package net.samagames.core.listeners.pubsub;

import net.samagames.api.pubsub.IPacketsReceiver;
import net.samagames.core.APIPlugin;

/**
 * This file is a part of the SamaGames project
 * This code is absolutely confidential.
 * (C) Copyright Elydra Network 2015
 * All rights reserved.
 */
public class GlobalChannelHandler implements IPacketsReceiver
{

    private final APIPlugin plugin;

    public GlobalChannelHandler(APIPlugin plugin)
    {
        this.plugin = plugin;
    }

    @Override
    public void receive(String channel, String packet)
    {
        if (packet.equalsIgnoreCase("reboot"))
        {
            plugin.getServer().shutdown();
        } else if (packet.equalsIgnoreCase("rebootIfEmpty"))
        {
            if (plugin.getServer().getOnlinePlayers().size() == 0)
                plugin.getServer().shutdown();
        }
    }
}
