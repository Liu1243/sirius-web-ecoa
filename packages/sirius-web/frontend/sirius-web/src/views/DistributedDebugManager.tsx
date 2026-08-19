/*******************************************************************************
 * Copyright (c) 2025 Dassault Aviation.
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

import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Checkbox from '@mui/material/Checkbox';
import Chip from '@mui/material/Chip';
import Container from '@mui/material/Container';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import IconButton from '@mui/material/IconButton';
import RefreshIcon from '@mui/icons-material/Refresh';
import StopIcon from '@mui/icons-material/Stop';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import DeleteIcon from '@mui/icons-material/Delete';
import DeleteSweepIcon from '@mui/icons-material/DeleteSweep';
import ComputerIcon from '@mui/icons-material/Computer';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import PersonIcon from '@mui/icons-material/Person';
import CircularProgress from '@mui/material/CircularProgress';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import Tooltip from '@mui/material/Tooltip';
import FormControlLabel from '@mui/material/FormControlLabel';
import Switch from '@mui/material/Switch';
import { useEffect, useState } from 'react';
import { NavigationBar } from '@eclipse-sirius/sirius-web-application';
import { loadSession, SessionUser } from '../auth/session';

interface DebugContainer {
  sessionId: string;
  projectId: string;
  projectName?: string;
  userId: string;
  username: string;
  targetDir: string;
  composeProjectName: string;
  networkName: string;
  dockerSubnet: string;
  clientContainer: string;
  clientConnected: boolean;
  runningServices: string[];
  configuredServices: string[];
  started: boolean;
  createdAt: string;
}

interface AllContainersResponse {
  success: boolean;
  containers: DebugContainer[];
  total: number;
  running: number;
  error?: string;
}

type DialogType = 'stop' | 'stopAll' | 'delete' | 'batchDelete';

const anonymousSession: SessionUser = {
  authenticated: false,
  id: null,
  username: null,
  displayName: null,
  admin: false,
};

export const DistributedDebugManager = () => {
  const [session, setSession] = useState<SessionUser>(anonymousSession);
  const [sessionLoading, setSessionLoading] = useState(true);
  const [containers, setContainers] = useState<DebugContainer[]>([]);
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState<string | null>(null); // sessionId of in-progress action
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [showAllUsers, setShowAllUsers] = useState(false);
  // Multi-select for batch delete (only stopped containers)
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [confirmDialog, setConfirmDialog] = useState<{
    open: boolean;
    container: DebugContainer | null;
    type: DialogType;
  }>({ open: false, container: null, type: 'stop' });

  const isAdmin = session.admin;

  // The backend (my-containers / admin/containers) handles user-scoping.
  // my-containers returns the calling user's containers + any unattributed ones;
  // admin/containers returns everything. No client-side userId filter needed.
  const displayedContainers = containers;

  const runningCount = displayedContainers.filter((c) => c.started).length;
  const stoppedCount = displayedContainers.filter((c) => !c.started).length;
  const totalCount = displayedContainers.length;

  // Selectable = stopped containers only
  const selectableIds = displayedContainers.filter((c) => !c.started).map((c) => c.sessionId);
  const allStoppedSelected = selectableIds.length > 0 && selectableIds.every((id) => selectedIds.has(id));
  const someStoppedSelected = selectableIds.some((id) => selectedIds.has(id));

  useEffect(() => {
    loadSession().then((currentSession) => {
      setSession(currentSession);
      setSessionLoading(false);
    });
  }, []);

  useEffect(() => {
    if (session.authenticated) {
      loadContainers();
    }
  }, [session.authenticated, showAllUsers]);

  // Clear selection when container list changes
  useEffect(() => {
    setSelectedIds(new Set());
  }, [containers]);

  const loadContainers = async () => {
    setLoading(true);
    setError('');
    try {
      const endpoint =
        isAdmin && showAllUsers ? '/api/distributed-debug/admin/containers' : '/api/distributed-debug/my-containers';

      const response = await fetch(endpoint);
      if (!response.ok) {
        if (response.status === 403) {
          setError('没有权限查看容器列表');
        } else {
          try {
            const errorData = await response.json();
            setError(errorData.error || '加载容器列表失败，请稍后重试');
          } catch {
            setError('加载容器列表失败，请稍后重试');
          }
        }
        setLoading(false);
        return;
      }

      const data: AllContainersResponse = await response.json();
      if (data.success) {
        setContainers(data.containers);
      } else {
        setError(data.error || '获取容器状态失败');
      }
    } catch {
      setError('网络错误，无法连接到服务器');
    } finally {
      setLoading(false);
    }
  };

  // ─── Start ────────────────────────────────────────────────────────────────

  const handleStartContainer = async (container: DebugContainer) => {
    setActionLoading(container.sessionId);
    setError('');
    setMessage('');

    try {
      const response = await fetch('/api/distributed-debug/start', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          target_dir: container.targetDir,
          client_container: container.clientContainer,
          project_id: container.projectId,
          user_id: container.userId,
          username: container.username,
        }),
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        setError(errorData.message || '启动容器失败');
        return;
      }

      const result = await response.json();
      if (result.success) {
        setMessage(`容器 ${container.composeProjectName} 已启动`);
        await loadContainers();
      } else {
        setError('启动容器失败');
      }
    } catch {
      setError('网络错误，无法启动容器');
    } finally {
      setActionLoading(null);
    }
  };

  // ─── Stop ─────────────────────────────────────────────────────────────────

  const handleStopContainer = async (container: DebugContainer) => {
    setConfirmDialog({ open: false, container: null, type: 'stop' });
    setActionLoading(container.sessionId);
    setError('');
    setMessage('');

    try {
      const response = await fetch('/api/distributed-debug/stop', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          target_dir: container.targetDir,
          client_container: container.clientContainer,
          session_id: container.sessionId,
        }),
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        setError(errorData.message || '停止容器失败');
        return;
      }

      const result = await response.json();
      if (result.success) {
        setMessage(`容器 ${container.composeProjectName} 已停止`);
        await loadContainers();
      } else {
        setError('停止容器失败');
      }
    } catch {
      setError('网络错误，无法停止容器');
    } finally {
      setActionLoading(null);
    }
  };

  const handleStopAllContainers = async () => {
    setConfirmDialog({ open: false, container: null, type: 'stopAll' });
    setLoading(true);
    setError('');
    setMessage('');

    try {
      const containersToStop = displayedContainers.filter((c) => c.started);
      let successCount = 0;
      let failCount = 0;

      for (const container of containersToStop) {
        try {
          const response = await fetch('/api/distributed-debug/stop', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              target_dir: container.targetDir,
              client_container: container.clientContainer,
              session_id: container.sessionId,
            }),
          });
          if (response.ok) {
            successCount++;
          } else {
            failCount++;
          }
        } catch {
          failCount++;
        }
      }

      if (successCount > 0) {
        setMessage(`已成功停止 ${successCount} 个容器${failCount > 0 ? `，${failCount} 个失败` : ''}`);
      } else if (failCount > 0) {
        setError(`停止容器失败，共 ${failCount} 个`);
      }

      await loadContainers();
    } catch {
      setError('网络错误，无法停止容器');
    } finally {
      setLoading(false);
    }
  };

  // ─── Delete ───────────────────────────────────────────────────────────────

  const handleDeleteContainer = async (container: DebugContainer) => {
    setConfirmDialog({ open: false, container: null, type: 'delete' });
    setActionLoading(container.sessionId);
    setError('');
    setMessage('');

    try {
      const response = await fetch('/api/distributed-debug/admin/delete-session', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ session_id: container.sessionId }),
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        setError(errorData.error || '删除会话失败');
        return;
      }

      const result = await response.json();
      if (result.success) {
        setMessage(`会话 ${container.composeProjectName} 已删除`);
        await loadContainers();
      } else {
        setError(result.error || '删除会话失败');
      }
    } catch {
      setError('网络错误，无法删除会话');
    } finally {
      setActionLoading(null);
    }
  };

  const handleBatchDeleteStopped = async () => {
    setConfirmDialog({ open: false, container: null, type: 'batchDelete' });
    setLoading(true);
    setError('');
    setMessage('');

    const ids = Array.from(selectedIds);
    if (ids.length === 0) {
      setLoading(false);
      return;
    }

    try {
      const response = await fetch('/api/distributed-debug/admin/batch-delete-sessions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ session_ids: ids }),
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        setError(errorData.error || '批量删除失败');
        setLoading(false);
        return;
      }

      const result = await response.json();
      if (result.successCount > 0) {
        setMessage(
          `已成功删除 ${result.successCount} 个会话${result.failCount > 0 ? `，${result.failCount} 个失败` : ''}`
        );
      } else {
        setError(`批量删除失败，共 ${result.failCount} 个`);
      }

      await loadContainers();
    } catch {
      setError('网络错误，无法批量删除会话');
    } finally {
      setLoading(false);
    }
  };

  // ─── Selection helpers ────────────────────────────────────────────────────

  const toggleSelect = (sessionId: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(sessionId)) {
        next.delete(sessionId);
      } else {
        next.add(sessionId);
      }
      return next;
    });
  };

  const toggleSelectAllStopped = () => {
    if (allStoppedSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(selectableIds));
    }
  };

  // ─── Confirm dialog helpers ───────────────────────────────────────────────

  const openStopConfirm = (container: DebugContainer) => setConfirmDialog({ open: true, container, type: 'stop' });

  const openStopAllConfirm = () => setConfirmDialog({ open: true, container: null, type: 'stopAll' });

  const openDeleteConfirm = (container: DebugContainer) => setConfirmDialog({ open: true, container, type: 'delete' });

  const openBatchDeleteConfirm = () => setConfirmDialog({ open: true, container: null, type: 'batchDelete' });

  const closeConfirm = () => setConfirmDialog({ open: false, container: null, type: 'stop' });

  const confirmAction = () => {
    if (confirmDialog.type === 'stop' && confirmDialog.container) {
      handleStopContainer(confirmDialog.container);
    } else if (confirmDialog.type === 'stopAll') {
      handleStopAllContainers();
    } else if (confirmDialog.type === 'delete' && confirmDialog.container) {
      handleDeleteContainer(confirmDialog.container);
    } else if (confirmDialog.type === 'batchDelete') {
      handleBatchDeleteStopped();
    }
  };

  const confirmDialogTitle = () => {
    switch (confirmDialog.type) {
      case 'stop':
        return '确认停止容器';
      case 'stopAll':
        return '确认停止所有容器';
      case 'delete':
        return '确认删除会话';
      case 'batchDelete':
        return `确认批量删除 ${selectedIds.size} 个会话`;
    }
  };

  const confirmDialogText = () => {
    switch (confirmDialog.type) {
      case 'stop':
        return `确定要停止容器 "${confirmDialog.container?.composeProjectName}" 吗？此操作不可撤销。`;
      case 'stopAll':
        return `确定要停止所有 ${runningCount} 个运行中的容器吗？此操作不可撤销。`;
      case 'delete':
        return `确定要删除会话 "${confirmDialog.container?.composeProjectName}" 的记录吗？只有已停止的容器才能删除，此操作将清理会话元数据文件。`;
      case 'batchDelete':
        return `确定要删除选中的 ${selectedIds.size} 个已停止会话的记录吗？此操作将清理对应的会话元数据文件，不可撤销。`;
    }
  };

  const confirmButtonLabel = () => {
    switch (confirmDialog.type) {
      case 'stop':
      case 'stopAll':
        return '确认停止';
      case 'delete':
      case 'batchDelete':
        return '确认删除';
    }
  };

  const confirmButtonColor = (): 'error' | 'warning' => {
    return confirmDialog.type === 'delete' || confirmDialog.type === 'batchDelete' ? 'error' : 'error';
  };

  const formatDate = (dateStr: string | null | undefined) => {
    if (!dateStr) return '未知';
    try {
      const d = new Date(dateStr);
      return isNaN(d.getTime()) ? '未知' : d.toLocaleString('zh-CN');
    } catch {
      return '未知';
    }
  };

  const formatUsername = (container: DebugContainer) => {
    if (container.username) return container.username;
    if (container.userId && container.userId !== 'unknown') return container.userId;
    return '未知';
  };

  if (sessionLoading) {
    return null;
  }

  if (!session.authenticated) {
    return (
      <Box sx={{ minHeight: '100vh', backgroundColor: '#f6f8fb' }}>
        <NavigationBar />
        <Container maxWidth="xl" sx={{ py: 5 }}>
          <Alert severity="warning">请先登录后访问此页面</Alert>
        </Container>
      </Box>
    );
  }

  const isAnyActionLoading = loading || actionLoading !== null;

  return (
    <Box sx={{ minHeight: '100vh', backgroundColor: '#f6f8fb' }}>
      <NavigationBar />
      <Container maxWidth="xl" sx={{ py: 4 }}>
        <Stack spacing={3}>
          {/* Header */}
          <Box
            sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 2 }}>
            <Box>
              <Typography variant="h4" sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <ComputerIcon fontSize="large" color="primary" />
                分布式调试容器管理
              </Typography>
              <Typography color="text.secondary" sx={{ mt: 0.5 }}>
                查看和管理分布式调试会话中的容器
              </Typography>
            </Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              {isAdmin && (
                <FormControlLabel
                  control={
                    <Switch checked={showAllUsers} onChange={(e) => setShowAllUsers(e.target.checked)} size="small" />
                  }
                  label={
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                      <AdminPanelSettingsIcon fontSize="small" />
                      <Typography variant="body2">显示所有用户</Typography>
                    </Box>
                  }
                />
              )}
              <Tooltip title="刷新列表">
                <IconButton onClick={loadContainers} disabled={isAnyActionLoading} size="small">
                  {loading ? <CircularProgress size={20} /> : <RefreshIcon />}
                </IconButton>
              </Tooltip>
            </Box>
          </Box>

          {/* Status Summary */}
          <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
            <Card sx={{ flex: 1, minWidth: 160 }}>
              <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
                <Typography variant="caption" color="text.secondary">
                  总容器数
                </Typography>
                <Typography variant="h4">{totalCount}</Typography>
              </CardContent>
            </Card>
            <Card sx={{ flex: 1, minWidth: 160 }}>
              <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
                <Typography variant="caption" color="text.secondary">
                  运行中
                </Typography>
                <Typography variant="h4" color={runningCount > 0 ? 'success.main' : 'text.primary'}>
                  {runningCount}
                </Typography>
              </CardContent>
            </Card>
            <Card sx={{ flex: 1, minWidth: 160 }}>
              <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
                <Typography variant="caption" color="text.secondary">
                  已停止
                </Typography>
                <Typography variant="h4">{stoppedCount}</Typography>
              </CardContent>
            </Card>
          </Box>

          {/* Messages */}
          {message && (
            <Alert severity="success" onClose={() => setMessage('')}>
              {message}
            </Alert>
          )}
          {error && (
            <Alert severity="error" onClose={() => setError('')}>
              {error}
            </Alert>
          )}

          {/* Container List */}
          <Card>
            <CardContent>
              <Box
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  mb: 2,
                  flexWrap: 'wrap',
                  gap: 1,
                }}>
                <Typography variant="h6">
                  容器列表
                  {showAllUsers && isAdmin && <Chip size="small" label="管理员视图" color="primary" sx={{ ml: 1 }} />}
                </Typography>
                <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                  {/* Batch delete button — visible when stopped containers are selected */}
                  {someStoppedSelected && (
                    <Button
                      variant="contained"
                      color="error"
                      size="small"
                      startIcon={<DeleteSweepIcon />}
                      onClick={openBatchDeleteConfirm}
                      disabled={isAnyActionLoading}>
                      删除选中 ({selectedIds.size})
                    </Button>
                  )}
                  {/* Stop all button */}
                  {runningCount > 0 && (
                    <Button
                      variant="outlined"
                      color="error"
                      size="small"
                      startIcon={<StopIcon />}
                      onClick={openStopAllConfirm}
                      disabled={isAnyActionLoading}>
                      停止所有容器
                    </Button>
                  )}
                </Box>
              </Box>

              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      {/* Select-all checkbox for stopped containers */}
                      <TableCell padding="checkbox">
                        <Tooltip title={stoppedCount === 0 ? '没有可删除的已停止容器' : '全选已停止容器'}>
                          <span>
                            <Checkbox
                              size="small"
                              indeterminate={someStoppedSelected && !allStoppedSelected}
                              checked={allStoppedSelected}
                              onChange={toggleSelectAllStopped}
                              disabled={stoppedCount === 0 || isAnyActionLoading}
                            />
                          </span>
                        </Tooltip>
                      </TableCell>
                      <TableCell>状态</TableCell>
                      {showAllUsers && isAdmin && <TableCell>用户</TableCell>}
                      <TableCell>项目</TableCell>
                      <TableCell>会话ID</TableCell>
                      <TableCell>服务</TableCell>
                      <TableCell>网络</TableCell>
                      <TableCell>创建时间</TableCell>
                      <TableCell align="right">操作</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {displayedContainers.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={showAllUsers && isAdmin ? 9 : 8} align="center" sx={{ py: 4 }}>
                          <Typography color="text.secondary">{loading ? '加载中...' : '暂无容器'}</Typography>
                        </TableCell>
                      </TableRow>
                    ) : (
                      displayedContainers.map((container) => {
                        const isRowLoading = actionLoading === container.sessionId;
                        const isStopped = !container.started;
                        const isSelected = selectedIds.has(container.sessionId);

                        return (
                          <TableRow
                            key={container.sessionId}
                            selected={isSelected}
                            sx={{
                              opacity: isRowLoading ? 0.6 : 1,
                              transition: 'opacity 0.2s',
                            }}>
                            {/* Checkbox — only for stopped containers */}
                            <TableCell padding="checkbox">
                              {isStopped && (
                                <Checkbox
                                  size="small"
                                  checked={isSelected}
                                  onChange={() => toggleSelect(container.sessionId)}
                                  disabled={isAnyActionLoading}
                                />
                              )}
                            </TableCell>
                            <TableCell>
                              <Chip
                                size="small"
                                label={container.started ? '运行中' : '已停止'}
                                color={container.started ? 'success' : 'default'}
                                variant={container.started ? 'filled' : 'outlined'}
                              />
                            </TableCell>
                            {showAllUsers && isAdmin && (
                              <TableCell>
                                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                                  <PersonIcon fontSize="small" color="action" />
                                  <Typography variant="body2">{formatUsername(container)}</Typography>
                                </Box>
                              </TableCell>
                            )}
                            <TableCell>
                              <Typography variant="body2" fontWeight={500}>
                                {container.projectName ||
                                  (container.projectId && container.projectId !== 'unknown'
                                    ? container.projectId
                                    : '未知')}
                              </Typography>
                              <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                                {container.composeProjectName}
                              </Typography>
                            </TableCell>
                            <TableCell>
                              <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.75rem' }}>
                                {container.sessionId}
                              </Typography>
                            </TableCell>
                            <TableCell>
                              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
                                <Typography variant="caption">
                                  运行: {container.runningServices.length}/{container.configuredServices.length}
                                </Typography>
                                <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
                                  {container.runningServices.slice(0, 3).map((service) => (
                                    <Chip
                                      key={service}
                                      size="small"
                                      label={service.replace('ecoa-', '')}
                                      color="success"
                                      variant="outlined"
                                      sx={{ height: 20, fontSize: '0.65rem' }}
                                    />
                                  ))}
                                  {container.runningServices.length > 3 && (
                                    <Chip
                                      size="small"
                                      label={`+${container.runningServices.length - 3}`}
                                      variant="outlined"
                                      sx={{ height: 20, fontSize: '0.65rem' }}
                                    />
                                  )}
                                </Box>
                              </Box>
                            </TableCell>
                            <TableCell>
                              <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                                {container.dockerSubnet}
                              </Typography>
                              {container.clientConnected && (
                                <Chip
                                  size="small"
                                  label="Client已连接"
                                  color="info"
                                  variant="outlined"
                                  sx={{
                                    height: 18,
                                    fontSize: '0.6rem',
                                    mt: 0.5,
                                    display: 'block',
                                    width: 'fit-content',
                                  }}
                                />
                              )}
                            </TableCell>
                            <TableCell>
                              <Typography variant="caption">{formatDate(container.createdAt)}</Typography>
                            </TableCell>
                            <TableCell align="right">
                              <Box sx={{ display: 'flex', gap: 0.5, justifyContent: 'flex-end' }}>
                                {/* Start button — only for stopped containers */}
                                {isStopped && (
                                  <Tooltip
                                    title={
                                      !container.targetDir
                                        ? '无法启动：会话元数据丢失，请删除此记录后重新发起调试'
                                        : '启动容器'
                                    }>
                                    <span>
                                      <IconButton
                                        size="small"
                                        color="success"
                                        onClick={() => handleStartContainer(container)}
                                        disabled={isAnyActionLoading || !container.targetDir}>
                                        {isRowLoading ? (
                                          <CircularProgress size={16} color="success" />
                                        ) : (
                                          <PlayArrowIcon fontSize="small" />
                                        )}
                                      </IconButton>
                                    </span>
                                  </Tooltip>
                                )}
                                {/* Stop button — only for running containers */}
                                {container.started && (
                                  <Tooltip title="停止容器">
                                    <span>
                                      <IconButton
                                        size="small"
                                        color="error"
                                        onClick={() => openStopConfirm(container)}
                                        disabled={isAnyActionLoading}>
                                        {isRowLoading ? (
                                          <CircularProgress size={16} color="error" />
                                        ) : (
                                          <StopIcon fontSize="small" />
                                        )}
                                      </IconButton>
                                    </span>
                                  </Tooltip>
                                )}
                                {/* Delete button — only for stopped containers */}
                                {isStopped && (
                                  <Tooltip title="删除会话记录">
                                    <span>
                                      <IconButton
                                        size="small"
                                        color="error"
                                        onClick={() => openDeleteConfirm(container)}
                                        disabled={isAnyActionLoading}>
                                        {isRowLoading ? (
                                          <CircularProgress size={16} color="error" />
                                        ) : (
                                          <DeleteIcon fontSize="small" />
                                        )}
                                      </IconButton>
                                    </span>
                                  </Tooltip>
                                )}
                              </Box>
                            </TableCell>
                          </TableRow>
                        );
                      })
                    )}
                  </TableBody>
                </Table>
              </TableContainer>
            </CardContent>
          </Card>

          {/* Info Card */}
          <Card variant="outlined">
            <CardContent>
              <Typography variant="subtitle2" gutterBottom>
                使用说明
              </Typography>
              <Typography variant="body2" color="text.secondary">
                • 此页面显示所有分布式调试会话中的 Docker 容器状态
                <br />
                • 普通用户只能查看和管理自己的容器
                <br />
                • 管理员可以查看所有用户的容器，并可以停止任何容器
                <br />• <strong>启动</strong>（▶）：重新启动已停止的容器
                <br />• <strong>停止</strong>（■）：停止运行中的容器
                <br />• <strong>删除</strong>（🗑）：清理已停止容器的会话记录和 Compose 文件（容器必须已停止）
                <br />
                • 勾选已停止的容器后可点击「删除选中」进行批量删除
                <br />• 建议在调试完成后及时停止并删除容器以释放资源
              </Typography>
            </CardContent>
          </Card>
        </Stack>
      </Container>

      {/* Confirmation Dialog */}
      <Dialog open={confirmDialog.open} onClose={closeConfirm} maxWidth="xs" fullWidth>
        <DialogTitle>{confirmDialogTitle()}</DialogTitle>
        <DialogContent>
          <DialogContentText>{confirmDialogText()}</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeConfirm} disabled={isAnyActionLoading}>
            取消
          </Button>
          <Button
            onClick={confirmAction}
            color={confirmButtonColor()}
            variant="contained"
            disabled={isAnyActionLoading}
            autoFocus>
            {isAnyActionLoading ? <CircularProgress size={20} /> : confirmButtonLabel()}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};
