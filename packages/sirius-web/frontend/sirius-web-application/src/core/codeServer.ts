/*******************************************************************************
 * Copyright (c) 2026 Dassault Aviation.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

const CODE_SERVER_PORT = '8443';

export const normalizeCodeServerFolder = (path: string): string => {
  if (/\.[^/\\]+$/.test(path)) {
    return path.replace(/[/\\][^/\\]+$/, '');
  }
  return path;
};

export const getCodeServerUrl = (path: string): string => {
  const folder = normalizeCodeServerFolder(path);
  const host = `${window.location.hostname}:${CODE_SERVER_PORT}`;
  return `http://${host}/?folder=${encodeURIComponent(folder)}`;
};
