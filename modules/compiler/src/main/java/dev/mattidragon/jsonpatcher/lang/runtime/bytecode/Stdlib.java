package dev.mattidragon.jsonpatcher.lang.runtime.bytecode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Contains shared access to the standard library files of the bytecode runtime
 * <p>
 * The data here is loaded once during static init from files included bundled with this code.
 */
public class Stdlib {
    /**
     * Contains a list of all globally available standard libraries.
     */
    public static final String[] GLOBAL_LIBRARY_NAMES;
    /**
     * Contains a list of all standard libraries that can be imported.
     */
    public static final String[] MISC_LIBRARY_NAMES;
    /**
     * Contains the contents of the standard library files.
     */
    public static final Map<String, String> LIBRARY_CONTENTS;

    static {
        GLOBAL_LIBRARY_NAMES = loadLibList("globals");
        MISC_LIBRARY_NAMES = loadLibList("misc");

        var contents = new HashMap<String, String>();
        for (var name : GLOBAL_LIBRARY_NAMES) {
            loadLibraryContent(name, contents);
        }
        for (var name : MISC_LIBRARY_NAMES) {
            loadLibraryContent(name, contents);
        }
        LIBRARY_CONTENTS = Collections.unmodifiableMap(contents);
    }

    private static void loadLibraryContent(String name, HashMap<String, String> contents) {
        var filename = "/bytecode-runtime-files/stdlib/" + name + ".jsonpatch";

        try (var stream = Stdlib.class.getResourceAsStream(filename)) {
            if (stream == null) {
                throw new IllegalStateException("Cannot find stdlib at " + filename);
            }

            contents.put(name, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read stdlib at " + filename, e);
        }
    }

    private static String[] loadLibList(String name) {
        try (var stream = Stdlib.class.getResourceAsStream("/bytecode-runtime-files/" + name + ".liblist")) {
            if (stream == null) {
                throw new IllegalStateException("Cannot find stdlib list");
            }

            return new BufferedReader(new InputStreamReader(stream))
                    .lines()
                    .toArray(String[]::new);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load stdlib list", e);
        }
    }

    private Stdlib() {
    }
}
