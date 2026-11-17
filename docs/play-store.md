# Play Store: listing and console answers

Draft answers for the Play Console forms. They come from reading this app's code and the privacy page on
ghstmail.space. Anything about what the servers store is for whoever runs the backend to confirm before
it is submitted.

The listing text and images are in `fastlane/metadata/android/en-US/`.

## App details

| Field | Value |
|---|---|
| App name | GhstMail: email aliases |
| Package | `space.ghstmail.android` (cannot change after the first upload) |
| App or game | App |
| Category | Productivity |
| Free or paid | Free |
| Contact email | support@ghstmail.space |
| Website | https://www.ghstmail.space |
| Privacy policy | https://www.ghstmail.space/privacy |
| Contains ads | No |

## App access

All of the app sits behind sign-in, so reviewers need a login. Create a test account with a password
(not a Google one), put its email and password in the "App access" form, and keep them out of this repo.

## Account deletion

| Field | Value |
|---|---|
| Can users create an account | Yes |
| In the app | Account → Delete account → Email support (opens an email to support@ghstmail.space) |
| Web link for deletion requests | https://www.ghstmail.space/privacy (the "Delete your account" section) |
| What is deleted | The account and every alias, within 7 days (per the privacy page) |

A page of its own for deletion requests would be a safer link than a section of the privacy page.

## Data safety

| Question | Answer |
|---|---|
| Does the app collect or share user data | Yes, collects. Shares: no |
| Is data encrypted in transit | Yes (HTTPS only) |
| Can users request deletion | Yes, see above |

Data types collected, all for "App functionality" and "Account management", none optional:

| Data type | What it is here |
|---|---|
| Personal info → Email address | The address you sign up with; aliases forward to it |
| Personal info → User IDs | The account id behind the session |
| App activity → Other user-generated content | Alias labels and filter domains. A label defaults to the name of the app or website the alias was made for |

Not collected by the app: location, contacts, photos, files, device ids, advertising id, crash logs,
analytics. The app has no analytics or ads SDK.

The mail that passes through an alias reaches the servers from the sender, not from this app, so it is
not part of the app's data safety form. It is covered by the privacy policy.

## Content rating

No violence, sexual content, profanity, drugs, gambling or user-to-user communication inside the app.
Expected rating: Everyone / PEGI 3.

## Target audience

Suggested: 18 and over. That keeps the app out of the Families policy. The terms on the website do not
state a minimum age; pick whatever matches them.

## Payments

GhstMail Plus is sold on the website. The app has no purchase screen and must not link to the web
checkout; adding one means using Google Play Billing.

## Sign in with Google

Google sign-in needs one Android OAuth client per signing key in the Google Cloud project `ghstmail-514519`,
each with the package name and the key's SHA-1:

| Key | Where its SHA-1 comes from |
|---|---|
| Debug | `~/.android/debug.keystore` (client exists) |
| Upload key | The keystore named in `keystore.properties` |
| Play app signing key | Play Console → Test and release → App integrity, after the first upload |

## Building the bundle

```
./gradlew :app:bundleRelease
```

Signing reads `keystore.properties` at the repo root (`storeFile`, `storePassword`, `keyAlias`,
`keyPassword`). Without it the bundle is signed with the debug key and Play rejects it.
