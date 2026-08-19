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
package org.eclipse.sirius.web.domain.boundedcontexts.componentcode.repositories;

import org.eclipse.sirius.web.domain.boundedcontexts.componentcode.ComponentCodeVersion;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for ComponentCodeVersion entities.
 *
 * @author Obeo
 */
@Repository
public interface IComponentCodeVersionRepository extends ListCrudRepository<ComponentCodeVersion, UUID> {

    List<ComponentCodeVersion> findByProjectIdOrderByComponentIdAscCreatedAtDesc(UUID projectId);

    List<ComponentCodeVersion> findByProjectIdAndComponentIdOrderByCreatedAtDesc(UUID projectId, String componentId);

    Optional<ComponentCodeVersion> findByIdAndProjectId(UUID id, UUID projectId);

    boolean existsByProjectIdAndComponentIdAndVersionName(UUID projectId, String componentId, String versionName);

    @Query("SELECT v.* FROM component_code_version v WHERE v.id = :id")
    Optional<ComponentCodeVersion> findByIdWithTags(@Param("id") UUID id);

    @Query("SELECT v.* FROM component_code_version v WHERE v.project_id = :projectId ORDER BY v.component_id ASC, v.created_at DESC")
    List<ComponentCodeVersion> findByProjectIdWithTagsOrderByComponentIdAscCreatedAtDesc(@Param("projectId") UUID projectId);

    List<ComponentCodeVersion> findByProjectIdAndImportStatus(UUID projectId, String importStatus);

    @Query("SELECT v.* FROM component_code_version v WHERE v.project_id = :projectId AND v.import_status = :importStatus ORDER BY v.component_id ASC, v.created_at DESC")
    List<ComponentCodeVersion> findPendingByProjectId(@Param("projectId") UUID projectId, @Param("importStatus") String importStatus);

    @Query("SELECT v.* FROM component_code_version v WHERE v.project_id = :projectId AND (v.import_status IS NULL) ORDER BY v.component_id ASC, v.created_at DESC")
    List<ComponentCodeVersion> findOfficialByProjectId(@Param("projectId") UUID projectId);

    @Modifying
    @Query("DELETE FROM component_code_version WHERE import_status = 'REJECTED' AND created_at < :cutoff")
    void deleteRejectedOlderThan(@Param("cutoff") java.time.Instant cutoff);
}
