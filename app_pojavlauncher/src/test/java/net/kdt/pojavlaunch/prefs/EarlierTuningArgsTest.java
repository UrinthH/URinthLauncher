package net.kdt.pojavlaunch.prefs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

/** Taking back the Java arguments that earlier builds of the launcher wrote into Settings. */
public class EarlierTuningArgsTest {
    /** Exactly what 3ad3d01 wrote into an empty Java arguments field. */
    private static final String SEEDED =
            "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=130 " +
            "-XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch " +
            "-XX:G1NewSizePercent=28 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M " +
            "-XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 " +
            "-XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 " +
            "-XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 " +
            "-XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1";

    /** What 5c427e7 appended on top of whatever was already there. */
    private static final String MERGED = "-XX:SoftRefLRUPolicyMSPerMB=0 -XX:+UseStringDeduplication";

    /** The seeded string with its last {@code count} flags cut off; AlwaysPreTouch stays in. */
    private static String seededMinusLast(int count) {
        String[] tokens = SEEDED.split(" ");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < tokens.length - count; i++) {
            if (i > 0) out.append(' ');
            out.append(tokens[i]);
        }
        return out.toString();
    }

    @Test
    public void emptyInputGivesAnEmptyString() {
        assertEquals("", EarlierTuningArgs.removeFrom(null));
        assertEquals("", EarlierTuningArgs.removeFrom(""));
        assertEquals("", EarlierTuningArgs.removeFrom("  \n\t "));
    }

    @Test
    public void removesTheWholeSeededString() {
        assertEquals("", EarlierTuningArgs.removeFrom(SEEDED));
    }

    @Test
    public void removesTheSeededStringWithTheMergedFlagsAppended() {
        assertEquals("", EarlierTuningArgs.removeFrom(SEEDED + " " + MERGED));
    }

    @Test
    public void alwaysPreTouchDoesNotSurviveTheSeededString() {
        assertFalse(EarlierTuningArgs.removeFrom(SEEDED + " -Dfoo=bar").contains("AlwaysPreTouch"));
    }

    @Test
    public void keepsArgumentsTheUserAddedAroundTheSeededString() {
        String before = "-Dfml.ignoreInvalidMinecraftCertificates=true " + SEEDED + " " + MERGED + " -Xss2M";
        assertEquals("-Dfml.ignoreInvalidMinecraftCertificates=true -Xss2M",
                EarlierTuningArgs.removeFrom(before));
    }

    @Test
    public void removesTheMergedFlagsFromArgumentsTheUserWrote() {
        // 5c427e7 appended these to whatever was there, including a user's own arguments.
        assertEquals("-Dfoo=bar -Xss1M", EarlierTuningArgs.removeFrom("-Dfoo=bar -Xss1M " + MERGED));
    }

    @Test
    public void leavesAFewMatchingFlagsInUserWrittenArgumentsAlone() {
        String theirs = "-XX:+UseG1GC -XX:MaxGCPauseMillis=130 -XX:+AlwaysPreTouch -Dx=y";
        assertEquals(theirs, EarlierTuningArgs.removeFrom(theirs));
    }

    @Test
    public void stillRecognisesTheSeededStringWhenUpToThreeFlagsWereTrimmed() {
        assertEquals("", EarlierTuningArgs.removeFrom(seededMinusLast(3)));
    }

    @Test
    public void treatsAHeavilyEditedSeededStringAsTheUsersOwn() {
        String edited = seededMinusLast(4);
        assertEquals(edited, EarlierTuningArgs.removeFrom(edited));
    }

    @Test
    public void toleratesNewlinesTabsAndRepeatedSpaces() {
        String messy = SEEDED.replace(" ", "  ").replace("-XX:+AlwaysPreTouch", "\n-XX:+AlwaysPreTouch\t")
                + "\n-Dkeep=me\n";
        assertEquals("-Dkeep=me", EarlierTuningArgs.removeFrom(messy));
    }

    @Test
    public void joinsWhatIsKeptWithSingleSpaces() {
        assertEquals("-Da=1 -Db=2", EarlierTuningArgs.removeFrom("  -Da=1 \n\n  -Db=2\t"));
    }

    @Test
    public void runningItAgainChangesNothing() {
        String once = EarlierTuningArgs.removeFrom(SEEDED + " " + MERGED + " -Dkeep=me");
        assertEquals("-Dkeep=me", once);
        assertEquals(once, EarlierTuningArgs.removeFrom(once));
    }
}
