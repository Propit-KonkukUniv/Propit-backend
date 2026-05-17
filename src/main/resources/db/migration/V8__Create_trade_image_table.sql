CREATE TABLE trade_image (
                             id BIGINT AUTO_INCREMENT PRIMARY KEY,
                             trade_log_id BIGINT NOT NULL,
                             s3_key VARCHAR(255) NOT NULL,
                             created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                             CONSTRAINT fk_trade_image_trade
                                 FOREIGN KEY (trade_log_id)
                                     REFERENCES trade_log(id)
                                     ON DELETE CASCADE
);