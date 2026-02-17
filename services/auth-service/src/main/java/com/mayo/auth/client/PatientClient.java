package com.mayo.auth.client;

import com.mayo.common.core.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "patient-record-service", url = "${app.services.patient-record.url:http://localhost:8082}")
public interface PatientClient {

    @PostMapping("/api/v1/patient-records")
    ApiResponse<PatientDto> createPatientRecord(@RequestBody PatientDto patientDto);
}
