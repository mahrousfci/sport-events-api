package com.sportevents.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sport_events")
public class SportEvent {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String sport;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SportEventStatus status;

    @Column(nullable = false)
    private LocalDateTime startTime;

    /**
     * Optimistic locking version — prevents concurrent status transitions
     * from overwriting each other silently.
     */
    @Version
    private Long version;

    protected SportEvent() {}   // JPA requires a no-arg constructor

    public SportEvent(String name, String sport, LocalDateTime startTime) {
        this.id        = UUID.randomUUID().toString();
        this.name      = name;
        this.sport     = sport;
        this.status    = SportEventStatus.INACTIVE;
        this.startTime = startTime;
    }

    // Getters
    public String          getId()        { return id; }
    public String          getName()      { return name; }
    public String          getSport()     { return sport; }
    public SportEventStatus getStatus()   { return status; }
    public LocalDateTime   getStartTime() { return startTime; }
    public Long            getVersion()   { return version; }

    // Setters (only mutable fields)
    public void setName(String name)              { this.name = name; }
    public void setSport(String sport)            { this.sport = sport; }
    public void setStatus(SportEventStatus status){ this.status = status; }
    public void setStartTime(LocalDateTime t)     { this.startTime = t; }
}
