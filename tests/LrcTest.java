package com.victor.flacprueba;
public class LrcTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        Lrc x = Lrc.parse("\uFEFF[ar:Prueba]\n[00:10.50]Uno\n[00:12.005][00:20.00]Dos\n[offset:500]");
        check(x.lines.size() == 3);
        check(x.lines.get(0).time == 10000);
        check(x.lines.get(1).time == 11505);
        check(x.active(9999) == -1);
        check(x.active(10000) == 0);
        check(x.active(50000) == 2);
        check(x.active(11000) == 0); // backward seek
        check(Lrc.parse("[00:99.00]Invalid\nPlain").lines.isEmpty());
        check(Lrc.parse("[00:01.1]A").lines.get(0).time == 1100);
        check(Lrc.parse("[offset:-1000]\n[00:01]A").lines.get(0).time == 2000);
        check(Lrc.parse("[00:02]B\r\n[00:01]A").lines.get(0).text.equals("A"));
        check(Lrc.parse("[00:00]A\n[00:01]\n[00:02]B").active(1500) == 1);
        System.out.println("12 comprobaciones LRC superadas.");
    }
}
