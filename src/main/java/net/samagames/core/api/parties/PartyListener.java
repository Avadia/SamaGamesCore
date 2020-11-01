package net.samagames.core.api.parties;

import net.samagames.api.pubsub.IPacketsReceiver;
import org.bukkit.Bukkit;

import java.util.UUID;

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
public class PartyListener implements IPacketsReceiver {
    private final PartiesManager partiesManager;

    public PartyListener(PartiesManager partiesManager) {
        this.partiesManager = partiesManager;
    }

    @Override
    public void receive(String channel, String message) {
        String[] parts = channel.split("\\.");
        if (parts.length < 2)
            return;

        String action = parts[1];
        String[] args = message.split(" ");

        switch (action) {
            case "disband": {
                if (args.length < 1)
                    return;

                UUID player = UUID.fromString(args[0]);
                Party partyForPlayer = partiesManager.getPartyForPlayer(player);
                if (partyForPlayer != null) {
                    partyForPlayer.getPlayers().clear();
                    partiesManager.unloadParties();
                }
                break;
            }
            case "join": {
                if (args.length < 1)
                    return;

                UUID player = UUID.fromString(args[0]);
                if (Bukkit.getOfflinePlayer(player).isOnline())
                    partiesManager.loadPlayer(player);
                break;
            }
            case "kick":
            case "leave":
            case "lead": {
                if (args.length < 2)
                    return;

                UUID oldPlayer = UUID.fromString(args[0]);
                UUID newPlayer = UUID.fromString(args[1]);
                if (Bukkit.getOfflinePlayer(oldPlayer).isOnline())
                    partiesManager.loadPlayer(oldPlayer);
                if (Bukkit.getOfflinePlayer(newPlayer).isOnline())
                    partiesManager.loadPlayer(newPlayer);
                break;
            }
            case "disconnect": {
                if (args.length < 2)
                    return;

                UUID party = UUID.fromString(args[0]);
                partiesManager.loadParty(party);
                //clear cache if no player
                partiesManager.unloadParties();
                break;
            }
        }
    }
}