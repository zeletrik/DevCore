FROM eclipse-temurin:25-jre@sha256:ecdc0adb37bde1a281c4d245a3cd535004b1fc8322d560f5b09463b248c81df0
COPY build/libs/devcore.jar /app/devcore.jar
USER 1000:1000
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
# The image has no curl or wget, so bash talks HTTP to the health endpoint itself, on the configured port and path.
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
	CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/${SERVER_PORT:-8080} && printf "GET %s/actuator/health HTTP/1.0\r\n\r\n" "$SERVER_SERVLET_CONTEXT_PATH" >&3 && grep -q "\"status\":\"UP\"" <&3'
ENTRYPOINT ["java", "-jar", "/app/devcore.jar"]
