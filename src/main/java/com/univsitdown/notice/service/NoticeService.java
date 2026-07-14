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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
                noticeRepository.findVisibleByCategory(category, Instant.now(), pageable)
                        .map(NoticeListItemResponse::from)
        );
    }

    @Transactional(readOnly = true)
    public NoticeDetailResponse getNotice(UUID id) {
        return noticeRepository.findById(id)
                .filter(notice -> notice.isVisibleAt(Instant.now()))
                .map(NoticeDetailResponse::from)
                .orElseThrow(NoticeNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public PageResponse<NoticeListItemResponse> getAdminNotices(String categoryParam, Pageable pageable) {
        NoticeCategory category = (categoryParam == null || categoryParam.equalsIgnoreCase("ALL"))
                ? null
                : NoticeCategory.valueOf(categoryParam);
        return PageResponse.from(
                noticeRepository.findActiveByCategory(category, pageable)
                        .map(NoticeListItemResponse::from)
        );
    }

    @Transactional(readOnly = true)
    public NoticeDetailResponse getAdminNotice(UUID id) {
        return NoticeDetailResponse.from(findActiveNotice(id));
    }

    @Transactional
    public NoticeDetailResponse createNotice(CreateNoticeRequest request) {
        Notice notice = Notice.create(
                request.title(),
                request.content(),
                request.category(),
                request.publishedAt(),
                request.expiresAt()
        );
        return NoticeDetailResponse.from(noticeRepository.save(notice));
    }

    @Transactional
    public NoticeDetailResponse updateNotice(UUID id, UpdateNoticeRequest request) {
        Notice notice = findActiveNotice(id);
        notice.updateByAdmin(
                request.title(),
                request.content(),
                request.category(),
                request.publishedAt(),
                request.isExpiresAtProvided(),
                request.expiresAt()
        );
        return NoticeDetailResponse.from(notice);
    }

    @Transactional
    public void deleteNotice(UUID id) {
        findActiveNotice(id).deactivate();
    }

    private Notice findActiveNotice(UUID id) {
        return noticeRepository.findById(id)
                .filter(Notice::isActive)
                .orElseThrow(NoticeNotFoundException::new);
    }
}
