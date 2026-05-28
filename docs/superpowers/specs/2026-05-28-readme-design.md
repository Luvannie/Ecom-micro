# README Restructure Design

## Context
- Root README.md was deleted (per git status)
- BE/README.md currently exists with comprehensive backend content
- FE has no README.md
- Goal: Create a comprehensive, showcase-ready documentation system

## Design

### Structure
Three README files:
1. `/README.md` - Root executive summary (new)
2. `/BE/README.md` - Detailed backend guide (enhance existing)
3. `/FE/README.md` - Frontend guide (new)

### 1. Root README (`/README.md`)

**Purpose:** Executive summary for quick readers (recruiters, managers, collaborators)

**Content (~100 lines):**
- Project title + tech stack badges (icons)
- One-paragraph project overview
- Text-based architecture diagram showing microservice topology
- Quick links to BE/README.md and FE/README.md
- Key stats (11 microservices, 9 backend services, Spring Boot, React, Kafka, etc.)
- Quick start commands (docker compose + mvn commands)

### 2. BE README (`/BE/README.md`)

**Purpose:** Detailed backend documentation for developers working on backend

**Content (~300+ lines):**
- Keep existing content intact
- Add service table with ports
- Add more comprehensive API examples across all services
- Add cross-cutting patterns section (Saga, Outbox, Retry, Circuit Breaker, etc.)
- Add infrastructure details section
- Add database per service explanation
- Add business flow diagrams

### 3. FE README (`/FE/README.md`)

**Purpose:** Frontend developer onboarding guide

**Content (~150-200 lines):**
- Project overview (Vite + React + TypeScript)
- Tech stack details
- Project structure (directories, key files)
- Setup and run commands
- Available pages and components overview
- API integration layer (api.ts)
- State management approach (context)
- Key architecture decisions

## Scope
- All three README files should be cohesive (consistent styling, cross-references)
- No duplication: different content in each file, cross-link rather than repeat
- Both showcase-ready (portfolio) and developer-friendly (onboarding)

## Status
- Approved
