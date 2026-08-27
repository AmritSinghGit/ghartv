# GharTV change history

## 0.5.1-tv-test

First release candidate intended for installation on the physical Hisense E6N.

- Polished the login, live guide, channel cards and player overlays for a 10-foot television interface.
- Opens the guide immediately after OTP verification while the catalogue refreshes in the background.
- Added automatic and manual GharTV update checks with SHA-256 verification.
- Added persistent release signing and same-package upgrade support.
- Added a dedicated GitHub Pages installation page and stable latest-APK address.
- Added public-repository privacy, security, licensing and Operon-project boundaries.
- Corrected runtime acceptance so `.LoginActivity`, `.MainActivity` and `.PlayerActivity` are all valid focused GharTV screens.
- Retained the exact existing project folder, package identity and `ghartv` lane.

## 0.5.0-jio-only

- Removed all starter web and YouTube destinations.
- Removed provider/M3U import and external-provider source screens.
- Removed embedded browser and provider activity classes.
- Made Jio phone-number/OTP sign-in mandatory at application start.
- Made the guide, search, favourites, EPG, direct playback and remote controls Jio-only.

## Rejected owner-review iterations retained as history only

- 0.1 Cable Mode: provider launcher.
- 0.2 Phone Mode: mobile-app control layer.
- 0.3 One Guide: mixed provider and free-web guide.
- 0.4.2 Nova: native Jio architecture mixed with free-web/provider sources.
