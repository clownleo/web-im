package me.smilence.eventbean;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;

/**
 * for register
 * Created by leo on 16-5-8.
 */
public class SetChatKeyBean extends EventBean {
    @JsonProperty("to_user")
    public String toUser;
    @JsonProperty("chat_key")
    public String chatKey;

    @Override
    public boolean validate() {
        return super.validate() &&
                StringUtils.isNoneEmpty(
                        toUser,
                        chatKey
                );
    }

}
