package cn.jpush;
/**
 * Web-IM Protocol Event
 * Created by leo on 16/1/25.
 */
public class IMEvent {
    public static final String
            LOGIN = "login",
            LOGOUT = "logout",
            REGISTER = "register",
            GET_KET_ENCRYPTED="get_key_encrypted",
            GET_KET_ENCRYPTED_bck="get_key_encrypted_bck";

    public static final String GET_USER_INFO = "get_user_info";
    public static final String SEND_SINGLE_TEXT = "s_single_text";
    public static final String MSG_SYNC = "msg_sync";
    public static final String MSG_RECV = "msg_recv";
    public static final String ADD_FRIEND = "add_friend";
    public static final String SEND_GROUP_MSG = "send_group_msg";
    public static final String CREATE_GROUP = "create_group";
    public static final String GET_GROUPS_LIST = "get_groups_list";
    public static final String GET_GROUP_INFO = "get_group_info";
    public static final String ADD_GROUP_MEMBER = "add_group_member";
    public static final String DEL_GROUP_MEMBER = "del_group_member";
    public static final String GET_GROUP_MEMBERS = "get_group_members";
    public static final String EXIT_GROUP = "exit_group";
    public static final String UPDATE_GROUP_INFO = "update_group_info";
    public static final String EVENT_NOTIFICATION = "event_notification";
}
