package org.example;

public class Room {
    private final int x;
    private final int y;

    private boolean pit;
    private boolean gold;

    public Room(int x, int y) {
        this.x = x;
        this.y = y;
        this.pit = false;
        this.gold = false;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public boolean hasPit() {
        return pit;
    }

    public void setPit(boolean pit) {
        this.pit = pit;
    }

    public boolean hasGold() {
        return gold;
    }

    public void setGold(boolean gold) {
        this.gold = gold;
    }

    @Override
    public String toString() {
        return "Room{x=" + x + ", y=" + y + "}";
    }
}