package com.music.bitchord

import java.text.Normalizer

actual fun String.nfd(): String = Normalizer.normalize(this, Normalizer.Form.NFD)

actual fun String.nfkc(): String = Normalizer.normalize(this, Normalizer.Form.NFKC)
