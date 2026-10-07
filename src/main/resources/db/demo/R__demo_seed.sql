-- ============================================================================
-- Demo seed data (profile "demo" only) — 11_IMPLEMENTATION_PLAN.md §5.
-- Repeatable migration: re-applied when this file changes; every insert is idempotent.
-- Demo accounts log in with their username (admin, lecturer1, student1, ...) and password  Aives@123.
-- ============================================================================

-- Users ----------------------------------------------------------------------
INSERT INTO users (user_id, role_id, username, full_name, email, hashed_password, student_code) VALUES
  ('00000000-0000-4000-8000-000000000001', 1, 'admin', 'Quản trị viên',        'admin@aives.local',     '$2a$10$PYoFIkLPo99vyWpZPRNSAu2KEtNOpfb1FNIg8i42ZuCOJqIyEanp6', NULL),
  ('00000000-0000-4000-8000-000000000011', 2, 'lecturer1', 'Giảng viên Một',       'lecturer1@aives.local', '$2a$10$PYoFIkLPo99vyWpZPRNSAu2KEtNOpfb1FNIg8i42ZuCOJqIyEanp6', NULL),
  ('00000000-0000-4000-8000-000000000012', 2, 'lecturer2', 'Giảng viên Hai',       'lecturer2@aives.local', '$2a$10$PYoFIkLPo99vyWpZPRNSAu2KEtNOpfb1FNIg8i42ZuCOJqIyEanp6', NULL),
  ('00000000-0000-4000-8000-000000000021', 3, 'student1', 'Sinh viên Một',        'student1@aives.local',  '$2a$10$PYoFIkLPo99vyWpZPRNSAu2KEtNOpfb1FNIg8i42ZuCOJqIyEanp6', 'SE190001'),
  ('00000000-0000-4000-8000-000000000022', 3, 'student2', 'Sinh viên Hai',        'student2@aives.local',  '$2a$10$PYoFIkLPo99vyWpZPRNSAu2KEtNOpfb1FNIg8i42ZuCOJqIyEanp6', 'SE190002'),
  ('00000000-0000-4000-8000-000000000023', 3, 'student3', 'Sinh viên Ba',         'student3@aives.local',  '$2a$10$PYoFIkLPo99vyWpZPRNSAu2KEtNOpfb1FNIg8i42ZuCOJqIyEanp6', 'SE190003'),
  ('00000000-0000-4000-8000-000000000024', 3, 'student4', 'Sinh viên Bốn',        'student4@aives.local',  '$2a$10$PYoFIkLPo99vyWpZPRNSAu2KEtNOpfb1FNIg8i42ZuCOJqIyEanp6', 'SE190004'),
  ('00000000-0000-4000-8000-000000000025', 3, 'student5', 'Sinh viên Năm',        'student5@aives.local',  '$2a$10$PYoFIkLPo99vyWpZPRNSAu2KEtNOpfb1FNIg8i42ZuCOJqIyEanp6', 'SE190005')
ON CONFLICT DO NOTHING;

-- Course + lecturer assignment -------------------------------------------------
INSERT INTO courses (course_id, code, name, description, default_language) VALUES
  ('10000000-0000-4000-8000-000000000001', 'SWD392', 'Software Architecture and Design',
   'Môn Kiến trúc và Thiết kế phần mềm', 'VI')
ON CONFLICT DO NOTHING;

INSERT INTO course_lecturers (course_id, lecturer_id) VALUES
  ('10000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000011'),
  ('10000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000012')
ON CONFLICT DO NOTHING;

-- Topics + course terms (STT hotwords) ------------------------------------------
INSERT INTO topics (topic_id, course_id, name, sort_order, created_by) VALUES
  ('20000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'Architectural styles', 1, '00000000-0000-4000-8000-000000000011'),
  ('20000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', 'Design principles', 2, '00000000-0000-4000-8000-000000000011'),
  ('20000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001', 'Quality attributes', 3, '00000000-0000-4000-8000-000000000011'),
  ('20000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000001', 'Architecture documentation', 4, '00000000-0000-4000-8000-000000000011')
ON CONFLICT DO NOTHING;

INSERT INTO course_terms (course_term_id, course_id, term)
SELECT gen_random_uuid(), '10000000-0000-4000-8000-000000000001', t
FROM unnest(ARRAY['modular monolith', 'microservices', 'layered architecture', 'SOLID', 'coupling', 'cohesion',
                  'scalability', 'availability', 'latency', 'API gateway', 'event-driven', 'ADR', 'C4 model']) AS t
ON CONFLICT DO NOTHING;

-- Rubrics ----------------------------------------------------------------------
INSERT INTO rubrics (rubric_id, course_id, name, description, created_by) VALUES
  ('30000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'Khái niệm & ví dụ', 'R1', '00000000-0000-4000-8000-000000000011'),
  ('30000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', 'Phân tích đánh đổi', 'R2', '00000000-0000-4000-8000-000000000011')
ON CONFLICT DO NOTHING;

INSERT INTO rubric_criteria (criterion_id, rubric_id, name, description, max_score, weight_percent, sort_order) VALUES
  ('31000000-0000-4000-8000-000000000011', '30000000-0000-4000-8000-000000000001', 'Độ chính xác khái niệm', 'Nêu đúng và đủ các ý chính của khái niệm', 10, 60, 1),
  ('31000000-0000-4000-8000-000000000012', '30000000-0000-4000-8000-000000000001', 'Ví dụ minh hoạ', 'Đưa ví dụ phù hợp, giải thích được', 10, 40, 2),
  ('31000000-0000-4000-8000-000000000021', '30000000-0000-4000-8000-000000000002', 'Độ chính xác', 'Kiến thức đúng, không mâu thuẫn', 10, 40, 1),
  ('31000000-0000-4000-8000-000000000022', '30000000-0000-4000-8000-000000000002', 'Phân tích đánh đổi', 'Chỉ ra ưu, nhược điểm và bối cảnh áp dụng', 10, 40, 2),
  ('31000000-0000-4000-8000-000000000023', '30000000-0000-4000-8000-000000000002', 'Lập luận & diễn đạt', 'Lập luận mạch lạc, có kết luận rõ ràng', 10, 20, 3)
ON CONFLICT DO NOTHING;

-- Questions: 6 PUBLISHED + 2 DRAFT (owner lecturer1) ------------------------------
INSERT INTO questions (question_id, course_id, topic_id, content, reference_answer, bloom_level, language, status, origin,
                       rubric_id, owner_id, published_by, published_at) VALUES
  ('40000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001',
   'Kiến trúc Modular Monolith là gì?',
   '- Một đơn vị triển khai (deployable) duy nhất
- Chia thành các module theo nghiệp vụ, ranh giới rõ ràng
- Module giao tiếp qua interface/in-process call
- Thường dùng chung một DB nhưng mỗi module sở hữu dữ liệu riêng',
   'REMEMBER', 'VI', 'PUBLISHED', 'MANUAL', '30000000-0000-4000-8000-000000000001',
   '00000000-0000-4000-8000-000000000011', '00000000-0000-4000-8000-000000000011', now()),
  ('40000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000002',
   'Giải thích nguyên lý "high cohesion, low coupling" và cho ví dụ.',
   '- Cohesion: các thành phần trong module cùng phục vụ một trách nhiệm
- Coupling: mức phụ thuộc giữa các module
- Coupling thấp giúp thay đổi cục bộ, dễ test, dễ thay thế
- Ví dụ hợp lý (vd: tách module thanh toán gọi qua interface)',
   'UNDERSTAND', 'VI', 'PUBLISHED', 'MANUAL', '30000000-0000-4000-8000-000000000001',
   '00000000-0000-4000-8000-000000000011', '00000000-0000-4000-8000-000000000011', now()),
  ('40000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000002',
   'Nguyên lý Dependency Inversion trong SOLID nói gì?',
   '- Module cấp cao không phụ thuộc module cấp thấp; cả hai phụ thuộc abstraction
- Abstraction không phụ thuộc chi tiết
- Hiện thực qua interface + dependency injection
- Ví dụ: service phụ thuộc Repository interface thay vì class cụ thể',
   'UNDERSTAND', 'VI', 'PUBLISHED', 'MANUAL', '30000000-0000-4000-8000-000000000001',
   '00000000-0000-4000-8000-000000000011', '00000000-0000-4000-8000-000000000011', now()),
  ('40000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000003',
   'Hệ thống thi vấn đáp cần độ trễ thấp giữa các câu hỏi. Em sẽ áp dụng những kỹ thuật kiến trúc nào để giảm độ trễ?',
   '- Cache/tiền xử lý (vd: tổng hợp sẵn TTS câu hỏi chính)
- Gọi dịch vụ song song/bất đồng bộ khi có thể
- Timeout + phương án dự phòng
- Chọn model nhỏ/nhanh, giảm payload
- Đo đạc p95 để kiểm chứng',
   'APPLY', 'VI', 'PUBLISHED', 'MANUAL', '30000000-0000-4000-8000-000000000002',
   '00000000-0000-4000-8000-000000000011', '00000000-0000-4000-8000-000000000011', now()),
  ('40000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001',
   'So sánh Modular Monolith và Microservices cho một nhóm sinh viên 5 người làm đồ án trong 10 tuần. Em chọn kiến trúc nào và vì sao?',
   '- Microservices: triển khai/scale độc lập nhưng chi phí vận hành, mạng, dữ liệu phân tán cao
- Modular monolith: đơn giản triển khai, debug, transaction cục bộ
- Với nhóm nhỏ, thời gian ngắn → modular monolith hợp lý hơn
- Ranh giới module tốt cho phép tách service sau này',
   'ANALYZE', 'VI', 'PUBLISHED', 'MANUAL', '30000000-0000-4000-8000-000000000002',
   '00000000-0000-4000-8000-000000000011', '00000000-0000-4000-8000-000000000011', now()),
  ('40000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000004',
   'Vì sao nên ghi lại quyết định kiến trúc bằng ADR? Phân tích lợi ích và chi phí.',
   '- ADR ghi bối cảnh, quyết định, hệ quả
- Lợi ích: truy vết lý do, onboarding, tránh tranh luận lặp lại
- Chi phí: thời gian viết, cần duy trì, có thể lỗi thời
- Nên viết ngắn, cho quyết định quan trọng',
   'ANALYZE', 'VI', 'PUBLISHED', 'MANUAL', '30000000-0000-4000-8000-000000000002',
   '00000000-0000-4000-8000-000000000011', '00000000-0000-4000-8000-000000000011', now())
ON CONFLICT DO NOTHING;

INSERT INTO questions (question_id, course_id, topic_id, content, reference_answer, bloom_level, language, status, origin, owner_id) VALUES
  ('40000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000003',
   'Availability là gì và đo bằng cách nào?', NULL, 'REMEMBER', 'VI', 'DRAFT', 'MANUAL', '00000000-0000-4000-8000-000000000011'),
  ('40000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001',
   'Khi nào nên chọn kiến trúc event-driven?', NULL, NULL, 'VI', 'DRAFT', 'MANUAL', '00000000-0000-4000-8000-000000000011')
ON CONFLICT DO NOTHING;

-- Viva exam READY for students 1-3 (3 main questions, 2 follow-ups, 600 s) --------
INSERT INTO viva_exams (viva_exam_id, course_id, title, created_by, examiner_id, window_start, window_end, language,
                        main_question_count, max_followups_per_question, time_limit_per_student_sec, status) VALUES
  ('50000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'Vấn đáp giữa kỳ SWD392 – Demo',
   '00000000-0000-4000-8000-000000000011', '00000000-0000-4000-8000-000000000011',
   now() - interval '1 hour', now() + interval '30 days', 'VI', 3, 2, 600, 'READY')
ON CONFLICT DO NOTHING;

INSERT INTO viva_exam_students (viva_exam_id, student_id, seq_no) VALUES
  ('50000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000021', 1),
  ('50000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000022', 2),
  ('50000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000023', 3)
ON CONFLICT DO NOTHING;

INSERT INTO exam_sessions (session_id, viva_exam_id, course_id, student_id, examiner_id) VALUES
  ('51000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001',
   '00000000-0000-4000-8000-000000000021', '00000000-0000-4000-8000-000000000011'),
  ('51000000-0000-4000-8000-000000000002', '50000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001',
   '00000000-0000-4000-8000-000000000022', '00000000-0000-4000-8000-000000000011'),
  ('51000000-0000-4000-8000-000000000003', '50000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001',
   '00000000-0000-4000-8000-000000000023', '00000000-0000-4000-8000-000000000011')
ON CONFLICT DO NOTHING;

-- Question sets: consecutive students share no question (RANDOM_BALANCED, 07 §2), ordered by Bloom level.
INSERT INTO session_questions (session_question_id, session_id, question_id, order_no) VALUES
  ('52000000-0000-4000-8000-000000000011', '51000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000001', 1),
  ('52000000-0000-4000-8000-000000000012', '51000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000002', 2),
  ('52000000-0000-4000-8000-000000000013', '51000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000005', 3),
  ('52000000-0000-4000-8000-000000000021', '51000000-0000-4000-8000-000000000002', '40000000-0000-4000-8000-000000000003', 1),
  ('52000000-0000-4000-8000-000000000022', '51000000-0000-4000-8000-000000000002', '40000000-0000-4000-8000-000000000004', 2),
  ('52000000-0000-4000-8000-000000000023', '51000000-0000-4000-8000-000000000002', '40000000-0000-4000-8000-000000000006', 3),
  ('52000000-0000-4000-8000-000000000031', '51000000-0000-4000-8000-000000000003', '40000000-0000-4000-8000-000000000001', 1),
  ('52000000-0000-4000-8000-000000000032', '51000000-0000-4000-8000-000000000003', '40000000-0000-4000-8000-000000000002', 2),
  ('52000000-0000-4000-8000-000000000033', '51000000-0000-4000-8000-000000000003', '40000000-0000-4000-8000-000000000005', 3)
ON CONFLICT DO NOTHING;
