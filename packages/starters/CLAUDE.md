# CLAUDE.md — packages/starters

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Module Purpose

Provides sample/starter Spring Boot auto-configuration modules that pre-wire domain models (Flow, Task) into a Sirius Web application, serving as both reference implementations and out-of-the-box demonstrations.

## Sub-modules

### Backend (`backend/`)

- `sirius-components-flow-starter` — Spring Boot starter for the Flow (dataflow/component diagram) sample domain; root Java package `org.eclipse.sirius.components.flow.starter`
- `sirius-components-task-starter` — Spring Boot starter for the Task (project management) sample domain; root Java package `org.eclipse.sirius.components.task.starter`

There is no frontend sub-module in this package; starters are backend auto-configuration only.

## How to Build

**Backend:**
```bash
cd packages/starters/backend
mvn clean compile
```
