# Third-party native code

| Folder | Upstream | Version | License |
|---|---|---|---|
| `signalsmith-stretch/` | https://github.com/Signalsmith-Audio/signalsmith-stretch | 1.3.2 (`88061fd4`) | MIT (`LICENSE.txt`) |
| `signalsmith-linear/` | https://github.com/Signalsmith-Audio/linear | 0.6.6 | MIT (`LICENSE.txt`) |

Copied verbatim; the Automix loop mix (`native/loopmix/`) is the only user.
Signalsmith Stretch includes `signalsmith-linear/stft.h`, which resolves
against this folder.
