# CLAUDE.md — packages/task

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Module Purpose

Provides the Task domain model (projects, tasks, tags) for Sirius Web's sample project-management application, including both the EMF-style domain objects and the EMF.edit item providers used for tree editing.

## Sub-modules

### Backend (`backend/`)

- `sirius-components-task` — Core Task domain model (EClasses: Task, Project, Tag, etc.); root Java package `org.eclipse.sirius.components.task` (sub-packages: `impl`, `util`)
- `sirius-components-task-edit` — EMF.edit item providers for the Task domain; root Java package `org.eclipse.sirius.components.task.provider`

There is no frontend sub-module in this package; UI is provided by `packages/starters` and `sirius-web` application packages.

## How to Build

```bash
cd packages/task/backend
mvn clean compile
```
