package dev.hogumeter.core;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		// 운영과 동일 메이저 고정 (스택 확정: PostgreSQL 16)
		// Testcontainers 2.0: self-type 제네릭 제거 + 모듈별 패키지(org.testcontainers.postgresql)
		return new PostgreSQLContainer(DockerImageName.parse("postgres:16"));
	}

}
