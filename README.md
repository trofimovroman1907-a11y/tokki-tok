<div align="center">

<img src="assets/tokki-tok-icon.png" alt="Tokki Tok" width="256">

**Tokki Tok**

**An independent modified build based on [MargyT](https://github.com/narezany/MargyT).**
A TikTok mod for Android.

[![channel](https://img.shields.io/badge/channel-margytiktok-8DD1B0?style=flat-square)](https://t.me/margytiktok)
[![forum](https://img.shields.io/badge/forum-margeletforum-8DD1B0?style=flat-square)](https://t.me/margeletforum)
[![plugins](https://img.shields.io/badge/plugins-docs-8DD1B0?style=flat-square)](docs/plugins.md)
[![licence](https://img.shields.io/badge/licence-GPL--3.0-8DD1B0?style=flat-square)](#licence)

</div>

---

**Tokki Tok** is a custom build based on the MargyT project. It is intended for independent use and modification while respecting the original upstream work.

### ⚠️ APK Not Included
This repository does not include the TikTok APK. To build Tokki Tok, you must supply the proprietary original TikTok APK locally. It is someone else's proprietary code and is not ours to redistribute.

### Region Spoofing Notice
The mod allows changing the region the app thinks you are in (reaching `carrier_region` and `sys_region`). Note that this **does not change your server-side account/store_region**, which is fixed when the account is registered. Changing your IP for other purposes requires a VPN.

### Upstream Requirements
This build preserves the upstream author-notice screen requirements as specified in the license (see [ADDITIONAL-TERMS.md](ADDITIONAL-TERMS.md)).

### Licence
Licensed under GPL-3.0-or-later with additional terms.

---

Keeps TikTok's own package, `com.zhiliaoapp.musically`, so it goes in **instead**
of the official app rather than beside it: uninstall TikTok first, then
install this. arm64 only. No root.

Everything the mod adds is on one screen: **Settings and privacy → MargyT**,
the first row. There is no second icon on the home screen — the mod goes in
through TikTok's own settings and nowhere else.

Nothing here is hidden from the person using it. Every switch says what it does
and what it does not reach, the mod keeps a diary of what it saw that can be
copied out in one tap, and the parts that cannot work are written down rather
than quietly left broken.
