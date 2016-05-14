package me.smilence.eventbean;

import org.apache.commons.lang3.StringUtils;

/**
 * for register
 * Created by leo on 16-5-8.
 */
public class RemoveGroupMemberBean extends EventBean {
    public String group;
    public String member;

    @Override
    public boolean validate() {
        return super.validate()
                && StringUtils.isNoneEmpty(
                group,
                member
        );
    }
}
