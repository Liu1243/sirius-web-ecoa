/*******************************************************************************
 * Copyright (c) 2025 Obeo.
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
export interface NewRepresentationTreeItemContextMenuContributionState {
  isModalOpen: boolean;
}

export interface GQLGetExistingRepresentationsQueryVariables {
  editingContextId: string;
  targetObjectId: string;
}

export interface GQLGetExistingRepresentationsQueryData {
  viewer: GQLExistingRepresentationsViewer;
}

export interface GQLExistingRepresentationsViewer {
  editingContext: GQLExistingRepresentationsEditingContext;
}

export interface GQLExistingRepresentationsEditingContext {
  representations: GQLExistingRepresentationsConnection;
}

export interface GQLExistingRepresentationsConnection {
  edges: GQLExistingRepresentationsEdge[];
}

export interface GQLExistingRepresentationsEdge {
  node: GQLExistingRepresentation;
}

export interface GQLExistingRepresentation {
  id: string;
  label: string;
  kind: string;
  iconURLs: string[];
  targetObjectId: string;
}
