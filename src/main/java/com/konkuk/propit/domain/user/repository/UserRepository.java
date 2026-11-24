package com.konkuk.propit.domain.user.repository;

import com.konkuk.propit.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    @Query("select u.tradeLogVersion from User u where u.id = :userId")
    Optional<Long> findTradeLogVersionById(@Param("userId") Long userId);

    // 읽어서 +1 한 뒤 저장하면 동시에 수정될 때 증가분이 사라지므로 DB에서 바로 올린다
    @Modifying
    @Query("update User u set u.tradeLogVersion = u.tradeLogVersion + 1 where u.id = :userId")
    void increaseTradeLogVersion(@Param("userId") Long userId);
}