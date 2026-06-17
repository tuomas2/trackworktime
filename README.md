# Sykerö Track Work Time

> **Fork notice:** This is a fork of [mathisdt/trackworktime](https://codeberg.org/mathisdt/trackworktime),
> maintained by Sykerö Software. It adds first-class Pebble smartwatch support
> (PebbleKit Android 2). All credit for the original app goes to the upstream author.

Track your work time the easy way — and control it right from your Pebble watch.

## Pebble support (the highlight of this fork)

- Start and stop tracking, and pick a task, right from your wrist
- See your status — current task, time worked, time left today — on the
  [Sykerö TimeStyle](https://github.com/Sykero-Software/TimeStylePebble) watchface
- Companion [Track Work Time](https://github.com/Sykero-Software/PebbleTrackWorkTime)
  watchapp + the Sykerö TimeStyle watchface, both on the Pebble appstore

## Key features

- Manual or automatic clock-in/out — by location geo-fencing (no GPS, battery-friendly)
  or by your workplace Wi-Fi network name (the network just has to be visible)
- Categorize each interval by client/task plus free text; edit the task list to suit you
- Flexible-time account: always see how much you've worked, and how much is left for
  today or this week
- Quick clock-in/out: home-screen widget, launcher shortcuts, or a Quick Settings tile
- Edit planned working time with a tap on any date
- Automatic daily backups to a folder you choose
- Reports: raw events for export, or year/month/week summaries (optional decimal-hours column)
- Automation-friendly: trigger tracking from Tasker or Automate via broadcast intents
  (`org.zephyrsoft.trackworktime.ClockIn` / `ClockOut` / `StatusRequest`), and listen for
  `…event.Created` / `Updated` / `Deleted` broadcasts

## Privacy

**This app won't use your personal data for anything you don't want.** No ads, no tracking.
If the app crashes it can offer to email a crash report to the developer — only if you agree,
every time. Tracked times and places are never included in the report.

## Feedback

[File an issue](https://github.com/Sykero-Software/trackworktime/issues) or
[email us](mailto:trackworktime@sykero.fi).

## License

GPL v3. If you submit changes, they are automatically licensed under GPL v3 as well.
