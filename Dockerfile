# ============ 构建阶段 ============
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /build

# 安装 unzip（Maven Wrapper 下载 Maven 时需要解压）
RUN apt-get update && apt-get install -y --no-install-recommends unzip && rm -rf /var/lib/apt/lists/*

# 先复制 Maven Wrapper 和 POM，利用 Docker 缓存加速依赖下载
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY family-tree-common/pom.xml family-tree-common/
COPY family-tree-domain/pom.xml family-tree-domain/
COPY family-tree-infrastructure/pom.xml family-tree-infrastructure/
COPY family-tree-application/pom.xml family-tree-application/
COPY family-tree-web/pom.xml family-tree-web/
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# 复制源码并构建
COPY family-tree-common/src family-tree-common/src
COPY family-tree-domain/src family-tree-domain/src
COPY family-tree-infrastructure/src family-tree-infrastructure/src
COPY family-tree-application/src family-tree-application/src
COPY family-tree-web/src family-tree-web/src
RUN ./mvnw package -DskipTests -B

# ============ 运行阶段（Distroless 最小化攻击面） ============
FROM gcr.io/distroless/java21-debian12

LABEL maintainer="Family-Tree"
LABEL description="族谱管理系统"

WORKDIR /app

# 从构建阶段复制 JAR
COPY --from=builder /build/family-tree-web/target/*.jar app.jar

# 数据目录（H2 数据库文件）
VOLUME ["/app/data"]

# 日志目录
VOLUME ["/app/logs"]

EXPOSE 8090

# JVM 参数：容器感知 + 默认使用 prod profile
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENV SPRING_PROFILES_ACTIVE=prod

# Distroless 镜像仅包含 Java 运行时，无 shell
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
