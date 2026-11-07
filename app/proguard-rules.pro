# Our own R8 rules for release builds; the libraries bring theirs (consumer rules).

# Lets R8 widen a class's or member's access when that lets it inline or merge
# more, which makes the dex smaller.
-allowaccessmodification

# Moves every class that is left into one package, so the dex's names and
# string table are shorter. Classes that must keep their names are kept by
# the libraries' consumer rules, which this doesn't override.
-repackageclasses ''
