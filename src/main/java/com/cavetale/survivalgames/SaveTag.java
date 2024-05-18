package com.cavetale.survivalgames;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SaveTag {
    protected boolean event;
    protected boolean pause;
    protected boolean useTeams = true;
    protected Map<UUID, Integer> scores = new HashMap<>();

    public void addScore(UUID uuid, int amount) {
        scores.compute(uuid, (u, i) -> i != null ? i + amount : amount);
    }
}
