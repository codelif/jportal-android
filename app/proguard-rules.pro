# kotlinx.serialization keeps what it needs through its own consumer rules.
# ktjiit models are only touched through generated serializers, nothing reflective.
-dontwarn org.slf4j.**

# the debug report names portal errors by class, keep them readable
-keepnames class in.codelif.ktjiit.http.PortalException$*
