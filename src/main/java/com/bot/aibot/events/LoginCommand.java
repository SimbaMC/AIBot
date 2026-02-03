package com.bot.aibot.events;

import com.bot.aibot.config.BotConfig;
import com.bot.aibot.utils.NeteaseApi;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;


public class LoginCommand implements Command<CommandSourceStack> {

    @Override
    public int run(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("§7[Bot] 正在申请二维码..."), false);

        new Thread(() -> {
            // 1. 获取 Key
            String key = NeteaseApi.getLoginKey();
            if (key == null) {
                sendMsg(context, "§c申请失败，请查看后台报错。");
                return;
            }

            // 2. 获取二维码 URL (netease://...)
            String qrUrl = NeteaseApi.getLoginQrUrl(key);

            // 3. 转换成在线二维码图片链接 (利用 qrserver.com API)
            // 这样玩家点开链接就能看到图，不用在控制台看字符画
            String webQrLink = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=" + qrUrl;

            // 4. 发送可点击的链接给玩家
            Component linkText = Component.literal("§b§l[点击这里打开二维码]")
                    .setStyle(Style.EMPTY
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, webQrLink))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("打开网易云APP扫码")))
                    );

            sendMsg(context, "§a二维码已生成！请使用 §e网易云音乐APP §a扫码：");
            context.getSource().sendSuccess(() -> linkText, false);

            // 5. 开始轮询 (检查是否扫码)
            checkLoop(context, key);

        }).start();

        return 1;
    }

    private void checkLoop(CommandContext<CommandSourceStack> context, String key) {
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < 60000) { // 超时时间 60秒
            try {
                Thread.sleep(3000); // 每3秒查一次

                NeteaseApi.LoginResult result = NeteaseApi.checkLoginStatus(key);

                if (result.code == 800) {
                    sendMsg(context, "§c二维码已过期，请重新输入指令。");
                    return;
                }
                if (result.code == 802) {
                    // 只要发一次提示就行，避免刷屏，这里简化处理不发了，或者可以发"等待确认"
                }
                if (result.code == 803) {
                    // 成功！
                    String fullCookie = result.cookie;
                    sendMsg(context, "§a§l登录成功！§r Cookie 已自动保存。");

                    // 6. 自动保存到 Config
                    // 注意：这需要 BotConfig 支持运行时写入
                    updateConfig(fullCookie);
                    return;
                }

            } catch (InterruptedException e) {
                break;
            }
        }
        sendMsg(context, "§c登录超时。");
    }

    private void sendMsg(CommandContext<CommandSourceStack> context, String msg) {
        context.getSource().sendSuccess(() -> Component.literal(msg), false);
    }

    // 写入配置文件的辅助方法
    private void updateConfig(String cookie) {
        BotConfig.SERVER.neteaseCookie.set(cookie);
        BotConfig.SERVER_SPEC.save(); // 强制保存到磁盘
    }
}