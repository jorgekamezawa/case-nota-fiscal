# Imagem do serviço para o ECS Fargate (ADR-0004). Os testes rodam no CI antes deste build.

# Compila e separa o jar nas camadas do Spring Boot: dependências mudam pouco e ficam em cache entre versões.
FROM eclipse-temurin:21.0.12.1_1-jdk-noble AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -Dmaven.test.skip=true \
    && java -Djarmode=tools -jar target/geradornotafiscal-0.0.1-SNAPSHOT.jar extract --layers --launcher --destination extraido

# Só o JRE e as camadas, com usuário sem privilégio.
FROM eclipse-temurin:21.0.12.1_1-jre-noble
RUN useradd --system --uid 10001 aplicacao
WORKDIR /aplicacao
COPY --from=build /workspace/extraido/dependencies/ ./
COPY --from=build /workspace/extraido/spring-boot-loader/ ./
COPY --from=build /workspace/extraido/snapshot-dependencies/ ./
COPY --from=build /workspace/extraido/application/ ./
USER aplicacao
EXPOSE 8080
# A JVM usa até 75% da memória do contêiner; o restante fica para threads e buffers.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
