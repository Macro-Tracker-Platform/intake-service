package com.olehprukhnytskyi.macrotrackerintakeservice.dto;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntakeSyncResponseDto {
    private List<IntakeSyncItemDto> data;
    private Instant nextSyncTime;
    private boolean hasMore;
}
