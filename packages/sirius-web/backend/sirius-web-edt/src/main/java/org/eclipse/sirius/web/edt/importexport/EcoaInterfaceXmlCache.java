/*******************************************************************************
 * Copyright (c) 2024 Dassault Aviation.
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
package org.eclipse.sirius.web.edt.importexport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * In-memory cache for raw ECOA interface XML bytes per editing context.
 *
 * <p>
 * Sirius Web's JSON serializer cannot properly round-trip EMF objects whose
 * containment reference declares an INTERFACE type (e.g. {@code OperationType}).
 * The sub-type information (Event/Data/RequestResponse) is lost during
 * serialize → DB → deserialize, resulting in an empty {@code operations} list
 * after the first persist cycle.
 *
 * <p>
 * This cache stores the original ECOA interface XML bytes at import time and
 * provides them at export time as a fallback when {@code operations} is empty.
 */
@Component
public class EcoaInterfaceXmlCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(EcoaInterfaceXmlCache.class);
    private static final String CACHE_DIR_NAME = ".ecoa-xml-cache";

    /**
     * editingContextId → (interfaceFileName → raw XML bytes).
     * e.g. "abc-123" → {"svc_PingPong.interface.xml" → <bytes>}
     */
    private final Map<String, Map<String, byte[]>> cache = new ConcurrentHashMap<>();

    private final Path cacheRoot;

    public EcoaInterfaceXmlCache(@Value("${ecoa.workspace.dir}") String workspaceDir) {
        this.cacheRoot = Paths.get(workspaceDir).resolve(CACHE_DIR_NAME);
    }

    /**
     * Store a raw ECOA interface XML for the given editing context.
     *
     * @param editingContextId the editing context ID
     * @param fileName         the interface file name (e.g. "svc_PingPong.interface.xml")
     * @param xmlBytes         the raw XML bytes
     */
    public void store(String editingContextId, String fileName, byte[] xmlBytes) {
        cache.computeIfAbsent(editingContextId, id -> new ConcurrentHashMap<>())
                .put(fileName, xmlBytes);
        this.persistToDisk(editingContextId, fileName, xmlBytes);
        LOGGER.debug("Cached interface XML '{}' for editing context {}", fileName, editingContextId);
    }

    /**
     * Retrieve a cached raw ECOA interface XML.
     *
     * @param editingContextId the editing context ID
     * @param fileName         the interface file name (e.g. "svc_PingPong.interface.xml")
     * @return the raw XML bytes, or {@code null} if not cached
     */
    public byte[] get(String editingContextId, String fileName) {
        Map<String, byte[]> ctxCache = cache.get(editingContextId);
        if (ctxCache != null && ctxCache.containsKey(fileName)) {
            return ctxCache.get(fileName);
        }
        return this.loadFromDisk(editingContextId, fileName);
    }

    /**
     * Returns all cached entries (fileName → bytes) for the given editing context.
     *
     * <p>If the in-memory cache has no entries for the context, the disk cache directory is
     * scanned first so that entries persisted in a previous JVM run are included.
     *
     * @param editingContextId the editing context ID
     * @return unmodifiable snapshot of the fileName → bytes map; empty map if nothing cached
     */
    public Map<String, byte[]> getAllFor(String editingContextId) {
        // Populate from disk if the in-memory map is absent (e.g. server restart).
        if (!cache.containsKey(editingContextId)) {
            this.loadAllFromDisk(editingContextId);
        }
        Map<String, byte[]> ctxCache = cache.get(editingContextId);
        if (ctxCache == null || ctxCache.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(ctxCache);
    }

    /**
     * Clear the cache for the given editing context (e.g. on project deletion).
     *
     * @param editingContextId the editing context ID
     */
    public void clear(String editingContextId) {
        cache.remove(editingContextId);
        this.clearDiskCache(editingContextId);
        LOGGER.debug("Cleared interface XML cache for editing context {}", editingContextId);
    }

    private void persistToDisk(String editingContextId, String fileName, byte[] xmlBytes) {
        try {
            Path contextDir = this.cacheRoot.resolve(editingContextId);
            Files.createDirectories(contextDir);
            Files.write(contextDir.resolve(fileName), xmlBytes);
        } catch (IOException exception) {
            LOGGER.warn("Failed to persist cached XML '{}' for editing context {}", fileName, editingContextId, exception);
        }
    }

    private byte[] loadFromDisk(String editingContextId, String fileName) {
        Path file = this.cacheRoot.resolve(editingContextId).resolve(fileName);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            byte[] xmlBytes = Files.readAllBytes(file);
            cache.computeIfAbsent(editingContextId, id -> new ConcurrentHashMap<>()).put(fileName, xmlBytes);
            LOGGER.info("Loaded cached XML '{}' ({} bytes) for editing context {} from disk", fileName, xmlBytes.length, editingContextId);
            return xmlBytes;
        } catch (IOException exception) {
            LOGGER.warn("Failed to load cached XML '{}' for editing context {}", fileName, editingContextId, exception);
            return null;
        }
    }

    private void loadAllFromDisk(String editingContextId) {
        Path contextDir = this.cacheRoot.resolve(editingContextId);
        if (!Files.isDirectory(contextDir)) {
            return;
        }
        try (var paths = Files.list(contextDir)) {
            paths.filter(Files::isRegularFile).forEach(file -> {
                try {
                    byte[] xmlBytes = Files.readAllBytes(file);
                    String fileName = file.getFileName().toString();
                    cache.computeIfAbsent(editingContextId, id -> new ConcurrentHashMap<>()).put(fileName, xmlBytes);
                    LOGGER.debug("Bulk-loaded cached XML '{}' for editing context {} from disk", fileName, editingContextId);
                } catch (IOException exception) {
                    LOGGER.warn("Failed to bulk-load cached XML '{}' for editing context {}", file, editingContextId, exception);
                }
            });
        } catch (IOException exception) {
            LOGGER.warn("Failed to list disk cache dir for editing context {}", editingContextId, exception);
        }
    }

    private void clearDiskCache(String editingContextId) {
        Path contextDir = this.cacheRoot.resolve(editingContextId);
        if (!Files.exists(contextDir)) {
            return;
        }
        try (var paths = Files.walk(contextDir)) {
            paths.sorted((left, right) -> right.compareTo(left))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            LOGGER.warn("Failed to delete cached XML path {}", path, exception);
                        }
                    });
        } catch (IOException exception) {
            LOGGER.warn("Failed to clear disk cache for editing context {}", editingContextId, exception);
        }
    }
}
