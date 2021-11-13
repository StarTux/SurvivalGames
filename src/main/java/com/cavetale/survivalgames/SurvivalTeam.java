package com.cavetale.survivalgames;

import com.cavetale.core.event.player.PlayerTeamQuery;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public enum SurvivalTeam {
    RED("Red", NamedTextColor.RED),
    BLUE("Blue", NamedTextColor.BLUE),
    GREEN("Green", NamedTextColor.GREEN),
    AQUA("Aqua", NamedTextColor.AQUA),
    PURPLE("Purple", NamedTextColor.LIGHT_PURPLE),
    GOLD("Gold", NamedTextColor.GOLD);

    public final String key;
    public final String displayName;
    public final NamedTextColor color;
    public final Component component;
    public final PlayerTeamQuery.Team queryTeam;

    SurvivalTeam(final String displayName, final NamedTextColor color) {
        this.key = name().toLowerCase();
        this.displayName = displayName;
        this.color = color;
        this.component = Component.text(displayName, color);
        this.queryTeam = new PlayerTeamQuery.Team("survivalgames:" + key,
                                                  Component.text(displayName, color));
    }
}
