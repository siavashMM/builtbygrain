package com.builtbygrain.backend.performance;

import java.lang.reflect.Method;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("publicCacheInvalidationPolicy")
public class PublicCacheInvalidationPolicy {

    public boolean isMutation(Method method) {
        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);
        if (transactional == null) {
            transactional = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), Transactional.class);
        }
        return transactional != null && !transactional.readOnly();
    }
}
