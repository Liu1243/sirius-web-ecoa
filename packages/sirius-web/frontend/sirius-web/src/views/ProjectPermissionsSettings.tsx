import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Divider from '@mui/material/Divider';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMultiToast } from '@eclipse-sirius/sirius-components-core';
import { ChangeEvent, useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { loadSession, SessionUser } from '../auth/session';

type ProjectRole = 'ACCESS';

interface UserSummary {
  id: string;
  username: string;
  displayName: string;
  admin: boolean;
  active: boolean;
}

interface ProjectMembership {
  userId: string;
  username: string;
  displayName: string;
  admin: boolean;
  role: ProjectRole;
}

interface ProjectPermissionsResponse {
  projectId: string;
  memberships: ProjectMembership[];
  users: UserSummary[];
}

const anonymousSession: SessionUser = {
  authenticated: false,
  id: null,
  username: null,
  displayName: null,
  admin: false,
};

export const ProjectPermissionsSettings = () => {
  const { projectId } = useParams();
  const { addErrorMessage, addMessages } = useMultiToast();
  const [session, setSession] = useState<SessionUser>(anonymousSession);
  const [data, setData] = useState<ProjectPermissionsResponse | null>(null);
  const [accessError, setAccessError] = useState('');
  const [loading, setLoading] = useState(true);
  const [selectedUserId, setSelectedUserId] = useState('');
  const [createForm, setCreateForm] = useState({
    username: '',
    displayName: '',
    password: '',
    admin: false,
  });
  const [createUsernameError, setCreateUsernameError] = useState('');
  const [createDisplayNameError, setCreateDisplayNameError] = useState('');
  const [createPasswordError, setCreatePasswordError] = useState('');

  const addSuccessMessage = (message: string) => addMessages([{ body: message, level: 'SUCCESS' }]);

  const getRequestErrorMessage = (status: number, action: string) => {
    if (status === 401) {
      return `请先登录后再${action}`;
    }
    if (status === 403) {
      return `当前账号没有权限${action}。只有全局管理员或该项目的权限管理员可以执行此操作。`;
    }
    if (status === 404) {
      return `目标项目或账号不存在，无法${action}`;
    }
    return `${action}失败，请稍后重试`;
  };

  const availableUsers = useMemo(() => {
    if (!data) {
      return [];
    }
    const memberIds = new Set(data.memberships.map((membership) => membership.userId));
    return data.users.filter((user) => !user.admin && !memberIds.has(user.id));
  }, [data]);

  const loadData = async () => {
    if (!projectId) {
      return;
    }
    setLoading(true);
    const [sessionResponse, permissionsResponse] = await Promise.all([
      loadSession(),
      fetch(`/api/admin/projects/${projectId}/permissions`, {
        credentials: 'include',
      }),
    ]);
    setSession(sessionResponse);
    setAccessError('');
    if (!permissionsResponse.ok) {
      setLoading(false);
      setData(null);
      setAccessError(getRequestErrorMessage(permissionsResponse.status, '管理该项目的权限'));
      return;
    }
    const body = (await permissionsResponse.json()) as ProjectPermissionsResponse;
    setData(body);
    setLoading(false);
  };

  useEffect(() => {
    loadData();
  }, [projectId]);

  const grantProjectAccess = async (userId: string) => {
    if (!projectId) {
      return;
    }
    const response = await fetch(`/api/admin/projects/${projectId}/permissions/${userId}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      credentials: 'include',
      body: JSON.stringify({ role: 'ACCESS' }),
    });
    if (!response.ok) {
      addErrorMessage(getRequestErrorMessage(response.status, '授予项目权限'));
      return;
    }
    addSuccessMessage('项目权限已授予');
    await loadData();
  };

  const removeMembership = async (userId: string) => {
    if (!projectId) {
      return;
    }
    const response = await fetch(`/api/admin/projects/${projectId}/permissions/${userId}`, {
      method: 'DELETE',
      credentials: 'include',
    });
    if (!response.ok) {
      addErrorMessage(getRequestErrorMessage(response.status, '移除项目权限'));
      return;
    }
    addSuccessMessage('项目权限已移除');
    await loadData();
  };

  const addExistingUser = async () => {
    if (!selectedUserId) {
      return;
    }
    await grantProjectAccess(selectedUserId);
    setSelectedUserId('');
  };

  const createUser = async () => {
    if (!projectId) {
      return;
    }
    setCreateUsernameError('');
    setCreateDisplayNameError('');
    setCreatePasswordError('');

    // 前置校验：检查用户名 / 显示名是否与已有用户冲突（大小写不敏感）
    const allUsers = data?.users ?? [];
    const lowerUsername = createForm.username.trim().toLowerCase();
    const lowerDisplayName = createForm.displayName.trim().toLowerCase();
    let hasFieldError = false;
    if (lowerUsername && allUsers.some((u) => u.username.toLowerCase() === lowerUsername)) {
      setCreateUsernameError('用户名已存在。');
      hasFieldError = true;
    }
    if (lowerDisplayName && allUsers.some((u) => u.displayName.toLowerCase() === lowerDisplayName)) {
      setCreateDisplayNameError('显示名已存在。');
      hasFieldError = true;
    }
    if (createForm.password.length < 8) {
      setCreatePasswordError('初始密码不能少于 8 位。');
      hasFieldError = true;
    }
    if (hasFieldError) {
      return;
    }

    const response = await fetch('/api/admin/users', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      credentials: 'include',
      body: JSON.stringify({
        username: createForm.username,
        displayName: createForm.displayName,
        password: createForm.password,
        admin: createForm.admin,
        projectId,
        role: 'ACCESS',
      }),
    });
    if (!response.ok) {
      if (response.status === 409) {
        // 解析服务端返回的具体冲突字段
        try {
          const body = await response.json();
          const msg: string = (body?.message ?? '').toLowerCase();
          if (msg.includes('display name')) {
            setCreateDisplayNameError('显示名已存在。');
          } else {
            setCreateUsernameError('用户名已存在。');
          }
        } catch {
          addErrorMessage('创建用户失败，用户名或显示名已存在');
        }
      } else if (response.status === 400) {
        try {
          const body = await response.json();
          const msg: string = body?.message ?? '';
          if (msg.toLowerCase().includes('password') && msg.toLowerCase().includes('8')) {
            setCreatePasswordError('初始密码不能少于 8 位。');
          } else {
            addErrorMessage(msg || getRequestErrorMessage(response.status, '创建用户并授予项目权限'));
          }
        } catch {
          addErrorMessage(getRequestErrorMessage(response.status, '创建用户并授予项目权限'));
        }
      } else {
        addErrorMessage(getRequestErrorMessage(response.status, '创建用户并授予项目权限'));
      }
      return;
    }
    addSuccessMessage('用户已创建并授予项目权限');
    setCreateForm({
      username: '',
      displayName: '',
      password: '',
      admin: false,
    });
    await loadData();
  };

  if (loading) {
    return <Typography>加载中...</Typography>;
  }

  if (!data) {
    return <Alert severity="warning">{accessError || '当前用户没有权限管理该项目的成员访问。'}</Alert>;
  }

  return (
    <Stack spacing={3}>
      <Card variant="outlined">
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="h6">项目授权用户</Typography>
            <Typography color="text.secondary">
              只有两种状态：有项目权限和没有项目权限。有权限的用户可以完整查看、编辑并管理该项目。
            </Typography>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>用户</TableCell>
                  <TableCell>账号</TableCell>
                  <TableCell>全局管理员</TableCell>
                  <TableCell>项目权限</TableCell>
                  <TableCell align="right">操作</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {data.memberships.map((membership) => (
                  <TableRow key={membership.userId}>
                    <TableCell>{membership.displayName}</TableCell>
                    <TableCell>{membership.username}</TableCell>
                    <TableCell>{membership.admin ? '是' : '否'}</TableCell>
                    <TableCell>已授权</TableCell>
                    <TableCell align="right">
                      <Button color="error" size="small" onClick={() => removeMembership(membership.userId)}>
                        移除权限
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Stack>
        </CardContent>
      </Card>

      <Card variant="outlined">
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="h6">给已有用户授权</Typography>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
              <FormControl fullWidth>
                <InputLabel>用户</InputLabel>
                <Select label="用户" value={selectedUserId} onChange={(event) => setSelectedUserId(event.target.value)}>
                  {availableUsers.map((user) => (
                    <MenuItem key={user.id} value={user.id}>
                      {user.displayName} ({user.username})
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <Button variant="contained" onClick={addExistingUser}>
                授予项目权限
              </Button>
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      <Card variant="outlined">
        <CardContent>
          <Stack spacing={2.5}>
            <Typography variant="h6">新建用户并授权</Typography>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
              <TextField
                label="用户名"
                fullWidth
                value={createForm.username}
                onChange={(event) => {
                  setCreateUsernameError('');
                  setCreateForm({ ...createForm, username: event.target.value });
                }}
                error={!!createUsernameError}
                helperText={createUsernameError}
              />
              <TextField
                label="显示名"
                fullWidth
                value={createForm.displayName}
                onChange={(event) => {
                  setCreateDisplayNameError('');
                  setCreateForm({ ...createForm, displayName: event.target.value });
                }}
                error={!!createDisplayNameError}
                helperText={createDisplayNameError}
              />
            </Stack>
            <TextField
              label="初始密码"
              type="password"
              fullWidth
              value={createForm.password}
              onChange={(event) => {
                setCreatePasswordError('');
                setCreateForm({ ...createForm, password: event.target.value });
              }}
              error={!!createPasswordError}
              helperText={createPasswordError || '不少于 8 位。'}
            />
            <Divider />
            <Box>
              <label>
                <input
                  type="checkbox"
                  checked={createForm.admin}
                  disabled={!session.admin}
                  onChange={(event: ChangeEvent<HTMLInputElement>) =>
                    setCreateForm({ ...createForm, admin: event.target.checked })
                  }
                />{' '}
                创建为全局管理员
              </label>
            </Box>
            <Button variant="contained" onClick={createUser}>
              创建用户并授予项目权限
            </Button>
          </Stack>
        </CardContent>
      </Card>
    </Stack>
  );
};
