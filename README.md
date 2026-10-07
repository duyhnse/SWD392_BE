# AIVES Backend

Backend của hệ thống thi vấn đáp có AI (AI-powered Viva Exam System). Spring Boot 4 · Java 21 · PostgreSQL 16 · JWT.

## Yêu cầu
- JDK 21 trở lên
- Maven (hoặc dùng `./mvnw`)
- Docker (chạy database và chạy test)

## Chạy lần đầu
```bash
cp .env.example .env          # rồi sửa DB_PASSWORD và JWT_SECRET (xem ghi chú trong file)
docker compose up -d --wait   # bật PostgreSQL ở 127.0.0.1:5432
./mvnw spring-boot:run        # API chạy ở http://localhost:8080
```
`.env` chứa bí mật nên **không bao giờ commit** (đã được git bỏ qua). Tạo khóa JWT: `openssl rand -base64 32`.

Schema database nằm ở `init-scripts/init_schema.sql` và chỉ được chạy **lần đầu** khi volume còn trống.
Muốn làm lại từ đầu (mất hết dữ liệu local): `docker compose down -v && docker compose up -d --wait`.
Hibernate chỉ *kiểm tra* schema khớp với entity (`ddl-auto: validate`), không tự sửa DB. Đổi entity thì phải sửa file SQL tương ứng.

## Test
```bash
./mvnw verify
```
Integration test tự bật một PostgreSQL tạm bằng Testcontainers nên chỉ cần Docker đang chạy, không cần DB local.

## API hiện có
| Method | Đường dẫn | Quyền | Mô tả |
|---|---|---|---|
| POST | `/api/v1/auth/signup` | công khai | Đăng ký, luôn tạo tài khoản **STUDENT** |
| POST | `/api/v1/auth/login` | công khai | Đăng nhập, trả JWT |
| GET | `/api/v1/users/me` | đã đăng nhập | Thông tin của mình |
| GET | `/api/v1/users/{id}` | chính chủ hoặc ADMIN | Thông tin người dùng |

Gửi token ở header `Authorization: Bearer <token>`. Mọi lỗi trả cùng một dạng JSON: `status`, `error`, `message`, `path`, và `fieldErrors` khi lỗi validate.

## Quy ước
- **Không commit** mật khẩu, token, khóa, file `.env`. Cấu hình nhạy cảm đi qua biến môi trường.
- Role (`ADMIN`/`LECTURER`/`STUDENT`) không bao giờ lấy từ dữ liệu client gửi lên. Admin/giảng viên do ADMIN cấp quyền.
- Mỗi tính năng làm trên một nhánh riêng (`feature/...`, `fix/...`), tạo Pull Request vào `main`, CI phải xanh mới merge.
- Mỗi endpoint mới phải có test; endpoint cần đăng nhập phải test cả trường hợp thiếu token (401) và sai quyền (403).
- Controller chỉ nhận/trả DTO, không trả thẳng entity. Logic nghiệp vụ nằm ở Service.
