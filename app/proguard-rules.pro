-keepattributes *Annotation*
-keepclassmembers class * {
    @com.opencsv.bean.CsvBindByName <fields>;
}
-keep class com.opencsv.** { *; }
-keep class org.apache.commons.** { *; }
-dontwarn org.apache.commons.**
-dontwarn com.opencsv.**
