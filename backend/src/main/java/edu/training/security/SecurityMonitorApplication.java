package edu.training.security;

import edu.training.security.mapper.AlarmMapper;
import edu.training.security.mapper.DeviceMapper;
import edu.training.security.model.Alarm;
import edu.training.security.model.Device;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.LocalDateTime;

/**
 * 智能安防远程监控与告警平台 - 主启动类
 *
 * 启动时写入演示数据（设备、告警），便于学习和演示；避免重复写入。
 * 说明：时间统一回拨 10 天（配合演示的“历史数据”观感）。
 */
@SpringBootApplication
public class SecurityMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecurityMonitorApplication.class, args);
    }

    @Bean
    CommandLineRunner seed(DeviceMapper devices, AlarmMapper alarms) {
        return args -> {
            if (devices.selectCount(null) > 0) {
                return;
            }
            devices.insert(new Device(null, "CAM-001", "西门摄像头", "摄像头", "A区西门", "在线"));
            devices.insert(new Device(null, "CAM-002", "仓库摄像头", "摄像头", "B区仓库", "在线"));
            devices.insert(new Device(null, "DOOR-001", "教学楼门禁", "门禁", "教学楼", "在线"));
            LocalDateTime t = LocalDateTime.now().minusDays(10);
            alarms.insert(new Alarm(null, "人员闯入", "A区西门", "高", "待处置", "AI视觉分析", "AI识别到人员进入限制区域", t.minusMinutes(5)));
            alarms.insert(new Alarm(null, "人员聚集", "教学楼南侧", "中", "已关闭", "AI视觉分析", "AI识别到人员聚集", t.minusMinutes(9)));
            alarms.insert(new Alarm(null, "烟火异常", "B区仓库", "高", "待处置", "AI视觉分析", "检测到疑似烟火异常", t.minusMinutes(13)));
        };
    }
}
