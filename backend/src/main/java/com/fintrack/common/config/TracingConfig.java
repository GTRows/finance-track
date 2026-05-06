package com.fintrack.common.config;

import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.observation.ObservationRegistry;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tracing-aware decorators for executors that span Micrometer's auto-instrumented boundaries.
 *
 * <p>Spring Boot 3.2 + Micrometer 1.12 propagate the {@link io.micrometer.observation.Observation}
 * context across {@code @Async}, {@code @Scheduled}, and the default {@code WebClient} call paths.
 * However {@code CompletableFuture.supplyAsync(supplier, executor)} bypasses any auto-instrumented
 * default executor when an explicit executor is passed. The {@link #tracingPriceVirtualExecutor}
 * bean wraps the raw {@code priceVirtualExecutor} bean from {@code PriceConfig} (25-03) so every
 * task submitted picks up the current {@link ContextSnapshot} (which carries the observation /
 * trace span context) before running on its virtual carrier thread.
 */
@Configuration
public class TracingConfig {

    /**
     * Wraps {@code priceVirtualExecutor} with a {@link ContextSnapshot}-propagating decorator. The
     * {@link ObservationRegistry} parameter is intentionally injected to make the bean dependency
     * explicit -- the wrapped executor is only useful once the observation infrastructure is on the
     * classpath, and ordering ensures the registry is materialised first.
     */
    @Bean("tracingPriceVirtualExecutor")
    public ExecutorService tracingPriceVirtualExecutor(
            @Qualifier("priceVirtualExecutor") ExecutorService delegate,
            @SuppressWarnings("unused") ObservationRegistry observationRegistry) {
        return new ContextPropagatingExecutorService(delegate);
    }

    /**
     * Tiny adapter that captures the calling thread's context snapshot at submission time and
     * re-installs it on the executing virtual thread before running the wrapped task. Lifecycle
     * methods delegate verbatim so Spring's bean factory shutdown still closes the underlying
     * executor exactly once.
     */
    static final class ContextPropagatingExecutorService implements ExecutorService {

        private final ExecutorService delegate;
        private final ContextSnapshotFactory snapshotFactory;

        ContextPropagatingExecutorService(ExecutorService delegate) {
            this.delegate = delegate;
            this.snapshotFactory = ContextSnapshotFactory.builder().build();
        }

        private Runnable wrap(Runnable task) {
            ContextSnapshot snapshot = snapshotFactory.captureAll();
            return snapshot.wrap(task);
        }

        private <T> Callable<T> wrap(Callable<T> task) {
            ContextSnapshot snapshot = snapshotFactory.captureAll();
            return snapshot.wrap(task);
        }

        @Override
        public void execute(Runnable command) {
            delegate.execute(wrap(command));
        }

        @Override
        public Future<?> submit(Runnable task) {
            return delegate.submit(wrap(task));
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            return delegate.submit(wrap(task), result);
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            return delegate.submit(wrap(task));
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
                throws InterruptedException {
            return delegate.invokeAll(tasks.stream().map(this::wrap).toList());
        }

        @Override
        public <T> List<Future<T>> invokeAll(
                Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
                throws InterruptedException {
            return delegate.invokeAll(tasks.stream().map(this::wrap).toList(), timeout, unit);
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
                throws InterruptedException, java.util.concurrent.ExecutionException {
            return delegate.invokeAny(tasks.stream().map(this::wrap).toList());
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
                throws InterruptedException,
                        java.util.concurrent.ExecutionException,
                        java.util.concurrent.TimeoutException {
            return delegate.invokeAny(tasks.stream().map(this::wrap).toList(), timeout, unit);
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
