# CLAUDE.md — packages/tests

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Module Purpose

Provides shared test infrastructure and base classes used across all Sirius Web backend modules, covering architecture rules, GraphQL test helpers, and Spring integration test utilities.

## Sub-modules

### Backend (`backend/`)

- `sirius-components-tests` — Base test classes and ArchUnit architecture rule checks; root Java package `org.eclipse.sirius.components.tests` (sub-package: `architecture`)
- `sirius-components-graphql-tests` — Test utilities for asserting GraphQL query/mutation/subscription behavior; root Java package `org.eclipse.sirius.components.graphql.tests`
- `sirius-components-spring-tests` — Spring Boot test context utilities and base integration test classes; root Java package `org.eclipse.sirius.components.spring.tests`

There is no frontend sub-module in this package.

## How to Build

```bash
cd packages/tests/backend
mvn clean compile
```
