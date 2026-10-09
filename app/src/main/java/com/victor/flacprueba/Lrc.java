package com.victor.flacprueba;

import java.util.*;
import java.util.regex.*;

/** Standard line-level LRC. A positive offset advances the displayed lyric. */
public final class Lrc {
    public static final class Line {
        public final long time;
        public final String text;
        Line(long time, String text) { this.time = time; this.text = text; }
    }
    public final List<Line> lines;
    private Lrc(List<Line> lines) { this.lines = Collections.unmodifiableList(lines); }
    public static Lrc empty() { return new Lrc(new ArrayList<>()); }
    public static Lrc parse(String input) {
        Pattern stamp = Pattern.compile("\\[(\\d{1,4}):([0-5]\\d)(?:[.:](\\d{1,3}))?\\]");
        Pattern offsetPattern = Pattern.compile("(?im)\\[offset:([+-]?\\d+)\\]");
        long offset = 0;
        Matcher offsets = offsetPattern.matcher(input);
        while (offsets.find()) {
            try { offset = Math.max(-86400000L, Math.min(86400000L, Long.parseLong(offsets.group(1)))); }
            catch (NumberFormatException ignored) { }
        }
        List<Line> result = new ArrayList<>();
        for (String raw : input.replace("\uFEFF", "").split("\\r?\\n")) {
            Matcher m = stamp.matcher(raw);
            List<Long> times = new ArrayList<>(); int end = 0;
            while (m.find()) {
                String fraction = m.group(3);
                long millis = fraction == null ? 0 : Long.parseLong((fraction + "00").substring(0, 3));
                times.add(Long.parseLong(m.group(1)) * 60000L + Long.parseLong(m.group(2)) * 1000L + millis - offset);
                end = m.end();
            }
            if (!times.isEmpty()) {
                String text = raw.substring(end).trim();
                for (Long time : times) result.add(new Line(time, text));
            }
        }
        result.sort(Comparator.comparingLong(line -> line.time));
        return new Lrc(result);
    }
    public int active(long position) {
        int lo = 0, hi = lines.size() - 1, found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).time <= position) { found = mid; lo = mid + 1; }
            else hi = mid - 1;
        }
        return found;
    }
}
