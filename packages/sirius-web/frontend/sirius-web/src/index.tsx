/*******************************************************************************
 * Copyright (c) 2019, 2025 Obeo.
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
import { loadDevMessages, loadErrorMessages } from '@apollo/client/dev';
import { ExtensionRegistry } from '@eclipse-sirius/sirius-components-core';
import { NodeTypeContribution } from '@eclipse-sirius/sirius-components-diagrams';
import {
  DiagramRepresentationConfiguration,
  navigationBarLeftContributionExtensionPoint,
  navigationBarRightContributionExtensionPoint,
  projectSettingsTabExtensionPoint,
  routerExtensionPoint,
  NodeTypeRegistry,
  ProjectSettingTabContribution,
  SiriusWebApplication,
  defaultExtensionRegistry,
} from '@eclipse-sirius/sirius-web-application';
import { edtExtensionRegistry } from '@eclipse-sirius/sirius-web-edt';
import { papayaExtensionRegistry } from '@eclipse-sirius/sirius-web-papaya';
import { forkRegistry } from '@eclipse-sirius/sirius-web-view-fork';
import AdminPanelSettingsOutlinedIcon from '@mui/icons-material/AdminPanelSettingsOutlined';
import { createRoot } from 'react-dom/client';
import { RouteProps } from 'react-router-dom';
import './core/randomUUID';
import { SessionNavigationActions } from './navigation/SessionNavigationActions';
import { ContainerManagementNav } from './navigation/ContainerManagementNav';
import { httpOrigin, wsOrigin } from './core/URL';
import { SiriusWebExtensionRegistryMergeStrategy } from './extension/SiriusWebExtensionRegistryMergerStrategy';
import { EllipseNode } from './nodes/EllipseNode';
import { EllipseNodeConverter } from './nodes/EllipseNodeConverter';
import { EllipseNodeLayoutHandler } from './nodes/EllipseNodeLayoutHandler';
import { LoginPage } from './views/LoginPage';
import { AccountPage } from './views/AccountPage';
import { ProjectPermissionsSettings } from './views/ProjectPermissionsSettings';
import { UsersAdminPage } from './views/UsersAdminPage';
import { DistributedDebugManager } from './views/DistributedDebugManager';

import './fonts.css';
import './ReactFlow.css';
import './reset.css';
import './variables.css';

if (process.env.NODE_ENV !== 'production') {
  loadDevMessages();
  loadErrorMessages();
}

// Start with defaultExtensionRegistry which already contains componentHistory
// to avoid duplicate workbench view contributions, use it as base and merge others
const registry = new ExtensionRegistry();
registry.addAll(defaultExtensionRegistry, new SiriusWebExtensionRegistryMergeStrategy());
// Merge additional registries - their unique contributions will be added
registry.addAll(forkRegistry, new SiriusWebExtensionRegistryMergeStrategy());
registry.addAll(papayaExtensionRegistry, new SiriusWebExtensionRegistryMergeStrategy());
registry.addAll(edtExtensionRegistry, new SiriusWebExtensionRegistryMergeStrategy());
registry.putData<ProjectSettingTabContribution[]>(projectSettingsTabExtensionPoint, {
  identifier: 'ecoa_project_permissions_tab',
  data: [
    {
      id: 'members',
      title: '用户权限',
      icon: <AdminPanelSettingsOutlinedIcon />,
      component: ProjectPermissionsSettings,
    },
  ],
});
registry.putData<RouteProps[]>(routerExtensionPoint, {
  identifier: 'ecoa_login_route',
  data: [
    {
      path: '/login',
      element: <LoginPage />,
    },
    {
      path: '/account',
      element: <AccountPage />,
    },
    {
      path: '/admin/users',
      element: <UsersAdminPage />,
    },
    {
      path: '/distributed-debug',
      element: <DistributedDebugManager />,
    },
  ],
});
registry.addComponent(navigationBarRightContributionExtensionPoint, {
  identifier: 'ecoa_session_navigation_actions',
  Component: SessionNavigationActions,
});
registry.addComponent(navigationBarLeftContributionExtensionPoint, {
  identifier: 'ecoa_container_management_nav',
  Component: ContainerManagementNav,
});

const nodeTypeRegistry: NodeTypeRegistry = {
  nodeLayoutHandlers: [new EllipseNodeLayoutHandler()],
  nodeConverters: [new EllipseNodeConverter()],
  nodeTypeContributions: [<NodeTypeContribution component={EllipseNode} type={'ellipseNode'} />],
};

const container = document.getElementById('root');
const root = createRoot(container!);
root.render(
  <SiriusWebApplication
    httpOrigin={httpOrigin}
    wsOrigin={wsOrigin}
    extensionRegistry={registry}
    extensionRegistryMergeStrategy={new SiriusWebExtensionRegistryMergeStrategy()}>
    <DiagramRepresentationConfiguration nodeTypeRegistry={nodeTypeRegistry} />
  </SiriusWebApplication>
);
