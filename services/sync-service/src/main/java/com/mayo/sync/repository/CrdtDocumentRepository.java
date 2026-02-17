package com.mayo.sync.repository;

import com.mayo.sync.entity.CrdtDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CrdtDocumentRepository extends JpaRepository<CrdtDocument, UUID> {

    Optional<CrdtDocument> findByDocumentId(String documentId);

    List<CrdtDocument> findByDocumentType(String documentType);

    @Query("SELECT d FROM CrdtDocument d WHERE d.documentId IN :documentIds")
    List<CrdtDocument> findByDocumentIds(@Param("documentIds") List<String> documentIds);

    boolean existsByDocumentId(String documentId);
}