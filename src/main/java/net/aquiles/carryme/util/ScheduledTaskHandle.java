package net.aquiles.carryme.util;

@FunctionalInterface
public interface ScheduledTaskHandle {

    ScheduledTaskHandle NOOP = () -> {
    };

    void cancel();
}
