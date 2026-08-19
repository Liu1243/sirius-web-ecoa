export interface ComponentCodeVersion {
  id: string;
  componentId: string;
  componentName: string;
  versionName: string;
  commitMessage: string | null;
  author: string;
  createdAt: string;
  modelVersionId: string | null;
  tags: ComponentCodeTag[];
}

export interface ComponentCodeTag {
  id: string;
  name: string;
  color: string;
}

export interface ComponentHistoryEntry {
  componentId: string;
  componentName: string;
  versions: ComponentCodeVersion[];
}

export interface ComponentCodeHistory {
  components: ComponentHistoryEntry[];
}

export interface ComponentPaginationInfo {
  currentPage: number;
  totalPages: number;
  totalVersions: number;
}

export interface CreateVersionInput {
  projectId: string;
  componentId: string;
  componentName: string;
  versionName: string;
  codeContent: string;
  commitMessage?: string;
  modelVersionId?: string;
}

export interface CreateTagInput {
  projectId: string;
  name: string;
  color: string;
}

export interface TagVersionInput {
  versionId: string;
  tagId: string;
}
