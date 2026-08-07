FROM python:3.12-slim
# SEC-06: 베이스 이미지 고정 태그. `:latest`는 빌드마다 다른 바이너리를 산출물에 섞는다.
# 갱신은 Dependabot(.github/dependabot.yml)이 PR로 올린다.
COPY --from=ghcr.io/astral-sh/uv:0.11.28 /uv /uvx /bin/
WORKDIR /app
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project
COPY src src
RUN uv sync --frozen --no-dev
# BE-26(코드리뷰 20260806): 컨테이너 탈출 경로는 없지만(docker.sock 마운트·privileged·host 네트워크
# 전무) RCE급 취약점이 터지면 root 권한을 얻는다 — 비루트로 피해 반경을 컨테이너 안으로 좁힌다.
RUN groupadd -r app && useradd -r -g app -d /app app && chown -R app:app /app
USER app
CMD ["uv", "run", "--no-dev", "python", "-m", "collector"]
