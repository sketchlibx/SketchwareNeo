package mod.hey.studios.moreblock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import a.a.a.wq;

/**
 * Stores MoreBlock parameter default values.
 * <p>
 * {@code eC} (the project data manager) only persists {@code name:spec} pairs in the encrypted
 * {@code logic} file and has no place for extra data, and the spec grammar must not change, so defaults
 * live in a small side file next to it: {@code <project data dir>/moreblock_defaults}
 * ({@code wq.a(sc_id)} is the same directory {@code eC} reads {@code logic} from).
 * <p>
 * A project without that file generates exactly the code it always did. Each entry stores the exact
 * spec it was created for and is only used while the MoreBlock still has that spec, so entries left
 * behind by deleted/renamed/re-created MoreBlocks can never be applied to the wrong block.
 * <p>
 * File format (UTF-8, one record per line, fields separated by TAB with {@code \\ \t \n \r} escaped):
 * <pre>
 * #moreblock-defaults v1
 * B  javaName  blockName  spec
 * D  parameterName  javaExpression
 * </pre>
 */
public final class MoreBlockDefaultsStore {
    private static final String FILE_NAME = "moreblock_defaults";
    private static final String HEADER = "#moreblock-defaults v1";

    private MoreBlockDefaultsStore() {
    }

    private static final class Block {
        final String javaName;
        final String blockName;
        final String spec;
        final LinkedHashMap<String, String> defaults = new LinkedHashMap<>();

        Block(String javaName, String blockName, String spec) {
            this.javaName = javaName;
            this.blockName = blockName;
            this.spec = spec;
        }

        boolean is(String javaName, String blockName) {
            return this.javaName.equals(javaName) && this.blockName.equals(blockName);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------------------------

    /**
     * Replaces the defaults of one MoreBlock. An empty map removes any stored entry, so re-creating a
     * MoreBlock without defaults also clears old ones. Does nothing (and creates no file) if there is
     * nothing to store and nothing to clear.
     */
    public static synchronized void save(@NonNull String scId, @NonNull String javaName, @NonNull String blockName,
                                         @NonNull String spec, @NonNull Map<String, String> defaults) {
        saveTo(new File(wq.a(scId), FILE_NAME), javaName, blockName, spec, defaults);
    }

    /** @return the defaults (parameter name to Java expression) valid for exactly this MoreBlock + spec, or an empty map. */
    @NonNull
    public static synchronized Map<String, String> get(@NonNull String scId, @NonNull String javaName,
                                                       @NonNull String blockName, @NonNull String spec) {
        return getFrom(new File(wq.a(scId), FILE_NAME), javaName, blockName, spec);
    }

    /** Encodes defaults for passing them through an Intent extra ({@code name TAB expression} per line). */
    @NonNull
    public static String encode(@NonNull Map<String, String> defaults) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            sb.append(escape(entry.getKey())).append('\t').append(escape(entry.getValue())).append('\n');
        }
        return sb.toString();
    }

    @NonNull
    public static Map<String, String> decode(@Nullable String encoded) {
        Map<String, String> map = new LinkedHashMap<>();
        if (encoded == null || encoded.isEmpty()) return map;
        for (String line : encoded.split("\n")) {
            String[] parts = line.split("\t", -1);
            if (parts.length == 2 && !parts[0].isEmpty()) {
                map.put(unescape(parts[0]), unescape(parts[1]));
            }
        }
        return map;
    }

    // ---------------------------------------------------------------------------------------------
    // Implementation (package-private for tests)
    // ---------------------------------------------------------------------------------------------

    static void saveTo(File file, String javaName, String blockName, String spec, Map<String, String> defaults) {
        if (defaults.isEmpty() && !file.isFile()) return;

        List<Block> blocks = new ArrayList<>();
        try {
            if (file.isFile()) blocks = read(file);
        } catch (IOException | RuntimeException e) {
            // Unreadable file: keep it for inspection instead of silently destroying it.
            //noinspection ResultOfMethodCallIgnored
            file.renameTo(new File(file.getPath() + ".corrupt"));
            blocks = new ArrayList<>();
        }

        boolean changed = blocks.removeIf(block -> block.is(javaName, blockName));
        if (!defaults.isEmpty()) {
            Block block = new Block(javaName, blockName, spec);
            block.defaults.putAll(defaults);
            blocks.add(block);
            changed = true;
        }
        if (!changed) return;

        try {
            if (blocks.isEmpty()) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            } else {
                write(file, blocks);
            }
        } catch (IOException ignored) {
            // The MoreBlock itself is already saved; losing defaults must never break creating it.
        }
    }

    @NonNull
    static Map<String, String> getFrom(File file, String javaName, String blockName, String spec) {
        if (!file.isFile()) return new LinkedHashMap<>();
        try {
            for (Block block : read(file)) {
                if (block.is(javaName, blockName) && block.spec.equals(spec)) {
                    return new LinkedHashMap<>(block.defaults);
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Corrupt file: behave as if there were no defaults.
        }
        return new LinkedHashMap<>();
    }

    private static List<Block> read(File file) throws IOException {
        List<Block> blocks = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String first = reader.readLine();
            if (first == null || !first.equals(HEADER)) throw new IOException("Unknown format");

            Block current = null;
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t", -1);
                if (parts[0].equals("B") && parts.length == 4) {
                    current = new Block(unescape(parts[1]), unescape(parts[2]), unescape(parts[3]));
                    blocks.add(current);
                } else if (parts[0].equals("D") && parts.length == 3 && current != null) {
                    current.defaults.put(unescape(parts[1]), unescape(parts[2]));
                }
            }
        }
        return blocks;
    }

    private static void write(File file, List<Block> blocks) throws IOException {
        File temp = new File(file.getPath() + ".tmp");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(temp), StandardCharsets.UTF_8)) {
            writer.write(HEADER);
            writer.write('\n');
            for (Block block : blocks) {
                writer.write("B\t" + escape(block.javaName) + "\t" + escape(block.blockName) + "\t" + escape(block.spec) + "\n");
                for (Map.Entry<String, String> entry : block.defaults.entrySet()) {
                    writer.write("D\t" + escape(entry.getKey()) + "\t" + escape(entry.getValue()) + "\n");
                }
            }
        }
        if (!temp.renameTo(file)) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            if (!temp.renameTo(file)) throw new IOException("Could not replace " + file);
        }
    }

    static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\t' -> sb.append("\\t");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    static String unescape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                switch (next) {
                    case 't' -> sb.append('\t');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    default -> sb.append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
