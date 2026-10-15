package net.kdt.pojavlaunch.home;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class HomeActivityLogTest {
    @Test
    public void appendKeepsNewestFirstAndCapsLength() {
        List<HomeActivityLog.Entry> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            list = HomeActivityLog.append(list, new HomeActivityLog.Entry(i, "play", "game " + i), 3);
        }
        assertEquals(3, list.size());
        assertEquals("game 4", list.get(0).text);
        assertEquals("game 2", list.get(2).text);
    }

    @Test
    public void serializeAndParseRoundTrip() {
        List<HomeActivityLog.Entry> list = new ArrayList<>();
        // Lists are newest-first, so the 2000 ms entry goes first.
        list.add(new HomeActivityLog.Entry(2000L, HomeActivityLog.TYPE_INSTALL, "Installed Vanilla 1.8.9"));
        list.add(new HomeActivityLog.Entry(1000L, HomeActivityLog.TYPE_PLAY, "Played 1.21.1"));
        List<HomeActivityLog.Entry> back = HomeActivityLog.parse(HomeActivityLog.serialize(list));
        assertEquals(2, back.size());
        assertEquals(2000L, back.get(0).timeMillis);
        assertEquals(HomeActivityLog.TYPE_INSTALL, back.get(0).type);
        assertEquals("Played 1.21.1", back.get(1).text);
    }

    @Test
    public void tabsAndNewlinesInTextDoNotBreakTheFormat() {
        List<HomeActivityLog.Entry> list = new ArrayList<>();
        list.add(new HomeActivityLog.Entry(5L, "play", "a\tb\nc"));
        List<HomeActivityLog.Entry> back = HomeActivityLog.parse(HomeActivityLog.serialize(list));
        assertEquals(1, back.size());
        assertEquals("a b c", back.get(0).text);
    }

    @Test
    public void damagedLinesAreSkipped() {
        List<HomeActivityLog.Entry> back = HomeActivityLog.parse("garbage\n12\tplay\tok\nnot-a-number\tplay\tbad");
        assertEquals(1, back.size());
        assertEquals("ok", back.get(0).text);
    }

    @Test
    public void ageBuckets() {
        long now = 10_000_000_000L;
        assertEquals(HomeActivityLog.AgeUnit.NOW, HomeActivityLog.age(now, now - 30_000L).unit);
        HomeActivityLog.Age minutes = HomeActivityLog.age(now, now - 5 * 60_000L);
        assertEquals(HomeActivityLog.AgeUnit.MINUTES, minutes.unit);
        assertEquals(5, minutes.count);
        HomeActivityLog.Age hours = HomeActivityLog.age(now, now - 2 * 3_600_000L);
        assertEquals(HomeActivityLog.AgeUnit.HOURS, hours.unit);
        assertEquals(2, hours.count);
        HomeActivityLog.Age days = HomeActivityLog.age(now, now - 3 * 86_400_000L);
        assertEquals(HomeActivityLog.AgeUnit.DAYS, days.unit);
        assertEquals(3, days.count);
        assertEquals(HomeActivityLog.AgeUnit.OLDER, HomeActivityLog.age(now, now - 30L * 86_400_000L).unit);
        assertEquals(HomeActivityLog.AgeUnit.NOW, HomeActivityLog.age(now, now + 1000L).unit);
    }
}
