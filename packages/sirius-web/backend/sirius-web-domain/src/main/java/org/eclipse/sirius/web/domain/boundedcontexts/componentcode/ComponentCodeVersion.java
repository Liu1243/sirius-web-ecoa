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

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Entity representing a version of component code.
 *
 * @author Obeo
 */
@Table("component_code_version")
public class ComponentCodeVersion implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column("project_id")
    private UUID projectId;

    @Column("component_id")
    private String componentId;

    @Column("component_name")
    private String componentName;

    @Column("version_name")
    private String versionName;

    @Column("code_content")
    private String codeContent;

    @Column("commit_message")
    private String commitMessage;

    @Column("author")
    private String author;

    @Column("created_at")
    private Instant createdAt;

    @Column("model_version_id")
    private String modelVersionId;

    @Column("import_status")
    private String importStatus;

    @Transient
    private boolean isNew;

    @MappedCollection(idColumn = "version_id")
    private Set<VersionTag> tags = new HashSet<>();

    private ComponentCodeVersion(Builder builder) {
        this.id = builder.id;
        this.projectId = builder.projectId;
        this.componentId = builder.componentId;
        this.componentName = builder.componentName;
        this.versionName = builder.versionName;
        this.codeContent = builder.codeContent;
        this.commitMessage = builder.commitMessage;
        this.author = builder.author;
        this.createdAt = builder.createdAt;
        this.modelVersionId = builder.modelVersionId;
        this.importStatus = builder.importStatus;
        this.isNew = true;
    }

    public ComponentCodeVersion() {
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Builder for ComponentCodeVersion.
     */
    public static class Builder {
        private UUID id;
        private UUID projectId;
        private String componentId;
        private String componentName;
        private String versionName;
        private String codeContent;
        private String commitMessage;
        private String author;
        private Instant createdAt;
        private String modelVersionId;
        private String importStatus;

        public Builder id(UUID theId) {
            this.id = theId;
            return this;
        }

        public Builder projectId(UUID theProjectId) {
            this.projectId = theProjectId;
            return this;
        }

        public Builder componentId(String theComponentId) {
            this.componentId = theComponentId;
            return this;
        }

        public Builder componentName(String theComponentName) {
            this.componentName = theComponentName;
            return this;
        }

        public Builder versionName(String theVersionName) {
            this.versionName = theVersionName;
            return this;
        }

        public Builder codeContent(String theCodeContent) {
            this.codeContent = theCodeContent;
            return this;
        }

        public Builder commitMessage(String theCommitMessage) {
            this.commitMessage = theCommitMessage;
            return this;
        }

        public Builder author(String theAuthor) {
            this.author = theAuthor;
            return this;
        }

        public Builder createdAt(Instant theCreatedAt) {
            this.createdAt = theCreatedAt;
            return this;
        }

        public Builder modelVersionId(String theModelVersionId) {
            this.modelVersionId = theModelVersionId;
            return this;
        }

        public Builder importStatus(String theImportStatus) {
            this.importStatus = theImportStatus;
            return this;
        }

        public ComponentCodeVersion build() {
            return new ComponentCodeVersion(this);
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public String getComponentId() {
        return componentId;
    }

    public void setComponentId(String componentId) {
        this.componentId = componentId;
    }

    public String getComponentName() {
        return componentName;
    }

    public void setComponentName(String componentName) {
        this.componentName = componentName;
    }

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public String getCodeContent() {
        return codeContent;
    }

    public void setCodeContent(String codeContent) {
        this.codeContent = codeContent;
    }

    public String getCommitMessage() {
        return commitMessage;
    }

    public void setCommitMessage(String commitMessage) {
        this.commitMessage = commitMessage;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getModelVersionId() {
        return modelVersionId;
    }

    public void setModelVersionId(String modelVersionId) {
        this.modelVersionId = modelVersionId;
    }

    public String getImportStatus() {
        return importStatus;
    }

    public void setImportStatus(String importStatus) {
        this.importStatus = importStatus;
    }

    public Set<VersionTag> getTags() {
        return tags;
    }

    public void setTags(Set<VersionTag> tags) {
        this.tags = tags;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public void markAsNew() {
        this.isNew = true;
    }

    @Override
    public boolean equals(Object o) {
        boolean result = false;
        if (this == o) {
            result = true;
        } else if (o != null && getClass() == o.getClass()) {
            ComponentCodeVersion that = (ComponentCodeVersion) o;
            result = Objects.equals(id, that.id);
        }
        return result;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
