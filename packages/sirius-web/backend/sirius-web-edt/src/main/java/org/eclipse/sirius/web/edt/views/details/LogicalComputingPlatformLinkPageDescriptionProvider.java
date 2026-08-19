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
import org.eclipse.sirius.components.view.builder.generated.reference.ReferenceBuilders;
import org.eclipse.sirius.components.view.builder.generated.view.ViewBuilders;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.form.GroupDisplayMode;
import org.eclipse.sirius.components.view.form.PageDescription;
import org.eclipse.sirius.components.view.form.WidgetDescription;
import org.eclipse.sirius.components.view.widget.reference.ReferenceWidgetDescription;
import org.eclipse.sirius.web.edt.messages.IEdtMessageService;
import org.eclipse.sirius.web.edt.views.details.api.IPageDescriptionProvider;
import org.springframework.stereotype.Service;

/**
 * Details page for LogicalComputingPlatformLink.
 * <p>
 * From/To are platform selects; Protocol is a radio button (TCP/UDP);
 * Parameters File is a select filtered by protocol from Step5 bindings.
 * DDS is an optional checkbox that upgrades the chosen transport to DDS middleware,
 * with an associated Domain ID (0-232) field shown only when DDS is enabled.
 */
@Service("edtLogicalComputingPlatformLinkPageDescriptionProvider")
@SuppressWarnings("checkstyle:MultipleStringLiterals")
public class LogicalComputingPlatformLinkPageDescriptionProvider implements IPageDescriptionProvider {

    private final IEdtMessageService messageService;

    public LogicalComputingPlatformLinkPageDescriptionProvider(IEdtMessageService messageService) {
        this.messageService = Objects.requireNonNull(messageService);
    }

    @Override
    public PageDescription getPageDescription(IColorProvider colorProvider) {

        // --- Group 1: Core ---
        var coreGroup = new FormBuilders().newGroupDescription()
                .name("Core Properties")
                .labelExpression(this.messageService.coreProperties())
                .semanticCandidatesExpression("aql:self")
                .displayMode(GroupDisplayMode.LIST)
                .build();

        coreGroup.getChildren().add(this.newTextfieldWidget("Id", "id", true,
                "edtTip.platformLink.id"));
        coreGroup.getChildren().add(this.newNumericTextfieldWidget("Throughput (MB/s)", "throughputMegaBytesPerSecond", false,
                "edtTip.platformLink.throughput"));
        coreGroup.getChildren().add(this.newNumericTextfieldWidget("Latency (µs)", "latencyMicroSeconds", false,
                "edtTip.platformLink.latency"));

        // --- Group 2: Endpoints ---
        var endpointsGroup = new FormBuilders().newGroupDescription()
                .name("Platform Endpoints")
                .labelExpression("Platform Endpoints")
                .semanticCandidatesExpression("aql:self")
                .displayMode(GroupDisplayMode.LIST)
                .build();

        endpointsGroup.getChildren().add(this.newPlatformReferenceWidget("From Platform", "from"));
        endpointsGroup.getChildren().add(this.newPlatformReferenceWidget("To Platform", "to"));

        // --- Group 3: Transport Binding ---
        var transportGroup = new FormBuilders().newGroupDescription()
                .name("Transport Binding")
                .labelExpression("Transport Binding")
                .semanticCandidatesExpression("aql:self")
                .displayMode(GroupDisplayMode.LIST)
                .build();

        transportGroup.getChildren().add(this.newProtocolRadioWidget("edtTip.platformLink.protocol"));
        transportGroup.getChildren().add(this.newParametersFileSelectWidget("edtTip.platformLink.parametersFile"));
        transportGroup.getChildren().add(this.newDdsCheckboxWidget("edtTip.platformLink.enableDds"));
        transportGroup.getChildren().add(this.newDdsDomainIdWidget("edtTip.platformLink.ddsDomainId"));
        transportGroup.getChildren().add(this.newDdsTopicNameWidget("edtTip.platformLink.ddsTopicName"));

        return new FormBuilders().newPageDescription()
                .name("Edt Logical Computing Platform Link")
                .domainType("edtlogical:LogicalComputingPlatformLink")
                .labelExpression("aql:self.id")
                .groups(coreGroup, endpointsGroup, transportGroup)
                .build();
    }

    private ReferenceWidgetDescription newPlatformReferenceWidget(String label, String featureName) {
        var style = new ReferenceBuilders().newReferenceWidgetDescriptionStyle().build();

        return new ReferenceBuilders().newReferenceWidgetDescription()
                .name(label)
                .labelExpression(label)
                .referenceOwnerExpression("aql:self")
                .referenceNameExpression("aql:'" + featureName + "'")
                .style(style)
                .build();
    }

    /**
     * Radio buttons for transport protocols. DDS is a separate checkbox — only TCP and UDP appear here.
     */
    private WidgetDescription newProtocolRadioWidget(String help) {
        var builder = new FormBuilders().newRadioDescription()
                .name("Protocol")
                .labelExpression("Protocol")
                .valueExpression("aql:self.TransportBindingProtocol")
                .candidatesExpression("aql:Sequence{'TCP', 'UDP'}")
                .candidateLabelExpression("aql:candidate")
                .diagnosticsExpression("aql:null")
                .body(new ViewBuilders().newChangeContext()
                        .expression("aql:self")
                        .children(new ViewBuilders().newSetValue()
                                .featureName("TransportBindingProtocol")
                                .valueExpression("aql:newValue")
                                .build())
                        .build());
        if (help != null) {
            builder.helpExpression("aql:'" + help.replace("'", "''") + "'");
        }
        return builder.build();
    }

    /**
     * Dropdown that shows binding file names from Step5, filtered by the selected protocol:
     *   TCP → {name}.tcp-params.xml   (from Step5.TCPBindings)
     *   UDP → {name}.udp-binding.xml  (from Step5.UDPBindings)
     * Step5 is at self.eContainer().eContainer() (link → LogicalSystem → Step5).
     */
    private WidgetDescription newParametersFileSelectWidget(String help) {
        String candidatesExpr =
                "aql:if self.TransportBindingProtocol = 'TCP' "
                + "then self.eContainer().eContainer().TCPBindings->collect(b | b.name + '.tcp-params.xml') "
                + "else if self.TransportBindingProtocol = 'UDP' "
                + "then self.eContainer().eContainer().UDPBindings->collect(b | b.Name + '.udp-binding.xml')"
                + "else Sequence{} "
                + "endif endif";

        var builder = new FormBuilders().newSelectDescription()
                .name("Parameters File")
                .labelExpression("Parameters File")
                .valueExpression("aql:self.TransportBindingParameters")
                .candidatesExpression(candidatesExpr)
                .candidateLabelExpression("aql:candidate")
                .diagnosticsExpression("aql:null")
                .body(new ViewBuilders().newChangeContext()
                        .expression("aql:self")
                        .children(new ViewBuilders().newSetValue()
                                .featureName("TransportBindingParameters")
                                .valueExpression("aql:newValue")
                                .build())
                        .build());
        if (help != null) {
            builder.helpExpression("aql:'" + help.replace("'", "''") + "'");
        }
        return builder.build();
    }

    /**
     * Checkbox to enable DDS middleware on top of the chosen TCP/UDP transport.
     */
    private WidgetDescription newDdsCheckboxWidget(String help) {
        var builder = new FormBuilders().newCheckboxDescription()
                .name("Enable DDS middleware")
                .labelExpression("Enable DDS middleware (upgrade TCP/UDP to CycloneDDS)")
                .valueExpression("aql:self.useDDS")
                .diagnosticsExpression("aql:null")
                .body(new ViewBuilders().newChangeContext()
                        .expression("aql:self")
                        .children(new ViewBuilders().newSetValue()
                                .featureName("useDDS")
                                .valueExpression("aql:newValue")
                                .build())
                        .build());
        if (help != null) {
            builder.helpExpression("aql:'" + help.replace("'", "''") + "'");
        }
        return builder.build();
    }

    /**
     * Topic ID field (CycloneDDS domain ID), visible only when the DDS checkbox is checked.
     */
    private WidgetDescription newDdsDomainIdWidget(String help) {
        var builder = new FormBuilders().newTextfieldDescription()
                .name("Topic ID")
                .labelExpression("Topic ID (0-232)")
                .valueExpression("aql:self.ddsDomainId")
                .diagnosticsExpression("aql:null")
                .isEnabledExpression("aql:self.useDDS")
                .style(new FormBuilders().newTextfieldDescriptionStyle().build())
                .body(new ViewBuilders().newChangeContext()
                        .expression("aql:self")
                        .children(new ViewBuilders().newIf()
                                .conditionExpression("aql:newValue <> null and newValue <> ''")
                                .children(new ViewBuilders().newSetValue()
                                        .featureName("ddsDomainId")
                                        .valueExpression("aql:newValue")
                                        .build())
                                .build())
                        .build());
        if (help != null) {
            builder.helpExpression("aql:'" + help.replace("'", "''") + "'");
        }
        return builder.build();
    }

    private WidgetDescription newDdsTopicNameWidget(String help) {
        var builder = new FormBuilders().newTextfieldDescription()
                .name("DDS Topic Name")
                .labelExpression("Topic Name (default: LdpLocalPeerData)")
                .valueExpression("aql:self.ddsTopicName")
                .diagnosticsExpression("aql:null")
                .isEnabledExpression("aql:self.useDDS")
                .style(new FormBuilders().newTextfieldDescriptionStyle().build())
                .body(new ViewBuilders().newChangeContext()
                        .expression("aql:self")
                        .children(new ViewBuilders().newSetValue()
                                .featureName("ddsTopicName")
                                .valueExpression("aql:newValue")
                                .build())
                        .build());
        if (help != null) {
            builder.helpExpression("aql:'" + help.replace("'", "''") + "'");
        }
        return builder.build();
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
                .body(new ViewBuilders().newChangeContext().expression("aql:self").children(bodyOperations).build());
        if (help != null) {
            builder.helpExpression("aql:'" + help.replace("'", "''") + "'");
        }
        return builder.build();
    }

    private Operation newSetValue(String featureName, String valueExpression) {
        return new ViewBuilders().newSetValue().featureName(featureName).valueExpression(valueExpression).build();
    }

} // LogicalComputingPlatformLinkPageDescriptionProvider
