# Profiles

Dwell profiles are named snapshots of the settings that change the dashboard's
behavior and presentation together. Manual switching is the first supported
profile mode; automatic time, app, or calendar triggers are intentionally out of
scope for now.

## Built-in profiles

Dwell ships with five stable built-in profile IDs:

- `work`
- `focus`
- `night`
- `presentation`
- `desk-clock`

Built-in profiles can be edited by changing profile-controlled settings while
the profile is active. Use Settings -> Profiles -> Reset defaults to restore a
built-in profile to its shipped defaults.

The Presentation profile is privacy conservative by default: it shows clock,
weather, and idle status while hiding private widgets such as todos, expenses,
and calendar details.

## Custom profiles

Settings -> Profiles supports creating a custom profile from the current
dashboard state, duplicating the active profile, renaming custom profiles, and
deleting custom profiles. Custom profile IDs are generated once and persisted in
`~/.screensaver/settings.json`.

Deleting the active custom profile keeps the currently visible dashboard
settings and returns the active profile marker to Current.

## Precedence

`SettingsModel` remains the persisted source of truth. `activeProfileId`
selects the current profile, and `profiles` stores built-in and custom profile
records.

Profile-controlled settings are copied into the top-level settings model when a
profile is applied. While a built-in or custom profile is active, edits to
profile-controlled settings update the active profile snapshot immediately.

Profile-controlled settings currently include:

- dashboard mode and mode variants
- Console widget border style
- enabled widgets
- Console widget layout and widget order
- idle timeout
- clock format, seconds, and date display
- quieter Lumen
- keypress and right-click dismissal behavior
- dashboard lock/edit behavior

These remain global and are not copied into profile snapshots:

- theme
- tray icon setting
- start-with-system registration
- backend URL and API key secret id
- weather API key secret id
- per-widget configs
- widget secret revisions
- welcome/first-run state

Widget/provider secrets remain in the OS secret store or fallback secret store.
Profiles only store secret references that already exist in global widget
configuration; they do not duplicate secret values.
