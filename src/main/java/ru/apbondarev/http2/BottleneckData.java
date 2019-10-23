package ru.apbondarev.http2;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class BottleneckData {

    private final List<byte[]> bottleneckData;

    public BottleneckData(int min, int max) {
        if (max <= min) {
            throw new IllegalArgumentException("max <= min");
        }
        bottleneckData = new ArrayList<>(max - min);
        List<Integer> keys = new ArrayList<>(max - min);
        for (int key = 0; key < max - min; key++) {
            keys.add(key);
        }
        Collections.shuffle(keys);
        for (Integer key : keys) {
            String value = Long.toHexString(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE));
            bottleneckData.add(toBytes(key, value));
        }
    }

    public final synchronized String get(int key) {
        for (byte[] bytes : bottleneckData) {
            if (getKey(bytes) == key) {
                return new String(bytes, 4, bytes.length - 4, StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    public final synchronized void put(int key, String value) {
        byte[] byteBuffer = toBytes(key, value);
        for (int i = 0; i < bottleneckData.size(); i++) {
            if (getKey(bottleneckData.get(i)) == key) {
                bottleneckData.set(i, byteBuffer);
                return;
            }
        }
        bottleneckData.add(byteBuffer);
    }

    private static byte[] toBytes(int key, String value) {
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(Integer.BYTES + valueBytes.length)
                .putInt(key)
                .put(valueBytes)
                .array();
    }

    private static int getKey(byte[] bytes) {
        return bytes[0] << 24 | bytes[1] << 16 | bytes[2] << 8 | bytes[3];
    }
}
