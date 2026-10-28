package org.example;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Мир Вампуса прямоугольного размера: width x height.
 * Все правила прежние: буфер у старта, ямы 0.2, золото рядом с Вампусом,
 * проверка достижимости, CLIMB только из [1,1].
 */
public class WumpusWorld {

    private final int width;
    private final int height;
    private final Random random;

    private Room[][] rooms; // [y][x]

    private int hunterX;
    private int hunterY;
    private Direction hunterDirection;

    private int wumpusX;
    private int wumpusY;
    private boolean wumpusAlive;

    private int goldX;
    private int goldY;
    private boolean goldTaken;

    private boolean hasArrow;

    private GameStatus status;

    public WumpusWorld(int requestedWidth, int requestedHeight) {
        this(requestedWidth, requestedHeight, System.currentTimeMillis());
    }

    public WumpusWorld(int requestedWidth, int requestedHeight, long seed) {
        this.width = Math.max(4, requestedWidth);
        this.height = Math.max(4, requestedHeight);
        this.random = new Random(seed);
        generate();
    }

    // =======================
    // ГЕНЕРАЦИЯ МИРА
    // =======================

    private void generate() {
        while (true) {
            initRooms();

            // Охотник в левом нижнем углу: (0, height - 1) = [1,1]
            hunterX = 0;
            hunterY = height - 1;
            hunterDirection = Direction.RIGHT;

            wumpusAlive = true;
            goldTaken = false;
            hasArrow = true;
            status = GameStatus.PLAYING;

            if (!placeWumpusAndGold()) {
                continue;
            }
            if (!placePits()) {
                continue;
            }
            if (isPathFromHunterToGold()) {
                return;
            }
        }
    }

    private void initRooms() {
        rooms = new Room[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                rooms[y][x] = new Room(x, y);
            }
        }
    }

    private boolean placeWumpusAndGold() {
        List<int[]> wumpusCandidates = new ArrayList<>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!inStartBuffer(x, y) && hasGoldCandidateForWumpus(x, y)) {
                    wumpusCandidates.add(new int[]{x, y});
                }
            }
        }

        if (wumpusCandidates.isEmpty()) {
            return false;
        }

        Collections.shuffle(wumpusCandidates, random);

        for (int[] c : wumpusCandidates) {
            int wx = c[0];
            int wy = c[1];

            List<int[]> goldCandidates = goldCandidatesForWumpus(wx, wy);
            if (goldCandidates.isEmpty()) {
                continue;
            }

            Collections.shuffle(goldCandidates, random);
            int[] g = goldCandidates.get(0);

            wumpusX = wx;
            wumpusY = wy;
            goldX = g[0];
            goldY = g[1];
            rooms[goldY][goldX].setGold(true);
            return true;
        }

        return false;
    }

    private boolean hasGoldCandidateForWumpus(int wx, int wy) {
        return !goldCandidatesForWumpus(wx, wy).isEmpty();
    }

    private List<int[]> goldCandidatesForWumpus(int wx, int wy) {
        List<int[]> candidates = new ArrayList<>();

        for (Direction direction : Direction.values()) {
            int nx = wx + direction.getDx();
            int ny = wy + direction.getDy();

            if (!isInside(nx, ny)) continue;
            if (inStartBuffer(nx, ny)) continue;

            candidates.add(new int[]{nx, ny});
        }

        return candidates;
    }

    private boolean placePits() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (inStartBuffer(x, y)) continue;
                if (x == wumpusX && y == wumpusY) continue;
                if (x == goldX && y == goldY) continue;

                if (random.nextDouble() < 0.2) {
                    rooms[y][x].setPit(true);
                }
            }
        }
        return true;
    }

    // =======================
    // ПРОВЕРКА ДОСТИЖИМОСТИ
    // =======================

    private boolean isPathFromHunterToGold() {
        boolean[][] visited = new boolean[height][width];

        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{hunterX, hunterY});
        visited[hunterY][hunterX] = true;

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int cx = current[0];
            int cy = current[1];

            if (cx == goldX && cy == goldY) {
                return true;
            }

            for (Direction direction : Direction.values()) {
                int nx = cx + direction.getDx();
                int ny = cy + direction.getDy();

                if (!isInside(nx, ny)) continue;
                if (visited[ny][nx]) continue;
                if (rooms[ny][nx].hasPit()) continue;
                if (wumpusAlive && nx == wumpusX && ny == wumpusY) continue;

                visited[ny][nx] = true;
                queue.add(new int[]{nx, ny});
            }
        }

        return false;
    }

    // =======================
    // ДЕЙСТВИЯ ОХОТНИКА
    // =======================

    public StepResult performAction(HunterAction action) {
        if (action == null || isGameOver()) {
            return new StepResult(EnumSet.noneOf(Percept.class), 0, isGameOver());
        }

        switch (action) {
            case FORWARD:
                return doForward();
            case TURN_LEFT:
                hunterDirection = hunterDirection.turnLeft();
                return new StepResult(currentPercepts(), -1, false);
            case TURN_RIGHT:
                hunterDirection = hunterDirection.turnRight();
                return new StepResult(currentPercepts(), -1, false);
            case SHOOT:
                return doShoot();
            case CLIMB:
                return doClimb();
            default:
                return new StepResult(EnumSet.noneOf(Percept.class), 0, false);
        }
    }

    private StepResult doForward() {
        int nx = hunterX + hunterDirection.getDx();
        int ny = hunterY + hunterDirection.getDy();
        double reward = -1;

        if (!isInside(nx, ny)) {
            return new StepResult(EnumSet.of(Percept.BUMP), reward, false);
        }

        hunterX = nx;
        hunterY = ny;

        if (rooms[ny][nx].hasPit()) {
            status = GameStatus.FELL_INTO_PIT;
            return new StepResult(EnumSet.noneOf(Percept.class), -1000, true);
        }

        if (wumpusAlive && nx == wumpusX && ny == wumpusY) {
            status = GameStatus.EATEN_BY_WUMPUS;
            return new StepResult(EnumSet.noneOf(Percept.class), -1000, true);
        }

        Set<Percept> percepts = currentPercepts();
        tryGrabGold();
        return new StepResult(percepts, reward, false);
    }

    private StepResult doShoot() {
        Set<Percept> percepts = currentPercepts();

        if (!hasArrow) {
            return new StepResult(percepts, -1, false);
        }

        hasArrow = false;
        double reward = -11; // -1 за действие и -10 за стрелу

        int cx = hunterX + hunterDirection.getDx();
        int cy = hunterY + hunterDirection.getDy();
        boolean scream = false;

        while (isInside(cx, cy)) {
            if (wumpusAlive && cx == wumpusX && cy == wumpusY) {
                wumpusAlive = false;
                scream = true;
                break;
            }
            cx += hunterDirection.getDx();
            cy += hunterDirection.getDy();
        }

        if (scream) {
            percepts.add(Percept.SCREAM);
        }

        return new StepResult(percepts, reward, false);
    }

    private StepResult doClimb() {
        // [1,1] во внутренних координатах: (0, height - 1)
        if (hunterX == 0 && hunterY == height - 1) {
            if (goldTaken) {
                status = GameStatus.WON;
                return new StepResult(EnumSet.noneOf(Percept.class), 1000, true);
            } else {
                status = GameStatus.EXITED_WITHOUT_GOLD;
                return new StepResult(EnumSet.noneOf(Percept.class), -1, true);
            }
        }
        return new StepResult(currentPercepts(), -1, false);
    }

    private void tryGrabGold() {
        if (!goldTaken && !wumpusAlive && hunterX == goldX && hunterY == goldY) {
            goldTaken = true;
        }
    }

    private Set<Percept> currentPercepts() {
        Set<Percept> percepts = EnumSet.noneOf(Percept.class);
        if (hasBreeze(hunterX, hunterY)) percepts.add(Percept.BREEZE);
        if (hasStench(hunterX, hunterY)) percepts.add(Percept.SMELL);
        if (hasGlitter(hunterX, hunterY)) percepts.add(Percept.GLITTER);
        return percepts;
    }

    // =======================
    // КООРДИНАТЫ И БУФЕР
    // =======================

    public boolean isInside(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    public boolean inStartBuffer(int x, int y) {
        if (!isInside(x, y)) {
            return false;
        }
        int startX = 0;
        int startY = height - 1;
        return Math.abs(x - startX) <= 1 && Math.abs(y - startY) <= 1;
    }

    // =======================
    // ДАТЧИКИ
    // =======================

    public boolean hasBreeze(int x, int y) {
        if (!isInside(x, y)) return false;
        for (Direction d : Direction.values()) {
            int nx = x + d.getDx();
            int ny = y + d.getDy();
            if (isInside(nx, ny) && rooms[ny][nx].hasPit()) return true;
        }
        return false;
    }

    public boolean hasStench(int x, int y) {
        if (!isInside(x, y)) return false;
        if (!wumpusAlive) return false;
        for (Direction d : Direction.values()) {
            int nx = x + d.getDx();
            int ny = y + d.getDy();
            if (nx == wumpusX && ny == wumpusY) return true;
        }
        return false;
    }

    public boolean hasGlitter(int x, int y) {
        if (!isInside(x, y)) return false;
        return !goldTaken && x == goldX && y == goldY;
    }

    // =======================
    // ВСПОМОГАТЕЛЬНЫЕ
    // =======================

    public boolean canMove(int x, int y, Direction direction) {
        if (!isInside(x, y) || direction == null) return false;
        return isInside(x + direction.getDx(), y + direction.getDy());
    }

    public boolean isPit(int x, int y) {
        return isInside(x, y) && rooms[y][x].hasPit();
    }

    public boolean isGold(int x, int y) {
        return isInside(x, y) && x == goldX && y == goldY && !goldTaken;
    }

    public boolean isWumpus(int x, int y) {
        return isInside(x, y) && wumpusAlive && x == wumpusX && y == wumpusY;
    }

    public boolean isHunter(int x, int y) {
        return x == hunterX && y == hunterY;
    }

    public Room getRoom(int x, int y) {
        if (!isInside(x, y)) {
            throw new IndexOutOfBoundsException("Комната вне мира: (" + x + ", " + y + ")");
        }
        return rooms[y][x];
    }

    public void reset() {
        hunterX = 0;
        hunterY = height - 1;
        hunterDirection = Direction.RIGHT;
        wumpusAlive = true;
        goldTaken = false;
        hasArrow = true;
        status = GameStatus.PLAYING;
    }

    // =======================
    // ПЕЧАТЬ
    // =======================

    public void printInfo() {
        System.out.println("=== Мир Вампуса ===");
        System.out.println("Размер мира: " + width + " x " + height);
        System.out.println("Охотник: (" + hunterX + ", " + hunterY + "), направление: " + hunterDirection);
        System.out.println("Вампус: (" + wumpusX + ", " + wumpusY + "), жив: " + wumpusAlive);
        System.out.println("Золото: (" + goldX + ", " + goldY + "), взято: " + goldTaken);
        System.out.println("Стрела есть: " + hasArrow);
        System.out.println("Состояние игры: " + status);
        System.out.println();
        printPits();
        System.out.println();
        printMap();
    }

    private void printPits() {
        System.out.print("Ямы:");
        boolean any = false;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (rooms[y][x].hasPit()) {
                    if (any) System.out.print(", ");
                    System.out.print("(" + x + ", " + y + ")");
                    any = true;
                }
            }
        }
        if (!any) System.out.print(" нет");
        System.out.println();
    }

    public void printMap() {
        System.out.println("Карта мира:");
        for (int y = 0; y < height; y++) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < width; x++) {
                char c;
                if (x == hunterX && y == hunterY) c = 'H';
                else if (wumpusAlive && x == wumpusX && y == wumpusY) c = 'W';
                else if (!goldTaken && x == goldX && y == goldY) c = 'G';
                else if (rooms[y][x].hasPit()) c = 'P';
                else c = '.';
                sb.append(c).append(' ');
            }
            System.out.println(sb);
        }
        System.out.println("Легенда: H - охотник, W - Вампус, G - золото, P - яма, . - пусто");
    }

    // =======================
    // ГЕТТЕРЫ
    // =======================

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getHunterX() {
        return hunterX;
    }

    public int getHunterY() {
        return hunterY;
    }

    public Direction getHunterDirection() {
        return hunterDirection;
    }

    public int getWumpusX() {
        return wumpusX;
    }

    public int getWumpusY() {
        return wumpusY;
    }

    public boolean isWumpusAlive() {
        return wumpusAlive;
    }

    public int getGoldX() {
        return goldX;
    }

    public int getGoldY() {
        return goldY;
    }

    public boolean isGoldTaken() {
        return goldTaken;
    }

    public boolean isHasArrow() {
        return hasArrow;
    }

    public GameStatus getStatus() {
        return status;
    }

    public boolean isGameOver() {
        return status != GameStatus.PLAYING;
    }
}