/*******************************************************************************
 * Copyright (c) 2026 Dassault Aviation.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Dassault Aviation - initial API and implementation
 *******************************************************************************/

/**
 * Unified version item used by VersionPickerPanel.
 * Both DownloadProjectDialog and ImportProjectMenuItem map their own types to this.
 */
export interface VersionItem {
  componentId: string;
  componentName: string;
  versionName: string;
  /** Available from import ZIP preview. Undefined in download dialog. */
  author?: string;
  /** Available from import ZIP preview. Undefined in download dialog. */
  commitMessage?: string;
  tags?: { name: string; color: string }[];
  /** Available in download dialog. Undefined in import preview. */
  createdAt?: string;
}

export interface VersionPickerPanelProps {
  items: VersionItem[];
  /**
   * Selected version keys in the format `"{componentId}/{versionName}"`.
   * This format matches exactly what ImportProjectMenuItem sends to the backend.
   * DownloadProjectDialog maps these back to version UUIDs before calling the API.
   */
  selectedKeys: string[];
  onChange: (keys: string[]) => void;
  /** Set to true while an async operation is in progress to disable all controls. */
  disabled?: boolean;
}
