package org.eclipse.sirius.web.application.componentcode.dto;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

public record ComponentCodeVersionDTO(
    UUID id,
    String componentId,
    String componentName,
    String versionName,
    String commitMessage,
    String author,
    String createdAt,
    String modelVersionId,
    List<ComponentCodeTagDTO> tags,
    String codeContent
) {
    public ComponentCodeVersionDTO(
            UUID id,
            String componentId,
            String componentName,
            String versionName,
            String commitMessage,
            String author,
            Instant createdAtInstant,
            String modelVersionId,
            List<ComponentCodeTagDTO> tags,
            String codeContent) {
        this(id, componentId, componentName, versionName, commitMessage, author,
             createdAtInstant != null ? DateTimeFormatter.ISO_INSTANT.format(createdAtInstant) : null,
             modelVersionId, tags, codeContent);
    }
}
