# Anchor

An Android app that blocks distracting apps and websites, and puts a Bible verse on screen
instead.

- **Hard blocks** — apps and sites you never want open
- **Daily limits** — five minutes on Facebook, then it shuts for the day
- **Site blocking without app blocking** — Chrome keeps working, reddit.com doesn't
- **Adult filter** — built-in seed list plus a one-tap import of a maintained public blocklist
- **PIN on everything that loosens** — removing a block, stretching a limit, or switching strict
  mode off all ask for it. Adding a block never does
- **Strict mode** — PIN plus a cooldown, so turning blocking off stops being a snap decision
- **Uninstall protection** — getting rid of Anchor takes the PIN too
- **The whole Bible offline** — all 66 books, 31,098 verses, with search. No connection needed

Everything stays on your phone. No account, no sync, no ads, nothing uploaded.

Scripture is the World English Bible, which is public domain, so there's nothing to license.

---

## Just want to install it?

Grab the APK from the [Releases](../../releases) page. You don't need any of the build
instructions below.

Android will warn you the app is from an unknown source, or Play Protect will say it blocked
it. That's Google flagging anything that didn't come from the Play Store, not a problem with
the app — tap **More details**, then **Install anyway**.

On first run Anchor will ask for the accessibility permission. The warning Android shows here
sounds alarming: it says the app can view and control your screen, and that's technically
true, because reading which app you're in and what's in the browser address bar is the only
way any blocker can work. None of it goes anywhere. If you'd rather check that for yourself
than take my word for it, that's what the source in this repo is for.

On the same screen, leave **Anchor blocking shortcut** switched off. It puts a floating button
on screen that disables blocking in one tap, which rather defeats the purpose.

Then add your blocks, and **get someone you trust to set the PIN and keep it**. A PIN you know
yourself is a speed bump you'll drive over at two in the morning.

---

## Building it yourself

You need Android Studio. It's free.

### 1. Install Android Studio

Download from https://developer.android.com/studio. Accept the default install, which pulls
down the SDK and everything else. First run takes a while.

### 2. Open the project

`File > Open`, pick the `Anchor` folder, and wait for Gradle to finish syncing. Android Studio
will probably offer to upgrade the Android Gradle Plugin or Kotlin version — accept it, the
project is plain enough that upgrades go through cleanly.

If it complains about a missing SDK, click the link in the error and it'll install it.

### 3. Turn on developer mode on your phone

Settings > About phone > tap **Build number** seven times. Then Settings > System >
Developer options > enable **USB debugging**.

### 4. Plug in and run

Connect the phone by USB, accept the "allow debugging" prompt, pick your phone from the
device dropdown at the top of Android Studio, and hit the green ▶ button.

That's it — it installs and launches.

### Building a standalone APK instead

`Build > Build Bundle(s) / APK(s) > Build APK(s)`. The file lands in
`app/build/outputs/apk/debug/`. Copy it to the phone and open it to install (you'll need to
allow installs from unknown sources). Useful if you want to give it to someone else without
plugging their phone into your computer.

---

## First run

1. Open Anchor. It'll tell you blocking is off.
2. Tap **Open accessibility settings**, find Anchor under *Installed apps* (or *Downloaded
   apps* on some phones), and switch it on. Android will show a scary-sounding warning — that's
   the standard prompt every blocker gets, because reading what's on screen is exactly how they
   all work.
3. Add your blocks. Apps come from a picker; sites you type in as `reddit.com`.
4. Set a PIN, then arm strict mode.

The Bible unpacks itself on first launch, which takes a second or two.

---

## How it works

An `AccessibilityService` gets told by Android whenever a new window comes to the front. Anchor
checks the package name against your app rules, and if the app is a browser it reads the
address bar and checks the hostname against your site rules. A match launches the block screen
over the top.

Daily limits work by clocking how long a matching app or site stays in front, saved per day and
cleared overnight.

### What strict mode actually does

While it's armed, the rules are frozen. Editing anything means requesting a change and waiting
out the cooldown you chose — fifteen minutes to twenty-four hours. Anchor also bounces you out
of the accessibility settings page during that wait, so the obvious route around it needs the
same wait as everything else.

### The accessibility shortcut is a kill switch

Android lets you attach any accessibility service to the floating button or the
volume-key shortcut. If you turn that on for Anchor, one tap disables blocking
instantly - no PIN, no cooldown. It's a system feature applied to every
accessibility service, so the app can't intercept or disable it.

**Leave "Anchor blocking shortcut" off.** The main toggle is the one you want.

### Uninstall protection

Settings has a toggle that registers Anchor as a device administrator. While active,
Android greys out Uninstall — long-pressing the icon or hitting Uninstall in Settings
does nothing. Anchor requests no other administrator powers; the policy file is
deliberately empty, so it can't lock your screen, change your password, or wipe the
device.

Turning it off inside Anchor costs the PIN. Turning it off from system Settings is
blocked by the strict-mode guard for the length of your cooldown.

### What it can't do

Be honest with yourself about this bit. It's your phone and you're an admin on it, so:

- Booting into **Safe Mode** disables all accessibility services, and lets you deactivate
  the device admin and uninstall
- **adb** from a computer can uninstall it regardless
- A **factory reset** obviously wipes everything
- A browser Anchor doesn't know about won't have its address bar read

The address-bar reading covers Chrome, Firefox, Brave, Edge, Opera, Samsung Internet,
DuckDuckGo, Vivaldi, Kiwi, UC and Yandex. Anything else, block the browser itself.

The cooldown is designed for the failure mode that actually happens — the two-in-the-morning
impulse — not for a determined half hour with a laptop. **If you want blocking you genuinely
can't undo, have someone else set the PIN and keep it.** That's the only version of this that
holds against yourself, and no app can change that.


---

## Building without a computer

You don't need Android Studio. The repo includes a GitHub Actions workflow
(`.github/workflows/build.yml`) that builds the APK on GitHub's machines - free and
unmetered on public repos - so the whole loop can run from a phone browser.

**One-off setup (about ten minutes, phone is fine):**

1. Make a GitHub account and create a new **public** repo called `anchor`.
2. Upload this project to it. On a phone the easiest route is the GitHub web editor -
   or push it once from any computer you can borrow, then never touch that computer again.
3. Go to the **Actions** tab. The build kicks off by itself.

**From then on:**

- Every push rebuilds automatically. Open the Actions tab, tap the latest run, and the APK
  is at the bottom under *Artifacts*.
- Want to build without changing anything? Actions tab, pick **Build APK**, tap
  **Run workflow**.
- Tag a commit `v1.0` and it publishes a Release with the APK attached on a permanent
  link - that's the link you'd share with people.

Editing code on a phone works through github.com: open any file, tap the pencil, commit.
Rough for big changes, fine for tweaking a verse or adding a browser.

The first CI run may fail on a version mismatch - the log says exactly which line. That's
normal for a first build on someone else's machine.

### About the signing key

The workflow builds a *debug* APK, which installs fine and is what most small open-source
Android projects ship. The catch is that debug builds are signed with a throwaway key, so
you can't later swap to a properly signed build without users uninstalling first. If you
think you'll ever want that, generate a keystore early, add it to the repo secrets, and
switch the workflow to `assembleRelease`.

---

## If you share it

Nothing here needs to cost money. A public GitHub repo is free, Actions minutes on public
repos are free, and a tip-jar link (Ko-fi, Buy Me a Coffee) costs nothing to set up - they
take a cut of donations rather than charging you.

Deliberately *not* going to the Play Store avoids the parts that get expensive in time:
the $25 account, the twelve-tester closed test, the accessibility permission review, and
the annual target-API treadmill.

---

## Project layout

```
app/src/main/
├── assets/
│   ├── bible.db.gz          all 66 books, gzipped SQLite with FTS (~4.8 MB)
│   └── verses.json          45 curated verses for the block screen
├── java/com/dan/anchor/
│   ├── MainActivity.kt      bottom nav shell
│   ├── data/
│   │   ├── Prefs.kt         rules, PIN, strict mode, daily usage
│   │   ├── BibleDb.kt       unpacks and queries the Bible
│   │   └── Verses.kt        loads and rotates the block verses
│   ├── block/
│   │   ├── BlockerService.kt        the accessibility service
│   │   ├── BlockRules.kt            matching logic and browser ids
│   │   └── BlockOverlayActivity.kt  the block screen
│   └── ui/                  Compose screens and theme
```

## Things worth changing

- **More or different verses** — edit `assets/verses.json`. Each entry needs `reference`,
  `book`, `chapter`, `verseStart`, `verseEnd`, `theme` and `text`. Book names must match the
  database (`Psalms`, not `Psalm`).
- **A different translation** — swap `bible.db.gz` for another SQLite file with the same
  `books` and `verses` tables. Mind the copyright: most modern translations aren't free to
  redistribute. WEB, KJV and ASV are safe.
- **More browsers** — add the package name and its address-bar view id to `BROWSERS` in
  `BlockRules.kt`. Use Android Studio's Layout Inspector to find the id.
- **A longer pause** — Settings offers 0/5/8/20/60 seconds. Change the options in
  `SettingsScreen.kt` if you want longer.
- **Daily limits** — quick chips are 5/10/15/30/60 minutes, and there's a free-text field next
  to them, so any number of minutes works.
