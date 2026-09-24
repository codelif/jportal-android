# Play Store setup

Everything to type into the Play Console, in order. Labels move around in the console now and then, so match by meaning if a name differs.

## Values you'll be asked for

| Field | Value |
|---|---|
| App name | `JPortal` |
| Package name (a.k.a. application ID, the name inside the bundle) | `in.codelif.jportal.android` |
| Default language | English (United States), `en-US` |
| App or game | App |
| Free or paid | Free (can never become paid later) |
| Category | Education |
| Website | `https://github.com/codelif/jportal-android` |
| Privacy policy URL | `https://github.com/codelif/jportal-android/blob/main/PRIVACY.md` |
| Release name | the tag, like `v0.1.0-rc.1` (Play suggests `10001 (0.1.0-rc.1)`, either is fine) |
| Upload key / app signing key | the same key: `~/.local/share/jportal-release/release.jks`, alias `jportal`, passwords in `release.env` next to it |
| Short description | `store/listing/en-US/short-description.txt` |
| Full description | `store/listing/en-US/full-description.txt` |
| App icon, 512x512 | `store/icon-512.png` |
| Feature graphic, 1024x500 | `store/feature-1024x500.png` |
| Phone screenshots | `store/screenshots/phone/*.png`, in file order |

The package name is baked into every bundle and can never change. Pick English (United States) as the default language: the release workflow uploads release notes under `en-US` and fails on a mismatch.

## 1. Developer account

1. Go to play.google.com/console/signup and pick **Yourself** (personal account).
2. Pay the one-time $25.
3. Developer name: what the store shows under the app, like `codelif` or your name.
4. Verify your identity with a government ID and verify a phone number and email. Google may also want you to confirm through the Play Console app on an Android phone.
5. Wait for verification, usually 1 to 3 days.

Personal accounts made after November 2023 have to run a closed test with 12 testers for 14 days before they can publish to everyone. Step 7 covers it.

## 2. Create the app

All apps › **Create app**:

- App name: `JPortal`
- Default language: English (United States)
- App or game: App
- Free or paid: Free
- Tick both declarations (Developer Program Policies, US export laws)

If it asks for a package name here, it's `in.codelif.jportal.android`.

## 3. Set up your app (Dashboard › "Set up your app")

Every item here has to be done before even a closed test can go out.

### App access

- Choose **All or some functionality in my app is restricted**.
- Add instructions:
  - Name: `Demo student`
  - Username and password: leave empty
  - Other information: `Signing in needs a JIIT university Google account. To see the whole app without one, tap "Demo" at the top right of the sign-in screen. It loads a made up student, stays offline and never contacts the university portal.`

### Ads

No, my app does not contain ads.

### Content rating

- Email: your contact email.
- Category: **All other app types** (utility, productivity, reference).
- Answer **No** to everything: violence, fear, sexuality, gambling, language, controlled substances, crude humour, user interaction or content sharing, sharing location, digital purchases, unrestricted internet access or web browsing.
- Submit. Expect everyone/3+ ratings everywhere.

### Target audience and content

- Age groups: **18 and over** only.
- Appeal to children: No.

Anything under 18 pulls in the Families policy, which the app gains nothing from.

### News apps

No.

### Data safety

This is the one to read carefully. The app sends things off the phone only to the JIIT portal and to Google for sign-in, always because the user asked it to. Nothing reaches the developer.

Overview:

- Does your app collect or share any of the required user data types? **Yes**
- Is all of the user data collected by your app encrypted in transit? **Yes** (https only)
- Which account creation methods does your app support? The app never creates accounts, you sign in with the JIIT account you already have. Pick the option for apps that don't let users create an account; if the form insists, pick **OAuth** (Google sign-in) and say accounts are created and deleted by JIIT.
- Do you provide a way for users to request that their data is deleted? The app keeps nothing off the phone: signing out or uninstalling deletes everything it holds. Answer **No** if the only choices are about server-side data, the portal data belongs to JIIT.

Data types, what the app sends to the portal and to Google sign-in:

| Category | Type | Collected | Shared | Ephemeral | Required | Purpose |
|---|---|---|---|---|---|---|
| Personal info | Name | Yes | No | No | Required | App functionality, Account management |
| Personal info | Email address | Yes | No | No | Required | App functionality, Account management |
| Personal info | User IDs (enrollment number) | Yes | No | No | Required | App functionality, Account management |
| App activity | Other user-generated content (feedback ratings) | Yes | No | No | Optional | App functionality |

Everything else (location, contacts, photos, files, health, messages, device ids, diagnostics, crash logs, web history) is **not collected**.

"Shared: No" rests on Play's exemption for data sent to a third party because the user directly asked for it, which is exactly signing in to the portal and submitting feedback. The data the app only receives and shows (marks, fees, bank details, address) isn't collected in Play's sense, it never leaves the phone from the app's side. Read Play's own help page on the form once before submitting; if a question reads differently than this table assumes, answer the stricter way.

### Government apps

No.

### Financial features

My app doesn't provide any financial features. (It only shows fees the portal already has.)

### Health apps

No health features.

### Advertising ID

No. The app never asks for it.

### Privacy policy

`https://github.com/codelif/jportal-android/blob/main/PRIVACY.md`

### Store settings

- App category: Education
- Tags: pick the closest few, like Education, Productivity
- Email address: a public contact email, required
- Website: `https://github.com/codelif/jportal-android`
- Phone: leave empty

### Main store listing

- App name: `JPortal`
- Short description and full description: paste from `store/listing/en-US/`
- App icon: `store/icon-512.png`
- Feature graphic: `store/feature-1024x500.png`
- Phone screenshots: all 8 from `store/screenshots/phone/`, in file order
- Tablet, Chromebook, XR screenshots: skip
- Video: skip

## 4. App signing, with your own key

This happens the first time you create a release (step 6). When Play asks how to sign:

1. **Change app signing key** (or "Use a different key").
2. **Export and upload a key from Java keystore**.
3. Download `pepk.jar` and the encryption public key it offers (`encryption_public_key.pem`), into `~/Downloads`.
4. Run, and answer both password prompts with the password from `~/.local/share/jportal-release/release.env`:

   ```sh
   java -jar ~/Downloads/pepk.jar \
     --keystore=$HOME/.local/share/jportal-release/release.jks \
     --alias=jportal \
     --output=$HOME/Downloads/jportal-signing-key.zip \
     --include-cert \
     --rsa-aes-encryption \
     --encryption-key-path=$HOME/Downloads/encryption_public_key.pem
   ```

5. Upload `jportal-signing-key.zip`.
6. Upload key: the same key. If Play asks for an upload key certificate, skip it; the bundles are signed with the key you just gave it.
7. Delete `jportal-signing-key.zip` afterwards.

Check under Test and release › App integrity › App signing: the app signing key's SHA-256 should be `90:51:B5:B7:D7:F1:94:6C:EE:DB:AD:D5:BE:B2:D5:FA:6A:11:1A:60:EC:B3:D9:3B:7B:46:87:1B:F2:FC:CC:EE`, the same as the GitHub APKs. That's what lets people move between the two without uninstalling.

## 5. First test build

```sh
tools/release.sh 0.1.0-rc.1
git push origin main v0.1.0-rc.1
```

This makes a GitHub prerelease with the APK and a CI download named `play-v0.1.0-rc.1` (Actions › the release run › Artifacts). Unzip it: it holds `app-play-release.aab` and `mapping.txt`.

Play's API can't take an app's very first bundle, so this one goes up by hand.

## 6. Closed testing

Test and release › Testing › **Closed testing** › the default track (`Closed testing - Alpha`, the workflow's `alpha` track) › Manage track:

1. **Countries/regions**: India (add more if anyone testing is abroad).
2. **Testers**: create an email list, add at least 12 testers' Google account emails (aim for 15, some always drop out). Save.
3. **Create new release**:
   - Signing: step 4 above.
   - App bundles: upload `app-play-release.aab`.
   - Release name: `v0.1.0-rc.1`.
   - Release notes: what testers should look at.
   - Next, then **Save and publish** (sends it for review, usually within a day or two for a closed track).
4. Upload the deobfuscation file: Test and release › App bundle explorer › the 10001 bundle › Downloads › upload `mapping.txt`. It turns crash reports back into readable code. The workflow does this by itself from the next tag on.
5. Copy the **opt-in link** from the Testers tab and send it to everyone on the list.

Every tester has to open the link, tap to join, install from Play and stay opted in. The 14 days start once 12 of them are in, and they need to be 14 days in a row. Uninstalling doesn't break it, leaving the test does. Push a couple of rc tags during the test, Play looks for signs the app was actually tested and improved.

## 7. Service account, so tags upload by themselves

1. console.cloud.google.com › pick or create a project.
2. APIs & Services › Library › **Google Play Android Developer API** › Enable.
3. IAM & Admin › Service accounts › **Create service account**, name `jportal-release`, no roles. Done.
4. Open it › Keys › Add key › Create new key › **JSON**. It downloads a file.
5. Play Console › **Users and permissions** › Invite new users › paste the service account's email (`...@....iam.gserviceaccount.com`).
   - Account permissions: none.
   - App permissions › Add app › JPortal, with: **Release to production, exclude devices, and use Play App Signing**, **Release apps to testing tracks**, **Manage testing tracks and edit tester lists**.
   - Invite user.
6. Store the key and delete the file:

   ```sh
   gh secret set PLAY_SERVICE_ACCOUNT_JSON -R codelif/jportal-android < ~/Downloads/<the-key>.json
   rm ~/Downloads/<the-key>.json
   ```

New permissions can take up to a day to start working. From then on, a test tag lands in closed testing and a plain tag in production review, with release notes and the mapping file.

## 8. Production access

After 14 days with 12 opted-in testers, Dashboard › **Apply for production**. It asks how you recruited testers, what feedback you got and what you changed, and whether the app is ready. Answer plainly. Review takes up to about 7 days.

## 9. First release, on Play and GitHub together

1. Publishing overview › turn on **Managed publishing**. Approved changes then wait for you instead of going live on their own.
2. Production › Countries/regions: India (or everywhere).
3. Tag and push:

   ```sh
   tools/release.sh 0.1.0
   git push origin main v0.1.0
   ```

   The GitHub release is public as soon as CI finishes. The bundle goes to Play's production review.
4. When Play approves, Publishing overview › **Publish changes**.

Or, to have both go out at once: do step 3 but keep the GitHub release a draft for the first time only (`gh release edit v0.1.0 --draft` right after CI makes it), then publish both together: `gh release edit v0.1.0 --draft=false` and Publish changes on Play.

## Every release after that

`tools/release.sh x.y.z`, push the tag, and CI takes care of GitHub and Play. With managed publishing on, press Publish changes once Play has reviewed it.
