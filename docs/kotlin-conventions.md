# Kotlin Conventions and Codestyle guidelines
This repo presents some conventions and code style guidelines, with Android application development in mind.
Following these will help you achieve **readable** and **easy-to-maintain** code.

💡 Before we dive into the recommendations, it is important to say this:

If you are part of a team, take the time to define a set of rules and conventions with your colleagues before starting development.
This will make your lives much easier in the future — both for you and for future developers.


### 1. Proper naming of functions and variables
Based on my experience, here’s what I usually recommend:
- Acronyms and abbreviations can confuse developers,
- very descriptive names, however, do not.
- Generic names can also confuse developers.
- Do not be afraid to use long names.
- If you refactor, change, or update code, be sure to check that naming is still valid.
- Functions should use verbs and clearly describe the action they perform.
- Naming classes: keep it simple and descriptive.
- Avoid repetition in hierarchies and namespaces.
- Keep one tense. Do not mix tenses for functions serving the same purpose (e.g., `renameAccountClicked`, `updateAccountClick`).
- If possible, match the names with the business requirements.

some examples:

```kotlin
// avoid repetition in hierarchies and namespaces
sealed class AuthorizedLogin {
    ❌ data object AuthorizedLoginSucceeded
    ✅ data object Succeeded

    ❌ data object AuthorizedLoginFailed
    ✅ data object Failed

    ❌ data object AuthorizedLoginInProgress
    ✅ data object InProgress
}
// so later in the code you get this:
return AuthorizedLogin.Succeeded
// instead of this:
return AuthorizedLogin.AuthorizedLoginSucceeded

// Acronyms and abbreviations can confuse developers,very descriptive names, however, do not.
❌ fun getData() { ... }
✅ fun getAccountData() { ... }

❌ fun updAccount(account: Account) { ... }
✅ fun updateAccount(account: Account) { ... }

❌ fun updateAccount(newAccountName: String) { ... }
✅ fun updateAccountName(newAccountName: String) { ... }
```

### 2. Order of public and private functions in Classes:
1. First, implement interfaces and abstract classes (if any). If these functions are empty/not used place them at the bottom of the class.
2. Second, place public functions at the top of the class.
3. Finally, place private functions at the bottom of the class, following the same order in which they are used by the public functions.
   Example:
```kotlin
class AccountViewModel : SomeInterface {

    init {
        sendLogEvent()
    }

    override fun functionFromInterface() { ... }

    fun onAccountClick() {
        ...
        updateAccount()
    }

    private fun updateAccount() { ... }

    private fun sendLogEvent() { ... }
}
```

### 3. Wrap chained calls
If there are many (usually more than two) chained calls, then wrap them and don't write them all on one line. It can help with debugging.
```kotlin
❌ val anchor = owner?.firstChild?.siblings(forward = true)?.dropWhile { it is PsiComment || it is PsiWhiteSpace }

✅ val anchor = owner
    ?.firstChild
    ?.siblings(forward = true)
    ?.dropWhile { element ->
        element is PsiComment || element is PsiWhiteSpace
    }
```

### 4. Named arguments and parameter list wrapping
It's not always clear what arguments are passed into a function. Named arguments can solve this.
In addition, when there are more than two arguments, use list wrapping.
```kotlin
// named arguments & parameter list wrapping
drawSquare(
    x = 10,
    y = 10,
    width = 100,
    height = 100,
    fill = true,
)
```

### 5. Avoid all usage of the implicit argument name `it` inside multi-line expressions or multi-line lambdas
One of the most important conventions. Unless it is absolutely clear from context try to avoid implicit `it`.

### 6. Read Kotlin and Android conventions
- https://kotlinlang.org/docs/coding-conventions.html
- https://developer.android.com/topic/architecture/recommendations#naming-conventions
- https://developer.android.com/kotlin/style-guide

### 7. Define rules for naming composables
I usually use the suffix `Screen` for each new screen, `Content` for the main content of the screen, and `View` for smaller inner composables.

```kotlin
@Composable
fun AccountScreen(
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AccountContent(
        state = state,
        modifier = modifier,
    )
}

@Composable
private fun AccountContent(
    state: AccountUiState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(text = state.accountName)
        AccountDetailsView(details = state.details)
    }
}

@Composable
private fun AccountDetailsView(
    details: AccountDetails,
    modifier: Modifier = Modifier,
) { ... }
```

### 8. Follow these consistently. If you need to deviate, say why.
