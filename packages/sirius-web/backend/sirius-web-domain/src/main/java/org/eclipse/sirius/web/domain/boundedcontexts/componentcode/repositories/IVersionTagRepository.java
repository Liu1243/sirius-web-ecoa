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

import org.eclipse.sirius.web.domain.boundedcontexts.componentcode.VersionTag;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for VersionTag entities.
 *
 * @author Obeo
 */
@Repository
public interface IVersionTagRepository extends ListCrudRepository<VersionTag, VersionTag.VersionTagId> {

    @Modifying
    @Query("DELETE FROM version_tag WHERE version_id = :versionId")
    void deleteByVersionId(@Param("versionId") UUID versionId);

    boolean existsByVersionIdAndTagId(UUID versionId, UUID tagId);

    List<VersionTag> findByVersionId(UUID versionId);
}
