import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { loadSession, logout, SessionUser } from '../auth/session';

const anonymousSession: SessionUser = {
  authenticated: false,
  id: null,
  username: null,
  displayName: null,
  admin: false,
};

export const SessionNavigationActions = () => {
  const navigate = useNavigate();
  const [session, setSession] = useState<SessionUser>(anonymousSession);

  useEffect(() => {
    loadSession().then(setSession);
  }, []);

  const handleLogout = async () => {
    await logout();
    setSession(anonymousSession);
    navigate('/login');
  };

  if (!session.authenticated) {
    return (
      <Button color="inherit" size="small" onClick={() => navigate('/login')}>
        登录
      </Button>
    );
  }

  return (
    <Stack direction="row" spacing={1} alignItems="center">
      <Typography variant="body2" color="inherit">
        {session.displayName ?? session.username}
      </Typography>
      {session.admin ? (
        <Button color="inherit" size="small" onClick={() => navigate('/admin/users')}>
          用户管理
        </Button>
      ) : null}
      <Button color="inherit" size="small" onClick={() => navigate('/account')}>
        账号
      </Button>
      <Button color="inherit" size="small" onClick={handleLogout}>
        退出
      </Button>
    </Stack>
  );
};
