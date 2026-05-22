# Widget Author Guide

Screen Saver App supports two widget tiers, and the fastest way to start is to **copy one of the working samples** and modify it:

- **`samples/sample-kotlin-widget`** — a Kotlin JAR widget (Tier 1). Compose UI, full JVM access.
- **`samples/sample-declarative-widget`** — a YAML-described widget (Tier 2). No code; the host renders one of the built-in templates against data from a file, HTTP endpoint, or command.

Both are real, buildable modules in this repo. Use them as your starting point.

## Where widgets live at runtime

The host discovers widgets from:

```text
~/.screensaver/widgets/
```

(Windows: `%USERPROFILE%\.screensaver\widgets\`.)

A widget is either:

- A `.jar` file dropped directly into that folder (Tier 1), **or**
- A subfolder containing `widget.yaml` plus any data / script files it references (Tier 2).

After dropping a widget in, reload from the tray menu (**Tray → Reload widgets**) or from **Settings → Widgets → Reload**. You don't need to restart the daemon.

## Hello world — Kotlin JAR widget (Tier 1)

The fastest path is to copy `samples/sample-kotlin-widget` and rename it.

1. **Copy the sample** to a new module, e.g. `samples/my-widget/`. Keep the same layout:

   ```text
   my-widget/
     build.gradle.kts
     src/jvmMain/kotlin/.../MyWidgetFactory.kt
     src/jvmMain/resources/META-INF/services/com.droidslife.screensaver.widget.api.WidgetFactory
   ```

2. **Implement `WidgetFactory`.** The sample's `RandomQuoteWidgetFactory.kt` is ~80 lines and covers the whole surface. The essentials:

   ```kotlin
   class MyWidgetFactory : WidgetFactory {
       override val id: String = "com.example.mywidget"
       override val displayName: String = "My Widget"
       override val description: String = "Says hello."
       override val category: WidgetCategory = WidgetCategory.INFORMATION

       // Console-grid bounds. Drop or accept defaults (4×2 / clamped 2×1…12×6).
       override val preferredSize = WidgetSize(
           minCols = 3, minRows = 1,
           defaultCols = 4, defaultRows = 2,
           maxCols = 8, maxRows = 4,
       )

       override fun create(config: WidgetConfig, scope: WidgetScope): Widget = MyWidget
   }

   private object MyWidget : Widget {
       // summary() feeds Cinematic-drawer chips and Ambient minimal lines.
       // Cheap, no IO — read in-memory snapshots only.
       override fun summary(): WidgetSummary = WidgetSummary(
           primaryValue = "Hello",
           primaryLabel = "My Widget",
       )

       @Composable
       override fun Content(modifier: Modifier) {
           Text("Hello, dashboard.", modifier = modifier)
       }
   }
   ```

   The host's `Widget.Render(target, scope, modifier)` default forwards every target to `Content(modifier)`. Override `Render` only when you want a different layout per target — for most widgets the defaults plus a good `summary()` are enough.

3. **Register the factory** with Java `ServiceLoader` by listing it in:

   ```text
   src/jvmMain/resources/META-INF/services/com.droidslife.screensaver.widget.api.WidgetFactory
   ```

   The file contains one line: the fully-qualified factory class name (e.g. `com.example.MyWidgetFactory`).

4. **Build the JAR:**

   ```sh
   ./gradlew :samples:my-widget:jvmJar
   ```

   The output JAR is written to `samples/my-widget/build/libs/`.

5. **Install it locally** by dropping the JAR into `~/.screensaver/widgets/`:

   ```sh
   cp samples/my-widget/build/libs/my-widget-jvm-*.jar ~/.screensaver/widgets/
   ```

6. **Reload** from the tray menu (or Settings → Widgets) and enable your widget. It should appear on the dashboard the next time it triggers.

Notes:

- The host depends on the `:widget-api` module (`com.droidslife.screensaver:widget-api`). The sample imports it as a project dependency; out-of-tree authors will need a published `widget-api` JAR. Until that's published to a Maven repo, the simplest path is to keep your widget inside this repo's `samples/` folder.
- v1 does not isolate widget classloaders, so only run code you trust.
- Use `WidgetScope` for coroutines / lifecycle. Use `WidgetConfig` to read settings declared by your factory.

## Hello world — Declarative widget (Tier 2)

Copy `samples/sample-declarative-widget` to a new folder, then edit `widget.yaml`. A minimal example:

```yaml
id: com.example.hello
title: Hello
description: A minimal declarative widget.
category: information
apiVersion: 1
template: text
preferredSpan: 1
refresh: 60s
source:
  type: file
  path: hello.txt
bindings:
  text: $.message
```

Then drop the folder into `~/.screensaver/widgets/`:

```sh
cp -r samples/my-declarative-widget ~/.screensaver/widgets/
```

Reload from the tray menu. The host turns the manifest into a `DeclarativeWidgetFactory`, so Kotlin and YAML widgets share the same registry, settings, storage, and lifecycle.

### Source types

- `file` — read a local file (JSON / text).
- `http` — fetch a JSON endpoint.
- `command` — run a process and parse its stdout.

### Templates

- `text`
- `kv` — key/value pairs.
- `list`
- `grid`
- `chart`
- `image`

### Secrets

Secrets declared with `type: secret` are stored outside `settings.json` (Windows Credential Manager / Linux libsecret, or obfuscated fallback). Widget config receives a secret id; call `WidgetConfig.secret(key)` to resolve the real value at runtime.

## Testing a widget locally

The easiest path is the one above: build, copy into `~/.screensaver/widgets/`, reload from the tray. For a faster inner loop while developing in this repo:

1. Add your sample module to `settings.gradle.kts`.
2. Build it with `./gradlew :samples:my-widget:jvmJar`.
3. Symlink the built JAR into `~/.screensaver/widgets/` so each rebuild is picked up by **Reload** without a copy step:

   ```sh
   ln -s "$PWD/samples/my-widget/build/libs/my-widget-jvm-1.0.0.jar" ~/.screensaver/widgets/
   ```

4. Use **Tray → Reload widgets** after each rebuild.

## Reference: the `widget-api` surface

The public API lives in the `:widget-api` module:

- `WidgetFactory` — the entry point. Implement and register via `ServiceLoader`.
- `Widget` — has a single `@Composable Content(Modifier)` method.
- `WidgetConfig` — typed access to settings and secrets.
- `WidgetScope` — coroutine scope / lifecycle hooks for the widget instance.
- `WidgetCategory` — `INFORMATION`, etc. (drives grouping in Settings).

See `samples/sample-kotlin-widget/src/jvmMain/kotlin/.../RandomQuoteWidgetFactory.kt` for a complete, working reference.
