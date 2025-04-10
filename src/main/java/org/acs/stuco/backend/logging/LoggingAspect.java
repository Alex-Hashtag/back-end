package org.acs.stuco.backend.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.*;
import java.util.stream.IntStream;


@Aspect
@Component
public class LoggingAspect
{
    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static final long SLOW_METHOD_THRESHOLD_MS = 500;
    private static final long VERY_SLOW_METHOD_THRESHOLD_MS = 2000;
    private static final String[] SENSITIVE_HEADERS = {"authorization", "cookie", "set-cookie"};
    private static final String[] SENSITIVE_PARAM_PATTERNS = {"password", "secret", "token", "key"};

    private static final Set<String> NOISY_METHOD_PREFIXES = Set.of("get", "is", "has", "toString", "hashCode", "equals");
    private static final Set<String> SIGNIFICANT_PACKAGES = Set.of(
            "controller", "security", "auth", "user", "admin", "order", "payment", "checkout"
    );


    @Around("execution(* org.acs.stuco.backend..*(..)) && " +
            "!within(org.acs.stuco.backend.logging..*) && " +
            "!target(org.springframework.web.filter.GenericFilterBean)")
    public Object logMethodExecution(ProceedingJoinPoint joinPoint) throws Throwable
    {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        String methodName = methodSignature.getName();
        String className = methodSignature.getDeclaringType().getSimpleName();

        if (shouldSkipLogging(className, methodName))
        {
            return joinPoint.proceed();
        }

        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        MDC.put("method", className + "." + methodName);
        MDC.put("user", getCurrentUser());

        boolean isSignificant = isSignificantOperation(className, methodName);

        HttpServletRequest request = getCurrentRequest();
        if (request != null && isSignificant)
        {
            logRequestDetails(request);
        }

        if (logger.isDebugEnabled() || isSignificant)
        {
            logMethodEntry(joinPoint, methodSignature, isSignificant);
        }

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        try
        {

            Object result = joinPoint.proceed();
            stopWatch.stop();

            logMethodExit(methodSignature, stopWatch, result, isSignificant);

            if (result instanceof ResponseEntity && isSignificant)
            {
                logResponseDetails((ResponseEntity<?>) result);
            }

            return result;
        } catch (Exception e)
        {
            stopWatch.stop();

            logException(methodSignature, stopWatch, e);

            if (e.getMessage() != null &&
                    (e.getMessage().contains("access") ||
                            e.getMessage().contains("permission") ||
                            e.getMessage().contains("auth")))
            {
                logSecurityContext();
            }
            throw e;
        } finally
        {
            MDC.clear();
        }
    }


    private boolean shouldSkipLogging(String className, String methodName)
    {
        if (isInSignificantPackage(className))
        {
            return false; // Never skip logging for significant packages
        }

        return NOISY_METHOD_PREFIXES.stream().anyMatch(methodName::startsWith) &&
                !methodName.contains("Security") &&
                !methodName.contains("Auth");
    }


    private boolean isSignificantOperation(String className, String methodName)
    {

        return className.contains("Controller") ||
                className.contains("Security") ||
                className.contains("Auth") ||
                isInSignificantPackage(className) ||
                methodName.startsWith("create") ||
                methodName.startsWith("update") ||
                methodName.startsWith("delete") ||
                methodName.startsWith("process") ||
                methodName.startsWith("validate") ||
                methodName.startsWith("authenticate");
    }


    private boolean isInSignificantPackage(String className)
    {
        return SIGNIFICANT_PACKAGES.stream().anyMatch(className.toLowerCase()::contains);
    }


    private void logRequestDetails(HttpServletRequest request)
    {
        Map<String, Object> requestInfo = new HashMap<>();
        requestInfo.put("uri", request.getRequestURI());
        requestInfo.put("method", request.getMethod());

        Map<String, String> significantHeaders = new HashMap<>();
        java.util.Collections.list(request.getHeaderNames())
                .forEach(header ->
                {
                    if (isSignificantHeader(header))
                    {
                        if (Arrays.stream(SENSITIVE_HEADERS).anyMatch(h -> h.equalsIgnoreCase(header)))
                        {
                            significantHeaders.put(header, "*****");
                        }
                        else
                        {
                            significantHeaders.put(header, request.getHeader(header));
                        }
                    }
                });

        if (!significantHeaders.isEmpty())
        {
            requestInfo.put("headers", significantHeaders);
        }

        requestInfo.put("client", request.getRemoteAddr());

        try
        {
            logger.info("Request: {}", objectMapper.writeValueAsString(requestInfo));
        } catch (JsonProcessingException e)
        {
            logger.info("Request: {}", requestInfo);
        }
    }


    private boolean isSignificantHeader(String header)
    {
        String headerLower = header.toLowerCase();
        return headerLower.contains("content-type") ||
                headerLower.contains("accept") ||
                headerLower.contains("user-agent") ||
                headerLower.contains("origin") ||
                headerLower.contains("referer") ||
                headerLower.contains("authorization") ||
                headerLower.contains("x-");
    }


    private void logResponseDetails(ResponseEntity<?> response)
    {
        Map<String, Object> responseInfo = new HashMap<>();
        responseInfo.put("status", response.getStatusCodeValue());

        HttpStatus status = (HttpStatus) response.getStatusCode();
        if (status == HttpStatus.UNAUTHORIZED ||
                status == HttpStatus.FORBIDDEN ||
                status.is5xxServerError())
        {
            responseInfo.put("security_context", getSecurityContextDetails());
        }

        try
        {
            logger.info("Response: {}", objectMapper.writeValueAsString(responseInfo));
        } catch (JsonProcessingException e)
        {
            logger.info("Response: {}", responseInfo);
        }
    }


    private void logSecurityContext()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null)
        {
            Map<String, Object> securityInfo = new HashMap<>();
            securityInfo.put("principal", auth.getName());
            securityInfo.put("authorities", auth.getAuthorities());
            securityInfo.put("authenticated", auth.isAuthenticated());

            try
            {
                logger.info("Security context: {}", objectMapper.writeValueAsString(securityInfo));
            } catch (JsonProcessingException e)
            {
                logger.info("Security context: {}", securityInfo);
            }
        }
        else
        {
            logger.info("Security context: Not authenticated");
        }
    }


    private String getCurrentUser()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
    }


    private HttpServletRequest getCurrentRequest()
    {
        try
        {
            return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        } catch (IllegalStateException e)
        {
            return null;
        }
    }


    private String getSecurityContextDetails()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "No security context";

        return String.format("User: %s, Roles: %s, Authenticated: %s",
                auth.getName(),
                auth.getAuthorities(),
                auth.isAuthenticated());
    }


    private Map<String, Object> formatArguments(String[] names, Object[] values)
    {
        Map<String, Object> formattedArgs = new HashMap<>();
        if (names == null || values == null) return formattedArgs;

        IntStream.range(0, Math.min(names.length, values.length)).forEach(i ->
        {
            String name = names[i];
            Object value = values[i];

            if (value == null)
            {
                formattedArgs.put(name, null);
            }
            else if (isBulkObject(value))
            {
                formattedArgs.put(name, value.getClass().getSimpleName());
            }
            else if (isSensitiveParameter(name))
            {
                formattedArgs.put(name, "*****");
            }
            else
            {
                formattedArgs.put(name, getStringRepresentation(value));
            }
        });
        return formattedArgs;
    }


    private boolean isBulkObject(Object value)
    {
        return value instanceof HttpServletRequest ||
                value instanceof Authentication ||
                value instanceof Collection && ((Collection<?>) value).size() > 10 ||
                value instanceof Map && ((Map<?, ?>) value).size() > 10;
    }


    private Object getStringRepresentation(Object value)
    {
        return switch (value)
        {
            case null -> null;
            case Collection<?> collection -> String.format("%s[size=%d]",
                    value.getClass().getSimpleName(),
                    collection.size());
            case Map map -> String.format("%s[size=%d]",
                    value.getClass().getSimpleName(),
                    map.size());
            default ->
                    value instanceof String || value.getClass().isPrimitive() ? value : value.getClass().getSimpleName();
        };
    }


    private boolean isSensitiveParameter(String paramName)
    {
        if (paramName == null) return false;

        String lowerName = paramName.toLowerCase();
        return Arrays.stream(SENSITIVE_PARAM_PATTERNS).anyMatch(lowerName::contains);
    }


    private void logMethodEntry(ProceedingJoinPoint joinPoint, MethodSignature signature, boolean isSignificant)
    {
        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("operation", "CALL");
        logEntry.put("method", signature.getDeclaringType().getSimpleName() + "." + signature.getName());

        if (isSignificant || logger.isDebugEnabled())
        {
            Map<String, Object> args = formatArguments(signature.getParameterNames(), joinPoint.getArgs());
            if (!args.isEmpty())
            {
                logEntry.put("params", args);
            }
        }

        try
        {
            if (isSignificant)
            {
                logger.info(objectMapper.writeValueAsString(logEntry));
            }
            else if (logger.isDebugEnabled())
            {
                logger.debug(objectMapper.writeValueAsString(logEntry));
            }
        } catch (JsonProcessingException e)
        {
            if (isSignificant)
            {
                logger.info("Method call: {}", signature.getDeclaringType().getSimpleName() + "." + signature.getName());
            }
            else
            {
                logger.debug("Method call: {}", signature.getDeclaringType().getSimpleName() + "." + signature.getName());
            }
        }
    }


    private void logMethodExit(MethodSignature signature, StopWatch stopWatch, Object result, boolean isSignificant)
    {
        long executionTime = stopWatch.getTotalTimeMillis();
        String methodName = signature.getDeclaringType().getSimpleName() + "." + signature.getName();

        if (executionTime > VERY_SLOW_METHOD_THRESHOLD_MS)
        {
            logger.warn("PERFORMANCE ALERT: Method {} took {}ms (very slow)", methodName, executionTime);
        }

        else if (executionTime > SLOW_METHOD_THRESHOLD_MS)
        {
            logger.info("PERFORMANCE: Method {} took {}ms (slow)", methodName, executionTime);
        }

        else if (isSignificant)
        {
            logger.info("Method {} completed in {}ms", methodName, executionTime);
        }
        else if (logger.isDebugEnabled())
        {
            logger.debug("Method {} completed in {}ms", methodName, executionTime);
        }
    }


    private void logException(MethodSignature signature, StopWatch stopWatch, Exception e)
    {
        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("operation", "ERROR");
        logEntry.put("method", signature.getDeclaringType().getSimpleName() + "." + signature.getName());
        logEntry.put("time_ms", stopWatch.getTotalTimeMillis());
        logEntry.put("exception", e.getClass().getSimpleName());
        logEntry.put("message", e.getMessage());

        try
        {
            logger.error(objectMapper.writeValueAsString(logEntry));
        } catch (JsonProcessingException ex)
        {
            logger.error("Exception in {}.{}(): {} - {}",
                    signature.getDeclaringType().getSimpleName(),
                    signature.getName(),
                    e.getClass().getSimpleName(),
                    e.getMessage());
        }
    }
}

