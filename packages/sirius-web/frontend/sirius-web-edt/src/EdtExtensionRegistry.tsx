/*******************************************************************************
 * Copyright (c) 2024, 2025 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/

import {
  ComponentExtension,
  DataExtension,
  ExtensionRegistry,
  WorkbenchViewContribution,
  workbenchViewContributionExtensionPoint,
} from '@eclipse-sirius/sirius-components-core';
import {
  ActionProps,
  DiagramNodeActionOverrideContribution,
  DiagramPaletteToolContributionProps,
  EdgeData,
  NodeData,
  ReactFlowPropsCustomizer,
  diagramNodeActionOverrideContributionExtensionPoint,
  diagramPaletteToolExtensionPoint,
  diagramRendererReactFlowPropsCustomizerExtensionPoint,
} from '@eclipse-sirius/sirius-components-diagrams';
import {
  OmniboxCommand,
  OmniboxCommandComponentProps,
  OmniboxCommandOverrideContribution,
  omniboxCommandOverrideContributionExtensionPoint,
} from '@eclipse-sirius/sirius-components-omnibox';
import {
  NavigationBarProps,
  navigationBarCenterContributionExtensionPoint,
  useCurrentProject,
} from '@eclipse-sirius/sirius-web-application';
import ArchitectureIcon from '@mui/icons-material/Architecture';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Typography from '@mui/material/Typography';
import { Edge, Node, ReactFlowProps } from '@xyflow/react';
import { CompositeDiagramLegend } from './diagrams/legend/CompositeDiagramLegend';
import { EdtComponentLabelDetailNodeActionContribution } from './nodeactions/EdtComponentLabelDetailNodeActionContribution';
import { EdtComponentDiagramToolContribution } from './tools/EdtComponentDiagramToolContribution';
import { EdtComponentLabelDetailToolContribution } from './tools/EdtComponentLabelDetailToolContribution';
import { EdtView } from './workbenchviews/EdtView';

const EDT_NATURE = 'siriusComponents://nature?kind=edt';

const edtExtensionRegistry = new ExtensionRegistry();

const EdtProjectNavbarSubtitle = ({ children }: NavigationBarProps) => {
  const { project } = useCurrentProject();

  if (project.natures.filter((nature) => nature.name === EDT_NATURE).length > 0) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
        {children}
        <Typography variant="caption" style={{ whiteSpace: 'nowrap' }}>
          EDT Project
        </Typography>
      </div>
    );
  } else if (project) {
    return <div>{children}</div>;
  } else {
    return null;
  }
};

const navigationBarCenterContributionExtension: ComponentExtension<NavigationBarProps> = {
  identifier: `edt_${navigationBarCenterContributionExtensionPoint.identifier}`,
  Component: EdtProjectNavbarSubtitle,
};
edtExtensionRegistry.addComponent(
  navigationBarCenterContributionExtensionPoint,
  navigationBarCenterContributionExtension
);

import ComponentPropertyIcon from '../static/icon/component_property.png';
import ComponentReferenceIcon from '../static/icon/component_reference.png';
import ComponentServiceIcon from '../static/icon/component_service.png';

import { LogicalSystemDiagramLegend } from './diagrams/legend/LogicalSystemDiagramLegend';
import { ComponentImplementationDiagramLegend } from './diagrams/legend/ComponentImplementationDiagramLegend';

const reactFlowPropsCustomizer: ReactFlowPropsCustomizer = ({
  children,
  ...props
}: ReactFlowProps<Node<NodeData>, Edge<EdgeData>>) => {
  let isCompositeDiagram = false;
  let isLogicalSystemDiagram = false;
  let isComponentImplementationDiagram = false;

  if (props.nodes) {
    props.nodes.forEach((node) => {
      // Check for Composite Diagram elements
      if (
        node.data?.targetObjectKind?.includes('entity=Component') ||
        node.data?.targetObjectKind?.includes('entity=ComponentService') ||
        node.data?.targetObjectKind?.includes('entity=ComponentReference') ||
        node.data?.targetObjectKind?.includes('entity=ComponentProperty')
      ) {
        isCompositeDiagram = true;
      }

      // Check for Logical System Diagram elements
      if (
        node.data?.targetObjectKind?.includes('LogicalComputingPlatform') ||
        node.data?.targetObjectKind?.includes('LogicalComputingNode') ||
        node.data?.targetObjectKind?.includes('ProtectionDomain') ||
        node.data?.targetObjectKind?.includes('DeployedModuleInstance') ||
        node.data?.targetObjectKind?.includes('DeployedTriggerInstance') ||
        node.data?.targetObjectKind?.includes('domain=edtlogical') ||
        node.data?.targetObjectKind?.includes('domain=edtdeployment')
      ) {
        // Exclude links if they somehow appear as nodes (unlikely but safe)
        if (!node.data?.targetObjectKind?.includes('Link')) {
          isLogicalSystemDiagram = true;
        }
      }

      // Check for Component Implementation Diagram elements
      if (
        node.data?.targetObjectKind?.includes('ModuleInstance') ||
        node.data?.targetObjectKind?.includes('TriggerInstance') ||
        node.data?.targetObjectKind?.includes('DynamicTriggerInstance') ||
        node.data?.targetObjectKind?.includes('OperationInstance') ||
        node.data?.targetObjectKind?.includes('domain=edtimplementation')
      ) {
        if (
          !node.data?.targetObjectKind?.includes('DeployedModuleInstance') &&
          !node.data?.targetObjectKind?.includes('DeployedTriggerInstance') &&
          !node.data?.targetObjectKind?.includes('Link')
        ) {
          isComponentImplementationDiagram = true;
        }
      }

      const kind = node.data?.targetObjectKind;
      const style = node.data?.style as any;

      if (kind && style) {
        let iconUrl: string | null = null;
        if (kind.includes('entity=ComponentService')) {
          iconUrl = ComponentServiceIcon;
        } else if (kind.includes('entity=ComponentReference')) {
          iconUrl = ComponentReferenceIcon;
        } else if (kind.includes('entity=ComponentProperty')) {
          iconUrl = ComponentPropertyIcon;
        }

        if (iconUrl) {
          // Force background to transparent to overwrite existing background shorthand
          style.background = 'transparent';
          style.backgroundColor = 'transparent';
          style.backgroundImage = `url(${iconUrl})`;
          // Make the icon fill the whole node instead of keeping aspect ratio inside
          // Using 100% 100% ensures the image stretches to cover the full node area
          style.backgroundSize = '100% 100%';
          style.backgroundRepeat = 'no-repeat';
          style.backgroundPosition = 'center';
          style.border = 'none';
          style.borderWidth = 0;
          style.boxShadow = 'none';
        }
      }
    });
  }
  const newChildren = (
    <>
      {children}
      {isCompositeDiagram && <CompositeDiagramLegend />}
      {isLogicalSystemDiagram && <LogicalSystemDiagramLegend />}
      {isComponentImplementationDiagram && <ComponentImplementationDiagramLegend />}
    </>
  );
  return { children: newChildren, ...props };
};

const edtDiagramPanelExtension: DataExtension<Array<ReactFlowPropsCustomizer>> = {
  identifier: `edt_${diagramRendererReactFlowPropsCustomizerExtensionPoint.identifier}`,
  data: [reactFlowPropsCustomizer],
};
edtExtensionRegistry.putData(diagramRendererReactFlowPropsCustomizerExtensionPoint, edtDiagramPanelExtension);

const diagramPaletteToolContributions: DiagramPaletteToolContributionProps[] = [
  {
    canHandle: (diagramElement: Node<NodeData> | Edge<EdgeData> | null) => {
      return diagramElement?.data
        ? diagramElement.data.targetObjectKind.startsWith(
            'siriusComponents://semantic?domain=edtproject&entity=Component'
          )
        : false;
    },
    component: EdtComponentLabelDetailToolContribution,
  },
  {
    canHandle: (diagramElement: Node<NodeData> | Edge<EdgeData> | null) => diagramElement === null,
    component: EdtComponentDiagramToolContribution,
  },
];
edtExtensionRegistry.putData<DiagramPaletteToolContributionProps[]>(diagramPaletteToolExtensionPoint, {
  identifier: `edt_${diagramPaletteToolExtensionPoint.identifier}`,
  data: diagramPaletteToolContributions,
});

/*******************************************************************************
 *
 * Omnibox command overrides
 *
 * Used to override the default rendering of omnibox commands
 *
 *******************************************************************************/
const ShowDocumentationCommand = ({ command, onKeyDown, onClose }: OmniboxCommandComponentProps) => {
  const handleClick = () => {
    window.open('https://ecoa.technology/', '_blank')?.focus();
    onClose();
  };

  return (
    <ListItemButton key={command.id} data-testid={command.label} onClick={handleClick} onKeyDown={onKeyDown}>
      <ListItemIcon>{command.icon}</ListItemIcon>
      <ListItemText sx={{ whiteSpace: 'nowrap', textOverflow: 'ellipsis' }}>{command.label}</ListItemText>
    </ListItemButton>
  );
};

const omniboxCommandOverrides: OmniboxCommandOverrideContribution[] = [
  {
    canHandle: (command: OmniboxCommand) => {
      return command.id === 'showDocumentation';
    },
    component: ShowDocumentationCommand,
  },
];

edtExtensionRegistry.putData<OmniboxCommandOverrideContribution[]>(omniboxCommandOverrideContributionExtensionPoint, {
  identifier: `edt_${omniboxCommandOverrideContributionExtensionPoint.identifier}`,
  data: omniboxCommandOverrides,
});

/*******************************************************************************
 *
 * Diagram node action command overrides
 *
 * Used to override the default show label node action
 *
 *******************************************************************************/
const diagramNodeActionOverrides: DiagramNodeActionOverrideContribution[] = [
  {
    canHandle: ({ action }: ActionProps) => {
      return action.id === 'edt_show_label';
    },
    component: EdtComponentLabelDetailNodeActionContribution,
  },
];

edtExtensionRegistry.putData<DiagramNodeActionOverrideContribution[]>(
  diagramNodeActionOverrideContributionExtensionPoint,
  {
    identifier: `edt_${diagramNodeActionOverrideContributionExtensionPoint.identifier}`,
    data: diagramNodeActionOverrides,
  }
);

/*******************************************************************************
 *
 * EDT workbench view contribution
 *
 *******************************************************************************/
const edtWorkbenchViewContributions: WorkbenchViewContribution[] = [
  {
    id: 'edt-view',
    title: 'EDT View',
    icon: <ArchitectureIcon />,
    component: EdtView,
  },
];
edtExtensionRegistry.putData(workbenchViewContributionExtensionPoint, {
  identifier: `edt_${workbenchViewContributionExtensionPoint.identifier}`,
  data: edtWorkbenchViewContributions,
});

export { edtExtensionRegistry };
