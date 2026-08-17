# Render KHÔNG hỗ trợ Java native - chỉ có Node, Python, Ruby, Go, Rust, Docker.
# Nên Java bắt buộc đi qua Docker. Render tự build file này trên máy chủ của họ,
# mình KHÔNG cần cài Docker Desktop để deploy.

# ===== Tầng 1: BUILD =====
# Tầng này có sẵn JDK + Maven để biên dịch. Nó nặng ~500MB nhưng KHÔNG bị mang
# sang tầng sau, nên không tính vào dung lượng image cuối.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml TRƯỚC src, và tải dependency ngay tại đây.
# Lý do: Docker cache theo từng dòng. pom.xml ít đổi hơn code rất nhiều, nên
# lần build sau chỉ cần đổi code là tầng tải-dependency được dùng lại từ cache
# -> khỏi tải lại 75 jar. Copy cả hai cùng lúc là mất hết lợi thế này.
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# ===== Tầng 2: CHẠY =====
# Chỉ cần JRE để chạy, không cần JDK và không cần Maven.
# Bản alpine nhỏ hơn nhiều -> image cuối ~200MB thay vì ~700MB.
# Image nhỏ = deploy nhanh hơn, cold start của Render ngắn hơn.
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Chạy bằng user thường, không phải root. Container bị chiếm quyền thì kẻ tấn
# công cũng không có root. Đây là thứ ngân hàng sẽ hỏi khi nói về container.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=build /app/target/*.jar app.jar

# 512MB của Render rất chật cho Spring Boot.
# MaxRAMPercentage=70 -> heap tối đa ~360MB, chừa phần còn lại cho metaspace,
# thread stack và bộ nhớ ngoài heap. Không đặt thì JVM lấy 25% = quá ít.
# UseSerialGC -> GC một luồng, ít tốn RAM hơn G1 mặc định. Máy 512MB thì
# G1 vừa ngốn bộ nhớ vừa không có lợi vì chỉ có ít CPU.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"

# Dùng "sh -c" để $JAVA_OPTS được shell khai triển thành nhiều tham số.
# Viết ENTRYPOINT ["java", "$JAVA_OPTS", ...] thì $JAVA_OPTS bị truyền nguyên
# văn thành MỘT tham số và JVM báo lỗi không nhận ra.
#
# KHÔNG hardcode cổng ở đây - app đọc biến PORT do Render cấp.
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
