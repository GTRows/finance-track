FROM maven:3.9.14-eclipse-temurin-25-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# Fetch tessdata-fast for Turkish + English (used by the OCR worker).
# Downloaded at build time so the runtime image is self-contained and does
# not rely on network access for OCR.
FROM alpine:3.20 AS tessdata
RUN apk add --no-cache curl
RUN mkdir -p /tessdata && \
    curl -fsSL -o /tessdata/eng.traineddata \
      https://github.com/tesseract-ocr/tessdata_fast/raw/4.1.0/eng.traineddata && \
    curl -fsSL -o /tessdata/tur.traineddata \
      https://github.com/tesseract-ocr/tessdata_fast/raw/4.1.0/tur.traineddata

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl tesseract-ocr-data-eng leptonica
WORKDIR /app
RUN mkdir -p /var/log/fintrack /app/tessdata
COPY --from=build /app/target/*.jar app.jar
COPY --from=tessdata /tessdata/ /app/tessdata/
ENV TESSDATA_PATH=/app/tessdata
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
