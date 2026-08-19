/*******************************************************************************
 * Copyright (c) 2025 Dassault Aviation.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Dassault Aviation - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.web.edt.generator;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.sirius.web.auth.AppUser;
import org.eclipse.sirius.web.auth.CurrentUserService;
import org.eclipse.sirius.web.domain.boundedcontexts.project.services.api.IProjectSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST controller for distributed debugging container management.
 *
 * <ul>
 * <li>GET /api/distributed-debug/my-containers — list current user's containers</li>
 * <li>GET /api/distributed-debug/admin/containers — list all containers (admin only)</li>
 * <li>POST /api/distributed-debug/stop — stop a container</li>
 * </ul>
 */
@RestController
public class DistributedDebugController {

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedDebugController.class);

    @Value("${ecoa.python.generator.url}")
    private String pythonGeneratorUrl;

    private final GenerationTaskJdbcRepository taskRepository;

    private final IProjectSearchService projectSearchService;

    private final CurrentUserService currentUserService;

    private final RestTemplate restTemplate;

    public DistributedDebugController(GenerationTaskJdbcRepository taskRepository, IProjectSearchService projectSearchService, CurrentUserService currentUserService) {
        this.taskRepository = Objects.requireNonNull(taskRepository);
        this.projectSearchService = Objects.requireNonNull(projectSearchService);
        this.currentUserService = Objects.requireNonNull(currentUserService);
        this.restTemplate = new RestTemplate();
    }

    private static final String INTERNAL_SERVICE_HEADER = "X-Internal-Service";
    private static final String INTERNAL_SERVICE_VALUE = "ecoa-backend";

    /**
     * Get current user's containers.
     */
    @GetMapping("/api/distributed-debug/my-containers")
    public ResponseEntity<ContainersResponse> getMyContainers() {
        var currentUser = this.currentUserService.getCurrentUser();
        String userId = currentUser.map(AppUser::id).orElse(null);
        String username = currentUser.map(AppUser::username).orElse("");

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ContainersResponse(false, List.of(), 0, 0, "未登录"));
        }

        try {
            // Fetch containers from Python service, passing user identity as headers
            String url = this.pythonGeneratorUrl + "/api/distributed-debug/my-containers";
            HttpHeaders headers = new HttpHeaders();
            headers.set(INTERNAL_SERVICE_HEADER, INTERNAL_SERVICE_VALUE);
            headers.set("X-Ecoa-User-Id", userId);
            headers.set("X-Ecoa-Username", username != null ? username : "");
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = this.restTemplate.exchange(url, HttpMethod.GET, entity, Map.class).getBody();

            if (response == null) {
                return ResponseEntity.ok(new ContainersResponse(true, List.of(), 0, 0, null));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> containersData = (List<Map<String, Object>>) response.get("containers");

            if (containersData == null) {
                return ResponseEntity.ok(new ContainersResponse(true, List.of(), 0, 0, null));
            }

            List<DebugContainerDTO> containers = containersData.stream()
                    .map(this::mapToContainerDTO)
                    .toList();

            int total = containers.size();
            int running = (int) containers.stream().filter(DebugContainerDTO::started).count();

            return ResponseEntity.ok(new ContainersResponse(true, containers, total, running, null));
        } catch (HttpClientErrorException.NotFound e) {
            LOGGER.error("Container service not available (404) for user {}", userId);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ContainersResponse(false, List.of(), 0, 0, "容器管理服务暂未启动，请稍后重试或联系管理员"));
        } catch (RestClientException e) {
            LOGGER.error("Failed to connect to container service for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ContainersResponse(false, List.of(), 0, 0, "容器管理服务连接失败，请稍后重试"));
        } catch (Exception e) {
            LOGGER.error("Unexpected error fetching containers for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ContainersResponse(false, List.of(), 0, 0, "获取容器列表失败，请稍后重试"));
        }
    }

    /**
     * Get all containers (admin only).
     * Returns flat list of all containers (same format as my-containers for frontend compatibility).
     */
    @GetMapping("/api/distributed-debug/admin/containers")
    public ResponseEntity<ContainersResponse> getAllContainers() {
        var currentUser = this.currentUserService.getCurrentUser();
        boolean isAdmin = currentUser.map(AppUser::admin).orElse(false);

        if (!isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ContainersResponse(false, List.of(), 0, 0, "需要管理员权限"));
        }

        try {
            // Fetch all containers from Python service, using the internal service header
            String url = this.pythonGeneratorUrl + "/api/distributed-debug/admin/containers";
            HttpHeaders headers = new HttpHeaders();
            headers.set(INTERNAL_SERVICE_HEADER, INTERNAL_SERVICE_VALUE);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = this.restTemplate.exchange(url, HttpMethod.GET, entity, Map.class).getBody();

            if (response == null) {
                return ResponseEntity.ok(new ContainersResponse(true, List.of(), 0, 0, null));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> containersData = (List<Map<String, Object>>) response.get("containers");

            if (containersData == null) {
                return ResponseEntity.ok(new ContainersResponse(true, List.of(), 0, 0, null));
            }

            List<DebugContainerDTO> containers = containersData.stream()
                    .map(this::mapToContainerDTO)
                    .toList();

            int total = containers.size();
            int running = (int) containers.stream().filter(DebugContainerDTO::started).count();

            return ResponseEntity.ok(new ContainersResponse(true, containers, total, running, null));
        } catch (HttpClientErrorException.NotFound e) {
            LOGGER.error("Container service not available (404) for admin request");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ContainersResponse(false, List.of(), 0, 0, "容器管理服务暂未启动，请稍后重试或联系管理员"));
        } catch (RestClientException e) {
            LOGGER.error("Failed to connect to container service for admin request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ContainersResponse(false, List.of(), 0, 0, "容器管理服务连接失败，请稍后重试"));
        } catch (Exception e) {
            LOGGER.error("Unexpected error fetching all containers: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ContainersResponse(false, List.of(), 0, 0, "获取容器列表失败，请稍后重试"));
        }
    }

    /**
     * Stop a container.
     */
    @PostMapping("/api/distributed-debug/stop")
    public ResponseEntity<StopResponse> stopContainer(@RequestBody StopRequest request) {
        LOGGER.info("stopContainer called with request: targetDir={}, sessionId={}, clientContainer={}",
                request != null ? request.targetDir() : "null",
                request != null ? request.sessionId() : "null",
                request != null ? request.clientContainer() : "null");

        boolean hasTargetDir = request != null && request.targetDir() != null && !request.targetDir().isBlank();
        boolean hasSessionId = request != null && request.sessionId() != null && !request.sessionId().isBlank();
        if (!hasTargetDir && !hasSessionId) {
            LOGGER.warn("stopContainer: both targetDir and sessionId are missing/blank");
            return ResponseEntity.badRequest()
                    .body(new StopResponse(false, "Missing target directory"));
        }

        try {
            String url = this.pythonGeneratorUrl + "/api/distributed-debug/stop";
            HttpHeaders headers = new HttpHeaders();
            headers.set(INTERNAL_SERVICE_HEADER, INTERNAL_SERVICE_VALUE);
            HttpEntity<StopRequest> entity = new HttpEntity<>(request, headers);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = this.restTemplate.exchange(url, HttpMethod.POST, entity, Map.class).getBody();

            if (response == null) {
                return ResponseEntity.ok(new StopResponse(false, "No response from server"));
            }

            Boolean success = (Boolean) response.get("success");
            String message = (String) response.get("message");

            if (Boolean.TRUE.equals(success)) {
                return ResponseEntity.ok(new StopResponse(true, message));
            } else {
                return ResponseEntity.ok(new StopResponse(false, message != null ? message : "停止容器失败"));
            }
        } catch (HttpClientErrorException.NotFound e) {
            LOGGER.error("Container service not available (404) when stopping container");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new StopResponse(false, "容器管理服务暂未启动，请稍后重试或联系管理员"));
        } catch (RestClientException e) {
            LOGGER.error("Failed to connect to container service when stopping container: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new StopResponse(false, "容器管理服务连接失败，请稍后重试"));
        } catch (Exception e) {
            LOGGER.error("Unexpected error stopping container: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new StopResponse(false, "停止容器失败，请稍后重试"));
        }
    }

    /**
     * Delete a stopped container session (cleans up session file and compose file).
     * Only sessions that are NOT running can be deleted.
     */
    @PostMapping("/api/distributed-debug/admin/delete-session")
    public ResponseEntity<DeleteResponse> deleteContainer(@RequestBody DeleteRequest request) {
        var currentUser = this.currentUserService.getCurrentUser();
        boolean isAdmin = currentUser.map(AppUser::admin).orElse(false);
        String userId = currentUser.map(AppUser::id).orElse(null);

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new DeleteResponse(false, null, "未登录", null));
        }

        if (request == null || request.sessionId() == null || request.sessionId().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new DeleteResponse(false, null, "Missing session_id", null));
        }

        LOGGER.info("deleteContainer called: sessionId={}, isAdmin={}, userId={}", request.sessionId(), isAdmin, userId);

        try {
            String url = this.pythonGeneratorUrl + "/api/distributed-debug/admin/delete-session";
            HttpHeaders headers = new HttpHeaders();
            headers.set(INTERNAL_SERVICE_HEADER, INTERNAL_SERVICE_VALUE);
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> body = Map.of("session_id", request.sessionId());
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = this.restTemplate.exchange(url, HttpMethod.POST, entity, Map.class).getBody();

            if (response == null) {
                return ResponseEntity.ok(new DeleteResponse(false, request.sessionId(), "No response from server", null));
            }

            Boolean success = (Boolean) response.get("success");
            String error = (String) response.get("error");
            @SuppressWarnings("unchecked")
            List<String> deletedFiles = (List<String>) response.get("deleted_files");

            if (Boolean.TRUE.equals(success)) {
                return ResponseEntity.ok(new DeleteResponse(true, request.sessionId(), null, deletedFiles));
            } else {
                return ResponseEntity.ok(new DeleteResponse(false, request.sessionId(), error != null ? error : "删除失败", null));
            }
        } catch (HttpClientErrorException.NotFound e) {
            LOGGER.error("Container service not available (404) when deleting session {}", request.sessionId());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new DeleteResponse(false, request.sessionId(), "容器管理服务暂未启动，请稍后重试或联系管理员", null));
        } catch (RestClientException e) {
            LOGGER.error("Failed to connect to container service when deleting session {}: {}", request.sessionId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new DeleteResponse(false, request.sessionId(), "容器管理服务连接失败，请稍后重试", null));
        } catch (Exception e) {
            LOGGER.error("Unexpected error deleting session {}: {}", request.sessionId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DeleteResponse(false, request.sessionId(), "删除会话失败，请稍后重试", null));
        }
    }

    /**
     * Batch delete multiple stopped container sessions.
     */
    @PostMapping("/api/distributed-debug/admin/batch-delete-sessions")
    public ResponseEntity<BatchDeleteResponse> batchDeleteContainers(@RequestBody BatchDeleteRequest request) {
        var currentUser = this.currentUserService.getCurrentUser();
        boolean isAdmin = currentUser.map(AppUser::admin).orElse(false);
        String userId = currentUser.map(AppUser::id).orElse(null);

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new BatchDeleteResponse(false, 0, 0, 0, List.of(), "未登录"));
        }

        if (request == null || request.sessionIds() == null || request.sessionIds().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new BatchDeleteResponse(false, 0, 0, 0, List.of(), "Missing session_ids"));
        }

        LOGGER.info("batchDeleteContainers called: count={}, isAdmin={}, userId={}",
                request.sessionIds().size(), isAdmin, userId);

        try {
            String url = this.pythonGeneratorUrl + "/api/distributed-debug/admin/batch-delete-sessions";
            HttpHeaders headers = new HttpHeaders();
            headers.set(INTERNAL_SERVICE_HEADER, INTERNAL_SERVICE_VALUE);
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, List<String>> body = Map.of("session_ids", request.sessionIds());
            HttpEntity<Map<String, List<String>>> entity = new HttpEntity<>(body, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = this.restTemplate.exchange(url, HttpMethod.POST, entity, Map.class).getBody();

            if (response == null) {
                return ResponseEntity.ok(new BatchDeleteResponse(false, request.sessionIds().size(), 0, request.sessionIds().size(), List.of(), "No response from server"));
            }

            Boolean success = (Boolean) response.get("success");
            Integer total = (Integer) response.get("total");
            Integer successCount = (Integer) response.get("success_count");
            Integer failCount = (Integer) response.get("fail_count");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");

            List<DeleteResponse> deleteResults = results != null
                    ? results.stream().map(r -> new DeleteResponse(
                            Boolean.TRUE.equals(r.get("success")),
                            (String) r.get("session_id"),
                            (String) r.get("error"),
                            null)).toList()
                    : List.of();

            return ResponseEntity.ok(new BatchDeleteResponse(
                    Boolean.TRUE.equals(success),
                    total != null ? total : 0,
                    successCount != null ? successCount : 0,
                    failCount != null ? failCount : 0,
                    deleteResults,
                    null));
        } catch (HttpClientErrorException.NotFound e) {
            LOGGER.error("Container service not available (404) for batch delete");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new BatchDeleteResponse(false, request.sessionIds().size(), 0, request.sessionIds().size(), List.of(), "容器管理服务暂未启动，请稍后重试或联系管理员"));
        } catch (RestClientException e) {
            LOGGER.error("Failed to connect to container service for batch delete: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new BatchDeleteResponse(false, request.sessionIds().size(), 0, request.sessionIds().size(), List.of(), "容器管理服务连接失败，请稍后重试"));
        } catch (Exception e) {
            LOGGER.error("Unexpected error in batch delete: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BatchDeleteResponse(false, request.sessionIds().size(), 0, request.sessionIds().size(), List.of(), "批量删除失败，请稍后重试"));
        }
    }

    @SuppressWarnings("unchecked")
    private DebugContainerDTO mapToContainerDTO(Map<String, Object> data) {
        String sessionId = (String) data.get("session_id");
        String projectId = (String) data.get("project_id");
        String projectName = (String) data.get("project_name");
        String userId = (String) data.get("user_id");
        String username = (String) data.get("username");
        String targetDir = (String) data.get("target_dir");
        String composeProjectName = (String) data.get("compose_project_name");
        String networkName = (String) data.get("network_name");
        String dockerSubnet = (String) data.get("docker_subnet");
        String clientContainer = (String) data.get("client_container");
        Boolean clientConnected = (Boolean) data.get("client_connected");
        Boolean started = (Boolean) data.get("started");
        String createdAt = (String) data.get("created_at");

        List<String> runningServices = (List<String>) data.getOrDefault("running_services", List.of());
        List<String> configuredServices = (List<String>) data.getOrDefault("configured_services", List.of());

        // If projectName is not provided, try to fetch from project service
        if (projectName == null && projectId != null) {
            try {
                projectName = this.projectSearchService.findById(projectId)
                        .map(p -> p.getName())
                        .orElse(null);
            } catch (Exception e) {
                // Ignore, will use projectId as fallback
            }
        }

        return new DebugContainerDTO(
                sessionId != null ? sessionId : "",
                projectId != null ? projectId : "",
                projectName,
                userId != null ? userId : "",
                username != null ? username : "",
                targetDir != null ? targetDir : "",
                composeProjectName != null ? composeProjectName : "",
                networkName != null ? networkName : "",
                dockerSubnet != null ? dockerSubnet : "",
                clientContainer != null ? clientContainer : "",
                clientConnected != null ? clientConnected : false,
                runningServices != null ? runningServices : List.of(),
                configuredServices != null ? configuredServices : List.of(),
                started != null ? started : false,
                createdAt != null ? createdAt : ""
        );
    }

    // -------------------------------------------------------------------------
    // DTO Records
    // -------------------------------------------------------------------------

    public record DebugContainerDTO(
            String sessionId,
            String projectId,
            String projectName,
            String userId,
            String username,
            String targetDir,
            String composeProjectName,
            String networkName,
            String dockerSubnet,
            String clientContainer,
            boolean clientConnected,
            List<String> runningServices,
            List<String> configuredServices,
            boolean started,
            String createdAt) {
    }

    public record ContainersResponse(
            boolean success,
            List<DebugContainerDTO> containers,
            int total,
            int running,
            String error) {
    }

    /**
     * User group containing all containers for a specific user.
     */
    public record UserContainersGroup(
            String userId,
            String username,
            int total,
            int running,
            List<DebugContainerDTO> containers) {
    }

    /**
     * Admin response with containers grouped by user.
     */
    public record AdminContainersResponse(
            boolean success,
            List<UserContainersGroup> users,
            int totalUsers,
            int totalContainers,
            int totalRunning,
            String error) {
    }

    public record StopRequest(
            @JsonProperty("target_dir") String targetDir,
            @JsonProperty("client_container") String clientContainer,
            @JsonProperty("session_id") String sessionId) {
    }

    public record StopResponse(boolean success, String message) {
    }

    public record DeleteRequest(
            @JsonProperty("session_id") String sessionId) {
    }

    public record DeleteResponse(
            boolean success,
            String sessionId,
            String error,
            List<String> deletedFiles) {
    }

    public record BatchDeleteRequest(
            @JsonProperty("session_ids") List<String> sessionIds) {
    }

    public record BatchDeleteResponse(
            boolean success,
            int total,
            int successCount,
            int failCount,
            List<DeleteResponse> results,
            String error) {
    }
}
