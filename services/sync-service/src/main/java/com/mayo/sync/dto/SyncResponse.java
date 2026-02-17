package com.mayo.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncResponse {

    private String status;
    private List<ConflictDto> conflicts;
    private List<DeltaChangeDto> serverChanges;
    private Long newVersion;
}