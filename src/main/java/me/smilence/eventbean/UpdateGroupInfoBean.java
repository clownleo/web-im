package me.smilence.eventbean;

import org.apache.commons.lang3.StringUtils;

/**
 * for register
 * Created by leo on 16-5-8.
 */
public class UpdateGroupInfoBean extends EventBean {
    public String group;
    public Object info;

    @Override
    public boolean validate() {
        return super.validate()
                && StringUtils.isNoneEmpty(group)
                && info != null;
    }
}
