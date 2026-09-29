package com.crackit.ai.client;

import com.crackit.ai.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AiServiceClient {

    private final RestTemplate restTemplate;

    @Value("${ai.service.url:http://localhost:8000/api/ai}")
    private String aiServiceBaseUrl;

    public JDAnalysisResponse analyzeJd(JDAnalysisRequest request) {
        return restTemplate.postForObject(aiServiceBaseUrl + "/analyze-jd", request, JDAnalysisResponse.class);
    }

    public ResumeTailoringResponse tailorResume(ResumeTailoringRequest request) {
        return restTemplate.postForObject(aiServiceBaseUrl + "/tailor-resume", request, ResumeTailoringResponse.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> parseResume(byte[] pdfBytes, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource resource = new ByteArrayResource(pdfBytes) {
            @Override public String getFilename() { return filename != null ? filename : "resume.pdf"; }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.exchange(aiServiceBaseUrl + "/parse-resume", HttpMethod.POST, entity, Map.class);
        return response.getBody();
    }

    public byte[] generateResumePdf(Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        ResponseEntity<byte[]> response = restTemplate.exchange(aiServiceBaseUrl + "/generate-resume-pdf", HttpMethod.POST, entity, byte[].class);
        return response.getBody();
    }

    public Map<String, Object> generateInterviewPrep(Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                aiServiceBaseUrl + "/generate-interview-prep", HttpMethod.POST, entity, Map.class);
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> interviewChat(Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                aiServiceBaseUrl + "/interview-chat", HttpMethod.POST, entity, Map.class);
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> generateRoadmap(Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                aiServiceBaseUrl + "/generate-roadmap", HttpMethod.POST, entity, Map.class);
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> enhanceBullet(Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                aiServiceBaseUrl + "/enhance-bullet", HttpMethod.POST, entity, Map.class);
        return response.getBody();
    }
}