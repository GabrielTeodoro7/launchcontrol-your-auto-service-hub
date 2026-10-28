# Compila o projeto usando a mesma versão principal de Java validada no CI.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -f backend/pom.xml clean package

# Servidor compatível com Jakarta Servlet 6.1 e Java 25.
FROM tomcat:11-jdk25-temurin
ENV LC_UPLOAD_DIR=/var/launchcontrol/uploads
RUN mkdir -p /var/launchcontrol/uploads
COPY --from=build /workspace/backend/target/launchcontrol.war /usr/local/tomcat/webapps/launchcontrol.war
EXPOSE 8080
