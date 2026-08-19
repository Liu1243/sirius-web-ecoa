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
export interface UploadProjectViewState {
  file: File | null;
  loading: boolean;
  newProjectId: string | null;
  /**
   * Upload progress percentage (0–100) while the file is being transferred.
   * `null` when no upload is in progress.
   * 100 means the file has been fully sent; the server is still processing.
   */
  uploadProgress: number | null;
  /** Whether the backend rejected the upload due to a project name conflict. */
  nameConflict: boolean;
  /** New project name entered by the user when resolving a name conflict. */
  renameAs: string;
}

export interface GQLUploadProjectMutationData {
  uploadProject: GQLUploadProjectPayload;
}

export interface GQLUploadProjectPayload {
  __typename: string;
}

export interface GQLErrorPayload extends GQLUploadProjectPayload {
  message: string;
}

export interface GQLUploadProjectSuccessPayload extends GQLUploadProjectPayload {
  project: GQLProject;
}

export interface GQLProject {
  id: string;
}
