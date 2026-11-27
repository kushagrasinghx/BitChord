package com.music.bitchord

/** Unicode NFD (canonical decomposition): accents split off their letters. */
expect fun String.nfd(): String

/** Unicode NFKC: compatibility forms folded, so full-width `Ｄａｉ` reads `Dai`. */
expect fun String.nfkc(): String

/** Whether this is a letter of the Latin script (Unicode Script=Latin). */
expect fun Char.isLatinScript(): Boolean
