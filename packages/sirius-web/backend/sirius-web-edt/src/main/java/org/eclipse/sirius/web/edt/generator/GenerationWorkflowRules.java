/**
 * Copyright (c) 2026 Obeo.
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sirius.web.edt.generator;

import java.util.List;
import java.util.Objects;

public final class GenerationWorkflowRules {

    // DIRECT_DEV: component direct development (no harness)
    private static final List<String> DIRECT_DEV_INITIAL_PHASES = List.of("EXVT", "MSCIGT");
    private static final List<String> DIRECT_DEV_CONTINUE_PHASES = List.of("CSMGVT", "LDP");

    // HARNESS_DEV: component harness test development
    private static final List<String> HARNESS_DEV_INITIAL_PHASES = List.of("EXVT", "ASCTG", "MSCIGT");
    private static final List<String> HARNESS_DEV_CONTINUE_PHASES = List.of("CSMGVT");

    // INTEGRATION: application-level integration / system verification
    // Note: MSCIGT is executed implicitly by Python backend when selectedVersions are provided
    private static final List<String> INTEGRATION_DEFAULT_PHASES = List.of("EXVT", "LDP");
    private static final List<String> INTEGRATION_INITIAL_PHASES = List.of("EXVT", "MSCIGT", "CSMGVT", "LDP");
    private static final List<String> INTEGRATION_CONTINUE_PHASES = List.of("CSMGVT", "LDP");

    private GenerationWorkflowRules() {
        // Utility class
    }

    public static List<String> defaultPhases(GenerationWorkflowMode mode, boolean continuing) {
        Objects.requireNonNull(mode);
        return switch (mode) {
            case DIRECT_DEV -> continuing ? DIRECT_DEV_CONTINUE_PHASES : DIRECT_DEV_INITIAL_PHASES;
            case HARNESS_DEV -> continuing ? HARNESS_DEV_CONTINUE_PHASES : HARNESS_DEV_INITIAL_PHASES;
            case INTEGRATION -> continuing ? INTEGRATION_CONTINUE_PHASES : INTEGRATION_DEFAULT_PHASES;
        };
    }

    public static void validate(GenerationWorkflowMode mode, List<String> selectedPhases, boolean continuing) {
        Objects.requireNonNull(mode);
        List<String> phases = selectedPhases == null ? List.of() : selectedPhases;

        if (mode == GenerationWorkflowMode.DIRECT_DEV && !continuing) {
            ensureOnlyAllowed(phases, DIRECT_DEV_INITIAL_PHASES, "DIRECT_DEV initial runs only allow EXVT and MSCIGT");
            return;
        }
        if (mode == GenerationWorkflowMode.DIRECT_DEV) {
            ensureOnlyAllowed(phases, DIRECT_DEV_CONTINUE_PHASES, "DIRECT_DEV continue runs only allow CSMGVT and LDP");
            return;
        }
        if (mode == GenerationWorkflowMode.HARNESS_DEV && !continuing) {
            ensureOnlyAllowed(phases, HARNESS_DEV_INITIAL_PHASES, "HARNESS_DEV initial runs only allow EXVT, ASCTG and MSCIGT");
            return;
        }
        if (mode == GenerationWorkflowMode.HARNESS_DEV) {
            ensureOnlyAllowed(phases, HARNESS_DEV_CONTINUE_PHASES, "HARNESS_DEV continue runs only allow CSMGVT");
            return;
        }
        ensureOnlyAllowed(phases, continuing ? INTEGRATION_CONTINUE_PHASES : INTEGRATION_INITIAL_PHASES,
                continuing ? "INTEGRATION continue runs only allow CSMGVT and LDP" : "INTEGRATION initial runs only allow EXVT, MSCIGT, CSMGVT and LDP");
    }

    private static void ensureOnlyAllowed(List<String> selectedPhases, List<String> allowedPhases, String message) {
        boolean hasInvalidPhase = selectedPhases.stream().anyMatch(phase -> !allowedPhases.contains(phase));
        if (hasInvalidPhase) {
            throw new IllegalArgumentException(message);
        }
    }
}
