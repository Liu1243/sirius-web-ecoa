/*******************************************************************************
 * Copyright (c) 2025 Obeo.
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
import { useState, useEffect } from 'react';

export interface SessionUser {
  authenticated: boolean;
  id: string | null;
  username: string | null;
  displayName: string | null;
  admin: boolean;
}

const anonymousSession: SessionUser = {
  authenticated: false,
  id: null,
  username: null,
  displayName: null,
  admin: false,
};

export const useSession = (): { session: SessionUser; loading: boolean } => {
  const [session, setSession] = useState<SessionUser>(anonymousSession);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadSession = async () => {
      try {
        const response = await fetch('/api/auth/session');
        if (response.ok) {
          const data = await response.json();
          setSession(data);
        } else {
          setSession(anonymousSession);
        }
      } catch (error) {
        console.error('Failed to load session:', error);
        setSession(anonymousSession);
      } finally {
        setLoading(false);
      }
    };

    loadSession();
  }, []);

  return { session, loading };
};
