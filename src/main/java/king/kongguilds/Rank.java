package king.kongguilds;

public class Rank {
    private int level;
    private String name;

    public Rank(int level, String name) {
        this.level = level;
        this.name = name;
    }

    // Getters
    public int getLevel() {
        return level;
    }

    public String getName() {
        return name;
    }
}