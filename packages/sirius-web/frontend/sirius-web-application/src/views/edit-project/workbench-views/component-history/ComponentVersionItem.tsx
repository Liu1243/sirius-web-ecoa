import { useState } from 'react';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import DeleteIcon from '@mui/icons-material/Delete';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import { ComponentCodeVersion } from './ComponentHistoryView.types';

// Returns black or white depending on background luminance
function getContrastColor(hexColor: string): string {
  const r = parseInt(hexColor.slice(1, 3), 16);
  const g = parseInt(hexColor.slice(3, 5), 16);
  const b = parseInt(hexColor.slice(5, 7), 16);
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
  return luminance > 0.5 ? '#000000' : '#ffffff';
}

interface ComponentVersionItemProps {
  version: ComponentCodeVersion;
  isSelected: boolean;
  onClick: () => void;
  isAdmin: boolean;
  onDeleteVersion: (versionId: string) => Promise<boolean>;
  isBatchMode?: boolean;
  isChecked?: boolean;
  onToggleSelection?: (versionId: string) => void;
}

export const ComponentVersionItem = ({
  version,
  isSelected,
  onClick,
  isAdmin,
  onDeleteVersion,
  isBatchMode,
  isChecked,
  onToggleSelection,
}: ComponentVersionItemProps) => {
  const formattedDate = new Date(version.createdAt).toLocaleDateString('zh-CN');
  const [hovered, setHovered] = useState(false);
  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    event.stopPropagation();
    setMenuAnchor(event.currentTarget);
  };

  const handleMenuClose = () => setMenuAnchor(null);

  const handleDeleteClick = () => {
    handleMenuClose();
    setDeleteDialogOpen(true);
  };

  const handleDeleteConfirm = async () => {
    setDeleting(true);
    const success = await onDeleteVersion(version.id);
    setDeleting(false);
    if (success) setDeleteDialogOpen(false);
  };

  const handleCheckboxClick = (event: React.ChangeEvent<HTMLInputElement>) => {
    event.stopPropagation();
    if (onToggleSelection) onToggleSelection(version.id);
  };

  const handleBoxClick = () => {
    if (!isBatchMode) onClick();
  };

  // MoreVert is visible when hovered or while its menu is open
  const showActions = hovered || Boolean(menuAnchor);

  return (
    <>
      <Box
        onClick={handleBoxClick}
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
        sx={{
          display: 'flex',
          alignItems: 'flex-start',
          padding: isBatchMode ? '6px 8px 6px 16px' : '6px 8px 6px 24px',
          cursor: isBatchMode ? 'default' : 'pointer',
          backgroundColor: isSelected ? 'action.selected' : 'transparent',
          '&:hover': { backgroundColor: 'action.hover' },
          borderLeft: '3px solid',
          borderColor: isSelected ? 'primary.main' : 'transparent',
          transition: 'border-color 0.15s',
        }}>
        {isBatchMode && isAdmin && (
          <Checkbox
            size="small"
            checked={isChecked || false}
            onChange={handleCheckboxClick}
            sx={{ padding: 0, marginRight: 1, mt: 0.25 }}
          />
        )}

        {/* Main content */}
        <Box sx={{ flex: 1, minWidth: 0 }}>
          {/* Version name */}
          <Typography variant="body2" fontWeight={isSelected ? 600 : 400} noWrap sx={{ lineHeight: 1.4 }}>
            {version.versionName}
          </Typography>

          {/* Date · author */}
          <Typography variant="caption" color="text.secondary" noWrap display="block" sx={{ lineHeight: 1.4 }}>
            {formattedDate} · {version.author}
          </Typography>

          {/* Tags row */}
          {version.tags.length > 0 && (
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.25, mt: 0.5 }}>
              {version.tags.map((tag) => (
                <Chip
                  key={tag.id}
                  label={tag.name}
                  size="small"
                  sx={{
                    backgroundColor: tag.color,
                    color: getContrastColor(tag.color),
                    height: 18,
                    fontSize: '0.6rem',
                    '& .MuiChip-label': { px: 0.75 },
                  }}
                />
              ))}
            </Box>
          )}

          {/* Commit message — up to 2 lines */}
          {version.commitMessage && (
            <Typography
              variant="caption"
              color="text.secondary"
              sx={{
                display: '-webkit-box',
                WebkitLineClamp: 2,
                WebkitBoxOrient: 'vertical',
                overflow: 'hidden',
                fontStyle: 'italic',
                mt: 0.5,
                lineHeight: 1.3,
              }}>
              {version.commitMessage}
            </Typography>
          )}
        </Box>

        {/* Admin action button — visible on hover or while menu is open */}
        {isAdmin && !isBatchMode && (
          <IconButton
            size="small"
            onClick={handleMenuOpen}
            sx={{
              opacity: showActions ? 1 : 0,
              transition: 'opacity 0.15s',
              flexShrink: 0,
              mt: -0.25,
              ml: 0.5,
            }}>
            <MoreVertIcon fontSize="small" />
          </IconButton>
        )}
      </Box>

      {/* Admin context menu */}
      <Menu
        anchorEl={menuAnchor}
        open={Boolean(menuAnchor)}
        onClose={handleMenuClose}
        onClick={(e) => e.stopPropagation()}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}>
        <MenuItem onClick={handleDeleteClick} sx={{ color: 'error.main' }}>
          <ListItemIcon>
            <DeleteIcon fontSize="small" color="error" />
          </ListItemIcon>
          <ListItemText>删除版本</ListItemText>
        </MenuItem>
      </Menu>

      {/* Delete confirmation dialog */}
      <Dialog open={deleteDialogOpen} onClose={() => setDeleteDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>确认删除版本</DialogTitle>
        <DialogContent>
          <DialogContentText>
            您确定要删除版本 <strong>{version.versionName}</strong> 吗？此操作不可撤销。
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialogOpen(false)} disabled={deleting}>
            取消
          </Button>
          <Button onClick={handleDeleteConfirm} color="error" variant="contained" disabled={deleting}>
            {deleting ? '删除中...' : '删除'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};
