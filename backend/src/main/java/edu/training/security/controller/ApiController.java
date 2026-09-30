package edu.training.security.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.training.security.mapper.AiRecordMapper;
import edu.training.security.mapper.AlarmMapper;
import edu.training.security.mapper.DeviceMapper;
import edu.training.security.model.AiRecord;
import edu.training.security.model.Alarm;
import edu.training.security.model.Device;
import edu.training.security.service.AiService;
import edu.training.security.service.AlarmRuleMapper;
import edu.training.security.service.AlertWebSocketHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "智能安防远程监控与告警平台")
@RestController
@RequestMapping("/api")
public class ApiController {

    private final DeviceMapper deviceMapper;
    private final AlarmMapper alarmMapper;
    private final AiRecordMapper aiRecordMapper;
    private final AiService aiService;
    private final AlarmRuleMapper ruleMapper;
    private final AlertWebSocketHandler websocket;
    private final ObjectMapper objectMapper;

    @Value("${ai.watch-dir}")
    private String watchDir;

    public ApiController(DeviceMapper deviceMapper, AlarmMapper alarmMapper, AiRecordMapper aiRecordMapper,
                         AiService aiService, AlarmRuleMapper ruleMapper, AlertWebSocketHandler websocket,
                         ObjectMapper objectMapper) {
        this.deviceMapper = deviceMapper;
        this.alarmMapper = alarmMapper;
        this.aiRecordMapper = aiRecordMapper;
        this.aiService = aiService;
        this.ruleMapper = ruleMapper;
        this.websocket = websocket;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "监控汇总：在线设备/告警/待处置/AI事件")
    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        List<Device> devices = deviceMapper.selectList(null);
        long online = devices.stream().filter(d -> "在线".equals(d.getStatus())).count();
        List<Alarm> alarms = alarmMapper.selectList(new QueryWrapper<Alarm>().orderByDesc("event_time"));
        long pending = alarms.stream().filter(a -> "待处置".equals(a.getStatus())).count();
        long processing = alarms.stream().filter(a -> "处置中".equals(a.getStatus())).count();
        long aiEvents = aiRecordMapper.selectCount(null);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deviceTotal", devices.size());
        m.put("deviceOnline", online);
        m.put("onlineRate", devices.isEmpty() ? 0 : Math.round(online * 1000.0 / devices.size()) / 10.0);
        m.put("alarmTotal", alarms.size());
        m.put("pending", pending);
        m.put("processing", processing);
        m.put("aiEvents", aiEvents);
        m.put("wsClients", websocket.clientCount());
        m.put("latest", alarms.stream().limit(10).toList());
        return m;
    }

    @Operation(summary = "告警列表（按事件时间倒序）")
    @GetMapping("/alarms")
    public List<Alarm> alarms() {
        return alarmMapper.selectList(new QueryWrapper<Alarm>().orderByDesc("event_time"));
    }

    @Operation(summary = "设备列表")
    @GetMapping("/devices")
    public List<Device> devices() {
        return deviceMapper.selectList(null);
    }

    @Operation(summary = "标记处置中")
    @PostMapping("/alarms/{id}/process")
    public Map<String, Object> process(@PathVariable Long id) {
        Alarm a = alarmMapper.selectById(id);
        if (a == null) {
            return Map.of("status", "error", "message", "告警不存在");
        }
        a.setStatus("处置中");
        alarmMapper.updateById(a);
        return Map.of("status", "ok", "id", id, "state", a.getStatus());
    }

    @Operation(summary = "关闭告警（可附处置说明）")
    @PostMapping("/alarms/{id}/handle")
    public Map<String, Object> handle(@PathVariable Long id,
                                      @RequestParam(required = false) String note) {
        Alarm a = alarmMapper.selectById(id);
        if (a == null) {
            return Map.of("status", "error", "message", "告警不存在");
        }
        a.setStatus("已关闭");
        if (note != null && !note.isBlank()) {
            String detail = a.getDetail() == null ? "" : a.getDetail();
            a.setDetail(detail + "｜处置备注：" + note.trim());
        }
        alarmMapper.updateById(a);
        return Map.of("status", "ok", "id", id, "state", a.getStatus());
    }

    @Operation(summary = "接收识别服务推送（ai-watch 链路）：检测全量入库+告警+WebSocket广播")
    @PostMapping("/ai/event")
    public Map<String, Object> aiEvent(@RequestBody Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> objects = (List<Map<String, Object>>) payload.getOrDefault("objects", List.of());
        String image = String.valueOf(payload.getOrDefault("image", ""));
        String imagePath = String.valueOf(payload.getOrDefault("imagePath", ""));
        String area = resolveArea(payload, image);

        AlarmRuleMapper.Event e = ruleMapper.map(objects);
        AiRecord record = saveRecord(image, imagePath, area, objects, e);
        if (e.skip()) {
            return Map.of("status", "skipped", "recordId", record.getId());
        }
        Alarm alarm = new Alarm(null, e.type(), area, e.level(), "待处置", "AI视觉分析", e.detail(), LocalDateTime.now());
        alarmMapper.insert(alarm);
        websocket.broadcast("{\"event\":\"new-alarm\",\"type\":\"" + e.type() + "\"}");
        return Map.of("status", "created", "alarm", alarm, "recordId", record.getId());
    }

    @Operation(summary = "按需触发识别：上传图片 -> 调用识别服务 /detect -> 检测入库+告警+广播")
    @PostMapping("/ai/analyze")
    public Map<String, Object> analyze(@RequestParam("image") MultipartFile image,
                                       @RequestParam(required = false) String area) throws IOException {
        File dir = new File(watchDir, "tmp");
        dir.mkdirs();
        File saved = new File(dir, System.currentTimeMillis() + "_" + image.getOriginalFilename());
        image.transferTo(saved);

        List<Map<String, Object>> objects = aiService.detect(saved.getAbsolutePath());
        String realArea = (area != null && !area.isBlank()) ? area : resolveArea(Map.of(), image.getOriginalFilename());
        AlarmRuleMapper.Event e = ruleMapper.map(objects);
        AiRecord record = saveRecord(image.getOriginalFilename(), saved.getAbsolutePath(), realArea, objects, e);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("file", image.getOriginalFilename());
        resp.put("objects", objects);
        resp.put("recordId", record.getId());
        if (e.skip()) {
            resp.put("status", "skipped");
            resp.put("alarm", null);
            return resp;
        }
        Alarm alarm = new Alarm(null, e.type(), realArea, e.level(), "待处置", "AI视觉分析", e.detail(), LocalDateTime.now());
        alarmMapper.insert(alarm);
        websocket.broadcast("{\"event\":\"new-alarm\",\"type\":\"" + e.type() + "\"}");
        resp.put("status", "created");
        resp.put("alarm", alarm);
        return resp;
    }

    @Operation(summary = "AI检测记录列表（按识别时间倒序，含未成警的兜底记录）")
    @GetMapping("/ai/records")
    public List<AiRecord> aiRecords(@RequestParam(defaultValue = "100") Integer limit) {
        return aiRecordMapper.selectList(new QueryWrapper<AiRecord>()
                .orderByDesc("event_time").orderByDesc("id").last("LIMIT " + Math.max(1, Math.min(limit, 500))));
    }

    @Operation(summary = "按记录id读取被检测图片（限定ai-watch目录内，防路径穿越）")
    @GetMapping("/ai/image/{id}")
    public ResponseEntity<byte[]> aiImage(@PathVariable Long id) throws IOException {
        AiRecord rec = aiRecordMapper.selectById(id);
        if (rec == null || rec.getImagePath() == null || rec.getImagePath().isBlank()) {
            return ResponseEntity.notFound().build();
        }
        java.nio.file.Path base = new File(watchDir).getCanonicalFile().toPath();
        java.nio.file.Path target = new File(rec.getImagePath()).getCanonicalFile().toPath();
        if (!target.startsWith(base) || !java.nio.file.Files.isRegularFile(target)) {
            return ResponseEntity.status(403).build();
        }
        byte[] bytes = java.nio.file.Files.readAllBytes(target);
        String name = target.getFileName().toString().toLowerCase();
        String ctype = name.endsWith(".png") ? "image/png"
                : name.endsWith(".gif") ? "image/gif" : "image/jpeg";
        return ResponseEntity.ok()
                .header("Content-Type", ctype)
                .header("Cache-Control", "max-age=3600")
                .body(bytes);
    }

    /**
     * 下午要求1：检测结果（无论是否成警）一律写入 ai_record，并主动广播 new-record。
     */
    private AiRecord saveRecord(String image, String imagePath, String area,
                                List<Map<String, Object>> objects, AlarmRuleMapper.Event e) {
        String json;
        try {
            json = objectMapper.writeValueAsString(objects);
        } catch (Exception ex) {
            json = "[]";
        }
        AiRecord record = new AiRecord(null, image, imagePath, area, json,
                objects.size(), e.type(), !e.skip(), LocalDateTime.now());
        aiRecordMapper.insert(record);
        websocket.broadcast("{\"event\":\"new-record\",\"image\":\"" + image
                + "\",\"type\":\"" + e.type() + "\",\"alarmed\":" + !e.skip() + "}");
        return record;
    }

    /**
     * 区域解析：优先取 payload.area；否则按测试图命名规则 "01_A区西门_单人闯入.png" 提取；兜底"未知区域"。
     */
    private String resolveArea(Map<String, Object> payload, String imageName) {
        Object area = payload.get("area");
        if (area != null && !String.valueOf(area).isBlank()) {
            return String.valueOf(area);
        }
        if (imageName != null) {
            String[] parts = imageName.split("_");
            if (parts.length >= 2 && !parts[1].isBlank()) {
                return parts[1];
            }
        }
        return "未知区域";
    }
}
