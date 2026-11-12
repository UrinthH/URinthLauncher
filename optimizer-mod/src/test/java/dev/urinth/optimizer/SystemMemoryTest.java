package dev.urinth.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** What the mod reads out of /proc: the parsing is tested on text, the reading on whatever system the tests run on. */
class SystemMemoryTest {
    private static final String MEMINFO = "MemTotal:        2832396 kB\n"
        + "MemFree:           95336 kB\n"
        + "MemAvailable:     712480 kB\n"
        + "Buffers:            2848 kB\n"
        + "Cached:           842316 kB\n"
        + "SwapCached:        14024 kB\n";

    @Test
    void readsAKeyOfMeminfoInKilobytes() {
        assertEquals(712480L, SystemMemory.kilobytes(MEMINFO, "MemAvailable"));
        assertEquals(2832396L, SystemMemory.kilobytes(MEMINFO, "MemTotal"));
        assertEquals(14024L, SystemMemory.kilobytes(MEMINFO, "SwapCached"));
    }

    @Test
    void aKeyIsMatchedWholeNotByItsBeginning() {
        assertEquals(-1L, SystemMemory.kilobytes(MEMINFO, "Mem"));
        assertEquals(-1L, SystemMemory.kilobytes(MEMINFO, "Swap"));
        assertEquals(842316L, SystemMemory.kilobytes(MEMINFO, "Cached"));
    }

    @Test
    void aMissingOrBrokenAnswerIsMinusOne() {
        assertEquals(-1L, SystemMemory.kilobytes(null, "MemAvailable"));
        assertEquals(-1L, SystemMemory.kilobytes("", "MemAvailable"));
        assertEquals(-1L, SystemMemory.kilobytes("MemFree: 5 kB\n", "MemAvailable"));
        assertEquals(-1L, SystemMemory.kilobytes("MemAvailable: lots kB\n", "MemAvailable"));
        assertEquals(-1L, SystemMemory.kilobytes("MemAvailable:\n", "MemAvailable"));
        assertEquals(-1L, SystemMemory.kilobytes("MemAvailable: -7 kB\n", "MemAvailable"));
    }

    @Test
    void theLastLineWithoutANewlineIsRead() {
        assertEquals(42L, SystemMemory.kilobytes("MemFree: 1 kB\nMemAvailable: 42 kB", "MemAvailable"));
        assertEquals(42L, SystemMemory.kilobytes("MemAvailable: 42", "MemAvailable"));
    }

    @Test
    void readsTheMajorFaultsOfAStatLine() {
        String stat = "4242 (java) S 1 4242 4242 0 -1 4194560 52113 1130 146 2 31033 4112 0 0 20 0 96 0 123456 4294967296 123456";
        assertEquals(146L, SystemMemory.majorFaults(stat));
    }

    @Test
    void aProgramNameWithSpacesAndBracketsDoesNotShiftTheFields() {
        String stat = "7 (my (odd) name) R 1 7 7 0 -1 4194560 11 22 33 44 55";
        assertEquals(33L, SystemMemory.majorFaults(stat));
    }

    @Test
    void aBrokenStatLineIsMinusOne() {
        assertEquals(-1L, SystemMemory.majorFaults(null));
        assertEquals(-1L, SystemMemory.majorFaults(""));
        assertEquals(-1L, SystemMemory.majorFaults("no brackets here"));
        assertEquals(-1L, SystemMemory.majorFaults("7 (java) S 1 7"));
        assertEquals(-1L, SystemMemory.majorFaults("7 (java) S 1 7 7 0 -1 4194560 11 22 xx 44"));
        assertEquals(-1L, SystemMemory.majorFaults("7 (java)"));
    }

    @Test
    void theRealFilesAreReadWhereTheSystemHasThem() {
        if (Files.isReadable(Path.of("/proc/meminfo"))) {
            long available = SystemMemory.availableBytes();
            assertTrue(available == -1L || available > 0L, "available " + available);
        }
        if (Files.isReadable(Path.of("/proc/self/stat"))) {
            assertTrue(SystemMemory.majorFaults() >= -1L);
        }
    }
}
