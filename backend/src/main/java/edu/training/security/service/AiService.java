package edu.training.security.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class AiService {

    @Value("${ai.service-url}")
    private String aiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 讲义§5.3：平台调用识别服务 GET /detect?url=xxx -> {objects:[{class,conf}]}
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> detect(String imagePathOrUrl) {
        Map<String, Object> resp = restTemplate.getForObject(
                aiUrl + "/detect?url={u}", Map.class, imagePathOrUrl);
        if (resp == null || resp.get("objects") == null) {
            return List.of();
        }
        return (List<Map<String, Object>>) resp.get("objects");
    }
}
