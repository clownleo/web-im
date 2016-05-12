package me.smilence.eventbean;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;

/**
 * 用于设置密保
 * Created by leo on 16-5-9.
 */
public class KeyBckBean extends EventBean {
    public String username;
    @JsonProperty("signature")
    public String signature;
    @JsonProperty("key_encrypted_bck")
    public String keyEncryptedBck;

    @Override
    public boolean validate() {
        return super.validate() &&
                StringUtils.isNoneEmpty(
                        username,
                        signature,
                        keyEncryptedBck
                );
    }

}
