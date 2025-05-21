package com.reece.platform.agt.orchestrated.saga.saga;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;

/**
 * Marks a method as a transactional step within a Saga.
 * <p>
 * This annotation is typically used to identify and label methods that represent
 * a specific transactional operation in a Saga orchestration flow.
 * The associated {@code transaction} name is used to track or log the execution
 * of that step for observability and debugging.
 *
 * <p><b>Example usage:</b></p>
 * <pre>{@code
 * @SagaTransaction(transaction = "createCustomer")
 * public Mono<OrderContext> createCustomer(OrderContext context) {
 *     // perform transactional logic
 * }
 * }</pre>
 *
 * <p>This annotation is often used together with {@link SagaCompensation} to define
 * both the forward and compensating actions of a Saga step.</p>
 * 
 * @see SagaCompensation
 * @author anthony.torres
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SagaTransaction {

    /**
     * A unique label or identifier for this Saga transaction step.
     * Useful for logging, debugging, and correlating with compensation steps.
     *
     * @return the transaction name
     */
    String transaction();
}
