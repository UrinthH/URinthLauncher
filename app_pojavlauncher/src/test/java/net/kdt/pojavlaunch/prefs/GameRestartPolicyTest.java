package net.kdt.pojavlaunch.prefs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class GameRestartPolicyTest {
    private static LaunchFingerprint fingerprint(int allocation) {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("allocation", allocation);
        return LaunchFingerprint.of(prefs);
    }

    @Test
    public void noGameProcessMeansPlayJustStartsOne() {
        GameRestartPolicy.Record record = new GameRestartPolicy.Record(4242, fingerprint(1024));
        assertEquals(GameRestartPolicy.Verdict.NO_GAME,
                GameRestartPolicy.decide(Collections.<Integer>emptyList(), record, fingerprint(2048)));
        assertEquals(GameRestartPolicy.Verdict.NO_GAME, GameRestartPolicy.decide(null, record, fingerprint(2048)));
        assertEquals(GameRestartPolicy.Verdict.NO_GAME,
                GameRestartPolicy.decide(Collections.<Integer>emptyList(), null, null));
    }

    @Test
    public void aRunningGameStartedWithTheSameSettingsIsLeftAlone() {
        GameRestartPolicy.Record record = new GameRestartPolicy.Record(4242, fingerprint(2048));
        assertEquals(GameRestartPolicy.Verdict.SAME,
                GameRestartPolicy.decide(Arrays.asList(4242), record, fingerprint(2048)));
    }

    @Test
    public void aRunningGameStartedWithOtherSettingsIsOutOfDate() {
        GameRestartPolicy.Record record = new GameRestartPolicy.Record(4242, fingerprint(1024));
        assertEquals(GameRestartPolicy.Verdict.CHANGED,
                GameRestartPolicy.decide(Arrays.asList(4242), record, fingerprint(2048)));
    }

    @Test
    public void aRecordOfAnotherProcessIsNotTheRunningGame() {
        GameRestartPolicy.Record record = new GameRestartPolicy.Record(4242, fingerprint(1024));
        assertEquals(GameRestartPolicy.Verdict.UNKNOWN,
                GameRestartPolicy.decide(Arrays.asList(5151), record, fingerprint(2048)));
        assertEquals(GameRestartPolicy.Verdict.UNKNOWN,
                GameRestartPolicy.decide(Arrays.asList(5151), null, fingerprint(2048)));
        assertEquals(GameRestartPolicy.Verdict.UNKNOWN,
                GameRestartPolicy.decide(Arrays.asList(4242), record, null));
    }

    @Test
    public void theRecordSurvivesTheFile() {
        GameRestartPolicy.Record record = new GameRestartPolicy.Record(4242, fingerprint(1536));
        GameRestartPolicy.Record read = GameRestartPolicy.decode(GameRestartPolicy.encode(record));
        assertNotNull(read);
        assertEquals(4242, read.pid);
        assertEquals(Collections.<String>emptyList(), read.fingerprint.differences(record.fingerprint));
    }

    @Test
    public void textThatIsNotARecordIsIgnored() {
        assertNull(GameRestartPolicy.decode(null));
        assertNull(GameRestartPolicy.decode(""));
        assertNull(GameRestartPolicy.decode("pid=4242"));
        assertNull(GameRestartPolicy.decode("pid=4242\n"));
        assertNull(GameRestartPolicy.decode("pid=abc\nallocation=1024\n"));
        assertNull(GameRestartPolicy.decode("pid=0\nallocation=1024\n"));
        assertNull(GameRestartPolicy.decode("pid=-5\nallocation=1024\n"));
        assertNull(GameRestartPolicy.decode("allocation=1024\npid=4242\n"));
    }
}
