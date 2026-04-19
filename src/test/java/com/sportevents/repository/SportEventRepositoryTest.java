package com.sportevents.repository;

import com.sportevents.model.SportEvent;
import com.sportevents.model.SportEventStatus;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("SportEventRepository — JPA Integration")
class SportEventRepositoryTest {

    @Autowired
    private SportEventRepository repo;

    private SportEvent save(String sport, SportEventStatus status, LocalDateTime start) {
        SportEvent e = new SportEvent("Match", sport, start);
        e.setStatus(status);
        return repo.saveAndFlush(e);
    }

    @BeforeEach
    void clean() { repo.deleteAll(); }

    @Test @DisplayName("saved entity is retrievable by id")
    void saveAndFindById() {
        SportEvent e = save("football", SportEventStatus.INACTIVE, LocalDateTime.now().plusHours(1));
        assertThat(repo.findById(e.getId())).isPresent();
    }

    @Test @DisplayName("findAllFiltered — no filters returns all sorted by startTime")
    void noFilter_allSortedByStartTime() {
        save("football", SportEventStatus.INACTIVE, LocalDateTime.now().plusHours(3));
        save("hockey",   SportEventStatus.INACTIVE, LocalDateTime.now().plusHours(1));
        save("tennis",   SportEventStatus.INACTIVE, LocalDateTime.now().plusHours(2));

        Page<SportEvent> page = repo.findAllFiltered(null, null, PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent().get(0).getSport()).isEqualTo("hockey");
        assertThat(page.getContent().get(2).getSport()).isEqualTo("football");
    }

    @Test @DisplayName("findAllFiltered — filter by status")
    void filterByStatus() {
        save("football", SportEventStatus.INACTIVE, LocalDateTime.now().plusHours(1));
        save("hockey",   SportEventStatus.ACTIVE,   LocalDateTime.now().plusHours(1));

        Page<SportEvent> active = repo.findAllFiltered(SportEventStatus.ACTIVE, null, PageRequest.of(0, 10));
        assertThat(active.getTotalElements()).isEqualTo(1);
        assertThat(active.getContent().get(0).getSport()).isEqualTo("hockey");
    }

    @Test @DisplayName("findAllFiltered — filter by sport (case-insensitive)")
    void filterBySport_caseInsensitive() {
        save("Football", SportEventStatus.INACTIVE, LocalDateTime.now().plusHours(1));
        save("hockey",   SportEventStatus.INACTIVE, LocalDateTime.now().plusHours(1));

        assertThat(repo.findAllFiltered(null, "football", PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
        assertThat(repo.findAllFiltered(null, "FOOTBALL", PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
    }

    @Test @DisplayName("findAllFiltered — combined status + sport filter")
    void filterByStatusAndSport() {
        save("football", SportEventStatus.ACTIVE,   LocalDateTime.now().plusHours(1));
        save("football", SportEventStatus.INACTIVE, LocalDateTime.now().plusHours(1));
        save("hockey",   SportEventStatus.ACTIVE,   LocalDateTime.now().plusHours(1));

        Page<SportEvent> result = repo.findAllFiltered(SportEventStatus.ACTIVE, "football", PageRequest.of(0, 10));
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test @DisplayName("pagination returns correct page")
    void pagination() {
        for (int i = 1; i <= 5; i++) {
            save("football", SportEventStatus.INACTIVE, LocalDateTime.now().plusHours(i));
        }
        Page<SportEvent> page1 = repo.findAllFiltered(null, null, PageRequest.of(0, 2));
        Page<SportEvent> page2 = repo.findAllFiltered(null, null, PageRequest.of(1, 2));

        assertThat(page1.getContent()).hasSize(2);
        assertThat(page2.getContent()).hasSize(2);
        assertThat(page1.getTotalElements()).isEqualTo(5);
        assertThat(page1.getTotalPages()).isEqualTo(3);
    }

    @Test @DisplayName("@Version increments on each save")
    void versionIncrementsOnSave() {
        SportEvent e = save("football", SportEventStatus.INACTIVE, LocalDateTime.now().plusHours(1));
        Long v1 = e.getVersion();
        e.setStatus(SportEventStatus.ACTIVE);
        SportEvent updated = repo.saveAndFlush(e);
        assertThat(updated.getVersion()).isGreaterThan(v1);
    }
}
