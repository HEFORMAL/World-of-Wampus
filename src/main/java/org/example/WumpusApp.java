package org.example;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * JavaFX-визуализация прямоугольного мира Вампуса с Q-learning.
 * Пользователь задаёт ширину и высоту карты отдельно.
 */
public class WumpusApp extends Application {

    private static final int CELL_SIZE = 70;

    private static final String WUMPUS_ICON = "👹";
    private static final String GOLD_ICON = "💰";
    private static final String PIT_ICON = "🕳";
    private static final String START_ICON = "🏠";
    private static final String BREEZE_ICON = "💨";
    private static final String STENCH_ICON = "👃";
    private static final String GLITTER_ICON = "✨";

    private Stage stage;

    private WumpusWorld world;
    private WumpusQLearning brain;

    private Canvas canvas;
    private Label statusLabel;
    private Label perceptLabel;
    private Label rewardLabel;
    private TextField widthField;
    private TextField heightField;

    private Button newWorldBtn;
    private Button trainBtn;
    private Button runBtn;
    private Button stepBtn;
    private Button pauseBtn;

    private Timeline runTimeline;
    private boolean trained = false;
    private boolean training = false;
    private double lastReward = 0;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        createGame(4, 4);

        widthField = new TextField("4");
        widthField.setPrefWidth(50);
        widthField.setPrefHeight(30);
        widthField.setAlignment(Pos.CENTER);

        heightField = new TextField("4");
        heightField.setPrefWidth(50);
        heightField.setPrefHeight(30);
        heightField.setAlignment(Pos.CENTER);

        newWorldBtn = new Button("🎲 Новый мир");
        trainBtn = new Button("🧠 Обучить агента");
        runBtn = new Button("▶ Запустить");
        stepBtn = new Button("👣 Шаг");
        pauseBtn = new Button("⏸ Пауза");

        newWorldBtn.setOnAction(e -> handleNewWorld());
        trainBtn.setOnAction(e -> handleTrain());
        runBtn.setOnAction(e -> handleRun());
        stepBtn.setOnAction(e -> handleStep());
        pauseBtn.setOnAction(e -> handlePause());

        HBox topControls = new HBox(10,
                new Label("Ширина:"), widthField,
                new Label("Высота:"), heightField,
                newWorldBtn, trainBtn, runBtn, stepBtn, pauseBtn);
        topControls.setAlignment(Pos.CENTER);
        topControls.setPadding(new Insets(10));

        canvas = new Canvas(world.getWidth() * CELL_SIZE, world.getHeight() * CELL_SIZE);

        statusLabel = new Label("Создан новый мир. Нажми «Обучить агента».");
        statusLabel.setFont(Font.font("Segoe UI", 14));
        statusLabel.setTextFill(Color.DARKBLUE);

        perceptLabel = new Label("Восприятия: —");
        perceptLabel.setFont(Font.font("Segoe UI", 14));

        rewardLabel = new Label("Последний reward: 0");
        rewardLabel.setFont(Font.font("Segoe UI", 13));
        rewardLabel.setTextFill(Color.GRAY);

        VBox bottom = new VBox(5, statusLabel, perceptLabel, rewardLabel);
        bottom.setAlignment(Pos.CENTER);
        bottom.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setTop(topControls);
        root.setCenter(canvas);
        root.setBottom(bottom);

        Scene scene = new Scene(root,
                Math.max(world.getWidth() * CELL_SIZE + 40, 620),
                Math.max(world.getHeight() * CELL_SIZE + 200, 520));
        stage.setTitle("Мир Вампуса — Q-learning");
        stage.setScene(scene);
        stage.show();

        setButtons(true, true, false, false, false);
        draw();
    }

    // ================= КНОПКИ =================

    private void setButtons(boolean newWorld, boolean train, boolean run, boolean step, boolean pause) {
        newWorldBtn.setDisable(!newWorld);
        trainBtn.setDisable(!train);
        runBtn.setDisable(!run);
        stepBtn.setDisable(!step);
        pauseBtn.setDisable(!pause);
    }

    private void handleNewWorld() {
        if (training) {
            return;
        }
        stopRun();
        int w = readWidth();
        int h = readHeight();
        createGame(w, h);

        brain = null;
        trained = false;
        lastReward = 0;

        setButtons(true, true, false, false, false);
        statusLabel.setTextFill(Color.DARKBLUE);
        statusLabel.setText("Создан новый мир " + w + "×" + h + ". Обучи агента!");
        perceptLabel.setText("Восприятия: —");
        rewardLabel.setText("Последний reward: 0");

        resizeCanvas();
        stage.sizeToScene();
        draw();
    }

    private void handleTrain() {
        if (training) {
            return;
        }
        stopRun();
        training = true;
        setButtons(false, false, false, false, false);
        statusLabel.setTextFill(Color.DARKBLUE);
        statusLabel.setText("Обучение идёт... (несколько секунд)");

        Thread t = new Thread(() -> {
            trainAgent(20000, 300);
            Platform.runLater(() -> {
                training = false;
                trained = true;
                world.reset();
                lastReward = 0;
                setButtons(true, true, true, true, false);
                statusLabel.setText("Обучение завершено! «Запустить» — прогон, «Шаг» — вручную.");
                draw();
            });
        });
        t.setDaemon(true);
        t.start();
    }

    private void handleRun() {
        if (!trained || brain == null || training) {
            return;
        }
        if (world.isGameOver()) {
            world.reset();
            lastReward = 0;
        }
        setButtons(true, false, false, false, true);
        statusLabel.setTextFill(Color.DARKBLUE);
        statusLabel.setText("Агент идёт...");
        draw();

        runTimeline = new Timeline(new KeyFrame(Duration.millis(400), e -> runOneStep()));
        runTimeline.setCycleCount(Timeline.INDEFINITE);
        runTimeline.play();
    }

    private void handleStep() {
        if (!trained || brain == null || training) {
            return;
        }
        if (world.isGameOver()) {
            world.reset();
            lastReward = 0;
            draw();
        }
        runOneStep();
    }

    private void handlePause() {
        stopRun();
        setButtons(true, true, trained, trained, false);
        statusLabel.setTextFill(Color.DARKBLUE);
        statusLabel.setText("Пауза. Можно продолжить, сделать шаг или создать новый мир.");
    }

    // ================= ПРОГОН =================

    private void runOneStep() {
        if (brain == null) {
            return;
        }
        if (world.isGameOver()) {
            stopRun();
            showGameOver();
            setButtons(true, true, true, true, false);
            return;
        }

        int state = brain.stateOf(world);
        HunterAction action = brain.bestAction(state);
        StepResult result = world.performAction(action);
        lastReward = result.reward;

        draw();
        updateStatus(action);

        if (result.done) {
            stopRun();
            showGameOver();
            setButtons(true, true, true, true, false);
        }
    }

    private void showGameOver() {
        switch (world.getStatus()) {
            case WON:
                statusLabel.setText("🏆 ПОБЕДА! Охотник взял золото и выбрался!");
                statusLabel.setTextFill(Color.DARKGREEN);
                break;
            case FELL_INTO_PIT:
                statusLabel.setText("💀 Охотник упал в яму! Нажми «Запустить» для новой попытки.");
                statusLabel.setTextFill(Color.RED);
                break;
            case EATEN_BY_WUMPUS:
                statusLabel.setText("💀 Вампус съел охотника! Нажми «Запустить» для новой попытки.");
                statusLabel.setTextFill(Color.RED);
                break;
            case EXITED_WITHOUT_GOLD:
                statusLabel.setText("🚪 Охотник выбрался без золота. Не победа.");
                statusLabel.setTextFill(Color.DARKORANGE);
                break;
            default:
                break;
        }
    }

    private void stopRun() {
        if (runTimeline != null) {
            runTimeline.stop();
            runTimeline = null;
        }
    }

    private void updateStatus(HunterAction action) {
        perceptLabel.setText("Восприятия: " + describePercepts(world.getHunterX(), world.getHunterY()));
        rewardLabel.setText(String.format("Последний reward: %+.1f   |   Действие: %s",
                lastReward, action));
        statusLabel.setTextFill(Color.DARKBLUE);
        statusLabel.setText(String.format(
                "Охотник: (%d,%d) %s   |   Стрела: %s   |   Золото: %s   |   Вампус: %s",
                world.getHunterX(), world.getHunterY(),
                world.getHunterDirection(),
                world.isHasArrow() ? "есть" : "нет",
                world.isGoldTaken() ? "взято" : "на месте",
                world.isWumpusAlive() ? "жив" : "мёртв"));
    }

    // ================= ОБУЧЕНИЕ =================

    private void trainAgent(int episodes, int maxSteps) {
        brain = new WumpusQLearning(world.getWidth(), world.getHeight(),
                0.1, 0.99, 0.2, System.currentTimeMillis());

        double epsilon = 0.2;
        double[][] bestTable = null;
        double bestEval = Double.NEGATIVE_INFINITY;

        for (int episode = 1; episode <= episodes; episode++) {
            world.reset();
            brain.setEpsilon(epsilon);
            epsilon = Math.max(0.01, epsilon * 0.9995);

            boolean done = false;
            int steps = 0;
            while (!done && steps < maxSteps) {
                int s = brain.stateOf(world);
                HunterAction a = brain.chooseAction(s);
                StepResult r = world.performAction(a);
                int s2 = brain.stateOf(world);
                brain.learn(s, a, r.reward, s2, r.done);
                done = r.done;
                steps++;
            }

            if (episode % 500 == 0) {
                double eval = evaluateGreedy(maxSteps);
                if (eval > bestEval) {
                    bestEval = eval;
                    bestTable = brain.snapshot();
                }
            }
        }

        if (bestTable != null) {
            brain.restore(bestTable);
        }
    }

    private double evaluateGreedy(int maxSteps) {
        world.reset();
        double total = 0;
        int steps = 0;
        while (!world.isGameOver() && steps < maxSteps) {
            int s = brain.stateOf(world);
            HunterAction a = brain.bestAction(s);
            StepResult r = world.performAction(a);
            total += r.reward;
            steps++;
        }
        return total;
    }

    // ================= ВСПОМОГАТЕЛЬНЫЕ =================

    private void createGame(int w, int h) {
        world = new WumpusWorld(w, h);
    }

    private void resizeCanvas() {
        canvas.setWidth(world.getWidth() * CELL_SIZE);
        canvas.setHeight(world.getHeight() * CELL_SIZE);
    }

    private int readWidth() {
        try {
            return Math.max(4, Integer.parseInt(widthField.getText().trim()));
        } catch (NumberFormatException e) {
            return 4;
        }
    }

    private int readHeight() {
        try {
            return Math.max(4, Integer.parseInt(heightField.getText().trim()));
        } catch (NumberFormatException e) {
            return 4;
        }
    }

    private String describePercepts(int x, int y) {
        StringBuilder sb = new StringBuilder();
        if (world.hasStench(x, y)) sb.append("👃 ");
        if (world.hasBreeze(x, y)) sb.append("💨 ");
        if (world.hasGlitter(x, y)) sb.append("✨ ");
        return sb.length() == 0 ? "—" : sb.toString().trim();
    }

    // ================= ОТРИСОВКА =================

    private void draw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        int w = world.getWidth();
        int h = world.getHeight();

        g.setFill(Color.web("#F5F0E6"));
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                drawCell(g, x, y);
            }
        }

        g.setStroke(Color.web("#BBBBBB"));
        g.setLineWidth(1);
        for (int i = 0; i <= w; i++) {
            g.strokeLine(i * CELL_SIZE, 0, i * CELL_SIZE, h * CELL_SIZE);
        }
        for (int i = 0; i <= h; i++) {
            g.strokeLine(0, i * CELL_SIZE, w * CELL_SIZE, i * CELL_SIZE);
        }

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                drawCellContent(g, x, y);
            }
        }

        drawHunter(g);
    }

    private void drawCell(GraphicsContext g, int x, int y) {
        double px = x * CELL_SIZE;
        double py = y * CELL_SIZE;

        if (x == 0 && y == world.getHeight() - 1) {
            g.setFill(Color.LIGHTGREEN);
            g.fillRect(px, py, CELL_SIZE, CELL_SIZE);
        }
        if (world.isPit(x, y)) {
            g.setFill(Color.web("#FFCACA"));
            g.fillRect(px, py, CELL_SIZE, CELL_SIZE);
        }
        if (world.isWumpus(x, y)) {
            g.setFill(Color.web("#FF8A8A"));
            g.fillRect(px, py, CELL_SIZE, CELL_SIZE);
        }
        if (world.isGold(x, y)) {
            g.setFill(Color.web("#FFE97A"));
            g.fillRect(px, py, CELL_SIZE, CELL_SIZE);
        }
    }

    private void drawCellContent(GraphicsContext g, int x, int y) {
        double cx = x * CELL_SIZE + CELL_SIZE / 2.0;
        double cy = y * CELL_SIZE + CELL_SIZE / 2.0;

        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);

        if (x == 0 && y == world.getHeight() - 1) {
            g.setFill(Color.BLACK);
            g.setFont(Font.font(14));
            g.fillText(START_ICON, cx, cy + 22);
            g.setFont(Font.font(10));
            g.fillText("[1,1]", cx, cy - 25);
        }
        if (world.isPit(x, y)) {
            g.setFill(Color.BLACK);
            g.setFont(Font.font(30));
            g.fillText(PIT_ICON, cx, cy);
        }
        if (world.isWumpus(x, y)) {
            g.setFill(Color.BLACK);
            g.setFont(Font.font(36));
            g.fillText(WUMPUS_ICON, cx, cy);
        }
        if (world.isGold(x, y)) {
            g.setFill(Color.BLACK);
            g.setFont(Font.font(30));
            g.fillText(GOLD_ICON, cx, cy);
        }

        double indent = 12;
        double iconX = x * CELL_SIZE + CELL_SIZE - indent;
        double iconY = y * CELL_SIZE + indent;
        g.setFont(Font.font(12));

        if (world.hasStench(x, y)) {
            g.fillText(STENCH_ICON, iconX, iconY);
        }
        if (world.hasBreeze(x, y)) {
            g.fillText(BREEZE_ICON, iconX - 16, iconY);
        }
        if (world.hasGlitter(x, y)) {
            g.fillText(GLITTER_ICON, iconX - 32, iconY);
        }
    }

    private void drawHunter(GraphicsContext g) {
        int x = world.getHunterX();
        int y = world.getHunterY();
        Direction dir = world.getHunterDirection();

        double cx = x * CELL_SIZE + CELL_SIZE / 2.0;
        double cy = y * CELL_SIZE + CELL_SIZE / 2.0;

        g.setFill(Color.web("#4A90E2", 0.4));
        g.fillOval(cx - 22, cy - 22, 44, 44);

        String arrow;
        switch (dir) {
            case UP: arrow = "↑"; break;
            case RIGHT: arrow = "→"; break;
            case DOWN: arrow = "↓"; break;
            case LEFT: arrow = "←"; break;
            default: arrow = "?";
        }

        g.setFill(Color.DARKBLUE);
        g.setFont(Font.font("Arial", FontWeight.BOLD, 32));
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        g.fillText(arrow, cx, cy);
    }

    public static void main(String[] args) {
        launch(args);
    }
}