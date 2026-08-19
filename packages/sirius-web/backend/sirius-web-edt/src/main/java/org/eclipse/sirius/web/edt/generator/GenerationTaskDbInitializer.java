/*******************************************************************************
 * Copyright (c) 2025 Dassault Aviation.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Dassault Aviation - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.web.edt.generator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Initializes the edt_generation_tasks table on application startup if it doesn't exist.
 * Uses a simple CREATE TABLE IF NOT EXISTS to remain idempotent across restarts.
 */
@Component
public class GenerationTaskDbInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerationTaskDbInitializer.class);

    private final JdbcClient jdbcClient;

    public GenerationTaskDbInitializer(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @PostConstruct
    public void initSchema() {
        LOGGER.info("Initializing edt_generation_tasks table...");
        this.jdbcClient.sql("""
                CREATE TABLE IF NOT EXISTS edt_generation_tasks (
                    task_id         VARCHAR(36)  PRIMARY KEY,
                    project_id      VARCHAR(36)  NOT NULL,
                    workspace_id    VARCHAR(36),
                    workflow_mode   VARCHAR(32)  NOT NULL DEFAULT 'DIRECT_DEV',
                    base_project_file TEXT,
                    active_project_file TEXT,
                    harness_project_file TEXT,
                    status          VARCHAR(32)  NOT NULL DEFAULT 'INIT',
                    sub_status      VARCHAR(64)  NOT NULL DEFAULT 'NONE',
                    progress        INTEGER      NOT NULL DEFAULT 0,
                    output_path     TEXT,
                    error_summary   TEXT,
                    logs            TEXT,
                    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """).update();
        this.jdbcClient.sql("""
                CREATE INDEX IF NOT EXISTS idx_egt_project_id ON edt_generation_tasks(project_id)
                """).update();
        // Add workspace_id column if upgrading from an older schema (idempotent)
        try {
            this.jdbcClient.sql("""
                    ALTER TABLE edt_generation_tasks ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36)
                    """).update();
        } catch (Exception e) {
            LOGGER.debug("workspace_id column may already exist or dialect unsupported: {}", e.getMessage());
        }
        try {
            this.jdbcClient.sql("""
                    ALTER TABLE edt_generation_tasks ADD COLUMN IF NOT EXISTS workflow_mode VARCHAR(32) DEFAULT 'DIRECT_DEV'
                    """).update();
            this.jdbcClient.sql("""
                    ALTER TABLE edt_generation_tasks ADD COLUMN IF NOT EXISTS base_project_file TEXT
                    """).update();
            this.jdbcClient.sql("""
                    ALTER TABLE edt_generation_tasks ADD COLUMN IF NOT EXISTS active_project_file TEXT
                    """).update();
            this.jdbcClient.sql("""
                    ALTER TABLE edt_generation_tasks ADD COLUMN IF NOT EXISTS harness_project_file TEXT
                    """).update();
        } catch (Exception e) {
            LOGGER.debug("workflow context columns may already exist or dialect unsupported: {}", e.getMessage());
        }
        // Add user_id column for per-user generation history isolation
        try {
            this.jdbcClient.sql("""
                    ALTER TABLE edt_generation_tasks ADD COLUMN IF NOT EXISTS user_id VARCHAR(36)
                    """).update();
            this.jdbcClient.sql("""
                    CREATE INDEX IF NOT EXISTS idx_egt_user_id ON edt_generation_tasks(user_id)
                    """).update();
        } catch (Exception e) {
            LOGGER.debug("user_id column may already exist or dialect unsupported: {}", e.getMessage());
        }
        // Add source state columns for P0 workflow optimization
        try {
            this.jdbcClient.sql("""
                    ALTER TABLE edt_generation_tasks ADD COLUMN IF NOT EXISTS source_state VARCHAR(32)
                    """).update();
            this.jdbcClient.sql("""
                    ALTER TABLE edt_generation_tasks ADD COLUMN IF NOT EXISTS code_workspace_path TEXT
                    """).update();
            this.jdbcClient.sql("""
                    ALTER TABLE edt_generation_tasks ADD COLUMN IF NOT EXISTS source_readiness_evidence VARCHAR(64)
                    """).update();
        } catch (Exception e) {
            LOGGER.debug("source state columns may already exist or dialect unsupported: {}", e.getMessage());
        }
        // Add CSMGVT result columns for INTEGRATION mode
        try {
            this.jdbcClient.sql("""
                    ALTER TABLE edt_generation_tasks ADD COLUMN IF NOT EXISTS csmgvt_result TEXT
                    """).update();
            this.jdbcClient.sql("""
                    ALTER TABLE edt_generation_tasks ADD COLUMN IF NOT EXISTS csmgvt_product_check TEXT
                    """).update();
            this.jdbcClient.sql("""
                    ALTER TABLE edt_generation_tasks ADD COLUMN IF NOT EXISTS csmgvt_compile_errors TEXT
                    """).update();
            this.jdbcClient.sql("""
                    ALTER TABLE edt_generation_tasks ADD COLUMN IF NOT EXISTS csmgvt_csm_result TEXT
                    """).update();
        } catch (Exception e) {
            LOGGER.debug("CSMGVT result columns may already exist or dialect unsupported: {}", e.getMessage());
        }
        // Add code backflow columns
        try {
            this.jdbcClient.sql("""
                    ALTER TABLE edt_generation_tasks ADD COLUMN IF NOT EXISTS test_workspace_path TEXT
                    """).update();
            this.jdbcClient.sql("""
                    ALTER TABLE edt_generation_tasks ADD COLUMN IF NOT EXISTS patch_artifact_path TEXT
                    """).update();
            this.jdbcClient.sql("""
                    ALTER TABLE edt_generation_tasks ADD COLUMN IF NOT EXISTS source_version_id VARCHAR(36)
                    """).update();
            this.jdbcClient.sql("""
                    ALTER TABLE edt_generation_tasks ADD COLUMN IF NOT EXISTS source_revision VARCHAR(64)
                    """).update();
        } catch (Exception e) {
            LOGGER.debug("code backflow columns may already exist or dialect unsupported: {}", e.getMessage());
        }
        // Add component version selections column
        try {
            this.jdbcClient.sql("""
                    ALTER TABLE edt_generation_tasks ADD COLUMN IF NOT EXISTS component_version_selections TEXT
                    """).update();
        } catch (Exception e) {
            LOGGER.debug("component_version_selections column may already exist or dialect unsupported: {}", e.getMessage());
        }
        LOGGER.info("edt_generation_tasks table ready.");
    }
}
