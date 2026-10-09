-- ============================================================================
-- Demo seed data (profile "demo" only) — 11_IMPLEMENTATION_PLAN.md §5.
-- Repeatable migration: re-applied when this file changes; every insert is idempotent.
-- Demo accounts log in with their username (admin, lecturer1, student1, ...) and password  Aives@123
-- (except vinhdqse190180, see below).
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
  ('00000000-0000-4000-8000-000000000025', 3, 'student5', 'Sinh viên Năm',        'student5@aives.local',  '$2a$10$PYoFIkLPo99vyWpZPRNSAu2KEtNOpfb1FNIg8i42ZuCOJqIyEanp6', 'SE190005'),
  -- Team member's sample account (password VinhAives@2026)
  ('00000000-0000-4000-8000-000000000031', 3, 'vinhdqse190180', 'Dương Quốc Vinh', 'vinhdqse190180@fpt.edu.vn', '$2a$10$NOpdBA0mJwNrR0EC6H0NF.E/LzWE7pWccov0sszzOTuaz/0b5GbcW', 'SE190180')
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

-- ============================================================================
-- CRUD milestone scenarios (15_CRUD_CATALOGUE.md): a second course, an upcoming
-- buổi thi with a blueprint, and a finished buổi thi with graded, released results.
-- ============================================================================

-- Second course taught only by lecturer2 (lecturer1 must not see it — AC-C1)
INSERT INTO courses (course_id, code, name, description, default_language) VALUES
  ('10000000-0000-4000-8000-000000000002', 'PRN232', 'Building Cross-Platform Back-End Applications', 'Môn lập trình back-end', 'VI')
ON CONFLICT DO NOTHING;
INSERT INTO course_lecturers (course_id, lecturer_id) VALUES
  ('10000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-000000000012')
ON CONFLICT DO NOTHING;

-- Buổi thi sắp diễn ra (READY, window in 3 days) with a blueprint: 1 Design-principles UNDERSTAND + 1 APPLY + 1 Architectural-styles
INSERT INTO viva_exams (viva_exam_id, course_id, title, created_by, examiner_id, window_start, window_end, language,
                        main_question_count, max_followups_per_question, time_limit_per_student_sec, status,
                        description, instructions, location) VALUES
  ('50000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', 'Vấn đáp cuối kỳ SWD392', '00000000-0000-4000-8000-000000000011', '00000000-0000-4000-8000-000000000011',
   date_trunc('hour', now()) + interval '3 days', date_trunc('hour', now()) + interval '3 days 4 hours', 'VI', 3, 2, 900, 'READY',
   'Vấn đáp cuối kỳ, 3 câu chính, tối đa 2 câu hỏi thêm mỗi câu.',
   'Dùng Chrome/Edge trên máy tính, kiểm tra micro trước khi bắt đầu, ngồi nơi yên tĩnh.', 'Online')
ON CONFLICT DO NOTHING;
INSERT INTO viva_exam_blueprint_items (blueprint_item_id, viva_exam_id, topic_id, bloom_level, question_count, sort_order) VALUES
  ('53000000-0000-4000-8000-000000000021', '50000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000002', 'UNDERSTAND', 1, 1),
  ('53000000-0000-4000-8000-000000000022', '50000000-0000-4000-8000-000000000002', NULL, 'APPLY', 1, 2),
  ('53000000-0000-4000-8000-000000000023', '50000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000001', NULL, 1, 3)
ON CONFLICT DO NOTHING;
INSERT INTO viva_exam_students (viva_exam_id, student_id, seq_no, added_by) VALUES
  ('50000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-000000000024', 1, '00000000-0000-4000-8000-000000000011'),
  ('50000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-000000000025', 2, '00000000-0000-4000-8000-000000000011'),
  ('50000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-000000000031', 3, '00000000-0000-4000-8000-000000000011')
ON CONFLICT DO NOTHING;
INSERT INTO exam_sessions (session_id, viva_exam_id, course_id, student_id, examiner_id, status) VALUES
  ('51000000-0000-4000-8000-000000000210', '50000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000024', '00000000-0000-4000-8000-000000000011', 'SCHEDULED'),
  ('51000000-0000-4000-8000-000000000220', '50000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000025', '00000000-0000-4000-8000-000000000011', 'SCHEDULED'),
  ('51000000-0000-4000-8000-000000000230', '50000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000031', '00000000-0000-4000-8000-000000000011', 'SCHEDULED')
ON CONFLICT DO NOTHING;
INSERT INTO session_questions (session_question_id, session_id, question_id, order_no, status) VALUES
  ('52000000-0000-4000-8000-000000002110', '51000000-0000-4000-8000-000000000210', '40000000-0000-4000-8000-000000000001', 1, 'PENDING'),
  ('52000000-0000-4000-8000-000000002120', '51000000-0000-4000-8000-000000000210', '40000000-0000-4000-8000-000000000002', 2, 'PENDING'),
  ('52000000-0000-4000-8000-000000002130', '51000000-0000-4000-8000-000000000210', '40000000-0000-4000-8000-000000000004', 3, 'PENDING'),
  ('52000000-0000-4000-8000-000000002210', '51000000-0000-4000-8000-000000000220', '40000000-0000-4000-8000-000000000003', 1, 'PENDING'),
  ('52000000-0000-4000-8000-000000002220', '51000000-0000-4000-8000-000000000220', '40000000-0000-4000-8000-000000000004', 2, 'PENDING'),
  ('52000000-0000-4000-8000-000000002230', '51000000-0000-4000-8000-000000000220', '40000000-0000-4000-8000-000000000005', 3, 'PENDING'),
  ('52000000-0000-4000-8000-000000002310', '51000000-0000-4000-8000-000000000230', '40000000-0000-4000-8000-000000000001', 1, 'PENDING'),
  ('52000000-0000-4000-8000-000000002320', '51000000-0000-4000-8000-000000000230', '40000000-0000-4000-8000-000000000002', 2, 'PENDING'),
  ('52000000-0000-4000-8000-000000002330', '51000000-0000-4000-8000-000000000230', '40000000-0000-4000-8000-000000000004', 3, 'PENDING')
ON CONFLICT DO NOTHING;

-- Buổi thi đã kết thúc (CLOSED 10 days ago, results released 2 days ago):
-- student1 and vinhdqse190180 completed and were graded; student2 never came (MISSED).
INSERT INTO viva_exams (viva_exam_id, course_id, title, created_by, examiner_id, window_start, window_end, language,
                        main_question_count, max_followups_per_question, time_limit_per_student_sec, status,
                        description, location, results_released, results_released_at) VALUES
  ('50000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001', 'Vấn đáp giữa kỳ SWD392 – Đợt 1', '00000000-0000-4000-8000-000000000011', '00000000-0000-4000-8000-000000000011',
   date_trunc('hour', now()) - interval '10 days', date_trunc('hour', now()) - interval '10 days' + interval '4 hours', 'VI',
   3, 2, 600, 'CLOSED', 'Đợt vấn đáp giữa kỳ đầu tiên.', 'Online', true, now() - interval '2 days')
ON CONFLICT DO NOTHING;
INSERT INTO viva_exam_students (viva_exam_id, student_id, seq_no, added_by) VALUES
  ('50000000-0000-4000-8000-000000000003', '00000000-0000-4000-8000-000000000021', 1, '00000000-0000-4000-8000-000000000011'),
  ('50000000-0000-4000-8000-000000000003', '00000000-0000-4000-8000-000000000031', 2, '00000000-0000-4000-8000-000000000011'),
  ('50000000-0000-4000-8000-000000000003', '00000000-0000-4000-8000-000000000022', 3, '00000000-0000-4000-8000-000000000011')
ON CONFLICT DO NOTHING;
INSERT INTO exam_sessions (session_id, viva_exam_id, course_id, student_id, examiner_id, status, end_reason, cancel_reason,
                           started_at, deadline_at, ended_at, last_seen_at, consent_recorded_at) VALUES
  ('51000000-0000-4000-8000-000000000310', '50000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000021', '00000000-0000-4000-8000-000000000011', 'COMPLETED', 'ALL_QUESTIONS_DONE', NULL, date_trunc('hour', now()) - interval '10 days' + interval '1 hours', date_trunc('hour', now()) - interval '10 days' + interval '1 hours 10 minutes', date_trunc('hour', now()) - interval '10 days' + interval '1 hours 8 minutes', date_trunc('hour', now()) - interval '10 days' + interval '1 hours', date_trunc('hour', now()) - interval '10 days' + interval '1 hours'),
  ('51000000-0000-4000-8000-000000000320', '50000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000031', '00000000-0000-4000-8000-000000000011', 'COMPLETED', 'ALL_QUESTIONS_DONE', NULL, date_trunc('hour', now()) - interval '10 days' + interval '2 hours', date_trunc('hour', now()) - interval '10 days' + interval '2 hours 10 minutes', date_trunc('hour', now()) - interval '10 days' + interval '2 hours 8 minutes', date_trunc('hour', now()) - interval '10 days' + interval '2 hours', date_trunc('hour', now()) - interval '10 days' + interval '2 hours'),
  ('51000000-0000-4000-8000-000000000330', '50000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000022', '00000000-0000-4000-8000-000000000011', 'CANCELLED', NULL, 'NO_SHOW', NULL, NULL, NULL, NULL, NULL)
ON CONFLICT DO NOTHING;
INSERT INTO session_questions (session_question_id, session_id, question_id, order_no, status) VALUES
  ('52000000-0000-4000-8000-000000003110', '51000000-0000-4000-8000-000000000310', '40000000-0000-4000-8000-000000000001', 1, 'DONE'),
  ('52000000-0000-4000-8000-000000003120', '51000000-0000-4000-8000-000000000310', '40000000-0000-4000-8000-000000000002', 2, 'DONE'),
  ('52000000-0000-4000-8000-000000003130', '51000000-0000-4000-8000-000000000310', '40000000-0000-4000-8000-000000000005', 3, 'DONE'),
  ('52000000-0000-4000-8000-000000003210', '51000000-0000-4000-8000-000000000320', '40000000-0000-4000-8000-000000000003', 1, 'DONE'),
  ('52000000-0000-4000-8000-000000003220', '51000000-0000-4000-8000-000000000320', '40000000-0000-4000-8000-000000000004', 2, 'DONE'),
  ('52000000-0000-4000-8000-000000003230', '51000000-0000-4000-8000-000000000320', '40000000-0000-4000-8000-000000000006', 3, 'DONE'),
  ('52000000-0000-4000-8000-000000003310', '51000000-0000-4000-8000-000000000330', '40000000-0000-4000-8000-000000000001', 1, 'NOT_REACHED'),
  ('52000000-0000-4000-8000-000000003320', '51000000-0000-4000-8000-000000000330', '40000000-0000-4000-8000-000000000002', 2, 'NOT_REACHED'),
  ('52000000-0000-4000-8000-000000003330', '51000000-0000-4000-8000-000000000330', '40000000-0000-4000-8000-000000000005', 3, 'NOT_REACHED')
ON CONFLICT DO NOTHING;
INSERT INTO exam_turns (turn_id, session_id, session_question_id, question_id, parent_turn_id, turn_type, turn_order, followup_index,
                        question_text, language, status, asked_at, answer_submitted_at, response_duration_sec,
                        student_transcript, followup_decision, processing_ms) VALUES
  ('55000000-0000-4000-8000-000000003111', '51000000-0000-4000-8000-000000000310', '52000000-0000-4000-8000-000000003110', '40000000-0000-4000-8000-000000000001', NULL, 'MAIN', 1, 0, 'Kiến trúc Modular Monolith là gì?', 'VI', 'ANSWERED', date_trunc('hour', now()) - interval '10 days' + interval '1 hours 2 minutes', date_trunc('hour', now()) - interval '10 days' + interval '1 hours 2 minutes' + interval '70 seconds', 65, 'Dạ Modular Monolith là một ứng dụng triển khai duy nhất nhưng bên trong chia thành các module theo nghiệp vụ, mỗi module có ranh giới rõ ràng và giao tiếp qua interface, thường dùng chung một cơ sở dữ liệu nhưng mỗi module sở hữu dữ liệu riêng.', 'NEXT_COMPLETE', 4200),
  ('55000000-0000-4000-8000-000000003121', '51000000-0000-4000-8000-000000000310', '52000000-0000-4000-8000-000000003120', '40000000-0000-4000-8000-000000000002', NULL, 'MAIN', 2, 0, 'Giải thích nguyên lý "high cohesion, low coupling" và cho ví dụ.', 'VI', 'ANSWERED', date_trunc('hour', now()) - interval '10 days' + interval '1 hours 4 minutes', date_trunc('hour', now()) - interval '10 days' + interval '1 hours 4 minutes' + interval '70 seconds', 65, 'Cohesion là các thành phần trong một module cùng phục vụ một trách nhiệm, coupling là mức phụ thuộc giữa các module. Ví dụ module thanh toán gọi module đơn hàng qua interface.', 'FOLLOW_UP', 4200),
  ('55000000-0000-4000-8000-000000003122', '51000000-0000-4000-8000-000000000310', '52000000-0000-4000-8000-000000003120', '40000000-0000-4000-8000-000000000002', '55000000-0000-4000-8000-000000003121', 'FOLLOW_UP', 3, 1, 'Em có thể nói rõ hơn vì sao coupling thấp giúp kiểm thử dễ hơn không?', 'VI', 'ANSWERED', date_trunc('hour', now()) - interval '10 days' + interval '1 hours 6 minutes', date_trunc('hour', now()) - interval '10 days' + interval '1 hours 6 minutes' + interval '30 seconds', 28, 'Dạ vì module ít phụ thuộc nên có thể thay phần phụ thuộc bằng mock và test riêng từng module.', 'NEXT_COMPLETE', 3900),
  ('55000000-0000-4000-8000-000000003131', '51000000-0000-4000-8000-000000000310', '52000000-0000-4000-8000-000000003130', '40000000-0000-4000-8000-000000000005', NULL, 'MAIN', 4, 0, 'So sánh Modular Monolith và Microservices cho một nhóm sinh viên 5 người làm đồ án trong 10 tuần. Em chọn kiến trúc nào và vì sao?', 'VI', 'ANSWERED', date_trunc('hour', now()) - interval '10 days' + interval '1 hours 8 minutes', date_trunc('hour', now()) - interval '10 days' + interval '1 hours 8 minutes' + interval '70 seconds', 65, 'Microservices thì triển khai độc lập nhưng tốn chi phí vận hành, còn modular monolith đơn giản hơn. Với nhóm năm người làm mười tuần em chọn modular monolith.', 'NEXT_COMPLETE', 4200),
  ('55000000-0000-4000-8000-000000003211', '51000000-0000-4000-8000-000000000320', '52000000-0000-4000-8000-000000003210', '40000000-0000-4000-8000-000000000003', NULL, 'MAIN', 1, 0, 'Nguyên lý Dependency Inversion trong SOLID nói gì?', 'VI', 'ANSWERED', date_trunc('hour', now()) - interval '10 days' + interval '2 hours 2 minutes', date_trunc('hour', now()) - interval '10 days' + interval '2 hours 2 minutes' + interval '70 seconds', 65, 'Dependency Inversion nói module cấp cao không phụ thuộc module cấp thấp mà cả hai phụ thuộc vào abstraction, và abstraction không phụ thuộc chi tiết. Ví dụ service phụ thuộc repository interface, còn implementation được inject vào.', 'NEXT_COMPLETE', 4200),
  ('55000000-0000-4000-8000-000000003221', '51000000-0000-4000-8000-000000000320', '52000000-0000-4000-8000-000000003220', '40000000-0000-4000-8000-000000000004', NULL, 'MAIN', 2, 0, 'Hệ thống thi vấn đáp cần độ trễ thấp giữa các câu hỏi. Em sẽ áp dụng những kỹ thuật kiến trúc nào để giảm độ trễ?', 'VI', 'ANSWERED', date_trunc('hour', now()) - interval '10 days' + interval '2 hours 4 minutes', date_trunc('hour', now()) - interval '10 days' + interval '2 hours 4 minutes' + interval '70 seconds', 65, 'Em sẽ tổng hợp sẵn giọng đọc cho câu hỏi chính, gọi các dịch vụ AI song song khi có thể, đặt timeout và phương án dự phòng, chọn model nhỏ để giảm độ trễ và đo p95 để kiểm chứng.', 'NEXT_COMPLETE', 4200),
  ('55000000-0000-4000-8000-000000003231', '51000000-0000-4000-8000-000000000320', '52000000-0000-4000-8000-000000003230', '40000000-0000-4000-8000-000000000006', NULL, 'MAIN', 3, 0, 'Vì sao nên ghi lại quyết định kiến trúc bằng ADR? Phân tích lợi ích và chi phí.', 'VI', 'ANSWERED', date_trunc('hour', now()) - interval '10 days' + interval '2 hours 6 minutes', date_trunc('hour', now()) - interval '10 days' + interval '2 hours 6 minutes' + interval '70 seconds', 65, 'ADR ghi lại bối cảnh, quyết định và hệ quả nên sau này dễ truy vết lý do. Chi phí là mất thời gian viết và phải cập nhật.', 'FOLLOW_UP', 4200),
  ('55000000-0000-4000-8000-000000003232', '51000000-0000-4000-8000-000000000320', '52000000-0000-4000-8000-000000003230', '40000000-0000-4000-8000-000000000006', '55000000-0000-4000-8000-000000003231', 'FOLLOW_UP', 4, 1, 'Em có thể nêu thêm một lợi ích của ADR khi có thành viên mới vào nhóm không?', 'VI', 'ANSWERED', date_trunc('hour', now()) - interval '10 days' + interval '2 hours 8 minutes', date_trunc('hour', now()) - interval '10 days' + interval '2 hours 8 minutes' + interval '30 seconds', 28, 'Dạ người mới đọc ADR sẽ hiểu nhanh vì sao hệ thống được thiết kế như vậy mà không phải hỏi lại.', 'NEXT_COMPLETE', 3900)
ON CONFLICT DO NOTHING;
INSERT INTO grade_evaluations (evaluation_id, session_id, status, ai_total_score, final_total_score, ai_general_feedback,
                               lecturer_comment, confirmed_by, confirmed_at, llm_model, version) VALUES
  ('54000000-0000-4000-8000-000000000310', '51000000-0000-4000-8000-000000000310', 'CONFIRMED', 6.53, 6.93, 'Sinh viên nắm được kiến thức nền tảng, cần luyện thêm phần phân tích đánh đổi.', 'Làm bài tốt.', '00000000-0000-4000-8000-000000000011', now() - interval '3 days', 'mock-llm', 1),
  ('54000000-0000-4000-8000-000000000320', '51000000-0000-4000-8000-000000000320', 'CONFIRMED', 7.13, 7.53, 'Sinh viên nắm được kiến thức nền tảng, cần luyện thêm phần phân tích đánh đổi.', 'Làm bài tốt.', '00000000-0000-4000-8000-000000000011', now() - interval '3 days', 'mock-llm', 1)
ON CONFLICT DO NOTHING;
INSERT INTO question_grades (question_grade_id, evaluation_id, session_question_id, question_id, rubric_snapshot, status,
                             ai_score, final_score, include_in_total, ai_strengths, ai_weaknesses, ai_missing_points, ai_feedback,
                             ai_error, signals, lecturer_comment, confirmed_by, confirmed_at) VALUES
  ('56000000-0000-4000-8000-000000003110', '54000000-0000-4000-8000-000000000310', '52000000-0000-4000-8000-000000003110', '40000000-0000-4000-8000-000000000001', '{"rubricId": "30000000-0000-4000-8000-000000000001", "name": "Khái niệm & ví dụ", "criteria": [{"criterionId": "31000000-0000-4000-8000-000000000011", "name": "Độ chính xác khái niệm", "description": "Nêu đúng và đủ các ý chính của khái niệm", "maxScore": 10, "weightPercent": 60, "sortOrder": 1}, {"criterionId": "31000000-0000-4000-8000-000000000012", "name": "Ví dụ minh hoạ", "description": "Đưa ví dụ phù hợp, giải thích được", "maxScore": 10, "weightPercent": 40, "sortOrder": 2}]}', 'CONFIRMED', 7.60, 7.60, true, '["Nêu được các ý chính"]', '["Ví dụ còn chung chung"]', '[]', 'Câu trả lời khá đầy đủ, cần thêm ví dụ cụ thể.', NULL, '{"totalAnswerSec": 65, "words": 49, "wordsPerMin": 55, "followupsUsed": 0, "noAnswerTurns": 0}', NULL, '00000000-0000-4000-8000-000000000011', now() - interval '3 days'),
  ('56000000-0000-4000-8000-000000003120', '54000000-0000-4000-8000-000000000310', '52000000-0000-4000-8000-000000003120', '40000000-0000-4000-8000-000000000002', '{"rubricId": "30000000-0000-4000-8000-000000000001", "name": "Khái niệm & ví dụ", "criteria": [{"criterionId": "31000000-0000-4000-8000-000000000011", "name": "Độ chính xác khái niệm", "description": "Nêu đúng và đủ các ý chính của khái niệm", "maxScore": 10, "weightPercent": 60, "sortOrder": 1}, {"criterionId": "31000000-0000-4000-8000-000000000012", "name": "Ví dụ minh hoạ", "description": "Đưa ví dụ phù hợp, giải thích được", "maxScore": 10, "weightPercent": 40, "sortOrder": 2}]}', 'CONFIRMED', 6.20, 6.60, true, '["Nêu được các ý chính"]', '["Ví dụ còn chung chung"]', '[]', 'Câu trả lời khá đầy đủ, cần thêm ví dụ cụ thể.', NULL, '{"totalAnswerSec": 93, "words": 53, "wordsPerMin": 55, "followupsUsed": 1, "noAnswerTurns": 0}', NULL, '00000000-0000-4000-8000-000000000011', now() - interval '3 days'),
  ('56000000-0000-4000-8000-000000003130', '54000000-0000-4000-8000-000000000310', '52000000-0000-4000-8000-000000003130', '40000000-0000-4000-8000-000000000005', '{"rubricId": "30000000-0000-4000-8000-000000000002", "name": "Phân tích đánh đổi", "criteria": [{"criterionId": "31000000-0000-4000-8000-000000000021", "name": "Độ chính xác", "description": "Kiến thức đúng, không mâu thuẫn", "maxScore": 10, "weightPercent": 40, "sortOrder": 1}, {"criterionId": "31000000-0000-4000-8000-000000000022", "name": "Phân tích đánh đổi", "description": "Chỉ ra ưu, nhược điểm và bối cảnh áp dụng", "maxScore": 10, "weightPercent": 40, "sortOrder": 2}, {"criterionId": "31000000-0000-4000-8000-000000000023", "name": "Lập luận & diễn đạt", "description": "Lập luận mạch lạc, có kết luận rõ ràng", "maxScore": 10, "weightPercent": 20, "sortOrder": 3}]}', 'CONFIRMED', 5.80, 6.60, true, '["Nêu được các ý chính"]', '["Ví dụ còn chung chung"]', '[]', 'Câu trả lời khá đầy đủ, cần thêm ví dụ cụ thể.', NULL, '{"totalAnswerSec": 65, "words": 29, "wordsPerMin": 55, "followupsUsed": 0, "noAnswerTurns": 0}', NULL, '00000000-0000-4000-8000-000000000011', now() - interval '3 days'),
  ('56000000-0000-4000-8000-000000003210', '54000000-0000-4000-8000-000000000320', '52000000-0000-4000-8000-000000003210', '40000000-0000-4000-8000-000000000003', '{"rubricId": "30000000-0000-4000-8000-000000000001", "name": "Khái niệm & ví dụ", "criteria": [{"criterionId": "31000000-0000-4000-8000-000000000011", "name": "Độ chính xác khái niệm", "description": "Nêu đúng và đủ các ý chính của khái niệm", "maxScore": 10, "weightPercent": 60, "sortOrder": 1}, {"criterionId": "31000000-0000-4000-8000-000000000012", "name": "Ví dụ minh hoạ", "description": "Đưa ví dụ phù hợp, giải thích được", "maxScore": 10, "weightPercent": 40, "sortOrder": 2}]}', 'CONFIRMED', 8.60, 8.60, true, '["Nêu được các ý chính"]', '["Ví dụ còn chung chung"]', '[]', 'Câu trả lời khá đầy đủ, cần thêm ví dụ cụ thể.', NULL, '{"totalAnswerSec": 65, "words": 38, "wordsPerMin": 55, "followupsUsed": 0, "noAnswerTurns": 0}', NULL, '00000000-0000-4000-8000-000000000011', now() - interval '3 days'),
  ('56000000-0000-4000-8000-000000003220', '54000000-0000-4000-8000-000000000320', '52000000-0000-4000-8000-000000003220', '40000000-0000-4000-8000-000000000004', '{"rubricId": "30000000-0000-4000-8000-000000000002", "name": "Phân tích đánh đổi", "criteria": [{"criterionId": "31000000-0000-4000-8000-000000000021", "name": "Độ chính xác", "description": "Kiến thức đúng, không mâu thuẫn", "maxScore": 10, "weightPercent": 40, "sortOrder": 1}, {"criterionId": "31000000-0000-4000-8000-000000000022", "name": "Phân tích đánh đổi", "description": "Chỉ ra ưu, nhược điểm và bối cảnh áp dụng", "maxScore": 10, "weightPercent": 40, "sortOrder": 2}, {"criterionId": "31000000-0000-4000-8000-000000000023", "name": "Lập luận & diễn đạt", "description": "Lập luận mạch lạc, có kết luận rõ ràng", "maxScore": 10, "weightPercent": 20, "sortOrder": 3}]}', 'CONFIRMED', 6.80, 7.60, true, '["Nêu được các ý chính"]', '["Ví dụ còn chung chung"]', '[]', 'Câu trả lời khá đầy đủ, cần thêm ví dụ cụ thể.', NULL, '{"totalAnswerSec": 65, "words": 41, "wordsPerMin": 55, "followupsUsed": 0, "noAnswerTurns": 0}', NULL, '00000000-0000-4000-8000-000000000011', now() - interval '3 days'),
  ('56000000-0000-4000-8000-000000003230', '54000000-0000-4000-8000-000000000320', '52000000-0000-4000-8000-000000003230', '40000000-0000-4000-8000-000000000006', '{"rubricId": "30000000-0000-4000-8000-000000000002", "name": "Phân tích đánh đổi", "criteria": [{"criterionId": "31000000-0000-4000-8000-000000000021", "name": "Độ chính xác", "description": "Kiến thức đúng, không mâu thuẫn", "maxScore": 10, "weightPercent": 40, "sortOrder": 1}, {"criterionId": "31000000-0000-4000-8000-000000000022", "name": "Phân tích đánh đổi", "description": "Chỉ ra ưu, nhược điểm và bối cảnh áp dụng", "maxScore": 10, "weightPercent": 40, "sortOrder": 2}, {"criterionId": "31000000-0000-4000-8000-000000000023", "name": "Lập luận & diễn đạt", "description": "Lập luận mạch lạc, có kết luận rõ ràng", "maxScore": 10, "weightPercent": 20, "sortOrder": 3}]}', 'CONFIRMED', 6.00, 6.40, true, '["Nêu được các ý chính"]', '["Ví dụ còn chung chung"]', '[]', 'Câu trả lời khá đầy đủ, cần thêm ví dụ cụ thể.', NULL, '{"totalAnswerSec": 93, "words": 51, "wordsPerMin": 55, "followupsUsed": 1, "noAnswerTurns": 0}', NULL, '00000000-0000-4000-8000-000000000011', now() - interval '3 days')
ON CONFLICT DO NOTHING;
INSERT INTO criterion_scores (criterion_score_id, question_grade_id, criterion_id, max_score, weight_percent, ai_score,
                              ai_justification, final_score) VALUES
  ('57000000-0000-4000-8000-000000031100', '56000000-0000-4000-8000-000000003110', '31000000-0000-4000-8000-000000000011', 10, 60, 8, 'Dựa trên câu trả lời của sinh viên.', 8),
  ('57000000-0000-4000-8000-000000031110', '56000000-0000-4000-8000-000000003110', '31000000-0000-4000-8000-000000000012', 10, 40, 7, 'Dựa trên câu trả lời của sinh viên.', 7),
  ('57000000-0000-4000-8000-000000031200', '56000000-0000-4000-8000-000000003120', '31000000-0000-4000-8000-000000000011', 10, 60, 7, 'Dựa trên câu trả lời của sinh viên.', 7),
  ('57000000-0000-4000-8000-000000031210', '56000000-0000-4000-8000-000000003120', '31000000-0000-4000-8000-000000000012', 10, 40, 5, 'Dựa trên câu trả lời của sinh viên.', 6),
  ('57000000-0000-4000-8000-000000031300', '56000000-0000-4000-8000-000000003130', '31000000-0000-4000-8000-000000000021', 10, 40, 6, 'Dựa trên câu trả lời của sinh viên.', 7),
  ('57000000-0000-4000-8000-000000031310', '56000000-0000-4000-8000-000000003130', '31000000-0000-4000-8000-000000000022', 10, 40, 5, 'Dựa trên câu trả lời của sinh viên.', 6),
  ('57000000-0000-4000-8000-000000031320', '56000000-0000-4000-8000-000000003130', '31000000-0000-4000-8000-000000000023', 10, 20, 7, 'Dựa trên câu trả lời của sinh viên.', 7),
  ('57000000-0000-4000-8000-000000032100', '56000000-0000-4000-8000-000000003210', '31000000-0000-4000-8000-000000000011', 10, 60, 9, 'Dựa trên câu trả lời của sinh viên.', 9),
  ('57000000-0000-4000-8000-000000032110', '56000000-0000-4000-8000-000000003210', '31000000-0000-4000-8000-000000000012', 10, 40, 8, 'Dựa trên câu trả lời của sinh viên.', 8),
  ('57000000-0000-4000-8000-000000032200', '56000000-0000-4000-8000-000000003220', '31000000-0000-4000-8000-000000000021', 10, 40, 7, 'Dựa trên câu trả lời của sinh viên.', 8),
  ('57000000-0000-4000-8000-000000032210', '56000000-0000-4000-8000-000000003220', '31000000-0000-4000-8000-000000000022', 10, 40, 6, 'Dựa trên câu trả lời của sinh viên.', 7),
  ('57000000-0000-4000-8000-000000032220', '56000000-0000-4000-8000-000000003220', '31000000-0000-4000-8000-000000000023', 10, 20, 8, 'Dựa trên câu trả lời của sinh viên.', 8),
  ('57000000-0000-4000-8000-000000032300', '56000000-0000-4000-8000-000000003230', '31000000-0000-4000-8000-000000000021', 10, 40, 6, 'Dựa trên câu trả lời của sinh viên.', 6.5),
  ('57000000-0000-4000-8000-000000032310', '56000000-0000-4000-8000-000000003230', '31000000-0000-4000-8000-000000000022', 10, 40, 6, 'Dựa trên câu trả lời của sinh viên.', 6),
  ('57000000-0000-4000-8000-000000032320', '56000000-0000-4000-8000-000000003230', '31000000-0000-4000-8000-000000000023', 10, 20, 6, 'Dựa trên câu trả lời của sinh viên.', 7)
ON CONFLICT DO NOTHING;
