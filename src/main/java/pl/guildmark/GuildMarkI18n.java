package pl.guildmark;

public final class GuildMarkI18n {
    private GuildMarkI18n() {}

    public static String tr(String english, String polish) {
        return GuildMarkClient.SETTINGS != null && GuildMarkClient.SETTINGS.isPolish() ? polish : english;
    }
}
