# NewPipeExtractor evaluates YouTube's player JS with Mozilla Rhino for the
# signature / n-parameter deciphering. Stripping it silently breaks extraction.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter { *; }
-dontwarn org.mozilla.javascript.tools.**

# The extractor parses responses reflectively in places; keep it conservatively.
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# Rhino references desktop-JVM APIs that don't exist on Android; ignore them.
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**

# Media3, Coil and kotlinx libraries ship their own consumer rules.
