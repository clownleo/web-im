package cn.jpush.eventbean;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

/**
 *
 * Created by leo on 16-5-9.
 */
public class MessageBean extends EventBean {
    @JsonProperty("from_user")
    public String fromUser;
    @JsonProperty("to_user")
    public String toUser;
    @JsonProperty("from_group")
    public String fromGroup;
    @JsonProperty("date_time")
    public Date dateTime;
    public String type;
    public String context;
}
