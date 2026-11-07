package dev.urinth.find;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FindConfigTest {
    @Test
    void operatorsByDefault() {
        assertEquals(2, FindConfig.DEFAULT_LEVEL);
        assertEquals(2, FindConfig.permissionLevel(null));
        assertEquals(2, FindConfig.permissionLevel(""));
        assertEquals(2, FindConfig.permissionLevel("# only a comment\n"));
    }

    @Test
    void theFileTheModWritesIsReadBackAsTheDefault() {
        assertEquals(FindConfig.DEFAULT_LEVEL, FindConfig.permissionLevel(FindConfig.DEFAULT_TEXT));
    }

    @Test
    void anyLevelFromZeroToFour() {
        assertEquals(0, FindConfig.permissionLevel("permission-level=0"));
        assertEquals(1, FindConfig.permissionLevel("permission-level=1"));
        assertEquals(3, FindConfig.permissionLevel("permission-level = 3 "));
        assertEquals(4, FindConfig.permissionLevel("# who\npermission-level=4\n"));
    }

    @Test
    void outOfRangeLevelsAreClamped() {
        assertEquals(4, FindConfig.permissionLevel("permission-level=9"));
        assertEquals(0, FindConfig.permissionLevel("permission-level=-3"));
    }

    @Test
    void unreadableValuesGiveTheDefault() {
        assertEquals(2, FindConfig.permissionLevel("permission-level=everyone"));
        assertEquals(2, FindConfig.permissionLevel("permission-level="));
        assertEquals(2, FindConfig.permissionLevel("permission-level=\\u00zz"));
        assertEquals(2, FindConfig.permissionLevel("something-else=0"));
    }
}
