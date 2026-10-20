"""为 html-to-doc 转换产物补充 A4 页面设置与居中页码页脚。

convert_doc.py 会忽略 HTML 的 @page，输出的 <w:sectPr/> 为空，
导致页面为默认纸张、无页边距、无页码。本脚本做幂等的后处理。

用法: python finalize_docx.py --src in.docx --dst out.docx
"""
import argparse
import shutil
import zipfile

SECT = (
    '<w:sectPr xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
    '<w:footerReference w:type="default" r:id="rIdFtr1"/>'
    '<w:pgSz w:w="11906" w:h="16838"/>'
    '<w:pgMar w:top="1247" w:right="1361" w:bottom="1247" w:left="1361" '
    'w:header="851" w:footer="992" w:gutter="0"/>'
    '<w:docGrid w:linePitch="312"/></w:sectPr>'
)

RPR = (
    '<w:rPr><w:rFonts w:ascii="SimSun" w:hAnsi="SimSun" w:eastAsia="SimSun" w:hint="eastAsia"/>'
    '<w:color w:val="595959"/><w:sz w:val="18"/></w:rPr>'
)

FOOTER = (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
    '<w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
    '<w:p><w:pPr><w:jc w:val="center"/>'
    '<w:spacing w:before="0" w:after="0" w:line="240" w:lineRule="auto"/>' + RPR + '</w:pPr>'
    '<w:r>' + RPR + '<w:t xml:space="preserve">— </w:t></w:r>'
    '<w:r>' + RPR + '<w:fldChar w:fldCharType="begin"/></w:r>'
    '<w:r>' + RPR + '<w:instrText xml:space="preserve"> PAGE </w:instrText></w:r>'
    '<w:r>' + RPR + '<w:fldChar w:fldCharType="separate"/></w:r>'
    '<w:r>' + RPR + '<w:t>1</w:t></w:r>'
    '<w:r>' + RPR + '<w:fldChar w:fldCharType="end"/></w:r>'
    '<w:r>' + RPR + '<w:t xml:space="preserve"> —</w:t></w:r>'
    '</w:p></w:ftr>'
)


def patch(path: str, out: str) -> None:
    with zipfile.ZipFile(path) as z:
        items = {n: z.read(n) for n in z.namelist()}
        order = list(z.namelist())

    doc = items['word/document.xml'].decode('utf-8')
    if 'footerReference' in doc:
        shutil.copyfile(path, out)
        return
    if '<w:sectPr/>' in doc:
        doc = doc.replace('<w:sectPr/>', SECT)
    else:
        # 已有 sectPr 但无页脚：补 footerReference 与页面尺寸
        import re
        m = re.search(r'<w:sectPr[^>]*>', doc)
        if not m:
            raise RuntimeError('未找到 sectPr，结构异常')
        tag = m.group(0)
        doc = doc.replace(tag, tag[: -1] + ' xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
                          '<w:footerReference w:type="default" r:id="rIdFtr1"/>', 1)
        if 'w:pgSz' not in doc:
            doc = doc.replace('<w:footerReference w:type="default" r:id="rIdFtr1"/>',
                              '<w:footerReference w:type="default" r:id="rIdFtr1"/>'
                              '<w:pgSz w:w="11906" w:h="16838"/>'
                              '<w:pgMar w:top="1247" w:right="1361" w:bottom="1247" w:left="1361" '
                              'w:header="851" w:footer="992" w:gutter="0"/>', 1)
    items['word/document.xml'] = doc.encode('utf-8')

    rels = items['word/_rels/document.xml.rels'].decode('utf-8')
    if 'footer1.xml' not in rels:
        rels = rels.replace('</Relationships>',
                            '<Relationship Id="rIdFtr1" Type="http://schemas.openxmlformats.org/'
                            'officeDocument/2006/relationships/footer" Target="footer1.xml" /></Relationships>')
    items['word/_rels/document.xml.rels'] = rels.encode('utf-8')

    ct = items['[Content_Types].xml'].decode('utf-8')
    if 'footer1.xml' not in ct:
        ct = ct.replace('</Types>',
                        '<Override PartName="/word/footer1.xml" ContentType="application/vnd.'
                        'openxmlformats-officedocument.wordprocessingml.footer+xml"/></Types>')
    items['[Content_Types].xml'] = ct.encode('utf-8')

    items['word/footer1.xml'] = FOOTER.encode('utf-8')
    if 'word/footer1.xml' not in order:
        order.append('word/footer1.xml')

    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
        for n in order:
            z.writestr(n, items[n])


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--src', required=True)
    ap.add_argument('--dst', required=True)
    a = ap.parse_args()
    patch(a.src, a.dst)
    print('finalized ->', a.dst)
