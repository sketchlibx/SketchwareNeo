package mod.sketchlibx.terminal;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class TerminalCommandServer {

    public interface Dispatcher {
        void dispatch(Request request, TerminalReply reply);
    }

    public static final class Request {
        public final String id;
        private final Map<String, String> values;

        Request(String id, Map<String, String> values) {
            this.id = id;
            this.values = values;
        }

        public String get(String key) {
            return values.get(key);
        }

        public boolean getBoolean(String key) {
            return "true".equals(values.get(key));
        }
    }

    private static final Pattern REQUEST_ID = Pattern.compile("[0-9A-Za-z_-]{1,64}");
    private static final Set<String> KNOWN_KEYS = Set.of(
            TerminalCommands.KEY_COMMAND, TerminalCommands.KEY_SC_ID, TerminalCommands.KEY_CONFIRMED,
            TerminalCommands.KEY_DRY_RUN, TerminalCommands.KEY_URL, TerminalCommands.KEY_ARGC);
    private static final int MAX_REQUEST_BYTES = 40 * 1024;
    private static final long STALE_MILLIS = 60L * 60L * 1000L;
    private static final long PURGE_INTERVAL_MILLIS = 60L * 1000L;

    private final File directory;
    private final Dispatcher dispatcher;
    private final long pollMillis;
    private final Set<String> undeletable = new HashSet<>();
    private Thread thread;
    private volatile boolean running;
    private long lastPurge;

    public TerminalCommandServer(File directory, Dispatcher dispatcher, long pollMillis) {
        this.directory = directory;
        this.dispatcher = dispatcher;
        this.pollMillis = pollMillis;
    }

    public static boolean isValidRequestId(String id) {
        return id != null && REQUEST_ID.matcher(id).matches();
    }

    public synchronized void start() {
        if (running) return;
        //noinspection ResultOfMethodCallIgnored
        directory.mkdirs();
        running = true;
        thread = new Thread(this::loop, "terminal-command-server");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) thread.interrupt();
        thread = null;
    }

    public boolean isRunning() {
        return running;
    }

    private void loop() {
        while (running) {
            try {
                processOnce();
                Thread.sleep(pollMillis);
            } catch (InterruptedException e) {
                return;
            } catch (RuntimeException e) {
                // keep serving: one bad request must not stop the terminal helpers
            }
        }
    }

    /** Handles every request currently waiting; returns how many were consumed. */
    public int processOnce() {
        long now = System.currentTimeMillis();
        if (now - lastPurge > PURGE_INTERVAL_MILLIS) {
            lastPurge = now;
            purgeStale(now);
        }
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".req"));
        if (files == null || files.length == 0) return 0;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).thenComparing(File::getName));
        int handled = 0;
        for (File file : files) {
            String name = file.getName();
            String id = name.substring(0, name.length() - ".req".length());
            if (undeletable.contains(id)) continue;
            if (!isValidRequestId(id)) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                continue;
            }
            byte[] data = readLimited(file);
            if (!file.delete() && file.exists()) {
                undeletable.add(id);
                new TerminalReply(directory, id).finish(TerminalCleanup.CODE_FAILED, "The request could not be consumed.");
                continue;
            }
            TerminalReply reply = new TerminalReply(directory, id);
            Request request = data == null ? null : parse(id, data);
            if (request == null) {
                reply.finish(TerminalCleanup.CODE_INVALID, "Malformed request.");
            } else {
                try {
                    dispatcher.dispatch(request, reply);
                } catch (Throwable t) {
                    reply.finish(TerminalCleanup.CODE_FAILED, "Unexpected error: " + t.getMessage());
                }
            }
            handled++;
        }
        return handled;
    }

    static Request parse(String id, byte[] data) {
        String text = new String(data, StandardCharsets.UTF_8);
        Map<String, String> values = new HashMap<>();
        for (String line : text.split("\n")) {
            if (line.isEmpty()) continue;
            int separator = line.indexOf('=');
            if (separator <= 0) return null;
            String key = line.substring(0, separator);
            if (!isKnownKey(key) || values.containsKey(key)) return null;
            values.put(key, line.substring(separator + 1));
        }
        return values.containsKey(TerminalCommands.KEY_COMMAND) ? new Request(id, values) : null;
    }

    private static boolean isKnownKey(String key) {
        if (KNOWN_KEYS.contains(key)) return true;
        return key.matches(Pattern.quote(TerminalCommands.ARG_KEY_PREFIX) + "[0-9]{1,2}");
    }

    private static byte[] readLimited(File file) {
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[MAX_REQUEST_BYTES + 1];
            int total = 0;
            int read;
            while (total < buffer.length && (read = input.read(buffer, total, buffer.length - total)) > 0) total += read;
            return total > MAX_REQUEST_BYTES ? null : Arrays.copyOf(buffer, total);
        } catch (IOException e) {
            return null;
        }
    }

    private void purgeStale(long now) {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = file.getName();
            boolean ours = name.endsWith(".out") || name.endsWith(".rc") || name.endsWith(".rc.tmp") || name.endsWith(".req.tmp");
            if (ours && file.isFile() && now - file.lastModified() > STALE_MILLIS) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
        undeletable.removeIf(id -> !new File(directory, id + ".req").exists());
    }
}
