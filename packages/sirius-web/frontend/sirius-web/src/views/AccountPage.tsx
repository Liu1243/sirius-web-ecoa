import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Container from '@mui/material/Container';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { FormEvent, useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { changePassword, loadSession, SessionUser } from '../auth/session';
import { NavigationBar } from '@eclipse-sirius/sirius-web-application';

const anonymousSession: SessionUser = {
  authenticated: false,
  id: null,
  username: null,
  displayName: null,
  admin: false,
};

export const AccountPage = () => {
  const [session, setSession] = useState<SessionUser>(anonymousSession);
  const [loading, setLoading] = useState(true);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    loadSession().then((currentSession) => {
      setSession(currentSession);
      setLoading(false);
    });
  }, []);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setMessage('');
    setError('');
    const success = await changePassword(currentPassword, newPassword);
    if (success) {
      setMessage('密码已更新');
      setCurrentPassword('');
      setNewPassword('');
    } else {
      setError('修改密码失败，请确认当前密码是否正确');
    }
  };

  if (loading) {
    return null;
  }

  if (!session.authenticated) {
    return <Navigate to="/login" replace />;
  }

  return (
    <Box sx={{ minHeight: '100vh', backgroundColor: '#f6f8fb' }}>
      <NavigationBar />
      <Container maxWidth="sm" sx={{ py: 6 }}>
        <Card>
          <CardContent sx={{ p: 4 }}>
            <Stack spacing={3}>
              <Box>
                <Typography variant="h4">账号安全</Typography>
                <Typography color="text.secondary">当前账号：{session.displayName ?? session.username}</Typography>
              </Box>
              {message ? <Alert severity="success">{message}</Alert> : null}
              {error ? <Alert severity="error">{error}</Alert> : null}
              <Box component="form" onSubmit={handleSubmit}>
                <Stack spacing={2}>
                  <TextField
                    label="当前密码"
                    type="password"
                    value={currentPassword}
                    onChange={(event) => setCurrentPassword(event.target.value)}
                    required
                    fullWidth
                  />
                  <TextField
                    label="新密码"
                    type="password"
                    value={newPassword}
                    onChange={(event) => setNewPassword(event.target.value)}
                    required
                    fullWidth
                    helperText="建议使用不少于 8 位的新密码。"
                  />
                  <Button variant="contained" type="submit">
                    更新密码
                  </Button>
                </Stack>
              </Box>
            </Stack>
          </CardContent>
        </Card>
      </Container>
    </Box>
  );
};
