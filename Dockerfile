# syntax=docker/dockerfile:1
# The jar comes from Gradle (CI, or `./gradlew bootJar` locally), so there is no build stage here.

# Stage 1: split the jar into Spring Boot's layers. Runs on the build machine's own platform, since the layout is the
# same for every target: the arm64 image needs no emulation.
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jre@sha256:945247f9d092703e0c6be92243ef3c51640229e23ab34d353c8dcdc73e6cd4e6 AS layers
WORKDIR /layers
COPY build/libs/devcore.jar devcore.jar
RUN java -Djarmode=tools -jar devcore.jar extract --layers --destination extracted

# Stage 2: the runtime image.
FROM eclipse-temurin:25-jre@sha256:945247f9d092703e0c6be92243ef3c51640229e23ab34d353c8dcdc73e6cd4e6
WORKDIR /app
# Dependencies first: they rarely change, so a release usually pushes only the small application layer.
COPY --from=layers /layers/extracted/dependencies/ ./
COPY --from=layers /layers/extracted/application/ ./
# The base image's unprivileged user; the files stay root-owned and read-only to it.
USER 1000:1000
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
# The image has no curl or wget, so bash talks HTTP to the health endpoint itself, on the configured port and path.
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
	CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/${SERVER_PORT:-8080} && printf "GET %s/actuator/health HTTP/1.0\r\n\r\n" "$SERVER_SERVLET_CONTEXT_PATH" >&3 && grep -q "\"status\":\"UP\"" <&3'
# NOTE: no AOT cache or CDS archive is trained here. Either bakes in the build machine's CPU features, and this image
# runs on whatever a homelab has; the JIT compiling for the real CPU is worth the slower start.
# NOTE: the JDK's Base64 intrinsic stays off until the base image has the Java4Shell fix (CVE-2026-114938, JDK 25.0.5.1).
# On x86-64 CPUs with AVX-512 VBMI it writes past its buffer on crafted input, and several tools decode what a visitor
# pastes. The flag is on the command line so that overriding JAVA_TOOL_OPTIONS can't drop it; the plain decoder is fast
# enough for DevCore's input cap.
ENTRYPOINT ["java", "-XX:-UseBASE64Intrinsics", "-jar", "devcore.jar"]
