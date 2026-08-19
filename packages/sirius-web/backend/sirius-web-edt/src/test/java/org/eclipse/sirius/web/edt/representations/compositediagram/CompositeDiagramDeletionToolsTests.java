/**
 * Copyright (c) 2026 Obeo.
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sirius.web.edt.representations.compositediagram;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.sirius.components.view.ChangeContext;
import org.eclipse.sirius.components.view.builder.generated.view.ViewBuilders;
import org.eclipse.sirius.components.view.builder.providers.DefaultColorProvider;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.diagram.EdgeDescription;
import org.eclipse.sirius.components.view.diagram.NodeDescription;
import org.eclipse.sirius.web.edt.representations.compositediagram.edgedescriptions.ServiceLinkEdgeDescriptionProvider;
import org.eclipse.sirius.web.edt.representations.compositediagram.nodedescriptions.ComponentNodeDescriptionProvider;
import org.eclipse.sirius.web.edt.services.EdtColorPaletteProvider;
import org.junit.jupiter.api.Test;

public class CompositeDiagramDeletionToolsTests {

    @Test
    public void givenComponentNodeWhenCreatedThenDeleteToolUsesSemanticDeletionService() {
        NodeDescription componentNode = new ComponentNodeDescriptionProvider(this.colorProvider()).create();

        assertThat(componentNode.getPalette()).isNotNull();
        assertThat(componentNode.getPalette().getDeleteTool()).isNotNull();
        assertThat(componentNode.getPalette().getDeleteTool().getName()).isEqualTo("Delete");
        assertThat(componentNode.getPalette().getDeleteTool().getBody()).singleElement()
                .isInstanceOf(ChangeContext.class)
                .extracting(ChangeContext.class::cast)
                .extracting(ChangeContext::getExpression)
                .isEqualTo("aql:self.deleteComponentAndRelatedLinks()");
    }

    @Test
    public void givenServiceLinkEdgeWhenCreatedThenDeleteToolUsesDefaultSemanticDeletion() {
        EdgeDescription serviceLinkEdge = new ServiceLinkEdgeDescriptionProvider(this.colorProvider()).create();

        assertThat(serviceLinkEdge.getPalette()).isNotNull();
        assertThat(serviceLinkEdge.getPalette().getDeleteTool()).isNotNull();
        assertThat(serviceLinkEdge.getPalette().getDeleteTool().getName()).isEqualTo("Delete");
        assertThat(serviceLinkEdge.getPalette().getDeleteTool().getBody()).singleElement()
                .isInstanceOf(ChangeContext.class)
                .extracting(ChangeContext.class::cast)
                .extracting(ChangeContext::getExpression)
                .isEqualTo("aql:self.defaultDelete()");
    }

    private IColorProvider colorProvider() {
        var view = new ViewBuilders().newView()
                .colorPalettes(new EdtColorPaletteProvider().getColorPalette())
                .build();
        return new DefaultColorProvider(view);
    }
}
