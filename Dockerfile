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
# Bản alpine nhỏ hơn nhiều. Đo thật 20/08: image cuối 301MB (không phải ~200MB như
# ước lượng ban đầu) - vẫn nhỏ hơn nhiều so với gộp một tầng, vì tầng build kèm
# JDK + Maven ~500MB đã bị bỏ lại.
# Image nhỏ = deploy nhanh hơn, cold start của Render ngắn hơn.
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Chạy bằng user thường, không phải root. Container bị chiếm quyền thì kẻ tấn
# công cũng không có root. Đây là thứ ngân hàng sẽ hỏi khi nói về container.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=build /app/target/*.jar app.jar

# 512MB của Render rất chật cho Spring Boot. Phân bổ:
#   heap 300MB + metaspace <=96MB + thread stack/code cache/direct buffer ~80MB
#   = ~476MB, chừa margin dưới 512MB.
#
# ⚠️ DÙNG -Xmx TUYỆT ĐỐI, KHÔNG dùng -XX:MaxRAMPercentage. Đã đo và đây là lý do:
# MaxRAMPercentage tính theo RAM mà JVM *nghĩ* là có. JVM biết được giới hạn
# container nhờ đọc cgroup, nhưng việc đó KHÔNG luôn thành công. Trên Docker
# Desktop/WSL2 thử thật thì:
#     java -Xlog:os+container=trace  ->  "controller memory is not enabled"
#                                        "required controllers disabled at kernel level"
# JVM tự tắt container support rồi đọc RAM máy thật (11.9GB), nên
# MaxRAMPercentage=70 cho ra heap 8.1GB TRONG container 512MB.
#
# Hậu quả nếu để vậy: heap phình quá 512MB -> KERNEL giết container (OOM-kill).
# Không phải OutOfMemoryError của Java - không stack trace, không log, chỉ thấy
# Render restart. Loại lỗi tệ nhất để chẩn đoán.
#
# Trên Render (Linux thật, cgroup v2 đủ controller) thì JVM có thể dò đúng. Nhưng
# gói free là 512MB cố định - biết trước con số thì đặt thẳng, khỏi phụ thuộc vào
# việc dò có thành công hay không. Rẻ hơn và kiểm chứng được ở mọi môi trường.
#
# UseSerialGC -> GC một luồng, ít tốn RAM hơn G1 mặc định. Máy 512MB ít CPU thì
# G1 vừa ngốn bộ nhớ vừa không có lợi.
# MaxMetaspaceSize -> chặn metaspace phình vô hạn; Spring nạp rất nhiều class.
# ⭐ CHIA LAI NGAN SACH 03/09, sau khi deploy DO voi OutOfMemoryError: Metaspace.
#
# Metaspace giu METADATA CUA CLASS, khong phai doi tuong. No nam NGOAI heap, va
# -Xmx khong cham toi no. Dat trong 96m tu hoi app con nho, roi khong ai xem lai.
#
# ⚠️ DO DUOC, va so do lat nguoc chan doan ban dau:
#
#     CACHE_TYPE=none   ->  metaspace 114.6 MB
#     CACHE_TYPE=redis  ->  metaspace 114.8 MB
#
# Redis chi them 0.2 MB. App can ~115 MB metaspace, tran la 96 MB - no da VUOT
# TRAN TU TRUOC, va vuot dan qua ca thang: JPA entity, MapStruct mapper, springdoc,
# KYC, doi soat, cache config. Moi thu them vai nghin class.
#
# `redisConnectionFactory` chi la bean TINH CO duoc dung luc cham tran. Stack trace
# goi ten GIOT NUOC CUOI CUNG, khong goi ten cai binh day. Do la ly do dat lai
# CACHE_TYPE=none roi deploy lai van do y het - da thu that.
#
# Chua bang cach CHUYEN ngan sach chu khong xin them: app nay khong bao gio dung
# toi 300m heap - Hikari toi da 5 ket noi, phan trang chan o 100 dong, khong co cho
# nao doc ca bang len RAM. Do duoc heap chi dung 73 MB. Bot heap, dua cho metaspace.
#
# Do lai voi cau hinh duoi day, app len duoc va NMT bao:
#   Total committed = 297 MB / 512 MB   (metaspace 115, heap 73, code 19, thread 16)
#
# ⚠️ MaxDirectMemorySize dat de phong xa: Netty (nen cua Lettuce) cap phat buffer
# NGOAI heap va mac dinh cua no bang -Xmx. Khong chan thi cho do phinh am tham cho
# toi khi KERNEL giet container - khong stack trace, khong log, chi thay Render
# restart. Khac han loi lan nay: loi lan nay la JVM tu bao, co stack trace, do doc.
ENV JAVA_OPTS="-Xms64m -Xmx176m \
  -XX:MaxMetaspaceSize=160m \
  -XX:ReservedCodeCacheSize=48m \
  -XX:MaxDirectMemorySize=32m \
  -Xss512k \
  -XX:+UseSerialGC"

# Dùng "sh -c" để $JAVA_OPTS được shell khai triển thành nhiều tham số.
# Viết ENTRYPOINT ["java", "$JAVA_OPTS", ...] thì $JAVA_OPTS bị truyền nguyên
# văn thành MỘT tham số và JVM báo lỗi không nhận ra.
#
# KHÔNG hardcode cổng ở đây - app đọc biến PORT do Render cấp.
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
