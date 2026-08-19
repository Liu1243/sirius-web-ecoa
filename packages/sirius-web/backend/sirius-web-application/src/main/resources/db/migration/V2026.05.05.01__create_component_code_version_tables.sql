-- 组件代码版本表
CREATE TABLE component_code_version (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    component_id VARCHAR(255) NOT NULL,
    component_name VARCHAR(255) NOT NULL,
    version_name VARCHAR(100) NOT NULL,
    code_content TEXT NOT NULL,
    commit_message VARCHAR(500),
    author VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    model_version_id VARCHAR(255),
    CONSTRAINT uk_component_version UNIQUE (project_id, component_id, version_name),
    CONSTRAINT fk_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
);

-- 组件代码标签表
CREATE TABLE component_code_tag (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    name VARCHAR(50) NOT NULL,
    color VARCHAR(7) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_project_tag UNIQUE (project_id, name),
    CONSTRAINT fk_project_tag FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
);

-- 版本-标签关联表
CREATE TABLE version_tag (
    version_id UUID NOT NULL,
    tag_id UUID NOT NULL,
    PRIMARY KEY (version_id, tag_id),
    CONSTRAINT fk_version FOREIGN KEY (version_id) REFERENCES component_code_version(id) ON DELETE CASCADE,
    CONSTRAINT fk_tag FOREIGN KEY (tag_id) REFERENCES component_code_tag(id) ON DELETE CASCADE
);

-- 索引
CREATE INDEX idx_component_code_version_project ON component_code_version(project_id);
CREATE INDEX idx_component_code_version_component ON component_code_version(project_id, component_id);
CREATE INDEX idx_component_code_tag_project ON component_code_tag(project_id);
