package it.polito.cloudresources.be.config.log;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Aspect for logging service method calls
 */
@Aspect
@Component
@Slf4j
public class MethodLoggingAspect {

    /**
     * Logs service method execution with parameters and execution time
     */
    @Around("execution(* it.polito.cloudresources.be.service.*.*(..))")
    public Object logServiceMethodExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getDeclaringType().getSimpleName() + "." + signature.getName();
        String args = Arrays.toString(joinPoint.getArgs());
        String currentUser = getCurrentUsername();
        
        log.debug("SERVICE CALL: {} by user '{}' with args: {}", methodName, currentUser, args);
        
        long startTime = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long endTime = System.currentTimeMillis();
        
        log.debug("SERVICE RETURN: {} completed in {}ms", methodName, (endTime - startTime));
        
        return result;
    }
    
    /**
     * Logs controller method execution with parameters and execution time
     */
    @Around("execution(* it.polito.cloudresources.be.controller.*.*(..))")
    public Object logControllerMethodExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getDeclaringType().getSimpleName() + "." + signature.getName();
        String currentUser = getCurrentUsername();
        
        // For controllers, we only log method name, not args (as they are already logged by filter)
        log.debug("CONTROLLER CALL: {} by user '{}'", methodName, currentUser);
        
        long startTime = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long endTime = System.currentTimeMillis();
        
        log.debug("CONTROLLER RETURN: {} completed in {}ms", methodName, (endTime - startTime));
        
        return result;
    }
    
    /**
     * Get current username from security context
     */
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return "anonymous";
        }
        return auth.getName();
    }
}