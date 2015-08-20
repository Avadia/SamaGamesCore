package net.samagames.core.api.player;

import net.samagames.api.player.AbstractPlayerData;
import net.samagames.api.player.IPlayerDataManager;
import net.samagames.core.ApiImplementation;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This file is a part of the SamaGames project
 * This code is absolutely confidential.
 * Created by zyuiop
 * (C) Copyright Elydra Network 2015
 * All rights reserved.
 */
public class PlayerDataManager implements IPlayerDataManager
{

    private final ApiImplementation api;
    private final ConcurrentHashMap<UUID, PlayerData> cachedData = new ConcurrentHashMap<>();
    private final CoinsManager coinsManager;
    private final StarsManager starsManager;

    public PlayerDataManager(ApiImplementation api)
    {
        this.api = api;
        coinsManager = new CoinsManager(api);
        starsManager = new StarsManager(api);
    }

    CoinsManager getCoinsManager()
    {
        return coinsManager;
    }

    StarsManager getStarsManager()
    {
        return starsManager;
    }

    @Override
    public AbstractPlayerData getPlayerData(UUID player)
    {
        return getPlayerData(player, false);
    }

    @Override
    public AbstractPlayerData getPlayerData(UUID player, boolean forceRefresh)
    {
        if (!cachedData.containsKey(player))
        {
            PlayerData data = new PlayerData(player, api, this);
            cachedData.put(player, data);
            return data;
        }

        PlayerData data = cachedData.get(player);

        if (forceRefresh)
        {
            data.updateData();
            return data;
        }

        data.refreshIfNeeded();
        return data;
    }

    @Override
    public void unload(UUID player)
    {
        cachedData.remove(player);
    }
}
