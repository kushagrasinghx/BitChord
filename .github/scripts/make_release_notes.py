#!/usr/bin/env python3
import sys
import subprocess
from datetime import datetime, timezone

def main():
    if len(sys.argv) < 7:
        print("Usage: make_release_notes.py <output_file> <release_title> <short_sha> <full_sha> <version_name> <tag_name>")
        sys.exit(1)

    out_file = sys.argv[1]
    release_title = sys.argv[2]
    short_sha = sys.argv[3]
    full_sha = sys.argv[4]
    version_name = sys.argv[5]
    tag_name = sys.argv[6]

    try:
        recent_commits = subprocess.check_output(
            ["git", "log", "-n", "10", '--pretty=format:- [%h](https://github.com/kushagrasinghx/BitChord/commit/%H) %s (%an)'],
            text=True
        ).strip()
    except Exception as e:
        recent_commits = f"- Commit {short_sha}"

    now_utc = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")

    content = f"""## BitChord {release_title}

Automated build from upstream [kushagrasinghx/BitChord](https://github.com/kushagrasinghx/BitChord).

- **Commit:** [`{short_sha}`](https://github.com/kushagrasinghx/BitChord/commit/{full_sha})
- **Base Version:** `{version_name}`
- **Sync Date:** {now_utc}

### Recent Upstream Commits
{recent_commits}

### Download APKs
| Target | Asset | Description |
|---|---|---|
| 📱 **Phone (Universal)** | `BitChord-{tag_name}.apk` | Recommended for all phones & tablets |
| 📱 Phone (arm64-v8a) | `BitChord-{tag_name}_arm64-v8a.apk` | Optimized for 64-bit devices |
| 📱 Phone (armeabi-v7a) | `BitChord-{tag_name}_armeabi-v7a.apk` | Legacy 32-bit devices |
| 📱 Phone (x86_64) | `BitChord-{tag_name}_x86_64.apk` | Android emulators & Chromebooks |
| 📺 **Android TV** | `BitChordTV-{tag_name}.apk` | Universal Android TV package |
| 📺 TV (splits) | `BitChordTV-{tag_name}_*.apk` | Architecture-specific TV packages |
| ⌚ WearOS | `BitChordWear-{tag_name}.apk` | WearOS smartwatch companion (if built) |

---
*Automatic build provided by BitChord fork workflow.*
"""

    with open(out_file, "w", encoding="utf-8") as f:
        f.write(content)

    print(f"Generated release notes at {out_file}")

if __name__ == "__main__":
    main()
