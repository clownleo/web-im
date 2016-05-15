package me.smilence.eventbean;

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
    @JsonProperty("group")
    public String group;
    @JsonProperty("date_time")
    public Date dateTime;
    public int type;
    public String content;

    public MessageBean(String fromUser, String toUser, String group, Date dateTime, int type, String content) {
        this.fromUser = fromUser;
        this.toUser = toUser;
        this.group = group;
        this.dateTime = dateTime;
        this.type = type;
        this.content = content;
    }
    public MessageBean(){}
}
