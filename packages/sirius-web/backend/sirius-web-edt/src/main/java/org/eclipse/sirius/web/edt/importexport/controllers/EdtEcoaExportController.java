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
package org.eclipse.sirius.web.edt.importexport.controllers;

import java.util.Objects;
import java.util.Optional;

import org.eclipse.sirius.web.application.project.services.api.IProjectEditingContextService;
import org.eclipse.sirius.web.domain.boundedcontexts.project.services.api.IProjectSearchService;
import org.eclipse.sirius.web.edt.importexport.EdtEcoaExportService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * REST controller for exporting EDT projects to ECOA XML format.
 * 
 * Endpoint: GET /api/edt/ecoa/export/{projectId}
 */
@Controller
@RequestMapping("/api/edt/ecoa")
public class EdtEcoaExportController {

    private final EdtEcoaExportService edtEcoaExportService;

    private final IProjectSearchService projectSearchService;

    private final IProjectEditingContextService projectEditingContextService;

    public EdtEcoaExportController(
            EdtEcoaExportService edtEcoaExportService,
            IProjectSearchService projectSearchService,
            IProjectEditingContextService projectEditingContextService) {
        this.edtEcoaExportService = Objects.requireNonNull(edtEcoaExportService);
        this.projectSearchService = Objects.requireNonNull(projectSearchService);
        this.projectEditingContextService = Objects.requireNonNull(projectEditingContextService);
    }

    /**
     * Export an EDT project to ECOA XML format.
     *
     * @param projectId
     *            the project ID
     * @return the ZIP file containing ECOA XML files
     */
    @ResponseBody
    @GetMapping(path = "/export/{projectId}")
    public ResponseEntity<Resource> exportProject(@PathVariable String projectId) {
        // Find the project
        var optionalProject = this.projectSearchService.findById(projectId);
        if (optionalProject.isEmpty()) {
            return new ResponseEntity<>(null, new HttpHeaders(), HttpStatus.NOT_FOUND);
        }

        var project = optionalProject.get();
        String projectName = project.getName();

        // Get editing context ID
        Optional<String> optionalEditingContextId = this.projectEditingContextService.getEditingContextId(projectId);
        if (optionalEditingContextId.isEmpty()) {
            return new ResponseEntity<>(null, new HttpHeaders(), HttpStatus.NOT_FOUND);
        }

        String editingContextId = optionalEditingContextId.get();

        // Export to ECOA XML
        Optional<byte[]> optionalZipBytes = this.edtEcoaExportService.exportToZip(editingContextId, projectName);
        if (optionalZipBytes.isEmpty()) {
            return new ResponseEntity<>(null, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        byte[] zipBytes = optionalZipBytes.get();

        // Prepare response
        ContentDisposition contentDisposition = ContentDisposition.builder("attachment")
                .filename(projectName + "_ecoa.zip")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(contentDisposition);
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentLength(zipBytes.length);

        ByteArrayResource resource = new ByteArrayResource(zipBytes);

        return new ResponseEntity<>(resource, headers, HttpStatus.OK);
    }
}
