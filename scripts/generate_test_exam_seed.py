#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""FU/be-seed (UNI-2634): phát sinh migration và devdata cho đề test giả định.

Nguồn: docs/specs/2026-11-17-de-test-gia-dinh.md (số liệu "giả định" của mục 2-3).
Chạy từ gốc repo:  python scripts/generate_test_exam_seed.py
Phát sinh:
  - backend/src/main/resources/db/migration/exam/V202611171200__assumed_templates_fill.sql
  - backend/src/main/resources/db/devdata/R__seed_test_exam_data.sql
Không ghi groupOrder/shuffle (slice be-api bỏ các trường này).
"""

import hashlib
import json
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MIG_EXAM = ROOT / "backend/src/main/resources/db/migration/exam/V202611171200__assumed_templates_fill.sql"
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
TASK_SENTENCE = mid5("catalog", "task", "sentence-completion")  # migration mới
TAG_HIEM = mid5("catalog", "tag", "test-hiem")
KNOW_TOAN_HEP = mid5("catalog", "knowledge", "test-toan-hep")
KNOW_DIA_HEP = mid5("catalog", "knowledge", "test-dia-hep")
COMBO_QD05 = mid5("catalog", "combination", "qd05")
COMBO_QD05_VERSION = mid5("catalog", "combination-version", "qd05")

# =====================================================================
# 1) JSON cấu trúc (dùng chung cho migration exam_template và devdata exam)
# =====================================================================

def blk(bid, name, qtype, qcount=None, sets=None, size=None, task=None,
        child=None, group=None, rppq=1, tags=None, expected=None, locked=None,
        matrix=None, scope=None):
    if qcount is None and sets is not None and size is not None:
        qcount = sets * size
    return {"id": bid, "name": name, "questionType": qtype, "questionCount": qcount,
            "setCount": sets, "setSize": size, "knowledgeScope": scope, "matrix": matrix,
            "taskTypeCode": task, "childAnswerType": child, "templateGroupCode": group,
            "answerTypeLocked": locked, "requiredPointsPerQuestion": rppq,
            "tagCodes": tags, "expectedScoring": expected}

def scope_nodes(node_ids, match="ANY"):
    return {"nodeIds": node_ids, "match": match, "includeDescendants": True,
            "competencyCodes": []}

def section(sid, subject, qcount, blocks, scope=None, quotas=None, matrix=None,
            choice=None):
    return {"id": sid, "subjectCode": subject, "questionCount": qcount,
            "knowledgeScope": scope, "answerQuotas": quotas, "matrix": matrix,
            "choiceGroup": choice, "blocks": blocks}

def part(pid, name, duration, qcount, sections=None, options=None, max_points=None,
         choose=None, paper=None):
    return {"id": pid, "order": None, "name": name, "durationMinutes": duration,
            "questionCount": qcount, "questionCountOnPaper": paper if paper is not None else qcount,
            "questionCountRequired": qcount,
            "participation": {"mode": "CHOICE" if choose else "ALL",
                              "choose": choose},
            "maxPoints": max_points, "sections": sections or [],
            "options": options or [], "mode": "CHOICE" if choose else "FIXED"}

def option(oid, kind, code, name=None, sections=None, blocks=None,
           choose_subjects=None, counts=None, confirmed=None):
    return {"id": oid, "kind": kind, "code": code, "name": name,
            "chooseSubjects": choose_subjects, "countsByPick": counts,
            "expertConfirmed": confirmed, "sections": sections or [],
            "blocks": blocks or []}

def doc(parts):
    return {"parts": [{**part, "order": i} for i, part in enumerate(parts, start=1)]}

SCIENCE_SUBJECTS = [("LY", "Vật lí", "NATURAL"), ("HOA", "Hóa học", "NATURAL"),
                    ("SINH", "Sinh học", "NATURAL"), ("SU", "Lịch sử", "SOCIAL"),
                    ("DIA", "Địa lí", "SOCIAL")]

SCI_QUOTA = {"unit": "PERCENT", "items": [
    {"answerType": "SINGLE_CHOICE", "value": 75},
    {"answerType": "SHORT_ANSWER", "value": 25}]}
SCI_MATRIX = {"mode": "SEPARATE", "unit": "PERCENT", "cells": [],
              "byGrade": [{"code": "G10", "value": 20}, {"code": "G11", "value": 30},
                          {"code": "G12", "value": 50}],
              "byDifficulty": [{"code": "NB", "value": 20}, {"code": "TH", "value": 40},
                               {"code": "VD", "value": 30}, {"code": "VDC", "value": 10}]}
MATH_QUOTA = {"unit": "COUNT", "items": [
    {"answerType": "SINGLE_CHOICE", "value": 35},
    {"answerType": "SHORT_ANSWER", "value": 15}]}
MATH_MATRIX = {"mode": "SEPARATE", "unit": "COUNT", "cells": [],
               "byGrade": [{"code": "G10", "value": 3}, {"code": "G11", "value": 20},
                           {"code": "G12", "value": 27}],
               "byDifficulty": [{"code": "NB", "value": 2}, {"code": "TH", "value": 21},
                                {"code": "VD", "value": 20}, {"code": "VDC", "value": 7}]}


def hsa_math_section(sec_id="s-math", single_count=41, quota=None, scope=None):
    return section(sec_id, "TOAN", 50, [
        blk("b-math-single", "Câu đơn Toán", None, qcount=single_count,
            task="SINGLE", group="HSA_MATH_SINGLE"),
        blk("b-math-set", "Chùm ngữ cảnh Toán", "QUESTION_SET", sets=3, size=3,
            task="CLUSTER", child="SINGLE_CHOICE", group="HSA_MATH_CONTEXT",
            locked=True)],
        scope=scope, quotas=quota if quota is not None else MATH_QUOTA,
        matrix=MATH_MATRIX)


def hsa_lit_section(sec_id="s-lit", tag=None):
    return section(sec_id, "VAN", 50, [
        blk("b-lit-word", "Nhận diện lỗi dùng từ", "SINGLE_CHOICE", 10,
            task="WORD_USAGE_ERROR", group="HSA_VN_WORD_USAGE", locked=True),
        blk("b-lit-single", "Câu đơn khác", "SINGLE_CHOICE", 15,
            group="HSA_VN_SINGLE"),
        blk("b-lit-passage", "Đọc ngữ liệu", "QUESTION_SET", sets=5, size=5,
            task="PASSAGE_READING", child="SINGLE_CHOICE",
            group="HSA_VN_PASSAGE", tags=["TEST-HIEM"] if tag else None)])


def hsa_eng_section(sec_id="s-eng", sentence_group=True):
    groups = [
        blk("b-eng-syn", "Từ đồng nghĩa", "SINGLE_CHOICE", 5, task="SYNONYM",
            group="HSA_EN_SYNONYM", locked=True),
        blk("b-eng-ant", "Từ trái nghĩa", "SINGLE_CHOICE", 5, task="ANTONYM",
            group="HSA_EN_ANTONYM", locked=True),
        blk("b-eng-dlg", "Hoàn thành hội thoại", "SINGLE_CHOICE", 5,
            task="DIALOGUE_COMPLETION", group="HSA_EN_DIALOGUE", locked=True)]
    if sentence_group:
        groups.append(blk("b-eng-sentence", "Hoàn thành câu", "SINGLE_CHOICE", 20,
                          task="SENTENCE_COMPLETION", group="HSA_EN_SENTENCE"))
    else:
        groups[0]["questionCount"] = 12
        groups[1]["questionCount"] = 12
        groups[2]["questionCount"] = 11
    groups.append(blk("b-eng-read", "Đọc hiểu", "QUESTION_SET", sets=3, size=5,
                      task="READING_COMPREHENSION", child="SINGLE_CHOICE",
                      group="HSA_EN_READING", locked=True))
    return section(sec_id, "ANH", 50, groups)


def hsa_science_option(oid="o-sci", dia_scope=None):
    secs = []
    for i, (code, name, grp) in enumerate(SCIENCE_SUBJECTS):
        scope = scope_nodes([KNOW_DIA_HEP]) if (code == "DIA" and dia_scope) else None
        secs.append(section(f"s-sci-{code.lower()}", code, None, [
            blk(f"b-sci-{code.lower()}-cluster", f"{name} · chùm", "QUESTION_SET",
                sets=2, size=3, task="CLUSTER", child="SINGLE_CHOICE",
                group=f"HSA_{code}_CLUSTER"),
            blk(f"b-sci-{code.lower()}-rest", f"{name} · lấy phần còn lại", None,
                group=f"HSA_{code}_REMAINDER")],
            scope=scope, quotas=SCI_QUOTA, matrix=SCI_MATRIX, choice=grp))
    return option(oid, "SUBJECT_CHOICE", "SCIENCE", "Khoa học", sections=secs,
                  choose_subjects=3, counts=[17, 17, 16])


def hsa_part3(dia_scope=None):
    return part("p3", "Phần 3 · Khoa học hoặc Tiếng Anh", 60, 50,
                options=[hsa_science_option(dia_scope=dia_scope),
                         option("o-eng", "SUBJECT", "ANH", "Tiếng Anh",
                                sections=[hsa_eng_section("s-eng3")])],
                max_points=50, choose=1)


def template_hsa_full():
    return doc([
        part("p1", "Phần 1 · Toán học và Xử lí số liệu", 75, 50,
             sections=[hsa_math_section("s-math1")], max_points=50),
        part("p2", "Phần 2 · Văn học – Ngôn ngữ", 60, 50,
             sections=[hsa_lit_section("s-lit2")], max_points=50),
        hsa_part3(),
    ])


def template_hsa_part_math():
    return doc([part("p1", "Toán học và Xử lí số liệu", 75, 50,
                     sections=[hsa_math_section("s-math1")], max_points=50)])


def template_hsa_part_lit():
    return doc([part("p2", "Văn học – Ngôn ngữ", 60, 50,
                     sections=[hsa_lit_section("s-lit2")], max_points=50)])


def template_hsa_part_science():
    return doc([hsa_part3()])


def template_qda_full():
    return doc([
        part("p1", "Phần 1 · Toán học và Xử lí số liệu", 80, 50,
             sections=[section("s-qda-toan", "TOAN", 50,
                               [blk("b-qda-toan", "Trắc nghiệm", "SINGLE_CHOICE", 50)])],
             max_points=50),
        part("p2", "Phần 2 · Ngôn ngữ – Văn học", 55, 50,
             sections=[section("s-qda-van", "VAN", 50,
                               [blk("b-qda-van", "Trắc nghiệm", "SINGLE_CHOICE", 50)])],
             max_points=50),
        part("p3", "Phần 3 · Khoa học (3A: QĐ01–QĐ04) hoặc Tiếng Anh (3B: QĐ05)",
             60, 50, choose=1, max_points=50, options=[
                option("o-qd01", "COMBINATION", "QD01", "QĐ01",
                       sections=[section("s-qd01-ly", "LY", 25,
                                         [blk("b-qd01-ly", "Trắc nghiệm", "SINGLE_CHOICE", 25)]),
                                 section("s-qd01-hoa", "HOA", 25,
                                         [blk("b-qd01-hoa", "Trắc nghiệm", "SINGLE_CHOICE", 25)])]),
                option("o-qd02", "COMBINATION", "QD02", "QĐ02",
                       sections=[section("s-qd02-hoa", "HOA", 25,
                                         [blk("b-qd02-hoa", "Trắc nghiệm", "SINGLE_CHOICE", 25)]),
                                 section("s-qd02-sinh", "SINH", 25,
                                         [blk("b-qd02-sinh", "Trắc nghiệm", "SINGLE_CHOICE", 25)])]),
                option("o-qd03", "COMBINATION", "QD03", "QĐ03",
                       sections=[section("s-qd03-ly", "LY", 25,
                                         [blk("b-qd03-ly", "Trắc nghiệm", "SINGLE_CHOICE", 25)]),
                                 section("s-qd03-sinh", "SINH", 25,
                                         [blk("b-qd03-sinh", "Trắc nghiệm", "SINGLE_CHOICE", 25)])]),
                option("o-qd04", "COMBINATION", "QD04", "QĐ04",
                       sections=[section("s-qd04-su", "SU", 25,
                                         [blk("b-qd04-su", "Trắc nghiệm", "SINGLE_CHOICE", 25)]),
                                 section("s-qd04-dia", "DIA", 25,
                                         [blk("b-qd04-dia", "Trắc nghiệm", "SINGLE_CHOICE", 25)])]),
                option("o-qd05", "COMBINATION", "QD05", "QĐ05",
                       sections=[section("s-qd05-anh", "ANH", 50,
                                         [blk("b-qd05-anh", "Trắc nghiệm", "SINGLE_CHOICE", 50)])]),
             ]),
    ])


def template_bca_full(english_points=0.75, part2_max=60):
    return doc([
        part("p1", "Phần 1 · Tự luận Ngữ văn", None, 1, max_points=25,
             sections=[section("s-bca-van", "VAN", 1, [
                 blk("b-bca-essay", "Nghị luận xã hội (tối thiểu 500 chữ)",
                     "ESSAY", 1, rppq=25)])]),
        part("p2", "Phần 2 · Trắc nghiệm bắt buộc", None, 65, max_points=part2_max,
             sections=[
                 section("s-bca-toan", "TOAN", 35, [
                     blk("b-bca-toan-mc", "Trắc nghiệm chọn một", "SINGLE_CHOICE", 25),
                     blk("b-bca-toan-sa", "Trả lời ngắn", "SHORT_ANSWER", 10)]),
                 section("s-bca-su", "SU", 10, [
                     blk("b-bca-su", "Trắc nghiệm", "SINGLE_CHOICE", 10)]),
                 section("s-bca-anh", "ANH", 20, [
                     blk("b-bca-anh", "Trắc nghiệm", "SINGLE_CHOICE", 20,
                         rppq=english_points)])]),
        part("p3", "Phần 3 · Tự chọn", None, 15, choose=1, max_points=15, options=[
            option(f"o-ca{i}", "COMBINATION", f"CA{i}", f"CA{i}",
                   sections=[section(f"s-ca{i}", subj, 15,
                                     [blk(f"b-ca{i}", "Trắc nghiệm",
                                          "SINGLE_CHOICE", 15)])])
            for i, subj in enumerate(("LY", "HOA", "SINH", "DIA"), start=1)]),
    ])


def template_vact_full():
    return doc([
        part("p1", "Phần 1 · Sử dụng ngôn ngữ", None, 60, max_points=600,
             sections=[
                 section("s-va-vie", "VIETNAMESE", 30,
                         [blk("b-va-vie", "Tiếng Việt", "SINGLE_CHOICE", 30, rppq=10)]),
                 section("s-va-anh", "ANH", 30,
                         [blk("b-va-anh", "Tiếng Anh", "SINGLE_CHOICE", 30, rppq=10)])]),
        part("p2", "Phần 2 · Toán học", None, 30, max_points=300,
             sections=[section("s-va-toan", "TOAN", 30,
                               [blk("b-va-toan", "Toán học", "SINGLE_CHOICE", 30, rppq=10)])]),
        part("p3", "Phần 3 · Tư duy khoa học", None, 30, max_points=300,
             sections=[section("s-va-st", "SCIENTIFIC_THINKING", 30, [
                 blk("b-va-st-1", "Logic, phân tích số liệu", "SINGLE_CHOICE", 12, rppq=10),
                 blk("b-va-st-2", "Suy luận khoa học", "SINGLE_CHOICE", 18, rppq=10)])]),
    ])


def subject_doc(subject, name, duration, qcount, blocks, paper=None):
    return doc([part("p1", name, duration, qcount,
                     sections=[section("s1", subject, qcount, blocks)],
                     max_points=10, paper=paper)])


TEMPLATES = {
    # (structure_doc, duration_minutes giữ nguyên nếu None, expert_note giữ)
    "QDA_FULL": (template_qda_full(), 195, None),
    "BCA_FULL": (template_bca_full(), 180, None),
    "VACT_FULL": (template_vact_full(), 150, None),
    "SP2E_MATH_2025": (subject_doc("TOAN", "Toán", 90, 22, [
        blk("b1", "Trắc nghiệm chọn một", "SINGLE_CHOICE", 12, rppq=0.25),
        blk("b2", "Đúng/Sai", "TRUE_FALSE", 4, rppq=1),
        blk("b3", "Trả lời ngắn", "SHORT_ANSWER", 6, rppq=0.5)]), 90,
        "Chia dạng câu 12–4–6 và điểm 0,25/1/0,5 là giả định (đề test giả định mục 2.3)."),
    "SP2E_MATH_2026": (subject_doc("TOAN", "Toán", 90, 40, [
        blk("b1", "Lựa chọn một đáp án", "SINGLE_CHOICE", 20, rppq=0.25),
        blk("b2", "Lựa chọn nhiều đáp án", "MULTIPLE_CHOICE", 5, rppq=0.25),
        blk("b3", "Dữ liệu tổng hợp", "QUESTION_SET", sets=1, size=5, rppq=0.25),
        blk("b4", "Trả lời ngắn", "SHORT_ANSWER", 10, rppq=0.25)]), 90,
        "Điểm 0,25/câu và thời lượng 90 phút là giả định (đề test giả định mục 2.2, 2.3)."),
    "THPTQG_LITERATURE_2025": (subject_doc("VAN", "Ngữ văn", 120, 7, [
        blk("b1", "Đọc hiểu", "QUESTION_SET", sets=1, size=5, child="ESSAY", rppq=0.8),
        blk("b2", "Viết đoạn văn (khoảng 200 chữ)", "ESSAY", 1, rppq=2),
        blk("b3", "Viết bài văn (khoảng 600 chữ)", "ESSAY", 1, rppq=4)]), 120,
        "Đọc hiểu là chùm 5 câu con Tự luận, tổng 4 điểm; điểm theo hướng dẫn chấm."),
    "THPTQG_INFORMATICS_2025": (doc([
        part("p1", "Phần I · Trắc nghiệm chọn một", None, 24, max_points=6,
             sections=[section("s-tin-1", "INFORMATICS", 24,
                               [blk("b-tin-mc", "Trắc nghiệm chọn một",
                                    "SINGLE_CHOICE", 24, rppq=0.25)])]),
        part("p2", "Phần II · Đúng/Sai", None, 4, max_points=4, choose=1, paper=6,
             sections=[section("s-tin-tf", "INFORMATICS", 2,
                               [blk("b-tin-tf", "Đúng/Sai chung", "TRUE_FALSE", 2)])],
             options=[
                 option("o-cs", "BRANCH", "CS", "Khoa học máy tính",
                        sections=[section("s-tin-cs", "INFORMATICS", 2,
                                          [blk("b-tin-cs", "Đúng/Sai Khoa học máy tính",
                                               "TRUE_FALSE", 2)])]),
                 option("o-ict", "BRANCH", "ICT", "Tin học ứng dụng",
                        sections=[section("s-tin-ict", "INFORMATICS", 2,
                                          [blk("b-tin-ict", "Đúng/Sai Tin học ứng dụng",
                                               "TRUE_FALSE", 2)])])]),
    ]), 50, "Đề in 30 câu (6 câu Đúng/Sai trên đề: 2 chung + 2 CS + 2 ICT); học viên làm 28."),
    "HSA_FULL": (template_hsa_full(), 195, None),
    "HSA_PART_MATH": (template_hsa_part_math(), 75, None),
    "HSA_PART_LITERATURE": (template_hsa_part_lit(), 60, None),
    "HSA_PART_SCIENCE": (template_hsa_part_science(), 60, None),
}

SPT_BLOCKS = {
    "VAN": [blk("b1", "Đọc hiểu", "QUESTION_SET", sets=2, size=5,
                child="SINGLE_CHOICE", rppq=0.4),
            blk("b2", "Viết đoạn", "ESSAY", 1, rppq=2),
            blk("b3", "Viết bài", "ESSAY", 1, rppq=4)],
    "ANH": [blk("b1", "Trắc nghiệm chọn một", "SINGLE_CHOICE", 20, rppq=0.25),
            blk("b2", "Đọc hiểu", "QUESTION_SET", sets=2, size=6,
                child="SINGLE_CHOICE", rppq=0.25),
            blk("b3", "Viết", "ESSAY", 1, rppq=2)],
    "LY": [blk("b1", "Trắc nghiệm chọn một", "SINGLE_CHOICE", 12, rppq=0.25),
           blk("b2", "Đúng/Sai", "TRUE_FALSE", 4, rppq=1),
           blk("b3", "Trả lời ngắn", "SHORT_ANSWER", 4, rppq=0.5),
           blk("b4", "Tự luận", "ESSAY", 1, rppq=1)],
    "HOA": [blk("b1", "Trắc nghiệm chọn một", "SINGLE_CHOICE", 18, rppq=0.25),
            blk("b2", "Đúng/Sai", "TRUE_FALSE", 4, rppq=1),
            blk("b3", "Trả lời ngắn", "SHORT_ANSWER", 5, rppq=0.3)],
    "SINH": [blk("b1", "Trắc nghiệm chọn một", "SINGLE_CHOICE", 12, rppq=0.25),
             blk("b2", "Đúng/Sai", "TRUE_FALSE", 4, rppq=1),
             blk("b3", "Trả lời ngắn", "SHORT_ANSWER", 6, rppq=0.5)],
    "SU": [blk("b1", "Trắc nghiệm chọn một", "SINGLE_CHOICE", 20, rppq=0.25),
           blk("b2", "Đúng/Sai", "TRUE_FALSE", 4, rppq=1),
           blk("b3", "Tự luận", "ESSAY", 1, rppq=1)],
    "DIA": [blk("b1", "Trắc nghiệm chọn một", "SINGLE_CHOICE", 8, rppq=0.25),
            blk("b2", "Đúng/Sai", "TRUE_FALSE", 4, rppq=1),
            blk("b3", "Trả lời ngắn", "SHORT_ANSWER", 8, rppq=0.5)],
    "ECON_LAW": [blk("b1", "Trắc nghiệm chọn một", "SINGLE_CHOICE", 16, rppq=0.25),
                 blk("b2", "Đúng/Sai", "TRUE_FALSE", 4, rppq=1),
                 blk("b3", "Tự luận", "ESSAY", 1, rppq=2)],
    "INFORMATICS": [blk("b1", "Trắc nghiệm chọn một", "SINGLE_CHOICE", 20, rppq=0.25),
                    blk("b2", "Đúng/Sai", "TRUE_FALSE", 4, rppq=1),
                    blk("b3", "Tự luận", "ESSAY", 1, rppq=1)],
    "TECH_INDUSTRY": [blk("b1", "Trắc nghiệm chọn một", "SINGLE_CHOICE", 16, rppq=0.25),
                      blk("b2", "Đúng/Sai", "TRUE_FALSE", 4, rppq=1),
                      blk("b3", "Trả lời ngắn", "SHORT_ANSWER", 4, rppq=0.5)],
    "TECH_AGRICULTURE": [blk("b1", "Trắc nghiệm chọn một", "SINGLE_CHOICE", 16, rppq=0.25),
                         blk("b2", "Đúng/Sai", "TRUE_FALSE", 4, rppq=1),
                         blk("b3", "Trả lời ngắn", "SHORT_ANSWER", 4, rppq=0.5)],
}
SPT_META = {  # code -> (subject, duration, questionCount, tên môn)
    "SPT_VAN_2025": ("VAN", 90, 12, "Ngữ văn"),
    "SPT_ANH_2025": ("ANH", 60, 33, "Tiếng Anh"),
    "SPT_LY_2025": ("LY", 60, 21, "Vật lí"),
    "SPT_HOA_2025": ("HOA", 60, 27, "Hóa học"),
    "SPT_SINH_2025": ("SINH", 60, 22, "Sinh học"),
    "SPT_SU_2025": ("SU", 60, 25, "Lịch sử"),
    "SPT_DIA_2025": ("DIA", 60, 20, "Địa lí"),
    "SPT_ECON_LAW_2025": ("ECON_LAW", 60, 21, "GD kinh tế và pháp luật"),
    "SPT_INFORMATICS_2025": ("INFORMATICS", 60, 25, "Tin học"),
    "SPT_TECH_INDUSTRY_2025": ("TECH_INDUSTRY", 60, 24, "Công nghệ – công nghiệp"),
    "SPT_TECH_AGRICULTURE_2025": ("TECH_AGRICULTURE", 60, 24, "Công nghệ – nông nghiệp"),
}
SP2E_BLOCKS = {
    "VAN": [blk("b1", "Trắc nghiệm chọn một", "SINGLE_CHOICE", 10, rppq=0.2),
            blk("b2", "Đọc hiểu", "QUESTION_SET", sets=2, size=5,
                child="SINGLE_CHOICE", rppq=0.2),
            blk("b3", "Viết đoạn", "ESSAY", 1, rppq=2),
            blk("b4", "Viết bài", "ESSAY", 1, rppq=4)],
    "ANH": [blk("b1", "Trắc nghiệm chọn một", "SINGLE_CHOICE", 30, rppq=0.2),
            blk("b2", "Đọc hiểu", "QUESTION_SET", sets=2, size=5,
                child="SINGLE_CHOICE", rppq=0.2),
            blk("b3", "Điền khuyết", "CLOZE", sets=1, size=10, rppq=0.2)],
    "LY_HOA_SINH_DIA": [blk("b1", "Trắc nghiệm chọn một", "SINGLE_CHOICE", 16, rppq=0.25),
                        blk("b2", "Nhiều đáp án", "MULTIPLE_CHOICE", 4, rppq=0.25),
                        blk("b3", "Ghép đôi", "MATCHING", 2, rppq=1),
                        blk("b4", "Trả lời ngắn", "SHORT_ANSWER", 6, rppq=0.5)],
    "SU_ECON": [blk("b1", "Trắc nghiệm chọn một", "SINGLE_CHOICE", 24, rppq=0.25),
                blk("b2", "Nhiều đáp án", "MULTIPLE_CHOICE", 4, rppq=0.25),
                blk("b3", "Ghép đôi", "MATCHING", 2, rppq=1),
                blk("b4", "Trả lời ngắn", "SHORT_ANSWER", 2, rppq=0.5)],
}
SP2E_META = {
    "SP2E_VAN_2026": ("VAN", 90, 22, "Ngữ văn", "VAN"),
    "SP2E_ANH_2026": ("ANH", 60, 50, "Tiếng Anh", "ANH"),
    "SP2E_LY_2026": ("LY", 60, 28, "Vật lí", "LY_HOA_SINH_DIA"),
    "SP2E_HOA_2026": ("HOA", 60, 28, "Hóa học", "LY_HOA_SINH_DIA"),
    "SP2E_SINH_2026": ("SINH", 60, 28, "Sinh học", "LY_HOA_SINH_DIA"),
    "SP2E_DIA_2026": ("DIA", 60, 28, "Địa lí", "LY_HOA_SINH_DIA"),
    "SP2E_SU_2026": ("SU", 60, 32, "Lịch sử", "SU_ECON"),
    "SP2E_ECON_LAW_2026": ("ECON_LAW", 60, 32, "GD kinh tế và pháp luật", "SU_ECON"),
}
SPT_MATH = ("TOAN", 90, 25, "Toán", [blk("b1", "Trắc nghiệm chọn một", "SINGLE_CHOICE", 16, rppq=0.25),
                                     blk("b2", "Đúng/Sai", "TRUE_FALSE", 2, rppq=1),
                                     blk("b3", "Trả lời ngắn", "SHORT_ANSWER", 4, rppq=0.25),
                                     blk("b4", "Tự luận", "ESSAY", 3, rppq=1)])
for code, (subj, dur, qc, name) in SPT_META.items():
    TEMPLATES[code] = (subject_doc(subj, name, dur, qc, SPT_BLOCKS[subj]), dur,
                       "Chia khối và điểm là giả định (đề test giả định mục 2.4).")
for code, (subj, dur, qc, name, key) in SP2E_META.items():
    TEMPLATES[code] = (subject_doc(subj, name, dur, qc, SP2E_BLOCKS[key]), dur,
                       "Chia khối và điểm là giả định (đề test giả định mục 2.4).")
TEMPLATES["SPT_MATH_2025"] = (subject_doc(SPT_MATH[0], SPT_MATH[3], SPT_MATH[1],
                                          SPT_MATH[2], SPT_MATH[4]), 90, None)


def dump(d):
    return json.dumps(d, ensure_ascii=False, separators=(",", ":"))


def sql_quote(s):
    return "'" + s.replace("'", "''") + "'"


def emit_template_migration():
    lines = ["-- FU/be-seed (UNI-2634): hoàn thiện mẫu theo đề test giả định",
             "-- (docs/specs/2026-11-17-de-test-gia-dinh.md). Số liệu \"giả định\" chờ chuyên gia.",
             "-- Không ghi groupOrder/shuffle: các trường đảo bị bỏ ở lane này.",
             ""]
    for code, (doc_obj, duration, note) in TEMPLATES.items():
        cols = f"structure = {sql_quote(dump(doc_obj))}::jsonb"
        cols += f", duration_minutes = {duration if duration is not None else 'NULL'}"
        if note is not None:
            cols += f", expert_note = {sql_quote(note)}"
        lines.append(f"UPDATE exam.exam_template SET {cols} WHERE code = {sql_quote(code)};")
    return "\n".join(lines) + "\n"


def emit_catalog_migration():
    return f"""-- FU/be-seed (UNI-2634): dạng bài "Hoàn thành câu" của phần Tiếng Anh HSA
-- (đề test giả định mục 2.1). Nhóm Hoàn thành câu dùng dạng bài này thay vì chia 12/12/11.
INSERT INTO catalog.catalog_item (id, group_id, code, code_normalized, value, name_vi, name_en, description, display_order, status, is_system, parent_id, depth, path)
SELECT '{TASK_SENTENCE}', '{G_TASK}', 'SENTENCE_COMPLETION', 'sentence_completion',
       GREATEST(9, g.last_value + 1, COALESCE((SELECT MAX(value) + 1 FROM catalog.catalog_item WHERE group_id = g.id), 1)),
       'Hoàn thành câu', 'Sentence completion', '[MẪU] Dạng bài hoàn thành câu (HSA Tiếng Anh)', 8, 1, false, NULL, 1, '/{TASK_SENTENCE}/'
FROM catalog.catalog_group g WHERE g.id = '{G_TASK}'
ON CONFLICT (group_id, code_normalized) DO NOTHING;

UPDATE catalog.catalog_group g SET last_value = GREATEST(g.last_value, COALESCE((SELECT MAX(value) FROM catalog.catalog_item WHERE group_id = g.id), 0)) WHERE g.id = '{G_TASK}';
"""


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
    out.append("-- QUESTION_TAG + nút MAPID dùng cho bài lỗi TEST-ERR-03/06/07.")
    items = [
        (TAG_HIEM, G_TAG, "TEST-HIEM", "test-hiem", "Nhãn test hiếm", "Rare test tag", "[MẪU] Nhãn cho TEST-ERR-03", 0),
        (KNOW_TOAN_HEP, G_KNOW, "TEST-TOAN-HEP", "test-toan-hep", "Chương test hẹp Toán", "Narrow test chapter Math", "[MẪU] Nút MAPID cho TEST-ERR-06", 0),
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

def exam_row(code, name, kind, scope, parts_doc, timing, duration=None,
             max_score=None, reference_max=None, practice="RAW", part_code=None,
             subject=None, template=None, structure_year=None, matrix_year=None,
             years_confirmed=True, description=None):
    return {"code": code, "name": name, "kind": kind, "scope": scope, "doc": parts_doc,
            "timing": timing, "duration": duration, "max_score": max_score,
            "reference_max": reference_max, "practice": practice,
            "part_code": part_code, "subject": subject, "template": template,
            "structure_year": structure_year, "matrix_year": matrix_year,
            "years_confirmed": years_confirmed,
            "description": description or "Đề test giả định (UNI-2634)."}


EXAMS = [
    exam_row("TEST-HSA-01", "TEST · HSA toàn bài", "HSA", "FULL",
             template_hsa_full(), "PER_PART", 195, 150,
             template="HSA_FULL", structure_year=2026, matrix_year=2026),
    exam_row("TEST-HSA-02", "TEST · HSA phần Toán", "HSA", "PART",
             template_hsa_part_math(), "PER_PART", 75, 50,
             part_code="TOAN", template="HSA_PART_MATH",
             structure_year=2026, matrix_year=2026),
    exam_row("TEST-HSA-03", "TEST · HSA phần Văn", "HSA", "PART",
             template_hsa_part_lit(), "PER_PART", 60, 50,
             part_code="VAN", template="HSA_PART_LITERATURE",
             structure_year=2026, matrix_year=2026),
    exam_row("TEST-HSA-04", "TEST · HSA phần Khoa học/Tiếng Anh", "HSA", "PART",
             template_hsa_part_science(), "PER_PART", 60, 50,
             part_code="SCIENCE", template="HSA_PART_SCIENCE",
             structure_year=2026, matrix_year=2026),
    exam_row("TEST-QDA-01", "TEST · QDA toàn bài", "QDA", "FULL",
             template_qda_full(), "PER_PART", 195, 150, template="QDA_FULL"),
    exam_row("TEST-BCA-01", "TEST · BCA toàn bài", "BCA", "FULL",
             template_bca_full(), "SHARED", 180, 100, template="BCA_FULL"),
    exam_row("TEST-VACT-01", "TEST · V-ACT toàn bài", "VACT", "FULL",
             template_vact_full(), "SHARED", 150, None, reference_max=1200,
             practice="LINEAR", template="VACT_FULL"),
    exam_row("TEST-SPT-01", "TEST · SPT Toán 2025", "SPT", "SUBJECT",
             TEMPLATES["SPT_MATH_2025"][0], "PER_PART", 90, 10,
             subject="TOAN", template="SPT_MATH_2025",
             structure_year=2025, matrix_year=2025),
    exam_row("TEST-SP2E-01", "TEST · SP2E Toán 2025", "SP2E", "SUBJECT",
             TEMPLATES["SP2E_MATH_2025"][0], "PER_PART", 90, 10,
             subject="TOAN", template="SP2E_MATH_2025",
             structure_year=2025, matrix_year=2025),
    exam_row("TEST-SP2E-02", "TEST · SP2E Toán 2026", "SP2E", "SUBJECT",
             TEMPLATES["SP2E_MATH_2026"][0], "PER_PART", 90, 10,
             subject="TOAN", template="SP2E_MATH_2026",
             structure_year=2026, matrix_year=2025, years_confirmed=True),
    exam_row("TEST-SP2E-03", "TEST · SP2E Tiếng Anh 2026", "SP2E", "SUBJECT",
             TEMPLATES["SP2E_ANH_2026"][0], "PER_PART", 60, 10,
             subject="ANH", template="SP2E_ANH_2026",
             structure_year=2026, matrix_year=2026),
    exam_row("TEST-THPTQG-01", "TEST · THPTQG Toán 2025", "THPTQG", "SUBJECT",
             subject_doc("TOAN", "Toán", 90, 22, [
                 blk("b1", "Trắc nghiệm chọn một", "SINGLE_CHOICE", 12, rppq=0.25),
                 blk("b2", "Đúng/Sai", "TRUE_FALSE", 4, rppq=1),
                 blk("b3", "Trả lời ngắn", "SHORT_ANSWER", 6, rppq=0.5)]),
             "PER_PART", 90, 10, subject="TOAN", template="THPTQG_MATH_2025",
             structure_year=2025, matrix_year=2025),
    exam_row("TEST-THPTQG-02", "TEST · THPTQG Ngữ văn 2025", "THPTQG", "SUBJECT",
             subject_doc("VAN", "Ngữ văn", 120, 7, [
                 blk("b1", "Đọc hiểu", "QUESTION_SET", sets=1, size=5,
                     child="ESSAY", rppq=0.8),
                 blk("b2", "Viết đoạn văn (khoảng 200 chữ)", "ESSAY", 1, rppq=2),
                 blk("b3", "Viết bài văn (khoảng 600 chữ)", "ESSAY", 1, rppq=4)]),
             "PER_PART", 120, 10, subject="VAN", template="THPTQG_LITERATURE_2025",
             structure_year=2025, matrix_year=2025),
    exam_row("TEST-THPTQG-03", "TEST · THPTQG Tin học 2025", "THPTQG", "SUBJECT",
             TEMPLATES["THPTQG_INFORMATICS_2025"][0], "SHARED", 50, 10,
             subject="INFORMATICS", template="THPTQG_INFORMATICS_2025",
             structure_year=2025, matrix_year=2025),
    # ---- 13 bài lỗi ----
    exam_row("TEST-ERR-01", "TEST · ERR ma trận thiếu cách áp", "THPTQG", "SUBJECT",
             subject_doc("TOAN", "Toán", 90, 22, [
                 blk("b1", "Trắc nghiệm chọn một", "SINGLE_CHOICE", 12, rppq=0.25,
                     matrix={"mode": None, "unit": "COUNT", "cells": [],
                             "byGrade": [{"code": "G12", "value": 12}],
                             "byDifficulty": []}),
                 blk("b2", "Đúng/Sai", "TRUE_FALSE", 4, rppq=1),
                 blk("b3", "Trả lời ngắn", "SHORT_ANSWER", 6, rppq=0.5)]),
             "PER_PART", 90, 10, subject="TOAN", template="THPTQG_MATH_2025",
             structure_year=2025, matrix_year=2025,
             description="Lỗi: khối MĐ có matrix nhưng chưa chọn cách áp (mode null) -> MATRIX_INCOMPLETE."),
    exam_row("TEST-ERR-02", "TEST · ERR điểm phần 2 BCA", "BCA", "FULL",
             template_bca_full(english_points=1, part2_max=60), "SHARED", 180, 100,
             template="BCA_FULL",
             description="Lỗi: khối Tiếng Anh 1đ (tổng phần 65 > max 60) -> PART_POINTS_MISMATCH + POINTS_TOTAL_MISMATCH."),
    exam_row("TEST-ERR-03", "TEST · ERR chùm hiếm", "HSA", "PART",
             doc([part("p2", "Văn học – Ngôn ngữ", 60, 50,
                       sections=[hsa_lit_section("s-lit2", tag=True)],
                       max_points=50)]),
             "PER_PART", 60, 50, part_code="VAN", template="HSA_PART_LITERATURE",
             structure_year=2026, matrix_year=2026,
             description="Lỗi: nhóm Đọc ngữ liệu lọc nhãn TEST-HIEM (2 < 5 bộ) -> SET_SHORTAGE."),
    exam_row("TEST-ERR-04", "TEST · ERR tổng môn 49", "HSA", "PART",
             doc([part("p1", "Toán học và Xử lí số liệu", 75, 50,
                       sections=[hsa_math_section("s-math1", single_count=40)],
                       max_points=50)]),
             "PER_PART", 75, 50, part_code="TOAN", template="HSA_PART_MATH",
             structure_year=2026, matrix_year=2026,
             description="Lỗi: nhóm câu đơn 40 (môn 49) -> SECTION_TOTAL_MISMATCH."),
    exam_row("TEST-ERR-05", "TEST · ERR chỉ tiêu TLN 14", "HSA", "PART",
             doc([part("p1", "Toán học và Xử lí số liệu", 75, 50,
                       sections=[hsa_math_section("s-math1", quota={
                           "unit": "COUNT", "items": [
                               {"answerType": "SINGLE_CHOICE", "value": 35},
                               {"answerType": "SHORT_ANSWER", "value": 14}]})],
                       max_points=50)]),
             "PER_PART", 75, 50, part_code="TOAN", template="HSA_PART_MATH",
             structure_year=2026, matrix_year=2026,
             description="Lỗi: chỉ tiêu Trả lời ngắn 14 (tổng 49) -> ANSWER_QUOTA_TOTAL_MISMATCH."),
    exam_row("TEST-ERR-06", "TEST · ERR MAPID hẹp Toán", "HSA", "PART",
             doc([part("p1", "Toán học và Xử lí số liệu", 75, 50,
                       sections=[hsa_math_section(
                           "s-math1", scope=scope_nodes([KNOW_TOAN_HEP]))],
                       max_points=50)]),
             "PER_PART", 75, 50, part_code="TOAN", template="HSA_PART_MATH",
             structure_year=2026, matrix_year=2026,
             description="Lỗi: phạm vi MAPID TEST-TOAN-HEP (10 TLN < 15) -> ANSWER_TYPE_SHORTAGE."),
    exam_row("TEST-ERR-07", "TEST · ERR Địa lí chỉ đủ 16", "HSA", "PART",
             doc([part("p3", "Khoa học hoặc Tiếng Anh", 60, 50,
                       options=[hsa_science_option(dia_scope=True),
                                option("o-eng", "SUBJECT", "ANH", "Tiếng Anh",
                                       sections=[hsa_eng_section("s-eng3")])],
                       max_points=50, choose=1)]),
             "PER_PART", 60, 50, part_code="SCIENCE", template="HSA_PART_SCIENCE",
             structure_year=2026, matrix_year=2026,
             description="Lỗi: Địa lí đủ đúng 16, thiếu khi nhận 17 -> INVALID ở 3 selections."),
    exam_row("TEST-ERR-08", "TEST · ERR Vật lí thiếu nhóm còn lại", "HSA", "PART",
             doc([part("p3", "Khoa học hoặc Tiếng Anh", 60, 50,
                       options=[option("o-sci", "SUBJECT_CHOICE", "SCIENCE", "Khoa học",
                                       sections=[
                                           section("s-sci-ly", "LY", None, [
                                               blk("b-sci-ly-cluster", "Vật lí · chùm",
                                                   "QUESTION_SET", sets=2, size=3,
                                                   task="CLUSTER", child="SINGLE_CHOICE",
                                                   group="HSA_LY_CLUSTER"),
                                               blk("b-sci-ly-fixed", "Vật lí · cố định 11",
                                                   None, qcount=11)],
                                               quotas=SCI_QUOTA, matrix=SCI_MATRIX,
                                               choice="NATURAL"),
                                           *hsa_science_option()["sections"][1:]],
                                       choose_subjects=3, counts=[17, 17, 16]),
                                option("o-eng", "SUBJECT", "ANH", "Tiếng Anh",
                                       sections=[hsa_eng_section("s-eng3")])],
                       max_points=50, choose=1)]),
             "PER_PART", 60, 50, part_code="SCIENCE", template="HSA_PART_SCIENCE",
             structure_year=2026, matrix_year=2026,
             description="Vật lí gồm 6 câu chùm và 11 câu đơn; giải đủ 17 câu trước khi bỏ một câu."),
    exam_row("TEST-ERR-09", "TEST · ERR Hóa học chỉ tiêu số câu", "HSA", "PART",
             doc([part("p3", "Khoa học hoặc Tiếng Anh", 60, 50,
                       options=[option("o-sci", "SUBJECT_CHOICE", "SCIENCE", "Khoa học",
                                       sections=[
                                           hsa_science_option()["sections"][0],
                                           section("s-sci-hoa", "HOA", None, [
                                               blk("b-sci-hoa-cluster", "Hóa học · chùm",
                                                   "QUESTION_SET", sets=2, size=3,
                                                   task="CLUSTER", child="SINGLE_CHOICE",
                                                   group="HSA_HOA_CLUSTER"),
                                               blk("b-sci-hoa-rest", "Hóa học · lấy phần còn lại",
                                                   None, group="HSA_HOA_REMAINDER")],
                                               quotas={"unit": "COUNT", "items": [
                                                   {"answerType": "SINGLE_CHOICE", "value": 12},
                                                   {"answerType": "SHORT_ANSWER", "value": 4}]},
                                               matrix=SCI_MATRIX, choice="NATURAL"),
                                           *hsa_science_option()["sections"][2:]],
                                       choose_subjects=3, counts=[17, 17, 16]),
                                option("o-eng", "SUBJECT", "ANH", "Tiếng Anh",
                                       sections=[hsa_eng_section("s-eng3")])],
                       max_points=50, choose=1)]),
             "PER_PART", 60, 50, part_code="SCIENCE", template="HSA_PART_SCIENCE",
             structure_year=2026, matrix_year=2026,
             description="Lỗi: chỉ tiêu Hóa học có tổng 16 câu thay vì 17 câu."),
    exam_row("TEST-ERR-10", "TEST · ERR countsByPick 17-17-17", "HSA", "PART",
             doc([part("p3", "Khoa học hoặc Tiếng Anh", 60, 50,
                       options=[option("o-sci", "SUBJECT_CHOICE", "SCIENCE", "Khoa học",
                                       sections=hsa_science_option()["sections"],
                                       choose_subjects=3, counts=[17, 17, 17]),
                                option("o-eng", "SUBJECT", "ANH", "Tiếng Anh",
                                       sections=[hsa_eng_section("s-eng3")])],
                       max_points=50, choose=1)]),
             "PER_PART", 60, 50, part_code="SCIENCE", template="HSA_PART_SCIENCE",
             structure_year=2026, matrix_year=2026,
             description="Lỗi: countsByPick 17/17/17 -> SUBJECT_CHOICE_COUNTS_INVALID."),
    exam_row("TEST-ERR-11", "TEST · ERR ô G12×VDC thiếu", "THPTQG", "SUBJECT",
             subject_doc("TOAN", "Toán", 90, 22, [
                 blk("b1", "Trắc nghiệm chọn một", "SINGLE_CHOICE", 12, rppq=0.25,
                     matrix={"mode": "COMBINED", "unit": "COUNT",
                             "cells": [{"gradeCode": "G12", "difficultyCode": "VDC",
                                        "value": 12}],
                             "byGrade": [], "byDifficulty": []}),
                 blk("b2", "Đúng/Sai", "TRUE_FALSE", 4, rppq=1),
                 blk("b3", "Trả lời ngắn", "SHORT_ANSWER", 6, rppq=0.5)]),
             "PER_PART", 90, 10, subject="TOAN", template="THPTQG_MATH_2025",
             structure_year=2025, matrix_year=2025,
             description="Lỗi: ô Lớp 12 × VDC đặt 12, bộ mẫu tối đa 4 -> CELL_SHORTAGE."),
    exam_row("TEST-ERR-12", "TEST · ERR khối TLN bỏ điểm", "THPTQG", "SUBJECT",
             subject_doc("TOAN", "Toán", 90, 22, [
                 blk("b1", "Trắc nghiệm chọn một", "SINGLE_CHOICE", 12, rppq=0.25),
                 blk("b2", "Đúng/Sai", "TRUE_FALSE", 4, rppq=1),
                 blk("b3", "Trả lời ngắn", "SHORT_ANSWER", 6, rppq=None)]),
             "PER_PART", 90, 10, subject="TOAN", template="THPTQG_MATH_2025",
             structure_year=2025, matrix_year=2025,
             description="Lỗi: khối Trả lời ngắn bỏ điểm mỗi câu -> POINTS_TOTAL_MISMATCH."),
    exam_row("TEST-ERR-13", "TEST · ERR năm chưa xác nhận", "SP2E", "SUBJECT",
             TEMPLATES["SP2E_MATH_2026"][0], "PER_PART", 90, 10,
             subject="TOAN", template="SP2E_MATH_2026",
             structure_year=2026, matrix_year=2025, years_confirmed=False,
             description="Lỗi: cấu trúc 2026, ma trận 2025, chưa xác nhận -> YEARS_UNCONFIRMED."),
]

COMBO_ITEM = {"QD01": "00000000-0000-7000-8400-000000000709",
              "QD02": "00000000-0000-7000-8400-000000000710",
              "QD03": "00000000-0000-7000-8400-000000000711",
              "QD04": "00000000-0000-7000-8400-000000000712",
              "QD05": COMBO_QD05,
              "CA1": "00000000-0000-7000-8400-000000000713",
              "CA2": "00000000-0000-7000-8400-000000000714",
              "CA3": "00000000-0000-7000-8400-000000000715",
              "CA4": "00000000-0000-7000-8400-000000000716"}


def emit_exam(exam):
    eid = uid("exam", exam["code"])
    out = []
    code = exam["code"]
    qc = sum(p["questionCount"] or 0 for p in exam["doc"]["parts"])
    has_essay = json.dumps(exam["doc"], ensure_ascii=False).find('"ESSAY"') >= 0
    has_eng = '"ANH"' in json.dumps(exam["doc"])
    combos = {o["code"] for p in exam["doc"]["parts"] for o in p["options"]
              if o["kind"] == "COMBINATION"}
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
            f"{len(combos)},false,0")
    out.append(f"-- {code}: {exam['name']}")
    out.append("INSERT INTO exam.exam (id, code, code_normalized, name, description, instructions,")
    out.append("    kind_item_id, scope, part_code, subject_item_id, template_code, structure_year,")
    out.append("    matrix_year, years_confirmed, timing, duration_minutes, max_score, reference_max,")
    out.append("    practice_scoring, status, check_status, question_count, has_essay,")
    out.append("    has_english_option, combination_count, has_unpublished_changes, revision) VALUES")
    out.append(f"  ({cols})")
    out.append("ON CONFLICT (id) DO NOTHING;")
    out.append("")
    out.append("INSERT INTO exam.exam_structure (exam_id, parts, structure_hash, base_version, updated_by) VALUES")
    out.append(f"  ('{eid}',{sql_quote(dump(exam['doc']))}::jsonb,'seed-{code.lower()}',NULL,NULL)")
    out.append("ON CONFLICT (exam_id) DO NOTHING;")
    out.append("")
    # block refs
    rows = []
    for p in exam["doc"]["parts"]:
        for s in p["sections"]:
            for b in s["blocks"]:
                rows.append(ref_row(eid, p, None, s, b))
        for o in p["options"]:
            for s in o["sections"]:
                for b in s["blocks"]:
                    rows.append(ref_row(eid, p, o, s, b))
            for b in o["blocks"]:
                rows.append(ref_row(eid, p, o, None, b))
    if rows:
        out.append("INSERT INTO exam.exam_block_ref (id, exam_id, version_no, block_id, part_id,")
        out.append("    option_code, section_id, subject_item_id, question_type, answer_types,")
        out.append("    task_type_item_id, knowledge_item_ids, knowledge_mode, knowledge_inherited,")
        out.append("    tag_item_ids, competency_item_ids, required_points, combination_item_id) VALUES")
        out.append(",\n".join(rows) + "\nON CONFLICT (id) DO NOTHING;")
        out.append("")
    return out


def ref_row(eid, p, o, s, b):
    subject_code = s["subjectCode"] if s else (o["code"] if o and o["kind"] == "SUBJECT" else None)
    scope = b.get("knowledgeScope") or (s or {}).get("knowledgeScope")
    answer_types = set()
    if b.get("questionType"):
        answer_types.add(b["questionType"])
    if b.get("childAnswerType"):
        answer_types.add(b["childAnswerType"])
    if not answer_types and s and s.get("answerQuotas"):
        answer_types = {i["answerType"] for i in s["answerQuotas"]["items"]}
    know_ids = (scope or {}).get("nodeIds") or None
    tag_ids = [TASK_TAG_IDS.get(t, t) for t in (b.get("tagCodes") or [])]
    tag_ids = [t for t in tag_ids if t]
    combo = COMBO_ITEM.get(o["code"]) if o and o["kind"] == "COMBINATION" else None
    combo_ref = (f"(SELECT id FROM catalog.catalog_item WHERE group_id='{G_COMBO}' AND code_normalized='qd05')"
                 if combo == COMBO_QD05 else sql_quote(combo) if combo else "NULL")
    task = b.get("taskTypeCode")
    if task == "SENTENCE_COMPLETION":
        task_ref = f"(SELECT id FROM catalog.catalog_item WHERE group_id='{G_TASK}' AND code_normalized='sentence_completion')"
    else:
        task_ref = sql_quote(TASK[task]) if task else "NULL"
    return (f"('{uid('ref', eid, b['id'])}','{eid}',NULL,'{b['id']}','{p['id']}',"
            f"{sql_quote(o['code']) if o else 'NULL'},{sql_quote(s['id']) if s else 'NULL'},"
            f"{sql_quote(SUBJECT[subject_code]) if subject_code else 'NULL'},"
            f"{sql_quote(b['questionType']) if b.get('questionType') else 'NULL'},"
            f"{pg_array(sorted(answer_types)) if answer_types else 'NULL'},"
            f"{task_ref},"
            f"{pg_array(know_ids) if know_ids else 'NULL'},"
            f"{sql_quote(scope['match']) if scope and scope.get('match') else 'NULL'},"
            f"{'true' if not b.get('knowledgeScope') else 'false'},"
            f"{pg_array(tag_ids) if tag_ids else 'NULL'},NULL,"
            f"{b['requiredPointsPerQuestion'] if b.get('requiredPointsPerQuestion') is not None else 'NULL'},"
            f"{combo_ref})")


TASK_TAG_IDS = {"TEST-HIEM": TAG_HIEM}


def main():
    MIG_EXAM.write_text(emit_catalog_migration() + "\n" + emit_template_migration(), encoding="utf-8")
    SEED.write_text(emit_seed(), encoding="utf-8")
    n_q = len(QUESTIONS) + sum(len(s["children"]) + 1 for s in SETS)
    print(f"wrote {MIG_EXAM.name}, {SEED.name}; questions={n_q}, exams={len(EXAMS)}")


if __name__ == "__main__":
    main()
