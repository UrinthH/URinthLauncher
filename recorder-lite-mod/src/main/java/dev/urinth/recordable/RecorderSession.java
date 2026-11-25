package dev.urinth.recordable;

import dev.urinth.recordable.enc.Frame;
import dev.urinth.recordable.enc.FramePool;
import dev.urinth.recordable.enc.H264Out;
import dev.urinth.recordable.enc.MjpegOut;
import dev.urinth.recordable.enc.VideoOut;
import dev.urinth.recordable.gl.GpuCapture;
import dev.urinth.recordable.nat.Native;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * One recording, from the key press to the finished file.
 *
 * <p>Two threads work together. The render thread (the game's main thread) calls {@link #frame} once per rendered frame: that is where
 * the picture is started on the graphics chip and where finished pictures are collected, which together take a fraction of a
 * millisecond. The encoder thread takes the collected pictures, gives them to the video encoder and writes the file. The render thread
 * never waits for the encoder: when the encoder is behind, pictures are skipped, and the {@link Governor} lowers the capture rate.
 */
public final class RecorderSession {
    private static final Logger LOG = LoggerFactory.getLogger("Record-able Lite");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final long MB = 1024L * 1024L;
    /** The Motion-JPEG backup costs the processor a lot, so it never records faster than this. */
    private static final int MJPEG_FPS = 15;
    /** With only a software encoder, keep the picture small so the game keeps its speed. */
    private static final int SOFTWARE_HEIGHT = 360;
    private static final int SOFTWARE_FPS = 20;
    private static final int[] STANDARD_HEIGHTS = {1080, 720, 480, 360};
    /** Two reports closer together than this are one frame seen twice (a hook that was registered two times). */
    private static final long MIN_FRAME_GAP_NS = 1_000_000L;
    /** In fast mode the first picture waits this long, so the game's normal frame time can be measured first. */
    private static final long PREROLL_NS = 300_000_000L;
    /** Fast mode (a picture on most frames) starts when a capture interval is shorter than this many game frames, and ends above the other. */
    private static final double FAST_ON = 1.7;
    private static final double FAST_OFF = 1.9;
    /** Setting up the video encoder happens on the render thread, so it gets a time limit. */
    private static final long HARDWARE_BUDGET_NS = 6_000_000_000L;
    /** The buffers for waiting pictures may use this much memory at most. */
    private static final long POOL_BUDGET_BYTES = 40L * MB;

    public enum State { RECORDING, PAUSED, STOPPING, DONE }

    private final Consumer<String> say;
    private final Governor governor;
    private final GpuCapture capture;
    private final FramePool pool;
    private final VideoOut out;
    private final Path file;
    private final String summary;
    private final long minFreeBytes;
    private final long maxFileBytes;
    private final Path marker;
    private final Report report;
    private final Thread worker;
    private final AtomicLong encoderDrops = new AtomicLong();

    private volatile State state = State.RECORDING;
    private volatile boolean stopFlag;
    private volatile boolean finished;
    private volatile String autoStop;
    private volatile String stopReason = "";
    private volatile int reportedFps;

    // render thread only
    private final long startNs = System.nanoTime();
    private long pausedTotalNs;
    private long pauseStartNs;
    private long lastFrameNs;
    private long nextCaptureNs;
    private double frameNs = 11_000_000;
    private int sinceCapture;
    private boolean everCaptured;
    private boolean fast;
    private long captureCostNs;
    private long seenDrops;
    private long lastRateNoticeNs;

    private RecorderSession(Consumer<String> say, Governor governor, GpuCapture capture, FramePool pool, VideoOut out, Path file,
                            String summary, long minFreeBytes, long maxFileBytes, Path marker, Report report) {
        this.say = say;
        this.governor = governor;
        this.capture = capture;
        this.pool = pool;
        this.out = out;
        this.file = file;
        this.summary = summary;
        this.minFreeBytes = minFreeBytes;
        this.maxFileBytes = maxFileBytes;
        this.marker = marker;
        this.report = report;
        this.reportedFps = governor.fps();
        this.nextCaptureNs = startNs;
        this.worker = new Thread(this::encoderLoop, "Record-able Lite encoder");
        this.worker.setDaemon(true);
        this.worker.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
    }

    // ---- start ----------------------------------------------------------------------------------------------------------------

    /** What the builders produced. */
    private static final class Built {
        GpuCapture capture;
        VideoOut out;
        Path file;
        int maxFps;
        /** Everything about the recording, for the log and the report. */
        String text;
        /** A short version for the chat. */
        String brief;
        /** Something the player must be told right away (a fallback was used), or null. */
        String warning;
    }

    /**
     * Sets everything up and starts recording. Must run on the render thread (it creates graphics objects).
     *
     * @param say shows a message to the player; may be called from any thread
     * @return the running session, or null if recording is not possible (the player was told why)
     */
    public static RecorderSession start(Settings s, Path gameDir, int srcW, int srcH, Consumer<String> say) {
        Report report = new Report(Report.fileFor(gameDir));
        report.addAll(Report.environment(gameDir));
        report.add("settings: " + s.toJson().replaceAll("\\s+", " ").trim());
        report.add("game picture: " + srcW + "x" + srcH);
        List<String> notes = new ArrayList<>();
        Path dir = OutputFolder.choose(s.saveToGallery, gameDir, OutputFolder.galleryCandidates(), notes);
        if (dir == null) {
            report.addAll(notes);
            report.add("result: no folder to save to");
            report.write();
            say.accept("Cannot create a folder for the recordings: " + String.join("; ", notes));
            return null;
        }
        boolean shared = OutputFolder.isShared(dir);
        long free = usableSpace(dir);
        report.add("output folder: " + dir + (shared ? " (phone storage; the gallery and video editors see it)"
                : " (the game's own folder; the gallery and video editors cannot see it)") + ", free " + (free >= 0 ? free / MB + " MB" : "unknown"));
        if (free >= 0 && free < s.minFreeMb * MB) {
            report.addAll(notes);
            report.add("result: not enough free storage");
            report.write();
            say.accept("Not enough free storage to record (" + (free / MB) + " MB left).");
            return null;
        }
        if (srcW < 16 || srcH < 16) {
            report.addAll(notes);
            report.add("result: the game picture is too small");
            report.write();
            say.accept("The game picture is too small to record.");
            return null;
        }
        String stamp = LocalDateTime.now().format(STAMP);
        Path marker = Settings.configDir(gameDir).resolve("encoder-starting.marker");
        Built b = null;
        String hardwareProblem = null;
        if (s.hardwareEncoder) {
            touch(marker);                                    // stays behind only if the native encoder start brings the game down
            b = buildHardware(s, dir, stamp, srcW, srcH, gameDir, () -> delete(marker), notes);
            if (b == null) {
                delete(marker);
                hardwareProblem = notes.isEmpty() ? "no reason given" : notes.get(notes.size() - 1);
            }
        } else {
            hardwareProblem = "it is switched off in the settings (F9)";
        }
        if (b == null) {
            b = buildBackup(s, dir, stamp, srcW, srcH, notes);
            if (b != null) {
                b.warning = "The phone's video encoder could not be used (" + shorten(hardwareProblem, 140) + "), so this is a backup .avi file. "
                        + "It plays on a computer, but not in the gallery or CapCut. Report: " + report.file();
            }
        }
        report.addAll(notes);
        if (b == null) {
            report.add("result: recording is not possible");
            report.write();
            say.accept("Recording is not possible on this device: " + shorten(String.join("; ", notes), 200) + ". Report: " + report.file());
            LOG.warn("Cannot record: {}", notes);
            return null;
        }
        report.add("recording: " + b.file + " - " + b.text);
        FramePool pool = new FramePool(poolFrames(b.maxFps, b.capture.frameBytes()), b.capture.frameBytes());
        Governor governor = new Governor(b.maxFps);
        RecorderSession session = new RecorderSession(say, governor, b.capture, pool, b.out, b.file, b.text, s.minFreeMb * MB,
                s.maxFileMb * MB, marker, report);
        report.write();
        for (String n : notes) {
            LOG.info("{}", n);
        }
        session.worker.start();
        LOG.info("Recording to {}: {}", b.file, b.text);
        say.accept("Recording started: " + b.brief + ". Saving to " + dir + (shared ? "" : " (not visible to the gallery)") + ". Press - to stop.");
        if (b.warning != null) {
            say.accept(b.warning);
        }
        return session;
    }

    private static Built buildHardware(Settings s, Path dir, String stamp, int srcW, int srcH, Path gameDir, Runnable firstOutput,
                                       List<String> notes) {
        if (!Native.init(nativeFolders(gameDir))) {
            notes.add("the phone's video encoder cannot be reached: " + Native.status());
            return null;
        }
        notes.add("native calls: " + Native.status());
        int ring = ringFor(s.fps());
        long deadline = System.nanoTime() + HARDWARE_BUDGET_NS;
        boolean softwareOnly = false;
        for (int wanted : heightsFrom(s.height())) {
            if (System.nanoTime() > deadline) {
                notes.add("stopped trying video sizes after " + HARDWARE_BUDGET_NS / 1_000_000_000L + " s");
                break;
            }
            int[] size = GpuCapture.outputSize(srcW, srcH, wanted);
            GpuCapture cap = new GpuCapture(size[0], size[1], GpuCapture.Mode.NV12, ring);
            cap.setSwapChroma(s.swapChroma);
            if (!cap.init()) {
                notes.add("screen capture failed: " + cap.problem());
                cap.close();
                return null;                                  // the same graphics problem would hit every size
            }
            Path file = dir.resolve(stamp + ".mp4");
            try {
                H264Out out = H264Out.open(file, size[0], size[1], s.fps(), s.kbps() * 1000, cap.mode() == GpuCapture.Mode.RGBA,
                        s.swapChroma, firstOutput);
                Built b = new Built();
                b.capture = cap;
                b.out = out;
                b.file = file;
                b.maxFps = s.fps();
                b.text = size[0] + "x" + size[1] + " at up to " + b.maxFps + " fps, " + out.describe() + "; " + cap.details();
                b.brief = size[0] + "x" + size[1] + " at up to " + b.maxFps + " fps, " + s.kbps() / 1000.0 + " Mbit/s";
                notes.add("video encoder search: " + out.attempts());
                if (size[1] != GpuCapture.outputSize(srcW, srcH, s.height())[1]) {
                    notes.add("the video encoder refused the full size, so the video is " + size[0] + "x" + size[1]);
                    b.warning = "The phone's video encoder refused " + s.height() + "p, so this video is " + size[1] + "p.";
                }
                return b;
            } catch (H264Out.SoftwareEncoderException e) {
                notes.add(e.getMessage());
                cap.close();
                softwareOnly = true;
                break;
            } catch (IOException | RuntimeException | LinkageError e) {
                notes.add("encoder at " + size[0] + "x" + size[1] + ": " + e.getMessage());
                cap.close();
                if (e instanceof LinkageError) {
                    return null;
                }
            }
        }
        if (softwareOnly) {
            // There is no hardware encoder. A software one works, but it takes processor time from the game, so keep it small.
            int[] size = GpuCapture.outputSize(srcW, srcH, Math.min(SOFTWARE_HEIGHT, s.height()));
            GpuCapture cap = new GpuCapture(size[0], size[1], GpuCapture.Mode.NV12, 3);
            cap.setSwapChroma(s.swapChroma);
            if (cap.init()) {
                Path file = dir.resolve(stamp + ".mp4");
                try {
                    int fps = Math.min(SOFTWARE_FPS, s.fps());
                    H264Out out = H264Out.openAllowingSoftware(file, size[0], size[1], fps, Math.min(s.kbps(), 2500) * 1000,
                            cap.mode() == GpuCapture.Mode.RGBA, s.swapChroma, firstOutput);
                    Built b = new Built();
                    b.capture = cap;
                    b.out = out;
                    b.file = file;
                    b.maxFps = fps;
                    b.text = size[0] + "x" + size[1] + " at up to " + fps + " fps, " + out.describe() + "; " + cap.details();
                    b.brief = size[0] + "x" + size[1] + " at up to " + fps + " fps (software encoder)";
                    b.warning = "This phone has no hardware video encoder, so the slower software one is used at " + size[1] + "p and "
                            + fps + " fps to keep the game fast. The file is a normal .mp4.";
                    notes.add("video encoder search: " + out.attempts());
                    return b;
                } catch (IOException | RuntimeException | LinkageError e) {
                    notes.add("software encoder: " + e.getMessage());
                }
            } else {
                notes.add("screen capture failed: " + cap.problem());
            }
            cap.close();
        }
        return null;
    }

    private static Built buildBackup(Settings s, Path dir, String stamp, int srcW, int srcH, List<String> notes) {
        int[] size = GpuCapture.outputSize(srcW, srcH, Math.min(480, s.height()));
        GpuCapture cap = new GpuCapture(size[0], size[1], GpuCapture.Mode.RGBA, 3);
        if (!cap.init()) {
            notes.add("screen capture failed: " + cap.problem());
            cap.close();
            return null;
        }
        Path file = dir.resolve(stamp + ".avi");
        try {
            MjpegOut out = MjpegOut.open(file, size[0], size[1], MJPEG_FPS);
            Built b = new Built();
            b.capture = cap;
            b.out = out;
            b.file = file;
            b.maxFps = MJPEG_FPS;
            b.text = size[0] + "x" + size[1] + " at up to " + MJPEG_FPS + " fps in the backup format (.avi), because the hardware encoder "
                    + "is not available";
            b.brief = size[0] + "x" + size[1] + " at up to " + MJPEG_FPS + " fps (backup .avi)";
            return b;
        } catch (IOException | RuntimeException | LinkageError e) {
            notes.add("backup format: " + e);
            cap.close();
            return null;
        }
    }

    /** Sizes to try, the wanted one first, then the standard ones below it. */
    static int[] heightsFrom(int requested) {
        List<Integer> list = new ArrayList<>();
        list.add(requested);
        for (int h : STANDARD_HEIGHTS) {
            if (h < requested) {
                list.add(h);
            }
        }
        int[] r = new int[list.size()];
        for (int i = 0; i < r.length; i++) {
            r[i] = list.get(i);
        }
        return r;
    }

    /** More pictures can be in flight at once when most frames are captured. */
    static int ringFor(int maxFps) {
        return maxFps > 45 ? 6 : 3;
    }

    /** Buffers for pictures that wait for the encoder: a few more at high rates, but never more than the memory budget allows. */
    static int poolFrames(int maxFps, int frameBytes) {
        int wanted = maxFps > 45 ? 10 : 6;
        int affordable = (int) Math.max(1, POOL_BUDGET_BYTES / Math.max(1, frameBytes));
        return Math.max(3, Math.min(wanted, affordable));
    }

    /** Places where the native helper may be unpacked; some phones forbid running code from some folders, so there are several. */
    static Path[] nativeFolders(Path gameDir) {
        List<Path> folders = new ArrayList<>();
        addFolder(folders, System.getProperty("java.io.tmpdir"), "recordable-lite");
        addFolder(folders, System.getProperty("user.home"), ".recordable-lite");
        String javaHome = System.getProperty("java.home");
        if (javaHome != null && !javaHome.isEmpty()) {
            folders.add(Path.of(javaHome, "lib", "recordable-lite"));
        }
        String pkg = androidPackage();
        if (pkg != null) {
            folders.add(Path.of("/data/data", pkg, "cache", "recordable-lite"));
            folders.add(Path.of("/data/data", pkg, "files", "recordable-lite"));
        }
        folders.add(Settings.configDir(gameDir).resolve("native"));
        return folders.toArray(new Path[0]);
    }

    private static void addFolder(List<Path> list, String base, String name) {
        if (base != null && !base.isEmpty()) {
            list.add(Path.of(base).resolve(name));
        }
    }

    /** The package name of the Android app this Java runs in (the name of the process), or null if that is not a phone. */
    static String androidPackage() {
        try {
            byte[] raw = Files.readAllBytes(Path.of("/proc/self/cmdline"));
            int end = 0;
            while (end < raw.length && raw[end] != 0) {
                end++;
            }
            String name = new String(raw, 0, end, StandardCharsets.UTF_8).trim();
            return name.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+") ? name : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    static String shorten(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, Math.max(0, max - 3)) + "...";
    }

    // ---- per frame (render thread) --------------------------------------------------------------------------------------------

    public State state() {
        return state;
    }

    public boolean isActive() {
        State st = state;
        return st == State.RECORDING || st == State.PAUSED;
    }

    /** True when the file is closed and every resource is released. */
    public boolean isDone() {
        return state == State.DONE && finished;
    }

    public Path file() {
        return file;
    }

    /** Capture rate right now, after the frame-rate protection had its say. */
    public int currentFps() {
        return governor.fps();
    }

    public String description() {
        return summary;
    }

    /** Length of the video so far, not counting pauses. */
    public long elapsedMs() {
        long now = System.nanoTime();
        long paused = pausedTotalNs + (state == State.PAUSED ? now - pauseStartNs : 0);
        return Math.max(0, (now - startNs - paused) / 1_000_000L);
    }

    public void pause() {
        if (state == State.RECORDING) {
            pauseStartNs = System.nanoTime();
            state = State.PAUSED;
            say.accept("Recording paused. Press = to continue.");
        }
    }

    public void resume() {
        if (state == State.PAUSED) {
            long now = System.nanoTime();
            pausedTotalNs += now - pauseStartNs;
            nextCaptureNs = now;
            lastFrameNs = 0;
            sinceCapture = 0;
            everCaptured = false;
            state = State.RECORDING;
            say.accept("Recording continues.");
        }
    }

    /**
     * Called once for every rendered frame while a recording exists, at the moment the finished picture of the game is in the main render
     * target. Any problem inside stops the recording instead of reaching the game.
     */
    public void frame(int srcFbo, int srcW, int srcH) {
        State st = state;
        if (st == State.STOPPING || st == State.DONE) {
            return;
        }
        try {
            frameInner(st, srcFbo, srcW, srcH);
        } catch (Throwable t) {
            LOG.error("The recorder hit a problem and stops (the game is not affected)", t);
            stop("internal error: " + t);
        }
    }

    private void frameInner(State st, int srcFbo, int srcW, int srcH) {
        long t0 = System.nanoTime();
        String why = autoStop;
        if (why == null && governor.stopRequested()) {
            why = governor.reason();
        }
        if (why == null && finished) {
            why = "the encoder stopped";
        }
        if (why != null) {
            stop(why);
            return;
        }
        capture.poll(pool);                                   // hand finished pictures to the encoder thread
        if (st == State.PAUSED) {
            return;
        }
        if (lastFrameNs != 0 && t0 - lastFrameNs < MIN_FRAME_GAP_NS) {
            return;                                           // the same frame reported twice; it must count once
        }
        long interval = lastFrameNs == 0 ? 0 : t0 - lastFrameNs;
        lastFrameNs = t0;
        if (everCaptured) {
            sinceCapture++;
        }
        updateFastMode();
        if (interval > 0) {
            governor.onFrame(interval, everCaptured ? sinceCapture : 0, captureCostNs, fast);
            frameNs += 0.05 * (Math.min(interval, 100_000_000L) - frameNs);
        }
        long drops = pool.dropped() + encoderDrops.get() + capture.skippedCount();
        if (drops > seenDrops) {
            long news = Math.min(3, drops - seenDrops);
            seenDrops = drops;
            for (long i = 0; i < news; i++) {
                governor.onBacklog();
            }
        }
        announceRateChange(t0);

        long intervalNs = 1_000_000_000L / Math.max(1, governor.fps());
        boolean due = t0 + (long) (frameNs / 2) >= nextCaptureNs;
        // Two frames apart at the closest, three while the governor wants a frame that follows no capture so it can measure the cost.
        // In fast mode (a picture on most frames) there are no such frames; the game's speed before the first picture is the yardstick,
        // so the first picture waits until that was measured.
        int spacing = fast ? 1 : (governor.needsCleanFrame() ? 3 : 2);
        boolean measured = !fast || (t0 - startNs >= PREROLL_NS && governor.hasBaseline());
        if (due && measured && (!everCaptured || sinceCapture >= spacing)) {
            long pts = Math.max(0, (t0 - startNs - pausedTotalNs) / 1000L);
            if (capture.issue(srcFbo, srcW, srcH, pts)) {
                everCaptured = true;
                sinceCapture = 0;
            }
            nextCaptureNs += intervalNs;
            if (nextCaptureNs < t0 + intervalNs / 4) {
                nextCaptureNs = t0 + intervalNs;               // far behind (a stall): do not try to catch up with a burst
            }
            captureCostNs = System.nanoTime() - t0;
        }
    }

    /** Fast mode: the capture interval is shorter than about two game frames, so pictures are taken on most frames. */
    private void updateFastMode() {
        double ratio = (1e9 / Math.max(1, governor.fps())) / Math.max(1.0, frameNs);
        if (fast) {
            if (ratio > FAST_OFF) {
                fast = false;
            }
        } else if (ratio < FAST_ON) {
            fast = true;
        }
    }

    private void announceRateChange(long now) {
        int fps = governor.fps();
        if (fps != reportedFps && now - lastRateNoticeNs > 5_000_000_000L) {
            reportedFps = fps;
            lastRateNoticeNs = now;
            say.accept("Recorder: " + governor.reason() + " (to protect your game's frame rate).");
            LOG.info("Capture rate is now {} fps: {}", fps, governor.reason());
            report.add("rate change at " + clock(elapsedMs()) + ": " + governor.reason());
        }
    }

    // ---- stop -----------------------------------------------------------------------------------------------------------------

    /**
     * Stops capturing at once and finishes the file in the background. Render thread only. Safe to call more than once.
     *
     * @param reason empty for a normal stop, otherwise why the recorder stopped by itself
     */
    public void stop(String reason) {
        if (state == State.STOPPING || state == State.DONE) {
            return;
        }
        if (state == State.PAUSED) {
            pausedTotalNs += System.nanoTime() - pauseStartNs;
        }
        state = State.STOPPING;
        stopReason = reason == null ? "" : reason;
        try {
            long deadline = System.nanoTime() + 80_000_000L;   // pictures still on their way get a moment to arrive
            while (capture.inFlight() > 0 && System.nanoTime() < deadline) {
                capture.poll(pool);
                if (capture.inFlight() > 0) {
                    Thread.sleep(2);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            LOG.warn("Could not collect the last pictures", e);
        }
        try {
            String stats = String.format("%d started, %d delivered, %d skipped; %d dropped for lack of buffers, %d refused by the encoder; "
                            + "ended at %d fps (%s mode)", capture.issuedCount(), capture.deliveredCount(), capture.skippedCount(),
                    pool.dropped(), encoderDrops.get(), governor.fps(), fast ? "fast" : "normal");
            LOG.info("Capture finished: {}", stats);
            report.add("capture: " + stats);
            capture.close();
        } catch (RuntimeException e) {
            LOG.warn("Could not free the capture objects", e);
        }
        stopFlag = true;
        state = State.DONE;
    }

    /** Waits (a few seconds at most) until a stopped session has closed its file. */
    public void awaitDone(long maxWaitMs) {
        try {
            worker.join(maxWaitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Stops and waits (a few seconds at most) until the file is closed. For the moment the game shuts down. */
    public void stopAndWait(String reason, long maxWaitMs) {
        stop(reason);
        try {
            worker.join(maxWaitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- encoder thread -------------------------------------------------------------------------------------------------------

    private void encoderLoop() {
        long lastDisk = System.nanoTime();
        boolean ok = false;
        String error = null;
        try {
            while (true) {
                Frame f = pool.take(15);
                if (f != null) {
                    try {
                        if (!out.accept(f)) {
                            encoderDrops.incrementAndGet();
                        }
                    } finally {
                        pool.release(f);
                    }
                    continue;
                }
                if (stopFlag && pool.queued() == 0) {
                    break;
                }
                out.idle();
                long now = System.nanoTime();
                if (now - lastDisk > 2_000_000_000L) {
                    lastDisk = now;
                    long free = usableSpace(file.getParent());
                    if (free >= 0 && free < minFreeBytes) {
                        autoStop = "the storage is almost full (" + (free / MB) + " MB left)";
                    } else if (maxFileBytes > 0 && out.bytes() >= maxFileBytes) {
                        autoStop = "the file reached the size limit of " + (maxFileBytes / MB) + " MB";
                    }
                }
            }
            out.finish();
            ok = true;
        } catch (Throwable t) {
            error = t.toString();
            LOG.error("The encoder thread failed", t);
            out.abort();
        } finally {
            finished = true;
            delete(marker);                                    // a leftover marker only makes the next start careful
        }
        finishReport(ok, error);
    }

    private void finishReport(boolean ok, String error) {
        long size = 0;
        try {
            size = Files.size(file);
        } catch (IOException ignored) {
            // the file may not exist if nothing was written
        }
        long ms = out.millis();
        String reason = stopReason;
        String result;
        if (ok && size > 0) {
            String text = "Saved: " + file + " (" + clock(ms) + ", " + Math.max(1, size / MB) + " MB)";
            if (!reason.isEmpty()) {
                text += ". The recorder stopped because " + reason + ".";
            }
            say.accept(text);
            LOG.info("{} [{}]", text, out.describe());
            result = text;
        } else if (ok) {
            result = "Nothing was recorded" + (reason.isEmpty() ? "." : " (" + reason + ").");
            say.accept(result);
        } else {
            result = "The recording failed: " + (error == null ? "unknown error" : error)
                    + (size > 0 ? ". What was already written is saved in " + file : "");
            say.accept(result);
        }
        report.add("result: " + result);
        report.write();
    }

    // ---- small helpers --------------------------------------------------------------------------------------------------------

    public static String clock(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
    }

    private static long usableSpace(Path p) {
        try {
            return Files.getFileStore(p).getUsableSpace();
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    private static void touch(Path marker) {
        try {
            Files.createDirectories(marker.getParent());
            Files.write(marker, new byte[]{'1'});
        } catch (IOException | RuntimeException ignored) {
            // without the marker the crash protection is just missing
        }
    }

    private static void delete(Path marker) {
        try {
            Files.deleteIfExists(marker);
        } catch (IOException | RuntimeException ignored) {
            // see touch()
        }
    }
}
