package king.kongguilds.Model;

import java.util.UUID;

public class Member {
    private UUID uuid;
    private String name;
    private String joinDate;
    private String rank;
    private int score;

    public Member(UUID uuid, String name, String joinDate, String rank, int score) {
        this.uuid = uuid;
        this.name = name;
        this.joinDate = joinDate;
        this.rank = rank;
        this.score = score;
    }

    // Getters
    public UUID getUUID() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public String getJoinDate() {
        return joinDate;
    }

    public String getRank() {
        return rank;
    }

    public int getScore() {
        return score;
    }

    // Setters
    public void setUUID(UUID uuid) {
        this.uuid = uuid;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setJoinDate(String joinDate) {
        this.joinDate = joinDate;
    }

    public void setRank(String rank) {
        this.rank = rank;
    }

    public void setScore(int score) {
        this.score = score;
    }
}
