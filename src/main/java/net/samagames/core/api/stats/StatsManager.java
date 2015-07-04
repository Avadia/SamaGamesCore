package net.samagames.core.api.stats;

import net.samagames.api.stats.AbstractStatsManager;
import net.samagames.api.stats.IPlayerStat;
import net.samagames.api.stats.Leaderboard;
import net.samagames.core.APIPlugin;
import org.bukkit.Bukkit;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

/**
 * This file is a part of the SamaGames project
 * This code is absolutely confidential.
 * (C) Copyright Elydra Network 2015
 * All rights reserved.
 */
public class StatsManager extends AbstractStatsManager {

	public StatsManager(String game) {
		super(game);
	}

	@Override
	public void increase(final UUID player, final String stat, final int amount) {
		Bukkit.getScheduler().runTaskAsynchronously(APIPlugin.getInstance(), () -> {
			Jedis j = APIPlugin.getApi().getResource();
			j.zincrby("gamestats:" + game + ":" + stat, amount, player.toString());
			j.close();
		});
	}

	@Override
	public void setValue(UUID player, String stat, int value) {
		Bukkit.getScheduler().runTaskAsynchronously(APIPlugin.getInstance(), () -> {
			Jedis j = APIPlugin.getApi().getResource();
			j.zadd("gamestats:" + game + ":" + stat, value, player.toString());
			j.close();
		});
	}

	@Override
	public double getStatValue(UUID player, String stat) {
		Jedis j = APIPlugin.getApi().getResource();
		double value = j.zscore("gamestats:"+game+":"+stat, player.toString());
		j.close();

		return value;
	}

	@Override
	public Leaderboard getLeaderboard(String stat)
	{
		ArrayList<IPlayerStat> leaderboard = new ArrayList<>();
		Jedis jedis = APIPlugin.getApi().getResource();
		Set<String> ids = jedis.zrevrange("gamestats:" + game + ":" + stat, 0, 2);
		jedis.close();

		for (String id : ids)
		{
            IPlayerStat playerStat = new PlayerStat(UUID.fromString(id), this.game, stat);
			playerStat.fill();

			leaderboard.add(playerStat);
		}

		return new Leaderboard(leaderboard.get(0), leaderboard.get(1), leaderboard.get(2));
	}
}
