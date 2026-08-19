package org.eclipse.sirius.web.edt.importexport;

import java.util.List;
import java.util.UUID;

import org.eclipse.sirius.components.core.api.IInput;

/**
 * Input for importing a Sirius Web project ZIP into an existing EDT project.
 *
 * <p>The ZIP must be a standard Sirius Web project archive (produced by "Download project").
 * It contains {@code {name}/documents/*.json} for the EMF resources and optionally
 * {@code {name}/component-code/} for component code versions.
 *
 * <p>Only the {@link Steps} document is imported (replacing the existing one).
 * ComponentCode versions from {@code selectedVersionIds} are imported via the service layer.
 */
public record EdtImportSiriusWebZipInput(
        UUID id,
        /** Raw bytes of the uploaded Sirius Web project ZIP. */
        byte[] zipBytes,
        /** Display name used as the resource metadata adapter label. */
        String projectName,
        /** The project entity UUID — used for component-code association. */
        UUID projectId,
        /** UUIDs of component code versions to import. May be empty. */
        List<String> selectedVersionIds
) implements IInput {
}
