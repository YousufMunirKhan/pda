# API Standards

Networking

- Retrofit
- OkHttp
- Gson or Kotlin Serialization

Rules

- Never call Retrofit from Activity or Fragment.
- All network calls go through Repository.
- Handle loading state.
- Handle error state.
- Handle empty state.
- Refresh expired tokens automatically.
- Use HTTPS only.
- Parse API errors correctly.