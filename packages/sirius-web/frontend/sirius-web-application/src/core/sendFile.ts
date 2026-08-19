/*******************************************************************************
 * Copyright (c) 2019, 2025 Obeo.
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

/**
 * Maximum time (ms) to wait for the server to finish processing an upload.
 * The backend imports all project content synchronously on the HTTP thread, so
 * large projects can take a while.  10 minutes is generous but prevents the UI
 * from being permanently stuck when the backend hangs or takes unexpectedly long.
 */
export const UPLOAD_TIMEOUT_MS = 10 * 60 * 1000; // 10 minutes

/**
 * Low-level XHR-based file sender.
 *
 * Sends a pre-built {@link FormData} to any URL via XMLHttpRequest so that:
 * - Upload progress can be reported through {@param onUploadProgress}.
 * - A hard timeout prevents the promise from hanging forever if the server
 *   is slow or unresponsive.
 * - The abort and error cases are always handled, resolving the promise either
 *   way so callers never leak state.
 *
 * @param url               Full URL to POST to.
 * @param formData          The multipart body to send.
 * @param onUploadProgress  Optional callback called with a 0–100 percentage
 *                          as bytes are transferred to the server.  Not called
 *                          during server-side processing.
 * @returns Parsed JSON response body.
 */
export const sendFormData = (
  url: string,
  formData: FormData,
  onUploadProgress?: (percent: number) => void
): Promise<any> => {
  const csrfToken = getCookie('XSRF-TOKEN');

  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();

    // Prevent the promise from hanging forever if the server never responds.
    xhr.timeout = UPLOAD_TIMEOUT_MS;

    // Track bytes sent to the server (phase 1: file transfer).
    xhr.upload.onprogress = (event: ProgressEvent) => {
      if (event.lengthComputable && onUploadProgress) {
        const percent = Math.round((event.loaded / event.total) * 100);
        onUploadProgress(percent);
      }
    };

    xhr.onload = () => {
      try {
        const data = JSON.parse(xhr.responseText);
        resolve(data);
      } catch {
        reject(new Error(`Server returned non-JSON response (status ${xhr.status})`));
      }
    };

    xhr.onerror = () => {
      reject(new Error('Network error during upload'));
    };

    xhr.ontimeout = () => {
      reject(new Error('Upload request timed out'));
    };

    xhr.onabort = () => {
      reject(new Error('Upload request was aborted'));
    };

    xhr.open('POST', url);
    if (csrfToken) {
      xhr.setRequestHeader('X-XSRF-TOKEN', csrfToken);
    }
    // Send session cookie so the server recognises the authenticated user.
    xhr.withCredentials = true;
    xhr.send(formData);
  });
};

/**
 * Sends a multipart GraphQL mutation containing a file upload.
 *
 * Wraps {@link sendFormData} with the standard GraphQL multipart request format
 * (https://github.com/jaydenseric/graphql-multipart-request-spec).
 *
 * @param onUploadProgress - called with a percentage value (0–100) as the file
 *   is transferred to the server.  Not called during server-side processing.
 */
export const sendFile = (
  httpOrigin: string,
  query: string,
  variables: any,
  file: File,
  onUploadProgress?: (percent: number) => void
): Promise<any> => {
  const operations = { query, variables };

  const formData = new FormData();
  formData.append('operations', JSON.stringify(operations));
  formData.append('map', JSON.stringify({ '0': 'variables.file' }));
  formData.append('0', file);

  return sendFormData(`${httpOrigin}/api/graphql/upload`, formData, onUploadProgress);
};

const getCookie = (name: string): string | null => {
  if (!document.cookie) return null;
  for (const cookie of document.cookie.split(';')) {
    const trimmed = cookie.trim();
    if (trimmed.startsWith(name + '=')) {
      return decodeURIComponent(trimmed.substring(name.length + 1));
    }
  }
  return null;
};
