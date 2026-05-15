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

# Run the animation every N global-region ticks. Keep this at 1 for the smoothest sun/moon motion.
tick-period: 1

# Match vanilla sleep behavior by clearing rain/thunder once the animated skip finishes.
clear-weather-on-finish: true

# If true, only worlds with Environment.NORMAL are animated.
normal-worlds-only: true
```
