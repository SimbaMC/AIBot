package com.bot.aibot.events;

import com.bot.aibot.API.QrCode;
import com.bot.aibot.config.BotConfig;
import com.bot.aibot.utils.NeteaseApi;
import com.bot.aibot.utils.NeteaseApi.LoginResult;
// 引入刚才拖进去的库
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public class LoginCommand implements Command<CommandSourceStack> {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bot")
                .then(Commands.literal("login")
                        .executes(new LoginCommand())));
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        new Thread(() -> {
            try {
                CommandSourceStack source = context.getSource();
                sendMsg(source, "🔍 正在获取登录 Key...");

                // 1. 获取 Key
                String key = NeteaseApi.getLoginKey();
                if (key == null) {
                    sendMsg(source, "❌ 获取 Key 失败");
                    return;
                }

                String url = NeteaseApi.getLoginQrUrl(key);

                // =========================================================
                // 【本地算法生成】适配 IDEA 深色控制台
                // =========================================================
                try {
                    // 使用刚才拖进来的 QrCode 类 (Low 容错率让矩阵更稀疏，易于识别)
                    QrCode qr = QrCode.encodeText(url, QrCode.Ecc.LOW);

                    System.out.println("\n");
                    System.out.println(">>> 请拉宽控制台，使用手机扫码：");

                    for (int y = 0; y < qr.size; y++) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("      "); // 左边距
                        for (int x = 0; x < qr.size; x++) {
                            // getModule: true=黑(数据), false=白(背景)
                            boolean isBlackData = qr.getModule(x, y);

                            // 【视觉修正逻辑】
                            // 控制台是黑底的。
                            // 我们用 "██" (白色字符) 来画二维码的背景(白)。
                            // 我们用 "  " (空格) 来透出控制台的底色(黑)，作为二维码的数据点。
                            // 并且横向打印两个字符，防止二维码变瘦长。
                            if (isBlackData) {
                                sb.append("  "); // 黑点 (透出背景)
                            } else {
                                sb.append("██"); // 白点 (显示字符)
                            }
                        }
                        System.out.println(sb.toString());
                    }
                    System.out.println("\n");

                } catch (Exception e) {
                    System.out.println("⚠️ 二维码绘制失败，请复制链接：" + url);
                    e.printStackTrace();
                }
                // =========================================================

                if (source.getEntity() != null) {
                    source.sendSystemMessage(Component.literal("§b[点击这里扫码登录]")
                            .setStyle(Style.EMPTY
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                                    .withUnderlined(true)));
                }

                // 轮询检查
                int timeout = 0;
                while (timeout < 60) {
                    Thread.sleep(3000);
                    LoginResult result = NeteaseApi.checkLoginStatus(key);
                    if (result.code == 803) {
                        sendMsg(source, "✅ 登录成功！Cookie 已保存。");
                        if (result.cookie != null && BotConfig.SERVER != null) {
                            BotConfig.SERVER.neteaseCookie.set(result.cookie);
                            BotConfig.SERVER.neteaseCookie.save();
                        }
                        break;
                    } else if (result.code == 800) {
                        sendMsg(source, "❌ 已过期");
                        break;
                    }
                    timeout++;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
        return 1;
    }

    private void sendMsg(CommandSourceStack source, String msg) {
        source.sendSystemMessage(Component.literal(msg));
        if (source.getEntity() != null) System.out.println(">>> [Bot] " + msg);
    }
}