package net.samagames.core.api.games;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.samagames.api.games.IGameProperties;
import net.samagames.core.APIPlugin;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

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
class GameProperties implements IGameProperties {
    private String templateID;
    private String mapName;
    private JsonObject options;
    private JsonObject mapProperties;
    private int minSlots;
    private int maxSlots;

    public GameProperties() {
        reload();
    }

    public void reload() {
        try {
            File file = new File(APIPlugin.getInstance().getDataFolder().getAbsoluteFile().getParentFile().getParentFile(), "game.json");

            if (!file.exists()) {
                APIPlugin.getInstance().getLogger().warning("No game properties file found! If this serveur isn't a game server, don't worry about this message!");
                return;
            }

            JsonObject rootJson = new JsonParser().parse(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)).getAsJsonObject();
            this.templateID = rootJson.get("template-id").getAsString();
            this.mapName = rootJson.get("map-name").getAsString();
            this.minSlots = rootJson.get("min-slots").getAsInt();
            this.maxSlots = rootJson.get("max-slots").getAsInt();
            this.options = rootJson.get("options").getAsJsonObject();

            File worldFolder = new File(APIPlugin.getInstance().getDataFolder().getAbsoluteFile().getParentFile().getParentFile(), "world");
            File arenaFile = new File(worldFolder, "arena.json");

            if (!arenaFile.exists()) {
                this.mapProperties = new JsonObject();
                APIPlugin.getInstance().getLogger().warning("No arena properties file found! If this serveur isn't a game server, don't worry about this message!");
                return;
            }

            this.mapProperties = new JsonParser().parse(new InputStreamReader(new FileInputStream(arenaFile), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            APIPlugin.getInstance().getLogger().severe("Can't open the game properties file. Abort start!");
            APIPlugin.getInstance().disable();
            Bukkit.shutdown();
        }
    }

    public String getMapName() {
        return mapName;
    }

    public int getMinSlots() {
        return minSlots;
    }

    public int getMaxSlots() {
        return maxSlots;
    }

    public JsonElement getGameOption(String key, JsonElement defaultValue) {
        if (options.has(key))
            return options.get(key);
        else
            return defaultValue;
    }

    public JsonObject getGameOptions() {
        return options;
    }

    public JsonElement getMapProperty(String key, JsonElement defaultValue) {
        if (this.mapProperties.has(key))
            return this.mapProperties.get(key);
        else
            return defaultValue;
    }

    public JsonObject getMapProperties() {
        return mapProperties;
    }

    public String getTemplateID() {
        return templateID;
    }
}
