# 01. 아키텍처

<!-- 이 문서: 런타임 구성·레이어·디렉토리 구조. 새 코드를 "어디에 둘지"의 기준. -->

## 런타임 아키텍처
<!-- 배포 토폴로지: 프로세스·프록시·DB·캐시·외부 서비스. same-origin/CORS 여부. -->

## 백엔드 레이어 (계층형)
<!-- 예시(교체): Controller → Service(Interface+Impl) → Mapper/Repository → DB.
     비즈니스 로직=Service, DB/외부=인터페이스 주입(테스트 목 대체). 공통 응답 봉투. -->

## 디렉토리 구조 (feature-first — 도메인 우선)
<!-- 예시(교체):
  {{module}}/ { controller, service, mapper, VO/DTO }
  common/ { config, exception, response, ... }
  도메인 우선으로 묶는다(레이어 우선 X). -->

## 프론트엔드 구조 (요약)
<!-- 예시(교체): pages/·components/·hooks/·api/·store/·types/. 요청은 단일 axios 인스턴스. -->

## 인가 규칙
<!-- 공개 경로 / 인증 필요 / 역할별 게이팅. 어디서 강제하는가(필터·미들웨어). -->
