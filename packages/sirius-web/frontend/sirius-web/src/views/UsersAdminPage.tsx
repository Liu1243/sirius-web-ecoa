import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Checkbox from '@mui/material/Checkbox';
import Container from '@mui/material/Container';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { FormEvent, useEffect, useMemo, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { NavigationBar } from '@eclipse-sirius/sirius-web-application';
import { loadSession, SessionUser } from '../auth/session';

interface UserSummary {
  id: string;
  username: string;
  displayName: string;
  admin: boolean;
  active: boolean;
}

const anonymousSession: SessionUser = {
  authenticated: false,
  id: null,
  username: null,
  displayName: null,
  admin: false,
};

const emptyCreateForm = {
  username: '',
  displayName: '',
  password: '',
  admin: false,
};

const emptyEditForm = {
  username: '',
  displayName: '',
  password: '',
  admin: false,
};

export const UsersAdminPage = () => {
  const [session, setSession] = useState<SessionUser>(anonymousSession);
  const [sessionLoading, setSessionLoading] = useState(true);
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [usersLoading, setUsersLoading] = useState(false);
  const [selectedUserId, setSelectedUserId] = useState('');
  const [createForm, setCreateForm] = useState(emptyCreateForm);
  const [editForm, setEditForm] = useState(emptyEditForm);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [createPasswordError, setCreatePasswordError] = useState('');
  const [createUsernameError, setCreateUsernameError] = useState('');
  const [createDisplayNameError, setCreateDisplayNameError] = useState('');
  const [editUsernameError, setEditUsernameError] = useState('');
  const [editDisplayNameError, setEditDisplayNameError] = useState('');
  const [pendingDeleteUser, setPendingDeleteUser] = useState<UserSummary | null>(null);

  const selectedUser = useMemo(() => users.find((user) => user.id === selectedUserId) ?? null, [users, selectedUserId]);
  const isCurrentUser = (user: UserSummary) => user.id === session.id;

  const loadUsers = async () => {
    setUsersLoading(true);
    setError('');
    const response = await fetch('/api/admin/users');
    if (!response.ok) {
      setUsersLoading(false);
      setError(response.status === 403 ? '只有管理员可以管理系统用户。' : '加载用户列表失败，请稍后重试。');
      return;
    }
    const body = (await response.json()) as UserSummary[];
    setUsers(body);
    setUsersLoading(false);
  };

  useEffect(() => {
    loadSession().then((currentSession) => {
      setSession(currentSession);
      setSessionLoading(false);
    });
  }, []);

  useEffect(() => {
    if (session.authenticated && session.admin) {
      loadUsers();
    }
  }, [session.authenticated, session.admin]);

  useEffect(() => {
    setEditUsernameError('');
    setEditDisplayNameError('');
    if (!selectedUser) {
      setEditForm(emptyEditForm);
      return;
    }
    setEditForm({
      username: selectedUser.username,
      displayName: selectedUser.displayName,
      password: '',
      admin: selectedUser.admin,
    });
  }, [selectedUser]);

  const handleCreate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setMessage('');
    setError('');
    setCreatePasswordError('');
    setCreateUsernameError('');
    setCreateDisplayNameError('');

    // 前置校验：检查用户名 / 显示名是否与已有用户冲突（大小写不敏感）
    const lowerUsername = createForm.username.trim().toLowerCase();
    const lowerDisplayName = createForm.displayName.trim().toLowerCase();
    let hasFieldError = false;
    if (users.some((u) => u.username.toLowerCase() === lowerUsername)) {
      setCreateUsernameError('用户名已存在。');
      hasFieldError = true;
    }
    if (users.some((u) => u.displayName.toLowerCase() === lowerDisplayName)) {
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
      body: JSON.stringify({
        username: createForm.username,
        displayName: createForm.displayName,
        password: createForm.password,
        admin: createForm.admin,
        projectId: null,
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
          setError('创建失败，用户名或显示名已存在。');
        }
      } else if (response.status === 400) {
        try {
          const body = await response.json();
          const msg: string = body?.message ?? '';
          if (msg.toLowerCase().includes('password') && msg.toLowerCase().includes('8')) {
            setCreatePasswordError('初始密码不能少于 8 位。');
          } else {
            setError(msg || '创建用户失败，请检查输入后重试。');
          }
        } catch {
          setError('创建用户失败，请检查输入后重试。');
        }
      } else {
        setError('创建用户失败，请稍后重试。');
      }
      return;
    }
    setCreateForm(emptyCreateForm);
    setMessage('用户已创建。');
    await loadUsers();
  };

  const handleUpdate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedUser) {
      return;
    }
    setMessage('');
    setError('');
    setEditUsernameError('');
    setEditDisplayNameError('');

    // 前置校验：检查用户名 / 显示名是否与其他用户冲突（大小写不敏感，排除自身）
    const lowerUsername = editForm.username.trim().toLowerCase();
    const lowerDisplayName = editForm.displayName.trim().toLowerCase();
    let hasFieldError = false;
    if (users.some((u) => u.id !== selectedUser.id && u.username.toLowerCase() === lowerUsername)) {
      setEditUsernameError('用户名已存在。');
      hasFieldError = true;
    }
    if (users.some((u) => u.id !== selectedUser.id && u.displayName.toLowerCase() === lowerDisplayName)) {
      setEditDisplayNameError('显示名已存在。');
      hasFieldError = true;
    }
    if (hasFieldError) {
      return;
    }

    const response = await fetch(`/api/admin/users/${selectedUser.id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        username: editForm.username,
        displayName: editForm.displayName,
        admin: editForm.admin,
        password: editForm.password,
      }),
    });
    if (!response.ok) {
      if (response.status === 409) {
        // 解析服务端返回的具体冲突字段
        try {
          const body = await response.json();
          const msg: string = (body?.message ?? '').toLowerCase();
          if (msg.includes('display name')) {
            setEditDisplayNameError('显示名已存在。');
          } else {
            setEditUsernameError('用户名已存在。');
          }
        } catch {
          setError('更新失败，用户名或显示名已存在。');
        }
      } else if (response.status === 400) {
        setError('更新失败，请确认至少保留一个管理员，且密码不少于 8 位。');
      } else if (response.status === 403) {
        setError('不能修改自己的管理员权限。');
      } else {
        setError('更新用户失败，请稍后重试。');
      }
      return;
    }
    setMessage('用户信息已更新。');
    await loadUsers();
  };

  const handleDelete = async (user: UserSummary) => {
    if (!window.confirm(`确认停用用户“${user.displayName}”吗？`)) {
      return;
    }
    setMessage('');
    setError('');
    const response = await fetch(`/api/admin/users/${user.id}`, {
      method: 'DELETE',
    });
    if (!response.ok) {
      if (response.status === 400) {
        setError('停用失败，系统至少需要保留一个管理员。');
      } else if (response.status === 403) {
        setError('不能停用当前登录账号。');
      } else {
        setError('停用用户失败，请稍后重试。');
      }
      return;
    }
    if (selectedUserId === user.id) {
      setSelectedUserId('');
    }
    setMessage('用户已停用。');
    await loadUsers();
  };

  const handlePermanentDeleteRequest = (user: UserSummary) => {
    setPendingDeleteUser(user);
  };

  const handlePermanentDeleteCancel = () => {
    setPendingDeleteUser(null);
  };

  const handlePermanentDeleteConfirm = async () => {
    if (!pendingDeleteUser) {
      return;
    }
    const user = pendingDeleteUser;
    setPendingDeleteUser(null);
    setMessage('');
    setError('');
    const response = await fetch(`/api/admin/users/${user.id}/permanent`, {
      method: 'DELETE',
    });
    if (!response.ok) {
      if (response.status === 400) {
        setError('删除失败，系统至少需要保留一个管理员。');
      } else if (response.status === 403) {
        setError('不能删除当前登录账号。');
      } else if (response.status === 404) {
        setError('用户不存在，请刷新页面后重试。');
      } else {
        setError('删除用户失败，请稍后重试。');
      }
      return;
    }
    if (selectedUserId === user.id) {
      setSelectedUserId('');
    }
    setMessage(`用户"${user.displayName}"已永久删除。`);
    await loadUsers();
  };

  if (sessionLoading) {
    return null;
  }

  if (!session.authenticated) {
    return <Navigate to="/login" replace />;
  }

  if (!session.admin) {
    return <Navigate to="/errors/404" replace />;
  }

  return (
    <Box sx={{ minHeight: '100vh', backgroundColor: '#f6f8fb' }}>
      <NavigationBar />
      <Container maxWidth="xl" sx={{ py: 5 }}>
        <Stack spacing={3}>
          <Box>
            <Typography variant="h4">用户管理</Typography>
            <Typography color="text.secondary">
              管理员可以查看系统用户、创建用户、修改账号信息，并停用不再使用的账号。
            </Typography>
          </Box>
          {message ? <Alert severity="success">{message}</Alert> : null}
          {error ? <Alert severity="error">{error}</Alert> : null}
          <Card>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6">系统用户</Typography>
                {usersLoading ? <Typography>加载中...</Typography> : null}
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>显示名</TableCell>
                      <TableCell>用户名</TableCell>
                      <TableCell>管理员</TableCell>
                      <TableCell>状态</TableCell>
                      <TableCell align="right">操作</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {users.map((user) => (
                      <TableRow key={user.id} selected={user.id === selectedUserId}>
                        <TableCell>
                          {user.displayName}
                          {isCurrentUser(user) ? '（当前登录）' : ''}
                        </TableCell>
                        <TableCell>{user.username}</TableCell>
                        <TableCell>{user.admin ? '是' : '否'}</TableCell>
                        <TableCell>{user.active ? '启用' : '停用'}</TableCell>
                        <TableCell align="right">
                          <Button size="small" onClick={() => setSelectedUserId(user.id)}>
                            编辑
                          </Button>
                          {user.active && !isCurrentUser(user) ? (
                            <Button color="warning" size="small" onClick={() => handleDelete(user)}>
                              停用
                            </Button>
                          ) : null}
                          {!isCurrentUser(user) ? (
                            <Button color="error" size="small" onClick={() => handlePermanentDeleteRequest(user)}>
                              删除
                            </Button>
                          ) : null}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Stack>
            </CardContent>
          </Card>

          <Stack direction={{ xs: 'column', lg: 'row' }} spacing={3} alignItems="stretch">
            <Card sx={{ flex: 1 }}>
              <CardContent>
                <Stack spacing={2}>
                  <Typography variant="h6">创建用户</Typography>
                  <Box component="form" onSubmit={handleCreate}>
                    <Stack spacing={2}>
                      <TextField
                        label="用户名"
                        value={createForm.username}
                        onChange={(event) => {
                          setCreateUsernameError('');
                          setCreateForm({ ...createForm, username: event.target.value });
                        }}
                        error={!!createUsernameError}
                        helperText={createUsernameError}
                        required
                        fullWidth
                      />
                      <TextField
                        label="显示名"
                        value={createForm.displayName}
                        onChange={(event) => {
                          setCreateDisplayNameError('');
                          setCreateForm({ ...createForm, displayName: event.target.value });
                        }}
                        error={!!createDisplayNameError}
                        helperText={createDisplayNameError}
                        required
                        fullWidth
                      />
                      <TextField
                        label="初始密码"
                        type="password"
                        value={createForm.password}
                        onChange={(event) => {
                          setCreatePasswordError('');
                          setCreateForm({ ...createForm, password: event.target.value });
                        }}
                        error={!!createPasswordError}
                        helperText={createPasswordError || '不少于 8 位。'}
                        required
                        fullWidth
                      />
                      <FormControlLabel
                        control={
                          <Checkbox
                            checked={createForm.admin}
                            onChange={(event) => setCreateForm({ ...createForm, admin: event.target.checked })}
                          />
                        }
                        label="创建为管理员"
                      />
                      <Button variant="contained" type="submit">
                        创建用户
                      </Button>
                    </Stack>
                  </Box>
                </Stack>
              </CardContent>
            </Card>

            <Card sx={{ flex: 1 }}>
              <CardContent>
                <Stack spacing={2}>
                  <Typography variant="h6">编辑用户</Typography>
                  {selectedUser ? (
                    <Box component="form" onSubmit={handleUpdate}>
                      <Stack spacing={2}>
                        <TextField
                          label="用户名"
                          value={editForm.username}
                          onChange={(event) => {
                            setEditUsernameError('');
                            setEditForm({ ...editForm, username: event.target.value });
                          }}
                          error={!!editUsernameError}
                          helperText={editUsernameError}
                          required
                          fullWidth
                        />
                        <TextField
                          label="显示名"
                          value={editForm.displayName}
                          onChange={(event) => {
                            setEditDisplayNameError('');
                            setEditForm({ ...editForm, displayName: event.target.value });
                          }}
                          error={!!editDisplayNameError}
                          helperText={editDisplayNameError}
                          required
                          fullWidth
                        />
                        <TextField
                          label="重置密码"
                          type="password"
                          value={editForm.password}
                          onChange={(event) => setEditForm({ ...editForm, password: event.target.value })}
                          helperText="留空表示不修改密码。"
                          fullWidth
                        />
                        <FormControlLabel
                          control={
                            <Checkbox
                              checked={editForm.admin}
                              onChange={(event) => setEditForm({ ...editForm, admin: event.target.checked })}
                            />
                          }
                          label="管理员"
                        />
                        <Button variant="contained" type="submit">
                          保存修改
                        </Button>
                      </Stack>
                    </Box>
                  ) : (
                    <Typography color="text.secondary">先从上方用户列表中选择一个账号进行编辑。</Typography>
                  )}
                </Stack>
              </CardContent>
            </Card>
          </Stack>
        </Stack>
      </Container>

      {/* 永久删除确认对话框 */}
      <Dialog open={pendingDeleteUser !== null} onClose={handlePermanentDeleteCancel}>
        <DialogTitle>确认永久删除用户</DialogTitle>
        <DialogContent>
          <DialogContentText>
            确认永久删除用户 <strong>{pendingDeleteUser?.displayName}</strong>（{pendingDeleteUser?.username}
            ）吗？
            <br />
            <br />
            此操作<strong>无法撤销</strong>，该用户的所有项目权限也将同时删除。
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handlePermanentDeleteCancel}>取消</Button>
          <Button color="error" variant="contained" onClick={handlePermanentDeleteConfirm}>
            永久删除
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};
