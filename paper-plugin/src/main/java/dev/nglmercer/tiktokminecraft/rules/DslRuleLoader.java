package dev.nglmercer.tiktokminecraft.rules;

import dev.nglmercer.tiktokminecraft.actions.ActionContext;
import dev.nglmercer.tiktokminecraft.actions.ActionRegistry;
import dev.nglmercer.tiktokminecraft.dsl.DslCompiler;
import dev.nglmercer.tiktokminecraft.dsl.DslException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the optional rule scripts from {@code scripts/}.
 *
 * <p>A script is compiled into the same rules a YAML file produces, so both
 * formats share one engine. A script that does not compile is reported with
 * its file, line and column, and never replaces the working rules.</p>
 */
public final class DslRuleLoader {

    /** The outcome of a load. */
    public static final class LoadResult {
        private final List<EventRule> rules;
        private final Map<String, String> failures;

        LoadResult(List<EventRule> rules, Map<String, String> failures) {
            this.rules = Collections.unmodifiableList(new ArrayList<EventRule>(rules));
            this.failures = Collections.unmodifiableMap(failures);
        }

        public List<EventRule> rules() {
            return rules;
        }

        /** The problems, keyed by the script they were found in. */
        public Map<String, String> failures() {
            return failures;
        }

        public boolean isValid() {
            return failures.isEmpty();
        }
    }

    private DslRuleLoader() {
    }

    /** Loads every script in a directory. */
    public static LoadResult load(
            Path scriptsDirectory, ActionRegistry registry, ActionContext validationContext) {
        List<EventRule> rules = new ArrayList<EventRule>();
        Map<String, String> failures = new LinkedHashMap<String, String>();
        if (scriptsDirectory == null || !Files.isDirectory(scriptsDirectory)) {
            return new LoadResult(rules, failures);
        }
        List<Path> files = new ArrayList<Path>();
        try (DirectoryStream<Path> stream =
                Files.newDirectoryStream(scriptsDirectory, "*.ttl")) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    files.add(file);
                }
            }
        } catch (IOException failure) {
            failures.put(scriptsDirectory.toString(), "The directory could not be read");
            return new LoadResult(rules, failures);
        }
        Collections.sort(files);
        for (Path file : files) {
            String name = file.getFileName().toString();
            try {
                String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                rules.addAll(
                        new DslCompiler(name, registry, validationContext).compile(source));
            } catch (DslException failure) {
                failures.put(name, failure.getMessage());
            } catch (IOException failure) {
                failures.put(name, "The file could not be read: " + failure.getMessage());
            }
        }
        return new LoadResult(rules, failures);
    }
}
