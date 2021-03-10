package com.daqi.bleaction;

import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import androidx.core.content.ContextCompat;

import java.math.BigDecimal;

public class BluetoothUtils {

    public static final int OpenBluetooth_Request_Code = 10086;

    /**
      * 字节数组转十六进制字符串
      */
    public static String bytesToHexString(byte[] src) {
        if (src == null || src.length == 0){
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder("");
        if (src == null || src.length <= 0) {
            return null;
        }
        for (int i = 0; i < src.length; i++) {
            int v = src[i] & 0xFF;
            String hex = Integer.toHexString(v);
            if (hex.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hex);
        }
        return stringBuilder.toString();
    }

    /**
     * 将字符串转成字节数组
     */
    public static byte[] hexStringToBytes(String str) {
        byte abyte0[] = new byte[str.length() / 2];
        byte[] s11 = str.getBytes();
        for (int i1 = 0; i1 < s11.length / 2; i1++) {
            byte byte1 = s11[i1 * 2 + 1];
            byte byte0 = s11[i1 * 2];
            String s2;
            abyte0[i1] = (byte) (
                    (byte0 = (byte) (Byte.decode((new StringBuilder(String.valueOf(s2 = "0x")))
                            .append(new String(new byte[]{byte0})).toString())
                            .byteValue() << 4)) ^
                            (byte1 = Byte.decode((new StringBuilder(String.valueOf(s2)))
                                    .append(new String(new byte[]{byte1})).toString()).byteValue()));
        }
        return abyte0;
    }

    /**
     * 权限列表判断
     */
    public static boolean hasPermissions(Context context,String[] permissionList){
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        for (String permissionStr : permissionList) {
            if (ContextCompat.checkSelfPermission(context, permissionStr) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * 刷新缓存
     *  @author daqi
     *  @time 2019/3/25
     */
    public boolean refreshDeviceCache(BluetoothGatt bluetoothGatt) {
        if (bluetoothGatt != null) {
            try {
                boolean paramBoolean = ((Boolean) bluetoothGatt.getClass()
                        .getMethod("refresh", new Class[0])
                        .invoke(bluetoothGatt, new Object[0]))
                        .booleanValue();
                return paramBoolean;
            } catch (Exception localException) {
            }
        }
        return false;
    }
}
