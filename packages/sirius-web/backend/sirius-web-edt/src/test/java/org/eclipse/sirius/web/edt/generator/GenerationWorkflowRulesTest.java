/**
 * Copyright (c) 2026 Obeo.
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sirius.web.edt.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

public class GenerationWorkflowRulesTest {

    @Test
    public void harnessDevInitialDefaultsUseExvtAsctgAndMscigt() {
        assertThat(GenerationWorkflowRules.defaultPhases(GenerationWorkflowMode.HARNESS_DEV, false))
                .containsExactly("EXVT", "ASCTG", "MSCIGT");
    }

    @Test
    public void integrationDefaultsUseExvtAndLdp() {
        assertThat(GenerationWorkflowRules.defaultPhases(GenerationWorkflowMode.INTEGRATION, false))
                .containsExactly("EXVT", "LDP");
    }

    @Test
    public void integrationInitialAllowsOptionalCsmgvt() {
        assertThatCode(() -> GenerationWorkflowRules.validate(GenerationWorkflowMode.INTEGRATION, List.of("EXVT", "CSMGVT", "LDP"), false))
                .doesNotThrowAnyException();
    }

    @Test
    public void harnessDevContinueOnlyAllowsCsmgvt() {
        assertThatThrownBy(() -> GenerationWorkflowRules.validate(GenerationWorkflowMode.HARNESS_DEV, List.of("MSCIGT"), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("HARNESS_DEV continue runs only allow CSMGVT");
    }
}
