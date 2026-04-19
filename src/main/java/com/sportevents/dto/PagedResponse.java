package com.sportevents.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Paginated response wrapper")
public class PagedResponse<T> {

    @Schema(description = "Items on this page")
    private final List<T> content;

    @Schema(description = "Zero-based current page number", example = "0")
    private final int page;

    @Schema(description = "Page size requested", example = "20")
    private final int size;

    @Schema(description = "Total number of matching items", example = "42")
    private final long totalElements;

    @Schema(description = "Total number of pages", example = "3")
    private final int totalPages;

    public PagedResponse(List<T> content, int page, int size,
                         long totalElements, int totalPages) {
        this.content       = content;
        this.page          = page;
        this.size          = size;
        this.totalElements = totalElements;
        this.totalPages    = totalPages;
    }

    public List<T> getContent()      { return content; }
    public int     getPage()         { return page; }
    public int     getSize()         { return size; }
    public long    getTotalElements(){ return totalElements; }
    public int     getTotalPages()   { return totalPages; }
}
