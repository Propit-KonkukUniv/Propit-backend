package com.konkuk.propit.domain.tradelog.repository;

import com.konkuk.propit.domain.tradelog.entity.TradeLog;
import com.konkuk.propit.domain.user.entity.User;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.repository.query.Param;

public interface TradeLogRepository extends JpaRepository<TradeLog, Long> {

    @Query("select distinct tl from TradeLog tl " +
            "left join fetch tl.tradeEmotions te " +
            "left join fetch te.emotion " +
            "where tl.user = :user")
    List<TradeLog> findAllWithEmotionsByUser(User user);

    // 최신순 조회
    List<TradeLog> findByUserOrderByCreatedAtDesc(User user);

    // 감정 태그까지 한 번에 가져온다. 트랜잭션 밖에서 감정을 읽어도 지연 로딩이 일어나지 않는다.
    // 감정이 없는 매매일지도 나와야 해서 left join을 쓴다.
    @Query("select distinct tl from TradeLog tl " +
            "left join fetch tl.tradeEmotions te " +
            "left join fetch te.emotion " +
            "where tl.user = :user and tl.sellDate = :sellDate")
    List<TradeLog> findWithEmotionsByUserAndSellDate(@Param("user") User user, @Param("sellDate") LocalDate sellDate);

    Optional<TradeLog> findByIdAndUserId(Long id, Long userId);

    List<TradeLog> findByUserId(Long userId, Sort sort);

    @Query("select tl from TradeLog tl join fetch tl.tradeEmotions where tl.user = :user and tl.sellDate = :date")
    List<TradeLog> findWithEmotions(User user, LocalDate date);
}