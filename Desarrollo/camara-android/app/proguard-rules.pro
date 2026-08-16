# La APK del piloto se genera sin minificar. Estas reglas existen para que un build de
# release no rompa la serialización del contrato si alguna vez se activa la minificación.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.yerbanalytics.camara.contrato.** {
    *** Companion;
}
-keepclasseswithmembers class com.yerbanalytics.camara.contrato.** {
    kotlinx.serialization.KSerializer serializer(...);
}
