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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.sirius.components.view.ChangeContext;
import org.eclipse.sirius.components.view.Operation;
import org.eclipse.sirius.components.view.SetValue;
import org.eclipse.sirius.components.view.form.RadioDescription;
import org.eclipse.sirius.components.view.form.TextfieldDescription;
import org.eclipse.sirius.components.view.form.WidgetDescription;
import org.eclipse.sirius.web.edt.messages.IEdtMessageService;
import org.junit.jupiter.api.Test;

class LogicalComputingPageDescriptionProviderTests {

    private final IEdtMessageService messageService = new IEdtMessageService.NoOp();

    @Test
    void givenLogicalComputingPlatformPageWhenBuiltThenCorePropertiesExposePlatformIdentifiers() {
        var pageDescription = new LogicalComputingPlatformPageDescriptionProvider(this.messageService).getPageDescription(null);

        Map<String, WidgetDescription> widgetsByName = pageDescription.getGroups().get(0).getChildren().stream()
                .filter(WidgetDescription.class::isInstance)
                .map(WidgetDescription.class::cast)
                .collect(Collectors.toMap(WidgetDescription::getName, Function.identity()));

        assertThat(widgetsByName).containsKeys("Id", "ELI Platform Id", "Multicast Interface IP (intra-platform only, for nodes_deployment.xml)");

        assertThat(widgetsByName.get("Id")).isInstanceOf(TextfieldDescription.class);
        assertThat(((TextfieldDescription) widgetsByName.get("Id")).getValueExpression()).isEqualTo("aql:self.id");

        assertThat(widgetsByName.get("ELI Platform Id")).isInstanceOf(TextfieldDescription.class);
        assertThat(((TextfieldDescription) widgetsByName.get("ELI Platform Id")).getValueExpression())
                .isEqualTo("aql:self.eLIPlatformId");

        assertThat(widgetsByName.get("Multicast Interface IP (intra-platform only, for nodes_deployment.xml)")).isInstanceOf(TextfieldDescription.class);
        assertThat(((TextfieldDescription) widgetsByName.get("Multicast Interface IP (intra-platform only, for nodes_deployment.xml)")).getValueExpression()).isEqualTo("aql:self.ipAddress");
    }

    @Test
    void givenLogicalComputingNodePageWhenBuiltThenCorePropertiesExposeRuntimeConfiguration() {
        var pageDescription = new LogicalComputingNodePageDescriptionProvider(this.messageService).getPageDescription(null);

        List<WidgetDescription> widgets = pageDescription.getGroups().get(0).getChildren().stream()
                .filter(WidgetDescription.class::isInstance)
                .map(WidgetDescription.class::cast)
                .toList();
        Map<String, WidgetDescription> widgetsByName = widgets.stream()
                .collect(Collectors.toMap(WidgetDescription::getName, Function.identity()));

        assertThat(widgets).extracting(WidgetDescription::getName).containsExactly(
                "Id",
                "Endianess Type",
                "Module Switch Time Micro Seconds",
                "Available Memory Giga Bytes",
                "Os Name",
                "Os Version",
                "Node IP Address (intra-platform only, not for cross-platform TCP/UDP)");

        assertThat(widgetsByName.get("Id")).isInstanceOf(TextfieldDescription.class);
        assertThat(((TextfieldDescription) widgetsByName.get("Id")).getValueExpression()).isEqualTo("aql:self.id");

        assertThat(widgetsByName.get("Endianess Type")).isInstanceOf(RadioDescription.class);
        assertThat(((RadioDescription) widgetsByName.get("Endianess Type")).getValueExpression())
                .isEqualTo("aql:self.EndianessType");
        assertThat(((RadioDescription) widgetsByName.get("Endianess Type")).getCandidatesExpression())
                .isEqualTo("aql:self.eClass().getEStructuralFeature('EndianessType').eType.eLiterals->collect(l | l.instance)");
        assertThat(((RadioDescription) widgetsByName.get("Endianess Type")).getCandidateLabelExpression())
                .isEqualTo("aql:candidate.toString()");
        assertThat(((RadioDescription) widgetsByName.get("Endianess Type")).getBody()).singleElement()
                .isInstanceOf(ChangeContext.class)
                .extracting(ChangeContext.class::cast)
                .extracting(Operation::getChildren)
                .asList()
                .singleElement()
                .isInstanceOf(SetValue.class)
                .extracting(SetValue.class::cast)
                .extracting(SetValue::getValueExpression)
                .isEqualTo("aql:newValue");

        assertThat(widgetsByName.get("Module Switch Time Micro Seconds")).isInstanceOf(TextfieldDescription.class);
        assertThat(((TextfieldDescription) widgetsByName.get("Module Switch Time Micro Seconds")).getValueExpression())
                .isEqualTo("aql:self.ModuleSwitchTimeMicroSeconds");

        assertThat(widgetsByName.get("Available Memory Giga Bytes")).isInstanceOf(TextfieldDescription.class);
        assertThat(((TextfieldDescription) widgetsByName.get("Available Memory Giga Bytes")).getValueExpression())
                .isEqualTo("aql:self.AvailableMemoryGigaBytes");

        assertThat(widgetsByName.get("Os Name")).isInstanceOf(RadioDescription.class);
        assertThat(((RadioDescription) widgetsByName.get("Os Name")).getValueExpression())
                .isEqualTo("aql:self.osName");
        assertThat(((RadioDescription) widgetsByName.get("Os Name")).getCandidatesExpression())
                .isEqualTo("aql:self.eClass().getEStructuralFeature('osName').eType.eLiterals->collect(l | l.instance)");
        assertThat(((RadioDescription) widgetsByName.get("Os Name")).getCandidateLabelExpression())
                .isEqualTo("aql:candidate.toString()");
        assertThat(((RadioDescription) widgetsByName.get("Os Name")).getBody()).singleElement()
                .isInstanceOf(ChangeContext.class)
                .extracting(ChangeContext.class::cast)
                .extracting(Operation::getChildren)
                .asList()
                .singleElement()
                .isInstanceOf(SetValue.class)
                .extracting(SetValue.class::cast)
                .extracting(SetValue::getValueExpression)
                .isEqualTo("aql:newValue");

        assertThat(widgetsByName.get("Os Version")).isInstanceOf(TextfieldDescription.class);
        assertThat(((TextfieldDescription) widgetsByName.get("Os Version")).getValueExpression()).isEqualTo("aql:self.osVersion");

        assertThat(widgetsByName.get("Node IP Address (intra-platform only, not for cross-platform TCP/UDP)")).isInstanceOf(TextfieldDescription.class);
        assertThat(((TextfieldDescription) widgetsByName.get("Node IP Address (intra-platform only, not for cross-platform TCP/UDP)")).getValueExpression()).isEqualTo("aql:self.ipAddress");
    }
}
