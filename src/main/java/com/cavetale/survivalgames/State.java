package com.cavetale.survivalgames;

public enum State {
    IDLE,
    COUNTDOWN(30),
    LOOTING(45),
    FREE_FOR_ALL(60 * 8),
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
}
