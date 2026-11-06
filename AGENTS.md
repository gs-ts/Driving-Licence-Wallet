# DrivingLicenseWallet

## Project Structure and project facts
- **Navigation Pattern:** Navigation 3 only, no Navigation 2.
- **Architecture:** Pragmatic Clean Architecture + MVVM (avoiding over-engineering, adapting to project needs) + domain driven design (avoiding over-engineering, adapting to project needs).
- **UI:** We use Material 3 and Jetpack Compose. No View-based Material (com.google.android.material). The M3 theme lives in Theme.kt.
- **DI:** We use Hilt.
- **Build & checks:** `./gradlew build detektMain` must pass. Detekt does not check test files.
- **Dependencies:** no Compose BOM; versions are set by hand in `gradle/libs.versions.toml`. Ask before adding a dependency.
- **Storage:** Room for data, DataStore for preferences. minSdk 29.

## Research & Knowledge Sources
When unsure about an API or a version, or a design approach, or you need some inspiration, check the official docs first. Use the blogs as secondary sources:
- **Official Docs:** https://developer.android.com/, https://m3.material.io/, https://kotlinlang.org/docs/home.html
- **Architecture and Design Patterns:**
    - https://martinfowler.com/bliki/DomainDrivenDesign.html
    - https://martinfowler.com/bliki/AnemicDomainModel.html
    - https://martinfowler.com/tags/domain%20driven%20design.html
    - https://martinfowler.com/tags/application%20architecture.html
    - https://refactoring.guru/design-patterns
- For architecture work, use also the pragmatic-clean-architecture-kotlin skill.
- **Expert Blogs:**
    - https://jakewharton.com/blog/
    - https://www.romainguy.dev/posts/
    - https://johnoreilly.dev/
    - https://adambennett.dev/
    - https://joebirch.co/
    - https://zsmb.co/
    - https://marcellogalhardo.dev/posts/
    - https://samwho.dev/
    - https://handstandsam.com/
    - https://commonsware.com/blog/
- **Specialized & Newsletters:**
    - https://doveletter.dev/preview
    - https://jetc.dev/
    - https://code.cash.app/
    - https://www.revenuecat.com/blog/
- **Open Source Standards:** https://github.com/skydoves
- **Tooling:** Utilize configured MCP servers (like Dove Letter) if available, and search official docs/blogs for real-time Android insights.

## Review Instructions
1. **Prompt Review:** Carefully analyze my prompt. Ask when a missing requirement (e.g., data models) would change the design. Otherwise pick a sensible default, say which one, and continue.
2. **Double-Check for Deprecation:** Android evolves fast.
3. **Simplicity:** Do not overengineer. Prefer readable, maintainable code.
4. **Self-Correction:** Review your answer thoroughly before providing it.
5. **Production quality:** means correct, tested, and safe in the R8 release build. It does not mean more layers.

## Kotlin Conventions
Follow the Kotlin conventions in docs/kotlin-conventions.md.
@docs/kotlin-conventions.md

## How to work with me
- Explain the reasoning behind non-trivial decisions: the trade-off, and the alternative you rejected.
- Push back when my request conflicts with these rules, the official docs, or a better approach. Argue your case, then follow my decision.
