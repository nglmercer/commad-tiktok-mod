package dev.nglmercer.tiktokminecraft.functions;

import dev.nglmercer.tiktokminecraft.functions.FunctionCompiler;
import dev.nglmercer.tiktokminecraft.functions.FunctionException;
import dev.nglmercer.tiktokminecraft.functions.FunctionExecutionPlan;
import dev.nglmercer.tiktokminecraft.functions.FunctionExecutor;
import dev.nglmercer.tiktokminecraft.functions.FunctionLimits;
import dev.nglmercer.tiktokminecraft.functions.FunctionRegistry;
import dev.nglmercer.tiktokminecraft.functions.FunctionResult;
import dev.nglmercer.tiktokminecraft.log.LogCategory;
import dev.nglmercer.tiktokminecraft.log.PluginLog;
import dev.nglmercer.tiktokminecraft.security.CommandPolicy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.bukkit.plugin.Plugin;

/**
 * Runs functions off the server thread, with bounded concurrency and a
 * bounded queue.
 *
 * <p>Compilation and execution both happen on a worker thread. The worker
 * waits for the commands to be dispatched by the server thread, so the
 * server is never blocked by a running function.</p>
 */
public final class FunctionJobManager implements AutoCloseable {

    /** One function run. */
    public static final class Job {
        private final String id;
        private final String functionId;
        private final String target;
        private final Instant createdAt = Instant.now();
        private volatile String state = "queued";
        private volatile String lastError;
        private volatile FunctionExecutor.Work work;

        Job(String id, String functionId, String target) {
            this.id = id;
            this.functionId = functionId;
            this.target = target;
        }

        public String id() {
            return id;
        }

        public String functionId() {
            return functionId;
        }

        public String target() {
            return target;
        }

        public String state() {
            return state;
        }

        public String lastError() {
            return lastError;
        }

        public Instant createdAt() {
            return createdAt;
        }
    }

    private final Plugin plugin;
    private final FunctionRegistry registry;
    private final FunctionLimits limits;
    private final CommandPolicy policy;
    private final String minecraftVersion;
    private final CommandDispatcher dispatcher;
    private final boolean consoleCommandsAllowed;
    private final PluginLog log;
    private final ThreadPoolExecutor workers;
    private final Map<String, Job> jobs = new ConcurrentHashMap<String, Job>();

    public FunctionJobManager(
            Plugin plugin,
            PluginLog log,
            FunctionRegistry registry,
            FunctionLimits limits,
            CommandPolicy policy,
            String minecraftVersion,
            CommandDispatcher dispatcher,
            boolean consoleCommandsAllowed,
            int maxConcurrent,
            int maxQueued) {
        this.plugin = plugin;
        this.log = log;
        this.registry = registry;
        this.limits = limits;
        this.policy = policy;
        this.minecraftVersion = minecraftVersion;
        this.dispatcher = dispatcher;
        this.consoleCommandsAllowed = consoleCommandsAllowed;
        int concurrency = Math.max(1, maxConcurrent);
        this.workers =
                new ThreadPoolExecutor(
                        concurrency,
                        concurrency,
                        0L,
                        TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<Runnable>(Math.max(1, maxQueued)),
                        runnable -> {
                            Thread thread = new Thread(runnable, "tiktokminecraft-function");
                            thread.setDaemon(true);
                            return thread;
                        },
                        new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Submits a function run.
     *
     * @return a future that completes with the result of the run
     */
    public CompletableFuture<FunctionResult> submit(
            String functionId, Map<String, Object> args, ExecutionContext context) {
        UUID target = context == null ? null : context.targetPlayer();
        TargetSession session =
                target == null
                        ? new TargetSession.ServerSession(plugin)
                        : new TargetSession.PlayerSession(target);
        String jobId = UUID.randomUUID().toString();
        Job job = new Job(jobId, functionId, session.describe());
        jobs.put(jobId, job);
        CompletableFuture<FunctionResult> future = new CompletableFuture<FunctionResult>();

        Runnable task = () -> run(job, functionId, args, session, future);
        try {
            workers.execute(task);
        } catch (RejectedExecutionException rejected) {
            jobs.remove(jobId);
            job.state = "rejected";
            job.lastError = "The function queue is full";
            future.complete(
                    new FunctionResult(
                            functionId,
                            "rejected",
                            Collections.<com.google.gson.JsonObject>emptyList(),
                            job.lastError,
                            0,
                            0));
        }
        return future;
    }

    private void run(
            Job job,
            String functionId,
            Map<String, Object> args,
            TargetSession session,
            CompletableFuture<FunctionResult> future) {
        try {
            FunctionExecutionPlan plan =
                    new FunctionCompiler(registry, limits, policy, minecraftVersion)
                            .compile(functionId, args);
            job.state = "running";
            PaperCommandBridge bridge =
                    new PaperCommandBridge(dispatcher, session, policy, consoleCommandsAllowed);
            FunctionExecutor executor =
                    new FunctionExecutor(bridge, limits, new PaperDelayService(plugin));
            FunctionExecutor.Work running = executor.create(plan);
            job.work = running;
            running.run();
            com.google.gson.JsonObject snapshot = running.snapshot();
            job.state = snapshot.get("state").getAsString();
            if (snapshot.has("last_error")) {
                job.lastError = snapshot.get("last_error").getAsString();
            }
            logResult(job, snapshot);
            future.complete(
                    new FunctionResult(
                            functionId,
                            job.state,
                            toJsonObjects(snapshot.getAsJsonArray("results")),
                            job.lastError,
                            plan.getItems().size(),
                            snapshot.get("processed_steps").getAsInt()));
        } catch (FunctionException failure) {
            fail(job, functionId, failure.getMessage(), future);
        } catch (RuntimeException failure) {
            fail(job, functionId, String.valueOf(failure.getMessage()), future);
        } finally {
            jobs.remove(job.id());
        }
    }

    /**
     * Records the outcome of a run.
     *
     * <p>Every command is reported, so an administrator can see exactly what
     * a function did and why it did not do what was expected.</p>
     */
    private void logResult(Job job, com.google.gson.JsonObject snapshot) {
        if (log == null) {
            return;
        }
        for (com.google.gson.JsonElement element : snapshot.getAsJsonArray("results")) {
            if (!element.isJsonObject()) {
                continue;
            }
            com.google.gson.JsonObject entry = element.getAsJsonObject();
            String text = entry.has("text") ? entry.get("text").getAsString() : "";
            String output = entry.has("output") ? entry.get("output").getAsString() : "";
            String code = entry.has("code") ? entry.get("code").getAsString() : "";
            boolean success = entry.has("success") && entry.get("success").getAsBoolean();
            if (success) {
                log.debug(LogCategory.FUNCTIONS,
                        job.functionId() + " -> " + text + ": " + output);
            } else {
                log.warn(LogCategory.FUNCTIONS,
                        job.functionId() + " -> " + text + " failed: " + output + " (" + code + ")");
            }
        }
    }

    private void fail(
            Job job,
            String functionId,
            String message,
            CompletableFuture<FunctionResult> future) {
        job.state = "failed";
        job.lastError = message;
        future.complete(
                new FunctionResult(
                        functionId,
                        "failed",
                        Collections.<com.google.gson.JsonObject>emptyList(),
                        message,
                        0,
                        0));
    }

    /** Converts a JSON array into a list of objects. */
    private static List<com.google.gson.JsonObject> toJsonObjects(
            com.google.gson.JsonArray array) {
        List<com.google.gson.JsonObject> objects =
                new ArrayList<com.google.gson.JsonObject>(array.size());
        for (com.google.gson.JsonElement element : array) {
            if (element.isJsonObject()) {
                objects.add(element.getAsJsonObject());
            }
        }
        return objects;
    }

    /** The jobs that are still known. */
    public List<Job> list() {
        return new ArrayList<Job>(jobs.values());
    }

    public int activeCount() {
        return jobs.size();
    }

    /** Cancels a job. */
    public boolean cancel(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) {
            return false;
        }
        FunctionExecutor.Work running = job.work;
        if (running != null) {
            running.cancel("CANCELLED_BY_ADMIN");
        }
        job.state = "cancelled";
        return true;
    }

    /** Cancels every job. */
    public void cancelAll() {
        for (Job job : new ArrayList<Job>(jobs.values())) {
            cancel(job.id());
        }
    }

    @Override
    public void close() {
        cancelAll();
        workers.shutdownNow();
    }
}
