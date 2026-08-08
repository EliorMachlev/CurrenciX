# Project-specific proguard/R8 rules.

# EvalEx is built with Project Lombok. `lombok.Generated` is a compile-time
# annotation that is not shipped at runtime but is still referenced from the
# compiled bytecode, so R8 flags it as a missing class during minification of
# the fdroid release build. Suppress the warning — nothing at runtime needs
# these annotation classes.
-dontwarn lombok.**
