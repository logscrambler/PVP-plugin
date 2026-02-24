package org.mcmod.pVPpl.game;

import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Team {
    private final String name;
    private UUID leader;
    private final Set<UUID> members = new HashSet<>();
    private final Set<UUID> invitedPlayers = new HashSet<>();

    public Team(String name, UUID leader) {
        this.name = name;
        this.leader = leader;
        this.members.add(leader);
    }

    public String getName() {
        return name;
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        if (members.contains(leader)) {
            this.leader = leader;
        }
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public void addMember(UUID player) {
        members.add(player);
        invitedPlayers.remove(player);
    }

    public void removeMember(UUID player) {
        members.remove(player);
    }

    public void invite(UUID player) {
        invitedPlayers.add(player);
    }

    public boolean isInvited(UUID player) {
        return invitedPlayers.contains(player);
    }
    
    public int getSize() {
        return members.size();
    }
}
