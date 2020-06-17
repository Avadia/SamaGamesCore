package net.samagames.core.api.hydroangeas;

import net.samagames.core.APIPlugin;
import net.samagames.core.ApiImplementation;
import net.samagames.core.api.hydroangeas.connection.ConnectionManager;
import net.samagames.core.api.hydroangeas.packets.queues.QueueAddPlayerPacket;
import net.samagames.core.api.hydroangeas.packets.queues.QueueAttachPlayerPacket;
import net.samagames.core.api.hydroangeas.packets.queues.QueuePacket;
import net.samagames.core.api.hydroangeas.packets.queues.QueueRemovePlayerPacket;
import net.samagames.core.api.parties.Party;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
public class HydroangeasManager {
    private final ConnectionManager connectionManager;
    private final PacketReceiver packetReceiver;

    private final APIPlugin plugin;
    private final ApiImplementation api;

    public HydroangeasManager(APIPlugin plugin) {
        this.plugin = plugin;
        this.api = plugin.getAPI();
        this.connectionManager = new ConnectionManager(this);
        this.packetReceiver = new PacketReceiver(plugin);

        //TODO save all template data in redis
       /* plugin.getAPI().getPubSub().subscribe("hydroHubReceiver", (channel, packet) ->
        {
            try
            {
                this.connectionManager.getPacket(packet);
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        });*/

    }

    public void rejoinQueueToLeader(UUID leader, UUID player) {
        List<QPlayer> list = new ArrayList<>();
        list.add(new QPlayer(player, getPriority(player)));

        this.connectionManager.sendPacket(new QueueAttachPlayerPacket(new QPlayer(leader, getPriority(leader)), list));
    }

    public void removePlayerFromQueues(UUID uuid) {
        this.connectionManager.sendPacket(new QueueRemovePlayerPacket(new QPlayer(uuid, getPriority(uuid))));
    }

    public void addPlayerToQueue(UUID player, String game, String map) {
        QPlayer qPlayer = new QPlayer(player, getPriority(player));
        this.connectionManager.sendPacket(new QueueAddPlayerPacket(QueuePacket.TypeQueue.NAMED, game, map, qPlayer));
    }

    public void addPlayerToQueue(UUID player, String templateID) {
        QPlayer qPlayer = new QPlayer(player, getPriority(player));
        this.connectionManager.sendPacket(new QueueAddPlayerPacket(QueuePacket.TypeQueue.NAMEDID, templateID, qPlayer));
    }

    public void addPartyToQueue(UUID leader, UUID party, String game, String map) {
        Party party1 = api.getPartiesManager().getParty(party);

        List<QPlayer> players = party1.getPlayers().stream().map(player -> new QPlayer(player, getPriority(player))).collect(Collectors.toList());
        QPlayer qPlayer = new QPlayer(leader, getPriority(leader));

        addPlayerToQueue(leader, game, map);

        this.connectionManager.sendPacket(new QueueAttachPlayerPacket(qPlayer, players));
    }

    public void addPartyToQueue(UUID leader, UUID party, String templateID) {
        Party party1 = api.getPartiesManager().getParty(party);

        List<QPlayer> players = party1.getPlayers().stream().map(player -> new QPlayer(player, getPriority(player))).collect(Collectors.toList());
        QPlayer qPlayer = new QPlayer(leader, getPriority(leader));

        addPlayerToQueue(leader, templateID);

        this.connectionManager.sendPacket(new QueueAttachPlayerPacket(qPlayer, players));
    }

    public int getPriority(UUID uuid) {
        return plugin.getAPI().getPermissionsManager().getPlayer(uuid).getRank();
    }

    public PacketReceiver getPacketReceiver() {
        return this.packetReceiver;
    }
}
