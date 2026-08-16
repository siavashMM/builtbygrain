package com.builtbygrain.backend.performance;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.datasource.read-replica.enabled", havingValue = "true")
public class ReadReplicaAspect {

    @Around("@annotation(com.builtbygrain.backend.performance.ReadFromReplica)")
    public Object routeToReplica(ProceedingJoinPoint joinPoint) throws Throwable {
        ReadReplicaContext.enter();
        try {
            return joinPoint.proceed();
        } finally {
            ReadReplicaContext.exit();
        }
    }
}
