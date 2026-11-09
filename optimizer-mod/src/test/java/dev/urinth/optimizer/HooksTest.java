package dev.urinth.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A hook that keeps failing is switched off, so a fault can neither flood the log nor go on changing the game. */
class HooksTest {
    private static void fail(String hook, int times) {
        for (int i = 0; i < times; i++) Hooks.failed(hook, new IllegalStateException("test " + i));
    }

    @Test
    void aHookStaysUsableThroughAFewFailures() {
        Hooks.forgetFailures();
        try {
            fail("fog", Hooks.MAX_FAILURES - 1);
            assertTrue(Hooks.usable("fog"));
        } finally {
            Hooks.forgetFailures();
        }
    }

    @Test
    void aHookThatKeepsFailingIsSwitchedOff() {
        Hooks.forgetFailures();
        try {
            fail("fog", Hooks.MAX_FAILURES);
            assertFalse(Hooks.usable("fog"));
            fail("fog", 1000);
            assertFalse(Hooks.usable("fog"));
        } finally {
            Hooks.forgetFailures();
        }
    }

    @Test
    void otherHooksAreNotAffected() {
        Hooks.forgetFailures();
        try {
            fail("fog", Hooks.MAX_FAILURES);
            for (String other : new String[] {"culling", "particles", "uploads", "simulation distance", "controller", "server governor"}) {
                assertTrue(Hooks.usable(other), other);
            }
            // a name nobody knows is counted on its own, not against a real hook
            fail("something else", Hooks.MAX_FAILURES);
            assertFalse(Hooks.usable("something else"));
            assertTrue(Hooks.usable("culling"));
        } finally {
            Hooks.forgetFailures();
        }
    }

    @Test
    void theLastFailureIsRemembered() {
        Hooks.forgetFailures();
        try {
            assertEquals("", Hooks.failure());
            Hooks.failed("uploads", new IllegalStateException("boom"));
            assertTrue(Hooks.failure().startsWith("uploads: "), Hooks.failure());
            assertTrue(Hooks.failure().contains("boom"), Hooks.failure());
        } finally {
            Hooks.forgetFailures();
        }
    }

    @Test
    void withoutTheFogHookTheCullingIsOffToo() {
        Hooks.forgetFailures();
        SoftDistance.reset();
        try {
            SoftDistance.apply(7, 10, true);
            assertEquals(7, SoftDistance.cullLimit());
            fail("fog", Hooks.MAX_FAILURES);
            assertEquals(Integer.MAX_VALUE, SoftDistance.cullLimit());
        } finally {
            Hooks.forgetFailures();
            SoftDistance.reset();
        }
    }
}
