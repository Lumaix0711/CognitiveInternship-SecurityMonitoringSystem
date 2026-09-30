package edu.training.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.training.security.model.Alarm;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AlarmMapper extends BaseMapper<Alarm> {
}
