package com.bot.aibot.network.packet;

import com.bot.aibot.API.QrCode;
import com.bot.aibot.config.BotConfig;
import com.bot.aibot.utils.NeteaseApi;
import com.bot.aibot.utils.NeteaseApi.LoginResult;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CRequestLoginPacket {

    // 【新增】客户端登录状态锁 (全局静态变量)
    public static volatile boolean isLoggingIn = false;

    // 不需要参数，只是一个触发信号
    public S2CRequestLoginPacket() {}
    public S2CRequestLoginPacket(FriendlyByteBuf buf) {}
    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // --- 这段代码在【客户端】执行 ---
            System.out.println(">>> [Client] 收到登录指令，开始执行本地登录...");
            startClientLogin();
        });
        ctx.get().setPacketHandled(true);
    }

    private void startClientLogin() {
        // 【上锁】
        isLoggingIn = true;

        new Thread(() -> {
            try {
                // 使用 Minecraft.getInstance().player 发送本地提示
                var player = Minecraft.getInstance().player;
                if (player == null) return;

                printMsg("🔍 正在获取登录 Key...");

                String key = NeteaseApi.getLoginKey();
                if (key == null) {
                    printMsg("❌ 获取 Key 失败，请检查你的网络连接。");
                    return;
                }

                String url = NeteaseApi.getLoginQrUrl(key);

                // 1. 打印二维码 (复制你原来的逻辑)
                try {
                    QrCode qr = QrCode.encodeText(url, QrCode.Ecc.LOW);
                    System.out.println("\n>>> 请扫码登录："); // 客户端控制台也能看
                    // ... (二维码打印逻辑省略，跟你原来的一样，或者只发链接) ...
                } catch (Exception e) {}

                // 2. 发送可点击链接给玩家
                player.sendSystemMessage(Component.literal("§b[点击这里扫码登录]")
                        .setStyle(Style.EMPTY
                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                                .withUnderlined(true)));

                // 3. 轮询检测
                int timeout = 0;
                while (timeout < 60 && isLoggingIn) {
                    Thread.sleep(3000);
                    LoginResult result = NeteaseApi.checkLoginStatus(key);

                    if (result.code == 803) {
                        printMsg("✅ 登录成功！Cookie 已保存到本地客户端。");
                        if (result.cookie != null) {
                            // 【核心】保存到 CLIENT 配置
                            BotConfig.CLIENT.neteaseCookie.set(result.cookie);
                            BotConfig.CLIENT.neteaseCookie.save();

                            // 刷新 API 内存
                            NeteaseApi.setCookie(result.cookie);
                        }
                        break;
                    } else if (result.code == 800) {
                        printMsg("❌ 二维码已过期");
                        break;
                    }
                    timeout++;
                }

                // 如果是被手动停止的
                if (!isLoggingIn) {
                    printMsg("⚠️ 登录任务已手动终止。");
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // 【解锁】
                isLoggingIn = false;
            }
        }).start();
    }

    private void printMsg(String msg) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("§e[Bot] " + msg));
            }
        });
    }
}