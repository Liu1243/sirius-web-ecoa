import { useState } from 'react';
import Box from '@mui/material/Box';
import Collapse from '@mui/material/Collapse';
import Typography from '@mui/material/Typography';
import Checkbox from '@mui/material/Checkbox';
import Pagination from '@mui/material/Pagination';
import Divider from '@mui/material/Divider';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import FolderIcon from '@mui/icons-material/Folder';
import FolderOpenIcon from '@mui/icons-material/FolderOpen';
import InboxIcon from '@mui/icons-material/Inbox';
import { ComponentHistoryEntry, ComponentCodeVersion, ComponentPaginationInfo } from './ComponentHistoryView.types';
import { ComponentVersionItem } from './ComponentVersionItem';

interface ComponentHistoryEntryWithPagination extends ComponentHistoryEntry {
  pagination: ComponentPaginationInfo;
}

interface ComponentHistoryTreeProps {
  components: ComponentHistoryEntryWithPagination[];
  selectedVersionId: string | null;
  onSelectVersion: (version: ComponentCodeVersion) => void;
  isAdmin: boolean;
  onDeleteVersion: (versionId: string) => Promise<boolean>;
  isBatchMode?: boolean;
  selectedVersionIds?: string[];
  onToggleVersionSelection?: (versionId: string) => void;
  onSelectAllVersions?: (componentId: string, versionIds: string[]) => void;
  onPageChange?: (componentId: string, page: number) => void;
}

interface ComponentNodeProps {
  entry: ComponentHistoryEntryWithPagination;
  selectedVersionId: string | null;
  onSelectVersion: (version: ComponentCodeVersion) => void;
  isAdmin: boolean;
  onDeleteVersion: (versionId: string) => Promise<boolean>;
  isBatchMode?: boolean;
  selectedVersionIds?: string[];
  onToggleVersionSelection?: (versionId: string) => void;
  onSelectAllVersions?: (componentId: string, versionIds: string[]) => void;
  onPageChange?: (componentId: string, page: number) => void;
}

const ComponentNode = ({
  entry,
  selectedVersionId,
  onSelectVersion,
  isAdmin,
  onDeleteVersion,
  isBatchMode,
  selectedVersionIds = [],
  onToggleVersionSelection,
  onSelectAllVersions,
  onPageChange,
}: ComponentNodeProps) => {
  const [expanded, setExpanded] = useState(true);
  const hasVersions = entry.versions.length > 0;
  const pagination = entry.pagination;

  const versionIds = entry.versions.map((v) => v.id);
  const selectedCount = versionIds.filter((id) => selectedVersionIds.includes(id)).length;
  const isAllSelected = selectedCount === versionIds.length && versionIds.length > 0;
  const isIndeterminate = selectedCount > 0 && selectedCount < versionIds.length;

  const handleComponentCheckboxClick = (event: React.ChangeEvent<HTMLInputElement>) => {
    event.stopPropagation();
    if (onSelectAllVersions) onSelectAllVersions(entry.componentId, versionIds);
  };

  const handleExpandClick = () => {
    if (hasVersions) setExpanded(!expanded);
  };

  const handlePageChange = (_event: React.ChangeEvent<unknown>, page: number) => {
    if (onPageChange) onPageChange(entry.componentId, page);
  };

  return (
    <Box>
      {/* Component header row */}
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          px: 1,
          py: 0.5,
          bgcolor: 'action.hover',
          borderBottom: '1px solid',
          borderColor: 'divider',
          cursor: hasVersions ? 'pointer' : 'default',
          userSelect: 'none',
          '&:hover': { bgcolor: hasVersions ? 'action.focus' : 'action.hover' },
        }}
        onClick={handleExpandClick}>
        {isBatchMode && isAdmin ? (
          <Checkbox
            size="small"
            checked={isAllSelected}
            indeterminate={isIndeterminate}
            onChange={handleComponentCheckboxClick}
            onClick={(e) => e.stopPropagation()}
            sx={{ padding: 0, mr: 0.5 }}
          />
        ) : (
          <Box sx={{ display: 'flex', alignItems: 'center', mr: 0.5, color: 'text.secondary' }}>
            {expanded && hasVersions ? (
              <ExpandMoreIcon sx={{ fontSize: 18 }} />
            ) : (
              <ChevronRightIcon sx={{ fontSize: 18 }} />
            )}
          </Box>
        )}

        {!isBatchMode && (
          <Box sx={{ mr: 0.75, display: 'flex', color: 'primary.main' }}>
            {expanded && hasVersions ? <FolderOpenIcon sx={{ fontSize: 16 }} /> : <FolderIcon sx={{ fontSize: 16 }} />}
          </Box>
        )}

        <Typography
          variant="body2"
          fontWeight={500}
          sx={{ flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {entry.componentName}
        </Typography>

        {/* Version count pill */}
        {pagination && (
          <Box
            sx={{
              ml: 0.5,
              px: 0.75,
              py: 0.1,
              borderRadius: 10,
              bgcolor: 'action.selected',
              flexShrink: 0,
            }}>
            <Typography variant="caption" color="text.secondary" sx={{ fontSize: '0.65rem', whiteSpace: 'nowrap' }}>
              {pagination.totalVersions}
              {pagination.totalPages > 1 ? ` · ${pagination.currentPage}/${pagination.totalPages}` : ''}
            </Typography>
          </Box>
        )}
      </Box>

      {/* Version list */}
      <Collapse in={expanded}>
        <Box>
          {entry.versions.map((version) => (
            <ComponentVersionItem
              key={version.id}
              version={version}
              isSelected={selectedVersionId === version.id}
              onClick={() => onSelectVersion(version)}
              isAdmin={isAdmin}
              onDeleteVersion={onDeleteVersion}
              isBatchMode={isBatchMode}
              isChecked={selectedVersionIds?.includes(version.id) || false}
              onToggleSelection={onToggleVersionSelection}
            />
          ))}

          {/* Pagination for multi-page components */}
          {pagination && pagination.totalPages > 1 && (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 1, px: 2 }}>
              <Pagination
                size="small"
                count={pagination.totalPages}
                page={pagination.currentPage}
                onChange={handlePageChange}
                siblingCount={0}
                boundaryCount={1}
              />
            </Box>
          )}
        </Box>
      </Collapse>

      <Divider />
    </Box>
  );
};

export const ComponentHistoryTree = ({
  components,
  selectedVersionId,
  onSelectVersion,
  isAdmin,
  onDeleteVersion,
  isBatchMode,
  selectedVersionIds,
  onToggleVersionSelection,
  onSelectAllVersions,
  onPageChange,
}: ComponentHistoryTreeProps) => {
  if (components.length === 0) {
    return (
      <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', py: 4, px: 2 }}>
        <InboxIcon sx={{ fontSize: 40, color: 'text.disabled', mb: 1 }} />
        <Typography variant="body2" color="text.secondary" textAlign="center">
          暂无组件源码
        </Typography>
      </Box>
    );
  }

  return (
    <Box>
      {components.map((entry) => (
        <ComponentNode
          key={entry.componentId}
          entry={entry}
          selectedVersionId={selectedVersionId}
          onSelectVersion={onSelectVersion}
          isAdmin={isAdmin}
          onDeleteVersion={onDeleteVersion}
          isBatchMode={isBatchMode}
          selectedVersionIds={selectedVersionIds}
          onToggleVersionSelection={onToggleVersionSelection}
          onSelectAllVersions={onSelectAllVersions}
          onPageChange={onPageChange}
        />
      ))}
    </Box>
  );
};
