# DaylightCycle

DaylightCycle is a Folia-supported Minecraft plugin that smooths the normal night-to-day sleep skip into a configurable sun/moon transition.

## Features

- Keeps the transition speed configurable in `config.yml`.

## Configuration

```yaml
# How long the custom night-to-day sleep transition should last, in real seconds.
# Increase this to make the sun/moon move slower while players sleep.
# Decrease this to make the sun/moon move faster.
# Examples:
#   transition-seconds: 4.0   # faster
#   transition-seconds: 16.0  # slower
transition-seconds: 8.0

# How often the sun/moon animation updates, measured in server ticks.
# Keep this at 1 for the smoothest movement.
# Higher values update less often and can look choppier, so only change this if you know you need to.
tick-period: 1

# Vanilla sleeping clears rain and thunder after the night skip.
# Since DaylightCycle replaces the instant skip with an animation, this keeps that vanilla weather behavior.
clear-weather-on-finish: true
```
