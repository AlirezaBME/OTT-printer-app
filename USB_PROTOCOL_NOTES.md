# USB Protocol Notes — Canon Laser Printers

## 1. USB Device Identifiers

| Device | Vendor ID | Product ID | Class / Subclass / Protocol |
|---|---|---|---|
| **Canon LBP6030 / LBP6040 / LBP6018L** | `0x04A9` (1193) | `0x2795` (10133) | `0x07` / `0x01` / `0x02` |
| **Canon LBP6000 / LBP6018** | `0x04A9` (1193) | `0x271A` (10010) | `0x07` / `0x01` / `0x02` |
| **Canon LBP6020 / LBP6020B** | `0x04A9` (1193) | `0x275F` (10079) | `0x07` / `0x01` / `0x02` |
| **Canon LBP6200** | `0x04A9` (1193) | `0x273B` (10043) | `0x07` / `0x01` / `0x02` |
| **Canon LBP6230** | `0x04A9` (1193) | `0x2796` (10134) | `0x07` / `0x01` / `0x02` |

---

## 2. Standard USB Printer Class 1.1 Control Requests

All requests are issued to the printer interface (recipient = Interface, type = Class).

### A. GET_DEVICE_ID
- **`bmRequestType`**: `0xA1` (10100001b: Device-to-Host, Class, Interface)
- **`bRequest`**: `0x00` (`GET_DEVICE_ID`)
- **`wValue`**: Configuration index (typically `0`)
- **`wIndex`**: Interface index (typically `0`)
- **`wLength`**: Buffer length (typically `1024` bytes)
- **Return format:**
  - Bytes 0..1: 16-bit big-endian length of descriptor string `N` including length bytes.
  - Bytes 2..N-1: ASCII string conforming to IEEE-1284 Device ID syntax (`KEY:VALUE;KEY:VALUE;...`).

### B. GET_PORT_STATUS
- **`bmRequestType`**: `0xA1`
- **`bRequest`**: `0x01` (`GET_PORT_STATUS`)
- **`wValue`**: `0`
- **`wIndex`**: Interface index
- **`wLength`**: `1`
- **Return format:** Single byte bitmask:
  - **Bit 5 (`0x20`)**: Paper Empty (`1` = Paper empty / out, `0` = Paper present)
  - **Bit 4 (`0x10`)**: Selected (`1` = Printer selected / on-line, `0` = Off-line)
  - **Bit 3 (`0x08`)**: Not Error (`1` = Normal state, `0` = Hardware error condition)
  - *Standard ready state:* `0x18` (Selected = 1, Not Error = 1, Paper Empty = 0).

### C. SOFT_RESET
- **`bmRequestType`**: `0x21` (Host-to-Device, Class, Interface)
- **`bRequest`**: `0x02` (`SOFT_RESET`)
- **`wValue`**: `0`
- **`wIndex`**: Interface index
- **`wLength`**: `0`
- Resets bulk endpoints without renegotiating USB descriptors.

---

## 3. Endpoints & Transfer Policies

- **Bulk OUT Endpoint (typically `0x01` or `0x02`):**
  - Used for streaming binary print jobs (CARPS2 or UFRII LT).
  - Max packet size: `64` bytes (USB 2.0 Full-Speed) or `512` bytes (USB 2.0 High-Speed).
  - Safe chunk size: **`16384` bytes (16 KB)**.
  - Transfer timeout: **15,000 ms**.
  - Always verify that the returned byte count from `bulkTransfer()` equals the chunk length requested.

- **Bulk IN Endpoint (typically `0x81` or `0x82`):**
  - Used for reading printer hardware responses and status query replies.
  - Timeout: **5,000 ms**.
