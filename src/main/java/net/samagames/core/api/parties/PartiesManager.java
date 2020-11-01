package net.samagames.core.api.parties;

import net.samagames.api.parties.IPartiesManager;
import net.samagames.api.parties.IParty;
import net.samagames.core.ApiImplementation;
import org.bukkit.Bukkit;
import redis.clients.jedis.Jedis;

import java.util.*;
import java.util.stream.Collectors;

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
public class PartiesManager implements IPartiesManager {
    private final ApiImplementation api;
    private final HashMap<UUID, Party> parties;

    public PartiesManager(ApiImplementation api) {
        this.parties = new HashMap<>();
        this.api = api;
    }

    //TODO add to listerner before join
    public void loadPlayer(UUID player) {
        //TODO create partie if not already
        try {
            Party party = getPartyForPlayer(player);

            if (party == null) {
                Jedis jedis = api.getBungeeResource();
                if (!jedis.exists("currentparty:" + player.toString())) {
                    jedis.close();
                    return;
                }
                UUID partieID = UUID.fromString(jedis.get("currentparty:" + player.toString()));
                loadParty(partieID);
                jedis.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void loadParty(UUID party) {
        Jedis jedis = api.getBungeeResource();
        String leader = jedis.get("party:" + party.toString() + ":lead");
        Map<String, String> data = jedis.hgetAll("party:" + party.toString() + ":members");
        jedis.close();
        if (leader == null)
            return;

        Party partie = new Party(party, UUID.fromString(leader), data.keySet().stream().map(UUID::fromString).collect(Collectors.toList()));
        parties.put(party, partie);
    }

    public void unloadPlayer(UUID player) {
        Party party = getPartyForPlayer(player);
        if (party != null) {
            unloadParties();
        }
    }

    //Check all in case of dead party
    public void unloadParties() {
        for (Party party : new ArrayList<>(parties.values())) {
            int online = 0;
            //Check si tous les joueurs se sont deconnecter
            for (UUID players : party.getPlayers()) {
                if (Bukkit.getPlayer(players) != null) {
                    online++;
                }
            }
            if (online == 0) {
                parties.remove(party.getParty());
            }
        }
    }

    @Override
    public List<UUID> getPlayersInParty(UUID party) {
        Party party1 = getParty(party);
        if (party1 != null) {
            return party1.getPlayers();
        } else {
            return new ArrayList<>();
        }
    }

    @Override
    public String getCurrentServer(UUID party) {
        Jedis jedis = api.getBungeeResource();
        String server = jedis.get("party:" + party.toString() + ":server");
        jedis.close();
        return server;
    }

    @Override
    public UUID getLeader(UUID party) {
        Party partie = getParty(party);
        return (partie != null) ? partie.getLeader() : null;
    }

    @Override
    public Party getParty(UUID partie) {
        //TODO load if not
        return parties.get(partie);
    }

    @Override
    public Party getPartyForPlayer(UUID player) {
        for (Party party : parties.values()) {
            if (party.containsPlayer(player)) {
                return party;
            }
        }
        return null;
    }

    @Override
    public HashMap<UUID, IParty> getParties() {
        return new HashMap<>(parties);
    }
}
