# Auth 使用预先打包的 JAR，不在镜像构建时编译源码。
# 构建前将 backend/auth/target/auth.jar 复制到 infra/docker/backend/auth.jar。
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY infra/docker/backend/auth.jar auth.jar

# 私钥文件由 Compose 在运行时只读挂载，不复制到镜像。
EXPOSE 9000

CMD [ "java", "-jar", "auth.jar" ]
