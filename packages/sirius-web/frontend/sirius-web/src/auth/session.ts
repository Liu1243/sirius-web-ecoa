export interface SessionUser {
  authenticated: boolean;
  id: string | null;
  username: string | null;
  displayName: string | null;
  admin: boolean;
}

export const loadSession = async (): Promise<SessionUser> => {
  const response = await fetch('/api/auth/session');
  if (!response.ok) {
    return { authenticated: false, id: null, username: null, displayName: null, admin: false };
  }
  return (await response.json()) as SessionUser;
};

export const login = async (username: string, password: string): Promise<boolean> => {
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ username, password }),
  });
  return response.ok;
};

export const logout = async (): Promise<void> => {
  await fetch('/api/auth/logout', {
    method: 'POST',
  });
};

export const changePassword = async (currentPassword: string, newPassword: string): Promise<boolean> => {
  const response = await fetch('/api/auth/change-password', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ currentPassword, newPassword }),
  });
  return response.ok;
};
