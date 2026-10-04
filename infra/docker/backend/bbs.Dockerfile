# # 后端 java 程序容器
# ! 后端需预打包为 jar 包，存放在当前目录下，命名为 "bbs.jar"，即 "infra/docker/backend/bbs.jar"
# 环境变量依靠 application.yml 的 profile (dev/prod) 注入
# 如需覆盖环境变量请自行配置 .env
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY infra/docker/backend/bbs.jar bbs.jar

VOLUME /data/parabbs/images

EXPOSE 8080

CMD [ "java", "-jar", "bbs.jar" ]

# # 部署流程
# 1. 打包 JAR 并迁移 (在 backend/bbs/ 目录执行) :
#   ./mvnw clean package -DskipTests && cp target/bbs.jar ../../infra/docker/backend/
# 2. 构建镜像 (在项目根目录执行) :
#   docker build -f infra/docker/backend/Dockerfile -t parabbs-backend .
# 3. 运行容器 (在项目根目录执行) :
#   docker run -d \
#       -p 8080:8080 \
#       -v /data/parabbs/images:/data/parabbs/images \
#       --name parabbs