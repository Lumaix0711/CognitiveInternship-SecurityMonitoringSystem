package edu.training.security.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AlarmRuleMapperTest {

    private final AlarmRuleMapper mapper = new AlarmRuleMapper();

    private List<Map<String, Object>> objs(Object... pairs) {
        var list = new java.util.ArrayList<Map<String, Object>>();
        for (int i = 0; i < pairs.length; i += 2) {
            list.add(Map.of("class", pairs[i], "conf", pairs[i + 1]));
        }
        return list;
    }

    @Test
    void 无人无异常_兜底目标识别异常不入库() {
        var e = mapper.map(objs("traffic light", 0.9));
        assertEquals("目标识别异常", e.type());
        assertTrue(e.skip());
    }

    @Test
    void 空objects_兜底skip() {
        assertTrue(mapper.map(List.of()).skip());
    }

    @Test
    void 单人_人员闯入_中() {
        var e = mapper.map(objs("person", 0.88));
        assertEquals("人员闯入", e.type());
        assertEquals("中", e.level());
        assertFalse(e.skip());
    }

    @Test
    void 边界两人_人员聚集_高() {
        var e = mapper.map(objs("person", 0.9, "person", 0.8));
        assertEquals("人员聚集", e.type());
        assertEquals("高", e.level());
        assertTrue(e.detail().contains("2人"));
    }

    @Test
    void 三人_仍为人员聚集() {
        var e = mapper.map(objs("person", 0.9, "person", 0.8, "person", 0.7));
        assertEquals("人员聚集", e.type());
        assertEquals("高", e.level());
    }

    @Test
    void 烟火优先级最高_压过人员() {
        var e = mapper.map(objs("person", 0.9, "person", 0.8, "smoke", 0.75));
        assertEquals("烟火异常", e.type());
        assertEquals("高", e.level());
    }

    @Test
    void fire类_烟火异常() {
        var e = mapper.map(objs("fire", 0.6));
        assertEquals("烟火异常", e.type());
    }

    @Test
    void 车辆_中() {
        var e = mapper.map(objs("car", 0.94));
        assertEquals("车辆异常", e.type());
        assertEquals("中", e.level());
        assertTrue(e.detail().contains("car(94%)"));
    }

    @Test
    void 动物进入_中() {
        var e = mapper.map(objs("dog", 0.92));
        assertEquals("动物进入", e.type());
        assertEquals("中", e.level());
    }

    @Test
    void 物品异常_中() {
        var e = mapper.map(objs("backpack", 0.91));
        assertEquals("物品异常", e.type());
        assertEquals("中", e.level());
    }

    @Test
    void 人员优先于车辆() {
        var e = mapper.map(objs("person", 0.9, "car", 0.9));
        assertEquals("人员闯入", e.type());
    }
}
