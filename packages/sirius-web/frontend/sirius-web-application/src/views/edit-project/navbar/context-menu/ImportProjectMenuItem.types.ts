/*******************************************************************************
 * Copyright (c) 2026 Obeo.
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

export interface ImportProjectMenuItemProps {
  project: { id: string };
  onClick: () => void;
}

/** A component code version entry returned by the /preview endpoint. */
export interface ZipVersionEntry {
  componentId: string;
  componentName: string;
  versionName: string;
  commitMessage?: string;
  author?: string;
  tags?: { name: string; color: string }[];
}

export type ImportStep = 'select-file' | 'select-versions' | 'importing' | 'done' | 'error';

export interface ImportProjectMenuItemState {
  dialogOpen: boolean;
  step: ImportStep;
  /** The file chosen by the user — kept in state so it survives step transitions. */
  selectedFile: File | null;
  /** Versions parsed from the ZIP preview. */
  availableVersions: ZipVersionEntry[];
  /** Keys selected by the user: "{componentId}/{versionName}". */
  selectedVersionKeys: string[];
  message: string | null;
}
