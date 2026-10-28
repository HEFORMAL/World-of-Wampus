package org.example;

import java.util.Scanner;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Введите ширину мира (минимум 4): ");
        int w = 4;
        try {
            w = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            w = 4;
        }
        if (w < 4) {
            System.out.println("Ширина меньше 4. Использую 4.");
            w = 4;
        }

        System.out.print("Введите высоту мира (минимум 4): ");
        int h = 4;
        try {
            h = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            h = 4;
        }
        if (h < 4) {
            System.out.println("Высота меньше 4. Использую 4.");
            h = 4;
        }

        WumpusWorld world = new WumpusWorld(w, h);
        world.printInfo();
        printHelp();

        while (true) {
            if (world.isGameOver()) {
                printGameResult(world);
                break;
            }

            System.out.print("> ");
            String command = scanner.nextLine().trim().toLowerCase();

            if (command.isEmpty()) {
                continue;
            }

            HunterAction action = parseAction(command);
            if (action != null) {
                StepResult result = world.performAction(action);
                System.out.println("Ощущения: " + describe(result.percepts));
                System.out.println("Награда: " + result.reward);
                System.out.println("Позиция: (" + world.getHunterX() + ", " + world.getHunterY()
                        + "), направление: " + world.getHunterDirection()
                        + ", стрела: " + (world.isHasArrow() ? "есть" : "нет")
                        + ", золото: " + (world.isGoldTaken() ? "взято" : "нет"));
                continue;
            }

            switch (command) {
                case "m":
                case "карта":
                    world.printMap();
                    break;
                case "h":
                case "помощь":
                    printHelp();
                    break;
                case "q":
                case "выход":
                    System.out.println("Выход из игры.");
                    return;
                default:
                    System.out.println("Неизвестная команда.");
                    printHelp();
                    break;
            }
        }
    }

    private static HunterAction parseAction(String command) {
        switch (command) {
            case "w":
            case "вперёд":
            case "вперед":
                return HunterAction.FORWARD;

            case "a":
            case "влево":
                return HunterAction.TURN_LEFT;

            case "d":
            case "вправо":
                return HunterAction.TURN_RIGHT;

            case "s":
            case "выстрел":
                return HunterAction.SHOOT;

            case "c":
            case "climb":
            case "выбраться":
                return HunterAction.CLIMB;


            default:
                return null;
        }
    }

    private static String describe(Set<Percept> percepts) {
        if (percepts.isEmpty()) {
            return "нет";
        }

        StringBuilder sb = new StringBuilder();

        for (Percept percept : percepts) {
            if (sb.length() > 0) {
                sb.append(", ");
            }

            switch (percept) {
                case BREEZE:
                    sb.append("ветерок");
                    break;
                case SMELL:
                    sb.append("запах");
                    break;
                case GLITTER:
                    sb.append("блеск");
                    break;
                case BUMP:
                    sb.append("удар");
                    break;
                case SCREAM:
                    sb.append("крик Вампуса");
                    break;
            }
        }

        return sb.toString();
    }

    private static void printGameResult(WumpusWorld world) {
        switch (world.getStatus()) {
            case WON:
                System.out.println("ПОБЕДА! Золото взято, охотник выбрался!");
                break;
            case FELL_INTO_PIT:
                System.out.println("Охотник упал в яму и погиб. (-1000 очков)");
                break;
            case EATEN_BY_WUMPUS:
                System.out.println("Вампус съел охотника! (-1000 очков)");
                break;
            case EXITED_WITHOUT_GOLD:
                System.out.println("Охотник выбрался из пещеры без золота. Игра окончена.");
                break;
            default:
                break;
        }
        world.printMap();
    }

    private static void printHelp() {
        System.out.println("Команды:");
        System.out.println("  W / вперёд  - движение вперёд");
        System.out.println("  A / влево   - поворот на 90 градусов влево");
        System.out.println("  D / вправо  - поворот на 90 градусов вправо");
        System.out.println("  S / выстрел - выстрел вперёд (1 стрела)");
        System.out.println("  C / выбраться - выбраться из пещеры (только из [1,1])");
        System.out.println("  M / карта   - показать карту");
        System.out.println("  H / помощь  - справка");
        System.out.println("  Q / выход   - выход");
        System.out.println("  C / выбраться - выбраться из пещеры (только из [1,1])");
    }
}