package com.agt.orchestrated.saga.saga;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a method is part of a Saga step and associates it with a corresponding
 * rollback function to be executed if the saga needs to be reverted.
 * <p>
 * This annotation is typically used in conjunction with a mechanism (e.g., AOP, proxying, or
 * manual scanning) that inspects methods annotated with {@code @SagaCompensation} and
 * registers their rollback counterparts into a {@link SagaContext}.
 *
 * <p><b>Example usage:</b></p>
 * <pre>{@code
 * @SagaCompensation(rollbackFunction = "revertInventory")
 * public Mono<OrderContext> reserveInventory(OrderContext context) {
 *     // logic to reserve items
 * }
 *
 * public Mono<Void> revertInventory(OrderContext context) {
 *     // logic to release reserved items
 * }
 * }</pre>
 *
 * @author anthony.torres
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SagaCompensation {

    /**
     * The name of the method that should be called to compensate (rollback) the action
     * defined in the annotated method.
     *
     * @return the method name (must exist in the same class)
     */
    String rollbackFunction();
}
