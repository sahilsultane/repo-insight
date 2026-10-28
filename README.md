# GitHub Repo Insight

> AI-Powered GitHub Repository Analysis, Codebase Explanation and Database Visualization Platform

Paste any public GitHub repository URL and get:
- Live repository metadata
- Interactive file tree with per-file analysis
- Detected programming languages, frameworks, dependencies
- Architecture diagram (React Flow) built from actual Spring annotations
- ER diagram built from JPA entities and SQL scripts
- AI-generated project summary, architecture, and database explanations
- Ask-AI chat grounded in the actual repository analysis

---

## Prerequisites

| Tool | Minimum Version |
|------|----------------|
| Java | 21 |
| Maven | 3.9+ |
| Node.js | 18+ |
| npm | 9+ |
| PostgreSQL | 14+ |
| Docker + Docker Compose | 24+ (optional) |

---

## Quick Start (Docker — Recommended)

### 1. Copy and fill environment variables

```bash
cp .env.example .env
```

Edit `.env`:

```
GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxx    # Required
AI_PROVIDER=gemini                        # or "openai"
GEMINI_API_KEY=AIza...                    # if using Gemini
OPENAI_API_KEY=sk-...                     # if using OpenAI
DB_USERNAME=repoinsight
DB_PASSWORD=repoinsight123
JWT_SECRET=change_this_to_a_long_random_secret_key_32chars_min
```

### 2. Build and start everything

```bash
docker compose up --build
```

This starts:
- `postgres` on port 5432
- `backend` (Spring Boot) on port 8080
- `frontend` (Nginx) on port 3000

### 3. Open the app

```
http://localhost:3000
```

---

## Manual Setup (Without Docker)

### Step 1 — Start PostgreSQL

Create a database named `repo_insight` with a user `repoinsight`:

```sql
CREATE DATABASE repo_insight;
CREATE USER repoinsight WITH PASSWORD 'repoinsight123';
GRANT ALL PRIVILEGES ON DATABASE repo_insight TO repoinsight;
```

### Step 2 — Configure Backend

Copy `.env.example` to `.env` and fill in your keys (see above).

Then set environment variables (Windows PowerShell):

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/repo_insight"
$env:SPRING_DATASOURCE_USERNAME="repoinsight"
$env:SPRING_DATASOURCE_PASSWORD="repoinsight123"
$env:GITHUB_TOKEN="ghp_your_token_here"
$env:GEMINI_API_KEY="your_gemini_key"
$env:AI_PROVIDER="gemini"
$env:JWT_SECRET="your_long_random_secret_key_here"
```

### Step 3 — Run Backend

```bash
cd backend
mvn spring-boot:run
```

Backend starts on **http://localhost:8080**

### Step 4 — Run Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend starts on **http://localhost:5173**

> The Vite dev server proxies `/api/*` to `http://localhost:8080` automatically.

---

## Getting API Keys

### GitHub Token (Required)
1. Go to https://github.com/settings/tokens
2. Generate a new token (classic)
3. Select scopes: `public_repo`
4. Copy token → set as `GITHUB_TOKEN`

Without this you still get ~60 GitHub API requests/hour (usually enough for one analysis).

### Gemini API Key
1. Go to https://aistudio.google.com/app/apikey
2. Create a new key → set as `GEMINI_API_KEY`

### OpenAI API Key (alternative)
1. Go to https://platform.openai.com/api-keys
2. Create key → set as `OPENAI_API_KEY`
3. Set `AI_PROVIDER=openai`

---

## Usage

1. Open http://localhost:3000 (or 5173 for dev)
2. Paste a public GitHub repository URL:
   ```
   https://github.com/spring-projects/spring-petclinic
   ```
3. Click **Analyze Repository**
4. Watch the progress steps (Fetching → Analyzing → Generating)
5. Explore the results:
   - **Summary** — AI description of the project
   - **Tech Stack** — detected languages and frameworks
   - **Codebase** — interactive file tree, click any file
   - **Database** — detected tables and schema
   - **Key Files** — ranked important files with explanations
   - **Architecture** — interactive React Flow diagram
   - **ER Diagram** — interactive entity-relationship diagram
   - **AI Chat** — ask questions about the repository

---

## Supported Repository Types

### Deep Analysis (v1.0)
- Java + Spring Boot
- Maven (`pom.xml`) or Gradle (`build.gradle`)
- MySQL, PostgreSQL, H2
- JPA / Hibernate entities
- SQL schema files

### Basic Metadata (all repos)
- Language detection
- Stars, forks, description, license
- File tree structure
- Framework hints from common config files

---

## Architecture

```
React Frontend (Vite + Tailwind)
       │
       │ REST / Axios
       ▼
Spring Boot Backend (Java 21)
       │
       ├── GitHub REST API  ──── Metadata, file tree, file content
       │
       ├── Analysis Pipeline
       │       ├── LanguageDetector
       │       ├── BuildSystemAnalyzer  (pom.xml / build.gradle)
       │       ├── ConfigFileAnalyzer   (application.properties / .yml)
       │       ├── JavaAstAnalyzer      (JavaParser AST)
       │       ├── DatabaseAnalyzer     (JPA entities + SQL files)
       │       └── ArchitectureGraphBuilder  (React Flow data)
       │
       ├── AI Layer
       │       ├── GeminiProvider
       │       └── OpenAiProvider
       │               → receives structured context, NOT raw source
       │
       └── PostgreSQL  ──── analyses, repositories, users, tables, columns
```

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register a new user |
| POST | `/api/auth/login` | Login, get JWT token |
| POST | `/api/repositories/analyze` | Start a new analysis |
| GET | `/api/analyses/{id}` | Get full analysis result |
| GET | `/api/analyses/{id}/status` | Poll analysis status |
| POST | `/api/analyses/{id}/ask` | AI chat about the repository |

---

## Limitations

- Only Java/Spring Boot repositories are deeply analyzed (AST-level)
- GitHub API has a 60 req/hour rate limit without a token (5000/hour with token)
- Repositories > 500MB may be partially analyzed
- Private repositories are not supported (require OAuth flow)
- At most 80 Java files are analyzed per repository to avoid rate limiting

---

## Project Structure

```
github-repo-insight/
├── backend/
│   ├── src/main/java/com/githubrepoinsight/
│   │   ├── ai/              Gemini + OpenAI providers
│   │   ├── analyzer/        Core analysis engine
│   │   ├── config/          Spring Security, WebClient config
│   │   ├── controller/      REST controllers
│   │   ├── dto/             Request/response DTOs
│   │   ├── exception/       Custom exceptions + global handler
│   │   ├── github/          GitHub REST API client
│   │   ├── model/           JPA entities
│   │   ├── repository/      Spring Data repositories
│   │   ├── security/        JWT filter + service
│   │   └── service/         Business logic
│   └── pom.xml
├── frontend/
│   └── src/
│       ├── components/      UI components (sidebar, analysis panels)
│       ├── hooks/           useAnalysis polling hook
│       ├── layouts/         MainLayout
│       ├── pages/           Home, Analysis, Login, Register, About
│       ├── services/        Axios API client
│       └── store/           Zustand auth store
├── docker-compose.yml
└── .env.example
```
