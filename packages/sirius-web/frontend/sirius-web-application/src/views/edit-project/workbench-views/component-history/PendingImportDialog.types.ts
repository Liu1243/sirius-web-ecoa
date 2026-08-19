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

export interface PendingVersion {
  id: string;
  componentId: string;
  componentName: string;
  versionName: string;
  commitMessage: string | null;
  author: string;
  createdAt: string;
  tags: { id: string; name: string; color: string }[];
}

export interface PendingImportDialogProps {
  open: boolean;
  projectId: string;
  onClose: () => void;
  onConfirmed: () => void;
}
