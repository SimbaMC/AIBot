package com.bot.aibot.network.packet;

import com.bot.aibot.API.QrCode;
import com.bot.aibot.client.LoginQrScreen; // 即使引用了，只要放在 safeRun 里就安全
import com.bot.aibot.config.BotConfig;
import com.bot.aibot.utils.NeteaseApi;
import com.bot.aibot.utils.NeteaseApi.LoginResult;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

public class S2CRequestLoginPacket {

    // 客户端登录状态锁
    public static volatile boolean isLoggingIn = false;

    public S2CRequestLoginPacket() {}
    public S2CRequestLoginPacket(FriendlyByteBuf buf) {}
    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 【核心修复】使用 DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)
            // 这告诉 Forge：这段代码只在客户端执行，服务器不要碰，也不要试图加载里面的类
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientHandler.handleLogin();
            });
        });
        ctx.get().setPacketHandled(true);
    }

    // 【核心隔离】将所有涉及 Minecraft/Screen 的代码移到一个内部静态类中
    // 只有当 DistExecutor 确定是 CLIENT 端时，才会加载这个内部类
    private static class ClientHandler {

        public static void handleLogin() {
            if (isLoggingIn) {
                printMsg("§c[Bot] 客户端已有正在进行的登录任务！");
                return;
            }
            System.out.println(">>> [Client] 收到登录指令...");
            startClientLogin();
        }

        private static void startClientLogin() {
            isLoggingIn = true;

            new Thread(() -> {
                try {
                    var minecraft = net.minecraft.client.Minecraft.getInstance();
                    var player = minecraft.player;
                    if (player == null) return;

                    printMsg("🔍 正在获取登录 Key (客户端模式)...");

                    String key = NeteaseApi.getLoginKey();
                    if (key == null) {
                        printMsg("❌ 获取 Key 失败，请检查网络。");
                        return;
                    }
                    String rawUrl = NeteaseApi.getLoginQrUrl(key);

                    // 1. 打开 GUI (如果 LoginQrScreen 存在)
                    // 使用全限定名或确保引用安全
                    minecraft.execute(() -> {
                        // 如果你有 LoginQrScreen，这里可以用
                        minecraft.setScreen(new LoginQrScreen(rawUrl));
                    });

                    // 2. 备用方案：生成二维码链接
                    String qrImgUrl = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data="
                            + URLEncoder.encode(rawUrl, StandardCharsets.UTF_8);

                    // 3. 备用方案：控制台打印
                    try {
                        QrCode qr = QrCode.encodeText(rawUrl, QrCode.Ecc.LOW);
                        System.out.println("\n>>> 请扫码登录：");
                        for (int y = 0; y < qr.size; y++) {
                            StringBuilder sb = new StringBuilder();
                            sb.append("   ");
                            for (int x = 0; x < qr.size; x++) {
                                sb.append(qr.getModule(x, y) ? "  " : "██");
                            }
                            System.out.println(sb.toString());
                        }
                    } catch (Exception e) {}

                    printMsg("§b[点击打开二维码图片]");
                    player.sendSystemMessage(Component.literal("§n[点击这里打开浏览器扫码]")
                            .setStyle(Style.EMPTY
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, qrImgUrl))
                                    .withColor(net.minecraft.network.chat.TextColor.parseColor("#00AAFF"))
                                    .withUnderlined(true)));

                    // 4. 轮询
                    int timeout = 0;
                    while (timeout < 60 && isLoggingIn) {
                        // 检测 ESC 关闭窗口
                        if (minecraft.screen == null && timeout > 5) {
                            // 可选操作
                        }

                        Thread.sleep(3000);
                        LoginResult result = NeteaseApi.checkLoginStatus(key);

                        if (result.code == 803) {
                            if (result.cookie != null) {
                                BotConfig.CLIENT.neteaseCookie.set(result.cookie);
                                BotConfig.CLIENT.neteaseCookie.save();
                                NeteaseApi.setCookie(result.cookie);

                                // 关闭窗口
                                minecraft.execute(() -> {
                                    if (minecraft.screen instanceof LoginQrScreen) {
                                        minecraft.setScreen(null);
                                    }
                                    if (minecraft.player != null) {
                                        minecraft.player.sendSystemMessage(Component.literal("§a[Bot] 登录成功！"));
                                    }
                                });
                            }
                            break;
                        } else if (result.code == 800) {
                            printMsg("❌ 二维码已过期");
                            break;
                        }
                        timeout++;
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    printMsg("❌ 登录错误: " + e.getMessage());
                } finally {
                    isLoggingIn = false;
                    // 确保窗口关闭
                    net.minecraft.client.Minecraft.getInstance().execute(() -> {
                        if (net.minecraft.client.Minecraft.getInstance().screen instanceof LoginQrScreen) {
                            net.minecraft.client.Minecraft.getInstance().setScreen(null);
                        }
                    });
                }
            }).start();
        }

        private static void printMsg(String msg) {
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                if (net.minecraft.client.Minecraft.getInstance().player != null) {
                    net.minecraft.client.Minecraft.getInstance().player.sendSystemMessage(Component.literal("§e[Bot] " + msg));
                }
            });
        }
    }
}