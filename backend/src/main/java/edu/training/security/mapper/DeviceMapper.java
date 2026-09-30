package edu.training.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.training.security.model.Device;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceMapper extends BaseMapper<Device> {
}
