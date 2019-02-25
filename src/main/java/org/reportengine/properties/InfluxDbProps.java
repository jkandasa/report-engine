package org.reportengine.properties;

import lombok.Data;
import lombok.ToString;

import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ToString
@ConfigurationProperties(prefix = "spring.influx")
public class InfluxDbProps {

    private String url;

    private String database;

    private String user;

    private String password;

    private Boolean idValidationEnabled;
}
