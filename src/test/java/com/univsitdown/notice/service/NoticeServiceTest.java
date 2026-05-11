package com.univsitdown.notice.service;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.notice.domain.Notice;
import com.univsitdown.notice.domain.NoticeCategory;
import com.univsitdown.notice.dto.NoticeDetailResponse;
import com.univsitdown.notice.dto.NoticeListItemResponse;
import com.univsitdown.notice.exception.NoticeNotFoundException;
import com.univsitdown.notice.repository.NoticeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

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
