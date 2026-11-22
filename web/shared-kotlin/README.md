# @bitchord/shared-kotlin

The Android app's own logic, from `shared/src/commonMain`, compiled to JavaScript
for the web player. The web player imports rules from here instead of keeping a
TypeScript copy, so a change to one on Android is the same change on the web.

`dist/` is generated. Don't edit it:

```bash
./gradlew :shared:exportWeb
```

It is committed because the site builds on Cloudflare Pages, which has Node but
no JDK. CI regenerates it whenever `shared/` changes
(`.github/workflows/web.yml`, job *shared-kotlin*). On a branch in this
repository it commits the update itself; on a fork's PR it fails and names the
command above.

What's exported, and how it maps to Kotlin, is in
`shared/src/jsMain/kotlin/com/music/bitchord/web/`. Each export is a thin
adapter (Kotlin lists to JavaScript arrays, and so on); every rule stays in
commonMain.
