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
import { ForwardedRef, forwardRef, useImperativeHandle, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { WorkbenchViewComponentProps, WorkbenchViewHandle } from '@eclipse-sirius/sirius-components-core';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import InputAdornment from '@mui/material/InputAdornment';
import SearchIcon from '@mui/icons-material/Search';
import DeleteIcon from '@mui/icons-material/Delete';
import CancelIcon from '@mui/icons-material/Cancel';
import SelectAllIcon from '@mui/icons-material/SelectAll';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogActions from '@mui/material/DialogActions';
import Alert from '@mui/material/Alert';
import { useComponentCodeHistory } from './useComponentCodeHistory';
import { ComponentHistoryTree } from './ComponentHistoryTree';
import { ComponentCodeVersion, ComponentHistoryEntry, ComponentPaginationInfo } from './ComponentHistoryView.types';
import { ComponentVersionDetailDialog } from './ComponentVersionDetailDialog';
import { useSession } from './useSession';

interface ComponentHistoryEntryWithPagination extends ComponentHistoryEntry {
  pagination: ComponentPaginationInfo;
}

export const ComponentHistoryView = forwardRef<WorkbenchViewHandle, WorkbenchViewComponentProps>(
  ({ id, editingContextId }: WorkbenchViewComponentProps, ref: ForwardedRef<WorkbenchViewHandle>) => {
    const { t } = useTranslation('sirius-web-application', { keyPrefix: 'componentHistory' });
    const { state, deleteVersion, deleteVersions } = useComponentCodeHistory(editingContextId);
    const { session } = useSession();
    const isAdmin = session.admin;
    const [selectedVersion, setSelectedVersion] = useState<ComponentCodeVersion | null>(null);
    const [searchQuery, setSearchQuery] = useState('');
    const [detailDialogOpen, setDetailDialogOpen] = useState(false);

    // Batch selection state
    const [isBatchMode, setIsBatchMode] = useState(false);
    const [selectedVersionIds, setSelectedVersionIds] = useState<string[]>([]);

    // Pagination state per component (componentId -> page number)
    const [componentPages, setComponentPages] = useState<Record<string, number>>({});

    // Delete dialog state
    const [batchDeleteDialogOpen, setBatchDeleteDialogOpen] = useState(false);
    const [isDeleting, setIsDeleting] = useState(false);
    const [deleteResult, setDeleteResult] = useState<{ success: string[]; failed: string[] } | null>(null);

    useImperativeHandle(
      ref,
      () => ({
        id,
        getWorkbenchViewConfiguration: () => ({}),
        applySelection: null,
      }),
      []
    );

    const filteredComponents = useMemo(() => {
      if (!state.history) return [];
      return state.history.components.filter((entry) => {
        if (!searchQuery) return true;
        const query = searchQuery.toLowerCase();
        return entry.componentName.toLowerCase().includes(query) || entry.componentId.toLowerCase().includes(query);
      });
    }, [state.history, searchQuery]);

    // Paginate versions within each component
    const paginatedComponents = useMemo<ComponentHistoryEntryWithPagination[]>(() => {
      return filteredComponents.map((entry) => {
        const currentPage = componentPages[entry.componentId] || 1;
        const totalVersions = entry.versions.length;
        const totalPages = Math.ceil(totalVersions / 5); // 5 versions per component page

        const startIndex = (currentPage - 1) * 5;
        const endIndex = startIndex + 5;
        const paginatedVersions = entry.versions.slice(startIndex, endIndex);

        return {
          ...entry,
          versions: paginatedVersions,
          pagination: {
            currentPage,
            totalPages: Math.max(1, totalPages),
            totalVersions,
          },
        };
      });
    }, [filteredComponents, componentPages]);

    const handlePageChange = (componentId: string, page: number) => {
      setComponentPages((prev) => ({
        ...prev,
        [componentId]: page,
      }));
    };

    const handleDeleteVersion = async (versionId: string): Promise<boolean> => {
      const success = await deleteVersion(versionId);
      if (success && selectedVersion?.id === versionId) {
        setSelectedVersion(null);
      }
      return success;
    };

    // Clicking an already-selected version opens the detail dialog directly
    const handleSelectVersion = (version: ComponentCodeVersion) => {
      if (selectedVersion?.id === version.id) {
        setDetailDialogOpen(true);
      } else {
        setSelectedVersion(version);
      }
    };

    const handleToggleBatchMode = () => {
      setIsBatchMode(!isBatchMode);
      setSelectedVersionIds([]);
    };

    const handleToggleVersionSelection = (versionId: string) => {
      setSelectedVersionIds((prev) => {
        if (prev.includes(versionId)) {
          return prev.filter((id) => id !== versionId);
        }
        return [...prev, versionId];
      });
    };

    const handleSelectAllVersions = (_componentId: string, versionIds: string[]) => {
      setSelectedVersionIds((prev) => {
        const otherIds = prev.filter((id) => !versionIds.includes(id));
        const allSelected = versionIds.every((id) => prev.includes(id));

        if (allSelected) {
          // Deselect all versions of this component
          return otherIds;
        } else {
          // Select all versions of this component
          return [...otherIds, ...versionIds];
        }
      });
    };

    const handleBatchDelete = async () => {
      if (selectedVersionIds.length === 0) return;

      setIsDeleting(true);
      setDeleteResult(null);

      const result = await deleteVersions(selectedVersionIds);

      setDeleteResult(result);
      setIsDeleting(false);

      if (result.failed.length === 0) {
        // All deleted successfully
        setSelectedVersionIds([]);
        setTimeout(() => {
          setBatchDeleteDialogOpen(false);
          setDeleteResult(null);
        }, 1500);
      }
    };

    const handleCloseBatchDeleteDialog = () => {
      if (!isDeleting) {
        setBatchDeleteDialogOpen(false);
        setDeleteResult(null);
        if (deleteResult && deleteResult.failed.length === 0) {
          setSelectedVersionIds([]);
        }
      }
    };

    // Reset pagination when search changes
    const handleSearchChange = (e: React.ChangeEvent<HTMLInputElement>) => {
      setSearchQuery(e.target.value);
      setComponentPages({}); // Reset all component pages
      setSelectedVersion(null);
    };

    return (
      <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
        {/* Header */}
        <Box sx={{ padding: 1, borderBottom: 1, borderColor: 'divider' }}>
          <TextField
            size="small"
            fullWidth
            placeholder={t('searchComponents')}
            value={searchQuery}
            onChange={handleSearchChange}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" />
                </InputAdornment>
              ),
            }}
          />

          {isAdmin && (
            <Box sx={{ display: 'flex', gap: 1, mt: 1 }}>
              {!isBatchMode ? (
                <Button
                  size="small"
                  variant="outlined"
                  startIcon={<SelectAllIcon />}
                  onClick={handleToggleBatchMode}
                  fullWidth>
                  批量选择
                </Button>
              ) : (
                <>
                  <Button
                    size="small"
                    variant="contained"
                    color="error"
                    startIcon={<DeleteIcon />}
                    onClick={() => setBatchDeleteDialogOpen(true)}
                    disabled={selectedVersionIds.length === 0}
                    sx={{ flex: 1 }}>
                    删除 ({selectedVersionIds.length})
                  </Button>
                  <Button
                    size="small"
                    variant="outlined"
                    startIcon={<CancelIcon />}
                    onClick={handleToggleBatchMode}
                    sx={{ flex: 1 }}>
                    取消
                  </Button>
                </>
              )}
            </Box>
          )}
        </Box>

        {/* Tree */}
        <Box sx={{ flex: 1, overflow: 'auto' }}>
          {state.loading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', py: 4 }}>
              <CircularProgress size={24} />
            </Box>
          ) : (
            <ComponentHistoryTree
              components={paginatedComponents}
              selectedVersionId={selectedVersion?.id || null}
              onSelectVersion={handleSelectVersion}
              isAdmin={isAdmin}
              onDeleteVersion={handleDeleteVersion}
              isBatchMode={isBatchMode}
              selectedVersionIds={selectedVersionIds}
              onToggleVersionSelection={handleToggleVersionSelection}
              onSelectAllVersions={handleSelectAllVersions}
              onPageChange={handlePageChange}
            />
          )}
        </Box>

        {/* Selected Version Actions */}
        {selectedVersion && !isBatchMode && (
          <>
            <Divider />
            <Box sx={{ padding: 1 }}>
              <Typography variant="caption" color="text.secondary" display="block" noWrap>
                {selectedVersion.componentName}
              </Typography>
              <Typography variant="body2" fontWeight="medium" display="block" noWrap gutterBottom>
                {selectedVersion.versionName}
              </Typography>
              <Button size="small" variant="contained" fullWidth onClick={() => setDetailDialogOpen(true)}>
                查看详情
              </Button>
            </Box>
          </>
        )}

        {/* Detail Dialog */}
        <ComponentVersionDetailDialog
          open={detailDialogOpen}
          version={selectedVersion}
          onClose={() => setDetailDialogOpen(false)}
          isAdmin={isAdmin}
          onDeleteVersion={handleDeleteVersion}
        />

        {/* Batch Delete Confirmation Dialog */}
        <Dialog open={batchDeleteDialogOpen} onClose={handleCloseBatchDeleteDialog} maxWidth="sm" fullWidth>
          <DialogTitle>{t('batchDeleteTitle')}</DialogTitle>
          <DialogContent>
            <DialogContentText>{t('batchDeleteConfirm', { count: selectedVersionIds.length })}</DialogContentText>

            {deleteResult && (
              <Box sx={{ mt: 2 }}>
                {deleteResult.failed.length > 0 && (
                  <Alert severity="error" sx={{ mb: 1 }}>
                    {t('deleteFailed', { count: deleteResult.failed.length })}
                  </Alert>
                )}
                {deleteResult.success.length > 0 && (
                  <Alert severity="success">{t('deleteSuccess', { count: deleteResult.success.length })}</Alert>
                )}
              </Box>
            )}
          </DialogContent>
          <DialogActions>
            <Button onClick={handleCloseBatchDeleteDialog} disabled={isDeleting}>
              {deleteResult?.failed.length ? t('close') : t('cancel')}
            </Button>
            <Button
              onClick={handleBatchDelete}
              color="error"
              variant="contained"
              disabled={isDeleting || (deleteResult?.success.length || 0) > 0}
              startIcon={<DeleteIcon />}>
              {isDeleting ? t('deleting') : t('confirmDelete', { count: selectedVersionIds.length })}
            </Button>
          </DialogActions>
        </Dialog>
      </Box>
    );
  }
);
