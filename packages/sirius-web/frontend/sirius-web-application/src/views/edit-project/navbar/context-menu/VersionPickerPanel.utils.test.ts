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

import { describe, expect, it } from 'vitest';
import { VersionItem } from './VersionPickerPanel.types';
import {
  filterByQuery,
  getComponentSelectionState,
  getComponents,
  getSelectionSummary,
  getVersionsForComponent,
  toggleComponent,
} from './VersionPickerPanel.utils';

const ITEMS: VersionItem[] = [
  { componentId: 'compA', componentName: 'ComponentA', versionName: 'v1.0', tags: [{ name: 'bugfix', color: '#f00' }] },
  { componentId: 'compA', componentName: 'ComponentA', versionName: 'v2.0', tags: [] },
  { componentId: 'compB', componentName: 'ComponentB', versionName: 'v1.0', tags: [] },
];

describe('filterByQuery', () => {
  it('returns all items when query is empty', () => {
    expect(filterByQuery(ITEMS, '')).toHaveLength(3);
  });

  it('returns all items when query is whitespace', () => {
    expect(filterByQuery(ITEMS, '   ')).toHaveLength(3);
  });

  it('filters by component name (case-insensitive)', () => {
    expect(filterByQuery(ITEMS, 'componentA')).toHaveLength(2);
    expect(filterByQuery(ITEMS, 'COMPONENTA')).toHaveLength(2);
  });

  it('filters by version name', () => {
    const result = filterByQuery(ITEMS, 'v2.0');
    expect(result).toHaveLength(1);
    expect(result[0]!.versionName).toBe('v2.0');
  });

  it('filters by tag name', () => {
    const result = filterByQuery(ITEMS, 'bugfix');
    expect(result).toHaveLength(1);
    expect(result[0]!.versionName).toBe('v1.0');
  });

  it('returns empty array when no items match', () => {
    expect(filterByQuery(ITEMS, 'xyz-no-match-9999')).toHaveLength(0);
  });
});

describe('getComponents', () => {
  it('returns unique components preserving order', () => {
    const comps = getComponents(ITEMS);
    expect(comps).toHaveLength(2);
    expect(comps[0]!.id).toBe('compA');
    expect(comps[1]!.id).toBe('compB');
  });

  it('returns empty array for empty input', () => {
    expect(getComponents([])).toHaveLength(0);
  });
});

describe('getVersionsForComponent', () => {
  it('returns only versions for the given component', () => {
    const result = getVersionsForComponent(ITEMS, 'compA');
    expect(result).toHaveLength(2);
    expect(result.every((v) => v.componentId === 'compA')).toBe(true);
  });

  it('returns empty array for unknown component', () => {
    expect(getVersionsForComponent(ITEMS, 'unknown')).toHaveLength(0);
  });
});

describe('getComponentSelectionState', () => {
  it('returns none when nothing is selected', () => {
    expect(getComponentSelectionState([], ITEMS, 'compA')).toBe('none');
  });

  it('returns some when one of two versions is selected', () => {
    expect(getComponentSelectionState(['compA/v1.0'], ITEMS, 'compA')).toBe('some');
  });

  it('returns all when all versions of the component are selected', () => {
    expect(getComponentSelectionState(['compA/v1.0', 'compA/v2.0'], ITEMS, 'compA')).toBe('all');
  });

  it('returns none for a component not in items', () => {
    expect(getComponentSelectionState(['compA/v1.0'], ITEMS, 'unknown')).toBe('none');
  });
});

describe('toggleComponent', () => {
  it('adds all versions of the component when selecting', () => {
    const result = toggleComponent([], ITEMS, 'compA', true);
    expect(result).toContain('compA/v1.0');
    expect(result).toContain('compA/v2.0');
    expect(result).not.toContain('compB/v1.0');
  });

  it('does not add duplicates when selecting an already-selected version', () => {
    const result = toggleComponent(['compA/v1.0'], ITEMS, 'compA', true);
    const count = result.filter((k) => k === 'compA/v1.0').length;
    expect(count).toBe(1);
  });

  it('removes all versions of the component when deselecting', () => {
    const result = toggleComponent(['compA/v1.0', 'compA/v2.0', 'compB/v1.0'], ITEMS, 'compA', false);
    expect(result).not.toContain('compA/v1.0');
    expect(result).not.toContain('compA/v2.0');
    expect(result).toContain('compB/v1.0');
  });

  it('leaves other components untouched when deselecting', () => {
    const result = toggleComponent(['compA/v1.0', 'compB/v1.0'], ITEMS, 'compA', false);
    expect(result).toEqual(['compB/v1.0']);
  });
});

describe('getSelectionSummary', () => {
  it('counts selected versions and unique components correctly', () => {
    const summary = getSelectionSummary(['compA/v1.0', 'compB/v1.0'], ITEMS);
    expect(summary.selectedVersions).toBe(2);
    expect(summary.totalVersions).toBe(3);
    expect(summary.selectedComponents).toBe(2);
    expect(summary.totalComponents).toBe(2);
  });

  it('returns zeros when nothing is selected', () => {
    const summary = getSelectionSummary([], ITEMS);
    expect(summary.selectedVersions).toBe(0);
    expect(summary.selectedComponents).toBe(0);
    expect(summary.totalVersions).toBe(3);
    expect(summary.totalComponents).toBe(2);
  });

  it('ignores keys that do not match any item', () => {
    const summary = getSelectionSummary(['ghost/v0.0'], ITEMS);
    expect(summary.selectedComponents).toBe(0);
  });
});
