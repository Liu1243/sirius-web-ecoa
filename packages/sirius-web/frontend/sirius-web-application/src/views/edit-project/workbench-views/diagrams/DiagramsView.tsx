/*******************************************************************************
 * Copyright (c) 2026 Obeo.
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
import {
  IconOverlay,
  useSelection,
  useWorkbench,
  WorkbenchViewComponentProps,
  WorkbenchViewHandle,
} from '@eclipse-sirius/sirius-components-core';
import BubbleChartIcon from '@mui/icons-material/BubbleChart';
import CircleIcon from '@mui/icons-material/Circle';
import RefreshIcon from '@mui/icons-material/Refresh';
import CircularProgress from '@mui/material/CircularProgress';
import IconButton from '@mui/material/IconButton';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { Theme } from '@mui/material/styles';
import { ForwardedRef, forwardRef, useImperativeHandle } from 'react';
import { makeStyles } from 'tss-react/mui';
import { useAllRepresentations } from './useAllRepresentations';

const useStyles = makeStyles()((theme: Theme) => ({
  root: {
    display: 'grid',
    gridTemplateRows: 'auto 1fr',
    overflow: 'hidden',
    height: '100%',
  },
  toolbar: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingLeft: theme.spacing(1),
    paddingRight: theme.spacing(0.5),
    borderBottomWidth: '1px',
    borderBottomStyle: 'solid',
    borderBottomColor: theme.palette.divider,
    minHeight: theme.spacing(4),
  },
  toolbarTitle: {
    fontSize: '0.75rem',
    fontWeight: 500,
    color: theme.palette.text.secondary,
    textTransform: 'uppercase',
    letterSpacing: '0.08em',
  },
  list: {
    overflow: 'auto',
    padding: 0,
  },
  empty: {
    padding: theme.spacing(1),
    color: theme.palette.text.secondary,
  },
  itemIcon: {
    minWidth: theme.spacing(4),
  },
  openDot: {
    fontSize: '0.5rem',
    color: theme.palette.primary.main,
  },
}));

export const DiagramsView = forwardRef<WorkbenchViewHandle, WorkbenchViewComponentProps>(
  ({ id, editingContextId }: WorkbenchViewComponentProps, ref: ForwardedRef<WorkbenchViewHandle>) => {
    const { classes } = useStyles();
    const { representationsMetadata, displayedRepresentationMetadata } = useWorkbench();
    const { setSelection } = useSelection();
    const { representations, loading, refetch } = useAllRepresentations(editingContextId);

    useImperativeHandle(ref, () => ({
      id,
      getWorkbenchViewConfiguration: () => ({}),
      applySelection: null,
    }));

    const openIds = new Set(representationsMetadata.map((r) => r.id));

    const handleClick = (representationId: string) => {
      setSelection({ entries: [{ id: representationId }] });
    };

    return (
      <div className={classes.root}>
        <div className={classes.toolbar}>
          <span className={classes.toolbarTitle}>视图列表</span>
          <Tooltip title="刷新列表">
            <span>
              <IconButton size="small" onClick={() => refetch()} disabled={loading}>
                {loading ? <CircularProgress size={14} /> : <RefreshIcon fontSize="small" />}
              </IconButton>
            </span>
          </Tooltip>
        </div>

        {representations.length === 0 && !loading ? (
          <Typography className={classes.empty} variant="subtitle2">
            该项目暂无视图
          </Typography>
        ) : (
          <List className={classes.list} dense>
            {representations.map((representation) => {
              const isActive = displayedRepresentationMetadata?.id === representation.id;
              const isOpen = openIds.has(representation.id);
              return (
                <ListItem
                  key={representation.id}
                  disablePadding
                  secondaryAction={
                    isOpen ? (
                      <Tooltip title="已打开">
                        <CircleIcon className={classes.openDot} />
                      </Tooltip>
                    ) : undefined
                  }>
                  <ListItemButton
                    selected={isActive}
                    onClick={() => handleClick(representation.id)}
                    dense>
                    <ListItemIcon className={classes.itemIcon}>
                      {representation.iconURLs && representation.iconURLs.length > 0 ? (
                        <IconOverlay iconURLs={representation.iconURLs} alt={representation.label} />
                      ) : (
                        <BubbleChartIcon fontSize="small" />
                      )}
                    </ListItemIcon>
                    <ListItemText
                      primary={representation.label}
                      primaryTypographyProps={{
                        noWrap: true,
                        title: representation.label,
                        variant: 'body2',
                        fontWeight: isActive ? 600 : 400,
                      }}
                    />
                  </ListItemButton>
                </ListItem>
              );
            })}
          </List>
        )}
      </div>
    );
  }
);
