# Architecture Decision Record

The three decisions that shape the app most, with their trade-offs. The
[README](../README.md) shows how each one works in the code and which tests
prove it.

## 1. The workflow as types, with the status derived from the history

**Context.** The assignment asks for an explicit workflow in which invalid
transitions are hard or impossible to represent, enforced by types rather than
by runtime checks (C3). It also asks that no observer, including the app after
a crash, ever sees a status without its matching history entry (C1).

**Decision.**

- Each status is a Kotlin type that offers only its allowed moves
  (`ApplicationStatus`). A move returns a `StatusChange`, which only `:domain`
  can create, so an illegal move does not compile.
- The status is not stored. `LicenceApplication` derives it from an
  append-only history, so a status change is one inserted row, and a status
  cannot disagree with its entry.
- The repository's `record` checks the current status and inserts the change
  in one transaction. Reading an application back replays its events through
  the same moves, so a damaged history never reaches the app.

**Alternatives.**

- An enum with a transition table is shorter, but it checks every move at
  runtime, so an illegal call still compiles.
- A status column next to the history works if every write updates both in
  one transaction. It keeps two copies of the truth, though, and any write that
  forgets one of them breaks C1.
- A state machine library adds a dependency and still checks at runtime.

**Consequences.**

- Illegal moves do not compile, and C1 holds by construction rather than by
  discipline.
- The history is both the audit trail and the source of truth.
- There are more types than with an enum, and a status read from storage must
  be narrowed with an exhaustive `when`.
- Each read replays the history, which is cheap for a handful of events.
- The database stores the names of the events and reasons, so renaming one
  needs a migration.

## 2. Crash-safe processing, one saved step at a time

**Context.** The worker can die at any point and run again from the start
(C2). A rerun must not duplicate history entries or side effects, and it must
end in the same state. Temporary failures must be retried, permanent ones must
end the workflow, and the proof must run on the JVM.

**Decision.**

- `ProcessApplicationUseCase`, in pure Kotlin, calls the issuer once per step
  and saves the result before the next call. Every run continues from the
  saved status.
- The issuer's answers depend only on the request. The age checks use the time
  processing started, which is saved once in the history.
- `record` refuses a change that does not start from the current status, so a
  duplicate or racing run cannot save a step twice; it continues from the
  stored status instead.
- An unreachable issuer is a temporary failure, and the run asks to be
  retried. An answer from the issuer, such as failed data, is permanent. On the
  last attempt, the domain records Failed with `ProcessingAbandoned`.
- WorkManager does the mechanics: unique work with `KEEP`, a network
  constraint, exponential backoff and the attempt count.

**Alternatives.**

- One call that returns every decision is cheaper, but the statuses would only
  replay that one answer instead of showing each step as it runs.
- Retries decided in the worker cannot be tested on the JVM, and retries in an
  HTTP client live in memory, so they die with the process.
- WorkManager alone cannot guarantee one run at a time, because a stopped
  worker stops cooperatively and can overlap the next run.

**Consequences.**

- Every crash point is safe, and the tests crash right before and right after
  each save.
- Duplicate runs are harmless, and giving up is a business event in the
  history, not just a log line.
- Processing makes three issuer calls instead of one. A real issuer would run
  the workflow on its server, and the app would submit once and follow the
  status.
- The domain knows the attempt number, and WorkManager also counts the runs
  that the system stopped.

## 3. Data protection with SQLCipher, Tink and Keystore keys

**Context.** The app stores a national ID, contact details and a face photo.
The assignment asks for encryption at rest with a reasonable key, such as one
backed by the Android Keystore, and for its limitations. Jetpack Security,
which the assignment mentions, is deprecated.

**Decision.**

- SQLCipher encrypts the whole Room database, including the history. Its key
  is 32 random bytes, used as a raw key.
- That key is saved encrypted by an AES-256-GCM key in the Android Keystore,
  through Tink, and written with `AtomicFile`, so a crash cannot leave half a
  key.
- The photo is encrypted with Tink's streaming AEAD while it is copied, with
  its own Keystore-protected key. The application ID is the associated data,
  so a file cannot pass for another application's photo.
- A new key is made only while no data exists. After that, a key that cannot
  be read is an error, and the app deletes nothing.
- Backup and device transfer are off, WorkManager gets only the application
  ID, and the logs carry no personal data.

**Alternatives.**

- Encrypting only the sensitive fields, the assignment's other example, is
  easy to get wrong: one forgotten field, such as a reason in the history,
  leaks data.
- Tink's `AndroidKeysetManager` can fall back to a key in cleartext when the
  Keystore fails.
- Making a new key and deleting the data when a key cannot be read loses every
  application, even after a temporary failure.
- Keys that need user authentication protect better, but they need an unlock
  flow, and background processing could not open the database on its own.

**Consequences.**

- No field can be missed, and the keys never leave the phone.
- The APK is bigger, because SQLCipher ships native libraries.
- A lost Keystore key makes the data unreadable, and the app crashes at launch
  until its storage is cleared. The README explains why this is rare and what
  a production app would do.
- Anyone who can unlock the phone can open the app and see the data.
