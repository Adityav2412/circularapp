# Circular Ring - Reverse Engineered App

Built from BLE sniffing of the official Circular app's HCI log.

## Protocol (NUS - Nordic UART Service)

**Service UUID:** 6e400001-b5a3-f393-e0a9-e50e24dcca9e
**RX (write to ring):** 6e400002-b5a3-f393-e0a9-e50e24dcca9e
**TX (notify from ring):** 6e400003-b5a3-f393-e0a9-e50e24dcca9e

## Known Commands

### App → Ring
| Command | Meaning |
|---------|---------|
| `ACK0001` | Handshake (send first) |
| `CAL<ISO8601>` | Sync time e.g. CAL2026-06-08T12:00:00Z |
| `FWV<version>` | Reply to firmware version request |
| `MOD1<model>` | Confirm model string |
| `NAME<mac>` | Confirm device name |
| `SNUCR<serial>` | Confirm serial number |
| `ALREOS` | Reply to alarm request |
| `MAREOS` | Reply to marathon request |
| `IBT0000-00-00T00:00:00Z` | Reply to IBT request |
| `FBL<n>` | Reply to FBL request |

### Ring → App
| Command | Meaning |
|---------|---------|
| `BAT<nnn>/<cc>` | Battery % / charging (01=charging) |
| `MOD0<model>` | Model info |
| `FWV` | Requesting firmware version |
| `SNU<serial>` | Serial number |
| `CAL<unix_ts>` | Calibration timestamp |
| `BSM<mode>` | Body sensor mode |
| `FBL<n>` | Firmware boot level |
| `ALR` | Alarm data request |
| `MAR` | Marathon data request |
| `IBT` | IBT data request |
| `NAM` | Name request |

## TODO (still unknown - use raw log screen to discover)
- Heart rate command/response format
- Step count
- Sleep staging
- SpO2

## How to build
1. Open in Android Studio
2. Sync gradle
3. Build APK → install on phone
4. Use raw log screen to capture unknown commands

## How to discover more commands
1. Connect ring via this app
2. Watch raw log screen for new messages from ring
3. Try commands in the "Send raw command" box
4. Share logs here to decode further
