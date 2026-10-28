package org.example;

import java.util.Set;

/**
 * Консольный тренер агента в мире Вампуса (Q-learning).
 * Обучение идёт на одной карте (фиксированный seed).
 */
public class WumpusTrainer {

    public static void main(String[] args) {
        int width = 4;
        int height = 4;
        long seed = 12345L;

        WumpusWorld world = new WumpusWorld(width, height, seed);
        world.printInfo();
        System.out.println();

        WumpusQLearning brain = new WumpusQLearning(width, height, 0.1, 0.99, 0.2, 1L);
        int episodes = 20000;
        int maxSteps = 200;
        int evalEvery = 500;
        double epsilon = 0.2;
        double epsilonDecay = 0.9995;
        double epsilonMin = 0.01;

        double[][] bestTable = null;
        double bestEval = Double.NEGATIVE_INFINITY;

        int winsWindow = 0;
        double rewardWindow = 0;

        for (int episode = 1; episode <= episodes; episode++) {
            world.reset();
            brain.setEpsilon(epsilon);
            epsilon = Math.max(epsilonMin, epsilon * epsilonDecay);

            boolean done = false;
            int steps = 0;
            double episodeReward = 0;

            while (!done && steps < maxSteps) {
                int s = brain.stateOf(world);
                HunterAction action = brain.chooseAction(s);
                StepResult r = world.performAction(action);
                int s2 = brain.stateOf(world);

                brain.learn(s, action, r.reward, s2, r.done);

                episodeReward += r.reward;
                done = r.done;
                steps++;
            }

            if (world.getStatus() == GameStatus.WON) winsWindow++;
            rewardWindow += episodeReward;

            if (episode % evalEvery == 0) {
                // Оценка жадной политики (epsilon = 0), её и сохраняем как "лучшую"
                double eval = evaluate(brain, world, maxSteps);
                if (eval > bestEval) {
                    bestEval = eval;
                    bestTable = brain.snapshot();
                }
                System.out.printf("Эпизод %5d | побед за %d: %3d | средняя награда: %7.1f | жадная политика: %7.1f | epsilon: %.3f%n",
                        episode, evalEvery, winsWindow, rewardWindow / evalEvery, eval, epsilon);
                winsWindow = 0;
                rewardWindow = 0;
            }
        }

        if (bestTable != null) {
            brain.restore(bestTable);
            System.out.println("\nИспользуем лучшую таблицу (награда жадной политики: " + bestEval + ")");
        }

        System.out.println("\n===== Проверка обученного агента =====");
        runPolicy(brain, world, maxSteps);
    }

    /** Тихий прогон жадной политики; возвращает суммарную награду. */
    private static double evaluate(WumpusQLearning brain, WumpusWorld world, int maxSteps) {
        world.reset();
        double total = 0;
        int steps = 0;
        while (!world.isGameOver() && steps < maxSteps) {
            HunterAction a = brain.bestAction(brain.stateOf(world));
            total += world.performAction(a).reward;
            steps++;
        }
        return total;
    }

    private static void runPolicy(WumpusQLearning brain, WumpusWorld world, int maxSteps) {
        world.reset();
        int steps = 0;
        double total = 0;

        while (!world.isGameOver() && steps < maxSteps) {
            int x = world.getHunterX();
            int y = world.getHunterY();
            Direction dir = world.getHunterDirection();

            HunterAction action = brain.bestAction(brain.stateOf(world));
            StepResult r = world.performAction(action);

            System.out.printf("Шаг %2d: (%d,%d) %-5s -> %-10s | награда: %+7.1f | %s%n",
                    steps + 1, x, y, dir, action, r.reward, describe(r.percepts));
            total += r.reward;
            steps++;
        }

        System.out.println("\nИтог:");
        System.out.println("  Шагов: " + steps);
        System.out.println("  Статус: " + world.getStatus());
        System.out.println("  Суммарная награда: " + total + "\n");
        world.printMap();
    }

    private static String describe(Set<Percept> percepts) {
        return percepts.isEmpty() ? "" : percepts.toString();
    }
}