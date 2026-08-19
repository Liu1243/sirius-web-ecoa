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
import { gql, useQuery } from '@apollo/client';
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Collapse,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Divider,
  IconButton,
  Tooltip,
  Typography,
} from '@mui/material';
import CheckIcon from '@mui/icons-material/Check';
import CloseIcon from '@mui/icons-material/Close';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import DeleteIcon from '@mui/icons-material/Delete';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import { ComponentCodeVersion } from './ComponentHistoryView.types';
import { useState, useMemo, useEffect } from 'react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';

// Detect language from file extension
const detectLanguage = (filePath: string | null): string => {
  if (!filePath) return 'text';
  const ext = filePath.split('.').pop()?.toLowerCase();
  switch (ext) {
    case 'c':
    case 'h':
      return 'c';
    case 'cpp':
    case 'cc':
    case 'cxx':
    case 'hpp':
      return 'cpp';
    case 'xml':
      return 'xml';
    case 'json':
      return 'json';
    case 'py':
      return 'python';
    case 'js':
    case 'jsx':
      return 'javascript';
    case 'ts':
    case 'tsx':
      return 'typescript';
    case 'java':
      return 'java';
    case 'go':
      return 'go';
    case 'rs':
      return 'rust';
    case 'md':
      return 'markdown';
    case 'yaml':
    case 'yml':
      return 'yaml';
    default:
      return 'text';
  }
};

const GET_COMPONENT_CODE_VERSION = gql`
  query getComponentCodeVersion($versionId: ID!) {
    componentCodeVersion(input: { versionId: $versionId }) {
      version {
        id
        componentId
        componentName
        versionName
        commitMessage
        author
        createdAt
        modelVersionId
        codeContent
        tags {
          id
          name
          color
        }
      }
    }
  }
`;

interface ComponentVersionDetailDialogProps {
  open: boolean;
  version: ComponentCodeVersion | null;
  onClose: () => void;
  isAdmin: boolean;
  onDeleteVersion: (versionId: string) => Promise<boolean>;
}

export const ComponentVersionDetailDialog = ({
  open,
  version,
  onClose,
  isAdmin,
  onDeleteVersion,
}: ComponentVersionDetailDialogProps) => {
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [expandedFiles, setExpandedFiles] = useState<Record<number, boolean>>({});
  // Track which file indices have just been copied (for brief checkmark feedback)
  const [copiedIndex, setCopiedIndex] = useState<number | null>(null);

  const { data, loading } = useQuery(GET_COMPONENT_CODE_VERSION, {
    variables: { versionId: version?.id || '' },
    skip: !open || !version,
    fetchPolicy: 'cache-and-network',
  });

  const versionDetail = data?.componentCodeVersion?.version;
  const codeContent = versionDetail?.codeContent || '';

  const parsedFiles = useMemo(() => {
    if (!codeContent) return [];

    try {
      const parsed = JSON.parse(codeContent);
      if (typeof parsed === 'object' && parsed !== null) {
        return Object.entries(parsed).map(([filePath, content]) => ({
          filePath,
          content: typeof content === 'string' ? content : JSON.stringify(content, null, 2),
        }));
      }
    } catch (_e) {
      // Try cleaning control characters, zero-width chars, invalid Unicode
      const cleanedContent = codeContent
        .replace(/[\x00-\x08\x0B-\x0C\x0E-\x1F\x7F-\x9F]/g, '')
        .replace(/[​-‍﻿]/g, '')
        .replace(/[￾￿]/g, '');

      try {
        const parsed = JSON.parse(cleanedContent);
        if (typeof parsed === 'object' && parsed !== null) {
          return Object.entries(parsed).map(([filePath, content]) => ({
            filePath,
            content: typeof content === 'string' ? content : JSON.stringify(content, null, 2),
          }));
        }
      } catch (_e2) {
        // Last resort: find matching braces
        try {
          const firstBrace = codeContent.indexOf('{');
          const lastBrace = codeContent.lastIndexOf('}');
          if (firstBrace !== -1 && lastBrace !== -1 && lastBrace > firstBrace) {
            const jsonContent = codeContent.substring(firstBrace, lastBrace + 1);
            const aggressive = jsonContent.replace(/[\x00-\x1F\x7F-\x9F​-‍﻿]/g, '');
            const parsed = JSON.parse(aggressive);
            if (typeof parsed === 'object' && parsed !== null) {
              return Object.entries(parsed).map(([filePath, content]) => ({
                filePath,
                content: typeof content === 'string' ? content : JSON.stringify(content, null, 2),
              }));
            }
          }
        } catch (_e3) {
          // All parsing attempts failed
        }
      }
    }

    return [{ filePath: null, content: codeContent }];
  }, [codeContent]);

  // Reset expanded state when dialog closes
  useEffect(() => {
    if (!open) {
      setExpandedFiles({});
      setCopiedIndex(null);
    }
  }, [open]);

  // Set default expanded state once files are loaded (first 3 expanded, rest collapsed)
  useEffect(() => {
    if (parsedFiles.length > 0) {
      setExpandedFiles((prev) => {
        // Only apply defaults on fresh load (empty state after dialog open)
        if (Object.keys(prev).length === 0) {
          const defaults: Record<number, boolean> = {};
          parsedFiles.forEach((_, i) => {
            defaults[i] = i < 3;
          });
          return defaults;
        }
        return prev;
      });
    }
  }, [parsedFiles.length]); // eslint-disable-line react-hooks/exhaustive-deps

  const toggleFileExpansion = (index: number) => {
    setExpandedFiles((prev) => ({ ...prev, [index]: !prev[index] }));
  };

  const expandAllFiles = () => {
    const all: Record<number, boolean> = {};
    parsedFiles.forEach((_, i) => {
      all[i] = true;
    });
    setExpandedFiles(all);
  };

  const collapseAllFiles = () => setExpandedFiles({});

  const handleCopyFile = (content: string, index: number) => {
    navigator.clipboard
      .writeText(content)
      .then(() => {
        setCopiedIndex(index);
        setTimeout(() => setCopiedIndex(null), 1500);
      })
      .catch(() => {});
  };

  const handleDeleteClick = () => setDeleteDialogOpen(true);

  const handleDeleteConfirm = async () => {
    if (!version) return;
    setDeleting(true);
    const success = await onDeleteVersion(version.id);
    setDeleting(false);
    if (success) {
      setDeleteDialogOpen(false);
      onClose();
    }
  };

  return (
    <>
      <Dialog open={open && !deleteDialogOpen} onClose={onClose} maxWidth="lg" fullWidth closeAfterTransition>
        <DialogTitle>
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <Typography variant="h6">
              {version?.componentName}
              <Typography component="span" variant="h6" color="text.secondary" sx={{ ml: 1 }}>
                {version?.versionName}
              </Typography>
            </Typography>
            <Box sx={{ display: 'flex', gap: 1 }}>
              {isAdmin && (
                <IconButton onClick={handleDeleteClick} size="small" color="error" title="删除版本">
                  <DeleteIcon fontSize="small" />
                </IconButton>
              )}
              <IconButton onClick={onClose} size="small">
                <CloseIcon />
              </IconButton>
            </Box>
          </Box>
        </DialogTitle>
        <DialogContent>
          {loading && !versionDetail ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', py: 6 }}>
              <CircularProgress size={32} />
            </Box>
          ) : versionDetail ? (
            <Box>
              {/* Metadata card */}
              <Box
                sx={{
                  bgcolor: 'action.hover',
                  borderRadius: 1,
                  border: '1px solid',
                  borderColor: 'divider',
                  px: 2,
                  py: 1.5,
                  mb: 2,
                }}>
                <Typography variant="body2" color="text.secondary" sx={{ mb: versionDetail.tags?.length ? 1 : 0 }}>
                  <strong>作者：</strong>
                  {versionDetail.author}
                  <Box component="span" sx={{ mx: 1, opacity: 0.4 }}>
                    ·
                  </Box>
                  <strong>创建：</strong>
                  {new Date(versionDetail.createdAt).toLocaleString()}
                  {versionDetail.modelVersionId && (
                    <>
                      <Box component="span" sx={{ mx: 1, opacity: 0.4 }}>
                        ·
                      </Box>
                      <strong>模型版本：</strong>
                      {versionDetail.modelVersionId}
                    </>
                  )}
                </Typography>
                {versionDetail.tags && versionDetail.tags.length > 0 && (
                  <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
                    {versionDetail.tags.map((tag: { id: string; name: string; color: string }) => (
                      <Chip
                        key={tag.id}
                        label={tag.name}
                        size="small"
                        sx={{
                          backgroundColor: tag.color,
                          color: '#fff',
                          height: 20,
                          fontSize: '0.7rem',
                        }}
                      />
                    ))}
                  </Box>
                )}
              </Box>

              {versionDetail.commitMessage && (
                <>
                  <Typography variant="subtitle2" gutterBottom>
                    提交信息
                  </Typography>
                  <Box sx={{ bgcolor: 'grey.50', p: 1.5, borderRadius: 1, mb: 2 }}>
                    <Typography variant="body2">{versionDetail.commitMessage}</Typography>
                  </Box>
                </>
              )}

              <Divider sx={{ my: 2 }} />

              {/* Code Content */}
              <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
                <Typography variant="subtitle2">
                  代码内容
                  {parsedFiles.length > 0 && (
                    <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 1 }}>
                      ({parsedFiles.length} 个文件)
                    </Typography>
                  )}
                </Typography>
                {parsedFiles.length > 1 && (
                  <Box sx={{ display: 'flex', gap: 1 }}>
                    <Button size="small" variant="outlined" onClick={expandAllFiles}>
                      展开全部
                    </Button>
                    <Button size="small" variant="outlined" onClick={collapseAllFiles}>
                      折叠全部
                    </Button>
                  </Box>
                )}
              </Box>

              {parsedFiles.length === 0 ? (
                <Box
                  sx={{
                    bgcolor: 'grey.900',
                    color: 'grey.500',
                    p: 2,
                    borderRadius: 1,
                    fontFamily: 'monospace',
                    fontSize: '0.875rem',
                  }}>
                  // 无代码内容
                </Box>
              ) : (
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                  {parsedFiles.map((file, index) => {
                    const language = detectLanguage(file.filePath);
                    const isExpanded = expandedFiles[index] ?? false;
                    const lineCount = file.content.split('\n').length;
                    const isCopied = copiedIndex === index;

                    return (
                      <Box key={index} sx={{ border: '1px solid', borderColor: 'grey.700', borderRadius: 1 }}>
                        {/* File header */}
                        {file.filePath && (
                          <Box
                            sx={{
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'space-between',
                              bgcolor: 'grey.800',
                              px: 1.5,
                              py: 0.5,
                              borderRadius: '4px 4px 0 0',
                            }}>
                            {/* Left: path + language + line count */}
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 0 }}>
                              <Typography
                                variant="caption"
                                sx={{
                                  color: 'primary.light',
                                  fontFamily: 'monospace',
                                  overflow: 'hidden',
                                  textOverflow: 'ellipsis',
                                  whiteSpace: 'nowrap',
                                }}>
                                {file.filePath}
                              </Typography>
                              {language !== 'text' && (
                                <Typography
                                  variant="caption"
                                  sx={{
                                    color: 'grey.400',
                                    fontSize: '0.65rem',
                                    flexShrink: 0,
                                    bgcolor: 'grey.700',
                                    px: 0.5,
                                    py: 0.1,
                                    borderRadius: 0.5,
                                  }}>
                                  {language}
                                </Typography>
                              )}
                              <Typography
                                variant="caption"
                                sx={{ color: 'grey.500', fontSize: '0.65rem', flexShrink: 0 }}>
                                {lineCount} 行
                              </Typography>
                            </Box>

                            {/* Right: copy + expand/collapse */}
                            <Box sx={{ display: 'flex', alignItems: 'center', flexShrink: 0 }}>
                              <Tooltip title={isCopied ? '已复制' : '复制代码'} placement="top">
                                <IconButton
                                  size="small"
                                  onClick={() => handleCopyFile(file.content, index)}
                                  sx={{ color: isCopied ? 'success.light' : 'grey.400', p: 0.25 }}>
                                  {isCopied ? (
                                    <CheckIcon sx={{ fontSize: 14 }} />
                                  ) : (
                                    <ContentCopyIcon sx={{ fontSize: 14 }} />
                                  )}
                                </IconButton>
                              </Tooltip>
                              <IconButton
                                size="small"
                                onClick={() => toggleFileExpansion(index)}
                                sx={{ color: 'grey.300', p: 0.25 }}>
                                {isExpanded ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
                              </IconButton>
                            </Box>
                          </Box>
                        )}

                        <Collapse in={isExpanded}>
                          <SyntaxHighlighter
                            language={language}
                            style={vscDarkPlus}
                            customStyle={{
                              margin: 0,
                              borderRadius: file.filePath ? '0 0 4px 4px' : '4px',
                              fontSize: '0.8rem',
                              maxHeight: '500px',
                              overflowY: 'auto',
                            }}
                            showLineNumbers
                            wrapLines={true}>
                            {file.content}
                          </SyntaxHighlighter>
                        </Collapse>
                      </Box>
                    );
                  })}
                </Box>
              )}
            </Box>
          ) : null}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose}>关闭</Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog
        open={deleteDialogOpen}
        onClose={() => setDeleteDialogOpen(false)}
        maxWidth="sm"
        fullWidth
        closeAfterTransition>
        <DialogTitle>确认删除版本</DialogTitle>
        <DialogContent>
          <DialogContentText>
            您确定要删除版本 <strong>{version?.versionName}</strong> 吗？此操作不可撤销。
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialogOpen(false)} disabled={deleting}>
            取消
          </Button>
          <Button onClick={handleDeleteConfirm} color="error" variant="contained" disabled={deleting}>
            {deleting ? '删除中...' : '删除'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};
