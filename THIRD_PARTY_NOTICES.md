# Third-Party Notices

EZBox bundles third-party code. The corresponding license texts are shipped
inside the APK under `assets/licenses/` and are reachable in-app from
**Menu → About → Licenses**.

## noVNC

EZBox bundles a copy of the [noVNC](https://github.com/novnc/noVNC) core
library (plus the minimal example UI) in `app/src/main/assets/novnc/`, used by
`NoVncActivity` as an alternative high-performance VNC client.

- Copyright (C) The noVNC authors
- Core library (`core/**/*.js`, `app/*.js`): **MPL 2.0**
- HTML/CSS assets: 2-Clause BSD
- Full notice: `app/src/main/assets/licenses/noVNC-LICENSE.txt`
- License text: `app/src/main/assets/licenses/MPL-2.0.txt`

The noVNC core files are used unmodified; the file headers in each source file
carry the original copyright and license notice, as required by MPL 2.0 §3.1.

## pako

`app/src/main/assets/novnc/vendor/pako/` is redistributed by noVNC.

- Copyright (C) 2014-2016 Vitaly Puzrin
- License: **MIT**
- Full text: `app/src/main/assets/licenses/pako-MIT.txt`

## RFB protocol

`app/src/main/java/com/mrzgaming/ezbox/RfbClient.kt` is an independent
implementation of the RFB (Remote Framebuffer) protocol written for this
project. The RFB protocol itself is a published specification, not licensed
code; no third-party VNC client source was copied into it.

"VNCD" and the VNC protocol are associated with their respective owners. EZBox
is an independent project and is not affiliated with or endorsed by AT&T,
Tridia, or the noVNC project.
