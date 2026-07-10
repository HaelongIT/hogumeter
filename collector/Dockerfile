FROM python:3.14-slim
# SEC-06: 베이스 이미지 고정 태그. `:latest`는 빌드마다 다른 바이너리를 산출물에 섞는다.
# 갱신은 Dependabot(.github/dependabot.yml)이 PR로 올린다.
COPY --from=ghcr.io/astral-sh/uv:0.11.28 /uv /uvx /bin/
WORKDIR /app
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project
COPY src src
RUN uv sync --frozen --no-dev
CMD ["uv", "run", "--no-dev", "python", "-m", "collector"]
