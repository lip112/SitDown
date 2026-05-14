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
        given(noticeRepository.findActiveByCategory(isNull(), any()))
                .willReturn(new PageImpl<>(List.of()));
        PageResponse<NoticeListItemResponse> result = noticeService.getNotices(null, PageRequest.of(0, 20));
        assertThat(result.content()).isEmpty();
    }

    @Test
    void getNotices_ALL_카테고리_전체반환() {
        given(noticeRepository.findActiveByCategory(isNull(), any()))
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
        then(noticeRepository).should().save(any(Notice.class));
    }

    @Test
    void updateNotice_공지수정_성공() {
        Notice notice = createNotice(true);
        given(noticeRepository.findById(notice.getId())).willReturn(Optional.of(notice));

        NoticeDetailResponse response = noticeService.updateNotice(
                notice.getId(),
                new UpdateNoticeRequest("수정 제목", null, NoticeCategory.EVENT, null, null)
        );

        assertThat(response.title()).isEqualTo("수정 제목");
        assertThat(response.category()).isEqualTo("EVENT");
    }

    @Test
    void updateNotice_없는공지_예외() {
        UUID noticeId = UUID.randomUUID();
        given(noticeRepository.findById(noticeId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> noticeService.updateNotice(
                noticeId,
                new UpdateNoticeRequest("수정 제목", null, null, null, null)
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
            publishedAt.set(notice, java.time.Instant.now());

            var createdAt = Notice.class.getDeclaredField("createdAt");
            createdAt.setAccessible(true);
            createdAt.set(notice, java.time.Instant.now());

            return notice;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
