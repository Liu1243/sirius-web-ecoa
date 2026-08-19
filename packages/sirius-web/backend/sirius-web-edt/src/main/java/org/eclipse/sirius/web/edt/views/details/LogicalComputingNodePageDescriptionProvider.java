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

import org.eclipse.sirius.components.view.builder.generated.form.FormBuilders;
import org.eclipse.sirius.components.view.builder.generated.reference.ReferenceBuilders;
import org.eclipse.sirius.components.view.builder.generated.view.ViewBuilders;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.Operation;
import org.eclipse.sirius.components.view.form.GroupDescription;
import org.eclipse.sirius.components.view.form.GroupDisplayMode;
import org.eclipse.sirius.components.view.form.PageDescription;
import org.eclipse.sirius.components.view.form.WidgetDescription;
import org.eclipse.sirius.components.view.widget.reference.ReferenceWidgetDescription;
import org.eclipse.sirius.web.edt.messages.IEdtMessageService;
import org.eclipse.sirius.web.edt.views.details.api.IPageDescriptionProvider;
import org.springframework.stereotype.Service;

/**
 * Details page for logical computing nodes, including node deployment IP mapping.
 */
@Service("edtLogicalComputingNodePageDescriptionProvider")
@SuppressWarnings("checkstyle:MultipleStringLiterals")
public class LogicalComputingNodePageDescriptionProvider implements IPageDescriptionProvider {

    private final IEdtMessageService messageService;

    public LogicalComputingNodePageDescriptionProvider(IEdtMessageService messageService) {
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

        corePropertiesGroupDescription.getChildren().add(this.newTextfieldWidget("Id", "id", true,
                "edtTip.logicalNode.id"));
        corePropertiesGroupDescription.getChildren().add(this.newEnumRadioWidget(
                "Endianess Type",
                "EndianessType",
                "edtTip.logicalNode.endianess"));
        corePropertiesGroupDescription.getChildren().add(this.newNumericTextfieldWidget("Module Switch Time Micro Seconds", "ModuleSwitchTimeMicroSeconds", true,
                "edtTip.logicalNode.switchTime"));
        corePropertiesGroupDescription.getChildren().add(this.newNumericTextfieldWidget("Available Memory Giga Bytes", "AvailableMemoryGigaBytes", true,
                "edtTip.logicalNode.availableMemory"));
        corePropertiesGroupDescription.getChildren().add(this.newEnumRadioWidget("Os Name", "osName",
                "edtTip.logicalNode.osName"));
        corePropertiesGroupDescription.getChildren().add(this.newTextfieldWidget("Os Version", "osVersion", false,
                "edtTip.logicalNode.osVersion"));
        corePropertiesGroupDescription.getChildren().add(this.newTextfieldWidget("Node IP Address (intra-platform only, not for cross-platform TCP/UDP)", "ipAddress", false,
                "edtTip.logicalNode.ipAddress"));

        var deploymentGroupDescription = this.getDeploymentGroupDescription();

        return new FormBuilders().newPageDescription()
                .name("Edt Logical Computing Node")
                .domainType("edtlogical:LogicalComputingNode")
                .labelExpression("aql:self.id")
                .groups(corePropertiesGroupDescription, deploymentGroupDescription)
                .build();
    }

    private GroupDescription getDeploymentGroupDescription() {
        var group = new FormBuilders().newGroupDescription()
                .name("Deployment")
                .labelExpression(this.messageService.protectionDomainLinks())
                .semanticCandidatesExpression("aql:self")
                .displayMode(GroupDisplayMode.LIST)
                .build();

        group.getChildren().add(this.newProtectionDomainLinkReferenceWidget());

        return group;
    }

    private ReferenceWidgetDescription newProtectionDomainLinkReferenceWidget() {
        var style = new ReferenceBuilders().newReferenceWidgetDescriptionStyle().build();

        // ProtectionDomainLink is a bidirectional many-valued reference to edtdeployment:ProtectionDomain
        return new ReferenceBuilders().newReferenceWidgetDescription()
                .name("Protection Domain Links")
                .labelExpression(this.messageService.protectionDomainLinks())
                .referenceOwnerExpression("aql:self")
                .referenceNameExpression("aql:'ProtectionDomainLink'")
                .style(style)
                .build();
    }

    private WidgetDescription newTextfieldWidget(String label, String featureName, boolean required, String help) {
        return this.newTextfield(
                label,
                "aql:self." + featureName,
                required
                        ? "aql:if self." + featureName + " <> null and self." + featureName
                                + " <> '' then null else 'ERROR: " + label + " is required' endif"
                        : "aql:null",
                help,
                this.newSetValue(featureName, "aql:newValue"));
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

    private WidgetDescription newEnumRadioWidget(String label, String featureName, String help) {
        var builder = new FormBuilders().newRadioDescription()
                .name(label)
                .labelExpression(label)
                .valueExpression("aql:self." + featureName)
                .candidatesExpression(this.enumInstancesExpression(featureName))
                .candidateLabelExpression("aql:candidate.toString()")
                .diagnosticsExpression("aql:null")
                .body(
                        new ViewBuilders().newChangeContext()
                                .expression("aql:self")
                                .children(this.newSetValue(featureName, "aql:newValue"))
                                .build());
        if (help != null) {
            builder.helpExpression("aql:'" + help.replace("'", "''") + "'");
        }
        return builder.build();
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

    private String enumInstancesExpression(String featureName) {
        return "aql:self.eClass().getEStructuralFeature('" + featureName + "').eType.eLiterals->collect(l | l.instance)";
    }

    private Operation newSetValue(String featureName, String valueExpression) {
        return new ViewBuilders().newSetValue()
                .featureName(featureName)
                .valueExpression(valueExpression)
                .build();
    }
}
