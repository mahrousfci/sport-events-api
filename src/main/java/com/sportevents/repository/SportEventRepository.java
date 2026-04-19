package com.sportevents.repository;

import com.sportevents.model.SportEvent;
import com.sportevents.model.SportEventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SportEventRepository extends JpaRepository<SportEvent, String> {

    /**
     * Filtered + paginated list. Null parameters are treated as "no filter".
     * JPQL coalescing pattern: (param IS NULL OR field = param).
     */
    @Query("""
           SELECT e FROM SportEvent e
           WHERE (:status IS NULL OR e.status = :status)
             AND (:sport  IS NULL OR LOWER(e.sport) = LOWER(:sport))
           ORDER BY e.startTime ASC
           """)
    Page<SportEvent> findAllFiltered(
            @Param("status") SportEventStatus status,
            @Param("sport")  String sport,
            Pageable pageable);
}
