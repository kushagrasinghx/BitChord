#!/usr/bin/env python3
"""Regenerates app/src/main/java/com/music/bitchord/playback/HanVariants.kt.

The table is OpenCC's Traditional → Simplified character map, cut down to the
entries that map one character to one character: once every character is folded
on its own, the phrase entries add nothing. OpenCC is Apache-2.0 —
https://github.com/BYVoid/OpenCC

    python3 tools/gen-han-variants.py            # downloads TSCharacters.txt if needed
    python3 tools/gen-han-variants.py TSCharacters.txt
"""

import os
import sys
import urllib.request

SOURCE_URL = (
    "https://raw.githubusercontent.com/BYVoid/OpenCC/master/data/dictionary/TSCharacters.txt"
)
OUT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app/src/main/java/com/music/bitchord/playback/HanVariants.kt",
)


def load(path):
    if not os.path.exists(path):
        print(f"downloading {SOURCE_URL}")
        urllib.request.urlretrieve(SOURCE_URL, path)
    pairs = []
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            fields = line.split()
            if len(fields) != 2:  # comments, blanks and the phrase entries
                continue
            traditional, simplified = fields
            if len(traditional) != 1 or len(simplified) != 1:
                continue
            if ord(traditional) > 0xFFFF or ord(simplified) > 0xFFFF:
                continue  # Char, not a surrogate pair
            if traditional == simplified:
                continue
            pairs.append((traditional, simplified))
    pairs.sort(key=lambda pair: ord(pair[0]))
    return pairs


def literal(text, indent="        "):
    chunks = [text[i : i + 64] for i in range(0, len(text), 64)]
    body = "\n".join(f'{indent}"{chunk}" +' for chunk in chunks[:-1])
    # No trailing comma: Kotlin allows one in argument lists but not after a
    # class-body property's initializer.
    return f'{body}\n{indent}"{chunks[-1]}"'


def main():
    pairs = load(sys.argv[1] if len(sys.argv) > 1 else "/tmp/TSCharacters.txt")
    traditional = "".join(pair[0] for pair in pairs)
    simplified = "".join(pair[1] for pair in pairs)
    template = f'''package com.music.bitchord.playback

/**
 * Traditional → Simplified folding, so the two uploads of one song read as one
 * name.
 *
 * YouTube Music carries the same track more than once: a mainland release billed
 * in Simplified characters and a Taiwanese or Hong Kong release of it billed in
 * Traditional, one videoId each, one artist. "理性与任性之间" against
 * "理性與任性之間" is not a different song, but it is a different string, so
 * every comparison that keys on the title — [QueueBuilder.isSameRecording],
 * AutoPlay's own-name guard — used to treat the pair as two tracks.
 *
 * Device ICU has a `Traditional-Simplified` transliterator, but it is API 29+ and
 * is not part of every Android build's ICU data, which would leave the fold
 * silently doing nothing on some phones. The table costs {len(pairs)} character pairs
 * (about {len(traditional.encode()) + len(simplified.encode())} bytes) and folds the same way everywhere.
 *
 * Generated from OpenCC's `TSCharacters.txt` (Apache-2.0,
 * https://github.com/BYVoid/OpenCC), keeping the entries that map one character
 * to one character — the phrase entries are not needed once every character is
 * folded on its own. Regenerate with `tools/gen-han-variants.py`.
 */
internal object HanVariants {{

    /** Folded to Simplified; index-aligned with [SIMPLIFIED], sorted by character. */
    private const val TRADITIONAL =
{literal(traditional)}

    private const val SIMPLIFIED =
{literal(simplified)}

    private val folded: Map<Char, Char> by lazy {{ TRADITIONAL.zip(SIMPLIFIED).toMap() }}

    /**
     * [text] with every Traditional character replaced by its Simplified form.
     * Text with nothing to fold — which is most of it — is returned as it came.
     */
    fun fold(text: String): String {{
        var out: StringBuilder? = null
        var copied = 0
        for (index in text.indices) {{
            val simplified = folded[text[index]] ?: continue
            if (out == null) out = StringBuilder(text.length)
            out.append(text, copied, index)
            out.append(simplified)
            copied = index + 1
        }}
        val builder = out ?: return text
        builder.append(text, copied, text.length)
        return builder.toString()
    }}
}}
'''
    with open(OUT, "w", encoding="utf-8") as handle:
        handle.write(template)
    print(f"wrote {OUT}: {len(pairs)} pairs")


if __name__ == "__main__":
    main()
