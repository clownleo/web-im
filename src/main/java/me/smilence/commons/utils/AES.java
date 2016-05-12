package me.smilence.commons.utils;

import com.sun.xml.internal.ws.api.message.ExceptionHasMessage;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Stream;

/**
 * AES工具
 * Created by leo on 16-5-11.
 */
public class AES {

    private static Cipher cipher;
    private static IvParameterSpec iv = new IvParameterSpec("0102030405060708".getBytes());

    static {
        try {
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    // 加密
    public static String Encrypt(String sSrc, String sKey) {
        try {
            sKey = hash16bytes(sKey);
            // 判断Key是否为16位
            if (sKey.length() != 16) {
                throw new RuntimeException("skey must be 16 bytes");
            }
            byte[] raw = sKey.getBytes();
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);
            byte[] encrypted = cipher.doFinal(sSrc.getBytes());

            return Base64.encodeBase64String(encrypted);//此处使用BAES64做转码功能，同时能起到2次加密的作用。
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    // 解密
    public static String Decrypt(String sSrc, String sKey) {
        try {
            sKey = hash16bytes(sKey);
            byte[] raw = sKey.getBytes("ASCII");
            if (raw.length != 16) {
                throw new RuntimeException("skey must be 16 bytes");
            }
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);
            byte[] encrypted1 = Base64.decodeBase64(sSrc);//先用bAES64解密
            byte[] original = cipher.doFinal(encrypted1);
            return new String(original);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static String hash16bytes(String str) {
        byte[] ori = DigestUtils.md5(str);
        byte[] result = new byte[8];
        Stream.iterate(0, integer -> integer + 1).limit(8)
                .forEach(index -> result[index] = (byte) (ori[index] ^ ori[index + 8]));
        return Hex.encodeHexString(result);
    }

    public static void main(String[] args) {
        System.out.println(Encrypt("你好", "你不好"));
        System.out.println(Decrypt("ef1uy4NcndwnVV9Y+oS/sw==", "你不好"));
    }
}