package edu.training.security.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("device")
public class Device {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String type;
    private String area;
    private String status;
}
