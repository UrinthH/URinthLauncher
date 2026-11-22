package dev.urinth.recordable;

import dev.urinth.recordable.enc.Frame;
import dev.urinth.recordable.enc.FramePool;
import dev.urinth.recordable.enc.H264Out;
import dev.urinth.recordable.enc.MjpegOut;
import dev.urinth.recordable.enc.VideoOut;
import dev.urinth.recordable.gl.GpuCapture;
import dev.urinth.recordable.nat.Shim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
    private static final int POOL_FRAMES = 6;
    private static final int RING = 3;
    /** The Motion-JPEG backup costs the processor a lot, so it never records faster than this. */
    private static final int MJPEG_FPS = 15;
    /** With only a software encoder, keep the picture small so the game keeps its speed. */
    private static final int SOFTWARE_HEIGHT = 360;
    private static final int SOFTWARE_FPS = 20;
    private static final int[] STANDARD_HEIGHTS = {1080, 720, 480, 360};
    /** Two reports closer together than this are one frame seen twice (a hook that was registered two times). */
    private static final long MIN_FRAME_GAP_NS = 1_000_000L;

    public enum State { RECORDING, PAUSED, STOPPING, DONE }

    private final Consumer<String> say;
    private final Governor governor;
    private final GpuCapture capture;
    private final FramePool pool;
    private final VideoOut out;
    private final Path file;
    private final String summary;
    private final long minFreeBytes;
    private final Path marker;
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
    private long captureCostNs;
    private long seenDrops;
    private long lastRateNoticeNs;

    private RecorderSession(Consumer<String> say, Governor governor, GpuCapture capture, FramePool pool, VideoOut out, Path file,
                            String summary, long minFreeBytes, Path marker) {
        this.say = say;
        this.governor = governor;
        this.capture = capture;
        this.pool = pool;
        this.out = out;
        this.file = file;
        this.summary = summary;
        this.minFreeBytes = minFreeBytes;
        this.marker = marker;
        this.reportedFps = governor.fps();
        this.nextCaptureNs = startNs;
        this.worker = new Thread(this::encoderLoop, "Record-able Lite encoder");
        this.worker.setDaemon(true);
        this.worker.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
    }

    // ---- start ----------------------------------------------------------------------------------------------------------------

    /** What {@link #build} produced. */
    private static final class Built {
        GpuCapture capture;
        VideoOut out;
        Path file;
        int maxFps;
        String text;
    }

    /**
     * Sets everything up and starts recording. Must run on the render thread (it creates graphics objects).
     *
     * @param say shows a message to the player; may be called from any thread
     * @return the running session, or null if recording is not possible (the player was told why)
     */
    public static RecorderSession start(Settings s, Path gameDir, int srcW, int srcH, Consumer<String> say) {
        Path dir = gameDir.resolve("recordings");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            say.accept("Cannot create the recordings folder: " + e.getMessage());
            return null;
        }
        long free = usableSpace(dir);
        if (free >= 0 && free < s.minFreeMb * MB) {
            say.accept("Not enough free storage to record (" + (free / MB) + " MB left).");
            return null;
        }
        if (srcW < 16 || srcH < 16) {
            say.accept("The game picture is too small to record.");
            return null;
        }
        String stamp = LocalDateTime.now().format(STAMP);
        Path marker = Settings.configDir(gameDir).resolve("encoder-starting.marker");
        Built b = null;
        List<String> notes = new ArrayList<>();
        if (s.hardwareEncoder) {
            touch(marker);                                    // stays behind only if the native encoder start brings the game down
            b = buildHardware(s, dir, stamp, srcW, srcH, gameDir, () -> delete(marker), notes);
            if (b == null) {
                delete(marker);
            }
        }
        if (b == null) {
            b = buildBackup(s, dir, stamp, srcW, srcH, notes);
        }
        if (b == null) {
            say.accept("Recording is not possible on this device: " + String.join("; ", notes));
            LOG.warn("Cannot record: {}", notes);
            return null;
        }
        for (String n : notes) {
            LOG.info("{}", n);
        }
        FramePool pool = new FramePool(POOL_FRAMES, b.capture.frameBytes());
        Governor governor = new Governor(b.maxFps);
        String summary = b.text;
        RecorderSession session = new RecorderSession(say, governor, b.capture, pool, b.out, b.file, summary, s.minFreeMb * MB, marker);
        session.worker.start();
        LOG.info("Recording to {}: {}", b.file, summary);
        say.accept("Recording started: " + summary + ". Press - to stop.");
        return session;
    }

    private static Built buildHardware(Settings s, Path dir, String stamp, int srcW, int srcH, Path gameDir, Runnable firstOutput,
                                       List<String> notes) {
        if (!Shim.isLoaded() && !Shim.load(nativeFolders(gameDir))) {
            notes.add("the phone's video encoder cannot be reached (" + Shim.status() + ")");
            return null;
        }
        boolean softwareOnly = false;
        for (int wanted : heightsFrom(s.height())) {
            int[] size = GpuCapture.outputSize(srcW, srcH, wanted);
            GpuCapture cap = new GpuCapture(size[0], size[1], GpuCapture.Mode.NV12, RING);
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
                if (size[1] != GpuCapture.outputSize(srcW, srcH, s.height())[1]) {
                    notes.add("the video encoder refused the full size, so the video is " + size[0] + "x" + size[1]);
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
            GpuCapture cap = new GpuCapture(size[0], size[1], GpuCapture.Mode.NV12, RING);
            cap.setSwapChroma(s.swapChroma);
            if (cap.init()) {
                Path file = dir.resolve(stamp + ".mp4");
                try {
                    H264Out out = H264Out.openAllowingSoftware(file, size[0], size[1], Math.min(SOFTWARE_FPS, s.fps()),
                            Math.min(s.kbps(), 2500) * 1000, cap.mode() == GpuCapture.Mode.RGBA, s.swapChroma, firstOutput);
                    Built b = new Built();
                    b.capture = cap;
                    b.out = out;
                    b.file = file;
                    b.maxFps = Math.min(SOFTWARE_FPS, s.fps());
                    b.text = size[0] + "x" + size[1] + " at up to " + b.maxFps + " fps; this phone has no hardware video encoder, so the "
                            + "slow software one is used at a small size to keep the game fast";
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
        GpuCapture cap = new GpuCapture(size[0], size[1], GpuCapture.Mode.RGBA, RING);
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
            return b;
        } catch (IOException | RuntimeException | LinkageError e) {
            notes.add("backup format: " + e);
            cap.close();
            return null;
        }
    }

    private static int[] heightsFrom(int requested) {
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

    /** Places where the native helper may be unpacked; some phones forbid running code from some folders, so there are several. */
    private static Path[] nativeFolders(Path gameDir) {
        List<Path> folders = new ArrayList<>();
        addFolder(folders, System.getProperty("java.io.tmpdir"), "recordable-lite");
        addFolder(folders, System.getProperty("user.home"), ".recordable-lite");
        folders.add(Settings.configDir(gameDir).resolve("native"));
        return folders.toArray(new Path[0]);
    }

    private static void addFolder(List<Path> list, String base, String name) {
        if (base != null && !base.isEmpty()) {
            list.add(Path.of(base).resolve(name));
        }
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
        if (interval > 0) {
            governor.onFrame(interval, everCaptured ? sinceCapture : 0, captureCostNs);
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
        // Two frames apart at the closest; three while the governor wants a frame that follows no capture, so it can measure the cost
        int spacing = governor.needsCleanFrame() ? 3 : 2;
        if (due && (!everCaptured || sinceCapture >= spacing)) {
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

    private void announceRateChange(long now) {
        int fps = governor.fps();
        if (fps != reportedFps && now - lastRateNoticeNs > 5_000_000_000L) {
            reportedFps = fps;
            lastRateNoticeNs = now;
            say.accept("Recorder: " + governor.reason() + " (to protect your game's frame rate).");
            LOG.info("Capture rate is now {} fps: {}", fps, governor.reason());
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
            LOG.info("Capture finished: {} started, {} delivered, {} skipped; {} dropped for lack of buffers, {} refused by the encoder",
                    capture.issuedCount(), capture.deliveredCount(), capture.skippedCount(), pool.dropped(), encoderDrops.get());
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
        report(ok, error);
    }

    private void report(boolean ok, String error) {
        long size = 0;
        try {
            size = Files.size(file);
        } catch (IOException ignored) {
            // the file may not exist if nothing was written
        }
        long ms = out.millis();
        String reason = stopReason;
        if (ok && size > 0) {
            String text = "Saved: " + file + " (" + clock(ms) + ", " + Math.max(1, size / MB) + " MB)";
            if (!reason.isEmpty()) {
                text += ". The recorder stopped because " + reason + ".";
            }
            say.accept(text);
            LOG.info("{} [{}]", text, out.describe());
        } else if (ok) {
            say.accept("Nothing was recorded" + (reason.isEmpty() ? "." : " (" + reason + ")."));
        } else {
            say.accept("The recording failed: " + (error == null ? "unknown error" : error)
                    + (size > 0 ? ". What was already written is saved in " + file : ""));
        }
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
