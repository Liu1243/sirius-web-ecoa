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
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity representing a tag for component code versions.
 *
 * @author Obeo
 */
@Table("component_code_tag")
public class ComponentCodeTag implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column("project_id")
    private UUID projectId;

    @Column("name")
    private String name;

    @Column("color")
    private String color;

    @Column("created_at")
    private Instant createdAt;

    @Transient
    private boolean isNew;

    private ComponentCodeTag(Builder builder) {
        this.id = builder.id;
        this.projectId = builder.projectId;
        this.name = builder.name;
        this.color = builder.color;
        this.createdAt = builder.createdAt;
        this.isNew = true;
    }

    public ComponentCodeTag() {
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Builder for ComponentCodeTag.
     */
    public static class Builder {
        private UUID id;
        private UUID projectId;
        private String name;
        private String color;
        private Instant createdAt;

        public Builder id(UUID theId) {
            this.id = theId;
            return this;
        }

        public Builder projectId(UUID theProjectId) {
            this.projectId = theProjectId;
            return this;
        }

        public Builder name(String theName) {
            this.name = theName;
            return this;
        }

        public Builder color(String theColor) {
            this.color = theColor;
            return this;
        }

        public Builder createdAt(Instant theCreatedAt) {
            this.createdAt = theCreatedAt;
            return this;
        }

        public ComponentCodeTag build() {
            return new ComponentCodeTag(this);
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
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
            ComponentCodeTag that = (ComponentCodeTag) o;
            result = Objects.equals(id, that.id);
        }
        return result;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
