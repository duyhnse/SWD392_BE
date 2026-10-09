package swd392.group6.AIVES.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByStudentCode(String studentCode);

    @Query("""
            select u from User u
            where (:roleId is null or u.roleId = :roleId)
              and (:active is null or u.active = :active)
              and (:q is null
                   or lower(u.username) like concat('%', :q, '%')
                   or lower(u.fullName) like concat('%', :q, '%')
                   or lower(u.email) like concat('%', :q, '%')
                   or lower(coalesce(u.studentCode, '')) like concat('%', :q, '%'))
            """)
    Page<User> search(@Param("q") String q, @Param("roleId") Short roleId, @Param("active") Boolean active,
                      Pageable pageable);
}
