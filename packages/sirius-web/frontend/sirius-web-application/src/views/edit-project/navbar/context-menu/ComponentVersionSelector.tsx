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
import { useState, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Alert from '@mui/material/Alert';
import Autocomplete from '@mui/material/Autocomplete';
import TextField from '@mui/material/TextField';
import {
  ComponentCodeVersion,
  ComponentCodeTag,
} from '../../workbench-views/component-history/ComponentHistoryView.types';
import { SelectedComponentVersion } from './GenerateEcoaDialog.types';

interface ComponentVersionSelectorProps {
  components: ComponentWithVersions[];
  availableTags: ComponentCodeTag[];
  selectedVersions: SelectedComponentVersion[];
  onSelectionChange: (selections: SelectedComponentVersion[]) => void;
}

export interface ComponentWithVersions {
  componentId: string;
  componentName: string;
  versions: ComponentCodeVersion[];
}

export const ComponentVersionSelector = ({
  components,
  availableTags: _availableTags,
  selectedVersions,
  onSelectionChange,
}: ComponentVersionSelectorProps) => {
  const { t } = useTranslation('sirius-web-application', { keyPrefix: 'componentVersionSelector' });
  const [searchQuery, setSearchQuery] = useState<string>('');

  // Filter components based on search query
  const filteredComponents = useMemo(() => {
    if (!searchQuery.trim()) {
      return components;
    }
    const query = searchQuery.toLowerCase();
    return components.filter((comp) => comp.componentName.toLowerCase().includes(query));
  }, [components, searchQuery]);

  // Handle version selection change
  const handleVersionChange = (componentId: string, componentName: string, version: ComponentCodeVersion | null) => {
    const newSelections = selectedVersions.filter((sv) => sv.componentId !== componentId);
    if (version) {
      newSelections.push({
        componentId,
        componentName,
        versionId: version.id,
        versionName: version.versionName,
        tags: version.tags,
      });
    }
    onSelectionChange(newSelections);
  };

  // Handle batch select/deselect all
  const handleSelectAll = () => {
    const newSelections: SelectedComponentVersion[] = [];
    filteredComponents.forEach((comp) => {
      if (comp.versions.length > 0) {
        // Select the first (latest) version
        const version = comp.versions[0];
        newSelections.push({
          componentId: comp.componentId,
          componentName: comp.componentName,
          versionId: version.id,
          versionName: version.versionName,
          tags: version.tags,
        });
      }
    });
    onSelectionChange(newSelections);
  };

  const handleClearAll = () => {
    // Clear selections for filtered components only
    const filteredComponentIds = new Set(filteredComponents.map((c) => c.componentId));
    const newSelections = selectedVersions.filter((sv) => !filteredComponentIds.has(sv.componentId));
    onSelectionChange(newSelections);
  };

  // Get all selected count for filtered components
  const selectedCountForFiltered = selectedVersions.filter((sv) =>
    filteredComponents.some((fc) => fc.componentId === sv.componentId)
  ).length;

  if (components.length === 0) {
    return (
      <Alert severity="info" sx={{ mt: 1 }}>
        暂无组件版本数据，请确保项目中已保存组件代码版本。
      </Alert>
    );
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      {/* Search and Actions */}
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          gap: 1,
        }}>
        <TextField
          size="small"
          placeholder={t('searchComponents')}
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          sx={{ flex: 1 }}
        />
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Chip
            label={t('selectAll')}
            size="small"
            onClick={handleSelectAll}
            color="primary"
            variant="outlined"
            sx={{ cursor: 'pointer' }}
          />
          <Chip
            label={t('clearAll')}
            size="small"
            onClick={handleClearAll}
            variant="outlined"
            sx={{ cursor: 'pointer' }}
          />
        </Box>
      </Box>

      {/* Selection Summary */}
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          bgcolor: 'action.hover',
          p: 1,
          borderRadius: 1,
        }}>
        <Typography variant="body2">
          已选择 <strong>{selectedVersions.length}</strong> / {components.length} 个组件
          {searchQuery && ` (过滤后: ${selectedCountForFiltered}/${filteredComponents.length})`}
        </Typography>
      </Box>

      {/* Component List */}
      <Box sx={{ maxHeight: 300, overflow: 'auto', border: 1, borderColor: 'divider', borderRadius: 1 }}>
        {filteredComponents.length === 0 ? (
          <Box sx={{ p: 2, textAlign: 'center' }}>
            <Typography variant="body2" color="text.secondary">
              没有符合搜索条件的组件
            </Typography>
          </Box>
        ) : (
          filteredComponents.map((comp) => {
            const selectedVersion = selectedVersions.find((sv) => sv.componentId === comp.componentId);
            const currentValue = selectedVersion
              ? comp.versions.find((v) => v.id === selectedVersion.versionId) || null
              : null;

            return (
              <Box
                key={comp.componentId}
                sx={{
                  p: 1.5,
                  borderBottom: 1,
                  borderColor: 'divider',
                  '&:last-child': { borderBottom: 0 },
                  bgcolor: selectedVersion ? 'action.selected' : 'transparent',
                }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                  <Typography variant="body2" fontWeight={selectedVersion ? 'bold' : 'normal'}>
                    {comp.componentName}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {comp.versions.length} 个版本
                  </Typography>
                </Box>

                <Autocomplete
                  size="small"
                  options={comp.versions}
                  value={currentValue}
                  onChange={(_, newValue) => handleVersionChange(comp.componentId, comp.componentName, newValue)}
                  getOptionLabel={(version) => version.versionName}
                  renderInput={(params) => (
                    <TextField
                      {...params}
                      placeholder={t('selectVersionPlaceholder')}
                      size="small"
                      required
                      error={!currentValue}
                      helperText={!currentValue ? t('selectVersionRequired') : ''}
                    />
                  )}
                  renderOption={(props, version) => (
                    <Box component="li" {...props} key={version.id}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, width: '100%' }}>
                        <Typography variant="body2" sx={{ flex: 1 }}>
                          {version.versionName}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {new Date(version.createdAt).toLocaleDateString('zh-CN')}
                        </Typography>
                        {version.tags.map((tag) => (
                          <Chip
                            key={tag.id}
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
                    </Box>
                  )}
                />
              </Box>
            );
          })
        )}
      </Box>

      {/* Selected Summary */}
      {selectedVersions.length > 0 && (
        <Box sx={{ mt: 1 }}>
          <Typography variant="caption" color="text.secondary" display="block" gutterBottom>
            已选版本摘要:
          </Typography>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
            {selectedVersions.map((sv) => (
              <Chip
                key={sv.componentId}
                label={`${sv.componentName}: ${sv.versionName}`}
                size="small"
                onDelete={() => handleVersionChange(sv.componentId, sv.componentName, null)}
                sx={{
                  '& .MuiChip-label': { fontSize: '0.75rem' },
                }}
              />
            ))}
          </Box>
        </Box>
      )}
    </Box>
  );
};
