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
export interface TreePathEntry {
  /** Labels from root ancestor down to the tree item's parent (not including the item itself). */
  ancestorLabels: string[];
  /** Whether the tree item has children (is expandable). */
  hasChildren: boolean;
}

export interface TreePathContextValue {
  /** Map from tree item ID to its tree path entry. */
  treePathEntries: Record<string, TreePathEntry>;
  /** Set or clear the tree path entry for a given item ID. Pass null to clear. */
  setTreePathEntry: (itemId: string, entry: TreePathEntry | null) => void;
}
