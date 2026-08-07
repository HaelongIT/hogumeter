// X-06(코드리뷰 20260806) — 이 저장소에 정적분석 도구가 하나도 없었다. 리뷰 실측: 이번 리뷰가 찾은
// 발견 중 기계가 잡았을 유형은 web의 훅 취소 가드 누락(FE-02·FE-03) 2건뿐이었고, 그건 정확히
// react-hooks/exhaustive-deps가 겨냥하는 패턴이다. 그래서 세 모듈 중 web부터 먼저 넣는다
// (00-summary.md「다음(2차)을 위한 메모」도입 순서 권고: web → collector → core).
//
//   npm run lint         # 검사만
//   npm run lint -- --fix
import js from '@eslint/js'
import reactHooks from 'eslint-plugin-react-hooks'
import globals from 'globals'
import tseslint from 'typescript-eslint'

export default tseslint.config(
  { ignores: ['dist/**', 'node_modules/**'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2022,
      globals: { ...globals.browser, ...globals.node },
    },
    plugins: { 'react-hooks': reactHooks },
    rules: {
      ...reactHooks.configs.recommended.rules,
      // 이 저장소는 클라이언트 검증을 편의로만 쓰고(웹 규율 참고) `any`를 드물게 의도적으로
      // 쓰는 곳이 있다 — 엄격 모드로 시작하면 초기 도입 비용이 커 오히려 안 켜질 위험이 있다.
      // 핵심 가치(훅 규칙)만 강제하고 나머지는 경고로 낮춰 점진 적용한다.
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
    },
  },
)
