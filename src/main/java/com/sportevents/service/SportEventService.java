package com.sportevents.service;

import com.sportevents.dto.CreateEventRequest;
import com.sportevents.dto.PagedResponse;
import com.sportevents.dto.SportEventResponse;
import com.sportevents.exception.InvalidStatusTransitionException;
import com.sportevents.exception.SportEventNotFoundException;
import com.sportevents.model.SportEvent;
import com.sportevents.model.SportEventStatus;
import com.sportevents.publisher.EventPublisher;
import com.sportevents.repository.SportEventRepository;
import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SportEventService {

    private final SportEventRepository repository;
    private final List<EventPublisher> publishers;   // SSE + any future channels

    public SportEventService(SportEventRepository repository, List<EventPublisher> publishers) {
        this.repository = repository;
        this.publishers = publishers;
    }

    // -----------------------------------------------------------------------
    // Create
    // -----------------------------------------------------------------------

    @Transactional
    public SportEventResponse createEvent(CreateEventRequest request) {
        SportEvent event = new SportEvent(
                request.getName(),
                request.getSport(),
                request.getStartTime());
        repository.save(event);

        SportEventResponse response = SportEventResponse.from(event);
        publishers.forEach(p -> p.publishCreated(response));  // broadcast EVENT_CREATED
        return response;
    }

    // -----------------------------------------------------------------------
    // Read — paginated
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PagedResponse<SportEventResponse> getAllEvents(
            SportEventStatus status, String sport, int page, int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<SportEvent> resultPage = repository.findAllFiltered(status, sport, pageable);

        List<SportEventResponse> content = resultPage.getContent()
                .stream()
                .map(SportEventResponse::from)
                .toList();

        return new PagedResponse<>(
                content,
                resultPage.getNumber(),
                resultPage.getSize(),
                resultPage.getTotalElements(),
                resultPage.getTotalPages());
    }

    @Transactional(readOnly = true)
    public SportEventResponse getEventById(String id) {
        return SportEventResponse.from(findOrThrow(id));
    }

    // -----------------------------------------------------------------------
    // Update status  (optimistic-lock protected)
    // -----------------------------------------------------------------------

    @Transactional
    public SportEventResponse updateStatus(String id, SportEventStatus newStatus) {
        try {
            SportEvent event = findOrThrow(id);
            validateTransition(event, newStatus);
            event.setStatus(newStatus);
            repository.saveAndFlush(event);          // flush forces version check immediately

            SportEventResponse response = SportEventResponse.from(event);
            publishers.forEach(p -> p.publishUpdated(response));
            return response;

        } catch (OptimisticLockException | OptimisticLockingFailureException ex) {
            throw new InvalidStatusTransitionException(
                    "Event " + id + " was modified concurrently. Please retry.");
        }
    }

    // -----------------------------------------------------------------------
    // Status transition rules
    // -----------------------------------------------------------------------

    private void validateTransition(SportEvent event, SportEventStatus newStatus) {
        SportEventStatus current = event.getStatus();

        if (current == newStatus) {
            throw new InvalidStatusTransitionException("Event is already in status: " + current);
        }
        if (current == SportEventStatus.FINISHED) {
            throw new InvalidStatusTransitionException("Cannot change status of a FINISHED event.");
        }
        if (current == SportEventStatus.INACTIVE && newStatus == SportEventStatus.FINISHED) {
            throw new InvalidStatusTransitionException("Cannot transition directly from INACTIVE to FINISHED.");
        }
        if (current == SportEventStatus.ACTIVE && newStatus == SportEventStatus.INACTIVE) {
            throw new InvalidStatusTransitionException("Cannot revert an ACTIVE event back to INACTIVE.");
        }
        if (current == SportEventStatus.INACTIVE && newStatus == SportEventStatus.ACTIVE
                && event.getStartTime().isBefore(LocalDateTime.now())) {
            throw new InvalidStatusTransitionException("Cannot activate an event whose start time is in the past.");
        }
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private SportEvent findOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new SportEventNotFoundException(id));
    }
}
