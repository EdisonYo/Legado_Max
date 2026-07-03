package io.legado.app.help

import io.legado.app.constant.AppConst
import io.legado.app.data.appDb
import io.legado.app.data.entities.DictRule
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.BookCover
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.printOnDebug
import splitties.init.appCtx
import java.io.File

object DefaultData {

    fun upVersion() {
        if (LocalConfig.versionCode < AppConst.appInfo.versionCode) {
            Coroutine.async {
                if (LocalConfig.needUpHttpTTS) {
                    importDefaultHttpTTS()
                }
                if (LocalConfig.needUpTxtTocRule) {
                    importDefaultTocRules()
                }
                if (LocalConfig.needUpRssSources) {
                    importDefaultRssSources()
                }
                if (LocalConfig.needUpDictRule) {
                    importDefaultDictRules()
                }
                if (LocalConfig.needUpThemeConfig) {
                    upDefaultTheme()
                }
            }.onError {
                it.printOnDebug()
            }
        }
    }

    val httpTTS: List<HttpTTS> by lazy {
        val json =
            String(
                appCtx.assets.open("defaultData${File.separator}httpTTS.json")
                    .readBytes()
            )
        HttpTTS.fromJsonArray(json).getOrElse {
            emptyList()
        }
    }

    val readConfigs: List<ReadBookConfig.Config> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}${ReadBookConfig.configFileName}")
                .readBytes()
        )
        GSON.fromJsonArray<ReadBookConfig.Config>(json).getOrNull()
            ?.map { config ->
                if (config.underlineOffset == 6f) {
                    config.copy(underlineOffset = 2f)
                } else {
                    config
                }
            }
            ?: emptyList()
    }

    val txtTocRules: List<TxtTocRule> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}txtTocRule.json")
                .readBytes()
        )
        GSON.fromJsonArray<TxtTocRule>(json).getOrNull() ?: emptyList()
    }

    val themeConfigs: List<ThemeConfig.Config> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}${ThemeConfig.configFileName}")
                .readBytes()
        )
        GSON.fromJsonArray<ThemeConfig.Config>(json).getOrNull() ?: emptyList()
    }

    val rssSources: List<RssSource> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}rssSources.json")
                .readBytes()
        )
        GSON.fromJsonArray<RssSource>(json).getOrDefault(emptyList())
    }

    val coverRule: BookCover.CoverRule by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}coverRule.json")
                .readBytes()
        )
        GSON.fromJsonObject<BookCover.CoverRule>(json).getOrThrow()
    }

    /**
     * 默认HTML封面模板
     * 支持变量：{{bookName}}（书名）、{{author}}（作者）
     */
    val coverHtmlTemplate: String by lazy {
        """
		<!DOCTYPE html>
		<html>
		<head>
			<meta charset="UTF-8">
			<meta name="viewport" content="width=300, initial-scale=1.0">
			<style>
				* {
					margin:0;
					padding:0;
					box-sizing:border-box;
				}
				html,body {
					width:300px;
					height:410px;
					overflow:hidden;
				}
				body {
					display:flex;
					flex-direction:column;
					justify-content:center;
					align-items:center;
					background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);
					color:#fff8f0;
					text-align:center;
					padding:28px 24px;
					gap:16px;
				}
				#title {
					font-size:72px;
					font-weight:800;
					line-height:1.15;
					word-break:break-word;
					max-width:252px;
					margin-top:60px;
				}
				#author-wrap {
					background:rgba(0,255,0,0.4);
					border-radius:12px;
					padding:8px 18px;
				}
				#author {
					font-size:26px;
					font-weight:700;
					max-width:252px;
					line-height:1.3;
					color:#e8f5e9;
				}
				#deco-line {
					width:80px;
					height:4px;
					background:rgba(255,248,240,0.25);
					flex-shrink:0;
				}
			</style>
		</head>
		<body>
			<div id="title">{{bookName}}</div>
			<div id="deco-line"></div>
			<div id="author-wrap">
				<div id="author">{{author}}</div>
			</div>
			<script>
				function fitText(el,maxSize,minSize,maxW,maxH){
					var size=maxSize;
					el.style.fontSize=size+'px';
					while (size>minSize){
						var rect=el.getBoundingClientRect();
						if (rect.width<=maxW&&rect.height<=maxH) break;
						size-=2;
						el.style.fontSize=size+'px'
					}
					return size
				}
				var title=document.getElementById('title');
				var authorEl=document.getElementById('author');
				var authorWrap=document.getElementById('author-wrap');
				var deco=document.getElementById('deco-line');
				var titleSize=fitText(title,72,14,252,210);
				var authorText=authorEl.textContent||'';
				if (authorText.trim()===''){
					authorWrap.style.display='none';
					deco.style.display='none'
				}else{
					authorWrap.style.display='';
					deco.style.display='';
					var authorMax=Math.min(32,Math.floor(titleSize*0.55));
					var authorMin=Math.max(15,Math.floor(titleSize*0.38));
					fitText(authorEl,authorMax,authorMin,252,55)
				}
			</script>
		</body>
		</html>
        """.trimIndent()
    }

    val dictRules: List<DictRule> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}dictRules.json")
                .readBytes()
        )
        GSON.fromJsonArray<DictRule>(json).getOrThrow()
    }

    val keyboardAssists: List<KeyboardAssist> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}keyboardAssists.json")
                .readBytes()
        )
        GSON.fromJsonArray<KeyboardAssist>(json).getOrThrow()
    }

    fun importDefaultHttpTTS() {
        appDb.httpTTSDao.deleteDefault()
        appDb.httpTTSDao.insert(*httpTTS.toTypedArray())
    }

    fun importDefaultTocRules() {
        appDb.txtTocRuleDao.deleteDefault()
        appDb.txtTocRuleDao.insert(*txtTocRules.toTypedArray())
    }

    fun importDefaultRssSources() {
        appDb.rssSourceDao.deleteDefault()
        appDb.rssSourceDao.insert(*rssSources.toTypedArray())
    }

    fun importDefaultDictRules() {
        appDb.dictRuleDao.insert(*dictRules.toTypedArray())
    }

    /**
     * 升级默认主题配置
     * 检查用户是否使用旧的默认主题，如果是则更新为新的默认主题
     */
    private fun upDefaultTheme() {
        ThemeConfig.upConfig()
        ThemeConfig.upDefaultThemeConfig()
    }

}
