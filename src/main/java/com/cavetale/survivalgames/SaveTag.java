package com.cavetale.survivalgames;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public final class SaveTag {
    protected boolean event;
    protected boolean pause;
    protected Map<UUID, Integer> scores = new HashMap<>();

    public void addScore(UUID uuid, int amount) {
        scores.compute(uuid, (u, i) -> i != null ? i + amount : amount);
    }
}
