# Keep kotlinx.serialization generated serializers
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class kotlinx.serialization.json.** {}
-keep,allowobfuscation,allowshrinking class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# osmdroid map tiles
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**