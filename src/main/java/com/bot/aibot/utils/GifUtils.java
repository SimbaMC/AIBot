package com.bot.aibot.utils;

import com.mojang.blaze3d.platform.NativeImage;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GifUtils {

    public static class ParsedGif {
        public List<NativeImage> images = new ArrayList<>();
        public List<Integer> delays = new ArrayList<>();
    }

    public static ParsedGif parseGif(byte[] data) {
        ParsedGif result = new ParsedGif();
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             ImageInputStream stream = ImageIO.createImageInputStream(bis)) {

            var readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            reader.setInput(stream);

            int count = reader.getNumImages(true);
            System.out.println(">>> [GifUtils] GIF 帧数: " + count);

            // 主画布：用于合成最终显示的图像
            BufferedImage master = null;
            // 备份画布：用于处理 restoreToPrevious (3) 类型的处置方法
            BufferedImage backup = null;

            for (int i = 0; i < count; i++) {
                BufferedImage rawFrame = reader.read(i);

                // 初始化画布
                if (master == null) {
                    master = new BufferedImage(rawFrame.getWidth(), rawFrame.getHeight(), BufferedImage.TYPE_INT_ARGB);
                }

                // 1.获取当前帧的处置方法
                String disposalMethod = getDisposalMethod(reader, i);
                BufferedImage frameStart = deepCopy(master);

                // 2. 绘制当前帧
                Graphics2D g2d = master.createGraphics();
                g2d.drawImage(rawFrame, 0, 0, null);
                g2d.dispose();

                // 3. 保存结果到 MC 纹理
                result.images.add(convertToNative(deepCopy(master)));
                result.delays.add(getDelay(reader, i));

                if ("restoreToBackgroundColor".equals(disposalMethod)) {
                    // 处置方法 2: 恢复背景色 (通常是透明)
                    Graphics2D gClear = master.createGraphics();
                    gClear.setComposite(AlphaComposite.Clear);
                    gClear.fillRect(0, 0, master.getWidth(), master.getHeight());
                    gClear.dispose();
                } else if ("restoreToPrevious".equals(disposalMethod)) {
                    // 处置方法 3: 恢复到绘制当前帧之前的状态
                    master = frameStart;
                }
                // doNotDispose (1) 或 none (0): 啥都不做，保留当前画面给下一帧叠加 (这是最常见的)
            }

        } catch (Exception e) {
            e.printStackTrace();
            if (!result.images.isEmpty()) return result;
            return null;
        }
        return result;
    }

    private static BufferedImage deepCopy(BufferedImage bi) {
        WritableRaster raster = bi.getColorModel().createCompatibleWritableRaster(bi.getWidth(), bi.getHeight());
        BufferedImage copy = new BufferedImage(bi.getColorModel(), raster, bi.isAlphaPremultiplied(), null);
        bi.copyData(copy.getRaster());
        return copy;
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
                // 打包为 ABGR int
                int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                image.setPixelRGBA(x, y, abgr);
            }
        }
        return image;
    }

    private static int getDelay(ImageReader reader, int index) {
        int delay = 100;
        try {
            Node node = getMetadataNode(reader, index);
            if (node != null) {
                NamedNodeMap attrs = node.getAttributes();
                Node delayNode = attrs.getNamedItem("delayTime");
                if (delayNode != null) {
                    delay = Integer.parseInt(delayNode.getNodeValue()) * 10;
                }
            }
        } catch (Exception ignored) {}
        return Math.max(delay, 20);
    }

    // 新增：获取处置方法
    private static String getDisposalMethod(ImageReader reader, int index) {
        try {
            Node node = getMetadataNode(reader, index);
            if (node != null) {
                NamedNodeMap attrs = node.getAttributes();
                Node disposalNode = attrs.getNamedItem("disposalMethod");
                if (disposalNode != null) {
                    return disposalNode.getNodeValue();
                }
            }
        } catch (Exception ignored) {}
        return "none";
    }

    // 辅助方法：获取元数据节点
    private static Node getMetadataNode(ImageReader reader, int index) throws IOException {
        IIOMetadata meta = reader.getImageMetadata(index);
        String format = meta.getNativeMetadataFormatName();
        Node root = meta.getAsTree(format);
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if ("GraphicControlExtension".equalsIgnoreCase(node.getNodeName())) {
                return node;
            }
        }
        return null;
    }
}