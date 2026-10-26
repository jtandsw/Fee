#!/usr/bin/env python3
"""docx 版面收尾：清理末尾空段落、合并多余分节，保留页脚。

html-to-doc 转换时，若 HTML 用 <section role="body"> 包裹全文，会额外产生
一个 nextPage 分节符与文末空段落，导致多出一张只有页码的空白页。
本脚本在转换完成后做两件事：
  1. 删除 body 末尾的空段落（<w:p w14:paraId="..."/>）。
  2. 删除带 nextPage 的段落内 sectPr，使全文归并为单节（页脚引用保留在 body 级 sectPr）。

用法： python fix_docx_layout.py <docx路径>
"""
import os
import re
import shutil
import sys
import zipfile


def patch_document_xml(xml: str) -> tuple[str, list[str]]:
    notes = []

    # 1. 末尾空段落
    xml, n1 = re.subn(r'<w:p w14:paraId="[0-9A-F]+"/>(?=<w:sectPr>)', '', xml)
    if n1:
        notes.append(f"删除末尾空段落 {n1} 处")

    # 2. 段落内的 nextPage 分节符
    removed = [0]

    def _drop(m):
        if 'nextPage' in m.group(0):
            removed[0] += 1
            return ''
        return m.group(0)

    xml = re.sub(r'<w:sectPr>.*?</w:sectPr>', _drop, xml, flags=re.S)
    if removed[0]:
        notes.append(f"移除 nextPage 分节符 {removed[0]} 处")

    return xml, notes


def main(path: str) -> int:
    if not os.path.isfile(path):
        print(f"[error] 文件不存在: {path}")
        return 1

    tmp = path + ".tmp"
    with zipfile.ZipFile(path) as zin:
        items = [(info, zin.read(info.filename)) for info in zin.infolist()]

    notes: list[str] = []
    with zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED) as out:
        for info, data in items:
            if info.filename == 'word/document.xml':
                xml = data.decode('utf-8')
                xml, notes = patch_document_xml(xml)
                data = xml.encode('utf-8')
            out.writestr(info, data)

    shutil.move(tmp, path)
    print("; ".join(notes) if notes else "无需调整")
    return 0


if __name__ == '__main__':
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(2)
    sys.exit(main(sys.argv[1]))
