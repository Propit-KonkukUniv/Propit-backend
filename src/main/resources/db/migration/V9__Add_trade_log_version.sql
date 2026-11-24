-- 매매일지가 바뀔 때마다 1씩 올라가는 버전
ALTER TABLE user ADD COLUMN trade_log_version BIGINT NOT NULL DEFAULT 0;

-- 리포트 캐시가 어떤 버전의 매매 내역으로 만들어졌는지
ALTER TABLE daily_report_cache ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE overview_report_cache ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- 기존 캐시는 어떤 매매 내역으로 만들었는지 알 수 없고,
-- AI 호출 실패로 비어 있는 리포트가 저장돼 있을 수도 있어서 비운다. 다음 조회 때 다시 만든다.
DELETE FROM daily_report_cache;
DELETE FROM overview_report_cache;
