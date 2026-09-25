# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-dontobfuscate
-keep,allowoptimization class is.xyz.mpv.** { public protected *; }
-keep,allowoptimization class net.mediaarea.mediainfo.lib.** { public protected *; }
-dontwarn org.xmlpull.v1.**
-dontnote org.xmlpull.v1.**
-dontwarn org.slf4j.impl.StaticLoggerBinder

# SMBJ ProGuard Rules
# Keep SMBJ classes
-keep class com.hierynomus.smbj.** { *; }
-keep class com.hierynomus.mssmb2.** { *; }
-keep class com.hierynomus.msdtyp.** { *; }
-keep class com.hierynomus.msfscc.** { *; }
-keep class com.hierynomus.protocol.** { *; }
-keep class com.hierynomus.spnego.** { *; }
-keep class com.hierynomus.ntlm.** { *; }
-keep class com.hierynomus.security.** { *; }

# JGSS (Kerberos/SPNEGO) - Optional, not needed for basic NTLM auth
# These are used for domain authentication, which we don't use
-dontwarn org.ietf.jgss.**
-dontnote org.ietf.jgss.**

# MBassador (event bus used by SMBJ) - EL (Expression Language) is optional
-dontwarn net.engio.mbassy.dispatch.el.**
-dontnote net.engio.mbassy.dispatch.el.**
-dontwarn javax.el.**
-dontnote javax.el.**

# Keep MBassador core classes
-keep class net.engio.mbassy.** { *; }

# BouncyCastle (crypto provider used by SMBJ)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ASN.1 classes
-keep class com.hierynomus.asn1.** { *; }

# Keep all classes that use reflection or are loaded dynamically
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# jUPnP (copied from org.jupnp.android JAR META-INF/proguard/proguard-rules.pro)
# R8 shrinking breaks jUPnP's SOAP request writing even with names preserved
# (-dontobfuscate) — keep the whole namespace like Jetty below.
-keep class org.jupnp.** { *; }
-keepclassmembers class ** extends org.jupnp.model.message.header.UpnpHeader {
   public <init>(...);
}

-keep @interface org.jupnp.binding.annotations.**

-keepnames @org.jupnp.binding.annotations.UpnpService class **

-keepclassmembers @org.jupnp.binding.annotations.UpnpService class ** {
    public <init>(...);
    @org.jupnp.binding.annotations.UpnpStateVariable <fields>;
    @org.jupnp.binding.annotations.UpnpAction <methods>;
    public <methods>;
    public <fields>;
}

-dontwarn org.osgi.service.component.annotations.Component
-dontwarn org.osgi.service.metatype.annotations.Designate
-dontwarn org.osgi.framework.BundleContext
-dontwarn org.osgi.service.component.ComponentContext
-dontwarn org.osgi.service.component.annotations.Activate
-dontwarn org.osgi.service.component.annotations.Deactivate
-dontwarn org.osgi.service.component.annotations.Modified
-dontwarn org.osgi.service.component.annotations.Reference
-dontwarn org.osgi.service.http.HttpContext
-dontwarn org.osgi.service.http.HttpService
-dontwarn org.osgi.service.http.NamespaceException
-dontwarn org.osgi.service.metatype.annotations.AttributeDefinition
-dontwarn org.osgi.service.metatype.annotations.ObjectClassDefinition
-dontwarn com.sun.net.httpserver.Headers
-dontwarn com.sun.net.httpserver.HttpExchange

# Jetty 9 (jUPnP Android transport)
-keep class org.eclipse.jetty.** { *; }
-dontwarn org.eclipse.jetty.**
-dontwarn javax.servlet.**