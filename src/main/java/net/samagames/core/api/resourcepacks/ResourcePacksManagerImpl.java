package net.samagames.core.api.resourcepacks;

import net.minecraft.server.v1_8_R3.PacketPlayInResourcePackStatus;
import net.minecraft.server.v1_8_R3.PacketPlayOutResourcePackSend;
import net.samagames.api.SamaGamesAPI;
import net.samagames.api.resourcepacks.IResourceCallback;
import net.samagames.api.resourcepacks.IResourcePacksManager;
import net.samagames.core.APIPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import redis.clients.jedis.Jedis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

/**
 * This file is a part of the SamaGames project
 * This code is absolutely confidential.
 * (C) Copyright Elydra Network 2015
 * All rights reserved.
 */
public class ResourcePacksManagerImpl implements IResourcePacksManager, Listener
{

    private final HashSet<UUID> currentlyDownloading = new HashSet<>();
    private final HashMap<UUID, KillerTask> killers = new HashMap<>();
    private final SamaGamesAPI api;
    private final String resetUrl;
    private final ProtocolHandler handler;
    private String forceUrl;
    private String forceHash;
    private IResourceCallback callback;

    public ResourcePacksManagerImpl(SamaGamesAPI api)
    {
        Bukkit.getPluginManager().registerEvents(this, APIPlugin.getInstance());

        this.handler = new ProtocolHandler(APIPlugin.getInstance(), this);
        this.api = api;

        Jedis jedis = api.getBungeeResource();
        this.resetUrl = jedis.get("resourcepacks:reseturl");
        APIPlugin.getInstance().getLogger().info("Resource packs reset URL defined to " + resetUrl);
        jedis.close();
    }

    @Override
    public void forcePack(String name)
    {
        forcePack(name, null);
    }

    @Override
    public void forcePack(String name, IResourceCallback callback)
    {
        Jedis jedis = api.getBungeeResource();
        forcePack(jedis.hget("resourcepack:" + name, "url"), jedis.hget("resourcepack:" + name, "hash"), callback);
        jedis.close();
    }

    @Override
    public void forcePack(String url, String hash, IResourceCallback callback)
    {
        Bukkit.getScheduler().runTaskAsynchronously(APIPlugin.getInstance(), () -> {
            forceUrl = url;
            forceHash = hash;
            APIPlugin.getInstance().getLogger().info("Defined automatic resource pack : " + forceUrl + " with hash " + forceHash);
        });

        this.callback = callback;
    }

    private void sendPack(Player player, String url, String hash)
    {
        APIPlugin.getInstance().getLogger().info("Sending pack to " + player.getName() + " : " + url + " with hash " + hash);

        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(new PacketPlayOutResourcePackSend(url, hash));
    }

    void handle(Player player, String hash, PacketPlayInResourcePackStatus.EnumResourcePackStatus state)
    {
        if (forceUrl == null || forceHash == null)
            return;

        if (callback != null)
            callback.callback(player, state);

        if (state == PacketPlayInResourcePackStatus.EnumResourcePackStatus.SUCCESSFULLY_LOADED)
        {
            currentlyDownloading.remove(player.getUniqueId());
            Bukkit.getScheduler().runTaskAsynchronously(APIPlugin.getInstance(), () -> {
                Jedis jedis = api.getBungeeResource();
                jedis.sadd("playersWithPack", player.getUniqueId().toString());
                jedis.close();
            });
        }

        if (killers.get(player.getUniqueId()) != null)
        {
            killers.get(player.getUniqueId()).changeState(state);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event)
    {
        final Player player = event.getPlayer();

        if (forceHash != null && forceUrl != null)
        {
            Bukkit.getScheduler().runTaskLater(APIPlugin.getInstance(), () -> {
                currentlyDownloading.add(player.getUniqueId());
                sendPack(player, forceUrl, forceHash);

                KillerTask task = new KillerTask(player, callback, this);
                task.runTaskTimer(APIPlugin.getInstance(), 20L, 20L);
                killers.put(event.getPlayer().getUniqueId(), task);
            }, 100);
        } else
        {
            Bukkit.getScheduler().runTaskLater(APIPlugin.getInstance(), () -> {
                Jedis jedis = api.getBungeeResource();
                Long l = jedis.srem("playersWithPack", player.getUniqueId().toString());
                jedis.close();

                if (l > 0)
                    ((CraftPlayer) player).getHandle().playerConnection.sendPacket(new PacketPlayOutResourcePackSend(resetUrl, "samareset.zip"));
            }, 100);
        }
    }

    @Override
    public void kickAllUndownloaded()
    {
        for (UUID id : currentlyDownloading)
        {
            Player player = Bukkit.getPlayer(id);
            if (player != null)
                player.kickPlayer(ChatColor.RED + "Il est nécessaire d'accepter le ressource pack pour jouer.");
        }

        currentlyDownloading.clear();
        killers.values().forEach(net.samagames.core.api.resourcepacks.KillerTask::cancel);
        killers.clear();
    }

    void removeKillerFor(UUID player)
    {
        killers.remove(player);
    }
}
