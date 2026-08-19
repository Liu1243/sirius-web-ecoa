/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.sirius.web.edt.views.details;

import java.util.Objects;

import org.eclipse.sirius.components.view.Operation;
import org.eclipse.sirius.components.view.builder.generated.form.FormBuilders;
import org.eclipse.sirius.components.view.builder.generated.view.ViewBuilders;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.form.GroupDisplayMode;
import org.eclipse.sirius.components.view.form.PageDescription;
import org.eclipse.sirius.components.view.form.WidgetDescription;
import org.eclipse.sirius.web.edt.messages.IEdtMessageService;
import org.eclipse.sirius.web.edt.views.details.api.IPageDescriptionProvider;
import org.springframework.stereotype.Service;

/**
 * Details page for logical processors. Renders the architecture type as radio
 * buttons (x86_64 / arm64) so users can declare the target platform for
 * cross-compilation.
 */
@Service("edtLogicalProcessorPageDescriptionProvider")
@SuppressWarnings("checkstyle:MultipleStringLiterals")
public class LogicalProcessorPageDescriptionProvider implements IPageDescriptionProvider {

    // Fixed architecture options. The ECOA standard stores type as a free-form
    // string; we constrain it here to the two platforms supported by the
    // cross-compilation toolchain.
    private static final String ARCH_X86_64 = "x86_64";

    private static final String ARCH_ARM64 = "arm64";

    private final IEdtMessageService messageService;

    public LogicalProcessorPageDescriptionProvider(IEdtMessageService messageService) {
        this.messageService = Objects.requireNonNull(messageService);
    }

    @Override
    public PageDescription getPageDescription(IColorProvider colorProvider) {
        var corePropertiesGroupDescription = new FormBuilders().newGroupDescription()
                .name("Core Properties")
                .labelExpression(this.messageService.coreProperties())
                .semanticCandidatesExpression("aql:self")
                .displayMode(GroupDisplayMode.LIST)
                .build();

        corePropertiesGroupDescription.getChildren().add(this.newNumericTextfieldWidget("Number", "number", true,
                "edtTip.logicalProcessor.number"));
        corePropertiesGroupDescription.getChildren().add(this.newArchRadioWidget("edtTip.logicalProcessor.type"));
        corePropertiesGroupDescription.getChildren().add(this.newNumericTextfieldWidget("Step Duration Nano Seconds", "StepDurationNanoSeconds", true,
                "edtTip.logicalProcessor.stepDuration"));

        return new FormBuilders().newPageDescription()
                .name("Edt Logical Processor")
                .domainType("edtlogical:LogicalProcessor")
                .labelExpression("aql:self.number")
                .groups(corePropertiesGroupDescription)
                .build();
    }

    /**
     * Radio widget for the processor architecture type (x86_64 / arm64).
     *
     * <p>The ECOA {@code type} attribute is a plain string. We provide a fixed
     * candidate set instead of deriving it from an EEnum so that the UI
     * constrains the value to architectures the cross-compilation toolchain
     * actually supports.</p>
     */
    private WidgetDescription newArchRadioWidget(String help) {
        // AQL sequence literal — both strings are treated as valid candidates.
        String candidatesExpr = "aql:Sequence{'" + ARCH_X86_64 + "', '" + ARCH_ARM64 + "'}";

        var builder = new FormBuilders().newRadioDescription()
                .name("Processor Type")
                .labelExpression("Processor Type")
                .valueExpression("aql:self.type")
                .candidatesExpression(candidatesExpr)
                .candidateLabelExpression("aql:candidate")
                .diagnosticsExpression(
                        "aql:if self.type <> null and self.type <> '' then null else 'ERROR: Processor Type is required' endif")
                .body(
                        new ViewBuilders().newChangeContext()
                                .expression("aql:self")
                                .children(this.newSetValue("type", "aql:newValue"))
                                .build());
        if (help != null) {
            builder.helpExpression("aql:'" + help.replace("'", "''") + "'");
        }
        return builder.build();
    }

    private WidgetDescription newNumericTextfieldWidget(String label, String featureName, boolean required, String help) {
        return this.newTextfield(
                label,
                "aql:self." + featureName,
                required
                        ? "aql:if self." + featureName + " <> null then null else 'ERROR: " + label + " is required' endif"
                        : "aql:null",
                help,
                new ViewBuilders().newIf()
                        .conditionExpression("aql:newValue <> null and newValue <> ''")
                        .children(this.newSetValue(featureName, "aql:newValue"))
                        .build());
    }

    private WidgetDescription newTextfield(String label, String valueExpression, String diagnosticsExpression, String help, Operation... bodyOperations) {
        var builder = new FormBuilders().newTextfieldDescription()
                .name(label)
                .labelExpression(label)
                .valueExpression(valueExpression)
                .diagnosticsExpression(diagnosticsExpression)
                .style(new FormBuilders().newTextfieldDescriptionStyle().build())
                .body(
                        new ViewBuilders().newChangeContext()
                                .expression("aql:self")
                                .children(bodyOperations)
                                .build());
        if (help != null) {
            builder.helpExpression("aql:'" + help.replace("'", "''") + "'");
        }
        return builder.build();
    }

    private Operation newSetValue(String featureName, String valueExpression) {
        return new ViewBuilders().newSetValue()
                .featureName(featureName)
                .valueExpression(valueExpression)
                .build();
    }
}
