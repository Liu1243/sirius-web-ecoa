/*******************************************************************************
 * Copyright (c) 2024, 2025 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.web.edt.componentcode;

import org.eclipse.sirius.web.domain.boundedcontexts.componentcode.repositories.IComponentCodeVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Cleans up REJECTED component code versions older than 7 days on application startup.
 */
@Service
public class ComponentCodeCleanupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComponentCodeCleanupService.class);
    private static final int RETENTION_DAYS = 7;

    private final IComponentCodeVersionRepository versionRepository;

    public ComponentCodeCleanupService(IComponentCodeVersionRepository versionRepository) {
        this.versionRepository = Objects.requireNonNull(versionRepository);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void cleanupRejectedVersions() {
        Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        LOGGER.info("Cleaning up REJECTED component code versions older than {} days", RETENTION_DAYS);
        this.versionRepository.deleteRejectedOlderThan(cutoff);
    }
}
