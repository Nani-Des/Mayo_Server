package com.mayo.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeltaChangeDto {

    @NotBlank
    private String recordId;

    @NotBlank
    private String recordType;

    @NotNull
    private ChangeType changeType;

    @NotNull
    private Long version;

    @NotNull
    private LocalDateTime timestamp;

    private String data;

    // CRDT support fields
    private String documentId;
    private byte[] crdtState;
    private Boolean isCrdtEnabled = false;

    public enum ChangeType {
        CREATE,
        UPDATE,
        DELETE
    }
}