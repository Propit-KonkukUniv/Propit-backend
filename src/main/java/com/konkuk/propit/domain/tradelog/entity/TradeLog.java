package com.konkuk.propit.domain.tradelog.entity;

import com.konkuk.propit.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TradeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 판매일
    @Column(nullable = false)
    private LocalDate sellDate;

    // 종목명
    @Column(nullable = false)
    private String stockName;

    // 가격
    @Column(nullable = false)
    private Long buyPrice;

    @Column(nullable = false)
    private Long sellPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Integer holdingDays;

    // 선택 입력
    @Column(columnDefinition = "TEXT")
    private String reason;

    private String imageUrl;

    // 계산 필드
    private Long profitAmount;

    private Double profitRate;

    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "tradeLog", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TradeEmotion> tradeEmotions = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // 수익 계산 메서드
    public void calculateProfit() {
        long totalBuy = buyPrice * quantity;
        long totalSell = sellPrice * quantity;

        this.profitAmount = totalSell - totalBuy;
        if (totalBuy == 0) {
            this.profitRate = 0.0;
        } else {
            this.profitRate = (double) this.profitAmount / totalBuy * 100;
        }
    }

    public void update(
            LocalDate sellDate,
            String stockName,
            Long buyPrice,
            Long sellPrice,
            Integer quantity,
            Integer holdingDays,
            String reason
    ) {
        this.sellDate = sellDate;
        this.stockName = stockName;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.quantity = quantity;
        this.holdingDays = holdingDays;
        this.reason = reason;
    }
}