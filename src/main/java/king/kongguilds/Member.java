package king.kongguilds;

import java.util.UUID;

public class Member {
    private UUID uuid;
    private String name;
    private String joinDate;
    private String rank;
    private int score;

    public Member(UUID uuid,String name, String joinDate, String rank, int score) {
        this.uuid = uuid;
        this.name = name;
        this.joinDate = joinDate;
        this.rank = rank;
        this.score = score;
    }

    // Getters and Setters
    public UUID getUUID() {
        return uuid;
    }

    public String getName() {return name;}

    public String getJoinDate() { return joinDate; }

    public String getRank() {
        return rank;
    }

    public int getScore() {
        return score;
    }
}