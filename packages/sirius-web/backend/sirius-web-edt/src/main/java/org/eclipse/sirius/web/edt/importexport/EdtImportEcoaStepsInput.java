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
package org.eclipse.sirius.web.edt.importexport;

import java.util.Objects;
import java.util.UUID;

import org.eclipse.sirius.components.core.api.IInput;

/**
 * Input used to trigger an ECOA Steps ZIP import via the editing context event handler. The zip bytes are passed
 * directly to avoid writing to a temp file before dispatch.
 */
public record EdtImportEcoaStepsInput(UUID id, byte[] zipBytes, String projectName) implements IInput {

    public EdtImportEcoaStepsInput {
        Objects.requireNonNull(id);
        Objects.requireNonNull(zipBytes);
    }
}
