package org.example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Q-Learning для прямоугольного мира Вампуса.
 * Состояние: (x, y, направление, стрела, Вампус убит, золото взято).
 */
public class WumpusQLearning {

    private static final int FLAGS = 8; // 2 * 2 * 2
    private static final double OPTIMISTIC_INIT = 1000.0;

    private double[][] q; // [состояние][действие]
    private final int width;
    private final double alpha;
    private final double gamma;
    private double epsilon;
    private final Random random;

    public WumpusQLearning(int width, int height, double alpha, double gamma, double epsilon, long seed) {
        this.width = width;
        this.alpha = alpha;
        this.gamma = gamma;
        this.epsilon = epsilon;
        this.random = new Random(seed);

        int stateCount = width * height * Direction.values().length * FLAGS;
        this.q = new double[stateCount][HunterAction.values().length];

        for (double[] row : q) {
            Arrays.fill(row, OPTIMISTIC_INIT);
        }
    }

    public void setEpsilon(double epsilon) {
        this.epsilon = epsilon;
    }

    /** Кодирует состояние мира в индекс строки Q-таблицы. */
    public int stateOf(WumpusWorld w) {
        int flags = (w.isHasArrow() ? 1 : 0)
                | (!w.isWumpusAlive() ? 2 : 0)
                | (w.isGoldTaken() ? 4 : 0);
        int cell = w.getHunterY() * width + w.getHunterX();
        return (cell * Direction.values().length + w.getHunterDirection().ordinal()) * FLAGS + flags;
    }

    public HunterAction chooseAction(int state) {
        if (random.nextDouble() < epsilon) {
            HunterAction[] actions = HunterAction.values();
            return actions[random.nextInt(actions.length)];
        }
        return bestAction(state);
    }

    public HunterAction bestAction(int state) {
        double best = maxValue(state);
        List<HunterAction> bests = new ArrayList<>();
        for (HunterAction a : HunterAction.values()) {
            if (Math.abs(q[state][a.ordinal()] - best) < 1e-9) {
                bests.add(a);
            }
        }
        return bests.get(random.nextInt(bests.size()));
    }

    public double maxValue(int state) {
        double max = Double.NEGATIVE_INFINITY;
        for (double v : q[state]) {
            if (v > max) max = v;
        }
        return max;
    }

    public void learn(int state, HunterAction action, double reward, int newState, boolean done) {
        double old = q[state][action.ordinal()];
        double future = done ? 0 : maxValue(newState);
        q[state][action.ordinal()] = old + alpha * (reward + gamma * future - old);
    }

    public double[][] snapshot() {
        double[][] copy = new double[q.length][];
        for (int i = 0; i < q.length; i++) copy[i] = q[i].clone();
        return copy;
    }

    public void restore(double[][] copy) {
        for (int i = 0; i < q.length; i++) q[i] = copy[i].clone();
    }
}