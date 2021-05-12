package com.cavetale.survivalgames;

public enum State {
    IDLE,
    COUNTDOWN(20),
    LOOTING(45),
    FREE_FOR_ALL(60 * 10),
    COUNTDOWN_SUDDEN_DEATH,
    SUDDEN_DEATH,
    END(60);

    final long seconds;

    State() {
        this.seconds = 0L;
    }

    State(final long seconds) {
        this.seconds = seconds;
    }
}
