/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.sirius.web.edt.representations.logicalsystemdiagram.nodedescriptions;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.sirius.components.view.builder.generated.view.ViewBuilders;
import org.eclipse.sirius.components.view.builder.providers.DefaultColorProvider;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.diagram.HeaderSeparatorDisplayMode;
import org.eclipse.sirius.components.view.diagram.InsideLabelPosition;
import org.eclipse.sirius.components.view.diagram.ListLayoutStrategyDescription;
import org.eclipse.sirius.components.view.diagram.NodeDescription;
import org.eclipse.sirius.web.edt.services.EdtColorPaletteProvider;
import org.junit.jupiter.api.Test;

public class LogicalPlatformNodeDescriptionProviderTests {

    @Test
    public void givenLogicalSystemContainersWhenCreatedThenHeadersReserveDedicatedSpace() {
        NodeDescription platformNode = new LogicalPlatformNodeDescriptionProvider(this.colorProvider()).create();
        NodeDescription logicalNode = this.childNamed(platformNode, LogicalPlatformNodeDescriptionProvider.LOGICAL_NODE_NAME);
        NodeDescription protectionDomain = this.childNamed(platformNode,
                LogicalPlatformNodeDescriptionProvider.LOGICAL_NODE_NAME,
                LogicalPlatformNodeDescriptionProvider.PROTECTION_DOMAIN_NAME);

        assertThat(platformNode.getInsideLabel().getPosition()).isEqualTo(InsideLabelPosition.TOP_CENTER);
        assertThat(platformNode.getInsideLabel().getStyle().isWithHeader()).isTrue();
        assertThat(platformNode.getInsideLabel().getStyle().getHeaderSeparatorDisplayMode())
                .isEqualTo(HeaderSeparatorDisplayMode.IF_CHILDREN);

        assertThat(logicalNode.getInsideLabel().getPosition()).isEqualTo(InsideLabelPosition.TOP_CENTER);
        assertThat(logicalNode.getInsideLabel().getStyle().isWithHeader()).isTrue();
        assertThat(logicalNode.getInsideLabel().getStyle().getHeaderSeparatorDisplayMode())
                .isEqualTo(HeaderSeparatorDisplayMode.IF_CHILDREN);

        assertThat(protectionDomain.getInsideLabel().getPosition()).isEqualTo(InsideLabelPosition.TOP_CENTER);
        assertThat(protectionDomain.getInsideLabel().getStyle().isWithHeader()).isTrue();
        assertThat(protectionDomain.getInsideLabel().getStyle().getHeaderSeparatorDisplayMode())
                .isEqualTo(HeaderSeparatorDisplayMode.NEVER);
    }

    @Test
    public void givenProtectionDomainListWhenCreatedThenDeployedInstancesUseGrowableHeaderAwareNodes() {
        NodeDescription platformNode = new LogicalPlatformNodeDescriptionProvider(this.colorProvider()).create();
        NodeDescription protectionDomain = this.childNamed(platformNode,
                LogicalPlatformNodeDescriptionProvider.LOGICAL_NODE_NAME,
                LogicalPlatformNodeDescriptionProvider.PROTECTION_DOMAIN_NAME);
        NodeDescription deployedModule = this.childNamed(platformNode,
                LogicalPlatformNodeDescriptionProvider.LOGICAL_NODE_NAME,
                LogicalPlatformNodeDescriptionProvider.PROTECTION_DOMAIN_NAME,
                LogicalPlatformNodeDescriptionProvider.DEPLOYED_MODULE_INSTANCE_NAME);
        NodeDescription deployedTrigger = this.childNamed(platformNode,
                LogicalPlatformNodeDescriptionProvider.LOGICAL_NODE_NAME,
                LogicalPlatformNodeDescriptionProvider.PROTECTION_DOMAIN_NAME,
                LogicalPlatformNodeDescriptionProvider.DEPLOYED_TRIGGER_INSTANCE_NAME);

        ListLayoutStrategyDescription layoutStrategy = (ListLayoutStrategyDescription) protectionDomain.getStyle().getChildrenLayoutStrategy();

        assertThat(layoutStrategy.getGrowableNodes())
                .extracting(NodeDescription::getName)
                .containsExactlyInAnyOrder(
                        LogicalPlatformNodeDescriptionProvider.DEPLOYED_MODULE_INSTANCE_NAME,
                        LogicalPlatformNodeDescriptionProvider.DEPLOYED_TRIGGER_INSTANCE_NAME);

        assertThat(deployedModule.getInsideLabel().getPosition()).isEqualTo(InsideLabelPosition.TOP_CENTER);
        assertThat(deployedModule.getInsideLabel().getStyle().isWithHeader()).isTrue();
        assertThat(deployedModule.getInsideLabel().getStyle().getHeaderSeparatorDisplayMode())
                .isEqualTo(HeaderSeparatorDisplayMode.NEVER);
        assertThat(deployedModule.getDefaultHeightExpression()).isEqualTo("aql:96");

        assertThat(deployedTrigger.getInsideLabel().getPosition()).isEqualTo(InsideLabelPosition.TOP_CENTER);
        assertThat(deployedTrigger.getInsideLabel().getStyle().isWithHeader()).isTrue();
        assertThat(deployedTrigger.getInsideLabel().getStyle().getHeaderSeparatorDisplayMode())
                .isEqualTo(HeaderSeparatorDisplayMode.NEVER);
        assertThat(deployedTrigger.getDefaultHeightExpression()).isEqualTo("aql:96");
    }

    private NodeDescription childNamed(NodeDescription root, String... path) {
        NodeDescription current = root;
        for (String name : path) {
            current = current.getChildrenDescriptions().stream()
                    .filter(child -> name.equals(child.getName()))
                    .findFirst()
                    .orElseThrow();
        }
        return current;
    }

    private IColorProvider colorProvider() {
        var view = new ViewBuilders().newView()
                .colorPalettes(new EdtColorPaletteProvider().getColorPalette())
                .build();
        return new DefaultColorProvider(view);
    }
}
