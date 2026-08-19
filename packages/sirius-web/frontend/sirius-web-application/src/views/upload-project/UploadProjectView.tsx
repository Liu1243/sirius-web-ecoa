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
import { gql } from '@apollo/client';
import { ServerContext, ServerContextValue, useMultiToast } from '@eclipse-sirius/sirius-components-core';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Container from '@mui/material/Container';
import LinearProgress from '@mui/material/LinearProgress';
import Paper from '@mui/material/Paper';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useContext, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Navigate } from 'react-router-dom';
import { makeStyles } from 'tss-react/mui';
import { FileUpload } from '../../core/file-upload/FileUpload';
import { sendFile } from '../../core/sendFile';
import { NavigationBar } from '../../navigationBar/NavigationBar';
import { useCurrentViewer } from '../../viewer/useCurrentViewer';
import {
  GQLUploadProjectPayload,
  GQLUploadProjectSuccessPayload,
  UploadProjectViewState,
} from './UploadProjectView.types';

const isAuthenticated = (viewer: any): boolean => {
  return viewer && viewer.id && viewer.id !== '';
};

const uploadProjectMutation = gql`
  mutation uploadProject($input: UploadProjectInput!) {
    uploadProject(input: $input) {
      __typename
      ... on UploadProjectSuccessPayload {
        project {
          id
        }
      }
      ... on ErrorPayload {
        message
      }
    }
  }
`.loc.source.body;

const useUploadProjectViewStyles = makeStyles()((theme) => ({
  uploadProjectViewContainer: {
    display: 'flex',
    flexDirection: 'column',
    paddingTop: theme.spacing(8),
  },
  uploadProjectView: {
    display: 'grid',
    gridTemplateColumns: '1fr',
    gridTemplateRows: 'min-content 1fr min-content',
    minHeight: '100vh',
  },
  main: {
    paddingTop: theme.spacing(3),
    paddingBottom: theme.spacing(3),
  },
  titleContainer: {
    display: 'flex',
    flexDirection: 'column',
    paddingBottom: theme.spacing(2),
  },
  buttons: {
    display: 'flex',
    flexDirection: 'row',
    justifyContent: 'start',
  },
  form: {
    display: 'flex',
    flexDirection: 'column',
    paddingTop: theme.spacing(1),
    paddingLeft: theme.spacing(2),
    paddingRight: theme.spacing(2),
    '& > *': {
      marginBottom: theme.spacing(2),
    },
  },
  progressContainer: {
    display: 'flex',
    flexDirection: 'column',
    gap: theme.spacing(0.5),
  },
  progressLabel: {
    display: 'flex',
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
}));

const isUploadProjectSuccessPayload = (payload: GQLUploadProjectPayload): payload is GQLUploadProjectSuccessPayload =>
  payload && payload.__typename === 'UploadProjectSuccessPayload';

export const UploadProjectView = () => {
  const { classes } = useUploadProjectViewStyles();
  const { addErrorMessage } = useMultiToast();
  const { t } = useTranslation('sirius-web-application', { keyPrefix: 'uploadProjectView' });
  const [state, setState] = useState<UploadProjectViewState>({
    file: null,
    loading: false,
    newProjectId: null,
    uploadProgress: null,
    nameConflict: false,
    renameAs: '',
  });

  const {
    viewer,
    viewer: {
      capabilities: {
        projects: { canUpload },
      },
    },
  } = useCurrentViewer();

  const { httpOrigin } = useContext<ServerContextValue>(ServerContext);

  const onUpload = (event: React.FormEvent<HTMLFormElement>) => {
    onUploadProject(event);
  };

  const onUploadProject = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await doUpload(state.renameAs || undefined);
  };

  const onRetryWithRename = async () => {
    await doUpload(state.renameAs);
  };

  const doUpload = async (renameAs?: string) => {
    setState((prevState) => ({ ...prevState, loading: true, uploadProgress: 0, nameConflict: false }));

    const variables: Record<string, any> = {
      input: {
        id: crypto.randomUUID(),
        file: null,
        ...(renameAs ? { renameAs } : {}),
      },
    };

    const handleProgress = (percent: number) => {
      setState((prevState) => ({ ...prevState, uploadProgress: percent }));
    };

    try {
      const response = await sendFile(httpOrigin, uploadProjectMutation, variables, state.file, handleProgress);
      const { data, error } = response as any;
      if (error) {
        setState((prevState) => ({ ...prevState, loading: false, uploadProgress: null }));
        addErrorMessage('An unexpected error has occurred, the file uploaded may be too large');
      } else if (data) {
        const { uploadProject } = data;
        if (isUploadProjectSuccessPayload(uploadProject)) {
          setState((prevState) => ({
            ...prevState,
            newProjectId: uploadProject.project.id,
            loading: false,
            uploadProgress: null,
          }));
        } else if (uploadProject.message?.includes('已存在')) {
          // Name conflict — show inline rename prompt instead of a toast
          setState((prevState) => ({
            ...prevState,
            loading: false,
            uploadProgress: null,
            nameConflict: true,
          }));
        } else {
          setState((prevState) => ({ ...prevState, loading: false, uploadProgress: null }));
          addErrorMessage(uploadProject.message);
        }
      } else {
        setState((prevState) => ({ ...prevState, loading: false, uploadProgress: null }));
        addErrorMessage('An unexpected error has occurred, the file uploaded may be too large');
      }
    } catch (exception) {
      setState((prevState) => ({ ...prevState, loading: false, uploadProgress: null }));
      addErrorMessage('An unexpected error has occurred, the file uploaded may be too large');
    }
  };

  const onFileSelected = (file: File) => {
    setState((prevState) => ({
      ...prevState,
      file: file,
    }));
  };

  if (!isAuthenticated(viewer)) {
    return <Navigate to={'/login'} />;
  }
  if (!canUpload) {
    return <Navigate to={'/errors/404'} />;
  }

  if (state.newProjectId) {
    return <Navigate to={`/projects/${state.newProjectId}/edit`} />;
  }

  // Determine what the progress bar should show:
  //  - uploadProgress 0-99 : file is being transferred to the server (determinate)
  //  - uploadProgress 100  : file sent; server is processing the ZIP (indeterminate)
  //  - uploadProgress null : idle, show nothing
  const isUploading = state.loading && state.uploadProgress !== null;
  const isProcessing = isUploading && state.uploadProgress === 100;
  const progressLabel = isProcessing
    ? t('processingLabel', { defaultValue: '服务器处理中...' })
    : isUploading
    ? t('uploadingLabel', {
        defaultValue: '正在上传 {{percent}}%',
        percent: state.uploadProgress,
      })
    : '';

  return (
    <div className={classes.uploadProjectView}>
      <NavigationBar />
      <main className={classes.main}>
        <Container maxWidth="sm">
          <div className={classes.uploadProjectViewContainer}>
            <div className={classes.titleContainer}>
              <Typography variant="h2" align="center" gutterBottom>
                {t('title')}
              </Typography>
              <Typography variant="h4" align="center" gutterBottom>
                {t('description')}
              </Typography>
            </div>
            <Paper>
              <form onSubmit={onUpload} encType="multipart/form-data" className={classes.form}>
                <FileUpload onFileSelected={onFileSelected} data-testid="file" />

                {/* Name-conflict rename prompt */}
                {state.nameConflict && (
                  <>
                    <Alert severity="warning">
                      {t('nameConflictMessage', {
                        defaultValue: '已存在同名项目，请输入新名称后重新上传',
                      })}
                    </Alert>
                    <TextField
                      label={t('newProjectNameLabel', { defaultValue: '新项目名称' })}
                      value={state.renameAs}
                      onChange={(e) => setState((prev) => ({ ...prev, renameAs: e.target.value }))}
                      required
                      fullWidth
                      autoFocus
                      size="small"
                      data-testid="rename-project-input"
                    />
                    <div className={classes.buttons}>
                      <Button
                        variant="contained"
                        color="primary"
                        disabled={!state.renameAs.trim() || state.loading}
                        loading={state.loading}
                        onClick={onRetryWithRename}
                        data-testid="rename-and-upload-project">
                        {t('retryWithNewName', { defaultValue: '确认重新上传' })}
                      </Button>
                    </div>
                  </>
                )}

                {/* Upload progress bar — only visible while an upload is in progress */}
                {isUploading && (
                  <div className={classes.progressContainer}>
                    <Box className={classes.progressLabel}>
                      <Typography variant="body2" color="text.secondary">
                        {progressLabel}
                      </Typography>
                      {!isProcessing && (
                        <Typography variant="body2" color="text.secondary">
                          {state.uploadProgress}%
                        </Typography>
                      )}
                    </Box>
                    {isProcessing ? (
                      <LinearProgress />
                    ) : (
                      <LinearProgress variant="determinate" value={state.uploadProgress ?? 0} />
                    )}
                  </div>
                )}

                {!state.nameConflict && (
                  <div className={classes.buttons}>
                    <Button
                      variant="contained"
                      type="submit"
                      color="primary"
                      disabled={!state.file || state.loading}
                      loading={state.loading}
                      data-testid="upload-project">
                      {t('submit')}
                    </Button>
                  </div>
                )}
              </form>
            </Paper>
          </div>
        </Container>
      </main>
    </div>
  );
};
