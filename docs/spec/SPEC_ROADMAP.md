# Spec Roadmap

> Auto-updated index. Last updated: 2026-06-22
>
> **AI Agents**: Read this file first to decide which specs to load. Load only what's relevant.

## Module Index

| Module | Spec | Domain Layer | Description | Sub-modules |
|--------|------|--------------|-------------|-------------|
| notification | [notification.spec.md](notification.spec.md) | Core Domain | RESTful notification service: email/sms notifications persisted to MySQL, published to RocketMQ, cached in Redis | — |

## Loading Guide

| Task Type | Load These Specs |
|-----------|-----------------|
| 實作 notification 功能 | `notification.spec.md` + `docs/srs/notification-core-api.srs.md` |
| 第一次理解系統 | 先讀本 SPEC_ROADMAP，再按需載入 |

## Recent Feature Changes

| Date | Module | Feature SRS | One-line Summary |
|------|--------|-------------|-----------------|
| 2026-06-22 | notification | [notification-core-api.srs.md](../srs/notification-core-api.srs.md) | Notification core CRUD API (5 endpoints) with MySQL/RocketMQ/Redis wiring |
