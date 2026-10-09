package swd392.group6.AIVES.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.UUID;

interface CourseRepository extends JpaRepository<Course, UUID> {

    boolean existsByCode(String code);

    /** {@code restrictToIds} null = every course (ADMIN); otherwise only those ids (a lecturer's assignments). */
    @Query("""
            select c from Course c
            where (:active is null or c.active = :active)
              and (:q is null
                   or lower(c.code) like concat('%', :q, '%')
                   or lower(c.name) like concat('%', :q, '%'))
              and (:unrestricted = true or c.courseId in :ids)
            """)
    Page<Course> search(@Param("q") String q, @Param("active") Boolean active,
                        @Param("unrestricted") boolean unrestricted, @Param("ids") Collection<UUID> ids,
                        Pageable pageable);
}
