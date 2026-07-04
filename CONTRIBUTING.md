# Documentation & Contribution Conventions

This file defines the standards used across all Code Solutions open source projects.

## File naming standard

| File | Language | Notes |
|------|----------|-------|
| `README.md` | English (default) | GitHub's default; visible when repo is opened |
| `README.pt-BR.md` | Portuguese (Brazil) | Mirror of the English README |
| `CONTRIBUTING.md` | English | Contribution guide and project conventions |
| `CHANGELOG.md` | English | Notable changes per release |
| `LICENSE` | English (legal) | Project license |

> If a feature/topic is only documented in one language, add the other as a separate file following the same convention above.

## Code comments

- **All code comments in English** (Javadoc, KDoc, Scaladoc, Python docstrings, `#`, `//`, `/* */`)
- Comments explain **why**, not what (the code shows what)
- Public APIs always have a docstring / KDoc / Javadoc
- Example:

  ```java
  // English only
  // Persists the document asynchronously to avoid blocking the request thread.
  public CompletableFuture<Void> save(Document doc) { ... }
  ```

## Commit messages

- Written in **English**
- Use the imperative mood ("Add", not "Added")
- Format: short summary (≤72 chars) + optional body
- Prefix examples: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`

  ```
  feat: add Qdrant vector store integration
  fix: handle empty RAG context gracefully
  docs: add README.pt-BR.md with Portuguese translation
  ```

## Branch names

- `main` — production
- `feat/<short-name>` — new feature
- `fix/<short-name>` — bug fix
- `docs/<short-name>` — documentation only
- `chore/<short-name>` — tooling / deps

## PR / commit hygiene

- One logical change per commit
- Tests pass before commit (`./mvnw test`, `sbt test`, `pytest`, etc.)
- Public API changes documented in the commit body

## When in doubt

- Follow the convention in the existing repo
- If it's a new project, copy this file as-is
