package com.reece.platform.agt.orchestrated.saga.config;

import java.lang.reflect.Method;
import java.util.function.Function;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;

import com.reece.platform.agt.orchestrated.saga.saga.SagaCompensation;
import com.reece.platform.agt.orchestrated.saga.saga.SagaContext;
import com.reece.platform.agt.orchestrated.saga.saga.SagaTransaction;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * AOP configuration for intercepting and managing annotated Saga transactional and compensation steps.
 * <p>
 * This class enables automatic registration and rollback of Saga steps using the
 * {@link SagaTransaction} and {@link SagaCompensation} annotations.
 * It integrates with Project Reactor's context to propagate and access {@link SagaContext}.
 *
 * <p>Features:
 * <ul>
 *   <li>Intercepts methods annotated with {@code @SagaTransaction} to manage rollback on failure.</li>
 *   <li>Registers compensation logic for methods annotated with {@code @SagaCompensation}.</li>
 *   <li>Injects the {@link SagaContext} into the Reactor context for per-request isolation.</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @SagaTransaction(transaction = "createUser")
 * public Mono<UserContext> createUser(UserContext context) {
 *     ...
 * }
 *
 * @SagaCompensation(rollbackFunction = "cancelUser")
 * public Mono<UserContext> saveUser(UserContext context) {
 *     ...
 * }
 *
 * public Mono<Void> cancelUser(UserContext context) {
 *     ...
 * }
 * }</pre>
 *
 * @author anthony.torres
 */
@Slf4j
@Aspect
@Component
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class SagaConfig {

    /** Key used to store and retrieve the SagaContext from the Reactor context. */
    public static final String SAGA_CONTEXT_KEY = "sagaContext";

    private static final ThreadLocal<SagaContext<?>> CURRENT_CONTEXT = new ThreadLocal<>();

    /**
     * Utility method to register a compensation function into the current Reactor context's SagaContext.
     *
     * @param label      A description of the transactional step.
     * @param rollbackFn The compensation logic to be registered.
     * @param <T>        The type of context shared between saga steps.
     * @return A {@link Mono<Void>} that completes once the compensation is registered.
     */
    public static <T> Mono<Void> registerCompensation(String label, Function<T, Mono<Void>> rollbackFn) {
        return Mono.deferContextual(ctx -> {
            if (!ctx.hasKey(SAGA_CONTEXT_KEY)) {
                log.warn("[SAGA] No SagaContext in reactor context for: {}", label);
                return Mono.empty();
            }
            SagaContext<T> context = ctx.get(SAGA_CONTEXT_KEY);
            context.registerCompensation(label, rollbackFn);
            return Mono.empty();
        });
    }

    /**
     * Retrieves the current SagaContext from thread-local storage (not used in Reactor pipeline).
     *
     * @param <T> The context type
     * @return The current {@link SagaContext}, or null if not set.
     */
    @SuppressWarnings("unchecked")
    public static <T> SagaContext<T> getCurrentContext() {
        return (SagaContext<T>) CURRENT_CONTEXT.get();
    }

    /**
     * Intercepts methods annotated with {@link SagaTransaction}.
     * <p>
     * Initializes a new {@link SagaContext}, injects it into the Reactor context,
     * and attaches rollback logic in case of error.
     *
     * @param pjp     The join point representing the intercepted method call.
     * @param sagaTx  The annotation instance.
     * @return The result of the method execution wrapped in a Mono, with rollback on failure.
     * @throws Throwable if underlying method throws
     */
    @Around("@annotation(sagaTx)")
    public Object aroundSagaTransaction(ProceedingJoinPoint pjp, SagaTransaction sagaTx) throws Throwable {
        Object[] args = pjp.getArgs();
        if (args.length == 0 || args[0] == null) {
            throw new IllegalStateException("@SagaTransaction requires a non-null context parameter");
        }

        Object contextObject = args[0];
        SagaContext<Object> sagaContext = new SagaContext<>();

        Object result = pjp.proceed();

        if (result instanceof Mono<?> mono) {
            return mono
                    .contextWrite(ctx -> ctx.put(SAGA_CONTEXT_KEY, sagaContext))
                    .doOnSuccess(r -> log.info("[SAGA] Transaction '{}' completed successfully.", sagaTx.transaction()))
                    .onErrorResume(e -> {
                        log.error("[SAGA] Transaction '{}' failed, performing rollback...", sagaTx.transaction(), e);
                        return sagaContext.rollback(contextObject).then(Mono.error(e));
                    });
        }

        return result;
    }

    /**
     * Intercepts methods annotated with {@link SagaCompensation}.
     * <p>
     * Resolves the rollback method specified in the annotation and registers it in the current saga context.
     *
     * @param pjp               The join point representing the intercepted method call.
     * @param sagaCompensation The annotation instance specifying the rollback function.
     * @return A Mono with the result of the original method, after registering its rollback logic.
     * @throws Throwable if the method or rollback function fail
     */
    @Around("@annotation(sagaCompensation)")
    public Object interceptSagaStep(ProceedingJoinPoint pjp, SagaCompensation sagaCompensation) throws Throwable {
        Object[] args = pjp.getArgs();
        Object context = args[0];
        Object target = pjp.getTarget();
        String rollbackFunctionName = sagaCompensation.rollbackFunction();

        Method rollbackMethod = target.getClass().getMethod(rollbackFunctionName, context.getClass());

        Function<Object, Mono<Void>> rollbackFn = ctx -> {
            try {
                return (Mono<Void>) rollbackMethod.invoke(target, ctx);
            } catch (Exception e) {
                log.error("[SAGA] Error invoking rollback '{}'", rollbackFunctionName, e);
                return Mono.empty();
            }
        };

        return Mono.deferContextual(ctx -> {
            if (!ctx.hasKey(SAGA_CONTEXT_KEY)) {
                log.warn("[SAGA] No SagaContext in context for: {}", pjp.getSignature().getName());
                return Mono.fromCallable(() -> {
                    try {
                        return pjp.proceed();
                    } catch (Throwable e) {
                        e.printStackTrace(); // TODO: Replace with proper error handling
                        return null;
                    }
                }).flatMap(r -> (Mono<?>) r);
            }

            SagaContext<Object> sagaCtx = ctx.get(SAGA_CONTEXT_KEY);
            sagaCtx.registerCompensation(pjp.getSignature().getName(), rollbackFn);

            return Mono.fromCallable(() -> {
                try {
                    return pjp.proceed();
                } catch (Throwable e) {
                    e.printStackTrace(); // TODO: Replace with proper error handling
                    return null;
                }
            }).flatMap(r -> (Mono<?>) r);
        });
    }
}
