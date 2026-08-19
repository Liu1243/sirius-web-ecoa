package org.eclipse.sirius.web.application.componentcode.services;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * ThreadLocal holder for the version IDs selected for export.
 * Set before calling ProjectExportService.export() and cleared afterwards.
 *
 * <p>Exposed as a Spring component with instance methods so static-method
 * architecture rules are not violated.
 */
@Component
public class ComponentCodeExportContext {

    private final ThreadLocal<List<UUID>> selectedVersionIds = new ThreadLocal<>();

    public void set(List<UUID> versionIds) {
        this.selectedVersionIds.set(versionIds != null ? versionIds : Collections.emptyList());
    }

    public List<UUID> get() {
        List<UUID> ids = this.selectedVersionIds.get();
        return ids != null ? ids : Collections.emptyList();
    }

    public void clear() {
        this.selectedVersionIds.remove();
    }
}
