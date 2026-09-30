package edu.training.security.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 讲义§9 告警映射规则（优先级自上而下）：
 * fire/smoke → 烟火异常/高
 * person>=2 → 人员聚集/高
 * person==1 → 人员闯入/中
 * car/truck/bus → 车辆异常/中
 * dog/horse/giraffe/elephant等 → 动物进入/中
 * suitcase/box/bowl/cup等 → 物品异常/中
 * 其它 → 目标识别异常（不入库、不推送）
 */
@Component
public class AlarmRuleMapper {

    public record Event(String type, String level, String detail, boolean skip) {
    }

    private static final Set<String> FIRE = Set.of("fire", "smoke");
    private static final Set<String> VEHICLE = Set.of("car", "truck", "bus");
    private static final Set<String> ANIMAL = Set.of("dog", "horse", "giraffe", "elephant",
            "cat", "bird", "sheep", "cow", "bear", "zebra", "monkey");
    private static final Set<String> OBJECT = Set.of("suitcase", "box", "bowl", "cup",
            "backpack", "handbag", "bottle");

    public Event map(List<Map<String, Object>> objects) {
        int personCount = 0;
        StringBuilder personTags = new StringBuilder();
        StringBuilder fireTags = new StringBuilder();
        StringBuilder vehicleTags = new StringBuilder();
        StringBuilder animalTags = new StringBuilder();
        StringBuilder objectTags = new StringBuilder();

        for (Map<String, Object> o : objects) {
            String cls = String.valueOf(o.get("class"));
            double conf = ((Number) o.getOrDefault("conf", 0.0)).doubleValue();
            String tag = cls + "(" + Math.round(conf * 100) + "%)";
            switch (cls) {
                case "person" -> {
                    personCount++;
                    personTags.append(personTags.isEmpty() ? tag : "," + tag);
                }
                case "fire", "smoke" -> fireTags.append(fireTags.isEmpty() ? tag : "," + tag);
                default -> {
                    if (VEHICLE.contains(cls)) {
                        vehicleTags.append(vehicleTags.isEmpty() ? tag : "," + tag);
                    } else if (ANIMAL.contains(cls)) {
                        animalTags.append(animalTags.isEmpty() ? tag : "," + tag);
                    } else if (OBJECT.contains(cls)) {
                        objectTags.append(objectTags.isEmpty() ? tag : "," + tag);
                    }
                }
            }
        }

        if (!fireTags.isEmpty()) {
            return new Event("烟火异常", "高", "检测到疑似烟火异常：" + fireTags, false);
        }
        if (personCount >= 2) {
            return new Event("人员聚集", "高", "AI识别到" + personCount + "人聚集：" + personTags, false);
        }
        if (personCount == 1) {
            return new Event("人员闯入", "中", "AI识别到人员进入限制区域：" + personTags, false);
        }
        if (!vehicleTags.isEmpty()) {
            return new Event("车辆异常", "中", "AI识别结果：" + vehicleTags, false);
        }
        if (!animalTags.isEmpty()) {
            return new Event("动物进入", "中", "AI识别结果：" + animalTags, false);
        }
        if (!objectTags.isEmpty()) {
            return new Event("物品异常", "中", "AI识别结果：" + objectTags, false);
        }
        return new Event("目标识别异常", null, "AI识别结果未匹配任何告警规则", true);
    }
}
