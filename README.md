# AGT Orchestrated Saga

**AGT Orchestrated Saga** is a lightweight Java library designed to simplify the implementation of the [Saga Pattern](https://microservices.io/patterns/data/saga.html) in orchestrated transactional flows using Spring Boot and Project Reactor.

It uses annotations and AOP to automatically register transactional steps and their compensations, reducing boilerplate and centralizing rollback logic for distributed or reactive workflows.

---

## 📦 Installation

Add the following dependency to your `pom.xml`:

```xml
<dependency>
  <groupId>com.agt</groupId>
  <artifactId>agt-orchestrated-saga</artifactId>
  <version>1.0.0</version>
</dependency>
```

---

## 🧩 Features

- `@SagaTransaction` annotation to mark the root transactional flow.
- `@SagaCompensation` annotation to associate rollback logic per step.
- Reactor-compatible (`Mono<T>`) with automatic rollback on error.
- Clean, functional syntax using `Function<T, Mono<T>>` and `Function<T, Mono<Void>>`.
- Works with Spring Boot (AOP enabled).

---

## 🚀 Quick Start

### 1. Annotate the saga entry point:

```java
@SagaTransaction(transaction = "createUserSaga")
public Mono<UserContext> createUser(UserContext context) {
    return createCustomer(context)
        .flatMap(this::saveUser)
        .flatMap(this::sendWelcomeEmail);
}
```

### 2. Annotate each step and define a rollback method:

```java
@SagaCompensation(rollbackFunction = "revertCustomer")
public Mono<UserContext> createCustomer(UserContext ctx) {
    // call external service to create customer
    return Mono.just(ctx);
}

public Mono<Void> revertCustomer(UserContext ctx) {
    // call external service to delete customer
    return Mono.empty();
}
```

### 3. Context object

All methods in the saga must receive a shared `Context` object (e.g., `UserContext`) as the first argument.

---

## 🔁 How it works

- When `@SagaTransaction` is triggered, a `SagaExecutionContext` is injected into Reactor's context.
- Each `@SagaCompensation` step registers its rollback function.
- If any step throws an error, the rollback is executed in **reverse order**.

---

## 🛡 Requirements

- Java 17+ (Java 21 recommended)
- Spring Boot 3.1+
- Project Reactor

---

## 📂 Package Structure

```
com.agt.saga
│
├── @SagaTransaction         // Marks main saga flow
├── @SagaCompensation        // Marks a step and its rollback
├── SagaExecutionContext     // Holds rollback functions
├── SagaContext              // (Internal) Executes compensation in reverse
└── SagaTransactionConfig    // AOP setup for automatic wiring
```

---

## ✅ Best Practices

- Keep rollback logic **idempotent**.
- Log and monitor rollback executions.
- Group Saga logic in **dedicated service classes**.
- Avoid using `@Transactional` (incompatible with reactive flows).

---

## 🧪 Example

A complete example project can be found [here](https://github.com/agt/agt-orchestrated-saga-example) (coming soon).

---

## 📄 License

MIT © Anthony Gabriel Torres — Feel free to fork, extend, and use in your own projects.