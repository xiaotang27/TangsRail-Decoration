package top.xiaotang27.trdm.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * ModMenu 入口：装了 Mod Menu 时，模组列表里会多一个「设置」按钮，直接打开通用设置。
 * 没装 Mod Menu 时这个类不会被加载（fabric.mod.json 里的 modmenu 入口点只在有 Mod Menu 时才解析）。
 */
public class TrdmModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new TrdmConfigScreen(parent);
    }
}
