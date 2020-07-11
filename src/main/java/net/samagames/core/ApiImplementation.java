package net.samagames.core;

import in.ashwanthkumar.slack.webhook.Slack;
import net.samagames.api.SamaGamesAPI;
import net.samagames.core.api.achievements.AchievementManager;
import net.samagames.core.api.friends.FriendsManager;
import net.samagames.core.api.games.GameManager;
import net.samagames.core.api.gui.GuiManager;
import net.samagames.core.api.hydroangeas.HydroangeasManager;
import net.samagames.core.api.names.UUIDTranslator;
import net.samagames.core.api.network.JoinManagerImplement;
import net.samagames.core.api.network.ModerationJoinHandler;
import net.samagames.core.api.network.PartiesPubSub;
import net.samagames.core.api.network.RegularJoinHandler;
import net.samagames.core.api.options.ServerOptions;
import net.samagames.core.api.parties.PartiesManager;
import net.samagames.core.api.parties.PartyListener;
import net.samagames.core.api.permissions.PermissionManager;
import net.samagames.core.api.player.PlayerDataManager;
import net.samagames.core.api.pubsub.PubSubAPI;
import net.samagames.core.api.resourcepacks.ResourcePacksManagerImpl;
import net.samagames.core.api.settings.SettingsManager;
import net.samagames.core.api.shops.ShopsManager;
import net.samagames.core.api.stats.StatsManager;
import net.samagames.core.listeners.pubsub.GlobalUpdateListener;
import net.samagames.persistanceapi.GameServiceManager;
import net.samagames.tools.SkyFactory;
import net.samagames.tools.cameras.CameraManager;
import net.samagames.tools.npc.NPCManager;
import redis.clients.jedis.Jedis;

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
public class ApiImplementation extends SamaGamesAPI {
    private final APIPlugin plugin;
    private final PubSubAPI pubSub;
    private GuiManager guiManager;
    private SettingsManager settingsManager;
    private PlayerDataManager playerDataManager;
    private UUIDTranslator uuidTranslator;
    private JoinManagerImplement joinManager;
    private PartiesManager partiesManager;
    private ResourcePacksManagerImpl resourcePacksManager;
    private PermissionManager permissionsManager;
    private FriendsManager friendsManager;
    private SkyFactory skyFactory;
    private StatsManager statsManager;
    private ShopsManager shopsManager;
    private NPCManager npcManager;
    private CameraManager cameraManager;
    private AchievementManager achievementManager;
    private GameManager gameManager;
    private ServerOptions serverOptions;

    private boolean keepCache = false;

    public ApiImplementation(APIPlugin plugin) {
        super(plugin);

        this.plugin = plugin;

        this.pubSub = new PubSubAPI(this);
        //TODO redo
        GlobalUpdateListener listener = new GlobalUpdateListener(plugin);
        this.pubSub.subscribe("groupchange", listener);
        this.pubSub.subscribe("global", listener);
        this.pubSub.subscribe("networkEvent_WillQuit", listener);
        this.pubSub.subscribe(plugin.getServerName(), listener);
        this.pubSub.subscribe("commands.servers." + getServerName(), new RemoteCommandsHandler(plugin));
        this.pubSub.subscribe("commands.servers.all", new RemoteCommandsHandler(plugin));

        ModerationJoinHandler moderationJoinHandler = new ModerationJoinHandler(this);
        getJoinManager().registerHandler(moderationJoinHandler, -1);
        pubSub.subscribe(plugin.getServerName(), moderationJoinHandler);
        pubSub.subscribe("partyjoin." + getServerName(), new PartiesPubSub(this, getJoinManager()));
        pubSub.subscribe("join." + getServerName(), new RegularJoinHandler(getJoinManager()));

        PartyListener partyListener = new PartyListener(getPartiesManager());
        this.pubSub.subscribe("parties.disband", partyListener);
        this.pubSub.subscribe("parties.leave", partyListener);
        this.pubSub.subscribe("parties.kick", partyListener);
        this.pubSub.subscribe("parties.join", partyListener);
        this.pubSub.subscribe("parties.lead", partyListener);
    }

    public void onShutdown() {
        this.playerDataManager.onShutdown();
        this.pubSub.disable();
    }

    @Override
    public APIPlugin getPlugin() {
        return plugin;
    }

    @Override
    public Slack getSlackLogsPublisher() {
        return new Slack(plugin.getConfig().getString("slack")).icon(":smiley_cat:").displayName("Meow");
    }

    @Override
    public PermissionManager getPermissionsManager() {
        return (permissionsManager == null) ? (this.permissionsManager = new PermissionManager(this)) : this.permissionsManager;
    }

    @Override
    public ServerOptions getServerOptions() {
        return (serverOptions == null) ? (this.serverOptions = new ServerOptions()) : this.serverOptions;
    }

    @Override
    public NPCManager getNPCManager() {
        return (npcManager == null) ? (this.npcManager = new NPCManager(this)) : this.npcManager;
    }

    @Override
    public ResourcePacksManagerImpl getResourcePacksManager() {
        return (resourcePacksManager == null) ? (this.resourcePacksManager = new ResourcePacksManagerImpl(this)) : this.resourcePacksManager;
    }

    @Override
    public FriendsManager getFriendsManager() {
        return (friendsManager == null) ? (this.friendsManager = new FriendsManager(this)) : this.friendsManager;
    }

    @Override
    public GameManager getGameManager() {
        return (gameManager == null) ? (this.gameManager = new GameManager(this)) : this.gameManager;
    }

    @Override
    public PartiesManager getPartiesManager() {
        return (partiesManager == null) ? (this.partiesManager = new PartiesManager(this)) : this.partiesManager;
    }

    @Override
    public SkyFactory getSkyFactory() {
        return (skyFactory == null) ? (this.skyFactory = new SkyFactory(plugin)) : this.skyFactory;
    }

    @Override
    public CameraManager getCameraManager() {
        return (cameraManager == null) ? (this.cameraManager = new CameraManager(this)) : this.cameraManager;
    }

    @Override
    public JoinManagerImplement getJoinManager() {
        return (joinManager == null) ? (this.joinManager = new JoinManagerImplement(this)) : this.joinManager;
    }

    @Override
    public StatsManager getStatsManager() {
        return (statsManager == null) ? (this.statsManager = new StatsManager(this)) : this.statsManager;
    }

    @Override
    public ShopsManager getShopsManager() {
        return (shopsManager == null) ? (this.shopsManager = new ShopsManager(this)) : this.shopsManager;
    }

    @Override
    public GuiManager getGuiManager() {
        return (guiManager == null) ? (this.guiManager = new GuiManager(plugin)) : this.guiManager;
    }

    @Override
    public SettingsManager getSettingsManager() {
        return (settingsManager == null) ? (this.settingsManager = new SettingsManager(this)) : this.settingsManager;
    }

    @Override
    public PlayerDataManager getPlayerManager() {
        return (playerDataManager == null) ? (this.playerDataManager = new PlayerDataManager(this)) : this.playerDataManager;
    }

    @Override
    public AchievementManager getAchievementManager() {
        return (achievementManager == null) ? (this.achievementManager = new AchievementManager(this)) : this.achievementManager;
    }

    @Override
    public UUIDTranslator getUUIDTranslator() {
        return (uuidTranslator == null) ? (this.uuidTranslator = new UUIDTranslator(plugin, this)) : this.uuidTranslator;
    }

    public PubSubAPI getPubSub() {
        return pubSub;
    }

    public Jedis getBungeeResource() {
        return plugin.getDatabaseConnector().getBungeeResource();
    }

    public GameServiceManager getGameServiceManager() {
        return plugin.getGameServiceManager();
    }

    public HydroangeasManager getHydroangeasManager() {
        return plugin.getHydroangeasManager();
    }

    public boolean isKeepCache() {
        return keepCache;
    }

    public void setKeepCache(boolean keepCache) {
        this.keepCache = keepCache;
    }

    @Override
    public String getServerName() {
        return plugin.getServerName();
    }
}
