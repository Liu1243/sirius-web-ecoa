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

import { VersionItem } from './VersionPickerPanel.types';

/** Filter items by a free-text query against component name, version name, and tag names. */
export function filterByQuery(items: VersionItem[], query: string): VersionItem[] {
  if (!query.trim()) return items;
  const q = query.toLowerCase();
  return items.filter(
    (item) =>
      item.componentName.toLowerCase().includes(q) ||
      item.versionName.toLowerCase().includes(q) ||
      (item.tags ?? []).some((t) => t.name.toLowerCase().includes(q))
  );
}

/** Return unique components (id + name), preserving insertion order. */
export function getComponents(items: VersionItem[]): { id: string; name: string }[] {
  const seen = new Set<string>();
  const result: { id: string; name: string }[] = [];
  for (const item of items) {
    if (!seen.has(item.componentId)) {
      seen.add(item.componentId);
      result.push({ id: item.componentId, name: item.componentName });
    }
  }
  return result;
}

/** Return all versions belonging to a specific component. */
export function getVersionsForComponent(items: VersionItem[], componentId: string): VersionItem[] {
  return items.filter((item) => item.componentId === componentId);
}

/**
 * Returns the tri-state checkbox state for a component:
 * - 'all'  — every version is selected
 * - 'some' — at least one but not all versions are selected
 * - 'none' — no version is selected
 */
export function getComponentSelectionState(
  selectedKeys: string[],
  items: VersionItem[],
  componentId: string
): 'all' | 'some' | 'none' {
  const versions = getVersionsForComponent(items, componentId);
  if (versions.length === 0) return 'none';
  const selectedCount = versions.filter((v) => selectedKeys.includes(`${v.componentId}/${v.versionName}`)).length;
  if (selectedCount === 0) return 'none';
  if (selectedCount === versions.length) return 'all';
  return 'some';
}

/**
 * Select (select=true) or deselect (select=false) all versions of a component.
 * Returns a new selectedKeys array without mutating the original.
 */
export function toggleComponent(
  selectedKeys: string[],
  items: VersionItem[],
  componentId: string,
  select: boolean
): string[] {
  const componentKeys = getVersionsForComponent(items, componentId).map((v) => `${v.componentId}/${v.versionName}`);
  if (select) {
    const toAdd = componentKeys.filter((k) => !selectedKeys.includes(k));
    return [...selectedKeys, ...toAdd];
  }
  return selectedKeys.filter((k) => !componentKeys.includes(k));
}

/** Compute the summary counts shown in the footer row. */
export function getSelectionSummary(
  selectedKeys: string[],
  items: VersionItem[]
): { selectedVersions: number; totalVersions: number; selectedComponents: number; totalComponents: number } {
  const totalComponents = new Set(items.map((i) => i.componentId)).size;
  const selectedComponentIds = new Set(
    selectedKeys.map((key) => key.split('/')[0]).filter((id) => items.some((i) => i.componentId === id))
  );
  return {
    selectedVersions: selectedKeys.length,
    totalVersions: items.length,
    selectedComponents: selectedComponentIds.size,
    totalComponents,
  };
}
