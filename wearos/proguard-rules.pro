# Nothing in this app is found by name at runtime — the Data Layer is plain
# keys, and Compose and Play services ship their own consumer rules — so unlike
# the phone app this one lets R8 rename too: a smaller dex is less to load on a
# watch CPU. Line numbers are kept; map traces with build/outputs/mapping.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
