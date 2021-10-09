package com.cavetale.survivalgames;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class TeamScore {
    protected final SurvivalTeam team;
    protected int alivePlayers;
    protected int kills;
}
