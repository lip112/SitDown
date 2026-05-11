package com.univsitdown.notice.service;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.notice.domain.Notice;
import com.univsitdown.notice.domain.NoticeCategory;
import com.univsitdown.notice.dto.NoticeDetailResponse;
import com.univsitdown.notice.dto.NoticeListItemResponse;
import com.univsitdown.notice.exception.NoticeNotFoundException;
import com.univsitdown.notice.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeRepository noticeRepository;

    @Transactional(readOnly = true)
    public PageResponse<NoticeListItemResponse> getNotices(String categoryParam, Pageable pageable) {
        NoticeCategory category = (categoryParam == null || categoryParam.equalsIgnoreCase("ALL"))
                ? null
                : NoticeCategory.valueOf(categoryParam);
        return PageResponse.from(
                noticeRepository.findActiveByCategory(category, pageable)
                        .map(NoticeListItemResponse::from)
        );
    }

    @Transactional(readOnly = true)
    public NoticeDetailResponse getNotice(UUID id) {
        return noticeRepository.findById(id)
                .filter(Notice::isActive)
                .map(NoticeDetailResponse::from)
                .orElseThrow(NoticeNotFoundException::new);
    }
}
