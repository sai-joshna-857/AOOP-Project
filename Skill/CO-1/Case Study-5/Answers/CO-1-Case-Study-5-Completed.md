# CO-1 Case Study 5 — Completed (Team Submission)

**Subject:** AOOP (Advanced Object-Oriented Programming, 25CS2205E)
**Case Study 5:** User Accounts, Email Verification and Contact Details
**Common base:** Case Study 1 (Money & Pricing — value objects, invariants) and Case Study 2 (Order Lifecycle — state, guarded transitions) — Case 5 applies *both* patterns: typed value objects for contact data, and a state model for verification instead of Boolean flags.

> Structure of this submission, as required: Problem Analysis → Invariants (Q1) → Domain Classification (Q2) → Making Illegal States Unrepresentable, with Java + UML (Q3) → Verification & Lifecycle (Q4) → Cross-Aggregate Rules (Q5) → Design Justification.

---

## 1. Problem Analysis — what is wrong with the existing design

The existing system stores a user as **primitive types, nullable fields and Boolean flags**: `String email`, `boolean emailVerified`, `Date verifiedAt`, `String verificationToken`, `Date tokenExpiry`, loose `String`s for phone and address parts, and a `status` string. That shape *admits* every bug the company has seen:

| Bug reported | Root cause in the design |
|---|---|
| Registrations with incorrectly formatted emails | `email` is a raw `String` — nothing validates it at construction |
| Users "verified" with no verification date recorded | `emailVerified` (boolean) and `verifiedAt` (nullable date) are **two independent fields** that can contradict each other — 4 combinations, only 2 are meaningful |
| Expired tokens still accepted | `verificationToken` and `tokenExpiry` are separate nullable fields; nothing forces the expiry check to happen inside the validation operation itself |
| Phone numbers in many different formats | `phone` is a free `String` — no canonical form is ever enforced |
| Incomplete postal addresses | Street/city/postal code/country are separate loose fields — any subset can be null or blank and the object still "exists" |
| Suspended users can still log in | `status` is a free string / unenforced flag, and login does not go through a single guarded check on it |
| Duplicate accounts: `Asha@Example.com` vs `asha@example.com` | Emails are compared as raw strings, case-sensitively, with no normalisation and no uniqueness enforcement |

**Design issues, in the language of Cases 1 and 2:** (1) data is held in types that cannot express the rules (primitive obsession — exactly like `double` for money and `String` for currency in Case 1); (2) one fact is stored in several independent fields that can disagree (exactly like `status` + `paid`/`shipped`/`cancelled` booleans in Case 2); (3) rules live in scattered service code that every caller must remember to invoke, instead of inside the objects themselves.

**The fix follows the same two moves as Cases 1 and 2:** replace primitives with **self-enforcing value objects** (`EmailAddress`, `PhoneNumber`, `PostalAddress`, `VerificationToken`), and replace flag combinations with **one type per verification state**, so contradictory states cannot be constructed at all.

---

## Q1. (6 Marks) Business invariants — at least seven

| # | Invariant (business rule) | Why it must be enforced | What goes wrong if it is violated |
|---|---|---|---|
| 1 | **Every email address is syntactically valid** — exactly one `@`, non-empty local part, a valid domain with a dot/TLD, no spaces, within length limits | The email is the user's identity, login ID and only recovery channel | Accounts that can never be contacted, verified or recovered; undeliverable mail; garbage identities in the system |
| 2 | **An email is "verified" if and only if a verification was actually completed, with the time recorded** — verified status and `verifiedAt` can never disagree | Verification is a security/compliance claim: "we proved this person controls this inbox" | The reported bug — accounts marked verified with no date: unverifiable audit trail, accounts treated as trusted that never proved ownership, compliance failure |
| 3 | **A verification token is accepted only before its expiry, only once, and only for the account/email it was issued to** | A token is a time-limited proof of inbox control; expiry bounds how long a leaked link stays dangerous | The reported bug — expired (effectively permanent) tokens: a forwarded/old email link becomes a forever-key to the account; a used token replayed gives a second actor access |
| 4 | **A phone number is stored in exactly one canonical format** (E.164: `+` + country code + digits, e.g. `+919876543210`) with a valid country code and length for that country | Phone is used for OTP/SMS and for matching duplicates — it must compare and dial identically everywhere | The reported bug — the same number stored as `09876…`, `+91 98765…`, `98765…`: SMS/OTP delivery failures, the same person not recognised as the same contact, equality checks failing |
| 5 | **A postal address, when present, is complete** — street, city, postal code and country all present and non-blank, and the postal code matches the country's format | A partial address is worse than none: it looks usable but fails at shipping/billing time | The reported bug — undeliverable post, failed invoices/KYC, support cost, and no way to distinguish "no address" from "broken address" |
| 6 | **Account status controls what the account can do:** only an **ACTIVE** account can log in; a SUSPENDED account cannot log in until reactivated; a DELETED account can never log in or be used again; an account that has not completed required verification cannot be fully ACTIVE | Status is the enforcement point for abuse control, user requests and legal deletion | The reported bug — suspended users logging in makes suspension meaningless (abuse continues, bans are unenforceable); deleted accounts resurrecting violates the user's deletion request |
| 7 | **An email address is unique across all accounts, after normalisation** (trim + lower-case the whole address for comparison/storage key, e.g. `Asha@Example.COM` ≡ `asha@example.com`) | Email is the login identity and the password-reset channel — it must point to exactly one account | The reported bug — duplicate accounts: password reset becomes ambiguous (which account gets reset?), one person splits their identity, and an attacker can shadow-register a look-alike of a victim's address |
| 8 | **Changing the email address resets verification:** the account is unverified for the new address until the new address is verified (extra, strengthens #2/#3) | Verification proves control of *that* inbox, not of the person forever | A user (or an attacker who hijacked a session) swaps in a new email and inherits "verified" trust they never proved — account recovery is then captured by the unverified address |

## Q2. (6 Marks) Entities and value objects

| Concept | Kind | Justification — identity, equality, lifecycle, mutability |
|---|---|---|
| **User Account** | **Entity (aggregate root)** | Has its own identity (`UserId`) that survives every change — email, phone, address and even status all change during its life, yet it is still the *same* account. Equality is by identity, not by attributes; it has a real lifecycle (registered → active → suspended → deleted). It is the aggregate root: verification and contact changes go through it, never around it. |
| **User Identifier** | **Value object** | A typed wrapper around the ID value (e.g. a UUID). It has no lifecycle of its own and is interchangeable if the value is the same; its job is type safety — a `UserId` cannot be accidentally passed where an email or a token is expected, and IDs of different kinds cannot be swapped (same pattern as `OrderId`/`ProductId` in Case 2). Immutable. |
| **Email Address** | **Value object** | Defined entirely by its (normalised) value: `asha@example.com` is the same address wherever it appears; no identity, no lifecycle. Equality and hashing are by the normalised value — which is also what makes the uniqueness rule in Q5 enforceable. Immutable: "changing email" means replacing the object, never editing it in place. Validation and normalisation happen once, in its factory. |
| **Phone Number** | **Value object** | Defined entirely by its canonical E.164 value; interchangeable, immutable, no lifecycle. Two inputs in different raw formats that parse to the same E.164 number are *the same value object* — exactly the property the current system lacks. |
| **Postal Address** | **Value object** | A complete address is a single fact made of parts (street, city, postal code, country) that are meaningless to change one at a time from outside; it has no identity — two users at the same address hold equal addresses. Equality is by all its fields; it is immutable and replaced as a whole. Being one object is what lets it enforce "complete or absent, never partial". |
| **Verification Token** | **Value object** (with a caveat) | The token *value* is defined by its data: the secret string + which email it was issued for + its expiry instant. It is immutable, compared by value, and meaningless outside that data — so as domain data it is a value object. *Caveat:* it is short-lived and single-use; that behaviour is enforced not by the token itself but by the account's verification state, which consumes the pending token when verification succeeds or expires (see Q3/Q4). It must also never be stored or compared as plain text casually — see the hashing note in Q4. |

**Memory rule (same as Cases 1–2):** *Entity = identity survives change ("which one is it?"). Value = equality by attributes, interchangeable, immutable ("what is it worth / what does it say?").*

## Q3. (8 Marks) Making illegal states unrepresentable

### What goes wrong with primitives, nulls and Booleans

The basic `User` holds 11 loose fields. Count the damage:

1. **`boolean emailVerified` + nullable `verifiedAt` + nullable `verificationToken` + nullable `tokenExpiry`** encode *one* fact — "where is this email in its verification journey?" — in **four independent fields**. That is 2 × (present/absent)³ combinations, and most of them are nonsense that the type system happily accepts:
   - `verified = true, verifiedAt = null` → the reported "verified with no date" bug.
   - `verified = true, token still present and unexpired` → is the old token still usable? Undefined.
   - `verified = false, verifiedAt = <yesterday>` → verified or not?
   - `token = null, tokenExpiry = <tomorrow>` → an expiry with nothing to expire.
   This is Case 2's `status` + `paid`/`shipped`/`cancelled` problem, field for field.
2. **`String email`, `String phone`** accept *anything* — `"not-an-email"`, `""`, `"123"` are all legal values until some service method happens to check them (Case 1's `String currency` problem). Every new code path must remember to re-validate.
3. **Nullable address parts** (`street`, `city`, `postalCode`, `country` each nullable) make "address" 16 combinations of present/absent — including the useless 14 partial ones. And `null` itself is ambiguous: does `phone = null` mean "user has no phone", "not asked yet", or "data lost"?
4. **`String status`** is an open set: `"ACTIVE"`, `"active"`, `"SUSPENDED"`, `"SUSPNDED"` (typo) are all storable, so login checks written as string comparisons silently fail open.
5. **Public / settable fields** mean any code can flip `emailVerified = true` without a token ever existing — enforcement by convention, not by construction.

### The improved design

**(1) Domain-specific types** — each validates itself once, at construction (factories are private-constructor + static `of(...)`, exactly like Case 1's `Money`/`Percentage`):

| Type | Kind | Validation rules (enforced in the factory) |
|---|---|---|
| `UserId` | Value | Non-null UUID; generated by the system, never typed by a user |
| `EmailAddress` | Value | Trimmed; exactly one `@`; local part 1–64 chars, no spaces; domain has ≥ 1 dot, valid labels, TLD ≥ 2 letters; total ≤ 254 chars. **Normalised to lower-case on construction** — equality, hashing and the uniqueness key all use the normalised form |
| `PhoneNumber` | Value | Parsed from any input format (`09876…`, `+91 98765…`, `98765…` with a default country) into **E.164** (`+` + country code + national number, digits only); country code must exist and digit count must fit that country (e.g. India = 10 national digits). Stored/compared only in E.164 |
| `PostalAddress` | Value | All four parts required, non-blank: `street`, `city`, `postalCode`, `country`; postal code pattern checked per country (e.g. India: 6 digits, first digit 1–9). *An address object that exists is complete — partial addresses cannot be constructed* |
| `VerificationToken` | Value | Non-blank secret (≥ 32 characters of cryptographic randomness, generated by the system — never chosen by the user); bound to the `EmailAddress` it was issued for; carries `expiresAt` **inside the same object**, so a token can never exist without its expiry; validity is asked of the token itself: `token.isValidFor(email, now)` |
| `AccountStatus` | Value (enum) | Closed set: `PENDING_VERIFICATION, ACTIVE, SUSPENDED, DELETED` — a typo'd status cannot be stored, and each login/behaviour rule switches on the enum exhaustively |

**(2) Email verification as states, not flags** — one type per state (Case 2, Model B). The account's verification is a single field of a sealed type; each state carries *exactly* the data that state needs, and nothing else:

```java
sealed interface EmailVerification
        permits Unverified, PendingVerification, Verified { }

record Unverified() implements EmailVerification { }

record PendingVerification(VerificationToken token)      // token carries its own expiry
        implements EmailVerification { }

record Verified(Instant verifiedAt)                      // the date is INSIDE the state
        implements EmailVerification { }
```

Now read the old bug list against this type: "verified with no date" — **cannot be built**, `Verified` requires an `Instant`. "Token accepted after expiry" — an unverified account holds a `PendingVerification` whose only exit is `verify(...)`, which asks the token itself (code below). "Verified but a live token still floating around" — impossible: moving to `Verified` *replaces* the `PendingVerification`, the token ceases to exist. There is exactly one field, so states cannot contradict each other.

**(3) Optional contact information as `Optional<T>` of a complete type** — "optional" is expressed once, at the field, with a *complete* value inside:

```java
private Optional<PhoneNumber> phone;        // empty = no phone; present = a valid, canonical number
private Optional<PostalAddress> address;    // empty = no address; present = a COMPLETE address
```

No nulls, no partial address, and callers are forced by the compiler to handle the empty case.

**(4) The aggregate — `UserAccount` (all fields private, no setters):**

```java
public final class UserAccount {

    private final UserId id;
    private EmailAddress email;
    private EmailVerification verification;          // ONE field, never flags
    private Optional<PhoneNumber> phone;
    private Optional<PostalAddress> address;
    private AccountStatus status;

    private UserAccount(UserId id, EmailAddress email, VerificationToken token) {
        this.id = id;
        this.email = email;
        this.verification = new PendingVerification(token);
        this.phone = Optional.empty();
        this.address = Optional.empty();
        this.status = AccountStatus.PENDING_VERIFICATION;
    }

    static UserAccount register(UserId id, EmailAddress email, VerificationToken token) {
        return new UserAccount(id, email, token);    // starts unverified, by construction
    }

    public void verifyEmail(String presentedSecret, Instant now) {
        if (!(verification instanceof PendingVerification pending)) {
            throw new DomainException("Email is not awaiting verification");
        }
        if (!pending.token().isValidFor(email, presentedSecret, now)) {
            throw new DomainException("Token is incorrect or has expired");
        }
        this.verification = new Verified(now);       // token is consumed & gone
        if (status == AccountStatus.PENDING_VERIFICATION) {
            status = AccountStatus.ACTIVE;
        }
    }

    public void changeEmail(EmailAddress newEmail, VerificationToken tokenForNewEmail) {
        this.email = newEmail;
        this.verification = new PendingVerification(tokenForNewEmail); // Invariant 8: trust resets
    }

    public boolean canLogin() {
        return status == AccountStatus.ACTIVE;       // the ONLY login rule, in ONE place
    }

    public void suspend()   { requireNotDeleted(); status = AccountStatus.SUSPENDED; }
    public void reactivate(){ if (status == AccountStatus.SUSPENDED && verification instanceof Verified) status = AccountStatus.ACTIVE; }
    public void delete()    { status = AccountStatus.DELETED; }
    // getters only for state other code may read; NO setters for any field
}
```

And the token checks its own expiry — the check cannot be forgotten by a caller, because there is no other way to use a token:

```java
public record VerificationToken(String secret, EmailAddress issuedFor, Instant expiresAt) {
    public VerificationToken {                        // compact constructor = always validated
        if (secret == null || secret.length() < 32) throw new IllegalArgumentException("Weak token");
        Objects.requireNonNull(issuedFor); Objects.requireNonNull(expiresAt);
    }
    boolean isValidFor(EmailAddress email, String presentedSecret, Instant now) {
        return issuedFor.equals(email)
            && secret.equals(presentedSecret)         // constant-time compare in real code (Q4)
            && now.isBefore(expiresAt);               // expiry enforced HERE, every time
    }
}
```

### UML class diagram (text form)

```
                        ┌────────────────────┐
                        │     UserAccount    │  «entity, aggregate root»
                        ├────────────────────┤
                        │ - id : UserId      │
                        │ - email            │
                        │ - verification     │
                        │ - phone            │
                        │ - address          │
                        │ - status           │
                        ├────────────────────┤
                        │ + verifyEmail()    │
                        │ + changeEmail()    │
                        │ + suspend()        │
                        │ + canLogin()       │
                        └─────────┬──────────┘
        ┌───────────────┬─────────┼──────────────┬─────────────────┐
        │ 1               │ 1     │ 1            │ 0..1            │ 0..1
┌───────┴───────┐ ┌───────┴──────────────┐ ┌─────┴────────┐ ┌─────┴────────┐ ┌─────┴─────────┐
│   EmailAddress│ │ «sealed»             │ │ AccountStatus│ │  PhoneNumber │ │ PostalAddress │
│   «value»     │ │  EmailVerification   │ │  «enum»      │ │   «value»    │ │   «value»     │
├───────────────┤ ├──────────────────────┤ ├──────────────┤ ├──────────────┤ ├───────────────┤
│ - normalised  │ │ Unverified           │ │ PENDING_VER. │ │ - e164       │ │ - street      │
│ + of(raw)     │ │ PendingVerification  │ │ ACTIVE       │ │ + parse(raw) │ │ - city        │
└───────────────┘ │ Verified(verifiedAt) │ │ SUSPENDED    │ └──────────────┘ │ - postalCode  │
                  └──────────┬───────────┘ │ DELETED      │                  │ - country     │
                             │ 0..1        └──────────────┘                  └───────────────┘
                  ┌──────────┴───────────┐
                  │  VerificationToken   │  «value»
                  ├──────────────────────┤
                  │ - secret             │
                  │ - issuedFor : Email  │
                  │ - expiresAt : Instant│
                  │ + isValidFor(...)    │
                  └──────────────────────┘
```

## Q4. (6 Marks) Email verification and account lifecycle

**1. Information maintained at registration:** the system-generated `UserId`; the normalised `EmailAddress`; a **hashed** password credential (never the plain password); the `VerificationToken` issued (in production, store only a *hash* of the secret — see below) with its expiry (e.g. issued now, expires in 24 hours); `status = PENDING_VERIFICATION`; registration timestamp; optional phone/address start as `Optional.empty()`. Nothing else is needed — in particular **no** `emailVerified` boolean and **no** `verifiedAt` date yet, because no verification has happened.

**2. How tokens are generated and validated:**
- *Generation:* a cryptographically random secret (e.g. 32 bytes from `SecureRandom`, encoded), created by the system at registration (and re-created on resend or email change), bound to that account's email, with an explicit expiry (e.g. 24 h). Only a **hash** of the secret (e.g. SHA-256) is stored in the database — the same discipline as passwords — so a database leak does not leak usable verification links.
- *Validation (all inside `VerificationToken.isValidFor`, called only from `UserAccount.verifyEmail`):* (a) account must currently be in `PendingVerification` state; (b) presented secret's hash matches the stored hash — compared in constant time; (c) token was issued for *this* account's *current* email; (d) `now < expiresAt`. All four must pass; then the state moves to `Verified(now)` and the token is consumed.

**3. Expired tokens:** `verifyEmail` rejects them (check (d)) and the account simply stays in `PendingVerification` — no partial state, no exception-path corruption. The user requests a **resend**: the system generates a *new* token (new secret, new expiry) which **replaces** the old pending token, so at most one live token exists per account and the old link dies immediately. (Rate-limit resends, e.g. a few per hour, so the resend endpoint cannot be used to spam an inbox.)

**4. A verified user changes their email:** verification is a property of the *address*, not the person — so `changeEmail(...)` (code in Q3) atomically replaces the email, **discards the old `Verified` state**, and puts the account into `PendingVerification` with a fresh token for the **new** address. The account returns to full standing only when the new address is verified. Until then it should not be treated as verified anywhere (e.g. the new address receives no password-reset trust). Optionally, a notification is sent to the *old* address so the real owner learns of the change.

**5. Keeping suspended/deleted accounts out:** login has exactly one gate — `UserAccount.canLogin()` returns true **only** for `status == ACTIVE` (Q3 code). The authentication service calls it after password verification and refuses the session otherwise, with a generic "account not available" response. Because `status` is an enum mutated only through `suspend()` / `reactivate()` / `delete()` on the aggregate, there is no second flag anywhere that can disagree with it — and `DELETED` has no transition back, so deletion is final. (Suspended accounts also have existing sessions/tokens revoked at suspension time, so an already-logged-in suspended user is cut off too, not just blocked at next login.)

**6. Preventing contradictory verification states (design changes):** delete the four loose fields (`emailVerified`, `verifiedAt`, `verificationToken`, `tokenExpiry`) and replace them with **the single sealed `EmailVerification` field** from Q3; make the transitions methods on `UserAccount` (`verifyEmail`, `changeEmail`, plus reissue on resend) as the *only* ways the field changes; keep all fields private with no setters; and record `verifiedAt` *inside* `Verified`, so the timestamp exists precisely when — and only when — the state is verified. Contradiction is then not "checked for" — it is **unrepresentable**, the same claim Case 2's Model B makes for orders.

## Q5. (4 Marks) Cross-aggregate rules and design evaluation

**1. Is email uniqueness an invariant of an individual `User` object? No.** An invariant of one object must be checkable from that object's own data. A single `UserAccount` knows only its own email; uniqueness is a statement about **the set of all accounts** ("no *other* account has this normalised email"). One account cannot see the others — this is exactly Case 2's stock/credit pattern: a rule spanning aggregates is not an invariant of any one of them.

**2. How the application enforces it:** in the **registration service** (application/domain service coordinating the repository): normalise the email via `EmailAddress.of(...)` → look up that normalised value in the user repository → if an account already exists, reject the registration; otherwise create and save the account. Normalisation is what makes the check meaningful: `Asha@Example.com`, ` ASHA@example.com ` and `asha@example.com` all reduce to the same key *before* the lookup, closing the reported duplicate bug.

**3. The simultaneous-registration race:** two requests with the same normalised email can both pass the lookup at the same moment ("check-then-act" race — neither exists *yet*), then both insert, producing exactly the duplicate the rule exists to prevent. An application-level check alone **cannot** close this window.

**4. Database protection (the real guarantee):** a **UNIQUE constraint / unique index on the normalised-email column** of the users table. The database enforces it atomically at insert: in the race above, the second insert fails with a constraint violation, which the registration service catches and turns into the normal "email already registered" error. The application check remains — it gives a friendly, early error in the 99% case — but the *invariant* is guaranteed by the unique index, which no code path, bug or race can bypass. (The same pattern: unique index on any other must-be-unique value, e.g. a token hash, if the schema stores one per account.)

**5. Strongly typed verification model vs Booleans + nullables — one advantage, one disadvantage:**
- **Advantage:** whole classes of bugs become **impossible instead of merely checked for** — "verified without a date", "live token on a verified account", "expiry never checked" cannot be constructed, and the compiler forces every `switch`/pattern match over the sealed states to handle each state explicitly. Review and testing shrink: the type *is* the specification.
- **Disadvantage:** **more code and more friction at the boundaries** — three state types plus token/address/phone types instead of a few columns; persistence must map the state type to/from columns (a discriminator + nullable state columns in the DB, re-validated on load), and simple CRUD/admin tooling and junior developers face a steeper learning curve. The trade is worth it where correctness is a security property (identity, verification), exactly as in Cases 1–2.

---

## 2. Design Justification — how each reported bug is now prevented

| Reported bug | Prevented by |
|---|---|
| Malformed emails register | `EmailAddress.of(...)` validates + normalises; there is no other way to create one |
| "Verified" but no verification date | `Verified(Instant)` carries the date inside the state; the state cannot exist without it |
| Expired tokens accepted | Expiry lives inside `VerificationToken` and is checked inside `isValidFor(...)` — the only door to `Verified` |
| Phones in many formats | `PhoneNumber.parse(...)` canonicalises to E.164; equality/storage only in that form |
| Incomplete addresses | `PostalAddress` requires all parts; optionality is `Optional<PostalAddress>` — complete or absent |
| Suspended users log in | Single gate `canLogin()` = `status == ACTIVE`; enum status, mutated only via aggregate methods |
| Duplicate accounts via capitalisation | Lower-case normalisation + repository check + **DB unique index on the normalised email** (race-proof) |
| Verified trust carried to a new email | `changeEmail(...)` resets verification to `PendingVerification` for the new address |

## 3. Quick revision — Case 5 in the language of Cases 1 and 2

| Case 1 / 2 pattern | Case 5 instance |
|---|---|
| `double` / `String currency` (Case 1) | `String email`, `String phone`, `String status` → typed `EmailAddress`, `PhoneNumber`, enum `AccountStatus` |
| Status string + 3 booleans (Case 2) | `emailVerified` + nullable `verifiedAt`/`token`/`expiry` → one sealed `EmailVerification` type per state (Model B) |
| Private constructor + factory (Case 1) | `EmailAddress.of`, `PhoneNumber.parse` — validation in exactly one place |
| Rule needing data outside the aggregate (Case 2: stock, credit) | Email uniqueness → registration service + DB unique constraint, **not** a `User` invariant |
| Derived over stored (Case 2: total) | Don't store "verified" as a fact *plus* its evidence separately — the state type *is* the fact and the evidence |

*End of CO-1 Case Study 5 — Completed.*
