package com.univsitdown.notice.service;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.notice.domain.Notice;
import com.univsitdown.notice.domain.NoticeCategory;
import com.univsitdown.notice.dto.CreateNoticeRequest;
import com.univsitdown.notice.dto.NoticeDetailResponse;
import com.univsitdown.notice.dto.NoticeListItemResponse;
import com.univsitdown.notice.dto.UpdateNoticeRequest;
import com.univsitdown.notice.exception.NoticeNotFoundException;
import com.univsitdown.notice.repository.NoticeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    @Mock
    NoticeRepository noticeRepository;

    @InjectMocks
    NoticeService noticeService;

    @Test
    void getNotices_카테고리없음_전체반환() {
        given(noticeRepository.findVisibleByCategory(isNull(), any(Instant.class), any()))
                .willReturn(new PageImpl<>(List.of()));
        PageResponse<NoticeListItemResponse> result = noticeService.getNotices(null, PageRequest.of(0, 20));
        assertThat(result.content()).isEmpty();
    }

    @Test
    void getNotices_ALL_카테고리_전체반환() {
        given(noticeRepository.findVisibleByCategory(isNull(), any(Instant.class), any()))
                .willReturn(new PageImpl<>(List.of()));
        PageResponse<NoticeListItemResponse> result = noticeService.getNotices("ALL", PageRequest.of(0, 20));
        assertThat(result.content()).isEmpty();
    }

    @Test
    void getNotice_존재하면_반환() {
        Notice notice = createNotice(true);
        given(noticeRepository.findById(notice.getId())).willReturn(Optional.of(notice));
        NoticeDetailResponse response = noticeService.getNotice(notice.getId());
        assertThat(response.title()).isEqualTo("테스트 공지");
    }

    @Test
    void getNotice_없으면_예외() {
        given(noticeRepository.findById(any())).willReturn(Optional.empty());
        assertThatThrownBy(() -> noticeService.getNotice(UUID.randomUUID()))
                .isInstanceOf(NoticeNotFoundException.class);
    }

    @Test
    void getNotice_비활성이면_예외() {
        Notice notice = createNotice(false);
        given(noticeRepository.findById(notice.getId())).willReturn(Optional.of(notice));
        assertThatThrownBy(() -> noticeService.getNotice(notice.getId()))
                .isInstanceOf(NoticeNotFoundException.class);
    }

    @Test
    void getNotice_발행전이면_예외() {
        Notice notice = createNotice(true, Instant.now().plusSeconds(3600), null);
        given(noticeRepository.findById(notice.getId())).willReturn(Optional.of(notice));

        assertThatThrownBy(() -> noticeService.getNotice(notice.getId()))
                .isInstanceOf(NoticeNotFoundException.class);
    }

    @Test
    void getNotice_만료됐으면_예외() {
        Notice notice = createNotice(
                true,
                Instant.now().minusSeconds(7200),
                Instant.now().minusSeconds(3600)
        );
        given(noticeRepository.findById(notice.getId())).willReturn(Optional.of(notice));

        assertThatThrownBy(() -> noticeService.getNotice(notice.getId()))
                .isInstanceOf(NoticeNotFoundException.class);
    }

    @Test
    void getAdminNotices_발행전과_만료된_활성공지도_조회한다() {
        Notice scheduled = createNotice(true, Instant.now().plusSeconds(3600), null);
        Notice expired = createNotice(
                true,
                Instant.now().minusSeconds(7200),
                Instant.now().minusSeconds(3600)
        );
        PageRequest pageable = PageRequest.of(0, 20);
        given(noticeRepository.findActiveByCategory(null, pageable))
                .willReturn(new PageImpl<>(List.of(scheduled, expired), pageable, 2));

        PageResponse<NoticeListItemResponse> response = noticeService.getAdminNotices(null, pageable);

        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(1).expiresAt()).isNotNull();
    }

    @Test
    void getAdminNotice_만료된_활성공지의_상세를_조회한다() {
        Notice expired = createNotice(
                true,
                Instant.now().minusSeconds(7200),
                Instant.now().minusSeconds(3600)
        );
        given(noticeRepository.findById(expired.getId())).willReturn(Optional.of(expired));

        NoticeDetailResponse response = noticeService.getAdminNotice(expired.getId());

        assertThat(response.expiresAt()).isNotNull();
        assertThat(response.isNew()).isTrue();
    }

    @Test
    void createNotice_공지생성_성공() {
        Notice saved = createNotice(true);
        saved.update(
                "공지 제목",
                "공지 내용",
                NoticeCategory.INFO,
                Instant.parse("2026-05-14T00:00:00Z"),
                null
        );
        given(noticeRepository.save(any(Notice.class))).willReturn(saved);

        NoticeDetailResponse response = noticeService.createNotice(new CreateNoticeRequest(
                "공지 제목",
                "공지 내용",
                NoticeCategory.INFO,
                Instant.parse("2026-05-14T00:00:00Z"),
                null
        ));

        assertThat(response.title()).isEqualTo("공지 제목");
        assertThat(response.publishedAt()).isEqualTo("2026-05-14 09:00:00");
        then(noticeRepository).should().save(any(Notice.class));
    }

    @Test
    void updateNotice_공지수정_성공() {
        Notice notice = createNotice(true, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
        given(noticeRepository.findById(notice.getId())).willReturn(Optional.of(notice));
        UpdateNoticeRequest request = new UpdateNoticeRequest();
        request.setTitle("수정 제목");
        request.setCategory(NoticeCategory.EVENT);

        NoticeDetailResponse response = noticeService.updateNotice(
                notice.getId(),
                request
        );

        assertThat(response.title()).isEqualTo("수정 제목");
        assertThat(response.category()).isEqualTo("EVENT");
        assertThat(response.expiresAt()).isNotNull();
    }

    @Test
    void updateNotice_명시적_null이면_만료시각을_삭제한다() {
        Notice notice = createNotice(true, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
        given(noticeRepository.findById(notice.getId())).willReturn(Optional.of(notice));
        UpdateNoticeRequest request = new UpdateNoticeRequest();
        request.setExpiresAt(null);

        NoticeDetailResponse response = noticeService.updateNotice(notice.getId(), request);

        assertThat(response.expiresAt()).isNull();
        assertThat(notice.getExpiresAt()).isNull();
    }

    @Test
    void updateNotice_없는공지_예외() {
        UUID noticeId = UUID.randomUUID();
        given(noticeRepository.findById(noticeId)).willReturn(Optional.empty());
        UpdateNoticeRequest request = new UpdateNoticeRequest();
        request.setTitle("수정 제목");

        assertThatThrownBy(() -> noticeService.updateNotice(
                noticeId,
                request
        )).isInstanceOf(NoticeNotFoundException.class);
    }

    @Test
    void deleteNotice_공지비활성화_성공() {
        Notice notice = createNotice(true);
        given(noticeRepository.findById(notice.getId())).willReturn(Optional.of(notice));

        noticeService.deleteNotice(notice.getId());

        assertThat(notice.isActive()).isFalse();
    }

    @Test
    void deleteNotice_없는공지_예외() {
        UUID noticeId = UUID.randomUUID();
        given(noticeRepository.findById(noticeId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> noticeService.deleteNotice(noticeId))
                .isInstanceOf(NoticeNotFoundException.class);
        then(noticeRepository).should(never()).delete(any());
    }

    private Notice createNotice(boolean active) {
        return createNotice(active, Instant.now().minusSeconds(60), null);
    }

    private Notice createNotice(boolean active, Instant publishedAtValue, Instant expiresAtValue) {
        try {
            var constructor = Notice.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Notice notice = constructor.newInstance();

            var id = Notice.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(notice, UUID.randomUUID());

            var title = Notice.class.getDeclaredField("title");
            title.setAccessible(true);
            title.set(notice, "테스트 공지");

            var content = Notice.class.getDeclaredField("content");
            content.setAccessible(true);
            content.set(notice, "테스트 내용");

            var category = Notice.class.getDeclaredField("category");
            category.setAccessible(true);
            category.set(notice, NoticeCategory.INFO);

            var isActive = Notice.class.getDeclaredField("isActive");
            isActive.setAccessible(true);
            isActive.set(notice, active);

            var publishedAt = Notice.class.getDeclaredField("publishedAt");
            publishedAt.setAccessible(true);
            publishedAt.set(notice, publishedAtValue);

            var expiresAt = Notice.class.getDeclaredField("expiresAt");
            expiresAt.setAccessible(true);
            expiresAt.set(notice, expiresAtValue);

            var createdAt = Notice.class.getDeclaredField("createdAt");
            createdAt.setAccessible(true);
            createdAt.set(notice, java.time.Instant.now());

            return notice;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
