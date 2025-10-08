# Contributing to Wellness Pro

Thanks for your interest in contributing! Please follow these guidelines to make the process smooth.

1. Fork the repository and create a feature branch from `main`.
2. Follow the code style in the project (Kotlin idioms, XML layout best practices).
3. Add or update string resources in `res/values/strings.xml` for user-facing text.
4. Update documentation in the `docs/` folder if you add or change features.
5. Run the test suite where applicable and verify the project builds:

```pwsh
./gradlew clean assembleDebug
```

6. Create a Pull Request with a clear description of the change and link to any related issue.

7. Use small, focused PRs. Large refactors are fine but split them into logical commits.

Maintainers will review PRs and provide feedback. Thanks!
