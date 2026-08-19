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
import { useNavigate } from 'react-router-dom';
import { loadSession, login } from '../auth/session';

export const LoginPage = () => {
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Run only once on mount — [navigate] dependency causes infinite loop because
  // React Router recreates the navigate reference on every history.replace(),
  // re-triggering this effect and cycling between /login and /projects.
  useEffect(() => {
    loadSession().then((session) => {
      if (session.authenticated) {
        navigate('/projects', { replace: true });
      }
    });
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitting(true);
    setError('');
    const success = await login(username, password);
    setSubmitting(false);
    if (success) {
      navigate('/projects', { replace: true });
    } else {
      setError('用户名或密码不正确');
    }
  };

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        background: 'linear-gradient(135deg, #f4f7fb 0%, #dbe7f5 100%)',
      }}>
      <Container maxWidth="sm">
        <Card elevation={6}>
          <CardContent sx={{ p: 5 }}>
            <Stack spacing={3}>
              <Box>
                <Typography variant="h4" gutterBottom>
                  用户登录
                </Typography>
                <Typography color="text.secondary">使用系统账号登录后，可以按项目粒度访问和管理权限。</Typography>
              </Box>
              {error ? <Alert severity="error">{error}</Alert> : null}
              <Box component="form" onSubmit={handleSubmit}>
                <Stack spacing={2.5}>
                  <TextField
                    label="用户名"
                    value={username}
                    onChange={(event) => setUsername(event.target.value)}
                    required
                    fullWidth
                  />
                  <TextField
                    label="密码"
                    type="password"
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    required
                    fullWidth
                  />
                  <Button type="submit" variant="contained" size="large" disabled={submitting}>
                    {submitting ? '登录中...' : '登录'}
                  </Button>
                </Stack>
              </Box>
              <Alert severity="info">
                首次启动会自动创建管理员账号 `admin / admin123456`，请登录后尽快修改或替换。
              </Alert>
            </Stack>
          </CardContent>
        </Card>
      </Container>
    </Box>
  );
};
