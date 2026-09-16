# EiryMouse

EiryMouse is an Android application that turns a compatible Android device into a Bluetooth HID mouse and auxiliary input device for a PC.

The application is designed to operate without installing dedicated receiver software on the PC. Mouse, keyboard, modifier-key, and related input events are sent through the Android Bluetooth HID Device profile.

## Main Features

- Bluetooth HID mouse operation
- TouchPad with one-finger and two-finger gestures
- A / B / C virtual stick controls
- Mouse buttons and scrolling
- Modifier keys: Shift, Ctrl, Alt, and Windows
- Android IME based text-entry interface
- Han/Zen, Muhenkan, Henkan, and Enter controls
- Floating control functions
- Portrait and landscape layouts
- Config and Help screens

## Requirements

- Android device with Bluetooth HID Device profile support
- Bluetooth-enabled host PC
- Android Studio and an Android SDK for building the project

Bluetooth HID Device support depends on the Android device and its ROM/firmware implementation. A device having Bluetooth does not necessarily mean that it can operate as a Bluetooth HID Device.

## Tested Devices

| Device | Current result |
| --- | --- |
| Samsung Galaxy S10 | Working; current reference device |
| OPPO Reno7 A | Connection has worked with an earlier EiryMouse build; behavior may vary by build |
| TORQUE G06 (KYG03) | Bluetooth HID connection currently unavailable |
| TORQUE G04 | Bluetooth HID connection currently unavailable |

The Galaxy S10 is currently used as the normal reference device for development and testing.

## Project Structure

- `app/` - Android application source code and resources
- `gradle/` - Gradle Wrapper and dependency configuration
- `build.gradle.kts` - Root Gradle build configuration
- `settings.gradle.kts` - Gradle project settings
- `gradle.properties` - Gradle properties
- `gradlew` / `gradlew.bat` - Gradle Wrapper launchers

Build outputs, local Android SDK paths, Git internal data, temporary logs, and other machine-specific files are excluded from the distribution project.

## Opening the Project in Android Studio

1. Clone or download this repository.
2. Open the `EiryMouse` project directory in Android Studio.
3. Allow Gradle Sync to complete.
4. Select the desired build variant.
5. Build and install the application on a compatible Android device.

`local.properties` is intentionally not included. Android Studio generates or updates it according to the Android SDK location on each development machine.

## Bluetooth Connection Notes

The PC must support Bluetooth, and the Android device must provide the Bluetooth HID Device profile required for EiryMouse to register itself as an input device.

Bluetooth compatibility differs between Android manufacturers and ROM implementations. Connection failure on a particular Android device does not necessarily indicate a general application failure.

## Current Development Status

EiryMouse is functional on the Galaxy S10 and is being prepared as a source-code release. Core mouse, touch, stick, keyboard-assistance, configuration, and UI functions have been tested on the current reference device.

Device-specific Bluetooth HID compatibility remains a known limitation. In particular, TORQUE G04 and G06 compatibility is currently deferred rather than being treated as a release blocker.

## Package Name

The Android package name is currently:

```text
com.example.diazymouse
```

This name is inherited from an earlier stage of the project and has intentionally not been changed in the current release in order to avoid unnecessary compatibility and regression risks.

## License

This project is licensed under the MIT License.
See the `LICENSE` file for details.
