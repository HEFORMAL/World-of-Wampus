package org.example;

import java.util.Set;

/**
 * Результат выполнения одного действия охотником.
 * Содержит ощущения, полученную награду и флаг завершения эпизода.
 */
public class StepResult {
    public final Set<Percept> percepts;
    public final double reward;
    public final boolean done;

    public StepResult(Set<Percept> percepts, double reward, boolean done) {
        this.percepts = percepts;
        this.reward = reward;
        this.done = done;
    }
}