package com.konkuk.propit.domain.tradelog.repository;

import com.konkuk.propit.domain.tradelog.entity.TradeLog;
import com.konkuk.propit.domain.user.entity.User;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.time.LocalDate;
import java.util.Optional;

public interface TradeLogRepository extends JpaRepository<TradeLog, Long> {

    // 특정 유저의 매매 기록 조회
    List<TradeLog> findAllByUser(User user);

    // 최신순 조회
    List<TradeLog> findByUserOrderByCreatedAtDesc(User user);

    List<TradeLog> findByUserAndSellDate(User user, LocalDate sellDate);

    Optional<TradeLog> findByIdAndUserId(Long id, Long userId);

    List<TradeLog> findByUserId(Long userId, Sort sort);

    @Query("select tl from TradeLog tl join fetch tl.tradeEmotions where tl.user = :user and tl.sellDate = :date")
    List<TradeLog> findWithEmotions(User user, LocalDate date);
}