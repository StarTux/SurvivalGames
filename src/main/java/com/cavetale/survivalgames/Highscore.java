package com.cavetale.survivalgames;

import com.winthier.playercache.PlayerCache;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class Highscore {
    public final UUID uuid;
    public final int score;
    protected int placement;

    public String name() {
        return PlayerCache.nameForUuid(uuid);
    }
}
