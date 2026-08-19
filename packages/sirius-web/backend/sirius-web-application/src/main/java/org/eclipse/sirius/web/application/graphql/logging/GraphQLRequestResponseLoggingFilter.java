/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.sirius.web.application.graphql.logging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Logs GraphQL requests/responses on /api/graphql to help diagnose tool execution failures.
 * <p>
 * We keep logging selective to avoid noise, but still print full stack traces when an exception
 * bubbles up through the servlet layer.
 * </p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class GraphQLRequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final String LOG_PREFIX = "[ECOA-TRACE]";

    private static final Logger LOG = LoggerFactory.getLogger(GraphQLRequestResponseLoggingFilter.class);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public GraphQLRequestResponseLoggingFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || !uri.endsWith("/api/graphql");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var requestWrapper = new ContentCachingRequestWrapper(request);
        var responseWrapper = new ContentCachingResponseWrapper(response);

        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } catch (Exception e) {
            // If something escapes GraphQL execution and hits the servlet layer, we want the full stack.
            LOG.error("{} GraphQL HTTP request failed with exception", LOG_PREFIX, e);
            throw e;
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            String requestBody = getBodyAsString(requestWrapper.getContentAsByteArray());
            String responseBody = getBodyAsString(responseWrapper.getContentAsByteArray());

            // Always log failing GraphQL HTTP responses, and keep the existing selective logging for successful requests.
            boolean looksLikeToolInvocation = containsAny(requestBody,
                    "invoke", "Invoke", "tool", "Tool", "connectorTools", "Create Service Link", "CreateServiceLink");
            boolean responseHasErrors = containsAny(responseBody, "\"errors\"", "Something went wrong");
            boolean failingStatus = responseWrapper.getStatus() >= 400;

            if (looksLikeToolInvocation || responseHasErrors || failingStatus) {
                GraphQLRequest gqlRequest = tryParseRequest(requestBody);
                GraphQLResponse gqlResponse = tryParseResponse(responseBody);

                LOG.info(
                        "{} GraphQL /api/graphql {}ms status={} operationName={} variables={} hasErrors={} responseErrors={} requestBody={} responseBody={}",
                        LOG_PREFIX,
                        durationMs,
                        responseWrapper.getStatus(),
                        gqlRequest.operationName(),
                        sanitizeForSingleLine(gqlRequest.variablesJson()),
                        gqlResponse.hasErrors(),
                        sanitizeForSingleLine(gqlResponse.errorsJson()),
                        sanitizeForSingleLine(requestBody),
                        sanitizeForSingleLine(responseBody)
                );
            }

            // Important: copy content back to the original response.
            responseWrapper.copyBodyToResponse();
        }
    }

    private String getBodyAsString(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private boolean containsAny(String s, String... needles) {
        if (s == null || s.isBlank()) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isEmpty() && s.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String sanitizeForSingleLine(String s) {
        if (s == null) {
            return "null";
        }
        // Keep logs readable in one line; avoid dumping megabytes.
        String oneLine = s.replace("\r", "\\r").replace("\n", "\\n");
        int max = 20_000;
        if (oneLine.length() > max) {
            return oneLine.substring(0, max) + "...(truncated)";
        }
        return oneLine;
    }

    private GraphQLRequest tryParseRequest(String requestBody) {
        if (requestBody == null || requestBody.isBlank()) {
            return new GraphQLRequest(null, "{}");
        }
        try {
            Map<String, Object> json = this.objectMapper.readValue(requestBody, MAP);
            Object operationName = json.get("operationName");
            Object variables = json.get("variables");
            String variablesJson = variables == null ? "{}" : this.objectMapper.writeValueAsString(variables);
            return new GraphQLRequest(operationName == null ? null : String.valueOf(operationName), variablesJson);
        } catch (Exception e) {
            // If parsing fails, fall back to raw body (truncated later).
            return new GraphQLRequest(null, requestBody);
        }
    }

    private GraphQLResponse tryParseResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return new GraphQLResponse(false, "");
        }
        try {
            Map<String, Object> json = this.objectMapper.readValue(responseBody, MAP);
            Object errors = json.get("errors");
            boolean hasErrors = errors != null;
            String errorsJson = errors == null ? "" : this.objectMapper.writeValueAsString(errors);
            return new GraphQLResponse(hasErrors, errorsJson);
        } catch (Exception e) {
            return new GraphQLResponse(containsAny(responseBody, "\"errors\""), responseBody);
        }
    }

    private record GraphQLRequest(String operationName, String variablesJson) {
    }

    private record GraphQLResponse(boolean hasErrors, String errorsJson) {
    }
}
