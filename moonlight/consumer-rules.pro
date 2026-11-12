# Moonlight engine. Native code (moonlight-core) calls back into these classes by name,
# so keep the whole engine package rather than disabling obfuscation app-wide like upstream.
-keep class com.limelight.** { *; }

# Okio
-keep class sun.misc.Unsafe { *; }
-dontwarn java.nio.file.*
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn okio.**

# BouncyCastle (providers are looked up reflectively)
-keep class org.bouncycastle.jcajce.provider.asymmetric.* { *; }
-keep class org.bouncycastle.jcajce.provider.asymmetric.util.* { *; }
-keep class org.bouncycastle.jcajce.provider.asymmetric.rsa.* { *; }
-keep class org.bouncycastle.jcajce.provider.digest.** { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.** { *; }
-keep class org.bouncycastle.jcajce.spec.* { *; }
-keep class org.bouncycastle.jce.** { *; }
-dontwarn javax.naming.**

# jMDNS
-dontwarn javax.jmdns.impl.DNSCache
-dontwarn org.slf4j.**
