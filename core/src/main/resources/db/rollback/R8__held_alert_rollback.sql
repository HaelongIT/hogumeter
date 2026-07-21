-- R8: V8 역. 방해금지 보류 큐 제거. (REL-05 rollback-drill이 전진→역순 후진을 검증한다.)
drop table if exists held_alert;
