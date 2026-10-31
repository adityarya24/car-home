package dev.yuvaan.carhome;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// What a spoken line asks for. English and Hinglish: "navigate to India Gate",
// "India Gate le chalo", "play Arijit Singh", "Arijit ke gaane bajao", "next".
final class VoiceCommand {
    enum Kind { OPEN, NAVIGATE, PLAY_MUSIC, PLAY_VIDEO, NEXT, PREVIOUS, PAUSE, RESUME, SEARCH }

    final Kind kind;
    final String query;

    private VoiceCommand(Kind kind, String query) {
        this.kind = kind;
        this.query = query;
    }

    private static final Pattern NAV_EN = Pattern.compile(
            "^(?:navigate|navigation|directions?|route|take me|drive|go|chalo|le chalo)(?: me)?(?: to| towards| for)? (.+)$");
    private static final Pattern NAV_HI = Pattern.compile(
            "^(.+?) (?:le chalo|chalo|jana hai|ka rasta(?: batao| dikhao)?|ki direction|navigate karo)$");
    private static final Pattern OPEN = Pattern.compile("^(?:open|launch) (.+?)(?: app)?$|^(.+?)(?: app)? (?:kholo|khol do|open karo)$");
    private static final Pattern PLAY_EN = Pattern.compile("^(?:play|put on|start) (.+)$");
    private static final Pattern PLAY_HI = Pattern.compile("^(.+?) (?:bajao|chalao|lagao|sunao|play karo)$");
    private static final Pattern VIDEO = Pattern.compile(
            "^(?:(?:play|put on|start|show) (.+)|(.+?) (?:bajao|chalao|lagao|sunao|dikhao|play karo))$");
    private static final Pattern ON_VIDEO = Pattern.compile("^(.+?) (?:on|in|par|pe) youtube$");
    private static final Pattern ON_MUSIC = Pattern.compile("^(.+?) (?:on|in|par|pe) (?:youtube music|yt music|you tube music)$");

    static VoiceCommand parse(String spoken) {
        String s = spoken == null ? "" : spoken.toLowerCase(Locale.ROOT)
                .replaceAll("[.,!?]", " ").replaceAll("^(?:ok google|hey google|please) ", "")
                .replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) return new VoiceCommand(Kind.SEARCH, "");

        switch (s) {
            case "next": case "next song": case "skip": case "agla gana": case "agla gaana": case "next gaana":
                return new VoiceCommand(Kind.NEXT, null);
            case "previous": case "previous song": case "back": case "pichla gana": case "pichla gaana":
                return new VoiceCommand(Kind.PREVIOUS, null);
            case "pause": case "stop": case "stop music": case "pause music": case "band karo": case "gaana band karo": case "gana band karo":
                return new VoiceCommand(Kind.PAUSE, null);
            case "resume": case "play": case "continue": case "play music": case "gaana chalao": case "gana chalao":
                return new VoiceCommand(Kind.RESUME, null);
        }

        Matcher o = OPEN.matcher(s);
        if (o.matches()) return new VoiceCommand(Kind.OPEN, (o.group(1) != null ? o.group(1) : o.group(2)).trim());

        // "youtube pe peppa pig lagao", "peppa pig youtube pe chalao", "cartoon dikhao"
        boolean youtube = s.matches(".*\\byou ?tube\\b.*") && !s.matches(".*\\b(?:you ?tube|yt) music\\b.*");
        String bare = s.replaceAll("\\b(?:on|in|par|pe|me) you ?tube\\b|\\byou ?tube (?:on|par|pe|me)\\b|\\byou ?tube\\b", " ")
                .replaceAll("\\s+", " ").trim();
        Matcher v = VIDEO.matcher(bare);
        if (v.matches() && (youtube || bare.matches(".*\\b(?:cartoons?|videos?|rhymes?|poems?)\\b.*"))) {
            return new VoiceCommand(Kind.PLAY_VIDEO, (v.group(1) != null ? v.group(1) : v.group(2)).trim());
        }

        Matcher m;
        if ((m = NAV_EN.matcher(s)).matches() || (m = NAV_HI.matcher(s)).matches()) {
            return new VoiceCommand(Kind.NAVIGATE, m.group(1).trim());
        }
        if ((m = PLAY_EN.matcher(s)).matches() || (m = PLAY_HI.matcher(s)).matches()) {
            String q = m.group(1).trim();
            Matcher on;
            if ((on = ON_MUSIC.matcher(q)).matches()) return new VoiceCommand(Kind.PLAY_MUSIC, songs(on.group(1)));
            if ((on = ON_VIDEO.matcher(q)).matches()) return new VoiceCommand(Kind.PLAY_VIDEO, on.group(1).trim());
            return new VoiceCommand(Kind.PLAY_MUSIC, songs(q));
        }
        return new VoiceCommand(Kind.SEARCH, spoken.trim());
    }

    // "arijit ke gaane" -> "arijit", "arijit song" -> "arijit"; the rest is the search.
    private static String songs(String q) {
        String t = q.replaceAll(" (?:ke|ka|ki) (?:gaane|gane|gaana|gana|songs?)$", "")
                .replaceAll(" (?:songs?|gaane|gane|gaana|gana)$", "").trim();
        return t.isEmpty() ? q.trim() : t;
    }

    @Override
    public String toString() {
        return kind + (query == null ? "" : " [" + query + "]");
    }
}
