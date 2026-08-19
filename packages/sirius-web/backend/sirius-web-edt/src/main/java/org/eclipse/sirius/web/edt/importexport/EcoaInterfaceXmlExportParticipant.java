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
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.sirius.web.application.project.services.api.IProjectExportParticipant;
import org.eclipse.sirius.web.domain.boundedcontexts.project.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Exports the {@link EcoaInterfaceXmlCache} entries for a project into the project ZIP under
 * {@code {projectName}/ecoa-interface-cache/{fileName}}.
 *
 * <p>On re-import via {@link EdtImportSiriusWebZipEventHandler} these entries are restored to
 * the cache before the ECOA roundtrip so that service connections (DataLink / EventLink /
 * RequestLink) are preserved across export/import cycles.
 */
@Service
public class EcoaInterfaceXmlExportParticipant implements IProjectExportParticipant {

    private static final Logger LOGGER = LoggerFactory.getLogger(EcoaInterfaceXmlExportParticipant.class);
    private static final String ECOA_CACHE_DIR = "ecoa-interface-cache";

    private final EcoaInterfaceXmlCache interfaceXmlCache;

    public EcoaInterfaceXmlExportParticipant(EcoaInterfaceXmlCache interfaceXmlCache) {
        this.interfaceXmlCache = Objects.requireNonNull(interfaceXmlCache);
    }

    @Override
    public Map<String, Object> exportData(Project project, String editingContextId, ZipOutputStream outputStream) {
        Map<String, byte[]> cacheEntries = this.interfaceXmlCache.getAllFor(editingContextId);
        if (cacheEntries.isEmpty()) {
            LOGGER.debug("EcoaInterfaceXmlCache is empty for editing context {} - nothing to export", editingContextId);
            return Map.of();
        }

        int written = 0;
        for (Map.Entry<String, byte[]> entry : cacheEntries.entrySet()) {
            String zipPath = project.getName() + "/" + ECOA_CACHE_DIR + "/" + entry.getKey();
            try {
                outputStream.putNextEntry(new ZipEntry(zipPath));
                outputStream.write(entry.getValue());
                outputStream.closeEntry();
                written++;
            } catch (IOException e) {
                LOGGER.warn("Failed to write ECOA interface cache entry '{}' to ZIP: {}", zipPath, e.getMessage());
            }
        }

        LOGGER.info("EcoaInterfaceXmlExportParticipant: wrote {} cache entries to ZIP for editing context {}", written, editingContextId);
        return Map.of();
    }
}
