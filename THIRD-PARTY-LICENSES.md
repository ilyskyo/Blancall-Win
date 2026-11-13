# 第三方组件与许可（Windows 版）

本文件与 Android 版 [THIRD-PARTY-LICENSES](https://github.com/ilyskyo/Blancall-NoAI/blob/main/THIRD-PARTY-LICENSES) 采用同一口径：**本项目源代码以 MIT 发布，第三方组件与模型各自遵循其原始许可**。

- 适用版本：`7.6.1-win.2`
- 范围：随便携包 / 用户级安装分发的二进制、模型、字体与运行时；构建期与测试期工具单列在 §12，不随包分发。
- 再分发要求：再分发本仓库或便携包（或其重要部分）时，须一并保留本文件、`LICENSE` 与各组件声明。

## 目录

1. [ncnn](#1-ncnn)
2. [CASIA-HWDB 与中文手写模型权重](#2-casia-hwdb-与中文手写模型权重)
3. [EMNIST / NIST SD19 与拉丁手写模型权重](#3-emnist--nist-sd19-与拉丁手写模型权重)
4. [Apache PDFBox](#4-apache-pdfbox)
5. [Apache POI / HWPF](#5-apache-poi--hwpf)
6. [JNA](#6-jna)
7. [org.json](#7-orgjson)
8. [Compose Multiplatform / Kotlin / Skiko / Skia](#8-compose-multiplatform--kotlin--skiko--skia)
9. [Eclipse Temurin 运行时](#9-eclipse-temurin-运行时)
10. [霞鹜文楷（LXGW WenKai）](#10-霞鹜文楷lxgw-wenkai)
11. [FSRS-6 算法](#11-fsrs-6-算法)
12. [构建期与测试期工具（不随包分发）](#12-构建期与测试期工具不随包分发)
13. [合规保留要求](#13-合规保留要求)
- [附录 A：Apache License 2.0 全文](#附录-aapache-license-20-全文)

---

## 1. ncnn

- 项目：https://github.com/Tencent/ncnn
- 版本：release 20261020，Windows x64 预编译共享库（vs2019）
- 用途：桌面端手写识别 CPU 推理
- 分发方式：`models/ncnn.dll`（动态链接；由 JNA 调用其 C API）
- 许可证：BSD 3-Clause License
- 版权：Copyright (c) 2017 Tencent
- 备注：推理配置为纯 CPU，不使用 Vulkan / GPU 后端

```
BSD 3-Clause License

Copyright (c) 2017, Tencent
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

## 2. CASIA-HWDB 与中文手写模型权重

- 数据集：CASIA Online and Offline Chinese Handwriting Databases（CASIA-HWDB）
- 发布方：中国科学院自动化研究所（Institute of Automation, CAS）· 模式识别国家重点实验室（NLPR）
- 用途：`models/hccr`（7356 类：7185 汉字 + 171 字母数字符号）的训练数据
- 分发方式：**该权重不在本仓库、也不在便携包内**；仅通过 GitHub Release 资产 `Blancall-7.6.1-handwriting-pack-win-x64.zip` 单独提供，用户手动解压到应用根目录。下载/使用即表示接受下述非商业限制。
- 版权：数据库全部样本数据的版权归中国科学院自动化研究所所有
- 数据集主页：https://nlpr.ia.ac.cn/databases/handwriting/Home.html
- 使用条款要点（学术用途条件）：
  1. 数据库免费用于学术研究；所有样本**不得用于任何商业目的**（包括产品开发）；
  2. 样本数据版权归中国科学院自动化研究所所有；
  3. 基于该数据库发表研究成果时应适当致谢（推荐引用见下）。
- 商业使用：受费用约束，商业授权请联系中科院自动化所（Cheng-Lin Liu，liucl@nlpr.ia.ac.cn）
- 推荐引用：

```
C.-L. Liu, F. Yin, D.-H. Wang, Q.-F. Wang, "CASIA online and offline Chinese
handwriting databases," Proc. 11th International Conference on Document
Analysis and Recognition (ICDAR), Beijing, China, 2011, pp. 37-41.
```

> **结论**：`models/hccr` 下的模型权重虽由本项目自行训练，但因训练数据授权限制，**不在 MIT 商业授权范围内，仅限学习、研究用途，不得用于任何商业用途**。Windows 版与 Android 版使用的权重为同一份文件（SHA-256 `3E37EFE9…B37EBF2`）；为避免在公开仓库中再分发受限制权重，Windows 仓库与便携包均不包含该文件。

## 3. EMNIST / NIST SD19 与拉丁手写模型权重

- 数据集：EMNIST Balanced（源自 NIST Special Database 19 / NIST SD19）
- 发布方：美国国家标准与技术研究院（NIST）
- 用途：`models/hccr_en`（47 类）的训练数据。EMNIST Balanced 会折叠易混淆字符对（如 l/1、O/0），因此类别数为 47 而非 62。
- 版权：NIST 商标归属美国政府；EMNIST 衍生数据遵循 NIST 数据使用条款
- 数据集主页：https://www.nist.gov/itl/iad/image-analysis/homepage-emnist
- 许可状态：NIST 公开数据集不施加非商业限制（与第 2 项不同），因此 `models/hccr_en` 位于本项目 MIT 商业授权范围内。
- 网络结构参考：https://github.com/barkiayoub/handwritten-character-recognition（Apache License 2.0，仅参考结构与转换流程，本仓库不含其代码副本）

## 4. Apache PDFBox

- 项目：https://pdfbox.apache.org/
- 版本：3.0.4（`pdfbox`、`pdfbox-io`、`fontbox`）
- 用途：PDF 文本抽取、前 3 页缩略图渲染、PDF 试卷导出
- 许可证：Apache License 2.0
- 版权：Copyright 2002-2024 The Apache Software Foundation
- NOTICE 摘要（随包保留）：

```
Apache PDFBox
Copyright 2002-2024 The Apache Software Foundation

This product includes software developed at
The Apache Software Foundation (http://www.apache.org/).
Based on source code contributed to the original PDFBox project.
Copyright (c) 2002-2007, www.pdfbox.org

Includes the Adobe Glyph List
Copyright 1997, 1998, 2002, 2007, 2010 Adobe Systems Incorporated.

Includes the Zapf Dingbats Glyph List
Copyright 2002, 2010 Adobe Systems Incorporated.

Includes the Bidi Mirroring Glyph Property (BidiMirroring-8.0.0.txt)
Copyright 1991-2015 Unicode, Inc.

Includes parts of TwelveMonkeys ImageIO
Copyright 2008-2016 Harald Kuhr

Includes the CMYK ICC-profile (CGATS001Compat-v2-micro.icc)
Released to the public domain under the Creative Commons CC0 license
by Clinton Ingram, saucecontrol
```

> PDFBox 的 `META-INF/LICENSE` 还包含其 EXTERNAL COMPONENTS 的附加声明（PDFBox 原始贡献、Adobe AFM、Red Hat 等）。上游完整文本随依赖 jar 分发，须一并保留。

## 5. Apache POI / HWPF

- 项目：https://poi.apache.org/
- 版本：5.3.0（`poi`、`poi-scratchpad`）
- 用途：旧版 `.doc`（Word 97-2003）正文抽取（HWPF）
- 许可证：Apache License 2.0
- 版权：Copyright The Apache Software Foundation
- 传递依赖（均为 Apache License 2.0）：`commons-io`、`commons-compress`、`commons-collections4`、`commons-codec`、`log4j-api`、`SparseBitSet`、`commons-math3`
- 测试样本：`desktop/src/test/resources/fixtures/sample.doc` 来自 Apache POI test-data（Apache-2.0），仅用于自动化测试，不随发布包分发

## 6. JNA

- 项目：https://github.com/java-native-access/jna
- 版本：5.15.0
- 用途：调用 `models/ncnn.dll` 的 C API
- 许可证：双许可 —— Apache License 2.0 / LGPL 2.1；本项目按 **Apache License 2.0** 使用
- 版权：Copyright 2007-2025 Timothy Wall and contributors
- 分发方式：`jna` jar 与 `com/sun/jna/win32-x86-64/jnidispatch.dll`（打包时已剔除其他平台 native）

## 7. org.json

- 项目：https://github.com/douglascrockford/JSON-java
- 版本：20240303
- 用途：与 Android 版完全一致的 JSON 读写
- 许可证：Public Domain

## 8. Compose Multiplatform / Kotlin / Skiko / Skia

- Compose Multiplatform 1.10.3、Kotlin 2.2.20、kotlinx.coroutines 1.10.2
  - 许可证：Apache License 2.0
  - 版权：JetBrains s.r.o. / JetBrains Team
- Skiko（Compose Desktop 的 Skia 绑定）
  - 许可证：Apache License 2.0
  - 备注：Skiko 发行包内含 Skia 及其原生依赖（Skia 为 BSD 3-Clause；另含 FreeType、HarfBuzz、libpng、zlib、ICU 等），各组件许可见 Skiko 发布包；本项目未修改这些二进制。
- Material Icons Extended：Apache License 2.0（Google / JetBrains 分发）

## 9. Eclipse Temurin 运行时

- 项目：https://adoptium.net/
- 版本：Eclipse Temurin 21.0.13+9（JAVA_VERSION_DATE 2026-10-20）
- 用途：`tools/package-windows.ps1` 通过 `jlink` 生成精简运行时，随便携包分发（`runtime/`）
- 许可证：GNU General Public License v2 with Classpath Exception（GPLv2 + CE）
- 说明：Classpath Exception 允许将运行时与独立程序一同分发；OpenJDK 源码可从 Adoptium / OpenJDK 获取。再分发时须保留 GPLv2 + CE 声明，不得移除运行时内的许可文件。

## 10. 霞鹜文楷（LXGW WenKai）

- 上游：https://github.com/lxgw/LxgwWenKai
- 用途：PDF 试卷导出时嵌入 Regular 字重（`desktop/src/main/resources/fonts/xwwk_regular.ttf`），保证跨机器排版一致
- 许可证：SIL Open Font License 1.1（允许嵌入与再分发）
- 说明：界面文字使用 Windows 自带字体（如 Microsoft YaHei UI），不随包分发系统字体。

## 11. FSRS-6 算法

- 上游实现：https://github.com/open-spaced-repetition/fsrs-rs（MIT License）
- 算法说明：https://github.com/open-spaced-repetition/awesome-fsrs/wiki/The-Algorithm
- 论文：J. Ye, J. Su, Y. Cao. *A Stochastic Shortest Path Algorithm for Optimizing Spaced Repetition Scheduling*. DOI: 10.1145/3534678.3539081
- 用途：`shared` 中的 Kotlin 移植实现，携带 FSRS-6 官方默认参数（21 个权重）与状态转移公式
- 兼容性：难度（D）/ 稳定性（S）状态语义与 v5 兼容，Android 版 `fsrs_state.json` 无需迁移

## 12. 构建期与测试期工具（不随包分发）

| 工具 | 版本 | 许可 | 说明 |
|---|---|---|---|
| Gradle | 8.14.3 | Apache License 2.0 | 构建 |
| JUnit Jupiter | 5.11.4 | Eclipse Public License 2.0 | 测试 |
| JUnit 4（vintage） | 4.13.2 | Eclipse Public License 1.0 | 测试 |
| WiX Toolset | 3.14 | Microsoft Reciprocal License (MS-RL) | 仅在尝试生成 MSI 时使用 |
| jpackage | JDK 21 | GPLv2 + Classpath Exception | 打包工具 |

以上工具不会出现在便携包或安装目录中（jlink 运行时另见 §9）。

## 13. 合规保留要求

1. **保留本文件**：再分发源码或二进制时，须一并保留 `THIRD-PARTY-LICENSES.md`、`LICENSE`、PDFBox / POI 的 NOTICE 与字体 OFL 声明。
2. **模型例外**：`models/hccr` 不在 MIT 商业授权范围内，且不随仓库分发；再分发 Release 手写包时须保留本文件的限制说明，并不得用于商业产品（或先取得中科院自动化所的商业授权）。
3. **ncnn**：以二进制形式再分发时须复现其 BSD 3-Clause 版权声明、条件与免责声明（见 §1）。
4. **Apache 组件**：再分发时须提供 Apache License 2.0 副本（见附录 A）并保留 NOTICE。
5. **Temurin 运行时**：再分发便携包时须保留 GPLv2 + CE 声明与运行时内的许可文件。
6. **未修改声明**：本项目未修改上述第三方组件的源码；`shared` 中的算法为独立 Kotlin 重写/移植，不是上游源码副本。
7. **许可冲突处理**：若本文件与某组件随包许可文本冲突，以该组件随包许可文本为准。

---

## 附录 A：Apache License 2.0 全文

```text

                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

   1. Definitions.

      "License" shall mean the terms and conditions for use, reproduction,
      and distribution as defined by Sections 1 through 9 of this document.

      "Licensor" shall mean the copyright owner or entity authorized by
      the copyright owner that is granting the License.

      "Legal Entity" shall mean the union of the acting entity and all
      other entities that control, are controlled by, or are under common
      control with that entity. For the purposes of this definition,
      "control" means (i) the power, direct or indirect, to cause the
      direction or management of such entity, whether by contract or
      otherwise, or (ii) ownership of fifty percent (50%) or more of the
      outstanding shares, or (iii) beneficial ownership of such entity.

      "You" (or "Your") shall mean an individual or Legal Entity
      exercising permissions granted by this License.

      "Source" form shall mean the preferred form for making modifications,
      including but not limited to software source code, documentation
      source, and configuration files.

      "Object" form shall mean any form resulting from mechanical
      transformation or translation of a Source form, including but
      not limited to compiled object code, generated documentation,
      and conversions to other media types.

      "Work" shall mean the work of authorship, whether in Source or
      Object form, made available under the License, as indicated by a
      copyright notice that is included in or attached to the work
      (an example is provided in the Appendix below).

      "Derivative Works" shall mean any work, whether in Source or Object
      form, that is based on (or derived from) the Work and for which the
      editorial revisions, annotations, elaborations, or other modifications
      represent, as a whole, an original work of authorship. For the purposes
      of this License, Derivative Works shall not include works that remain
      separable from, or merely link (or bind by name) to the interfaces of,
      the Work and Derivative Works thereof.

      "Contribution" shall mean any work of authorship, including
      the original version of the Work and any modifications or additions
      to that Work or Derivative Works thereof, that is intentionally
      submitted to Licensor for inclusion in the Work by the copyright owner
      or by an individual or Legal Entity authorized to submit on behalf of
      the copyright owner. For the purposes of this definition, "submitted"
      means any form of electronic, verbal, or written communication sent
      to the Licensor or its representatives, including but not limited to
      communication on electronic mailing lists, source code control systems,
      and issue tracking systems that are managed by, or on behalf of, the
      Licensor for the purpose of discussing and improving the Work, but
      excluding communication that is conspicuously marked or otherwise
      designated in writing by the copyright owner as "Not a Contribution."

      "Contributor" shall mean Licensor and any individual or Legal Entity
      on behalf of whom a Contribution has been received by Licensor and
      subsequently incorporated within the Work.

   2. Grant of Copyright License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      copyright license to reproduce, prepare Derivative Works of,
      publicly display, publicly perform, sublicense, and distribute the
      Work and such Derivative Works in Source or Object form.

   3. Grant of Patent License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      (except as stated in this section) patent license to make, have made,
      use, offer to sell, sell, import, and otherwise transfer the Work,
      where such license applies only to those patent claims licensable
      by such Contributor that are necessarily infringed by their
      Contribution(s) alone or by combination of their Contribution(s)
      with the Work to which such Contribution(s) was submitted. If You
      institute patent litigation against any entity (including a
      cross-claim or counterclaim in a lawsuit) alleging that the Work
      or a Contribution incorporated within the Work constitutes direct
      or contributory patent infringement, then any patent licenses
      granted to You under this License for that Work shall terminate
      as of the date such litigation is filed.

   4. Redistribution. You may reproduce and distribute copies of the
      Work or Derivative Works thereof in any medium, with or without
      modifications, and in Source or Object form, provided that You
      meet the following conditions:

      (a) You must give any other recipients of the Work or
          Derivative Works a copy of this License; and

      (b) You must cause any modified files to carry prominent notices
          stating that You changed the files; and

      (c) You must retain, in the Source form of any Derivative Works
          that You distribute, all copyright, patent, trademark, and
          attribution notices from the Source form of the Work,
          excluding those notices that do not pertain to any part of
          the Derivative Works; and

      (d) If the Work includes a "NOTICE" text file as part of its
          distribution, then any Derivative Works that You distribute must
          include a readable copy of the attribution notices contained
          within such NOTICE file, excluding those notices that do not
          pertain to any part of the Derivative Works, in at least one
          of the following places: within a NOTICE text file distributed
          as part of the Derivative Works; within the Source form or
          documentation, if provided along with the Derivative Works; or,
          within a display generated by the Derivative Works, if and
          wherever such third-party notices normally appear. The contents
          of the NOTICE file are for informational purposes only and
          do not modify the License. You may add Your own attribution
          notices within Derivative Works that You distribute, alongside
          or as an addendum to the NOTICE text from the Work, provided
          that such additional attribution notices cannot be construed
          as modifying the License.

      You may add Your own copyright statement to Your modifications and
      may provide additional or different license terms and conditions
      for use, reproduction, or distribution of Your modifications, or
      for any such Derivative Works as a whole, provided Your use,
      reproduction, and distribution of the Work otherwise complies with
      the conditions stated in this License.

   5. Submission of Contributions. Unless You explicitly state otherwise,
      any Contribution intentionally submitted for inclusion in the Work
      by You to the Licensor shall be under the terms and conditions of
      this License, without any additional terms or conditions.
      Notwithstanding the above, nothing herein shall supersede or modify
      the terms of any separate license agreement you may have executed
      with Licensor regarding such Contributions.

   6. Trademarks. This License does not grant permission to use the trade
      names, trademarks, service marks, or product names of the Licensor,
      except as required for reasonable and customary use in describing the
      origin of the Work and reproducing the content of the NOTICE file.

   7. Disclaimer of Warranty. Unless required by applicable law or
      agreed to in writing, Licensor provides the Work (and each
      Contributor provides its Contributions) on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
      implied, including, without limitation, any warranties or conditions
      of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
      PARTICULAR PURPOSE. You are solely responsible for determining the
      appropriateness of using or redistributing the Work and assume any
      risks associated with Your exercise of permissions under this License.

   8. Limitation of Liability. In no event and under no legal theory,
      whether in tort (including negligence), contract, or otherwise,
      unless required by applicable law (such as deliberate and grossly
      negligent acts) or agreed to in writing, shall any Contributor be
      liable to You for damages, including any direct, indirect, special,
      incidental, or consequential damages of any character arising as a
      result of this License or out of the use or inability to use the
      Work (including but not limited to damages for loss of goodwill,
      work stoppage, computer failure or malfunction, or any and all
      other commercial damages or losses), even if such Contributor
      has been advised of the possibility of such damages.

   9. Accepting Warranty or Additional Liability. While redistributing
      the Work or Derivative Works thereof, You may choose to offer,
      and charge a fee for, acceptance of support, warranty, indemnity,
      or other liability obligations and/or rights consistent with this
      License. However, in accepting such obligations, You may act only
      on Your own behalf and on Your sole responsibility, not on behalf
      of any other Contributor, and only if You agree to indemnify,
      defend, and hold each Contributor harmless for any liability
      incurred by, or claims asserted against, such Contributor by reason
      of your accepting any such warranty or additional liability.

   END OF TERMS AND CONDITIONS

   APPENDIX: How to apply the Apache License to your work.

      To apply the Apache License to your work, attach the following
      boilerplate notice, with the fields enclosed by brackets "[]"
      replaced with your own identifying information. (Don't include
      the brackets!)  The text should be enclosed in the appropriate
      comment syntax for the file format. We also recommend that a
      file or class name and description of purpose be included on the
      same "printed page" as the copyright notice for easier
      identification within third-party archives.

   Copyright [yyyy] [name of copyright owner]

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```

