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
- **Optional Uninstall protection** — getting rid of Anchor takes the PIN too
- **Schedules** — block only after 10pm, or only on weekdays, or around the clock
- **The whole Bible offline** — all 66 books, 31,098 verses, with search. No connection needed
- **Unlock gates** — Reddit stays shut until you've done ten minutes in your language app
- **Emergency extensions** — a couple a week, no PIN needed, for when time runs out mid-conversation on instagram, for example.
- **Time warnings** — a quiet heads-up before an allowance ends
- **Tap a verse reference** on the block screen to read the passage it came from

Everything stays on your phone. No account, no sync, no ads, nothing uploaded.

Scripture is the World English Bible, which is public domain, so there's nothing to license.

Android only. This won't work on an iPhone or iPad - sorry.

---

## Read this before you install

**Installing Anchor is going to feel alarming.** Your phone will try to talk you out of it
several times, using some genuinely frightening language. None of those warnings mean
something is wrong with the app. They mean the app didn't come from the Google Play Store.

I want to be straight with you about why, and about exactly what you're going to see, so
that none of it takes you by surprise.

### Why it isn't on the Play Store

Putting an app on the Play Store costs money, requires a verified developer identity, and
takes weeks of review. Anchor is free, has no ads, collects nothing, and makes me nothing.
I built it for myself, and I'm sharing it because it might help other Christians — and
honestly anyone trying to get their screen time under control.

That decision has a cost, and you're about to pay it in warning screens.

### The other reason it looks scary

Anchor genuinely does ask for powerful permissions. It has to. To block an app or a
website, something has to be able to see which app is open and what's in your browser's
address bar. There is no gentler way to do that on Android — every blocker works this way.

Android can't tell the difference between an app that reads your screen to block Reddit and
one that reads your screen to steal your banking password. So it warns you as though it's
the second one. That's the right call by Android, and it's why the warnings exist.

**But please don't just take my word for any of this.** "Trust me bro" from a stranger on
the internet should mean nothing to you, especially about an app asking for permissions
this powerful. If I'm asking you to hand over that much access, the burden is on me to let
you check, not on you to take it on faith.

So check it:

- **The entire source code is in this repository.** Nothing is hidden or compiled away. If
  you know someone who codes, hand them the link and ask them to look for anything that
  sends data anywhere. There's one network request in the whole app, and it downloads a
  public list of adult sites to block.
- **Look up how app blockers work on Android.** Search for how Cold Turkey, AppBlock,
  BlockSite or any of the others do it. You'll find they all use the same accessibility
  permission, for the same reason, because there isn't another way.
- **Search the warnings themselves.** "Unsafe app blocked", "restricted setting",
  "unknown sources" — see what people say about what those actually mean.

I'd genuinely rather you spent twenty minutes checking and then installed it with your eyes
open, than installed it nervously because I sounded convincing. And I'd rather you didn't
install it at all than get frightened off something I really do think can help.

### What you'll actually see, step by step

You'll hit roughly five or six of these. Every one is normal.

**1. Downloading the file**
Your browser will say something like *"This type of file can harm your device. Do you want
to keep anchor-1.1.apk anyway?"*
→ Tap **Download anyway** or **OK**.

**2. Being allowed to install at all**
When you open the file, you'll likely get *"For your security, your phone is not allowed to
install unknown apps from this source."*
→ Tap **Settings**, turn on **Allow from this source**, then press back.

**3. Play Protect**
Google will scan it and may say *"Unsafe app blocked"* or *"App scan recommended"*.
→ Tap **More details**, then **Install anyway**. If it offers to send the app to Google for
scanning, you can say yes or no — either is fine.

**4. Samsung phones only: Auto Blocker**
Newer Samsungs ship with Auto Blocker on, which refuses sideloaded apps outright.
→ Settings → Security and privacy → Auto Blocker → turn off **Block app installs from
unauthorised sources**. Turn it back on afterwards if you like; Anchor keeps working.

**5. Unlocking the accessibility setting**
This one catches everybody. On Android 13 and newer, apps installed outside the Play Store
are blocked from accessibility by default, and the switch will simply be greyed out with a
message about a **restricted setting**.
→ Settings → Apps → Anchor → tap the **⋮** menu in the top corner → **Allow restricted
settings**.

**6. The accessibility permission itself**
Settings → Accessibility → Installed apps → Anchor → turn on. Android will warn that Anchor
can **view and control your screen**.

That warning is accurate, and Anchor will show you its own plain-English explanation first.
It reads which app is in front and what's in your browser address bar. It does not read
your messages or anything you type, it keeps no history of where you've been, and nothing
leaves your phone.

**Also on that screen:** leave **Anchor blocking shortcut** switched OFF. It adds a floating
button that turns blocking off in one tap, which rather defeats the point.

**7. Optional: uninstall protection**
If you turn this on later, you'll get one more approval screen about a **device
administrator**. This is what stops you deleting the app in a weak moment.

### One thing I'm still checking

A friend reported that after installing Anchor, his tap-and-pay stopped working and Google
Wallet asked him to add his cards again. **I haven't confirmed Anchor caused this**, and it
may well be coincidence — Wallet removes cards for lots of reasons.

But the honest answer is I don't know yet, and I'd rather tell you now than have it surprise
you. If it matters to you, **leave uninstall protection off** until I've got to the bottom of
it. That's the feature most likely to be involved, and everything else works fine without it.

If it does happen to you, re-adding the card in Google Wallet fixes it — and please open an
issue here, because it would help me work out what's going on.

### Then you're in

Add the apps and sites you want to stay off. You can block them outright, give them a few
minutes a day, or only block them at certain hours.

Adding a block never asks for anything. Removing one asks for your PIN.

And the thing that actually makes this work: **get someone you trust to set the PIN and keep
it.** A PIN you chose yourself is a speed bump you'll drive over at two in the morning.

Download the APK from the [Releases](../../releases) page.

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
