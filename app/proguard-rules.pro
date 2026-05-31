# Project-specific R8 rules.

# FSRS-Kotlin references Lombok compile-time annotations in bytecode metadata,
# but the annotations are not runtime dependencies.
-dontwarn lombok.Generated
-dontwarn lombok.NonNull
