CREATE TABLE user (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      email VARCHAR(255) NOT NULL,
                      password VARCHAR(255) NOT NULL,
                      nickname VARCHAR(255) NOT NULL,
                      created_at DATETIME(6),
                      updated_at DATETIME(6)
) ENGINE=InnoDB;