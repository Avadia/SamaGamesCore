package net.samagames.core.api.games;

import net.samagames.api.SamaGamesAPI;
import net.samagames.api.games.*;
import net.samagames.api.games.pearls.IPearlManager;
import net.samagames.api.games.themachine.ICoherenceMachine;
import net.samagames.api.parties.IParty;
import net.samagames.core.APIPlugin;
import net.samagames.core.ApiImplementation;
import net.samagames.core.api.games.pearls.PearlManager;
import net.samagames.core.api.games.themachine.CoherenceMachineImpl;
import net.samagames.persistanceapi.beans.statistics.HostStatisticsBean;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/*
 * This file is part of SamaGamesCore.
 *
 * SamaGamesCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SamaGamesCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SamaGamesCore.  If not, see <http://www.gnu.org/licenses/>.
 */
public class GameManager implements IGameManager {
    private final ApiImplementation api;

    private final ConcurrentHashMap<UUID, Long> playerDisconnectedTime;
    private final GameProperties gameProperties;
    private final IPearlManager pearlManager;
    private IGameStatisticsHelper gameStatisticsHelper;
    @SuppressWarnings("rawtypes")
    private Game game;
    private int maxReconnectTime;
    private boolean freeMode;
    private boolean legacyPvP;

    private long startTimestamp;
    private long endTimestamp;

    public GameManager(ApiImplementation api) {
        this.api = api;
        this.game = null;

        this.playerDisconnectedTime = new ConcurrentHashMap<>();

        this.maxReconnectTime = -1;
        this.freeMode = false;
        this.legacyPvP = false;

        this.gameProperties = new GameProperties();
        this.gameStatisticsHelper = null;
        this.pearlManager = new PearlManager(api);
    }

    @Override
    public void registerGame(Game game) {
        if (this.game != null)
            throw new IllegalStateException("A game is already registered!");

        this.game = game;

        this.api.getJoinManager().registerHandler(new GameLoginHandler(this), 100);

        this.api.getPlugin().getExecutor().scheduleAtFixedRate(() ->
        {
            if (game != null)
                this.refreshArena();
        }, 1L, 3 * 30L, TimeUnit.SECONDS);

        game.handlePostRegistration();

        //Check for reconnection can be started when we change the mas reconnection time but fuck it
        this.api.getPlugin().getExecutor().scheduleAtFixedRate(() ->
        {
            for (Map.Entry<UUID, Long> entry : this.playerDisconnectedTime.entrySet()) {
                if (!isReconnectAllowed(entry.getKey())) {
                    onPlayerReconnectTimeOut(Bukkit.getOfflinePlayer(entry.getKey()), false);
                }
            }
        }, 1L, 30L, TimeUnit.SECONDS);

        APIPlugin.getInstance().getLogger().info("Registered game '" + game.getGameName() + "' successfuly!");
    }

    public void rejoinTemplateQueue(Player p) {
        this.api.getPlugin().getServer().getScheduler().runTaskAsynchronously(this.api.getPlugin(), () ->
        {
            IParty party = SamaGamesAPI.get().getPartiesManager().getPartyForPlayer(p.getUniqueId());

            if (party == null) {
                this.api.getHydroangeasManager().addPlayerToQueue(p.getUniqueId(), getGameProperties().getTemplateID());
            } else {
                if (!party.getLeader().equals(p.getUniqueId())) {
                    p.sendMessage(ChatColor.RED + "Vous n'êtes pas le leader de votre partie, vous ne pouvez donc pas l'ajouter dans une file d'attente.");
                    return;
                }

                this.api.getHydroangeasManager().addPartyToQueue(p.getUniqueId(), party.getParty(), getGameProperties().getTemplateID());
            }
        });
    }

    @Override
    public void kickPlayer(Player p, String msg) {
        if (!this.api.getPlugin().isEnabled()) {
            p.kickPlayer(msg);
            return;
        }

        if (!p.isOnline())
            return;

        this.api.getPlayerManager().connectToServer(p.getUniqueId(), "lobby");
    }

    @Override
    public void onPlayerDisconnect(Player player) {
        GamePlayer gamePlayer = this.game.getPlayer(player.getUniqueId());

        if (this.maxReconnectTime > 0
                && gamePlayer != null
                && !this.game.isModerator(gamePlayer.getPlayerIfOnline())
                && !gamePlayer.isSpectator()
                && this.game.getStatus() == Status.IN_GAME) {
            long currentTime = System.currentTimeMillis();

            this.playerDisconnectedTime.put(player.getUniqueId(), currentTime);

            this.api.getPlugin().getExecutor().execute(() -> {
                Jedis jedis = api.getBungeeResource();
                jedis.set("rejoin:" + player.getUniqueId(), this.api.getServerName());
                jedis.expire("rejoin:" + player.getUniqueId(), this.maxReconnectTime * 60);
                jedis.close();
            });
        }

        this.game.handleLogout(player);

        refreshArena();
    }

    @Override
    public void onPlayerReconnect(Player player) {
        this.game.handleReconnect(player);

        Long decoTime = this.playerDisconnectedTime.get(player.getUniqueId());

        if (decoTime != null)
            this.playerDisconnectedTime.remove(player.getUniqueId());

        refreshArena();
    }

    @Override
    public void onPlayerReconnectTimeOut(OfflinePlayer player, boolean silent) {
        this.playerDisconnectedTime.remove(player.getUniqueId());
        this.game.handleReconnectTimeOut(player, silent);
    }

    public void refreshArena() {
        if (this.game == null)
            throw new IllegalStateException("Can't refresh arena because the arena is null!");

        new ServerStatus(SamaGamesAPI.get().getServerName(), this.game.getGameName(), this.gameProperties.getMapName(), this.game.getStatus(), this.game.getConnectedPlayers() + api.getJoinManager().countExpectedPlayers(), this.gameProperties.getMaxSlots()).sendToHubs();
    }

    @Override
    public void startTimer() {
        this.startTimestamp = System.currentTimeMillis();
    }

    @Override
    public void stopTimer() {
        this.endTimestamp = System.currentTimeMillis();

        this.api.getPlugin().getExecutor().execute(() ->
        {
            HostStatisticsBean hostStatisticsBean = new HostStatisticsBean(
                    this.gameProperties.getTemplateID(),
                    this.api.getServerName(),
                    Bukkit.getIp(),
                    new UUID(0, 0),
                    this.startTimestamp,
                    System.currentTimeMillis() - this.startTimestamp);

            try {
                this.api.getGameServiceManager().createHostRecord(hostStatisticsBean);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void setKeepPlayerCache(boolean keepIt) {
        this.api.setKeepCache(keepIt);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Game getGame() {
        return this.game;
    }

    @Override
    public Status getGameStatus() {
        if (this.game == null)
            return null;

        return this.game.getStatus();
    }

    @Override
    public ICoherenceMachine getCoherenceMachine() {
        if (this.game == null)
            throw new NullPointerException("Can't get CoherenceMachine because game is null!");

        if (this.game.getCoherenceMachine() == null)
            return new CoherenceMachineImpl(this.game, this.gameProperties);

        return this.game.getCoherenceMachine();
    }

    @Override
    public GameProperties getGameProperties() {
        return this.gameProperties;
    }

    @Override
    public GameGuiManager getGameGuiManager() {
        return new GameGuiManager();
    }

    @Override
    public int getMaxReconnectTime() {
        return this.maxReconnectTime;
    }

    @Override
    public void setMaxReconnectTime(int minutes) {
        this.maxReconnectTime = minutes;
    }

    @Override
    public long getGameTime() {
        return this.endTimestamp - this.startTimestamp;
    }

    @Override
    public IGameStatisticsHelper getGameStatisticsHelper() {
        return this.gameStatisticsHelper;
    }

    @Override
    public void setGameStatisticsHelper(IGameStatisticsHelper gameStatisticsHelper) {
        this.gameStatisticsHelper = gameStatisticsHelper;
    }

    @Override
    public IPearlManager getPearlManager() {
        return this.pearlManager;
    }

    @Override
    public boolean isWaited(UUID uuid) {
        return this.playerDisconnectedTime.containsKey(uuid);
    }

    @Override
    public boolean isFreeMode() {
        return this.freeMode;
    }

    @Override
    public void setFreeMode(boolean freeMode) {
        this.freeMode = freeMode;
    }

    @Override
    public boolean isLegacyPvP() {
        return this.legacyPvP;
    }

    @Override
    public void setLegacyPvP(boolean legacyPvP) {
        this.legacyPvP = legacyPvP;
    }

    @Override
    public boolean isReconnectAllowed(Player player) {
        return this.isReconnectAllowed(player.getUniqueId());
    }

    @Override
    public boolean isReconnectAllowed(UUID player) {
        if (this.maxReconnectTime <= 0)
            return false;

        Long decoTime = this.playerDisconnectedTime.get(player);

        return decoTime != null && System.currentTimeMillis() < this.maxReconnectTime * 60 * 1000 + decoTime;
    }

    @Override
    public boolean isKeepingPlayerCache() {
        return this.api.isKeepCache();
    }
}
