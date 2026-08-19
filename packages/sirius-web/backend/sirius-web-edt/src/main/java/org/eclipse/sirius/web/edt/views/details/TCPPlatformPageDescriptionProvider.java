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
 * Details page for edttcp::TCPPlatform — TCP endpoint config (IP address + port) for one platform.
 */
@Service("edtTCPPlatformPageDescriptionProvider")
@SuppressWarnings("checkstyle:MultipleStringLiterals")
public class TCPPlatformPageDescriptionProvider implements IPageDescriptionProvider {

    private final IEdtMessageService messageService;

    public TCPPlatformPageDescriptionProvider(IEdtMessageService messageService) {
        this.messageService = Objects.requireNonNull(messageService);
    }

    @Override
    public PageDescription getPageDescription(IColorProvider colorProvider) {
        var group = new FormBuilders().newGroupDescription()
                .name("Core Properties")
                .labelExpression(this.messageService.coreProperties())
                .semanticCandidatesExpression("aql:self")
                .displayMode(GroupDisplayMode.LIST)
                .build();

        group.getChildren().add(this.newTextfieldWidget("Name", "name", true,
                "edtTip.tcpPlatform.name"));
        group.getChildren().add(this.newTextfieldWidget("Address (IP)", "address", true,
                "edtTip.tcpPlatform.address"));
        group.getChildren().add(this.newNumericTextfieldWidget("Port (1-65535)", "port", true,
                "edtTip.tcpPlatform.port"));

        return new FormBuilders().newPageDescription()
                .name("Edt TCP Platform")
                .domainType("edttcp:TCPPlatform")
                .labelExpression("aql:self.name")
                .groups(group)
                .build();
    }

    private WidgetDescription newTextfieldWidget(String label, String featureName, boolean required, String help) {
        return this.newTextfield(
                label,
                "aql:self." + featureName,
                required
                        ? "aql:if self." + featureName + " <> null and self." + featureName + " <> '' then null else 'ERROR: " + label + " is required' endif"
                        : "aql:null",
                help,
                this.newSetValue(featureName, "aql:newValue"));
    }

    private WidgetDescription newNumericTextfieldWidget(String label, String featureName, boolean required, String help) {
        return this.newTextfield(
                label,
                "aql:self." + featureName,
                required
                        ? "aql:if self." + featureName + " <> 0 then null else 'ERROR: " + label + " must be > 0' endif"
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
                .body(new ViewBuilders().newChangeContext().expression("aql:self").children(bodyOperations).build());
        if (help != null) {
            builder.helpExpression("aql:'" + help.replace("'", "''") + "'");
        }
        return builder.build();
    }

    private Operation newSetValue(String featureName, String valueExpression) {
        return new ViewBuilders().newSetValue().featureName(featureName).valueExpression(valueExpression).build();
    }

} // TCPPlatformPageDescriptionProvider
