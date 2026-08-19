import Chip from '@mui/material/Chip';
import { ComponentCodeTag } from './ComponentHistoryView.types';

interface TagBadgeProps {
  tag: ComponentCodeTag;
  onDelete?: () => void;
  size?: 'small' | 'medium';
}

export const TagBadge = ({ tag, onDelete, size = 'small' }: TagBadgeProps) => {
  return (
    <Chip
      label={tag.name}
      size={size}
      style={{
        backgroundColor: tag.color,
        color: getContrastColor(tag.color),
        marginRight: 4,
        marginBottom: 2,
      }}
      onDelete={onDelete}
    />
  );
};

function getContrastColor(hexColor: string): string {
  const r = parseInt(hexColor.slice(1, 3), 16);
  const g = parseInt(hexColor.slice(3, 5), 16);
  const b = parseInt(hexColor.slice(5, 7), 16);
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
  return luminance > 0.5 ? '#000000' : '#ffffff';
}
