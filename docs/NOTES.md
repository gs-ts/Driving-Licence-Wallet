# Notes

The assumptions, the shortcuts, how AI was used, what to improve before
production, and what was deliberately left out.

## Assumptions

- **Generic EU.** The national ID, the phone number and the address follow no
  country's format, because the EU has no common one, so they only need to be
  filled in. A name needs at least one letter, in any script.
- **Licence categories** are the EU's, from AM to DE (Directive 2006/126/EC).
- **Ages** are checked on the date processing starts, because EU minimum ages
  apply when the licence is issued (Directive 2006/126/EC). The issuer's time
  zone counts, not the phone's, and someone born on 29 February turns a year
  older on 1 March in other years. The assignment's ages, 18 and 21, apply to
  every category.
- **Photos** must be JPEG. The standards for ID photos (ICAO 9303, and ISO/IEC
  18013-5 for mobile licences) use JPEG or JPEG 2000, and phones do not take
  JPEG 2000 photos. Quality is a score from 0 to 100, as in ISO/IEC 29794-5,
  simulated from the resolution.
- **The issuer decides the business rules.** The app checks only the required
  fields, the email's shape and the photo type, for instant feedback.
- **One application** can be in progress at a time. Approved, Rejected and
  Failed are final, and trying again means a new application.
- **Failed** covers both a failed check and a give-up after repeated network
  errors, and the history says which. The history holds status changes only;
  retries go to the logs.
- **Processing waits for a network,** as it would with a real issuer, although
  the simulated issuer runs on the phone.
- **Privacy (GDPR):** the app collects only the assignment's fields and logs
  no personal data.
- **English only,** with every text in string resources.
- **Approved is the end of the workflow.** A real system would then issue a
  mobile driving licence (ISO/IEC 18013-5) into the applicant's EUDI Wallet.

## Shortcuts

- **The issuer runs inside the app,** with one call per step. A real issuer
  would run the workflow on its server, and the app would submit once and
  follow the status.
- **A review is a local command** from the Simulation section on the
  applicant's own status screen, instead of a back office.
- **"Server unreachable" is kept in memory,** so it is off again after the
  app's process ends.
- **Photo quality comes from the resolution,** not from an analysis of the
  image.
- **The issuer's settings** (the watchlist, the thresholds and the delay) are
  code, so changing them needs a new build.
- **WorkManager's attempt count decides when to give up,** and it also counts
  the runs that the system stopped.
- **The app shows only the latest application.** Older ones stay in the
  encrypted database.
- **Code quality is checked by detekt and Android lint in CI.** A production
  team would add a platform such as SonarQube.

## AI usage

The project was developed with Claude Code, using Claude Opus 5.5. Claude
wrote 70% or more of the code.

- The developer set up the project: the build, detekt, CI and the rules in
  `AGENTS.md`. Claude built the app with two guides that the developer wrote:
  the [Kotlin conventions and code style][conventions], which the repository
  keeps in `docs/kotlin-conventions.md`, and the
  [pragmatic-clean-architecture-kotlin][skill] skill for the architecture.
- The developer wrote the domain's foundations (the contracts, the interfaces
  and the domain models), and agreed the test cases, by their given/when/then
  names, before Claude wrote them. Claude also completed the classes that were
  left unfinished, and turned the developer's pseudocode into working code.
- The developer wrote a good part of the UI layer.
- The developer made the design decisions, reviewed every change, restyled
  parts of it, and tested each step on real devices.
- At the end, Claude Opus 5.5 and Gemini Pro each reviewed the project against
  the assignment's requirements.

[conventions]: https://github.com/gs-ts/Kotlin-Conventions-And-Codestyle
[skill]: https://github.com/gs-ts/pragmatic-clean-architecture-kotlin

## Before production

- **A real issuer server.** The app would submit once, with the application ID
  as the idempotency key, and the server would run the workflow. The app would
  follow the status, through push messages or polling, and resolve conflicts
  between its copy and the server's.
- **Error handling.** A recovery screen for a database that cannot be opened,
  and error reports, including a bug in a processing step, which today is only
  logged.
- **A status banner** from WorkManager's work info, such as "Waiting for the
  network" or "Retrying".
- **The release build.** Remove `packageScope`, so that R8 optimizes the whole
  app, add keep rules where needed, sign it, and test it on devices.
- **Privacy.** Hide the recent-apps thumbnail, show the internal review reasons
  only to reviewers, and delete old applications and their photos after a
  retention period.
- **The form surviving process death,** without putting the national ID in the
  saved state, for example as a draft in the encrypted database.
- **Identity from the EUDI Wallet.** The applicant's identity would come from
  the wallet's PID instead of being typed. Without the wallet, the app would
  check each country's ID and phone formats.
- **Our own attempt counter,** so that system stops do not use up attempts,
  and a database rule that allows only one application in progress.
- **More tests:** migration tests in CI, an end-to-end UI test, and
  accessibility checks.

## Deliberately not done

- **A real server, sync and conflict resolution:** the assignment keeps
  everything on the device.
- **Issuing the licence** into the wallet after approval: out of scope.
- **Real biometric, liveness and quality checks:** the assignment asks to
  simulate them.
- **Country-specific formats:** the EU has no single format.
- **Camera capture:** the gallery is enough, and the Photo Picker needs no
  permission.
- **A list of past applications:** the app opens on the latest one.
- **The form surviving process death, and a back arrow on the form:** the
  assignment does not ask for them, and system Back works.
- **An error screen for a database that cannot be opened:** it is rare, and
  the README documents it.
- **Keys bound to user authentication, and hiding the recent-apps thumbnail:**
  both trade usability for privacy, which is a product decision.
- **A release build run on a device, an end-to-end UI test, and animations:**
  the assignment asks for the debug build, and the other two are optional.
