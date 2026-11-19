#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""UNI-2701 (FU-QA-SEEDS, FU-SEEDGEN-1): phát sinh devdata cho đề test giả định.

Nguồn: docs/specs/2026-11-17-de-test-gia-dinh.md (mục 4).
Chạy từ gốc repo:  python scripts/generate_test_exam_seed.py
Phát sinh backend/src/main/resources/db/devdata/R__seed_test_exam_data.sql:
  - 14 bài hợp lệ `TEST-*` dựng từ cấu trúc mẫu hiện hành: đọc nguyên văn (cả id) từ migration
    mẫu V202611180652 (HSA, QDA, BCA, SPT, SP2E) và V202611181305 (THPTQG, V-ACT), cộng bản vá
    điểm SP2E ở V202611181234. Không sửa, không chuẩn hóa thêm.
  - 13 bài lỗi `TEST-ERR-*` dựng từ bài hợp lệ, mỗi bài đổi đúng một chỗ.
  - bước nâng cấp cho cơ sở dữ liệu đã nạp seed đợt 2B (dấu `seed-<mã>` -> `seed3-<mã>`).
  - 1.695+ câu `TESTQ-*` dùng chung cho các nguồn.
Không ghi groupOrder/shuffle/task_type_item_id (cấu trúc v2); chạy lại phải ra cùng byte.
"""

import copy
import hashlib
import json
import re
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MIGRATIONS = ROOT / "backend/src/main/resources/db/migration/exam"
# Thứ tự áp dụng: tệp sau ghi đè mẫu cùng mã của tệp trước.
TEMPLATE_MIGRATIONS = ("V202611180652__exam_templates_ba_numbers.sql",
                       "V202611181305__exam_templates_thptqg_vact.sql")
SEED = ROOT / "backend/src/main/resources/db/devdata/R__seed_test_exam_data.sql"

NS = uuid.UUID("00000000-0000-7000-8000-000000000000")

def uid(*parts):
    return str(uuid.uuid5(NS, "thi360-testseed-" + "-".join(str(p) for p in parts)))

def mid5(*parts):
    return str(uuid.uuid5(NS, "thi360-" + "-".join(str(p) for p in parts)))

def question_id(code):
    """Deterministic ID shared with PostgreSQL md5() in the generated series."""
    return str(uuid.UUID(hashlib.md5(("thi360-testq-" + code).encode("utf-8")).hexdigest()))

# ---- catalog ids (khớp seed đợt 1/2) ----
G_TASK = "00000000-0000-7000-8100-000000000006"
G_COMBO = "00000000-0000-7000-8100-000000000007"
G_TAG = "00000000-0000-7000-8100-000000000008"
G_KNOW = "00000000-0000-7000-8100-000000000009"
G_COMPETENCY = "00000000-0000-7000-8100-00000000000a"

SUBJECT = {
    "TOAN": "00000000-0000-7000-8200-000000000201",
    "VAN": "00000000-0000-7000-8200-000000000202",
    "ANH": "00000000-0000-7000-8200-000000000203",
    "LY": "00000000-0000-7000-8200-000000000204",
    "HOA": "00000000-0000-7000-8200-000000000205",
    "SINH": "00000000-0000-7000-8200-000000000206",
    "SU": "00000000-0000-7000-8200-000000000207",
    "DIA": "00000000-0000-7000-8200-000000000208",
    "VIETNAMESE": "00000000-0000-7000-8400-000000000213",
    "ECON_LAW": "00000000-0000-7000-8400-000000000214",
    "INFORMATICS": "00000000-0000-7000-8400-000000000215",
    "TECH_INDUSTRY": "00000000-0000-7000-8400-000000000216",
    "TECH_AGRICULTURE": "00000000-0000-7000-8400-000000000217",
    "SCIENTIFIC_THINKING": "00000000-0000-7000-8400-000000000218",
}
KIND = {
    "HSA": "00000000-0000-7000-8200-000000000101",
    "QDA": "00000000-0000-7000-8200-000000000102",
    "BCA": "00000000-0000-7000-8400-000000000103",
    "VACT": "00000000-0000-7000-8400-000000000104",
    "SPT": "00000000-0000-7000-8400-000000000105",
    "SP2E": "00000000-0000-7000-8400-000000000106",
    "THPTQG": "00000000-0000-7000-8400-000000000107",
}
GRADE = {
    "G10": "00000000-0000-7000-8200-000000000301",
    "G11": "00000000-0000-7000-8200-000000000302",
    "G12": "00000000-0000-7000-8200-000000000303",
}
LEVEL = {
    "NB": "00000000-0000-7000-8200-000000000401",
    "TH": "00000000-0000-7000-8200-000000000402",
    "VD": "00000000-0000-7000-8200-000000000403",
    "VDC": "00000000-0000-7000-8200-000000000404",
}
TASK = {
    "SINGLE": "00000000-0000-7000-8200-000000000601",
    "CLUSTER": "00000000-0000-7000-8200-000000000602",
    "SYNONYM": "00000000-0000-7000-8600-000000000601",
    "ANTONYM": "00000000-0000-7000-8600-000000000602",
    "DIALOGUE_COMPLETION": "00000000-0000-7000-8600-000000000603",
    "READING_COMPREHENSION": "00000000-0000-7000-8600-000000000604",
    "WORD_USAGE_ERROR": "00000000-0000-7000-8600-000000000605",
    "PASSAGE_READING": "00000000-0000-7000-8600-000000000606",
}
TAG_HIEM = mid5("catalog", "tag", "test-hiem")
KNOW_TOAN_HEP = mid5("catalog", "knowledge", "test-toan-hep")
KNOW_DIA_HEP = mid5("catalog", "knowledge", "test-dia-hep")
COMBO_QD05 = mid5("catalog", "combination", "qd05")
COMBO_QD05_VERSION = mid5("catalog", "combination-version", "qd05")

# =====================================================================
# 1) Mẫu cấu trúc hiện hành, đọc từ migration
# =====================================================================

_TEMPLATE_UPDATE = re.compile(
    r"UPDATE exam\.exam_template SET\n(?P<set>.*?)\nWHERE code = '(?P<code>[A-Z0-9_]+)';", re.S)
_TEMPLATE_COLUMN = re.compile(r"^  (?P<name>[a-z_]+) = (?P<value>.*?),?$")


def _integer(literal):
    return None if literal == "NULL" else int(literal)


def _read_templates():
    rows = {}
    for name in TEMPLATE_MIGRATIONS:
        sql = (MIGRATIONS / name).read_text(encoding="utf-8")
        for match in _TEMPLATE_UPDATE.finditer(sql):
            columns = {}
            for line in match.group("set").split("\n"):
                column = _TEMPLATE_COLUMN.match(line)
                if column:
                    columns[column.group("name")] = column.group("value")
            structure = columns["structure"]
            assert structure.startswith("'") and structure.endswith("'::jsonb"), match.group("code")
            rows[match.group("code")] = {
                "doc": json.loads(structure[1:-len("'::jsonb")].replace("''", "'")),
                "timing": columns["timing"].strip("'"),
                "duration": _integer(columns["duration_minutes"]),
                "max_score": _integer(columns["max_score"]),
                "structure_year": _integer(columns["structure_year"]),
                "matrix_year": _integer(columns["matrix_year"]),
            }
    return rows


TEMPLATE_ROWS = _read_templates()


def apply_sp2e_points_fix(code, doc):
    """V202611181234 (FU-BA-Q15): 50 prompts and ten points for these three 2026 subjects."""
    for index, part in enumerate(doc["parts"], start=1):
        part["maxPoints"] = 10.0 if code == "SP2E_ANH_2026" else 5.2 if index == 1 else 4.8
        for section in part["sections"]:
            for block in section["blocks"]:
                if block["questionType"] == "TRUE_FALSE":
                    block["requiredPointsPerQuestion"] = 0.8
                    block["expectedScoring"] = {"trueFalseMethod": "PER_STATEMENT"}
                else:
                    block["requiredPointsPerQuestion"] = 0.2


def template_row(code):
    """The template as the database holds it after every migration, as an independent copy."""
    row = copy.deepcopy(TEMPLATE_ROWS[code])
    if code in ("SP2E_ANH_2026", "SP2E_SU_2026", "SP2E_ECON_LAW_2026"):
        apply_sp2e_points_fix(code, row["doc"])
    return row


def dump(d):
    return json.dumps(d, ensure_ascii=False, separators=(",", ":"))


def sql_quote(s):
    return "'" + s.replace("'", "''") + "'"


# =====================================================================
# 2) Devdata: catalog fixture + ngân hàng câu + 27 đề test
# =====================================================================

PLAIN_SUFFIX = {
    "SINGLE_CHOICE": " One Two",
    "SHORT_ANSWER": " 1",
    "MULTIPLE_CHOICE": " One Two Three",
    "TRUE_FALSE": " First Second Third Fourth",
    "ESSAY": " Give credit for a clear argument.",
    "MATCHING": " Left one Left two Right one Right two",
}


def sc_body():
    return {"type": "SINGLE_CHOICE", "options": [
        {"id": "a", "content": "<p>One</p>", "correct": True},
        {"id": "b", "content": "<p>Two</p>", "correct": False}]}


def sa_body(answer="1"):
    return {"type": "SHORT_ANSWER", "answers": [answer], "inputHint": ""}


def mc_body():
    return {"type": "MULTIPLE_CHOICE", "scoringRule": "ALL_OR_NOTHING",
            "options": [
                {"id": "a", "content": "<p>One</p>", "correct": True},
                {"id": "b", "content": "<p>Two</p>", "correct": True},
                {"id": "c", "content": "<p>Three</p>", "correct": False}]}


def tf_body():
    return {"type": "TRUE_FALSE", "scoringMethod": "COUNT_TABLE",
            "scoreTable": [0.1, 0.25, 0.5, 1],
            "statements": [
                {"id": "a", "content": "<p>First</p>", "answer": True},
                {"id": "b", "content": "<p>Second</p>", "answer": False},
                {"id": "c", "content": "<p>Third</p>", "answer": True},
                {"id": "d", "content": "<p>Fourth</p>", "answer": False}]}


def essay_body():
    return {"type": "ESSAY", "gradingGuide": "<p>Give credit for a clear argument.</p>",
            "rubric": [], "wordLimit": 500}


def matching_body():
    return {"type": "MATCHING", "reuseRight": False,
            "left": [{"id": "a", "content": "<p>Left one</p>"},
                     {"id": "b", "content": "<p>Left two</p>"}],
            "right": [{"id": "c", "content": "<p>Right one</p>"},
                      {"id": "d", "content": "<p>Right two</p>"}],
            "pairs": [{"leftId": "a", "rightId": "c", "points": 1},
                      {"leftId": "b", "rightId": "d", "points": 1}]}


def cloze_body(n_blanks, blank_points):
    blanks = [{"id": f"b{i + 1}", "answers": [f"ans{i + 1}"], "points": blank_points}
              for i in range(n_blanks)]
    return {"type": "CLOZE",
            "passage": "<p>Text " + " ".join(f"[[b{i + 1}]]" for i in range(n_blanks)) + "</p>",
            "blanks": blanks}


def set_body(children, passage="<p>Context</p>"):
    return {"type": "QUESTION_SET", "passage": passage, "children": children}


BODY = {"SINGLE_CHOICE": sc_body, "SHORT_ANSWER": sa_body, "MULTIPLE_CHOICE": mc_body,
        "TRUE_FALSE": tf_body, "ESSAY": essay_body, "MATCHING": matching_body}

# Câu: (family_key, subject, type, points, task_code, grade, level, knowledge_ids, tag_ids, status)
QUESTIONS = []  # dict rows
SETS = []  # dict parent + children


def qrow(family, idx, subject, qtype, points, task=None, grade="G12", level="TH",
         knowledge=(), tags=(), status="READY"):
    code = f"TESTQ-{family}-{idx:03d}"
    return {"id": question_id(code), "code": code, "type": qtype, "subject": subject,
            "grade": grade, "level": level, "task": task, "points": points,
            "knowledge": tuple(knowledge), "tags": tuple(tags), "status": status}


def qset(family, idx, subject, child_type, child_points, n_children, task=None,
         child_rows=None, knowledge=(), tags=(), status="READY"):
    """child_rows: list of dicts (grade, level, knowledge) per child."""
    pcode = f"TESTQ-{family}-S{idx:03d}"
    parent_id = question_id(pcode)
    children = []
    rows = child_rows or [{}] * n_children
    for k in range(n_children):
        c = rows[k]
        ccode = f"{pcode}-c{k + 1}"
        children.append({"id": question_id(ccode), "code": ccode, "type": child_type,
                         "grade": c.get("grade", "G12"), "level": c.get("level", "TH"),
                         "points": child_points,
                         "knowledge": tuple(c.get("knowledge", knowledge)),
                         "position": k})
    return {"id": parent_id, "code": pcode, "type": "QUESTION_SET",
            "subject": subject, "grade": "G12", "level": "TH", "task": task,
            "points": child_points * n_children, "knowledge": tuple(knowledge),
            "tags": tuple(tags), "status": status, "children": children}


GL_CYCLE = [(g, l) for g in ("G10", "G11", "G12") for l in ("NB", "TH", "VD", "VDC")]


def fill_gl(rows):
    """Distribute rows across the twelve grade/difficulty cells."""
    for i, r in enumerate(rows):
        r["grade"], r["level"] = GL_CYCLE[i % len(GL_CYCLE)]




def build_pool():
    global QUESTIONS
    fam = 0
    # --- TOAN: MĐ 1đ (108) + TLN 1đ (40), trải đủ 12 ô, nhu cầu đơn: G10≥3 G11≥18 G12≥20; NB≥2 TH≥18 VD≥16 VDC≥5
    rows = [qrow("TOAN-MC1", fam := fam + 1, "TOAN", "SINGLE_CHOICE", 1, task="SINGLE")
            for _ in range(108)]
    fill_gl(rows)
    short_answers = [qrow("TOAN-SA1", fam := fam + 1, "TOAN", "SHORT_ANSWER", 1, task="SINGLE")
                     for _ in range(40)]
    fill_gl(short_answers)
    rows.extend(short_answers)
    QUESTIONS.extend(rows)
    # --- TOAN chùm 3-con MĐ 1đ: 6 bộ + 6 bộ scope
    for i in range(6):
        SETS.append(qset("TOAN-CTX", i + 1, "TOAN", "SINGLE_CHOICE", 1, 3,
                         task="CLUSTER", child_rows=[{"grade": g, "level": l} for g, l in
                                     (("G11", "TH"), ("G12", "VD"), ("G12", "VDC"))]))
    for i in range(6):
        SETS.append(qset("TOAN-CTX-HEP", i + 1, "TOAN", "SINGLE_CHOICE", 1, 3,
                         task="CLUSTER", child_rows=[{"grade": g, "level": l, "knowledge": [KNOW_TOAN_HEP]}
                                     for g, l in (("G11", "TH"), ("G12", "VD"), ("G12", "VD"))]))
    # --- TOAN scope TEST-TOAN-HEP: MĐ 60, TLN 10
    scoped = [qrow("TOAN-MC1-HEP", fam := fam + 1, "TOAN", "SINGLE_CHOICE", 1,
                   task="SINGLE", knowledge=[KNOW_TOAN_HEP]) for _ in range(60)]
    scoped += [qrow("TOAN-SA1-HEP", fam := fam + 1, "TOAN", "SHORT_ANSWER", 1,
                    task="SINGLE", knowledge=[KNOW_TOAN_HEP]) for _ in range(10)]
    fill_gl(scoped)
    QUESTIONS.extend(scoped)
    # --- TOAN MĐ 0,25: 40 câu rải đều và một câu G12/VDC cho TEST-ERR-11.
    rows = [qrow("TOAN-MC025", fam := fam + 1, "TOAN", "SINGLE_CHOICE", 0.25)
            for _ in range(40)]
    # Keep subsequent family numbers stable; the code prefix makes this ID distinct.
    rows.append(qrow("TOAN-MC025", fam + 1, "TOAN", "SINGLE_CHOICE", 0.25))
    fill_gl(rows)
    rows[-1]["grade"], rows[-1]["level"] = "G12", "VDC"
    QUESTIONS.extend(rows)
    # --- TOAN ĐS 1đ COUNT_TABLE (8)
    QUESTIONS += [qrow("TOAN-TF1", fam := fam + 1, "TOAN", "TRUE_FALSE", 1)
                  for _ in range(8)]
    # --- TOAN TLN 0,25 (20) và TLN 0,5 (12)
    QUESTIONS += [qrow("TOAN-SA025", fam := fam + 1, "TOAN", "SHORT_ANSWER", 0.25)
                  for _ in range(20)]
    QUESTIONS += [qrow("TOAN-SA05", fam := fam + 1, "TOAN", "SHORT_ANSWER", 0.5)
                  for _ in range(12)]
    # --- TOAN NĐ 0,25 (10) + chùm 5-con 0,25 (2 bộ)
    QUESTIONS += [qrow("TOAN-NQ025", fam := fam + 1, "TOAN", "MULTIPLE_CHOICE", 0.25)
                  for _ in range(10)]
    for i in range(2):
        SETS.append(qset("TOAN-SET5", i + 1, "TOAN", "SINGLE_CHOICE", 0.25, 5))
    # --- TOAN Tự luận 1đ (6)
    QUESTIONS += [qrow("TOAN-ESSAY1", fam := fam + 1, "TOAN", "ESSAY", 1)
                  for _ in range(6)]
    # --- TOAN MĐ 10đ (60)
    QUESTIONS += [qrow("TOAN-MC10", fam := fam + 1, "TOAN", "SINGLE_CHOICE", 10)
                  for _ in range(60)]
    # --- VAN MĐ 1đ 100 (20 WORD_USAGE_ERROR, 80 không lọc)
    for i in range(100):
        task = "WORD_USAGE_ERROR" if i < 20 else "SINGLE"
        QUESTIONS.append(qrow("VAN-MC1", fam := fam + 1, "VAN", "SINGLE_CHOICE", 1,
                              task=task))
    # --- VAN chùm 5-con MĐ 1đ: 10 bộ, đúng 2 mang nhãn TEST-HIEM
    for i in range(10):
        SETS.append(qset("VAN-PASSAGE", i + 1, "VAN", "SINGLE_CHOICE", 1, 5,
                         task="PASSAGE_READING",
                         tags=[TAG_HIEM] if i < 2 else ()))
    # --- VAN TL 25đ (2), chùm TL 0,8 (2 bộ × 5 con ESSAY), TL 2đ (2), TL 4đ (2)
    QUESTIONS += [qrow("VAN-ESSAY25", fam := fam + 1, "VAN", "ESSAY", 25)
                  for _ in range(2)]
    for i in range(2):
        SETS.append(qset("VAN-ESSAYSET", i + 1, "VAN", "ESSAY", 0.8, 5))
    QUESTIONS += [qrow("VAN-ESSAY2", fam := fam + 1, "VAN", "ESSAY", 2) for _ in range(2)]
    QUESTIONS += [qrow("VAN-ESSAY4", fam := fam + 1, "VAN", "ESSAY", 4) for _ in range(2)]
    # --- ANH MĐ 1đ 100 (10 SYN + 10 ANT + 10 DLG + 40 SENTENCE + 30 trống)
    anh_tasks = (["SYNONYM"] * 10 + ["ANTONYM"] * 10 + ["DIALOGUE_COMPLETION"] * 10
                 + ["SENTENCE_COMPLETION"] * 40 + ["SINGLE"] * 30)
    for i, task in enumerate(anh_tasks):
        QUESTIONS.append(qrow("ANH-MC1", fam := fam + 1, "ANH", "SINGLE_CHOICE", 1,
                              task=task))
    # --- ANH chùm 5-con MĐ 1đ: 6 bộ
    for i in range(6):
        SETS.append(qset("ANH-READ", i + 1, "ANH", "SINGLE_CHOICE", 1, 5,
                         task="READING_COMPREHENSION"))
    # --- ANH MĐ 0,75 (40); MĐ 10đ (60)
    QUESTIONS += [qrow("ANH-MC075", fam := fam + 1, "ANH", "SINGLE_CHOICE", 0.75)
                  for _ in range(40)]
    QUESTIONS += [qrow("ANH-MC10", fam := fam + 1, "ANH", "SINGLE_CHOICE", 10)
                  for _ in range(60)]
    # --- ANH MĐ 0,2 (60); chùm 5-con 0,2 (4 bộ); CLOZE 10 vị trí 0,2 (2 câu)
    QUESTIONS += [qrow("ANH-MC02", fam := fam + 1, "ANH", "SINGLE_CHOICE", 0.2)
                  for _ in range(60)]
    for i in range(4):
        SETS.append(qset("ANH-READ02", i + 1, "ANH", "SINGLE_CHOICE", 0.2, 5))
    QUESTIONS += [qrow("ANH-CLOZE02", fam := fam + 1, "ANH", "CLOZE", 2)
                  for _ in range(2)]
    # --- 5 môn Khoa học: MĐ 1đ 50, TLN 1đ 8, chùm 3-con 4 bộ; trải 12 ô
    for code in ("LY", "HOA", "SINH", "SU", "DIA"):
        rows = [qrow(f"{code}-MC1", fam := fam + 1, code, "SINGLE_CHOICE", 1)
                for _ in range(50)]
        rows += [qrow(f"{code}-SA1", fam := fam + 1, code, "SHORT_ANSWER", 1)
                 for _ in range(8)]
        fill_gl(rows)
        # đảm bảo biên: G10≥3,G11≥5,G12≥9; NB≥3,TH≥7,VD≥5,VDC≥2 phía đơn (đã có nhiều)
        QUESTIONS.extend(rows)
        for i in range(4):
            SETS.append(qset(f"{code}-CTX", i + 1, code, "SINGLE_CHOICE", 1, 3,
                             task="CLUSTER", child_rows=[{"grade": g, "level": l} for g, l in
                                         (("G10", "NB"), ("G11", "TH"), ("G12", "VD"))]))
    # --- DIA scope TEST-DIA-HEP: đúng biên 16 câu
    dia_children = [{"grade": "G10", "level": "TH", "knowledge": [KNOW_DIA_HEP]},
                    {"grade": "G11", "level": "TH", "knowledge": [KNOW_DIA_HEP]},
                    {"grade": "G12", "level": "VD", "knowledge": [KNOW_DIA_HEP]}]
    for i in range(2):
        children = [dict(c) for c in dia_children]
        children[0]["level"] = "TH" if i == 0 else "NB"
        SETS.append(qset("DIA-CTX-HEP", i + 1, "DIA", "SINGLE_CHOICE", 1, 3,
                         task="CLUSTER", child_rows=children))
    dia_sc = [("G10", "TH"), ("G11", "NB"), ("G12", "NB"),
              ("G12", "TH"), ("G12", "VDC"), ("G12", "VD")]
    for i, (g, l) in enumerate(dia_sc):
        QUESTIONS.append(qrow("DIA-MC1-HEP", fam := fam + 1, "DIA", "SINGLE_CHOICE", 1,
                              grade=g, level=l, knowledge=[KNOW_DIA_HEP]))
    dia_sa = [("G11", "VD"), ("G11", "VDC"), ("G12", "TH"), ("G12", "VD")]
    for i, (g, l) in enumerate(dia_sa):
        QUESTIONS.append(qrow("DIA-SA1-HEP", fam := fam + 1, "DIA", "SHORT_ANSWER", 1,
                              grade=g, level=l, knowledge=[KNOW_DIA_HEP]))
    # --- VIETNAMESE, SCIENTIFIC_THINKING MĐ 10đ mỗi môn 60
    for code in ("VIETNAMESE", "SCIENTIFIC_THINKING"):
        QUESTIONS += [qrow(f"{code}-MC10", fam := fam + 1, code, "SINGLE_CHOICE", 10)
                      for _ in range(60)]
    # --- INFORMATICS: MĐ 0,25 (48), ĐS 1đ (8)
    QUESTIONS += [qrow("TIN-MC025", fam := fam + 1, "INFORMATICS", "SINGLE_CHOICE", 0.25)
                  for _ in range(48)]
    QUESTIONS += [qrow("TIN-TF1", fam := fam + 1, "INFORMATICS", "TRUE_FALSE", 1)
                  for _ in range(8)]
    # --- UNI-2701, TEST-ERR-07: Địa lí mới có 14 câu MĐ + 1 chùm (ma trận lớp 2/4/11, độ khó 5/9/3).
    # Cộng với 6 câu MĐ đã có (một câu G12/VDC không dùng được) nút TEST-DIA-HEP có 13 câu MĐ dùng được:
    # lớp 1/3/9 và độ khó 4/7/2, đủ cho bài 16 câu (chùm TESTQ-DIA-CTX-HEP-S001 + 13 câu) và thiếu
    # đúng một câu MĐ cho bài 17 câu. Chỉ thêm ở cuối: mã câu đánh số theo `fam`, các mã cũ giữ nguyên.
    for g, l in (("G11", "TH"), ("G11", "TH"), ("G12", "NB"), ("G12", "NB"),
                 ("G12", "VD"), ("G12", "TH"), ("G12", "TH"), ("G12", "TH")):
        QUESTIONS.append(qrow("DIA-MC1-HEP", fam := fam + 1, "DIA", "SINGLE_CHOICE", 1,
                              grade=g, level=l, knowledge=[KNOW_DIA_HEP]))
    # decoys: mỗi họ chính thêm 1 DRAFT + 1 RETIRED (không được đếm)
    seen = set()
    decoys = []
    for r in QUESTIONS:
        key = r["code"].rsplit("-", 1)[0]
        if key in seen:
            continue
        seen.add(key)
        for st in ("DRAFT", "RETIRED"):
            d = dict(r)
            d["status"] = st
            d["code"] = f"{key}-{st[:2]}"
            d["id"] = question_id(d["code"])
            decoys.append(d)
    QUESTIONS.extend(decoys)
    seen_sets = set()
    set_decoys = []
    for original in SETS:
        family = original["code"].rsplit("-S", 1)[0]
        if family in seen_sets:
            continue
        seen_sets.add(family)
        for status in ("DRAFT", "RETIRED"):
            code = f"{family}-{status}"
            children = []
            for child in original["children"]:
                child_code = f"{code}-c{child['position'] + 1}"
                children.append({**child, "code": child_code, "id": question_id(child_code)})
            set_decoys.append({**original, "code": code, "id": question_id(code),
                               "status": status, "children": children})
    SETS.extend(set_decoys)
    return fam



def plain_of(qtype, code, children=()):
    plain = f"{code} content"
    if qtype == "QUESTION_SET":
        return plain + " Context " + " ".join(plain_of(c["type"], c["code"]) for c in children)
    if qtype == "CLOZE":
        return plain + " Text " + " ".join(f"[[b{i}]]" for i in range(1, 11))
    return plain + PLAIN_SUFFIX.get(qtype, "")

TESTQ_COLUMNS = ("id, code, type, subject_id, grade_id, level_id, task_type_id, points, "
                 "content, plain, body, parent_id, position, seed_status, knowledge_ids, tag_ids")


def task_id(task):
    if task is None:
        return "NULL"
    if task == "SENTENCE_COMPLETION":
        return f"(SELECT id FROM catalog.catalog_item WHERE group_id='{G_TASK}' AND code_normalized='sentence_completion')"
    return sql_quote(TASK[task])


def periodic(values):
    """Shortest repeating prefix, for compact grade/difficulty SQL arrays."""
    for width in range(1, len(values) + 1):
        if all(value == values[index % width] for index, value in enumerate(values)):
            return values[:width]
    raise AssertionError("non-empty series required")


def emit_ready_series(out):
    """Emit scalar READY pools via generate_series, preserving deterministic per-row IDs."""
    groups = []
    for row in QUESTIONS:
        if row["status"] != "READY":
            continue
        prefix, number = row["code"].rsplit("-", 1)
        number = int(number)
        key = (prefix, row["type"], row["subject"], row["task"], row["points"],
               row["knowledge"], row["tags"])
        if groups and groups[-1][0] == key and groups[-1][2] + 1 == number:
            groups[-1][2] = number
            groups[-1][3].append(row)
        else:
            groups.append([key, number, number, [row]])
    for (prefix, qtype, subject, task, points, knowledge, tags), start, end, rows in groups:
        # The extra TEST-ERR-11 row is corrected after the existing periodic series.
        periodic_rows = rows[:-1] if prefix == "TESTQ-TOAN-MC025" else rows
        grades = periodic([GRADE[row["grade"]] for row in periodic_rows])
        levels = periodic([LEVEL[row["level"]] for row in periodic_rows])
        grade_array = "ARRAY[" + ",".join(sql_quote(value) for value in grades) + "]::uuid[]"
        level_array = "ARRAY[" + ",".join(sql_quote(value) for value in levels) + "]::uuid[]"
        body = BODY[qtype]() if qtype != "CLOZE" else cloze_body(10, 0.2)
        suffix = plain_of(qtype, "X")[len("X content"):]
        out.append(f"INSERT INTO testq_rows ({TESTQ_COLUMNS})")
        out.append(f"SELECT md5('thi360-testq-' || seeded.code)::uuid, seeded.code, '{qtype}', '{SUBJECT[subject]}',")
        out.append(f"       ({grade_array})[1 + (n - {start}) % {len(grades)}], ({level_array})[1 + (n - {start}) % {len(levels)}],")
        out.append(f"       {task_id(task)}, {points}, '<p>' || seeded.code || ' content</p>',")
        out.append(f"       seeded.code || ' content' || {sql_quote(suffix)}, {sql_quote(dump(body))}::jsonb,")
        out.append(f"       NULL, NULL, 'READY', {pg_array(knowledge)}, {pg_array(tags)}")
        out.append(f"FROM generate_series({start}, {end}) AS series(n)")
        out.append(f"CROSS JOIN LATERAL (SELECT {sql_quote(prefix + '-')} || lpad(n::text, GREATEST(3, length(n::text)), '0') AS code) seeded;")
        if prefix == "TESTQ-TOAN-MC025":
            out.append("")
            out.append("-- TEST-ERR-11 requires exactly four eligible G12/VDC questions at 0.25 points.")
            out.append("UPDATE testq_rows")
            out.append(f"SET grade_id = '{GRADE[rows[-1]['grade']]}',")
            out.append(f"    level_id = '{LEVEL[rows[-1]['level']]}'")
            out.append(f"WHERE code = '{rows[-1]['code']}';")
        out.append("")


def emit_seed():
    build_pool()
    out = []
    out.append("-- FU/be-seed (UNI-2634): dữ liệu dev/test cho đề test giả định")
    out.append("-- (docs/specs/2026-11-17-de-test-gia-dinh.md mục 3.3, 3.1, 3.2).")
    out.append("-- Phát sinh bằng scripts/generate_test_exam_seed.py; id cố định, ON CONFLICT DO NOTHING.")
    out.append("-- Mọi số \"giả định\" chờ chuyên gia xác nhận.")
    out.append("")
    out.append("-- QUESTION_TAG + nút MAPID dùng cho bài lỗi TEST-ERR-03/07 và phạm vi MAPID hẹp của Toán.")
    items = [
        (TAG_HIEM, G_TAG, "TEST-HIEM", "test-hiem", "Nhãn test hiếm", "Rare test tag", "[MẪU] Nhãn cho TEST-ERR-03", 0),
        (KNOW_TOAN_HEP, G_KNOW, "TEST-TOAN-HEP", "test-toan-hep", "Chương test hẹp Toán", "Narrow test chapter Math", "[MẪU] Nút MAPID hẹp của Toán (nguồn thu hẹp, dùng ở phạm vi thử)", 0),
        (KNOW_DIA_HEP, G_KNOW, "TEST-DIA-HEP", "test-dia-hep", "Chương test hẹp Địa lí", "Narrow test chapter Geography", "[MẪU] Nút MAPID cho TEST-ERR-07", 1),
    ]
    for item_id, group_id, code, normalized, name_vi, name_en, description, order in items:
        out.append("INSERT INTO catalog.catalog_item (id, group_id, code, code_normalized, value, name_vi, name_en, description, display_order, status, is_system, parent_id, depth, path)")
        out.append(f"SELECT '{item_id}', g.id, '{code}', '{normalized}', GREATEST(g.last_value, COALESCE((SELECT MAX(value) FROM catalog.catalog_item WHERE group_id=g.id), 0)) + 1,")
        out.append(f"    '{name_vi}', '{name_en}', '{description}', {order}, 1, false, NULL, 1, '/{item_id}/'")
        out.append(f"FROM catalog.catalog_group g WHERE g.id='{group_id}' ON CONFLICT (group_id, code_normalized) DO NOTHING;")
    out.append("")
    out.append("INSERT INTO catalog.catalog_item_ref (item_id, ref_item_id) VALUES")
    out.append(f"  ('{KNOW_TOAN_HEP}', '{SUBJECT['TOAN']}'),")
    out.append(f"  ('{KNOW_TOAN_HEP}', '{GRADE['G12']}'),")
    out.append(f"  ('{KNOW_DIA_HEP}', '{SUBJECT['DIA']}'),")
    out.append(f"  ('{KNOW_DIA_HEP}', '{GRADE['G12']}')")
    out.append("ON CONFLICT (item_id, ref_item_id) DO NOTHING;")
    out.append("")
    out.append("-- QĐ05 là lựa chọn Tiếng Anh 50 câu trong mẫu QDA.")
    out.append("INSERT INTO catalog.catalog_item (id, group_id, code, code_normalized, value, name_vi, name_en, description, display_order, status, is_system, parent_id, depth, path)")
    out.append(f"SELECT '{COMBO_QD05}', g.id, 'QD05', 'qd05', GREATEST(g.last_value, COALESCE((SELECT MAX(value) FROM catalog.catalog_item WHERE group_id=g.id), 0)) + 1,")
    out.append(f"    'QĐ05 · Tiếng Anh', 'QD05 · English', '[MẪU] Phương án QĐ05 (QDA)', 17, 1, false, NULL, 1, '/{COMBO_QD05}/'")
    out.append(f"FROM catalog.catalog_group g WHERE g.id = '{G_COMBO}' ON CONFLICT (group_id, code_normalized) DO NOTHING;")
    out.append("INSERT INTO catalog.combination_version (id, item_id, version_no, exam_item_id, source_note, is_current)")
    out.append(f"SELECT '{COMBO_QD05_VERSION}', id, 1, '{KIND['QDA']}', '[MẪU] Phương án QĐ05', true FROM catalog.catalog_item WHERE group_id = '{G_COMBO}' AND code_normalized = 'qd05'")
    out.append("ON CONFLICT DO NOTHING;")
    out.append("INSERT INTO catalog.combination_subject (combination_version_id, subject_item_id, position)")
    out.append(f"SELECT v.id, '{SUBJECT['ANH']}', 0 FROM catalog.combination_version v JOIN catalog.catalog_item i ON i.id=v.item_id WHERE i.group_id='{G_COMBO}' AND i.code_normalized='qd05' AND v.version_no=1")
    out.append("ON CONFLICT DO NOTHING;")
    out.append("")
    out.append("UPDATE catalog.catalog_group g SET last_value = GREATEST(g.last_value,")
    out.append("    COALESCE((SELECT MAX(i.value) FROM catalog.catalog_item i WHERE i.group_id = g.id), 0));")
    out.append("")
    # ---------- question pool ----------
    out.append("-- A repeatable script may run twice in one transaction (tests): ON COMMIT DROP has not fired yet.")
    out.append("DROP TABLE IF EXISTS pg_temp.testq_rows;")
    out.append("CREATE TEMP TABLE testq_rows (")
    out.append("    id uuid PRIMARY KEY, code text NOT NULL, type text NOT NULL,")
    out.append("    subject_id uuid NOT NULL, grade_id uuid NOT NULL, level_id uuid NOT NULL,")
    out.append("    task_type_id uuid, points numeric(8,4) NOT NULL, content text NOT NULL,")
    out.append("    plain text NOT NULL, body jsonb NOT NULL, parent_id uuid, position integer,")
    out.append("    seed_status text NOT NULL, knowledge_ids uuid[] NOT NULL DEFAULT '{}',")
    out.append("    tag_ids uuid[] NOT NULL DEFAULT '{}') ON COMMIT DROP;")
    out.append("")
    emit_ready_series(out)
    out.append(f"INSERT INTO testq_rows ({TESTQ_COLUMNS}) VALUES")
    vals = []

    for r in QUESTIONS:
        if r["status"] == "READY":
            continue
        body = BODY[r["type"]]() if r["type"] != "CLOZE" else cloze_body(10, 0.2)
        vals.append(
            f"('{r['id']}','{r['code']}','{r['type']}','{SUBJECT[r['subject']]}',"
            f"'{GRADE[r['grade']]}','{LEVEL[r['level']]}',{task_id(r['task'])},{r['points']},"
            f"'<p>{r['code']} content</p>',{sql_quote(plain_of(r['type'], r['code']))},{sql_quote(dump(body))}::jsonb,NULL,NULL,"
            f"'{r['status']}',{pg_array(r['knowledge'])},{pg_array(r['tags'])})")
    for s in SETS:
        children = [{"id": c["id"], "type": c["type"], "content": f"<p>{c['code']} content</p>",
                     "body": BODY[c["type"]](), "points": c["points"],
                     "gradeItemId": GRADE[c["grade"]], "levelItemId": LEVEL[c["level"]],
                     "knowledgeItemIds": list(c["knowledge"])} for c in s["children"]]
        vals.append(
            f"('{s['id']}','{s['code']}','QUESTION_SET','{SUBJECT[s['subject']]}',"
            f"'{GRADE[s['grade']]}','{LEVEL[s['level']]}',{task_id(s['task'])},{s['points']},"
            f"'<p>{s['code']} content</p>',{sql_quote(plain_of(s['type'], s['code'], s['children']))},{sql_quote(dump(set_body(children)))}::jsonb,NULL,NULL,"
            f"'{s['status']}',{pg_array(s['knowledge'])},{pg_array(s['tags'])})")
        for c in s["children"]:
            vals.append(
                f"('{c['id']}','{c['code']}','{c['type']}','{SUBJECT[s['subject']]}',"
                f"'{GRADE[c['grade']]}','{LEVEL[c['level']]}',{task_id(s['task'])},{c['points']},"
                f"'<p>{c['code']} content</p>',{sql_quote(plain_of(c['type'], c['code']))},{sql_quote(dump(BODY[c['type']]()))}::jsonb,"
                f"'{s['id']}',{c['position']},'{s['status']}',{pg_array(c['knowledge'])},'{{}}')")
    out.append(",\n".join(vals) + ";")
    out.append("")
    # question rows (parents first for FK)
    out.append("WITH inserted AS (INSERT INTO questionbank.question (id, code, type, status, parent_id, position,")
    out.append("    subject_item_id, grade_item_id, level_item_id, task_type_item_id, points,")
    out.append("    current_version_no, working_version_no, content_text, search_text)")
    out.append("SELECT id, code, type, seed_status, NULL, NULL, subject_id, grade_id, level_id,")
    out.append("       task_type_id, points,")
    out.append("       CASE WHEN seed_status = 'DRAFT' THEN NULL ELSE 1 END,")
    out.append("       CASE WHEN seed_status = 'DRAFT' THEN 1 ELSE NULL END,")
    out.append("       plain, lower(code || ' ' || plain)")
    out.append("FROM testq_rows WHERE parent_id IS NULL")
    out.append("ON CONFLICT DO NOTHING RETURNING subject_item_id, status)")
    out.append("INSERT INTO questionbank.question_source_revision (subject_item_id, revision)")
    out.append("SELECT subject_item_id, count(*) FROM inserted WHERE status = 'READY' GROUP BY subject_item_id")
    out.append("ON CONFLICT (subject_item_id) DO UPDATE")
    out.append("SET revision = questionbank.question_source_revision.revision + EXCLUDED.revision;")
    out.append("")
    out.append("INSERT INTO questionbank.question (id, code, type, status, parent_id, position,")
    out.append("    subject_item_id, grade_item_id, level_item_id, task_type_item_id, points,")
    out.append("    current_version_no, working_version_no, content_text, search_text)")
    out.append("SELECT id, code, type, seed_status, parent_id, position, subject_id, grade_id, level_id,")
    out.append("       task_type_id, points, CASE WHEN seed_status = 'DRAFT' THEN NULL ELSE 1 END,")
    out.append("       CASE WHEN seed_status = 'DRAFT' THEN 1 ELSE NULL END, plain, lower(code || ' ' || plain)")
    out.append("FROM testq_rows WHERE parent_id IS NOT NULL")
    out.append("ON CONFLICT DO NOTHING;")
    out.append("")
    # snapshots
    out.append("INSERT INTO questionbank.question_version (question_id, version_no, snapshot, ready_at)")
    out.append("SELECT s.id, 1, jsonb_build_object(")
    out.append("    'subjectItemId', s.subject_id, 'gradeItemId', s.grade_id, 'levelItemId', s.level_id,")
    out.append("    'taskTypeItemId', s.task_type_id, 'competencyItemId', NULL, 'points', s.points,")
    out.append("    'knowledge', COALESCE((SELECT jsonb_agg(jsonb_build_object('itemId', k, 'primary', k = s.knowledge_ids[1]))")
    out.append("        FROM unnest(s.knowledge_ids) k), '[]'::jsonb),")
    out.append("    'tagItemIds', COALESCE(to_jsonb(s.tag_ids), '[]'::jsonb), 'content', s.content,")
    out.append("    'solution', NULL, 'commonMistakes', '[]'::jsonb, 'attachmentIds', '[]'::jsonb,")
    out.append("    'body', s.body, 'sourceCode', NULL),")
    out.append("    CASE WHEN q.status = 'DRAFT' THEN NULL ELSE now() END")
    out.append("FROM testq_rows s JOIN questionbank.question q ON q.id = s.id")
    out.append("ON CONFLICT DO NOTHING;")
    out.append("")
    out.append("INSERT INTO questionbank.question_knowledge (question_id, knowledge_item_id, is_primary)")
    out.append("SELECT s.id, k, k = s.knowledge_ids[1] FROM testq_rows s")
    out.append("    JOIN questionbank.question q ON q.id = s.id CROSS JOIN LATERAL unnest(s.knowledge_ids) k")
    out.append("ON CONFLICT DO NOTHING;")
    out.append("")
    out.append("INSERT INTO questionbank.question_tag (question_id, tag_item_id)")
    out.append("SELECT s.id, t FROM testq_rows s JOIN questionbank.question q ON q.id = s.id")
    out.append("    CROSS JOIN LATERAL unnest(s.tag_ids) t")
    out.append("ON CONFLICT DO NOTHING;")
    out.append("")
    out.append("")
    # ---------- exams ----------
    for exam in EXAMS:
        out.extend(emit_exam(exam))
    return "\n".join(out) + "\n"


def pg_array(ids):
    return "'{" + ",".join(str(i) for i in ids) + "}'"


# =====================================================================
# 3) 14 bài hợp lệ + 13 bài lỗi
# =====================================================================

COMBINATION_COUNT = {"QDA": 5, "BCA": 4}  # Global List của danh mục seed: QD01-QD05, CA1-CA4


def exam_row(code, name, kind, scope, template_code, *, part_code=None, subject=None,
             duration="template", max_score="template", reference_max=None, practice="RAW",
             matrix_year=None, years_confirmed=True, description=None):
    """A valid exam: the template's own structure, ids and numbers (de-test 4.4)."""
    template = template_row(template_code)
    doc = template["doc"]
    return {"code": code, "name": name, "kind": kind, "scope": scope,
            "doc": {"parts": doc["parts"]}, "exam_quotas": doc.get("examQuotas") or [],
            "timing": template["timing"],
            "duration": template["duration"] if duration == "template" else duration,
            "max_score": template["max_score"] if max_score == "template" else max_score,
            "reference_max": reference_max, "practice": practice,
            "part_code": part_code, "subject": subject, "template": template_code,
            "structure_year": template["structure_year"],
            "matrix_year": matrix_year or template["matrix_year"],
            "years_confirmed": years_confirmed,
            "description": description or f"Đề test giả định dựng từ mẫu {template_code} (UNI-2701)."}


def error_row(base, code, name, description, mutate=None, **overrides):
    """A fixture: a copy of a valid exam with exactly one change (the mutation or the override)."""
    row = copy.deepcopy(next(e for e in EXAMS if e["code"] == base))
    row.update(code=code, name=name, description=description)
    if mutate:
        mutate(row["doc"])
    row.update(overrides)
    return row


def blocks_of(node):
    """Every block below a part, section or option, in document order."""
    found = []
    for section in node.get("sections", []) + node.get("subjectPool", []):
        found += section["blocks"]
    found += node.get("blocks", [])
    for option in node.get("options", []):
        found += blocks_of(option)
    return found


def scope_nodes(node_ids):
    return {"nodeIds": node_ids, "competencyCodes": []}


def block_of(doc, group, question_type=None):
    matches = [block for part in doc["parts"] for block in blocks_of(part)
               if block.get("templateGroupCode") == group
               and question_type in (None, block["questionType"])]
    assert len(matches) == 1, (group, question_type, len(matches))
    return matches[0]


def section_of(doc, subject):
    matches = [section for part in doc["parts"]
               for section in part.get("sections", []) + part.get("subjectPool", [])
               + [s for o in part.get("options", []) for s in o["sections"]]
               if section["subjectCode"] == subject]
    assert len(matches) == 1, (subject, len(matches))
    return matches[0]


def option_of(doc, code):
    matches = [o for part in doc["parts"] for o in part.get("options", []) if o["code"] == code]
    assert len(matches) == 1, (code, len(matches))
    return matches[0]


EXAMS = [
    exam_row("TEST-HSA-01", "TEST · HSA toàn bài", "HSA", "FULL", "HSA_FULL"),
    exam_row("TEST-HSA-02", "TEST · HSA phần Toán", "HSA", "PART", "HSA_PART_MATH",
             part_code="TOAN"),
    exam_row("TEST-HSA-03", "TEST · HSA phần Văn", "HSA", "PART", "HSA_PART_LITERATURE",
             part_code="VAN"),
    exam_row("TEST-HSA-04", "TEST · HSA phần Khoa học", "HSA", "PART", "HSA_PART_SCIENCE",
             part_code="SCIENCE"),
    exam_row("TEST-QDA-01", "TEST · QDA toàn bài", "QDA", "FULL", "QDA_FULL"),
    exam_row("TEST-BCA-01", "TEST · BCA toàn bài", "BCA", "FULL", "BCA_FULL"),
    exam_row("TEST-VACT-01", "TEST · V-ACT toàn bài", "VACT", "FULL", "VACT_FULL",
             max_score=None, reference_max=1200, practice="LINEAR"),
    exam_row("TEST-SPT-01", "TEST · SPT Toán 2025", "SPT", "SUBJECT", "SPT_MATH_2025",
             subject="TOAN", duration=90),
    exam_row("TEST-SP2E-01", "TEST · SP2E Toán 2025", "SP2E", "SUBJECT", "SP2E_MATH_2025",
             subject="TOAN"),
    # Cấu trúc 2026 trên ma trận 2025, đã xác nhận: bài hợp lệ làm gốc của TEST-ERR-13.
    exam_row("TEST-SP2E-02", "TEST · SP2E Toán 2026", "SP2E", "SUBJECT", "SP2E_MATH_2026",
             subject="TOAN", matrix_year=2025),
    exam_row("TEST-SP2E-03", "TEST · SP2E Tiếng Anh 2026", "SP2E", "SUBJECT", "SP2E_ANH_2026",
             subject="ANH"),
    exam_row("TEST-THPTQG-01", "TEST · THPTQG Toán 2025", "THPTQG", "SUBJECT",
             "THPTQG_MATH_2025", subject="TOAN"),
    exam_row("TEST-THPTQG-02", "TEST · THPTQG Ngữ văn 2025", "THPTQG", "SUBJECT",
             "THPTQG_LITERATURE_2025", subject="VAN"),
    exam_row("TEST-THPTQG-03", "TEST · THPTQG Tin học 2025", "THPTQG", "SUBJECT",
             "THPTQG_INFORMATICS_2025", subject="INFORMATICS"),
]


def err01(doc):
    first = doc["parts"][0]["sections"][0]["blocks"][0]
    first["matrix"] = {"mode": None, "unit": "COUNT", "cells": [],
                       "byGrade": [{"code": "G12", "value": 12}], "byDifficulty": []}


def err02(doc):
    for block in section_of(doc, "ANH")["blocks"]:
        assert block["requiredPointsPerQuestion"] == 0.75
        block["requiredPointsPerQuestion"] = 1


def err03(doc):
    block = block_of(doc, "HSA_MATH_CONTEXT")
    assert block["questionType"] == "QUESTION_SET" and not block.get("slots")
    block["tagCodes"] = ["TEST-HIEM"]


def err04(doc):
    block = block_of(doc, "HSA_MATH_SINGLE", "SINGLE_CHOICE")
    assert block["questionCount"] == 32
    block["questionCount"] = 31


def err05(doc):
    quotas = section_of(doc, "TOAN")["answerQuotas"]["items"]
    assert quotas[1] == {"answerType": "SHORT_ANSWER", "value": 15}
    quotas[1]["value"] = 14


def err06(doc):
    block = block_of(doc, "HSA_MATH_SINGLE", "SHORT_ANSWER")
    assert block["questionCount"] == 15
    block["questionType"] = "SINGLE_CHOICE"


def err07(doc):
    section_of(doc, "DIA")["knowledgeScope"] = scope_nodes([KNOW_DIA_HEP])


def err08(doc):
    del block_of(doc, "HSA_LY_FLEX", "SHORT_ANSWER")["templateGroupCode"]


def err09(doc):
    section_of(doc, "HOA")["answerQuotas"] = {"unit": "COUNT", "items": [
        {"answerType": "SINGLE_CHOICE", "value": 12}, {"answerType": "SHORT_ANSWER", "value": 4}]}


def err10(doc):
    option_of(doc, "SCIENCE")["countsByPick"] = [17, 17, 17]


def err11(doc):
    doc["parts"][0]["sections"][0]["blocks"][0]["matrix"] = {
        "mode": "COMBINED", "unit": "COUNT",
        "cells": [{"gradeCode": "G12", "difficultyCode": "VDC", "value": 12}],
        "byGrade": [], "byDifficulty": []}


def err12(doc):
    doc["parts"][2]["sections"][0]["blocks"][0]["requiredPointsPerQuestion"] = None


EXAMS += [
    error_row("TEST-THPTQG-01", "TEST-ERR-01", "TEST · ERR ma trận thiếu cách áp",
              "Lỗi: nhóm MĐ có matrix nhưng chưa chọn cách áp (mode null) -> MATRIX_INCOMPLETE.", err01),
    error_row("TEST-BCA-01", "TEST-ERR-02", "TEST · ERR điểm phần 2 BCA",
              "Lỗi: các nhóm Tiếng Anh 1đ thay 0,75đ (phần 2: 65 > 60) -> PART_POINTS_MISMATCH.", err02),
    error_row("TEST-HSA-02", "TEST-ERR-03", "TEST · ERR chùm hiếm",
              "Lỗi: nhóm Ngữ cảnh Toán (1 bộ) thêm nhãn TEST-HIEM, không bộ Toán nào mang nhãn -> SET_SHORTAGE.", err03),
    error_row("TEST-HSA-02", "TEST-ERR-04", "TEST · ERR tổng môn 49",
              "Lỗi: nhóm MĐ 31 thay 32 (môn 49) -> SECTION_TOTAL_MISMATCH.", err04),
    error_row("TEST-HSA-02", "TEST-ERR-05", "TEST · ERR chỉ tiêu TLN 14",
              "Lỗi: chỉ tiêu Trả lời ngắn 14 (tổng 49) -> ANSWER_QUOTA_TOTAL_MISMATCH.", err05),
    error_row("TEST-HSA-02", "TEST-ERR-06", "TEST · ERR chỉ tiêu TLN mồ côi",
              "Lỗi: khối Trả lời ngắn của Toán đổi thành Một đáp án; chỉ tiêu Trả lời ngắn 15 không còn khối nào nhận -> ANSWER_TYPE_SHORTAGE.", err06),
    error_row("TEST-HSA-04", "TEST-ERR-07", "TEST · ERR Địa lí chỉ đủ 16",
              "Lỗi: Địa lí đặt phạm vi TEST-DIA-HEP, đủ đúng 16, thiếu khi nhận 17 -> INVALID ở 6 trong 10 bộ ba.",
              err07),
    error_row("TEST-HSA-04", "TEST-ERR-08", "TEST · ERR Vật lí nhóm TLN mất mã mẫu",
              "Đối chứng: nhóm Trả lời ngắn của Vật lí bỏ mã nhóm mẫu (nhóm còn lại).", err08),
    error_row("TEST-HSA-04", "TEST-ERR-09", "TEST · ERR Hóa học chỉ tiêu số câu",
              "Lỗi: chỉ tiêu Hóa học có tổng 16 câu thay vì 17 câu.", err09),
    error_row("TEST-HSA-04", "TEST-ERR-10", "TEST · ERR countsByPick 17-17-17",
              "Lỗi: countsByPick 17/17/17 -> SUBJECT_CHOICE_COUNTS_INVALID.", err10),
    error_row("TEST-THPTQG-01", "TEST-ERR-11", "TEST · ERR ô G12×VDC thiếu",
              "Lỗi: ô Lớp 12 × VDC đặt 12, bộ mẫu tối đa 4 -> CELL_SHORTAGE.", err11),
    error_row("TEST-THPTQG-01", "TEST-ERR-12", "TEST · ERR khối TLN bỏ điểm",
              "Lỗi: khối Trả lời ngắn bỏ điểm mỗi câu -> POINTS_TOTAL_MISMATCH.", err12),
    error_row("TEST-SP2E-02", "TEST-ERR-13", "TEST · ERR năm chưa xác nhận",
              "Lỗi: cấu trúc 2026, ma trận 2025, chưa xác nhận -> YEARS_UNCONFIRMED.",
              years_confirmed=False),
]



def has_essay_block(blocks, section):
    """ExamBlockRefProjector.hasEssay: an ESSAY block or child type, or an ESSAY answer quota."""
    if any(b.get("questionType") == "ESSAY" or b.get("childAnswerType") == "ESSAY" for b in blocks):
        return True
    quotas = (section or {}).get("answerQuotas")
    return bool(quotas) and any(q["answerType"] == "ESSAY" for q in quotas["items"])


def summarize(exam):
    """ExamBlockRefProjector.summarize + ExamService.summarize: the statistics columns of exam.exam."""
    parts = exam["doc"]["parts"]
    has_essay = has_english = False
    for part in parts:
        options = part.get("options", [])
        for section in (part.get("sections", []) + part.get("subjectPool", [])
                        + [s for o in options for s in o["sections"]]):
            has_essay |= has_essay_block(section["blocks"], section)
            has_english |= section["subjectCode"] == "ANH"
        for option in options:
            has_english |= option["kind"] == "SUBJECT" and option["code"] == "ANH"
            has_essay |= has_essay_block(option.get("blocks", []), None)
    question_count = sum(part["questionCount"] or 0 for part in parts)
    combinations = (COMBINATION_COUNT[exam["kind"]]
                    if any(part.get("codeSource") for part in parts) else 0)
    return question_count, has_essay, has_english, combinations


# Every place a blocks[] array can sit in structure v2 (jsonpath, lax mode unwraps the arrays).
BLOCK_MEMBER_PATHS = ("$.parts.sections.blocks", "$.parts.subjectPool.blocks",
                      "$.parts.options.sections.blocks", "$.parts.options.blocks")


def seed_marker(code):
    return f"seed3-{code.lower()}"


def upgradable(code, exam_id, structure):
    """An exam still holding exactly the structure the 2B seed wrote: the literal hash
    `seed-<code>` (every API write replaces it with a SHA-256 and sets `edited`), a draft that
    was never published. Admin-edited, reloaded, published or already upgraded rows never match."""
    return (f"{structure}.structure_hash = 'seed-{code.lower()}' AND NOT {structure}.edited"
            f" AND EXISTS (SELECT 1 FROM exam.exam e WHERE e.id = {exam_id} AND e.status = 'DRAFT')"
            f" AND NOT EXISTS (SELECT 1 FROM exam.exam_version v WHERE v.exam_id = {exam_id})")


def upgrade_statements(exam, eid, question_count, has_essay, has_english, combinations):
    """Wave 3 upgrade of a database that holds the 2B seed (docs: de-test 4.4). The structure
    itself is replaced by the upsert that follows; this statement drops the working block refs
    of the old structure and refreshes the columns derived from the template and the structure
    (name, description, status and the check history of the exam are left to the admin)."""
    code = exam["code"]
    guard = upgradable(code, "s.exam_id", "s")
    return [
        "WITH upgrading AS (SELECT s.exam_id FROM exam.exam_structure s",
        f"    WHERE s.exam_id = '{eid}' AND {guard}),",
        "dropped AS (DELETE FROM exam.exam_block_ref r USING upgrading u",
        "    WHERE r.exam_id = u.exam_id AND r.version_no IS NULL RETURNING r.id)",
        f"UPDATE exam.exam e SET template_code = {sql_quote(exam['template'])},",
        f"    structure_year = {exam['structure_year'] or 'NULL'}, matrix_year = {exam['matrix_year'] or 'NULL'},",
        f"    timing = '{exam['timing']}', duration_minutes = {exam['duration'] or 'NULL'},",
        f"    max_score = {exam['max_score'] if exam['max_score'] is not None else 'NULL'},",
        f"    question_count = {question_count}, has_essay = {'true' if has_essay else 'false'},",
        f"    has_english_option = {'true' if has_english else 'false'}, combination_count = {combinations},",
        "    check_status = CASE WHEN e.check_status = 'UNCHECKED' THEN 'UNCHECKED' ELSE 'NEEDS_RECHECK' END,",
        "    revision = e.revision + 1, updated_at = now()",
        "FROM upgrading u WHERE e.id = u.exam_id;",
        "",
    ]


def emit_exam(exam):
    eid = uid("exam", exam["code"])
    out = []
    code = exam["code"]
    qc, has_essay, has_eng, combination_count = summarize(exam)
    cols = (f"'{eid}','{code}','{code.lower()}',{sql_quote(exam['name'])},"
            f"{sql_quote(exam['description'])},NULL,'{KIND[exam['kind']]}','{exam['scope']}',"
            f"{sql_quote(exam['part_code']) if exam['part_code'] else 'NULL'},"
            f"{sql_quote(SUBJECT[exam['subject']]) if exam['subject'] else 'NULL'},"
            f"{sql_quote(exam['template']) if exam['template'] else 'NULL'},"
            f"{exam['structure_year'] if exam['structure_year'] else 'NULL'},"
            f"{exam['matrix_year'] if exam['matrix_year'] else 'NULL'},"
            f"{'true' if exam['years_confirmed'] else 'false'},'{exam['timing']}',"
            f"{exam['duration'] if exam['duration'] else 'NULL'},"
            f"{exam['max_score'] if exam['max_score'] is not None else 'NULL'},"
            f"{exam['reference_max'] if exam['reference_max'] is not None else 'NULL'},"
            f"'{exam['practice']}','DRAFT','UNCHECKED',{qc},"
            f"{'true' if has_essay else 'false'},{'true' if has_eng else 'false'},"
            f"{combination_count},false,0")
    out.append(f"-- {code}: {exam['name']}")
    out.append("INSERT INTO exam.exam (id, code, code_normalized, name, description, instructions,")
    out.append("    kind_item_id, scope, part_code, subject_item_id, template_code, structure_year,")
    out.append("    matrix_year, years_confirmed, timing, duration_minutes, max_score, reference_max,")
    out.append("    practice_scoring, status, check_status, question_count, has_essay,")
    out.append("    has_english_option, combination_count, has_unpublished_changes, revision) VALUES")
    out.append(f"  ({cols})")
    out.append("ON CONFLICT (id) DO NOTHING;")
    out.append("")
    out.extend(upgrade_statements(exam, eid, qc, has_essay, has_eng, combination_count))
    out.append("INSERT INTO exam.exam_structure AS s (exam_id, parts, exam_quotas, structure_hash, base_version,")
    out.append("    updated_by) VALUES")
    out.append(f"  ('{eid}',{sql_quote(dump(exam['doc']))}::jsonb,{sql_quote(dump(exam['exam_quotas']))}::jsonb,"
          f"'{seed_marker(code)}',NULL,NULL)")
    out.append("ON CONFLICT (exam_id) DO UPDATE SET parts = EXCLUDED.parts, exam_quotas = EXCLUDED.exam_quotas,")
    out.append("    structure_hash = EXCLUDED.structure_hash, edited = false, updated_at = now()")
    out.append(f"WHERE {upgradable(code, 's.exam_id', 's')};")
    out.append("")
    # block refs
    rows = []
    for p in exam["doc"]["parts"]:
        for s in p.get("sections", []):
            for b in s["blocks"]:
                rows.append(ref_row(eid, p, None, s, b))
        for s in p.get("subjectPool", []):
            for b in s["blocks"]:
                rows.append(ref_row(eid, p, None, s, b))
        for o in p.get("options", []):
            for s in o.get("sections", []):
                for b in s.get("blocks", []):
                    rows.append(ref_row(eid, p, o, s, b))
            for b in o.get("blocks", []):
                rows.append(ref_row(eid, p, o, None, b))
    if rows:
        out.append("INSERT INTO exam.exam_block_ref (id, exam_id, version_no, block_id, part_id,")
        out.append("    option_code, section_id, subject_item_id, question_type, answer_types,")
        out.append("    knowledge_item_ids, knowledge_mode, knowledge_inherited,")
        out.append("    tag_item_ids, competency_item_ids, required_points, combination_item_id) VALUES")
        out.append(",\n".join(rows) + "\nON CONFLICT DO NOTHING;")
        out.append("")
        # The structure above is never replaced once stored, so the refs must follow the stored one:
        # drop seeded rows for blocks it does not contain. A unique index on (exam, version, block)
        # already keeps the projection of a later edit and the seeded rows from coexisting.
        # FU-BE13-2: only an id that is a member of a blocks[] array counts; a part, section or
        # option id (or any other nested id) that equals a removed block id must not keep its ref.
        seeded_ids = ",".join(f"'{uid('ref', eid, b['id'])}'" for b in all_blocks(exam["doc"]))
        out.append("DELETE FROM exam.exam_block_ref r")
        out.append(f"WHERE r.exam_id = '{eid}' AND r.version_no IS NULL AND r.id IN ({seeded_ids})")
        out.append("  AND NOT EXISTS (SELECT 1 FROM exam.exam_structure s WHERE s.exam_id = r.exam_id")
        out.append("      AND (" + "\n        OR ".join(
            f"jsonb_path_exists(s.parts, '{path} ? (@.id == $block)', "
            "jsonb_build_object('block', r.block_id))" for path in BLOCK_MEMBER_PATHS) + "));")
        out.append("")
    return out


def all_blocks(document):
    blocks = []
    for p in document["parts"]:
        for s in p.get("sections", []) + p.get("subjectPool", []):
            blocks += s["blocks"]
        for o in p.get("options", []):
            for s in o.get("sections", []):
                blocks += s.get("blocks", [])
            blocks += o.get("blocks", [])
    return blocks


def uuid_array(refs):
    """Resolved ids in document order; unresolved codes are dropped and an empty result is NULL."""
    return "NULLIF(ARRAY_REMOVE(ARRAY[" + ",".join(refs) + "]::uuid[], NULL), '{}'::uuid[])"


def ref_row(eid, p, o, s, b):
    """Mirror of ExamBlockRefProjector for one block of the stored structure."""
    subject_code = s["subjectCode"] if s else (o["code"] if o and o["kind"] == "SUBJECT" else None)
    scope = b.get("knowledgeScope") or (s or {}).get("knowledgeScope")
    answer_types = []
    for answer_type in (b.get("questionType"), b.get("childAnswerType")):
        if answer_type and answer_type not in answer_types:
            answer_types.append(answer_type)
    if not answer_types and s and s.get("answerQuotas"):
        for item in s["answerQuotas"]["items"]:
            if item["answerType"] not in answer_types:
                answer_types.append(item["answerType"])
    know_ids = list(dict.fromkeys((scope or {}).get("nodeIds") or [])) or None
    tag_refs = [sql_quote(TASK_TAG_IDS[t]) if t in TASK_TAG_IDS else
                f"(SELECT id FROM catalog.catalog_item WHERE group_id='{G_TAG}' AND code={sql_quote(t)})"
                for t in dict.fromkeys(b.get("tagCodes") or [])]
    competency_refs = [f"(SELECT id FROM catalog.catalog_item WHERE group_id='{G_COMPETENCY}' AND code={sql_quote(c)})"
                       for c in dict.fromkeys((scope or {}).get("competencyCodes") or [])]
    return (f"('{uid('ref', eid, b['id'])}','{eid}',NULL,'{b['id']}','{p['id']}',"
            f"{sql_quote(o['code']) if o else 'NULL'},{sql_quote(s['id']) if s else 'NULL'},"
            f"{sql_quote(SUBJECT[subject_code]) if subject_code else 'NULL'},"
            f"{sql_quote(b['questionType']) if b.get('questionType') else 'NULL'},"
            f"{pg_array(answer_types) if answer_types else 'NULL'},"
            f"{pg_array(know_ids) if know_ids else 'NULL'},"
            f"{sql_quote('ANY') if scope else 'NULL'},"
            f"{'true' if not b.get('knowledgeScope') else 'false'},"
            f"{uuid_array(tag_refs) if tag_refs else 'NULL'},"
            f"{uuid_array(competency_refs) if competency_refs else 'NULL'},"
            f"{b['requiredPointsPerQuestion'] if b.get('requiredPointsPerQuestion') is not None else 'NULL'},"
            "NULL)")


REMOVED_V2_KEYS = ("taskTypeCode", "match", "includeDescendants", "groupOrder", "shuffle",
                   "blockShuffleRule", "task_type_item_id")


def assert_structure_v2():
    """FU-SEEDGEN-1: a regeneration must never bring back a key structure v2 removed."""
    for exam in EXAMS:
        text = json.dumps(exam["doc"])
        for key in REMOVED_V2_KEYS:
            assert f'"{key}"' not in text, f"{exam['code']} carries removed v2 key {key}"


TASK_TAG_IDS = {"TEST-HIEM": TAG_HIEM}


def main():
    assert_structure_v2()
    SEED.write_text(emit_seed().rstrip("\n") + "\n", encoding="utf-8", newline="\n")
    n_q = len(QUESTIONS) + sum(len(s["children"]) + 1 for s in SETS)
    print(f"wrote {SEED.name}; questions={n_q}, exams={len(EXAMS)}")


if __name__ == "__main__":
    main()
