package dev.nglmercer.tiktokminecraft.functions;

import dev.nglmercer.tiktokminecraft.functions.FunctionException;
import dev.nglmercer.tiktokminecraft.functions.FunctionExecutor;
import dev.nglmercer.tiktokminecraft.functions.FunctionLimits;
import dev.nglmercer.tiktokminecraft.functions.FunctionRegistry;
import dev.nglmercer.tiktokminecraft.functions.FunctionResult;
import dev.nglmercer.tiktokminecraft.functions.FunctionStorage;
import dev.nglmercer.tiktokminecraft.security.CommandPolicy;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.plugin.Plugin;

/**
 * Owns the function engine.
 *
 * <p>The engine is initialized from the plugin, not from an HTTP server: it
 * loads the function directory, compiles functions on demand and runs them
 * through a job queue that dispatches every command on the server thread.</p>
 *
 * <p>A reload is atomic. The new set is built and validated first; only a
 * complete, valid set replaces the running one.</p>
 */
public final class FunctionEngineService implements AutoCloseable {

    private final Plugin plugin;
    private final dev.nglmercer.tiktokminecraft.log.PluginLog log;
    private final Path functionRoot;
    private final FunctionRegistry registry = new FunctionRegistry();
    private final FunctionLimits limits;
    private final CommandPolicy policy;
    private final String minecraftVersion;
    private final CommandDispatcher dispatcher;
    private final FunctionJobManager jobs;

    public FunctionEngineService(
            Plugin plugin,
            dev.nglmercer.tiktokminecraft.log.PluginLog log,
            Path functionRoot,
            FunctionLimits limits,
            CommandPolicy policy,
            String minecraftVersion,
            CommandDispatcher dispatcher,
            boolean consoleCommandsAllowed,
            int maxConcurrent,
            int maxQueued) {
        this.plugin = plugin;
        this.log = log;
        this.functionRoot = functionRoot;
        this.limits = limits;
        this.policy = policy;
        this.minecraftVersion = minecraftVersion;
        this.dispatcher = dispatcher;
        this.jobs =
                new FunctionJobManager(
                        plugin,
                        log,
                        registry,
                        limits,
                        policy,
                        minecraftVersion,
                        dispatcher,
                        consoleCommandsAllowed,
                        maxConcurrent,
                        maxQueued);
    }

    /** The directory the functions are read from. */
    public Path functionRoot() {
        return functionRoot;
    }

    public FunctionLimits limits() {
        return limits;
    }

    public CommandPolicy policy() {
        return policy;
    }

    public FunctionRegistry registry() {
        return registry;
    }

    public FunctionJobManager jobs() {
        return jobs;
    }

    /** The ids of every loaded function. */
    public List<String> list() {
        return new ArrayList<String>(registry.ids());
    }

    public dev.nglmercer.tiktokminecraft.functions.FunctionDefinition definition(String id) {
        return registry.get(id);
    }

    /**
     * Resolves a function and returns the commands it would run.
     *
     * @throws FunctionException when the function is unknown or its arguments
     *     do not validate
     */
    public FunctionPreview preview(String functionId, Map<String, Object> args)
            throws FunctionException {
        FunctionExecutionPlan plan = compile(functionId, args);
        List<String> commands = new ArrayList<String>();
        List<String> steps = new ArrayList<String>();
        for (FunctionExecutionPlan.Item item : plan.getItems()) {
            if (item instanceof FunctionExecutionPlan.Command) {
                commands.add(((FunctionExecutionPlan.Command) item).getCommand());
                steps.add("command " + ((FunctionExecutionPlan.Command) item).getCommand());
            } else if (item instanceof FunctionExecutionPlan.Delay) {
                steps.add("delay " + ((FunctionExecutionPlan.Delay) item).getTicks() + " ticks");
            }
        }
        return new FunctionPreview(functionId, commands, steps);
    }

    /**
     * Runs a function.
     *
     * <p>The function is compiled on a worker thread, never on the server
     * thread. The returned future completes when the run finishes.</p>
     */
    public CompletableFuture<FunctionResult> execute(
            String functionId, Map<String, Object> args, ExecutionContext context) {
        return jobs.submit(functionId, args, context);
    }

    /** Compiles a function without running it. */
    public FunctionExecutionPlan compile(String functionId, Map<String, Object> args)
            throws FunctionException {
        return new FunctionCompiler(registry, limits, policy, minecraftVersion)
                .compile(functionId, args == null ? new LinkedHashMap<String, Object>() : args);
    }

    /**
     * Reloads the function directory.
     *
     * @return the outcome: the loaded ids, or the per-file failures
     */
    public ReloadResult reload() throws IOException {
        FunctionRegistry fresh = new FunctionRegistry();
        FunctionStorage.LoadResult loaded =
                FunctionStorage.loadAll(functionRoot, fresh, limits);
        if (!loaded.failures.isEmpty()) {
            // The previous, working set stays active.
            return ReloadResult.failed(loaded.failures);
        }
        registry.replaceAll(fresh);
        return ReloadResult.ok(loaded.loaded);
    }

    /** Cancels every running and queued job. */
    public void cancelAll() {
        jobs.cancelAll();
    }

    @Override
    public void close() {
        jobs.close();
    }

    /** The outcome of a function reload. */
    public static final class ReloadResult {
        private final List<String> loaded;
        private final Map<String, String> failures;

        private ReloadResult(List<String> loaded, Map<String, String> failures) {
            this.loaded = Collections.unmodifiableList(loaded);
            this.failures = Collections.unmodifiableMap(failures);
        }

        static ReloadResult ok(List<String> loaded) {
            return new ReloadResult(new ArrayList<String>(loaded),
                    new LinkedHashMap<String, String>());
        }

        static ReloadResult failed(Map<String, String> failures) {
            return new ReloadResult(new ArrayList<String>(), new LinkedHashMap<String, String>(failures));
        }

        public boolean isValid() {
            return failures.isEmpty();
        }

        public List<String> loaded() {
            return loaded;
        }

        public Map<String, String> failures() {
            return failures;
        }
    }

    /** @return the plugin the engine belongs to */
    public Plugin plugin() {
        return plugin;
    }

    /** The UUID of the target of a context, or null. */
    public static UUID targetOf(ExecutionContext context) {
        return context == null ? null : context.targetPlayer();
    }
}
