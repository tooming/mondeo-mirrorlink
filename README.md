# mondeo-mirrorlink

Home-grown MirrorLink server for a Ford Mondeo Mk5's SYNC 2 head unit, built
because EasyConnected (the usual hobbyist tool for this) is closed-source and
of unclear provenance ("some Chinese stuff").

Built via GitHub Actions only - no local Android Studio/Gradle needed. Every
push builds a debug APK, downloadable from the Actions run's artifacts.

## Protocol background

MirrorLink is an open, ETSI-published spec (TS 103 544 series, CCC-authored).
Key facts that shaped this design:

- Terminology is backwards from intuition: the **phone is the "Server"**, the
  **car is the "Client"**.
- Transport is **USB CDC/NCM**, not RNDIS. The car (as USB host) sends a
  vendor control request (`bmRequestType=0x40, bRequest=0xF0`) asking the
  phone to switch into a MirrorLink-capable USB personality. An unrooted
  stock Android phone will auto-STALL this (no OS hook for it) - the spec
  explicitly anticipates that case. What actually matters for a client to
  treat us as present: the exposed USB device class is CDC/NCM (or whatever
  the phone's plain "USB tethering" toggle produces - untested whether stock
  Android exposes NCM or RNDIS) and a MirrorLink device is advertised over
  UPnP - see TS 103 544-1 clause 4.2.
- Discovery uses SSDP/UPnP: device type
  `urn:schemas-upnp-org:device:TmServerDevice:1`, advertised via
  `NOTIFY ssdp:alive` on 239.255.255.250:1900, with a device description XML
  served over HTTP at the advertised `LOCATION`. See TS 103 544-12.
- The spec requires the device description XML to be **XML-signed**, verified
  by the client against a certificate obtained through the Device Attestation
  Protocol (TS 103 544-4) - which requires CCC membership we don't have. Real
  EasyConnected users report it working without any such certificate, which
  suggests SYNC 2 doesn't actually enforce this at runtime - but that's
  unverified until we test against the real car.
- Video/input (VNC-based display + touch passthrough, TS 103 544-2) is not
  implemented yet - out of scope for phase 1.

## Phase 1 goal

Find out, cheaply, whether the Mondeo's SYNC 2 unit reacts to us *at all*
before investing in the (much larger) video/input pipeline. The app:

1. Waits for a `TRANSPORT_USB` network (i.e. USB tethering enabled while
   plugged into the car).
2. Sends `NOTIFY ssdp:alive` bursts for the MirrorLink device type, and
   answers `M-SEARCH` queries.
3. Serves an **unsigned** device description XML over HTTP.
4. Logs every step (SSDP sent/received, HTTP requests) directly in its own
   scrolling UI, since the phone's USB port will be occupied by the car and
   `adb logcat` won't be available.

## Reading the result

- Nothing in the log ever shows an incoming HTTP request → car never even
  fetched our description, discovery/USB-personality is the blocker (likely
  CDC/NCM vs RNDIS mismatch).
- HTTP request logged, but SYNC 2 does nothing further (no MirrorLink icon,
  no session) → likely the missing XML signature is being enforced after all.
- SYNC 2 shows a MirrorLink icon / attempts a session → discovery works,
  proceed to phase 2 (VNC display + input injection).

## Testing

1. Push to `main`, wait for the Actions run, download `mondeo-mirrorlink-debug.apk`
   from the run's artifacts.
2. Sideload it onto an Android phone (`allow installs from this source` for
   whatever browser/files app you use to open the APK - no root required).
3. Plug the phone into the Mondeo's USB-A port.
4. Open the app, tap **Open USB tethering settings**, enable USB tethering.
5. Tap **Start MirrorLink announce**, watch the log, watch SYNC 2's screen.
