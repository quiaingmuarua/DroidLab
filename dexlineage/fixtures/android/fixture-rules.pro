# These are symbol-obfuscation fixtures, not optimized application releases.
-dontshrink
-dontoptimize
# AGP 9.4 supplies an anonymous r8-map-id SourceFile value. Preflight verifies
# its exact correspondence with this build's separate R8 Mapping identifier.

# Do not add -dontobfuscate, keepnames, or blanket -keep rules here.
# Original names are deliberately available only in the separate R8 mapping.
