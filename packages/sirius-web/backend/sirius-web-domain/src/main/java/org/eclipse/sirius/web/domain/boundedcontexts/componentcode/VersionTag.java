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
package org.eclipse.sirius.web.domain.boundedcontexts.componentcode;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity representing the many-to-many relationship between versions and tags.
 *
 * @author Obeo
 */
@Table("version_tag")
public class VersionTag {

    @Column("version_id")
    private UUID versionId;

    @Column("tag_id")
    private UUID tagId;

    public VersionTag() {
    }

    private VersionTag(Builder builder) {
        this.versionId = builder.versionId;
        this.tagId = builder.tagId;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Builder for VersionTag.
     */
    public static class Builder {
        private UUID versionId;
        private UUID tagId;

        public Builder versionId(UUID theVersionId) {
            this.versionId = theVersionId;
            return this;
        }

        public Builder tagId(UUID theTagId) {
            this.tagId = theTagId;
            return this;
        }

        public VersionTag build() {
            return new VersionTag(this);
        }
    }

    public UUID getVersionId() {
        return versionId;
    }

    public void setVersionId(UUID versionId) {
        this.versionId = versionId;
    }

    public UUID getTagId() {
        return tagId;
    }

    public void setTagId(UUID tagId) {
        this.tagId = tagId;
    }

    @Override
    public boolean equals(Object o) {
        boolean result = false;
        if (this == o) {
            result = true;
        } else if (o != null && getClass() == o.getClass()) {
            VersionTag that = (VersionTag) o;
            result = Objects.equals(versionId, that.versionId) && Objects.equals(tagId, that.tagId);
        }
        return result;
    }

    @Override
    public int hashCode() {
        return Objects.hash(versionId, tagId);
    }

    /**
     * Composite key class for VersionTag.
     */
    public static class VersionTagId implements Serializable {
        private UUID versionId;
        private UUID tagId;

        public VersionTagId() {
        }

        public VersionTagId(UUID versionId, UUID tagId) {
            this.versionId = versionId;
            this.tagId = tagId;
        }

        public UUID getVersionId() {
            return versionId;
        }

        public void setVersionId(UUID versionId) {
            this.versionId = versionId;
        }

        public UUID getTagId() {
            return tagId;
        }

        public void setTagId(UUID tagId) {
            this.tagId = tagId;
        }

        @Override
        public boolean equals(Object o) {
            boolean result = false;
            if (this == o) {
                result = true;
            } else if (o != null && getClass() == o.getClass()) {
                VersionTagId that = (VersionTagId) o;
                result = Objects.equals(versionId, that.versionId) && Objects.equals(tagId, that.tagId);
            }
            return result;
        }

        @Override
        public int hashCode() {
            return Objects.hash(versionId, tagId);
        }
    }
}
