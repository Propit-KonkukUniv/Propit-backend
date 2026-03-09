CREATE TABLE trade_log (
                           id BIGINT AUTO_INCREMENT PRIMARY KEY,
                           user_id BIGINT NOT NULL,
                           sell_date DATE NOT NULL,
                           stock_name VARCHAR(255) NOT NULL,
                           buy_price BIGINT NOT NULL,
                           sell_price BIGINT NOT NULL,
                           quantity INT NOT NULL,
                           holding_days INT NOT NULL,
                           reason TEXT,
                           image_url VARCHAR(255),
                           profit_amount BIGINT,
                           profit_rate DOUBLE,
                           created_at DATETIME(6),
                           CONSTRAINT fk_trade_log_user FOREIGN KEY (user_id) REFERENCES user (id)
) ENGINE=InnoDB;

CREATE TABLE trade_emotion (
                               id BIGINT AUTO_INCREMENT PRIMARY KEY,
                               trade_log_id BIGINT,
                               emotion_id BIGINT,
                               CONSTRAINT fk_trade_emotion_log FOREIGN KEY (trade_log_id) REFERENCES trade_log (id),
                               CONSTRAINT fk_trade_emotion_emotion FOREIGN KEY (emotion_id) REFERENCES emotion (id)
) ENGINE=InnoDB;