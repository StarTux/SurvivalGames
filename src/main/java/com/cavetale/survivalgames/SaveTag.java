package com.cavetale.survivalgames;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SaveTag {
    protected boolean event;
    protected boolean useTeams = true;
    protected Map<UUID, Integer> kills = new HashMap<>();

    public void addKills(UUID uuid, int amount) {
        kills.compute(uuid, (u, i) -> i != null ? i + amount : amount);
    }
}
