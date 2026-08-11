# Rezeptli ProGuard/R8-Regeln
#
# Room, Hilt/Dagger, Coil und Compose liefern eigene Consumer-Regeln mit,
# daher sind hier nur projektspezifische Ergaenzungen noetig.

# Kotlin-Metadaten fuer Reflection-freie, aber typsichere Serialisierung erhalten.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# Room-Entities und DAOs werden ueber generierte Klassen angesprochen.
-keep class ch.rezeptli.app.data.local.entity.** { *; }

# Enum-Werte werden ueber valueOf() aus der Datenbank rekonstruiert.
-keepclassmembers enum ch.rezeptli.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
