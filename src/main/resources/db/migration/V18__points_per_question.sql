-- Points per main question (D81): a template row may give each of its questions a maximum number of points; all rows
-- together make exactly 10. Blank on every row = equal weight (total = mean of the question scores).
alter table exam_template_items add column points_per_question numeric(4, 2)
    check (points_per_question is null or (points_per_question >= 0.25 and points_per_question <= 10));

-- Copied at check-in from the template row, then onto the question grade when the evaluation is created.
alter table attempt_questions add column max_points numeric(4, 2);
alter table question_grades add column max_points numeric(4, 2);
