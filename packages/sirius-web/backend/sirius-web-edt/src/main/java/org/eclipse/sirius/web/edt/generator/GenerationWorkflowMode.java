/**
 * Copyright (c) 2026 Obeo.
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sirius.web.edt.generator;

public enum GenerationWorkflowMode {
    /** Component direct development: EXVT → MSCIGT → CODE_EDIT_REQUIRED → CSMGVT/LDP. */
    DIRECT_DEV,
    /** Component harness test development: EXVT → ASCTG → MSCIGT → CODE_EDIT_REQUIRED → CSMGVT. */
    HARNESS_DEV,
    /** Application-level integration / system verification: EXVT → MSCIGT → component overlay → CSMGVT/LDP. */
    INTEGRATION;

    /**
     * Parse a string value to a GenerationWorkflowMode. Returns DIRECT_DEV for null input.
     */
    public static GenerationWorkflowMode fromString(String value) {
        if (value == null) {
            return DIRECT_DEV;
        }
        return valueOf(value.toUpperCase());
    }
}
