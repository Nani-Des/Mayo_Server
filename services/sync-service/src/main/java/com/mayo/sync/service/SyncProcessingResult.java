package com.mayo.sync.service;

import com.mayo.sync.entity.Conflict;

import java.util.List;

/**
 * Result of processing client sync changes
 */
public class SyncProcessingResult {

    private final List<Conflict> conflicts;
    private final List<String> processedCrdtDocuments;

    public SyncProcessingResult(List<Conflict> conflicts, List<String> processedCrdtDocuments) {
        this.conflicts = conflicts;
        this.processedCrdtDocuments = processedCrdtDocuments;
    }

    public List<Conflict> getConflicts() {
        return conflicts;
    }

    public List<String> getProcessedCrdtDocuments() {
        return processedCrdtDocuments;
    }
}