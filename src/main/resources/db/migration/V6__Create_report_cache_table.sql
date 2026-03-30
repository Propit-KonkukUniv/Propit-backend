CREATE TABLE daily_report_cache (
                                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                    user_id BIGINT NOT NULL,
                                    date DATE NOT NULL,
                                    report_json TEXT NOT NULL,
                                    CONSTRAINT uk_user_date UNIQUE (user_id, date)
);

CREATE TABLE overview_report_cache (
                                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                       user_id BIGINT NOT NULL,
                                       report_json TEXT NOT NULL,
                                       CONSTRAINT uk_user UNIQUE (user_id)
);