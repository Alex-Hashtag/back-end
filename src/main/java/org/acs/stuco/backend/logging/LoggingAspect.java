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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;


@Aspect
@Component
public class LoggingAspect
{
    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final long SLOW_METHOD_THRESHOLD_MS = 500;
    private static final String[] SENSITIVE_HEADERS = {"authorization", "cookie", "set-cookie"};

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

        MDC.put("requestId", UUID.randomUUID().toString());
        MDC.put("method", className + "." + methodName);
        MDC.put("user", getCurrentUser());

        HttpServletRequest request = getCurrentRequest();
        if (request != null)
        {
            logRequestDetails(request);
        }

        if (logger.isDebugEnabled())
        {
            logMethodEntry(joinPoint, methodSignature);
        }

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        try
        {
            Object result = joinPoint.proceed();
            stopWatch.stop();

            logMethodExit(methodSignature, stopWatch, result);
            logResponseDetails(result);
            return result;
        } catch (Exception e)
        {
            stopWatch.stop();
            logException(methodSignature, stopWatch, e);
            logSecurityContext();
            throw e;
        } finally
        {
            MDC.clear();
        }
    }

    // --- Enhanced Helper Methods ---

    private boolean shouldSkipLogging(String className, String methodName)
    {
        return methodName.startsWith("get") && !className.contains("Security");
    }

    private void logRequestDetails(HttpServletRequest request)
    {
        Map<String, Object> requestInfo = new HashMap<>();
        requestInfo.put("uri", request.getRequestURI());
        requestInfo.put("method", request.getMethod());
        requestInfo.put("headers", filterSensitiveHeaders(request));
        requestInfo.put("client", request.getRemoteAddr());

        try
        {
            logger.info("Request details: {}", objectMapper.writeValueAsString(requestInfo));
        } catch (JsonProcessingException e)
        {
            logger.info("Request details: {}", requestInfo);
        }
    }

    private Map<String, String> filterSensitiveHeaders(HttpServletRequest request)
    {
        Map<String, String> headers = new HashMap<>();
        java.util.Collections.list(request.getHeaderNames())
                .forEach(header ->
                {
                    if (Arrays.stream(SENSITIVE_HEADERS).noneMatch(h -> h.equalsIgnoreCase(header)))
                    {
                        headers.put(header, request.getHeader(header));
                    }
                    else
                    {
                        headers.put(header, "*****");
                    }
                });
        return headers;
    }

    private void logResponseDetails(Object result)
    {
        if (result instanceof ResponseEntity<?> response)
        {
            Map<String, Object> responseInfo = new HashMap<>();
            responseInfo.put("status", response.getStatusCodeValue());
            responseInfo.put("headers", filterSensitiveHeaders(response));

            if (response.getStatusCode() == HttpStatus.FORBIDDEN)
            {
                responseInfo.put("security_context", getSecurityContextDetails());
            }

            try
            {
                logger.info("Response details: {}", objectMapper.writeValueAsString(responseInfo));
            } catch (JsonProcessingException e)
            {
                logger.info("Response details: {}", responseInfo);
            }
        }
    }

    private Map<String, String> filterSensitiveHeaders(ResponseEntity<?> response)
    {
        Map<String, String> headers = new HashMap<>();
        response.getHeaders().forEach((name, values) ->
        {
            if (Arrays.stream(SENSITIVE_HEADERS).noneMatch(h -> h.equalsIgnoreCase(name)))
            {
                headers.put(name, String.join(",", values));
            }
            else
            {
                headers.put(name, "*****");
            }
        });
        return headers;
    }

    private void logSecurityContext()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null)
        {
            Map<String, Object> securityInfo = new HashMap<>();
            securityInfo.put("name", auth.getName());
            securityInfo.put("authorities", auth.getAuthorities());
            securityInfo.put("authenticated", auth.isAuthenticated());

            try
            {
                logger.debug("Security context: {}", objectMapper.writeValueAsString(securityInfo));
            } catch (JsonProcessingException e)
            {
                logger.debug("Security context: {}", securityInfo);
            }
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

    // Existing helper methods with enhanced parameter handling
    private Map<String, Object> formatArguments(String[] names, Object[] values)
    {
        Map<String, Object> formattedArgs = new HashMap<>();
        if (names == null || values == null) return formattedArgs;

        IntStream.range(0, Math.min(names.length, values.length)).forEach(i ->
        {
            String name = names[i];
            Object value = values[i];

            if (value instanceof HttpServletRequest)
            {
                formattedArgs.put(name, "HttpServletRequest");
            }
            else if (value instanceof Authentication)
            {
                formattedArgs.put(name, getSecurityContextDetails());
            }
            else
            {
                formattedArgs.put(name, isSensitive(name) ? "*****" : value);
            }
        });
        return formattedArgs;
    }

    private boolean shouldSkipLogging(String methodName)
    {
        return methodName.startsWith("get") ||
                methodName.equals("toString") ||
                methodName.equals("hashCode") ||
                methodName.equals("equals");
    }

    private void logMethodEntry(ProceedingJoinPoint joinPoint, MethodSignature signature)
    {
        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("event", "METHOD_ENTRY");
        logEntry.put("arguments", formatArguments(signature.getParameterNames(), joinPoint.getArgs()));

        try
        {
            logger.debug(objectMapper.writeValueAsString(logEntry));
        } catch (JsonProcessingException e)
        {
            logger.debug("Entering method [{}].{}() with args: {}",
                    signature.getDeclaringType().getSimpleName(),
                    signature.getName(),
                    Arrays.toString(joinPoint.getArgs()));
        }
    }

    private void logMethodExit(MethodSignature signature, StopWatch stopWatch, Object result)
    {
        long executionTime = stopWatch.getTotalTimeMillis();
        String logMessage = "Exiting method [{}].{}() | Time: {} ms";

        if (executionTime > SLOW_METHOD_THRESHOLD_MS)
        {
            logger.warn(logMessage + " (SLOW)",
                    signature.getDeclaringType().getSimpleName(),
                    signature.getName(),
                    executionTime);
        }
        else if (logger.isDebugEnabled())
        {
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("event", "METHOD_EXIT");
            logEntry.put("executionTimeMs", executionTime);

            try
            {
                logger.debug(objectMapper.writeValueAsString(logEntry));
            } catch (JsonProcessingException e)
            {
                logger.debug(logMessage,
                        signature.getDeclaringType().getSimpleName(),
                        signature.getName(),
                        executionTime);
            }
        }
    }

    private void logException(MethodSignature signature, StopWatch stopWatch, Exception e)
    {
        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("event", "METHOD_ERROR");
        logEntry.put("executionTimeMs", stopWatch.getTotalTimeMillis());
        logEntry.put("error", e.getClass().getSimpleName());
        logEntry.put("message", e.getMessage());

        try
        {
            logger.error(objectMapper.writeValueAsString(logEntry));
        } catch (JsonProcessingException ex)
        {
            logger.error("Exception in [{}].{}() after {} ms: {}",
                    signature.getDeclaringType().getSimpleName(),
                    signature.getName(),
                    stopWatch.getTotalTimeMillis(),
                    e.getMessage());
        }
    }


    private boolean isSensitive(String paramName)
    {
        return paramName != null &&
                (paramName.toLowerCase().contains("password") ||
                        paramName.toLowerCase().contains("secret") ||
                        paramName.toLowerCase().contains("token"));
    }
}
