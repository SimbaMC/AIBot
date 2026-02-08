package com.bot.aibot.utils;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

public class ApngUtils {

    // PNG 文件头签名
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    public static class ParsedApng {
        public List<NativeImage> images = new ArrayList<>();
        public List<Integer> delays = new ArrayList<>();
    }

    public static ParsedApng parse(byte[] data) {
        if (!isApng(data)) return null;

        ParsedApng result = new ParsedApng();
        try {
            List<Chunk> chunks = readChunks(data);
            Chunk ihdr = getChunk(chunks, "IHDR");
            if (ihdr == null) return null;

            // 获取原图完整尺寸
            int canvasWidth = bytesToInt(ihdr.data, 0);
            int canvasHeight = bytesToInt(ihdr.data, 4);

            // 公共块 (PLTE, tRNS 等需要复制到每一帧)
            List<Chunk> globalChunks = new ArrayList<>();
            for (Chunk c : chunks) {
                if (isGlobalChunk(c.type)) globalChunks.add(c);
                if (c.type.equals("IDAT")) break; // IDAT 之前的是全局头
            }

            BufferedImage master = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
            BufferedImage prevBuffer = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);

            // 帧序列处理
            int frameIndex = 0;
            byte[] sequenceNumber = new byte[4]; // fcTL 里的序号

            for (int i = 0; i < chunks.size(); i++) {
                Chunk c = chunks.get(i);

                if (c.type.equals("fcTL")) {
                    // 找到了帧控制块
                    FrameControl fc = new FrameControl(c.data);

                    // 寻找紧随其后的图像数据 (IDAT 或 fdAT)
                    List<Chunk> frameDataChunks = new ArrayList<>();
                    int j = i + 1;
                    while (j < chunks.size()) {
                        Chunk next = chunks.get(j);
                        if (next.type.equals("fcTL") || next.type.equals("IEND")) break;

                        if (next.type.equals("IDAT")) {
                            // 第一帧通常是 IDAT
                            frameDataChunks.add(next);
                        } else if (next.type.equals("fdAT")) {
                            // 后续帧是 fdAT，需要去掉前4字节的序列号，转为 IDAT
                            byte[] realData = Arrays.copyOfRange(next.data, 4, next.data.length);
                            frameDataChunks.add(new Chunk("IDAT", realData));
                        }
                        j++;
                    }

                    if (!frameDataChunks.isEmpty()) {
                        // 1. 组装成一个临时的单帧 PNG 文件
                        byte[] tempPng = createPng(ihdr, globalChunks, fc, frameDataChunks);
                        BufferedImage frameImg = ImageIO.read(new ByteArrayInputStream(tempPng));

                        // 2. 备份当前画布 (为了 DisposeOp.PREVIOUS)
                        copyImage(master, prevBuffer);

                        // 3. 混合绘制 (BlendOp)
                        Graphics2D g2d = master.createGraphics();
                        if (fc.blendOp == 0) { // APNG_BLEND_OP_SOURCE (覆盖)
                            g2d.setComposite(AlphaComposite.Src);
                        } else { // APNG_BLEND_OP_OVER (混合)
                            g2d.setComposite(AlphaComposite.SrcOver);
                        }
                        g2d.drawImage(frameImg, fc.xOffset, fc.yOffset, null);
                        g2d.dispose();

                        // 4. 输出结果
                        result.images.add(convertToNative(deepCopy(master)));

                        // 延迟计算: num / den (秒) -> 毫秒
                        int delay = 100;
                        if (fc.delayDen != 0) {
                            delay = (int) ((double) fc.delayNum / fc.delayDen * 1000);
                        }
                        result.delays.add(Math.max(delay, 20));

                        // 5. 处理结束后的清理 (DisposeOp)
                        Graphics2D gDisp = master.createGraphics();
                        gDisp.setComposite(AlphaComposite.Src);
                        if (fc.disposeOp == 1) { // APNG_DISPOSE_OP_BACKGROUND (清空当前帧区域)
                            // 注意：这里用 Clear 模式擦除区域
                            gDisp.setComposite(AlphaComposite.Clear);
                            gDisp.fillRect(fc.xOffset, fc.yOffset, fc.width, fc.height);
                        } else if (fc.disposeOp == 2) { // APNG_DISPOSE_OP_PREVIOUS (回滚)
                            gDisp.drawImage(prevBuffer, 0, 0, null);
                        }
                        gDisp.dispose();

                        frameIndex++;
                    }
                }
            }

            if (result.images.isEmpty()) return null;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return result;
    }

    // 重新组装一个合法的 PNG 文件流
    private static byte[] createPng(Chunk originalIhdr, List<Chunk> globals, FrameControl fc, List<Chunk> dataChunks) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(PNG_SIGNATURE);

        // 修改 IHDR 的宽高为当前帧的宽高
        byte[] newIhdrData = Arrays.copyOf(originalIhdr.data, originalIhdr.data.length);
        intToBytes(fc.width, newIhdrData, 0);
        intToBytes(fc.height, newIhdrData, 4);
        writeChunk(baos, "IHDR", newIhdrData);

        // 写入全局块 (PLTE, tRNS)
        for (Chunk c : globals) {
            writeChunk(baos, c.type, c.data);
        }

        // 写入图像数据
        for (Chunk c : dataChunks) {
            writeChunk(baos, "IDAT", c.data);
        }

        writeChunk(baos, "IEND", new byte[0]);
        return baos.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream os, String type, byte[] data) throws IOException {
        int len = data.length;
        os.write(intToBytes(len)); // Length
        os.write(type.getBytes()); // Type
        os.write(data);            // Data

        // CRC
        CRC32 crc = new CRC32();
        crc.update(type.getBytes());
        crc.update(data);
        os.write(intToBytes((int) crc.getValue()));
    }

    // 简单判断是否包含 acTL 块
    public static boolean isApng(byte[] data) {
        if (data.length < 8) return false;
        // 检查 PNG 签名
        for (int i = 0; i < 8; i++) if (data[i] != PNG_SIGNATURE[i]) return false;

        // 暴力搜索 acTL 字符串
        for (int i = 8; i < Math.min(data.length, 1024); i++) { // 只搜前1KB
            if (data[i] == 'a' && data[i+1] == 'c' && data[i+2] == 'T' && data[i+3] == 'L') {
                return true;
            }
        }
        return false;
    }

    // --- 辅助类与方法 ---

    private static class Chunk {
        String type;
        byte[] data;
        public Chunk(String type, byte[] data) { this.type = type; this.data = data; }
    }

    private static class FrameControl {
        int width, height, xOffset, yOffset;
        short delayNum, delayDen;
        byte disposeOp, blendOp;

        public FrameControl(byte[] d) {
            // fcTL 结构: sequence(4), width(4), height(4), x(4), y(4), delay_num(2), delay_den(2), dispose(1), blend(1)
            // 注意：我们解析时是从 data 开始，不包含 length/type
            // sequence 在外部处理或者在这里忽略
            width = bytesToInt(d, 4);
            height = bytesToInt(d, 8);
            xOffset = bytesToInt(d, 12);
            yOffset = bytesToInt(d, 16);
            delayNum = bytesToShort(d, 20);
            delayDen = bytesToShort(d, 22);
            disposeOp = d[24];
            blendOp = d[25];
        }
    }

    private static List<Chunk> readChunks(byte[] data) throws IOException {
        List<Chunk> chunks = new ArrayList<>();
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        dis.skipBytes(8); // 跳过签名

        while (dis.available() > 0) {
            int len = dis.readInt();
            byte[] typeBytes = new byte[4];
            dis.read(typeBytes);
            String type = new String(typeBytes);
            byte[] d = new byte[len];
            dis.readFully(d);
            dis.readInt(); // CRC
            chunks.add(new Chunk(type, d));
            if (type.equals("IEND")) break;
        }
        return chunks;
    }

    private static Chunk getChunk(List<Chunk> chunks, String type) {
        for (Chunk c : chunks) if (c.type.equals(type)) return c;
        return null;
    }

    private static boolean isGlobalChunk(String type) {
        return type.equals("PLTE") || type.equals("tRNS") || type.equals("cHRM") || type.equals("gAMA") || type.equals("iCCP") || type.equals("sRGB");
    }

    private static int bytesToInt(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16) | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }

    private static short bytesToShort(byte[] b, int off) {
        return (short) (((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF));
    }

    private static byte[] intToBytes(int v) {
        return new byte[] {(byte)(v>>>24), (byte)(v>>>16), (byte)(v>>>8), (byte)v};
    }

    private static void intToBytes(int v, byte[] b, int off) {
        b[off] = (byte)(v>>>24); b[off+1] = (byte)(v>>>16); b[off+2] = (byte)(v>>>8); b[off+3] = (byte)v;
    }

    // 图像复制与转换 (直接复用 GifUtils 的逻辑，或者再写一遍)
    private static BufferedImage deepCopy(BufferedImage bi) {
        BufferedImage copy = new BufferedImage(bi.getWidth(), bi.getHeight(), bi.getType());
        Graphics2D g = copy.createGraphics();
        g.drawImage(bi, 0, 0, null);
        g.dispose();
        return copy;
    }

    private static void copyImage(BufferedImage src, BufferedImage dst) {
        Graphics2D g = dst.createGraphics();
        g.setComposite(AlphaComposite.Src); // 完全替换
        g.drawImage(src, 0, 0, null);
        g.dispose();
    }

    private static NativeImage convertToNative(BufferedImage bi) {
        int w = bi.getWidth();
        int h = bi.getHeight();
        NativeImage image = new NativeImage(w, h, true);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = bi.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = (argb) & 0xFF;
                int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                image.setPixelRGBA(x, y, abgr);
            }
        }
        return image;
    }
}