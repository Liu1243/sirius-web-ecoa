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

import SearchIcon from '@mui/icons-material/Search';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import InputAdornment from '@mui/material/InputAdornment';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { VersionPickerPanelProps } from './VersionPickerPanel.types';
import {
  filterByQuery,
  getComponentSelectionState,
  getComponents,
  getSelectionSummary,
  getVersionsForComponent,
  toggleComponent,
} from './VersionPickerPanel.utils';

export const VersionPickerPanel = ({ items, selectedKeys, onChange, disabled = false }: VersionPickerPanelProps) => {
  const { t } = useTranslation('sirius-web-application', { keyPrefix: 'versionPickerPanel' });
  const [searchQuery, setSearchQuery] = useState('');
  const [activeComponentId, setActiveComponentId] = useState<string | null>(() => {
    const comps = getComponents(items);
    return comps.length > 0 ? comps[0]?.id ?? null : null;
  });

  // Reset active component when items loads asynchronously (e.g. after dialog opens and data fetches)
  useEffect(() => {
    setActiveComponentId((current) => {
      if (current !== null) return current;
      const first = getComponents(items)[0];
      return first ? first.id : null;
    });
  }, [items]);

  // ── filtered data ──────────────────────────────────────────────────────────
  const filteredItems = useMemo(() => filterByQuery(items, searchQuery), [items, searchQuery]);
  const filteredComponents = useMemo(() => getComponents(filteredItems), [filteredItems]);

  const handleSearchChange = (value: string) => {
    setSearchQuery(value);
    if (value.trim()) {
      const first = getComponents(filterByQuery(items, value))[0];
      if (first) setActiveComponentId(first.id);
    }
  };

  // ── global checkbox ────────────────────────────────────────────────────────
  const filteredKeys = useMemo(() => filteredItems.map((v) => `${v.componentId}/${v.versionName}`), [filteredItems]);
  const globalSelectedCount = useMemo(
    () => filteredKeys.filter((k) => selectedKeys.includes(k)).length,
    [filteredKeys, selectedKeys]
  );
  const globalState = useMemo((): 'all' | 'some' | 'none' => {
    if (filteredKeys.length === 0) return 'none';
    if (globalSelectedCount === filteredKeys.length) return 'all';
    if (globalSelectedCount > 0) return 'some';
    return 'none';
  }, [filteredKeys, globalSelectedCount]);

  const handleGlobalToggle = () => {
    if (globalState === 'all') {
      onChange(selectedKeys.filter((k) => !filteredKeys.includes(k)));
    } else {
      const toAdd = filteredKeys.filter((k) => !selectedKeys.includes(k));
      onChange([...selectedKeys, ...toAdd]);
    }
  };

  // ── right-panel data ───────────────────────────────────────────────────────
  const rightPanelVersions = useMemo(() => {
    if (!activeComponentId) return [];
    const all = getVersionsForComponent(items, activeComponentId);
    return searchQuery.trim() ? filterByQuery(all, searchQuery) : all;
  }, [items, activeComponentId, searchQuery]);

  const activeComponent = activeComponentId
    ? getComponents(items).find((c) => c.id === activeComponentId) ?? null
    : null;

  const activeVersionCount = activeComponentId ? getVersionsForComponent(items, activeComponentId).length : 0;

  const activeSelectedCount = activeComponentId
    ? getVersionsForComponent(items, activeComponentId).filter((v) =>
        selectedKeys.includes(`${v.componentId}/${v.versionName}`)
      ).length
    : 0;

  const handleRightSelectAll = () => {
    if (activeComponentId) onChange(toggleComponent(selectedKeys, items, activeComponentId, true));
  };
  const handleRightSelectNone = () => {
    if (activeComponentId) onChange(toggleComponent(selectedKeys, items, activeComponentId, false));
  };

  // ── summary ────────────────────────────────────────────────────────────────
  const summary = getSelectionSummary(selectedKeys, items);

  // ── render ─────────────────────────────────────────────────────────────────
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: 520 }}>
      {/* Panels */}
      <Box
        sx={{
          display: 'flex',
          flex: 1,
          overflow: 'hidden',
          border: 1,
          borderColor: 'divider',
          borderRadius: 1,
        }}>
        {/* ── Left panel ── */}
        <Box
          sx={{
            width: '35%',
            borderRight: 1,
            borderColor: 'divider',
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
          }}>
          {/* Search box */}
          <Box sx={{ p: 1, borderBottom: 1, borderColor: 'divider' }}>
            <TextField
              size="small"
              fullWidth
              placeholder={t('searchPlaceholder')}
              value={searchQuery}
              onChange={(e) => handleSearchChange(e.target.value)}
              disabled={disabled}
              slotProps={{
                input: {
                  startAdornment: (
                    <InputAdornment position="start">
                      <SearchIcon fontSize="small" />
                    </InputAdornment>
                  ),
                },
              }}
            />
          </Box>

          {/* Global select-all row */}
          <Box
            onClick={disabled ? undefined : handleGlobalToggle}
            sx={{
              display: 'flex',
              alignItems: 'center',
              px: 1,
              py: 0.5,
              bgcolor: 'action.hover',
              borderBottom: 1,
              borderColor: 'divider',
              cursor: disabled ? 'default' : 'pointer',
            }}>
            <Checkbox
              size="small"
              checked={globalState === 'all'}
              indeterminate={globalState === 'some'}
              disabled={disabled}
              sx={{ p: 0.5 }}
              onClick={(e) => e.stopPropagation()}
              onChange={handleGlobalToggle}
            />
            <Typography variant="body2" sx={{ flex: 1, ml: 0.5 }}>
              全部组件
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {globalSelectedCount}/{filteredKeys.length}
            </Typography>
          </Box>

          {/* Component list */}
          <List dense disablePadding sx={{ flex: 1, overflow: 'auto' }}>
            {filteredComponents.length === 0 ? (
              <Box sx={{ p: 2, textAlign: 'center' }}>
                <Typography variant="body2" color="text.secondary">
                  无匹配组件
                </Typography>
              </Box>
            ) : (
              filteredComponents.map((comp) => {
                const state = getComponentSelectionState(selectedKeys, items, comp.id);
                const compVersions = getVersionsForComponent(items, comp.id);
                const selectedCount = compVersions.filter((v) =>
                  selectedKeys.includes(`${v.componentId}/${v.versionName}`)
                ).length;
                const isActive = activeComponentId === comp.id;

                return (
                  <ListItem key={comp.id} disablePadding>
                    <Box
                      sx={{
                        display: 'flex',
                        alignItems: 'center',
                        width: '100%',
                        borderLeft: '3px solid',
                        borderLeftColor: isActive ? 'primary.main' : 'transparent',
                        bgcolor: isActive ? 'action.selected' : 'transparent',
                        '&:hover': {
                          bgcolor: isActive ? 'action.selected' : 'action.hover',
                        },
                        pl: 0.5,
                        pr: 1,
                        py: 0.75,
                      }}>
                      <Checkbox
                        size="small"
                        checked={state === 'all'}
                        indeterminate={state === 'some'}
                        disabled={disabled}
                        sx={{ p: 0.5, mr: 0.5, flexShrink: 0 }}
                        onClick={(e) => e.stopPropagation()}
                        onChange={() => onChange(toggleComponent(selectedKeys, items, comp.id, state !== 'all'))}
                      />
                      <Box
                        sx={{ flex: 1, overflow: 'hidden', cursor: 'pointer' }}
                        onClick={() => !disabled && setActiveComponentId(comp.id)}>
                        <Typography variant="body2" noWrap>
                          {comp.name}
                        </Typography>
                      </Box>
                      <Typography variant="caption" color="text.secondary" sx={{ ml: 1, flexShrink: 0 }}>
                        {selectedCount}/{compVersions.length}
                      </Typography>
                    </Box>
                  </ListItem>
                );
              })
            )}
          </List>
        </Box>

        {/* ── Right panel ── */}
        <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
          {activeComponent ? (
            <>
              {/* Header */}
              <Box
                sx={{
                  px: 2,
                  py: 1,
                  borderBottom: 1,
                  borderColor: 'divider',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  flexShrink: 0,
                }}>
                <Box sx={{ overflow: 'hidden', mr: 1 }}>
                  <Typography variant="subtitle1" fontWeight="bold" noWrap>
                    {activeComponent.name}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {activeVersionCount} 个版本，已选 {activeSelectedCount} 个
                  </Typography>
                </Box>
                <Box sx={{ display: 'flex', gap: 1, flexShrink: 0 }}>
                  <Button size="small" onClick={handleRightSelectAll} disabled={disabled}>
                    全选
                  </Button>
                  <Button size="small" onClick={handleRightSelectNone} disabled={disabled}>
                    全不选
                  </Button>
                </Box>
              </Box>

              {/* Version list */}
              <List dense disablePadding sx={{ flex: 1, overflow: 'auto' }}>
                {rightPanelVersions.map((version, idx) => {
                  const key = `${version.componentId}/${version.versionName}`;
                  const checked = selectedKeys.includes(key);

                  return (
                    <Box key={key}>
                      <ListItem alignItems="flex-start" disablePadding sx={{ px: 1, py: 0.5 }}>
                        <Checkbox
                          size="small"
                          checked={checked}
                          disabled={disabled}
                          sx={{ mt: 0.5, mr: 0.5, p: 0.5, flexShrink: 0 }}
                          onChange={() =>
                            onChange(checked ? selectedKeys.filter((k) => k !== key) : [...selectedKeys, key])
                          }
                        />
                        <Box sx={{ flex: 1, overflow: 'hidden', py: 0.5 }}>
                          {/* Version name */}
                          <Typography variant="body2" fontWeight="bold">
                            {version.versionName}
                          </Typography>

                          {/* Author · date */}
                          {(version.author || version.createdAt) && (
                            <Typography variant="caption" color="text.secondary" display="block">
                              {[version.author, version.createdAt].filter(Boolean).join(' · ')}
                            </Typography>
                          )}

                          {/* Commit message — max 2 lines */}
                          {version.commitMessage && (
                            <Typography
                              variant="caption"
                              color="text.secondary"
                              display="block"
                              sx={{
                                display: '-webkit-box',
                                WebkitLineClamp: 2,
                                WebkitBoxOrient: 'vertical',
                                overflow: 'hidden',
                                mt: 0.25,
                              }}>
                              {version.commitMessage}
                            </Typography>
                          )}

                          {/* Tags */}
                          {version.tags && version.tags.length > 0 && (
                            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5, mt: 0.5 }}>
                              {version.tags.map((tag) => (
                                <Chip
                                  key={tag.name}
                                  label={tag.name}
                                  size="small"
                                  sx={{
                                    backgroundColor: tag.color,
                                    color: '#fff',
                                    height: 18,
                                    fontSize: '0.65rem',
                                  }}
                                />
                              ))}
                            </Box>
                          )}
                        </Box>
                      </ListItem>
                      {idx < rightPanelVersions.length - 1 && <Divider component="li" variant="inset" sx={{ ml: 5 }} />}
                    </Box>
                  );
                })}
              </List>
            </>
          ) : (
            /* Empty state */
            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flex: 1,
                p: 3,
              }}>
              <Typography variant="body2" color="text.secondary" textAlign="center">
                从左侧选择一个组件
                <br />
                以查看其版本列表
              </Typography>
            </Box>
          )}
        </Box>
      </Box>

      {/* Summary row */}
      <Box sx={{ pt: 1, textAlign: 'right' }}>
        <Typography variant="caption" color="text.secondary">
          已选 {summary.selectedVersions} / {summary.totalVersions} 个版本，涉及 {summary.selectedComponents} /{' '}
          {summary.totalComponents} 个组件
        </Typography>
      </Box>
    </Box>
  );
};
