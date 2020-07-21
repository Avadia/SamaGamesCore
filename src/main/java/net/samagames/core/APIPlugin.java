package net.samagames.core;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.samagames.api.exceptions.DataNotFoundException;
import net.samagames.core.api.hydroangeas.HydroangeasManager;
import net.samagames.core.database.DatabaseConnector;
import net.samagames.core.database.RedisServer;
import net.samagames.core.legacypvp.LegacyManager;
import net.samagames.core.listeners.general.*;
import net.samagames.core.listeners.pluginmessages.PluginMessageListener;
import net.samagames.core.utils.CommandBlocker;
import net.samagames.persistanceapi.GameServiceManager;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import us.myles.ViaVersion.api.Via;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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
public class APIPlugin extends JavaPlugin {
    private static APIPlugin instance;
    private final CopyOnWriteArraySet<String> ipWhiteList = new CopyOnWriteArraySet<>();
    private ApiImplementation api;
    private DatabaseConnector databaseConnector;
    private String serverName;
    private boolean allowJoin;
    private String joinPermission = null;
    private ScheduledExecutorService executor;

    //private NicknamePacketListener nicknamePacketListener;
    private DebugListener debugListener;
    private CompletionPacketListener completionPacketListener;
    private GlobalJoinListener globalJoinListener;

    private LegacyManager legacyManager;
    private GameServiceManager gameServiceManager;
    private HydroangeasManager hydroangeasManager;

    public static APIPlugin getInstance() {
        return instance;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void onEnable() {
        instance = this;

        getLogger().info("#==========[WELCOME TO SAMAGAMES API]==========#");
        getLogger().info("# SamaGamesAPI is now loading. Please read     #");
        getLogger().info("# carefully all outputs coming from it.        #");
        getLogger().info("#==============================================#");

        executor = Executors.newScheduledThreadPool(32);

        getLogger().info("Loading main configuration...");
        this.saveDefaultConfig();

        if (getConfig().contains("bungeename")) {
            serverName = getConfig().getString("bungeename");
        } else {
            getLogger().severe("Plugin cannot load : ServerName is empty.");
            this.setEnabled(false);
            Bukkit.getServer().shutdown();
            return;
        }

        if (getConfig().contains("join-permission"))
            joinPermission = getConfig().getString("join-permission");

        File dataFile = new File(getDataFolder().getAbsoluteFile().getParentFile().getParentFile(), "data.yml");
        this.getLogger().info("Searching data.yml in " + dataFile.getAbsolutePath());
        if (dataFile.exists()) {
            YamlConfiguration dataYML = YamlConfiguration.loadConfiguration(dataFile);

            String bungeeIp = dataYML.getString("redis-bungee-ip", "127.0.0.1");
            int bungeePort = dataYML.getInt("redis-bungee-port", 4242);
            String bungeePassword = dataYML.getString("redis-bungee-password", "passw0rd");
            RedisServer bungee = new RedisServer(bungeeIp, bungeePort, bungeePassword);

            String sqlIp = dataYML.getString("sql-ip", "127.0.0.1");
            int sqlPort = dataYML.getInt("sql-port", 3306);
            String sqlName = dataYML.getString("sql-name", "minecraft");
            String sqlUsername = dataYML.getString("sql-user", "root");
            String sqlPassword = dataYML.getString("sql-pass", "passw0rd");

            gameServiceManager = new GameServiceManager(sqlIp, sqlUsername, sqlPassword, sqlName, sqlPort);

            databaseConnector = new DatabaseConnector(this, bungee);
        } else {
            getLogger().severe("Cannot find database configuration. Stopping!");
            this.setEnabled(false);
            this.getServer().shutdown();
            return;
        }

        hydroangeasManager = new HydroangeasManager(this);

        api = new ApiImplementation(this);

        this.legacyManager = new LegacyManager(this);

        ChatHandleListener chatHandleListener = new ChatHandleListener(this);
        api.getPubSub().subscribe("mute.add", chatHandleListener);
        api.getPubSub().subscribe("mute.remove", chatHandleListener);
        Bukkit.getPluginManager().registerEvents(chatHandleListener, this);

        globalJoinListener = new GlobalJoinListener(api);
        Bukkit.getPluginManager().registerEvents(globalJoinListener, this);

        debugListener = new DebugListener();
        api.getJoinManager().registerHandler(debugListener, 0);

        //Invisible fix
        getServer().getPluginManager().registerEvents(new InvisiblePlayerFixListener(this), this);

        api.getPubSub().subscribe("*", debugListener);
        //Nickname
        //TODO nickname
        //nicknamePacketListener = new NicknamePacketListener(this);
        completionPacketListener = new CompletionPacketListener(this);

        Bukkit.getPluginManager().registerEvents(new TabsColorsListener(this), this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "WDL|CONTROL");
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "WDL|INIT", (s, player, bytes) -> {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeInt(1);
            out.writeBoolean(false);
            out.writeInt(1);
            out.writeBoolean(false);
            out.writeBoolean(false);
            out.writeBoolean(false);
            out.writeBoolean(false);
            Bukkit.getLogger().info("Blocked WorldDownloader of " + player.getDisplayName());
            player.sendPluginMessage(this, "WDL|CONTROL", out.toByteArray());
        });

        this.getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : this.getServer().getOnlinePlayers())
                Arrays.asList(35, 36, 37, 38, 39).forEach(id -> {
                    try {
                        api.getAchievementManager().incrementAchievement(player.getUniqueId(), id, 1);
                    } catch (DataNotFoundException e) {
                        e.printStackTrace();
                    }
                });
        }, 20L * 60, 20L * 60);

        PluginMessageListener pluginMessageListener = new PluginMessageListener(api);

        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "Network");
        this.getServer().getMessenger().registerIncomingPluginChannel(this, "Network", pluginMessageListener);

        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "Achievement");
        this.getServer().getMessenger().registerIncomingPluginChannel(this, "Achievement", pluginMessageListener);

        for (String command : this.getDescription().getCommands().keySet()) {
            try {
                Class clazz = Class.forName("net.samagames.core.commands.Command" + StringUtils.capitalize(command));
                Constructor<APIPlugin> ctor = clazz.getConstructor(APIPlugin.class);
                getCommand(command).setExecutor(ctor.newInstance(this));
                getLogger().info("Loaded command " + command + " successfully. ");
            } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | InvocationTargetException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            while (!Via.getPlatform().isPluginEnabled()) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            getLogger().info("Removing private commands...");
            CommandBlocker.removeCommands();
            getLogger().info("Removed private commands.");
            getServer().getWorlds().get(0).setGameRuleValue("announceAdvancements", "false");
            allowJoin = true;
            getServer().setWhitelist(false);
            getExecutor().scheduleAtFixedRate(() -> {
                if (this.isEnabled()) {
                    try {
                        getLogger().info("Trying to register server to the proxy");
                        api.getPubSub().send("servers", "heartbeat " + getServerName());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, 0, 60, TimeUnit.SECONDS);
        });
    }

    public void onDisable() {
        api.getPubSub().send("servers", "stop " + serverName);
        //nicknamePacketListener.close();
        completionPacketListener.close();
        executor.shutdown();
        try {
            executor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        api.onShutdown();
        databaseConnector.killConnection();
        gameServiceManager.disconnect();
        getServer().shutdown();
    }

    public ApiImplementation getAPI() {
        return api;
    }

    public DebugListener getDebugListener() {
        return debugListener;
    }

    public ScheduledExecutorService getExecutor() {
        return executor;
    }

    public void refreshIps(Set<String> ips) {
        ipWhiteList.stream().filter(ip -> !ips.contains(ip)).forEach(ipWhiteList::remove);

        ips.stream().filter(ip -> !ipWhiteList.contains(ip)).forEach(ipWhiteList::add);
    }

    public CopyOnWriteArraySet<String> getIpWhiteList() {
        return ipWhiteList;
    }

    public String getServerName() {
        return serverName;
    }

    public String getJoinPermission() {
        return joinPermission;
    }

    public boolean isAllowJoin() {
        return allowJoin;
    }

    public void disable() {
        setEnabled(false);
    }

    public GlobalJoinListener getGlobalJoinListener() {
        return globalJoinListener;
    }

    public boolean isHub() {
        return getServerName().startsWith("hub");
    }

    public DatabaseConnector getDatabaseConnector() {
        return databaseConnector;
    }

    public GameServiceManager getGameServiceManager() {
        return gameServiceManager;
    }

    public HydroangeasManager getHydroangeasManager() {
        return hydroangeasManager;
    }

    public LegacyManager getLegacyManager() {
        return legacyManager;
    }
}
