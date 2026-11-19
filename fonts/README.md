# fonts/ 篆体字体素材目录

本目录存放辈分水印篆体渲染（#233）所用的原始字体文件。上线使用的不是这里的完整 TTF，
而是由它们子集化（fonttools `pyftsubset`）后生成的 woff2，输出到
`family-tree-web/src/main/resources/static/fonts/`。

## 文件清单与授权

| 文件 | 字体名 | 来源 | 覆盖 | 授权 | 用途 |
| --- | --- | --- | --- | --- | --- |
| `bbyz-seal-script.ttf` | BBYZ Seal Script（字库数据：QuanZiKu Shuowen Jiezi (CMEX) + zdic.net） | GitHub `donghuaigu123/font-speed-convert`（MIT）仓库内 `app/assets/fonts/zhuanshu.ttf` | 4752 个常用汉字，直接映射现代 Unicode 码位 | `fsType=0`（Installable，无内嵌限制）；上游字体本身未附独立授权声明，来源数据为全字库（OFL-1.0）说文段 + 汉典 | 首选：拟子集化为 woff2 用于 `#generation-watermark .wm-char` |
| `fzxiaozhuanti-sample.ttf` | 方正小篆体 FZXiaoZhuanTi-S13T | GitHub `anfei/xiaozhuan` 仓库内 `font/fangzhengxiaozhuan.TTF` | 6763 个 GB2312 汉字 | 方正商业字库，`fsType=8`（受限许可，禁止内嵌分发） | 仅供本地对比/预览，禁止打包进前端或部署上线 |

## 备注

- 上线产物：`../family-tree-web/src/main/resources/static/fonts/seal-subset.woff2`（38KB，
  仅收录库内现有 52 个辈分字 + 数字 + `·`），由 `subset-seal.sh` 生成；
  新增辈分字后重跑该脚本即可，缺字期间浏览器自动回落楷体。
- 完整 4752 字 woff2 约 4MB，水印场景过重，不采用全量子集。
- 检索结论：Google Fonts、npm cn-fontsource、猫啃网等渠道均无现成开源小篆成品字体；
  `frankslin/kaiyuan-small-seal-font`（OFL-1.1）质量最高但官方尚未发行字型文件，
  其管线需从 Wikimedia 扫描重跑，暂不采用。
- 若项目进入商用发行（见 `docs/commercialization-plan.md`），`bbyz-seal-script.ttf`
  的来源授权链条（CMEX + zdic.net）需要在发布前进一步确认，或改用开元小篆自建管线产物。
