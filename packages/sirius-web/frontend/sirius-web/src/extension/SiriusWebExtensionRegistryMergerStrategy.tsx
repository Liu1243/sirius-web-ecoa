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
  ExtensionRegistryMergeStrategy,
  workbenchViewContributionExtensionPoint,
} from '@eclipse-sirius/sirius-components-core';
import { diagramNodeActionOverrideContributionExtensionPoint } from '@eclipse-sirius/sirius-components-diagrams';
import { omniboxCommandOverrideContributionExtensionPoint } from '@eclipse-sirius/sirius-components-omnibox';
import { routerExtensionPoint } from '@eclipse-sirius/sirius-web-application';

export class SiriusWebExtensionRegistryMergeStrategy implements ExtensionRegistryMergeStrategy {
  public mergeComponentExtensions(
    _identifier: string,
    existingValues: ComponentExtension<any>[],
    newValues: ComponentExtension<any>[]
  ): ComponentExtension<any>[] {
    return [...existingValues, ...newValues];
  }

  public mergeDataExtensions(
    identifier: string,
    existingValue: DataExtension<any>,
    newValue: DataExtension<any>
  ): DataExtension<any> {
    if (identifier === omniboxCommandOverrideContributionExtensionPoint.identifier) {
      return {
        identifier: `siriusweb_${omniboxCommandOverrideContributionExtensionPoint.identifier}`,
        data: [...existingValue.data, ...newValue.data],
      };
    } else if (identifier === diagramNodeActionOverrideContributionExtensionPoint.identifier) {
      return {
        identifier: `siriusweb_${diagramNodeActionOverrideContributionExtensionPoint.identifier}`,
        data: [...existingValue.data, ...newValue.data],
      };
    } else if (identifier === workbenchViewContributionExtensionPoint.identifier) {
      // Merge and deduplicate by view id to prevent duplicates
      const merged = [...existingValue.data, ...newValue.data];
      const unique = merged.filter((item, index, self) => index === self.findIndex((t) => t.id === item.id));
      return {
        identifier: `siriusweb_${workbenchViewContributionExtensionPoint.identifier}`,
        data: unique,
      };
    } else if (identifier === routerExtensionPoint.identifier) {
      return {
        identifier: `siriusweb_${routerExtensionPoint.identifier}`,
        data: [...existingValue.data, ...newValue.data],
      };
    }
    return newValue;
  }
}
