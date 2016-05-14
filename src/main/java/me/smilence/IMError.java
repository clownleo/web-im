package me.smilence;

import java.util.HashMap;
import java.util.Map;

/**
 * 错误的定义
 * Created by leo on 16-5-9.
 */
@SuppressWarnings("ThrowableInstanceNeverThrown")
public class IMError extends RuntimeException {
    public static IMError
            FORMAT_ERROR = new IMError(-1, "format error"),
            USERNAME_ALREADY_EXIST = new IMError(4001, "username already exist"),
            USERNAME_NOT_EXIST = new IMError(4002, "username not exist"),
            GROUP_NAME_ALREADY_EXIST = new IMError(4003, "group name already exist"),
            GROUP_NOT_EXIST = new IMError(4004, "group not exist"),
            AUTH_FAIL = new IMError(4005, "auth fail"),
            TARGET_NOT_EXIST = new IMError(4006, "target not exist"),
            UNLOGIN = new IMError(4007, "user unlogin"),
            INVALI_REQUEST = new IMError(4008, "invalidate request");
    private static Map<Integer, IMError> code2message;

    private static Map<Integer, IMError> getCode2message() {
        if (code2message == null) {
            synchronized (IMError.class) {
                if (code2message == null) {
                    code2message = new HashMap<>();
                }
            }
        }
        return code2message;
    }

    private int code;
    private String message;

    private IMError(int code, String message) {
        super(message);
        this.code = code;

        getCode2message().put(code, this);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return super.getMessage();
    }

    public static IMError getMessage(int code) {
        return code2message.get(code);
    }
}