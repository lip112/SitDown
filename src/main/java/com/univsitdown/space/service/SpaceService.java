package com.univsitdown.space.service;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.reservation.repository.ReservationRepository;
import com.univsitdown.space.domain.Space;
import com.univsitdown.space.domain.SpaceCategory;
import com.univsitdown.space.dto.CongestionPredictionResponse;
import com.univsitdown.space.dto.CreateSpaceRequest;
import com.univsitdown.space.dto.SpaceDetailResponse;
import com.univsitdown.space.dto.SpaceListItemResponse;
import com.univsitdown.space.exception.SpaceNotFoundException;
import com.univsitdown.space.repository.SeatRepository;
import com.univsitdown.space.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SpaceService {

    private final SpaceRepository spaceRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final FavoriteService favoriteService;

    /**
     * 캐시 키에 category·keyword·페이지 정보를 모두 포함해 필터 조합별로 독립 캐시를 유지한다.
     * 예약 생성/취소 시 space:list 전체를 evict하므로 availableSeats 수치가 즉시 반영된다.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "space:list",
               key = "#category + ':' + #keyword + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public PageResponse<SpaceListItemResponse> getSpaces(SpaceCategory category, String keyword, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(9));
        String keywordPattern = keyword != null ? "%" + keyword + "%" : null;
        Page<SpaceListItemResponse> page = spaceRepository
                .findByFilters(category, keywordPattern, pageable)
                .map(space -> toListItem(space, now));
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public SpaceDetailResponse getSpace(UUID id, UUID userId) {
        Space space = spaceRepository.findById(id)
                .orElseThrow(SpaceNotFoundException::new);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(9));
        int total = (int) seatRepository.countBySpaceIdAndIsEnabledTrue(id);
        int rows = seatRepository.findMaxRowBySpaceId(id);
        int columns = seatRepository.findMaxColBySpaceId(id);
        int occupied = (int) reservationRepository.countOccupiedBySpaceId(id, now);
        boolean isFav = userId != null && favoriteService.isFavorite(userId, id);
        return SpaceDetailResponse.from(space, total, total - occupied, rows, columns, isFav);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "space:congestion", key = "#id + ':' + #date")
    public CongestionPredictionResponse getCongestionPrediction(UUID id, LocalDate date) {
        Space space = spaceRepository.findById(id)
                .orElseThrow(SpaceNotFoundException::new);
        int totalSeats = (int) seatRepository.countBySpaceIdAndIsEnabledTrue(id);

        int openHour  = space.getOpenTime().getHour();
        int closeHour = space.getCloseTime().getHour();

        List<LocalDate> refDates = java.util.stream.IntStream.rangeClosed(1, 4)
                .mapToObj(w -> date.minusWeeks(w))
                .toList();

        List<CongestionPredictionResponse.HourlyItem> hourly = new ArrayList<>();
        for (int h = openHour; h < closeHour; h++) {
            double sum = 0;
            for (LocalDate ref : refDates) {
                LocalDateTime slotStart = ref.atTime(h, 0);
                LocalDateTime slotEnd   = ref.atTime(h + 1, 0);
                long occupied = reservationRepository.countOccupiedAtSlot(id, slotStart, slotEnd);
                sum += totalSeats > 0 ? (double) occupied / totalSeats : 0.0;
            }
            double avgRate = sum / refDates.size();
            hourly.add(new CongestionPredictionResponse.HourlyItem(
                    h, Math.round(avgRate * 100.0) / 100.0,
                    CongestionPredictionResponse.toLevel(avgRate)));
        }
        return new CongestionPredictionResponse(id.toString(), date.toString(), hourly);
    }

    @Transactional
    public SpaceDetailResponse createSpace(CreateSpaceRequest request) {
        Space space = Space.create(
                request.name(),
                request.floor(),
                request.category(),
                request.openTime(),
                request.closeTime(),
                request.maxReservationHours(),
                request.features(),
                request.thumbnailUrl()
        );
        return SpaceDetailResponse.from(spaceRepository.save(space));
    }

    private SpaceListItemResponse toListItem(Space space, LocalDateTime now) {
        int total = (int) seatRepository.countBySpaceIdAndIsEnabledTrue(space.getId());
        int occupied = (int) reservationRepository.countOccupiedBySpaceId(space.getId(), now);
        return SpaceListItemResponse.from(space, total, total - occupied);
    }
}
