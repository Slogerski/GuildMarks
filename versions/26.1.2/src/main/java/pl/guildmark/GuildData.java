package pl.guildmark;

import java.util.ArrayList;
import java.util.List;

public final class GuildData {
    public int formatVersion = 2;
    public List<Guild> guilds = new ArrayList<>();

    public static final class Guild {
        public String name = "";
        public int color = 0x9B5CFF;
        public List<String> players = new ArrayList<>();
        public String markFile = "";
        public String markPath = "";
        public String markUrl = "";
        public boolean showOnChest = true;
        public boolean showOnCape = true;
        public boolean showOnShield = false;
        public boolean showOnElytra = false;
        public boolean showOnHelmet = false;
        public String relation = "foreign";

        public Guild() {}
        public Guild(String name) { this.name = name; }
    }
}
