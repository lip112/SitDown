package com.univsitdown.global.config;

import com.univsitdown.space.domain.Space;
import com.univsitdown.space.domain.SpaceCategory;
import com.univsitdown.space.dto.SpaceDetailResponse;
import com.univsitdown.space.repository.SpaceRepository;
import com.univsitdown.space.service.SpaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "jwt.secret=test-secret-key-for-cache-integration-test-32chars",
        "spring.data.redis.host=invalid-host-triggers-fallback"
})
@Testcontainers
class CacheIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("sitdown_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired SpaceService spaceService;
    @SpyBean SpaceRepository spaceRepository;

    private Space saveSpace() {
        return spaceRepository.save(Space.create(
                "캐시테스트열람실_" + UUID.randomUUID(), 1, SpaceCategory.READING_ROOM,
                LocalTime.of(0, 0), LocalTime.of(23, 59), 4, List.of(), null));
    }

    @Test
    void getSpace_두번_호출시_DB_두번_조회() {
        Space space = saveSpace();
        UUID spaceId = space.getId();

        spaceService.getSpace(spaceId, null);
        spaceService.getSpace(spaceId, null);

        verify(spaceRepository, times(2)).findById(spaceId);
    }

    @Test
    void getSpace_캐시결과가_정확한_ID를_포함() {
        Space space = saveSpace();
        UUID spaceId = space.getId();

        SpaceDetailResponse first  = spaceService.getSpace(spaceId, null);
        SpaceDetailResponse second = spaceService.getSpace(spaceId, null);

        assertThat(first.id()).isEqualTo(spaceId.toString());
        assertThat(second.id()).isEqualTo(spaceId.toString());
    }
}
