# Widget Author Guide

Screen Saver App supports two widget tiers.

## Kotlin JAR Widgets

Implement `com.droidslife.screensaver.widget.api.WidgetFactory` from the `:widget-api` module and register it with Java `ServiceLoader`:

```text
META-INF/services/com.droidslife.screensaver.widget.api.WidgetFactory
```

Copy the built JAR to:

```text
~/.screensaver/widgets/
```

The host loads compatible factories on startup and from the Settings -> Widgets reload action. v1 does not isolate widget classloaders, so only run code you trust.

## Declarative Widgets

Create a folder in `~/.screensaver/widgets/` containing `widget.yaml`. The host turns the manifest into a `DeclarativeWidgetFactory`, so Kotlin and YAML widgets share the same registry, settings, storage, and lifecycle path.

Supported source types:

- `file`
- `http`
- `command`

Supported templates:

- `text`
- `kv`
- `list`
- `grid`
- `chart`
- `image`

Secrets declared with `type: secret` are stored outside settings JSON. Widget config receives a secret id, and `WidgetConfig.secret(key)` resolves the real value at runtime.
