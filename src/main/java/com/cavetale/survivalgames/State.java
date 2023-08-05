package com.cavetale.survivalgames;

public enum State {
    IDLE,
    COUNTDOWN(60),
    LOOTING(45),
    FREE_FOR_ALL(60 * 10),
    COUNTDOWN_SUDDEN_DEATH(10),
    SUDDEN_DEATH,
    END(60);

    final long seconds;

    State() {
        this.seconds = 1L;
    }

    State(final long seconds) {
        this.seconds = seconds;
    }

    public boolean canUseItem() {
        return this == LOOTING
            || this == FREE_FOR_ALL
            || this == SUDDEN_DEATH;
    }
}
