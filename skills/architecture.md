# Clean Architecture

Always separate layers.

Presentation

↓

Domain

↓

Data

Rules

- UI never talks directly to API.
- UI never talks directly to Room.
- Repository handles data sources.
- Business logic belongs inside UseCases.
- Every feature should be independent.
- Follow SOLID principles.
- Prefer composition over inheritance.