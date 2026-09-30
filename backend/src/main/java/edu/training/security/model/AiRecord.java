package edu.training.security.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_record")
public class AiRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String image;
    private String imagePath;
    private String area;
    private String objects;
    private Integer objectCount;
    private String alarmType;
    private Boolean alarmed;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventTime;
}
