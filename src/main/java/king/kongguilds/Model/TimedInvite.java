package king.kongguilds.Model;

public class TimedInvite {
    private final String guildName;
    private final long expireAt;

    public TimedInvite(String guildName, long expireDurationMillis) {
        this.guildName = guildName;
        this.expireAt = System.currentTimeMillis() + expireDurationMillis;
    }

    public String getGuildName() {
        return guildName;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expireAt;
    }
}