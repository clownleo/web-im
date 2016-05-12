package me.smilence.eventbean;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;

/**
 * for register
 * Created by leo on 16-5-8.
 */
public class ResetKeyEncBean extends EventBean {
    public String username;
    @JsonProperty("signature")
    public String signature;
    @JsonProperty("key_encrypted")
    public String keyEncrypted;

    @Override
    public boolean validate() {
        return super.validate() &&
                StringUtils.isNoneEmpty(
                        username,
                        signature,
                        keyEncrypted
                );
    }

}
