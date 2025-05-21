package com.reece.platform.agt.orchestrated.saga.saga;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * {@code SagaContext} is a reactive utility that helps coordinate and manage
 * rollback logic for distributed transactions (Sagas) in a reactive pipeline.
 * <p>
 * This class enables developers to register compensation steps associated with
 * transactional actions. If any part of the process fails, the registered
 * compensation functions are executed in reverse order to logically undo the operations.
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * SagaContext<OrderContext> context = new SagaContext<>();
 * context.registerCompensation("Revert inventory", this::revertInventory);
 * context.registerCompensation("Cancel order", this::cancelOrder);
 *
 * return performActions()
 *     .onErrorResume(e -> context.rollback(orderContext)
 *         .then(Mono.error(new RuntimeException("Saga failed", e))));
 * }</pre>
 *
 * @param <T> the shared context or payload used by all compensation functions
 * 
 * @author anthony.torres
 */
@Slf4j
public class SagaContext<T> {

    /**
     * List of registered compensation steps to execute in reverse order during rollback.
     */
    private final List<Compensation<T>> compensations = new ArrayList<>();

    /**
     * Registers a compensation step to be executed in case of rollback.
     *
     * @param label        A descriptive name for the compensation step, useful for logging and debugging.
     * @param compensation A function that receives the shared context and returns a {@link Mono<Void>} representing the rollback action.
     */
    public void registerCompensation(String label, Function<T, Mono<Void>> compensation) {
        compensations.add(new Compensation<>(label, compensation));
        log.info("[SAGA] Registered compensation step: {}", label);
        log.warn("[SAGA] Compensated steps: {}", compensation);
    }

    /**
     * Executes all registered compensation steps in reverse order.
     * 
     * <p>If a compensation step fails, the error is logged and the process continues
     * with the next compensation.</p>
     *
     * @param context The shared state to be passed into each compensation function.
     * @return A {@link Mono<Void>} that completes when all compensations have been attempted.
     */
    public Mono<Void> rollback(T context) {
        List<Compensation<T>> compensationsToBeExcecuted = compensations;
        log.warn("[SAGA] Starting rollback of {} steps", compensationsToBeExcecuted.size());
        List<Compensation<T>> reversed = new ArrayList<>(compensationsToBeExcecuted);
        Collections.reverse(reversed);

        return Flux.fromIterable(reversed)
                .concatMap(comp -> {
                    log.warn("[SAGA] Executing compensation: {}", comp.label);
                    return comp.compensation.apply(context)
                            .doOnSuccess(v -> log.info("[SAGA] Compensation successful: {}", comp.label))
                            .onErrorResume(e -> {
                                log.error("[SAGA] Compensation failed: {}", comp.label, e);
                                return Mono.empty();
                            });
                })
                .then()
                .doOnSuccess(v -> log.info("[SAGA] Rollback completed"));
    }

    /**
     * Internal class to represent a compensation step.
     */
    private static class Compensation<T> {
        final String label;
        final Function<T, Mono<Void>> compensation;

        Compensation(String label, Function<T, Mono<Void>> compensation) {
            this.label = label;
            this.compensation = compensation;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
